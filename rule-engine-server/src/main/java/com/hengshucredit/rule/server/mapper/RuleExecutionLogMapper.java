package com.hengshucredit.rule.server.mapper;

import com.hengshucredit.rule.server.search.ElasticLogClient.Ref;
import java.io.Serializable;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
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
    String RESTORED_TRACE = "COALESCE(trace_info,CONVERT(UNCOMPRESS(es_trace_archive) USING utf8mb4))";
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

    @Select("SELECT l.*," + RESTORED_TRACE + " AS stored_trace FROM rule_execution_log_current l ${ew.customSqlSegment}")
    @Results(id = "auditLog",value = @Result(column = "stored_trace",property = "traceInfo"))
    IPage<RuleExecutionLog> selectLogicalPage(IPage<RuleExecutionLog> page,
                                             @Param(Constants.WRAPPER) Wrapper<RuleExecutionLog> wrapper);

    @Select("SELECT id,create_time,trace_id,execution_project_id,root_rule_id,rule_code,project_code,rule_version,"
            + "revision_id,artifact_digest,attempt_no,model_type,source,auth_code,auth_type,token_code,success,"
            + "execute_time_ms,client_app_name,(trace_info IS NOT NULL OR es_trace_archive IS NOT NULL) AS trace_available "
            + "FROM rule_execution_log_current ${ew.customSqlSegment}")
    IPage<RuleExecutionLog> selectSummaryPage(IPage<RuleExecutionLog> page,
                                              @Param(Constants.WRAPPER) Wrapper<RuleExecutionLog> wrapper);

    @Select("SELECT id,create_time AS time,es_revision AS revision FROM rule_execution_log_current ${ew.customSqlSegment}")
    IPage<Ref> selectSearchPage(IPage<?> page,
                                             @Param(Constants.WRAPPER) Wrapper<RuleExecutionLog> wrapper);

    @Select("SELECT id,create_time AS time,es_revision AS revision FROM rule_execution_log WHERE id=#{id}")
    Ref selectAuditIdentity(@Param("id") Serializable id);

    @Select("SELECT * FROM rule_execution_log_current ${ew.customSqlSegment}")
    List<RuleExecutionLog> selectLogicalList(@Param(Constants.WRAPPER) Wrapper<RuleExecutionLog> wrapper);

    @Select("SELECT l.*," + RESTORED_TRACE + " AS stored_trace FROM rule_execution_log l WHERE id=#{id}")
    @ResultMap("auditLog")
    RuleExecutionLog selectAuditLogById(@Param("id") Serializable id);

    @Select("SELECT COUNT(*) FROM rule_execution_log_current ${ew.customSqlSegment}")
    Long selectLogicalCount(@Param(Constants.WRAPPER) Wrapper<RuleExecutionLog> wrapper);
}
