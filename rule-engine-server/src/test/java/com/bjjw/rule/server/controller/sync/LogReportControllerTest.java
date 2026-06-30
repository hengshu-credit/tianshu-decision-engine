package com.bjjw.rule.server.controller.sync;

import com.bjjw.rule.model.entity.RuleExecutionLog;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.RuleBillingService;
import com.bjjw.rule.server.service.RuleExecutionLogService;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LogReportControllerTest {

    @Test
    public void rejectsReportWithoutProjectScope() {
        LogReportController controller = newController(new CapturingLogService(), new CapturingBillingService());

        R<Void> result = controller.report(Collections.singletonList(new RuleExecutionLog()), new MockHttpServletRequest());

        assertEquals(401, result.getCode());
    }

    @Test
    public void rejectsLogFromOtherProject() {
        LogReportController controller = newController(new CapturingLogService(), new CapturingBillingService());
        MockHttpServletRequest request = requestWithProject("RISK_DEMO");
        RuleExecutionLog log = new RuleExecutionLog();
        log.setProjectCode("OTHER");

        R<Void> result = controller.report(Collections.singletonList(log), request);

        assertEquals(403, result.getCode());
    }

    @Test
    public void savesScopedLogsAndRecordsBilling() {
        CapturingLogService logService = new CapturingLogService();
        CapturingBillingService billingService = new CapturingBillingService();
        LogReportController controller = newController(logService, billingService);
        RuleExecutionLog log = new RuleExecutionLog();
        log.setRuleCode("RC_TEST");
        log.setSuccess(1);
        log.setExecuteTimeMs(12L);

        R<Void> result = controller.report(Collections.singletonList(log), requestWithProject("RISK_DEMO"));

        assertEquals(200, result.getCode());
        assertEquals(1, logService.saved.size());
        assertEquals("RISK_DEMO", logService.saved.get(0).getProjectCode());
        assertEquals("CLIENT", logService.saved.get(0).getSource());
        assertEquals(1, billingService.billed.size());
        assertEquals("RC_TEST", billingService.billed.get(0).getRuleCode());
    }

    private static MockHttpServletRequest requestWithProject(String projectCode) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("projectId", 1L);
        request.setAttribute("projectCode", projectCode);
        return request;
    }

    private static LogReportController newController(RuleExecutionLogService logService,
                                                     RuleBillingService billingService) {
        LogReportController controller = new LogReportController();
        setField(controller, "logService", logService);
        setField(controller, "billingService", billingService);
        return controller;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static class CapturingLogService extends RuleExecutionLogService {
        private final List<RuleExecutionLog> saved = new ArrayList<>();

        @Override
        public boolean saveBatch(Collection<RuleExecutionLog> entityList) {
            saved.addAll(entityList);
            return true;
        }
    }

    private static class CapturingBillingService extends RuleBillingService {
        private final List<RuleExecutionLog> billed = new ArrayList<>();

        @Override
        public void recordEngineExecutionLog(RuleExecutionLog log) {
            billed.add(log);
        }
    }
}
