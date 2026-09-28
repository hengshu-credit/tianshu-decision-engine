package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelVersion;
import com.hengshucredit.rule.server.health.OnnxWarmupStatus;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelVersionMapper;
import jakarta.annotation.Resource;
import com.hengshucredit.rule.server.service.onnx.OnnxModelExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
public class OnnxModelWarmupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OnnxModelWarmupRunner.class);

    private final RuleModelMapper modelMapper;
    private final OnnxModelExecutionService executionService;
    private final OnnxWarmupStatus warmupStatus;
    @Resource private RuleModelVersionMapper versionMapper;

    public OnnxModelWarmupRunner(RuleModelMapper modelMapper,
                                 OnnxModelExecutionService executionService,
                                 OnnxWarmupStatus warmupStatus) {
        this.modelMapper = modelMapper;
        this.executionService = executionService;
        this.warmupStatus = warmupStatus;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<RuleModel> candidates;
        try {
            candidates = modelMapper.selectList(new QueryWrapper<RuleModel>()
                    .select("id", "model_code", "model_name", "model_format", "status", "preload_on_startup", "published_version")
                    .eq("status", 1).eq("preload_on_startup", 1).gt("published_version", 0));
        } catch (RuntimeException | LinkageError e) {
            warmupStatus.fail(e);
            log.error("查询 ONNX 启动预热候选失败", e);
            return;
        }
        List<WarmupTarget> targets = new ArrayList<>();
        for (RuleModel candidate : candidates == null
                ? Collections.<RuleModel>emptyList() : candidates) {
            if (!requiresWarmup(candidate)) continue;
            try {
                // 先读发布快照元信息，避免一次载入所有大模型；不按主表编辑中的格式判断。
                RuleModelVersion version = versionMapper.selectOne(new QueryWrapper<RuleModelVersion>()
                        .select("id", "model_id", "version", "model_format", "status")
                        .eq("model_id", candidate.getId()).eq("version", candidate.getPublishedVersion()));
                validatePublishedVersion(candidate, version);
                if ("ONNX".equals(effectiveFormat(candidate, version))) {
                    targets.add(new WarmupTarget(candidate, version.getId(), null));
                }
            } catch (RuntimeException | LinkageError e) {
                targets.add(new WarmupTarget(candidate, null, e));
            }
        }
        warmupStatus.start(targets.size());
        for (WarmupTarget target : targets) {
            RuleModel candidate = target.model();
            try {
                if (target.failure() != null) throw new IllegalStateException("发布模型快照读取失败", target.failure());
                RuleModelVersion version = versionMapper.selectById(target.versionId());
                validatePublishedVersion(candidate, version);
                if (!"ONNX".equals(effectiveFormat(candidate, version))) {
                    throw new IllegalStateException("发布 ONNX 模型快照格式已变化");
                }
                byte[] modelBytes = Base64.getDecoder().decode(version.getModelContent());
                if (modelBytes.length == 0) throw new IllegalStateException("发布模型内容为空");
                if (version.getModelDigest() != null && !version.getModelDigest().isBlank()
                        && !version.getModelDigest().equalsIgnoreCase(Sha256Digests.bytes(modelBytes))) {
                    throw new IllegalStateException("发布模型摘要校验失败");
                }
                OnnxModelExecutionService.PreloadOutcome outcome =
                        executionService.preloadWithOutcome(modelBytes, version.getModelConfig());
                warmupStatus.recordSuccess(outcome == OnnxModelExecutionService.PreloadOutcome.CPU_FALLBACK);
                log.info("ONNX 发布模型启动预加载成功: {}({}) version={}",
                        candidate.getModelName(), candidate.getModelCode(), candidate.getPublishedVersion());
            } catch (RuntimeException | LinkageError e) {
                warmupStatus.recordFailure(e);
                log.error("ONNX 发布模型启动预加载失败: {}({}) version={}",
                        candidate.getModelName(), candidate.getModelCode(), candidate.getPublishedVersion(), e);
            }
        }
        warmupStatus.complete();
    }

    private boolean requiresWarmup(RuleModel candidate) {
        return candidate != null && Integer.valueOf(1).equals(candidate.getStatus())
                && Integer.valueOf(1).equals(candidate.getPreloadOnStartup())
                && candidate.getPublishedVersion() != null && candidate.getPublishedVersion() > 0;
    }

    private void validatePublishedVersion(RuleModel candidate, RuleModelVersion version) {
        if (version == null || version.getId() == null
                || !Objects.equals(candidate.getId(), version.getModelId())
                || !Objects.equals(candidate.getPublishedVersion(), version.getVersion())
                || !Integer.valueOf(1).equals(version.getStatus())) {
            throw new IllegalStateException("发布模型快照缺失或不可用，请重新发布: modelId="
                    + candidate.getId() + ", version=" + candidate.getPublishedVersion());
        }
    }

    private String effectiveFormat(RuleModel candidate, RuleModelVersion version) {
        if (version.getModelFormat() != null && !version.getModelFormat().isBlank()) {
            return version.getModelFormat();
        }
        // 早期发布快照没有保存格式；只用生产指针的格式做兼容判定，内容仍来自快照。
        return candidate.getModelFormat();
    }

    private record WarmupTarget(RuleModel model, Long versionId, Throwable failure) { }
}
