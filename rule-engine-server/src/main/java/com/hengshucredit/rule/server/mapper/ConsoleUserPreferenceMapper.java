package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.ConsoleUserPreference;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConsoleUserPreferenceMapper
        extends BaseMapper<ConsoleUserPreference> {

    @Select("SELECT preference_value "
            + "FROM console_user_preference "
            + "WHERE user_id = #{userId} AND preference_key = #{preferenceKey} "
            + "LIMIT 1")
    String findValue(@Param("userId") Long userId,
                     @Param("preferenceKey") String preferenceKey);

    @Insert("INSERT INTO console_user_preference "
            + "(user_id, preference_key, preference_value, create_by, update_by) "
            + "VALUES (#{userId}, #{preferenceKey}, #{preferenceValue}, "
            + "#{operator}, #{operator}) "
            + "ON DUPLICATE KEY UPDATE "
            + "preference_value = VALUES(preference_value), "
            + "update_by = VALUES(update_by), update_time = CURRENT_TIMESTAMP")
    int upsertValue(@Param("userId") Long userId,
                    @Param("preferenceKey") String preferenceKey,
                    @Param("preferenceValue") String preferenceValue,
                    @Param("operator") String operator);

    @Delete("DELETE FROM console_user_preference "
            + "WHERE user_id = #{userId} AND preference_key = #{preferenceKey}")
    int deleteValue(@Param("userId") Long userId,
                    @Param("preferenceKey") String preferenceKey);
}
