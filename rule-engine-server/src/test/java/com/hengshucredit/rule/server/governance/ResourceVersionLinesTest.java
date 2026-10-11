package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ResourceVersionLinesTest {
    @Test public void overwritingV1DoesNotMoveLatestBackFromV2() {
        var v1 = version(1, 1); var v2 = version(2, 2); var overwrite = version(3, 1);
        var history = List.of(v1, overwrite, v2);
        assertSame(v2, ResourceVersionLines.heads(history).get(0));
        assertSame(overwrite, ResourceVersionLines.head(history, v1));
        assertEquals(Integer.valueOf(1), v1.getVersionNo());
    }

    @Test public void deletedHeadCannotFallBackToOldRevision() {
        var v1 = version(1, 1); var overwrite = version(3, 1); overwrite.setDeleted(true);
        assertThrows(IllegalArgumentException.class, () -> ResourceVersionLines.head(List.of(v1, overwrite), v1));
        assertFalse(ResourceVersionLines.published(overwrite));
    }

    private GovernedResourceVersion version(int revision, int business) {
        var value = new GovernedResourceVersion(); value.setId((long) revision);
        value.setVersionNo(revision); value.setBusinessVersion(business); value.setEffectiveStatus("ACTIVE");
        return value;
    }
}
