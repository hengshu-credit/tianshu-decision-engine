package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.GovernedResource;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GovernedResourceMapper extends BaseMapper<GovernedResource> {
    /** 所属项目引用只需治理状态，不授予项目配置读取权。 */
    @org.apache.ibatis.annotations.Select("SELECT id,resource_type,resource_id,project_id,effective_version_id,effective_version_no,effective_status FROM governed_resource WHERE resource_type='PROJECT' AND resource_id=#{id}")
    GovernedResource selectProjectReferenceMetadata(@org.apache.ibatis.annotations.Param("id") Long id);
    /** 只补齐既有资源的首次版本指针，不能更新已有版本或业务配置。 */
    @org.apache.ibatis.annotations.Update("UPDATE governed_resource SET effective_version_id=#{versionId}, effective_version_no=#{versionNo} WHERE id=#{id} AND effective_version_id IS NULL AND effective_version_no=0")
    int linkBaselineVersion(@org.apache.ibatis.annotations.Param("id") Long id, @org.apache.ibatis.annotations.Param("versionId") Long versionId, @org.apache.ibatis.annotations.Param("versionNo") Integer versionNo);
}
