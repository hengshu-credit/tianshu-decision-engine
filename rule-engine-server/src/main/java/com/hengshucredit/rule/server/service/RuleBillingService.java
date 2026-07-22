package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hengshucredit.rule.model.entity.RuleBillingConfig;
import com.hengshucredit.rule.model.entity.RuleBillingRecord;
import com.hengshucredit.rule.model.entity.RuleBillingSummary;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.mapper.RuleBillingConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleBillingRecordMapper;
import com.hengshucredit.rule.server.mapper.RuleBillingSummaryMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RuleBillingService extends ServiceImpl<RuleBillingConfigMapper, RuleBillingConfig> {

    private static final String TARGET_ENGINE = "ENGINE";
    private static final String TARGET_API = "API";

    @Resource
    private RuleBillingRecordMapper recordMapper;

    @Resource
    private RuleBillingSummaryMapper summaryMapper;

    @Resource
    private RuleProjectMapper projectMapper;

    @Resource
    private RuleDefinitionMapper definitionMapper;

    @Resource
    private ProjectFilterService projectFilterService;

    public IPage<RuleBillingConfig> pageConfigs(int pageNum, int pageSize, String scope, Long projectId,
                                                String projectCode, String projectName,
                                                String billingTarget, String billingCode, Integer status) {
        ProjectFilterService.ProjectMatches projectMatches = projectFilterService.resolve(projectCode, projectName);
        if (projectMatches.isActive() && projectMatches.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }
        LambdaQueryWrapper<RuleBillingConfig> wrapper = new LambdaQueryWrapper<>();
        if (hasText(scope)) {
            wrapper.eq(RuleBillingConfig::getScope, scope);
        }
        if (projectId != null && projectId > 0) {
            wrapper.eq(RuleBillingConfig::getProjectId, projectId);
        }
        if (projectMatches.isActive()) {
            wrapper.in(RuleBillingConfig::getProjectId, projectMatches.getProjectIds());
        }
        if (hasText(billingTarget)) {
            wrapper.eq(RuleBillingConfig::getBillingTarget, billingTarget);
        }
        if (hasText(billingCode)) {
            wrapper.like(RuleBillingConfig::getBillingCode, billingCode);
        }
        if (status != null) {
            wrapper.eq(RuleBillingConfig::getStatus, status);
        }
        wrapper.orderByDesc(RuleBillingConfig::getCreateTime);
        IPage<RuleBillingConfig> page = page(new Page<>(pageNum, pageSize), wrapper);
        fillConfigProjectName(page.getRecords());
        return page;
    }

    public IPage<RuleBillingRecord> pageRecords(int pageNum, int pageSize, String billingTarget, String billingCode,
                                                String projectCode, String projectName, String authType, String authCode,
                                                String tokenCode, String beginTime, String endTime) {
        ProjectFilterService.ProjectMatches projectMatches = projectFilterService.resolve(null, projectName);
        if (projectMatches.isActive() && projectMatches.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }
        LambdaQueryWrapper<RuleBillingRecord> wrapper = new LambdaQueryWrapper<>();
        if (hasText(billingTarget)) {
            wrapper.eq(RuleBillingRecord::getBillingTarget, billingTarget);
        }
        if (hasText(billingCode)) {
            wrapper.like(RuleBillingRecord::getBillingCode, billingCode);
        }
        if (hasText(projectCode)) {
            wrapper.like(RuleBillingRecord::getProjectCode, projectCode);
        }
        if (projectMatches.isActive()) {
            wrapper.in(RuleBillingRecord::getProjectCode, projectMatches.getProjectCodes());
        }
        if (hasText(authType)) {
            wrapper.eq(RuleBillingRecord::getAuthType, authType);
        }
        if (hasText(authCode)) {
            wrapper.like(RuleBillingRecord::getAuthCode, authCode);
        }
        if (hasText(tokenCode)) {
            wrapper.like(RuleBillingRecord::getTokenCode, tokenCode);
        }
        if (hasText(beginTime)) {
            wrapper.ge(RuleBillingRecord::getOccurTime, beginTime + " 00:00:00");
        }
        if (hasText(endTime)) {
            wrapper.le(RuleBillingRecord::getOccurTime, endTime + " 23:59:59");
        }
        wrapper.orderByDesc(RuleBillingRecord::getOccurTime);
        return recordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public IPage<RuleBillingSummary> pageSummaries(int pageNum, int pageSize, String billingTarget,
                                                   String billingCode, String projectCode, String projectName, String authType,
                                                   String authCode,
                                                   String beginDate, String endDate) {
        ProjectFilterService.ProjectMatches projectMatches = projectFilterService.resolve(null, projectName);
        if (projectMatches.isActive() && projectMatches.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }
        LambdaQueryWrapper<RuleBillingSummary> wrapper = new LambdaQueryWrapper<>();
        if (hasText(billingTarget)) {
            wrapper.eq(RuleBillingSummary::getBillingTarget, billingTarget);
        }
        if (hasText(billingCode)) {
            wrapper.like(RuleBillingSummary::getBillingCode, billingCode);
        }
        if (hasText(projectCode)) {
            wrapper.like(RuleBillingSummary::getProjectCode, projectCode);
        }
        if (projectMatches.isActive()) {
            wrapper.in(RuleBillingSummary::getProjectCode, projectMatches.getProjectCodes());
        }
        if (hasText(authType)) {
            wrapper.eq(RuleBillingSummary::getAuthType, authType);
        }
        if (hasText(authCode)) {
            wrapper.like(RuleBillingSummary::getAuthCode, authCode);
        }
        if (hasText(beginDate)) {
            wrapper.ge(RuleBillingSummary::getSummaryDate, beginDate);
        }
        if (hasText(endDate)) {
            wrapper.le(RuleBillingSummary::getSummaryDate, endDate);
        }
        wrapper.orderByDesc(RuleBillingSummary::getSummaryDate)
                .orderByAsc(RuleBillingSummary::getProjectCode)
                .orderByAsc(RuleBillingSummary::getBillingCode);
        return summaryMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public void saveConfigWithDefaults(RuleBillingConfig config) {
        fillConfigDefaults(config);
        save(config);
    }

    public void updateConfigWithDefaults(RuleBillingConfig config) {
        fillConfigDefaults(config);
        updateById(config);
    }

    @Transactional
    public int refreshSummary(LocalDate summaryDate) {
        if (summaryDate == null) {
            summaryDate = LocalDate.now();
        }
        LocalDateTime begin = summaryDate.atStartOfDay();
        LocalDateTime end = summaryDate.plusDays(1).atStartOfDay();
        deleteSummaries(summaryDate);
        List<RuleBillingRecord> records = findRecords(begin, end);
        Map<String, RuleBillingSummary> summaryMap = new LinkedHashMap<>();
        for (RuleBillingRecord record : records) {
            String key = record.getProjectId() + "|" + record.getProjectCode() + "|" + record.getBillingCode()
                    + "|" + record.getBillingTarget() + "|" + record.getTargetRefId()
                    + "|" + record.getAuthId();
            RuleBillingSummary summary = summaryMap.get(key);
            if (summary == null) {
                summary = new RuleBillingSummary();
                summary.setSummaryDate(summaryDate);
                summary.setProjectId(record.getProjectId());
                summary.setProjectCode(record.getProjectCode());
                summary.setBillingCode(record.getBillingCode());
                summary.setBillingTarget(record.getBillingTarget());
                summary.setTargetRefId(record.getTargetRefId());
                summary.setAuthId(record.getAuthId());
                summary.setAuthCode(record.getAuthCode());
                summary.setAuthType(record.getAuthType());
                summary.setCurrency(record.getCurrency());
                summary.setTotalCount(0L);
                summary.setSuccessCount(0L);
                summary.setFailCount(0L);
                summary.setTotalQuantity(BigDecimal.ZERO);
                summary.setTotalAmount(BigDecimal.ZERO);
                summary.setAvgCostTimeMs(BigDecimal.ZERO);
                summaryMap.put(key, summary);
            }
            summary.setTotalCount(summary.getTotalCount() + 1);
            if (record.getSuccess() != null && record.getSuccess() == 1) {
                summary.setSuccessCount(summary.getSuccessCount() + 1);
            } else {
                summary.setFailCount(summary.getFailCount() + 1);
            }
            summary.setTotalQuantity(summary.getTotalQuantity().add(nullToZero(record.getQuantity())));
            summary.setTotalAmount(summary.getTotalAmount().add(nullToZero(record.getAmount())));
            summary.setAvgCostTimeMs(summary.getAvgCostTimeMs().add(new BigDecimal(record.getCostTimeMs() == null ? 0L : record.getCostTimeMs())));
        }
        for (RuleBillingSummary summary : summaryMap.values()) {
            if (summary.getTotalCount() > 0) {
                summary.setAvgCostTimeMs(summary.getAvgCostTimeMs()
                        .divide(new BigDecimal(summary.getTotalCount()), 2, RoundingMode.HALF_UP));
            }
            insertSummary(summary);
        }
        return summaryMap.size();
    }

    public void recordEngineExecution(RuleDefinition definition, boolean success, Long costTimeMs, String errorMessage) {
        recordEngineExecution(definition, success, costTimeMs, errorMessage, null);
    }

    public void recordEngineExecution(RuleDefinition definition, boolean success, Long costTimeMs,
                                      String errorMessage, ProjectAuthContext authContext) {
        if (definition == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Long executionProjectId = authContext != null && authContext.getProjectId() != null
                ? authContext.getProjectId()
                : definition.getProjectId();
        List<RuleBillingConfig> configs = findActiveEngineConfigs(definition, now, executionProjectId);
        if (configs.isEmpty()) {
            return;
        }
        RuleProject project = executionProjectId == null ? null : findProject(executionProjectId);
        for (RuleBillingConfig config : configs) {
            RuleBillingRecord record = new RuleBillingRecord();
            record.setProjectId(executionProjectId);
            record.setProjectCode(project == null
                    ? authContext == null ? definition.getProjectCode() : authContext.getProjectCode()
                    : project.getProjectCode());
            record.setBillingCode(config.getBillingCode());
            record.setBillingName(config.getBillingName());
            record.setBillingTarget(TARGET_ENGINE);
            record.setTargetRefId(config.getTargetRefId());
            record.setRuleCode(definition.getRuleCode());
            record.setSuccess(success ? 1 : 0);
            record.setCostTimeMs(costTimeMs);
            record.setErrorMessage(errorMessage);
            record.setOccurTime(now);
            record.setCurrency(hasText(config.getCurrency()) ? config.getCurrency() : "CNY");
            record.setUnitPrice(nullToZero(config.getUnitPrice()));
            record.setQuantity(resolveQuantity(config.getChargeType(), success, costTimeMs));
            record.setAmount(record.getQuantity().multiply(record.getUnitPrice()).setScale(6, RoundingMode.HALF_UP));
            applyAuthAttribution(record, authContext);
            insertRecord(record);
        }
    }

    public void recordEngineExecutionLog(RuleExecutionLog log) {
        recordEngineExecutionLog(log, null);
    }

    public void recordEngineExecutionLog(RuleExecutionLog log, ProjectAuthContext authContext) {
        if (log == null || !hasText(log.getRuleCode())) {
            return;
        }
        RuleDefinition definition = authContext == null
                ? findDefinitionByRuleCode(log.getRuleCode())
                : findDefinitionByRuleCode(log.getRuleCode(), authContext.getProjectId());
        if (definition == null) {
            return;
        }
        boolean success = log.getSuccess() != null && log.getSuccess() == 1;
        recordEngineExecution(definition, success, log.getExecuteTimeMs(), log.getErrorMessage(), authContext);
    }

    public boolean isRuleAccessible(Long projectId, String ruleCode) {
        if (projectId == null || !hasText(ruleCode)) {
            return false;
        }
        return definitionMapper.selectCount(projectRuleWrapper(ruleCode, projectId)) > 0;
    }

    public void recordApiExecution(RuleExternalApiConfig apiConfig, RuleExternalDatasource datasource,
                                   boolean success, Long costTimeMs, String errorMessage) {
        if (apiConfig == null || datasource == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<RuleBillingConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleBillingConfig::getStatus, 1)
                .eq(RuleBillingConfig::getBillingTarget, TARGET_API)
                .and(w -> w.isNull(RuleBillingConfig::getTargetRefId)
                        .or()
                        .eq(RuleBillingConfig::getTargetRefId, apiConfig.getId()))
                .and(w -> w.eq(RuleBillingConfig::getScope, RuleVariableService.SCOPE_GLOBAL)
                        .or()
                        .eq(RuleBillingConfig::getScope, RuleVariableService.SCOPE_PROJECT)
                        .eq(RuleBillingConfig::getProjectId, datasource.getProjectId()))
                .and(w -> w.isNull(RuleBillingConfig::getEffectiveTime)
                        .or()
                        .le(RuleBillingConfig::getEffectiveTime, now))
                .and(w -> w.isNull(RuleBillingConfig::getExpireTime)
                        .or()
                        .ge(RuleBillingConfig::getExpireTime, now));
        List<RuleBillingConfig> configs = list(wrapper);
        if (configs.isEmpty() && !hasText(apiConfig.getBillingItemCode())) {
            return;
        }
        RuleProject project = datasource.getProjectId() == null ? null : projectMapper.selectById(datasource.getProjectId());
        if (configs.isEmpty()) {
            RuleBillingRecord record = buildApiRecord(apiConfig, datasource, project, apiConfig.getBillingItemCode(),
                    apiConfig.getApiName(), apiConfig.getUnitPrice(), "CNY", success, costTimeMs, errorMessage, now, null);
            recordMapper.insert(record);
            return;
        }
        for (RuleBillingConfig config : configs) {
            RuleBillingRecord record = buildApiRecord(apiConfig, datasource, project, config.getBillingCode(),
                    config.getBillingName(), config.getUnitPrice(), config.getCurrency(), success, costTimeMs,
                    errorMessage, now, config.getTargetRefId());
            record.setQuantity(resolveQuantity(config.getChargeType(), success, costTimeMs));
            record.setAmount(record.getQuantity().multiply(record.getUnitPrice()).setScale(6, RoundingMode.HALF_UP));
            recordMapper.insert(record);
        }
    }

    private RuleBillingRecord buildApiRecord(RuleExternalApiConfig apiConfig, RuleExternalDatasource datasource,
                                             RuleProject project, String billingCode, String billingName,
                                             BigDecimal unitPrice, String currency, boolean success, Long costTimeMs,
                                             String errorMessage, LocalDateTime occurTime, Long targetRefId) {
        RuleBillingRecord record = new RuleBillingRecord();
        record.setProjectId(datasource.getProjectId());
        record.setProjectCode(project == null ? null : project.getProjectCode());
        record.setBillingCode(billingCode);
        record.setBillingName(billingName);
        record.setBillingTarget(TARGET_API);
        record.setTargetRefId(targetRefId == null ? apiConfig.getId() : targetRefId);
        record.setApiCode(apiConfig.getApiCode());
        record.setDatasourceCode(datasource.getDatasourceCode());
        record.setSuccess(success ? 1 : 0);
        record.setCostTimeMs(costTimeMs);
        record.setErrorMessage(errorMessage);
        record.setOccurTime(occurTime);
        record.setCurrency(hasText(currency) ? currency : "CNY");
        record.setUnitPrice(nullToZero(unitPrice));
        record.setQuantity(BigDecimal.ONE);
        record.setAmount(record.getQuantity().multiply(record.getUnitPrice()).setScale(6, RoundingMode.HALF_UP));
        return record;
    }

    protected List<RuleBillingConfig> findActiveEngineConfigs(RuleDefinition definition, LocalDateTime now,
                                                               Long executionProjectId) {
        return list(new LambdaQueryWrapper<RuleBillingConfig>()
                .eq(RuleBillingConfig::getStatus, 1)
                .eq(RuleBillingConfig::getBillingTarget, TARGET_ENGINE)
                .and(w -> w.isNull(RuleBillingConfig::getTargetRefId)
                        .or()
                        .eq(RuleBillingConfig::getTargetRefId, definition.getId()))
                .and(w -> w.eq(RuleBillingConfig::getScope, RuleVariableService.SCOPE_GLOBAL)
                        .or()
                        .eq(RuleBillingConfig::getScope, RuleVariableService.SCOPE_PROJECT)
                        .eq(RuleBillingConfig::getProjectId, executionProjectId))
                .and(w -> w.isNull(RuleBillingConfig::getEffectiveTime)
                        .or()
                        .le(RuleBillingConfig::getEffectiveTime, now))
                .and(w -> w.isNull(RuleBillingConfig::getExpireTime)
                        .or()
                        .ge(RuleBillingConfig::getExpireTime, now)));
    }

    protected RuleProject findProject(Long projectId) {
        return projectMapper.selectById(projectId);
    }

    protected RuleDefinition findDefinitionByRuleCode(String ruleCode) {
        return definitionMapper.selectOne(new LambdaQueryWrapper<RuleDefinition>()
                .eq(RuleDefinition::getRuleCode, ruleCode));
    }

    protected RuleDefinition findDefinitionByRuleCode(String ruleCode, Long projectId) {
        return definitionMapper.selectOne(projectRuleWrapper(ruleCode, projectId));
    }

    private LambdaQueryWrapper<RuleDefinition> projectRuleWrapper(String ruleCode, Long projectId) {
        return new LambdaQueryWrapper<RuleDefinition>()
                .eq(RuleDefinition::getRuleCode, ruleCode)
                .and(w -> w.eq(RuleDefinition::getProjectId, projectId)
                        .or()
                        .exists("SELECT 1 FROM rule_definition_ref rdr "
                                + "WHERE rdr.definition_id = rule_definition.id AND rdr.project_id = " + projectId));
    }

    protected void insertRecord(RuleBillingRecord record) {
        recordMapper.insert(record);
    }

    protected void deleteSummaries(LocalDate summaryDate) {
        summaryMapper.delete(new LambdaQueryWrapper<RuleBillingSummary>()
                .eq(RuleBillingSummary::getSummaryDate, summaryDate));
    }

    protected List<RuleBillingRecord> findRecords(LocalDateTime begin, LocalDateTime end) {
        return recordMapper.selectList(new LambdaQueryWrapper<RuleBillingRecord>()
                .ge(RuleBillingRecord::getOccurTime, begin)
                .lt(RuleBillingRecord::getOccurTime, end));
    }

    protected void insertSummary(RuleBillingSummary summary) {
        summaryMapper.insert(summary);
    }

    private void applyAuthAttribution(RuleBillingRecord record, ProjectAuthContext authContext) {
        if (authContext == null) return;
        record.setAuthId(authContext.getAuthId());
        record.setAuthCode(authContext.getAuthCode());
        record.setAuthType(authContext.getAuthType());
        record.setTokenId(authContext.getTokenId());
        record.setTokenCode(authContext.getTokenCode());
        record.setAuthPhase(authContext.getAuthPhase());
    }

    private BigDecimal resolveQuantity(String chargeType, boolean success, Long costTimeMs) {
        if ("SUCCESS".equals(chargeType) && !success) {
            return BigDecimal.ZERO;
        }
        if ("DURATION".equals(chargeType)) {
            return new BigDecimal(costTimeMs == null ? 0L : costTimeMs).divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP);
        }
        return BigDecimal.ONE;
    }

    private void fillConfigDefaults(RuleBillingConfig config) {
        if (!hasText(config.getScope())) {
            config.setScope(RuleVariableService.SCOPE_PROJECT);
        }
        if (RuleVariableService.SCOPE_GLOBAL.equals(config.getScope())) {
            config.setProjectId(0L);
        }
        if (config.getProjectId() == null) {
            config.setProjectId(0L);
        }
        if (!hasText(config.getBillingTarget())) {
            config.setBillingTarget(TARGET_ENGINE);
        }
        if (!hasText(config.getChargeType())) {
            config.setChargeType("COUNT");
        }
        if (!hasText(config.getCurrency())) {
            config.setCurrency("CNY");
        }
        if (config.getUnitPrice() == null) {
            config.setUnitPrice(BigDecimal.ZERO);
        }
        if (config.getStatus() == null) {
            config.setStatus(1);
        }
    }

    private void fillConfigProjectName(List<RuleBillingConfig> list) {
        if (list == null || list.isEmpty()) return;
        for (RuleBillingConfig config : list) {
            if (config.getProjectId() != null && config.getProjectId() > 0) {
                RuleProject project = projectMapper.selectById(config.getProjectId());
                config.setProjectName(project == null ? null : project.getProjectName());
            }
        }
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
