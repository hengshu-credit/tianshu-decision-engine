package com.hengshucredit.rule.server.sftp;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class EngineSftpProtectionTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();

    @Test public void failuresPersistAcrossInstancesAndUnblockOnlyTheSelectedSubject() throws Exception {
        try (var fixture = new EngineSftpFixture(files.getRoot().toPath())) {
            var now = new AtomicLong(System.currentTimeMillis());
            var manager = new DataSourceTransactionManager(fixture.db.getDataSource());
            var guard = new EngineSftpProtection(fixture.db,manager,now::get);
            var other = new EngineSftpProtection(fixture.db,manager,now::get);
            var config = new EngineSftpProtection.Policy(10,2,2,60,30,0);
            assertEquals(1,guard.save(config).revision());
            assertThrows(IllegalStateException.class, () -> other.save(config));
            assertFalse(guard.authenticate("192.0.2.1","SERVICE",()->false));
            assertFalse(other.authenticate("192.0.2.1","SERVICE",()->false));
            var bans = guard.bans(1,20).getRecords();
            assertEquals(2,bans.size());
            assertFalse(guard.authenticate("192.0.2.2","SERVICE",()->true));
            guard.unblock("SERVICE",now.get()+30000);
            assertFalse(guard.authenticate("192.0.2.1","SERVICE",()->true));
            assertTrue(other.authenticate("192.0.2.2","SERVICE",()->true));
            assertThrows(IllegalStateException.class, () -> guard.unblock("SERVICE",now.get()+30000));
            now.addAndGet(30000);
            assertTrue(guard.authenticate("192.0.2.1","SERVICE",()->true));
            assertTrue(guard.bans(1,20).getRecords().isEmpty());
            assertTrue(guard.allowFileRequest(7)); assertTrue(guard.allowFileRequest(7));
            assertFalse(guard.allowFileRequest(7)); assertTrue(guard.allowFileRequest(8));
            now.addAndGet(1000);
            assertTrue(guard.allowFileRequest(7));
        }
    }

    @Test public void oneMinuteBudgetIsSharedByConcurrentSessions() throws Exception {
        try (var fixture = new EngineSftpFixture(files.getRoot().toPath())) {
            var now = new AtomicLong(System.currentTimeMillis());
            var guard = new EngineSftpProtection(fixture.db,new DataSourceTransactionManager(fixture.db.getDataSource()),now::get);
            guard.save(new EngineSftpProtection.Policy(1,10,2,60,30,0));
            var workers = Executors.newFixedThreadPool(2);
            try {
                var start = new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.Callable<Boolean> auth = () -> { start.await(); return guard.authenticate("192.0.2.3",null,()->true); };
                var a=workers.submit(auth);
                var b=workers.submit(auth);
                start.countDown();
                assertNotEquals(a.get(),b.get());
                var block=guard.bans(1,10).getRecords().get(0);
                assertEquals("RATE",block.reason());
                guard.unblock(block.subject(),block.blockedUntil());
                assertTrue(guard.authenticate("192.0.2.3",null,()->true));
            } finally { workers.shutdownNow(); }
        }
    }
}
