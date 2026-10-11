package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GovernedResourceVersionMapper
        extends BaseMapper<GovernedResourceVersion> {
    @Select("SELECT MAX(COALESCE(business_version, version_no)) FROM governed_resource_version WHERE resource_type=#{type} AND resource_id=#{id}")
    Integer maxBusinessVersion(@Param("type") String type, @Param("id") Long id);
}
