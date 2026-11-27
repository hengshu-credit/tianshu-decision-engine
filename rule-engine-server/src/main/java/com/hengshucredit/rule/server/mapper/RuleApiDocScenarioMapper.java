package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.RuleApiDocScenario;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RuleApiDocScenarioMapper extends BaseMapper<RuleApiDocScenario> {
    record EnabledCount(Long definitionId, long caseCount) {}

    @Select("""
            <script>
            SELECT definition_id, COUNT(*) AS case_count FROM rule_api_doc_scenario
            WHERE status = 1 AND definition_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY definition_id
            </script>
            """)
    List<EnabledCount> countEnabledByDefinition(@Param("ids") List<Long> ids);
}
