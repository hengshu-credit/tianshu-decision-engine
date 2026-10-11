package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.*;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class RuleDraftDeletionServiceTest {
    @Test
    public void unfinishedRuleCanBeDiscardedWithoutCompilingOrPublishing() {
        Fixture f = new Fixture();
        assertTrue(f.service.isUnapproved(f.service.lock(7L)));
        f.service.discard(f.definition, "owner");
        assertEquals(Integer.valueOf(-1), f.definition.getStatus());
        assertEquals("DELETED", f.revision.getState());
        assertEquals("broken unfinished content", f.revision.getModelJson());
        assertNull(f.revision.getArtifactId());
        assertEquals(1, f.events.size());
        assertEquals("DELETE_UNAPPROVED", f.events.get(0).getAction());
        assertEquals("owner", f.events.get(0).getActor());
    }

    @Test
    public void eachApprovalOrPublicationEvidenceRequiresApprovalEvenWhenStatusIsUnpublished() {
        for (String history : List.of("requestMapper", "governedVersionMapper", "revisionMapper", "publishedMapper", "versionMapper")) {
            Fixture f = new Fixture();
            f.history = history;
            assertFalse(history, f.service.isUnapproved(f.definition));
            assertEquals(Integer.valueOf(0), f.definition.getStatus());
            assertTrue(f.events.isEmpty());
        }
    }

    @Test
    public void publishedOfflineAndUnknownStatusCannotBeTreatedAsANewDraft() {
        for (Integer status : new Integer[]{1, 2, null, -1}) {
            Fixture f = new Fixture();
            f.definition.setStatus(status);
            assertFalse(f.service.isUnapproved(f.definition));
        }
        Fixture f = new Fixture();
        f.definition.setPublishedVersion(1);
        assertFalse(f.service.isUnapproved(f.definition));
    }

    @Test
    public void deletedRuleCannotBeLockedForAnotherApprovalOrDeletion() {
        Fixture f = new Fixture();
        f.definition.setStatus(-1);
        assertThrows(IllegalArgumentException.class, () -> f.service.lock(7L));
    }

    private static class Fixture {
        final RuleDraftDeletionService service = new RuleDraftDeletionService();
        final RuleDefinition definition = new RuleDefinition();
        final RuleRevision revision = new RuleRevision();
        final List<RuleLifecycleEvent> events = new ArrayList<>();
        String history;

        Fixture() {
            definition.setId(7L); definition.setStatus(0);
            revision.setId(8L); revision.setDefinitionId(7L); revision.setState("DRAFT");
            revision.setModelJson("broken unfinished content");
            bind("definitionMapper", RuleDefinitionMapper.class);
            bind("revisionMapper", RuleRevisionMapper.class);
            bind("publishedMapper", RulePublishedMapper.class);
            bind("versionMapper", RuleDefinitionVersionMapper.class);
            bind("requestMapper", GovernanceApprovalRequestMapper.class);
            bind("governedVersionMapper", GovernedResourceVersionMapper.class);
            bind("eventMapper", RuleLifecycleEventMapper.class);
        }

        private void bind(String field, Class<?> type) {
            Object mapper = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "selectOne" -> definition;
                    case "selectCount" -> field.equals(history) ? 1L : 0L;
                    case "selectList" -> List.of(revision);
                    case "updateById" -> 1;
                    case "insert" -> { events.add((RuleLifecycleEvent) args[0]); yield 1; }
                    default -> throw new AssertionError("Unexpected mapper call: " + method.getName());
                };
            });
            ReflectionTestUtils.setField(service, field, mapper);
        }
    }
}
