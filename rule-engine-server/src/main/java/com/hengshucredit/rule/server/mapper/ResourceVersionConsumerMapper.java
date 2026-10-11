package com.hengshucredit.rule.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

/** Configuration only: never load model binaries or execution logs for dependency checks. */
@Mapper
public interface ResourceVersionConsumerMapper {
    @Select("""
            SELECT 'VARIABLE' resourceType, id resourceId, CAST(JSON_OBJECT('sourceConfig',source_config) AS CHAR) snapshotJson, '当前配置' sourceLabel
              FROM rule_variable WHERE status != -1
            UNION ALL SELECT 'DATA_OBJECT',id,CAST(JSON_OBJECT('sourceContent',source_content) AS CHAR),'当前配置'
              FROM rule_data_object WHERE status != -1
            UNION ALL SELECT 'RULE',c.definition_id,CAST(JSON_OBJECT('modelJson',c.model_json) AS CHAR),'当前设计'
              FROM rule_definition_content c JOIN rule_definition d ON d.id=c.definition_id WHERE d.status != -1
            UNION ALL SELECT 'RULE',p.definition_id,CAST(JSON_OBJECT('modelJson',p.model_json) AS CHAR),'当前生产'
              FROM rule_published p JOIN rule_definition d ON d.id=p.definition_id WHERE d.status != -1 AND p.status=1
            UNION ALL SELECT 'RULE',r.definition_id,CAST(JSON_OBJECT('modelJson',r.model_json) AS CHAR),CONCAT('设计修订 ',r.revision_no)
              FROM rule_revision r JOIN rule_definition d ON d.id=r.definition_id
              WHERE d.status != -1 AND r.state IN ('DRAFT','REVIEW','APPROVED')
            UNION ALL SELECT 'MODEL',i.model_id,CAST(JSON_OBJECT('sourceOperand',i.source_operand,'defaultOperand',i.default_operand) AS CHAR),'当前入参'
              FROM rule_model_input_field i JOIN rule_model m ON m.id=i.model_id WHERE m.status != -1
            UNION ALL SELECT 'MODEL',id,CAST(JSON_OBJECT('modelConfig',model_config) AS CHAR),'当前配置'
              FROM rule_model WHERE status != -1
            UNION ALL SELECT 'FUNCTION',id,CAST(JSON_OBJECT('implType',impl_type,'implScript',impl_script) AS CHAR),'当前配置'
              FROM rule_function WHERE status != -1
            UNION ALL SELECT 'EXTERNAL_API',id,CAST(JSON_OBJECT('executionConfig',execution_config,'requestMapping',request_mapping,
              'responseMapping',response_mapping,'requestScript',request_script,'responseScript',response_script,
              'successCondition',success_condition,'exceptionCondition',exception_condition,'asyncPollConfig',async_poll_config,
              'asyncCallbackConfig',async_callback_config,'authApiConfig',auth_api_config) AS CHAR),'当前配置'
              FROM rule_external_api_config WHERE status != -1
            UNION ALL SELECT 'EXTERNAL_DATASOURCE',id,CAST(JSON_OBJECT('authConfig',auth_config) AS CHAR),'当前配置'
              FROM rule_external_datasource WHERE status != -1
            UNION ALL SELECT 'EXPERIMENT',g.experiment_id,CAST(JSON_OBJECT('ruleId',g.rule_id,'versionMode',g.version_mode,
              'versionBindingId',g.version_binding_id,'conditionConfig',g.condition_config,'conditionExpression',g.condition_expression) AS CHAR),'当前分组'
              FROM rule_experiment_group g JOIN rule_experiment e ON e.id=g.experiment_id WHERE e.status != -1
            """)
    List<Map<String, Object>> currentSnapshots();

    @Select("""
            SELECT v.resource_type resourceType,v.resource_id resourceId,v.snapshot_json snapshotJson,
              CONCAT('业务 v',COALESCE(v.business_version,v.version_no)) sourceLabel,
              COALESCE(v.business_version,v.version_no) businessVersion
            FROM governed_resource_version v JOIN governed_resource g ON g.id=v.governed_resource_id
            WHERE g.effective_status != 'DELETED' AND COALESCE(v.deleted,0)=0 AND v.effective_status IN ('ACTIVE','PUBLISHED','ENABLED','1')
              AND NOT EXISTS (SELECT 1 FROM governed_resource_version n WHERE n.governed_resource_id=v.governed_resource_id
                AND COALESCE(n.business_version,n.version_no)=COALESCE(v.business_version,v.version_no) AND n.version_no>v.version_no)
            UNION ALL SELECT r.resource_type,r.resource_id,CASE WHEN r.status='PENDING' THEN COALESCE(r.submitted_snapshot_json,r.draft_snapshot_json) ELSE r.draft_snapshot_json END,
              CONCAT('审批 ',r.request_no),NULL
            FROM governance_approval_request r WHERE r.status IN ('EDITING','PENDING','CONFLICT','REJECTED')
              AND r.action NOT IN ('DELETE','DISABLE') AND NOT EXISTS (SELECT 1 FROM governed_resource g
                WHERE g.resource_type=r.resource_type AND g.resource_id=r.resource_id AND g.effective_status='DELETED')
            """)
    List<Map<String, Object>> governedSnapshots();

    @Select("""
            SELECT 'RULE' resourceType,v.definition_id resourceId,CAST(JSON_OBJECT('modelJson',v.model_json) AS CHAR) snapshotJson,
              CONCAT('规则业务 v',b.version_no) sourceLabel,b.version_no businessVersion,v.id nativeId,'RULE_BINDING' nativeSource
            FROM rule_version_binding b JOIN rule_definition_version v ON v.id=b.snapshot_id
              JOIN rule_definition d ON d.id=v.definition_id WHERE b.status=1 AND d.status != -1
            UNION ALL SELECT 'FUNCTION',v.function_id,v.function_json,CONCAT('函数历史 ',v.version),v.business_version,v.id,'FUNCTION_HISTORY'
            FROM rule_function_version v JOIN rule_function f ON f.id=v.function_id WHERE f.status != -1
            UNION ALL SELECT 'MODEL',v.model_id,v.execution_snapshot_json,CONCAT('模型历史 ',v.version),v.business_version,v.id,'SNAPSHOT'
            FROM rule_model_version v JOIN rule_model m ON m.id=v.model_id WHERE m.status != -1 AND v.status=1 AND v.execution_snapshot_json IS NOT NULL
            """)
    List<Map<String, Object>> nativeSnapshots();
}
