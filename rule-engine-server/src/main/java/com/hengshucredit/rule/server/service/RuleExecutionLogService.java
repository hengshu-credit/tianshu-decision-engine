package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.mapper.RuleExecutionLogMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 规则执行日志服务，提供批量插入等能力
 */
@Service
public class RuleExecutionLogService extends ServiceImpl<RuleExecutionLogMapper, RuleExecutionLog> {

    @Resource
    private ProjectFilterService projectFilterService;
    @Resource
    private RulePublishedMapper publishedMapper;
    @Resource
    private RuleSetStatisticsStore statisticsStore;

    public void applyPublishedAttribution(RuleExecutionLog log, String authenticatedProjectCode) {
        if (log == null || !hasText(log.getRuleCode())) return;
        if (log.getRevisionId() != null && hasText(log.getArtifactDigest())) return;
        RulePublished published = findPublished(log.getRuleCode(), authenticatedProjectCode);
        if (published == null) return;
        log.setRuleVersion(published.getVersion());
        log.setRevisionId(published.getRevisionId());
        log.setArtifactDigest(published.getArtifactDigest());
    }

    /**
     * 不分区的 trace 身份行负责并发互斥，日志仍保留在原 create_time 分区中。
     * 已存在的历史重复记录不删除；首次使用时绑定最高尝试的代表行。
     */
    @Transactional
    public void saveLogical(RuleExecutionLog log) {
        if (log != null) {
            RuleSetTraceSummary summary = RuleSetTraceSummary.fromTrace(log.getTraceInfo());
            log.setRuleSetStatsJson(summary.json());
            log.setRuleSetStatsStatus(summary.status());
            log.setRuleSetStatsVersion(RuleSetStatisticsStore.FORMAT_VERSION);
        }
        if (log == null || !hasText(log.getTraceId())) {
            if (log != null) log.setId(null);
            if (log != null && !save(log)) {
                throw new IllegalStateException("执行日志未写入");
            }
            if (log != null && statisticsStore != null) {
                log.setCreateTime(baseMapper.selectPhysicalCreateTime(log.getId()));
                persistStatistics(log);
            }
            return;
        }
        // 单元测试和无持久化的预览执行可能通过子类覆盖 save；不要强制调用
        // MyBatis-Plus 的 lambdaQuery（其 baseMapper 尚未注入）。
        if (baseMapper == null) {
            if (!save(log)) {
                throw new IllegalStateException("执行日志未写入");
            }
            return;
        }
        baseMapper.lockLogicalIdentity(log.getTraceId());
        RuleExecutionLogMapper.LogicalIdentity identity = baseMapper.selectLogicalIdentity(log.getTraceId());
        if (identity == null) throw new IllegalStateException("无法锁定逻辑执行日志身份");
        RuleExecutionLog existing;
        if (identity.logId() == null) {
            existing = baseMapper.selectLegacyLogicalRecord(log.getTraceId());
            boolean created = existing == null;
            if (created) {
                // 物理主键由数据库分配，不复用客户端或旧恢复载荷携带的行 ID。
                log.setId(null);
                if (!save(log)) throw new IllegalStateException("执行日志未写入");
                LocalDateTime storedTime = baseMapper.selectInsertedCreateTime(log.getId(), log.getTraceId());
                if (storedTime == null) throw new IllegalStateException("无法确认新执行日志的物理位置");
                log.setCreateTime(storedTime);
                existing = log;
            }
            if (baseMapper.bindLogicalIdentity(log.getTraceId(), existing.getId(), existing.getCreateTime()) != 1) {
                throw new IllegalStateException("逻辑执行日志身份未绑定");
            }
            if (created) {
                log.setCreateTime(existing.getCreateTime());
                persistStatistics(log);
                return;
            }
        } else {
            existing = baseMapper.selectLogicalRecord(identity.logId(), identity.logCreateTime());
            if (existing == null) throw new IllegalStateException("逻辑执行日志关联的审计记录不存在");
        }
        if (attempt(existing) > attempt(log)) return;
        log.setId(existing.getId());
        log.setCreateTime(existing.getCreateTime());
        LambdaUpdateWrapper<RuleExecutionLog> update = new LambdaUpdateWrapper<RuleExecutionLog>()
                .eq(RuleExecutionLog::getId, existing.getId())
                .eq(RuleExecutionLog::getCreateTime, existing.getCreateTime())
                .eq(RuleExecutionLog::getTraceId, log.getTraceId())
                .apply("COALESCE(attempt_no, 1) <= {0}", attempt(log))
                .set(log.getOutputResult() == null, RuleExecutionLog::getOutputResult, null)
                .set(log.getTraceInfo() == null, RuleExecutionLog::getTraceInfo, null)
                .set(log.getErrorMessage() == null, RuleExecutionLog::getErrorMessage, null);
        if (baseMapper.update(log, update) != 1) {
            RuleExecutionLog current = baseMapper.selectLogicalRecord(existing.getId(), existing.getCreateTime());
            if (current != null && attempt(current) > attempt(log)) return;
            throw new IllegalStateException("执行日志更新未生效");
        }
        persistStatistics(log);
    }

