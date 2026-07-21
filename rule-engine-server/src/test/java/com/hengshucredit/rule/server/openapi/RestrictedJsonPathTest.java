package com.hengshucredit.rule.server.openapi;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;

public class RestrictedJsonPathTest {

    @Test
    public void readsObjectPropertiesAndArrayIndexes() {
        Object body = JSON.parse("{\"customer\":{\"idNo\":\"A001\"},\"items\":[{\"score\":88}]}");

        Assert.assertEquals("A001", RestrictedJsonPath.read(body, "$.customer.idNo"));
        Assert.assertEquals(88, RestrictedJsonPath.read(body, "$.items[0].score"));
        Assert.assertSame(body, RestrictedJsonPath.read(body, "$"));
    }

    @Test
    public void rejectsUnboundedOrExecutableSyntax() {
        assertRejected("$..idNo");
        assertRejected("$.items[*]");
        assertRejected("$.items[?(@.score>60)]");
        assertRejected("$.items.length()");
        assertRejected("customer.idNo");
    }

    private void assertRejected(String path) {
        try {
            RestrictedJsonPath.read(JSON.parse("{}"), path);
            Assert.fail("Expected path to be rejected: " + path);
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("JSONPath"));
        }
    }
}
