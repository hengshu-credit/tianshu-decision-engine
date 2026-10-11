package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.GovernanceDependencySnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GovernanceDependencySnapshotMapper
        extends BaseMapper<GovernanceDependencySnapshot> {
    @Select("""
            SELECT DISTINCT r.request_no
            FROM governance_dependency_snapshot d
            JOIN governance_approval_request r ON r.id = d.request_id
            WHERE d.target_resource_type = #{type} AND d.target_resource_id = #{id}
              AND (r.status IN ('EDITING', 'PENDING') OR EXISTS (
                SELECT 1 FROM governed_resource g WHERE g.effective_version_id = d.version_id
                  AND g.effective_status != 'DELETED'))
              AND r.action NOT IN ('DELETE', 'DISABLE')
              AND NOT (r.resource_type = #{type} AND r.resource_id = #{id})
            ORDER BY r.request_no LIMIT 5
            """)
    java.util.List<String> activeConsumers(@Param("type") String type, @Param("id") Long id);
}
