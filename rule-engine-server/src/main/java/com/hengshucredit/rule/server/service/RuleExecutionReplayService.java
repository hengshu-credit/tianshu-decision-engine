package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.server.mapper.DecisionArtifactMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import com.hengshucredit.rule.server.derived.HistoryFieldValues;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 从历史日志构造完全离线的规则诊断执行。回溯不会调用真实 API、数据库、名单或模型，
 * 也不会写入新的执行日志和计费记录。
 */
@Service
public class RuleExecutionReplayService {
    @Resource private RuleExecutionLogService executionLogService;
    @Resource private RuleRuntimeCallLogService runtimeCallLogService;
    @Resource private RulePublishedMapper publishedMapper;
    @Resource private RuleRevisionMapper revisionMapper;
    @Resource private DecisionArtifactMapper artifactMapper;
    @Resource private RuleExecuteService executeService;
    @Resource private RuleVariableService variableService;
    @Resource private RuleProjectService projectService;

    public Map<String, Object> replay(Long logId) {
        RuleExecutionLog log = logId == null ? null : executionLogService.getById(logId);
        if (log == null) throw new IllegalArgumentException("执行日志不存在，无法回溯");
        Long projectId = log.getExecutionProjectId();
        if (projectId == null && log.getProjectCode() != null) {
            var project = projectService.getOne(new LambdaQueryWrapper<com.hengshucredit.rule.model.entity.RuleProject>()
                    .eq(com.hengshucredit.rule.model.entity.RuleProject::getProjectCode, log.getProjectCode())
                    .last("LIMIT 1"));
            projectId = project == null ? null : project.getId();
        }
        List<String> warnings = new ArrayList<>();
        RulePublished currentPublished = findPublished(log.getRuleCode(), log.getProjectCode());
        if (currentPublished == null) throw new IllegalArgumentException("规则当前未发布，无法回溯");
        RulePublished published = resolveReplayPublished(log, currentPublished, warnings);
        boolean historicalArtifact = published != currentPublished;
        if (!historicalArtifact && changedAttribution(log, published)) {
            warnings.add("历史日志对应的规则制品已变化，本次按当前已发布内容执行，结果可能与原执行不同。");
        }
        Map<String, Object> params = parseMap(log.getInputParams());
        if (params == null) {
            params = new LinkedHashMap<>();
            warnings.add("历史日志没有可解析的请求入参，缺失字段将按 null 继续执行。");
        }

        Map<String, RuleVariable> variablesById = new LinkedHashMap<>();
        Map<String, RuleVariable> variablesByCode = new LinkedHashMap<>();
        for (RuleVariable variable : safeVariables(projectId)) {
            if (variable == null || variable.getId() == null) continue;
            variablesById.put(variableKey(variable.getId()), variable);
            if (hasText(scriptName(variable))) variablesByCode.putIfAbsent(scriptName(variable), variable);
        }
        VariableResolutionInvocationCache cache = new VariableResolutionInvocationCache();
        restoreHistoryFields(cache, log.getHistoryFields(), variablesById, warnings);
        List<RuleRuntimeCallLog> sourceLogs = runtimeCallLogService.sourceLogsByRootTraceId(
                log.getTraceId(), projectId);
        restoreSourceLogs(cache, sourceLogs, variablesByCode, warnings);

        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setOfflineReplay(true);
        options.setInvocationCache(cache);
        RuleExecuteService.ExecutionOutcome outcome = executeService.executePublishedWithOptions(
                published, params, projectId, "CONSOLE_REPLAY", options, "REPLAY", null,
                true, false, false);
        RuleResult result = outcome.getResult();

        List<Map<String, Object>> missingSources = cache.replayMissingSources();
        for (Map<String, Object> missing : missingSources) {
            warnings.add("历史来源缺失，已将 " + String.valueOf(missing.get("scriptName"))
                    + " 赋值为 null（" + String.valueOf(missing.get("sourceType")) + "）。");
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("replayed", true);
        response.put("replaySource", historicalArtifact ? "HISTORICAL_ARTIFACT" : "CURRENT_PUBLISHED");
        response.put("sourceLogCount", sourceLogs.size());
        response.put("traceId", result.getTraceId());
        response.put("success", result.isSuccess() ? 1 : 0);
        response.put("errorMessage", result.getErrorMessage());
        response.put("executeTimeMs", result.getExecuteTimeMs());
        response.put("ruleCode", published.getRuleCode());
        response.put("ruleVersion", published.getVersion());
        response.put("revisionId", published.getRevisionId());
        response.put("artifactDigest", published.getArtifactDigest());
        response.put("inputParams", toJson(params));
        response.put("outputResult", toJson(result.getResult()));
        response.put("traceInfo", result.getTraces() == null ? null : toJson(result.getTraces()));
        response.put("warnings", warnings);
        response.put("missingSources", missingSources);
        return response;
    }

    private void restoreHistoryFields(VariableResolutionInvocationCache cache, String raw,
                                      Map<String, RuleVariable> variables, List<String> warnings) {
        Map<String, Object> fields = parseMap(raw);
        if (fields == null) return;
        Object nested = fields.get("fields");
        if (nested instanceof Map<?, ?>) fields = castMap(nested);
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!entry.getKey().startsWith("VARIABLE:")) continue;
            RuleVariable variable = variables.get(entry.getKey());
            if (variable == null) {
                warnings.add("历史字段 " + entry.getKey() + " 在当前规则资源中不存在，无法按 ID 回溯。");
                continue;
            }
            cache.restoreVariableResult(entry.getKey(), scriptName(variable), entry.getValue());
        }
    }

    private void restoreSourceLogs(VariableResolutionInvocationCache cache, List<RuleRuntimeCallLog> logs,
                                   Map<String, RuleVariable> variables, List<String> warnings) {
        if (logs == null) return;
        for (RuleRuntimeCallLog log : logs) {
            if (log == null) continue;
            String action = log.getActionType();
            if ("MODEL_EXECUTE".equals(action)) {
                Map<String, Object> response = parseMap(log.getResponseBody());
                if (log.getTargetRefId() != null && response != null) {
                    cache.restoreResponse("MODEL:" + log.getTargetRefId(), response);
                }
                continue;
            }
            if ("API_ASSIGNMENT".equals(action)) {
                Map<String, Object> history = parseMap(log.getHistoryFields());
                if (history == null || !history.containsKey("value")) continue;
                Object value = history.get("value");
                if (masked(value)) continue;
                restoreVariableByCode(cache, variables, log.getTargetCode(), value, "API", warnings);
                continue;
            }
            if ("DB_VARIABLE_QUERY".equals(action)) {
                Map<String, Object> response = parseMap(log.getResponseBody());
                if (response == null || !response.containsKey("extractedValue")) continue;
                Object value = response.get("extractedValue");
                restoreVariableByCode(cache, variables, log.getTargetCode(), value, "DB", warnings);
                continue;
            }
            if ("LIST_VARIABLE_MATCH".equals(action)) {
                Map<String, Object> response = parseMap(log.getResponseBody());
                if (response == null || !response.containsKey("hit")) continue;
                Object value = response.get("hit");
                RuleVariable variable = variables.get(log.getTargetCode());
                if (variable != null && value != null) {
                    Map<String, Object> config = parseMap(variable.getSourceConfig());
                    if ("NUMBER".equals(config == null ? null : config.get("returnMode"))) {
                        value = Boolean.TRUE.equals(value) ? 1 : 0;
                    }
                    cache.restoreVariableResult("VARIABLE:" + variable.getId(), scriptName(variable), value);
                } else if (variable == null && hasText(log.getTargetCode())) {
                    warnings.add("名单变量 " + log.getTargetCode() + " 在当前规则资源中不存在，无法按历史匹配结果回溯。");
                }
                continue;
            }
            if ("API_INVOKE".equals(action)) {
                Map<String, Object> response = parseMap(log.getResponseBody());
                if (response == null || log.getTargetRefId() == null) continue;
                for (RuleVariable variable : variables.values()) {
                    Map<String, Object> config = parseMap(variable.getSourceConfig());
                    if (!"API".equals(variable.getVarSource()) || config == null
                            || !Objects.equals(asLong(config.get("apiConfigId")), log.getTargetRefId())) continue;
                    String key = "VARIABLE:" + variable.getId();
                    if (cache.hasVariableResult(key)) continue;
                    String path = text(config.get("resultPath"));
                    String resultPath = hasText(path) ? path : "body";
                    if (!HistoryFieldValues.present(response, resultPath)) continue;
                    Object value = HistoryFieldValues.read(response, resultPath);
                    cache.restoreVariableResult(key, scriptName(variable), value);
                }
            }
        }
    }

    private void restoreVariableByCode(VariableResolutionInvocationCache cache,
                                       Map<String, RuleVariable> variables, String code, Object value,
                                       String source, List<String> warnings) {
        if (!hasText(code)) return;
        RuleVariable variable = variables.get(code);
        if (variable == null) {
            warnings.add(source + " 来源变量 " + code + " 在当前规则资源中不存在，无法按历史结果回溯。");
            return;
        }
        cache.restoreVariableResult("VARIABLE:" + variable.getId(), scriptName(variable), value);
    }

    private RulePublished findPublished(String ruleCode, String projectCode) {
        LambdaQueryWrapper<RulePublished> wrapper = new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getRuleCode, ruleCode).eq(RulePublished::getStatus, 1);
        if (hasText(projectCode)) wrapper.eq(RulePublished::getProjectCode, projectCode);
        RulePublished result = publishedMapper.selectOne(wrapper.last("LIMIT 1"));
        if (result != null || !hasText(projectCode)) return result;
        return publishedMapper.selectOne(new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getRuleCode, ruleCode).eq(RulePublished::getStatus, 1)
                .orderByDesc(RulePublished::getPublishTime).last("LIMIT 1"));
    }

    /** 优先按日志记录的不可变制品回溯；旧日志或制品缺失时才回退当前发布版本。 */
    private RulePublished resolveReplayPublished(RuleExecutionLog log, RulePublished current,
                                                 List<String> warnings) {
        if (log.getRevisionId() == null || revisionMapper == null || artifactMapper == null) return current;
        RuleRevision revision = revisionMapper.selectById(log.getRevisionId());
        if (revision == null || revision.getArtifactId() == null) {
            warnings.add("历史日志缺少可执行制品，已回退当前发布版本执行。");
            return current;
        }
        if (log.getRootRuleId() != null && revision.getDefinitionId() != null
                && !Objects.equals(log.getRootRuleId(), revision.getDefinitionId())) {
            warnings.add("历史日志与修订定义不匹配，已回退当前发布版本执行。");
            return current;
        }
        DecisionArtifact artifact = artifactMapper.selectById(revision.getArtifactId());
        if (artifact == null || !hasText(artifact.getArtifactDigest())
                || (hasText(log.getArtifactDigest())
                && !Objects.equals(log.getArtifactDigest(), artifact.getArtifactDigest()))) {
            warnings.add("历史日志对应制品不存在或摘要不一致，已回退当前发布版本执行。");
            return current;
        }
        RulePublished historical = new RulePublished();
        historical.setRuleCode(log.getRuleCode() == null ? current.getRuleCode() : log.getRuleCode());
        historical.setDefinitionId(revision.getDefinitionId());
        historical.setRevisionId(revision.getId());
        historical.setArtifactId(revision.getArtifactId());
        historical.setArtifactDigest(artifact.getArtifactDigest());
        historical.setProjectCode(log.getProjectCode() == null ? current.getProjectCode() : log.getProjectCode());
        historical.setVersion(log.getRuleVersion() == null ? revision.getRevisionNo() : log.getRuleVersion());
        historical.setModelType(current.getModelType());
        historical.setCompiledScript(revision.getCompiledScript());
        historical.setCompiledType(revision.getCompiledType());
        historical.setModelJson(revision.getModelJson());
        historical.setOpenApiConfigJson(revision.getOpenApiConfigJson());
        historical.setStatus(1);
        warnings.add("已按历史日志对应的不可变制品回溯，当前发布版本变化不会影响本次结果。");
        return historical;
    }

    private boolean changedAttribution(RuleExecutionLog log, RulePublished published) {
        return (log.getRevisionId() != null && published.getRevisionId() != null
                && !Objects.equals(log.getRevisionId(), published.getRevisionId()))
                || (log.getRuleVersion() != null && published.getVersion() != null
                && !Objects.equals(log.getRuleVersion(), published.getVersion()))
                || (hasText(log.getArtifactDigest()) && hasText(published.getArtifactDigest())
                && !Objects.equals(log.getArtifactDigest(), published.getArtifactDigest()));
    }

    private List<RuleVariable> safeVariables(Long projectId) {
        if (variableService == null) return Collections.emptyList();
        return variableService.listByProject(projectId, null);
    }

    private String scriptName(RuleVariable variable) {
        if (variable == null) return null;
        return hasText(variable.getScriptName()) ? variable.getScriptName() : variable.getVarCode();
    }

    private String variableKey(Long id) { return "VARIABLE:" + id; }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String raw) {
        if (!hasText(raw)) return null;
        try { return JSON.parseObject(raw, LinkedHashMap.class); }
        catch (RuntimeException ignored) { return null; }
    }

    private Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? null : Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String toJson(Object value) { return value == null ? null : JSON.toJSONString(value); }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private boolean masked(Object value) {
        return value instanceof String && ("******".equals(value) || "[MASKED]".equals(value));
    }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
}
