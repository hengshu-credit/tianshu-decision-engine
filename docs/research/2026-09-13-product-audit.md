# 天枢决策引擎产品与实现评估

后续更新：指定的 A05、A07、A08、A09、A11、A13、A14、A15、A17 已完成本轮代码修复，详见[修复记录与验证边界](E:/workspace/tianshu-decision-engine/docs/research/2026-09-13-selected-fixes.md)。以下问题清单保留最初评估时的发现与状态。

天枢已经具备从数据准备、规则设计、测试审批到发布执行和运行分析的主要能力。当前最值得优先投入的是配置结果的可信度、跨模块操作的连续性、异常后的修复入口，以及可复现的运行性能验证。九种设计器、审批、外数、数据库、模型、版本回滚等均有实际实现，不能作为“缺失功能”重新建设。[^S1][^S2]

本轮已完成四项相互关联的改进：工作台状态不再误报就绪；刷新失败清除旧结果、过期响应不能覆盖新结果；最近执行失败或统计证据不完整时不再判定运行已就绪；两个新建规则入口使用同一套业务场景说明。后续需求见[功能优化计划与需求](E:/workspace/tianshu-decision-engine/docs/research/2026-09-13-optimization-requirements.md)。

## 1. 评估范围与可信边界

评估基于 2026-09-13 的本地 `master` 工作区，起始提交为 `f5fa537`。研究依据为当前代码、接口、测试和本地页面，不沿用历史规划文档中的任务或优先级。初始工作区无未提交变更，本轮未切换分支、未提交或推送。

本次采用全局结构检查与关键业务链路深读相结合的方式。重点阅读项目工作台、规则创建、变量来源、SQL 查询、生命周期、校验报告、规则测试、日志、编译执行、冻结制品、并发解析和客户端缓存。页面手工检查覆盖项目工作台、项目规则、规则管理、变量管理及数据库变量表单；自动浏览器测试覆盖更广的管理页、设计器及引导流程。没有对每条业务分支逐一人工操作，也没有开展真实业务人员可用性访谈。

| 证据级别 | 含义 | 本轮适用内容 |
|---|---|---|
| 已复现 | 修改前用测试得到失败结果，修改后通过 | 工作台就绪误报、刷新竞态、失败执行被标记 READY |
| 页面观察 | 本地真实 Vue 页面、模拟 API 数据下观察 | 规则类型说明、弹窗布局、变量表单、列表密度 |
| 代码确认 | 通过当前实现和调用链确认 | SQL 前置校验、项目资源统计、制品解码、缓存淘汰 |
| 待验证假设 | 已发现可能的成本，但尚无足够实测支撑 | 大制品解码对延迟的贡献、大项目工作台负载、真实业务配置耗时 |

本机 MySQL、Redis 均未在默认端口监听，Docker 状态查询没有正常返回。后端启动在 MySQL 连接处失败，因此没有完成真实数据库下的新建、审批、发布、SDK 同步闭环，也没有真实端到端容量数字。模拟 API 成功只能证明页面与请求交互，不能证明数据库持久化、鉴权或发布生效。

## 2. 产品对象与业务流程

### 2.1 用户能力应按工作内容区分

“非技术人员”不能简单等同于不会 SQL。业务策略人员应能独立配置条件、动作和评分；具备 SQL 能力的分析人员应能直接编辑查询。数据库连接、供应商鉴权、PMML/ONNX 运行时和脚本扩展仍需要明确的技术协作边界。减少的是重复理解、跨页找资源和错误定位成本，而不是取消这些能力。

| 使用者 | 主要任务 | 应独立完成的内容 | 需要协作的边界 |
|---|---|---|---|
| 业务策略人员 | 调整准入、评分、结果分支 | 选变量、编条件、配置结果、准备测试、解释差异 | 业务口径、数据源能力变更 |
| SQL 型分析人员 | 定义数据库变量与结果映射 | 写 SQL、绑定参数、预览记录、选择结果字段 | 账号权限、网络、查询容量 |
| 审批人员 | 判断变更影响与测试充分性 | 看变更摘要、受影响资源、测试证据和发布对象 | 技术故障、跨团队依赖 |
| 接入与运维人员 | 发布、同步、调用与排障 | 项目鉴权、制品部署、SDK 状态、容量与恢复 | 业务规则本身的正确性 |

### 2.2 产品主线

