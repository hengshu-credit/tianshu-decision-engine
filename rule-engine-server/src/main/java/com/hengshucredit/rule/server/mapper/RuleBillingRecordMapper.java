package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.RuleBillingRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RuleBillingRecordMapper extends BaseMapper<RuleBillingRecord> {
    /** 根据数据库当前attempt原子更新全部执行事实，不依赖此前SELECT看到的版本。 */
    @Update("UPDATE rule_billing_record SET project_id=#{row.projectId}, project_code=#{row.projectCode}, "
            + "billing_config_id=#{row.billingConfigId}, billing_basis=#{row.billingBasis}, "
            + "billing_code=#{row.billingCode}, billing_name=#{row.billingName}, billing_target=#{row.billingTarget}, "
            + "target_ref_id=#{row.targetRefId}, root_trace_id=#{row.rootTraceId}, attempt_no=#{row.attemptNo}, "
            + "request_id=#{row.requestId}, rule_code=#{row.ruleCode}, rule_id=#{row.ruleId}, rule_version=#{row.ruleVersion}, revision_id=#{row.revisionId}, artifact_digest=#{row.artifactDigest}, api_code=#{row.apiCode}, datasource_code=#{row.datasourceCode}, "
            + "auth_id=#{row.authId}, auth_code=#{row.authCode}, auth_type=#{row.authType}, token_id=#{row.tokenId}, "
            + "token_code=#{row.tokenCode}, auth_phase=#{row.authPhase}, success=#{row.success}, "
            + "quantity=#{row.quantity}, unit_price=#{row.unitPrice}, amount=#{row.amount}, currency=#{row.currency}, "
            + "cost_time_ms=#{row.costTimeMs}, error_message=#{row.errorMessage}, occur_time=#{row.occurTime} "
            + "WHERE id=#{row.id} AND billing_dedup_key=#{row.billingDedupKey} "
            + "AND COALESCE(attempt_no,1) <= COALESCE(#{row.attemptNo},1)")
    int updateIfNoNewerAttempt(@Param("row") RuleBillingRecord record);

    /**
     * UPDATE返回0既可能是较旧attempt，也可能是useAffectedRows=true下完全相同的重放。
     * current-read避开RR旧视图；按实际列精度比较，并以二进制比较字符串以保留大小写与空格。
     */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT COALESCE(attempt_no,1) AS attemptNo, ("
            + "project_id <=> #{row.projectId} "
            + "AND CAST(project_code AS BINARY) <=> CAST(#{row.projectCode} AS BINARY) "
            + "AND billing_config_id <=> #{row.billingConfigId} "
            + "AND CAST(billing_basis AS BINARY) <=> CAST(#{row.billingBasis} AS BINARY) "
            + "AND CAST(billing_code AS BINARY) <=> CAST(#{row.billingCode} AS BINARY) "
            + "AND CAST(billing_name AS BINARY) <=> CAST(#{row.billingName} AS BINARY) "
            + "AND CAST(billing_target AS BINARY) <=> CAST(#{row.billingTarget} AS BINARY) "
            + "AND target_ref_id <=> #{row.targetRefId} "
            + "AND CAST(root_trace_id AS BINARY) <=> CAST(#{row.rootTraceId} AS BINARY) "
            + "AND CAST(request_id AS BINARY) <=> CAST(#{row.requestId} AS BINARY) "
            + "AND CAST(rule_code AS BINARY) <=> CAST(#{row.ruleCode} AS BINARY) "
            + "AND rule_id <=> #{row.ruleId} AND rule_version <=> #{row.ruleVersion} AND revision_id <=> #{row.revisionId} "
            + "AND CAST(artifact_digest AS BINARY) <=> CAST(#{row.artifactDigest} AS BINARY) "
            + "AND CAST(api_code AS BINARY) <=> CAST(#{row.apiCode} AS BINARY) "
            + "AND CAST(datasource_code AS BINARY) <=> CAST(#{row.datasourceCode} AS BINARY) "
            + "AND auth_id <=> #{row.authId} "
            + "AND CAST(auth_code AS BINARY) <=> CAST(#{row.authCode} AS BINARY) "
            + "AND CAST(auth_type AS BINARY) <=> CAST(#{row.authType} AS BINARY) "
            + "AND token_id <=> #{row.tokenId} "
            + "AND CAST(token_code AS BINARY) <=> CAST(#{row.tokenCode} AS BINARY) "
            + "AND CAST(auth_phase AS BINARY) <=> CAST(#{row.authPhase} AS BINARY) "
            + "AND success <=> #{row.success} "
            + "AND quantity <=> CAST(#{row.quantity} AS DECIMAL(18,6)) "
            + "AND unit_price <=> CAST(#{row.unitPrice} AS DECIMAL(18,6)) "
            + "AND amount <=> CAST(#{row.amount} AS DECIMAL(18,6)) "
            + "AND CAST(currency AS BINARY) <=> CAST(#{row.currency} AS BINARY) "
            + "AND cost_time_ms <=> #{row.costTimeMs} "
            + "AND CAST(error_message AS BINARY) <=> CAST(#{row.errorMessage} AS BINARY) "
            + "AND occur_time <=> CAST(#{row.occurTime} AS DATETIME)) AS matchesFacts "
            + "FROM rule_billing_record WHERE id=#{row.id} AND billing_dedup_key=#{row.billingDedupKey} FOR UPDATE")
    WriteState selectWriteState(@Param("row") RuleBillingRecord record);

    record WriteState(int attemptNo, boolean matchesFacts) { }
}
