package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.junit.Assert.assertTrue;

public class RuleExternalApiConfigServiceTest {

    @BeforeClass
    public static void initTableInfo() {
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""),
                RuleExternalApiConfig.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""),
                RuleExternalDatasource.class);
    }

    @Test
    public void projectIdFiltersApisThroughStableDatasourceOwnership() {
        RecordingMapper apiMapper = new RecordingMapper();
        RecordingMapper datasourceMapper = new RecordingMapper();
        RuleExternalDatasource datasource = new RuleExternalDatasource();
        datasource.setId(3L);
        datasourceMapper.records = Collections.singletonList(datasource);

        RuleExternalApiConfigService service = new RuleExternalApiConfigService();
        ReflectionTestUtils.setField(service, "baseMapper",
                apiMapper.proxy(RuleExternalApiConfigMapper.class));
        ReflectionTestUtils.setField(service, "datasourceMapper",
                datasourceMapper.proxy(RuleExternalDatasourceMapper.class));
        ReflectionTestUtils.setField(service, "projectFilterService",
                new ProjectFilterService());

        service.pageList(1, 10, null, 7L, null, null,
                null, null, null, null, null);

        assertTrue(datasourceMapper.wrapper.getSqlSegment()
                .contains("projectId"));
        assertTrue(datasourceMapper.wrapper.getParamNameValuePairs()
                .containsValue(7L));
        assertTrue(apiMapper.wrapper.getSqlSegment()
                .contains("datasourceId"));
        assertTrue(apiMapper.wrapper.getParamNameValuePairs()
                .containsValue(3L));
    }

    @Test
    public void deletingDatasourceInvalidatesAllApiRuntimeRegistries() {
        RuleExternalApiConfig first = new RuleExternalApiConfig();
        first.setId(11L);
        RuleExternalApiConfig second = new RuleExternalApiConfig();
        second.setId(12L);
        RecordingMapper apiMapper = new RecordingMapper();
        apiMapper.records = java.util.Arrays.asList(first, second);

        RuleExternalApiConfigService service = new RuleExternalApiConfigService();
        ReflectionTestUtils.setField(service, "baseMapper", apiMapper.proxy(RuleExternalApiConfigMapper.class));
        ReflectionTestUtils.setField(service, "apiHttpClientRegistry", new ApiHttpClientRegistry());
        ReflectionTestUtils.setField(service, "externalApiGuardRegistry", new ExternalApiGuardRegistry(new ExternalCallProperties()));
        ReflectionTestUtils.setField(service, "circuitBreakerRegistry", new ExternalApiCircuitBreakerRegistry(new ExternalCallProperties()));
        ReflectionTestUtils.setField(service, "externalApiResponseCache", new ExternalApiResponseCache(new ExternalCallProperties(), null, System::currentTimeMillis));

        service.deleteByDatasourceId(7L);

        assertTrue(apiMapper.wrapper.getSqlSegment().contains("datasourceId"));
    }

    private static class RecordingMapper {
        private LambdaQueryWrapper<?> wrapper;
        private java.util.List<?> records = Collections.emptyList();

        private <T> T proxy(Class<T> mapperType) {
            return mapperType.cast(Proxy.newProxyInstance(
                    mapperType.getClassLoader(), new Class[]{mapperType},
                    (proxy, method, args) -> {
                        if ("selectList".equals(method.getName())) {
                            wrapper = (LambdaQueryWrapper<?>) args[0];
                            return records;
                        }
                        if ("selectPage".equals(method.getName())) {
                            Page<?> page = (Page<?>) args[0];
                            wrapper = (LambdaQueryWrapper<?>) args[1];
                            page.setRecords(Collections.emptyList());
                            page.setTotal(0);
                            return page;
                        }
                        return defaultValue(method.getReturnType());
                    }));
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }
}
