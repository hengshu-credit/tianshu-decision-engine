package com.hengshucredit.rule.server.transfer;

import com.hengshucredit.rule.model.entity.OfflineTransferLog;
import com.hengshucredit.rule.server.security.ConsoleAccessDeniedException;
import com.hengshucredit.rule.server.security.ConsoleAccessGuard;
import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import com.hengshucredit.rule.server.service.OfflineTransferLogService;
import com.hengshucredit.rule.server.sql.TestSchemaTables;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class TransferTaskStoreTest {
    private JdbcTemplate db;
    private TransferTaskStore store;
    private final UUID id = UUID.randomUUID();
    private final String session = "a".repeat(64);

    @Before public void setUp() throws Exception {
        db = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + id + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        TestSchemaTables.create(db, List.of("offline_transfer_part", "offline_transfer_action"));
        store = new TransferTaskStore(db);
    }

    @After public void close() { db.execute("SHUTDOWN"); }

    @Test public void partsAreSessionBoundImmutableAndReassembledInOrder() throws Exception {
        byte[] first = new byte[TransferTaskStore.PART_SIZE];
        Arrays.fill(first, (byte) 17);
        byte[] tail = {42, 99};
        store.create(id, session, "UPLOAD", "OPEN", first.length + tail.length);
        assertThrows(IllegalArgumentException.class, () -> store.receive(id, "b".repeat(64), 1, new ByteArrayInputStream(tail)));
        store.receive(id, session, 1, new ByteArrayInputStream(tail));
        store.receive(id, session, 1, new ByteArrayInputStream(tail));
        assertThrows(IllegalArgumentException.class, () -> store.receive(id, session, 1, new ByteArrayInputStream(new byte[]{1,2})));
        assertThrows(IllegalArgumentException.class, () -> store.receive(id, session, 1, new ByteArrayInputStream(new byte[]{42})));
        assertThrows(IllegalArgumentException.class, () -> store.receive(id, session, Integer.MAX_VALUE, new ByteArrayInputStream(tail)));
        var out = new ByteArrayOutputStream();
        var file = store.upload(id, session);
        assertThrows(IllegalArgumentException.class, () -> store.writeFile(file, out));
        store.receive(id, session, 0, new ByteArrayInputStream(first));
        store.writeFile(file, out);
        byte[] expected = Arrays.copyOf(first, first.length + tail.length);
        System.arraycopy(tail, 0, expected, first.length, tail.length);
        assertArrayEquals(expected, out.toByteArray());
    }

    @Test public void retriedSubmitUsesOneExportAndDownloadRechecksResourceVisibility() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var exports = new AtomicInteger();
        var visible = new AtomicBoolean();
        var roots = List.of(new TransferRootRequest("RULE", 1L));
        var exported = new OfflineResourceTransferService.ExportedPackage(null, new byte[]{3, 5});
        var transfer = new OfflineResourceTransferService() {
            @Override TransferBundle exportTo(List<TransferRootRequest> selected, boolean recursive, TransferProgress progress, java.io.OutputStream sink) throws java.io.IOException {
                assertEquals(roots, selected);
                assertTrue(recursive);
                exports.incrementAndGet();
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("未收到测试放行信号");
                } catch (InterruptedException stopped) { throw new IllegalStateException(stopped); }
                sink.write(exported.bytes());
                return exported.bundle();
            }
        };
        var audit = new OfflineTransferLogService() {
            @Override public Long recordExport(List<TransferRootRequest> selected, TransferBundle file, String digest, String actor) {
                assertEquals(roots, selected);
                assertSame(exported.bundle(), file);
                assertEquals(com.hengshucredit.rule.server.artifact.Sha256Digests.bytes(exported.bytes()), digest);
                return 12L;
            }
            @Override public OfflineTransferLog getById(java.io.Serializable logId) {
                assertEquals(12L, logId);
                return visible.get() ? new OfflineTransferLog() : null;
            }
        };
        var login = new com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties();
        login.setEnabled(false);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(db.getDataSource()));
        var tasks = new TransferTaskService(store, transfer, null, null, audit, new ConsoleProjectAccess(),
                new ConsoleAccessGuard(login, null), new ConsoleOperatorResolver(), transaction);
        var request = new TransferTaskService.Request(id, TransferTaskService.Operation.EXPORT, null, null,
                new TransferExportRequest(roots, true), null);
        try {
            assertEquals(id, tasks.submit(request, session));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(id, tasks.submit(request, session));
            assertThrows(IllegalArgumentException.class, () -> tasks.submit(request, "other"));
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while ("RUNNING".equals(store.owned(id, session).status()) && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals("SUCCESS", store.owned(id, session).status());
            assertEquals(1, exports.get());
            assertThrows(ConsoleAccessDeniedException.class, () -> tasks.downloadable(id, session));
            visible.set(true);
            var out = new ByteArrayOutputStream();
            new TransferTaskStore(db).writeFile(tasks.downloadable(id, session), out);
            assertArrayEquals(exported.bytes(), out.toByteArray());
        } finally { release.countDown(); tasks.close(); }
    }

    @Test public void pollingReturnsOnlyNewActionsAndOneFinalResult() {
        store.create(id, session, "SET_PREVIEW", "RUNNING", 0);
        store.progress(id.toString(), 5, "读取项目清单");
        var first = store.actions(id.toString(), 0).get(0);
        assertTrue(Math.abs(first.time() - System.currentTimeMillis()) < 60000);
        store.progress(id.toString(), 85, "核对资源引用");
        store.finish(id.toString(), "SUCCESS", 0, Map.of("ready", true));
        var more = store.actions(id.toString(), Long.parseLong(first.id()));
        assertEquals(List.of("核对资源引用"), more.stream().map(TransferTaskStore.Action::message).toList());
        assertEquals(Map.of("ready", true), store.owned(id, session).result());
        assertThrows(IllegalStateException.class, () -> store.finish(id.toString(), "FAILED", 0, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> store.owned(id, "b".repeat(64)));
    }
}
