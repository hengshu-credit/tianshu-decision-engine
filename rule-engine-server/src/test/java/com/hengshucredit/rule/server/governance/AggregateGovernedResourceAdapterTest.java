package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleExperiment;
import com.hengshucredit.rule.model.entity.RuleExperimentGroup;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.model.entity.RuleVariableOption;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveResponse;
import com.hengshucredit.rule.model.dto.RuleDraftSourceRequest;
import com.hengshucredit.rule.model.dto.RuleLifecycleActionRequest;
import com.hengshucredit.rule.model.dto.RulePreflightReport;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldOptionMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleExperimentGroupMapper;
import com.hengshucredit.rule.server.mapper.RuleModelInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableOptionMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import com.hengshucredit.rule.server.service.RuleLifecycleService;
import com.hengshucredit.rule.server.service.RuleDraftService;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AggregateGovernedResourceAdapterTest {
    @Test
    public void modelMissingFieldBindingsIsRejectedBeforeSubmissionInsteadOfFailingDuringApproval() {
        var adapter = new ModelGovernedResourceAdapter(null, null, null, codec());
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "modelService", new com.hengshucredit.rule.server.service.RuleModelService());
        var model = new RuleModel(); model.setModelCode("sample"); model.setModelName("模型"); model.setModelType("LR"); model.setModelFormat("PMML");
        var input = new RuleModelInputField(); input.setFieldName("Age"); input.setFieldType("DOUBLE");
        var output = new com.hengshucredit.rule.model.entity.RuleModelOutputField(); output.setFieldName("prediction"); output.setFieldType("DOUBLE");
        model.setInputFields(List.of(input)); model.setOutputFields(List.of(output));
        var incomplete = ResourceSnapshot.ofJson(CanonicalJson.write(model));
        Assert.assertTrue(adapter.validate(incomplete, "CREATE").stream().anyMatch(issue -> "MODEL_PUBLICATION_INVALID".equals(issue.code())
                && issue.message().contains("Age") && issue.message().contains("prediction")));
        Assert.assertTrue("删除不要求补齐待删除内容的输入输出", adapter.validate(incomplete, "DELETE").isEmpty());
        input.setSourceOperand("{\"kind\":\"PATH\",\"value\":\"Age\"}");
        output.setTargetOperand("{\"kind\":\"PATH\",\"value\":\"Decision.score\"}");
        Assert.assertTrue(adapter.validate(ResourceSnapshot.ofJson(CanonicalJson.write(model)), "CREATE").isEmpty());
    }

    @Test
    public void deletingOrDisablingAnAggregateNeverRewritesItsChildConfiguration() {
        for (String action : List.of("DRAFT_DELETE", "DELETE", "DISABLE")) {
            java.util.concurrent.atomic.AtomicReference<RuleVariable> stored = new java.util.concurrent.atomic.AtomicReference<>(variable());
            var options = (RuleVariableOptionMapper) Proxy.newProxyInstance(RuleVariableOptionMapper.class.getClassLoader(),
                    new Class<?>[]{RuleVariableOptionMapper.class}, (proxy, method, args) -> {
                        throw new AssertionError("删除和停用不能重建选项或要求草稿子配置完整：" + method.getName());
                    });
            var adapter = new VariableGovernedResourceAdapter(new SimpleEntityGovernedResourceAdapter.EntityStore<RuleVariable>() {
                public RuleVariable load(Long id) { return stored.get(); }
                public void insert(RuleVariable value) { Assert.fail("不能新建根资源"); }
                public void update(RuleVariable value) { stored.set(value); }
            }, options, codec());
            adapter.apply(new ApprovalApplyContext(1L, 7L, 1, action,
                    ResourceSnapshot.ofJson("{\"id\":7,\"options\":[{\"id\":9,\"optionValue\":\"A\"}]}"), "owner", null));
            Assert.assertEquals(Integer.valueOf("DISABLE".equals(action) ? 0 : -1), stored.get().getStatus());
        }
    }

    @Test
    public void restoringLegacyObjectSnapshotExplicitlyRestoresDefaultLoadingPolicy() {
        var adapter = new DataObjectGovernedResourceAdapter(null, null, null, codec());
        var legacy = adapter.normalizeDraft(ResourceSnapshot.ofJson("{\"id\":4,\"fields\":[{\"id\":8,\"refVariableId\":7}]}"));
        var restored = CanonicalJson.readMap(legacy.snapshotJson());
        Assert.assertEquals(Boolean.FALSE, restored.get("lazyLoadReferences"));
        Assert.assertEquals("VALUE", ((Map<?, ?>) ((List<?>) restored.get("fields")).get(0)).get("referenceMode"));
        var configured = CanonicalJson.readMap(adapter.normalizeDraft(ResourceSnapshot.ofJson(
                "{\"lazyLoadReferences\":true,\"fields\":[{\"referenceMode\":\"STRUCTURE\"}]}")).snapshotJson());
        Assert.assertEquals(Boolean.TRUE, configured.get("lazyLoadReferences"));
        Assert.assertEquals("STRUCTURE", ((Map<?, ?>) ((List<?>) configured.get("fields")).get(0)).get("referenceMode"));
    }

    @Test
    public void variableVersionContainsAndAppliesOptions() {
        RuleVariable variable = variable();
        RuleVariableOption option = new RuleVariableOption();
        option.setId(9L);
        option.setVariableId(7L);
        option.setOptionValue("A");
        option.setOptionLabel("甲");
        List<Object> inserted = new ArrayList<>();
        RuleVariableOptionMapper optionMapper =
                mapper(RuleVariableOptionMapper.class,
                        Map.of("selectList", List.of(option)),
                        inserted);
        VariableGovernedResourceAdapter adapter =
                new VariableGovernedResourceAdapter(
                        store(variable, RuleVariable::setId),
                        optionMapper, codec());

        ResourceSnapshot snapshot = adapter.loadEffective(7L);
        Assert.assertEquals(1, ((List<?>) CanonicalJson
                .readMap(snapshot.snapshotJson())
                .get("options")).size());
        Assert.assertFalse(adapter.collectDependencies(snapshot).stream()
                .anyMatch(ref -> "VARIABLE".equals(
                        ref.targetResourceType())
                        && Long.valueOf(7L).equals(
                        ref.targetResourceId())));
        adapter.apply(new ApprovalApplyContext(
                1L, 7L, 2, "UPDATE",
                snapshot, "user", null));

        RuleVariableOption saved =
                (RuleVariableOption) inserted.get(0);
        Assert.assertEquals(Long.valueOf(7L),
                saved.getVariableId());
        Assert.assertNull(saved.getId());
    }

    @Test
    public void variableSourceFailureDoesNotBlockDisableOrDelete() {
        RuleVariable variable = variable();
        RuleVariableOptionMapper optionMapper =
                mapper(RuleVariableOptionMapper.class,
                        Map.of("selectList", List.of()),
                        new ArrayList<>());
        VariableSourceReferenceValidator sourceValidator =
                new VariableSourceReferenceValidator() {
                    @Override
                    public List<GovernanceIssue> validate(
                            RuleVariable ignored) {
                        return List.of(GovernanceIssue.error(
                                "VARIABLE_SOURCE_NOT_FOUND",
                                "来源已删除",
                                GovernanceResourceTypes.VARIABLE,
                                7L, "$.sourceConfig.apiConfigId"));
                    }
                };
        VariableGovernedResourceAdapter adapter =
                new VariableGovernedResourceAdapter(
                        store(variable, RuleVariable::setId),
                        optionMapper, codec(), sourceValidator);
        ResourceSnapshot snapshot = adapter.loadEffective(7L);

        Assert.assertFalse(adapter.validate(snapshot, "UPDATE").isEmpty());
        Assert.assertTrue(adapter.validate(snapshot, "DISABLE").isEmpty());
        Assert.assertTrue(adapter.validate(snapshot, "DELETE").isEmpty());
    }

    @Test
    public void dataObjectVersionContainsFieldsAndNestedOptions() {
        RuleDataObject object = new RuleDataObject();
        object.setId(4L);
        object.setObjectCode("request");
        object.setObjectLabel("请求");
        object.setObjectType("OBJECT");
        object.setStatus(1);
        RuleDataObjectField field = new RuleDataObjectField();
        field.setId(8L);
        field.setObjectId(4L);
        field.setVarCode("age");
        field.setVarLabel("年龄");
        field.setVarType("INTEGER");
        RuleDataObjectFieldMapper fieldMapper =
                mapper(RuleDataObjectFieldMapper.class,
                        Map.of("selectList", List.of(field)),
                        new ArrayList<>());
        RuleDataObjectFieldOptionMapper optionMapper =
                mapper(RuleDataObjectFieldOptionMapper.class,
                        Map.of("selectList", List.of()),
                        new ArrayList<>());
        DataObjectGovernedResourceAdapter adapter =
                new DataObjectGovernedResourceAdapter(
                        store(object, RuleDataObject::setId),
                        fieldMapper, optionMapper, codec());

        ResourceSnapshot version = adapter.loadEffective(4L);
        Map<String, Object> snapshot = CanonicalJson.readMap(
                version.snapshotJson());
        List<?> fields = (List<?>) snapshot.get("fields");

        Assert.assertEquals(1, fields.size());
        Assert.assertTrue(((Map<?, ?>) fields.get(0))
                .containsKey("options"));
        Assert.assertFalse(adapter.collectDependencies(version).stream()
                .anyMatch(ref -> "DATA_OBJECT".equals(
                        ref.targetResourceType())
                        && Long.valueOf(4L).equals(
                        ref.targetResourceId())));
    }

    @Test
    public void dataObjectFieldVariableReferenceIsValidatedAndCollected() {
        RuleDataObject object = dataObject(4L, "PROJECT", 2L);
        RuleDataObjectField field = dataObjectField(8L, 4L, "INTEGER", 7L);
        RuleVariable variable = referencedVariable(7L, "GLOBAL", 0L, "NUMBER", 1);
        DataObjectGovernedResourceAdapter adapter = dataObjectAdapter(
                object, field, variable);

        ResourceSnapshot snapshot = adapter.loadEffective(4L);

        Assert.assertTrue(adapter.validate(snapshot, "UPDATE").isEmpty());
        Assert.assertTrue(adapter.collectDependencies(snapshot).stream()
                .anyMatch(ref -> "VARIABLE".equals(ref.targetResourceType())
                        && Long.valueOf(7L).equals(ref.targetResourceId())));
    }

    @Test
    public void globalDataObjectRejectsProjectVariableReference() {
        RuleDataObject object = dataObject(4L, "GLOBAL", 0L);
        RuleDataObjectField field = dataObjectField(8L, 4L, "STRING", 7L);
        RuleVariable variable = referencedVariable(7L, "PROJECT", 2L, "STRING", 1);
        DataObjectGovernedResourceAdapter adapter = dataObjectAdapter(
                object, field, variable);

        List<GovernanceIssue> issues = adapter.validate(
                adapter.loadEffective(4L), "UPDATE");

        Assert.assertTrue(issues.stream().anyMatch(issue ->
                "DATA_OBJECT_FIELD_VARIABLE_SCOPE_MISMATCH".equals(issue.code())));
    }

    @Test
    public void dataObjectFieldMayReferenceATypeCompatibleConstant() {
        RuleDataObject object = dataObject(4L, "PROJECT", 2L);
        RuleDataObjectField field = dataObjectField(8L, 4L, "NUMBER", 7L);
        RuleVariable constant = referencedVariable(7L, "GLOBAL", 0L, "NUMBER", 1);
        constant.setVarSource("CONSTANT");
        constant.setDefaultValue("65");
        DataObjectGovernedResourceAdapter adapter = dataObjectAdapter(object, field, constant);

        Assert.assertTrue(adapter.validate(adapter.loadEffective(4L), "UPDATE").isEmpty());
    }

    @Test
    public void dataObjectFieldRejectsDisabledOrIncompatibleVariable() {
        RuleDataObject object = dataObject(4L, "PROJECT", 2L);
        RuleDataObjectField field = dataObjectField(8L, 4L, "STRING", 7L);
        RuleVariable variable = referencedVariable(7L, "PROJECT", 2L, "NUMBER", 0);
        DataObjectGovernedResourceAdapter adapter = dataObjectAdapter(
                object, field, variable);

        List<GovernanceIssue> issues = adapter.validate(
                adapter.loadEffective(4L), "UPDATE");

        Assert.assertTrue(issues.stream().anyMatch(issue ->
                "DATA_OBJECT_FIELD_VARIABLE_DISABLED".equals(issue.code())));
        variable.setStatus(1);
        issues = adapter.validate(adapter.loadEffective(4L), "UPDATE");
        Assert.assertTrue(issues.stream().anyMatch(issue ->
                "DATA_OBJECT_FIELD_VARIABLE_TYPE_MISMATCH".equals(issue.code())));
    }

    @Test
    public void listElementChildRejectsDirectVariableReference() {
        RuleDataObject object = dataObject(4L, "PROJECT", 2L);
        RuleDataObjectField list = dataObjectField(8L, 4L, "LIST", null);
        RuleDataObjectField child = dataObjectField(9L, 4L, "STRING", 7L);
        child.setParentFieldId(8L);
        RuleVariable variable = referencedVariable(7L, "PROJECT", 2L, "STRING", 1);
        DataObjectGovernedResourceAdapter adapter = dataObjectAdapter(
                object, List.of(list, child), variable);

        List<GovernanceIssue> issues = adapter.validate(
                adapter.loadEffective(4L), "UPDATE");

        Assert.assertTrue(issues.stream().anyMatch(issue ->
                "DATA_OBJECT_FIELD_LIST_CHILD_REFERENCE_UNSUPPORTED".equals(issue.code())));
    }

    private DataObjectGovernedResourceAdapter dataObjectAdapter(
            RuleDataObject object, RuleDataObjectField field,
            RuleVariable variable) {
        return dataObjectAdapter(object, List.of(field), variable);
    }

    private DataObjectGovernedResourceAdapter dataObjectAdapter(
            RuleDataObject object, List<RuleDataObjectField> fields,
            RuleVariable variable) {
        RuleDataObjectFieldMapper fieldMapper =
                mapper(RuleDataObjectFieldMapper.class,
                        Map.of("selectList", fields),
                        new ArrayList<>());
        RuleDataObjectFieldOptionMapper optionMapper =
                mapper(RuleDataObjectFieldOptionMapper.class,
                        Map.of("selectList", List.of()),
                        new ArrayList<>());
        RuleVariableMapper variableMapper =
                mapper(RuleVariableMapper.class,
                        Map.of("selectById", variable),
                        new ArrayList<>());
        return new DataObjectGovernedResourceAdapter(
                store(object, RuleDataObject::setId),
                fieldMapper, optionMapper, codec(), variableMapper);
    }

    private RuleDataObject dataObject(Long id, String scope, Long projectId) {
        RuleDataObject object = new RuleDataObject();
        object.setId(id);
        object.setObjectCode("request");
        object.setObjectLabel("请求");
        object.setScope(scope);
        object.setProjectId(projectId);
        object.setObjectType("INPUT");
        object.setStatus(1);
        return object;
    }

    private RuleDataObjectField dataObjectField(
            Long id, Long objectId, String type, Long refVariableId) {
        RuleDataObjectField field = new RuleDataObjectField();
        field.setId(id);
        field.setObjectId(objectId);
        field.setProjectId(2L);
        field.setScope("PROJECT");
        field.setVarCode("age");
        field.setVarLabel("年龄");
        field.setScriptName("age");
        field.setVarType(type);
        field.setRefVariableId(refVariableId);
        field.setStatus(1);
        return field;
    }

    private RuleVariable referencedVariable(
            Long id, String scope, Long projectId, String type, int status) {
        RuleVariable variable = new RuleVariable();
        variable.setId(id);
        variable.setScope(scope);
        variable.setProjectId(projectId);
        variable.setVarCode("age");
        variable.setVarLabel("年龄");
        variable.setScriptName("age");
        variable.setVarType(type);
        variable.setVarSource("INPUT");
        variable.setStatus(status);
        return variable;
    }

    @Test
    public void modelBinaryIsProtectedWhileInputContractIsVersioned() {
        RuleModel model = new RuleModel();
        model.setId(5L);
        model.setModelCode("credit");
        model.setModelName("授信模型");
        model.setModelType("XGBOOST");
        model.setModelFormat("ONNX");
        model.setModelContent("binary-content");
        model.setModelConfig("{\"executionProvider\":\"CPU\","
                + "\"testParams\":\"{}\","
                + "\"testParamsSource\":\"UPLOAD_SAMPLE\"}");
        model.setStatus(1);
        RuleModelInputField input = new RuleModelInputField();
        input.setId(10L);
        input.setModelId(5L);
        input.setFieldName("age");
        input.setFieldLabel("年龄");
        input.setFieldType("INTEGER");
        RuleModelInputFieldMapper inputMapper =
                mapper(RuleModelInputFieldMapper.class,
                        Map.of("selectList", List.of(input)),
                        new ArrayList<>());
        RuleModelOutputFieldMapper outputMapper =
                mapper(RuleModelOutputFieldMapper.class,
                        Map.of("selectList", List.of()),
                        new ArrayList<>());
        ModelGovernedResourceAdapter adapter =
                new ModelGovernedResourceAdapter(
                        store(model, RuleModel::setId),
                        inputMapper, outputMapper, codec());
        java.util.concurrent.atomic.AtomicInteger modelSnapshots = new java.util.concurrent.atomic.AtomicInteger();
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "modelService",
                new com.hengshucredit.rule.server.service.RuleModelService() {
                    @Override public Integer publishedBusinessVersion(Long id) { Assert.assertEquals(Long.valueOf(5), id); return 2; }
                    @Override public void publish(Long id, String summary, String actor) {
                        Assert.assertEquals(Long.valueOf(5L), id);
                        Assert.assertTrue(String.valueOf(CanonicalJson.readMap(adapter.loadEffective(id).snapshotJson())
                                .get("modelConfig")).contains("35"));
                        Assert.assertEquals(Long.valueOf(10L), input.getId());
                        modelSnapshots.incrementAndGet();
                    }
                });

        ResourceSnapshot snapshot = adapter.loadEffective(5L);
        Map<String, Object> publicValue =
                CanonicalJson.readMap(snapshot.snapshotJson());

        Assert.assertFalse(snapshot.snapshotJson()
                .contains("binary-content"));
        Assert.assertNotNull(snapshot.secretPayloadCiphertext());
        Assert.assertEquals(1,
                ((List<?>) publicValue.get("inputFields")).size());
        Assert.assertFalse(adapter.collectDependencies(snapshot).stream()
                .anyMatch(ref -> "MODEL".equals(
                        ref.targetResourceType())
                        && Long.valueOf(5L).equals(
                        ref.targetResourceId())));

        publicValue.put("modelConfig",
                "{\"executionProvider\":\"CPU\","
                        + "\"testParams\":\"{\\\"age\\\":35}\","
                        + "\"testParamsSource\":\"SAVED\"}");
        adapter.apply(new ApprovalApplyContext(
                18L, 5L, 2, "UPDATE",
                ResourceSnapshot.ofJson(CanonicalJson.write(publicValue)),
                "tester", null));
        Map<String, Object> applied = CanonicalJson.readMap(
                adapter.loadEffective(5L).snapshotJson());
        Assert.assertEquals("字段和配置生效后才创建模型版本", 1, modelSnapshots.get());

        Assert.assertEquals(
                "{\"executionProvider\":\"CPU\","
                        + "\"testParams\":\"{\\\"age\\\":35}\","
                        + "\"testParamsSource\":\"SAVED\"}",
                applied.get("modelConfig"));
    }

    @Test
    public void modelOverwriteUsesTargetBusinessLineInsteadOfSnapshotNativeRevision() {
        RuleModel model = new RuleModel(); model.setId(5L);
        ModelGovernedResourceAdapter adapter = new ModelGovernedResourceAdapter(store(model, RuleModel::setId),
                mapper(RuleModelInputFieldMapper.class, Map.of(), new ArrayList<>()),
                mapper(RuleModelOutputFieldMapper.class, Map.of(), new ArrayList<>()), codec());
        var source = new com.hengshucredit.rule.model.entity.GovernedResourceVersion();
        source.setId(90L); source.setResourceType("MODEL"); source.setResourceId(5L);
        source.setBusinessVersion(2); source.setSnapshotJson("{\"id\":5,\"currentVersion\":7}");
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "governanceVersions",
                mapper(com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper.class, Map.of("selectById", source), new ArrayList<>()));
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "modelService", new com.hengshucredit.rule.server.service.RuleModelService() {
            @Override public void publishReusingVersion(Long id, Integer version, String summary, String actor) { Assert.assertEquals(Integer.valueOf(2), version); }
            @Override public Integer publishedBusinessVersion(Long id) { return 2; }
        });
        AppliedResource result = adapter.afterAggregateApplied(new ApprovalApplyContext(1L, 5L, 2, "RESTORE",
                ResourceSnapshot.ofJson(source.getSnapshotJson()), "admin", 90L, "REUSE"), new AppliedResource(5L, 2, "ACTIVE", null));
        Assert.assertEquals(Integer.valueOf(2), result.versionNo());
    }

    @Test
    public void experimentGroupRuleIdsParticipateInPreflight() {
        RuleExperiment experiment = new RuleExperiment();
        experiment.setId(6L);
        experiment.setExperimentCode("routing");
        experiment.setExperimentName("路由实验");
        experiment.setRoutingMode("RATIO");
        experiment.setStatus(1);
        RuleExperimentGroup group = new RuleExperimentGroup();
        group.setId(12L);
        group.setExperimentId(6L);
        group.setGroupType("CHAMPION");
        group.setRuleId(77L);
        group.setTrafficRatio(new java.math.BigDecimal("100"));
        group.setStatus(1);
        RuleExperimentGroupMapper groupMapper =
                mapper(RuleExperimentGroupMapper.class,
                        Map.of("selectList", List.of(group)),
                        new ArrayList<>());
        ExperimentGovernedResourceAdapter adapter =
                new ExperimentGovernedResourceAdapter(
                        store(experiment, RuleExperiment::setId),
                        groupMapper, codec());
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "experimentService", new com.hengshucredit.rule.server.service.RuleExperimentService());

        ResourceSnapshot snapshot = adapter.loadEffective(6L);

        Assert.assertTrue(adapter.collectDependencies(snapshot).stream()
                .anyMatch(ref -> "RULE".equals(
                        ref.targetResourceType())
                        && Long.valueOf(77L).equals(
                        ref.targetResourceId())));
        Assert.assertFalse(adapter.collectDependencies(snapshot).stream()
                .anyMatch(ref -> "EXPERIMENT".equals(
                        ref.targetResourceType())
                        && Long.valueOf(6L).equals(
                        ref.targetResourceId())));
        Assert.assertTrue(adapter.validate(snapshot).isEmpty());
    }

    @Test
    public void experimentWithoutAnExecutionRuleCannotPassPublicationValidation() {
        var adapter = new ExperimentGovernedResourceAdapter(null, null, codec());
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "experimentService", new com.hengshucredit.rule.server.service.RuleExperimentService());
        var draft = ResourceSnapshot.ofJson("{\"experimentCode\":\"draft\",\"experimentName\":\"未完成配置\",\"routingMode\":\"RATIO\","
                + "\"groups\":[{\"groupCode\":\"champion\",\"groupType\":\"CHAMPION\",\"status\":1,\"trafficRatio\":100}]}");
        var errors = adapter.validate(draft);
        Assert.assertTrue(errors.stream().anyMatch(error -> "EXPERIMENT_RULE_REQUIRED".equals(error.code())
                && "$.groups[0].ruleId".equals(error.referencePath())));
    }

    @Test public void experimentApprovalUsesTheSameRatiosAsExecution() {
        var adapter = new ExperimentGovernedResourceAdapter(null, null, codec());
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "experimentService", new com.hengshucredit.rule.server.service.RuleExperimentService());
        for (String groups : List.of(
                "[{\"groupType\":\"CHAMPION\",\"ruleId\":7,\"status\":1,\"trafficRatio\":1}]",
                "[{\"groupType\":\"CHAMPION\",\"ruleId\":7,\"status\":1,\"trafficRatio\":100},{\"groupType\":\"TEST\",\"ruleId\":7,\"status\":1,\"trafficRatio\":90}]",
                "[{\"groupType\":\"CHAMPION\",\"ruleId\":7,\"status\":1,\"trafficRatio\":0.001},{\"groupType\":\"CHALLENGER\",\"ruleId\":7,\"status\":1,\"trafficRatio\":99.999}]")) {
            var snapshot = ResourceSnapshot.ofJson("{\"experimentCode\":\"ratio\",\"experimentName\":\"比例\",\"routingMode\":\"RATIO\",\"testRoutingMode\":\"RATIO\",\"groups\":" + groups + "}");
            Assert.assertTrue("比例有误不能通过审批: " + groups, adapter.validate(snapshot).stream().anyMatch(issue -> "EXPERIMENT_ROUTING_INVALID".equals(issue.code())));
        }
    }

    @Test
    public void ruleVersionContainsDesignerContentAndFieldContract() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(9L);
        definition.setRuleCode("R001");
        definition.setRuleName("准入规则");
        definition.setModelType("DECISION_TABLE");
        definition.setStatus(1);
        RuleDefinitionContent content = new RuleDefinitionContent();
        content.setDefinitionId(9L);
        content.setModelJson("{\"rows\":[{\"id\":1}]}");
        RuleDefinitionInputField input =
                new RuleDefinitionInputField();
        input.setDefinitionId(9L);
        input.setVarId(3L);
        input.setRefType("VARIABLE");
        RuleDefinitionContentMapper contentMapper =
                mapper(RuleDefinitionContentMapper.class,
                        Map.of("selectOne", content),
                        new ArrayList<>());
        RuleDefinitionInputFieldMapper inputMapper =
                mapper(RuleDefinitionInputFieldMapper.class,
                        Map.of("selectList", List.of(input)),
                        new ArrayList<>());
        RuleDefinitionOutputFieldMapper outputMapper =
                mapper(RuleDefinitionOutputFieldMapper.class,
                        Map.of("selectList", List.of()),
                        new ArrayList<>());
        RuleGovernedResourceAdapter adapter =
                new RuleGovernedResourceAdapter(
                        store(definition, RuleDefinition::setId),
                        codec(), new RuleLifecycleService(),
                        null,
                        contentMapper, inputMapper, outputMapper);

        ResourceSnapshot version = adapter.loadEffective(9L);
        Map<String, Object> snapshot = CanonicalJson.readMap(
                version.snapshotJson());

        Assert.assertTrue(CanonicalJson.write(snapshot.get("content"))
                .contains("rows"));
        Assert.assertEquals(1,
                ((List<?>) snapshot.get("inputFieldsJson")).size());
        Assert.assertFalse(adapter.collectDependencies(version).stream()
                .anyMatch(ref -> "RULE".equals(
                        ref.targetResourceType())
                        && Long.valueOf(9L).equals(
                        ref.targetResourceId())));
        Assert.assertTrue(adapter.collectDependencies(version).stream()
                .anyMatch(ref -> "VARIABLE".equals(
                        ref.targetResourceType())
                        && Long.valueOf(3L).equals(
                        ref.targetResourceId())));
    }

    @Test
    public void ruleDataObjectFieldDependencyTargetsGovernedRootObject() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(9L);
        definition.setRuleCode("R001");
        definition.setRuleName("准入规则");
        definition.setModelType("RULE_SET");
        definition.setStatus(1);
        RuleDefinitionContent content = new RuleDefinitionContent();
        content.setDefinitionId(9L);
        content.setModelJson("{\"leftOperand\":{\"kind\":\"REFERENCE\","
                + "\"refType\":\"DATA_OBJECT\",\"refId\":37,"
                + "\"code\":\"request.score\"}}");
        RuleDataObjectField field = new RuleDataObjectField();
        field.setId(37L);
        field.setObjectId(12L);
        field.setProjectId(2L);
        field.setScope("PROJECT");
        field.setStatus(1);
        RuleDataObjectFieldMapper fieldMapper =
                mapper(RuleDataObjectFieldMapper.class,
                        Map.of("selectById", field), new ArrayList<>());
        RuleGovernedResourceAdapter adapter =
                new RuleGovernedResourceAdapter(
                        store(definition, RuleDefinition::setId),
                        codec(), new RuleLifecycleService(), null,
                        mapper(RuleDefinitionContentMapper.class,
                                Map.of("selectOne", content),
                                new ArrayList<>()),
                        mapper(RuleDefinitionInputFieldMapper.class,
                                Map.of("selectList", List.of()),
                                new ArrayList<>()),
                        mapper(RuleDefinitionOutputFieldMapper.class,
                                Map.of("selectList", List.of()),
                                new ArrayList<>()),
                        fieldMapper);

        List<ResourceDependencyRef> dependencies =
                adapter.collectDependencies(adapter.loadEffective(9L));

        Assert.assertTrue(dependencies.stream().anyMatch(ref ->
                "DATA_OBJECT".equals(ref.targetResourceType())
                        && Long.valueOf(12L).equals(ref.targetResourceId())));
        Assert.assertFalse(dependencies.stream().anyMatch(ref ->
                "DATA_OBJECT".equals(ref.targetResourceType())
                        && Long.valueOf(37L).equals(ref.targetResourceId())));
    }

    @Test
    public void ruleRejectionKeepsOfflineEffectiveStatus() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(9L);
        definition.setRuleCode("R001");
        definition.setRuleName("准入规则");
        definition.setModelType("SCRIPT");
        definition.setStatus(2);
        RuleDefinitionContent content = new RuleDefinitionContent();
        content.setDefinitionId(9L);
        content.setModelJson("{\"script\":\"effective = 1\"}");
        RuleDefinitionContentMapper contentMapper =
                mapper(RuleDefinitionContentMapper.class,
                        Map.of("selectOne", content),
                        new ArrayList<>());
        RuleDefinitionInputFieldMapper inputMapper =
                mapper(RuleDefinitionInputFieldMapper.class,
                        Map.of("selectList", List.of()),
                        new ArrayList<>());
        RuleDefinitionOutputFieldMapper outputMapper =
                mapper(RuleDefinitionOutputFieldMapper.class,
                        Map.of("selectList", List.of()),
                        new ArrayList<>());
        RuleRevision review = new RuleRevision();
        review.setId(8L);
        review.setDefinitionId(9L);
        review.setState("REVIEW");
        boolean[] rejected = {false};
        RuleDraftSourceRequest[] reopened = {null};
        RuleLifecycleService lifecycle =
                new RuleLifecycleService() {
                    @Override
                    public List<RuleRevision> listRevisions(
                            Long definitionId) {
                        return List.of(review);
                    }

                    @Override
                    public RuleRevision returnToDraft(
                            Long revisionId,
                            RuleLifecycleActionRequest request) {
                        rejected[0] = true;
                        review.setState("REJECTED");
                        return review;
                    }

                    @Override
                    public RuleDraftSaveResponse createDraftFromSource(
                            Long definitionId,
                            RuleDraftSourceRequest request) {
                        reopened[0] = request;
                        return new RuleDraftSaveResponse();
                    }
                };
        RuleGovernedResourceAdapter adapter =
                new RuleGovernedResourceAdapter(
                        store(definition, RuleDefinition::setId),
                        codec(), lifecycle, null,
                        contentMapper, inputMapper, outputMapper);
        ResourceSnapshot effective = adapter.loadEffective(9L);

        adapter.onApprovalTerminated(
                9L, effective, "alice", "需要修改", "REJECTED");

        Assert.assertTrue(rejected[0]);
        Assert.assertNotNull(reopened[0]);
        Assert.assertEquals(Long.valueOf(8L), reopened[0].getSourceId());
        Assert.assertEquals(2, ((Number) CanonicalJson.readMap(
                adapter.loadEffective(9L).snapshotJson())
                .get("status")).intValue());
    }

    @Test
    public void ruleReviewDigestDriftIsReportedBeforeApprovalApply() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(9L);
        definition.setRuleCode("R001");
        definition.setRuleName("准入规则");
        definition.setModelType("RULE_SET");
        definition.setStatus(0);
        RuleDefinitionContent content = new RuleDefinitionContent();
        content.setDefinitionId(9L);
        content.setModelJson("{\"groups\":[]}");
        RuleRevision review = new RuleRevision();
        review.setId(8L);
        review.setDefinitionId(9L);
        review.setState("REVIEW");
        review.setContentDigest("submitted-digest");
        RuleLifecycleService lifecycle = new RuleLifecycleService() {
            @Override
            public List<RuleRevision> listRevisions(Long definitionId) {
                return List.of(review);
            }

            @Override
            public RulePreflightReport preflightReport(
                    Long definitionId, Long revisionId) {
                RulePreflightReport report = new RulePreflightReport();
                report.setValid(true);
                report.setContentDigest("current-digest");
                return report;
            }
        };
        RuleGovernedResourceAdapter adapter =
                new RuleGovernedResourceAdapter(
                        store(definition, RuleDefinition::setId), codec(),
                        lifecycle, null,
                        mapper(RuleDefinitionContentMapper.class,
                                Map.of("selectOne", content),
                                new ArrayList<>()),
                        mapper(RuleDefinitionInputFieldMapper.class,
                                Map.of("selectList", List.of()),
                                new ArrayList<>()),
                        mapper(RuleDefinitionOutputFieldMapper.class,
                                Map.of("selectList", List.of()),
                                new ArrayList<>()));

        var submitted = CanonicalJson.readMap(adapter.loadEffective(9L).snapshotJson()); submitted.put("_ruleRevisionId", 8L);
        List<GovernanceIssue> issues = adapter.validate(ResourceSnapshot.ofJson(CanonicalJson.write(submitted)), "UPDATE");

        Assert.assertTrue(issues.stream().anyMatch(issue ->
                "RULE_REVIEW_PREFLIGHT_CHANGED".equals(issue.code())));
    }

    @Test
    public void ruleApprovalReportsActiveStatusAfterAutomaticPublish() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(9L);
        definition.setRuleCode("R001");
        definition.setRuleName("准入规则");
        definition.setModelType("RULE_SET");
        definition.setStatus(0);
        RuleRevision approved = new RuleRevision();
        approved.setId(8L);
        approved.setDefinitionId(9L);
        approved.setState("APPROVED");
        approved.setArtifactId(99L);
        RuleLifecycleService lifecycle = new RuleLifecycleService() {
            @Override public Integer publishedBusinessVersion(Long id, Long revisionId) { return 6; }
            @Override
            public List<RuleRevision> listRevisions(Long definitionId) {
                return List.of(approved);
            }

            @Override
            public RuleRevision publish(Long revisionId,
                                        RuleLifecycleActionRequest request) {
                approved.setState("PUBLISHED");
                return approved;
            }
        };
        RuleGovernedResourceAdapter adapter =
                new RuleGovernedResourceAdapter(
                        store(definition, RuleDefinition::setId), codec(),
                        lifecycle, null,
                        mapper(RuleDefinitionContentMapper.class,
                                Map.of(),
                                new ArrayList<>()),
                        mapper(RuleDefinitionInputFieldMapper.class,
                                Map.of("selectList", List.of()),
                                new ArrayList<>()),
                        mapper(RuleDefinitionOutputFieldMapper.class,
                                Map.of("selectList", List.of()),
                                new ArrayList<>()));

        AppliedResource result = adapter.afterAggregateApplied(
                new ApprovalApplyContext(1L, 9L, 1, "UPDATE",
                        ResourceSnapshot.ofJson("{\"id\":9}"),
                        "admin", null),
                new AppliedResource(9L, 1, "DISABLED", null));

        Assert.assertEquals("ACTIVE", result.effectiveStatus());
        Assert.assertEquals(Integer.valueOf(6), result.versionNo());
        Assert.assertEquals(Long.valueOf(99L), result.artifactId());
    }

    @Test
    public void ruleRestoreBuildsNewRevisionFromHistoricalSnapshot() {
        assertRuleRestore("NEW");
    }

    @Test public void ruleOverwriteUsesSharedBusinessLineEvenWhenSourceProjectionHasAnotherProductionNumber() {
        assertRuleRestore("REUSE");
    }

    private void assertRuleRestore(String restoreMode) {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(9L);
        definition.setRuleCode("R001");
        definition.setRuleName("准入规则");
        definition.setModelType("SCRIPT");
        definition.setStatus(1);
        RuleDefinitionContent current = new RuleDefinitionContent();
        current.setDefinitionId(9L);
        current.setModelJson("{\"script\":\"current = 1\"}");
        RuleDefinitionContentMapper contentMapper =
                mapper(RuleDefinitionContentMapper.class,
                        Map.of("selectOne", current),
                        new ArrayList<>());
        RuleDefinitionInputFieldMapper inputMapper =
                mapper(RuleDefinitionInputFieldMapper.class,
                        Map.of("selectList", List.of()),
                        new ArrayList<>());
        RuleDefinitionOutputFieldMapper outputMapper =
                mapper(RuleDefinitionOutputFieldMapper.class,
                        Map.of("selectList", List.of()),
                        new ArrayList<>());
        RuleRevision offline = new RuleRevision();
        offline.setId(6L);
        offline.setDefinitionId(9L);
        offline.setRevisionNo(4);
        offline.setState("OFFLINE");
        RuleRevision draft = new RuleRevision();
        draft.setId(7L);
        draft.setDefinitionId(9L);
        draft.setRevisionNo(5);
        draft.setState("DRAFT");
        draft.setLockVersion(0);
        Long[] selectedBase = {null};
        Integer[] overwriteTarget = {null};
        RuleLifecycleService lifecycle =
                new RuleLifecycleService() {
                    @Override public Integer publishedBusinessVersion(Long id, Long revisionId) { return 3; }
                    @Override public void configureRestoreBusinessVersion(RuleRevision revision, Integer businessVersion) { overwriteTarget[0] = businessVersion; }
                    @Override
                    public List<RuleRevision> listRevisions(
                            Long definitionId) {
                        return List.of(offline);
                    }

                    @Override
                    public RuleRevision createDraft(
                            Long definitionId,
                            Long baseRevisionId) {
                        selectedBase[0] = baseRevisionId;
                        return draft;
                    }

                    @Override
                    public RuleRevision submit(
                            Long revisionId,
                            RuleLifecycleActionRequest request) {
                        draft.setState("REVIEW");
                        return draft;
                    }

                    @Override
                    public RuleRevision approve(
                            Long revisionId,
                            RuleLifecycleActionRequest request) {
                        draft.setState("APPROVED");
                        draft.setArtifactId(99L);
                        return draft;
                    }

                    @Override
                    public RuleRevision publish(
                            Long revisionId,
                            RuleLifecycleActionRequest request) {
                        draft.setState("PUBLISHED");
                        return draft;
                    }
                };
        RuleDraftSaveRequest[] saved = {null};
        RuleDraftService draftService = new RuleDraftService() {
            @Override
            public RuleDraftSaveResponse save(
                    RuleDraftSaveRequest request) {
                saved[0] = request;
                draft.setModelJson(request.getModelJson());
                RuleDraftSaveResponse response =
                        new RuleDraftSaveResponse();
                response.setRevision(draft);
                return response;
            }
        };
        RuleGovernedResourceAdapter adapter =
                new RuleGovernedResourceAdapter(
                        store(definition, RuleDefinition::setId),
                        codec(), lifecycle, draftService,
                        contentMapper, inputMapper, outputMapper);
        var source = new com.hengshucredit.rule.model.entity.GovernedResourceVersion();
        source.setResourceType("RULE"); source.setResourceId(9L); source.setBusinessVersion(3);
        source.setSnapshotJson("{\"id\":9,\"publishedVersion\":8}");
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "governanceVersions",
                mapper(com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper.class, Map.of("selectById", source), new ArrayList<>()));
        ResourceSnapshot historical =
                ResourceSnapshot.ofJson(
                        "{\"id\":9,\"ruleCode\":\"R001\","
                                + "\"ruleName\":\"准入规则\","
                                + "\"modelType\":\"SCRIPT\","
                                + "\"content\":{\"modelJson\":"
                                + "\"{\\\"script\\\":"
                                + "\\\"historical = 1\\\"}\"},"
                                + "\"inputFieldsJson\":[],"
                                + "\"outputFieldsJson\":[]}");

        AppliedResource applied = adapter.apply(
                new ApprovalApplyContext(
                        41L, 9L, 3, "RESTORE",
                        historical, "alice", 1L, restoreMode));

        Assert.assertEquals(Long.valueOf(6L), selectedBase[0]);
        Assert.assertEquals("{\"script\":\"historical = 1\"}",
                saved[0].getModelJson());
        Assert.assertEquals("PUBLISHED", draft.getState());
        Assert.assertEquals(Long.valueOf(99L),
                applied.artifactId());
        if ("REUSE".equals(restoreMode)) Assert.assertEquals(Integer.valueOf(3), overwriteTarget[0]);
        Assert.assertEquals(3, ((Number) CanonicalJson.readMap(adapter.loadApplied(applied).snapshotJson()).get("publishedVersion")).intValue());
    }

    private RuleVariable variable() {
        RuleVariable variable = new RuleVariable();
        variable.setId(7L);
        variable.setVarCode("grade");
        variable.setVarLabel("等级");
        variable.setVarType("ENUM");
        variable.setStatus(1);
        return variable;
    }

    private <T> SimpleEntityGovernedResourceAdapter.EntityStore<T> store(
            T initial, IdSetter<T> idSetter) {
        return new SimpleEntityGovernedResourceAdapter.EntityStore<>() {
            private T value = initial;

            @Override
            public T load(Long id) {
                return value;
            }

            @Override
            public void insert(T entity) {
                idSetter.set(entity, 100L);
                value = entity;
            }

            @Override
            public void update(T entity) {
                value = entity;
            }
        };
    }

    private GovernanceSecretCodec codec() {
        ProjectAuthProperties properties = new ProjectAuthProperties();
        properties.setActiveKeyId("test");
        properties.setMasterKeys(Map.of(
                "test",
                "test-governance-master-key-32-characters-long"));
        return new GovernanceSecretCodec(
                new CredentialCipher(properties));
    }

    @SuppressWarnings("unchecked")
    private <T> T mapper(Class<T> type,
                         Map<String, Object> returns,
                         List<Object> inserted) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName())
                            && args != null && args.length > 0) {
                        inserted.add(args[0]);
                        return 1;
                    }
                    if (returns.containsKey(method.getName())) {
                        return returns.get(method.getName());
                    }
                    Class<?> resultType = method.getReturnType();
                    if (resultType == int.class) {
                        return 1;
                    }
                    if (resultType == long.class) {
                        return 0L;
                    }
                    if (resultType == boolean.class) {
                        return false;
                    }
                    return null;
                });
    }

    private interface IdSetter<T> {
        void set(T value, Long id);
    }
}
