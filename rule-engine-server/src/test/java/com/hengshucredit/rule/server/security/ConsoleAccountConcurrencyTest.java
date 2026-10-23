package com.hengshucredit.rule.server.security;

import com.hengshucredit.rule.model.dto.ConsoleUserSaveRequest;
import org.junit.Test;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ConsoleAccountConcurrencyTest {
    @Test public void resetLinkCanOnlyBeConsumedOnceAcrossTransactions() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            f.user("alice");
            f.credential.requestPasswordReset("alice", "https://console.example.test", "127.0.0.1");
            String token = f.mail.token();
            AtomicInteger successes = new AtomicInteger();
            race(() -> { try { f.credential.resetPassword(token, "ConcurrentNew123!", "127.0.0.1"); successes.incrementAndGet(); }
                catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("链接")); } });
            assertEquals(1, successes.get());
        }
    }

    @Test public void simultaneousDisablingKeepsUniqueSystemAdministrator() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            var admin = f.initialize();
            var roleRequest = new com.hengshucredit.rule.model.dto.ConsoleRoleSaveRequest();
            roleRequest.setRoleCode("ACCOUNT_MANAGER"); roleRequest.setRoleName("账户管理员");
            roleRequest.setPermissionCodes(List.of("account:manage", "role:manage"));
            Long managerRoleId = f.accounts.saveRole(roleRequest, "admin").getId();
            var second = new ConsoleUserSaveRequest(); second.setUsername("secondAdmin"); second.setPassword("OtherPass123!");
            second.setRoleIds(List.of(managerRoleId));
            Long secondId = f.accounts.saveUser(second, "admin").getId();
            AtomicInteger count = new AtomicInteger();
            AtomicInteger targets = new AtomicInteger();
            race(() -> { Long id = targets.getAndIncrement() % 2 == 0 ? admin.userId() : secondId;
                try { f.accounts.changeUserStatus(id, 0, "admin"); }
                catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("唯一超级管理员")); count.incrementAndGet(); } });
            assertEquals(Integer.valueOf(1), f.jdbc.queryForObject("SELECT COUNT(*) FROM console_user WHERE status=1", Integer.class));
            assertTrue(count.get() > 0);
        }
    }

    private void race(Runnable action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 4; i++) futures.add(pool.submit(() -> { start.await(); action.run(); return null; }));
            start.countDown();
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }
}
