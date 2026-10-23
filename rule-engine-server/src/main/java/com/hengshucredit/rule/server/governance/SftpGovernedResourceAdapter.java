package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.RuleSftpDatasource;
import com.hengshucredit.rule.server.service.SftpConnectPools;
import com.hengshucredit.rule.server.service.SftpConfigurationValidator;
import com.alibaba.fastjson.JSON;
import java.util.ArrayList;
import java.util.List;
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
                CredentialChanges.SFTP_FIELDS, secretCodec);
        this.pools = pools;
    }

    @Override
    public List<GovernanceIssue> validate(ResourceSnapshot draft) {
        List<GovernanceIssue> issues = new ArrayList<>(super.validate(draft));
        RuleSftpDatasource value = JSON.parseObject(draft.snapshotJson(), RuleSftpDatasource.class);
        SftpConfigurationValidator.issues(value).forEach((field, message) -> issues.add(
                GovernanceIssue.error("SFTP_CONFIGURATION_INVALID", message, resourceType(), value.getId(), "$." + field)));
        return issues;
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
