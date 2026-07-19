package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleFunctionMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProjectOwnedResourceFilterTest {

    @BeforeClass
    public static void initTableInfo() {
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RuleVariable.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RuleDataObject.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RuleModel.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RuleFunction.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RuleProject.class);
    }

    @Test
    public void variableProjectCodeFilterExcludesGlobalResources() {
        RecordingPageMapper recording = new RecordingPageMapper();
        RuleVariableService service = new RuleVariableService();
        ReflectionTestUtils.setField(service, "baseMapper", recording.proxy(RuleVariableMapper.class));
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper(project(7L)));
        ReflectionTestUtils.setField(service, "projectFilterService", projectFilter(project(7L)));

        service.pageList(1, 10, null, null, null, true, null, null,
                "RISK", null, null, null);

        assertProjectResourcesOnly(recording.wrapper, 7L);
    }

    @Test
    public void dataObjectProjectNameFilterExcludesGlobalResources() {
        RecordingPageMapper recording = new RecordingPageMapper();
        RuleDataObjectService service = new RuleDataObjectService();
        ReflectionTestUtils.setField(service, "baseMapper", recording.proxy(RuleDataObjectMapper.class));
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper(project(7L)));
        ReflectionTestUtils.setField(service, "projectFilterService", projectFilter(project(7L)));

        service.pageList(1, 10, null, null, null, "风控项目", null, null);

        assertProjectResourcesOnly(recording.wrapper, 7L);
    }

    @Test
    public void modelProjectCodeFilterExcludesGlobalResources() {
        RecordingPageMapper recording = new RecordingPageMapper();
        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "modelMapper", recording.proxy(RuleModelMapper.class));
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper(project(7L)));
        ReflectionTestUtils.setField(service, "projectFilterService", projectFilter(project(7L)));

        service.pageList(1, 10, null, null, null, null, null, null, "RISK", null);

        assertProjectResourcesOnly(recording.wrapper, 7L);
    }

    @Test
    public void functionProjectCodeFilterExcludesGlobalResources() {
        RecordingPageMapper recording = new RecordingPageMapper();
        RuleFunctionService service = new RuleFunctionService();
        ReflectionTestUtils.setField(service, "functionMapper", recording.proxy(RuleFunctionMapper.class));
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper(project(7L)));
        ReflectionTestUtils.setField(service, "projectFilterService", projectFilter(project(7L)));

        service.pageAll(1, 10, null, null, "RISK", null, null, null, null);

        assertProjectResourcesOnly(recording.wrapper, 7L);
    }

    @Test
    public void unmatchedVariableProjectFilterReturnsEmptyWithoutResourceQuery() {
        RecordingPageMapper recording = new RecordingPageMapper();
        RuleVariableService service = new RuleVariableService();
        ReflectionTestUtils.setField(service, "baseMapper", recording.proxy(RuleVariableMapper.class));
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper());
        ReflectionTestUtils.setField(service, "projectFilterService", projectFilter());

        IPage<RuleVariable> page = service.pageList(2, 30, null, null, null, true, null, null,
                "NOT_FOUND", null, null, null);

        assertEquals(2L, page.getCurrent());
        assertEquals(30L, page.getSize());
        assertEquals(0, recording.selectPageCount);
    }

    private static void assertProjectResourcesOnly(LambdaQueryWrapper<?> wrapper, Long projectId) {
        assertTrue(wrapper.getSqlSegment(), wrapper.getSqlSegment().contains("projectId"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(projectId));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("PROJECT"));
        assertFalse(wrapper.getParamNameValuePairs().containsValue("GLOBAL"));
    }

    private static RuleProjectMapper projectMapper(RuleProject... projects) {
        return (RuleProjectMapper) Proxy.newProxyInstance(
                RuleProjectMapper.class.getClassLoader(),
                new Class[]{RuleProjectMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        return Arrays.asList(projects);
                    }
                    if ("selectBatchIds".equals(method.getName())) {
                        return Collections.emptyList();
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static ProjectFilterService projectFilter(RuleProject... projects) {
        ProjectFilterService service = new ProjectFilterService();
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper(projects));
        return service;
    }

    private static RuleProject project(Long id) {
        RuleProject project = new RuleProject();
        project.setId(id);
        project.setProjectCode("RISK_A");
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
