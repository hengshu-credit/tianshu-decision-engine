package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.entity.RuleBillingConfig;
import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.model.entity.RuleExperiment;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleListLibrary;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.mapper.RuleBillingConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleDbDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleExperimentMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleListLibraryMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProjectScopedListFilterTest {

    @BeforeClass
    public static void initTableInfo() {
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RuleListLibrary.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RuleDbDatasource.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RuleExperiment.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RuleBillingConfig.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RuleExternalDatasource.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RuleExternalApiConfig.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RuleProject.class);
    }

    @Test
    public void listLibrariesFilterByResolvedProjectIds() {
        RecordingPageMapper recording = new RecordingPageMapper();
        RuleListService service = new RuleListService();
        ReflectionTestUtils.setField(service, "baseMapper", recording.proxy(RuleListLibraryMapper.class));
        ReflectionTestUtils.setField(service, "projectFilterService", projectFilter(project(7L, "RISK_A")));

        service.pageLibraries(1, 10, null, "RISK", "风控", null, null, null, null);

        assertProjectIdFilter(recording.wrapper, 7L);
    }

    @Test
    public void dbDatasourcesFilterByResolvedProjectIds() {
        RecordingPageMapper recording = new RecordingPageMapper();
        RuleDbDatasourceService service = new RuleDbDatasourceService();
        ReflectionTestUtils.setField(service, "baseMapper", recording.proxy(RuleDbDatasourceMapper.class));
        ReflectionTestUtils.setField(service, "projectFilterService", projectFilter(project(7L, "RISK_A")));

        service.pageList(1, 10, null, null, "RISK", "风控", null, null, null, null);

        assertProjectIdFilter(recording.wrapper, 7L);
    }

    @Test
    public void experimentsFilterByResolvedProjectIds() {
        RecordingPageMapper recording = new RecordingPageMapper();
        RuleExperimentService service = new RuleExperimentService();
        ReflectionTestUtils.setField(service, "baseMapper", recording.proxy(RuleExperimentMapper.class));
        ReflectionTestUtils.setField(service, "projectFilterService", projectFilter(project(7L, "RISK_A")));

        service.pageExperiments(1, 10, null, "RISK", "风控", null, null);

        assertProjectIdFilter(recording.wrapper, 7L);
    }

    @Test
    public void billingConfigsReturnEmptyPageWithoutBusinessQueryWhenNoProjectMatches() {
        RecordingPageMapper recording = new RecordingPageMapper();
        RuleBillingService service = new RuleBillingService();
        ReflectionTestUtils.setField(service, "baseMapper", recording.proxy(RuleBillingConfigMapper.class));
        ReflectionTestUtils.setField(service, "projectFilterService", projectFilter());

        Page<RuleBillingConfig> page = (Page<RuleBillingConfig>) service.pageConfigs(
                2, 30, null, null, null, "不存在", null, null, null);

        assertEquals(2L, page.getCurrent());
        assertEquals(30L, page.getSize());
        assertEquals(0, recording.selectPageCount);
    }

    @Test
    public void externalDatasourcesFilterByResolvedProjectIds() {
        RecordingPageMapper recording = new RecordingPageMapper();
        RuleExternalDatasourceService service = new RuleExternalDatasourceService();
        ReflectionTestUtils.setField(service, "baseMapper", recording.proxy(RuleExternalDatasourceMapper.class));
        ReflectionTestUtils.setField(service, "projectFilterService", projectFilter(project(7L, "RISK_A")));

        service.pageList(1, 10, null, null, "RISK", "风控", null, null, null, null);

        assertProjectIdFilter(recording.wrapper, 7L);
    }

    @Test
    public void externalApiConfigsFollowDatasourceProjectIds() {
        RecordingPageMapper recording = new RecordingPageMapper();
        RuleExternalApiConfigService service = new RuleExternalApiConfigService();
        RuleExternalDatasource datasource = new RuleExternalDatasource();
        datasource.setId(31L);
        datasource.setProjectId(7L);
        RuleExternalDatasourceMapper datasourceMapper = (RuleExternalDatasourceMapper) Proxy.newProxyInstance(
                RuleExternalDatasourceMapper.class.getClassLoader(),
                new Class[]{RuleExternalDatasourceMapper.class},
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.singletonList(datasource)
                        : defaultValue(method.getReturnType()));
        ReflectionTestUtils.setField(service, "baseMapper", recording.proxy(RuleExternalApiConfigMapper.class));
        ReflectionTestUtils.setField(service, "datasourceMapper", datasourceMapper);
        ReflectionTestUtils.setField(service, "projectFilterService", projectFilter(project(7L, "RISK_A")));

        service.pageList(1, 10, null, "RISK", "风控", null, null, null, null, null);

        assertTrue(recording.wrapper.getSqlSegment(), recording.wrapper.getSqlSegment().contains("datasourceId"));
        assertTrue(recording.wrapper.getParamNameValuePairs().containsValue(31L));
    }

    private static void assertProjectIdFilter(LambdaQueryWrapper<?> wrapper, Long projectId) {
        assertTrue(wrapper.getSqlSegment(), wrapper.getSqlSegment().contains("projectId"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(projectId));
    }

    private static ProjectFilterService projectFilter(RuleProject... projects) {
        ProjectFilterService service = new ProjectFilterService();
        RuleProjectMapper mapper = (RuleProjectMapper) Proxy.newProxyInstance(
                RuleProjectMapper.class.getClassLoader(),
                new Class[]{RuleProjectMapper.class},
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? java.util.Arrays.asList(projects)
                        : defaultValue(method.getReturnType()));
        ReflectionTestUtils.setField(service, "projectMapper", mapper);
        return service;
    }

    private static RuleProject project(Long id, String code) {
        RuleProject project = new RuleProject();
        project.setId(id);
        project.setProjectCode(code);
        project.setProjectName("风控项目");
        return project;
    }

    private static class RecordingPageMapper {
        private LambdaQueryWrapper<?> wrapper;
        private int selectPageCount;

        private <T> T proxy(Class<T> mapperType) {
            return mapperType.cast(Proxy.newProxyInstance(
                    mapperType.getClassLoader(),
                    new Class[]{mapperType},
                    (proxy, method, args) -> {
                        if ("selectPage".equals(method.getName())) {
                            selectPageCount++;
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
