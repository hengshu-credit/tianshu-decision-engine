package com.hengshucredit.rule.server.artifact;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.mapper.DecisionArtifactMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
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
    @org.springframework.context.annotation.Lazy
    private com.hengshucredit.rule.server.service.RuleVersionBindingService versionService;

    private final DecisionArtifactPackageCodec codec = new DecisionArtifactPackageCodec();

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
        if (published == null || published.getDefinitionId() == null
                || published.getRevisionId() == null || published.getArtifactId() == null) {
            return missing(published == null ? null : published.getDefinitionId(),
                    published == null ? null : published.getRevisionId(),
                    "已发布规则未绑定完整的修订与制品标识");
        }
        DecisionArtifact artifact = loadArtifact(published.getArtifactId());
        if (artifact == null || artifact.getPackageContent() == null
                || artifact.getPackageContent().length == 0) {
            return missing(published.getDefinitionId(), published.getRevisionId(),
                    "已发布规则绑定的字段快照制品不存在");
        }
        if (artifact.getId() == null || !published.getArtifactId().equals(artifact.getId())
                || !published.getDefinitionId().equals(artifact.getDefinitionId())
                || !published.getRevisionId().equals(artifact.getRevisionId())) {
            return invalid(published, "制品实体与已发布规则的 definitionId/revisionId 不一致");
        }

        DecisionArtifactPackageCodec.DecodedPackage decoded;
        try {
            decoded = codec.decode(artifact.getPackageContent());
        } catch (IllegalArgumentException e) {
            return invalid(published, "字段快照制品包校验失败");
        }
        if (!same(decoded.getArtifactDigest(), artifact.getArtifactDigest())
                || !same(decoded.getPackageDigest(), artifact.getPackageDigest())
                || !same(decoded.getArtifactDigest(), published.getArtifactDigest())) {
            return invalid(published, "字段快照制品摘要不一致");
        }

        DecisionArtifactPackage artifactPackage = decoded.getArtifactPackage();
        if (!metadataId(artifactPackage.getMetadata(), "definitionId",
                published.getDefinitionId())
                || !metadataId(artifactPackage.getMetadata(), "revisionId",
                published.getRevisionId())) {
            return invalid(published, "制品清单与已发布规则的 definitionId/revisionId 不一致");
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

    private boolean metadataId(Map<String, Object> metadata, String key, Long expected) {
        Object value = metadata.get(key);
        if (!(value instanceof Number number) || expected == null) {
            return false;
        }
        try {
            return new BigDecimal(number.toString()).longValueExact()
                    == expected.longValue();
        } catch (ArithmeticException | NumberFormatException ignored) {
            return false;
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
