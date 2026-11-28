package com.hengshucredit.rule.server.mapper;

import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.openapi.PublishedOpenApiContract;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RulePublishedMapper extends BaseMapper<RulePublished> {

    @Update("UPDATE rule_published SET open_api_config_json = #{openApiConfigJson} "
            + "WHERE definition_id = #{definitionId}")
    int updateOpenApiConfigByDefinitionId(@Param("definitionId") Long definitionId,
                                          @Param("openApiConfigJson") String openApiConfigJson);

    String GLOBAL_SCOPE = "COALESCE(d.project_id,0)=0 AND d.scope='GLOBAL'";
    String GLOBAL_DEFINITION = "SELECT d.id FROM rule_definition d WHERE " + GLOBAL_SCOPE;
    String PROJECT_OR_LINKED_GLOBAL = "SELECT d.id FROM rule_definition d WHERE d.project_id = {0} OR ("
            + GLOBAL_SCOPE + " AND EXISTS (SELECT 1 FROM rule_definition_ref r WHERE r.definition_id=d.id AND r.project_id={0}))";

    static LambdaQueryWrapper<RulePublished> restrictToProject(
            LambdaQueryWrapper<RulePublished> query, Long projectId) {
        if (projectId == null || projectId <= 0) return query.apply("1=0");
        return query.apply("definition_id IN (" + PROJECT_OR_LINKED_GLOBAL + ")", projectId);
    }

    @Select("SELECT p.* FROM rule_published p WHERE p.rule_code = #{ruleCode} AND p.status = 1 "
            + "AND p.definition_id IN (" + GLOBAL_DEFINITION + ") LIMIT 1")
    PublishedOpenApiContract selectGlobalOpenApiContract(@Param("ruleCode") String ruleCode);

    @Select("SELECT p.* "
            + "FROM rule_published p WHERE p.rule_code = #{ruleCode} AND p.status = 1 "
            + "AND p.definition_id IN (SELECT d.id FROM rule_definition d WHERE d.project_id = #{projectId} OR ("
            + GLOBAL_SCOPE + " AND EXISTS (SELECT 1 FROM rule_definition_ref r "
            + "WHERE r.definition_id = d.id AND r.project_id = #{projectId}))) LIMIT 1")
    PublishedOpenApiContract selectOpenApiContract(@Param("projectId") Long projectId,
                                                   @Param("ruleCode") String ruleCode);
}
