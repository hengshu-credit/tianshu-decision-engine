package com.hengshucredit.rule.server.service;
import com.hengshucredit.rule.model.entity.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RulePublicationValidatorTest {
    @Test public void changingLatestCanCreateCycleButFixedOldVersionDoesNotFollow() {
        RuleVersionBinding a = binding(1, 10, 1), b1 = binding(2, 20, 1), b2 = binding(3, 20, 2);
        Map<Long, RuleDefinitionVersion> history = new HashMap<>();
        history.put(1L, snapshot(1, "{\"type\":\"rule-call\",\"ruleId\":20}"));
        history.put(2L, snapshot(2, "{}")); history.put(3L, snapshot(3, "{}"));
        RuleRevision candidate = new RuleRevision(); candidate.setDefinitionId(20L); candidate.setPublishMode("NEW");
        candidate.setModelJson("{\"type\":\"rule-call\",\"ruleId\":10}");
        RulePublicationValidator validator = new RulePublicationValidator();
        assertThrows(IllegalStateException.class, () -> validator.validateGraph(candidate, List.of(a,b1,b2), history));
        history.get(1L).setModelJson("{\"type\":\"rule-call\",\"ruleId\":20,\"versionMode\":\"FIXED\",\"versionBindingId\":2}");
        validator.validateGraph(candidate, List.of(a,b1,b2), history);
    }
    private static RuleVersionBinding binding(long id,long definition,int version) {
        RuleVersionBinding b=new RuleVersionBinding(); b.setId(id);b.setDefinitionId(definition);b.setVersionNo(version);b.setStatus(1);b.setSnapshotId(id);return b;
    }
    private static RuleDefinitionVersion snapshot(long id,String model) {
        RuleDefinitionVersion s=new RuleDefinitionVersion();s.setId(id);s.setModelJson(model);return s;
    }
}
