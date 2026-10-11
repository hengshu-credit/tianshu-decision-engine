package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleModelVersion;
import com.hengshucredit.rule.model.dto.RuleTestSchema;
import com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService;
import com.hengshucredit.rule.server.artifact.RuleDependencyClosureService;
import com.hengshucredit.rule.server.mapper.RuleModelInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelVersionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ModelVersionSnapshotTest {
    @Before
    public void initializeMapperMetadata() {
        for (Class<?> type : List.of(RuleModel.class, RuleModelVersion.class,
                RuleModelInputField.class, RuleModelOutputField.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), type);
        }
    }

    @Test
    public void publishedVersionRetainsActualFieldIdsAndConfigurationAfterCurrentModelChanges() {
        RuleModel model = new RuleModel();
        model.setId(7L);
        model.setProjectId(3L);
        model.setScope("PROJECT");
        model.setModelCode("CustomerModel");
        model.setModelName("客户模型");
        model.setModelFormat("PMML");
        model.setCurrentVersion(1);
        model.setModelContent("AQID");
        model.setModelConfig("{\"testParams\":\"{\\\"Application\\\":{\\\"Age\\\":28}}\"}");
        model.setExecutionTimeoutMs(72000);

        RuleModelInputField input = new RuleModelInputField();
        input.setId(501L);
        input.setModelId(7L);
        input.setFieldName("Age");
        input.setFieldLabel("申请年龄");
        input.setFieldType("INTEGER");
        input.setSourceOperand("{\"kind\":\"PATH\",\"path\":\"Application.Age\"}");
        input.setStatus(1);
        RuleModelOutputField output = new RuleModelOutputField();
        output.setId(601L);
        output.setModelId(7L);
        output.setFieldName("Score");
        output.setFieldLabel("审批评分");
        output.setFieldType("NUMBER");
        output.setTargetOperand("{\"kind\":\"PATH\",\"path\":\"Decision.Score\"}");

        AtomicReference<JSONObject> storedVersion = new AtomicReference<>();
        RuleModelService service = new RuleModelService();
        ModelVersionTestService versionTests = new ModelVersionTestService();
        ReflectionTestUtils.setField(versionTests, "dependencyClosure", new RuleDependencyClosureService());
        ReflectionTestUtils.setField(versionTests, "runtimeSnapshots", new ArtifactRuntimeSnapshotService());
        ReflectionTestUtils.setField(versionTests, "schemas", new RuleTestSchemaService() {
            @Override public RuleTestSchema buildModelSnapshot(RuleModel model, ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot) {
                return new RuleTestSchema();
            }
        });
        ReflectionTestUtils.setField(service, "modelVersionTests", versionTests);
        ReflectionTestUtils.setField(service, "modelMapper", mapper(RuleModelMapper.class,
                (proxy, method, args) -> "selectById".equals(method.getName()) ? model : 1));
        ReflectionTestUtils.setField(service, "inputFieldMapper", mapper(RuleModelInputFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName()) ? List.of(input) : 1));
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleModelOutputFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName()) ? List.of(output) : 1));
        ReflectionTestUtils.setField(service, "versionMapper", mapper(RuleModelVersionMapper.class,
                (proxy, method, args) -> {
                    if ("selectOne".equals(method.getName())) {
                        RuleModelVersion previous = new RuleModelVersion();
                        previous.setModelId(7L);
                        previous.setVersion(1);
                        return previous;
                    }
                    if ("insert".equals(method.getName())) storedVersion.set(JSON.parseObject(JSON.toJSONString(args[0])));
                    return 1;
                }));

        service.publish(7L, "模型配置版本", "reviewer");
        input.setFieldName("NewAge");
        output.setFieldName("NewScore");
        model.setExecutionTimeoutMs(1000);
        model.setModelConfig("{}");

        assertNotNull(storedVersion.get());
        String raw = storedVersion.get().getString("executionSnapshotJson");
        assertNotNull("模型版本必须保存完整执行快照，不能只保存文件再回读当前字段", raw);
        JSONObject frozen = JSON.parseObject(raw).getJSONObject("model");
        assertEquals(Long.valueOf(7L), frozen.getLong("id"));
        assertEquals(Integer.valueOf(2), frozen.getInteger("currentVersion"));
        assertEquals(Integer.valueOf(72000), frozen.getInteger("executionTimeoutMs"));
        assertEquals("Age", frozen.getJSONArray("inputFields").getJSONObject(0).getString("fieldName"));
        assertEquals(Long.valueOf(501L), frozen.getJSONArray("inputFields").getJSONObject(0).getLong("id"));
        assertEquals("Score", frozen.getJSONArray("outputFields").getJSONObject(0).getString("fieldName"));
        assertEquals(Long.valueOf(601L), frozen.getJSONArray("outputFields").getJSONObject(0).getLong("id"));
        assertEquals("{\"Application\":{\"Age\":28}}",
                JSON.parseObject(frozen.getString("modelConfig")).getString("testParams"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
