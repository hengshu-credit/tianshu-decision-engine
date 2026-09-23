package com.hengshucredit.rule.server.artifact;

import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleModelVersion;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.service.RuleFieldAnalyzer;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class RuleDependencyClosureServiceTest {
    @Test
    public void modelInputRuntimeVariableAndFunctionAreFrozenBeyondPublicInputProjection() {
        FixtureService service = new FixtureService();
        service.revision.setModelJson("{\"kind\":\"REFERENCE\",\"refType\":\"MODEL\",\"refId\":11,\"code\":\"risk\"}");
        service.models.put(11L, model(11L, 3, 1, digest('a')));
        service.versions.put("11:3", version(11L, 3, digest('a')));
        var input = new com.hengshucredit.rule.model.entity.RuleModelInputField();
        input.setSourceOperand("{\"kind\":\"FUNCTION\",\"functionId\":13,\"args\":[{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":7,\"code\":\"apiScore\"}]}");
        service.modelInputs = List.of(input);
        RuleVariable api = variable(7L, 1);
        api.setVarSource("API"); api.setSourceConfig("{\"apiConfigId\":21}");
        service.variables.put(7L, api);
        service.functions.put(13L, function(13L, 1, "SCRIPT"));
        var closure = service.resolve(100L, 200L);
        Assert.assertFalse(closure.getIssues().toString(), closure.hasErrors());
        var ids = closure.getDependencies().stream().map(ArtifactDependency::getComponentId).toList();
        Assert.assertTrue(ids.toString(), ids.containsAll(List.of("VARIABLE:7", "FUNCTION:13", "BINDING:EXTERNAL_API:21")));
    }

    @Test
    public void listPathFreezesItsUpstreamEvenWhenPublicInputsOnlyContainRawFields() {
        FixtureService service = new FixtureService();
        service.revision.setModelJson("{\"kind\":\"PATH\",\"refType\":\"VARIABLE\",\"refId\":7,\"value\":\"listHit\"}");
        RuleVariable list = variable(7L, 1);
        list.setVarSource("LIST");
        list.setSourceConfig("{\"listIds\":[9],\"queryOperands\":[{\"kind\":\"PATH\","
                + "\"refType\":\"VARIABLE\",\"refId\":8,\"value\":\"oldMobile\"}]}");
        service.variables.put(7L, list);
        service.variables.put(8L, variable(8L, 1));

        var closure = service.resolve(100L, 200L);

        Assert.assertFalse(closure.getIssues().toString(), closure.hasErrors());
        Assert.assertTrue(closure.getDependencies().stream().anyMatch(value -> "VARIABLE:7".equals(value.getComponentId())));
        Assert.assertTrue(closure.getDependencies().stream().anyMatch(value -> "VARIABLE:8".equals(value.getComponentId())));
    }

    @Test
    public void scriptFunctionPickerReferenceFreezesFunctionById() {
        FixtureService service = new FixtureService();
        service.revision.setModelJson("{\"script\":\"return picked();\",\"scriptVarRefs\":["
                + "{\"refCode\":\"picked\",\"varId\":13,\"refType\":\"FUNCTION\"}]}");
        service.functions.put(13L, function(13L, 1, "JAVA"));
        var closure = service.resolve(100L, 200L);
        Assert.assertFalse(closure.getIssues().toString(), closure.hasErrors());
        Assert.assertEquals(List.of("FUNCTION:13"), closure.getDependencies().stream()
                .map(ArtifactDependency::getComponentId).toList());
    }

    @Test
    public void resolvesExplicitIdsAndPinsModelVersionAndDigest() {
        FixtureService service = new FixtureService();
        service.revision.setModelJson("{\"kind\":\"FUNCTION\",\"functionId\":13,"
                + "\"functionCode\":\"DisplayOnly\",\"args\":[]}");
        service.inputs = List.of(field("VARIABLE", 7L), field("MODEL", 11L));
        service.variables.put(7L, variable(7L, 1));
        service.models.put(11L, model(11L, 3, 1, digest('a')));
        service.versions.put("11:3", version(11L, 3, digest('a')));
        service.functions.put(13L, function(13L, 1, "SCRIPT"));

        RuleDependencyClosureService.DependencyClosure closure = service.resolve(100L, 200L);

        Assert.assertFalse(closure.hasErrors());
        Assert.assertEquals(List.of("FUNCTION:13", "MODEL:11:3", "VARIABLE:7"),
                closure.getDependencies().stream().map(ArtifactDependency::getComponentId).toList());
        ArtifactDependency model = closure.getDependencies().stream()
                .filter(dependency -> "MODEL".equals(dependency.getResourceType()))
                .findFirst().orElseThrow();
        Assert.assertEquals(Integer.valueOf(3), model.getVersion());
        Assert.assertEquals(digest('a'), model.getContentDigest());
        Assert.assertEquals(64, closure.getDependencyDigest().length());
    }

    @Test
    public void dataObjectFieldMappingFreezesReferencedVariableById() {
        FixtureService service = new FixtureService();
        service.revision.setModelJson("{}");
        service.inputs = Collections.singletonList(field("DATA_OBJECT", 30L));
        RuleDataObjectField objectField = new RuleDataObjectField();
        objectField.setId(30L);
        objectField.setProjectId(9L);
        objectField.setScope("PROJECT");
        objectField.setObjectId(20L);
        objectField.setVarCode("ageAlias");
        objectField.setScriptName("ageAlias");
        objectField.setVarType("NUMBER");
        objectField.setRefVariableId(7L);
        objectField.setStatus(1);
        service.dataObjectFields.put(30L, objectField);
        RuleDataObject object = new RuleDataObject();
        object.setId(20L);
        object.setLazyLoadReferences(true);
        object.setProjectId(9L);
        object.setScope("PROJECT");
        object.setStatus(1);
        service.dataObjects.put(20L, object);
        RuleVariable age = variable(7L, 1);
        age.setProjectId(9L);
        age.setScope("PROJECT");
        age.setScriptName("age");
        age.setVarType("NUMBER");
        age.setVarSource("INPUT");
        service.variables.put(7L, age);

        RuleDependencyClosureService.DependencyClosure closure =
                service.resolve(100L, 200L);

        Assert.assertFalse(closure.getIssues().toString(), closure.hasErrors());
        Assert.assertTrue(closure.getDependencies().stream()
                .anyMatch(value -> "DATA_OBJECT:30".equals(value.getComponentId())));
        Assert.assertTrue(closure.getDependencies().stream()
                .anyMatch(value -> "VARIABLE:7".equals(value.getComponentId())));
        ArtifactDependency frozenField = closure.getDependencies().stream()
                .filter(value -> "DATA_OBJECT:30".equals(value.getComponentId()))
                .findFirst().orElseThrow();
        RuleDataObjectField frozen = com.alibaba.fastjson.JSON.parseObject(
                new String(frozenField.getContent(), java.nio.charset.StandardCharsets.UTF_8),
                RuleDataObjectField.class);
        Assert.assertEquals(Long.valueOf(7L), frozen.getRefVariableId());
        Assert.assertEquals(Boolean.TRUE, frozen.getLazyReference());
    }

    @Test
    public void draftRootRuleIsNotRejectedAsItsOwnInactiveDependency() {
        FixtureService service = new FixtureService();
        service.definition.setStatus(0);
        service.revision.setModelJson("{}");

        RuleDependencyClosureService.DependencyClosure closure = service.resolve(100L, 200L);

        Assert.assertFalse(closure.getIssues().toString(), closure.hasErrors());
        Assert.assertFalse(closure.getIssues().stream().anyMatch(issue ->
                "INACTIVE_DEPENDENCY".equals(issue.getCode())
                        && "RULE".equals(issue.getResourceType())
                        && Long.valueOf(100L).equals(issue.getResourceId())));
    }

    @Test
    public void dataObjectConstantReferenceFreezesAsAConstantDependency() {
        FixtureService service = new FixtureService();
        service.revision.setModelJson("{}");
        service.inputs = List.of(field("DATA_OBJECT", 30L));
        RuleDataObjectField mapped = new RuleDataObjectField();
        mapped.setId(30L);
        mapped.setProjectId(9L);
        mapped.setScope("PROJECT");
        mapped.setVarType("NUMBER");
        mapped.setRefVariableId(7L);
        mapped.setStatus(1);
        service.dataObjectFields.put(30L, mapped);
        RuleVariable constant = variable(7L, 1);
        constant.setProjectId(9L);
        constant.setScope("PROJECT");
        constant.setVarSource("CONSTANT");
        constant.setDefaultValue("65");
        service.variables.put(7L, constant);

        var closure = service.resolve(100L, 200L);
        Assert.assertFalse(closure.getIssues().toString(), closure.hasErrors());
        Assert.assertTrue(closure.getDependencies().stream().anyMatch(value -> "CONSTANT:7".equals(value.getComponentId())));
    }

    @Test
    public void referencedInactiveRuleRemainsAHardDependencyError() {
        FixtureService service = new FixtureService();
        service.revision.setModelJson("{\"kind\":\"RULE_CALL\",\"ruleId\":101}");
        service.childDefinition = new RuleDefinition();
        service.childDefinition.setId(101L);
        service.childDefinition.setProjectId(9L);
        service.childDefinition.setStatus(0);

        RuleDependencyClosureService.DependencyClosure closure = service.resolve(100L, 200L);

        Assert.assertTrue(closure.hasErrors());
        Assert.assertTrue(closure.getIssues().stream().anyMatch(issue ->
                "INACTIVE_DEPENDENCY".equals(issue.getCode())
                        && "RULE".equals(issue.getResourceType())
                        && Long.valueOf(101L).equals(issue.getResourceId())));
    }

    @Test
    public void displayCodeWithoutIdIsRejectedAndNeverResolvedByName() {
        FixtureService service = new FixtureService();
        service.revision.setModelJson("{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\","
                + "\"code\":\"customerId\"}");

        RuleDependencyClosureService.DependencyClosure closure = service.resolve(
                100L, 200L, new RuleFieldAnalyzer.ResolvedFields(
                        Collections.emptyList(), Collections.emptyList()));

        Assert.assertTrue(closure.hasErrors());
        Assert.assertTrue(closure.getIssues().stream()
                .anyMatch(issue -> "MISSING_REFERENCE_ID".equals(issue.getCode())));
        Assert.assertEquals(0, service.variableLoadCount);
    }

    @Test
    public void recursiveRuleAndInactiveDependencyAreHardErrors() {
        FixtureService service = new FixtureService();
        service.revision.setModelJson("{\"kind\":\"RULE_CALL\",\"ruleId\":100}");
        service.inputs = Collections.singletonList(field("VARIABLE", 7L));
        service.variables.put(7L, variable(7L, 0));

        RuleDependencyClosureService.DependencyClosure closure = service.resolve(100L, 200L);

        Assert.assertTrue(closure.hasErrors());
        Assert.assertTrue(closure.getIssues().stream()
                .anyMatch(issue -> "RULE_DEPENDENCY_CYCLE".equals(issue.getCode())));
        Assert.assertTrue(closure.getIssues().stream()
                .anyMatch(issue -> "INACTIVE_DEPENDENCY".equals(issue.getCode())));
    }

    @Test
    public void collectsStableIdsFromAllDesignerPersistenceShapes() {
        FixtureService service = new FixtureService();
        service.revision.setModelJson("{\"dimensions\":[{\"_varId\":7,\"_refType\":\"VARIABLE\"," 
                + "\"varCode\":\"dimensionValue\"}],\"nodes\":[{\"leftVar\":\"leftValue\"," 
                + "\"leftVarId\":8,\"leftRefType\":\"VARIABLE\"}],"
                + "\"scriptVarRefs\":[{\"refCode\":\"scriptValue\",\"varId\":9,"
                + "\"refType\":\"VARIABLE\"}],\"function\":{\"functionId\":13,"
                + "\"functionCode\":\"frozenFunction\"},\"model\":{\"modelId\":11}} ");
        service.variables.put(7L, variable(7L, 1));
        service.variables.put(8L, variable(8L, 1));
        service.variables.put(9L, variable(9L, 1));
        service.functions.put(13L, function(13L, 1, "SCRIPT"));
        service.models.put(11L, model(11L, 3, 1, digest('b')));
        service.versions.put("11:3", version(11L, 3, digest('b')));

        RuleDependencyClosureService.DependencyClosure closure = service.resolve(100L, 200L);

        Assert.assertFalse(closure.getIssues().toString(), closure.hasErrors());
        Assert.assertEquals(List.of("FUNCTION:13", "MODEL:11:3", "VARIABLE:7", "VARIABLE:8", "VARIABLE:9"),
                closure.getDependencies().stream().map(ArtifactDependency::getComponentId).toList());
    }

    @Test
    public void createsOneExplicitBindingForEachActualExternalResourceId() {
        FixtureService service = new FixtureService();
        service.revision.setModelJson("{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\","
                + "\"refId\":7,\"code\":\"dbValue\"}");
        RuleVariable variable = variable(7L, 1);
        variable.setVarSource("DB");
        variable.setSourceConfig("{\"datasourceId\":99}");
        service.variables.put(7L, variable);

        RuleDependencyClosureService.DependencyClosure closure = service.resolve(100L, 200L);

        Assert.assertFalse(closure.getIssues().toString(), closure.hasErrors());
        ArtifactDependency binding = closure.getDependencies().stream()
                .filter(value -> "BINDING:DB_DATASOURCE:99".equals(value.getComponentId()))
                .findFirst().orElseThrow();
        Assert.assertEquals("DB_DATASOURCE", binding.getMetadata().get("targetResourceType"));
        Assert.assertEquals(99L, ((Number) binding.getMetadata()
                .get("sourceResourceId")).longValue());
    }

    @Test
    public void nestedRulePinsPublishedProjectionInsteadOfCurrentDraft() {
        FixtureService service = new FixtureService();
        service.revision.setModelJson("{\"kind\":\"RULE_CALL\",\"ruleId\":101,"
                + "\"ruleCode\":\"CHILD\"}");
        service.childDefinition = new RuleDefinition();
        service.childDefinition.setId(101L);
        service.childDefinition.setProjectId(9L);
        service.childDefinition.setRuleCode("CHILD");
        service.childDefinition.setRuleName("子规则");
        service.childDefinition.setModelType("SCRIPT");
        service.childDefinition.setStatus(1);
        service.childContent = new RuleDefinitionContent();
        service.childContent.setDefinitionId(101L);
        service.childContent.setModelJson("{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\","
                + "\"refId\":8,\"code\":\"draftValue\"}");
        service.childPublished = new RulePublished();
        service.childPublished.setDefinitionId(101L);
        service.childPublished.setRuleCode("CHILD");
        service.childPublished.setVersion(2);
        service.childPublished.setStatus(1);
        service.childPublished.setModelType("SCRIPT");
        service.childPublished.setModelJson("{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\","
                + "\"refId\":7,\"code\":\"publishedValue\"}");
        service.childPublished.setCompiledScript("publishedValue");
        service.childPublishedFields = new RuleFieldAnalyzer.ResolvedFields(
                Collections.singletonList(field("VARIABLE", 7L)),
                Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                Collections.emptyMap(), Collections.emptyMap(), "SCRIPT");
        service.inputs = Collections.singletonList(field("VARIABLE", 8L));
        service.variables.put(7L, variable(7L, 1));
        service.variables.put(8L, variable(8L, 1));

        RuleDependencyClosureService.DependencyClosure closure = service.resolve(
                100L, 200L, new RuleFieldAnalyzer.ResolvedFields(
                        Collections.emptyList(), Collections.emptyList()));

        Assert.assertFalse(closure.getIssues().toString(), closure.hasErrors());
        Assert.assertTrue(closure.getDependencies().stream()
                .anyMatch(value -> "VARIABLE:7".equals(value.getComponentId())));
        Assert.assertFalse(closure.getDependencies().stream()
                .anyMatch(value -> "VARIABLE:8".equals(value.getComponentId())));
        Assert.assertEquals("发布生命周期不得查询子规则当前字段投影", 0,
                service.inputFieldLoadCount);
        ArtifactDependency child = closure.getDependencies().stream()
                .filter(value -> "RULE:101:2".equals(value.getComponentId()))
                .findFirst().orElseThrow();
        Assert.assertTrue(new String(child.getContent(), java.nio.charset.StandardCharsets.UTF_8)
                .contains("publishedValue"));
        Assert.assertTrue(new String(child.getContent(), java.nio.charset.StandardCharsets.UTF_8)
                .contains("\"modelType\":\"SCRIPT\""));

        service.inputs = Collections.singletonList(field("VARIABLE", 9L));
        service.childDefinition.setRuleName("renamed current definition");
        service.childDefinition.setModelType("FLOW");
        RuleDependencyClosureService.DependencyClosure afterProjectionChange = service.resolve(
                100L, 200L, new RuleFieldAnalyzer.ResolvedFields(
                        Collections.emptyList(), Collections.emptyList()));
        Assert.assertEquals(closure.getDependencyDigest(),
                afterProjectionChange.getDependencyDigest());
        ArtifactDependency unchangedChild = afterProjectionChange.getDependencies().stream()
                .filter(value -> "RULE:101:2".equals(value.getComponentId()))
                .findFirst().orElseThrow();
        Assert.assertArrayEquals(child.getContent(), unchangedChild.getContent());
        Assert.assertEquals(0, service.inputFieldLoadCount);
    }

    @Test
    public void localQlResultFieldWithoutReferenceIsNotDependency() {
        FixtureService service = new FixtureService();
        RuleFieldAnalyzer.ResolvedFields fields =
                resolvedWithLocalOutput("credit_score_v1");

        RuleDependencyClosureService.DependencyClosure closure =
                service.resolve(100L, 200L, fields);

        Assert.assertFalse(closure.getIssues().stream()
                .anyMatch(issue -> "MISSING_REFERENCE_ID".equals(issue.getCode())));
    }

    @Test
    public void missingFrozenChildFieldSnapshotBlocksClosure() {
        FixtureService service = new FixtureService();
        service.revision.setModelJson("{\"kind\":\"RULE_CALL\",\"ruleId\":101}");
        service.childDefinition = new RuleDefinition();
        service.childDefinition.setId(101L);
        service.childDefinition.setProjectId(9L);
        service.childDefinition.setStatus(1);
        service.childPublished = new RulePublished();
        service.childPublished.setDefinitionId(101L);
        service.childPublished.setRevisionId(301L);
        service.childPublished.setVersion(2);
        service.childPublished.setStatus(1);
        service.childPublishedFields = new RuleFieldAnalyzer.ResolvedFields(
                Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(com.hengshucredit.rule.model.dto.RuleValidationIssue.error(
                        "FROZEN_REVISION_FIELD_SNAPSHOT_MISSING", "$.ruleFields",
                        "RULE", 101L, "字段快照缺失").withRevisionId(301L)),
                Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap());

        RuleDependencyClosureService.DependencyClosure closure = service.resolve(
                100L, 200L, new RuleFieldAnalyzer.ResolvedFields(
                        Collections.emptyList(), Collections.emptyList()));

        Assert.assertTrue(closure.hasErrors());
        Assert.assertTrue(closure.getIssues().stream().anyMatch(issue ->
                "FROZEN_REVISION_FIELD_SNAPSHOT_MISSING".equals(issue.getCode())));
        Assert.assertFalse(closure.getDependencies().stream().anyMatch(dependency ->
                dependency.getComponentId().startsWith("RULE:101:")));
        Assert.assertEquals(0, service.inputFieldLoadCount);
    }

    @Test
    public void structuredOutputWithoutReferenceStillRequiresStableId() {
        FixtureService service = new FixtureService();
        RuleFieldAnalyzer.ResolvedFields fields = new RuleFieldAnalyzer.ResolvedFields(
                Collections.emptyList(), Collections.singletonList(output("credit_score_v1")));

        RuleDependencyClosureService.DependencyClosure closure =
                service.resolve(100L, 200L, fields);

        Assert.assertTrue(closure.getIssues().stream()
                .anyMatch(issue -> "MISSING_REFERENCE_ID".equals(issue.getCode())));
    }

    private static RuleFieldAnalyzer.ResolvedFields resolvedWithLocalOutput(String name) {
        return new RuleFieldAnalyzer.ResolvedFields(
                Collections.emptyList(), Collections.singletonList(output(name)),
                Collections.emptyList(), new LinkedHashSet<>(Collections.singletonList(name)),
                Collections.emptyMap(), Collections.emptyMap());
    }

    private static RuleDefinitionOutputField output(String name) {
        RuleDefinitionOutputField field = new RuleDefinitionOutputField();
        field.setFieldName(name);
        field.setScriptName(name);
        field.setFieldType("NUMBER");
        field.setStatus(1);
        return field;
    }

    private static RuleDefinitionInputField field(String refType, Long refId) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setRefType(refType);
        field.setVarId(refId);
        field.setFieldName(refType + refId);
        field.setStatus(1);
        return field;
    }

    private static RuleVariable variable(Long id, int status) {
        RuleVariable variable = new RuleVariable();
        variable.setId(id);
        variable.setStatus(status);
        variable.setVarCode("displayOnly");
        return variable;
    }

    private static RuleModel model(Long id, int version, int status, String digest) {
        RuleModel model = new RuleModel();
        model.setId(id);
        model.setCurrentVersion(version);
        model.setStatus(status);
        model.setModelFormat("PMML");
        model.setModelDigest(digest);
        model.setModelContent("model-content");
        return model;
    }

    private static RuleModelVersion version(Long id, int version, String digest) {
        RuleModelVersion snapshot = new RuleModelVersion();
        snapshot.setModelId(id);
        snapshot.setVersion(version);
        snapshot.setModelFormat("PMML");
        snapshot.setModelDigest(digest);
        snapshot.setModelContent("version-content");
        snapshot.setStatus(1);
        return snapshot;
    }

    private static RuleFunction function(Long id, int status, String implType) {
        RuleFunction function = new RuleFunction();
        function.setId(id);
        function.setStatus(status);
        function.setImplType(implType);
        function.setImplScript("return 1;");
        return function;
    }

    private static String digest(char value) {
        return String.valueOf(value).repeat(64);
    }

    private static final class FixtureService extends RuleDependencyClosureService {
        private final RuleDefinition definition = new RuleDefinition();
        private final RuleRevision revision = new RuleRevision();
        private List<RuleDefinitionInputField> inputs = Collections.emptyList();
        private final Map<Long, RuleVariable> variables = new HashMap<>();
        private final Map<Long, RuleModel> models = new HashMap<>();
        private final Map<String, RuleModelVersion> versions = new HashMap<>();
        private final Map<Long, RuleFunction> functions = new HashMap<>();
        private final Map<Long, RuleDataObjectField> dataObjectFields = new HashMap<>();
        private final Map<Long, RuleDataObject> dataObjects = new HashMap<>();
        private List<com.hengshucredit.rule.model.entity.RuleModelInputField> modelInputs = List.of();
        private int variableLoadCount;
        private int inputFieldLoadCount;
        private RuleDefinition childDefinition;
        private RuleDefinitionContent childContent;
        private RulePublished childPublished;
        private RuleFieldAnalyzer.ResolvedFields childPublishedFields;

        private FixtureService() {
            definition.setId(100L);
            definition.setProjectId(9L);
            definition.setStatus(1);
            revision.setId(200L);
            revision.setDefinitionId(100L);
        }

        @Override
        protected RuleDefinition loadDefinition(Long definitionId) {
            return Long.valueOf(100L).equals(definitionId) ? definition
                    : Long.valueOf(101L).equals(definitionId) ? childDefinition : null;
        }

        @Override
        protected RuleRevision loadRevision(Long revisionId) {
            return revision;
        }

        @Override
        protected RuleDefinitionContent loadContent(Long definitionId) {
            return Long.valueOf(101L).equals(definitionId) ? childContent : null;
        }

        @Override
        protected RulePublished loadPublishedRule(Long definitionId) {
            return Long.valueOf(101L).equals(definitionId) ? childPublished : null;
        }

        @Override
        protected RuleFieldAnalyzer.ResolvedFields resolvePublishedFields(RulePublished published) {
            return childPublishedFields;
        }

        @Override
        protected List<RuleDefinitionInputField> loadInputFields(Long definitionId) {
            inputFieldLoadCount++;
            return inputs;
        }

        @Override
        protected List<RuleDefinitionOutputField> loadOutputFields(Long definitionId) {
            return Collections.emptyList();
        }

        @Override
        protected RuleVariable loadVariable(Long variableId) {
            variableLoadCount++;
            return variables.get(variableId);
        }

        @Override protected List<com.hengshucredit.rule.model.entity.RuleModelInputField> loadModelInputFields(Long modelId) { return modelInputs; }

        @Override
        protected RuleModel loadModel(Long modelId) {
            return models.get(modelId);
        }

        @Override
        protected RuleModelVersion loadModelVersion(Long modelId, Integer version) {
            return versions.get(modelId + ":" + version);
        }

        @Override
        protected RuleModelOutputField loadModelOutputField(Long outputFieldId) {
            return null;
        }

        @Override
        protected RuleFunction loadFunction(Long functionId) {
            return functions.get(functionId);
        }

        @Override
        protected RuleDataObjectField loadDataObjectField(Long fieldId) {
            return dataObjectFields.get(fieldId);
        }

        @Override
        protected RuleDataObject loadDataObject(Long objectId) {
            return dataObjects.get(objectId);
        }
    }
}
