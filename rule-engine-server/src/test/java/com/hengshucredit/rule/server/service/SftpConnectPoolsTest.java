package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleSftpDatasource;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class SftpConnectPoolsTest {
    @Test
    public void retriesTransientReadFailuresWithConfiguredAttempts() throws Exception {
        RetryingPools pools = new RetryingPools(2);

        SftpConnectPools.ReadResult result = pools.readFileWithRetry(1L, "/file.txt", 3, 0, 2D);

        Assert.assertArrayEquals("重试成功后应返回远端文件内容", new byte[]{7}, result.content());
        Assert.assertEquals("实际重试次数应记录为 2", 2, result.retryCount());
        Assert.assertEquals("初次失败后再执行两次", 3, pools.attempts);
    }
    @Test
    public void resolvesBasePathAndRejectsTraversal() {
        SftpConnectPools pools = new SftpConnectPools();
        RuleSftpDatasource datasource = new RuleSftpDatasource();
        datasource.setBasePath("/upload/contracts");

        Assert.assertEquals("/upload/contracts/2026/a.pdf",
                pools.remotePath("2026/a.pdf", datasource));
        Assert.assertEquals("/absolute/a.pdf",
                pools.remotePath("/absolute/a.pdf", datasource));
        try {
            pools.remotePath("2026/../secret.pdf", datasource);
            Assert.fail("上级目录路径必须被拒绝");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("上级目录"));
        }
    }

    @Test
    public void acceptsWindowsSeparatorsButKeepsRemotePathSemantics() {
        SftpConnectPools pools = new SftpConnectPools();
        RuleSftpDatasource datasource = new RuleSftpDatasource();
        datasource.setBasePath("/upload");
        Assert.assertEquals("/upload/images/id.png",
                pools.remotePath("images\\id.png", datasource));
    }

    private static class RetryingPools extends SftpConnectPools {
        private int failures;
        private int attempts;

        private RetryingPools(int failures) { this.failures = failures; }

        @Override
        protected byte[] readFileOnce(Long datasourceId, String remotePath) throws Exception {
            attempts++;
            if (failures-- > 0) throw new IOException("temporary SFTP failure");
            return new byte[]{7};
        }
    }
}
