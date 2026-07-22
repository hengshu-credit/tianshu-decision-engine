package com.hengshucredit.rule.server.artifact;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.RulePreflightReport;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.DecisionArtifactComponent;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.mapper.DecisionArtifactComponentMapper;
import com.hengshucredit.rule.server.mapper.DecisionArtifactMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import com.hengshucredit.rule.server.service.FunctionRegistrar;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DecisionArtifactService {
    @Resource
    private RuleRevisionMapper revisionMapper;
    @Resource
    private DecisionArtifactMapper artifactMapper;
    @Resource
    private DecisionArtifactComponentMapper componentMapper;
    @Resource
    private RulePreflightValidationService preflightService;
    @Resource
    private RuleDependencyClosureService dependencyClosureService;
    @Resource
    private FunctionRegistrar functionRegistrar;
    @Resource
    private RuleDefinitionService definitionService;

    private final DecisionArtifactPackageCodec codec = new DecisionArtifactPackageCodec();

    @Transactional
    public DecisionArtifact buildApprovedArtifact(Long revisionId, String actor) {
        RuleRevision revision = loadRevision(revisionId);
        if (revision == null) throw new IllegalArgumentException("规则修订不存在");
        if (!"REVIEW".equals(revision.getState()) && !"APPROVED".equals(revision.getState())) {
            throw new IllegalStateException("仅 REVIEW 修订可生成审批制品");
        }
        RulePreflightReport report = preflight(revisionId);
        if (!report.isValid()) {
            throw new IllegalStateException("发布前验证未通过: "
                    + (report.getErrors().isEmpty() ? "未知错误" : report.getErrors().get(0).getMessage()));
        }
        if (report.isBreakingChangeReasonRequired()) {
            throw new IllegalStateException("破坏性 Schema 变更必须填写原因");
        }
        RuleDependencyClosureService.DependencyClosure closure =
                resolveDependencies(revision.getDefinitionId(), revisionId);
        if (closure.hasErrors()) {
            throw new IllegalStateException("依赖闭包校验未通过");
        }

        DecisionArtifactPackage artifactPackage = new DecisionArtifactPackage();
        artifactPackage.putMetadata("definitionId", revision.getDefinitionId());
        artifactPackage.putMetadata("revisionId", revision.getId());
        RuleDefinition definition = definitionService == null
                ? null : definitionService.getById(revision.getDefinitionId());
        if (definition != null && definition.getModelType() != null) {
            artifactPackage.putMetadata("modelType", definition.getModelType());
        }
        artifactPackage.putMetadata("contentDigest", report.getContentDigest());
        artifactPackage.putMetadata("dependencyDigest", closure.getDependencyDigest());
        artifactPackage.putMetadata("javaMajor", 17);
        artifactPackage.putMetadata("qlExpressVersion", "4.1.0");
        artifactPackage.putMetadata("jpmmlVersion", "1.7.7");
        artifactPackage.putMetadata("onnxRuntimeVersion", "1.26.0");
        addText(artifactPackage, "rule/model.json", "application/json", revision.getModelJson());
        addText(artifactPackage, "rule/compiled.ql", "text/plain", report.getCompiledScript());
        addText(artifactPackage, "runtime/compiled.ql", "text/plain",
                buildRuntimeScript(report.getCompiledScript(), closure.getDependencies()));
        addText(artifactPackage, "schemas/input.schema.json", "application/schema+json",
                report.getInputSchemaJson());
        addText(artifactPackage, "schemas/output.schema.json", "application/schema+json",
                report.getOutputSchemaJson());
        List<RuleDefinitionInputField> inputFields = loadInputFields(revision.getDefinitionId());
        if (!inputFields.isEmpty()) {
            addText(artifactPackage, "rule/input-fields.json", "application/json",
                    CanonicalJson.write(JSON.parse(JSON.toJSONString(inputFields))));
        }
        List<RuleDefinitionOutputField> outputFields = loadOutputFields(revision.getDefinitionId());
        if (!outputFields.isEmpty()) {
            addText(artifactPackage, "rule/output-fields.json", "application/json",
                    CanonicalJson.write(JSON.parse(JSON.toJSONString(outputFields))));
        }
        if (revision.getOpenApiConfigJson() != null && !revision.getOpenApiConfigJson().isBlank()) {
            addText(artifactPackage, "rule/open-api.json", "application/json",
                    revision.getOpenApiConfigJson());
        }
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("valid", report.isValid());
        validation.put("errors", report.getErrors());
        validation.put("warnings", report.getWarnings());
        validation.put("schemaCompatibility", CanonicalJson.readMap(report.getSchemaCompatibilityJson()));
        addText(artifactPackage, "validation/report.json", "application/json",
                CanonicalJson.write(validation));
        for (ArtifactDependency dependency : closure.getDependencies()) {
            Map<String, Object> portableMetadata = new LinkedHashMap<>(dependency.getMetadata());
            portableMetadata.put("componentId", dependency.getComponentId());
            portableMetadata.put("resourceType", dependency.getResourceType());
            portableMetadata.put("resourceId", dependency.getResourceId());
            portableMetadata.put("version", dependency.getVersion());
            portableMetadata.put("embeddingMode", dependency.getEmbeddingMode());
            if ("EXPLICIT_BINDING".equals(dependency.getEmbeddingMode())) {
                portableMetadata.putAll(CanonicalJson.readMap(dependency.getContent()));
            }
            artifactPackage.addComponent(dependency.getComponentPath(), dependency.getMediaType(),
                    dependency.getContent(), portableMetadata);
        }

        byte[] packageContent = codec.encode(artifactPackage);
        DecisionArtifactPackageCodec.DecodedPackage decoded = codec.decode(packageContent);
        DecisionArtifact existing = findByDigest(decoded.getArtifactDigest());
        if (existing != null) {
            if (!decoded.getPackageDigest().equals(existing.getPackageDigest())) {
                throw new IllegalStateException("相同制品摘要对应不同包内容");
            }
            return existing;
        }

        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setDefinitionId(revision.getDefinitionId());
        artifact.setRevisionId(revisionId);
        artifact.setArtifactDigest(decoded.getArtifactDigest());
        artifact.setPackageDigest(decoded.getPackageDigest());
        artifact.setFormatVersion(DecisionArtifactPackage.FORMAT_VERSION);
        artifact.setManifestJson(buildManifestJson(artifactPackage, decoded.getArtifactDigest()));
        artifact.setValidationReportJson(CanonicalJson.write(validation));
        artifact.setRuntimeConstraintsJson(runtimeConstraints(closure.getDependencies()));
        artifact.setPackageContent(packageContent);
        artifact.setPackageSize((long) packageContent.length);
        artifact.setCreateBy(actor);
        artifact.setCreateTime(LocalDateTime.now());
        insertArtifact(artifact);

        Map<String, ArtifactDependency> byPath = new HashMap<>();
        for (ArtifactDependency dependency : closure.getDependencies()) {
            byPath.put(dependency.getComponentPath(), dependency);
        }
        for (DecisionArtifactPackage.Component component : artifactPackage.getComponents().values()) {
            ArtifactDependency dependency = byPath.get(component.getPath());
            DecisionArtifactComponent record = new DecisionArtifactComponent();
            record.setArtifactId(artifact.getId());
            record.setComponentId(dependency == null ? "RULE_COMPONENT:" + component.getPath()
                    : dependency.getComponentId());
            record.setComponentType(dependency == null ? "RULE" : dependency.getResourceType());
            record.setSourceType(dependency == null ? "RULE_REVISION" : dependency.getResourceType());
            record.setSourceId(dependency == null ? revision.getId() : dependency.getResourceId());
            record.setSourceVersion(dependency == null ? revision.getRevisionNo() : dependency.getVersion());
            record.setPackagePath(component.getPath());
            record.setMediaType(component.getMediaType());
            record.setContentDigest(Sha256Digests.bytes(component.getContent()));
            record.setContentSize((long) component.getContent().length);
            record.setMetadataJson(dependency == null ? null : CanonicalJson.write(dependency.getMetadata()));
            record.setCreateTime(LocalDateTime.now());
            insertComponent(record);
        }
        return artifact;
    }

    public DecisionArtifact getById(Long artifactId) {
        return artifactMapper.selectById(artifactId);
    }

    public RuntimeProjection loadRuntimeProjection(Long artifactId) {
        DecisionArtifact artifact = getById(artifactId);
        if (artifact == null) throw new IllegalArgumentException("决策制品不存在");
        DecisionArtifactPackage decoded = codec.decode(artifact.getPackageContent()).getArtifactPackage();
        return new RuntimeProjection(text(decoded, "rule/model.json"),
                text(decoded, "runtime/compiled.ql"), text(decoded, "rule/open-api.json"),
                text(decoded, "schemas/input.schema.json"),
                text(decoded, "schemas/output.schema.json"));
    }

    private String text(DecisionArtifactPackage artifactPackage, String path) {
        DecisionArtifactPackage.Component component = artifactPackage.getComponent(path);
        return component == null ? null : new String(component.getContent(), StandardCharsets.UTF_8);
    }

    private void addText(DecisionArtifactPackage artifactPackage, String path,
                         String mediaType, String value) {
        artifactPackage.addComponent(path, mediaType,
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private String buildRuntimeScript(String compiledScript, List<ArtifactDependency> dependencies) {
        if (functionRegistrar == null) return compiledScript;
        List<RuleFunction> functions = new ArrayList<>();
        for (ArtifactDependency dependency : dependencies) {
            if (!"FUNCTION".equals(dependency.getResourceType())
                    || !"EMBEDDED".equals(dependency.getEmbeddingMode())) continue;
            Map<String, Object> source = CanonicalJson.readMap(dependency.getContent());
            RuleFunction function = new RuleFunction();
            function.setId(dependency.getResourceId());
            function.setFuncCode(textValue(source.get("funcCode")));
            function.setParamsJson(textValue(source.get("paramsJson")));
            function.setReturnType(textValue(source.get("returnType")));
            function.setImplType(textValue(source.get("implType")));
            function.setImplScript(textValue(source.get("implScript")));
            functions.add(function);
        }
        String prefix = functionRegistrar.buildScriptFunctionPrefix(functions);
        return prefix == null || prefix.isEmpty() ? compiledScript : prefix + compiledScript;
    }

    private String textValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String runtimeConstraints(List<ArtifactDependency> dependencies) {
        List<Map<String, Object>> requirements = dependencies.stream()
                .filter(dependency -> !"EMBEDDED".equals(dependency.getEmbeddingMode()))
                .map(dependency -> Map.<String, Object>of(
                        "componentId", dependency.getComponentId(),
                        "mode", dependency.getEmbeddingMode()))
                .toList();
        return CanonicalJson.write(Map.of("requirements", requirements));
    }

    private String buildManifestJson(DecisionArtifactPackage artifactPackage, String artifactDigest) {
        List<Map<String, Object>> components = artifactPackage.getComponents().values().stream()
                .map(component -> Map.<String, Object>of(
                        "path", component.getPath(),
                        "mediaType", component.getMediaType(),
                        "size", component.getContent().length,
                        "digest", Sha256Digests.bytes(component.getContent())))
                .toList();
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("formatVersion", DecisionArtifactPackage.FORMAT_VERSION);
        manifest.put("artifactDigest", artifactDigest);
        manifest.put("metadata", artifactPackage.getMetadata());
        manifest.put("components", components);
        return CanonicalJson.write(manifest);
    }

    protected RuleRevision loadRevision(Long revisionId) {
        return revisionMapper.selectById(revisionId);
    }

    protected List<RuleDefinitionInputField> loadInputFields(Long definitionId) {
        return definitionService == null ? Collections.emptyList()
                : definitionService.listInputFields(definitionId);
    }

    protected List<RuleDefinitionOutputField> loadOutputFields(Long definitionId) {
        return definitionService == null ? Collections.emptyList()
                : definitionService.listOutputFields(definitionId);
    }

    protected RulePreflightReport preflight(Long revisionId) {
        return preflightService.validate(revisionId);
    }

    protected RuleDependencyClosureService.DependencyClosure resolveDependencies(
            Long definitionId, Long revisionId) {
        return dependencyClosureService.resolve(definitionId, revisionId);
    }

    protected DecisionArtifact findByDigest(String artifactDigest) {
        return artifactMapper.selectOne(new LambdaQueryWrapper<DecisionArtifact>()
                .eq(DecisionArtifact::getArtifactDigest, artifactDigest).last("LIMIT 1"));
    }

    protected void insertArtifact(DecisionArtifact artifact) {
        artifactMapper.insert(artifact);
    }

    protected void insertComponent(DecisionArtifactComponent component) {
        componentMapper.insert(component);
    }

    public static final class RuntimeProjection {
        private final String modelJson;
        private final String compiledScript;
        private final String openApiConfigJson;
        private final String inputSchemaJson;
        private final String outputSchemaJson;

        public RuntimeProjection(String modelJson, String compiledScript, String openApiConfigJson,
                                 String inputSchemaJson, String outputSchemaJson) {
            this.modelJson = modelJson;
            this.compiledScript = compiledScript;
            this.openApiConfigJson = openApiConfigJson;
            this.inputSchemaJson = inputSchemaJson;
            this.outputSchemaJson = outputSchemaJson;
        }

        public String getModelJson() {
            return modelJson;
        }

        public String getCompiledScript() {
            return compiledScript;
        }

        public String getOpenApiConfigJson() {
            return openApiConfigJson;
        }

        public String getInputSchemaJson() {
            return inputSchemaJson;
        }

        public String getOutputSchemaJson() {
            return outputSchemaJson;
        }
    }
}
