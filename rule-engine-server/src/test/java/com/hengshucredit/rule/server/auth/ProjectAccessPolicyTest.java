package com.hengshucredit.rule.server.auth;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class ProjectAccessPolicyTest {

    @Test
    public void parsesAndValidatesBoundedPolicyValues() {
        ProjectAccessPolicy policy = ProjectAccessPolicy.parse("{\"ipWhitelist\":[\"10.0.0.0/8\"],"
                + "\"hostWhitelist\":[\"api.example.com\"],\"qps\":20,\"burst\":40,"
                + "\"maxConcurrent\":10,\"requestTimeoutMs\":3000}");

        Assert.assertEquals(Arrays.asList("10.0.0.0/8"), policy.getIpWhitelist());
        Assert.assertEquals(20, policy.getQps());
        Assert.assertEquals(40, policy.getBurst());
        Assert.assertEquals(10, policy.getMaxConcurrent());
        Assert.assertEquals(3000, policy.getRequestTimeoutMs());
    }

    @Test
    public void rejectsUnboundedOrInconsistentValues() {
        assertInvalid("{\"qps\":10,\"burst\":5}", "burst");
        assertInvalid("{\"maxConcurrent\":10001}", "maxConcurrent");
        assertInvalid("{\"requestTimeoutMs\":50}", "requestTimeoutMs");
        assertInvalid("{\"ipWhitelist\":[\"not-an-ip\"]}", "IP");
        assertInvalid("{\"hostWhitelist\":[\"api..example.com\"]}", "Host");
        assertInvalid("{\"hostWhitelist\":[\"https://api.example.com\"]}", "Host");
    }

    private void assertInvalid(String json, String message) {
        try {
            ProjectAccessPolicy.parse(json);
            Assert.fail("Expected invalid policy");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }
}
