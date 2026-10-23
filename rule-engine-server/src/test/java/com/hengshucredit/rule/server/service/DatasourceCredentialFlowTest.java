package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.model.entity.RuleSftpDatasource;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class DatasourceCredentialFlowTest {
    @Test
    public void explicitClearAppliesToTrialWithoutRestoringSavedPassword() throws Exception {
        RuleDbDatasourceService service = databaseService(database());
        AtomicReference<RuleDbDatasource> tested = new AtomicReference<>();
        ReflectionTestUtils.setField(service, "dbConnectPools", new DBConnectPools() {
            @Override public void testConnection(RuleDbDatasource source) { tested.set(source); }
        });
        RuleDbDatasource draft = JSON.parseObject("{\"id\":1,\"password\":\"\",\"clearCredentials\":[\"password\"]}", RuleDbDatasource.class);
        service.testConnection(draft);
        assertEquals("", tested.get().getPassword());
        assertEquals("ssh-password", tested.get().getSshPassword());
    }

    @Test
    public void databaseDetailExposesConfiguredFlagsWithoutPlaintext() {
        RuleDbDatasource stored = database();
        RuleDbDatasourceService service = databaseService(stored);
        RuleDbDatasource detail = service.getDetail(1L);
        assertNull(detail.getPassword());
        assertNull(detail.getSshPassword());
        assertNull(detail.getSshPrivateKey());
        assertNull(detail.getSshPassphrase());
        assertEquals(Map.of("password", true, "sshPassword", true,
                "sshPrivateKey", true, "sshPassphrase", true),
                JSON.parseObject(JSON.toJSONString(detail)).get("configuredSecrets"));
    }

    @Test
    public void databaseDraftTrialRetainsMaskedCredentialsAndUsesReplacement() throws Exception {
        RuleDbDatasourceService service = databaseService(database());
        AtomicReference<RuleDbDatasource> tested = new AtomicReference<>();
        ReflectionTestUtils.setField(service, "dbConnectPools", new DBConnectPools() {
            @Override public void testConnection(RuleDbDatasource source) { tested.set(source); }
        });
        RuleDbDatasource draft = new RuleDbDatasource();
        draft.setId(1L);
        draft.setPassword(" new-password ");
        draft.setSshPassword("");
        service.testConnection(draft);
        assertEquals(" new-password ", tested.get().getPassword());
        assertEquals("ssh-password", tested.get().getSshPassword());
        assertEquals("private-key", tested.get().getSshPrivateKey());
        assertEquals("key-passphrase", tested.get().getSshPassphrase());
        assertEquals("", draft.getSshPassword());
        assertNull(draft.getSshPrivateKey());
        ReflectionTestUtils.setField(service, "draftCredentials",
                new com.hengshucredit.rule.server.governance.DatasourceDraftCredentials(null, null, null) {
                    @Override public Map<String, Object> load(String type, Long id) {
                        assertEquals("DATABASE", type);
                        assertEquals(Long.valueOf(1L), id);
                        return Map.of("password", "latest-draft-password");
                    }
                });
        draft.setPassword("");
        service.testConnection(draft);
        assertEquals("latest-draft-password", tested.get().getPassword());
        assertEquals("ssh-password", tested.get().getSshPassword());
        assertEquals("", draft.getPassword());
    }

    @Test
    public void sftpDraftTrialRetainsPrivateKeyAndDoesNotCopySecretsToRequest() throws Exception {
        RuleSftpDatasource stored = new RuleSftpDatasource();
        stored.setPassword("password");
        stored.setPrivateKey("private-key");
        stored.setPassphrase("key-passphrase");
        stored.setKnownHosts("host-fingerprint");
        RuleSftpDatasourceService service = new RuleSftpDatasourceService() {
            @Override public RuleSftpDatasource getById(Serializable id) { return stored; }
        };
        AtomicReference<RuleSftpDatasource> tested = new AtomicReference<>();
        ReflectionTestUtils.setField(service, "sftpConnectPools", new SftpConnectPools() {
            @Override public void testConnection(RuleSftpDatasource source) { tested.set(source); }
        });
        RuleSftpDatasource draft = new RuleSftpDatasource();
        draft.setId(1L);
        draft.setScope("GLOBAL");
        draft.setPassword("replacement");
        draft.setPrivateKey("");
        service.testConnection(draft);
        assertEquals("replacement", tested.get().getPassword());
        assertEquals("private-key", tested.get().getPrivateKey());
        assertEquals("key-passphrase", tested.get().getPassphrase());
        assertEquals("host-fingerprint", tested.get().getKnownHosts());
        assertEquals("", draft.getPrivateKey());
        assertNull(draft.getPassphrase());
        draft.setClearCredentials(java.util.List.of("privateKey", "passphrase"));
        service.testConnection(draft);
        assertEquals("", tested.get().getPrivateKey());
        assertEquals("", tested.get().getPassphrase());
        assertEquals("replacement", tested.get().getPassword());
        assertEquals("host-fingerprint", tested.get().getKnownHosts());
    }

    private RuleDbDatasource database() {
        RuleDbDatasource stored = new RuleDbDatasource();
        stored.setId(1L);
        stored.setProjectId(0L);
        stored.setPassword("password");
        stored.setSshPassword("ssh-password");
        stored.setSshPrivateKey("private-key");
        stored.setSshPassphrase("key-passphrase");
        return stored;
    }

    private RuleDbDatasourceService databaseService(RuleDbDatasource stored) {
        return new RuleDbDatasourceService() {
            @Override public RuleDbDatasource getById(Serializable id) { return stored; }
        };
    }
}
