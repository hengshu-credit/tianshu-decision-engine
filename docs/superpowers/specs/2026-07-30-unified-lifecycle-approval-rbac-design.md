# 统一生命周期、审批管理与账户权限体系设计

## 1. 目标

本次改造把项目中分散的规则生命周期、模型/函数/分流版本记录，以及字段、外数、数据库、项目的直接修改逻辑，统一为一套“编辑副本—提交审批—校验依赖—批准即生效—版本永久保留”的治理体系。

覆盖范围：

- 字段：变量、变量选项、数据对象、数据对象字段。
- 模型：模型文件、运行配置、输入字段、输出字段。
- 外数：外数数据源、外数 API 及其非敏感配置。
- 数据库：数据库数据源及连接配置。
- 函数：函数定义、参数、实现配置和脚本。
- 规则：规则基础信息、九种设计器内容、字段契约、开放接口配置和编译制品。
- 分流：分流实验及实验组。
- 项目：项目基础信息和启停状态。
- 账户权限：控制台账户、角色、菜单权限、操作权限和用户级权限覆盖。

项目访问令牌、项目鉴权凭据的签发、撤销和轮换不纳入资源版本快照，继续走独立安全流程。

## 2. 已确认的业务约束

1. 审批通过后立即生成新版本并生效；规则同时生成并发布不可变决策制品。
2. 审批驳回后，本次编辑副本终止，业务模块继续使用当前生效版本。
3. 被驳回内容不再作为可编辑草稿，但审批单、提交快照、差异、意见、操作人与时间永久保留。
4. 新建资源在审批通过前不进入正常业务列表；驳回后只在审批历史中保留。
5. 一张审批单只包含一个资源聚合的一次变更，不支持跨模块批量合并或原子上线。
6. 依赖资源必须已经生效；其他待审批申请不能充当依赖。
7. 允许同一用户提交并审批自己的申请，但必须同时拥有提交和审批权限。
8. 所有改变生效状态的操作均受生命周期管理：新建、修改、启用、停用、删除、历史版本恢复。
9. 删除采用软删除，历史版本、审批记录和血缘记录不可删除。
10. 权限只控制功能访问和操作，不在本次实现项目级数据范围隔离。
11. 角色权限取并集，用户级 `ALLOW` 可以额外授权，用户级 `DENY` 覆盖角色授权。
12. 前端菜单和按钮控制仅用于交互提示，后端必须使用同一权限编码执行最终鉴权。
13. 外数和数据库的密码、Token、密钥参与变更，但差异页不显示明文，只显示“未变更/已变更”。
14. 所有引用继续严格通过 ID 和 `ref_type` 关联，不通过名称或编码猜测。

## 3. 总体架构

采用“通用审批内核 + 模块适配器”。

业务表继续表示当前生效投影，规则执行、变量解析、外数调用和数据库连接等运行时链路仍从现有业务表读取，不让审批中的内容污染运行时。

通用审批内核负责：

- 编辑申请和审批单状态。
- 不可变资源版本。
- 审批审计事件。
- 基准版本并发冲突。
- 版本级依赖快照。
- 统一权限校验。
- 通用列表、详情和历史查询。

模块适配器负责：

- 从当前生效业务表创建规范快照。
- 校验编辑快照。
- 提取上游依赖和下游影响。
- 生成安全、可展示的语义差异。
- 审批通过后原子写入业务表。
- 对规则、模型等模块执行编译、制品生成或运行时刷新。

接口边界：

```java
public interface GovernedResourceAdapter {
    String resourceType();
    ResourceSnapshot loadEffective(Long resourceId);
    ResourceSnapshot normalizeDraft(ResourceSnapshot draft);
    List<GovernanceIssue> validate(ResourceSnapshot draft);
    List<ResourceDependencyRef> collectDependencies(ResourceSnapshot draft);
    ResourceDiff diff(ResourceSnapshot left, ResourceSnapshot right);
    AppliedResource apply(ApprovalApplyContext context);
}
```

通用内核不解析任何模块专用 JSON，也不直接修改模块业务表。

## 4. 资源聚合边界

每张审批单只治理一个资源聚合：

