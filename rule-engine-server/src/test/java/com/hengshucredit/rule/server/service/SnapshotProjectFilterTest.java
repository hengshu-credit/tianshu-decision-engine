package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.entity.RuleBillingRecord;
import com.hengshucredit.rule.model.entity.RuleBillingSummary;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.mapper.RuleBillingRecordMapper;
import com.hengshucredit.rule.server.mapper.RuleBillingSummaryMapper;
import com.hengshucredit.rule.server.mapper.RuleExecutionLogMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SnapshotProjectFilterTest {

    @BeforeClass
    public static void initTableInfo() {
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RuleExecutionLog.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RuleBillingRecord.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RuleBillingSummary.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RuleProject.class);
    }

    @Test
    public void executionLogsCombineDirectCodeLikeWithNameResolvedCodes() {
        RecordingMapper recording = new RecordingMapper();
        RuleExecutionLogService service = new RuleExecutionLogService();
        ReflectionTestUtils.setField(service, "baseMapper", recording.proxy(RuleExecutionLogMapper.class));
        ReflectionTestUtils.setField(service, "projectFilterService", projectFilter(project(7L, "RISK_A")));

        service.pageList(1, 20, null, null, "RISK", "风控", null, null,
                null, null, null, null, null);

        assertProjectCodeConditions(recording.wrapper, "RISK", "RISK_A");
    }

    @Test
    public void executionLogsReturnEmptyWithoutQueryWhenNameHasNoMatch() {
        RecordingMapper recording = new RecordingMapper();
        RuleExecutionLogService service = new RuleExecutionLogService();
        ReflectionTestUtils.setField(service, "baseMapper", recording.proxy(RuleExecutionLogMapper.class));
        ReflectionTestUtils.setField(service, "projectFilterService", projectFilter());

        Page<RuleExecutionLog> page = (Page<RuleExecutionLog>) service.pageList(
                2, 30, null, null, null, "不存在", null, null,
                null, null, null, null, null);

        assertEquals(2L, page.getCurrent());
        assertEquals(30L, page.getSize());
        assertEquals(0, recording.selectPageCount);
    }

    @Test
    public void billingRecordsCombineDirectCodeLikeWithNameResolvedCodes() {
        RecordingMapper recording = new RecordingMapper();
        RuleBillingService service = new RuleBillingService();
        ReflectionTestUtils.setField(service, "recordMapper", recording.proxy(RuleBillingRecordMapper.class));
        ReflectionTestUtils.setField(service, "projectFilterService", projectFilter(project(7L, "RISK_A")));

        service.pageRecords(1, 10, null, null, "RISK", "风控",
                null, null, null, null, null);

        assertProjectCodeConditions(recording.wrapper, "RISK", "RISK_A");
    }

    @Test
    public void billingSummariesFilterByResolvedProjectCodes() {
        RecordingMapper recording = new RecordingMapper();
        RuleBillingService service = new RuleBillingService();
        ReflectionTestUtils.setField(service, "summaryMapper", recording.proxy(RuleBillingSummaryMapper.class));
        ReflectionTestUtils.setField(service, "projectFilterService", projectFilter(project(7L, "RISK_A")));

        service.pageSummaries(1, 10, null, null, null, "风控",
                null, null, null, null);

        assertTrue(recording.wrapper.getSqlSegment(), recording.wrapper.getSqlSegment().contains("projectCode"));
        assertTrue(recording.wrapper.getParamNameValuePairs().containsValue("RISK_A"));
    }

    private static void assertProjectCodeConditions(LambdaQueryWrapper<?> wrapper,
                                                    String directCode, String resolvedCode) {
        assertTrue(wrapper.getSqlSegment(), wrapper.getSqlSegment().contains("projectCode"));
        assertTrue(wrapper.getParamNameValuePairs().values().stream()
                .anyMatch(value -> String.valueOf(value).contains(directCode)));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(resolvedCode));
    }

    private static ProjectFilterService projectFilter(RuleProject... projects) {
        ProjectFilterService service = new ProjectFilterService();
        RuleProjectMapper mapper = (RuleProjectMapper) Proxy.newProxyInstance(
                RuleProjectMapper.class.getClassLoader(),
                new Class[]{RuleProjectMapper.class},
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Arrays.asList(projects)
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

    private static class RecordingMapper {
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
                        if ("selectList".equals(method.getName())) {
                            wrapper = (LambdaQueryWrapper<?>) args[0];
                            return Collections.emptyList();
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