```mermaid
flowchart LR
  P[选择项目] --> D[准备字段和数据来源]
  D --> R[选择类型并设计规则]
  R --> T[样例与预期结果验证]
  T --> A[校验与审批]
  A --> V[发布确定修订与制品]
  V --> E[服务端或 SDK 执行]
  E --> L[日志追踪与运行分析]
  L --> R
```

这条主线已经存在，但分布在不同模块。用户仍需判断“资源已建立”“审批已通过”“规则已发布”“本次执行成功”分别意味着什么。工作台的总状态只能总结它实际检查过的事项；不能把资源数量、测试场景数量或一次执行记录解释成完整上线验收。

一级菜单平铺继续作为导航约束。建议在当前项目上下文内改善下一步入口、返回位置与草稿恢复，保持资源管理入口直接可达。SQL 也继续直接配置，配套增加参数表单、返回结构预览和错误解释。

## 3. 项目结构与已实现能力

### 3.1 架构判断

| 模块 | 当前职责 | 优化应落在何处 |
|---|---|---|
| `rule-engine-model` | 实体、DTO、执行与制品数据结构 | 统一可解释的状态和错误定位契约 |
| `rule-engine-core` | 九类模型编译、QLExpress 执行、表达式与结果处理 | 保持编译语义一致，补边界测试与微基准 |
| `rule-engine-server` | 管理 API、审批、发布、变量解析、运行日志 | 资源可用性判断、跨模块流程、制品加载和统计查询 |
| `rule-engine-client` | 同步、进程内规则缓存、执行与日志上报 | 同步收敛、缓存压力、日志积压的可观测性 |
| `rule-engine-example` | 接入示例 | 作为真实 SDK 联调链路的一部分 |
| `rule-engine-builder-ui` | Vue 控制台、设计器、可视化配置和测试 | 术语、状态、配置引导、异常恢复及密度 |

前后端独立构建，管理端通过 API 与服务端交互；客户端不直接访问管理数据库。规则执行需要同时理解设计态、已发布态和被冻结的依赖。优化应沿现有服务与组件职责实施，不应另建一套绕过生命周期的快捷发布流程。[^S2][^S3]

引用关系继续使用资源 ID，输出引用继续显式标注 `ref_type`；变量编码、模型编码、字段名称按用户输入保存。类型说明等帮助信息只影响展示，不改提交值、不自动推导业务编码。

### 3.2 功能覆盖矩阵

下表表示存在相应实现，不代表每项均已通过真实部署验收。

| 领域 | 当前能力 | 主要完善方向 |
|---|---|---|
| 项目与导航 | 项目工作台、项目范围、会话页签、令牌与接口说明 | 就绪口径、跨页返回、权限匹配 |
| 规则与九类设计器 | 表、树、流、规则集、交叉表、评分卡、复杂矩阵及脚本 | 业务选型、命中语义说明、校验定位 |
| 变量与数据对象 | 多来源变量、对象字段、常量、导入、字段校验 | 同义术语收敛、取值链路透明、缺依赖补齐 |
| 外数 | API 参数、鉴权、稳定性策略、条件和在线预览 | 默认行为解释、失败结果语义、配置成本 |
| 数据库 | 连接、只读查询、占位符参数、数据库变量 | 查询页与变量页的参数体验一致 |
| 名单 | 库、记录、匹配及审批 | 字段映射和未命中/失败的差异说明 |
| 模型与函数 | PMML/ONNX、输入输出、测试、版本、函数扩展 | 业务操作与运行时配置的交接说明 |
| 测试 | 按字段生成输入、样例、批量执行与追踪 | 预期断言、覆盖依据、绑定确切修订 |
| 治理与制品 | 审批、依赖检查、冻结、发布、回滚、绑定部署 | 审批证据与校验结果直接引导修复 |
| 日志与看板 | 调用来源、追踪、分位耗时、业务统计、账单 | 业务结论与技术失败分别解释 |
| 血缘与实验 | 引用展示、分流、版本操作 | 动态引用边界与实验业务效果验证 |
| 账户与权限 | 菜单、路由、按钮及后端权限治理 | 入口能力与实际权限一致，避免无解释跳转 |

九种编排形态不宜全部简化成一种：决策表强调多行条件与动作，树强调分支路径，流强调步骤与调用，规则集强调命中策略，评分卡强调累计评分，交叉表强调维度组合。核心编译器与设计器均已分开实现。新建时用业务描述帮助选择，比新增设计器或隐藏高级入口更符合现状。[^S2][^S4]

