package com.hengshucredit.rule.server.transfer;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.dto.ApiDocScenarioSaveRequest;
import com.hengshucredit.rule.model.dto.ProjectAuthSaveRequest;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.security.ConsoleResourceAccess;
import com.hengshucredit.rule.server.service.ProjectAuthService;
import com.hengshucredit.rule.server.service.RuleApiDocScenarioService;
import com.hengshucredit.rule.server.testdata.TestDataCatalog;
import com.hengshucredit.rule.server.testdata.TestResource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 可随所属资源一起迁移的页面配置。凭据、运行结果归因及源端行 ID 不进入设置清单。 */
@Service
@RequiredArgsConstructor
class TransferPageSettings {
    private final JdbcTemplate jdbc;
    private final ConsoleResourceAccess access;
    private final TestDataCatalog cases;
    private final ProjectAuthService auth;
    private final RuleApiDocScenarioService scenarios;

    private record CaseTable(TestResource resource, String table, String owner) { }
    private static final Map<TransferResourceType, CaseTable> CASES = Map.of(
            TransferResourceType.VARIABLE, new CaseTable(TestResource.VARIABLE, "rule_variable_test_case", "variable_id"),
            TransferResourceType.MODEL, new CaseTable(TestResource.MODEL, "rule_model_test_case", "model_id"),
            TransferResourceType.EXTERNAL_API, new CaseTable(TestResource.API, "rule_api_test_case", "api_id"));
    private static final List<String> AUTH = List.of("authCode", "authName", "authType", "placement", "parameterName",
            "accessPolicyJson", "asyncAccessLogEnabled", "tokenTtlSeconds", "tokenGraceSeconds", "status");
    private static final List<String> SCENARIO = List.of("scenarioName", "description", "requestJson",
            "responseJson", "expectationJson", "businessCodePath", "sortOrder", "includeInDoc", "status");

    List<Map<String,Object>> export(TransferKey key) {
        long owner = key.id();
        if (key.type() == TransferResourceType.PROJECT)
            return auth.listAuths(owner).stream().filter(row -> !"LEGACY_TOKEN".equals(row.getAuthType()))
                    .map(row -> fields(row, AUTH)).toList();
        if (key.type() == TransferResourceType.RULE)
            return scenarios.listByDefinition(owner).stream().map(row -> fields(row, SCENARIO)).toList();
        var spec = CASES.get(key.type());
        if (spec == null) return List.of();
        var rows = jdbc.queryForList("SELECT case_name AS name,case_code AS code,params_json AS paramsJson,"
                + "expected_json AS expectedJson" + (spec.resource == TestResource.VARIABLE ? ",context_definition_id AS contextDefinitionId" : "")
                + " FROM " + spec.table + " WHERE " + spec.owner + "=? ORDER BY case_code", owner);
        for (var row : rows) if (row.get("contextDefinitionId") instanceof Number id)
            access.require("RULE", id.longValue(), "view");
        return rows;
    }

    private static Map<String,Object> fields(Object value, List<String> allowed) {
        var obj = JSON.parseObject(JSON.toJSONString(value));
        Map<String,Object> result = new LinkedHashMap<>();
        for (String key : allowed) if (obj.get(key) != null) result.put(key, obj.get(key));
        return result;
    }

