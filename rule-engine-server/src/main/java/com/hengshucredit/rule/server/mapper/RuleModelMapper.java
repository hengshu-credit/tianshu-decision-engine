package com.hengshucredit.rule.server.mapper;

import com.hengshucredit.rule.model.entity.RuleModel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RuleModelMapper extends BaseMapper<RuleModel> {
    @org.apache.ibatis.annotations.Update("UPDATE rule_model m JOIN rule_model_version v ON v.id=#{versionId} AND v.model_id=m.id SET m.model_content=v.model_content WHERE m.id=#{modelId}")
    int restoreVersionContent(@org.apache.ibatis.annotations.Param("modelId") Long modelId,
                              @org.apache.ibatis.annotations.Param("versionId") Long versionId);
}
