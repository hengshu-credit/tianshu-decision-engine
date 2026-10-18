package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.server.service.DBConnectPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

/** 审批事务提交后失效本节点连接池；跨节点失效需要独立的通知与收敛机制。 */
public class DatabaseGovernedResourceAdapter extends SimpleEntityGovernedResourceAdapter<RuleDbDatasource> {
    private static final Logger log = LoggerFactory.getLogger(DatabaseGovernedResourceAdapter.class);
    private final DBConnectPools pools;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DatabasePoolRefreshNotifier refreshNotifier;

    public DatabaseGovernedResourceAdapter(EntityStore<RuleDbDatasource> store,
                                            GovernanceSecretCodec secretCodec,
                                            DBConnectPools pools) {
        super(GovernanceResourceTypes.DATABASE, RuleDbDatasource.class, store,
                RuleDbDatasource::getId, RuleDbDatasource::setId,
                RuleDbDatasource::getStatus, RuleDbDatasource::setStatus,
                Set.of("datasourceCode", "datasourceName", "dbType"),
                Set.of("password", "sshPassword", "sshPrivateKey", "sshPassphrase"), secretCodec);
        this.pools = pools;
    }

    @Override
    public AppliedResource apply(ApprovalApplyContext context) {
        AppliedResource applied = super.apply(context);
        if (context.draftWrite() || context.projectionOnly() || context.isolatedPublication()) return applied;
        Long resourceId = applied.resourceId();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    refreshLocalPool(resourceId);
                }
            });
        } else {
            refreshLocalPool(resourceId);
        }
        if (refreshNotifier != null) refreshNotifier.publish(resourceId);
        return applied;
    }

    private void refreshLocalPool(Long resourceId) {
        try {
            pools.refresh(resourceId);
        } catch (RuntimeException failure) {
            // 此时数据库变更已提交，不能让失效异常伪装成审批事务回滚。
            log.error("数据库变更已提交，但本节点连接池失效失败，数据源 ID={}", resourceId, failure);
        }
    }
}
