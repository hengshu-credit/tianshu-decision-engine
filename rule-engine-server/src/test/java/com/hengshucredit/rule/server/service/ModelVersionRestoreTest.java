package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.*;

public class ModelVersionRestoreTest {
    @Test
    public void restoreDraftUsesAllSelectedVersionConfigurationAndAllocatesNewFieldIdentities() {
        try (var fixture = new ModelVersionExecutionTest.Fixture()) {
            for (Class<?> type : List.of(RuleModel.class, RuleModelVersion.class, RuleModelInputField.class, RuleModelOutputField.class)) {
                TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), type);
            }
            RuleModel old = ModelVersionExecutionTest.model(1, "OldAge", 1);
            old.setModelConfig("{\"testParams\":\"{\\\"OldAge\\\":2}\"}");
            RuleModelVersion version = fixture.capture(old);
            RuleModel current = ModelVersionExecutionTest.model(2, "NewAge", 100);
            current.setExecutionTimeoutMs(1000);
            ReflectionTestUtils.setField(fixture.models, "modelVersionTests", fixture.tests);
            ReflectionTestUtils.setField(fixture.models, "modelMapper", mapper(RuleModelMapper.class, current));
            ReflectionTestUtils.setField(fixture.models, "versionMapper", mapper(RuleModelVersionMapper.class, version));
            ReflectionTestUtils.setField(fixture.models, "inputFieldMapper", mapper(RuleModelInputFieldMapper.class, current.getInputFields()));
            ReflectionTestUtils.setField(fixture.models, "outputFieldMapper", mapper(RuleModelOutputFieldMapper.class, current.getOutputFields()));
            RuleProject project = new RuleProject(); project.setId(3L); project.setProjectCode("project");
            ReflectionTestUtils.setField(fixture.models, "projectMapper", mapper(RuleProjectMapper.class, project));

            RuleModel restored = fixture.models.buildRestoreSnapshot(7L, 1);

            assertTrue(restored.getInputFields().get(0).getSourceOperand().contains("OldAge"));
            assertEquals(Integer.valueOf(5000), restored.getExecutionTimeoutMs());
            assertEquals(old.getModelConfig(), restored.getModelConfig());
            assertEquals(old.getModelContent(), restored.getModelContent());
            assertNull("历史字段行ID不能用于覆盖当前或其他模型的行", restored.getInputFields().get(0).getId());
            assertNull(restored.getOutputFields().get(0).getId());
            assertEquals(Long.valueOf(7L), restored.getInputFields().get(0).getModelId());
        }
    }

    private static Object mapper(Class<?> type, Object value) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> value);
    }
}
