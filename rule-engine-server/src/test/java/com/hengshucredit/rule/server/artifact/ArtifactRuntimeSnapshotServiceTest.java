package com.hengshucredit.rule.server.artifact;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.ArtifactDeployment;
import com.hengshucredit.rule.model.entity.ArtifactResourceBinding;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class ArtifactRuntimeSnapshotServiceTest {

    @Test
    public void exposesOpenApiContractFromSameVerifiedPackageWithoutAnotherDecode() {
        DecisionArtifactPackage pack = new DecisionArtifactPackage();
        String contract = "{\"requestBindings\":[{\"refType\":\"VARIABLE\",\"refId\":7}],\"enabled\":true}";
        pack.addComponent("rule/open-api.json", "application/json", contract.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] bytes = new DecisionArtifactPackageCodec().encode(pack);
        var decoded = new DecisionArtifactPackageCodec().decode(bytes);
        var decodes = new java.util.concurrent.atomic.AtomicInteger();
        ArtifactRuntimeSnapshotService service = new ArtifactRuntimeSnapshotService();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "codec", new DecisionArtifactPackageCodec() {
            @Override public DecodedPackage decode(byte[] content) { decodes.incrementAndGet(); return super.decode(content); }
        });
        Assert.assertEquals(contract, service.loadModelVersion(bytes, decoded.getArtifactDigest(), decoded.getPackageDigest(), 3L).getOpenApiConfigJson());
        Assert.assertEquals(contract, service.loadModelVersion(bytes, decoded.getArtifactDigest(), decoded.getPackageDigest(), 4L).getOpenApiConfigJson());
        Assert.assertEquals(1, decodes.get());
        DecisionArtifact model = modelArtifact(0, 16);
        Assert.assertNull(service.loadModelVersion(model.getPackageContent(), model.getArtifactDigest(), model.getPackageDigest(), 3L).getOpenApiConfigJson());
    }

    @Test
    public void reusesImmutableModelBytesWithoutSharingMutableExecutionFields() {
        DecisionArtifact artifact = modelArtifact(0, 1024);
        ArtifactRuntimeSnapshotService service = new ArtifactRuntimeSnapshotService() {
            protected DecisionArtifact loadArtifact(Long id) { return artifact; }
        };
        var first = service.load(1L, 10L, 1L).getModels().get(0);
        String content = first.getModelContent();
        first.getInputFields().get(0).setFieldName("changed");
        first.getOutputFields().clear();
        first.setModelContent("changed");
        var second = service.load(1L, 20L, 2L).getModels().get(0);
        Assert.assertNotSame(first, second);
        Assert.assertEquals(Long.valueOf(2), second.getProjectId());
        Assert.assertEquals("age", second.getInputFields().get(0).getFieldName());
        Assert.assertEquals("score", second.getOutputFields().get(0).getFieldName());
        Assert.assertArrayEquals(new byte[1024], java.util.Base64.getDecoder().decode(second.getModelContent()));
        Assert.assertSame("不可变模型内容不能随每次执行重新编码分配", content, second.getModelContent());
    }

    private DecisionArtifact modelArtifact(int marker, int modelBytes) {
        DecisionArtifactPackage pack = new DecisionArtifactPackage();
        pack.putMetadata("marker", marker);
        byte[] modelContent = new byte[modelBytes];
        if (modelBytes > 0) modelContent[0] = (byte) marker;
        pack.addComponent("models/42.bin", "application/octet-stream", modelContent,
                Map.of("resourceType", "MODEL", "model", Map.of("id", 42, "modelCode", "riskModel"),
                        "inputFields", List.of(Map.of("fieldName", "age", "varId", 7)),
                        "outputFields", List.of(Map.of("fieldName", "score", "varId", 8))));
        byte[] bytes = new DecisionArtifactPackageCodec().encode(pack);
        var decoded = new DecisionArtifactPackageCodec().decode(bytes);
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setId(1L);
        artifact.setArtifactDigest(decoded.getArtifactDigest());
        artifact.setPackageDigest(decoded.getPackageDigest());
        artifact.setPackageContent(bytes);
        return artifact;
    }

    @Test
    public void modelStringBudgetEvictionDoesNotEvictDecodedPackages() {
        var current = new java.util.concurrent.atomic.AtomicReference<DecisionArtifact>();
        var decodes = new java.util.concurrent.atomic.AtomicInteger();
        ArtifactRuntimeSnapshotService service = new ArtifactRuntimeSnapshotService() {
            protected DecisionArtifact loadArtifact(Long id) { return current.get(); }
        };
        org.springframework.test.util.ReflectionTestUtils.setField(service, "codec", new DecisionArtifactPackageCodec() {
            public DecodedPackage decode(byte[] bytes) { decodes.incrementAndGet(); return super.decode(bytes); }
        });
        DecisionArtifact first = modelArtifact(0, 8 * 1024 * 1024);
        for (int i = 0; i < 3; i++) {
            current.set(i == 0 ? first : modelArtifact(i, 8 * 1024 * 1024));
            service.load(1L, 10L, 1L);
        }
        // 三份原始模型仅24MiB；字符串超预算时不得连带淘汰已解压包。
        Assert.assertEquals(3, decodes.get());
        current.set(first);
        service.load(1L, 10L, 1L);
        Assert.assertEquals(3, decodes.get());
        long retainedWeight = (Long) org.springframework.test.util.ReflectionTestUtils.getField(service, "decodedBytes");
        Assert.assertTrue(retainedWeight <= 64L * 1024 * 1024);
        long stringWeight = (Long) org.springframework.test.util.ReflectionTestUtils.getField(service, "modelContentBytes");
        Assert.assertTrue(stringWeight > 0 && stringWeight <= 32L * 1024 * 1024);
    }

    @Test
    public void oversizedModelStringDoesNotDisableDecodingCache() {
        DecisionArtifact artifact = modelArtifact(0, 18 * 1024 * 1024);
        var decodes = new java.util.concurrent.atomic.AtomicInteger();
        ArtifactRuntimeSnapshotService service = new ArtifactRuntimeSnapshotService() {
            protected DecisionArtifact loadArtifact(Long id) { return artifact; }
        };
        org.springframework.test.util.ReflectionTestUtils.setField(service, "codec", new DecisionArtifactPackageCodec() {
            public DecodedPackage decode(byte[] bytes) { decodes.incrementAndGet(); return super.decode(bytes); }
        });
        var first = service.load(1L, 10L, 1L).getModels().get(0);
        var second = service.load(1L, 10L, 1L).getModels().get(0);
        Assert.assertEquals(first.getModelContent(), second.getModelContent());
        Assert.assertNotSame(first.getModelContent(), second.getModelContent());
        Assert.assertEquals(1, decodes.get());
    }

    @Test
    public void sameArtifactIdAndModelPathWithNewDigestCannotReuseOldContent() {
        var current = new java.util.concurrent.atomic.AtomicReference<>(modelArtifact(0, 1024));
        ArtifactRuntimeSnapshotService service = new ArtifactRuntimeSnapshotService() {
            protected DecisionArtifact loadArtifact(Long id) { return current.get(); }
        };
        var first = service.load(1L, 10L, 1L).getModels().get(0).getModelContent();
        current.set(modelArtifact(1, 1024));
        var second = service.load(1L, 10L, 1L).getModels().get(0).getModelContent();
        Assert.assertEquals(0, java.util.Base64.getDecoder().decode(first)[0]);
        Assert.assertEquals(1, java.util.Base64.getDecoder().decode(second)[0]);
    }

    @Test
    public void readsAnImportedDataObjectOncePerExecutionAndRefreshesTheNextExecution() {
        DecisionArtifactPackage pack = new DecisionArtifactPackage();
        for (int i = 0; i < 3; i++) {
            pack.addComponent("data-object-fields/" + i + ".json", "application/json",
                    CanonicalJson.writeBytes(Map.of("id", i, "objectId", 20)),
                    Map.of("resourceType", "DATA_OBJECT"));
        }
        byte[] bytes = new DecisionArtifactPackageCodec().encode(pack);
        var decoded = new DecisionArtifactPackageCodec().decode(bytes);
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setId(1L);
        artifact.setArtifactDigest(decoded.getArtifactDigest());
        artifact.setPackageDigest(decoded.getPackageDigest());
        artifact.setPackageContent(bytes);
        ArtifactDeployment deployment = new ArtifactDeployment();
        deployment.setId(2L);
        ArtifactResourceBinding binding = new ArtifactResourceBinding();
        binding.setComponentId("BINDING:DATA_OBJECT:20");
        binding.setTargetResourceId(501L);
        ArtifactRuntimeSnapshotService service = new ArtifactRuntimeSnapshotService() {
            protected DecisionArtifact loadArtifact(Long id) { return artifact; }
            protected ArtifactDeployment loadDeployment(Long artifactId, Long definitionId) { return deployment; }
            protected List<ArtifactResourceBinding> loadBindings(Long deploymentId) { return List.of(binding); }
        };
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var source = new java.util.concurrent.atomic.AtomicReference<>("first");
        var mapperType = com.hengshucredit.rule.server.mapper.RuleDataObjectMapper.class;
        Object mapper = java.lang.reflect.Proxy.newProxyInstance(mapperType.getClassLoader(),
                new Class<?>[] {mapperType}, (proxy, method, args) -> {
                    if (!"selectById".equals(method.getName())) throw new AssertionError(method.getName());
                    Assert.assertEquals(501L, args[0]);
                    reads.incrementAndGet();
                    if (source.get() == null) return null;
                    var object = new com.hengshucredit.rule.model.entity.RuleDataObject();
                    object.setSourceType("DATABASE");
                    object.setSourceContent(source.get());
                    return object;
                });
        org.springframework.test.util.ReflectionTestUtils.setField(service, "dataObjectMapper", mapper);
        for (String value : new String[] {"first", "second", null}) {
            source.set(value);
            int before = reads.get();
            var snapshot = service.load(1L, 10L, 1L);
            Assert.assertEquals(3, snapshot.getDataObjectFields().size());
            snapshot.getDataObjectFields().forEach(field -> Assert.assertEquals(value, field.getObjectSourceContent()));
            Assert.assertEquals("同一执行不能按字段重复查询绑定对象，包括对象已不存在时", 1, reads.get() - before);
        }
    }

    @Test
    public void reusesVerifiedDecodingWithoutSharingExecutionVariables() {
        DecisionArtifact artifact = variableArtifact();
        ArtifactRuntimeSnapshotService service = new ArtifactRuntimeSnapshotService() {
            protected DecisionArtifact loadArtifact(Long id) { return artifact; }
        };
        java.util.concurrent.atomic.AtomicInteger decodeCount = new java.util.concurrent.atomic.AtomicInteger();
        DecisionArtifactPackageCodec codec = new DecisionArtifactPackageCodec() {
            public DecodedPackage decode(byte[] bytes) {
                decodeCount.incrementAndGet();
                return super.decode(bytes);
            }
        };
        org.springframework.test.util.ReflectionTestUtils.setField(service, "codec", codec);
        var first = service.load(1L, 10L, 1L);
        first.getVariables().get(0).setVarCode("changed");
        first.getVariables().clear();
        var second = service.load(1L, 20L, 2L);
        Assert.assertEquals("age", second.getVariables().get(0).getVarCode());
        Assert.assertEquals(Long.valueOf(2), second.getVariables().get(0).getProjectId());
        Assert.assertEquals(1, decodeCount.get());
    }

    @Test
    public void cacheHitStillRejectsChangedContentOrDeclaredDigest() {
        DecisionArtifact artifact = variableArtifact();
        ArtifactRuntimeSnapshotService service = new ArtifactRuntimeSnapshotService() {
            protected DecisionArtifact loadArtifact(Long id) { return artifact; }
        };
        service.load(1L, 10L, 1L);
        String original = artifact.getArtifactDigest();
        artifact.setArtifactDigest("wrong");
        Assert.assertThrows(IllegalStateException.class, () -> service.load(1L, 10L, 1L));
        artifact.setArtifactDigest(original);
        artifact.getPackageContent()[0] ^= 1;
        Assert.assertThrows(RuntimeException.class, () -> service.load(1L, 10L, 1L));
    }

    @Test
    public void decodingCacheIsBoundedAndEvictsOldPackages() {
        var current = new java.util.concurrent.atomic.AtomicReference<DecisionArtifact>();
        var decodes = new java.util.concurrent.atomic.AtomicInteger();
        ArtifactRuntimeSnapshotService service = new ArtifactRuntimeSnapshotService() {
            protected DecisionArtifact loadArtifact(Long id) { return current.get(); }
        };
        org.springframework.test.util.ReflectionTestUtils.setField(service, "codec", new DecisionArtifactPackageCodec() {
            public DecodedPackage decode(byte[] bytes) { decodes.incrementAndGet(); return super.decode(bytes); }
        });
        for (int i = 0; i < 65; i++) { current.set(variableArtifact(i)); service.load(1L, 10L, 1L); }
        current.set(variableArtifact(0));
        service.load(1L, 10L, 1L);
        Assert.assertEquals(66, decodes.get());
        current.set(variableArtifact(64));
        service.load(1L, 10L, 1L);
        Assert.assertEquals(66, decodes.get());
    }

    private DecisionArtifact variableArtifact() { return variableArtifact(0); }

    private DecisionArtifact variableArtifact(int marker) {
        DecisionArtifactPackage pack = new DecisionArtifactPackage();
        pack.putMetadata("marker", marker);
        pack.addComponent("variables/7.json", "application/json",
                CanonicalJson.writeBytes(Map.of("id", 7, "varCode", "age", "varType", "NUMBER")),
                Map.of("resourceType", "VARIABLE"));
        byte[] bytes = new DecisionArtifactPackageCodec().encode(pack);
        var decoded = new DecisionArtifactPackageCodec().decode(bytes);
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setId(1L);
        artifact.setArtifactDigest(decoded.getArtifactDigest());
        artifact.setPackageDigest(decoded.getPackageDigest());
        artifact.setPackageContent(bytes);
        return artifact;
    }

    @Test
    public void loadsFrozenDataObjectFieldVariableMapping() {
        DecisionArtifactPackage artifactPackage = new DecisionArtifactPackage();
        artifactPackage.addComponent("data-object-fields/30.json", "application/json",
                CanonicalJson.writeBytes(Map.of(
                        "id", 30,
                        "objectId", 20,
                        "scriptName", "ageAlias",
                        "varType", "NUMBER",
                        "refVariableId", 7)),
                Map.of("componentId", "DATA_OBJECT:30",
                        "resourceType", "DATA_OBJECT",
                        "embeddingMode", "EMBEDDED"));
        byte[] packageBytes = new DecisionArtifactPackageCodec().encode(artifactPackage);
        DecisionArtifactPackageCodec.DecodedPackage encoded =
                new DecisionArtifactPackageCodec().decode(packageBytes);
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setId(1L);
        artifact.setArtifactDigest(encoded.getArtifactDigest());
        artifact.setPackageDigest(encoded.getPackageDigest());
        artifact.setPackageContent(packageBytes);
        ArtifactRuntimeSnapshotService service = new ArtifactRuntimeSnapshotService() {
            @Override
            protected DecisionArtifact loadArtifact(Long artifactId) {
                return artifact;
            }
        };

        ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot =
                service.load(1L, 100L, 9L);

        Assert.assertEquals(1, snapshot.getDataObjectFields().size());
        RuleDataObjectField field = snapshot.getDataObjectFields().get(0);
        Assert.assertEquals(Long.valueOf(30L), field.getId());
        Assert.assertEquals(Long.valueOf(7L), field.getRefVariableId());
    }

    @Test
    public void loadsFrozenVariablesAndAppliesExplicitTargetBinding() {
        for (String datasourceKey : List.of("datasourceId", "dbDatasourceId")) {
        DecisionArtifactPackage artifactPackage = new DecisionArtifactPackage();
        artifactPackage.addComponent("variables/7.json", "application/json",
                CanonicalJson.writeBytes(Map.of(
                        "id", 7,
                        "varCode", "riskData",
                        "scriptName", "riskData",
                        "varType", "NUMBER",
                        "varSource", "DB",
                        "sourceConfig", "{\"" + datasourceKey + "\":99,\"sql\":\"select 1\"}",
                        "status", 1)),
                Map.of("componentId", "VARIABLE:7", "resourceType", "VARIABLE",
                        "embeddingMode", "EMBEDDED"));
        artifactPackage.addComponent("bindings/variables/7.json", "application/json",
                CanonicalJson.writeBytes(Map.of(
                        "sourceComponentId", "VARIABLE:7",
                        "targetResourceType", "DB_DATASOURCE")),
                Map.of("componentId", "BINDING:VARIABLE:7", "resourceType", "BINDING",
                        "embeddingMode", "EXPLICIT_BINDING",
                        "targetResourceType", "DB_DATASOURCE"));
        byte[] packageBytes = new DecisionArtifactPackageCodec().encode(artifactPackage);
        DecisionArtifactPackageCodec.DecodedPackage encoded = new DecisionArtifactPackageCodec()
                .decode(packageBytes);
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setId(1L);
        artifact.setArtifactDigest(encoded.getArtifactDigest());
        artifact.setPackageDigest(encoded.getPackageDigest());
        artifact.setPackageContent(packageBytes);

        ArtifactDeployment deployment = new ArtifactDeployment();
        deployment.setId(2L);
        ArtifactResourceBinding binding = new ArtifactResourceBinding();
        binding.setDeploymentId(2L);
        binding.setComponentId("BINDING:VARIABLE:7");
        binding.setResourceType("DB_DATASOURCE");
        binding.setTargetResourceId(501L);
        ArtifactRuntimeSnapshotService service = new ArtifactRuntimeSnapshotService() {
            @Override
            protected DecisionArtifact loadArtifact(Long artifactId) {
                return artifact;
            }

            @Override
            protected ArtifactDeployment loadDeployment(Long artifactId, Long definitionId) {
                return deployment;
            }

            @Override
            protected List<ArtifactResourceBinding> loadBindings(Long deploymentId) {
                return List.of(binding);
            }
        };

        ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot = service.load(1L, 100L, 9L);

        Assert.assertEquals(1, snapshot.getVariables().size());
        RuleVariable variable = snapshot.getVariables().get(0);
        Assert.assertEquals(Long.valueOf(9L), variable.getProjectId());
        Assert.assertEquals(Long.valueOf(501L),
                JSON.parseObject(variable.getSourceConfig()).getLong(datasourceKey));
        binding.setTargetResourceId(502L);
        variable.setSourceConfig("{}");
        var reloaded = service.load(1L, 100L, 10L).getVariables().get(0);
        Assert.assertEquals(Long.valueOf(10L), reloaded.getProjectId());
        Assert.assertEquals(Long.valueOf(502L), JSON.parseObject(reloaded.getSourceConfig()).getLong(datasourceKey));
        }
    }
}
