package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.RuleBillingSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
public interface RuleBillingSummaryMapper extends BaseMapper<RuleBillingSummary> {
    @Insert("INSERT INTO rule_billing_summary_lock (summary_date) VALUES (#{date}) "
            + "ON DUPLICATE KEY UPDATE summary_date=rule_billing_summary_lock.summary_date")
    void lockDate(@Param("date") LocalDate date);

    /** 单条SQL读取一致快照；汇总留在数据库中，避免将全天明细装入Java堆。 */
    @Options(timeout = 120)
    @Insert("""
            INSERT INTO rule_billing_summary
              (summary_date, project_id, project_code, billing_code, billing_target, target_ref_id,
               auth_id, auth_code, auth_type, currency, total_count, success_count, fail_count,
               total_quantity, total_amount, avg_cost_time_ms)
            SELECT #{date}, grouped.project_id, grouped.project_code, grouped.billing_code,
                   grouped.billing_target, grouped.target_ref_id, grouped.auth_id,
                   first_record.auth_code, first_record.auth_type, grouped.currency,
                   grouped.total_count, grouped.success_count, grouped.fail_count,
                   grouped.total_quantity, grouped.total_amount, grouped.avg_cost_time_ms
            FROM (
              SELECT project_id, CAST(project_code AS BINARY) AS project_code,
                     CAST(billing_code AS BINARY) AS billing_code,
                     CAST(billing_target AS BINARY) AS billing_target, target_ref_id, auth_id,
                     CAST(currency AS BINARY) AS currency, MIN(id) AS first_id,
                     COUNT(*) AS total_count, SUM(CASE WHEN success=1 THEN 1 ELSE 0 END) AS success_count,
                     SUM(CASE WHEN success=1 THEN 0 ELSE 1 END) AS fail_count,
                     SUM(COALESCE(quantity,0)) AS total_quantity, SUM(COALESCE(amount,0)) AS total_amount,
                     ROUND(SUM(COALESCE(cost_time_ms,0))/COUNT(*),2) AS avg_cost_time_ms
              FROM rule_billing_record
              WHERE occur_time >= #{begin} AND occur_time < #{end}
              GROUP BY project_id, CAST(project_code AS BINARY), CAST(billing_code AS BINARY),
                       CAST(billing_target AS BINARY), target_ref_id, auth_id, CAST(currency AS BINARY)
            ) grouped
            JOIN rule_billing_record first_record ON first_record.id=grouped.first_id
            """)
    int aggregateDate(@Param("date") LocalDate date, @Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);
}
