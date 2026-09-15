package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDefinitionVersion;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.model.entity.RuleVersionBinding;
import com.hengshucredit.rule.model.entity.RulePublished;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RuleVersionBindingServiceTest {
    @Test
    public void olderPublishedReadMustNotBorrowNewOverwriteGeneration() {
        Fixture service = new Fixture();
        RuleDefinitionVersion old = snapshot(18, 1, "old"); old.setRevisionId(29L);
        old.setBusinessVersion(1); old.setBindingGeneration(1L);
        service.history.add(old);
        RuleDefinitionVersion replacement = snapshot(23, 2, "new"); replacement.setRevisionId(30L);
        replacement.setBusinessVersion(1); replacement.setBindingGeneration(2L);
        service.history.add(replacement);
        RuleVersionBinding binding = new RuleVersionBinding(); binding.setId(81L); binding.setDefinitionId(22L);
        binding.setVersionNo(1); binding.setGeneration(2L); binding.setSnapshotId(23L); binding.setStatus(1);
        service.bindings.add(binding);
        RulePublished readBeforeCommit = new RulePublished(); readBeforeCommit.setDefinitionId(22L);
        readBeforeCommit.setVersion(1); readBeforeCommit.setRevisionId(29L); readBeforeCommit.setCompiledScript("old");
        RulePublished resolved = service.resolvePublished(readBeforeCommit, null);
        assertEquals(Long.valueOf(1), resolved.getBindingGeneration());
        assertEquals("old", resolved.getCompiledScript());
    }
    @Test
    public void legacyHistoryDoesNotCollideWithNewRevisionOne() {
        Fixture service = new Fixture();
        service.history.add(snapshot(18, 1, "old1"));
        service.history.add(snapshot(23, 2, "old2"));
        RuleDefinitionVersion next = snapshot(30, 0, "new3");
        RuleVersionBinding binding = service.activate(revision("NEW", null, null), next);
        assertEquals(Integer.valueOf(3), next.getVersion());
        assertEquals(Integer.valueOf(3), next.getBusinessVersion());
        assertEquals(Integer.valueOf(3), binding.getVersionNo());
        assertEquals("old1", service.history.get(0).getCompiledScript());
        assertEquals("old2", service.history.get(1).getCompiledScript());
    }

    @Test
    public void overwriteKeepsBusinessIdAppendsSnapshotAndDoesNotMoveLatestBackwards() {
        Fixture service = new Fixture();
        service.history.add(snapshot(18, 1, "old1"));
        service.history.add(snapshot(23, 2, "old2"));
        service.ensureLegacyBindings(22L);
        RuleVersionBinding first = service.bindings.get(0);
        RuleDefinitionVersion replacement = snapshot(30, 0, "replacement");
        RuleVersionBinding updated = service.activate(revision("OVERWRITE", first.getId(), 1L), replacement);
        assertEquals(first.getId(), updated.getId());
        assertEquals(Long.valueOf(2), updated.getGeneration());
        assertEquals(Integer.valueOf(1), replacement.getBusinessVersion());
        assertEquals(Integer.valueOf(3), replacement.getVersion());
        assertEquals(2, service.latestBusinessVersion(22L));
        assertEquals("old1", service.history.get(0).getCompiledScript());
        assertEquals(3, service.history.size());
        assertThrows(IllegalStateException.class,
                () -> service.activate(revision("OVERWRITE", first.getId(), 1L), snapshot(31, 0, "stale")));
        assertEquals(3, service.history.size());
    }

    private static RuleDefinitionVersion snapshot(long id, int no, String script) {
        RuleDefinitionVersion value = new RuleDefinitionVersion();
        value.setId(id); value.setDefinitionId(22L); value.setVersion(no);
        value.setModelJson("{}"); value.setCompiledScript(script); return value;
    }
    private static RuleRevision revision(String mode, Long target, Long generation) {
        RuleRevision value = new RuleRevision();
        value.setId(29L); value.setDefinitionId(22L); value.setRevisionNo(1);
        value.setPublishMode(mode); value.setTargetVersionId(target); value.setTargetGeneration(generation);
        return value;
    }
    private static class Fixture extends RuleVersionBindingService {
        final List<RuleDefinitionVersion> history = new ArrayList<>();
        final List<RuleVersionBinding> bindings = new ArrayList<>();
        @Override protected List<RuleDefinitionVersion> history(Long id) { return history; }
        @Override protected List<RuleVersionBinding> bindings(Long id) { return bindings; }
        @Override protected void insertBinding(RuleVersionBinding value) { value.setId((long) bindings.size() + 1); bindings.add(value); }
        @Override protected void updateBinding(RuleVersionBinding value, long expected) { }
        @Override protected void insertSnapshot(RuleDefinitionVersion value) { history.add(value); }
    }
}
