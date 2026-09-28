package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.model.dto.ResourcePreflightReport;
import com.hengshucredit.rule.server.mapper.RuleDbDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.Map;

public class ResourcePreflightServiceTest {
    private ResourcePreflightService service;
    private RuleDbDatasourceMapper dbMapper;
    private RuleVariableMapper variableMapper;

    @Before
    public void setUp() {
        service = new ResourcePreflightService();
        dbMapper = mapper(RuleDbDatasourceMapper.class, Map.of());
        variableMapper = mapper(RuleVariableMapper.class, Map.of());
        ReflectionTestUtils.setField(service, "dbDatasourceMapper", dbMapper);
        ReflectionTestUtils.setField(service, "variableMapper", variableMapper);
        ReflectionTestUtils.setField(service, "apiConfigMapper", mapper(RuleExternalApiConfigMapper.class, Map.of()));
        ReflectionTestUtils.setField(service, "externalDatasourceMapper", mapper(RuleExternalDatasourceMapper.class, Map.of()));
        ReflectionTestUtils.setField(service, "dataObjectMapper", mapper(RuleDataObjectMapper.class, Map.of()));
        ReflectionTestUtils.setField(service, "modelMapper", mapper(RuleModelMapper.class, Map.of()));
    }

    @Test
    public void missingDatabaseIsReportedWithSavedConfigurationScope() {
        ResourcePreflightReport report = service.database(9L);
        Assert.assertFalse(report.isValid());
        Assert.assertEquals("DATABASE", report.getResourceType());
        Assert.assertEquals("SAVED_CONFIGURATION", report.getCheckScope());
        Assert.assertNotNull(report.getCheckedAt());
        Assert.assertEquals("RESOURCE_NOT_FOUND", report.getErrors().get(0).getCode());
    }

    @Test
    public void databaseVariableRejectsWriteSqlWithoutConnecting() {
        RuleVariable variable = new RuleVariable();
        variable.setStatus(1);
        variable.setVarSource("DB");
        variable.setSourceConfig("{\"dbDatasourceId\":3,\"dbSql\":\"DELETE FROM t\"}");
        RuleDbDatasource datasource = new RuleDbDatasource();
        datasource.setStatus(1);
        variableMapper = mapper(RuleVariableMapper.class, Map.of(7L, variable));
        dbMapper = mapper(RuleDbDatasourceMapper.class, Map.of(3L, datasource));
        ReflectionTestUtils.setField(service, "variableMapper", variableMapper);
        ReflectionTestUtils.setField(service, "dbDatasourceMapper", dbMapper);

        ResourcePreflightReport report = service.variable(7L);
        Assert.assertFalse(report.isValid());
        Assert.assertTrue(report.getErrors().stream().anyMatch(issue -> "DB_SQL_NOT_READ_ONLY".equals(issue.getCode())));
    }

    @Test
    public void malformedVariableJsonIsReportedWithoutCallingExternalSystems() {
        RuleVariable variable = new RuleVariable();
        variable.setStatus(1);
        variable.setVarSource("API");
        variable.setSourceConfig("{not-json");
        variableMapper = mapper(RuleVariableMapper.class, Map.of(8L, variable));
        ReflectionTestUtils.setField(service, "variableMapper", variableMapper);

        ResourcePreflightReport report = service.variable(8L);
        Assert.assertFalse(report.isValid());
        Assert.assertEquals("SOURCE_CONFIG_INVALID", report.getErrors().get(0).getCode());
        Assert.assertNotNull(report.getErrors());
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, Map<Long, ?> rows) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if ("selectById".equals(method.getName()) && args != null && args.length == 1) {
                Object key = args[0];
                return rows.get(key instanceof Number ? ((Number) key).longValue() : key);
            }
            if (method.getReturnType().isPrimitive()) return method.getReturnType() == boolean.class ? false : 0;
            return null;
        });
    }
}
