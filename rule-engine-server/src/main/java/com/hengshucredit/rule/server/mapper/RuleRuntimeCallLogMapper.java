package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.hengshucredit.rule.server.search.ElasticLogClient.Ref;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RuleRuntimeCallLogMapper extends BaseMapper<RuleRuntimeCallLog> {
    @Select("SELECT id,create_time AS time,es_revision AS revision FROM rule_runtime_call_log ${ew.customSqlSegment}")
    IPage<Ref> selectSearchPage(
            IPage<?> page,
            @Param(Constants.WRAPPER)
            Wrapper<RuleRuntimeCallLog> wrapper);
}
