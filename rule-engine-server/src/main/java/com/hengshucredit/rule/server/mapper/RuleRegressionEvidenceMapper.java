package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.RuleRegressionEvidence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import java.util.List;

@Mapper
public interface RuleRegressionEvidenceMapper extends BaseMapper<RuleRegressionEvidence> {
    @Select("""
            SELECT e.id, e.definition_id, e.scenario_id, e.revision_id, e.content_digest, e.dependency_digest,
              e.scenario_digest, e.eligible, e.state, e.success, e.validation_status FROM rule_regression_evidence e
            JOIN rule_regression_evidence_latest latest ON latest.evidence_id = e.id
            WHERE latest.definition_id = #{definitionId} AND latest.revision_id = #{revisionId}
            FOR UPDATE
            """)
    List<RuleRegressionEvidence> latestByCase(@Param("definitionId") Long definitionId, @Param("revisionId") Long revisionId);

    @Insert("""
            INSERT INTO rule_regression_evidence_latest (definition_id,revision_id,scenario_id,evidence_id)
            VALUES (#{definitionId},#{revisionId},#{scenarioId},#{evidenceId})
            ON DUPLICATE KEY UPDATE evidence_id = #{evidenceId}
            """)
    int pinLatest(@Param("definitionId") Long definitionId, @Param("revisionId") Long revisionId,
                  @Param("scenarioId") Long scenarioId, @Param("evidenceId") Long evidenceId);
}
