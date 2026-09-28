package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelVersion;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelVersionMapper;
import com.hengshucredit.rule.server.health.OnnxWarmupState;
import com.hengshucredit.rule.server.health.OnnxWarmupStatus;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.service.onnx.OnnxModelExecutionService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Function;

import static org.junit.Assert.*;

public class OnnxModelWarmupRunnerTest {
    @Test
    public void warmsOnlyEnabledPublishedModelsConfiguredForStartup() {
        Harness harness = new Harness();
        RuleModel active = model(1);
        RuleModel disabled = model(2); disabled.setStatus(0);
        RuleModel unpublished = model(3); unpublished.setPublishedVersion(null);
        RuleModel noPreload = model(4); noPreload.setPreloadOnStartup(0); noPreload.setModelDigest(null);
        harness.models.addAll(List.of(active, disabled, unpublished, noPreload));
        harness.versions.put(1L, version(active));

        harness.run();

        assertEquals(List.of(1L), harness.requestedModelIds);
        assertEquals(List.of(101L), harness.loadedSnapshotIds);
        assertEquals(1, harness.preloaded.size());
        assertEquals(1, harness.status.details().get("targetCount"));
        assertEquals(OnnxWarmupState.READY, harness.status.getState());
        assertTrue(harness.candidateQuery.getSqlSegment().contains("status ="));
        assertTrue(harness.candidateQuery.getSqlSegment().contains("preload_on_startup ="));
        assertTrue(harness.candidateQuery.getSqlSegment().contains("published_version >"));
        assertFalse(harness.candidateQuery.getSqlSelect().contains("model_content"));
    }

    @Test
    public void offlinePublishedModelWithMissingDigestDoesNotReadOrChangeContent() {
        Harness harness = new Harness();
        RuleModel offline = model(1);
        offline.setStatus(0); offline.setModelDigest(null);
        harness.models.add(offline);
        harness.run();
        assertTrue(harness.requestedModelIds.isEmpty());
        assertEquals(0, harness.status.details().get("targetCount"));
    }

    @Test
    public void zeroPublicationVersionDoesNotWarmOrRepairDraft() {
        Harness harness = new Harness();
        RuleModel draft = model(1);
        draft.setPublishedVersion(0); draft.setModelDigest(null);
        harness.models.add(draft);
        harness.run();
        assertTrue(harness.requestedModelIds.isEmpty());
        assertEquals(0, harness.status.details().get("targetCount"));
    }

    @Test
    public void publishedBytesAndRuntimeConfigAreUsedEvenWhenCurrentEditChangedFormat() {
        Harness harness = new Harness();
        RuleModel current = model(1);
        RuleModelVersion published = version(current);
        current.setModelFormat("PMML");
        current.setModelContent("unpublished invalid base64");
        current.setModelConfig("{\"device\":\"CUDA\"}");
        current.setModelDigest(null);
        current.setCurrentVersion(7);
        harness.models.add(current);
        harness.versions.put(1L, published);

        harness.run();

        assertEquals(OnnxWarmupState.READY, harness.status.getState());
        assertArrayEquals(new byte[]{1, 2, 3}, harness.preloaded.get(0));
        assertEquals(published.getModelConfig(), harness.configs.get(0));
        assertEquals("unpublished invalid base64", current.getModelContent());
        assertNull(current.getModelDigest());
        assertEquals(List.of(101L), harness.loadedSnapshotIds);
    }

    @Test
    public void publicationPointerIsPinnedBeforeConcurrentRepublication() {
        Harness harness = new Harness();
        RuleModel candidate = model(1);
        RuleModelVersion published = version(candidate);
        harness.models.add(candidate);
        harness.versions.put(1L, published);
        harness.loadContent = id -> {
            // 主表可能已发布第 6 版，本轮必须继续读取已选中的第 5 版快照。
            RuleModelVersion newer = version(candidate);
            newer.setId(102L); newer.setVersion(6); newer.setModelContent("new");
            harness.versions.put(1L, newer);
            return published;
        };

        harness.run();

        assertEquals(List.of(101L), harness.loadedSnapshotIds);
        assertArrayEquals(new byte[]{1, 2, 3}, harness.preloaded.get(0));
        assertEquals(OnnxWarmupState.READY, harness.status.getState());
    }

