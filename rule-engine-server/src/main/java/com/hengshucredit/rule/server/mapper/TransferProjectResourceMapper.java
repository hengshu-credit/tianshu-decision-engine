package com.hengshucredit.rule.server.mapper;

import com.hengshucredit.rule.server.transfer.TransferRootRequest;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 项目迁移从业务表读取成员 ID，包含未发布配置，不依赖治理版本是否已初始化。 */
public interface TransferProjectResourceMapper {
    @Select("""
            SELECT 'RULE' AS resource_type, r.id AS resource_id FROM rule_definition r
            WHERE r.status != -1 AND (r.project_id = #{projectId} OR EXISTS (
                SELECT 1 FROM rule_definition_ref ref
                WHERE ref.definition_id = r.id AND ref.project_id = #{projectId}))
            UNION ALL
            SELECT 'VARIABLE', id FROM rule_variable
            WHERE project_id = #{projectId} AND scope = 'PROJECT' AND status != -1
            UNION ALL
            SELECT 'DATA_OBJECT', id FROM rule_data_object
            WHERE project_id = #{projectId} AND scope = 'PROJECT' AND status != -1
            UNION ALL
            SELECT 'FUNCTION', id FROM rule_function
            WHERE project_id = #{projectId} AND scope = 'PROJECT' AND status != -1
            UNION ALL
            SELECT 'MODEL', id FROM rule_model
            WHERE project_id = #{projectId} AND scope = 'PROJECT' AND status != -1
            UNION ALL
            SELECT 'DATABASE', id FROM rule_db_datasource
            WHERE project_id = #{projectId} AND scope = 'PROJECT' AND status != -1
            UNION ALL
            SELECT 'EXTERNAL_DATASOURCE', id FROM rule_external_datasource
            WHERE project_id = #{projectId} AND scope = 'PROJECT' AND status != -1
            UNION ALL
            SELECT 'EXTERNAL_API', api.id FROM rule_external_api_config api
            JOIN rule_external_datasource ds ON ds.id = api.datasource_id
            WHERE ds.project_id = #{projectId} AND ds.scope = 'PROJECT'
                AND ds.status != -1 AND api.status != -1
            UNION ALL
            SELECT 'LIST_LIBRARY', id FROM rule_list_library
            WHERE project_id = #{projectId} AND scope = 'PROJECT' AND status != -1
            UNION ALL
            SELECT 'EXPERIMENT', id FROM rule_experiment
            WHERE project_id = #{projectId} AND status != -1
            UNION ALL
            SELECT 'FIELD_VALIDATION', id FROM rule_field_validation
            WHERE project_id = #{projectId} AND scope = 'PROJECT' AND status != -1
            ORDER BY resource_type, resource_id
            """)
    @ConstructorArgs({
            @Arg(column = "resource_type", javaType = String.class),
            @Arg(column = "resource_id", javaType = Long.class)
    })
    List<TransferRootRequest> selectProjectResources(@Param("projectId") Long projectId);
}
