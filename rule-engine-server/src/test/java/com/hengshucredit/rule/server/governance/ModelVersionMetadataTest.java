package com.hengshucredit.rule.server.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ModelVersionMetadataTest {
    @Test public void metadataViewNeverLoadsModelFilesAndRestoresByOwnedImmutableId() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "metadata"), RuleModel.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "metadata"), RuleModelVersion.class);
        RuleModel model = new RuleModel(); model.setId(7L); model.setCurrentVersion(3); model.setPublishedVersion(3);
        model.setStatus(1); model.setModelCode("original_Code"); model.setModelName("原始模型");
        RuleModelVersion file = new RuleModelVersion(); file.setId(88L); file.setModelId(7L); file.setVersion(3);
        AtomicInteger copies = new AtomicInteger(), restores = new AtomicInteger();
        var store = new SimpleEntityGovernedResourceAdapter.EntityStore<RuleModel>() {
            public RuleModel load(Long id) { throw new AssertionError("metadata must not read the binary model row"); }
            public void insert(RuleModel value) { throw new AssertionError("not creating a model"); }
            public void update(RuleModel value) { throw new AssertionError("restore must preserve null metadata explicitly"); }
            public void restore(RuleModel value) { restores.incrementAndGet(); assertEquals("original_Code", value.getModelCode()); }
        };
        ModelGovernedResourceAdapter adapter = new ModelGovernedResourceAdapter(store,
                proxy(RuleModelInputFieldMapper.class, (p, m, a) -> List.of()),
                proxy(RuleModelOutputFieldMapper.class, (p, m, a) -> List.of()), new GovernanceSecretCodec(null));
        ReflectionTestUtils.setField(adapter, "modelMapper", proxy(RuleModelMapper.class, (p, method, args) -> {
            if (method.getName().equals("restoreVersionContent")) { assertEquals(7L, args[0]); assertEquals(88L, args[1]); copies.incrementAndGet(); return 1; }
            assertFalse(((LambdaQueryWrapper<?>) args[0]).getSqlSelect().contains("model_content")); return model;
        }));
        ReflectionTestUtils.setField(adapter, "modelVersions", proxy(RuleModelVersionMapper.class, (p, method, args) -> {
            String selected = ((LambdaQueryWrapper<?>) args[0]).getSqlSelect();
            assertFalse(selected.contains("model_content")); assertFalse(selected.contains("execution_snapshot_json")); return file;
        }));
        ResourceSnapshot snapshot = adapter.loadEffective(7L);
        assertTrue(snapshot.snapshotJson().contains("\"_modelContentVersionId\":88"));
        assertNull(snapshot.secretPayloadCiphertext());
        adapter.restoreLatest(7L, snapshot, "reviewer");
        assertEquals(1, copies.get()); assertEquals(1, restores.get());
        file.setModelId(9L);
        assertThrows(IllegalArgumentException.class, () -> adapter.restoreLatest(7L, snapshot, "reviewer"));
        assertEquals(1, copies.get());
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
