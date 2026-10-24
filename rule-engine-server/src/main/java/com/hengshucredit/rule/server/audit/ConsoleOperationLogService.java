package com.hengshucredit.rule.server.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.entity.ConsoleOperationLog;
import com.hengshucredit.rule.server.mapper.ConsoleOperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ConsoleOperationLogService {
    private final ConsoleOperationLogMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(ConsoleOperationLog log) { mapper.insert(log); }

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
                ConsoleOperationLog::getResultCode, ConsoleOperationLog::getClientIp, ConsoleOperationLog::getDurationMs, ConsoleOperationLog::getCreateTime);
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
