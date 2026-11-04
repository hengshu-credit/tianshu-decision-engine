package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.ConsoleOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import java.time.LocalDateTime;

@Mapper
public interface ConsoleOperationLogMapper extends BaseMapper<ConsoleOperationLog> {
    /** 单账户锁由追加事务持有，多实例同时完成请求时也只追加一个区间。 */
    @Insert("INSERT INTO console_operation_log_lock(user_id) VALUES(#{userId}) "
            + "ON DUPLICATE KEY UPDATE user_id=console_operation_log_lock.user_id")
    void lockAppend(@Param("userId") Long userId);

    // 仅供可信审计写入链路使用；必须显式约束账户。列表和详情仍使用正常权限过滤。
    @InterceptorIgnore(dataPermission = "true")
    @Select("""
            SELECT id,user_id,username,module,action,request_method,request_path,page_path,
                   target_type,target_id,target_name,target_version,outcome,result_code,
                   change_summary,client_ip,duration_ms,create_time,leave_time
            FROM console_operation_log WHERE user_id=#{userId} ORDER BY id DESC LIMIT 1
            """)
    ConsoleOperationLog latestForAppend(@Param("userId") Long userId);

    @InterceptorIgnore(dataPermission = "true")
    @Update("""
            UPDATE console_operation_log
            SET create_time=LEAST(create_time,#{enterTime}),
                leave_time=GREATEST(COALESCE(leave_time,create_time),#{leaveTime}),
                duration_ms=COALESCE(duration_ms,0)+#{durationMs}
            WHERE id=#{id} AND user_id=#{userId}
            """)
    int extendForAppend(@Param("id") Long id, @Param("userId") Long userId,
                        @Param("enterTime") LocalDateTime enterTime,
                        @Param("leaveTime") LocalDateTime leaveTime,
                        @Param("durationMs") Long durationMs);
}