| 审批 TAB | 资源类型 | 聚合快照 |
|---|---|---|
| 字段 | `VARIABLE` | 变量及变量选项 |
| 字段 | `DATA_OBJECT` | 数据对象及全部对象字段、父子字段关系 |
| 模型 | `MODEL` | 模型元数据、二进制引用、配置、输入输出字段 |
| 外数 | `EXTERNAL_DATASOURCE` | 外数数据源与数据源鉴权配置 |
| 外数 | `EXTERNAL_API` | 单个 API、请求映射、鉴权覆盖和脚本配置 |
| 数据库 | `DB_DATASOURCE` | 数据库连接、SSH 和连接池配置 |
| 函数 | `FUNCTION` | 函数定义、参数与实现 |
| 规则 | `RULE` | 规则基础信息、设计内容、字段契约、开放接口配置 |
| 分流 | `EXPERIMENT` | 实验及全部实验组 |
| 项目 | `PROJECT` | 项目基础信息和启停状态 |

数据对象字段不单独产生与父对象脱节的版本。编辑任意对象字段时，提交整个数据对象聚合，以保证父子关系和字段顺序原子生效。

外数 API 与外数数据源分别审批。API 申请必须引用一个已生效的数据源版本。

## 5. 数据模型

### 5.1 资源治理

`governed_resource`

- `id`
- `resource_type`
- `resource_id`
- `project_id`
- `effective_version_id`
- `effective_version_no`
- `effective_status`：`ACTIVE`、`DISABLED`、`DELETED`
- `lock_version`
- `create_time`
- `update_time`

唯一键：`resource_type + resource_id`。

`governed_resource_version`

- `id`
- `governed_resource_id`
- `resource_type`
- `resource_id`
- `version_no`
- `source_version_id`：历史恢复时指向被复制版本。
- `approval_request_id`
- `snapshot_json`：不含明文敏感信息的规范快照。
- `snapshot_digest`
- `secret_payload_ciphertext`：存在敏感变更时保存 AES-GCM 密文。
- `secret_digest`
- `effective_status`
- `change_summary`
- `create_by`
- `create_time`

版本一旦写入不可修改。唯一键：`governed_resource_id + version_no`。

### 5.2 审批

`governance_approval_request`

- `id`
- `request_no`
- `resource_type`
- `resource_id`：已有资源保存真实 ID；新建资源在审批通过前使用 `0`，适配器落库后回填真实 ID。
- `project_id`
- `action`：`CREATE`、`UPDATE`、`ENABLE`、`DISABLE`、`DELETE`、`RESTORE`
- `status`：`EDITING`、`PENDING`、`APPROVED`、`REJECTED`、`CANCELLED`、`CONFLICT`
- `active_resource_key`：活动申请唯一键，终态清空。
- `base_version_id`
- `base_version_no`
- `source_version_id`：`RESTORE` 时必填。
- `draft_snapshot_json`
- `submitted_snapshot_json`
- `snapshot_digest`
- `secret_payload_ciphertext`
- `secret_digest`
- `dependency_digest`
- `validation_report_json`
- `change_summary`
- `submit_comment`
- `review_comment`
- `applicant`
- `submit_time`
- `reviewer`
- `review_time`
- `lock_version`
- `create_time`
- `update_time`

`EDITING` 内容仅申请人本人可修改、预检、提交或取消；进入 `PENDING` 后提交快照不可修改，申请人仍可撤回。审批权限与草稿所有权分离，因此允许申请人自行审批，也允许其他审批人审批，但审批人不能改写申请内容。`APPROVED`、`REJECTED`、`CANCELLED`、`CONFLICT` 均为只读记录。

已有资源的活动键为“资源类型 + 资源 ID”；新建资源按数据库业务唯一约束提取“作用域/项目 + 业务编码”等身份字段，并保存其规范摘要。同一业务身份只能存在一个 `EDITING/PENDING` 申请；并发碰撞返回稳定业务冲突。资源名称等可变展示字段不参与身份键，资源之间的依赖关联仍严格使用 ID。

`governance_approval_event`

- `id`
- `request_id`
- `action`
- `from_status`
- `to_status`
- `actor`
- `comment`
- `details_json`
- `create_time`

事件表只追加，不更新和删除。

### 5.3 依赖

`governance_dependency_snapshot`

