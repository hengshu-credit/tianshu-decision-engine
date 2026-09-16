package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.dto.ParsedConstant;
import com.hengshucredit.rule.model.dto.ParsedConstantGroup;
import com.hengshucredit.rule.model.dto.ParsedField;
import com.hengshucredit.rule.model.dto.ParsedObject;
import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleVariable;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.hengshucredit.rule.server.service.parser.JsonSchemaParser;
import com.hengshucredit.rule.server.service.parser.JavaEntityParser;
import com.hengshucredit.rule.server.service.parser.DdlTableParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GovernanceBatchImportServiceTest {

    @Test
    public void allFiveImportEntrypointsCreateReviewableDraftsFromRealParsers() {
        TestService service = new TestService();
        ReflectionTestUtils.setField(service, "jsonSchemaParser", new JsonSchemaParser());
        ReflectionTestUtils.setField(service, "javaEntityParser", new JavaEntityParser());
        ReflectionTestUtils.setField(service, "ddlTableParser", new DdlTableParser());
        Assert.assertEquals(Boolean.TRUE, service.importDataObjectsFromJava(null, "GLOBAL",
                "public class Java_Request { private String User_ID; private String[] Tags; }", "INPUT", "tester").get("success"));
        Assert.assertEquals(Boolean.TRUE, service.importDataObjectsFromJson(null, "GLOBAL",
                "{\"User_ID\":\"U1\",\"Nested\":{\"Age\":30}}", "Json_Request", "INPUT", "tester").get("success"));
        Assert.assertEquals(Boolean.TRUE, service.importDataObjectsFromDdl(null, "GLOBAL",
                "CREATE TABLE Ddl_Request (User_ID varchar(30), Amount decimal(12,2));", "INPUT", "tester").get("success"));
        Assert.assertEquals(Boolean.TRUE, service.importConstantsFromJava(null, "GLOBAL",
                "public class Limits { public static final int MAX_AGE = 65; }", "tester").get("success"));
        Assert.assertEquals(Boolean.TRUE, service.importConstantsFromJson(null, "GLOBAL",
                "{\"CODES\":[\"A\",\"B\"]}", "tester").get("success"));
        Assert.assertEquals(5, service.drafts.size());
        Assert.assertEquals(List.of("DATA_OBJECT", "DATA_OBJECT", "DATA_OBJECT", "VARIABLE", "VARIABLE"),
                service.drafts.stream().map(GovernanceDraftRequest::getResourceType).toList());
        Assert.assertEquals("LIST", JSON.parseObject(service.drafts.get(4).getSnapshotJson()).getString("varType"));
    }

    @Test
    public void importingConstantsCannotConvertAnOrdinaryVariable() {
        TestService service = new TestService();
        RuleVariable existing = new RuleVariable();
        existing.setId(7L);
        existing.setVarSource("INPUT");
        service.variables.put("Age", existing);
        ParsedConstantGroup group = new ParsedConstantGroup();
        group.setConstants(List.of(constant("Age", "年龄", "NUMBER", "65")));
        Map<String, Object> result = service.importConstants(null, "GLOBAL", group, "tester");
        Assert.assertEquals(Boolean.FALSE, result.get("success"));
        Assert.assertTrue(service.drafts.isEmpty());
        Assert.assertEquals("INPUT", existing.getVarSource());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void realNestedJsonCreatesAnApprovalWithIntactParentReferences() {
        TestService service = new TestService();
        ReflectionTestUtils.setField(service, "jsonSchemaParser", new JsonSchemaParser());
        Map<String, Object> result = service.importDataObjectsFromJson(
                7L, "PROJECT", "{\"Request_ID\":\"r1\",\"payload\":{\"Apps\":[{\"app_code\":\"a\"}]}}",
                "Mixed_Request", "INPUT", "tester");

        Assert.assertEquals(Boolean.TRUE, result.get("success"));
        Map<String, Object> snapshot = JSON.parseObject(service.drafts.get(0).getSnapshotJson(), Map.class);
        List<Map<String, Object>> fields = (List<Map<String, Object>>) snapshot.get("fields");
        Assert.assertEquals(4, fields.size());
        Assert.assertEquals("Mixed_Request", snapshot.get("objectCode"));
        Assert.assertEquals(findField(fields, "payload").get("id"), findField(fields, "Apps").get("parentFieldId"));
        Assert.assertEquals(findField(fields, "Apps").get("id"), findField(fields, "app_code").get("parentFieldId"));
        Assert.assertEquals("OBJECT", findField(fields, "Apps").get("genericType"));
        Assert.assertEquals("Request_ID", fields.get(0).get("varCode"));
    }

    @Test
    public void constantImportCreatesDraftsWithoutMutatingEffectiveRows() {
        TestService service = new TestService();
        RuleVariable existing = new RuleVariable();
        existing.setId(12L);
        existing.setProjectId(7L);
        existing.setScope("PROJECT");
        existing.setVarCode("MAX_AGE");
        existing.setVarLabel("原标签");
        existing.setScriptName("MAX_AGE");
        existing.setVarType("NUMBER");
        existing.setVarSource("CONSTANT");
        existing.setDefaultValue("60");
        existing.setStatus(1);
        service.variables.put("MAX_AGE", existing);

        ParsedConstantGroup group = new ParsedConstantGroup();
        group.setGroupCode("LIMITS");
        ParsedConstant update = constant("MAX_AGE", "最大年龄",
                "NUMBER", " 65 ");
        ParsedConstant create = constant("MIN_AGE", "最小年龄",
                "NUMBER", "18");
        group.setConstants(List.of(update, create));

        Map<String, Object> result = service.importConstants(
                7L, "PROJECT", group, "tester");

        Assert.assertEquals(Boolean.TRUE, result.get("success"));
        Assert.assertEquals(2, result.get("constantCount"));
        Assert.assertEquals(2, result.get("requestCount"));
        Assert.assertEquals(2, service.drafts.size());
        Assert.assertEquals("UPDATE", service.drafts.get(0).getAction());
        Assert.assertEquals(Long.valueOf(12L),
                service.drafts.get(0).getResourceId());
        Assert.assertEquals("CREATE", service.drafts.get(1).getAction());
        Assert.assertNull(service.drafts.get(1).getResourceId());
        Map<String, Object> updateSnapshot = JSON.parseObject(
                service.drafts.get(0).getSnapshotJson(), Map.class);
        Assert.assertEquals("65", updateSnapshot.get("defaultValue"));
        Assert.assertEquals("最大年龄", updateSnapshot.get("varLabel"));
        Assert.assertEquals("60", existing.getDefaultValue());

        TestService globalService = new TestService();
        globalService.importConstants(
                null, "GLOBAL", group, "tester");
        Map<String, Object> globalSnapshot = JSON.parseObject(
                globalService.drafts.get(0).getSnapshotJson(), Map.class);
        Assert.assertEquals(0L,
                ((Number) globalSnapshot.get("projectId")).longValue());
    }

    @Test
    public void jsonObjectImportPreservesStableIdsForMatchingFieldPaths() {
        TestService service = new TestService();
        RuleDataObject existing = new RuleDataObject();
        existing.setId(20L);
        existing.setProjectId(7L);
        existing.setScope("PROJECT");
        existing.setObjectCode("request");
        existing.setObjectLabel("请求");
        existing.setScriptName("request");
        existing.setObjectType("INPUT");
        existing.setStatus(1);
        service.objects.put("request", existing);

        Map<String, Object> root = field(101L, null,
                "params", "params", "OBJECT");
        Map<String, Object> oldChild = field(102L, 101L,
                "legacy", "params.legacy", "STRING");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", 20L);
        snapshot.put("projectId", 7L);
        snapshot.put("scope", "PROJECT");
        snapshot.put("objectCode", "request");
        snapshot.put("objectLabel", "请求");
        snapshot.put("scriptName", "request");
        snapshot.put("objectType", "INPUT");
        snapshot.put("status", 1);
        snapshot.put("fields", new ArrayList<>(List.of(root, oldChild)));
        service.snapshots.put("DATA_OBJECT:20", snapshot);

        ParsedObject parsed = new ParsedObject();
        parsed.setObjectCode("request");
        parsed.setObjectLabel("request");
        parsed.setScriptName("request");
        ParsedField parsedRoot = parsedField(1L, null,
                "params", "params", "OBJECT");
        ParsedField newChild = parsedField(2L, 1L,
                "name", "params.name", "STRING");
        parsed.setFields(List.of(parsedRoot, newChild));

        Map<String, Object> result = service.importDataObjects(
                7L, "PROJECT", "INPUT", "JSON", "{}",
                List.of(parsed), "tester");

        Assert.assertEquals(Boolean.TRUE, result.get("success"));
        Assert.assertEquals(1, service.drafts.size());
        GovernanceDraftRequest draft = service.drafts.get(0);
        Assert.assertEquals("UPDATE", draft.getAction());
        Map<String, Object> aggregate = JSON.parseObject(
                draft.getSnapshotJson(), Map.class);
        List<Map<String, Object>> fields =
                (List<Map<String, Object>>) aggregate.get("fields");
        Map<String, Object> stableRoot = findField(fields, "params");
        Map<String, Object> preserved = findField(fields, "legacy");
        Map<String, Object> added = findField(fields, "name");
        Assert.assertEquals(101L,
                ((Number) stableRoot.get("id")).longValue());
        Assert.assertEquals(102L,
                ((Number) preserved.get("id")).longValue());
        Assert.assertTrue(((Number) added.get("id")).longValue() < 0);
        Assert.assertEquals(101L,
                ((Number) added.get("parentFieldId")).longValue());
    }

    @Test
    public void objectImportReportsMissingReferencedObjectBeforeDraftCreation() {
        TestService service = new TestService();
        ParsedObject parsed = new ParsedObject();
        parsed.setObjectCode("Order");
        parsed.setObjectLabel("Order");
        parsed.setScriptName("Order");
        ParsedField address = parsedField(null, null,
                "address", "address", "OBJECT");
        address.setRefObjectCode("Address");
        parsed.setFields(List.of(address));

        Map<String, Object> result = service.importDataObjects(
                7L, "PROJECT", "INPUT", "JAVA", "class Order {}",
                List.of(parsed), "tester");

        Assert.assertEquals(Boolean.FALSE, result.get("success"));
        Assert.assertEquals(0, service.drafts.size());
        Assert.assertTrue(String.valueOf(result.get("error"))
                .contains("Address"));
        Assert.assertTrue(result.containsKey("conflicts"));
    }

    @Test
    public void globalImportUsesGlobalProjectForLookupAndApprovalMetadata() {
        TestService service = new TestService();
        RuleVariable existing = new RuleVariable();
        existing.setId(12L);
        existing.setProjectId(0L);
        existing.setScope("GLOBAL");
        existing.setVarCode("MAX_AGE");
        existing.setVarLabel("最大年龄");
        existing.setScriptName("MAX_AGE");
        existing.setVarType("NUMBER");
        existing.setVarSource("CONSTANT");
        existing.setDefaultValue("60");
        existing.setStatus(1);
        service.variables.put("MAX_AGE", existing);

        ParsedConstantGroup group = new ParsedConstantGroup();
        group.setConstants(List.of(constant(
                "MAX_AGE", "最大年龄", "NUMBER", "65")));

        service.importConstants(7L, "GLOBAL", group, "tester");

        Assert.assertEquals(Long.valueOf(0L),
                service.lastVariableLookupProjectId);
        Assert.assertEquals(Long.valueOf(0L),
                service.drafts.get(0).getProjectId());
        Assert.assertEquals("UPDATE", service.drafts.get(0).getAction());
    }

    @Test
    public void sameBatchNewObjectReferenceReportsPendingDependency() {
        TestService service = new TestService();
        ParsedObject order = new ParsedObject();
        order.setObjectCode("Order");
        order.setObjectLabel("Order");
        order.setScriptName("Order");
        ParsedField addressField = parsedField(null, null,
                "address", "address", "OBJECT");
        addressField.setRefObjectCode("Address");
        order.setFields(List.of(addressField));

        ParsedObject address = new ParsedObject();
        address.setObjectCode("Address");
        address.setObjectLabel("Address");
        address.setScriptName("Address");
        address.setFields(List.of(parsedField(
                null, null, "city", "city", "STRING")));

        Map<String, Object> result = service.importDataObjects(
                7L, "PROJECT", "INPUT", "JAVA", "entities",
                List.of(order, address), "tester");

        Assert.assertEquals(Boolean.FALSE, result.get("success"));
        Assert.assertEquals(0, service.drafts.size());
        Assert.assertTrue(String.valueOf(result.get("error"))
                .contains("同批次"));
        Assert.assertEquals(List.of("Address"),
                result.get("batchPendingDependencies"));
    }

    private static ParsedConstant constant(
            String code, String label, String type, String value) {
        ParsedConstant constant = new ParsedConstant();
        constant.setConstCode(code);
        constant.setConstLabel(label);
        constant.setScriptName(code);
        constant.setConstType(type);
        constant.setConstValue(value);
        return constant;
    }

    private static ParsedField parsedField(
            Long tempId, Long parentId, String code,
            String scriptName, String type) {
        ParsedField field = new ParsedField();
        field.setTempId(tempId);
        field.setParentFieldId(parentId);
        field.setFieldName(code);
        field.setFieldLabel(code);
        field.setScriptName(scriptName);
        field.setVarType(type);
        return field;
    }

    private static Map<String, Object> field(
            Long id, Long parentId, String code,
            String scriptName, String type) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("id", id);
        field.put("parentFieldId", parentId);
        field.put("varCode", code);
        field.put("varLabel", code);
        field.put("scriptName", scriptName);
        field.put("varType", type);
        field.put("sortOrder", 0);
        field.put("status", 1);
        field.put("options", List.of());
        return field;
    }

    private static Map<String, Object> findField(
            List<Map<String, Object>> fields, String code) {
        return fields.stream()
                .filter(field -> code.equals(field.get("varCode")))
                .findFirst()
                .orElseThrow();
    }

    private static class TestService
            extends GovernanceBatchImportService {
        private final Map<String, RuleVariable> variables =
                new LinkedHashMap<>();
        private final Map<String, RuleDataObject> objects =
                new LinkedHashMap<>();
        private final Map<String, Map<String, Object>> snapshots =
                new LinkedHashMap<>();
        private final List<GovernanceDraftRequest> drafts =
                new ArrayList<>();
        private long requestId = 1L;
        private Long lastVariableLookupProjectId;

        @Override
        protected RuleVariable findVariable(
                Long projectId, String scope, String code) {
            lastVariableLookupProjectId = projectId;
            return variables.get(code);
        }

        @Override
        protected RuleDataObject findDataObject(
                Long projectId, String scope, String code) {
            return objects.get(code);
        }

        @Override
        protected Map<String, Object> loadEffective(
                String resourceType, Long resourceId) {
            Map<String, Object> value = snapshots.get(
                    resourceType + ":" + resourceId);
            if (value != null) {
                return new LinkedHashMap<>(value);
            }
            RuleVariable variable = variables.values().stream()
                    .filter(item -> item.getId().equals(resourceId))
                    .findFirst()
                    .orElse(null);
            return variable == null
                    ? new LinkedHashMap<>()
                    : JSON.parseObject(JSON.toJSONString(variable),
                    LinkedHashMap.class);
        }

        @Override
        protected GovernanceApprovalRequest createDraft(
                GovernanceDraftRequest draft, String actor) {
            drafts.add(draft);
            GovernanceApprovalRequest request =
                    new GovernanceApprovalRequest();
            request.setId(requestId++);
            return request;
        }
    }
}
