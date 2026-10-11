package com.hengshucredit.rule.server.mapper;

import com.hengshucredit.rule.model.entity.RuleModelVersion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RuleModelVersionMapper extends BaseMapper<RuleModelVersion> {
    @Select("SELECT MAX(COALESCE(business_version, version)) FROM rule_model_version WHERE model_id=#{modelId}")
    Integer maxBusinessVersion(@Param("modelId") Long modelId);
}
