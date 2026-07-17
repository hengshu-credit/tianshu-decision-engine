package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelVersionMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import com.hengshucredit.rule.server.service.onnx.OnnxRuntimeSessionManager;
import com.hengshucredit.rule.server.service.onnx.OnnxModelExecutionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleModelServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void executeOnnxReturnsStandardResultAndLogicalOutputs() {
        RuleModel model = model();
        model.setModelCode("face_detector");
        model.setModelFormat("ONNX");
        model.setModelContent(java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}));
        model.setModelConfig("{\"onnxTaskType\":\"YUNET_FACE_DETECTION\"}");
        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "modelMapper", mapper(RuleModelMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return model;
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "inputFieldMapper", mapper(RuleModelInputFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : defaultValue(method.getReturnType())));
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleModelOutputFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : defaultValue(method.getReturnType())));
        ReflectionTestUtils.setField(service, "executionParameterBinder", new ExecutionParameterBinder());
        ReflectionTestUtils.setField(service, "modelExecutionTimeoutExecutor", new ModelExecutionTimeoutExecutor());
        ReflectionTestUtils.setField(service, "onnxModelExecutionService", new OnnxModelExecutionService(null, null) {
            @Override
            public Map<String, Object> execute(byte[] modelBytes, String config, Map<String, Object> params) {
                Map<String, Object> output = new LinkedHashMap<>();
                output.put("faces", Collections.singletonList(Collections.singletonMap("confidence", 0.99d)));
                return output;
            }
        });

        Map<String, Object> result = service.execute(1L, Collections.singletonMap("image", "base64"));

        assertEquals(true, result.get("success"));
        assertEquals("ONNX", result.get("modelFormat"));
        assertEquals("face_detector", result.get("modelCode"));
        assertEquals(1, ((List<?>) ((Map<String, Object>) result.get("outputs")).get("faces")).size());
    }

    @Test
    public void uploadOnnxValidatesMetadataAndCreatesTaskTemplateFields() {
        List<RuleModelInputField> inputs = new ArrayList<>();
        List<RuleModelOutputField> outputs = new ArrayList<>();
        AtomicReference<RuleModel> insertedModel = new AtomicReference<>();
        AtomicReference<String> persistedContent = new AtomicReference<>();
        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "onnxSessionManager", new OnnxRuntimeSessionManager() {
            @Override
            public Map<String, Object> inspect(byte[] modelBytes) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("inputs", Collections.singletonMap("input", Collections.singletonMap("shape", Arrays.asList(1, 3, 128, 128))));
                metadata.put("outputs", Collections.singletonMap("output1", Collections.singletonMap("shape", Arrays.asList(1, 2))));
                return metadata;
            }
        });
        ReflectionTestUtils.setField(service, "modelMapper", mapper(RuleModelMapper.class, (proxy, method, args) -> {
            if ("insert".equals(method.getName())) {
                RuleModel model = (RuleModel) args[0];
                persistedContent.set(model.getModelContent());
                model.setId(42L);
                insertedModel.set(model);
                return 1;
            }
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "inputFieldMapper", mapper(RuleModelInputFieldMapper.class, (proxy, method, args) -> {
            if ("insert".equals(method.getName())) inputs.add((RuleModelInputField) args[0]);
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleModelOutputFieldMapper.class, (proxy, method, args) -> {
            if ("insert".equals(method.getName())) outputs.add((RuleModelOutputField) args[0]);
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "versionMapper", mapper(RuleModelVersionMapper.class,
                (proxy, method, args) -> defaultValue(method.getReturnType())));

        RuleModel model = service.uploadAndParse(
                new MockMultipartFile("file", "anti-spoof-mn3.onnx", "application/octet-stream", new byte[]{1, 2, 3}),
                null, "GLOBAL", "mn3", "MN3", "NEURAL_NET", null, "initial", "{\"face_image\":\"base64\"}",
                "MN3_ANTISPOOF", "{}", 1, 90000);

        assertEquals(Long.valueOf(42L), model.getId());
        assertEquals(Arrays.asList("image", "faces"), Arrays.asList(inputs.get(0).getFieldName(), inputs.get(1).getFieldName()));
        assertEquals(Arrays.asList("results", "rawOutputs"), Arrays.asList(outputs.get(0).getFieldName(), outputs.get(1).getFieldName()));
        assertEquals(null, inputs.get(0).getScriptName());
        assertEquals(null, model.getModelContent());
        assertEquals("AQID", persistedContent.get());
        assertTrue(insertedModel.get().getModelConfig().contains("MN3_ANTISPOOF"));
        assertTrue(insertedModel.get().getModelConfig().contains("output1"));
        assertTrue(insertedModel.get().getModelConfig().contains("testParams"));
        assertEquals(Integer.valueOf(1), insertedModel.get().getPreloadOnStartup());
        assertEquals(Integer.valueOf(90000), insertedModel.get().getExecutionTimeoutMs());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void pageListDoesNotReadOrReturnLargeModelContent() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleModel.class);
        AtomicReference<String> selectedColumns = new AtomicReference<>();
        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "modelMapper", mapper(RuleModelMapper.class, (proxy, method, args) -> {
            if ("selectPage".equals(method.getName())) {
                LambdaQueryWrapper<RuleModel> wrapper = (LambdaQueryWrapper<RuleModel>) args[1];
                selectedColumns.set(wrapper.getSqlSelect());
                RuleModel model = model();
                model.setProjectId(null);
                model.setModelContent("large-base64-content");
                Page<RuleModel> page = (Page<RuleModel>) args[0];
                page.setRecords(Collections.singletonList(model));
                page.setTotal(1);
                return page;
            }
            return defaultValue(method.getReturnType());
        }));

        Page<RuleModel> result = (Page<RuleModel>) service.pageList(
                1, 10, null, null, null, null, null, null, null, null);

        assertTrue(selectedColumns.get() != null && !selectedColumns.get().isEmpty());
        assertFalse(selectedColumns.get().contains("model_content"));
        assertEquals(null, result.getRecords().get(0).getModelContent());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void updateMetadataDoesNotReadOrWriteLargeModelContent() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleModel.class);
        AtomicReference<String> selectedColumns = new AtomicReference<>();
        AtomicReference<RuleModel> updatedModel = new AtomicReference<>();
        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "modelMapper", mapper(RuleModelMapper.class, (proxy, method, args) -> {
            if ("selectOne".equals(method.getName())) {
                LambdaQueryWrapper<RuleModel> wrapper = (LambdaQueryWrapper<RuleModel>) args[0];
                selectedColumns.set(wrapper.getSqlSelect());
                RuleModel existing = model();
                existing.setModelContent(null);
                return existing;
            }
            if ("updateById".equals(method.getName())) {
                updatedModel.set((RuleModel) args[0]);
                return 1;
            }
            return defaultValue(method.getReturnType());
        }));

        RuleModel changes = new RuleModel();
        changes.setId(1L);
        changes.setModelName("updated");
        changes.setPreloadOnStartup(1);
        changes.setExecutionTimeoutMs(90000);
        service.update(changes);

        assertTrue(selectedColumns.get() != null && !selectedColumns.get().isEmpty());
        assertFalse(selectedColumns.get().contains("model_content"));
        assertEquals(null, updatedModel.get().getModelContent());
        assertEquals(Integer.valueOf(1), updatedModel.get().getPreloadOnStartup());
        assertEquals(Integer.valueOf(90000), updatedModel.get().getExecutionTimeoutMs());
    }

    @Test
    public void modelFieldEntitiesRemoveLegacyMissingAndTransformFields() {
        assertTrue(Stream.of(RuleModelInputField.class.getDeclaredFields())
                .noneMatch(field -> "missingValue".equals(field.getName())));
        assertTrue(Stream.of(RuleModelOutputField.class.getDeclaredFields())
                .noneMatch(field -> "transformType".equals(field.getName())));
        assertTrue(Stream.of(RuleModelOutputField.class.getDeclaredFields())
                .anyMatch(field -> "transformOperand".equals(field.getName())));
    }

    @Test
    public void publishRejectsUnboundInputField() {
        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "modelMapper", mapper(RuleModelMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return model();
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "inputFieldMapper", mapper(RuleModelInputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Collections.singletonList(inputField("age", "INTEGER", null, null, null));
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleModelOutputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Collections.emptyList();
            return defaultValue(method.getReturnType());
        }));

        try {
            service.publish(1L, "publish", "tester");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("未完整关联变量"));
            return;
        }
        throw new AssertionError("Expected publish validation error");
    }

    @Test
    public void publishRejectsTypeMismatch() {
        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "modelMapper", mapper(RuleModelMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return model();
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "inputFieldMapper", mapper(RuleModelInputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Collections.singletonList(inputField("age", "INTEGER", 10L, "VARIABLE", "age"));
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleModelOutputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Collections.emptyList();
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "variableMapper", mapper(RuleVariableMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return variable("STRING");
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "dataObjectFieldMapper", mapper(RuleDataObjectFieldMapper.class, (proxy, method, args) -> null));

        try {
            service.publish(1L, "publish", "tester");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("类型不匹配"));
            return;
        }
        throw new AssertionError("Expected publish validation error");
    }

    @Test
    public void publishRejectsLiteralOutputTarget() {
        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "modelMapper", mapper(RuleModelMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return model();
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "inputFieldMapper", mapper(RuleModelInputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Collections.emptyList();
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleModelOutputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) {
                RuleModelOutputField field = new RuleModelOutputField();
                field.setFieldName("score");
                field.setFieldLabel("score");
                field.setFieldType("DOUBLE");
                field.setTargetOperand("{\"kind\":\"LITERAL\",\"value\":100}");
                return Collections.singletonList(field);
            }
            return defaultValue(method.getReturnType());
        }));

        try {
            service.publish(1L, "publish", "tester");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().toLowerCase().contains("operand"));
            return;
        }
        throw new AssertionError("Expected writable target validation error");
    }

    @Test
    public void updateOutputFieldPersistsFunctionTransformOperand() {
        AtomicReference<RuleModelOutputField> updated = new AtomicReference<>();
        RuleModelService service = outputUpdateService(function(7L, 1, twoParams()), updated);
        RuleModelOutputField request = new RuleModelOutputField();
        request.setVarId(20L);
        request.setRefType("VARIABLE");
        request.setScriptName("score");
        request.setFieldLabel("评分");
        request.setFieldType("DOUBLE");
        String transform = transformOperand(7L,
                "{\"kind\":\"REFERENCE\",\"refId\":100,\"refType\":\"MODEL_OUTPUT\",\"code\":\"risk_model.probability\"}",
                "{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}");
        ReflectionTestUtils.setField(request, "transformOperand", transform);

        service.updateOutputField(100L, request);

        assertEquals(transform, ReflectionTestUtils.getField(updated.get(), "transformOperand"));
    }

    @Test
    public void updateOutputFieldRejectsIncompleteFunctionArguments() {
        assertInvalidTransform(transformOperand(7L,
                "{\"kind\":\"REFERENCE\",\"refId\":100,\"refType\":\"MODEL_OUTPUT\",\"code\":\"risk_model.probability\"}",
                "null"), function(7L, 1, twoParams()), "参数");
    }

    @Test
    public void updateOutputFieldRejectsNestedFunctionArguments() {
        assertInvalidTransform(transformOperand(7L,
                "{\"kind\":\"FUNCTION\",\"functionId\":8,\"functionCode\":\"max\",\"args\":[]}",
                "{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}"),
                function(7L, 1, twoParams()), "嵌套");
    }

    @Test
    public void updateOutputFieldRejectsMissingOrDisabledFunction() {
        assertInvalidTransform(transformOperand(99L,
                "{\"kind\":\"LITERAL\",\"value\":\"0.2\",\"valueType\":\"NUMBER\"}",
                "{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}"), null, "不存在");
        assertInvalidTransform(transformOperand(7L,
                "{\"kind\":\"LITERAL\",\"value\":\"0.2\",\"valueType\":\"NUMBER\"}",
                "{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}"),
                function(7L, 0, twoParams()), "停用");
    }

    @Test
    public void updateOutputFieldRejectsFunctionOutsideModelScope() {
        RuleFunction function = function(7L, 1, twoParams());
        function.setScope(RuleFunctionService.SCOPE_PROJECT);
        function.setProjectId(99L);
        assertInvalidTransform(transformOperand(7L,
                "{\"kind\":\"LITERAL\",\"value\":\"0.2\",\"valueType\":\"NUMBER\"}",
                "{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}"), function, "作用域");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void outputTransformUsesExplicitRawOutputAndLiteralArguments() {
        RuleFunction function = function(7L, 1, twoParams());
        function.setImplType("SCRIPT");
        function.setImplScript("return probability * base;");
        RuleFunctionService functionService = new RuleFunctionService() {
            @Override
            public RuleFunction getById(Long id) {
                return function.getId().equals(id) ? function : null;
            }
        };
        ReflectionTestUtils.setField(functionService, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(functionService, "functionRegistrar", new FunctionRegistrar());

        RuleModelOutputField output = new RuleModelOutputField();
        output.setFieldName("probability");
        output.setTransformOperand(transformOperand(7L,
                "{\"kind\":\"REFERENCE\",\"refId\":100,\"refType\":\"MODEL_OUTPUT\",\"code\":\"risk_model.probability\"}",
                "{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}"));
        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "ruleFunctionService", functionService);
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleModelOutputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Collections.singletonList(output);
            return defaultValue(method.getReturnType());
        }));
        RuleModel model = model();
        model.setModelCode("risk_model");
        Map<String, Object> rawOutputs = new LinkedHashMap<>();
        rawOutputs.put("probability", 0.2);

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(
                service, "applyOutputTransforms", model, Collections.emptyMap(), rawOutputs);

        assertEquals(120.0, ((Number) result.get("probability")).doubleValue(), 0.000001);
        assertEquals(0.2, ((Number) rawOutputs.get("probability")).doubleValue(), 0.000001);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void outputTransformResolvesConstantArgumentByStableId() {
        RuleFunction function = function(7L, 1, twoParams());
        function.setImplType("SCRIPT");
        function.setImplScript("return probability * base;");
        RuleFunctionService functionService = new RuleFunctionService() {
            @Override
            public RuleFunction getById(Long id) {
                return function.getId().equals(id) ? function : null;
            }
        };
        ReflectionTestUtils.setField(functionService, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(functionService, "functionRegistrar", new FunctionRegistrar());

        RuleModelOutputField output = new RuleModelOutputField();
        output.setFieldName("probability");
        output.setTransformOperand(transformOperand(7L,
                "{\"kind\":\"REFERENCE\",\"refId\":100,\"refType\":\"MODEL_OUTPUT\",\"code\":\"risk_model.probability\"}",
                "{\"kind\":\"REFERENCE\",\"refId\":7,\"refType\":\"CONSTANT\",\"code\":\"BASE_SCORE\"}"));
        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "ruleFunctionService", functionService);
        ReflectionTestUtils.setField(service, "variableService", new RuleVariableService() {
            @Override
            public Map<String, Object> buildRefConstantValueMap(Long projectId) {
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("CONSTANT:7", 600D);
                return values;
            }
        });
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleModelOutputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Collections.singletonList(output);
            return defaultValue(method.getReturnType());
        }));
        RuleModel model = model();
        model.setModelCode("risk_model");
        Map<String, Object> rawOutputs = new LinkedHashMap<>();
        rawOutputs.put("probability", 0.2);

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(
                service, "applyOutputTransforms", model, Collections.emptyMap(), rawOutputs);

        assertEquals(120.0, ((Number) result.get("probability")).doubleValue(), 0.000001);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void outputTransformCanReadCurrentRawOutputByTargetStableId() {
        RuleFunctionService functionService = new RuleFunctionService() {
            @Override
            public Object invoke(Long functionId, List<Object> args) {
                return ((Number) args.get(0)).doubleValue() + ((Number) args.get(1)).doubleValue();
            }
        };

        RuleModelOutputField output = new RuleModelOutputField();
        output.setFieldName("score");
        output.setVarId(130L);
        output.setRefType("VARIABLE");
        output.setTargetOperand("{\"kind\":\"REFERENCE\",\"refId\":130,\"refType\":\"VARIABLE\",\"code\":\"score\"}");
        output.setTransformOperand(transformOperand(31L,
                "{\"kind\":\"REFERENCE\",\"refId\":130,\"refType\":\"VARIABLE\",\"code\":\"stale_score\"}",
                "{\"kind\":\"LITERAL\",\"value\":\"200\",\"valueType\":\"NUMBER\"}"));

        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "ruleFunctionService", functionService);
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleModelOutputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Collections.singletonList(output);
            return defaultValue(method.getReturnType());
        }));
        RuleModel model = model();
        Map<String, Object> rawOutputs = new LinkedHashMap<>();
        rawOutputs.put("score", 500d);

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(
                service, "applyOutputTransforms", model, Collections.emptyMap(), rawOutputs);

        assertEquals(700d, ((Number) result.get("score")).doubleValue(), 0d);
    }

    private static RuleModel model() {
        RuleModel model = new RuleModel();
        model.setId(1L);
        model.setProjectId(10L);
        model.setScope(RuleModelService.SCOPE_PROJECT);
        model.setCurrentVersion(1);
        model.setModelContent("content");
        model.setModelConfig("{}");
        return model;
    }

    private static RuleModelInputField inputField(String name, String type, Long varId, String refType, String scriptName) {
        RuleModelInputField field = new RuleModelInputField();
        field.setId(1L);
        field.setModelId(1L);
        field.setFieldName(name);
        field.setFieldLabel(name);
        field.setFieldType(type);
        field.setVarId(varId);
        field.setRefType(refType);
        field.setScriptName(scriptName);
        field.setStatus(1);
        return field;
    }

    private static RuleVariable variable(String type) {
        RuleVariable variable = new RuleVariable();
        variable.setId(10L);
        variable.setVarType(type);
        return variable;
    }

    private static RuleModelService outputUpdateService(RuleFunction function,
                                                        AtomicReference<RuleModelOutputField> updated) {
        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "modelMapper", mapper(RuleModelMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return model();
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleModelOutputFieldMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) {
                RuleModelOutputField existing = new RuleModelOutputField();
                existing.setId(100L);
                existing.setModelId(1L);
                existing.setFieldName("score");
                return existing;
            }
            if ("updateById".equals(method.getName())) {
                updated.set((RuleModelOutputField) args[0]);
                return 1;
            }
            return defaultValue(method.getReturnType());
        }));
        RuleFunctionService functionService = new RuleFunctionService() {
            @Override
            public RuleFunction getById(Long id) {
                return function != null && function.getId().equals(id) ? function : null;
            }
        };
        ReflectionTestUtils.setField(service, "ruleFunctionService", functionService);
        return service;
    }

    private static void assertInvalidTransform(String transformOperand, RuleFunction function, String message) {
        RuleModelService service = outputUpdateService(function, new AtomicReference<>());
        RuleModelOutputField request = new RuleModelOutputField();
        request.setFieldLabel("评分");
        ReflectionTestUtils.setField(request, "transformOperand", transformOperand);
        try {
            service.updateOutputField(100L, request);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(message));
            return;
        }
        throw new AssertionError("Expected invalid transform operand");
    }

    private static RuleFunction function(Long id, int status, String paramsJson) {
        RuleFunction function = new RuleFunction();
        function.setId(id);
        function.setProjectId(10L);
        function.setScope(RuleFunctionService.SCOPE_PROJECT);
        function.setStatus(status);
        function.setFuncCode("scoreByProbability");
        function.setFuncName("概率转评分");
        function.setParamsJson(paramsJson);
        return function;
    }

    private static String twoParams() {
        return "[{\"name\":\"probability\",\"type\":\"NUMBER\"},{\"name\":\"base\",\"type\":\"NUMBER\"}]";
    }

    private static String transformOperand(Long functionId, String firstArg, String secondArg) {
        return "{\"kind\":\"FUNCTION\",\"functionId\":" + functionId
                + ",\"functionCode\":\"scoreByProbability\",\"args\":[" + firstArg + "," + secondArg + "]}";
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == Void.TYPE) return null;
        if (type == Boolean.TYPE) return false;
        if (type == Integer.TYPE || type == Long.TYPE || type == Short.TYPE || type == Byte.TYPE) return 0;
        if (type == Double.TYPE || type == Float.TYPE) return 0.0;
        return null;
    }
}
