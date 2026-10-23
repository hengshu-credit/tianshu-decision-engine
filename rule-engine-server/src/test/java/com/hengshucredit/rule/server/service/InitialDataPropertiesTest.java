package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InitialDataPropertiesTest {

    @Test
    public void startupSeedsAreOptInAndUseThePackagedScriptByDefault() {
        InitialDataProperties properties = new InitialDataProperties();

        assertFalse(properties.isEnabled());
        assertEquals(List.of("classpath:sql/initial-data.sql"), properties.getLocations());

        properties.setEnabled(true);
        properties.setLocations(List.of("file:/opt/tianshu/initial-data.sql"));
        assertTrue(properties.isEnabled());
        assertEquals(List.of("file:/opt/tianshu/initial-data.sql"), properties.getLocations());
    }
}
