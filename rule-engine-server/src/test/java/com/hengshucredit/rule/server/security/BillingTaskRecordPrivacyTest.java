package com.hengshucredit.rule.server.security;

import com.hengshucredit.rule.model.entity.RuleBillingSummaryTask;
import com.hengshucredit.rule.server.controller.mgmt.BillingController;
import com.hengshucredit.rule.server.service.BillingSummaryTaskService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class BillingTaskRecordPrivacyTest {
    @Test public void sharedMaintenanceProgressDoesNotExposeOtherAccountsOrGlobalCounts() {
        var task = new RuleBillingSummaryTask(); task.setTaskId("task"); task.setStatus("SUCCEEDED");
        task.setApplicant("another-account"); task.setRecordCount(999L); task.setSummaryCount(3);
        var controller = new BillingController();
        ReflectionTestUtils.setField(controller, "summaryTasks", new BillingSummaryTaskService() {
            @Override public RuleBillingSummaryTask get(String id) { return task; }
        });
        var scope = new java.util.concurrent.atomic.AtomicReference<>(new ConsoleProjectAccess.Scope(8L, Set.of(9L), Set.of("billing:view")));
        ReflectionTestUtils.setField(controller, "consoleProjects", new ConsoleProjectAccess() {
            @Override public Scope current() { return scope.get(); }
        });
        var visible = controller.getSummaryTask("task").getData();
        assertEquals("SUCCEEDED", visible.getStatus()); assertNull(visible.getApplicant()); assertNull(visible.getRecordCount());
        assertEquals("another-account", task.getApplicant());
        scope.set(new ConsoleProjectAccess.Scope(8L, Set.of(9L), Set.of("billing:view", "account:cross-record-view")));
        assertNull(controller.getSummaryTask("task").getData().getRecordCount());
        scope.set(new ConsoleProjectAccess.Scope(8L, Set.of(), Set.of("billing:view", "account:cross-record-view"), true, true, Map.of()));
        assertSame(task, controller.getSummaryTask("task").getData());
    }
}
