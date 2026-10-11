package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.RuleBillingSummaryTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

@Mapper
public interface RuleBillingSummaryTaskMapper extends BaseMapper<RuleBillingSummaryTask> {
    @Options(timeout = 2)
    @Insert("INSERT INTO rule_billing_summary_task (task_id, summary_date, status, applicant) "
            + "VALUES (#{id}, #{date}, 'PENDING', #{applicant}) "
            + "ON DUPLICATE KEY UPDATE task_id=rule_billing_summary_task.task_id")
    void enqueue(@Param("id") String id, @Param("date") LocalDate date, @Param("applicant") String applicant);

    @Select("SELECT * FROM rule_billing_summary_task WHERE active_date=#{date}")
    RuleBillingSummaryTask active(@Param("date") LocalDate date);

    @Select("SELECT * FROM rule_billing_summary_task WHERE summary_date=#{date} "
            + "ORDER BY requested_at DESC, task_id DESC LIMIT 1")
    RuleBillingSummaryTask latest(@Param("date") LocalDate date);

    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM rule_billing_summary_task WHERE status IN ('PENDING','RUNNING') "
            + "ORDER BY requested_at, task_id LIMIT 1 FOR UPDATE SKIP LOCKED")
    RuleBillingSummaryTask nextUnlocked();

    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM rule_billing_summary_task WHERE task_id=#{id} FOR UPDATE SKIP LOCKED")
    RuleBillingSummaryTask lockTask(@Param("id") String id);

    @Update("UPDATE rule_billing_summary_task SET status='RUNNING', started_at=COALESCE(started_at,CURRENT_TIMESTAMP(6)) "
            + "WHERE task_id=#{id} AND status='PENDING'")
    void start(@Param("id") String id);

    @Update("UPDATE rule_billing_summary_task SET status='SUCCEEDED', summary_count=#{count}, "
            + "record_count=(SELECT COALESCE(SUM(total_count),0) FROM rule_billing_summary WHERE summary_date=#{date}), "
            + "completed_at=CURRENT_TIMESTAMP(6), error_message=NULL WHERE task_id=#{id} AND status='RUNNING'")
    void complete(@Param("id") String id, @Param("date") LocalDate date, @Param("count") int count);

    @Update("UPDATE rule_billing_summary_task SET status='FAILED', completed_at=CURRENT_TIMESTAMP(6), "
            + "error_message=#{message} WHERE task_id=#{id} AND status='RUNNING'")
    void fail(@Param("id") String id, @Param("message") String message);
}
