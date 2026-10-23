package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.ConsoleUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConsoleUserMapper extends BaseMapper<ConsoleUser> {
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
