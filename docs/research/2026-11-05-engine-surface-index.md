# 2026-11-05 当前代码入口清单

本文件由当前受跟踪代码生成，表示全量入口盘点，不表示每一行代码或每个权限组合都已完成实测。

20 个一级菜单；57 个路由定义；61 个视图组件；46 个 Controller 类（含控制台认证、运维等目录）。

## 模块规模

| 模块 | 主代码文件 | 测试文件 |
|---|---:|---:|
| rule-engine-model | 161 | 2 |
| rule-engine-core | 66 | 41 |
| rule-engine-server | 559 | 410 |
| rule-engine-client-sdk | 25 | 21 |
| rule-engine-runtime | 12 | 5 |
| rule-engine-web | 376 | 410 |

## 路由定义

- `/login`
- `/register`
- `/forgot-password`
- `/password-reset`
- `/admin-recovery`
- `/no-permission`
- `/`
- `ledger`
- `ledger/articles/new`
- `ledger/articles/:id/edit`
- `ledger/articles/:id`
- `dashboard`
- `dashboard/settings`
- `project`
- `rule`
- `rule/:id`
- `project/:id`
- `designer/table/:id`
- `designer/tree/:id`
- `designer/flow/:id`
- `designer/ruleset/:id`
- `designer/cross/:id`
- `designer/score/:id`
- `designer/cross-adv/:id`
- `designer/score-adv/:id`
- `designer/script/:id`
- `designer/expression/:ruleId/:sessionId`
- `expression/:sessionId`
- `variable`
- `list`
- `list/:id`
- `datasource`
- `datasource/source/new`
- `datasource/source/:id`
- `datasource/api/new`
- `datasource/api/:id`
- `database`
- `database/new`
- `database/:id`
- `sftp`
- `sftp/logs`
- `model`
- `model/:id`
- `function`
- `test`
- `lineage`
- `experiment`
- `experiment/new`
- `experiment/detail/:experimentId`
- `log`
- `billing`
- `approval`
- `transfer`
- `approval/:id`
- `account`
- `account/profile`
- `system/settings`

## 页面视图