    private void persistStatistics(RuleExecutionLog log) {
        if (statisticsStore != null) statisticsStore.persist(log.getId(), log.getCreateTime(), log.getRuleSetStatsJson(), log.getRuleSetStatsStatus());
    }

    private static int attempt(RuleExecutionLog log) {
        return log.getAttemptNo() == null ? 1 : log.getAttemptNo();
    }

    @Transactional
    public void saveLogicalBatch(Collection<RuleExecutionLog> logs) {
        if (logs == null || logs.isEmpty()) return;
        if (baseMapper == null) {
            if (!saveBatch(logs)) throw new IllegalStateException("执行日志批量写入未生效");
            return;
        }
        List<RuleExecutionLog> ordered = new ArrayList<>(logs);
        ordered.removeIf(java.util.Objects::isNull);
        ordered.sort(Comparator.comparing(RuleExecutionLog::getTraceId,
                Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
        for (RuleExecutionLog log : ordered) saveLogical(log);
    }

    protected RulePublished findPublished(String ruleCode, String projectCode) {
        if (publishedMapper == null) return null;
        LambdaQueryWrapper<RulePublished> exact = new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getRuleCode, ruleCode)
                .eq(RulePublished::getStatus, 1);
        if (hasText(projectCode)) exact.eq(RulePublished::getProjectCode, projectCode);
        RulePublished published = publishedMapper.selectOne(exact.last("LIMIT 1"));
        if (published != null || !hasText(projectCode)) return published;

        List<RulePublished> candidates = publishedMapper.selectList(
                new LambdaQueryWrapper<RulePublished>()
                        .eq(RulePublished::getRuleCode, ruleCode)
                        .eq(RulePublished::getStatus, 1)
                        .orderByDesc(RulePublished::getPublishTime));
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    public IPage<RuleExecutionLog> pageList(int pageNum, int pageSize, String modelType, String source,
                                            String projectCode, String projectName, String ruleCode,
                                            String traceId, String authType, String authCode, String tokenCode,
                                            LocalDateTime startTime, LocalDateTime endTime) {
        return pageList(pageNum, pageSize, modelType, source, projectCode, projectName, ruleCode, traceId, authType, authCode, tokenCode, startTime, endTime, null);
    }

    @jakarta.annotation.Resource private ExecutionVersionFilter executionVersionFilter;

    public IPage<RuleExecutionLog> pageList(int pageNum, int pageSize, String modelType, String source,
                                            String projectCode, String projectName, String ruleCode,
                                            String traceId, String authType, String authCode, String tokenCode,
                                            LocalDateTime startTime, LocalDateTime endTime,
                                            com.hengshucredit.rule.model.dto.ExecutionVersionQuery versionQuery) {
        ProjectFilterService.ProjectMatches projectMatches = projectFilterService.resolve(null, projectName);
        if (projectMatches.isActive() && projectMatches.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }
        LambdaQueryWrapper<RuleExecutionLog> wrapper = new LambdaQueryWrapper<>();
        if (versionQuery != null) {
            Integer number = executionVersionFilter.resolve(versionQuery);
            wrapper.eq(versionQuery.getRuleId() != null, RuleExecutionLog::getRootRuleId, versionQuery.getRuleId());
            wrapper.eq(number != null, RuleExecutionLog::getRuleVersion, number);
            wrapper.eq(versionQuery.getRevisionId() != null, RuleExecutionLog::getRevisionId, versionQuery.getRevisionId());
        }
        if (hasText(modelType)) wrapper.eq(RuleExecutionLog::getModelType, modelType);
        if (hasText(source)) wrapper.eq(RuleExecutionLog::getSource, source);
        if (hasText(projectCode)) wrapper.like(RuleExecutionLog::getProjectCode, projectCode);
        if (projectMatches.isActive()) {
            wrapper.in(RuleExecutionLog::getProjectCode, projectMatches.getProjectCodes());
        }
        if (hasText(ruleCode)) wrapper.eq(RuleExecutionLog::getRuleCode, ruleCode);
        if (hasText(traceId)) wrapper.eq(RuleExecutionLog::getTraceId, traceId);
        if (hasText(authType)) wrapper.eq(RuleExecutionLog::getAuthType, authType);
        if (hasText(authCode)) wrapper.like(RuleExecutionLog::getAuthCode, authCode);
        if (hasText(tokenCode)) wrapper.like(RuleExecutionLog::getTokenCode, tokenCode);
        if (startTime != null) wrapper.ge(RuleExecutionLog::getCreateTime, startTime);
        if (endTime != null) wrapper.le(RuleExecutionLog::getCreateTime, endTime);
        wrapper.orderByDesc(RuleExecutionLog::getCreateTime);
        return baseMapper.selectLogicalPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Resource
    private RuleSetStatisticsQuery statistics;

    public Map<String, Object> ruleSetStats(String projectCode, String projectName, String ruleCode,
                                            LocalDateTime startTime, LocalDateTime endTime) {
        ProjectFilterService.ProjectMatches projects = projectFilterService.resolve(null, projectName);
        return statistics.query(projectCode, projects.isActive() ? projects.getProjectCodes() : null,
                ruleCode, startTime, endTime);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
