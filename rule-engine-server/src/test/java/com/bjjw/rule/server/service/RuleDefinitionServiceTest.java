package com.bjjw.rule.server.service;

import com.bjjw.rule.model.entity.RuleDefinition;
import com.bjjw.rule.model.entity.RuleDefinitionContent;
import com.bjjw.rule.model.entity.RuleDefinitionVersion;
import com.bjjw.rule.server.mapper.RuleDefinitionContentMapper;
import com.bjjw.rule.server.mapper.RuleDefinitionMapper;
import com.bjjw.rule.server.mapper.RuleDefinitionVersionMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RuleDefinitionServiceTest {

    @Test
    public void compareVersionsReportsChangedSections() {
        RuleDefinitionService service = new RuleDefinitionService();
        RuleDefinitionVersion left = version(1, "{\"a\":1}", "return 1;");
        RuleDefinitionVersion right = version(2, "{\"a\":2}", "return 1;");
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
        assertEquals(Integer.valueOf(1), updatedContent[0].getCompileStatus());
        assertEquals("rollback to v2", updatedContent[0].getCompileMessage());
        assertSame(definition, updatedDefinition[0]);
        assertEquals(Integer.valueOf(4), updatedDefinition[0].getCurrentVersion());
        assertEquals(Long.valueOf(10L), analyzer.definitionId);
        assertEquals("{\"rules\":[]}", analyzer.modelJson);
        assertEquals("DECISION_TABLE", analyzer.modelType);
        assertEquals(Long.valueOf(7L), analyzer.projectId);
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
}
