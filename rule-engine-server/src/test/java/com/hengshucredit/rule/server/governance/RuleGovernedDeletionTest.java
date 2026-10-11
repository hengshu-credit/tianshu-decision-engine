package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.dto.RulePushMessage;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleLifecycleEvent;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RulePublishOutbox;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.mapper.RulePublishOutboxMapper;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import com.hengshucredit.rule.server.service.RuleLifecycleService;
import com.hengshucredit.rule.server.publish.RulePushService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class RuleGovernedDeletionTest {
    @Test
    public void neverPublishedRuleCanBeDeletedWithoutCreatingOrPublishingARevision() {
        Fixture fixture = new Fixture();
        AppliedResource result = fixture.delete();

        assertEquals("DELETED", result.effectiveStatus());
        assertEquals(Integer.valueOf(-1), fixture.definition.getStatus());
        assertNull(result.artifactId());
        assertTrue(fixture.outbox.isEmpty());
        assertTrue(fixture.events.isEmpty());
        assertNull(fixture.revision);
    }

    @Test
    public void deletingPublishedRuleOfflinesRuntimeButKeepsDeletedProjectionAndAudit() {
        Fixture fixture = new Fixture();
        fixture.published = publication(9L, 19L);
        fixture.revision = new RuleRevision();
        fixture.revision.setId(19L);
        fixture.revision.setDefinitionId(9L);
        fixture.revision.setArtifactId(29L);
        fixture.revision.setState("PUBLISHED");
        fixture.revision.setLockVersion(0);
        fixture.definition.setStatus(1);

        AppliedResource result = fixture.delete();

        assertEquals("DELETED", result.effectiveStatus());
        assertEquals("删除不能被下线步骤改回status=2", Integer.valueOf(-1), fixture.definition.getStatus());
        assertEquals(Integer.valueOf(0), fixture.published.getStatus());
        assertEquals("OFFLINE", fixture.revision.getState());
        assertEquals(Long.valueOf(29), result.artifactId());
        assertEquals(1, fixture.outbox.size());
        RulePushMessage message = JSON.parseObject(fixture.outbox.get(0).getMessageJson(), RulePushMessage.class);
        assertEquals("UNPUBLISH", message.getAction());
        assertEquals(Long.valueOf(9), message.getDefinitionId());
        assertEquals("project-by-id", message.getProjectCode());
        assertEquals("OFFLINE", fixture.events.get(0).getAction());
    }

    @Test
    public void deletingLegacyPublishedRuleDisablesPublicationByIdWithoutInventingRevision() {
        Fixture fixture = new Fixture();
        fixture.published = publication(9L, null);
        fixture.definition.setStatus(1);

        AppliedResource result = fixture.delete();

        assertEquals("DELETED", result.effectiveStatus());
        assertEquals(Integer.valueOf(-1), fixture.definition.getStatus());
        assertEquals(Integer.valueOf(0), fixture.published.getStatus());
        assertNull(fixture.revision);
        assertNull(result.artifactId());
        assertTrue("无修订的旧发布不能伪造outbox关联ID", fixture.outbox.isEmpty());
        assertEquals(1, fixture.pushes.size());
        assertEquals("UNPUBLISH", fixture.pushes.get(0).getAction());
        assertEquals(Long.valueOf(9), fixture.pushes.get(0).getDefinitionId());
        assertEquals("old-display-code", fixture.pushes.get(0).getRuleCode());
        assertEquals("project-by-id", fixture.pushes.get(0).getProjectCode());
    }

    @Test
    public void legacyNotificationWaitsForApprovalCommitAndIsDiscardedOnRollback() {
        for (boolean commit : new boolean[]{false, true}) {
            Fixture fixture = new Fixture();
            fixture.published = publication(9L, null);
            TransactionSynchronizationManager.initSynchronization();
            try {
                fixture.delete();
                assertTrue("审批尚未提交时不能推送UNPUBLISH", fixture.pushes.isEmpty());
                List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
                if (commit) callbacks.forEach(TransactionSynchronization::afterCommit);
                callbacks.forEach(callback -> callback.afterCompletion(commit
                        ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK));
                assertEquals(commit ? 1 : 0, fixture.pushes.size());
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    public void legacyPublicationWriteFailurePreventsDeletingProjectionOrSendingNotification() {
        Fixture fixture = new Fixture();
        fixture.published = publication(9L, null);
        fixture.publicationUpdateCount = 0;
        IllegalStateException failure = assertThrows(IllegalStateException.class, fixture::delete);
        assertEquals("规则发布投影下线未生效", failure.getMessage());
        assertEquals(Integer.valueOf(0), fixture.definition.getStatus());
        assertTrue(fixture.pushes.isEmpty());
    }

    @Test
    public void explicitDisableStillRejectsNeverPublishedRule() {
        Fixture fixture = new Fixture();
        RuleGovernanceException failure = assertThrows(RuleGovernanceException.class,
                () -> fixture.apply("DISABLE"));
        assertEquals("FROZEN_REVISION_WRITE_REJECTED", failure.getCode());
    }

    @Test
    public void deletingRenamedLegacyRuleRetiresEveryActiveAliasByDefinitionId() {
        Fixture fixture = new Fixture();
        fixture.published = publication(9L, null);
        RulePublished alias = publication(9L, null);
        alias.setId(40L);
        alias.setRuleCode("renamed-display-code");
        fixture.aliases.add(alias);

        fixture.delete();

        assertEquals(Integer.valueOf(0), fixture.published.getStatus());
        assertEquals("改码留下的同ID运行时投影也必须撤销", Integer.valueOf(0), alias.getStatus());
        assertEquals(Integer.valueOf(-1), fixture.definition.getStatus());
        assertEquals(2, fixture.pushes.size());
        assertTrue(fixture.pushes.stream().allMatch(message -> Long.valueOf(9).equals(message.getDefinitionId())));
        assertEquals(List.of("old-display-code", "renamed-display-code"),
                fixture.pushes.stream().map(RulePushMessage::getRuleCode).toList());
    }

    @Test
    public void deletingModernPublicationAlsoRetiresRemainingLegacyAlias() {
        Fixture fixture = new Fixture();
        fixture.published = publication(9L, 19L);
        fixture.revision = new RuleRevision();
        fixture.revision.setId(19L);
        fixture.revision.setDefinitionId(9L);
        fixture.revision.setArtifactId(29L);
        fixture.revision.setState("PUBLISHED");
        fixture.revision.setLockVersion(0);
        RulePublished alias = publication(9L, null);
        alias.setId(40L);
        alias.setRuleCode("legacy-alias");
        fixture.aliases.add(alias);

        fixture.delete();

        assertEquals(Integer.valueOf(0), fixture.published.getStatus());
        assertEquals("现代修订下线后不能提前返回留下旧别名", Integer.valueOf(0), alias.getStatus());
        assertEquals("OFFLINE", fixture.revision.getState());
        assertEquals(Integer.valueOf(-1), fixture.definition.getStatus());
        assertEquals(1, fixture.outbox.size());
        assertEquals(1, fixture.pushes.size());
        assertEquals("legacy-alias", fixture.pushes.get(0).getRuleCode());
    }

    @Test
    public void remainingModernAliasUsesItsActualRevisionAndArtifactOutbox() {
        Fixture fixture = new Fixture();
        fixture.published = publication(9L, 19L);
        fixture.revision = new RuleRevision();
        fixture.revision.setId(19L);
        fixture.revision.setDefinitionId(9L);
        fixture.revision.setArtifactId(29L);
        fixture.revision.setState("PUBLISHED");
        fixture.revision.setLockVersion(0);
        RulePublished alias = publication(9L, 19L);
        alias.setId(40L);
        alias.setRuleCode("modern-alias");
        fixture.aliases.add(alias);

        fixture.delete();

        assertEquals(Integer.valueOf(0), alias.getStatus());
        assertTrue(fixture.pushes.isEmpty());
        assertEquals(2, fixture.outbox.size());
        RulePublishOutbox aliasEvent = fixture.outbox.get(1);
        assertEquals(Long.valueOf(19), aliasEvent.getRevisionId());
        assertEquals(Long.valueOf(29), aliasEvent.getArtifactId());
        RulePushMessage message = JSON.parseObject(aliasEvent.getMessageJson(), RulePushMessage.class);
        assertEquals("modern-alias", message.getRuleCode());
        assertEquals(Long.valueOf(9), message.getDefinitionId());
        assertEquals(Long.valueOf(19), message.getRevisionId());
    }

    private static RulePublished publication(Long definitionId, Long revisionId) {
        RulePublished published = new RulePublished();
        published.setId(39L);
        published.setDefinitionId(definitionId);
        published.setRevisionId(revisionId);
        published.setArtifactId(revisionId == null ? null : 29L);
        published.setRuleCode("old-display-code");
        published.setProjectCode("project-by-id");
        published.setStatus(1);
        return published;
    }

    private static final class Fixture {
        RuleDefinition definition = new RuleDefinition();
        RulePublished published;
        RuleRevision revision;
        final List<RulePublished> aliases = new ArrayList<>();
        final List<RulePublishOutbox> outbox = new ArrayList<>();
        final List<RuleLifecycleEvent> events = new ArrayList<>();
        final List<RulePushMessage> pushes = new ArrayList<>();
        int publicationUpdateCount = 1;
        final RuleGovernedResourceAdapter adapter;

        Fixture() {
            definition.setId(9L);
            definition.setRuleCode("current-display-code");
            definition.setRuleName("删除行为测试");
            definition.setModelType("SCORE");
            definition.setProjectCode("project-by-id");
            definition.setStatus(0);
            RuleLifecycleService lifecycle = new RuleLifecycleService() {
                @Override protected String actor() { return "reviewer"; }
                @Override protected RuleDefinition loadDefinition(Long id) { return definition; }
                @Override protected RuleDefinition lockDefinition(Long id) { assertEquals(Long.valueOf(9), id); return definition; }
                @Override protected RuleRevision findPublishedRevision(Long id) {
                    return revision != null && "PUBLISHED".equals(revision.getState()) ? revision : null;
                }
                @Override protected RuleRevision loadRevision(Long id) {
                    return revision != null && id.equals(revision.getId()) ? revision : null;
                }
                @Override protected boolean compareAndSetState(RuleRevision value, String expected, String target) {
                    return expected.equals(value.getState());
                }
                @Override protected void persistRevisionSnapshot(RuleRevision value) { revision = value; }
                @Override protected void insertEvent(RuleLifecycleEvent event) { events.add(event); }
            };
            ReflectionTestUtils.setField(lifecycle, "definitionService", new RuleDefinitionService() {
                @Override public boolean updateById(RuleDefinition value) { definition = value; return true; }
            });
            ReflectionTestUtils.setField(lifecycle, "publishedMapper", mapper(RulePublishedMapper.class, (name, args) -> {
                if ("selectOne".equals(name)) return published;
                if ("selectList".equals(name)) {
                    List<RulePublished> active = new ArrayList<>();
                    if (published != null && Integer.valueOf(1).equals(published.getStatus())) active.add(published);
                    aliases.stream().filter(value -> Integer.valueOf(1).equals(value.getStatus())).forEach(active::add);
                    return active;
                }
                if ("updateById".equals(name)) {
                    RulePublished updated = (RulePublished) args[0];
                    if (published != null && published.getId().equals(updated.getId())) published = updated;
                    return publicationUpdateCount;
                }
                return null;
            }));
            ReflectionTestUtils.setField(lifecycle, "pushService", new RulePushService() {
                @Override public void push(RulePushMessage message) { pushes.add(message); }
            });
            ReflectionTestUtils.setField(lifecycle, "outboxMapper", mapper(RulePublishOutboxMapper.class, (name, args) -> {
                if ("insert".equals(name)) { outbox.add((RulePublishOutbox) args[0]); return 1; }
                return null;
            }));
            ProjectAuthProperties properties = new ProjectAuthProperties();
            properties.setActiveKeyId("test");
            properties.setMasterKeys(Map.of("test", "test-governance-master-key-32-characters-long"));
            adapter = new RuleGovernedResourceAdapter(new SimpleEntityGovernedResourceAdapter.EntityStore<>() {
                @Override public RuleDefinition load(Long id) { return definition; }
                @Override public void insert(RuleDefinition value) { fail("删除不得创建规则"); }
                @Override public void update(RuleDefinition value) { definition = value; }
            }, new GovernanceSecretCodec(new CredentialCipher(properties)), lifecycle, null,
                    mapper(RuleDefinitionContentMapper.class, (name, args) -> null),
                    mapper(RuleDefinitionInputFieldMapper.class, (name, args) -> "selectList".equals(name) ? List.of() : null),
                    mapper(RuleDefinitionOutputFieldMapper.class, (name, args) -> "selectList".equals(name) ? List.of() : null));
        }

        AppliedResource delete() { return apply("DELETE"); }

        AppliedResource apply(String action) {
            return adapter.apply(new ApprovalApplyContext(107L, 9L, 2, action,
                    adapter.loadEffective(9L), "reviewer", null));
        }
    }

    private interface Call { Object invoke(String name, Object[] args); }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, Call call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Object result = call.invoke(method.getName(), args);
            if (result != null) return result;
            if (method.getReturnType() == int.class) return 1;
            if (method.getReturnType() == long.class) return 0L;
            return null;
        });
    }
}