- [rule-engine-web/src/views/AdminRecovery.vue](../../rule-engine-web/src/views/AdminRecovery.vue)
- [rule-engine-web/src/views/ForgotPassword.vue](../../rule-engine-web/src/views/ForgotPassword.vue)
- [rule-engine-web/src/views/Login.vue](../../rule-engine-web/src/views/Login.vue)
- [rule-engine-web/src/views/NoPermission.vue](../../rule-engine-web/src/views/NoPermission.vue)
- [rule-engine-web/src/views/PasswordReset.vue](../../rule-engine-web/src/views/PasswordReset.vue)
- [rule-engine-web/src/views/Register.vue](../../rule-engine-web/src/views/Register.vue)
- [rule-engine-web/src/views/account/AccountManagement.vue](../../rule-engine-web/src/views/account/AccountManagement.vue)
- [rule-engine-web/src/views/account/AccountProfile.vue](../../rule-engine-web/src/views/account/AccountProfile.vue)
- [rule-engine-web/src/views/account/OperationLogs.vue](../../rule-engine-web/src/views/account/OperationLogs.vue)
- [rule-engine-web/src/views/approval/ApprovalDetail.vue](../../rule-engine-web/src/views/approval/ApprovalDetail.vue)
- [rule-engine-web/src/views/approval/ApprovalList.vue](../../rule-engine-web/src/views/approval/ApprovalList.vue)
- [rule-engine-web/src/views/billing/BillingList.vue](../../rule-engine-web/src/views/billing/BillingList.vue)
- [rule-engine-web/src/views/dashboard/DashboardHome.vue](../../rule-engine-web/src/views/dashboard/DashboardHome.vue)
- [rule-engine-web/src/views/dashboard/DashboardSettings.vue](../../rule-engine-web/src/views/dashboard/DashboardSettings.vue)
- [rule-engine-web/src/views/database/DatabaseDetail.vue](../../rule-engine-web/src/views/database/DatabaseDetail.vue)
- [rule-engine-web/src/views/database/DatabaseList.vue](../../rule-engine-web/src/views/database/DatabaseList.vue)
- [rule-engine-web/src/views/datasource/ApiDetail.vue](../../rule-engine-web/src/views/datasource/ApiDetail.vue)
- [rule-engine-web/src/views/datasource/DatasourceDetail.vue](../../rule-engine-web/src/views/datasource/DatasourceDetail.vue)
- [rule-engine-web/src/views/datasource/DatasourceList.vue](../../rule-engine-web/src/views/datasource/DatasourceList.vue)
- [rule-engine-web/src/views/datasource/components/ApiExecutionEditor.vue](../../rule-engine-web/src/views/datasource/components/ApiExecutionEditor.vue)
- [rule-engine-web/src/views/designer/AdvancedCrossTable.vue](../../rule-engine-web/src/views/designer/AdvancedCrossTable.vue)
- [rule-engine-web/src/views/designer/AdvancedScorecard.vue](../../rule-engine-web/src/views/designer/AdvancedScorecard.vue)
- [rule-engine-web/src/views/designer/CrossTable.vue](../../rule-engine-web/src/views/designer/CrossTable.vue)
- [rule-engine-web/src/views/designer/DecisionFlow.vue](../../rule-engine-web/src/views/designer/DecisionFlow.vue)
- [rule-engine-web/src/views/designer/DecisionTable.vue](../../rule-engine-web/src/views/designer/DecisionTable.vue)
- [rule-engine-web/src/views/designer/DecisionTree.vue](../../rule-engine-web/src/views/designer/DecisionTree.vue)
- [rule-engine-web/src/views/designer/RuleSet.vue](../../rule-engine-web/src/views/designer/RuleSet.vue)
- [rule-engine-web/src/views/designer/Scorecard.vue](../../rule-engine-web/src/views/designer/Scorecard.vue)
- [rule-engine-web/src/views/designer/ScriptEditor.vue](../../rule-engine-web/src/views/designer/ScriptEditor.vue)
- [rule-engine-web/src/views/experiment/ExperimentDetail.vue](../../rule-engine-web/src/views/experiment/ExperimentDetail.vue)
- [rule-engine-web/src/views/experiment/ExperimentList.vue](../../rule-engine-web/src/views/experiment/ExperimentList.vue)
- [rule-engine-web/src/views/experiment/GroupActionForm.vue](../../rule-engine-web/src/views/experiment/GroupActionForm.vue)
- [rule-engine-web/src/views/expression/ExpressionEditorPage.vue](../../rule-engine-web/src/views/expression/ExpressionEditorPage.vue)
- [rule-engine-web/src/views/function/FunctionList.vue](../../rule-engine-web/src/views/function/FunctionList.vue)
- [rule-engine-web/src/views/ledger/LedgerEditor.vue](../../rule-engine-web/src/views/ledger/LedgerEditor.vue)
- [rule-engine-web/src/views/ledger/LedgerHome.vue](../../rule-engine-web/src/views/ledger/LedgerHome.vue)
- [rule-engine-web/src/views/ledger/LedgerReader.vue](../../rule-engine-web/src/views/ledger/LedgerReader.vue)
- [rule-engine-web/src/views/lineage/LineageGraph.vue](../../rule-engine-web/src/views/lineage/LineageGraph.vue)
- [rule-engine-web/src/views/log/ExecutionLog.vue](../../rule-engine-web/src/views/log/ExecutionLog.vue)
- [rule-engine-web/src/views/model/ModelDetail.vue](../../rule-engine-web/src/views/model/ModelDetail.vue)
- [rule-engine-web/src/views/model/ModelList.vue](../../rule-engine-web/src/views/model/ModelList.vue)
- [rule-engine-web/src/views/project/ProjectAuthDialog.vue](../../rule-engine-web/src/views/project/ProjectAuthDialog.vue)
- [rule-engine-web/src/views/project/ProjectDetail.vue](../../rule-engine-web/src/views/project/ProjectDetail.vue)
- [rule-engine-web/src/views/project/ProjectList.vue](../../rule-engine-web/src/views/project/ProjectList.vue)
- [rule-engine-web/src/views/rule/RuleDetail.vue](../../rule-engine-web/src/views/rule/RuleDetail.vue)
- [rule-engine-web/src/views/rule/RuleList.vue](../../rule-engine-web/src/views/rule/RuleList.vue)
- [rule-engine-web/src/views/ruleList/ListDetail.vue](../../rule-engine-web/src/views/ruleList/ListDetail.vue)
- [rule-engine-web/src/views/ruleList/ListLibrary.vue](../../rule-engine-web/src/views/ruleList/ListLibrary.vue)
- [rule-engine-web/src/views/sftp/SftpDatasourceList.vue](../../rule-engine-web/src/views/sftp/SftpDatasourceList.vue)
- [rule-engine-web/src/views/sftp/SftpQueryLog.vue](../../rule-engine-web/src/views/sftp/SftpQueryLog.vue)
- [rule-engine-web/src/views/system/FontSettings.vue](../../rule-engine-web/src/views/system/FontSettings.vue)
- [rule-engine-web/src/views/system/SystemSettings.vue](../../rule-engine-web/src/views/system/SystemSettings.vue)
- [rule-engine-web/src/views/system/WatermarkSettings.vue](../../rule-engine-web/src/views/system/WatermarkSettings.vue)
- [rule-engine-web/src/views/test/RuleTest.vue](../../rule-engine-web/src/views/test/RuleTest.vue)
- [rule-engine-web/src/views/transfer/OfflineTransfer.vue](../../rule-engine-web/src/views/transfer/OfflineTransfer.vue)
- [rule-engine-web/src/views/variable/VariableList.vue](../../rule-engine-web/src/views/variable/VariableList.vue)
- [rule-engine-web/src/views/variable/components/DerivedVariableEditor.vue](../../rule-engine-web/src/views/variable/components/DerivedVariableEditor.vue)
- [rule-engine-web/src/views/variable/components/HistoryFilters.vue](../../rule-engine-web/src/views/variable/components/HistoryFilters.vue)
- [rule-engine-web/src/views/variable/components/VariableImportHelp.vue](../../rule-engine-web/src/views/variable/components/VariableImportHelp.vue)
- [rule-engine-web/src/views/variable/components/VariableSourceSelector.vue](../../rule-engine-web/src/views/variable/components/VariableSourceSelector.vue)
- [rule-engine-web/src/views/variable/components/VariableToolbarActions.vue](../../rule-engine-web/src/views/variable/components/VariableToolbarActions.vue)

