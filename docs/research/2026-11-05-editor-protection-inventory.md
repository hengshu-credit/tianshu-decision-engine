# 弹窗、抽屉与详情编辑入口盘点（2026-11-05）

本清单从 Vue 模板 AST 枚举实际入口。标记仅说明代码中存在相关机制，不等同于所有交互路径均已验收；本轮修复及测试范围见实施记录。只读详情不要求未保存提醒，真实写入仍需独立请求锁与服务端权限。

共 80 个显式 Element Plus 弹窗/抽屉入口，分布于 44 个组件文件。配置详情页及九类设计器另按共用 mixin 审查。

| 文件 | 入口 | 标题 | 关闭拦截 | 文件含离开保护 | 文件含请求代次 |
|---|---|---|---|---|---|
| [ArtifactDeploymentDialog.vue](../../rule-engine-web/src/components/artifact/ArtifactDeploymentDialog.vue) | el-dialog:  | 跨环境部署决策制品 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [DesignerTestDialog.vue](../../rule-engine-web/src/components/common/DesignerTestDialog.vue) | el-dialog: innerVisible | 测试执行 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [ModuleCallLog.vue](../../rule-engine-web/src/components/common/ModuleCallLog.vue) | el-drawer: detailVisible | profile.detailTitle | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [ResourceVersionManager.vue](../../rule-engine-web/src/components/common/ResourceVersionManager.vue) | el-drawer: visible | 版本管理 | 有 | 无通用标记 | 有 |
| [EndNodeScopeDialog.vue](../../rule-engine-web/src/components/flow/EndNodeScopeDialog.vue) | el-dialog: innerVisible | 添加结束节点 | 专用处理/需结合语义核对 | 无通用标记 | 无通用标记 |
| [GovernancePublishAction.vue](../../rule-engine-web/src/components/GovernancePublishAction.vue) | el-dialog: issueVisible | 发布前检查未通过 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [GovernanceWorkspaceToolbar.vue](../../rule-engine-web/src/components/GovernanceWorkspaceToolbar.vue) | el-drawer: visible | 草稿与审批 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [LedgerAssets.vue](../../rule-engine-web/src/components/ledger/LedgerAssets.vue) | el-dialog: editVisible | 编辑素材 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [LedgerAssets.vue](../../rule-engine-web/src/components/ledger/LedgerAssets.vue) | el-dialog: referencesVisible | 素材引用 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [LedgerEventDialog.vue](../../rule-engine-web/src/components/ledger/LedgerEventDialog.vue) | el-dialog:  | form.id ? '编辑事件' : '新建事件' | 有 | 无通用标记 | 有 |
| [LedgerManagement.vue](../../rule-engine-web/src/components/ledger/LedgerManagement.vue) | el-dialog: typeVisible | typeForm.id ? '编辑事件类型' : '新增事件类型' | 有 | 有 | 有 |
| [ModelImpactDialog.vue](../../rule-engine-web/src/components/model/ModelImpactDialog.vue) | el-dialog:  | 模型引用影响确认 | 专用处理/需结合语义核对 | 无通用标记 | 无通用标记 |
| [ApiScenarioPanel.vue](../../rule-engine-web/src/components/rule/ApiScenarioPanel.vue) | el-dialog: draftVisible | draft.id ? '编辑 API 测试用例' : '新增 API 测试用例' | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [RegressionReportDrawer.vue](../../rule-engine-web/src/components/rule/RegressionReportDrawer.vue) | el-drawer:  | 回归报告 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [RegressionReportDrawer.vue](../../rule-engine-web/src/components/rule/RegressionReportDrawer.vue) | el-dialog: detailVisible | 回归报告详情 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [RuleDesignerActionBar.vue](../../rule-engine-web/src/components/rule/RuleDesignerActionBar.vue) | el-dialog: reportVisible | 校验报告 | 专用处理/需结合语义核对 | 无通用标记 | 无通用标记 |
| [RuleDesignerDialogs.vue](../../rule-engine-web/src/components/rule/RuleDesignerDialogs.vue) | el-dialog:  | title | 有 | 无通用标记 | 有 |
| [ThemeSettingsDrawer.vue](../../rule-engine-web/src/components/theme/ThemeSettingsDrawer.vue) | el-drawer:  | 主题设置 | 有 | 无通用标记 | 无通用标记 |
| [AccountManagement.vue](../../rule-engine-web/src/views/account/AccountManagement.vue) | el-dialog: accountDialogVisible | accountForm.id ? '编辑账户' : '新增账户' | 有 | 有 | 无通用标记 |
| [AccountManagement.vue](../../rule-engine-web/src/views/account/AccountManagement.vue) | el-dialog: permissionDialogVisible | 账户权限调整 | 有 | 有 | 无通用标记 |
| [AccountManagement.vue](../../rule-engine-web/src/views/account/AccountManagement.vue) | el-dialog: roleDialogVisible | roleForm.id ? '配置角色' : '新增角色' | 有 | 有 | 无通用标记 |
| [AccountManagement.vue](../../rule-engine-web/src/views/account/AccountManagement.vue) | el-dialog: passwordDialogVisible | 重置密码 | 有 | 有 | 无通用标记 |
| [OperationLogs.vue](../../rule-engine-web/src/views/account/OperationLogs.vue) | el-dialog: detailVisible | 操作日志详情 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [ApprovalDetail.vue](../../rule-engine-web/src/views/approval/ApprovalDetail.vue) | el-dialog: actionDialogVisible | actionDialogTitle | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [ApprovalDetail.vue](../../rule-engine-web/src/views/approval/ApprovalDetail.vue) | el-dialog: versionPreviewVisible | versionPreviewTitle | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [BillingList.vue](../../rule-engine-web/src/views/billing/BillingList.vue) | el-dialog: trialDialogVisible | 试运行与计费记录 | 专用处理/需结合语义核对 | 有 | 有 |
| [BillingList.vue](../../rule-engine-web/src/views/billing/BillingList.vue) | el-dialog: configDialogVisible | configForm.id ? '编辑计费配置' : '新建计费配置' | 有 | 有 | 有 |
| [DatabaseList.vue](../../rule-engine-web/src/views/database/DatabaseList.vue) | el-dialog: queryDialogVisible | 只读查询 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [ApiExecutionEditor.vue](../../rule-engine-web/src/views/datasource/components/ApiExecutionEditor.vue) | el-dialog: sampleVisible | 导入响应样例 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [ApiExecutionEditor.vue](../../rule-engine-web/src/views/datasource/components/ApiExecutionEditor.vue) | el-dialog: configImportVisible | 导入链路配置 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [DatasourceList.vue](../../rule-engine-web/src/views/datasource/DatasourceList.vue) | el-dialog: authTestDialogVisible | 数据源鉴权测试 | 专用处理/需结合语义核对 | 无通用标记 | 无通用标记 |
| [DatasourceList.vue](../../rule-engine-web/src/views/datasource/DatasourceList.vue) | el-dialog: invokeDialogVisible | API 调用测试 | 专用处理/需结合语义核对 | 无通用标记 | 无通用标记 |
| [RuleSet.vue](../../rule-engine-web/src/views/designer/RuleSet.vue) | el-dialog: versionVisible | 规则集版本历史 | 专用处理/需结合语义核对 | 有 | 无通用标记 |
| [RuleSet.vue](../../rule-engine-web/src/views/designer/RuleSet.vue) | el-dialog: versionViewVisible | versionViewTitle | 专用处理/需结合语义核对 | 有 | 无通用标记 |
| [ExperimentDetail.vue](../../rule-engine-web/src/views/experiment/ExperimentDetail.vue) | el-drawer: logDetailVisible | 分流日志详情 | 专用处理/需结合语义核对 | 有 | 有 |
| [ExperimentDetail.vue](../../rule-engine-web/src/views/experiment/ExperimentDetail.vue) | el-dialog: versionVisible | 分流实验版本管理 | 专用处理/需结合语义核对 | 有 | 有 |
| [ExperimentList.vue](../../rule-engine-web/src/views/experiment/ExperimentList.vue) | el-dialog: testVisible | 验证执行分流实验 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [ExpressionEditorPage.vue](../../rule-engine-web/src/views/expression/ExpressionEditorPage.vue) | el-dialog: testVisible | 测试当前表达式 | 专用处理/需结合语义核对 | 无通用标记 | 无通用标记 |
| [FunctionList.vue](../../rule-engine-web/src/views/function/FunctionList.vue) | el-dialog: dialogVisible | editForm.id ? '编辑函数' : '新建函数' | 有 | 有 | 有 |
| [FunctionList.vue](../../rule-engine-web/src/views/function/FunctionList.vue) | el-dialog: functionTestVisible | 函数测试 | 专用处理/需结合语义核对 | 有 | 有 |
| [FunctionList.vue](../../rule-engine-web/src/views/function/FunctionList.vue) | el-dialog: versionVisible | 函数版本管理 | 专用处理/需结合语义核对 | 有 | 有 |
| [LedgerEditor.vue](../../rule-engine-web/src/views/ledger/LedgerEditor.vue) | el-dialog: publishDialog | 文章发布配置 | 专用处理/需结合语义核对 | 有 | 有 |
| [LedgerEditor.vue](../../rule-engine-web/src/views/ledger/LedgerEditor.vue) | el-dialog: assetPicker | 素材库 | 专用处理/需结合语义核对 | 有 | 有 |
| [ExecutionLog.vue](../../rule-engine-web/src/views/log/ExecutionLog.vue) | el-drawer: detailVis | 日志详情 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [ModelDetail.vue](../../rule-engine-web/src/views/model/ModelDetail.vue) | el-dialog: versionVisible | 版本历史 | 专用处理/需结合语义核对 | 有 | 有 |
| [ModelDetail.vue](../../rule-engine-web/src/views/model/ModelDetail.vue) | el-dialog: testVisible | 模型测试 | 专用处理/需结合语义核对 | 有 | 有 |
| [ModelList.vue](../../rule-engine-web/src/views/model/ModelList.vue) | el-dialog: uploadVisible | 上传模型 | 有 | 有 | 有 |
| [ModelList.vue](../../rule-engine-web/src/views/model/ModelList.vue) | el-dialog: toGlobalVisible | 转为全局模型 | 有 | 有 | 有 |
| [ModelList.vue](../../rule-engine-web/src/views/model/ModelList.vue) | el-dialog: editVisible | 编辑模型 | 有 | 有 | 有 |
| [ProjectAuthDialog.vue](../../rule-engine-web/src/views/project/ProjectAuthDialog.vue) | el-dialog:  | 项目调用鉴权 | 专用处理/需结合语义核对 | 无通用标记 | 无通用标记 |
| [ProjectAuthDialog.vue](../../rule-engine-web/src/views/project/ProjectAuthDialog.vue) | el-dialog: authFormVisible | authForm.id ? '编辑鉴权' : '新增鉴权' | 专用处理/需结合语义核对 | 无通用标记 | 无通用标记 |
| [ProjectAuthDialog.vue](../../rule-engine-web/src/views/project/ProjectAuthDialog.vue) | el-dialog: fullDialogVisible | 完整鉴权值 | 专用处理/需结合语义核对 | 无通用标记 | 无通用标记 |
| [ProjectAuthDialog.vue](../../rule-engine-web/src/views/project/ProjectAuthDialog.vue) | el-dialog: fullTokenDialogVisible | 完整临时 Token | 专用处理/需结合语义核对 | 无通用标记 | 无通用标记 |
| [ProjectDetail.vue](../../rule-engine-web/src/views/project/ProjectDetail.vue) | el-dialog: dlgVis | 新建规则 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [ProjectDetail.vue](../../rule-engine-web/src/views/project/ProjectDetail.vue) | el-dialog: addRuleDlgVis | 添加全局规则 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [ProjectList.vue](../../rule-engine-web/src/views/project/ProjectList.vue) | el-dialog: dialogVisible | form.id ? '编辑项目' : '新建项目' | 有 | 有 | 无通用标记 |
| [RuleDetail.vue](../../rule-engine-web/src/views/rule/RuleDetail.vue) | el-dialog: baseEditVisible | 编辑规则基本信息 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [RuleDetail.vue](../../rule-engine-web/src/views/rule/RuleDetail.vue) | el-dialog: openApiPreviewVisible | 开放接口响应预览 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [RuleDetail.vue](../../rule-engine-web/src/views/rule/RuleDetail.vue) | el-dialog: testVisible | 规则测试 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [RuleDetail.vue](../../rule-engine-web/src/views/rule/RuleDetail.vue) | el-dialog: versionVisible | 版本历史 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [RuleList.vue](../../rule-engine-web/src/views/rule/RuleList.vue) | el-dialog: dialogVisible | 新建规则 | 有 | 有 | 有 |
| [ListDetail.vue](../../rule-engine-web/src/views/ruleList/ListDetail.vue) | el-dialog: dialogVisible | form.id ? '修改名单记录并审批' : '新增名单记录并审批' | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [ListLibrary.vue](../../rule-engine-web/src/views/ruleList/ListLibrary.vue) | el-dialog: dialogVisible | form.id ? '修改名单库' : '新建名单库' | 有 | 有 | 无通用标记 |
| [SftpDatasourceList.vue](../../rule-engine-web/src/views/sftp/SftpDatasourceList.vue) | el-dialog: dialogVisible | form.id ? '编辑 SFTP 数据源' : '新建 SFTP 数据源' | 有 | 有 | 有 |
| [SftpQueryLog.vue](../../rule-engine-web/src/views/sftp/SftpQueryLog.vue) | el-dialog: previewVisible | SFTP 文件预览 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [SystemSettings.vue](../../rule-engine-web/src/views/system/SystemSettings.vue) | el-dialog: testDialog | 发送测试邮件 | 专用处理/需结合语义核对 | 有 | 有 |
| [OfflineTransfer.vue](../../rule-engine-web/src/views/transfer/OfflineTransfer.vue) | el-dialog: resultVisible | 导入结果 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [OfflineTransfer.vue](../../rule-engine-web/src/views/transfer/OfflineTransfer.vue) | el-dialog: logDetailVisible | 迁移日志详情 | 专用处理/需结合语义核对 | 无通用标记 | 有 |
| [VariableList.vue](../../rule-engine-web/src/views/variable/VariableList.vue) | el-dialog: validationDialogVisible | validationForm.id ? '编辑字段校验' : '新建字段校验' | 有 | 有 | 有 |
| [VariableList.vue](../../rule-engine-web/src/views/variable/VariableList.vue) | el-dialog: dialogVisible | variableDialogTitle | 有 | 有 | 有 |
| [VariableList.vue](../../rule-engine-web/src/views/variable/VariableList.vue) | el-dialog: objectDialogVisible | objectDialogTitle | 有 | 有 | 有 |
| [VariableList.vue](../../rule-engine-web/src/views/variable/VariableList.vue) | el-dialog: optionDialogVisible | 枚举选项管理 | 有 | 有 | 有 |
| [VariableList.vue](../../rule-engine-web/src/views/variable/VariableList.vue) | el-dialog: variableTestVisible | 变量取数测试 | 专用处理/需结合语义核对 | 有 | 有 |
| [VariableList.vue](../../rule-engine-web/src/views/variable/VariableList.vue) | el-dialog: sourceDetailVisible | 变量取数详情 | 专用处理/需结合语义核对 | 有 | 有 |
| [VariableList.vue](../../rule-engine-web/src/views/variable/VariableList.vue) | el-dialog: importJavaEntityVisible | 导入 Java 实体类 | 有 | 有 | 有 |
| [VariableList.vue](../../rule-engine-web/src/views/variable/VariableList.vue) | el-dialog: importJsonObjectVisible | 导入 JSON 对象 | 有 | 有 | 有 |
| [VariableList.vue](../../rule-engine-web/src/views/variable/VariableList.vue) | el-dialog: importDdlVisible | 导入 DDL 建表语句 | 有 | 有 | 有 |
| [VariableList.vue](../../rule-engine-web/src/views/variable/VariableList.vue) | el-dialog: importJavaConstVisible | 导入 Java 常量类 | 有 | 有 | 有 |
| [VariableList.vue](../../rule-engine-web/src/views/variable/VariableList.vue) | el-dialog: importJsonConstVisible | 导入 JSON 常量 | 有 | 有 | 有 |
| [VariableList.vue](../../rule-engine-web/src/views/variable/VariableList.vue) | el-dialog: importResultVisible | 导入结果 | 专用处理/需结合语义核对 | 有 | 有 |
