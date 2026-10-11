package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import org.junit.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/** 默认门禁只验证输入、加密和事务确认；SQL并发语义由显式MySQL用例证明。 */
public class ExternalApiTrialStoreTest {
    @Test public void invalidIdentityAndStatusFailBeforeAnyPersistence() {
        ExternalApiTrialStore store = new ExternalApiTrialStore();
        String id = UUID.randomUUID().toString();
        assertThrows(IllegalArgumentException.class, () -> store.create(" ", id, 17L, "a".repeat(64), "{}", 1800));
        assertThrows(IllegalArgumentException.class, () -> store.create("owner", "1-1-1-1-1", 17L, "a".repeat(64), "{}", 1800));
        assertThrows(IllegalArgumentException.class, () -> store.create("owner", id, 17L, "a".repeat(64), "{}", 0));
        assertThrows(IllegalArgumentException.class, () -> store.finish(id, id, "RUNNING", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> store.claim(id, "owner", id, 0));
    }

    @Test public void creationEncryptsRawSnapshotAndCommitsBeforeReturning() {
        Fixture fixture = new Fixture();
        String raw = "{\"amount\":9007199254740993.123456789,\"credential\":\"test-only-secret\"}";
        var result = fixture.store.create("owner", fixture.key, 17L, "a".repeat(64), raw, 1800);
        assertSame(fixture.jdbc.state, result);
        String encrypted = (String) fixture.jdbc.insertArguments[5];
        assertFalse(encrypted.contains("test-only-secret")); assertEquals(raw, fixture.cipher.decrypt(encrypted));
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, fixture.transactions.propagation);
        assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, fixture.transactions.isolation);
        assertEquals(1, fixture.transactions.commits); assertEquals(0, fixture.transactions.rollbacks);
    }

    @Test public void zeroInsertDatabaseFailureOrCommitFailureCannotConfirmAcceptance() {
        Fixture zero = new Fixture(); zero.jdbc.insertResult = 0;
        assertThrows(IllegalStateException.class, () -> zero.create());
        assertEquals(0, zero.transactions.commits); assertEquals(1, zero.transactions.rollbacks);
        Fixture unavailable = new Fixture(); unavailable.jdbc.insertFailure = new DataAccessResourceFailureException("test unavailable");
        assertThrows(DataAccessResourceFailureException.class, () -> unavailable.create());
        assertEquals(1, unavailable.transactions.rollbacks);
        Fixture commit = new Fixture(); commit.transactions.commitFails = true;
        assertThrows(TransactionSystemException.class, () -> commit.create());
    }

    @Test public void duplicateReturnsTheOriginalImmutableSnapshotRatherThanRefreshingIt() {
        Fixture fixture = new Fixture(); fixture.jdbc.insertFailure = new DuplicateKeyException("same owner/request key");
        var original = fixture.jdbc.state;
        var result = fixture.store.create("owner", fixture.key, 18L, "b".repeat(64), "{\"changed\":true}", 3600);
        assertSame(original, result); assertEquals(5000L, result.expiresAt());
        assertEquals("{\"original\":true}", result.snapshotJson()); assertEquals("a".repeat(64), result.snapshotDigest());
        assertEquals(1, fixture.transactions.commits);
    }

    @Test public void zeroCancellationCannotReturnAnUnchangedActiveStateAsConfirmation() {
        Fixture fixture = new Fixture();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> fixture.store.cancel(fixture.jdbc.state.trialId(), "owner"));
        assertTrue(failure.getMessage().contains("取消"));
    }

    private static final class Fixture {
        final String key = UUID.randomUUID().toString();
        final RecordingJdbc jdbc = new RecordingJdbc();
        final RecordingTransactions transactions = new RecordingTransactions();
        final CredentialCipher cipher;
        final ExternalApiTrialStore store = new ExternalApiTrialStore();
        Fixture() {
            var keys = new ProjectAuthProperties(); keys.setActiveKeyId("test"); keys.setMasterKeys(Map.of("test", "test-only-key-" + UUID.randomUUID()));
            cipher = new CredentialCipher(keys);
            jdbc.state = new ExternalApiTrialStore.State(1, UUID.randomUUID().toString(), "owner", key, 17L,
                    "a".repeat(64), "{\"original\":true}", "CREATED", 0, null, null, 5000L, null, null, null, 1000L, 1000L);
            ReflectionTestUtils.setField(store, "jdbcTemplate", jdbc); ReflectionTestUtils.setField(store, "credentialCipher", cipher);
            ReflectionTestUtils.setField(store, "transactionManager", transactions);
        }
        ExternalApiTrialStore.State create() { return store.create("owner", key, 17L, "a".repeat(64), "{}", 1800); }
    }
    private static final class RecordingJdbc extends JdbcTemplate {
        ExternalApiTrialStore.State state;
        int insertResult = 1; RuntimeException insertFailure; Object[] insertArguments;
        @Override public int update(String sql, Object... arguments) {
            if (sql.startsWith("INSERT")) {
                insertArguments = arguments;
                if (insertFailure != null) throw insertFailure;
                return insertResult;
            }
            return 0;
        }
        @Override @SuppressWarnings("unchecked") public <T> List<T> query(String sql, RowMapper<T> mapper, Object... arguments) {
            return state == null ? new ArrayList<>() : List.of((T) state);
        }
    }
    private static final class RecordingTransactions implements PlatformTransactionManager {
        int commits, rollbacks, propagation, isolation; boolean commitFails;
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
            propagation = definition.getPropagationBehavior(); isolation = definition.getIsolationLevel(); return new SimpleTransactionStatus();
        }
        @Override public void commit(TransactionStatus status) {
            if (commitFails) throw new TransactionSystemException("test commit unavailable");
            commits++;
        }
        @Override public void rollback(TransactionStatus status) { rollbacks++; }
    }
}
