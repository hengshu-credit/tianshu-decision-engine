package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.auth.ProjectAuthType;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleBillingService;
import com.hengshucredit.rule.server.service.RuleExecutionLogService;
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
        ProjectAuthContext.direct(1L, "RISK_DEMO", 11L,
                "BASIC_MAIN", ProjectAuthType.BASIC).attach(request);
        RuleExecutionLog log = new RuleExecutionLog();
        log.setRuleCode("RC_TEST");
        log.setProjectCode("OTHER");

        R<Void> result = controller.report(Collections.singletonList(log), request);

        assertEquals(403, result.getCode());
    }

    @Test
    public void rejectsRuleOutsideAuthenticatedProjectEvenWhenProjectCodeIsBlank() {
        CapturingBillingService billingService = new CapturingBillingService();
        billingService.ruleAccessible = false;
        LogReportController controller = newController(new CapturingLogService(), billingService);
        RuleExecutionLog log = new RuleExecutionLog();
        log.setRuleCode("OTHER_PROJECT_RULE");

        MockHttpServletRequest request = requestWithProject("RISK_DEMO");
        ProjectAuthContext.direct(1L, "RISK_DEMO", 11L,
                "BASIC_MAIN", ProjectAuthType.BASIC).attach(request);

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
        log.setAuthCode("FAKE_AUTH");
        log.setTokenCode("FAKE_TOKEN");

        MockHttpServletRequest request = requestWithProject("RISK_DEMO");
        ProjectAuthContext.temporary(1L, "RISK_DEMO", 11L, "BASIC_MAIN", ProjectAuthType.BASIC,
                21L, "TOKEN_A", "VALID").attach(request);
        R<Void> result = controller.report(Collections.singletonList(log), request);

        assertEquals(200, result.getCode());
        assertEquals(1, logService.saved.size());
        assertEquals("RISK_DEMO", logService.saved.get(0).getProjectCode());
        assertEquals("CLIENT", logService.saved.get(0).getSource());
        assertEquals(Long.valueOf(11L), logService.saved.get(0).getAuthId());
        assertEquals("BASIC_MAIN", logService.saved.get(0).getAuthCode());
        assertEquals(ProjectAuthType.BASIC, logService.saved.get(0).getAuthType());
        assertEquals(Long.valueOf(21L), logService.saved.get(0).getTokenId());
        assertEquals("TOKEN_A", logService.saved.get(0).getTokenCode());
        assertEquals("VALID", logService.saved.get(0).getAuthPhase());
        assertEquals(1, billingService.billed.size());
        assertEquals("RC_TEST", billingService.billed.get(0).getRuleCode());
        assertEquals("BASIC_MAIN", billingService.authContext.getAuthCode());
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
        private boolean ruleAccessible = true;
        private ProjectAuthContext authContext;

        @Override
        public boolean isRuleAccessible(Long projectId, String ruleCode) {
            return ruleAccessible;
        }

        @Override
        public void recordEngineExecutionLog(RuleExecutionLog log, ProjectAuthContext context) {
            billed.add(log);
            authContext = context;
        }
    }
}