## 4. 问题清单与首批改进

P1 表示可能误导状态判断、阻断核心配置或影响正确性；P2 表示明显增加配置和排障成本；P3 表示需要测量后决定的优化。本轮未发现有充分证据需要立即按 P0 停用引擎的问题。

| 编号 | 问题与触发条件 | 影响与证据 | 优先级 | 当前状态 |
|---|---|---|---|---|
| A01 | 工作台未加载、空结果、失败时剩余项为 0 | 原组件据此显示“当前已就绪”；回归测试复现 | P1 | 已修复，区分检查中、失败、未知、部分可用、已处理[^S5] |
| A02 | 工作台刷新失败或响应乱序 | 旧数据继续展示，旧请求覆盖新结果 | P1 | 已修复，清除旧结果、请求序号校验、显式重试[^S6] |
| A03 | 已有运行记录但执行失败 | 原后端只检查执行次数和记录存在，仍返回 READY | P1 | 已修复，失败提示排查；缺成功统计/记录时返回 UNAVAILABLE[^S7] |
| A04 | 从项目或规则列表新建规则 | 九个类型仅有名称，业务人员无法判断适用场景 | P2 | 已补统一的场景、例子与下一步说明[^S4] |
| A05 | 只有全局共享字段，或存在停用字段 | 工作台只统计项目字段且仅排除删除状态；资源数量不能证明规则引用可用 | P1 | 待完善，改为当前规则实际依赖检查[^S7] |
| A06 | 任意规则/版本产生执行记录 | 工作台按项目编码聚合，未按指定制品、修订或测试/生产环境筛选 | P1 | 本轮已收窄展示措辞；精确的生效验证仍待实现[^S7] |
| A07 | 发布前校验出现失效引用或结构错误 | 报告显示 message、code、path，缺少直接定位编辑位置的动作 | P1 | 待完善，错误关联稳定资源和设计器位置[^S8] |
| A08 | 受限角色使用工作台快捷操作 | 快捷动作未按能力过滤，路由无权限时转向首个可访问页，用户不清楚原因 | P2 | 代码确认入口不一致；待真实角色场景复核[^S5][^S9] |
| A09 | 数据库变量需要 SQL 参数和结果提取 | 查询页有逐项参数表单，变量页仍需要 JSON 数组、结果路径与 JSON 样例 | P1 | 页面与代码确认，待统一参数编辑能力[^S10][^S11] |
| A10 | 来源列表为“0 个可用” | 能进入来源配置，但缺少创建依赖、带上下文返回并恢复草稿的直接入口 | P2 | 页面观察；需区分无资源、停用、未审批、无权限和加载失败[^S10] |
| A11 | SQL 含注释、末尾分号或字符串中的分号 | 前端要求以 SELECT 开头且任意分号均拒绝，拦截部分常见只读写法 | P2 | 代码确认前端行为；需核对后端口径后统一，不能放开多语句[^S11] |
| A12 | 有测试场景即被理解为测试充分 | 工作台统计场景数量，不代表已执行、断言成功、覆盖完整或适用于当前修订 | P1 | 待新增修订绑定的测试证据，保留现有样例与批量执行[^S7][^S12] |
| A13 | 1280×720 查看变量等宽表 | 标识、名称、脚本名称、来源、状态及多操作同屏竞争；固定列挤压中间字段 | P2 | 真实页面观察；待列优先级、密度和键盘可用性验证[^S10] |
| A14 | 创建规则后需继续配置 | 两个入口保存后返回列表，需要再辨认新规则并进入详情/设计 | P2 | 代码确认；待提供“创建并继续配置”，明确设计态与发布态[^S6][^S4] |
| A15 | 大制品被频繁执行 | `executePublished` 调用快照加载，加载会读制品、解码校验、解析组件与绑定 | P3 | 潜在热点，尚无真实负载下的占比测量[^S3][^S13] |
| A16 | 项目资源或日志量增长 | 工作台逐项 count、读取规则列表及最近日志；接口等待多次数据库查询 | P3 | 调用结构已确认，延迟影响待测；禁止先盲目加缓存[^S7] |
| A17 | SDK 缓存达到容量上限 | L1 从 keySet 任取一项淘汰，不保护高频规则；还需观察同步与淘汰交互 | P3 | 策略已确认，是否产生抖动待测[^S14] |
| A18 | 大配置页面持续扩展 | 变量页 5773 行、API 详情 4032 行，多个模式和状态集中在单文件 | P2 | 维护成本信号，不等于性能瓶颈；围绕真实需求逐步抽共享组件[^S10][^S15] |

