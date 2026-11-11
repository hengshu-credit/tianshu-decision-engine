-- MySQL 8。停止业务写入并备份，在一个连接中以遇错即停模式运行。
-- 清单只含已确认的单目标关系。先检查整份清单，再执行会隐式提交的 DDL。
SET NAMES utf8mb4;
DROP TEMPORARY TABLE IF EXISTS ts_er_relations;
CREATE TEMPORARY TABLE ts_er_relations (
  child_table VARCHAR(64) NOT NULL,
  child_column VARCHAR(64) NOT NULL,
  parent_table VARCHAR(64) NOT NULL,
  fk_name VARCHAR(64) NOT NULL PRIMARY KEY,
  delete_rule VARCHAR(16) NOT NULL,
  add_clause TEXT,
  orphan_example BIGINT
);
INSERT INTO ts_er_relations (child_table, child_column, parent_table, fk_name, delete_rule) VALUES
  ('console_user_project', 'project_ref_id', 'rule_project', 'fk_console_user_project_project_ref', 'RESTRICT'),
  ('console_user_project_permission', 'project_ref_id', 'rule_project', 'fk_console_user_project_permission_project_ref', 'RESTRICT'),
  ('rule_definition', 'project_ref_id', 'rule_project', 'fk_definition_project_ref', 'RESTRICT'),
  ('rule_model', 'project_ref_id', 'rule_project', 'fk_model_project_ref', 'RESTRICT'),
  ('rule_function', 'project_ref_id', 'rule_project', 'fk_function_project_ref', 'RESTRICT'),
  ('rule_variable', 'project_ref_id', 'rule_project', 'fk_variable_project_ref', 'RESTRICT'),
  ('rule_data_object', 'project_ref_id', 'rule_project', 'fk_data_object_project_ref', 'RESTRICT'),
  ('rule_field_validation', 'project_ref_id', 'rule_project', 'fk_field_validation_project_ref', 'RESTRICT'),
  ('rule_external_datasource', 'project_ref_id', 'rule_project', 'fk_external_datasource_project_ref', 'RESTRICT'),
  ('rule_db_datasource', 'project_ref_id', 'rule_project', 'fk_db_datasource_project_ref', 'RESTRICT'),
  ('rule_sftp_datasource', 'project_ref_id', 'rule_project', 'fk_sftp_datasource_project_ref', 'RESTRICT'),
  ('rule_list_library', 'project_ref_id', 'rule_project', 'fk_list_library_project_ref', 'RESTRICT'),
  ('rule_experiment', 'project_ref_id', 'rule_project', 'fk_experiment_project_ref', 'RESTRICT'),
  ('rule_billing_config', 'project_ref_id', 'rule_project', 'fk_billing_config_project_ref', 'RESTRICT'),
  ('rule_project_auth', 'project_id', 'rule_project', 'fk_project_auth_project', 'CASCADE'),
  ('rule_project_auth_token', 'auth_id', 'rule_project_auth', 'fk_project_auth_token_auth', 'CASCADE'),
  ('rule_project_auth_token', 'project_id', 'rule_project', 'fk_project_auth_token_project', 'CASCADE'),
  ('console_user', 'merged_into_user_id', 'console_user', 'fk_console_user_merged_into_user', 'RESTRICT'),
  ('console_user_project', 'user_id', 'console_user', 'fk_console_user_project_user', 'RESTRICT'),
  ('console_account_token', 'user_id', 'console_user', 'fk_console_account_token_user', 'RESTRICT'),
  ('console_user_project_permission', 'user_id', 'console_user', 'fk_console_user_project_permission_user', 'RESTRICT'),
  ('console_user_project_permission', 'permission_id', 'console_permission', 'fk_console_user_project_permission_permission', 'RESTRICT'),
  ('console_user_preference', 'user_id', 'console_user', 'fk_console_user_preference_user', 'RESTRICT'),
  ('console_user_role', 'user_id', 'console_user', 'fk_console_user_role_user', 'RESTRICT'),
  ('console_user_role', 'role_id', 'console_role', 'fk_console_user_role_role', 'RESTRICT'),
  ('console_role_permission', 'role_id', 'console_role', 'fk_console_role_permission_role', 'RESTRICT'),
  ('console_role_permission', 'permission_id', 'console_permission', 'fk_console_role_permission_permission', 'RESTRICT'),
  ('console_user_permission_override', 'user_id', 'console_user', 'fk_console_user_permission_override_user', 'RESTRICT'),
  ('console_user_permission_override', 'permission_id', 'console_permission', 'fk_console_user_permission_override_permission', 'RESTRICT'),
  ('governed_resource_version', 'governed_resource_id', 'governed_resource', 'fk_governed_resource_version_governed_resource', 'RESTRICT'),
  ('governance_approval_event', 'request_id', 'governance_approval_request', 'fk_governance_approval_event_request', 'RESTRICT'),
  ('governance_dependency_snapshot', 'request_id', 'governance_approval_request', 'fk_governance_dependency_snapshot_request', 'RESTRICT'),
  ('governance_dependency_snapshot', 'version_id', 'governed_resource_version', 'fk_governance_dependency_snapshot_version', 'RESTRICT'),
  ('rule_definition_input_field', 'definition_id', 'rule_definition', 'fk_definition_input_field_definition', 'CASCADE'),
  ('rule_definition_output_field', 'definition_id', 'rule_definition', 'fk_definition_output_field_definition', 'CASCADE'),
  ('rule_definition_content', 'definition_id', 'rule_definition', 'fk_definition_content_definition', 'CASCADE'),
  ('rule_definition_ref', 'definition_id', 'rule_definition', 'fk_definition_ref_definition', 'CASCADE'),
  ('rule_definition_ref', 'project_id', 'rule_project', 'fk_definition_ref_project', 'CASCADE'),
  ('rule_api_doc_scenario', 'definition_id', 'rule_definition', 'fk_api_doc_scenario_definition', 'CASCADE'),
  ('rule_regression_evidence_latest', 'evidence_id', 'rule_regression_evidence', 'fk_regression_evidence_latest_evidence', 'RESTRICT'),
  ('rule_designer_save_operation', 'definition_id', 'rule_definition', 'fk_designer_save_operation_definition', 'CASCADE'),
  ('rule_published', 'definition_id', 'rule_definition', 'fk_published_definition', 'RESTRICT'),
  ('rule_revision', 'definition_id', 'rule_definition', 'fk_revision_definition', 'RESTRICT'),
  ('rule_revision', 'base_revision_id', 'rule_revision', 'fk_revision_base_revision', 'RESTRICT'),
  ('rule_revision', 'governance_request_id', 'governance_approval_request', 'fk_revision_governance_request', 'RESTRICT'),
  ('rule_lifecycle_event', 'revision_id', 'rule_revision', 'fk_lifecycle_event_revision', 'RESTRICT'),
  ('decision_artifact_component', 'artifact_id', 'decision_artifact', 'fk_decision_artifact_component_artifact', 'RESTRICT'),
  ('artifact_deployment', 'artifact_id', 'decision_artifact', 'fk_artifact_deployment_artifact', 'RESTRICT'),
  ('artifact_resource_binding', 'deployment_id', 'artifact_deployment', 'fk_artifact_resource_binding_deployment', 'RESTRICT'),
  ('rule_publish_outbox', 'revision_id', 'rule_revision', 'fk_publish_outbox_revision', 'RESTRICT'),
  ('rule_publish_outbox', 'artifact_id', 'decision_artifact', 'fk_publish_outbox_artifact', 'RESTRICT'),
  ('rule_data_object', 'parent_object_id', 'rule_data_object', 'fk_data_object_parent_object', 'RESTRICT'),
  ('rule_data_object_field', 'object_id', 'rule_data_object', 'fk_data_object_field_object', 'RESTRICT'),
  ('rule_data_object_field', 'ref_object_id', 'rule_data_object', 'fk_data_object_field_ref_object', 'RESTRICT'),
  ('rule_data_object_field', 'ref_variable_id', 'rule_variable', 'fk_data_object_field_ref_variable', 'RESTRICT'),
  ('rule_data_object_field_option', 'field_id', 'rule_data_object_field', 'fk_data_object_field_option_field', 'CASCADE'),
  ('rule_variable_option', 'variable_id', 'rule_variable', 'fk_variable_option_variable', 'CASCADE'),
  ('rule_list_record', 'list_id', 'rule_list_library', 'fk_list_record_list', 'RESTRICT'),
  ('rule_list_change_batch', 'list_id', 'rule_list_library', 'fk_list_change_batch_list', 'RESTRICT'),
  ('rule_list_change_batch', 'approval_request_id', 'governance_approval_request', 'fk_list_change_batch_approval_request', 'RESTRICT'),
  ('rule_list_change_item', 'batch_id', 'rule_list_change_batch', 'fk_list_change_item_batch', 'RESTRICT'),
  ('rule_model_input_field', 'model_id', 'rule_model', 'fk_model_input_field_model', 'CASCADE'),
  ('rule_model_output_field', 'model_id', 'rule_model', 'fk_model_output_field_model', 'CASCADE'),
  ('rule_model_version', 'model_id', 'rule_model', 'fk_model_version_model', 'RESTRICT'),
  ('rule_model_ref', 'model_id', 'rule_model', 'fk_model_ref_model', 'CASCADE'),
  ('rule_model_ref', 'project_id', 'rule_project', 'fk_model_ref_project', 'CASCADE'),
  ('rule_external_api_config', 'datasource_id', 'rule_external_datasource', 'fk_external_api_config_datasource', 'RESTRICT'),
  ('rule_external_api_config', 'request_object_id', 'rule_data_object', 'fk_external_api_config_request_object', 'RESTRICT'),
  ('rule_external_api_config', 'response_object_id', 'rule_data_object', 'fk_external_api_config_response_object', 'RESTRICT'),
  ('rule_experiment_group', 'experiment_id', 'rule_experiment', 'fk_experiment_group_experiment', 'CASCADE'),
  ('rule_experiment_group', 'rule_id', 'rule_definition', 'fk_experiment_group_rule', 'RESTRICT'),
  ('rule_version_binding', 'definition_id', 'rule_definition', 'fk_version_binding_definition', 'RESTRICT'),
  ('offline_transfer_log_resource', 'log_id', 'offline_transfer_log', 'fk_offline_transfer_log_resource_log', 'RESTRICT'),
  ('rule_ledger_event_type', 'event_id', 'rule_ledger_event', 'fk_ledger_event_type_event', 'RESTRICT'),
  ('rule_ledger_event_type', 'type_id', 'rule_ledger_type', 'fk_ledger_event_type_type', 'RESTRICT'),
  ('rule_ledger_event_state', 'event_id', 'rule_ledger_event', 'fk_ledger_event_state_event', 'RESTRICT'),
  ('rule_ledger_article', 'owner_user_id', 'console_user', 'fk_ledger_article_owner_user', 'RESTRICT'),
  ('rule_ledger_article', 'draft_user_id', 'console_user', 'fk_ledger_article_draft_user', 'RESTRICT'),
  ('rule_ledger_revision', 'article_id', 'rule_ledger_article', 'fk_ledger_revision_article', 'RESTRICT'),
  ('rule_ledger_event', 'owner_user_id', 'console_user', 'fk_ledger_event_owner_user', 'RESTRICT'),
  ('rule_ledger_asset', 'owner_user_id', 'console_user', 'fk_ledger_asset_owner_user', 'RESTRICT'),
  ('rule_ledger_revision_reference', 'revision_id', 'rule_ledger_revision', 'fk_ledger_revision_reference_revision', 'RESTRICT'),
  ('rule_ledger_draft_reference', 'article_id', 'rule_ledger_article', 'fk_ledger_draft_reference_article', 'RESTRICT');

