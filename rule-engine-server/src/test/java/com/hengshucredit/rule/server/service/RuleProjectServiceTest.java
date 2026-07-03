package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class RuleProjectServiceTest {

    @Test
    public void getModelTypeLabelSupportsCurrentAndLegacyEnums() throws Exception {
        RuleProjectService service = new RuleProjectService();
        Method method = RuleProjectService.class.getDeclaredMethod("getModelTypeLabel", String.class);
        method.setAccessible(true);

        assertEquals("交叉表", method.invoke(service, "CROSS"));
        assertEquals("交叉表", method.invoke(service, "CROSS_TABLE"));
        assertEquals("评分卡", method.invoke(service, "SCORE"));
        assertEquals("评分卡", method.invoke(service, "SCORE_CARD"));
        assertEquals("复杂交叉表", method.invoke(service, "CROSS_ADV"));
        assertEquals("复杂交叉表", method.invoke(service, "CROSS_TABLE_ADV"));
        assertEquals("复杂评分卡", method.invoke(service, "SCORE_ADV"));
        assertEquals("复杂评分卡", method.invoke(service, "SCORE_CARD_ADV"));
        assertEquals("规则集", method.invoke(service, "RULE_SET"));
    }
}
