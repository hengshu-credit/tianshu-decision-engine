package com.hengshucredit.rule.server.openapi;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class OpenApiStatusesTest {

    @Test
    public void definesStableCodesForAllPublicFailureScenes() {
        Set<String> codes = new HashSet<>(Arrays.asList(
                OpenApiStatuses.SUCCESS,
                OpenApiStatuses.PARAMETER_VALIDATION_ERROR,
                OpenApiStatuses.REQUIRED_FIELD_MISSING,
                OpenApiStatuses.RESULT_ERROR,
                OpenApiStatuses.TOKEN_EXPIRED,
                OpenApiStatuses.ACCOUNT_PASSWORD_ERROR,
                OpenApiStatuses.IP_RESTRICTED,
                OpenApiStatuses.DOMAIN_RESTRICTED,
                OpenApiStatuses.ACCOUNT_DISABLED,
                OpenApiStatuses.QPS_CONCURRENCY_EXCEEDED,
                OpenApiStatuses.REQUEST_TOO_FREQUENT,
                OpenApiStatuses.REQUEST_TIMEOUT,
                OpenApiStatuses.PRODUCT_UNAUTHORIZED,
                OpenApiStatuses.DAILY_QUOTA_EXCEEDED,
                OpenApiStatuses.MONTHLY_QUOTA_EXCEEDED,
                OpenApiStatuses.REQUEST_PRODUCT_UNAUTHORIZED,
                OpenApiStatuses.SYSTEM_ERROR));

        Assert.assertEquals(17, codes.size());
        Assert.assertEquals(200, OpenApiStatuses.success().getHttpStatus());
        Assert.assertEquals(400, OpenApiStatuses.requiredField(null).getHttpStatus());
        Assert.assertEquals(429, OpenApiStatuses.qpsConcurrencyExceeded().getHttpStatus());
        Assert.assertEquals(504, OpenApiStatuses.requestTimeout().getHttpStatus());
    }
}