    @Test
    public void pmmlPublicationIsNotAnOnnxTargetEvenWhenCurrentEditIsOnnx() {
        Harness harness = new Harness();
        RuleModel candidate = model(1);
        harness.models.add(candidate);
        RuleModelVersion published = version(candidate); published.setModelFormat("PMML");
        harness.versions.put(1L, published);
        harness.run();
        assertEquals(0, harness.status.details().get("targetCount"));
        assertTrue(harness.loadedSnapshotIds.isEmpty());
        assertTrue(harness.preloaded.isEmpty());
    }

    @Test
    public void missingPublishedSnapshotFailsWithoutFallingBackToCurrentContent() {
        Harness harness = new Harness();
        harness.models.add(model(1));
        harness.run();
        assertEquals(OnnxWarmupState.FAILED, harness.status.getState());
        assertEquals(1, harness.status.details().get("failureCount"));
        assertTrue(harness.preloaded.isEmpty());
    }

    @Test
    public void unavailableOrMismatchedSnapshotIsRejected() {
        for (String invalid : List.of("modelId", "version", "status")) {
            Harness harness = new Harness();
            RuleModel candidate = model(1); harness.models.add(candidate);
            RuleModelVersion snapshot = version(candidate);
            switch (invalid) {
                case "modelId" -> snapshot.setModelId(2L);
                case "version" -> snapshot.setVersion(4);
                case "status" -> snapshot.setStatus(0);
                case "format" -> snapshot.setModelFormat("PMML");
            }
            harness.versions.put(1L, snapshot);
            harness.run();
            assertEquals(invalid, OnnxWarmupState.FAILED, harness.status.getState());
            assertTrue(invalid, harness.preloaded.isEmpty());
        }
    }

    @Test
    public void corruptPublishedDigestIsRejectedBeforeNativePreload() {
        Harness harness = new Harness();
        RuleModel candidate = model(1); harness.models.add(candidate);
        RuleModelVersion snapshot = version(candidate); snapshot.setModelDigest("corrupt");
        harness.versions.put(1L, snapshot);
        harness.run();
        assertEquals(OnnxWarmupState.FAILED, harness.status.getState());
        assertTrue(harness.preloaded.isEmpty());
    }

    @Test
    public void missingHistoricalDigestDoesNotCauseWritesOnStartup() {
        Harness harness = new Harness();
        RuleModel candidate = model(1); harness.models.add(candidate);
        RuleModelVersion snapshot = version(candidate); snapshot.setModelDigest(null);
        harness.versions.put(1L, snapshot);
        harness.run();
        assertEquals(OnnxWarmupState.READY, harness.status.getState());
        assertEquals(1, harness.preloaded.size());
        assertNull(snapshot.getModelDigest());
        // Harness 对全部写操作抛错，不能用修改主表/历史快照来使启动成功。
    }

    @Test
    public void continuesWhenOneModelHasNativeLibraryLoadFailure() {
        Harness harness = new Harness();
        harness.failFirstPreload = true;
        for (long id : List.of(1L, 2L)) {
            RuleModel candidate = model(id); harness.models.add(candidate);
            harness.versions.put(id, version(candidate));
        }
        harness.run();
        assertEquals(2, harness.preloaded.size());
        assertEquals(OnnxWarmupState.FAILED, harness.status.getState());
        assertEquals(1, harness.status.details().get("failureCount"));
        assertEquals(1, harness.status.details().get("successCount"));
    }

    @Test
    public void detailLoadFailureMarksReadinessFailedWithoutAbortingRunner() {
        Harness harness = new Harness();
        RuleModel candidate = model(1); harness.models.add(candidate);
        harness.versions.put(1L, version(candidate));
        harness.loadContent = id -> { throw new IllegalStateException("database unavailable"); };
        harness.run();
        assertEquals(OnnxWarmupState.FAILED, harness.status.getState());
        assertEquals(1, harness.status.details().get("failureCount"));
    }

    @Test
    public void successfulCpuFallbackIsReadyAndCounted() {
        Harness harness = new Harness();
        harness.cpuFallback = true;
        RuleModel candidate = model(1); harness.models.add(candidate);
        harness.versions.put(1L, version(candidate));
        harness.run();
        assertEquals(OnnxWarmupState.READY, harness.status.getState());
        assertEquals(1, harness.status.details().get("cpuFallbackCount"));
    }

