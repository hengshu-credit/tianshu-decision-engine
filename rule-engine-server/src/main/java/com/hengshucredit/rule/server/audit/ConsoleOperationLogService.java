package com.hengshucredit.rule.server.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.entity.ConsoleOperationLog;
import com.hengshucredit.rule.server.mapper.ConsoleOperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ConsoleOperationLogService {
    private final ConsoleOperationLogMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void append(ConsoleOperationLog log) {
        if (log.getCreateTime() == null) log.setCreateTime(LocalDateTime.now());
        long duration = log.getDurationMs() == null ? 0 : Math.max(0, log.getDurationMs());
        log.setDurationMs(duration);
        if (log.getLeaveTime() == null) log.setLeaveTime(log.getCreateTime().plusNanos(duration * 1_000_000));
        if (log.getLeaveTime().isBefore(log.getCreateTime())) log.setLeaveTime(log.getCreateTime());
        if (log.getUserId() != null && log.getUserId() > 0) {
            mapper.lockAppend(log.getUserId());
            ConsoleOperationLog previous = mapper.latestForAppend(log.getUserId());
            if (previous != null && Content.of(previous).equals(Content.of(log))) {
                if (mapper.extendForAppend(previous.getId(), log.getUserId(), log.getCreateTime(), log.getLeaveTime(), duration) == 1) {
                    log.setId(previous.getId());
                    return;
                }
                // 记录被保留策略删除时，从当前操作开始新建，不恢复已清理的历史。
            }
        }
        log.setId(null);
        mapper.insert(log);
    }

    /** 时间、耗时和存储 ID 不属于操作内容，其余元信息必须逐项完全一致。 */
    private record Content(Long userId, String username, String module, String action,
                           String method, String requestPath, String pagePath, String targetType,
                           String targetId, String targetName, String version, String outcome,
                           Integer resultCode, String summary, String clientIp) {
        private static Content of(ConsoleOperationLog log) {
            return new Content(log.getUserId(), log.getUsername(), log.getModule(), log.getAction(),
                    log.getRequestMethod(), log.getRequestPath(), log.getPagePath(), log.getTargetType(),
                    log.getTargetId(), log.getTargetName(), log.getTargetVersion(), log.getOutcome(),
                    log.getResultCode(), log.getChangeSummary(), log.getClientIp());
        }
    }

    public IPage<ConsoleOperationLog> page(int pageNum, int pageSize, String username,
            String module, String action, String outcome, String keyword, LocalDateTime startTime, LocalDateTime endTime) {
        return page(pageNum, pageSize, username, module, action, outcome, keyword, startTime, endTime, false);
    }

    public IPage<ConsoleOperationLog> systemChanges(int pageNum, int pageSize) {
        return page(pageNum, pageSize, null, "system", null, null, null, null, null, true);
    }

    private IPage<ConsoleOperationLog> page(int pageNum, int pageSize, String username,
            String module, String action, String outcome, String keyword, LocalDateTime startTime, LocalDateTime endTime, boolean changesOnly) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) throw new IllegalArgumentException("开始时间不能晚于结束时间");
        LambdaQueryWrapper<ConsoleOperationLog> query = new LambdaQueryWrapper<>();
        // 所有读取仅包含对象元信息，不映射旧版正文、响应和快照列。
        query.select(ConsoleOperationLog::getId, ConsoleOperationLog::getUserId, ConsoleOperationLog::getUsername,
                ConsoleOperationLog::getModule, ConsoleOperationLog::getAction, ConsoleOperationLog::getRequestMethod,
                ConsoleOperationLog::getRequestPath, ConsoleOperationLog::getPagePath, ConsoleOperationLog::getTargetType,
                ConsoleOperationLog::getTargetId, ConsoleOperationLog::getTargetName, ConsoleOperationLog::getTargetVersion,
                ConsoleOperationLog::getChangeSummary, ConsoleOperationLog::getOutcome,
                ConsoleOperationLog::getResultCode, ConsoleOperationLog::getClientIp, ConsoleOperationLog::getDurationMs,
                ConsoleOperationLog::getCreateTime, ConsoleOperationLog::getLeaveTime);
        query.like(username != null && !username.isBlank(), ConsoleOperationLog::getUsername, username)
                .eq(module != null && !module.isBlank(), ConsoleOperationLog::getModule, module)
                .eq(action != null && !action.isBlank(), ConsoleOperationLog::getAction, action)
                .eq(outcome != null && !outcome.isBlank(), ConsoleOperationLog::getOutcome, outcome)
                .ge(startTime != null, ConsoleOperationLog::getCreateTime, startTime)
                .le(endTime != null, ConsoleOperationLog::getCreateTime, endTime);
        if (keyword != null && !keyword.isBlank()) query.and(q -> q.like(ConsoleOperationLog::getTargetName, keyword)
                .or().like(ConsoleOperationLog::getTargetId, keyword).or().like(ConsoleOperationLog::getRequestPath, keyword)
                .or().like(ConsoleOperationLog::getPagePath, keyword).or().like(ConsoleOperationLog::getChangeSummary, keyword));
        query.orderByDesc(ConsoleOperationLog::getCreateTime).orderByDesc(ConsoleOperationLog::getId);
        if (changesOnly) query.notIn(ConsoleOperationLog::getAction, java.util.List.of("READ", "PAGE_VIEW", "FIELD_VIEW", "FIELD_SELECT"));
        return mapper.selectPage(new Page<>(Math.max(1, pageNum), Math.max(1, Math.min(100, pageSize))), query);
    }

    public ConsoleOperationLog detail(Long id) {
        ConsoleOperationLog log = mapper.selectById(id);
        if (log == null) throw new IllegalArgumentException("操作日志不存在");
        return log;
    }
}