### 4.1 首批修复的行为边界

工作台新增刷新入口，刷新期间禁止重复点击，加载失败时可在原位置重试。状态更新通过 `aria-live` 提示；帮助文案沿用现有颜色和间距变量，主说明、业务例子与后续步骤分层展示。这些选择遵循状态可感知与错误可修复原则，但本次并未完成整站无障碍合规测试。[^W1][^W2]

执行检查保持现有 DTO 与状态枚举。未发布仍为 BLOCKED；发布状态不可读为 UNAVAILABLE；已发布但近 24 小时无记录为 ACTION_REQUIRED；最近一次执行失败或窗口内有失败为 ATTENTION；统计或最近记录不可读为 UNAVAILABLE；现有证据未发现失败时为 READY。READY 的含义是“当前检查未发现失败”，不意味着任何业务结论必然正确。

类型帮助仅解释当前选项。两个入口仍提交原有 `modelType` 值，保留输入的混合大小写编码、项目 ID 和名称。没有生成规则、变量或业务结论，也没有改变规则的命中策略。

## 5. 配置复杂程度评估

以下为根据界面和实现给出的定性判断，不是用户实验得分。需要通过新手任务完成率、求助次数和耗时验证。

| 场景 | 当前复杂度 | 主要认知负担 | 期望改善 |
|---|---|---|---|
| 请求字段 + 简单决策表 | 中 | 编码/名称/脚本名称、类型选择、草稿与发布区别 | 类型说明、字段释义、创建后继续配置 |
| 分段评分和风险等级 | 中到高 | 多项是否累计、边界是否含端点、未命中如何处理 | 同屏规则摘要与边界样例 |
| 数据库变量 | 高 | SQL + 参数数组 + 结果路径 + 异常策略 + 样例 JSON | 保留 SQL；参数逐项绑定、结果字段可选择 |
| 外数接入 | 高 | 鉴权、请求映射、成功条件、重试、缓存和计费 | 已有分层基础上增加有效配置摘要与异常预演 |
| 多步骤决策流 | 高 | 节点依赖、执行顺序、分支、调用返回和终止范围 | 错误定位、局部追踪、关键行为解释 |
| 模型接入 | 高，属于专业协作 | 制品格式、输入形状、字段匹配和运行时 | 模型工程人员配置运行时，业务人员使用可解释输入输出 |
| 审批与上线 | 中到高 | 多类版本、冻结依赖、审批草稿与发布动作 | 一次展示“审批对象—验证证据—生效对象” |

不建议以减少点击数作为唯一目标。涉及确认调用成本、修订版本和异常行为的步骤有实际作用。应优先减少重复填写、丢失上下文和无法理解的错误；保留会影响业务结果的关键选择。

## 6. 执行效率与验证结论

### 6.1 已有优化机制

QLExpress 执行已启用脚本缓存。变量解析以依赖就绪波次组织工作，可用有界线程池并发处理；配置默认并行度为 4，队列满时使用调用线程执行任务。SDK 有进程内 L1 缓存和完整快照替换；HTTP 日志上报有缓冲、重试及丢弃/失败计数。这些能力应继续验证，而不是列成全新建设项。[^S16][^S17][^S14][^S18]

Redis Pub/Sub 本身是至多一次交付，断连期间可能丢失通知；当前 SDK 也已有定时全量同步。因此应测试“断连—重连—全量同步后版本收敛”，不能仅凭 Pub/Sub 的交付特性认定当前 SDK 完全无法恢复。[^W3][^S19]

### 6.2 本地微基准

在 i9-13900KF、JDK 17.0.19、Windows 本机执行一个包含年龄与分数判断的缓存热脚本；每种模式预热 5000 次，测量 20000 次，包含输入 Map 构建与返回正确性检查。两种模式按关闭追踪、开启追踪的顺序在同一 JVM 执行。

| 模式 | 平均耗时（μs） | P50（μs） | P95（μs） | P99（μs） |
|---|---:|---:|---:|---:|
| 关闭追踪 | 1.360 | 1.200 | 1.500 | 3.000 |
| 开启追踪 | 3.937 | 3.400 | 5.600 | 11.500 |

