package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteRequest;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteResult;
import com.hengshucredit.rule.model.dto.RuleExperimentGroupResult;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleExperiment;
import com.hengshucredit.rule.model.entity.RuleExperimentExecutionLog;
import com.hengshucredit.rule.model.entity.RuleExperimentGroup;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleExperimentVersion;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.mapper.RuleExperimentExecutionLogMapper;
import com.hengshucredit.rule.server.mapper.RuleExperimentGroupMapper;
import com.hengshucredit.rule.server.mapper.RuleExperimentMapper;
import com.hengshucredit.rule.server.mapper.RuleExperimentVersionMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RuleExperimentService extends ServiceImpl<RuleExperimentMapper, RuleExperiment> {

    private static final String ROUTING_RATIO = "RATIO";
    private static final String ROUTING_CONDITION = "CONDITION";
    private static final String GROUP_CHAMPION = "CHAMPION";
    private static final String GROUP_CHALLENGER = "CHALLENGER";
    private static final String GROUP_TEST = "TEST";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    @Resource
    private RuleExperimentGroupMapper groupMapper;

    @Resource
    private RuleExperimentExecutionLogMapper executionLogMapper;

    @Resource
    private RuleExperimentVersionMapper experimentVersionMapper;

    @Resource
    private RulePublishedMapper publishedMapper;

    @Resource
    private RuleExecuteService executeService;

    @Resource
    private QLExpressEngine qlExpressEngine;

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private ExecutionParameterBinder executionParameterBinder;

    @Resource
    private RuleFieldAnalyzer ruleFieldAnalyzer;

    public IPage<RuleExperiment> pageExperiments(int pageNum, int pageSize, Long projectId,
                                                 Integer status, String keyword) {
        LambdaQueryWrapper<RuleExperiment> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(RuleExperiment::getProjectId, projectId);
        }
        if (status != null) {
            wrapper.eq(RuleExperiment::getStatus, status);
        }
        if (hasText(keyword)) {
            wrapper.and(w -> w.like(RuleExperiment::getExperimentCode, keyword)
                    .or()
                    .like(RuleExperiment::getExperimentName, keyword));
        }
        wrapper.orderByDesc(RuleExperiment::getCreateTime);
        IPage<RuleExperiment> page = page(new Page<>(pageNum, pageSize), wrapper);
        fillGroups(page.getRecords());
        return page;
    }

    public RuleExperiment getDetail(Long id) {
        RuleExperiment experiment = getById(id);
        if (experiment != null) {
            experiment.setGroups(listGroups(id));
        }
        return experiment;
    }

    public List<Long> listReferencedDefinitionIds(Long experimentId) {
        RuleExperiment experiment = getDetail(experimentId);
        if (experiment == null) return Collections.emptyList();
        Set<String> ruleCodes = new LinkedHashSet<>();
        if (hasText(experiment.getConditionRuleCode())) ruleCodes.add(experiment.getConditionRuleCode());
        for (RuleExperimentGroup group : experiment.getGroups() == null ? Collections.<RuleExperimentGroup>emptyList() : experiment.getGroups()) {
            if (hasText(group.getRuleCode())) ruleCodes.add(group.getRuleCode());
        }
        Set<Long> definitionIds = new LinkedHashSet<>();
        for (String ruleCode : ruleCodes) {
            LambdaQueryWrapper<RulePublished> wrapper = new LambdaQueryWrapper<RulePublished>()
                    .eq(RulePublished::getRuleCode, ruleCode)
                    .eq(RulePublished::getStatus, 1)
                    .orderByDesc(RulePublished::getVersion);
            if (hasText(experiment.getProjectCode())) {
                wrapper.eq(RulePublished::getProjectCode, experiment.getProjectCode());
            }
            List<RulePublished> publishedRules = publishedMapper.selectList(wrapper);
            if (publishedRules != null && !publishedRules.isEmpty() && publishedRules.get(0).getDefinitionId() != null) {
                definitionIds.add(publishedRules.get(0).getDefinitionId());
            }
        }
        return new ArrayList<>(definitionIds);
    }

    public RuleFieldAnalyzer.ResolvedFields resolveTestFields(Long experimentId) {
        RuleExperiment experiment = getDetail(experimentId);
        if (experiment == null) {
            throw new IllegalArgumentException("分流实验不存在: " + experimentId);
        }
        List<RuleDefinitionInputField> inputs = new ArrayList<>();
        List<RuleDefinitionOutputField> outputs = new ArrayList<>();
        for (Long definitionId : listReferencedDefinitionIds(experimentId)) {
            List<RuleDefinitionInputField> ruleInputs = definitionService.listInputFields(definitionId);
            List<RuleDefinitionOutputField> ruleOutputs = definitionService.listOutputFields(definitionId);
            if (ruleInputs != null) inputs.addAll(ruleInputs);
            if (ruleOutputs != null) outputs.addAll(ruleOutputs);
        }
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (RuleExperimentGroup group : experiment.getGroups() == null ? Collections.<RuleExperimentGroup>emptyList() : experiment.getGroups()) {
            Map<String, Object> node = new LinkedHashMap<>();
            if (hasText(group.getConditionConfig())) {
                try {
                    node.put("conditionConfig", JSON.parseObject(group.getConditionConfig()));
                } catch (Exception e) {
                    node.put("conditionExpression", group.getConditionConfig());
                }
            }
            if (hasText(group.getConditionExpression())) {
                node.put("conditionExpression", group.getConditionExpression());
            }
            if (!node.isEmpty()) nodes.add(node);
        }
        if (!nodes.isEmpty()) {
            Map<String, Object> conditionModel = new LinkedHashMap<>();
            conditionModel.put("nodes", nodes);
            RuleFieldAnalyzer.ResolvedFields conditionFields = ruleFieldAnalyzer.resolveFields(
                    null, JSON.toJSONString(conditionModel), "FLOW", experiment.getProjectId());
            inputs.addAll(conditionFields.getInputFields());
        }
        if (hasText(experiment.getRequestKeyPath())) {
            String path = experiment.getRequestKeyPath().trim().replaceFirst("^\\$\\.", "");
            RuleDefinitionInputField requestKey = new RuleDefinitionInputField();
            requestKey.setFieldName(path);
            requestKey.setScriptName(path);
            requestKey.setFieldLabel(path);
            requestKey.setFieldType("STRING");
            requestKey.setRefType("VARIABLE");
            requestKey.setStatus(1);
            inputs.add(requestKey);
        }
        return new RuleFieldAnalyzer.ResolvedFields(
                deduplicateInputs(inputs), deduplicateOutputs(outputs));
    }

    public IPage<RuleExperimentExecutionLog> pageExecutionLogs(int pageNum, int pageSize, Long experimentId,
                                                               String experimentCode, String requestKey,
                                                               String stage, String groupCode, Integer success) {
        LambdaQueryWrapper<RuleExperimentExecutionLog> wrapper = new LambdaQueryWrapper<>();
        if (experimentId != null) {
            wrapper.eq(RuleExperimentExecutionLog::getExperimentId, experimentId);
        }
        if (hasText(experimentCode)) {
            wrapper.eq(RuleExperimentExecutionLog::getExperimentCode, experimentCode);
        }
        if (hasText(requestKey)) {
            wrapper.like(RuleExperimentExecutionLog::getRequestKey, requestKey);
        }
        if (hasText(stage)) {
            wrapper.eq(RuleExperimentExecutionLog::getStage, stage);
        }
        if (hasText(groupCode)) {
            wrapper.like(RuleExperimentExecutionLog::getGroupCode, groupCode);
        }
        if (success != null) {
            wrapper.eq(RuleExperimentExecutionLog::getSuccess, success);
        }
        wrapper.orderByDesc(RuleExperimentExecutionLog::getCreateTime);
        return executionLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Transactional
    public RuleExperiment saveExperiment(RuleExperiment experiment) {
        normalizeExperiment(experiment);
        validateExperiment(experiment);
        if (experiment.getId() == null) {
            save(experiment);
        } else {
            updateById(experiment);
            groupMapper.delete(new LambdaQueryWrapper<RuleExperimentGroup>()
                    .eq(RuleExperimentGroup::getExperimentId, experiment.getId()));
        }
        if (experiment.getGroups() != null) {
            for (RuleExperimentGroup group : experiment.getGroups()) {
                normalizeGroup(group);
                group.setExperimentId(experiment.getId());
                groupMapper.insert(group);
            }
        }
        saveVersionSnapshot(experiment.getId(), "save");
        return getDetail(experiment.getId());
    }

    @Transactional
    public void deleteExperiment(Long id) {
        groupMapper.delete(new LambdaQueryWrapper<RuleExperimentGroup>()
                .eq(RuleExperimentGroup::getExperimentId, id));
        removeById(id);
    }

    public RuleExperimentExecuteResult execute(String experimentCode, RuleExperimentExecuteRequest request) {
        long start = System.currentTimeMillis();
        RuleExperiment experiment = findEnabledExperiment(experimentCode);
        if (experiment == null) {
            throw new IllegalArgumentException("分流实验不存在或未启用: " + experimentCode);
        }
        List<RuleExperimentGroup> groups = listGroups(experiment.getId());
        validateRuntimeGroups(experiment, groups);

        Map<String, Object> params = request == null || request.getParams() == null
                ? Collections.emptyMap()
                : request.getParams();
        params = bindExperimentParams(experiment, params);
        String requestKey = resolveRequestKey(experiment, request, params);
        LocalDateTime requestTime = resolveRequestTime(request, params);
        String clientAppName = request == null ? null : request.getClientAppName();

        RuleExperimentExecuteResult result = new RuleExperimentExecuteResult();
        result.setExperimentCode(experiment.getExperimentCode());
        result.setExperimentName(experiment.getExperimentName());
        result.setRequestKey(requestKey);

        RouteChoice productionChoice = chooseProductionGroup(experiment, groups, params, requestKey, clientAppName);
        RuleExperimentGroupResult productionResult = runGroup(experiment, productionChoice.group, params,
                requestKey, requestTime, clientAppName, "PRODUCTION", productionChoice.reason, true);
        result.setProductionGroup(productionResult);
        result.getTags().add(productionChoice.group.getGroupCode());

        List<RouteChoice> testChoices = chooseTestGroups(experiment, groups, params, requestKey);
        for (RouteChoice testChoice : testChoices) {
            RuleExperimentGroup testGroup = testChoice.group;
            if (hasExecutedTestGroup(experiment.getId(), testGroup.getId(), requestKey)) {
                RuleExperimentGroupResult skipped = skippedTestResult(testGroup, "同一请求已执行过测试组，跳过重复空跑");
                result.getTestGroups().add(skipped);
                continue;
            }
            boolean invokeExternal = testGroup.getInvokeExternalSource() == null || testGroup.getInvokeExternalSource() == 1;
            RuleExperimentGroupResult testResult = runGroup(experiment, testGroup, params,
                    requestKey, requestTime, clientAppName, "TEST", testChoice.reason, invokeExternal);
            result.getTestGroups().add(testResult);
            result.getTags().add(testGroup.getGroupCode());
        }

        result.setSuccess(productionResult.isSuccess());
        result.setErrorMessage(productionResult.getErrorMessage());
        result.setExecuteTimeMs(System.currentTimeMillis() - start);
        return result;
    }

    private Map<String, Object> bindExperimentParams(RuleExperiment experiment, Map<String, Object> params) {
        return executionParameterBinder.bindRuleInputs(
                resolveTestFields(experiment.getId()).getInputFields(), params);
    }

    private List<RuleDefinitionInputField> deduplicateInputs(List<RuleDefinitionInputField> inputs) {
        Map<String, RuleDefinitionInputField> dedup = new LinkedHashMap<>();
        for (RuleDefinitionInputField field : inputs) {
            String key = firstText(field.getScriptName(), field.getFieldName());
            if (key != null) dedup.putIfAbsent(key, field);
        }
        return new ArrayList<>(dedup.values());
    }

    private List<RuleDefinitionOutputField> deduplicateOutputs(List<RuleDefinitionOutputField> outputs) {
        Map<String, RuleDefinitionOutputField> dedup = new LinkedHashMap<>();
        for (RuleDefinitionOutputField field : outputs) {
            String key = firstText(field.getScriptName(), field.getFieldName());
            if (key != null) dedup.putIfAbsent(key, field);
        }
        return new ArrayList<>(dedup.values());
    }

    private RouteChoice chooseProductionGroup(RuleExperiment experiment, List<RuleExperimentGroup> groups,
                                              Map<String, Object> params, String requestKey,
                                              String clientAppName) {
        List<RuleExperimentGroup> productionGroups = productionGroups(groups);
        RuleExperimentGroup champion = championGroup(groups);
        if (ROUTING_CONDITION.equals(experiment.getRoutingMode()) && hasInlineConditionGroups(productionGroups)) {
            RuleExperimentGroup fallback = null;
            for (RuleExperimentGroup group : productionGroups) {
                if (isFallbackGroup(group)) {
                    if (fallback == null) {
                        fallback = group;
                    }
                    continue;
                }
                if (matchesConditionGroup(group, params)) {
                    return new RouteChoice(group, "冠军挑战条件分流命中: " + group.getGroupCode());
                }
            }
            if (fallback != null) {
                return new RouteChoice(fallback, "冠军挑战条件未命中，执行兜底动作");
            }
            return new RouteChoice(champion, "冠军挑战条件未命中，回退冠军组");
        }
        if (ROUTING_CONDITION.equals(experiment.getRoutingMode()) && hasText(experiment.getConditionRuleCode())) {
            RulePublished routingRule = findPublishedRule(experiment, experiment.getConditionRuleCode());
            RuleExecuteService.ExecutionOutcome outcome = executeService.executePublishedWithOptions(
                    routingRule, params, experiment.getProjectId(), clientAppName,
                    VariableResolveOptions.defaults(), "EXPERIMENT_ROUTE");
            String groupCode = extractRouteGroupCode(outcome.getResult().getResult());
            RuleExperimentGroup matched = findGroupByRouteValue(productionGroups, groupCode);
            if (matched != null) {
                return new RouteChoice(matched, "条件分流规则返回组编码: " + groupCode);
            }
            return new RouteChoice(champion, "条件分流未命中有效组，回退冠军组");
        }
        int bucket = routeBucket(experiment.getExperimentCode(), requestKey);
        BigDecimal cursor = BigDecimal.ZERO;
        for (RuleExperimentGroup group : productionGroups) {
            cursor = cursor.add(group.getTrafficRatio() == null ? BigDecimal.ZERO : group.getTrafficRatio());
            if (new BigDecimal(bucket).compareTo(cursor) < 0) {
                return new RouteChoice(group, "比例分流命中桶位: " + bucket);
            }
        }
        return new RouteChoice(champion, "比例分流未命中有效区间，回退冠军组");
    }

    private List<RouteChoice> chooseTestGroups(RuleExperiment experiment, List<RuleExperimentGroup> groups,
                                               Map<String, Object> params, String requestKey) {
        List<RuleExperimentGroup> testGroups = activeGroups(groups, GROUP_TEST);
        if (testGroups.isEmpty()) {
            return Collections.emptyList();
        }
        if (ROUTING_RATIO.equals(experiment.getTestRoutingMode())) {
            RuleExperimentGroup chosen = chooseRatioGroup(testGroups, experiment.getExperimentCode() + ":TEST", requestKey);
            if (chosen == null) {
                chosen = testGroups.get(0);
            }
            return Collections.singletonList(new RouteChoice(chosen, "测试组比例分流命中"));
        }

        List<RouteChoice> choices = new ArrayList<>();
        RuleExperimentGroup fallback = null;
        boolean exclusive = experiment.getTestExclusive() == null || experiment.getTestExclusive() == 1;
        for (RuleExperimentGroup testGroup : testGroups) {
            if (isFallbackGroup(testGroup)) {
                if (fallback == null) {
                    fallback = testGroup;
                }
                continue;
            }
            if (!matchesConditionGroup(testGroup, params)) {
                continue;
            }
            choices.add(new RouteChoice(testGroup, "测试组条件命中"));
            if (exclusive) {
                break;
            }
        }
        if (choices.isEmpty() && fallback != null) {
            choices.add(new RouteChoice(fallback, "测试组条件未命中，执行兜底动作"));
        }
        return choices;
    }

    private RuleExperimentGroup chooseRatioGroup(List<RuleExperimentGroup> groups, String experimentCode, String requestKey) {
        int bucket = routeBucket(experimentCode, requestKey);
        BigDecimal cursor = BigDecimal.ZERO;
        for (RuleExperimentGroup group : groups) {
            cursor = cursor.add(group.getTrafficRatio() == null ? BigDecimal.ZERO : group.getTrafficRatio());
            if (new BigDecimal(bucket).compareTo(cursor) < 0) {
                return group;
            }
        }
        return null;
    }

    private RuleExperimentGroupResult runGroup(RuleExperiment experiment, RuleExperimentGroup group,
                                               Map<String, Object> params, String requestKey,
                                               LocalDateTime requestTime, String clientAppName,
                                               String stage, String routeReason,
                                               boolean invokeExternalSource) {
        RulePublished published = findPublishedRule(experiment, group.getRuleCode());
        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setSkipApiSources(!invokeExternalSource);
        if (GROUP_TEST.equals(group.getGroupType())) {
            options.setListMatchTime(requestTime);
        }
        RuleExecuteService.ExecutionOutcome outcome = executeService.executePublishedWithOptions(
                published, params, experiment.getProjectId(), clientAppName, options, "EXPERIMENT_" + stage);
        RuleExperimentGroupResult groupResult = toGroupResult(group, outcome, stage, routeReason);
        saveExecutionLog(experiment, group, groupResult, outcome.getExecuteParams(), requestKey, stage, routeReason);
        return groupResult;
    }

    private RuleExperimentGroupResult toGroupResult(RuleExperimentGroup group,
                                                    RuleExecuteService.ExecutionOutcome outcome,
                                                    String stage, String routeReason) {
        RuleResult ruleResult = outcome.getResult();
        RuleExperimentGroupResult result = new RuleExperimentGroupResult();
        result.setStage(stage);
        result.setGroupCode(group.getGroupCode());
        result.setGroupName(group.getGroupName());
        result.setGroupType(group.getGroupType());
        result.setRuleCode(group.getRuleCode());
        result.setRouteReason(routeReason);
        result.setMatched(true);
        result.setSuccess(ruleResult.isSuccess());
        result.setResult(ruleResult.getResult());
        result.setErrorMessage(ruleResult.getErrorMessage());
        result.setExecuteTimeMs(ruleResult.getExecuteTimeMs());
        result.setTraces(ruleResult.getTraces());
        result.setResolvedParams(new LinkedHashMap<>(outcome.getExecuteParams()));
        return result;
    }

    private void saveExecutionLog(RuleExperiment experiment, RuleExperimentGroup group,
                                  RuleExperimentGroupResult result, Map<String, Object> inputParams,
                                  String requestKey, String stage, String routeReason) {
        RuleExperimentExecutionLog log = new RuleExperimentExecutionLog();
        log.setExperimentId(experiment.getId());
        log.setExperimentCode(experiment.getExperimentCode());
        log.setRequestKey(requestKey);
        log.setStage(stage);
        log.setGroupId(group.getId());
        log.setGroupCode(group.getGroupCode());
        log.setGroupName(group.getGroupName());
        log.setGroupType(group.getGroupType());
        log.setRuleCode(group.getRuleCode());
        log.setRouteReason(routeReason);
        log.setSuccess(result.isSuccess() ? 1 : 0);
        log.setInputParams(toJsonSafely(inputParams));
        log.setOutputResult(toJsonSafely(result.getResult()));
        log.setTraceInfo(toJsonSafely(result.getTraces()));
        log.setErrorMessage(result.getErrorMessage());
        log.setExecuteTimeMs(result.getExecuteTimeMs());
        executionLogMapper.insert(log);
    }

    private String toJsonSafely(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JSON.toJSONString(value);
        } catch (StackOverflowError e) {
            return "{\"error\":\"JSON_SERIALIZE_STACK_OVERFLOW\"}";
        } catch (Exception e) {
            return "{\"error\":\"JSON_SERIALIZE_FAILED\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private RuleExperimentGroupResult skippedTestResult(RuleExperimentGroup group, String reason) {
        RuleExperimentGroupResult result = new RuleExperimentGroupResult();
        result.setStage("TEST");
        result.setGroupCode(group.getGroupCode());
        result.setGroupName(group.getGroupName());
        result.setGroupType(group.getGroupType());
        result.setRuleCode(group.getRuleCode());
        result.setRouteReason(reason);
        result.setMatched(false);
        result.setSkipped(true);
        result.setSuccess(true);
        return result;
    }

    private boolean matchesConditionGroup(RuleExperimentGroup group, Map<String, Object> params) {
        if (!hasText(group.getConditionExpression())) {
            return true;
        }
        RuleResult result = qlExpressEngine.execute(group.getConditionExpression(), params, false);
        Object value = result.getResult();
        return result.isSuccess() && Boolean.TRUE.equals(value);
    }

    private boolean hasExecutedTestGroup(Long experimentId, Long groupId, String requestKey) {
        if (!hasText(requestKey)) {
            return false;
        }
        return executionLogMapper.selectCount(new LambdaQueryWrapper<RuleExperimentExecutionLog>()
                .eq(RuleExperimentExecutionLog::getExperimentId, experimentId)
                .eq(RuleExperimentExecutionLog::getGroupId, groupId)
                .eq(RuleExperimentExecutionLog::getRequestKey, requestKey)
                .eq(RuleExperimentExecutionLog::getStage, "TEST")) > 0;
    }

    private RulePublished findPublishedRule(RuleExperiment experiment, String ruleCode) {
        if (!hasText(ruleCode)) {
            throw new IllegalArgumentException("实验组缺少规则编码");
        }
        LambdaQueryWrapper<RulePublished> wrapper = new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getRuleCode, ruleCode)
                .eq(RulePublished::getStatus, 1);
        if (hasText(experiment.getProjectCode())) {
            wrapper.eq(RulePublished::getProjectCode, experiment.getProjectCode());
        }
        RulePublished published = publishedMapper.selectOne(wrapper);
        if (published == null) {
            throw new IllegalArgumentException("已发布规则不存在或未启用: " + ruleCode);
        }
        return published;
    }

    private void validateExperiment(RuleExperiment experiment) {
        List<RuleExperimentGroup> groups = experiment.getGroups() == null
                ? Collections.emptyList()
                : experiment.getGroups();
        validateRuntimeGroups(experiment, groups);
    }

    private void validateRuntimeGroups(RuleExperiment experiment, List<RuleExperimentGroup> groups) {
        if (championGroup(groups) == null) {
            throw new IllegalArgumentException("分流实验必须且只能配置一组冠军组");
        }
        int championCount = activeGroups(groups, GROUP_CHAMPION).size();
        if (championCount != 1) {
            throw new IllegalArgumentException("分流实验必须且只能配置一组冠军组");
        }
        if (ROUTING_RATIO.equals(experiment.getRoutingMode())) {
            BigDecimal total = BigDecimal.ZERO;
            for (RuleExperimentGroup group : productionGroups(groups)) {
                total = total.add(group.getTrafficRatio() == null ? BigDecimal.ZERO : group.getTrafficRatio());
            }
            if (total.compareTo(ONE_HUNDRED) != 0) {
                throw new IllegalArgumentException("冠军组和挑战组分流比例之和必须为100%");
            }
        }
        if (ROUTING_CONDITION.equals(experiment.getRoutingMode())
                && !hasText(experiment.getConditionRuleCode())
                && hasInlineConditionGroups(productionGroups(groups))
                && !hasFallbackGroup(productionGroups(groups))) {
            throw new IllegalArgumentException("冠军挑战条件分流必须配置兜底动作");
        }

        List<RuleExperimentGroup> testGroups = activeGroups(groups, GROUP_TEST);
        if (ROUTING_RATIO.equals(experiment.getTestRoutingMode()) && !testGroups.isEmpty()) {
            BigDecimal total = BigDecimal.ZERO;
            for (RuleExperimentGroup group : testGroups) {
                total = total.add(group.getTrafficRatio() == null ? BigDecimal.ZERO : group.getTrafficRatio());
            }
            if (total.compareTo(ONE_HUNDRED) != 0) {
                throw new IllegalArgumentException("测试组分流比例之和必须为100%");
            }
        }
        if (ROUTING_CONDITION.equals(experiment.getTestRoutingMode())
                && hasVisualConditionGroups(testGroups)
                && !hasFallbackGroup(testGroups)) {
            throw new IllegalArgumentException("测试组条件分流必须配置兜底动作");
        }
    }

    private List<RuleExperimentGroup> productionGroups(List<RuleExperimentGroup> groups) {
        List<RuleExperimentGroup> result = new ArrayList<>();
        for (RuleExperimentGroup group : groups) {
            if (isActive(group) && (GROUP_CHAMPION.equals(group.getGroupType()) || GROUP_CHALLENGER.equals(group.getGroupType()))) {
                result.add(group);
            }
        }
        result.sort(groupComparator());
        return result;
    }

    private boolean hasInlineConditionGroups(List<RuleExperimentGroup> groups) {
        for (RuleExperimentGroup group : groups) {
            if (hasText(group.getConditionConfig()) || hasText(group.getConditionExpression())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasVisualConditionGroups(List<RuleExperimentGroup> groups) {
        for (RuleExperimentGroup group : groups) {
            if (hasText(group.getConditionConfig())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasFallbackGroup(List<RuleExperimentGroup> groups) {
        for (RuleExperimentGroup group : groups) {
            if (isFallbackGroup(group)) {
                return true;
            }
        }
        return false;
    }

    private List<RuleExperimentGroup> activeGroups(List<RuleExperimentGroup> groups, String groupType) {
        List<RuleExperimentGroup> result = new ArrayList<>();
        for (RuleExperimentGroup group : groups) {
            if (isActive(group) && groupType.equals(group.getGroupType())) {
                result.add(group);
            }
        }
        result.sort(groupComparator());
        return result;
    }

    private RuleExperimentGroup championGroup(List<RuleExperimentGroup> groups) {
        List<RuleExperimentGroup> champions = activeGroups(groups, GROUP_CHAMPION);
        return champions.isEmpty() ? null : champions.get(0);
    }

    private Comparator<RuleExperimentGroup> groupComparator() {
        return Comparator.comparing(g -> g.getSortOrder() == null ? 0 : g.getSortOrder());
    }

    private boolean isActive(RuleExperimentGroup group) {
        return group != null && (group.getStatus() == null || group.getStatus() == 1);
    }

    private RuleExperimentGroup findGroupByRouteValue(List<RuleExperimentGroup> groups, String routeValue) {
        if (!hasText(routeValue)) {
            return null;
        }
        for (RuleExperimentGroup group : groups) {
            if (routeValue.equals(group.getGroupCode()) || routeValue.equals(group.getConditionValue())) {
                return group;
            }
        }
        return null;
    }

    private String extractRouteGroupCode(Object routeResult) {
        if (routeResult instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) routeResult;
            Object value = firstNonNull(map.get("groupCode"), map.get("routeGroup"), map.get("experimentGroup"), map.get("result"));
            return value == null ? null : String.valueOf(value);
        }
        return routeResult == null ? null : String.valueOf(routeResult);
    }

    private int routeBucket(String experimentCode, String requestKey) {
        if (!hasText(requestKey)) {
            return (int) (Math.random() * 100);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest((experimentCode + ":" + requestKey).getBytes(StandardCharsets.UTF_8));
            int value = 0;
            for (int i = 0; i < 4; i++) {
                value = (value << 8) | (bytes[i] & 0xff);
            }
            return Math.floorMod(value, 100);
        } catch (Exception e) {
            return Math.floorMod((experimentCode + ":" + requestKey).hashCode(), 100);
        }
    }

    private RuleExperiment findEnabledExperiment(String experimentCode) {
        if (!hasText(experimentCode)) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<RuleExperiment>()
                .eq(RuleExperiment::getExperimentCode, experimentCode)
                .eq(RuleExperiment::getStatus, 1));
    }

    private List<RuleExperimentGroup> listGroups(Long experimentId) {
        if (experimentId == null) {
            return Collections.emptyList();
        }
        return groupMapper.selectList(new LambdaQueryWrapper<RuleExperimentGroup>()
                .eq(RuleExperimentGroup::getExperimentId, experimentId)
                .orderByAsc(RuleExperimentGroup::getSortOrder)
                .orderByAsc(RuleExperimentGroup::getId));
    }

    private void fillGroups(List<RuleExperiment> experiments) {
        if (experiments == null) {
            return;
        }
        for (RuleExperiment experiment : experiments) {
            experiment.setGroups(listGroups(experiment.getId()));
        }
    }

    private void normalizeExperiment(RuleExperiment experiment) {
        if (!hasText(experiment.getRoutingMode())) {
            experiment.setRoutingMode(ROUTING_RATIO);
        }
        if (experiment.getStatus() == null) {
            experiment.setStatus(1);
        }
        if (experiment.getTestExclusive() == null) {
            experiment.setTestExclusive(1);
        }
        if (!hasText(experiment.getTestRoutingMode())) {
            experiment.setTestRoutingMode(ROUTING_CONDITION);
        }
        if (!hasText(experiment.getRequestKeyPath())) {
            experiment.setRequestKeyPath("requestId");
        }
    }

    private void normalizeGroup(RuleExperimentGroup group) {
        if (group.getStatus() == null) {
            group.setStatus(1);
        }
        if (group.getInvokeExternalSource() == null) {
            group.setInvokeExternalSource(1);
        }
        if (group.getTrafficRatio() == null) {
            group.setTrafficRatio(BigDecimal.ZERO);
        }
        if (group.getSortOrder() == null) {
            group.setSortOrder(0);
        }
    }

    private String resolveRequestKey(RuleExperiment experiment, RuleExperimentExecuteRequest request,
                                     Map<String, Object> params) {
        if (request != null && hasText(request.getRequestKey())) {
            return request.getRequestKey();
        }
        Object configured = readPath(params, experiment.getRequestKeyPath());
        if (configured != null && hasText(String.valueOf(configured))) {
            return String.valueOf(configured);
        }
        String[] fallbackPaths = {"requestId", "orderNo", "applyNo", "applicationNo", "loanNo"};
        for (String path : fallbackPaths) {
            Object value = readPath(params, path);
            if (value != null && hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private LocalDateTime resolveRequestTime(RuleExperimentExecuteRequest request, Map<String, Object> params) {
        if (request != null && request.getRequestTime() != null) {
            return request.getRequestTime();
        }
        Object value = firstNonNull(readPath(params, "requestTime"), readPath(params, "applyTime"), readPath(params, "createTime"));
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value != null && hasText(String.valueOf(value))) {
            String text = String.valueOf(value).replace('T', ' ');
            try {
                return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception ignored) {
                return LocalDateTime.now();
            }
        }
        return LocalDateTime.now();
    }

    private Object readPath(Object root, String path) {
        if (root == null || !hasText(path)) {
            return null;
        }
        Object current = root;
        for (String part : path.split("\\.")) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) return value.trim();
        }
        return null;
    }

    private boolean isFallbackGroup(RuleExperimentGroup group) {
        if (group == null || !hasText(group.getConditionConfig())) {
            return false;
        }
        try {
            JSONObject json = JSON.parseObject(group.getConditionConfig());
            return Boolean.TRUE.equals(json.getBoolean("fallback"));
        } catch (Exception ignored) {
            return false;
        }
    }

    public List<RuleExperimentVersion> listVersions(Long experimentId) {
        return experimentVersionMapper.selectList(new LambdaQueryWrapper<RuleExperimentVersion>()
                .eq(RuleExperimentVersion::getExperimentId, experimentId)
                .orderByDesc(RuleExperimentVersion::getVersion));
    }

    public RuleExperimentVersion getVersion(Long experimentId, Integer version) {
        return experimentVersionMapper.selectOne(new LambdaQueryWrapper<RuleExperimentVersion>()
                .eq(RuleExperimentVersion::getExperimentId, experimentId)
                .eq(RuleExperimentVersion::getVersion, version));
    }

    public Map<String, Object> compareVersions(Long experimentId, Integer leftVersion, Integer rightVersion) {
        RuleExperimentVersion left = getVersion(experimentId, leftVersion);
        RuleExperimentVersion right = getVersion(experimentId, rightVersion);
        if (left == null || right == null) {
            throw new IllegalArgumentException("Version not found");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("left", left);
        result.put("right", right);
        result.put("experimentChanged", !sameText(left.getExperimentJson(), right.getExperimentJson()));
        result.put("groupsChanged", !sameText(left.getGroupsJson(), right.getGroupsJson()));
        return result;
    }

    @Transactional
    public void rollbackToVersion(Long experimentId, Integer version) {
        RuleExperimentVersion snapshot = getVersion(experimentId, version);
        if (snapshot == null) {
            throw new IllegalArgumentException("Version not found");
        }
        RuleExperiment experiment = JSON.parseObject(snapshot.getExperimentJson(), RuleExperiment.class);
        experiment.setId(experimentId);
        experiment.setGroups(null);
        updateById(experiment);
        groupMapper.delete(new LambdaQueryWrapper<RuleExperimentGroup>()
                .eq(RuleExperimentGroup::getExperimentId, experimentId));
        List<RuleExperimentGroup> groups = JSON.parseArray(snapshot.getGroupsJson(), RuleExperimentGroup.class);
        if (groups != null) {
            for (RuleExperimentGroup group : groups) {
                group.setId(null);
                group.setExperimentId(experimentId);
                groupMapper.insert(group);
            }
        }
        saveVersionSnapshot(experimentId, "rollback to v" + version);
    }

    private void saveVersionSnapshot(Long experimentId, String changeLog) {
        if (experimentId == null) return;
        RuleExperiment experiment = getById(experimentId);
        if (experiment == null) return;
        List<RuleExperimentGroup> groups = listGroups(experimentId);
        RuleExperimentVersion version = new RuleExperimentVersion();
        version.setExperimentId(experimentId);
        version.setVersion(nextVersion(experimentId));
        version.setExperimentJson(JSON.toJSONString(experiment));
        version.setGroupsJson(JSON.toJSONString(groups));
        version.setChangeLog(changeLog);
        version.setPublishTime(LocalDateTime.now());
        experimentVersionMapper.insert(version);
    }

    private int nextVersion(Long experimentId) {
        List<RuleExperimentVersion> versions = listVersions(experimentId);
        if (versions == null || versions.isEmpty()) return 1;
        Integer current = versions.get(0).getVersion();
        return (current == null ? 0 : current) + 1;
    }

    private boolean sameText(String left, String right) {
        if (left == null) return right == null;
        return left.equals(right);
    }

    private static class RouteChoice {
        private final RuleExperimentGroup group;
        private final String reason;

        private RouteChoice(RuleExperimentGroup group, String reason) {
            this.group = group;
            this.reason = reason;
        }
    }
}
