package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteResult;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.auth.ProjectExecutionGuard;
import com.hengshucredit.rule.server.config.ExecutionAdmission;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** A persisted order is the unit of admission. HTTP callers and recovery workers use the same lease. */
@Service
public class ExecutionQueue {
    private static final Logger log = LoggerFactory.getLogger(ExecutionQueue.class);
    @Resource private JdbcTemplate jdbc;
    @Resource private ExecutionAdmission admission;
    @Resource private ProjectExecutionGuard projectGuard;
    @Resource private ProjectAuthService authService;
    @Resource private RuleExperimentExecutionStateService experiments;
    @Resource private RuleExperimentService experimentService;
    @Resource private RuleIdempotencyService orders;
    @Resource private RuleExecutionStateService states;
    @Resource private RulePublishedMapper publishedRules;
    @Resource private RuleExecuteService rules;
    @Resource private com.hengshucredit.rule.server.openapi.OpenApiContractService contracts;

    private final ExecutorService recovery = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "accepted-order-recovery");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean scanning = new AtomicBoolean();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 16, 30,
            TimeUnit.SECONDS, new SynchronousQueue<>(), task -> {
        Thread thread = new Thread(task, "accepted-order-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final java.util.Set<String> assigned = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public boolean enter() {
        int remaining = RequestDeadlineContext.remainingMillis();
        return remaining > 0 && admission.enter(RequestDeadlineContext.currentNanoTime(), remaining);
    }

    public void leave() { admission.leave(); }

    public ProjectExecutionGuard.Permit permit(ProjectAuthContext auth) {
        return projectGuard.acquire(auth.getAuthId(), auth.getAccessPolicy());
    }

    public <T> ApiResponse<T> defer(boolean experiment, String traceId) {
        String table = experiment ? "rule_experiment_execution_state" : "rule_execution_state";
        String trace = experiment ? "experiment_trace_id" : "root_trace_id";
        jdbc.update("UPDATE " + table + " SET lease_until=NOW(6) WHERE " + trace + "=? AND status='QUEUED' AND lease_owner IS NULL", traceId);
        return pending(traceId);
    }

    public static <T> ApiResponse<T> pending(String traceId) {
        var response = ApiResponse.<T>fail(PlatformCode.RESULT_PENDING, "订单已接收，可按追踪号查询执行结果");
        response.setTraceId(traceId);
        return response;
    }

    public RuleExperimentExecuteResult executeExperiment(RuleExperimentExecutionStateService.Decision claim,
                                                 long projectId, String code, com.hengshucredit.rule.model.dto.RuleExperimentExecuteRequest request) {
        try {
            var result = experiments.withLease(claim, () -> experimentService.executeForProject(
                    code, projectId, request, claim.getTraceId()));
            experiments.complete(claim, result);
            return result;
        } catch (RuleExperimentExecutionStateService.StrategyInProgress running) {
            experiments.awaitStrategy(claim);
            return null;
        } catch (RuntimeException failure) {
            experiments.release(claim, failure.getMessage());
            throw failure;
        }
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 2000)
    public void recover() {
        if (recovery.isShutdown() || !scanning.compareAndSet(false,true)) return;
        try {
            recovery.execute(() -> {
                try {
                    // 先给同步请求执行机会，再接管失联或已返回待处理状态的请求。
                    for (boolean experiment : List.of(true,false)) recoverOne(experiment);
                } catch (RuntimeException failure) {
                    log.warn("待执行订单领取失败，将继续检查持久队列", failure);
                } finally { scanning.set(false); }
            });
        } catch (RejectedExecutionException shutdown) { scanning.set(false); }
    }

    private record Work(long id, long project, String trace, String status, int attempt, ExecutionDispatch dispatch) {}

    private void recoverOne(boolean experiment) {
        String table = experiment ? "rule_experiment_execution_state" : "rule_execution_state";
        String traceField = experiment ? "experiment_trace_id" : "root_trace_id";
        String resumable = experiment ? "status IN ('RUNNING','WAITING_EXTERNAL')" : "status='RUNNING'";
        var candidates = jdbc.query("SELECT id,project_id," + traceField + ",status,attempt_no,dispatch_json FROM " + table
                            + " WHERE dispatch_json IS NOT NULL AND (JSON_EXTRACT(dispatch_json,'$.authId') IS NOT NULL OR JSON_EXTRACT(dispatch_json,'$.globalKeyId') IS NOT NULL) AND ((status='QUEUED' AND (lease_until<=NOW(6) OR (lease_until IS NULL AND update_time<TIMESTAMPADD(MICROSECOND,-?,NOW(6))))) "
                            + "OR (" + resumable + " AND lease_until<=NOW(6))) ORDER BY update_time,id LIMIT 8",
                    (rs,n) -> new Work(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getInt(5),
                            JSON.parseObject(rs.getString(6), ExecutionDispatch.class)), (admission.waitMillis() + 1000) * 1000);
        for (Work work : candidates) {
            if (!assigned.add(work.trace())) continue;
            try {
                workers.execute(() -> {
                    boolean entered = admission.enterWorker();
                    try {
                        if (entered) dispatch(experiment, table, work);
                    } catch (RuntimeException failure) {
                        log.warn("持久订单接管失败，trace={}", work.trace(), failure);
                    } finally {
                        if (entered) admission.leave();
                        assigned.remove(work.trace());
                    }
                });
            } catch (RejectedExecutionException full) {
                assigned.remove(work.trace());
                return;
            }
        }
    }

    private void dispatch(boolean experiment, String table, Work work) {
        var auth = authService.queuedContext(work.project(), work.dispatch());
        if (auth == null) {
            failBeforeStart(table, work, "原项目或执行凭据已停用，请核对后恢复");
            return;
        }
        try (var permit = permit(auth);
             var deadline = RequestDeadlineContext.limit(auth.getAccessPolicy().getRequestTimeoutMs())) {
            if (experiment) recoverExperiment(work);
            else recoverRule(work, auth);
        } catch (ProjectExecutionGuard.Rejected busy) {
            // 该项目稍后再领取，其他项目仍可使用空余执行槽。
            jdbc.update("UPDATE " + table + " SET update_time=NOW(6),lease_until=DATE_ADD(NOW(6),INTERVAL 2 SECOND) "
                            + "WHERE id=? AND attempt_no=? AND status=? AND (lease_until IS NULL OR lease_until<=NOW(6))",
                    work.id(), work.attempt(), work.status());
        }
    }

    private void failBeforeStart(String table, Work work, String detail) {
        jdbc.update("UPDATE " + table + " SET status='FAILED_RETRYABLE',error_message=?,lease_owner=NULL,lease_until=NULL,finish_time=NOW(6) "
                + "WHERE id=? AND attempt_no=? AND status=? AND (lease_until IS NULL OR lease_until<NOW(6))",detail,work.id(),work.attempt(),work.status());
    }

    private void recoverExperiment(Work work) {
        var state = experiments.byTrace(work.trace());
        if (state == null || state.request() == null) {
            failBeforeStart("rule_experiment_execution_state", work, "缺少可恢复的原始实验请求");
            return;
        }
        var claim = experiments.claimAccepted(work.id(),work.attempt(),work.trace());
        if (claim == null) return;
        claim.setExternalRecoveryMode("WAITING_EXTERNAL".equals(work.status()) ? "ORIGINAL_ONLY" : work.dispatch().externalRecoveryMode());
        executeExperiment(claim,state.projectId(),state.experimentCode(),state.request());
    }

    private void recoverRule(Work work, ProjectAuthContext auth) {
        var state = states.findByRootTraceId(work.trace());
        if (state == null || state.attemptNo()!=work.attempt() || !work.status().equals(state.status())) return;
        var published = publishedRules.selectOne(new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getDefinitionId,state.definitionId()).eq(state.currentRevisionId()!=null,RulePublished::getRevisionId,state.currentRevisionId())
                .eq(RulePublished::getStatus,1).last("LIMIT 1"));
        if (published == null || !java.util.Objects.equals(published.getArtifactDigest(), state.artifactDigest())) {
            failBeforeStart("rule_execution_state",work,"接单时的已发布修订当前不可用");
            return;
        }
        if (work.dispatch().openContract()) {
            try {
                var contract = contracts.resolve(auth.getAuthId(), auth.getAuthCode(), state.ruleCode());
                if (!java.util.Objects.equals(contract.getProject().getId(), state.projectId()) ||
                        !java.util.Objects.equals(contract.getPublished().getDefinitionId(), state.definitionId()))
                    throw new IllegalArgumentException("开放规则授权已变更");
            } catch (IllegalArgumentException | com.hengshucredit.rule.server.openapi.OpenApiException denied) {
                failBeforeStart("rule_execution_state", work, "原开放规则授权已失效，请核对后恢复");
                return;
            }
        }
        Map<String,Object> params = orders.requestParams(state);
        var claim = orders.resumeAccepted(state,published,params);
        if (!claim.isClaimed()) return;
        try {
            boolean trace = work.dispatch().traceEnabled();
            claim.setExternalRecoveryMode(work.dispatch().externalRecoveryMode());
            RuleResult result = orders.withDecision(claim, () -> rules.executePublished(published,params,work.project(),
                    work.dispatch().clientAppName(), auth, trace, work.dispatch().recordTrace() == null ? trace : work.dispatch().recordTrace()));
            orders.complete(claim,result,null);
        } catch (RuntimeException failure) {
            orders.release(claim,failure.getMessage());
            throw failure;
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 30000)
    public void purgeCompleted() {
        states.purgeCompleted(false);
        states.purgeCompleted(true);
    }

    @PreDestroy public void close() { recovery.shutdownNow(); workers.shutdownNow(); }
}
