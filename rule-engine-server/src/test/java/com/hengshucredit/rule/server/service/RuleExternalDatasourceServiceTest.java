package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class RuleExternalDatasourceServiceTest {

    @Test
    public void ruleEngineDatasourceUsesLocalBaseUrlWhenBlank() throws Exception {
        RuleExternalDatasource datasource = new RuleExternalDatasource();
        datasource.setProtocol("RULE_ENGINE");

        Method method = RuleExternalDatasourceService.class.getDeclaredMethod("fillDefaults", RuleExternalDatasource.class);
        method.setAccessible(true);
        method.invoke(new RuleExternalDatasourceService(), datasource);

        assertEquals("rule-engine://local", datasource.getBaseUrl());
    }

    @Test
    public void datasourceAuthTypeIsNormalizedAndUnknownTypesAreRejected() throws Exception {
        RuleExternalDatasource datasource = new RuleExternalDatasource();
        datasource.setAuthType(" bearer ");
        Method method = RuleExternalDatasourceService.class.getDeclaredMethod(
                "fillDefaults", RuleExternalDatasource.class);
        method.setAccessible(true);
        method.invoke(new RuleExternalDatasourceService(), datasource);
        assertEquals("BEARER", datasource.getAuthType());

        RuleExternalDatasource invalid = new RuleExternalDatasource();
        invalid.setAuthType("UNKNOWN");
        assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(new RuleExternalDatasourceService(), invalid));
    }
}
