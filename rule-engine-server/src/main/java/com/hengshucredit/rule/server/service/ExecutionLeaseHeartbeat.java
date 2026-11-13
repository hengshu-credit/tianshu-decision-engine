package com.hengshucredit.rule.server.service;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 仅为本进程实际运行中的尝试续约。宕机后自然到期，旧实例不能续已失效的租约。 */
@Component
public class ExecutionLeaseHeartbeat {
    private static final Logger log = LoggerFactory.getLogger(ExecutionLeaseHeartbeat.class);
    @Resource private JdbcTemplate jdbc;
    private record Lease(boolean experiment, long id, String owner, int seconds) {}
    private final Set<Lease> executing = ConcurrentHashMap.newKeySet();

    interface Scope extends AutoCloseable {
        @Override void close();
    }

    Scope track(boolean experiment, long id, String owner, int seconds) {
        Lease lease = new Lease(experiment, id, owner, seconds);
        boolean added = executing.add(lease);
        return () -> { if (added) executing.remove(lease); };
    }

    @Scheduled(fixedDelay = 10000)
    public void renew() {
        for (Lease lease : executing) {
            String table = lease.experiment() ? "rule_experiment_execution_state" : "rule_execution_state";
            try {
                jdbc.update("UPDATE " + table + " SET lease_until=DATE_ADD(NOW(6),INTERVAL ? SECOND) "
                                + "WHERE id=? AND status='RUNNING' AND lease_owner=? AND lease_until>NOW(6) "
                                + "AND lease_until<TIMESTAMPADD(SECOND,?,NOW(6))",
                        lease.seconds(), lease.id(), lease.owner(), lease.seconds() / 2);
            } catch (RuntimeException error) {
                log.warn("执行租约续期失败，仍以数据库当前租约决定写入权", error);
            }
        }
    }
}
