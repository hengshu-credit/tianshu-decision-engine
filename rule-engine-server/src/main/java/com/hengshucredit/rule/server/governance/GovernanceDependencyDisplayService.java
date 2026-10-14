package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.mapper.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;

/** Read-only labels from the exact dependency revision; never substitute the current production head. */
@Service
public class GovernanceDependencyDisplayService {
    @Resource private GovernedResourceVersionMapper versions;
    @Resource private RuleModelVersionMapper models;
    @Resource private RuleFunctionVersionMapper functions;
    @Resource private RuleDefinitionVersionMapper rules;

    public Map<String, Object> describe(GovernancePreflightReport.ResolvedDependency dependency) {
        Map<String, Object> result = CanonicalJson.readMap(CanonicalJson.write(dependency));
        Metadata metadata = metadata(dependency);
        if (metadata == null) { result.put("metadataAvailable", false); return result; }
        JSONObject snapshot = metadata.snapshot();
        result.put("metadataAvailable", true);
        result.put("resourceName", first(snapshot, "varLabel", "objectLabel", "modelName", "funcName", "ruleName", "apiName", "datasourceName", "projectName", "experimentName", "listName", "validationName", "billingName"));
        result.put("resourceCode", first(snapshot, "varCode", "objectCode", "modelCode", "funcCode", "ruleCode", "apiCode", "datasourceCode", "projectCode", "experimentCode", "listCode", "validationCode", "billingCode"));
        result.put("targetRevisionNo", metadata.revision());
        result.put("valueType", first(snapshot, "varType", "returnType", "modelFormat", "objectType"));
        if ("VARIABLE".equals(dependency.targetResourceType()) && "CONSTANT".equals(snapshot.getString("varSource"))) result.put("displayResourceType", "CONSTANT");
        var selection = dependency.versionSelection();
        if (selection != null && Set.of("DATA_OBJECT", "MODEL_OUTPUT").contains(selection.refType())) {
            String collection = "MODEL_OUTPUT".equals(selection.refType()) ? "outputFields" : "fields";
            if (snapshot.getJSONArray(collection) != null) for (Object raw : snapshot.getJSONArray(collection)) {
                JSONObject field = (JSONObject) JSON.toJSON(raw);
                if (!Objects.equals(selection.refId(), field.getLong("id"))) continue;
                result.put("fieldName", first(field, "varLabel", "fieldLabel", "label"));
                result.put("fieldCode", first(field, "varCode", "fieldName", "featureName", "scriptName"));
                result.put("valueType", first(field, "varType", "fieldType"));
                break;
            }
        }
        return result;
    }

    private Metadata metadata(GovernancePreflightReport.ResolvedDependency dependency) {
        if (dependency.targetVersionId() == null) return null;
        var selection = dependency.versionSelection();
        if (selection != null && "RULE_BINDING".equals(selection.versionSource())) {
            var nativeVersion = loadRule(dependency.targetVersionId());
            if (nativeVersion == null || !Objects.equals(dependency.targetResourceId(), nativeVersion.getDefinitionId())) return null;
            for (var version : ruleHistory(dependency.targetResourceId())) {
                JSONObject snapshot = parse(version.getSnapshotJson());
                if (snapshot != null && nativeVersion.getRevisionId() != null && Objects.equals(nativeVersion.getRevisionId(), snapshot.getLong("_ruleRevisionId")))
                    return new Metadata(snapshot, nativeVersion.getVersion());
            }
            return null;
        }
        if (selection != null && "FUNCTION_HISTORY".equals(selection.versionSource())) {
            var version = loadFunction(dependency.targetVersionId());
            if (version == null || !Objects.equals(dependency.targetResourceId(), version.getFunctionId())) return null;
            JSONObject snapshot = parse(version.getFunctionJson());
            return snapshot == null ? null : new Metadata(snapshot, version.getVersion());
        }
        if (selection != null && "MODEL".equals(dependency.targetResourceType()) && !"BUSINESS_VERSION".equals(selection.versionSource())) {
            var version = loadModel(dependency.targetVersionId());
            if (version == null || !Objects.equals(dependency.targetResourceId(), version.getModelId())) return null;
            JSONObject execution = parse(version.getExecutionSnapshotJson());
            JSONObject snapshot = execution == null ? null : execution.getJSONObject("model");
            return snapshot == null ? null : new Metadata(snapshot, version.getVersion());
        }
        var version = loadGoverned(dependency.targetVersionId());
        if (version == null || !dependency.targetResourceType().equals(version.getResourceType()) || !Objects.equals(dependency.targetResourceId(), version.getResourceId())) return null;
        JSONObject snapshot = parse(version.getSnapshotJson());
        return snapshot == null ? null : new Metadata(snapshot, version.getVersionNo());
    }

    protected GovernedResourceVersion loadGoverned(Long id) { return versions.selectOne(new LambdaQueryWrapper<GovernedResourceVersion>()
            .select(GovernedResourceVersion::getId, GovernedResourceVersion::getResourceType, GovernedResourceVersion::getResourceId, GovernedResourceVersion::getVersionNo, GovernedResourceVersion::getSnapshotJson)
            .eq(GovernedResourceVersion::getId, id)); }
    protected RuleModelVersion loadModel(Long id) { return models.selectOne(new LambdaQueryWrapper<RuleModelVersion>()
            .select(RuleModelVersion::getId, RuleModelVersion::getModelId, RuleModelVersion::getVersion, RuleModelVersion::getExecutionSnapshotJson).eq(RuleModelVersion::getId, id)); }
    protected RuleFunctionVersion loadFunction(Long id) { return functions.selectById(id); }
    protected RuleDefinitionVersion loadRule(Long id) { return rules.selectOne(new LambdaQueryWrapper<RuleDefinitionVersion>()
            .select(RuleDefinitionVersion::getId, RuleDefinitionVersion::getDefinitionId, RuleDefinitionVersion::getVersion, RuleDefinitionVersion::getRevisionId).eq(RuleDefinitionVersion::getId, id)); }
    protected List<GovernedResourceVersion> ruleHistory(Long id) { return versions.selectList(new LambdaQueryWrapper<GovernedResourceVersion>()
            .select(GovernedResourceVersion::getSnapshotJson).eq(GovernedResourceVersion::getResourceType, "RULE").eq(GovernedResourceVersion::getResourceId, id)); }
    private static JSONObject parse(String text) { try { return text == null ? null : JSON.parseObject(text); } catch (com.alibaba.fastjson.JSONException invalid) { return null; } }
    private static String first(JSONObject source, String... keys) { for (String key : keys) { String value = source.getString(key); if (value != null && !value.isBlank()) return value; } return null; }
    private record Metadata(JSONObject snapshot, Integer revision) { }
}
