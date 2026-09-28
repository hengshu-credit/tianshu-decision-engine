package com.hengshucredit.rule.server.service;

import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class LogRetentionPropertiesTest {
    @Test
    public void zeroDaysAreValidPermanentRetentionDefaults() {
        LogRetentionProperties properties = new LogRetentionProperties();
        properties.validate();
    }

    @Test
    public void invalidIntervalAndDaysFailClosed() {
        LogRetentionProperties properties = new LogRetentionProperties();
        properties.setIntervalMs(1);
        assertThrows(IllegalStateException.class, properties::validate);
        properties.setIntervalMs(86_400_000L);
        properties.setAuthAccessLogDays(-1);
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    public void invalidBatchAndTimeoutBudgetsAreRejected() {
        LogRetentionProperties properties = new LogRetentionProperties();
        properties.setBatchSize(0);
        assertThrows(IllegalStateException.class, properties::validate);
        properties.setBatchSize(10_001);
        assertThrows(IllegalStateException.class, properties::validate);
        properties.setBatchSize(1000);
        properties.setMaxBatchesPerTable(101);
        assertThrows(IllegalStateException.class, properties::validate);
        properties.setMaxBatchesPerTable(10);
        properties.setQueryTimeoutSeconds(0);
        assertThrows(IllegalStateException.class, properties::validate);
    }
}
