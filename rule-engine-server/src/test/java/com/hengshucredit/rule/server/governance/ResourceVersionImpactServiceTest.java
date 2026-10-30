package com.hengshucredit.rule.server.governance;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.*;
import com.hengshucredit.rule.server.service.ResourceVersionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.Assert.*;

public class ResourceVersionImpactServiceTest {
    @Test public void encryptedAuthenticationConfigurationStillBlocksDeletingReferencedVersion() {
        var keys = new com.hengshucredit.rule.server.auth.ProjectAuthProperties();
        keys.setActiveKeyId("test"); keys.setMasterKeys(Map.of("test", UUID.randomUUID().toString()));
        var codec = new com.hengshucredit.rule.server.auth.StoredCredentialCodec(
                new com.hengshucredit.rule.server.auth.CredentialCipher(keys));
        var fixture = new Fixture();
        ReflectionTestUtils.setField(fixture.service, "storedCredentials", codec);
        var target = version("VARIABLE", 7L, 81L, 1, 1, "{\"id\":7}");
        String encrypted = codec.encryptJson(fixed("VARIABLE", 7, 81, "BUSINESS_VERSION"));
        fixture.current.add(source("EXTERNAL_DATASOURCE", 20L, com.alibaba.fastjson.JSON.toJSONString(
                Map.of("authConfig", com.alibaba.fastjson.JSON.parseObject(encrypted)))));
        fixture.current.add(source("EXTERNAL_API", 21L, com.alibaba.fastjson.JSON.toJSONString(Map.of("authApiConfig", encrypted))));
        var issues = fixture.service.analyze(target, List.of(target));
        assertEquals(2, issues.size());
        assertTrue(issues.stream().anyMatch(issue -> issue.message().contains("EXTERNAL_DATASOURCE #20")));
        assertTrue(issues.stream().anyMatch(issue -> issue.message().contains("EXTERNAL_API #21")));
    }

    @Test public void onlyReferencesToTheDeletedLineBlockItIncludingAnOverwrittenAnchor() {
        var target = version("VARIABLE", 7L, 91L, 3, 1, "{\"id\":7}");
        var original = version("VARIABLE", 7L, 81L, 1, 1, "{\"id\":7}");
        var other = version("VARIABLE", 7L, 82L, 2, 2, "{\"id\":7}");
        var fixture = new Fixture();
        fixture.current.add(source("RULE", 20L, fixed("VARIABLE",7,82,"BUSINESS_VERSION")));
        fixture.current.add(source("RULE", 21L, "{\"operand\":{\"refType\":\"VARIABLE\",\"refId\":7,\"versionMode\":\"LATEST\"}}"));
        assertTrue(fixture.service.analyze(target, List.of(target, original, other)).isEmpty());
        fixture.governed.add(source("RULE", 22L, fixed("VARIABLE",7,81,"BUSINESS_VERSION")));
        var issues = fixture.service.analyze(target, List.of(target, original, other));
        assertEquals(1, issues.size()); assertTrue(issues.get(0).message().contains("RULE #22"));
        fixture.governed.clear(); fixture.nativeRows.add(source("RULE",23L, "{\"script\":\"return resourceVersionValue('VARIABLE',7,'BUSINESS_VERSION:81','');\"}"));
        assertEquals(1, fixture.service.analyze(target, List.of(target, original, other)).size());
    }

    @Test public void dataObjectFieldChecksTheHistoricalOwnerAndVersionNamespace() {
        var target = version("DATA_OBJECT",7L,81L,1,1,"{\"id\":7,\"fields\":[{\"id\":701}]}");
        var fixture = new Fixture();
        fixture.current.add(source("VARIABLE",20L,fixed("DATA_OBJECT",999,81,"BUSINESS_VERSION")));
        fixture.current.add(source("VARIABLE",21L,fixed("VARIABLE",701,81,"BUSINESS_VERSION")));
        assertTrue(fixture.service.analyze(target,List.of(target)).isEmpty());
        fixture.current.add(source("VARIABLE",22L,fixed("DATA_OBJECT",701,81,"BUSINESS_VERSION")));
        assertEquals(1,fixture.service.analyze(target,List.of(target)).size());
    }

    @Test public void modelNativeIdsUseExplicitFileLinksAndNeverGuessSameBusinessNumber() {
        var target = version("MODEL",7L,81L,1,1,"{\"id\":7,\"_modelContentVersionId\":501,\"outputFields\":[{\"id\":701}]}");
        var fixture = new Fixture();
        var unknown = new RuleModelVersion(); unknown.setId(502L); unknown.setModelId(7L); unknown.setVersion(1); fixture.models.add(unknown);
        fixture.current.add(source("RULE",20L,fixed("MODEL",7,502,"SNAPSHOT")));
        fixture.current.add(source("RULE",21L,fixed("MODEL",7,81,"SNAPSHOT")));
        assertTrue(fixture.service.analyze(target,List.of(target)).isEmpty());
        fixture.current.add(source("RULE",22L,fixed("MODEL_OUTPUT",701,501,"SNAPSHOT")));
        assertEquals(1,fixture.service.analyze(target,List.of(target)).size());
    }