    void apply(TransferBundle.Resource resource, Map<String,Long> ids, boolean overwrite, TransferInstallationBindings.Profile bindings) {
        TransferKey key = TransferKey.parse(resource.key());
        Long owner = ids.get(resource.key());
        if (owner == null || owner <= 0) throw new IllegalArgumentException("附属配置缺少目标资源：" + key.value());
        access.require(key.type().name(), owner, "edit");
        var current = export(new TransferKey(key.type(), owner));
        for (var raw : resource.pageSettings()) {
            var row = new LinkedHashMap<>(raw);
            if (key.type() == TransferResourceType.PROJECT) {
                importAuth(owner, row, current, overwrite, key.value(), bindings);
            } else if (key.type() == TransferResourceType.RULE) {
                importScenario(owner, row, current, overwrite);
            } else {
                var spec = CASES.get(key.type());
                if (spec == null) throw new IllegalArgumentException("该资源不支持附属配置：" + key.type());
                Object source = row.get("contextDefinitionId");
                if (source != null) {
                    Long target = ids.get("RULE:" + source);
                    if (spec.resource != TestResource.VARIABLE || target == null)
                        throw new IllegalArgumentException("变量固定用例需要同时导入其上下文规则");
                    row.put("contextDefinitionId", target);
                }
                var candidate = JSON.parseObject(JSON.toJSONString(row), TestDataCatalog.ResourceCase.class);
                var existing = existing(row, current, "code");
                if (existing != null && sameCase(existing, candidate)) continue;
                requireAction(existing, overwrite, candidate.code());
                Long id = existing == null ? null : jdbc.queryForObject("SELECT id FROM " + spec.table
                        + " WHERE " + spec.owner + "=? AND case_code=?", Long.class, owner, candidate.code());
                cases.saveCase(spec.resource, owner, id, candidate);
            }
        }
    }

    private void importAuth(long owner, Map<String,Object> row, List<Map<String,Object>> current, boolean overwrite,
                            String projectKey, TransferInstallationBindings.Profile bindings) {
        requireFields(row, AUTH);
        var existing = existing(row, current, "authCode");
        if (existing != null && existing.equals(row)) return;
        var request = JSON.parseObject(JSON.toJSONString(row), ProjectAuthSaveRequest.class);
        requireAction(existing, overwrite, request.getAuthCode());
        if (existing == null) {
            if ("BASIC".equals(request.getAuthType())) {
                request.setIdentifier("basic_" + UUID.randomUUID());
                request.setSecret(UUID.randomUUID().toString());
            }
            bindings.bindAuth(projectKey, request);
            auth.createAuth(owner, request);
        } else {
            Long target = auth.listAuths(owner).stream().filter(item -> request.getAuthCode().equals(item.getAuthCode()))
                    .findFirst().orElseThrow().getId();
            auth.updateAuth(owner, target, request);
        }
    }

    private void importScenario(long owner, Map<String,Object> row, List<Map<String,Object>> current, boolean overwrite) {
        requireFields(row, SCENARIO);
        var existing = existing(row, current, "scenarioName");
        if (existing != null && existing.equals(row)) return;
        var request = JSON.parseObject(JSON.toJSONString(row), ApiDocScenarioSaveRequest.class);
        request.setResponseSource("MANUAL");
        requireAction(existing, overwrite, request.getScenarioName());
        if (existing == null) scenarios.create(owner, request);
        else {
            Long target = scenarios.listByDefinition(owner).stream()
                    .filter(item -> request.getScenarioName().equals(item.getScenarioName())).findFirst().orElseThrow().getId();
            scenarios.update(owner, target, request);
        }
    }

    private static Map<String,Object> existing(Map<String,Object> row, List<Map<String,Object>> current, String key) {
        Object value = row.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("附属配置缺少 " + key);
        return current.stream().filter(item -> value.equals(item.get(key))).findFirst().orElse(null);
    }

    private static boolean sameCase(Map<String,Object> existing, TestDataCatalog.ResourceCase candidate) {
        return Objects.equals(existing.get("name"), candidate.name())
                && Objects.equals(existing.get("contextDefinitionId"), candidate.contextDefinitionId())
                && CanonicalJson.sameDocument((String) existing.get("paramsJson"), candidate.paramsJson())
                && CanonicalJson.sameDocument((String) existing.get("expectedJson"), candidate.expectedJson());
    }

    private static void requireAction(Map<String,Object> existing, boolean overwrite, String label) {
        if (existing != null && !overwrite) throw new IllegalArgumentException("附属配置内容冲突：" + label);
    }

    private static void requireFields(Map<String,Object> data, List<String> fields) {
        if (!fields.containsAll(data.keySet())) throw new IllegalArgumentException("附属配置含有不支持的字段");
    }
}
