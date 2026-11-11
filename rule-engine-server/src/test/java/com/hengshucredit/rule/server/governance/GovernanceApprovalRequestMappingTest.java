package com.hengshucredit.rule.server.governance;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import org.junit.Assert;
import org.junit.Test;

public class GovernanceApprovalRequestMappingTest {
    @Test
    public void clearingPreflightEvidenceIsPersistedToDatabase() throws Exception {
        for (String field : new String[]{"validationReportJson", "dependencyDigest"}) {
            TableField mapping = GovernanceApprovalRequest.class.getDeclaredField(field).getAnnotation(TableField.class);
            Assert.assertNotNull(field, mapping);
            Assert.assertEquals(field, FieldStrategy.ALWAYS, mapping.updateStrategy());
        }
    }

    @Test
    public void terminalRequestCanReleaseActiveResourceKey() throws Exception {
        TableField mapping = GovernanceApprovalRequest.class
                .getDeclaredField("activeResourceKey")
                .getAnnotation(TableField.class);

        Assert.assertNotNull(mapping);
        Assert.assertEquals(FieldStrategy.ALWAYS, mapping.updateStrategy());
    }
}
