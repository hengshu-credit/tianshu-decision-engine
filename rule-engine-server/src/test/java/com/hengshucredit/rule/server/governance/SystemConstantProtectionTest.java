package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.service.SystemConstantPolicy;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class SystemConstantProtectionTest {
    @Test
    public void forgedDraftFlagsAndCodesCannotChangeDeleteOrRestoreSystemConstants() {
        var constant = new RuleVariable(); constant.setId(17L); constant.setSystemConstant(true);
        AtomicInteger writes = new AtomicInteger();
        var store = new SimpleEntityGovernedResourceAdapter.EntityStore<RuleVariable>() {
            public RuleVariable load(Long id) { return constant; }
            public void insert(RuleVariable value) { writes.incrementAndGet(); }
            public void update(RuleVariable value) { writes.incrementAndGet(); }
        };
        var adapter = new VariableGovernedResourceAdapter(store, null, null);
        var forged = ResourceSnapshot.ofJson("{\"id\":17,\"varCode\":\"user_changed\",\"systemConstant\":false}");
        assertThrows(IllegalArgumentException.class, () -> adapter.normalizeDraft(forged));
        for (String action : new String[]{"UPDATE", "DELETE", "DISABLE", "RESTORE", "DRAFT_DELETE"}) {
            assertThrows(IllegalArgumentException.class, () -> adapter.apply(new ApprovalApplyContext(null, 17L, 2, action, forged, "admin", null)));
        }
        assertEquals(0, writes.get());
        assertThrows(IllegalArgumentException.class, () -> adapter.normalizeDraft(ResourceSnapshot.ofJson("{\"systemConstant\":true}")));
    }
    @Test
    public void laterConstantsRemainEditableEvenWithSimilarNames() {
        var constant = new RuleVariable(); constant.setId(999L); constant.setVarCode("ONE"); constant.setSystemConstant(false);
        SystemConstantPolicy.requireMutable(constant);
        constant.setSystemConstant(true);
        assertThrows(IllegalArgumentException.class, () -> SystemConstantPolicy.requireMutable(constant));
    }
}
