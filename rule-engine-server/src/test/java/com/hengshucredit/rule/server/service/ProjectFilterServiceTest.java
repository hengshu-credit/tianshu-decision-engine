package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProjectFilterServiceTest {

    @BeforeClass
    public static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleProject.class);
    }

    @Test
    public void blankConditionsDoNotQueryProjects() throws Exception {
        FakeProjectMapper mapper = new FakeProjectMapper(Collections.emptyList());
        ProjectFilterService service = serviceWith(mapper);

        ProjectFilterService.ProjectMatches matches = service.resolve(" ", null);

        assertFalse(matches.isActive());
        assertTrue(matches.isEmpty());
        assertEquals(0, mapper.selectCount);
    }

    @Test
    public void codeAndNameUseLikeConditionsAndReturnOriginalIdsAndCodes() throws Exception {
        RuleProject first = project(7L, "Risk_A", "风控项目A");
        RuleProject second = project(8L, "Risk_B", "风控项目B");
        FakeProjectMapper mapper = new FakeProjectMapper(Arrays.asList(first, second));
        ProjectFilterService service = serviceWith(mapper);

        ProjectFilterService.ProjectMatches matches = service.resolve("Risk", "风控");

        assertTrue(matches.isActive());
        assertFalse(matches.isEmpty());
        assertEquals(Arrays.asList(7L, 8L), matches.getProjectIds());
        assertEquals(Arrays.asList("Risk_A", "Risk_B"), matches.getProjectCodes());
        assertTrue(mapper.wrapper.getSqlSegment().contains("projectCode"));
        assertTrue(mapper.wrapper.getSqlSegment().contains("projectName"));
        assertTrue(mapper.wrapper.getParamNameValuePairs().values().stream()
                .anyMatch(value -> String.valueOf(value).contains("Risk")));
        assertTrue(mapper.wrapper.getParamNameValuePairs().values().stream()
                .anyMatch(value -> String.valueOf(value).contains("风控")));
    }

    @Test
    public void activeConditionsWithNoMatchesStayActiveAndEmpty() throws Exception {
        FakeProjectMapper mapper = new FakeProjectMapper(Collections.emptyList());
        ProjectFilterService service = serviceWith(mapper);

        ProjectFilterService.ProjectMatches matches = service.resolve(null, "不存在");

        assertTrue(matches.isActive());
        assertTrue(matches.isEmpty());
        assertEquals(Collections.emptyList(), matches.getProjectIds());
        assertEquals(Collections.emptyList(), matches.getProjectCodes());
        assertEquals(1, mapper.selectCount);
    }

    private ProjectFilterService serviceWith(FakeProjectMapper mapper) throws Exception {
        ProjectFilterService service = new ProjectFilterService();
        Field field = ProjectFilterService.class.getDeclaredField("projectMapper");
        field.setAccessible(true);
        field.set(service, mapper.proxy());
        return service;
    }

    private RuleProject project(Long id, String code, String name) {
        RuleProject project = new RuleProject();
        project.setId(id);
        project.setProjectCode(code);
        project.setProjectName(name);
        return project;
    }

    private static class FakeProjectMapper {
        private final List<RuleProject> projects;
        private LambdaQueryWrapper<RuleProject> wrapper;
        private int selectCount;

        private FakeProjectMapper(List<RuleProject> projects) {
            this.projects = projects;
        }

        private RuleProjectMapper proxy() {
            return (RuleProjectMapper) Proxy.newProxyInstance(
                    RuleProjectMapper.class.getClassLoader(),
                    new Class[]{RuleProjectMapper.class},
                    (proxy, method, args) -> {
                        if ("selectList".equals(method.getName())) {
                            selectCount++;
                            wrapper = (LambdaQueryWrapper<RuleProject>) args[0];
                            return projects;
                        }
                        return defaultValue(method.getReturnType());
                    });
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
