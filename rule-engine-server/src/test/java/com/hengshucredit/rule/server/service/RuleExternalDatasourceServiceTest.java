package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

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
}
