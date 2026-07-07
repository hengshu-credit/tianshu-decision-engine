package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ExternalConfigDefaultsTest {

    @Test
    public void datasourceBlankAuthConfigBecomesNullForJsonColumn() throws Exception {
        RuleExternalDatasource datasource = new RuleExternalDatasource();
        datasource.setScope("GLOBAL");
        datasource.setAuthType("NONE");
        datasource.setAuthConfig("");

        invokeFillDefaults(new RuleExternalDatasourceService(), datasource);

        assertNull(datasource.getAuthConfig());
        assertEquals(Long.valueOf(0L), datasource.getProjectId());
    }

    @Test
    public void apiBlankJsonConfigsBecomeNullButMappingsArePreserved() throws Exception {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setHeaderConfig("");
        config.setQueryConfig("  ");
        config.setRequestMapping("{\"customerId\":\"$.customerId\"}");
        config.setResponseMapping("");
        config.setAuthApiConfig("");

        invokeFillDefaults(new RuleExternalApiConfigService(), config);

        assertNull(config.getHeaderConfig());
        assertNull(config.getQueryConfig());
        assertEquals("{\"customerId\":\"$.customerId\"}", config.getRequestMapping());
        assertNull(config.getResponseMapping());
        assertNull(config.getAuthApiConfig());
        assertNull(config.getContentType());
        assertEquals(Integer.valueOf(0), config.getResponseCacheSeconds());
    }

    @Test
    public void apiAsyncDefaultsPreserveSelectedAsyncConfigOnly() throws Exception {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setRequestMode("ASYNC");
        config.setAsyncResultMode("POLL");
        config.setAsyncPollConfig("{\"resultEndpointUrl\":\"/result/${taskId}\"}");
        config.setAsyncCallbackConfig("");
        config.setAsyncCallbackUrl("  ");
        config.setAsyncResultPath("body.data");

        invokeFillDefaults(new RuleExternalApiConfigService(), config);

        assertEquals("ASYNC", config.getRequestMode());
        assertEquals("POLL", config.getAsyncResultMode());
        assertEquals("{\"resultEndpointUrl\":\"/result/${taskId}\"}", config.getAsyncPollConfig());
        assertNull(config.getAsyncCallbackConfig());
        assertNull(config.getAsyncCallbackUrl());
        assertEquals("body.data", config.getAsyncResultPath());
    }

    private void invokeFillDefaults(Object service, Object target) throws Exception {
        Method method = service.getClass().getDeclaredMethod("fillDefaults", target.getClass());
        method.setAccessible(true);
        method.invoke(service, target);
    }
}
