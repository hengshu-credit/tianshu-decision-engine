package com.hengshucredit.rule.server.mapper;

import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Arg;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RuleExecutionLogMapper extends BaseMapper<RuleExecutionLog> {
    record LogicalIdentity(String traceId, Long logId, LocalDateTime logCreateTime) { }

    // 必须在调用方事务内执行；唯一 trace 行的锁持续到日志和指针一起提交。
    @Insert("INSERT INTO rule_execution_log_identity (trace_id) VALUES (#{traceId}) "
            + "ON DUPLICATE KEY UPDATE trace_id = rule_execution_log_identity.trace_id")
    int lockLogicalIdentity(@Param("traceId") String traceId);

    @Select("SELECT trace_id, log_id, log_create_time FROM rule_execution_log_identity WHERE trace_id = #{traceId} FOR UPDATE")
    @ConstructorArgs({@Arg(column = "trace_id", javaType = String.class), @Arg(column = "log_id", javaType = Long.class),
            @Arg(column = "log_create_time", javaType = LocalDateTime.class)})
    LogicalIdentity selectLogicalIdentity(@Param("traceId") String traceId);

    @Select("SELECT * FROM rule_execution_log WHERE id = #{id} AND create_time = #{createTime} FOR UPDATE")
    RuleExecutionLog selectLogicalRecord(@Param("id") Long id, @Param("createTime") LocalDateTime createTime);

    @Select("SELECT * FROM rule_execution_log WHERE trace_id = #{traceId} "
            + "ORDER BY COALESCE(attempt_no, 1) DESC, create_time DESC, id DESC LIMIT 1")
    RuleExecutionLog selectLegacyLogicalRecord(@Param("traceId") String traceId);

    @Select("SELECT create_time FROM rule_execution_log WHERE id = #{id} AND trace_id = #{traceId} "
            + "ORDER BY create_time DESC LIMIT 1")
    LocalDateTime selectInsertedCreateTime(@Param("id") Long id, @Param("traceId") String traceId);

    @Select("SELECT create_time FROM rule_execution_log WHERE id=#{id} ORDER BY create_time DESC LIMIT 1")
    LocalDateTime selectPhysicalCreateTime(@Param("id") Long id);

    @Update("UPDATE rule_execution_log_identity SET log_id = #{id}, log_create_time = #{createTime} "
            + "WHERE trace_id = #{traceId} AND log_id IS NULL")
    int bindLogicalIdentity(@Param("traceId") String traceId, @Param("id") Long id,
                            @Param("createTime") LocalDateTime createTime);

    @Select("SELECT * FROM rule_execution_log_current ${ew.customSqlSegment}")
    IPage<RuleExecutionLog> selectLogicalPage(IPage<RuleExecutionLog> page,
                                             @Param(Constants.WRAPPER) Wrapper<RuleExecutionLog> wrapper);

    @Select("SELECT * FROM rule_execution_log_current ${ew.customSqlSegment}")
    List<RuleExecutionLog> selectLogicalList(@Param(Constants.WRAPPER) Wrapper<RuleExecutionLog> wrapper);

    @Select("SELECT COUNT(*) FROM rule_execution_log_current ${ew.customSqlSegment}")
    Long selectLogicalCount(@Param(Constants.WRAPPER) Wrapper<RuleExecutionLog> wrapper);
}