- `id`
- `request_id`
- `version_id`
- `source_resource_type`
- `source_resource_id`
- `target_resource_type`
- `target_resource_id`
- `target_version_id`
- `target_version_no`
- `reference_path`
- `relation_type`
- `required`
- `resolution_status`
- `target_digest`
- `issue_code`
- `issue_message`
- `create_time`

提交审批时保存依赖快照，审批通过前重新解析并比较依赖摘要，避免提交后依赖被替换造成竞态。

### 5.4 账户权限

新增：

- `console_user`
- `console_role`
- `console_permission`
- `console_user_role`
- `console_role_permission`
- `console_user_permission_override`
- `console_security_audit_log`

`console_user_permission_override.effect` 为 `ALLOW` 或 `DENY`。

账户密码只保存 BCrypt 哈希，不提供密码回显。至少保留一个启用且拥有账户和角色管理权限的用户，避免系统被锁死。

用户名是登录、审批归属与审计记录使用的稳定身份，账户创建后不可修改；需要调整人员展示信息时只修改显示名称。

## 6. 状态流

### 6.1 编辑与提交

1. 用户从业务模块打开当前生效版本 Vn。
2. 点击新建、编辑、启停、删除或使用历史版本。
3. 系统创建或复用唯一的 `EDITING` 申请；业务表不变。
4. 编辑页面只保存申请的 `draft_snapshot_json`。
5. 提交时规范化快照，执行模块校验、血缘分析、依赖存在性、依赖状态、作用域、循环引用和下游影响校验。
6. 阻断错误存在时拒绝提交，申请保持 `EDITING`，返回可定位到字段或资源的冲突信息。
7. 校验通过后冻结 `submitted_snapshot_json`，保存依赖快照，进入 `PENDING`。

同一资源只允许一个 `EDITING` 或 `PENDING` 申请。

### 6.2 审批通过

1. 校验 `approval:approve` 权限。
2. 锁定审批单和 `governed_resource`。
3. 比较当前生效版本与 `base_version_id`。
4. 重新执行完整校验和依赖解析。
5. 基准版本或依赖摘要变化时转为 `CONFLICT`，不自动合并。
6. 校验通过后生成 Vn+1 不可变版本。
7. 调用模块适配器在同一事务中更新业务投影。
8. 更新 `governed_resource.effective_version_id`。
9. 审批单进入 `APPROVED`。
10. 规则生成决策制品并通过 outbox 发布；数据库连接池、函数注册器等运行时刷新也通过提交后事件执行。

适配器应用失败时事务回滚，审批单继续保持 `PENDING`，不会出现“已批准但未生效”；业务唯一键碰撞会明确转为 `CONFLICT`，释放活动键并保留完整审批记录。

### 6.3 驳回和取消

- `PENDING` 可被审批人驳回为 `REJECTED`。
- `EDITING` 或 `PENDING` 可由申请人取消为 `CANCELLED`。
- 驳回和取消后编辑副本不可继续修改。
- 业务模块继续展示终止操作时的当前生效版本；发生基准冲突时不得用旧基准覆盖更新后的生效版本。
- 新建资源不进入业务列表。
- 完整提交快照和事件记录永久保留。

### 6.4 历史版本恢复

选择历史版本 Vx 后创建 `RESTORE` 申请：

- 基准版本为当前生效 Vn。
- 提交快照复制自 Vx。
- `source_version_id = Vx`。
- 差异页比较 Vn 与 Vx。
- 审批通过后生成新的 Vn+1。
- Vx、Vn 和其他历史版本均不修改。

## 7. 血缘与冲突策略

### 7.1 提交前校验

所有适配器必须返回 ID 级依赖。校验项包括：

- 引用 ID 存在。
- `ref_type` 与目标资源类型一致。
- 目标资源已经生效且未删除。
- 停用资源不能作为新增依赖。
- 项目级资源作用域可见。
- 规则调用、模型输入、计算字段等不存在循环依赖。
- 规则编译、字段 Schema、模型格式、函数实现和分流配置有效。
- 停用或删除操作不存在仍生效的下游引用。

缺失或无效依赖为阻断错误，不允许填写理由强制通过。用户必须先修改申请或处理下游资源。

连接可用性测试保留为模块测试能力；配置结构错误阻断审批，网络瞬时失败只作为警告，避免外部系统短暂不可用永久阻断治理。

### 7.2 依赖变化

