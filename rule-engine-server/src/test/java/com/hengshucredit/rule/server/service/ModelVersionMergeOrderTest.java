package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.artifact.RuleDependencyClosureService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class ModelVersionMergeOrderTest {
    @Parameterized.Parameters(name = "{0}, nestedFirst={1}")
    public static Object[][] cases() { return new Object[][] {
            {"MODEL", true}, {"MODEL", false}, {"CONSTANT", true}, {"CONSTANT", false}, {"FUNCTION", true}, {"FUNCTION", false},
            {"VARIABLE_KIND", true}, {"VARIABLE_KIND", false}
    }; }
    @Parameterized.Parameter public String type;
    @Parameterized.Parameter(1) public boolean nestedFirst;

    @Test
    public void incompatibleVersionsOfSameIdentityAreRejectedRegardlessOfCollectionOrder() {
        try (var fixture = new ModelVersionExecutionTest.Fixture()) {
            Map<Long, RuleModel> models = new LinkedHashMap<>();
            Map<String, RuleModelVersion> versions = new LinkedHashMap<>();
            RuleVariable constant = new RuleVariable(); constant.setId(9L); constant.setProjectId(3L); constant.setScope("PROJECT");
            constant.setVarCode("Limit"); constant.setScriptName("Limit"); constant.setVarType("DOUBLE"); constant.setVarSource("CONSTANT");
            constant.setStatus(1); constant.setDefaultValue("2");
            RuleFunction function = new RuleFunction(); function.setId(13L); function.setProjectId(3L); function.setScope("PROJECT");
            function.setFuncCode("versionedFn"); function.setFuncName("版本函数"); function.setStatus(1); function.setImplType("SCRIPT");
            function.setParamsJson("[{\"name\":\"value\"}]"); function.setImplScript("return value + 1;");
            ReflectionTestUtils.setField(fixture.tests, "dependencyClosure", new RuleDependencyClosureService() {
                @Override protected RuleModel loadModel(Long id) { return models.get(id); }
                @Override protected RuleModelVersion loadModelVersion(Long id, Integer number) { return versions.get(id + ":" + number); }
                @Override protected RuleModelOutputField loadModelOutputField(Long id) {
                    return models.values().stream().flatMap(model -> model.getOutputFields().stream()).filter(field -> id.equals(field.getId())).findFirst().orElse(null);
                }
                @Override protected RuleVariable loadVariable(Long id) { return constant; }
                @Override protected RuleFunction loadFunction(Long id) { return function; }
            });
            RuleModel b = ModelVersionExecutionTest.model(1, "OldAge", 1);
            models.put(b.getId(), b); versions.put("7:1", fixture.capture(b));
            String source = switch (type) {
                case "MODEL" -> reference("MODEL_OUTPUT", 61, "HistoricalModel.prediction");
                case "CONSTANT", "VARIABLE_KIND" -> reference("CONSTANT", 9, "Limit");
                default -> "{\"kind\":\"FUNCTION\",\"functionId\":13,\"functionCode\":\"versionedFn\",\"args\":[{\"kind\":\"LITERAL\",\"value\":1}]}";
            };
            RuleModel a = ownedModel(8L, "ModelA", 81L, 82L);
            a.getInputFields().get(0).setSourceOperand(source);
            models.put(a.getId(), a); versions.put("8:1", fixture.capture(a));
            if ("MODEL".equals(type)) {
                b.setCurrentVersion(2); b.setPublishedVersion(2); b.getInputFields().get(0).setSourceOperand("{\"kind\":\"PATH\",\"value\":\"NewAge\"}");
                versions.put("7:2", fixture.capture(b));
            } else if ("CONSTANT".equals(type)) constant.setDefaultValue("3");
            else if ("VARIABLE_KIND".equals(type)) {
                constant.setVarSource("INPUT"); source = reference("VARIABLE", 9, "Limit");
            }
            else function.setImplScript("return value + 2;");
            String nested = reference("MODEL_OUTPUT", 82, "ModelA.prediction");
            RuleModel root = ownedModel(10L, "RootModel", 101L, 102L);
            root.getInputFields().get(0).setSourceOperand("{\"kind\":\"OPERATION\",\"terms\":[{\"operand\":"
                    + (nestedFirst ? nested : source) + "},{\"operator\":\"+\",\"operand\":" + (nestedFirst ? source : nested) + "}]}");
            var snapshot = fixture.capture(root);
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> fixture.tests.context(snapshot));
            assertTrue(failure.getMessage(), failure.getMessage().contains("冲突") || failure.getMessage().contains("不同版本"));
        }
    }

    private static RuleModel ownedModel(long id, String code, long input, long output) {
        RuleModel model = ModelVersionExecutionTest.model(1, "unused", 1);
        model.setId(id); model.setModelCode(code);
        model.getInputFields().get(0).setId(input); model.getInputFields().get(0).setModelId(id);
        model.getOutputFields().get(0).setId(output); model.getOutputFields().get(0).setModelId(id);
        return model;
    }
    private static String reference(String type, long id, String code) {
        return "{\"kind\":\"REFERENCE\",\"refType\":\"" + type + "\",\"refId\":" + id + ",\"code\":\"" + code + "\"}";
    }
}
