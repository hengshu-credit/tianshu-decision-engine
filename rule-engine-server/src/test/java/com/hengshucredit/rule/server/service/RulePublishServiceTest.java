package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionVersion;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.mapper.RuleDefinitionVersionMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.publish.RulePushService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;

public class RulePublishServiceTest {

    @Test
    public void publishesOpenApiContractToVersionAndDedicatedPublishedColumn() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(10L);
        definition.setProjectId(7L);
        definition.setRuleCode("OPEN_RISK");
        definition.setModelType("DECISION_TABLE");
        definition.setPublishedVersion(1);
        RuleDefinitionContent content = new RuleDefinitionContent();
        content.setDefinitionId(10L);
        content.setModelJson("{}");
        content.setCompiledScript("return true;");
        content.setCompiledType("QLEXPRESS");
        content.setCompileStatus(1);
        content.setOpenApiConfigJson("{\"enabled\":true,\"requestMappings\":[{"
                + "\"targetRefType\":\"VARIABLE\",\"targetVarId\":88,"
                + "\"sourceType\":\"BODY\",\"sourcePath\":\"$.mobile\"}],"
                + "\"envelopeTemplate\":{\"data\":\"${data}\"},"
                + "\"dataPath\":\"$.data\",\"successDataTemplate\":\"${result}\","
                + "\"errorDataTemplate\":{\"code\":\"${status.code}\"}}");
        RecordingDefinitionService definitionService = new RecordingDefinitionService(definition, content);
        RuleDefinitionInputField inputField = new RuleDefinitionInputField();
        inputField.setRefType("VARIABLE");
        inputField.setVarId(88L);
        inputField.setScriptName("mobile");
        inputField.setStatus(1);
        definitionService.inputFields = Collections.singletonList(inputField);
        final RuleDefinitionVersion[] insertedVersion = {null};
        final RulePublished[] insertedPublished = {null};
        final String[] publishedConfig = {null};

        RulePublishService service = new RulePublishService();
        ReflectionTestUtils.setField(service, "definitionService", definitionService);
        ReflectionTestUtils.setField(service, "projectService", new RuleProjectService() {
            @Override
            public RuleProject getById(java.io.Serializable id) {
                RuleProject project = new RuleProject();
                project.setProjectCode("P001");
                return project;
            }
        });
        ReflectionTestUtils.setField(service, "versionMapper", mapper(RuleDefinitionVersionMapper.class,
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName())) insertedVersion[0] = (RuleDefinitionVersion) args[0];
                    return defaultValue(method.getReturnType());
                }));
        ReflectionTestUtils.setField(service, "publishedMapper", mapper(RulePublishedMapper.class,
                (proxy, method, args) -> {
                    if ("selectOne".equals(method.getName())) return null;
                    if ("insert".equals(method.getName())) insertedPublished[0] = (RulePublished) args[0];
                    if ("updateOpenApiConfigByDefinitionId".equals(method.getName())) publishedConfig[0] = (String) args[1];
                    return defaultValue(method.getReturnType());
                }));
        ReflectionTestUtils.setField(service, "functionService", new RuleFunctionService() {
            @Override
            public List<RuleFunction> listByProject(Long projectId) {
                return Collections.emptyList();
            }
        });
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        ReflectionTestUtils.setField(service, "ruleCallCycleService", new RuleCallCycleService() {
            @Override
            public String validateNoCycle(Long definitionId, String pendingModelJson) {
                return null;
            }
        });
        ReflectionTestUtils.setField(service, "pushService", new RulePushService() {
            @Override
            public void push(com.hengshucredit.rule.model.dto.RulePushMessage message) {
            }
        });

        String error = service.publish(10L, "开放接口配置");

        Assert.assertNull(error);
        Assert.assertTrue(insertedVersion[0].getOpenApiConfigJson().contains("\"enabled\":true"));
        Assert.assertEquals(Long.valueOf(10L), insertedPublished[0].getDefinitionId());
        Assert.assertEquals(insertedVersion[0].getOpenApiConfigJson(), publishedConfig[0]);
    }

    @Test
    public void refusesToPublishInvalidStoredOpenApiContract() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(10L);
        RuleDefinitionContent content = new RuleDefinitionContent();
        content.setDefinitionId(10L);
        content.setOpenApiConfigJson("{\"enabled\":true}");
        RulePublishService service = new RulePublishService();
        ReflectionTestUtils.setField(service, "definitionService",
                new RecordingDefinitionService(definition, content));

        String error = service.publish(10L, null);

        Assert.assertNotNull(error);
        Assert.assertTrue(error.contains("开放接口配置"));
    }

    @Test
    public void refusesToPublishOpenApiContractWithStaleInputReference() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(10L);
        RuleDefinitionContent content = new RuleDefinitionContent();
        content.setDefinitionId(10L);
        content.setOpenApiConfigJson("{\"enabled\":true,\"requestMappings\":[{"
                + "\"targetRefType\":\"VARIABLE\",\"targetVarId\":99,"
                + "\"sourceType\":\"BODY\",\"sourcePath\":\"$.mobile\"}],"
                + "\"envelopeTemplate\":{\"data\":\"${data}\"},\"dataPath\":\"$.data\","
                + "\"successDataTemplate\":\"${result}\","
                + "\"errorDataTemplate\":{\"code\":\"${status.code}\"}}");
        RecordingDefinitionService definitionService = new RecordingDefinitionService(definition, content);
        RuleDefinitionInputField existing = new RuleDefinitionInputField();
        existing.setRefType("VARIABLE");
        existing.setVarId(100L);
        existing.setScriptName("otherMobile");
        existing.setStatus(1);
        definitionService.inputFields = Collections.singletonList(existing);
        RulePublishService service = new RulePublishService();
        ReflectionTestUtils.setField(service, "definitionService", definitionService);

        String error = service.publish(10L, null);

        Assert.assertNotNull(error);
        Assert.assertTrue(error.contains("VARIABLE:99"));
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

    private static class RecordingDefinitionService extends RuleDefinitionService {
        private final RuleDefinition definition;
        private final RuleDefinitionContent content;
        private List<RuleDefinitionInputField> inputFields = Collections.emptyList();

        private RecordingDefinitionService(RuleDefinition definition, RuleDefinitionContent content) {
            this.definition = definition;
            this.content = content;
        }

        @Override
        public RuleDefinition getById(java.io.Serializable id) {
            return definition;
        }

        @Override
        public RuleDefinitionContent getContent(Long definitionId) {
            return content;
        }

        @Override
        public List<RuleDefinitionOutputField> listOutputFields(Long definitionId) {
            return Collections.emptyList();
        }

        @Override
        public List<RuleDefinitionInputField> listInputFields(Long definitionId) {
            return inputFields;
        }

        @Override
        public void refreshFields(Long definitionId, String modelJson, String modelType) {
            // 字段列表由当前测试夹具直接提供，无需调用真实字段分析依赖。
        }

        @Override
        public boolean updateById(RuleDefinition entity) {
            return true;
        }
    }
}
