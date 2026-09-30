package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.mapper.*;
import com.hengshucredit.rule.server.controller.mgmt.RuleLineageController;
import com.hengshucredit.rule.server.controller.mgmt.OfflineResourceTransferController;
import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.security.ConsolePermissionInterceptor;
import com.hengshucredit.rule.server.security.ConsolePermissionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class RuleLineageOptionsTest {
    @BeforeClass
    public static void initializeTables() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleProject.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleExternalDatasource.class);
    }

    @Test
    public void functionAndExperimentSearchExposeNamesCodesAndStableIds() {
        for (String type : List.of("FUNCTION", "EXPERIMENT")) {
            Fixture fixture = new Fixture(type, List.of(Map.of("id", 7L, "code", "Risk_Mixed", "label", "授信判断", "projectId", 3L)));
            List<Map<String, Object>> records = fixture.service.options(type, "授信", null);
            assertEquals(1, records.size());
            assertEquals(7L, records.get(0).get("id"));
            assertEquals("Risk_Mixed", records.get(0).get("code"));
            assertEquals("授信项目", records.get(0).get("projectName"));
            assertEquals("credit", records.get(0).get("projectCode"));
            assertEquals("PROJECT", records.get(0).get("scope"));
            assertEquals("授信判断 (Risk_Mixed)", records.get(0).get("displayName"));
        }
    }

    @Test
    public void legacyOptionsStillReturnAnArrayAndDoNotSelectImplementationOrBinaryFields() {
        for (String type : List.of("PROJECT", "RULE", "VARIABLE", "DATA_OBJECT", "FUNCTION", "MODEL", "EXPERIMENT", "DB", "API", "DATASOURCE", "LIST")) {
            Fixture fixture = new Fixture(type, List.of(Map.of("id", 7L, "code", "MixedCase", "label", "同名配置", "projectId", 0L)));
            List<Map<String, Object>> records = fixture.service.options(type, "Mixed", null);
            assertEquals(type, 1, records.size());
            assertEquals("MixedCase", records.get(0).get("code"));
            assertTrue(fixture.query.getSqlSelect().contains(" AS code"));
            assertFalse(fixture.query.getSqlSelect().contains("content"));
            assertFalse(fixture.query.getSqlSelect().contains("password"));
            assertTrue(fixture.query.getSqlSegment().contains(" OR "));
            assertTrue(fixture.query.getParamNameValuePairs().containsValue("%Mixed%"));
            assertEquals(80, fixture.page.getSize());
        }
    }

    @Test
    public void controllerPreservesLegacyArrayAndOffersDatabasePagination() {
        Fixture fixture = new Fixture("FUNCTION", List.of(Map.of("id", 87L, "code", "lateFunction", "label", "后续页函数", "projectId", 0L)));
        fixture.total = 121;
        RuleLineageController controller = new RuleLineageController();
        ReflectionTestUtils.setField(controller, "lineageService", fixture.service);
        assertTrue(controller.options("FUNCTION", "", null, null, 20).getData() instanceof List);
        Object response = controller.options("FUNCTION", "", null, 5, 20).getData();
        assertTrue(response instanceof Page);
        Page<?> page = (Page<?>) response;
        assertEquals(5, page.getCurrent());
        assertEquals(20, page.getSize());
        assertEquals(121, page.getTotal());
        assertEquals(500, controller.options("FUNCTION", "", null, 0, 20).getCode());
        assertEquals(500, controller.options("FUNCTION", "", null, 1, 101).getCode());
    }

    @Test
    public void apiScopeAndProjectComeFromItsDatasource() {
        Fixture fixture = new Fixture("API", List.of(Map.of("id", 7L, "code", "supplier", "label", "报告", "datasourceId", 5L)));
        Map<String, Object> option = fixture.service.options("API", null, null).get(0);
        assertEquals(3L, option.get("projectId"));
        assertEquals("PROJECT", option.get("scope"));
        assertEquals("credit", option.get("projectCode"));
    }

    @Test
    public void projectScopeFilterAndLiteralUnderscoresRemainIntact() {
        Fixture fixture = new Fixture("FUNCTION", List.of());
        fixture.service.pageOptions("FUNCTION", "Rate_100%", 3L, 1, 20);
        assertTrue(fixture.query.getSqlSegment().contains("scope"));
        assertTrue(fixture.query.getParamNameValuePairs().containsValue("GLOBAL"));
        assertTrue(fixture.query.getParamNameValuePairs().containsValue("PROJECT"));
        assertTrue(fixture.query.getParamNameValuePairs().containsValue(3L));
        assertTrue(fixture.query.getParamNameValuePairs().containsValue("%Rate\\_100\\%%"));
    }

    @Test
    public void transferCandidatesUseTheSameQueryAndTheExportPermission() throws Exception {
        Fixture fixture = new Fixture("FUNCTION", List.of(Map.of("id", 7L, "code", "tax", "label", "税额", "projectId", 0L)));
        OfflineResourceTransferController controller = new OfflineResourceTransferController();
        ReflectionTestUtils.setField(controller, "lineageService", fixture.service);
        assertEquals(7L, controller.resources("FUNCTION", "tax", null, 1, 20).getData().getRecords().get(0).get("id"));
        assertEquals(422, controller.resources("FUNCTION", "tax", null, 0, 20).getCode());
        RuleEngineConsoleLoginProperties properties = new RuleEngineConsoleLoginProperties();
        ConsolePermissionService permissions = new ConsolePermissionService() {
            @Override public boolean hasPermission(Long userId, String permissionCode) { return "rule:view".equals(permissionCode); }
        };
        ConsolePermissionInterceptor interceptor = new ConsolePermissionInterceptor(properties, permissions);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rule/transfer/resources");
        request.getSession(true).setAttribute(properties.getSessionUserIdAttribute(), 9L);
        var handler = new HandlerMethod(controller, OfflineResourceTransferController.class.getMethod("resources", String.class, String.class, Long.class, int.class, int.class));
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
        MockHttpServletRequest legacy = new MockHttpServletRequest("GET", "/api/rule/lineage/options");
        legacy.setSession((org.springframework.mock.web.MockHttpSession) request.getSession());
        var lineageHandler = new HandlerMethod(new RuleLineageController(), RuleLineageController.class.getMethod("options", String.class, String.class, Long.class, Integer.class, int.class));
        assertFalse(interceptor.preHandle(legacy, new MockHttpServletResponse(), lineageHandler));
    }

    private static class Fixture {
        final RuleLineageService service = new RuleLineageService();
        QueryWrapper<?> query;
        Page<?> page;
        long total = -1;

        Fixture(String type, List<Map<String, Object>> rows) {
            RuleProject project = new RuleProject(); project.setId(3L); project.setProjectCode("credit"); project.setProjectName("授信项目");
            RuleExternalDatasource datasource = new RuleExternalDatasource(); datasource.setId(5L); datasource.setProjectId(3L); datasource.setScope("PROJECT");
            set("projectMapper", RuleProjectMapper.class, type.equals("PROJECT") ? rows : null, List.of(project));
            set("definitionMapper", RuleDefinitionMapper.class, type.equals("RULE") ? rows : null, List.of());
            set("variableMapper", RuleVariableMapper.class, type.equals("VARIABLE") ? rows : null, List.of());
            set("dataObjectMapper", RuleDataObjectMapper.class, type.equals("DATA_OBJECT") ? rows : null, List.of());
            set("functionMapper", RuleFunctionMapper.class, type.equals("FUNCTION") ? rows : null, List.of());
            set("experimentMapper", RuleExperimentMapper.class, type.equals("EXPERIMENT") ? rows : null, List.of());
            set("modelMapper", RuleModelMapper.class, type.equals("MODEL") ? rows : null, List.of());
            set("dbDatasourceMapper", RuleDbDatasourceMapper.class, type.equals("DB") ? rows : null, List.of());
            set("externalApiConfigMapper", RuleExternalApiConfigMapper.class, type.equals("API") ? rows : null, List.of());
            set("externalDatasourceMapper", RuleExternalDatasourceMapper.class, type.equals("DATASOURCE") ? rows : null, List.of(datasource));
            set("listLibraryMapper", RuleListLibraryMapper.class, type.equals("LIST") ? rows : null, List.of());
        }

        private <T> void set(String field, Class<T> mapper, List<Map<String, Object>> rows, List<?> entities) {
            ReflectionTestUtils.setField(service, field, Proxy.newProxyInstance(mapper.getClassLoader(), new Class[]{mapper}, (proxy, method, args) -> {
                if ("selectMapsPage".equals(method.getName())) {
                    query = (QueryWrapper<?>) args[1];
                    @SuppressWarnings("unchecked") Page<Map<String, Object>> requested = (Page<Map<String, Object>>) args[0];
                    page = requested;
                    List<Map<String, Object>> copies = new ArrayList<>();
                    for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) copies.add(new LinkedHashMap<>(row));
                    return requested.setRecords(copies).setTotal(total < 0 ? copies.size() : total);
                }
                if ("selectList".equals(method.getName())) return entities;
                if (method.getReturnType() == long.class) return 0L;
                if (method.getReturnType() == int.class) return 0;
                return null;
            }));
        }
    }
}
