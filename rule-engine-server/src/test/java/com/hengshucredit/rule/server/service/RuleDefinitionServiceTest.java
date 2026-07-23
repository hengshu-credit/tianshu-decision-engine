package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionVersion;
import com.hengshucredit.rule.model.dto.RuleQueryDTO;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionRefMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionVersionMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Map;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RuleDefinitionServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void pagedDefinitionQueriesUseStableCreateTimeAndIdOrder() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleDefinition.class);
        List<String> sqlSegments = new ArrayList<>();
        RuleDefinitionMapper mapper = mapper(RuleDefinitionMapper.class, (proxy, method, args) -> {
            if ("selectPage".equals(method.getName())) {
                sqlSegments.add(((LambdaQueryWrapper<RuleDefinition>) args[1]).getSqlSegment());
                return args[0];
            }
            return defaultValue(method.getReturnType());
        });
        RuleDefinitionService service = new RuleDefinitionService();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        service.pageList(new RuleQueryDTO());
        service.pageListForProject(1, 10, null, null, null,
                null, null, null, null, null, null, null, null);

        assertEquals(2, sqlSegments.size());
        for (String sqlSegment : sqlSegments) {
            String normalized = sqlSegment.replace("`", "").replace(" ", "").toLowerCase();
            assertTrue(sqlSegment, normalized.contains("orderbycreate_timedesc,iddesc")
                    || normalized.contains("orderbycreatetimedesc,iddesc"));
        }
    }

    @Test
    public void createWithContentAlwaysCreatesDraftDefinition() {
        RuleDefinitionService service = new RuleDefinitionService();
        RuleDefinition definition = new RuleDefinition();
        definition.setId(15L);
        definition.setProjectId(0L);
        definition.setModelType("TABLE");
        definition.setStatus(1);
        definition.setPublishedVersion(9);
        final RuleDefinition[] insertedDefinition = {null};

        ReflectionTestUtils.setField(service, "baseMapper", mapper(RuleDefinitionMapper.class, (proxy, method, args) -> {
            if ("insert".equals(method.getName())) {
                insertedDefinition[0] = (RuleDefinition) args[0];
                return 1;
            }
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "contentMapper", mapper(RuleDefinitionContentMapper.class,
                (proxy, method, args) -> "insert".equals(method.getName()) ? 1 : defaultValue(method.getReturnType())));
        ReflectionTestUtils.setField(service, "fieldAnalyzer", new RecordingRuleFieldAnalyzer());

        service.createWithContent(definition);

        assertSame(definition, insertedDefinition[0]);
        assertEquals(Integer.valueOf(0), insertedDefinition[0].getStatus());
        assertNull(insertedDefinition[0].getPublishedVersion());
    }

    @Test
    public void pageListForProjectIncludesRuleFieldMetadataForChildCalls() {
        RuleDefinitionService service = new RuleDefinitionService();
        RuleDefinition definition = new RuleDefinition();
        definition.setId(11L);
        definition.setRuleCode("MONTHLY_REPAYMENT_MATRIX");
        RuleDefinitionInputField input = new RuleDefinitionInputField();
        input.setDefinitionId(11L);
        input.setFieldName("credit_amount");
        input.setScriptName("CREDIT_AMOUNT");
        RuleDefinitionOutputField output = new RuleDefinitionOutputField();
        output.setDefinitionId(11L);
        output.setFieldName("monthly_success_repayment_amount");
        output.setScriptName("monthly_success_repayment_amount");

        ReflectionTestUtils.setField(service, "baseMapper", mapper(RuleDefinitionMapper.class, (proxy, method, args) -> {
            if ("selectPage".equals(method.getName())) {
                Page<RuleDefinition> page = (Page<RuleDefinition>) args[0];
                page.setRecords(Collections.singletonList(definition));
                page.setTotal(1);
                return page;
            }
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleDefinitionOutputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Arrays.asList(output);
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "inputFieldMapper", mapper(RuleDefinitionInputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Arrays.asList(input);
            return defaultValue(method.getReturnType());
        }));

        IPage<RuleDefinition> page = service.pageListForProject(1, 20, 7L, null, null,
                null, null, null, null, null, null, null, null);

        assertEquals(1, page.getRecords().size());
        assertEquals(1, page.getRecords().get(0).getInputFieldsJson().size());
        assertEquals("CREDIT_AMOUNT",
                page.getRecords().get(0).getInputFieldsJson().get(0).getScriptName());
        assertEquals(1, page.getRecords().get(0).getOutputFieldsJson().size());
        assertEquals("monthly_success_repayment_amount",
                page.getRecords().get(0).getOutputFieldsJson().get(0).getScriptName());
    }

    @Test
    public void linkedGlobalRuleIsAvailableInsideProject() {
        RuleDefinitionService service = new RuleDefinitionService();
        RuleDefinition definition = new RuleDefinition();
        definition.setId(88L);
        definition.setProjectId(0L);
        definition.setScope("GLOBAL");

        ReflectionTestUtils.setField(service, "baseMapper", mapper(RuleDefinitionMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return definition;
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "refMapper", mapper(RuleDefinitionRefMapper.class, (proxy, method, args) -> {
            if ("selectCount".equals(method.getName())) return 1L;
            return defaultValue(method.getReturnType());
        }));

        assertTrue(service.isDefinitionAvailableInProject(88L, 7L));
    }

    @Test
    public void compareVersionsReportsChangedSections() {
        RuleDefinitionService service = new RuleDefinitionService();
        RuleDefinitionVersion left = version(1, "{\"a\":1}", "return 1;");
        RuleDefinitionVersion right = version(2, "{\"a\":2}", "return 1;");
        left.setOpenApiConfigJson("{\"enabled\":false}");
        right.setOpenApiConfigJson("{\"enabled\":true}");
        final int[] calls = {0};
        ReflectionTestUtils.setField(service, "versionMapper", mapper(RuleDefinitionVersionMapper.class, (proxy, method, args) -> {
            if ("selectOne".equals(method.getName())) {
                return calls[0]++ == 0 ? left : right;
            }
            return defaultValue(method.getReturnType());
        }));

        Map<String, Object> result = service.compareVersions(1L, 1, 2);

        assertSame(left, result.get("left"));
        assertSame(right, result.get("right"));
        assertTrue((Boolean) result.get("modelJsonChanged"));
        assertFalse((Boolean) result.get("compiledScriptChanged"));
        assertTrue((Boolean) result.get("openApiConfigChanged"));
    }

    @Test
    public void rollbackToVersionRestoresSnapshotAndRefreshesFields() {
        RuleDefinitionService service = new RuleDefinitionService();
        RuleDefinition definition = new RuleDefinition();
        definition.setId(10L);
        definition.setProjectId(7L);
        definition.setModelType("DECISION_TABLE");
        definition.setCurrentVersion(3);
        RuleDefinitionContent content = new RuleDefinitionContent();
        content.setId(20L);
        content.setDefinitionId(10L);
        RuleDefinitionVersion snapshot = version(2, "{\"rules\":[]}", "return 2;");
        snapshot.setCompiledType("QL");
        snapshot.setOpenApiConfigJson("{\"enabled\":true}");
        RecordingRuleFieldAnalyzer analyzer = new RecordingRuleFieldAnalyzer();
        final RuleDefinitionContent[] updatedContent = {null};
        final RuleDefinition[] updatedDefinition = {null};

        ReflectionTestUtils.setField(service, "baseMapper", mapper(RuleDefinitionMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return definition;
            if ("updateById".equals(method.getName())) {
                updatedDefinition[0] = (RuleDefinition) args[0];
                return 1;
            }
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "contentMapper", mapper(RuleDefinitionContentMapper.class, (proxy, method, args) -> {
            if ("selectOne".equals(method.getName())) return content;
            if ("updateById".equals(method.getName())) {
                updatedContent[0] = (RuleDefinitionContent) args[0];
                return 1;
            }
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "versionMapper", mapper(RuleDefinitionVersionMapper.class, (proxy, method, args) -> {
            if ("selectOne".equals(method.getName())) return snapshot;
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "fieldAnalyzer", analyzer);

        service.rollbackToVersion(10L, 2);

        assertSame(content, updatedContent[0]);
        assertEquals("{\"rules\":[]}", updatedContent[0].getModelJson());
        assertEquals("return 2;", updatedContent[0].getCompiledScript());
        assertEquals("QL", updatedContent[0].getCompiledType());
        assertEquals("{\"enabled\":true}", updatedContent[0].getOpenApiConfigJson());
        assertEquals(Integer.valueOf(1), updatedContent[0].getCompileStatus());
        assertEquals("rollback to v2", updatedContent[0].getCompileMessage());
        assertSame(definition, updatedDefinition[0]);
        assertEquals(Integer.valueOf(4), updatedDefinition[0].getCurrentVersion());
        assertEquals(Long.valueOf(10L), analyzer.definitionId);
        assertEquals("{\"rules\":[]}", analyzer.modelJson);
        assertEquals("DECISION_TABLE", analyzer.modelType);
        assertEquals(Long.valueOf(7L), analyzer.projectId);
    }

    @Test
    public void saveScriptStoresDraftWithoutMutatingPublishedArtifact() {
        RuleDefinitionService service = new RuleDefinitionService();
        RuleDefinition definition = new RuleDefinition();
        definition.setId(10L);
        definition.setRuleCode("risk_rule");
        definition.setModelType("SCRIPT");
        definition.setStatus(1);
        RuleDefinitionContent content = new RuleDefinitionContent();
        content.setId(20L);
        content.setDefinitionId(10L);
        final RuleDefinitionContent[] updatedContent = {null};

        ReflectionTestUtils.setField(service, "baseMapper", mapper(RuleDefinitionMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return definition;
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "contentMapper", mapper(RuleDefinitionContentMapper.class, (proxy, method, args) -> {
            if ("selectOne".equals(method.getName())) return content;
            if ("updateById".equals(method.getName())) {
                updatedContent[0] = (RuleDefinitionContent) args[0];
                return 1;
            }
            return defaultValue(method.getReturnType());
        }));
        service.saveScript(10L, "score = amount * 2");

        assertTrue(updatedContent[0].getCompiledScript().contains("\"score\": score"));
        assertEquals(Integer.valueOf(1), updatedContent[0].getCompileStatus());
        assertEquals("script", updatedContent[0].getScriptMode());
    }

    @Test
    public void deleteWithContentDeletesApiDocumentationScenarios() {
        RuleDefinitionService service = new RuleDefinitionService();
        RecordingApiDocScenarioService scenarioService = new RecordingApiDocScenarioService();
        ReflectionTestUtils.setField(service, "baseMapper",
                mapper(RuleDefinitionMapper.class, (proxy, method, args) -> defaultValue(method.getReturnType())));
        ReflectionTestUtils.setField(service, "inputFieldMapper",
                mapper(RuleDefinitionInputFieldMapper.class,
                        (proxy, method, args) -> defaultValue(method.getReturnType())));
        ReflectionTestUtils.setField(service, "outputFieldMapper",
                mapper(RuleDefinitionOutputFieldMapper.class,
                        (proxy, method, args) -> defaultValue(method.getReturnType())));
        ReflectionTestUtils.setField(service, "contentMapper",
                mapper(RuleDefinitionContentMapper.class,
                        (proxy, method, args) -> defaultValue(method.getReturnType())));
        ReflectionTestUtils.setField(service, "apiDocScenarioService", scenarioService);

        service.deleteWithContent(12L);

        assertEquals(Long.valueOf(12L), scenarioService.deletedDefinitionId);
    }

    private static RuleDefinitionVersion version(int version, String modelJson, String script) {
        RuleDefinitionVersion result = new RuleDefinitionVersion();
        result.setDefinitionId(1L);
        result.setVersion(version);
        result.setModelJson(modelJson);
        result.setCompiledScript(script);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) return null;
        if (returnType == boolean.class) return false;
        if (returnType == void.class) return null;
        return 0;
    }

    private static class RecordingRuleFieldAnalyzer extends RuleFieldAnalyzer {
        private Long definitionId;
        private String modelJson;
        private String modelType;
        private Long projectId;

        @Override
        public void analyzeAndPersist(Long definitionId, String modelJson, String modelType, Long projectId) {
            this.definitionId = definitionId;
            this.modelJson = modelJson;
            this.modelType = modelType;
            this.projectId = projectId;
        }
    }

    private static class RecordingApiDocScenarioService extends RuleApiDocScenarioService {
        private Long deletedDefinitionId;

        @Override
        public void deleteByDefinition(Long definitionId) {
            this.deletedDefinitionId = definitionId;
        }
    }
}
