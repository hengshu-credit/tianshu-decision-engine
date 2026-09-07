<p align="center">
  <img src="https://hengshucredit.com/images/hengshucredit_animated.svg" alt="衡枢真信" width="120"><br>
  <strong>天工开物，枢衡定策</strong><br>
  <sub>Creating Possibilities. Calibrating Decisions.</sub>
</p>
<p align="center">
  <img src="docs/readme/brand-values.svg" alt="鉴真伪 · 斟信用 · 衡风险 · 枢定策" width="640">
</p>

# 天枢决策引擎

天枢决策引擎是一套面向风控与业务决策的可视化平台，将变量、名单、外部数据、模型和函数连接到统一的规则流程。业务人员可以通过九类设计器配置决策逻辑，使用样例验证结果，完成审核发布，并从执行日志追溯每次决策的条件、动作和输出。

平台基于 **Spring Boot 3.5、QLExpress 4、Vue 3 和 Element Plus**，提供独立部署的管理控制台、服务端 API 和 Java 客户端 SDK，支持服务端执行与业务应用内执行。

<p align="center">
  <a href="#功能总览">功能总览</a> ·
  <a href="#功能演示">功能演示</a> ·
  <a href="#九类规则配置与测试追踪">规则配置与追踪</a> ·
  <a href="#主题与工作台">主题与工作台</a> ·
  <a href="#部署与系统接入">部署与接入</a>
</p>

## 功能总览

| 能力 | 说明 |
|------|------|
| 可视化决策 | 决策表、决策树、决策流、规则集、交叉表、评分卡、复杂交叉表、复杂评分卡和 QL 脚本 |
| 数据准备 | 输入、计算、常量、API、数据库和名单变量；Java 实体、JSON、DDL 导入；数据对象字段与变量引用 |
| 模型与函数 | 模型字段配置、执行测试、版本管理；PMML/ONNX 推理；QLExpress、Java 类与 Spring Bean 函数 |
| 测试与追踪 | 按规则生成输入结构，收藏测试用例，查看命中条件、分支路径、评分明细与执行追踪 |
| 发布与治理 | 草稿、审核、批准、发布、下线；版本对比与回滚；依赖校验和不可变决策制品 |
| 运行分析 | 数据看板、血缘分析、分流实验、规则及外部资源调用日志、账单明细与汇总 |
| 权限与体验 | 账户、角色和权限覆盖；多页签工作台；白天/夜间主题、自定义渐变与导航布局 |
| 系统集成 | 项目调用鉴权、开放接口说明、Java SDK、规则同步与 Redis 变更推送 |

## 功能演示

以下界面截图采集于 **2026-09-07**，来自当前前端页面。管理页面采用固定业务样例；九类规则的测试结果与表达式追踪来自本地 QLExpress 引擎执行同一份示例配置，演示数值不代表生产业务指标。

### 数据看板

按项目、规则编码/名称和时间查询进件数、通过率、人审率、设备数及分布情况，并查看资源调用、执行耗时、规则集命中、审批和账单。各模块按当前账户权限展示。

![数据看板：进件决策与分布分析](docs/readme/dashboard.png)

点击“看板设置”可关联统计字段，配置个人默认与项目覆盖；未配置项逐项继承默认值。地图支持中心经纬度、缩放和视角恢复；非法坐标排除出地图统计，原始日志保持不变。

### 项目与规则管理

项目集中管理规则及相关资源，提供项目工作台、访问鉴权和接口说明。规则列表支持进入设计、执行测试与生命周期管理。

![项目管理](docs/readme/project.png)

<details>
<summary>查看项目工作台与规则列表</summary>

![项目工作台](docs/readme/project-detail.png)

![规则列表](docs/readme/rules.png)

</details>

### 变量与数据对象

在同一入口维护变量、数据对象、常量和字段校验。API、数据库与名单变量支持在线测试和取数详情，便于核对参数、返回值和依赖关系。

