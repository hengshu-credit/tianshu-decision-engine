package com.hengshucredit.rule.server.testdata;

import com.hengshucredit.rule.model.entity.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TestMockServiceTest {
    @Test public void objectAndNestedChildBindingsKeepSiblingsAndUseTypedIds() {
        RuleDataObject root = new RuleDataObject();
        root.setId(7L); root.setObjectCode("Applicant"); root.setMockRuleId(1L);
        RuleDataObjectField age = new RuleDataObjectField();
        age.setId(7L); age.setObjectId(7L); age.setVarType("INTEGER"); age.setVarCode("age"); age.setMockRuleId(2L);
        var rules = List.of(new MockRule(1L,"申请人","OBJECT","","FIXED","{\"profile\":{\"age\":20,\"score\":9007199254740993}}",false),
                new MockRule(2L,"年龄","NUMBER","","INTEGER","[33,33]",false));
        var result = TestMockService.objectSample(root,List.of(age),Map.of(7L,root),Map.of(7L,age),
                Map.of("DATA_OBJECT:7","Applicant.profile.age"),rules,new TestMockService.Bindings(Map.of(),Map.of(7L,1L),Map.of(7L,2L),Map.of()));
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
