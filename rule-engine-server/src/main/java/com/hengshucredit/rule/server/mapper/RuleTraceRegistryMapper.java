package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.RuleTraceRegistry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RuleTraceRegistryMapper extends BaseMapper<RuleTraceRegistry> {
    @Select("SELECT * FROM rule_trace_registry WHERE trace_id = #{traceId} FOR UPDATE")
    RuleTraceRegistry selectForUpdate(@Param("traceId") String traceId);
}