![变量管理](docs/readme/variable.png)

数据对象支持 Java 实体、JSON、DDL 导入及手动配置。展开对象可按层级维护字段，在“引用变量”中选择已有变量：调用方未传入对象字段时，从引用变量取值；已显式传入时优先使用当前字段值。列表元素内的字段不直接引用变量，可在 LIST 字段上整体引用。

![数据对象字段配置](docs/readme/data-object.png)

字段、变量和模型通过稳定 ID 关联，编码与导入字段名原样保留，名称调整不会依赖文本猜测重建引用。

### 名单管理

维护名单库和记录，支持导入、导出与匹配日志，可用于风险名单、准入名单及业务标签判断。

![名单管理](docs/readme/lists.png)

### 外数与数据库

外数管理集中配置 API 数据源、鉴权、请求与响应映射、超时、重试、缓存及计费。请求头、Query 和映射支持表单与 JSON 配置；复杂签名或响应处理可使用 QL 脚本。

![外数管理](docs/readme/datasource.png)

数据库管理提供连接配置、连接测试、查询 SQL、结果映射和调用日志。SQL 可直接配置，数据源应使用只读账号和查询类语句。

![数据库管理](docs/readme/database.png)

### 模型与函数

模型管理提供入参、出参、执行测试、版本和调用日志。PMML/ONNX 模型按声明精确匹配字段；ONNX 支持按模型选择 CPU 或 CUDA 运行设备，CUDA 需部署对应运行环境。

![模型管理](docs/readme/models.png)

函数管理支持 QLExpress、Java 类和 Spring Bean，把可复用计算封装后用于规则与表达式。

![函数管理](docs/readme/functions.png)

### 血缘与分流实验

血缘分析从变量、规则、模型、外数、数据库和名单等资源查看上下游依赖，辅助评估变更影响。结构化引用可追踪，脚本中的复杂动态引用仅作静态识别。

![资源血缘分析](docs/readme/lineage.png)

分流实验支持冠军、挑战和测试组，可按条件或流量路由，并查看实验配置、执行情况与历史版本。

![分流实验](docs/readme/experiment.png)

### 审批与账户权限

审批管理集中展示资源变更申请，支持草稿、依赖预检、提交、通过、驳回、取消及历史版本恢复。规则发布冻结依赖与制品摘要，后续编辑不会直接改写历史发布内容。

![审批管理](docs/readme/approval.png)

账户可分配多个角色，并对单项权限设置允许、拒绝或继承；明确拒绝优先，菜单与后端接口共同检查最终权限。

![账户与角色管理](docs/readme/account.png)

### 执行日志与账单

执行日志关联规则、耗时、输入输出及追踪信息，支持回看具体决策。外数 API、数据库、名单和模型调用分别记录其请求与响应内容。

![规则执行日志](docs/readme/logs.png)

账单管理支持引擎执行、API 调用和数据库调用的计费配置、明细与聚合汇总。

![账单管理](docs/readme/billing.png)

## 九类规则配置与测试追踪

规则设计的一般流程为：**配置输入输出 → 编辑条件与动作 → 保存并检查 → 执行测试 → 审核发布**。“仅保存草稿”与“保存并检查”分别表示内容保存和校验；保存、测试都不会自动发布线上规则。

规则测试页面可加载输入字段、手动填写样例、收藏固定用例并批量执行。在“表达式追踪树”页签中，系统按规则类型展示命中行、分支路径、矩阵单元格、评分明细或脚本表达式。可以按状态与关键字筛选，同时查看原始 JSON 追踪。

以下示例以人脸核验为业务背景，`livenessScore` 表示活体检测分，`faceSimilarity` 表示人脸相似度，`verified` 与 `riskLevel` 表示决策输出。每类规则均提供对应配置及测试截图。

### 1. 决策表

