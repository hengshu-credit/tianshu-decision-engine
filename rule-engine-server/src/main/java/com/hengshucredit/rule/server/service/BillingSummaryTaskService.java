package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleBillingSummaryTask;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import com.hengshucredit.rule.server.mapper.RuleBillingSummaryTaskMapper;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** 持久刷新任务：按日期合并重复提交，进程中断后由任一节点接续未完成任务。 */
@Service
public class BillingSummaryTaskService {
    private static final Logger log = LoggerFactory.getLogger(BillingSummaryTaskService.class);
    @Resource private RuleBillingSummaryTaskMapper tasks;
    @Resource private RuleBillingService billing;
    @Resource private ConsoleOperatorResolver operator;
    @Resource private PlatformTransactionManager transactionManager;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(action -> {
        Thread thread = new Thread(action, "billing-summary-refresh");
        thread.setDaemon(true);
        return thread;
    });

    public RuleBillingSummaryTask submit(LocalDate date) {
        LocalDate selectedDate = date == null ? LocalDate.now() : date;
        RuleBillingSummaryTask active = tasks.active(selectedDate);
        if (active != null) return active;
        String id = UUID.randomUUID().toString();
        try {
            tasks.enqueue(id, selectedDate, operator.resolve());
        } catch (QueryTimeoutException timeout) {
            // 并发首次提交可能碰到刚进入长事务的同日任务；避免请求一直等待其行锁。
            active = tasks.active(selectedDate);
            if (active != null) return active;
            throw timeout;
        }
        RuleBillingSummaryTask task = tasks.selectById(id);
        // 唯一active_date约束保留已有任务；终态历史仍能按taskId查询。
        if (task == null) task = tasks.latest(selectedDate);
        schedule();
        return task;
    }

    public RuleBillingSummaryTask get(String id) {
        RuleBillingSummaryTask task = tasks.selectById(id);
        if (task == null) throw new RuleGovernanceException(404, "BILLING_SUMMARY_TASK_NOT_FOUND", "汇总任务不存在，请重新查询", List.of());
        return task;
    }

    public RuleBillingSummaryTask latest(LocalDate date) {
        return tasks.latest(date == null ? LocalDate.now() : date);
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 2000)
    public void schedule() {
        if (worker.isShutdown() || !running.compareAndSet(false, true)) return;
        try {
            worker.execute(() -> {
                try { processNext(); }
                catch (Exception failure) { log.error("计费汇总后台处理失败，下轮将重新检查持久任务", failure); }
                finally { running.set(false); }
            });
        } catch (RuntimeException failure) {
            running.set(false);
            if (!worker.isShutdown()) throw failure;
        }
    }

    boolean processNext() {
        RuleBillingSummaryTask task = transaction().execute(status -> {
            RuleBillingSummaryTask selected = tasks.nextUnlocked();
            if (selected != null) tasks.start(selected.getTaskId());
            return selected;
        });
        if (task == null) return false;
        try {
            transaction().executeWithoutResult(status -> {
                RuleBillingSummaryTask current = tasks.lockTask(task.getTaskId());
                if (current == null || !"RUNNING".equals(current.getStatus())) return;
                int count = billing.rebuildSummary(current.getSummaryDate());
                tasks.complete(current.getTaskId(), current.getSummaryDate(), count);
            });
        } catch (Exception failure) {
            log.error("计费日汇总失败，taskId={}，date={}", task.getTaskId(), task.getSummaryDate(), failure);
            // 只有仍处于RUNNING的任务可标记失败，不覆盖其他节点已完成的结果。
            // 数据库不可用时此更新也会失败，持久RUNNING状态由恢复扫描重试。
            transaction().executeWithoutResult(status -> tasks.fail(task.getTaskId(), "汇总处理失败，原汇总保留，请重试或联系管理员"));
        }
        return true;
    }

    private TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction;
    }

    @PreDestroy public void close() { worker.shutdownNow(); }
}
