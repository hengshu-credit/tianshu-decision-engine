package com.hengshucredit.rule.server.artifact;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.ArtifactDeployment;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.mapper.ArtifactDeploymentMapper;
import com.hengshucredit.rule.server.mapper.DecisionArtifactMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import com.hengshucredit.rule.server.service.RuleFieldAnalyzer;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PublishedRuleFieldSnapshotResolver {
    static final String INPUT_FIELDS_PATH = "rule/input-fields.json";
    static final String OUTPUT_FIELDS_PATH = "rule/output-fields.json";
    static final String FIELD_RESOLUTION_PATH = "rule/field-resolution.json";
    private static final ObjectMapper STRICT_JSON = strictJsonMapper();

    @Resource
    private RulePublishedMapper publishedMapper;
    @Resource
    private DecisionArtifactMapper artifactMapper;
    @Resource
    private ArtifactDeploymentMapper deploymentMapper;
    @Resource
    private RuleRevisionMapper revisionMapper;
    @Resource
    @org.springframework.context.annotation.Lazy
    private com.hengshucredit.rule.server.service.RuleVersionBindingService versionService;

    @Resource
    private ArtifactRuntimeSnapshotService runtimeSnapshots = new ArtifactRuntimeSnapshotService();

    public RuleFieldAnalyzer.ResolvedFields resolve(Long definitionId) {
        RulePublished published = loadPublishedRule(definitionId);
        if (published == null) {
            return missing(definitionId, null, "未找到生效的已发布规则");
        }
        return resolve(published);
    }

    public RuleFieldAnalyzer.ResolvedFields resolve(Long definitionId, Long versionBindingId) {
        if (versionBindingId == null) return resolve(definitionId);
        RulePublished published = loadPublishedRule(definitionId);
        if (published == null) return missing(definitionId, null, "未找到生效的已发布规则");
        try {
            return resolve(versionService.resolvePublished(published, versionBindingId));
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return missing(definitionId, null, failure.getMessage());
        }
    }

    public RuleFieldAnalyzer.ResolvedFields resolve(RulePublished published) {
        return resolve(published, published == null || published.getDefinitionId() == null
                || published.getRevisionId() == null || published.getArtifactId() == null
                ? null : loadArtifact(published.getArtifactId()), null);
    }

    /** 精确修订读取，不通过当前已发布规则替换历史修订身份。 */
    public RuleFieldAnalyzer.ResolvedFields resolveRevision(RuleRevision revision, DecisionArtifact artifact) {
        if (revision == null) return missing(null, null, "规则修订不存在");
        RulePublished binding = new RulePublished();
        binding.setDefinitionId(revision.getDefinitionId());
        binding.setRevisionId(revision.getId());
        binding.setArtifactId(revision.getArtifactId());
        binding.setArtifactDigest(artifact == null ? null : artifact.getArtifactDigest());
        return resolve(binding, artifact, revision);
    }

    private RuleFieldAnalyzer.ResolvedFields resolve(RulePublished published, DecisionArtifact artifact,
                                                      RuleRevision revision) {
        if (published == null || published.getDefinitionId() == null
                || published.getRevisionId() == null || published.getArtifactId() == null) {
            return missing(published == null ? null : published.getDefinitionId(),
                    published == null ? null : published.getRevisionId(),
                    "已发布规则未绑定完整的修订与制品标识");
        }
        if (artifact == null || artifact.getPackageContent() == null
                || artifact.getPackageContent().length == 0) {
            return missing(published.getDefinitionId(), published.getRevisionId(),
                    "已发布规则绑定的字段快照制品不存在");
        }
        if (artifact.getId() == null || !published.getArtifactId().equals(artifact.getId())) {
            return invalid(published, "制品实体与已发布规则的 artifactId 不一致");
        }

        DecisionArtifactPackageCodec.DecodedPackage decoded;
        try {
            // 复用执行路径已有的有界解包缓存；仍核对本次实际字节，字段对象在下方重新解析。
            decoded = runtimeSnapshots.decodeVerified(artifact);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return invalid(published, "字段快照制品包校验失败");
        }
        if (!same(decoded.getArtifactDigest(), artifact.getArtifactDigest())
                || !same(decoded.getPackageDigest(), artifact.getPackageDigest())
                || !same(decoded.getArtifactDigest(), published.getArtifactDigest())) {
            return invalid(published, "字段快照制品摘要不一致");
        }

        DecisionArtifactPackage artifactPackage = decoded.getArtifactPackage();
        Long sourceDefinition = metadataId(artifactPackage.getMetadata(), "definitionId");
        Long sourceRevision = metadataId(artifactPackage.getMetadata(), "revisionId");
        if (sourceDefinition == null || sourceRevision == null) {
            return invalid(published, "制品清单缺少有效的源 definitionId/revisionId");
        }
        boolean localEntity = artifact.getDefinitionId() != null || artifact.getRevisionId() != null;
        if (localEntity && (!sourceDefinition.equals(artifact.getDefinitionId())
                || !sourceRevision.equals(artifact.getRevisionId()))) {
            return invalid(published, "制品实体与清单的源 definitionId/revisionId 不一致");
        }
        boolean directPublication = localEntity && sourceDefinition.equals(published.getDefinitionId())
                && sourceRevision.equals(published.getRevisionId());
        if (!directPublication && !matchesImportedTarget(published, artifact)) {
            return invalid(published, "字段快照制品缺少匹配的目标部署与修订绑定");
        }
        if (revision != null) {
            Object contentDigest = artifactPackage.getMetadata().get("contentDigest");
            String expectedDigest = directPublication && contentDigest instanceof String digest
                    ? digest : directPublication ? null : artifact.getArtifactDigest();
            if (!same(expectedDigest, revision.getContentDigest())) {
                return invalid(published, "修订内容摘要与字段快照制品的来源不一致");
            }
        }
        String snapshotModelType;
        try {
            snapshotModelType = snapshotModelType(artifactPackage.getMetadata());
        } catch (SnapshotFormatException e) {
            return invalid(published, "字段快照制品 modelType 元数据无效");
        }
        DecisionArtifactPackage.Component inputComponent =
                artifactPackage.getComponent(INPUT_FIELDS_PATH);
        DecisionArtifactPackage.Component outputComponent =
                artifactPackage.getComponent(OUTPUT_FIELDS_PATH);
        if (inputComponent == null || outputComponent == null) {
            return missing(published.getDefinitionId(), published.getRevisionId(),
                    "字段快照制品缺少输入或输出字段组件");
        }

        try {
            List<RuleDefinitionInputField> inputFields =
                    parseArray(inputComponent, RuleDefinitionInputField.class);
            List<RuleDefinitionOutputField> outputFields =
                    parseArray(outputComponent, RuleDefinitionOutputField.class);
            ResolutionMetadata metadata = readResolutionMetadata(
                    artifactPackage.getComponent(FIELD_RESOLUTION_PATH),
                    snapshotModelType, outputFields);
            return new RuleFieldAnalyzer.ResolvedFields(
                    inputFields, outputFields, Collections.emptyList(),
                    metadata.localOutputNames, metadata.inputPropertySchemas,
                    metadata.outputPropertySchemas, snapshotModelType);
        } catch (SnapshotFormatException e) {
            return invalid(published, "字段快照组件内容无效");
        }
    }

    protected RulePublished loadPublishedRule(Long definitionId) {
        return publishedMapper == null || definitionId == null ? null
                : publishedMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getDefinitionId, definitionId)
                .eq(RulePublished::getStatus, 1)
                .last("LIMIT 1"));
    }

    protected DecisionArtifact loadArtifact(Long artifactId) {
        return artifactMapper == null || artifactId == null ? null
                : artifactMapper.selectById(artifactId);
    }

    private boolean matchesImportedTarget(RulePublished published, DecisionArtifact artifact) {
        ArtifactDeployment deployment = loadDeployment(artifact.getId(), published.getDefinitionId());
        if (deployment == null || deployment.getId() == null || !"DEPLOYED".equals(deployment.getStatus())
                || !artifact.getId().equals(deployment.getArtifactId())
                || !published.getDefinitionId().equals(deployment.getTargetDefinitionId())) return false;
        RuleRevision target = loadTargetRevision(published.getRevisionId());
        return target != null && published.getRevisionId().equals(target.getId())
                && published.getDefinitionId().equals(target.getDefinitionId())
                && artifact.getId().equals(target.getArtifactId())
                && same(artifact.getArtifactDigest(), target.getContentDigest());
    }

    protected ArtifactDeployment loadDeployment(Long artifactId, Long definitionId) {
        return deploymentMapper == null ? null : deploymentMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArtifactDeployment>()
                        .eq(ArtifactDeployment::getArtifactId, artifactId)
                        .eq(ArtifactDeployment::getTargetDefinitionId, definitionId)
                        .eq(ArtifactDeployment::getStatus, "DEPLOYED")
                        .orderByDesc(ArtifactDeployment::getId).last("LIMIT 1"));
    }

    protected RuleRevision loadTargetRevision(Long revisionId) {
        return revisionMapper == null ? null : revisionMapper.selectById(revisionId);
    }

    private <T> List<T> parseArray(DecisionArtifactPackage.Component component, Class<T> type) {
        JavaType listType = STRICT_JSON.getTypeFactory()
                .constructCollectionType(List.class, type);
        try {
            List<T> values = STRICT_JSON.readValue(component.getContent(), listType);
            if (values == null) {
                throw new SnapshotFormatException("field array root must not be null");
            }
            if (values.contains(null)) {
                throw new SnapshotFormatException("field array items must not be null");
            }
            return values;
        } catch (IOException e) {
            throw new SnapshotFormatException(e);
        }
    }

    private ResolutionMetadata readResolutionMetadata(
            DecisionArtifactPackage.Component component,
            String snapshotModelType,
            List<RuleDefinitionOutputField> outputFields) {
        if (component == null) {
            Set<String> localOutputs = new LinkedHashSet<>();
            if ("SCRIPT".equals(snapshotModelType)) {
                for (RuleDefinitionOutputField field : outputFields) {
                    if (field.getVarId() == null && field.getRefType() == null) {
                        String name = field.getScriptName() == null
                                ? field.getFieldName() : field.getScriptName();
                        if (name != null && !name.isBlank()) {
                            localOutputs.add(name);
                        }
                    }
                }
            }
            return new ResolutionMetadata(localOutputs,
                    Collections.emptyMap(), Collections.emptyMap());
        }
        Map<String, Object> source;
        try {
            source = STRICT_JSON.readValue(component.getContent(),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
        } catch (IOException e) {
            throw new SnapshotFormatException(e);
        }
        if (source == null) {
            throw new SnapshotFormatException("field resolution root must not be null");
        }
        Set<String> localOutputs = stringSet(source.get("localOutputNames"));
        validateLocalOutputs(snapshotModelType, localOutputs, outputFields);
        Map<String, Object> inputSchemas = stringKeyMap(source.get("inputPropertySchemas"));
        Map<String, Object> outputSchemas = stringKeyMap(source.get("outputPropertySchemas"));
        return new ResolutionMetadata(localOutputs, inputSchemas, outputSchemas);
    }

    private Set<String> stringSet(Object value) {
        if (value == null) return Collections.emptySet();
        if (!(value instanceof List<?> list)) {
            throw new SnapshotFormatException("localOutputNames 必须是数组");
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw new SnapshotFormatException("localOutputNames 元素无效");
            }
            result.add(text);
        }
        return result;
    }

    private Map<String, Object> stringKeyMap(Object value) {
        if (value == null) return Collections.emptyMap();
        if (!(value instanceof Map<?, ?> map)) {
            throw new SnapshotFormatException("字段 schema 元数据必须是对象");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new SnapshotFormatException("字段 schema 元数据键无效");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private void validateLocalOutputs(
            String snapshotModelType,
            Set<String> localOutputs,
            List<RuleDefinitionOutputField> outputFields) {
        if (!localOutputs.isEmpty() && !"SCRIPT".equals(snapshotModelType)) {
            throw new SnapshotFormatException(
                    "非 SCRIPT 制品不得声明 localOutputNames");
        }
        for (String localName : localOutputs) {
            boolean matched = false;
            for (RuleDefinitionOutputField field : outputFields) {
                String outputName = field.getScriptName() == null
                        ? field.getFieldName() : field.getScriptName();
                if (localName.equals(outputName)
                        && field.getVarId() == null && field.getRefType() == null) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                throw new SnapshotFormatException(
                        "localOutputNames 必须对应无引用的冻结输出字段");
            }
        }
    }

    private String snapshotModelType(Map<String, Object> metadata) {
        Object value = metadata.get("modelType");
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw new SnapshotFormatException("modelType 必须是非空字符串");
        }
        return text.trim().toUpperCase(Locale.ROOT);
    }

    private Long metadataId(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (!(value instanceof Number number)) {
            return null;
        }
        try {
            long id = new BigDecimal(number.toString()).longValueExact();
            return id > 0 ? id : null;
        } catch (ArithmeticException | NumberFormatException ignored) {
            return null;
        }
    }

    private static ObjectMapper strictJsonMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        return mapper;
    }

    private boolean same(String expected, String actual) {
        return expected != null && expected.equals(actual);
    }

    private RuleFieldAnalyzer.ResolvedFields missing(
            Long definitionId, Long revisionId, String message) {
        RuleValidationIssue issue = RuleValidationIssue.error(
                "FROZEN_REVISION_FIELD_SNAPSHOT_MISSING", "$.ruleFields",
                "RULE", definitionId, message).withRevisionId(revisionId);
        return errorFields(issue);
    }

    private RuleFieldAnalyzer.ResolvedFields invalid(RulePublished published, String message) {
        RuleValidationIssue issue = RuleValidationIssue.error(
                "FROZEN_REVISION_FIELD_SNAPSHOT_INVALID", "$.ruleFields",
                "RULE", published.getDefinitionId(), message)
                .withRevisionId(published.getRevisionId());
        return errorFields(issue);
    }

    private RuleFieldAnalyzer.ResolvedFields errorFields(RuleValidationIssue issue) {
        return new RuleFieldAnalyzer.ResolvedFields(
                Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(issue), Collections.emptySet(),
                Collections.emptyMap(), Collections.emptyMap());
    }

    private record ResolutionMetadata(
            Set<String> localOutputNames,
            Map<String, Object> inputPropertySchemas,
            Map<String, Object> outputPropertySchemas) {
    }

    private static final class SnapshotFormatException extends IllegalArgumentException {
        private SnapshotFormatException(String message) {
            super(message);
        }

        private SnapshotFormatException(IOException cause) {
            super(cause);
        }
    }
}