按行配置条件组合与动作，并选择首次命中、全部命中或唯一命中策略。示例在活体分 ≥ 0.95 且相似度 ≥ 0.90 时输出通过与低风险；输入 `0.98 / 0.936` 后，该行命中。

![决策表：条件与动作配置](docs/readme/rule-table-config.png)

<details open>
<summary>测试追踪：命中行、条件与输出</summary>

![决策表测试追踪](docs/readme/rule-table-trace.png)

</details>

### 2. 决策树

通过开始、判断、动作和结束节点组织分支，适合层次清晰的准入判断。树分支不允许汇合。示例沿活体与相似度达标的路径执行“核验通过”，其余路径转人工复核。

![决策树：分支与动作配置](docs/readme/rule-tree-config.png)

<details>
<summary>测试追踪：命中路径与跳过分支</summary>

![决策树测试追踪](docs/readme/rule-tree-trace.png)

</details>

### 3. 决策流

通过图形节点编排条件、动作和公共后续流程，支持无环分支汇合。示例的“通过”和“复核”动作完成后进入公共结束节点，便于将不同分支接入统一后续处理。

![决策流：分支汇合配置](docs/readme/rule-flow-config.png)

<details>
<summary>测试追踪：执行路径与动作结果</summary>

![决策流测试追踪](docs/readme/rule-flow-trace.png)

</details>

### 4. 规则集

配置独立规则的名称、优先级、条件及动作。先按优先级降序、再按页面顺序执行；串行模式首次命中后停止，并行模式收集全部命中项。示例先判断相似度不足，再判断活体达标，返回命中的 `FACE_LIVENESS_PASS`。

![规则集：优先级与规则条件配置](docs/readme/rule-ruleset-config.png)

<details>
<summary>测试追踪：逐条判断与命中规则</summary>

![规则集测试追踪](docs/readme/rule-ruleset-trace.png)

</details>

### 5. 交叉表

配置行变量、列变量及交叉单元格的结果，适合有限枚举组合。简单交叉表按行列值匹配；示例用活体分 `0.95` 与风险等级 `LOW` 定位返回 `true` 的单元格。

![交叉表：行列与结果配置](docs/readme/rule-cross-config.png)

<details>
<summary>测试追踪：行列匹配与单元格结果</summary>

![交叉表测试追踪](docs/readme/rule-cross-trace.png)

</details>

### 6. 评分卡

设置初始分、评分项条件、分数和权重，将命中项的加权分累加到总分。示例初始分为 0，活体分达标增加 60 分，权重为 1，输出 `totalScore = 60`。

![评分卡：评分项与权重配置](docs/readme/rule-score-config.png)

<details>
<summary>测试追踪：命中评分项与总分</summary>

![评分卡测试追踪](docs/readme/rule-score-trace.png)

</details>

### 7. 复杂交叉表

行列可由多个维度及分段组合构成，适合区间与枚举共同参与的决策矩阵。示例按活体分“达标/待复核”和风险等级“低风险/需复核”划分组合；`0.98 / LOW` 命中通过单元格。

![复杂交叉表：维度分段与矩阵配置](docs/readme/rule-cross-adv-config.png)

<details>
<summary>测试追踪：维度判断与矩阵命中</summary>

![复杂交叉表测试追踪](docs/readme/rule-cross-adv-trace.png)

</details>

### 8. 复杂评分卡

按维度组配置评分维度、条件、分数和权重，并映射等级区间。每个维度只采用第一条命中规则，维度间累加。示例活体分达标获得 100 分，落入 `[90, 101)`，输出等级 `LOW`。

![复杂评分卡：维度评分与等级区间配置](docs/readme/rule-score-adv-config.png)

<details>
<summary>测试追踪：维度得分、总分与等级</summary>

![复杂评分卡测试追踪](docs/readme/rule-score-adv-trace.png)

</details>

复杂评分卡等级区间采用左闭右开 `[min, max)`；例如 90 分属于 `[90, 101)`，不属于 `[60, 90)`。边界配置应与业务分数范围一致。

