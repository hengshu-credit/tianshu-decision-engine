package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.governance.*;
import com.hengshucredit.rule.server.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ModelVersionRestoreLifecycleTest {
    @Test
    public void governedRestoreCreatesNewModelVersionWithNewPersistedFieldIdsAndKeepsHistory() {
        verifyRestore(false);
    }

    @Test
    public void reusedNumberPublishesEditedContentAsSeparateSnapshotAndKeepsOriginal() {
        verifyRestore(true);
    }

    private void verifyRestore(boolean reuse) {
        try (var fixture = new ModelVersionExecutionTest.Fixture()) {
            for (Class<?> type : List.of(RuleModel.class, RuleModelVersion.class, RuleModelInputField.class, RuleModelOutputField.class)) {
                TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), type);
            }
            RuleModel original = ModelVersionExecutionTest.model(1, "OriginalAge", 1);
            original.setModelConfig("{\"testParams\":\"{\\\"OriginalAge\\\":25}\"}");
            RuleModelVersion historical = fixture.capture(original);
            String unchangedHistory = historical.getExecutionSnapshotJson();
            RuleModel current = ModelVersionExecutionTest.model(2, "CurrentAge", 100);
            current.getInputFields().get(0).setId(71L); current.getOutputFields().get(0).setId(81L);
            AtomicReference<RuleModel> stored = new AtomicReference<>(current);
            List<RuleModelInputField> inputs = new ArrayList<>(current.getInputFields());
            List<RuleModelOutputField> outputs = new ArrayList<>(current.getOutputFields());
            AtomicReference<RuleModelVersion> createdVersion = new AtomicReference<>();
            RuleModelVersion latest = new RuleModelVersion(); latest.setVersion(2);
            RuleModelInputFieldMapper inputMapper = mapper(RuleModelInputFieldMapper.class, (proxy, method, args) -> {
                if ("selectList".equals(method.getName())) return new ArrayList<>(inputs);
                if ("selectById".equals(method.getName())) return null;
                if ("insert".equals(method.getName())) {
                    var field = (RuleModelInputField) args[0];
                    if (reuse) assertEquals(original.getInputFields().get(0).getId(), field.getId());
                    else { assertNull(field.getId()); field.setId(501L); }
                    inputs.add(field); return 1;
                }
                if ("delete".equals(method.getName())) { inputs.removeIf(field -> Long.valueOf(71L).equals(field.getId())); return 1; }
                throw new AssertionError("恢复不应按历史字段ID更新现有行: " + method.getName());
            });
            RuleModelOutputFieldMapper outputMapper = mapper(RuleModelOutputFieldMapper.class, (proxy, method, args) -> {
                if ("selectList".equals(method.getName())) return new ArrayList<>(outputs);
                if ("selectById".equals(method.getName())) return null;
                if ("insert".equals(method.getName())) {
                    var field = (RuleModelOutputField) args[0];
                    if (reuse) assertEquals(original.getOutputFields().get(0).getId(), field.getId());
                    else { assertNull(field.getId()); field.setId(601L); }
                    outputs.add(field); return 1;
                }
                if ("delete".equals(method.getName())) { outputs.removeIf(field -> Long.valueOf(81L).equals(field.getId())); return 1; }
                throw new AssertionError("恢复不应按历史字段ID更新现有行: " + method.getName());
            });
            ReflectionTestUtils.setField(fixture.models, "modelMapper", mapper(RuleModelMapper.class, (proxy, method, args) -> {
                if (method.getName().startsWith("select")) return stored.get();
                if ("updateById".equals(method.getName())) { stored.set((RuleModel) args[0]); return 1; }
                throw new AssertionError(method.getName());
            }));
            ReflectionTestUtils.setField(fixture.models, "versionMapper", mapper(RuleModelVersionMapper.class, (proxy, method, args) -> {
                if ("maxBusinessVersion".equals(method.getName())) return 2;
                if ("selectList".equals(method.getName())) return List.of(com.alibaba.fastjson.JSON.parseObject(com.alibaba.fastjson.JSON.toJSONString(historical), RuleModelVersion.class));
                if ("selectOne".equals(method.getName())) {
                    var query = (LambdaQueryWrapper<?>) args[0];
                    return query.getSqlSegment().contains("ORDER BY") ? latest : historical;
                }
                if ("insert".equals(method.getName())) { createdVersion.set((RuleModelVersion) args[0]); return 1; }
                throw new AssertionError(method.getName());
            }));
            ReflectionTestUtils.setField(fixture.models, "inputFieldMapper", inputMapper);
            ReflectionTestUtils.setField(fixture.models, "outputFieldMapper", outputMapper);
            ReflectionTestUtils.setField(fixture.models, "modelVersionTests", fixture.tests);
            ProjectAuthProperties properties = new ProjectAuthProperties(); properties.setActiveKeyId("test");
            properties.setMasterKeys(Map.of("test", "test-model-version-master-key-32-characters"));
            ModelGovernedResourceAdapter adapter = new ModelGovernedResourceAdapter(new SimpleEntityGovernedResourceAdapter.EntityStore<RuleModel>() {
                public RuleModel load(Long id) { return stored.get(); }
                public void insert(RuleModel model) { throw new AssertionError("恢复不能另建模型根资源"); }
                public void update(RuleModel model) { stored.set(model); }
            }, inputMapper, outputMapper, new GovernanceSecretCodec(new CredentialCipher(properties)));
            ReflectionTestUtils.setField(adapter, "modelService", fixture.models);
            GovernedResourceVersion source = new GovernedResourceVersion();
            source.setId(91L); source.setResourceType("MODEL"); source.setResourceId(7L); source.setSnapshotJson(CanonicalJson.write(original));
            ReflectionTestUtils.setField(adapter, "governanceVersions", mapper(GovernedResourceVersionMapper.class,
                    (proxy, method, args) -> { assertEquals("selectById", method.getName()); assertEquals(91L, args[0]); return source; }));
            RuleModel restored = fixture.models.buildRestoreSnapshot(7L, 1, reuse);
            if (reuse) restored.setModelName("恢复后修改的模型名称");
            ResourceSnapshot draft = adapter.normalizeDraft(ResourceSnapshot.ofJson(CanonicalJson.write(restored)));

            adapter.apply(new ApprovalApplyContext(99L, 7L, reuse ? 1 : 3, "RESTORE", draft, "reviewer", 91L, reuse ? "REUSE" : "NEW"));

            assertEquals(Integer.valueOf(3), stored.get().getCurrentVersion());
            assertEquals(Integer.valueOf(3), createdVersion.get().getVersion());
            assertEquals(Integer.valueOf(reuse ? 1 : 3), createdVersion.get().getDisplayVersion());
            if (reuse) assertEquals("恢复后修改的模型名称", stored.get().getModelName());
            assertEquals(1, inputs.size()); assertEquals(1, outputs.size());
            assertEquals(reuse ? original.getInputFields().get(0).getId() : Long.valueOf(501L), inputs.get(0).getId());
            assertEquals(reuse ? original.getOutputFields().get(0).getId() : Long.valueOf(601L), outputs.get(0).getId());
            assertTrue(createdVersion.get().getExecutionSnapshotJson().contains("OriginalAge"));
            assertFalse(createdVersion.get().getExecutionSnapshotJson().contains("CurrentAge"));
            Map<?, ?> model = (Map<?, ?>) CanonicalJson.readMap(createdVersion.get().getExecutionSnapshotJson()).get("model");
            assertEquals(inputs.get(0).getId().intValue(), ((Map<?, ?>) ((List<?>) model.get("inputFields")).get(0)).get("id"));
            assertEquals(unchangedHistory, historical.getExecutionSnapshotJson());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
