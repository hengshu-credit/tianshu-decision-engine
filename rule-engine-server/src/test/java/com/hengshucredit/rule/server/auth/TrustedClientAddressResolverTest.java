package com.hengshucredit.rule.server.auth;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Arrays;
import java.util.Collections;

public class TrustedClientAddressResolverTest {

    @Test
    public void trustsForwardedChainOnlyFromConfiguredProxyAndWalksRightToLeft() {
        ProjectAuthProperties properties = new ProjectAuthProperties();
        properties.setTrustedProxyCidrs(Arrays.asList("10.0.0.0/8", "2001:db8::/32"));
        TrustedClientAddressResolver resolver = new TrustedClientAddressResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.1.2.3");

        Assert.assertEquals("203.0.113.9", resolver.resolve(request));

        request.setRemoteAddr("198.51.100.8");
        Assert.assertEquals("198.51.100.8", resolver.resolve(request));
    }

    @Test
    public void supportsIpv6CidrAndNormalizesHostWithoutTreatingItAsIdentity() {
        ProjectAuthProperties properties = new ProjectAuthProperties();
        properties.setTrustedProxyCidrs(Collections.singletonList("2001:db8::/32"));
        TrustedClientAddressResolver resolver = new TrustedClientAddressResolver(properties);
        Assert.assertTrue(resolver.matchesIp("2001:db8:1::9", Collections.singletonList("2001:db8::/32")));
        Assert.assertFalse(resolver.matchesIp("2001:db9::1", Collections.singletonList("2001:db8::/32")));

        Assert.assertTrue(resolver.matchesHost("Api.Example.COM.:8443",
                Arrays.asList("api.example.com", "*.service.example.com")));
        Assert.assertTrue(resolver.matchesHost("risk.service.example.com",
                Collections.singletonList("*.service.example.com")));
        Assert.assertFalse(resolver.matchesHost("service.example.com",
                Collections.singletonList("*.service.example.com")));
    }
}
