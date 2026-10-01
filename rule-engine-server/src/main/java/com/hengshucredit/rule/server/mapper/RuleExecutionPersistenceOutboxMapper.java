package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.RuleExecutionPersistenceOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RuleExecutionPersistenceOutboxMapper extends BaseMapper<RuleExecutionPersistenceOutbox> {
    @Update("UPDATE rule_execution_persistence_outbox "
            + "SET delivery_status = 'PROCESSING', claim_token = #{claimToken}, "
            + "lease_until = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL #{leaseSeconds} SECOND), update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND delivery_status = 'PENDING' "
            + "AND (next_retry_time IS NULL OR next_retry_time <= CURRENT_TIMESTAMP)")
    int claimPending(@Param("id") Long id, @Param("claimToken") String claimToken,
                     @Param("leaseSeconds") int leaseSeconds);

    @Update("UPDATE rule_execution_persistence_outbox "
            + "SET delivery_status = 'PENDING', claim_token = NULL, lease_until = NULL, update_time = CURRENT_TIMESTAMP "
            + "WHERE delivery_status = 'PROCESSING' "
            + "AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP)")
    int releaseExpiredClaims(@Param("leaseSeconds") int leaseSeconds);

    @Update("UPDATE rule_execution_persistence_outbox "
            + "SET log_pending = #{logPending}, billing_pending = #{billingPending}, "
            + "delivery_status = #{deliveryStatus}, retry_count = #{retryCount}, "
            + "next_retry_time = #{nextRetryTime}, last_error = #{lastError}, "
            + "delivered_time = #{deliveredTime}, claim_token = NULL, lease_until = NULL, "
            + "update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND delivery_status = 'PROCESSING' "
            + "AND claim_token = #{claimToken} AND lease_until > CURRENT_TIMESTAMP")
    int updateClaimed(RuleExecutionPersistenceOutbox row);
}
