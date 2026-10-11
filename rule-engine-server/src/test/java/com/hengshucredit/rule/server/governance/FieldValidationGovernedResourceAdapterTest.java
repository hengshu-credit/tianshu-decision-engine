package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleFieldValidation;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleFieldValidationMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FieldValidationGovernedResourceAdapterTest {
    @Test
    public void directDraftDeletionStillRequiresRemovingDownstreamReferencesFirst() {
        FakeRepository repository = validRepository();
        RuleDefinitionInputField input = new RuleDefinitionInputField();
        input.setDefinitionId(31L); input.setValidationRuleIds("[17]");
        repository.inputFields = List.of(input);
        var context = new ApprovalApplyContext(null, 17L, 0, "DRAFT_DELETE",
                ResourceSnapshot.ofJson("{\"id\":17}"), "owner", null);
        Assert.assertThrows(IllegalArgumentException.class, () -> adapter(repository).apply(context));
        Assert.assertTrue(repository.updates.isEmpty());
        repository.inputFields = List.of();
        Assert.assertEquals("DELETED", adapter(repository).apply(context).effectiveStatus());
        Assert.assertEquals(Integer.valueOf(-1), repository.updates.get(0).getStatus());
    }

    @Test
    public void normalizationPreservesBusinessValuesAndProjectDependency() {
        FakeRepository repository = validRepository();

        ResourceSnapshot normalized = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson("{\"projectId\":7,"
                        + "\"scope\":\"project\","
                        + "\"validationCode\":\"Mobile_Check\","
                        + "\"validationName\":\"手机号校验\","
                        + "\"validationType\":\"regex\","
                        + "\"validationValue\":\"^1\\\\d{10}$\","
                        + "\"errorMessage\":\"手机号格式错误\","
                        + "\"projectName\":\"展示字段\","
                        + "\"builtIn\":false,\"status\":1}"));

        Map<String, Object> value = CanonicalJson.readMap(
                normalized.snapshotJson());
        Assert.assertEquals("Mobile_Check", value.get("validationCode"));
        Assert.assertEquals("^1\\d{10}$", value.get("validationValue"));
        Assert.assertEquals("PROJECT", value.get("scope"));
        Assert.assertEquals("REGEX", value.get("validationType"));
        Assert.assertFalse(value.containsKey("projectName"));
        Assert.assertFalse(value.containsKey("builtIn"));
        Assert.assertEquals(List.of(new ResourceDependencyRef(
                        GovernanceResourceTypes.PROJECT, 7L,
                        GovernanceResourceTypes.PROJECT, "$.projectId",
                        "BELONGS_TO", true)),
                adapter(repository).collectDependencies(normalized));
    }

    @Test
    public void validationRejectsInvalidValueProjectStatusAndDuplicateCode() {
        FakeRepository repository = validRepository();
        repository.project = null;
        repository.duplicateCount = 1L;
        ResourceSnapshot normalized = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson("{\"projectId\":7,"
                        + "\"scope\":\"PROJECT\","
                        + "\"validationCode\":\"dup\","
                        + "\"validationName\":\"重复规则\","
                        + "\"validationType\":\"REGEX\","
                        + "\"validationValue\":\"[\","
                        + "\"errorMessage\":\"错误\",\"status\":2}"));

        List<GovernanceIssue> issues = adapter(repository)
                .validate(normalized, "CREATE");

        Assert.assertTrue(hasError(issues, "FIELD_VALIDATION_PROJECT_NOT_FOUND"));
        Assert.assertTrue(hasError(issues, "FIELD_VALIDATION_VALUE_INVALID"));
        Assert.assertTrue(hasError(issues, "FIELD_VALIDATION_STATUS_INVALID"));
        Assert.assertTrue(hasError(issues, "FIELD_VALIDATION_CODE_DUPLICATE"));
    }

    @Test
    public void validationRejectsReservedCodeAndChangedIdentity() {
        FakeRepository repository = validRepository();
        ResourceSnapshot reserved = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson("{\"scope\":\"GLOBAL\","
                        + "\"validationCode\":\"builtin_mobile\","
                        + "\"validationName\":\"伪内置\","
                        + "\"validationType\":\"REGEX\","
                        + "\"validationValue\":\"x\","
                        + "\"errorMessage\":\"错误\"}"));
        repository.current = rule(15L, 7L, "PROJECT", "Stable_Code");
        ResourceSnapshot changed = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson("{\"id\":15,\"projectId\":7,"
                        + "\"scope\":\"PROJECT\","
                        + "\"validationCode\":\"Changed_Code\","
                        + "\"validationName\":\"规则\","
                        + "\"validationType\":\"REQUIRED\","
                        + "\"errorMessage\":\"必填\"}"));

        Assert.assertTrue(hasError(adapter(repository)
                        .validate(reserved, "CREATE"),
                "FIELD_VALIDATION_CODE_RESERVED"));
        Assert.assertTrue(hasError(adapter(repository)
                        .validate(changed, "UPDATE"),
                "FIELD_VALIDATION_IDENTITY_CHANGED"));
    }

    @Test
    public void builtinAndReferencedRulesBlockLifecycleActions() {
        FakeRepository repository = validRepository();
        repository.current = rule(11L, 0L, "GLOBAL", "builtin_mobile");
        ResourceSnapshot builtin = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson("{\"id\":11,\"scope\":\"GLOBAL\","
                        + "\"validationCode\":\"builtin_mobile\","
                        + "\"validationName\":\"手机号\","
                        + "\"validationType\":\"REGEX\","
                        + "\"validationValue\":\"^1[0-9]{10}$\","
                        + "\"errorMessage\":\"错误\"}"));
        Assert.assertTrue(hasError(adapter(repository)
                        .validate(builtin, "UPDATE"),
                "FIELD_VALIDATION_BUILTIN_IMMUTABLE"));

        repository.current = rule(17L, 7L, "PROJECT", "Mobile_Check");
        RuleDefinitionInputField input = new RuleDefinitionInputField();
        input.setDefinitionId(31L);
        input.setValidationRuleIds("[8,17]");
        repository.inputFields = List.of(input);
        ResourceSnapshot referenced = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson("{\"id\":17,\"projectId\":7,"
                        + "\"scope\":\"PROJECT\","
                        + "\"validationCode\":\"Mobile_Check\","
                        + "\"validationName\":\"手机号\","
                        + "\"validationType\":\"REQUIRED\","
                        + "\"errorMessage\":\"必填\"}"));

        Assert.assertTrue(hasError(adapter(repository)
                        .validate(referenced, "DISABLE"),
                "FIELD_VALIDATION_IN_USE"));
        Assert.assertTrue(hasError(adapter(repository)
                        .validate(referenced, "DELETE"),
                "FIELD_VALIDATION_IN_USE"));
        Assert.assertFalse(hasError(adapter(repository)
                        .validate(referenced, "UPDATE"),
                "FIELD_VALIDATION_IN_USE"));

        ResourceSnapshot implicitDisable = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson("{\"id\":17,\"projectId\":7,"
                        + "\"scope\":\"PROJECT\","
                        + "\"validationCode\":\"Mobile_Check\","
                        + "\"validationName\":\"手机号\","
                        + "\"validationType\":\"REQUIRED\","
                        + "\"errorMessage\":\"必填\",\"status\":0}"));
        Assert.assertTrue(hasError(adapter(repository)
                        .validate(implicitDisable, "UPDATE"),
                "FIELD_VALIDATION_STATUS_ACTION_REQUIRED"));
    }

    @Test
    public void applyCreatesUpdatesDisablesAndSoftDeletesProjection() {
        FakeRepository repository = validRepository();
        ResourceSnapshot snapshot = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson("{\"projectId\":7,"
                        + "\"scope\":\"PROJECT\","
                        + "\"validationCode\":\"Mobile_Check\","
                        + "\"validationName\":\"手机号\","
                        + "\"validationType\":\"REQUIRED\","
                        + "\"errorMessage\":\"必填\",\"status\":1}"));

        AppliedResource created = adapter(repository).apply(
                new ApprovalApplyContext(1L, null, 1,
                        "CREATE", snapshot, "owner", null));
        adapter(repository).apply(new ApprovalApplyContext(
                2L, 20L, 2, "UPDATE", snapshot, "owner", null));
        adapter(repository).apply(new ApprovalApplyContext(
                3L, 20L, 3, "DISABLE", snapshot, "owner", null));
        AppliedResource deleted = adapter(repository).apply(
                new ApprovalApplyContext(4L, 20L, 4,
                        "DELETE", snapshot, "owner", null));

        Assert.assertEquals(Long.valueOf(41L), created.resourceId());
        Assert.assertEquals("Mobile_Check",
                repository.inserted.getValidationCode());
        Assert.assertEquals(Integer.valueOf(1),
                repository.updates.get(0).getStatus());
        Assert.assertEquals(Integer.valueOf(0),
                repository.updates.get(1).getStatus());
        Assert.assertEquals(Integer.valueOf(-1),
                repository.updates.get(2).getStatus());
        Assert.assertEquals("DELETED", deleted.effectiveStatus());
    }

    private FieldValidationGovernedResourceAdapter adapter(
            FakeRepository repository) {
        return new FieldValidationGovernedResourceAdapter(
                repository.validationMapper(), repository.projectMapper(),
                repository.inputFieldMapper());
    }

    private FakeRepository validRepository() {
        FakeRepository repository = new FakeRepository();
        repository.project = new RuleProject();
        repository.project.setId(7L);
        return repository;
    }

    private RuleFieldValidation rule(Long id, Long projectId,
                                     String scope, String code) {
        RuleFieldValidation rule = new RuleFieldValidation();
        rule.setId(id);
        rule.setProjectId(projectId);
        rule.setScope(scope);
        rule.setValidationCode(code);
        rule.setValidationName("规则");
        rule.setValidationType("REQUIRED");
        rule.setErrorMessage("必填");
        rule.setStatus(1);
        return rule;
    }

    private boolean hasError(List<GovernanceIssue> issues, String code) {
        return issues.stream().anyMatch(issue -> issue.isError()
                && code.equals(issue.code()));
    }

    private static class FakeRepository {
        private RuleFieldValidation current;
        private RuleFieldValidation inserted;
        private RuleProject project;
        private Long duplicateCount = 0L;
        private List<RuleDefinitionInputField> inputFields = List.of();
        private final List<RuleFieldValidation> updates = new ArrayList<>();

        private RuleFieldValidationMapper validationMapper() {
            return proxy(RuleFieldValidationMapper.class, (method, args) ->
                    switch (method) {
                        case "selectById" -> current;
                        case "selectCount" -> duplicateCount;
                        case "insert" -> {
                            inserted = copy((RuleFieldValidation) args[0]);
                            ((RuleFieldValidation) args[0]).setId(41L);
                            yield 1;
                        }
                        case "updateById" -> {
                            updates.add(copy((RuleFieldValidation) args[0]));
                            yield 1;
                        }
                        default -> null;
                    });
        }

        private RuleProjectMapper projectMapper() {
            return proxy(RuleProjectMapper.class, (method, args) ->
                    "selectById".equals(method) ? project : null);
        }

        private RuleDefinitionInputFieldMapper inputFieldMapper() {
            return proxy(RuleDefinitionInputFieldMapper.class,
                    (method, args) -> "selectList".equals(method)
                            ? inputFields : null);
        }

        private RuleFieldValidation copy(RuleFieldValidation source) {
            RuleFieldValidation copy = new RuleFieldValidation();
            copy.setId(source.getId());
            copy.setProjectId(source.getProjectId());
            copy.setScope(source.getScope());
            copy.setValidationCode(source.getValidationCode());
            copy.setValidationName(source.getValidationName());
            copy.setValidationType(source.getValidationType());
            copy.setValidationValue(source.getValidationValue());
            copy.setErrorMessage(source.getErrorMessage());
            copy.setDescription(source.getDescription());
            copy.setStatus(source.getStatus());
            return copy;
        }

        private <T> T proxy(Class<T> type, MapperCall call) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(),
                    new Class<?>[]{type}, (proxy, method, args) -> {
                        Object value = call.invoke(method.getName(), args);
                        if (value != null
                                || !method.getReturnType().isPrimitive()) {
                            return value;
                        }
                        if (method.getReturnType() == boolean.class) return false;
                        if (method.getReturnType() == long.class) return 0L;
                        return 0;
                    }));
        }
    }

    @FunctionalInterface
    private interface MapperCall {
        Object invoke(String method, Object[] args);
    }
}
