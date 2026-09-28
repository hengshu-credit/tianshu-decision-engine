package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.RulePublishOutbox;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RulePublishOutboxMapper extends BaseMapper<RulePublishOutbox> {

    /**
     * 抢占一条待投递消息。多个 server 实例同时轮询时只有一个实例能成功把状态改成
     * DELIVERING，避免同一 outbox 记录被重复发送。
     */
    @Update("UPDATE rule_engine.rule_publish_outbox " +
            "SET delivery_status = 'DELIVERING', claim_token = #{claimToken}, " +
            "lease_until = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL #{leaseSeconds} SECOND), " +
            "update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND delivery_status = 'PENDING' " +
            "AND (next_retry_time IS NULL OR next_retry_time <= CURRENT_TIMESTAMP)")
    int claimPending(@Param("id") Long id, @Param("claimToken") String claimToken,
                     @Param("leaseSeconds") int leaseSeconds);

    /**
     * 进程在发送 Redis 前崩溃时释放遗留租约，下一轮轮询即可恢复投递。
     */
    @Update("UPDATE rule_engine.rule_publish_outbox " +
            "SET delivery_status = 'PENDING', claim_token = NULL, lease_until = NULL, " +
            "update_time = CURRENT_TIMESTAMP " +
            "WHERE delivery_status = 'DELIVERING' " +
            "AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP)")
    int releaseExpiredClaims(@Param("leaseSeconds") int leaseSeconds);

    @Update("UPDATE rule_engine.rule_publish_outbox " +
            "SET lease_until = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL #{leaseSeconds} SECOND), " +
            "update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND delivery_status = 'DELIVERING' AND claim_token = #{claimToken} " +
            "AND lease_until > CURRENT_TIMESTAMP")
    int renewClaim(@Param("id") Long id, @Param("claimToken") String claimToken,
                   @Param("leaseSeconds") int leaseSeconds);

    /**
     * 只允许当前持有 DELIVERING 租约的实例写回结果，防止过期实例覆盖后来实例的状态。
     */
    @Update("UPDATE rule_engine.rule_publish_outbox " +
            "SET delivery_status = #{deliveryStatus}, retry_count = #{retryCount}, " +
            "next_retry_time = #{nextRetryTime}, last_error = #{lastError}, " +
            "delivered_time = #{deliveredTime}, dead_letter_time = #{deadLetterTime}, " +
            "claim_token = NULL, lease_until = NULL, update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND delivery_status = 'DELIVERING' AND claim_token = #{claimToken} " +
            "AND lease_until > CURRENT_TIMESTAMP")
    int updateClaimed(RulePublishOutbox outbox);

    @Select("SELECT COUNT(*) FROM rule_engine.rule_publish_outbox WHERE delivery_status = #{status}")
    long countByStatus(@Param("status") String status);

    @Select("SELECT COUNT(*) FROM rule_engine.rule_publish_outbox WHERE delivery_status = 'PENDING' AND retry_count > 0")
    long countPendingRetries();

    @Select("SELECT MIN(create_time) FROM rule_engine.rule_publish_outbox WHERE delivery_status IN ('PENDING','DELIVERING')")
    java.time.LocalDateTime oldestActiveTime();
}
