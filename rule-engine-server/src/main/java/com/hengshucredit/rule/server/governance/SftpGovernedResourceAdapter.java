package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.RuleSftpDatasource;
import com.hengshucredit.rule.server.service.SftpConnectPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

/** 审批生效后刷新本节点 SFTP 连接池。 */
public class SftpGovernedResourceAdapter extends SimpleEntityGovernedResourceAdapter<RuleSftpDatasource> {
    private static final Logger log = LoggerFactory.getLogger(SftpGovernedResourceAdapter.class);
    private final SftpConnectPools pools;

    public SftpGovernedResourceAdapter(EntityStore<RuleSftpDatasource> store,
                                       GovernanceSecretCodec secretCodec,
                                       SftpConnectPools pools) {
        super(GovernanceResourceTypes.SFTP, RuleSftpDatasource.class, store,
                RuleSftpDatasource::getId, RuleSftpDatasource::setId,
                RuleSftpDatasource::getStatus, RuleSftpDatasource::setStatus,
                Set.of("datasourceCode", "datasourceName", "host", "username"),
                Set.of("password", "privateKey", "passphrase", "knownHosts"), secretCodec);
        this.pools = pools;
    }

    @Override
    public AppliedResource apply(ApprovalApplyContext context) {
        AppliedResource applied = super.apply(context);
        if (context.draftWrite() || context.projectionOnly() || context.isolatedPublication()) return applied;
        Long resourceId = applied.resourceId();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { refresh(resourceId); }
            });
        } else refresh(resourceId);
        return applied;
    }

    private void refresh(Long resourceId) {
        try { pools.refresh(resourceId); }
        catch (RuntimeException failure) { log.error("SFTP配置已提交，但本节点连接池失效失败，数据源 ID={}", resourceId, failure); }
    }
}