审批时重新解析依赖。以下情况转为 `CONFLICT`：

- 当前资源生效版本不再等于基准版本。
- 依赖资源被删除、停用或更换版本。
- 依赖摘要变化。
- 下游影响集合发生变化并使操作不再安全。

冲突申请不可重新编辑，用户从最新生效版本创建新的申请。

### 7.3 血缘展示

现有血缘分析继续展示生效资源图；审批详情增加“申请快照血缘”：

- 上游依赖：申请将使用什么。
- 下游影响：哪些生效资源会受影响。
- 缺失与冲突：资源、引用路径、错误原因和跳转入口。

脚本动态引用仍保持静态尽力识别的已知边界。

## 8. 版本差异

所有版本快照先规范化再计算 SHA-256，避免字段顺序造成伪差异。

差异由适配器生成语义结构，不在浏览器中对未知 JSON 做通用猜测。

- 字段：基础属性、来源配置、选项、对象字段树左右对齐。
- 模型：模型文件名、大小、摘要、格式、运行配置、输入输出字段；不展示二进制。
- 外数：数据源/API 基础信息、请求参数、Header、Body、脚本和鉴权配置；敏感值只显示是否变化。
- 数据库：连接方式、地址、库名、用户名、连接池、SSH；密码、私钥和口令只显示是否变化。
- 函数：参数和实现配置左右对齐，脚本使用 Monaco Diff。
- 规则：复用并扩展现有九种规则语义差异组件，按表达式追踪树风格左右展示条件、动作、节点、连线、矩阵、评分项和脚本。
- 分流：实验基础配置、组、流量、条件和目标规则左右对齐。
- 项目：基础属性和启停状态左右对齐。

审批详情默认比较“提交基准版本”与“提交快照”。历史页允许任意选择两个版本比较。

## 9. 审批管理页面

新增一级菜单“审批管理”，路由：

- `/approval`
- `/approval/:requestId`

列表页包含八个 TAB：

1. 字段
2. 模型
3. 外数
4. 数据库
5. 函数
6. 规则
7. 分流
8. 项目

每个 TAB 默认展示待审批数量徽标，支持按状态、操作类型、申请人、审批人、项目、关键词和时间筛选。

列表字段统一包含：

- 申请编号
- 资源名称/编码
- 资源子类型
- 操作类型
- 基准版本
- 申请状态
- 血缘校验摘要
- 申请人/提交时间
- 审批人/审批时间

详情页包含：

- 申请头部：状态、操作、资源、基准版本、目标版本、申请人与审批人。
- 冲突提示。
- 左右版本差异。
- 上游依赖和下游影响。
- 校验错误与警告。
- 审批事件时间线。
- 基于权限和状态显示提交、取消、通过、驳回按钮。

允许自提自批，不显示虚假的职责分离提示。

## 10. 原业务页面改造

业务列表只展示已生效且未软删除资源。

业务详情页：

- 默认展示当前生效版本。
- 显示当前版本号和生效状态。
- 若存在 `EDITING` 或 `PENDING` 申请，显示治理横幅并跳转对应申请。
- 编辑操作进入申请编辑上下文，不直接调用现有更新接口。
- 历史版本区域支持详情、任意版本对比和“使用此版本”。
- “使用此版本”创建 `RESTORE` 申请，不直接覆盖业务表。

所有原直接变更接口调整为：

- 仅供治理适配器内部调用；或
- 创建/更新审批申请，不再直接写生效业务表。

运行时同步、日志上报、规则开放调用等非管理接口不受审批页面影响。

## 11. 账户与权限页面

新增一级菜单“账户管理”，包含“账户”和“角色”两个 TAB。

账户页面支持：

- 新增账户。
- 启用/停用账户。
- 重置密码。
- 分配一个或多个角色。
- 查看角色权限并集。
- 对单个权限设置继承、额外允许或明确拒绝。
- 查看最终有效权限。

角色页面支持：

- 新增、编辑、停用角色。
- 按菜单分组配置页面访问和操作按钮权限。
- 查看角色成员。
- 防止停用或移除最后一个具备账户与角色管理能力的用户。

权限编码示例：

- `project:view`
- `project:edit`
- `project:submit`
- `approval:view`
- `approval:submit`
- `approval:approve`
- `account:view`
- `account:manage`
- `role:view`
- `role:manage`

