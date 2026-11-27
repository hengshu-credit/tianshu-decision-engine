package com.hengshucredit.rule.server.testdata;

import com.hengshucredit.rule.model.entity.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TestMockServiceTest {
    @Test public void fixedCollectionAndDecimalAliasesKeepJsonTypes() {
        var list = ExampleSetting.resolve("FIXED","[0,false]",null,null);
        assertEquals(MockRule.parse("[0,false]"),list.value("SET","choices","集合",null,List.of()));
        var decimal = ExampleSetting.resolve("FIXED","9007199254740993.01",null,null);
        assertEquals(new java.math.BigDecimal("9007199254740993.01"),decimal.value("BIGDECIMAL","amount","金额",null,List.of()).decimalValue());
    }

    @Test public void objectWithoutAnExampleUsesChildTypesAndLiveCaseOverrides() {
        RuleDataObject root = new RuleDataObject();
        root.setId(2L); root.setObjectCode("Applicant");
        RuleDataObjectField field = new RuleDataObjectField();
        field.setId(9L); field.setObjectId(2L); field.setVarCode("score"); field.setVarType("INTEGER");
        var auto = new TestMockService.Bindings(Map.of(),Map.of(),Map.of(),Map.of());
        var result = TestMockService.objectSample(root,List.of(field),Map.of(2L,root),Map.of(9L,field),Map.of(),List.of(),auto);
        assertTrue(result.path("score").isIntegralNumber());
        var binding = new ExampleSetting("CASE",null,null,() -> MockRule.parse("{\"score\":40,\"active\":false}"));
        var overrides = new TestMockService.Bindings(Map.of(),Map.of(2L,binding),Map.of(9L,new ExampleSetting("FIXED","0",null)),Map.of());
        result = TestMockService.objectSample(root,List.of(field),Map.of(2L,root),Map.of(9L,field),Map.of(),List.of(),overrides);
        assertEquals(MockRule.parse("{\"score\":0,\"active\":false}"),result);
    }

    @Test public void objectAndNestedChildBindingsKeepSiblingsAndUseTypedIds() {
        RuleDataObject root = new RuleDataObject();
        root.setId(7L); root.setObjectCode("Applicant"); root.setMockRuleId(1L);
        RuleDataObjectField age = new RuleDataObjectField();
        age.setId(7L); age.setObjectId(7L); age.setVarType("INTEGER"); age.setVarCode("age"); age.setMockRuleId(2L);
        var rules = List.of(new MockRule(1L,"申请人","OBJECT","","FIXED","{\"profile\":{\"age\":20,\"score\":9007199254740993}}",false),
                new MockRule(2L,"年龄","NUMBER","","INTEGER","[33,33]",false));
        var result = TestMockService.objectSample(root,List.of(age),Map.of(7L,root),Map.of(7L,age),
                Map.of("DATA_OBJECT:7","Applicant.profile.age"),rules,new TestMockService.Bindings(Map.of(),Map.of(7L,new ExampleSetting("RULE",null,1L)),Map.of(7L,new ExampleSetting("RULE",null,2L)),Map.of()));
        assertEquals(33,result.at("/profile/age").intValue());
        assertEquals(9007199254740993L,result.at("/profile/score").longValue());
    }

    @Test public void enumAndExplicitBindingTakePrecedenceOverTypeDefaults() {
        var type = new MockRule(1L,"类型默认","STRING","*","FIXED","\"默认\"",false);
        var business = new MockRule(2L,"状态","STRING","status","FIXED","\"通过\"",false);
        var rules = List.of(type,business);
        assertEquals("通过",TestMockService.sample("STRING","profile.status","结果",null,null,rules).asText());
        assertEquals("默认",TestMockService.sample("STRING","profile.status","结果",1L,null,rules).asText());
        assertEquals("审核",TestMockService.sample("ENUM","profile.status","结果",null,"[{\"optionValue\":\"审核\"}]",rules).asText());
        assertThrows(IllegalArgumentException.class,() -> TestMockService.sample("OBJECT","profile","对象",2L,null,rules));
    }

    @Test public void pageExamplesOverrideActiveBindingsAndManualPaths() {
        var bound = new MockRule(21L,"主动关联","NUMBER","","INTEGER","[20,20]",false);
        var path = new MockRule(22L,"手输路径","NUMBER","applicant.age","INTEGER","[30,30]",false);
        var rules = List.of(bound,path);
        assertEquals(0,ExampleSetting.resolve("FIXED","0",null,21L).value("NUMBER","applicant.age","年龄",null,rules).intValue());
        assertTrue(ExampleSetting.resolve("NONE",null,null,21L).value("NUMBER","applicant.age","年龄",null,rules).isMissingNode());
        assertEquals(20,ExampleSetting.resolve("AUTO",null,null,21L).value("NUMBER","applicant.age","年龄",null,rules).intValue());
        assertEquals(30,ExampleSetting.AUTO.value("NUMBER","applicant.age","年龄",null,rules).intValue());
        assertEquals("001",ExampleSetting.resolve(null,"001",null,null).value("STRING","code","编号",null,rules).asText());
        assertTrue(ExampleSetting.resolve("FIXED","",null,null).value("STRING","code","编号",null,rules).isMissingNode());
    }

    @Test public void explicitChildOmissionRemovesInheritedObjectMember() {
        var object=MockRule.parse("{\"profile\":{\"secret\":\"placeholder\",\"keep\":false}}");
        TestMockService.writePath(object,"profile.secret",com.fasterxml.jackson.databind.node.MissingNode.getInstance(),true);
        assertEquals("{\"profile\":{\"keep\":false}}",object.toString());
    }

    @Test public void customRangesAndTemplatesAreValidated() {
        var decimal = new MockRule(null,"利率","NUMBER","","DECIMAL","[0.05,0.09,2]",false);
        decimal.validate();
        var rate = decimal.sample().decimalValue();
        assertTrue(rate.doubleValue() >= 0.05 && rate.doubleValue() <= 0.09);
        assertEquals(2,rate.scale());
        var template = new MockRule(null,"单号","STRING","","TEMPLATE","\"Q-{digits}\"",false);
        template.validate();
        assertTrue(template.sample().asText().matches("Q-[0-9]{6}"));
        assertThrows(IllegalArgumentException.class,() -> new MockRule(null,"对象","OBJECT","","CHOICE","[1]",false).validate());
    }
}
