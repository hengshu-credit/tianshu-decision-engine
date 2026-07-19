package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class RuleVariableServiceTest {

    @Test
    public void toGlobalKeepsOriginalCodeAndClearsProjectOwnership() {
        AtomicInteger updates = new AtomicInteger();
        AtomicReference<LambdaUpdateWrapper<RuleVariable>> updateWrapper = new AtomicReference<>();
        RuleVariableService service = toGlobalService(
                variable(10L, "face_result", "PROJECT", 7L), 0L, 1, updates, updateWrapper);

        service.toGlobal(10L);

        Assert.assertEquals(1, updates.get());
        Assert.assertNotNull(updateWrapper.get());
        String sqlSet = updateWrapper.get().getSqlSet();
        Assert.assertTrue(sqlSet, sqlSet.contains("scope"));
        Assert.assertTrue(sqlSet, sqlSet.contains("projectId") || sqlSet.contains("project_id"));
        Assert.assertTrue(sqlSet, sqlSet.contains("updateTime") || sqlSet.contains("update_time"));
        Assert.assertFalse(sqlSet, sqlSet.contains("varCode") || sqlSet.contains("var_code"));
        Assert.assertTrue(updateWrapper.get().getParamNameValuePairs().containsValue(0L));
    }

    @Test
    public void toGlobalRejectsConflictingGlobalCodeWithoutUpdating() {
        AtomicInteger updates = new AtomicInteger();
        RuleVariableService service = toGlobalService(
                variable(10L, "face_result", "PROJECT", 7L), 1L, 1, updates, new AtomicReference<>());

        assertToGlobalRejected(service, 10L, "全局变量");

        Assert.assertEquals(0, updates.get());
    }

    @Test
    public void toGlobalRejectsAlreadyGlobalVariable() {
        AtomicInteger updates = new AtomicInteger();
        RuleVariableService service = toGlobalService(
                variable(10L, "face_result", "GLOBAL", 0L), 0L, 1, updates, new AtomicReference<>());

        assertToGlobalRejected(service, 10L, "已是全局变量");

        Assert.assertEquals(0, updates.get());
    }

    @Test
    public void toGlobalRejectsConcurrentScopeChange() {
        AtomicInteger updates = new AtomicInteger();
        RuleVariableService service = toGlobalService(
                variable(10L, "face_result", "PROJECT", 7L), 0L, 0, updates, new AtomicReference<>());

        assertToGlobalRejected(service, 10L, "状态已变化");

        Assert.assertEquals(1, updates.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void pageListOrdersByLatestUpdateThenId() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleVariable.class);
        AtomicReference<String> sqlSegment = new AtomicReference<>();
        RuleVariableMapper mapper = (RuleVariableMapper) Proxy.newProxyInstance(
                RuleVariableMapper.class.getClassLoader(), new Class<?>[]{RuleVariableMapper.class},
                (proxy, method, args) -> {
                    if ("selectPage".equals(method.getName())) {
                        sqlSegment.set(((LambdaQueryWrapper<RuleVariable>) args[1]).getSqlSegment());
                        return args[0];
                    }
                    return defaultValue(method.getReturnType());
                });
        RuleVariableService service = new RuleVariableService();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        service.pageList(1, 10, null, null, null, true, null,
                null, null, null, null, null);

        assertLatestUpdateOrder(sqlSegment.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void buildRefMapReadsModelMetadataOnceWithoutContent() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleModel.class);
        AtomicInteger queryCount = new AtomicInteger();
        AtomicReference<String> selectedColumns = new AtomicReference<>();
        RuleVariableService service = new FakeRuleVariableService(Collections.emptyList());
        RuleModelMapper mapper = (RuleModelMapper) Proxy.newProxyInstance(
                RuleModelMapper.class.getClassLoader(), new Class<?>[]{RuleModelMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        queryCount.incrementAndGet();
                        LambdaQueryWrapper<RuleModel> wrapper = (LambdaQueryWrapper<RuleModel>) args[0];
                        selectedColumns.set(wrapper.getSqlSelect());
                        RuleModel model = new RuleModel();
                        model.setId(101L);
                        model.setModelCode("buffalo_det_face");
                        return Collections.singletonList(model);
                    }
                    return null;
                });
        ReflectionTestUtils.setField(service, "modelMapper", mapper);

        Map<String, String> refs = service.buildRefScriptNameMap(1L);

        Assert.assertEquals("buffalo_det_face", refs.get("MODEL:101"));
        Assert.assertEquals(1, queryCount.get());
        Assert.assertNotNull(selectedColumns.get());
        Assert.assertFalse(selectedColumns.get().isEmpty());
        Assert.assertFalse(selectedColumns.get().contains("model_content"));
    }

    @Test
    public void modelOutputReferenceUsesCanonicalFieldName() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleModel.class);
        RuleVariableService service = new FakeRuleVariableService(Collections.emptyList());
        RuleModel model = new RuleModel();
        model.setId(101L);
        model.setModelCode("buffalo_det_face");
        RuleModelOutputField output = new RuleModelOutputField();
        output.setId(201L);
        output.setModelId(101L);
        output.setFieldName("faces");
        output.setScriptName("buffalo_det_face_faces");

        RuleModelMapper modelMapper = (RuleModelMapper) Proxy.newProxyInstance(
                RuleModelMapper.class.getClassLoader(), new Class<?>[]{RuleModelMapper.class},
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.singletonList(model) : null);
        RuleModelOutputFieldMapper outputMapper = (RuleModelOutputFieldMapper) Proxy.newProxyInstance(
                RuleModelOutputFieldMapper.class.getClassLoader(), new Class<?>[]{RuleModelOutputFieldMapper.class},
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.singletonList(output) : null);
        ReflectionTestUtils.setField(service, "modelMapper", modelMapper);
        ReflectionTestUtils.setField(service, "modelOutputFieldMapper", outputMapper);

        Map<String, String> refs = service.buildRefScriptNameMap(1L);

        Assert.assertEquals("buffalo_det_face.faces", refs.get("MODEL_OUTPUT:201"));
    }

    @Test
    public void buildsTrustedConstantExpressionsByStableId() {
        RuleVariable empty = constant(10L, "EMPTY_STRING", "STRING", "", 1);
        RuleVariable infinity = constant(11L, "POSITIVE_INFINITY", "DOUBLE", "Infinity", 1);
        RuleVariable disabled = constant(12L, "DISABLED", "NUMBER", "1", 0);
        RuleVariable input = constant(13L, "AGE", "NUMBER", "18", 1);
        input.setVarSource("INPUT");
        RuleVariableService service = new FakeRuleVariableService(Arrays.asList(empty, infinity, disabled, input));

        Map<Long, String> expressions = service.buildRefConstantExpressionMap(1L);

        Assert.assertEquals(2, expressions.size());
        Assert.assertEquals("''", expressions.get(10L));
        Assert.assertEquals("1.0 / 0.0", expressions.get(11L));
        Assert.assertFalse(expressions.containsKey(12L));
        Assert.assertFalse(expressions.containsKey(13L));

        Map<String, Object> values = service.buildRefConstantValueMap(1L);
        Assert.assertEquals("", values.get("CONSTANT:10"));
        Assert.assertEquals(Double.POSITIVE_INFINITY, values.get("CONSTANT:11"));
    }

    @Test
    public void normalizesTypedConstantAndAllowsEmptyString() {
        RuleVariableService service = new RuleVariableService();
        RuleVariable empty = constant(1L, "EMPTY_STRING", "STRING", "", 1);
        RuleVariable bool = constant(2L, "TRUE_VALUE", "BOOLEAN", " TRUE ", 1);

        service.validateAndNormalizeConstant(empty);
        service.validateAndNormalizeConstant(bool);

        Assert.assertEquals("", empty.getDefaultValue());
        Assert.assertEquals("true", bool.getDefaultValue());
    }

    @Test
    public void rejectsInvalidTypedConstant() {
        RuleVariableService service = new RuleVariableService();
        RuleVariable invalid = constant(1L, "BAD_LIST", "LIST", "{}", 1);

        try {
            service.validateAndNormalizeConstant(invalid);
            Assert.fail("Expected invalid list constant to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("JSON 数组"));
        }
    }

    private static RuleVariable constant(Long id, String code, String type, String value, int status) {
        RuleVariable variable = new RuleVariable();
        variable.setId(id);
        variable.setScope("GLOBAL");
        variable.setProjectId(0L);
        variable.setVarCode(code);
        variable.setVarLabel(code);
        variable.setScriptName(code);
        variable.setVarType(type);
        variable.setVarSource("CONSTANT");
        variable.setDefaultValue(value);
        variable.setStatus(status);
        return variable;
    }

    private static RuleVariable variable(Long id, String code, String scope, Long projectId) {
        RuleVariable variable = new RuleVariable();
        variable.setId(id);
        variable.setVarCode(code);
        variable.setVarLabel(code);
        variable.setScope(scope);
        variable.setProjectId(projectId);
        variable.setVarType("STRING");
        variable.setVarSource("COMPUTED");
        variable.setStatus(1);
        return variable;
    }

    @SuppressWarnings("unchecked")
    private static RuleVariableService toGlobalService(RuleVariable variable,
                                                       long conflictCount,
                                                       int updateResult,
                                                       AtomicInteger updates,
                                                       AtomicReference<LambdaUpdateWrapper<RuleVariable>> updateWrapper) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleVariable.class);
        RuleVariableMapper mapper = (RuleVariableMapper) Proxy.newProxyInstance(
                RuleVariableMapper.class.getClassLoader(), new Class<?>[]{RuleVariableMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) return variable;
                    if ("selectCount".equals(method.getName())) return conflictCount;
                    if ("update".equals(method.getName())) {
                        updates.incrementAndGet();
                        updateWrapper.set((LambdaUpdateWrapper<RuleVariable>) args[1]);
                        return updateResult;
                    }
                    return defaultValue(method.getReturnType());
                });
        RuleVariableService service = new RuleVariableService();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        return service;
    }

    private static void assertToGlobalRejected(RuleVariableService service, Long variableId, String messagePart) {
        try {
            service.toGlobal(variableId);
            Assert.fail("Expected variable global conversion to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(messagePart));
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        return null;
    }

    private static void assertLatestUpdateOrder(String sqlSegment) {
        Assert.assertNotNull(sqlSegment);
        String normalized = sqlSegment.replace("`", "").replace(" ", "").toLowerCase();
        Assert.assertTrue(sqlSegment, normalized.contains("orderbyupdate_timedesc,iddesc")
                || normalized.contains("orderbyupdatetimedesc,iddesc"));
    }

    private static class FakeRuleVariableService extends RuleVariableService {
        private final List<RuleVariable> variables;

        private FakeRuleVariableService(List<RuleVariable> variables) {
            this.variables = variables;
        }

        @Override
        public List<RuleVariable> listByProject(Long projectId, String varSource) {
            return variables;
        }

        @Override
        public List<RuleVariable> listGlobalOnly() {
            return variables == null ? Collections.emptyList() : variables;
        }
    }
}