### 9. QL 脚本

直接编写 QLExpress 脚本，适合需要灵活表达的逻辑；可从变量选择器插入字段并保留引用关系。示例执行活体与相似度判断，显式返回 `verified` 和 `riskLevel`。

![QL 脚本配置](docs/readme/rule-script-config.png)

<details>
<summary>测试追踪：表达式求值与分支结果</summary>

![QL 脚本测试追踪](docs/readme/rule-script-trace.png)

</details>

## 主题与工作台

点击右上角**账户菜单 → 主题设置**，即时预览界面外观。支持白天/夜间模式、8 个纯色与 4 个渐变预设、自定义纯色、两色/三色渐变、线性/径向渐变、深浅导航、流式/定宽内容区和色弱模式。

![主题设置：白天模式与预设](docs/readme/theme-light.png)

<details>
<summary>查看夜间模式、自定义渐变与顶部导航</summary>

![主题设置：夜间模式](docs/readme/theme-dark.png)

![主题设置：自定义三色径向渐变](docs/readme/theme-custom.png)

![主题设置：顶部平铺导航](docs/readme/theme-top.png)

</details>

菜单可放在左侧或顶部，一级入口始终平铺；顶部空间不足时横向滚动访问。多页签工作台支持在业务页面间切换。

点击“保存设置”持久化；点击“取消”、关闭按钮或按 Esc 恢复打开前的设置。“恢复默认”先预览，再保存。启用登录时设置按账户保存，可跨设备恢复；未启用登录时保存在当前浏览器。设计器未保存内容支持同一浏览器会话内恢复，恢复后仍需保存并检查。

## 部署与系统接入

| 组件 | 职责 | 默认端口 |
|------|------|----------|
| `rule-engine-builder-ui` | Vue 控制台，独立构建部署 | 9090（开发） |
| `rule-engine-server` | 管理、同步、执行与日志 API | 8080 |
| `rule-engine-core` / `rule-engine-model` | 规则编译执行、公共实体与 DTO | — |
| `rule-engine-client` | Java SDK，规则同步、缓存和执行 | — |
| `rule-engine-example` | SDK 集成示例 | 7070 |
| MySQL / Redis | 持久化配置、规则变更通知 | 3306 / 6379 |

运行环境为 JDK 17、Maven 3.6+、MySQL 8、Redis 和 Node.js 20.19+。前端构建产物独立部署，业务系统通过服务端同步规则，不直连管理数据库。

业务系统可通过开放 API 或 Java SDK 接入，按项目使用访问令牌、账号密码、API Key 或 HMAC-SHA256 鉴权。

```java
RuleResult result = ruleEngineClient.execute("face_threshold_table", requestMap);
```

SDK 通过 HTTP 同步规则与函数，并订阅 Redis 变更通知。`project-code` 用于项目路由，需与服务端项目编码一致；`app-name` 标识调用应用。规则依赖 API、数据库或名单取数时，使用服务端执行配置。

完整环境变量、启动命令、ONNX CPU/CUDA 配置和 SDK 接入示例见[部署与接入说明](docs/deployment.md)。

<details>
<summary>控制台登录界面</summary>

![控制台登录](docs/readme/login.png)

</details>

## 交流与许可证

| 微信 | 微信公众号 |
|:----:|:----------:|
| <img src="https://itlubber.art/upload/itlubber.png" alt="微信二维码" width="180"> | <img src="https://itlubber.art/upload/hengshucredit-com.png" alt="微信公众号二维码" width="180"> |
| itlubber | hengshucredit-com |

项目许可证见 [LICENSE](LICENSE)，第三方组件与交付要求见 [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)。包含 JPMML 的制品或网络服务按 AGPL-3.0 要求提供对应源代码、许可证和重建材料。

相关项目：[QLExpress](https://github.com/alibaba/QLExpress) · [qlexpress-rule](https://github.com/xiachongbu/qlexpress-rule)
