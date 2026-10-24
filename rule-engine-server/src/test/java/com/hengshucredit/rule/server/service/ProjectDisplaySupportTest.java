package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.junit.Test;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ProjectDisplaySupportTest {
    @Test
    public void allConsumersKeepTheirDisplayPolicyWithOneDeduplicatedQuery() throws Exception {
        for (Class<?> type : List.of(RuleVariableService.class, RuleFunctionService.class,
                RuleDbDatasourceService.class, RuleExternalDatasourceService.class, RuleSftpDatasourceService.class)) {
            Object service = type.getConstructor().newInstance();
            Class<?> entityType = Class.forName("com.hengshucredit.rule.model.entity."
                    + type.getSimpleName().replace("Service", ""));
            List<Object> rows = new ArrayList<>();
            for (Long id : new Long[]{0L, null, 3L, 3L, 9L}) {
                Object row = entityType.getConstructor().newInstance();
                BeanWrapperImpl bean = new BeanWrapperImpl(row);
                bean.setPropertyValue("projectId", id);
                bean.setPropertyValue("projectName", "original");
                rows.add(row);
            }
            AtomicInteger queries = new AtomicInteger();
            RuleProject first = new RuleProject();
            first.setId(3L); first.setProjectName("项目三"); first.setProjectCode("Mixed_Project");
            RuleProject duplicate = new RuleProject();
            duplicate.setId(3L); duplicate.setProjectName("later");
            RuleProjectMapper mapper = (RuleProjectMapper) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{RuleProjectMapper.class}, (proxy, method, args) -> {
                        if (!method.getName().equals("selectByIds")) throw new AssertionError(method.getName());
                        assertEquals(List.of(3L, 9L), new ArrayList<>((java.util.Collection<?>) args[0]));
                        queries.incrementAndGet();
                        return List.of(first, duplicate);
                    });
            ReflectionTestUtils.setField(service, "projectMapper", mapper);
            ReflectionTestUtils.invokeMethod(service, "fillProjectName", rows);
            assertEquals(type.getName(), 1, queries.get());
            assertEquals("项目三", name(rows.get(2)));
            assertEquals("项目三", name(rows.get(3)));
            boolean preservesGlobal = type == RuleVariableService.class || type == RuleFunctionService.class;
            assertEquals(preservesGlobal ? "original" : null, name(rows.get(0)));
            assertEquals(preservesGlobal ? "original" : null, name(rows.get(1)));
            assertEquals(type == RuleFunctionService.class ? "original" : null, name(rows.get(4)));
            if (type == RuleFunctionService.class) {
                assertEquals("Mixed_Project", new BeanWrapperImpl(rows.get(2)).getPropertyValue("projectCode"));
            }
            ReflectionTestUtils.invokeMethod(service, "fillProjectName", new ArrayList<>());
            ReflectionTestUtils.invokeMethod(service, "fillProjectName", rows.subList(0, 2));
            assertEquals("空列表与全局列表不查库", 1, queries.get());
        }
    }

    @Test
    public void missingProjectStillClearsStaleDatasourceName() {
        var row = new com.hengshucredit.rule.model.entity.RuleDbDatasource();
        row.setProjectId(9L); row.setProjectName("old");
        RuleProjectMapper mapper = (RuleProjectMapper) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{RuleProjectMapper.class}, (proxy, method, args) -> List.of());
        var service = new RuleDbDatasourceService();
        ReflectionTestUtils.setField(service, "projectMapper", mapper);
        ReflectionTestUtils.invokeMethod(service, "fillProjectName", List.of(row));
        assertNull(row.getProjectName());
    }

    private Object name(Object row) {
        return new BeanWrapperImpl(row).getPropertyValue("projectName");
    }
}
