package com.bjjw.rule.server.controller.sync;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleSyncControllerTest {

    @Test
    public void allowsFunctionSyncOnlyForTokenProject() {
        assertTrue(RuleSyncController.isAuthorizedProject(100L, 100L));
    }

    @Test
    public void rejectsFunctionSyncForOtherProject() {
        assertFalse(RuleSyncController.isAuthorizedProject(101L, 100L));
    }

    @Test
    public void rejectsFunctionSyncWhenProjectIdMissing() {
        assertFalse(RuleSyncController.isAuthorizedProject(null, 100L));
        assertFalse(RuleSyncController.isAuthorizedProject(100L, null));
    }
}