## 后端接口入口

- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/audit/ConsoleOperationLogController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/audit/ConsoleOperationLogController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/common/PlatformErrorController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/common/PlatformErrorController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/consolelogin/ConsoleAuthController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/consolelogin/ConsoleAuthController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/consolelogin/ConsoleThemePreferenceController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/consolelogin/ConsoleThemePreferenceController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/ExternalApiCallbackController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/ExternalApiCallbackController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/RuleModelController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/RuleModelController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/BillingController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/BillingController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ConsoleAccountController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ConsoleAccountController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ConsoleRoleController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ConsoleRoleController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/DashboardController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/DashboardController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/DbDatasourceController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/DbDatasourceController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/DecisionArtifactController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/DecisionArtifactController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ExecutionLogController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ExecutionLogController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ExecutionMetricsController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ExecutionMetricsController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ExternalDatasourceController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ExternalDatasourceController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/FieldPickerVersionController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/FieldPickerVersionController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/GovernanceApprovalController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/GovernanceApprovalController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/LedgerController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/LedgerController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/OfflineResourceTransferController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/OfflineResourceTransferController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ProjectAuthController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ProjectAuthController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ProjectOptionsController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ProjectOptionsController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ResourcePreflightController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ResourcePreflightController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ResourceVersionManagementController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ResourceVersionManagementController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleApiDocScenarioController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleApiDocScenarioController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleDataObjectController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleDataObjectController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleDefinitionController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleDefinitionController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleDesignerController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleDesignerController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleExperimentController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleExperimentController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleExpressionController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleExpressionController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleFieldValidationController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleFieldValidationController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleFunctionController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleFunctionController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleLineageController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleLineageController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleListController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleListController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleProjectController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleProjectController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleTestSchemaController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleTestSchemaController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleVariableController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleVariableController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleVersionController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleVersionController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuntimeCallLogController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuntimeCallLogController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/SftpDatasourceController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/SftpDatasourceController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/open/OpenRuleController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/open/OpenRuleController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/sync/LogReportController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/sync/LogReportController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/sync/ProjectAuthTokenController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/sync/ProjectAuthTokenController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/sync/RuleRuntimeController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/sync/RuleRuntimeController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/sync/RuleSyncController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/sync/RuleSyncController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/system/SystemCodeMappingSyncController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/system/SystemCodeMappingSyncController.java)
- [rule-engine-server/src/main/java/com/hengshucredit/rule/server/system/SystemSettingsController.java](../../rule-engine-server/src/main/java/com/hengshucredit/rule/server/system/SystemSettingsController.java)