这是单次进程内观察，不是 JMH 基准，也没有置信区间。它不包含 HTTP、鉴权、MySQL、制品解码、外数、PMML/ONNX、日志入库和并发排队，不能换算成系统 QPS 或生产延迟目标。可支持的结论是：简单热脚本的纯执行成本很低，追踪模式需要单独计量；不能据此宣称所有规则执行都快，或决定关闭线上追踪。复现程序与命令见[验证证据](E:/workspace/tianshu-decision-engine/docs/research/2026-09-13-evidence/verification.md)。

### 6.3 应建立的性能矩阵

| 测量面 | 场景 | 必须记录 | 优化触发依据 |
|---|---|---|---|
| 核心编译与执行 | 九类型；冷编译/热执行；追踪开/关；不同节点数量 | 编译时间、执行分位耗时、结果一致性、分配量 | 实测超过约定预算或出现非线性增长 |
| 制品加载 | 小/中/大包；嵌套规则；部署绑定变化 | 查询、解码校验、解析耗时及内存 | 加载成本在端到端耗时中占明显比例 |
| 来源解析 | 独立/串行依赖；缓存命中；超时、重试和降级 | 外部请求数、排队时间、真实取值结果、计费次数 | 重复调用、排队超预算或故障放大 |
| SDK | 断连、乱序推送、快照失败、缓存超过上限 | 版本收敛时间、缓存未命中、错误执行、日志丢弃 | 失效窗口、缓存抖动或不可解释丢日志 |
| 端到端 | 真实服务、代表性规则、多个并发档位 | P50/P95/P99、错误率、吞吐、CPU/内存、数据库与队列 | 双方确认的 SLO，不以本地微基准替代 |

仓库已有容量检查脚本，应复用其预热、请求超时、分位延迟和失败门槛，增加代表性场景及可复现报告。[^S20]

## 7. 验证状态

最终结果、命令和限制集中记录在[验证证据](E:/workspace/tianshu-decision-engine/docs/research/2026-09-13-evidence/verification.md)。本轮新增测试先观察失败，再验证修复；没有删除有效测试或放宽断言来获得通过。

后端默认 `mvn test` 首次遇到 Windows JDK 临时 Unix 域套接字连接错误，发生在 SDK 的本地 HTTP 测试服务创建阶段。根据当前 JDK 源码明确的临时目录设置方式，仅在复跑命令中指定了工作区目录；未修改 POM、系统 JDK 或长期环境变量。该设置下全量 1335 项测试中 1319 通过、16 跳过，0 failures、0 errors。

后端构建通过；后端实际启动受本机 MySQL 不可用阻塞。UI 手工操作使用真实开发服务器与模拟 API，页面控制台无 error；完成后已主动停止开发和模拟服务。真实部署、实际审批持久化、发布后调用、断连收敛及业务人员验收仍是后续交付条件。

## 8. 下一阶段决策

先完成可信状态、错误定位和数据库变量配置连续性，再建设绑定修订的测试证据和明确的发布验证。设计器与运行性能优化依靠可复现的测量推进。大文件拆分跟随真实改动实施，避免一次性重写所有页面。

本轮对代码事实与计划建议做了区分。18 项发现并不意味着已经完成 18 项功能改造；A01—A04 为首批落地内容，A05—A18 是有依据的完善方向或验证任务。详细状态、需求、依赖与验收标准以新计划表为准。

## 9. 来源

代码来源均为本地仓库当前版本，访问日期 2026-09-13；行号为本轮编辑后位置。外部资料仅用于交互原则和运行机制校对，不作为天枢功能存在与否的依据。