-- 本迁移专用的短期过程。失败后修复原因，重跑时会重建过程及会话清单。
DROP PROCEDURE IF EXISTS ts_migrate_20261111_er_fk;
DELIMITER $$
CREATE PROCEDURE ts_migrate_20261111_er_fk()
SQL SECURITY INVOKER
BEGIN
  DECLARE done BOOLEAN DEFAULT FALSE;
  DECLARE child, col, parent, fk VARCHAR(64);
  DECLARE del_rule VARCHAR(16);
  DECLARE msg VARCHAR(128);
  DECLARE alter_clause TEXT;
  DECLARE add_projection BOOLEAN;
  DECLARE edges CURSOR FOR
    SELECT child_table, child_column, parent_table, fk_name, delete_rule
      FROM ts_er_relations ORDER BY child_table, fk_name;
  DECLARE changes CURSOR FOR
    SELECT child_table, add_clause FROM ts_er_relations
     WHERE add_clause IS NOT NULL ORDER BY child_table, fk_name;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

  IF @@SESSION.foreign_key_checks <> 1 OR NOT EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='rule_project'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the existing engine database and enable FOREIGN_KEY_CHECKS before migration';
  END IF;

  OPEN edges;
  preflight: LOOP
    FETCH edges INTO child, col, parent, fk, del_rule;
    IF done THEN LEAVE preflight; END IF;
    -- 旧版尚无此子表时，由新版 schema.sql 在第一次建表时声明外键。
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=child) THEN
      ITERATE preflight;
    END IF;
    SET add_projection=FALSE;
    IF col='project_ref_id' THEN
      IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name=child AND column_name=col) THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                       AND table_name=child AND column_name=col AND column_type='bigint'
                       AND extra LIKE '%STORED GENERATED%'
                       AND LOWER(REPLACE(REPLACE(generation_expression,'`',''),' ',''))='nullif(project_id,0)') THEN
          SET msg=CONCAT('Unexpected generated column: ',child,'.',col);
          SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=msg;
        END IF;
      ELSE
        SET add_projection=TRUE;
      END IF;
    END IF;

    -- 避免将同名异义、复合键或不同删除策略误认成已安装的外键。
    IF EXISTS (SELECT 1 FROM information_schema.referential_constraints
               WHERE constraint_schema=DATABASE() AND constraint_name=fk) THEN
      IF NOT EXISTS (
        SELECT 1 FROM information_schema.key_column_usage k
        JOIN information_schema.referential_constraints r USING (constraint_schema,constraint_name,table_name)
        WHERE k.constraint_schema=DATABASE() AND k.constraint_name=fk AND k.table_name=child
          AND k.column_name=col AND k.referenced_table_schema=DATABASE()
          AND k.referenced_table_name=parent AND k.referenced_column_name='id'
          AND r.delete_rule=del_rule AND r.update_rule IN ('RESTRICT','NO ACTION')
          AND NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage k2
                          WHERE k2.constraint_schema=DATABASE() AND k2.constraint_name=fk
                          AND k2.table_name=child AND k2.ordinal_position>1)
      ) THEN
        SET msg=CONCAT('Conflicting foreign key: ',fk);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=msg;
      END IF;
    ELSE
      IF EXISTS (SELECT 1 FROM information_schema.key_column_usage WHERE table_schema=DATABASE()
                 AND table_name=child AND column_name=col AND referenced_table_name IS NOT NULL) THEN
        SET msg=CONCAT('Review existing foreign key on ',child,'.',col);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=msg;
      END IF;
      SET alter_clause=CONCAT(
        IF(add_projection,'ADD COLUMN `project_ref_id` BIGINT GENERATED ALWAYS AS (NULLIF(`project_id`,0)) STORED COMMENT ''所属项目外键，全局资源为空'' AFTER `project_id`, ',''),
        'ADD CONSTRAINT `',fk,'` FOREIGN KEY (`',col,'`) REFERENCES `',parent,'` (`id`) ON DELETE ',del_rule);
      UPDATE ts_er_relations SET add_clause=alter_clause WHERE fk_name=fk;
    END IF;

    -- 使用原始项目列预检，兼容尚未添加派生列的旧库。NULL 和全局 0 都有效。
    SET @er_value=IF(col='project_ref_id','NULLIF(c.`project_id`,0)',CONCAT('c.`',col,'`'));
    SET @er_sql=CONCAT('SELECT MIN(',@er_value,') INTO @er_orphan FROM `',child,'` c ',
      'WHERE ',@er_value,' IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `',parent,'` p WHERE p.id=',@er_value,')');
    PREPARE er_stmt FROM @er_sql;
    EXECUTE er_stmt;
    DEALLOCATE PREPARE er_stmt;
    UPDATE ts_er_relations SET orphan_example=@er_orphan WHERE fk_name=fk;
  END LOOP;
  CLOSE edges;

  IF EXISTS (SELECT 1 FROM ts_er_relations WHERE orphan_example IS NOT NULL) THEN
    SELECT child_table, child_column, parent_table, orphan_example FROM ts_er_relations
     WHERE orphan_example IS NOT NULL ORDER BY child_table, child_column;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Orphan references found; review the reported IDs. No foreign keys were added in this run';
  END IF;

  SET done=FALSE;
  OPEN changes;
  apply_changes: LOOP
    FETCH changes INTO child, alter_clause;
    IF done THEN LEAVE apply_changes; END IF;
    SET @er_sql=CONCAT('ALTER TABLE `',child,'` ',alter_clause);
    PREPARE er_stmt FROM @er_sql;
    EXECUTE er_stmt;
    DEALLOCATE PREPARE er_stmt;
  END LOOP;
  CLOSE changes;
END$$
DELIMITER ;
CALL ts_migrate_20261111_er_fk();
DROP PROCEDURE ts_migrate_20261111_er_fk;
SELECT k.table_name, k.column_name, k.referenced_table_name, k.referenced_column_name
  FROM information_schema.key_column_usage k JOIN ts_er_relations e
    ON k.table_name=e.child_table AND k.constraint_name=e.fk_name
 WHERE k.table_schema=DATABASE() ORDER BY k.table_name, k.column_name;
DROP TEMPORARY TABLE ts_er_relations;
