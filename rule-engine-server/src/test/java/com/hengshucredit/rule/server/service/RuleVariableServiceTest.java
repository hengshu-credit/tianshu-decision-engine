package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
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
