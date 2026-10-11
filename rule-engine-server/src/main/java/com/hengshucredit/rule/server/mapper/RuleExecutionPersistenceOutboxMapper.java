package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.RuleExecutionPersistenceOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RuleExecutionPersistenceOutboxMapper extends BaseMapper<RuleExecutionPersistenceOutbox> {
    @Select("SELECT * FROM rule_execution_persistence_outbox WHERE delivery_status = 'PENDING' "
            + "AND (next_retry_time IS NULL OR next_retry_time <= CURRENT_TIMESTAMP) "
            + "ORDER BY id LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    java.util.List<RuleExecutionPersistenceOutbox> lockDueBatch(@Param("limit") int limit);

    @Select("<script>SELECT * FROM rule_execution_persistence_outbox WHERE delivery_status = 'PENDING' "
            + "AND (next_retry_time IS NULL OR next_retry_time &lt;= CURRENT_TIMESTAMP) AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY id FOR UPDATE SKIP LOCKED</script>")
    java.util.List<RuleExecutionPersistenceOutbox> lockPendingBatch(@Param("ids") java.util.List<Long> ids);

    @Update("<script>UPDATE rule_execution_persistence_outbox SET log_pending = 0, billing_pending = 0, "
            + "log_json = NULL, definition_json = NULL, billing_snapshot_json = NULL, delivery_status = 'DELIVERED', "
            + "next_retry_time = NULL, last_error = NULL, delivered_time = CURRENT_TIMESTAMP, "
            + "claim_token = NULL, lease_until = NULL, update_time = CURRENT_TIMESTAMP "
            + "WHERE delivery_status = 'PENDING' AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int completeLockedBatch(@Param("ids") java.util.List<Long> ids);

    @Update("UPDATE rule_execution_persistence_outbox "
            + "SET delivery_status = 'PROCESSING', claim_token = #{claimToken}, "
            + "lease_until = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL #{leaseSeconds} SECOND), update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND delivery_status = 'PENDING' "
            + "AND (next_retry_time IS NULL OR next_retry_time <= CURRENT_TIMESTAMP)")
    int claimPending(@Param("id") Long id, @Param("claimToken") String claimToken,
                     @Param("leaseSeconds") int leaseSeconds);

    @Select("SELECT * FROM rule_execution_persistence_outbox WHERE id = #{id} "
            + "AND delivery_status = 'PROCESSING' AND claim_token = #{claimToken} "
            + "AND lease_until > CURRENT_TIMESTAMP")
    RuleExecutionPersistenceOutbox selectClaimed(@Param("id") Long id, @Param("claimToken") String claimToken);

    @Update("UPDATE rule_execution_persistence_outbox "
            + "SET delivery_status = 'PENDING', claim_token = NULL, lease_until = NULL, update_time = CURRENT_TIMESTAMP "
            + "WHERE delivery_status = 'PROCESSING' "
            + "AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP)")
    int releaseExpiredClaims(@Param("leaseSeconds") int leaseSeconds);

    @Update("UPDATE rule_execution_persistence_outbox "
            + "SET log_pending = #{logPending}, billing_pending = #{billingPending}, "
            + "log_json = #{logJson}, definition_json = #{definitionJson}, billing_snapshot_json = #{billingSnapshotJson}, "
            + "delivery_status = #{deliveryStatus}, retry_count = #{retryCount}, "
            + "next_retry_time = #{nextRetryTime}, last_error = #{lastError}, "
            + "delivered_time = #{deliveredTime}, claim_token = NULL, lease_until = NULL, "
            + "update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND delivery_status = 'PROCESSING' "
            + "AND claim_token = #{claimToken} AND lease_until > CURRENT_TIMESTAMP")
    int updateClaimed(RuleExecutionPersistenceOutbox row);
}