    @Test
    public void emptyCandidateListMarksWarmupReady() {
        Harness harness = new Harness();
        harness.run();
        assertEquals(OnnxWarmupState.READY, harness.status.getState());
        assertEquals(0, harness.status.details().get("targetCount"));
    }

    private static RuleModel model(long id) {
        RuleModel model = new RuleModel();
        model.setId(id); model.setModelCode("model" + id); model.setModelFormat("ONNX");
        model.setStatus(1); model.setPreloadOnStartup(1); model.setPublishedVersion(5);
        model.setModelContent(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}));
        model.setModelDigest(Sha256Digests.bytes(new byte[]{1, 2, 3}));
        model.setModelConfig("{\"onnxTaskType\":\"MN3_ANTISPOOF\"}");
        return model;
    }

    private static RuleModelVersion version(RuleModel model) {
        RuleModelVersion version = new RuleModelVersion();
        version.setId(model.getId() + 100); version.setModelId(model.getId());
        version.setVersion(model.getPublishedVersion()); version.setStatus(1);
        version.setModelFormat(model.getModelFormat()); version.setModelContent(model.getModelContent());
        version.setModelDigest(model.getModelDigest()); version.setModelConfig(model.getModelConfig());
        return version;
    }

    private static final class Harness {
        private final List<RuleModel> models = new ArrayList<>();
        private final Map<Long, RuleModelVersion> versions = new LinkedHashMap<>();
        private final List<Long> requestedModelIds = new ArrayList<>();
        private final List<Long> loadedSnapshotIds = new ArrayList<>();
        private final List<byte[]> preloaded = new ArrayList<>();
        private final List<String> configs = new ArrayList<>();
        private final OnnxWarmupStatus status = new OnnxWarmupStatus();
        private QueryWrapper<?> candidateQuery;
        private Function<Long, RuleModelVersion> loadContent;
        private boolean failFirstPreload;
        private boolean cpuFallback;

        private void run() {
            RuleModelMapper modelsMapper = (RuleModelMapper) Proxy.newProxyInstance(
                    RuleModelMapper.class.getClassLoader(), new Class<?>[]{RuleModelMapper.class},
                    (proxy, method, args) -> {
                        if ("selectList".equals(method.getName())) {
                            candidateQuery = (QueryWrapper<?>) args[0];
                            return models;
                        }
                        throw new AssertionError("禁止从编辑主表读取大模型或回写数据: " + method.getName());
                    });
            RuleModelVersionMapper versionsMapper = (RuleModelVersionMapper) Proxy.newProxyInstance(
                    RuleModelVersionMapper.class.getClassLoader(), new Class<?>[]{RuleModelVersionMapper.class},
                    (proxy, method, args) -> {
                        if ("selectOne".equals(method.getName())) {
                            QueryWrapper<?> query = (QueryWrapper<?>) args[0];
                            assertTrue(query.getSqlSegment().contains("model_id ="));
                            assertTrue(query.getSqlSegment().contains("version ="));
                            assertFalse(query.getSqlSelect().contains("model_content"));
                            Long modelId = (Long) query.getParamNameValuePairs().get("MPGENVAL1");
                            Integer versionNo = (Integer) query.getParamNameValuePairs().get("MPGENVAL2");
                            assertEquals(Integer.valueOf(5), versionNo);
                            requestedModelIds.add(modelId);
                            return versions.get(modelId);
                        }
                        if ("selectById".equals(method.getName())) {
                            Long id = (Long) args[0]; loadedSnapshotIds.add(id);
                            return loadContent == null ? versions.values().stream()
                                    .filter(v -> id.equals(v.getId())).findFirst().orElse(null) : loadContent.apply(id);
                        }
                        throw new AssertionError("不得修改发布快照: " + method.getName());
                    });
            OnnxModelExecutionService executor = new OnnxModelExecutionService(null) {
                @Override public PreloadOutcome preloadWithOutcome(byte[] bytes, String config) {
                    preloaded.add(bytes); configs.add(config);
                    if (failFirstPreload && preloaded.size() == 1) throw new UnsatisfiedLinkError("native library missing");
                    return cpuFallback ? PreloadOutcome.CPU_FALLBACK : PreloadOutcome.READY;
                }
            };
            OnnxModelWarmupRunner runner = new OnnxModelWarmupRunner(modelsMapper, executor, status);
            ReflectionTestUtils.setField(runner, "versionMapper", versionsMapper);
            runner.run(null);
        }
    }
}
