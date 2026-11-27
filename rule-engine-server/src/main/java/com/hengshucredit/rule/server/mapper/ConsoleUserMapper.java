package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.ConsoleUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ConsoleUserMapper extends BaseMapper<ConsoleUser> {
    @Select("""
            SELECT password_hash FROM console_user WHERE id=#{id} AND status=1 AND merged_into_user_id IS NULL
              AND (registration_status IS NULL OR registration_status='ACTIVE')
              AND permission_version=#{version}
            """)
    String activeLoginHash(@Param("id") Long id, @Param("version") Long version);

    @Insert("""
            INSERT INTO console_user_login_activity(user_id,last_login_time) VALUES(#{id},#{time})
            ON DUPLICATE KEY UPDATE last_login_time=GREATEST(last_login_time,#{time})
            """)
    void recordLoginTime(@Param("id") Long id, @Param("time") LocalDateTime time);

    @Select("SELECT config_json FROM rule_system_setting WHERE id=3")
    String accessSettingsJson();

    // 在登录事务的账户安全锁内递增序号，同一次成功登录只签发一个序号。
    @Insert("""
            INSERT INTO console_user_session_state(user_id, latest_sequence, minimum_sequence)
            VALUES(#{id}, 1, CASE WHEN #{single} THEN 1 ELSE 0 END)
            ON DUPLICATE KEY UPDATE
              minimum_sequence=CASE WHEN #{single} THEN latest_sequence+1 ELSE minimum_sequence END,
              latest_sequence=latest_sequence+1
            """)
    void advanceLoginSequence(@Param("id") Long id, @Param("single") boolean single);

    record SessionRange(long latestSequence, long minimumSequence) {
        public static final SessionRange LEGACY = new SessionRange(0, 0);
    }

    @Select("SELECT latest_sequence, minimum_sequence FROM console_user_session_state WHERE user_id=#{id}")
    SessionRange sessionRange(Long id);

    record LoginActivity(Long userId, LocalDateTime lastLoginTime) {}

    @Select("""
            <script>SELECT user_id, last_login_time FROM console_user_login_activity WHERE user_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach></script>
            """)
    List<LoginActivity> loginActivity(@Param("ids") List<Long> ids);

    @Select("SELECT id FROM console_security_lock WHERE id = 1 FOR UPDATE")
    Integer lockSecurity();

    @Select("SELECT initialized_at FROM console_security_lock WHERE id = 1")
    java.time.LocalDateTime initializedAt();

    @Select("SELECT administrator_user_id FROM console_security_lock WHERE id = 1")
    Long administratorUserId();

    @org.apache.ibatis.annotations.Update("UPDATE console_security_lock SET initialized_at=CURRENT_TIMESTAMP, administrator_user_id=#{userId} WHERE id=1 AND initialized_at IS NULL")
    int completeInitialization(@org.apache.ibatis.annotations.Param("userId") Long userId);

    @Select("""
            SELECT COALESCE(related_user.operator_name,related_user.username) FROM console_user canonical_user
            JOIN console_user related_user ON related_user.id=canonical_user.id OR related_user.merged_into_user_id=canonical_user.id
            WHERE COALESCE(canonical_user.operator_name,canonical_user.username)=#{actor} AND canonical_user.merged_into_user_id IS NULL
            """)
    java.util.List<String> operatorNames(@org.apache.ibatis.annotations.Param("actor") String actor);
}