    @Test public void ruleBindingsDoNotCollideWithGovernanceRevisionIds() {
        var target = version("RULE",7L,81L,1,1,"{\"id\":7}");
        target.setLegacySourceType("RULE_VERSION"); target.setLegacySourceId(81L);
        var fixture = new Fixture();
        var binding = new RuleVersionBinding(); binding.setId(501L); binding.setDefinitionId(7L); binding.setVersionNo(1); fixture.bindings.add(binding);
        fixture.current.add(source("EXPERIMENT",20L,"{\"ruleId\":7,\"versionMode\":\"FIXED\",\"versionBindingId\":81}"));
        assertTrue(fixture.service.analyze(target,List.of(target)).isEmpty());
        fixture.current.add(source("EXPERIMENT",21L,"{\"ruleId\":7,\"versionMode\":\"FIXED\",\"versionBindingId\":501}"));
        assertEquals(1,fixture.service.analyze(target,List.of(target)).size());
        fixture.current.clear();
        var otherLine = new HashMap<String,Object>(source("RULE",7L,"{\"ruleId\":7,\"versionMode\":\"FIXED\",\"versionBindingId\":501}"));
        otherLine.put("businessVersion",2); otherLine.put("nativeId",501L); fixture.nativeRows.add(otherLine);
        assertEquals("另一业务线的快照 ID 与绑定同号也不能漏掉消费者",1,fixture.service.analyze(target,List.of(target)).size());
    }

    @Test public void functionHistoryUsesExplicitBusinessMappingAndIgnoresDeletedConsumers() {
        var target = version("FUNCTION",7L,81L,1,1,"{\"id\":7}");
        var fixture = new Fixture();
        var nativeVersion = new RuleFunctionVersion(); nativeVersion.setId(501L); nativeVersion.setFunctionId(7L); nativeVersion.setBusinessVersion(1); fixture.functions.add(nativeVersion);
        fixture.current.add(source("RULE",20L,fixed("FUNCTION",7,81,"FUNCTION_HISTORY")));
        assertTrue(fixture.service.analyze(target,List.of(target)).isEmpty());
        fixture.current.add(source("RULE",21L,fixed("FUNCTION",7,501,"FUNCTION_HISTORY")));
        assertEquals(1,fixture.service.analyze(target,List.of(target)).size());
        fixture.current.clear();
        var removedConsumer = new HashMap<String,Object>(source("FUNCTION",22L,"{\"status\":1,\"implType\":\"SCRIPT\",\"implScript\":\"return invokeFunctionVersionById(7,'FUNCTION_HISTORY:501');\"}"));
        removedConsumer.put("nativeId",99L); fixture.nativeRows.add(removedConsumer);
        ReflectionTestUtils.setField(fixture.service,"resourceVersions",new ResourceVersionService() {
            @Override public void assertNativeVersionAvailable(String type, Long id, Integer business, Long nativeId) { throw new IllegalArgumentException("所属业务版本已删除"); }
        });
        assertTrue(fixture.service.analyze(target,List.of(target)).isEmpty());
    }

    private static String fixed(String type,long id,long version,String source) {
        return "{\"operand\":{\"kind\":\""+("FUNCTION".equals(type)?"FUNCTION":"REFERENCE")+"\",\"refType\":\""+type+"\",\"refId\":"+id+",\"functionId\":"+id+",\"versionMode\":\"FIXED\",\"versionId\":"+version+",\"versionSource\":\""+source+"\"}}";
    }
    private static Map<String,Object> source(String type,long id,String json) { return Map.of("resourceType",type,"resourceId",id,"snapshotJson",json,"sourceLabel","验证配置"); }
    private static GovernedResourceVersion version(String type,long owner,long id,int revision,int business,String json) {
        var row=new GovernedResourceVersion(); row.setResourceType(type);row.setResourceId(owner);row.setId(id);row.setVersionNo(revision);row.setBusinessVersion(business);row.setSnapshotJson(json);row.setEffectiveStatus("ACTIVE");return row;
    }
    private static class Fixture {
        final ResourceVersionImpactService service=new ResourceVersionImpactService();
        final List<Map<String,Object>> current=new ArrayList<>(),governed=new ArrayList<>(),nativeRows=new ArrayList<>();
        final List<RuleVersionBinding> bindings=new ArrayList<>();final List<RuleModelVersion> models=new ArrayList<>();final List<RuleFunctionVersion> functions=new ArrayList<>();
        Fixture() {
            for(var entity:List.of(RuleVersionBinding.class,RuleModelVersion.class,RuleFunctionVersion.class)) TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"version-impact"),entity);
            ReflectionTestUtils.setField(service,"consumers",Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResourceVersionConsumerMapper.class},(p,m,a)->switch(m.getName()){case "currentSnapshots"->current;case "governedSnapshots"->governed;default->nativeRows;}));
            ReflectionTestUtils.setField(service,"ruleBindings",Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{RuleVersionBindingMapper.class},(p,m,a)->bindings));
            ReflectionTestUtils.setField(service,"modelVersions",Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{RuleModelVersionMapper.class},(p,m,a)->models));
            ReflectionTestUtils.setField(service,"functionVersions",Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{RuleFunctionVersionMapper.class},(p,m,a)->functions));
        }
    }
}