[^S1]: [README 功能总览](E:/workspace/tianshu-decision-engine/README.md:24)。
[^S2]: [Maven 模块与依赖](E:/workspace/tianshu-decision-engine/pom.xml:13)；[九类编译器注册](E:/workspace/tianshu-decision-engine/rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleCompileService.java)；[前端导航](E:/workspace/tianshu-decision-engine/rule-engine-builder-ui/src/layout/layoutState.js:7)。
[^S3]: [发布态执行链路](E:/workspace/tianshu-decision-engine/rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleExecuteService.java:239)。
[^S4]: [规则创建入口](E:/workspace/tianshu-decision-engine/rule-engine-builder-ui/src/views/rule/RuleList.vue:246)；[统一类型帮助](E:/workspace/tianshu-decision-engine/rule-engine-builder-ui/src/components/rule/RuleModelTypeHelp.vue)；[评分卡编译](E:/workspace/tianshu-decision-engine/rule-engine-core/src/main/java/com/hengshucredit/rule/core/compiler/ScorecardCompiler.java:29)。
[^S5]: [工作台状态与动作](E:/workspace/tianshu-decision-engine/rule-engine-builder-ui/src/components/ProjectWorkbenchOverview.vue:185)；[状态回归测试](E:/workspace/tianshu-decision-engine/rule-engine-builder-ui/tests/unit/components/projectWorkbenchOverview.spec.js)。
[^S6]: [项目详情加载与跳转](E:/workspace/tianshu-decision-engine/rule-engine-builder-ui/src/views/project/ProjectDetail.vue:526)；[刷新回归测试](E:/workspace/tianshu-decision-engine/rule-engine-builder-ui/tests/unit/views/projectDetail.spec.js)。
[^S7]: [工作台后端统计与检查](E:/workspace/tianshu-decision-engine/rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/ProjectWorkbenchService.java:55)；[后端回归测试](E:/workspace/tianshu-decision-engine/rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/ProjectWorkbenchServiceTest.java)。
[^S8]: [发布前校验报告](E:/workspace/tianshu-decision-engine/rule-engine-builder-ui/src/components/rule/RuleValidationReport.vue:1)。
[^S9]: [路由权限与无权限跳转](E:/workspace/tianshu-decision-engine/rule-engine-builder-ui/src/router/authGuard.js:65)。
[^S10]: [变量管理与来源配置](E:/workspace/tianshu-decision-engine/rule-engine-builder-ui/src/views/variable/VariableList.vue:1500)；[来源选择器](E:/workspace/tianshu-decision-engine/rule-engine-builder-ui/src/views/variable/components/VariableSourceSelector.vue)。
[^S11]: [数据库查询参数与只读前置检查](E:/workspace/tianshu-decision-engine/rule-engine-builder-ui/src/views/database/DatabaseList.vue:1253)。
[^S12]: [规则测试与批量执行](E:/workspace/tianshu-decision-engine/rule-engine-builder-ui/src/views/test/RuleTest.vue:965)。
[^S13]: [冻结制品加载与解码](E:/workspace/tianshu-decision-engine/rule-engine-server/src/main/java/com/hengshucredit/rule/server/artifact/ArtifactRuntimeSnapshotService.java:46)。
[^S14]: [SDK 缓存与快照替换](E:/workspace/tianshu-decision-engine/rule-engine-client/src/main/java/com/hengshucredit/rule/client/cache/L1MemoryCache.java:31)。
[^S15]: [API 配置与检查清单](E:/workspace/tianshu-decision-engine/rule-engine-builder-ui/src/views/datasource/ApiDetail.vue:37)。
[^S16]: [QLExpress 缓存与追踪选项](E:/workspace/tianshu-decision-engine/rule-engine-core/src/main/java/com/hengshucredit/rule/core/engine/QLExpressEngine.java:38)。
[^S17]: [来源解析依赖波次](E:/workspace/tianshu-decision-engine/rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/VariableSourceResolver.java:131)；[有界执行器](E:/workspace/tianshu-decision-engine/rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/SourceResolutionExecutor.java:22)。
[^S18]: [HTTP 日志缓冲与状态计数](E:/workspace/tianshu-decision-engine/rule-engine-client/src/main/java/com/hengshucredit/rule/client/log/HttpLogReporter.java:40)。
[^S19]: [SDK 定时全量同步](E:/workspace/tianshu-decision-engine/rule-engine-client/src/main/java/com/hengshucredit/rule/client/RuleEngineClient.java:95)。
[^S20]: [容量测试入口](E:/workspace/tianshu-decision-engine/scripts/quality-gates/run-capacity-gate.mjs)；[引导流程浏览器回归](E:/workspace/tianshu-decision-engine/rule-engine-builder-ui/tests/e2e/guided-workflows.spec.js)。
[^W1]: W3C WAI，[Understanding SC 4.1.3: Status Messages](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html)，访问于 2026-09-13。
[^W2]: W3C WAI，[Understanding SC 3.3.3: Error Suggestion](https://www.w3.org/WAI/WCAG22/Understanding/error-suggestion.html)，访问于 2026-09-13。
[^W3]: Redis，[Redis Pub/sub](https://redis.io/docs/latest/develop/pubsub/)，交付语义与断连行为，访问于 2026-09-13。
