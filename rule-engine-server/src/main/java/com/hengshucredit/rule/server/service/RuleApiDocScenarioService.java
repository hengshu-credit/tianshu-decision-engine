package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.ApiDocScenarioSaveRequest;
import com.hengshucredit.rule.model.entity.RuleApiDocScenario;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.server.mapper.RuleApiDocScenarioMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RuleApiDocScenarioService {

    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String SOURCE_EXECUTED = "EXECUTED";

    @Resource
    private RuleApiDocScenarioMapper scenarioMapper;

    @Resource
    private RuleDefinitionMapper definitionMapper;

    public List<RuleApiDocScenario> listByDefinition(Long definitionId) {
        requireDefinition(definitionId);
        return scenarioMapper.selectList(new LambdaQueryWrapper<RuleApiDocScenario>()
                .eq(RuleApiDocScenario::getDefinitionId, definitionId)
                .orderByAsc(RuleApiDocScenario::getSortOrder)
                .orderByAsc(RuleApiDocScenario::getId));
    }

    @Transactional
    public RuleApiDocScenario create(Long definitionId, ApiDocScenarioSaveRequest request) {
        RuleDefinition definition = requireDefinition(definitionId);
        RuleApiDocScenario scenario = new RuleApiDocScenario();
        scenario.setDefinitionId(definitionId);
        applyRequest(scenario, request, definition);
        ensureUniqueName(definitionId, scenario.getScenarioName(), null);
        scenarioMapper.insert(scenario);
        return scenario;
    }

    @Transactional
    public RuleApiDocScenario update(Long definitionId, Long scenarioId, ApiDocScenarioSaveRequest request) {
        RuleApiDocScenario scenario = requireScenario(definitionId, scenarioId);
        RuleDefinition definition = requireDefinition(definitionId);
        applyRequest(scenario, request, definition);
        ensureUniqueName(definitionId, scenario.getScenarioName(), scenarioId);
        scenarioMapper.updateById(scenario);
        return scenario;
    }

    @Transactional
    public RuleApiDocScenario copy(Long definitionId, Long scenarioId, String scenarioName) {
        RuleApiDocScenario source = requireScenario(definitionId, scenarioId);
        RuleDefinition definition = requireDefinition(definitionId);
        String normalizedName = requireName(scenarioName);
        ensureUniqueName(definitionId, normalizedName, null);

        RuleApiDocScenario copied = new RuleApiDocScenario();
        copied.setDefinitionId(definitionId);
        copied.setScenarioName(normalizedName);
        copied.setDescription(source.getDescription());
        copied.setRequestJson(source.getRequestJson());
        copied.setResponseJson(source.getResponseJson());
        copied.setResponseSource(source.getResponseSource());
        copied.setOuterCode(source.getOuterCode());
        copied.setBusinessCodePath(source.getBusinessCodePath());
        copied.setBusinessCode(source.getBusinessCode());
        copied.setRuleVersion(currentVersion(definition));
        copied.setIncludeInDoc(0);
        copied.setSortOrder(nextSortOrder(definitionId));
        copied.setStatus(source.getStatus() == null ? 1 : source.getStatus());
        scenarioMapper.insert(copied);
        return copied;
    }

    @Transactional
    public void sort(Long definitionId, List<Long> scenarioIds) {
        requireDefinition(definitionId);
        if (scenarioIds == null) {
            throw new IllegalArgumentException("场景排序不能为空");
        }
        Set<Long> uniqueIds = new HashSet<>(scenarioIds);
        if (uniqueIds.size() != scenarioIds.size() || uniqueIds.contains(null)) {
            throw new IllegalArgumentException("场景排序包含重复或无效 ID");
        }
        for (int index = 0; index < scenarioIds.size(); index++) {
            RuleApiDocScenario scenario = requireScenario(definitionId, scenarioIds.get(index));
            scenario.setSortOrder(index);
            scenarioMapper.updateById(scenario);
        }
    }

    @Transactional
    public void delete(Long definitionId, Long scenarioId) {
        requireScenario(definitionId, scenarioId);
        scenarioMapper.deleteById(scenarioId);
    }

    public List<RuleApiDocScenario> listExportable(Long definitionId, Integer publishedVersion) {
        if (definitionId == null || publishedVersion == null) {
            return Collections.emptyList();
        }
        return scenarioMapper.selectList(new LambdaQueryWrapper<RuleApiDocScenario>()
                .eq(RuleApiDocScenario::getDefinitionId, definitionId)
                .eq(RuleApiDocScenario::getStatus, 1)
                .eq(RuleApiDocScenario::getIncludeInDoc, 1)
                .eq(RuleApiDocScenario::getRuleVersion, publishedVersion)
                .orderByAsc(RuleApiDocScenario::getSortOrder)
                .orderByAsc(RuleApiDocScenario::getId));
    }

    @Transactional
    public void deleteByDefinition(Long definitionId) {
        if (definitionId == null) return;
        scenarioMapper.delete(new LambdaQueryWrapper<RuleApiDocScenario>()
                .eq(RuleApiDocScenario::getDefinitionId, definitionId));
    }

    private void applyRequest(RuleApiDocScenario scenario, ApiDocScenarioSaveRequest request,
                              RuleDefinition definition) {
        if (request == null) {
            throw new IllegalArgumentException("API 测试用例不能为空");
        }
        String scenarioName = requireName(request.getScenarioName());
        requireObject(request.getRequestJson(), "请求报文");
        JSONObject responseObject = requireObject(request.getResponseJson(), "响应报文");
        String businessCodePath = StringUtils.hasText(request.getBusinessCodePath())
                ? request.getBusinessCodePath().trim() : null;

        scenario.setScenarioName(scenarioName);
        scenario.setDescription(trimToLength(request.getDescription(), 512, "场景说明"));
        scenario.setRequestJson(request.getRequestJson());
        scenario.setResponseJson(request.getResponseJson());
        scenario.setResponseSource(normalizeResponseSource(request.getResponseSource()));
        scenario.setOuterCode(readOuterCode(responseObject));
        scenario.setBusinessCodePath(businessCodePath);
        Object businessCode = readPath(responseObject, businessCodePath);
        scenario.setBusinessCode(businessCode == null ? null : String.valueOf(businessCode));
        scenario.setRuleVersion(currentVersion(definition));
        scenario.setIncludeInDoc(binaryFlag(request.getIncludeInDoc(), 0, "是否加入文档"));
        scenario.setSortOrder(request.getSortOrder() == null
                ? nextSortOrder(scenario.getDefinitionId()) : request.getSortOrder());
        scenario.setStatus(binaryFlag(request.getStatus(), 1, "状态"));

        // 保留用户输入的原始 JSON；以上解析仅用于校验和派生展示码。
    }

    private RuleDefinition requireDefinition(Long definitionId) {
        if (definitionId == null) {
            throw new IllegalArgumentException("规则 ID 不能为空");
        }
        RuleDefinition definition = definitionMapper.selectById(definitionId);
        if (definition == null) {
            throw new IllegalArgumentException("规则不存在，id=" + definitionId);
        }
        return definition;
    }

    private RuleApiDocScenario requireScenario(Long definitionId, Long scenarioId) {
        RuleApiDocScenario scenario = scenarioId == null ? null : scenarioMapper.selectById(scenarioId);
        if (scenario == null || !definitionId.equals(scenario.getDefinitionId())) {
            throw new IllegalArgumentException("API 测试用例不存在或不属于当前规则");
        }
        return scenario;
    }

    private JSONObject requireObject(String json, String label) {
        if (!StringUtils.hasText(json)) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        try {
            Object parsed = JSON.parse(json);
            if (!(parsed instanceof JSONObject)) {
                throw new IllegalArgumentException(label + "必须是 JSON 对象");
            }
            return (JSONObject) parsed;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(label + "不是合法 JSON");
        }
    }

    private Object readPath(JSONObject response, String path) {
        if (!StringUtils.hasText(path)) return null;
        String normalized = path.trim().startsWith("$") ? path.trim() : "$." + path.trim();
        try {
            return JSONPath.eval(response, normalized);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("内层业务码路径不合法");
        }
    }

    private Integer readOuterCode(JSONObject response) {
        Object value = response.get("code");
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void ensureUniqueName(Long definitionId, String scenarioName, Long excludeId) {
        LambdaQueryWrapper<RuleApiDocScenario> wrapper = new LambdaQueryWrapper<RuleApiDocScenario>()
                .eq(RuleApiDocScenario::getDefinitionId, definitionId)
                .eq(RuleApiDocScenario::getScenarioName, scenarioName);
        if (excludeId != null) wrapper.ne(RuleApiDocScenario::getId, excludeId);
        Long count = scenarioMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("同一规则下场景名称不能重复");
        }
    }

    private int nextSortOrder(Long definitionId) {
        List<RuleApiDocScenario> scenarios = scenarioMapper.selectList(
                new LambdaQueryWrapper<RuleApiDocScenario>()
                        .eq(RuleApiDocScenario::getDefinitionId, definitionId)
                        .orderByDesc(RuleApiDocScenario::getSortOrder)
                        .last("LIMIT 1"));
        if (scenarios == null || scenarios.isEmpty() || scenarios.get(0).getSortOrder() == null) return 0;
        return scenarios.get(0).getSortOrder() + 1;
    }

    private int currentVersion(RuleDefinition definition) {
        return definition.getCurrentVersion() == null ? 0 : definition.getCurrentVersion();
    }

    private String requireName(String value) {
        String normalized = trimToLength(value, 128, "场景名称");
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("场景名称不能为空");
        }
        return normalized;
    }

    private String normalizeResponseSource(String value) {
        String normalized = StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : SOURCE_MANUAL;
        if (!SOURCE_MANUAL.equals(normalized) && !SOURCE_EXECUTED.equals(normalized)) {
            throw new IllegalArgumentException("响应来源只支持 MANUAL 或 EXECUTED");
        }
        return normalized;
    }

    private int binaryFlag(Integer value, int defaultValue, String label) {
        int normalized = value == null ? defaultValue : value;
        if (normalized != 0 && normalized != 1) {
            throw new IllegalArgumentException(label + "只能为 0 或 1");
        }
        return normalized;
    }

    private String trimToLength(String value, int maxLength, String label) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }
}