后端通过 `@RequirePermission` 校验；前端通过权限指令控制菜单和按钮。接口拒绝返回 HTTP 403 和稳定错误码。

## 12. 登录迁移

数据库账户成为控制台登录的权威来源。

为避免现有部署锁死：

1. 当数据库尚无任何控制台账户时，允许现有配置中的 builtin 账户登录。
2. 首次成功登录后，将该用户名创建为数据库账户并授予系统超级管理员角色，密码保存为 BCrypt。
3. 一旦数据库存在账户，不再回退到 builtin 认证。
4. 不提供共享默认用户名或密码。
5. 自定义 `ConsoleLoginAuthenticator` 仍可作为外部认证扩展，但必须映射到本地账户后才能计算权限。

## 13. 兼容与迁移

新增幂等迁移服务：

- 规则、模型、函数和分流现有版本表回填到 `governed_resource_version`。
- 字段、外数、数据库和项目当前记录生成迁移版本 V1。
- 当前发布版本成为 `effective_version_id`。
- 现有规则修订与生命周期事件转换为审批记录或历史事件。
- 旧版本表在迁移阶段只读保留，运行时和新页面切换到统一版本服务。
- 迁移完成并验证后再删除旧写入路径，不直接删除旧表。

迁移必须幂等，并通过资源类型、资源 ID 和旧版本来源唯一键防止重复回填。

## 14. 安全

- 密码使用 BCrypt。
- 外数、数据库敏感负载使用现有 `CredentialCipher` 的 AES-GCM 能力加密。
- 快照、差异、审计事件、日志和 API 响应不得包含明文密码、Token、密钥或私钥。
- 敏感字段只保存密文和摘要，差异结果只返回 changed 标记。
- 审批通过后的业务投影解密仅发生在适配器事务内。
- 所有账户、角色、权限、登录和审批操作写审计日志。
- 会话中的用户 ID 与权限版本一并保存；角色或用户权限调整后使旧权限缓存失效。

## 15. 错误处理

稳定错误类型：

- `APPROVAL_STATE_CONFLICT`
- `APPROVAL_BASE_VERSION_CONFLICT`
- `APPROVAL_DEPENDENCY_CONFLICT`
- `APPROVAL_ACTIVE_REQUEST_EXISTS`
- `APPROVAL_VALIDATION_FAILED`
- `RESOURCE_NOT_EFFECTIVE`
- `RESOURCE_SOFT_DELETED`
- `PERMISSION_DENIED`
- `LAST_ADMIN_PROTECTION`

所有冲突返回结构化问题列表，包含资源类型、资源 ID、引用路径、问题代码、说明和可跳转修复目标，不把后端异常栈直接展示给业务用户。

## 16. 测试与验收

后端：

- 权限合并、用户 ALLOW/DENY、后端接口 403。
- 首次 builtin 账户迁移和数据库账户登录。
- 每种审批状态转换。
- 驳回后生效版本不变。
- 新建驳回后不进入业务列表。
- 基准版本和依赖摘要冲突。
- 缺失依赖、停用依赖、类型不匹配和循环依赖。
- 每个适配器的快照、差异、校验和应用。
- 历史恢复生成新版本且旧版本不变。
- 敏感信息不进入 JSON、日志和差异响应。
- 规则批准后制品、发布表和 outbox 一致。
- 迁移幂等。

前端：

- 菜单和按钮随最终权限变化。
- 用户权限 `DENY` 覆盖角色授权。
- 八个审批 TAB、筛选、待办徽标和详情跳转。
- 各资源差异组件。
- 审批通过、驳回、取消和冲突交互。
- 驳回后回到生效版本。
- 历史版本恢复申请。
- 敏感字段差异不泄漏。

端到端：

- 创建账户和角色并验证菜单/按钮及后端拒绝。
- 字段审批通过后规则可引用。
- 缺失字段阻断规则提交。
- 规则修改提交、左右对比、自提自批、制品发布。
- 规则历史版本恢复生成新版本。
- 数据库/外数敏感配置审批全过程无明文泄漏。
- 停用有下游引用的资源时显示冲突并阻断。

完成代码后严格执行仓库规定的前端启动、前端测试、后端编译、后端启动、后端测试和浏览器完整业务操作验证。
