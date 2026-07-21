package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.RuleProjectAuthToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface RuleProjectAuthTokenMapper extends BaseMapper<RuleProjectAuthToken> {
    @Update("UPDATE rule_engine.rule_project_auth_token SET last_used_time = #{lastUsedTime} WHERE id = #{id}")
    int updateLastUsedTime(@Param("id") Long id, @Param("lastUsedTime") LocalDateTime lastUsedTime);
}
