package com.hengshucredit.rule.server.mapper;

import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.openapi.PublishedOpenApiContract;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RulePublishedMapper extends BaseMapper<RulePublished> {

    @Update("UPDATE rule_engine.rule_published SET open_api_config_json = #{openApiConfigJson} "
            + "WHERE definition_id = #{definitionId}")
    int updateOpenApiConfigByDefinitionId(@Param("definitionId") Long definitionId,
                                          @Param("openApiConfigJson") String openApiConfigJson);

    @Select("SELECT definition_id AS definitionId, rule_code AS ruleCode, project_code AS projectCode, "
            + "version, status, open_api_config_json AS openApiConfigJson "
            + "FROM rule_engine.rule_published p WHERE p.rule_code = #{ruleCode} AND p.status = 1 "
            + "AND (p.project_code = #{projectCode} OR EXISTS (SELECT 1 FROM rule_engine.rule_definition_ref r "
            + "WHERE r.definition_id = p.definition_id AND r.project_id = #{projectId})) LIMIT 1")
    PublishedOpenApiContract selectOpenApiContract(@Param("projectId") Long projectId,
                                                   @Param("projectCode") String projectCode,
                                                   @Param("ruleCode") String ruleCode);
}
