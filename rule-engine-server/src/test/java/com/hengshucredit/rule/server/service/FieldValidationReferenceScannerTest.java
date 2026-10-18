package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;

public class FieldValidationReferenceScannerTest {

    @Test
    public void scansExternalApiAndExperimentBindingShapes() {
        Object api = JSON.parse("{\"version\":2,\"requestBranches\":[{"
                + "\"requestFields\":[{\"validationRuleIds\":[17]}]}],"
                + "\"steps\":[{\"requestFields\":[{\"validationRuleIds\":\"[18]\"}]}]}");
        Object experiment = JSON.parse("{\"VARIABLE:12\":[19],\"PATH:age\":[20]}");

        Assert.assertTrue(FieldValidationReferenceScanner
                .containsValidationRuleId(api, 17L));
        Assert.assertTrue(FieldValidationReferenceScanner
                .containsValidationRuleId(api, 18L));
        Assert.assertTrue(FieldValidationReferenceScanner
                .containsValidationRuleId(experiment, 19L));
        Assert.assertFalse(FieldValidationReferenceScanner
                .containsValidationRuleId(experiment, 21L));
    }
}
