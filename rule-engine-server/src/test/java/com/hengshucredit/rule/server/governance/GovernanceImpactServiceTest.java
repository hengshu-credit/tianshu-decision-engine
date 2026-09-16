package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.service.RuleLineageService;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public class GovernanceImpactServiceTest {

    @Test
    public void deletingNestedObjectDoesNotTreatItsOwnFieldsAsConsumers() {
        GovernanceImpactService service = new GovernanceImpactService(lineage(List.of(
                objectField(8L, 4L), objectField(9L, 4L))));
        Assert.assertTrue(service.analyze("DATA_OBJECT", 4L, "DELETE",
                ResourceSnapshot.ofJson("{\"id\":4,\"fields\":[{\"id\":8},{\"id\":9,\"parentFieldId\":8}]}")).isEmpty());
    }

    @Test
    public void anotherObjectsFieldRemainsAnExternalConsumer() {
        GovernanceImpactService service = new GovernanceImpactService(lineage(List.of(
                objectField(8L, 4L), objectField(9L, 5L))));
        Assert.assertFalse(service.analyze("DATA_OBJECT", 4L, "DELETE",
                ResourceSnapshot.ofJson("{\"id\":4,\"fields\":[{\"id\":8}]}")).isEmpty());
    }

    @Test
    public void disableIsBlockedWhenEffectiveDownstreamExists() {
        GovernanceImpactService service = new GovernanceImpactService(
                lineage(List.of(
                        node("VARIABLE", 7L, "年龄"),
                        node("RULE", 9L, "准入规则"))));

        List<GovernanceIssue> issues = service.analyze(
                "VARIABLE", 7L, "DISABLE",
                ResourceSnapshot.ofJson("{\"id\":7}"));

        Assert.assertEquals(1, issues.size());
        Assert.assertEquals("DOWNSTREAM_DEPENDENCY_ACTIVE",
                issues.get(0).code());
        Assert.assertTrue(issues.get(0).message()
                .contains("准入规则"));
    }

    @Test
    public void updateDoesNotTreatDownstreamAsConflict() {
        GovernanceImpactService service = new GovernanceImpactService(
                lineage(List.of(
                        node("VARIABLE", 7L, "年龄"),
                        node("RULE", 9L, "准入规则"))));

        Assert.assertTrue(service.analyze(
                "VARIABLE", 7L, "UPDATE",
                ResourceSnapshot.ofJson("{\"id\":7}"))
                .isEmpty());
    }

    @Test
    public void dataObjectChecksEveryFieldNode() {
        GovernanceImpactService service = new GovernanceImpactService(
                lineage(List.of(
                        node("DATA_FIELD", 8L, "年龄"),
                        node("MODEL", 5L, "授信模型"))));

        List<GovernanceIssue> issues = service.analyze(
                "DATA_OBJECT", 4L, "DELETE",
                ResourceSnapshot.ofJson(
                        "{\"id\":4,\"fields\":[{\"id\":8}]}"));

        Assert.assertEquals(1, issues.size());
        Assert.assertEquals("$.fields[0]",
                issues.get(0).referencePath());
    }

    @Test
    public void listLibraryDisableChecksListVariableAndRuleImpact() {
        GovernanceImpactService service = new GovernanceImpactService(
                lineage(List.of(
                        node("LIST", 6L, "手机号黑名单"),
                        node("VARIABLE", 7L, "黑名单命中"),
                        node("RULE", 9L, "准入规则"))));

        List<GovernanceIssue> issues = service.analyze(
                "LIST_LIBRARY", 6L, "DISABLE",
                ResourceSnapshot.ofJson("{\"id\":6}"));

        Assert.assertEquals(1, issues.size());
        Assert.assertTrue(issues.get(0).message()
                .contains("黑名单命中"));
        Assert.assertTrue(issues.get(0).message()
                .contains("准入规则"));
    }

    private RuleLineageService lineage(
            List<Map<String, Object>> nodes) {
        return new RuleLineageService() {
            @Override
            public Map<String, Object> graph(
                    String nodeType, Long nodeId,
                    String direction, Integer maxDepth) {
                return Map.of("nodes", nodes);
            }
        };
    }

    private Map<String, Object> node(
            String type, Long id, String label) {
        return Map.of("type", type, "id", type + ":" + id, "refId", id,
                "label", label, "code", label);
    }

    private Map<String, Object> objectField(Long fieldId, Long objectId) {
        Map<String, Object> node = new LinkedHashMap<>(node("DATA_FIELD", fieldId, "字段" + fieldId));
        node.put("objectNodeId", "DATA_OBJECT:" + objectId);
        return node;
    }
}
