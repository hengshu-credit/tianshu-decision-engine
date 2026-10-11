package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.entity.RuleBillingRecord;
import com.hengshucredit.rule.model.entity.RuleBillingSummary;
import org.apache.ibatis.annotations.*;

/** Version-filtered totals come from attributed facts; the daily materialized totals remain unchanged. */
@Mapper
public interface RuleBillingVersionSummaryMapper {
    @Options(timeout = 120)
    @Select("""
            SELECT grouped.summary_date, grouped.project_id, grouped.project_code, grouped.billing_code,
                   grouped.billing_target, grouped.target_ref_id, grouped.auth_id,
                   first_record.auth_code, first_record.auth_type, grouped.currency,
                   grouped.total_count, grouped.success_count, grouped.fail_count,
                   grouped.total_quantity, grouped.total_amount, grouped.avg_cost_time_ms
            FROM (
              SELECT DATE(occur_time) summary_date, project_id, CAST(project_code AS BINARY) project_code,
                     CAST(billing_code AS BINARY) billing_code, CAST(billing_target AS BINARY) billing_target,
                     target_ref_id, auth_id, CAST(currency AS BINARY) currency, MIN(id) first_id,
                     COUNT(*) total_count, SUM(CASE WHEN success=1 THEN 1 ELSE 0 END) success_count,
                     SUM(CASE WHEN success=1 THEN 0 ELSE 1 END) fail_count,
                     SUM(COALESCE(quantity,0)) total_quantity, SUM(COALESCE(amount,0)) total_amount,
                     ROUND(SUM(COALESCE(cost_time_ms,0))/COUNT(*),2) avg_cost_time_ms
              FROM rule_billing_record ${ew.customSqlSegment}
              GROUP BY DATE(occur_time), project_id, CAST(project_code AS BINARY), CAST(billing_code AS BINARY),
                       CAST(billing_target AS BINARY), target_ref_id, auth_id, CAST(currency AS BINARY)
            ) grouped JOIN rule_billing_record first_record ON first_record.id=grouped.first_id
            ORDER BY grouped.summary_date DESC, grouped.project_code, grouped.billing_code,
                     grouped.billing_target, grouped.target_ref_id, grouped.auth_id, grouped.currency, grouped.project_id
            """)
    IPage<RuleBillingSummary> selectVersionSummary(IPage<RuleBillingSummary> page, @Param("ew") Wrapper<RuleBillingRecord> filters);
}
