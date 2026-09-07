<p align="center">
  <img src="https://hengshucredit.com/images/hengshucredit_animated.svg" alt="衡枢真信" width="200">
</p>

<h3 align="center">

🔍 鉴真伪 · 📊 斟信用 · ⚖️ 衡风险 · 🎯 枢定策

</h3>



# 天枢决策引擎

> 天工开物，枢衡定策（Creating Possibilities. Calibrating Decisions）

> **重要提示**: 当前为预发布版，功能待增加，有部分功能逻辑还在修缮中 ......


天枢决策引擎是一套基于 Spring Boot 3.5、QLExpress 4、Vue 3 和 Element Plus 的可视化风控决策平台。系统面向业务人员提供规则项目、变量、名单、外数 API、外部数据库、模型、函数、规则测试、血缘分析、分流实验、执行日志和账单管理等能力；面向业务系统提供 `rule-engine-client` SDK，用于拉取、缓存并执行已发布规则。

本文为当前使用说明，更新于 **2026-09-07**，以仓库实际实现为准。本次重点补充[主题设置](#54-用户主题设置)、[数据看板](#715-数据看板)、[数据对象字段引用](#717-数据对象字段与变量引用)和[设计器保存与检查流程](#811-设计器保存检查与恢复)，并附功能演示截图。

历史架构与验证记录见[《天枢决策引擎当前实现研究报告》](docs/research/2026-07-17-tianshu-decision-engine-current-state.md)和[《天枢决策引擎生产就绪复盘与后续规划》](docs/research/2026-07-22-production-readiness-review.md)。两份报告分别记录 2026-07-17、2026-07-22 的状态，不代表本次重新执行的验收结论。


## 交流

|  微信 |  微信公众号 |
| :---: | :----: |
| <img src="https://itlubber.art/upload/itlubber.png" alt="itlubber.png" width="50%" border=0/> | <img src="https://itlubber.art/upload/hengshucredit-com.png" alt="hengshucredit-com.png" width="50%" border=0/> |
|  itlubber  | hengshucredit-com |

## 1. 功能总览

| 模块 | 主要能力 |
|------|----------|
| 数据看板 | 按项目、规则和时间统计进件决策、地图分布、资源调用、耗时、规则集命中、审批与账单；支持个人默认与项目覆盖字段映射 |
| 项目管理 | 管理规则项目、访问令牌和项目级接口说明 |
| 规则管理 | 新建、设计、编译、发布、下线和版本回滚规则 |
| 变量管理 | 管理输入、计算、常量、API、数据库、名单等变量；API/DB/名单变量支持在线测试和取数详情 |
| 名单管理 | 维护名单库、名单记录、导入导出和名单匹配日志 |
| 外数管理 | 配置外部 API 数据源、接口请求映射、响应映射、鉴权和调用日志 |
| 数据库管理 | 配置外部数据库连接池、测试连接、只读查询和数据库调用日志 |
| 模型管理 | 管理模型入参、出参、执行测试和模型调用日志 |
| 函数管理 | 管理 QLExpress 脚本、Java 类、Spring Bean 等函数 |
| 规则测试 | 按项目和规则加载输入字段，执行测试并查看追踪结果 |
| 血缘分析 | 从项目、变量、规则、模型、API、DB、名单等节点查看上下游依赖图 |
| 分流实验 | 配置冠军/挑战/测试组，按条件或流量执行实验 |
| 执行日志 | 查看服务端和客户端规则执行记录、耗时、结果和追踪树 |
| 账单管理 | 配置计费项、查看明细记录和聚合汇总 |
| 审批管理 | 统一处理资源变更草稿、依赖预检、提交、通过、驳回、取消、差异对比和历史版本恢复 |
| 账户管理 | 管理控制台账户、角色、角色权限和账户级允许/拒绝覆盖，按最终有效权限控制菜单与接口 |
| 主题设置 | 白天/夜间模式、预设与自定义纯色/渐变、左侧/顶部平铺菜单、深浅导航、内容宽度及色弱模式；支持预览、取消和保存 |
| 数据对象 | 从 Java 实体、JSON、DDL 导入或手动维护对象；分层编辑字段、引用已有变量，并将引用关系纳入审批与发布快照 |

## 2. 模块结构

| 模块 | 说明 | 默认端口 |
|------|------|----------|
| `rule-engine-model` | 公共实体、DTO 和数据库映射模型 | - |
| `rule-engine-core` | 规则编译器和 QLExpress 执行核心 | - |
| `rule-engine-server` | 管理端 REST API、同步接口、日志、外数和数据库服务 | 8080 |
| `rule-engine-client` | 业务系统 SDK，负责规则同步、L1 缓存和本地执行 | - |
| `rule-engine-example` | SDK 集成示例服务 | 7070 |
| `rule-engine-builder-ui` | Vue 3 控制台，独立构建和部署 | 9090 |
| `rule-engine-mysql` | MySQL 配置与初始化脚本 | 3306 |
| `rule-engine-redis` | Redis Pub/Sub 配置 | 6379 |

后端 Java 包名前缀统一为 `com.hengshucredit.rule.*`，数据库表名、Redis 频道和前端 API 路由保持业务语义不变。

## 3. 架构与运行链路

```mermaid
sequenceDiagram
  participant Biz as 业务代码
  participant C as RuleEngineClient
  participant L1 as L1 缓存
  participant HTTP as Server 同步 API
  participant Redis as Redis
  Biz->>C: Spring 注入后 start()
  C->>HTTP: 全量同步规则
  HTTP-->>C: CachedRule 列表
  C->>L1: 写入缓存
  C->>Redis: 订阅 projectCode 频道与 GLOBAL 广播
  loop 定时心跳（默认 5 分钟）
    C->>HTTP: 全量同步（兜底）
  end
  Biz->>C: execute(ruleCode, Map 或 DTO)
  C->>L1: get(ruleCode)
  alt 缓存未命中
    C->>HTTP: 单条拉取
    HTTP-->>C: CachedRule
    C->>L1: put
  end
  C->>C: QLExpress 本地执行
  C-->>Biz: RuleResult
  C->>C: 异步上报执行日志（若启用）
  Redis-->>C: 规则/函数变更消息
  C->>C: 更新或失效缓存
```

要点：

- 前后端分离部署，`rule-engine-builder-ui` 的构建产物在 `dist/`，不混入 `rule-engine-server`。
- 业务系统不直连 MySQL 获取规则，通过 SDK 调用服务端同步接口。
- Redis 需要与 `rule-engine-server` 使用同一实例。项目规则/函数变更会向 `rule:push:{projectCode}` 推送，GLOBAL 变更使用 `rule:push:broadcast`；`appName` 不参与项目频道路由。
- 执行日志默认通过有界异步 HTTP 队列上报；应用提供 `ExecutionLogReporter`（classpath 中存在 `KafkaTemplate` Bean 时会自动提供 Kafka reporter）时优先使用外部 reporter。

各业务功能通过变量引用、编译产物、发布版本和执行追踪形成完整链路：

```mermaid
flowchart LR
  P["项目与鉴权"] --> RES["变量 / 名单 / 外数 / 数据库 / 模型 / 函数"]
  RES --> DESIGN["九类规则设计器"]
  DESIGN --> CT["编译与测试"]
  CT --> PUB["版本与发布"]
  PUB --> RUN["Server / SDK 执行"]
  RUN --> TRACE["日志与规则回溯追踪树"]
  DESIGN --> LINEAGE["血缘分析"]
  PUB --> EXP["分流实验"]
  RUN --> BILL["计费明细与汇总"]
```

## 4. 环境要求

- JDK 17（Maven 构建会校验 Java 版本，低于 17 将直接失败）
- Maven 3.6+
- MySQL 8
- Redis
- Node.js 20.19+，不设置最高版本；建议使用当前维护中的 Node.js LTS，已验证 Node.js 26.4.0
- 可选 NVIDIA GPU：默认构建使用可移植的 ONNX Runtime CPU 包，不要求 NVIDIA 驱动、CUDA 或 cuDNN；只有使用 `-Ponnx-gpu` 构建 CUDA 版后端时，才需安装与项目 ONNX Runtime GPU 版本兼容的 NVIDIA 驱动、CUDA 和 cuDNN，并确保其动态库在服务进程的 `PATH`/`LD_LIBRARY_PATH` 中。

## 5. 本地启动

### 5.1 数据库与基础设施

```bash
cp .env.example .env
# 编辑 .env，替换全部 replace-with-* 占位值
docker compose --env-file .env up -d
```

PowerShell 可使用 `Copy-Item .env.example .env`。Compose 不再提供 MySQL/Redis 共享默认密码；全新数据卷会根据 `MYSQL_USERNAME`、`MYSQL_PASSWORD` 创建应用账号。已有数据卷升级时，需由数据库管理员按最小权限原则预先创建或更新该账号。

`schema.sql` 只包含数据库、表和索引等结构 DDL，不创建用户、不修改 root 账号、也不执行全局授权；`export_202607161151.sql` 是当前唯一的初始数据快照。空 Docker 数据卷首次启动时会依次执行 `01-schema.sql` 和 `02-export.sql`。根编排中的 `mysql-init` 对已有数据卷只重复执行结构 DDL，不会自动重放会覆盖业务数据的 export。项目鉴权、临时 Token 及其访问审计数据与部署主密钥绑定，不写入初始快照；服务启动后会把项目表中的兼容访问令牌按当前主密钥迁移为默认鉴权记录。

需要手工完整恢复时，固定顺序为：删除 `rule_engine` 数据库，执行 `schema.sql`，再执行 `export_202607161151.sql`。export 会清空并重建其覆盖的全部数据表，因此不得直接用于需要保留现有业务数据的数据库。

### 5.2 后端

```bash
mvn clean install -DskipTests
cd rule-engine-server
mvn spring-boot:run
```

以上命令构建并启动 CPU 版，适用于没有 NVIDIA GPU/CUDA 的机器。需要 CUDA 推理时，构建和启动必须使用同一个 Maven Profile：

```bash
mvn clean install -Ponnx-gpu -DskipTests
cd rule-engine-server
mvn spring-boot:run -Ponnx-gpu
```

GPU 构建完成后，可在仓库根目录执行真实 CUDA 诊断。诊断直接使用启动进程的 `PATH`，不需要额外传入 CUDA/cuDNN 目录：

```powershell
mvn "-Ponnx-gpu" "-Dtianshu.cuda.diagnostic=true" "-Dtest=CudaEnvironmentDiagnosticTest" "-Dsurefire.failIfNoSpecifiedTests=false" -pl rule-engine-server -am test
```

诊断通过后使用同一 `onnx-gpu` Profile 启动服务，并在模型管理页面将目标 ONNX 模型的执行设备显式设置为“GPU（CUDA）”。仅启用 GPU Profile 不会自动修改已有模型的 CPU/CUDA 配置。

后端启动时会将当前目录的 `.env` 和上级目录的 `.env` 作为可选配置源读取：在仓库根目录运行 JAR 时读取 `./.env`，在 `rule-engine-server` 目录运行 `mvn spring-boot:run` 时读取 `../.env`。操作系统环境变量和命令行参数的优先级高于 `.env`，因此生产环境仍应通过 Secret/KMS 注入真实凭据；缺少必填密钥时，现有安全校验仍会拒绝启动。关键配置如下：

| 配置 | 要求 |
|------|------|
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 必填；生产使用仅具备 `rule_engine` 所需权限的独立账号 |
| `REDIS_PASSWORD` | 必填；与 server 和 SDK 实际连接的 Redis 实例一致 |
| `RULE_AUTH_MASTER_KEY` | 必填；至少 32 位的私有随机密钥，生产通过 Secret/KMS 注入 |
| `RULE_AUTH_LEGACY_MASTER_KEY` / `RULE_AUTH_LEGACY_V2_MASTER_KEY` | 仅升级旧部署时配置；分别用于读取历史 `v1` 密文和曾复用 `v2` 标识的旧密文 |
| `CONSOLE_USERNAME` / `CONSOLE_PASSWORD` | 必填；密码默认按 BCrypt 校验 |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | 生产控制台的精确 HTTPS 来源，不使用任意来源 |
| `SESSION_COOKIE_SECURE` | HTTPS 生产环境设为 `true` |

本地临时开发如需使用明文控制台密码，可显式设置 `CONSOLE_PASSWORD_ENCODING=PLAIN`；生产必须使用 BCrypt 或外部身份系统，不应提交密码或密钥到仓库。

ONNX 神经网络模型可在“模型管理”中逐个选择 CPU 或 CUDA，并配置 GPU 设备号、显存上限、显存扩展策略、cuDNN 卷积算法搜索和默认 CUDA 流。CPU 版后端即使读取到历史 CUDA 配置也会自动回退 CPU，不影响服务启动；CUDA 版后端会实际检查 CUDA 共享库及其依赖是否可加载。服务按“模型文件内容 + 运行配置”缓存推理会话；开启“启动预加载”后会在服务启动阶段创建对应 CPU/CUDA 会话。配置 CUDA 的模型在 GPU 会话初始化或推理失败时会自动重试 CPU，CPU 成功后同一服务进程内后续调用会直接使用 CPU；修复 GPU 环境后需重启服务以重新尝试 CUDA。YuNet 人脸检测同样通过 ONNX Runtime 执行，OpenCV 仅保留图片解码、缩放和检测结果后处理。

模型运行时当前使用 JPMML Evaluator Metro 1.7.7 和 ONNX Runtime 1.26.0。PMML 输入字段按模型声明精确匹配，不自动转换大小写、驼峰或下划线；PMML/ONNX 缓存键均包含模型内容或运行配置，替换文件后不会继续复用旧模型。本项目已选择以 AGPL-3.0 开源方式交付 JPMML：凡对外提供包含 JPMML 的制品或网络服务，都必须同步提供与运行版本一致的完整 Corresponding Source、许可证和重建材料，详见 [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)。

### 5.3 前端

```bash
cd rule-engine-builder-ui
npm ci
npm run dev
```

开发访问地址为 `http://localhost:9090/`，`/api` 会代理到 `http://localhost:8080`。

### 5.4 用户主题设置

点击控制台右上角的**账户菜单 → 主题设置**，在右侧抽屉中调整外观。无需进入账户管理，也不影响规则配置和执行结果。

| 设置项 | 可选内容与使用方式 |
|--------|--------------------|
| 整体风格 | 白天模式、夜间模式，切换后即时预览页面和图表外观 |
| 预设主题色 | 8 个纯色预设、4 个渐变预设；点击色卡预览 |
| 自定义纯色 | 点击颜色选择器设置主题色，辅助色自动生成 |
| 自定义渐变 | 支持 2 色或 3 色，每个颜色可单独选择；支持线性和径向渐变；线性渐变角度为 0–360° |
| 菜单位置 | 左侧菜单或顶部菜单；一级菜单始终平铺，顶部空间不足时可横向滚动 |
| 导航栏风格 | 深色导航、浅色导航，可与白天/夜间模式组合 |
| 内容区域 | 流式或定宽，用于调整内容的横向布局 |
| 色弱模式 | 开启后调整界面显示；仍可结合文字标签识别状态 |

操作示例：打开主题设置，选择“夜间模式”，再选择“凝液紫渐变”，观察页面效果；不满意可点击“取消”，满意则点击“保存设置”。配置自定义渐变时，切换到“自定义渐变”，选择“3 色”，分别设置颜色，再选择“线性”与角度或“径向”。

修改配置会立即预览；只有点击“保存设置”才会持久化。点击“取消”、右上角关闭按钮或按 Esc 会恢复打开抽屉前的配置。当前抽屉没有遮罩，点击页面空白处不是取消操作。“恢复默认”同样只更新预览，需要再点击“保存设置”才会保存默认值。保存失败时会提示错误，应重试或取消，不能把预览效果视为保存成功。

启用控制台登录时，主题写入 `console_user_preference`，以当前 Session 中的 `console_user.id` 隔离，刷新、重新登录或更换设备后都能恢复；接口不接受客户端指定用户 ID。关闭登录时，配置保存到当前浏览器的 `localStorage`，不会跨设备同步。服务端配置是登录场景的最终权威，本地镜像只用于页面加载时尽早应用主题并减少闪烁。演示截图见 [7.16 主题设置](#716-主题设置演示)。

白天与夜间模式由统一语义令牌驱动，覆盖布局、业务页面、Element Plus 浮层与控件、9 类设计器和 Monaco 编辑器。渐变预设用于主按钮、品牌装饰和重点选中态；文字、边框和焦点环使用同一预设的实色主色与派生色阶，避免把任意 CSS 写入用户配置。

## 6. 核心模型类型

| 模型类型 | 设计器路由 | 顶层执行结果 | 命中与未命中语义 |
|----------|------------|--------------|------------------|
| 决策表 | `#/designer/table/{definitionId}` | `Map<输出字段, 值>` | `FIRST` 只执行第一条命中规则；`ALL` 按页面顺序执行全部命中规则，同一输出字段以后一次赋值为准；`UNIQUE` 要求至多命中一条，多条命中会执行失败。没有规则命中时，已声明输出字段仍返回且值为 `null`。 |
| 决策树 | `#/designer/tree/{definitionId}` | `Map<任务动作输出字段, 值>`；没有输出字段时可为 `null` | 从开始节点沿条件分支执行；没有匹配条件且没有默认连线时，该分支不再继续，未赋值的声明输出为 `null`。树不允许分支汇合。 |
| 决策流 | `#/designer/flow/{definitionId}` | `Map<任务动作输出字段, 值>`；没有输出字段时可为 `null` | 按页面顺序匹配条件分支；无默认分支且全部条件未命中时，不执行任何分支，也不经过公共汇合点；任一条件分支或默认分支到达汇合点后，从公共点继续执行。允许 DAG 分支汇合，循环路径在编译时拒绝。 |
| 规则集 | `#/designer/ruleset/{definitionId}` | 命中项 `List`，每项包含 `ruleCode`、`ruleName`、`priority`、`order` | 先按优先级降序、同优先级按页面顺序执行；`SERIAL` 首条命中后停止，`PARALLEL` 返回全部命中项；未命中返回空列表。可把同一列表写入已绑定的 LIST 结果字段。 |
| 交叉表 | `#/designer/cross/{definitionId}` | `Map<结果字段, 值>` | 简单矩阵按行值与列值选择一个单元格；未命中时结果字段为 `null`。兼容格式配置默认动作时，未命中执行默认动作。 |
| 评分卡 | `#/designer/score/{definitionId}` | `Map<总分字段, 值>`；配置等级阈值时同时返回等级字段 | 总分为初始分加全部命中评分项的 `分数 × 权重`；没有评分项命中时仍返回初始分。总分不落入任何等级区间时，等级为“未知”。 |
| 复杂交叉表 | `#/designer/cross-adv/{definitionId}` | `Map<结果字段, 值>` | 多行/列维度分段取笛卡尔积并选择首个匹配单元格；空单元格或无组合命中时结果为 `null`。 |
| 复杂评分卡 | `#/designer/score-adv/{definitionId}` | `Map<总分字段, 值>`；配置等级阈值时同时返回等级字段 | 每个维度内部只取第一条命中规则，维度之间累加；维度未命中贡献 0 分。未落入等级区间时等级为“未知”。 |
| QL 脚本 | `#/designer/script/{definitionId}` | 脚本显式 `_result`/`return` 的值；否则将检测到的公共输出字段包装为 `Map` | 由脚本自行决定分支和未命中结果；脚本为空或静态解析失败时拒绝编译。 |

各设计器的“测试”入口会按当前模型引用生成测试样例。决策表、规则集、树、流、交叉表、评分卡、复杂模型和脚本会优先使用规则实际输入字段，不把结果变量作为默认入参。

设计器测试弹窗统一使用 Monaco JSON 编辑器。点击“测试”后会自动生成当前规则输入字段样例；点击“执行”后在同一弹窗内展示本次输入、执行输出和错误信息，便于对照排查。

### 6.1 区间、终止和发布语义

- 评分卡、复杂评分卡的等级阈值统一使用左闭右开区间 `[min, max)`；相邻的 `[0, 250)`、`[250, 350)` 不重叠，边界值 `250` 只落入后一段。数值阈值重叠、反向或非数字会在编译时拒绝。
- 复杂交叉表支持 `[)`、`()`、`[]`、`(]` 四种端点。可静态确定的数值和日期字面量会按开闭端点校验：共享端点只有在两侧都包含该端点时才算重叠；反向区间和重叠区间拒绝编译。引用变量、函数等动态端点无法在编译期比较，配置者仍需保证其运行时区间互斥。
- 决策树/决策流结束节点的“结束当前规则”（`CURRENT_RULE`）立即返回当前规则已经产生的输出；“结束全部规则”（`ALL_RULES`）终止包括嵌套规则在内的整条调用链，并由根规则按根输出定义收集终止前已产生的值。
- 设计器中的“保存并编译”（QL 脚本为“保存并验证”）只更新当前草稿，不会改变线上已发布制品。保存成功后点击设计器顶部“前往规则生命周期”，在规则详情完成发布前校验、提交评审、批准和发布；只有发布完成后，业务执行与 SDK 同步才切换到新制品。

## 7. 当前功能截图

> 截图分批维护：7.15–7.17 为 2026-09-07 使用当前源码构建的真实前端、通过 Playwright 页面操作新采集的演示图（1600 × 1100）；API 数据沿用仓库固定示例，指标不代表真实业务统计。其余图库保留于 2026-07-26，个别布局可能与当前界面不同。截图不包含生产凭据，演示截图不替代真实后端联调验收。

### 7.1 登录

![控制台登录页](docs/project-usage/project-usage-01-login.png)

### 7.2 项目管理

![项目管理](docs/project-usage/project-usage-02-project-list.png)

### 7.3 项目详情与规则列表

![项目详情与规则列表](docs/project-usage/project-usage-07-project-detail.png)

规则列表操作使用保留文字的语义色按钮：详情为蓝色、设计为琥珀色、生命周期为灰蓝色、删除为红色；操作列固定在右侧，紧凑页面中仍可完整操作。

![规则列表语义操作按钮](docs/project-usage/project-usage-02-rule-list.png)

### 7.4 变量管理

变量管理支持项目级和全局变量。变量来源包括输入、计算、常量、API、数据库和名单。API、数据库、名单来源变量可在列表操作中点击“测试”，输入上下文 JSON 后直接触发对应外部取数或匹配逻辑，并写入对应模块调用日志。

API、数据库、名单来源变量额外提供“详情”入口，可查看该变量依赖的引擎输入字段、参数映射或查询字段，以及原始 `sourceConfig` JSON。

![变量管理](docs/project-usage/project-usage-03-variable.png)

### 7.5 名单管理

![名单管理](docs/project-usage/project-usage-09-list.png)

### 7.6 外数管理

外数管理用于统一配置外部 API 数据源、接口、鉴权、请求映射、响应映射、超时、重试、缓存和计费项。

请求头、Query、入参映射和响应映射支持业务表单与 JSON 双向配置；动态时间戳、签名、加解密、Token 解包及多结构响应可通过请求/响应 QL 脚本处理。脚本变量支持敏感值脱敏，请求与响应脚本可通过仅本次调用有效的 `state` 共享临时值，接口地址支持 `${appId}`、`${vars.appId}`、`${input.channel}` 等占位符。

`rule-engine-server/src/main/resources/sql/data-third-party-api.sql` 提供 15 个第三方数据源、17 个 API 的可重复导入模板，覆盖多种 Token、MD5/SM3/HMAC、3DES/RSA、动态 Header、Form/JSON 请求和响应解包场景。模板凭据均为 `REPLACE_BEFORE_ENABLE_*` 占位符并默认停用；可在“接口测试”中使用“生成请求预览”验证完整构造链路，预览不会访问外部地址。

![外数管理](docs/project-usage/project-usage-10-datasource.png)

### 7.7 数据库管理

数据库管理用于维护后端集中连接池。数据库变量执行时通过后端查询外部数据库，不由前端或客户端直连数据库。

数据源校验 SQL、数据库变量 SQL 和数据库测试 SQL 均使用 Monaco SQL 编辑器，支持 SQL 关键字高亮，便于维护较长查询语句。

![数据库管理](docs/project-usage/project-usage-11-database.png)

数据库日志按数据库语义记录连接方式、查询状态、开始结束时间、SQL、参数字段和值、返回结果表内容，以及解析后提取的变量字段和值。

![数据库调用日志](docs/project-usage/project-usage-12-database-log.png)

### 7.8 模型管理

![模型管理](docs/project-usage/project-usage-13-model.png)

### 7.9 函数管理

![函数管理](docs/project-usage/project-usage-04-function.png)

### 7.10 规则测试

规则测试会读取规则输入字段，并可从规则内容兜底提取测试入参。页面执行后可查看结果、入参和追踪树。

![规则测试](docs/project-usage/project-usage-05-rule-test.png)

规则回溯追踪树按层级展示规则状态、耗时、输入输出、条件命中、动作结果、嵌套规则和模型/函数等模块调用，便于还原一次决策的完整执行路径。

![规则回溯追踪树](docs/project-usage/project-usage-05-rule-trace-tree.png)

### 7.11 血缘分析

血缘分析支持从项目、变量、规则、模型、API、数据库、名单和外数源出发，查看上游依赖、下游引用或全量关系。不同类型节点用不同颜色展示。

![血缘分析](docs/project-usage/project-usage-14-lineage.png)

### 7.12 分流实验

分流实验支持配置冠军组、挑战组和测试组。冠军组和挑战组参与生产分流；测试组可在生产组执行后空跑，用于验证新规则结果。配置页支持按条件命中、流量比例、互斥执行、测试组是否调用 API 外数等策略，执行后会记录实验标签和明细。

![分流实验](docs/project-usage/project-usage-15-experiment.png)

### 7.13 执行日志

执行日志展示规则输入、输出、耗时、来源和表达式追踪。详情页提供各类规则执行回溯：决策表/规则集命中路径、树和流节点执行、交叉表/评分卡命中项、复杂模型维度命中、QL 脚本赋值与条件判断等。

![执行日志](docs/project-usage/project-usage-06-execution-log.png)

### 7.14 账单管理

![账单管理](docs/project-usage/project-usage-16-billing.png)

### 7.15 数据看板

从一级菜单“数据看板”进入，选择项目、规则编码/名称和统计时间，点击“查询”；点击“重置”恢复默认筛选。看板按当前用户权限展示模块，并分别提供加载、失败重试和空数据提示。

| 分区 | 内容与统计口径 |
|------|----------------|
| 进件与决策 | 进件数、通过数、人审数、拒绝数、未分类数、通过率、人审率和设备数；同一项目优先按请求编号、其次按 Trace ID 去重，保留时间最新的一条 |
| 分布分析 | 期数占比、金额占比和进件地图热力图；展示有效与排除记录数，非法坐标不进入地图统计 |
| 数据资源与调用 | 数据库、名单、外数等资源与调用情况、执行耗时和账单金额；外数只统计实际向供应商发起的 API 请求 |
| 规则与治理 | 规则集命中情况和审批状态，结合执行与审批记录查看运行情况 |

![数据看板：进件与决策及分布分析（固定演示数据）](docs/project-usage/project-usage-18-dashboard.png)

**看板设置**：点击筛选栏右侧“看板设置”，选择个人默认或具体项目，按指标关联变量或对象字段，并配置 JSON 来源等信息。引用保存资源 ID；项目未配置项逐项继承个人默认，再回落系统默认。个人默认仅使用全局字段。未启用登录时只能查看系统默认字段映射。映射失效会提示处理，对应指标暂不统计。

地图设置支持中心经纬度、缩放比例和“恢复中国默认值”；“保存地图视角”只保存在当前浏览器会话中，与账户主题的跨设备保存范围不同。经度须在 [-180, 180]，纬度须在 [-90, 90]，统计时排除非法值，不修改执行日志原始内容。

### 7.16 主题设置演示

下图展示账户菜单打开后的主题抽屉：可以预览整体风格、主题色、菜单位置和导航风格。抽屉内容可滚动，底部提供“取消”和“保存设置”。

![主题设置：白天模式和预设主题色](docs/project-usage/project-usage-19-theme-presets.png)

选择夜间模式与凝液紫渐变，页面和看板同步预览：

![主题设置：夜间模式与渐变主题预览](docs/project-usage/project-usage-19-theme-dark.png)

选择自定义渐变、3 色和径向，分别编辑颜色；切换为线性时可继续设置角度：

![主题设置：自定义三色径向渐变](docs/project-usage/project-usage-19-theme-custom.png)

选择顶部菜单并保存后，刷新页面仍保留设置。顶部导航保持一级菜单平铺，横向滚动可访问其余入口：

![主题设置：保存并刷新后的顶部平铺导航](docs/project-usage/project-usage-19-theme-top.png)

### 7.17 数据对象字段与变量引用

从“变量管理 → 数据对象”进入，通过表格行左侧箭头展开对象字段明细，可查看字段编码、名称、脚本名称、类型、引用对象和引用变量，并执行添加、编辑或删除。

![数据对象：表格展开与字段引用列](docs/project-usage/project-usage-20-object-fields.png)

编辑对象字段时，可在“引用变量”中选择已有变量。调用时如果没有传入当前对象字段，引擎从被引用变量取值；显式传入当前字段时优先采用调用方的值。例如对象字段 `deviceId` 引用了设备变量，调用方未提供该字段时才使用变量解析结果。

列表元素内部字段不能直接引用变量，应在 LIST 字段上整体引用。引用关联使用 `refVariableId` 等稳定 ID，不依赖名称匹配；编码和导入字段名原样保留。字段变更按现有审批流程生效，发布制品冻结相关映射与依赖，后续编辑不会直接改写历史发布快照。

## 8. 设计器截图

### 8.1 决策表

![决策表设计器](docs/project-usage/project-usage-designer-table.png)

### 8.2 决策树

![决策树设计器](docs/project-usage/project-usage-designer-tree.png)

### 8.3 决策流

![决策流设计器](docs/project-usage/project-usage-designer-flow.png)

### 8.4 规则集

![规则集设计器](docs/project-usage/project-usage-designer-ruleset.png)

### 8.5 交叉表

![交叉表设计器](docs/project-usage/project-usage-designer-cross.png)

### 8.6 评分卡

![评分卡设计器](docs/project-usage/project-usage-designer-score.png)

### 8.7 复杂交叉表

![复杂交叉表设计器](docs/project-usage/project-usage-designer-cross-adv.png)

### 8.8 复杂评分卡

![复杂评分卡设计器](docs/project-usage/project-usage-designer-score-adv.png)

### 8.9 QL 脚本

![QL 脚本设计器](docs/project-usage/project-usage-designer-script.png)

### 8.10 完整页面与业务状态截图索引

以下 66 张截图使用同一套可重复的“人脸风控中心”样例数据生成。完整业务状态图库基于真实生产构建在 `1600 × 1000` 视口采集；规则列表、变量管理、规则测试、追踪树、账单管理、决策表和交叉表又在本次任务最终阶段通过内置浏览器以 `1280 × 720` 视口重新操作、复核并覆盖为最新截图。样例覆盖字段选择、阈值与表达式、项目鉴权、开放 API、外数接口、模型执行、分流实验、人脸识别追踪、计费核账，以及规则从修改到发布的完整流程，且不包含生产凭据。

表单、字段选择和阈值编辑统一使用 Element Plus 原生输入类组件；字段摘要使用 `el-tag`，不再用自绘边框模拟输入框。列表操作保留紧凑链接形态并按业务语义配色：详情/查看为蓝色，编辑/设计为琥珀色，测试/执行为绿色，发布/转全局为青绿色，版本/生命周期为灰蓝色，下线为橙色，删除/撤销为红色；hover、focus 和禁用态由同一套全局样式统一控制，文字标签始终保留，避免只依赖颜色传达含义。

规则发布页中“线上已发布 v3”和“当前修订 v4”可以同时存在：v4 在草稿、评审或已批准阶段不会替换线上制品，只有完成“发布制品”后线上版本才切换为 v4。

| 业务域 | 页面与状态截图 |
|---|---|
| 登录与项目 | [登录](docs/project-usage/project-usage-01-login.png) · [项目列表](docs/project-usage/project-usage-02-project-list.png) · [项目详情](docs/project-usage/project-usage-07-project-detail.png) |
| 项目鉴权 | [鉴权配置](docs/project-usage/project-usage-02-project-auth.png) · [临时 Token](docs/project-usage/project-usage-02-project-auth-token.png) · [访问审计](docs/project-usage/project-usage-02-project-auth-audit.png) |
| 规则管理 | [规则列表](docs/project-usage/project-usage-02-rule-list.png) · [规则详情](docs/project-usage/project-usage-08-rule-detail.png) · [输入字段](docs/project-usage/project-usage-08-rule-input-fields.png) · [输出字段](docs/project-usage/project-usage-08-rule-output-fields.png) |
| 规则开放 API | [对外契约](docs/project-usage/project-usage-08-rule-open-api.png) · [API 测试用例](docs/project-usage/project-usage-08-rule-api-scenarios.png) |
| 发布流程 | [发布前校验](docs/project-usage/project-usage-08-rule-release-preflight.png) · [提交评审](docs/project-usage/project-usage-08-rule-release-review.png) · [批准并固化](docs/project-usage/project-usage-08-rule-release-approved.png) · [发布完成](docs/project-usage/project-usage-08-rule-release-published.png) |
| 字段管理 | [变量列表](docs/project-usage/project-usage-03-variable.png) · [数据对象](docs/project-usage/project-usage-03-data-object.png) · [字段校验](docs/project-usage/project-usage-03-field-validation.png) |
| 名单管理 | [名单库](docs/project-usage/project-usage-09-list.png) · [名单详情](docs/project-usage/project-usage-09-list-detail.png) |
| 外数数据源 | [数据源列表](docs/project-usage/project-usage-10-datasource.png) · [新建数据源](docs/project-usage/project-usage-10-datasource-create.png) · [数据源详情](docs/project-usage/project-usage-10-datasource-detail.png) · [数据源鉴权](docs/project-usage/project-usage-10-datasource-auth.png) · [调用日志](docs/project-usage/project-usage-10-datasource-log.png) |
| 外数 API | [新建 API](docs/project-usage/project-usage-10-api-create.png) · [API 详情](docs/project-usage/project-usage-10-api-detail.png) · [接口鉴权](docs/project-usage/project-usage-10-api-auth.png) · [请求配置](docs/project-usage/project-usage-10-api-request.png) · [响应配置](docs/project-usage/project-usage-10-api-response.png) · [请求预览测试](docs/project-usage/project-usage-10-api-test.png) |
| 数据库 | [数据库列表](docs/project-usage/project-usage-11-database.png) · [新建连接](docs/project-usage/project-usage-11-database-create.png) · [连接详情](docs/project-usage/project-usage-11-database-detail.png) · [调用日志](docs/project-usage/project-usage-12-database-log.png) |
| 模型管理 | [模型列表](docs/project-usage/project-usage-13-model.png) · [模型详情](docs/project-usage/project-usage-13-model-detail.png) · [输出字段绑定](docs/project-usage/project-usage-13-model-output-fields.png) · [模型执行测试](docs/project-usage/project-usage-13-model-test.png) |
| 函数与表达式 | [函数管理](docs/project-usage/project-usage-04-function.png) · [表达式配置](docs/project-usage/project-usage-17-expression-editor.png) · [表达式朔源测试](docs/project-usage/project-usage-17-expression-test.png) |
| 规则测试与追踪 | [规则测试](docs/project-usage/project-usage-05-rule-test.png) · [测试追踪树](docs/project-usage/project-usage-05-rule-trace-tree.png) · [执行日志](docs/project-usage/project-usage-06-execution-log.png) · [日志详情](docs/project-usage/project-usage-06-execution-log-detail.png) · [日志追踪树](docs/project-usage/project-usage-06-execution-log-trace.png) |
| 血缘分析 | [人脸字段血缘](docs/project-usage/project-usage-14-lineage.png) |
| 分流实验 | [实验列表](docs/project-usage/project-usage-15-experiment.png) · [新建实验](docs/project-usage/project-usage-15-experiment-create.png) · [实验详情](docs/project-usage/project-usage-15-experiment-detail.png) · [90/10 分流配置](docs/project-usage/project-usage-15-experiment-config.png) |
| 账单管理 | [计费配置](docs/project-usage/project-usage-16-billing-config.png) · [计费明细](docs/project-usage/project-usage-16-billing-record.png) · [计费汇总](docs/project-usage/project-usage-16-billing-summary.png) |
| 九类设计器 | [决策表](docs/project-usage/project-usage-designer-table.png) · [决策树](docs/project-usage/project-usage-designer-tree.png) · [决策流](docs/project-usage/project-usage-designer-flow.png) · [规则集](docs/project-usage/project-usage-designer-ruleset.png) · [交叉表](docs/project-usage/project-usage-designer-cross.png) · [评分卡](docs/project-usage/project-usage-designer-score.png) · [复杂交叉表](docs/project-usage/project-usage-designer-cross-adv.png) · [复杂评分卡](docs/project-usage/project-usage-designer-score-adv.png) · [QL 脚本](docs/project-usage/project-usage-designer-script.png) |

### 8.11 设计器保存、检查与恢复

当前设计器将草稿保存与检查状态明确区分，建议按以下顺序完成一次修改：

1. 从规则详情进入设计器，确认正在编辑可修改的草稿；查看历史版本时先按页面入口创建可编辑草稿。
2. 修改条件或动作后，观察“有未保存修改”状态。“仅保存草稿”保存当前内容，但不代表检查通过。
3. 点击“保存并检查”，依据校验报告修正问题。检查未通过时不要直接进入发布流程；发生保存冲突时重新加载并核对当前版本。
4. 当前内容显示“已检查，可测试”后，点击“进入测试”，输入样例并核对结果与追踪。再次修改后需要重新保存并检查。
5. 通过“生命周期”进入审核与发布，草稿保存和测试都不会自动发布线上版本。

离开存在未保存修改的设计器时会提示确认。同一浏览器会话发现未保存内容时，可选择“恢复内容”或“忽略”；恢复后仍需保存并检查，会话恢复不等同于服务器已保存，也不提供跨设备草稿备份。

## 9. 业务系统 SDK 集成

业务系统引入 `rule-engine-client` 后，可继续使用项目原有访问令牌，也可按项目配置账号密码、API Key 或 HMAC-SHA256。非旧令牌方式默认先调用 `/api/rule/auth/token` 换取短期 Bearer Token，再同步或执行规则；调用方不需要也不能传 `authCode`，服务端会根据凭证自动识别鉴权配置。

```yaml
rule-engine:
  client:
    server-url: http://localhost:8080
    app-name: your-service-name
    project-code: your-project-code
    token: <项目访问令牌>
    project-id: 1
    trace-enabled: true
    log-report-enabled: true
    log-buffer-size: 500
    log-batch-size: 50
    log-flush-interval-ms: 5000
    # 规则依赖 API/DB/名单变量时必须开启服务端执行
    server-side-execution: true
```

`project-code` 是项目级实时推送和项目函数隔离的必填路由键，必须与服务端项目编码完全一致；`app-name` 只标识调用应用，不会替代 `project-code` 订阅项目频道。未配置 `project-code` 时 SDK 仍可通过 HTTP 同步并接收 GLOBAL 广播，但不会订阅任何项目频道，因此不能获得项目规则/函数的实时变更。

账号密码方式示例（默认 Token 有效期 2 小时、失效后宽限 10 分钟，均可在项目鉴权配置中调整）：

```yaml
rule-engine:
  client:
    server-url: http://localhost:8080
    app-name: your-service-name
    project-code: your-project-code
    auth-type: BASIC
    username: caller-account
    password: caller-password
    token-exchange-enabled: true
    token-refresh-ahead-seconds: 60
```

API Key 使用 `auth-type: API_KEY` 并配置 `api-key`、`api-key-placement`（`HEADER` 或 `QUERY`）和 `api-key-parameter-name`；HMAC 使用 `auth-type: HMAC_SHA256` 并配置 `access-key` 与 `hmac-secret`。Java Builder 分别提供 `basicAuth(...)`、`apiKeyAuth(...)` 和 `hmacAuth(...)`。

HMAC 请求固定携带 `X-Rule-Access-Key`、`X-Rule-Timestamp`、`X-Rule-Nonce` 和 `X-Rule-Signature`。签名值为以下标准串使用 HMAC-SHA256 计算后的小写十六进制结果；Query 使用原始编码串，请求体使用原始字节：

```text
HTTP_METHOD\n
REQUEST_URI\n
RAW_QUERY\n
SHA256_HEX(REQUEST_BODY)\n
UNIX_TIMESTAMP_SECONDS\n
NONCE
```

直接调用 `/token` 时，只提交当前鉴权方式的凭证。例如账号密码使用 HTTP Basic：

```bash
curl -X POST http://localhost:8080/api/rule/auth/token \
  -H "Authorization: Basic <base64(username:password)>"

curl -X POST http://localhost:8080/api/rule/sync/execute/RC_PRICING_TABLE \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"clientAppName":"your-service-name","params":{}}'
```

执行示例：

```java
RuleResult result = ruleEngineClient.execute("RC_PRICING_TABLE", requestMap);
```

SDK 行为：

- 启动时先通过 HTTP 全量同步规则和函数，再建立 Redis 订阅；项目鉴权失败会直接使启动失败，不会留下半启动的订阅或定时任务。
- 成功的全量响应是权威快照：服务端已删除的规则/函数会从客户端移除，成功返回空数组会清空对应远端快照；HTTP 非 2xx、空响应、业务 `code != 200`、非法 JSON 或网络失败不会清空旧快照。
- 订阅 `rule:push:{projectCode}` 和 `rule:push:broadcast`，规则发布/下线及函数更新/删除后增量刷新本地状态。项目推送只接受相同 `projectCode`；GLOBAL 推送对所有客户端生效。
- 缓存未命中时可按规则编码单条拉取。
- 默认本地使用 QLExpress 执行脚本，适合只依赖入参、常量、计算变量和已同步函数的规则。
- 同名远端函数按“当前项目 PROJECT → GLOBAL”解析；删除项目函数后自动回退同名 GLOBAL 函数，删除 GLOBAL 函数不会删除仍存在的项目函数。HTTP 成功快照与 Redis 删除消息都会真正撤销已删除函数，不需要重启业务应用。
- 如果规则依赖 API 变量、数据库变量或名单变量，必须开启 `server-side-execution: true`，或直接调用服务端接口 `POST /api/rule/sync/execute/{ruleCode}`。这些外部变量只在服务端通过 `VariableSourceResolver` 解析，本地 SDK 不会直连外部 API、数据库或名单库。
- SDK 在 Token 到期前 60 秒自动续期；续期失败时继续使用旧 Token，直到其宽限期结束。
- 没有外部 `ExecutionLogReporter` 时，日志通过有界 HTTP 队列异步上报，不阻塞规则结果；达到 `log-batch-size` 立即发送，否则最多等待 `log-flush-interval-ms`。队列达到 `log-buffer-size` 后采用 drop-newest 并记录丢弃计数/告警；HTTP 或业务响应失败最多尝试 3 次，最终失败记录批次和日志计数。
- `RuleEngineClient.close()` 会在有界等待内冲刷自己创建的 HTTP reporter；规则结果不会因 reporter 抛错而改为失败。应用提供的外部 reporter 生命周期归应用容器管理，客户端不会替它启动或关闭。
- Spring 容器中存在自定义 `ExecutionLogReporter` 时始终优先使用；存在 `KafkaTemplate` 且没有自定义 reporter 时自动创建 Kafka reporter，默认主题为 `rule-execution-log`。是否使用 BASIC、API Key、HMAC 或旧令牌鉴权不会强制覆盖该 reporter 选择。

项目鉴权配置、长期凭证和短期 Token 均可在控制台再次查看完整值。长期凭证在数据库中使用 AES-GCM 可逆加密存储；启动服务前必须通过 `RULE_AUTH_MASTER_KEY` 配置至少 32 位的独立主密钥并妥善保管，未配置或使用公开开发密钥时服务会拒绝启动。新密文默认使用 `v2` 密钥；升级前若已有旧 `v1` 密文，需通过 `RULE_AUTH_LEGACY_MASTER_KEY` 保留原主密钥；若历史版本曾在更换密钥材料时继续复用 `v2` 标识，还需通过 `RULE_AUTH_LEGACY_V2_MASTER_KEY` 配置当时的材料。解密会优先使用密文标识对应的密钥，再尝试已配置的历史密钥；待旧凭证全部修改或重置后应移除历史密钥。可通过 `RULE_AUTH_ACTIVE_KEY_ID` 显式选择活动密钥版本，后续轮换必须使用新的 key ID。访问审计记录所有受保护接口调用；只有实际规则执行进入计费，计费明细可区分 `authCode` 和 `tokenCode`，按日汇总到鉴权配置维度。

## 10. 版本、审批、权限、日志和计费

- 规则采用 `DRAFT → REVIEW → APPROVED → PUBLISHED → OFFLINE` 生命周期。已发布规则再次编辑时会创建新草稿，线上仍执行原发布制品；审核人与发布人允许是同一账号，但每次状态变更都会记录操作人、时间、理由和校验结果。
- 提交审核和发布前均执行 Schema 校验、结构化依赖闭包与影响分析。破坏性 Schema 变更可以发布，但必须填写明确理由并进入审计时间线。
- 发布会把规则、变量、函数、模型和依赖快照打包为不可变决策制品，使用规范 JSON 计算 SHA-256 摘要。执行日志和 SDK 缓存同时记录发布修订 ID 与制品摘要，后续源对象变化不会改写旧制品。
- 规则详情页可以下载制品；`/api/rule/artifact/import` 校验摘要和运行时兼容性，`/api/rule/artifact/deploy` 只接受“制品组件 ID → 目标环境资源 ID”的显式绑定，不按名称或编码猜测，也不打包凭证等秘密信息。
- PMML/ONNX 上传、替换时校验文件摘要、格式、精确输入输出字段和运行时兼容性，不做隐式大小写或命名风格转换。样例不是必填项；提供样例时必须实际执行通过。模型删除、替换和下线前必须先生成引用影响分析并携带未失效的确认令牌。
- 规则、模型、函数和分流实验均有版本记录，可查看版本内容、对比差异并回滚。
- 侧栏“审批管理”是统一资源变更入口，按“待处理 / 我的申请 / 已结束 / 全部记录”查看字段、数据对象、模型、外数、数据库、函数、规则、名单、计费、实验和项目等申请。申请人可保存草稿、执行依赖预检、提交或取消；具备 `approval:approve` 的审批人可通过或驳回。详情页展示配置差异、依赖冲突、审批时间线和历史版本恢复入口，冲突申请不会覆盖当前生效版本。
- 侧栏“账户管理”包含“账户”和“角色”两个页签。角色批量授予权限；账户可分配多个角色，并对单项权限设置 `ALLOW`、`DENY` 或继承，其中明确拒绝优先。菜单和后端接口同时校验最终有效权限；账户/角色启停、密码重置和权限版本更新均由具备 `account:manage` / `role:manage` 的管理员操作。全新空库的首次成功登录会用 `CONSOLE_USERNAME` / `CONSOLE_PASSWORD` 引导创建持久化 `SUPER_ADMIN` 账户和权限目录；之后登录以数据库账户为准，部署配置不是长期共享账号库。
- 执行日志记录规则执行结果、耗时、输入输出、表达式追踪、修订 ID 和制品摘要。
- 外数 API、数据库查询、名单匹配和模型执行会写入各自模块调用日志，日志页面按模块展示 HTTP 请求、SQL 查询、名单匹配或模型输入输出等不同结构。
- 账单模块可对引擎执行、API 调用和数据库调用配置计费项，查看明细与汇总。

已有数据库升级后，由管理员执行一次 `POST /api/rule/definition/migrate-artifacts`。迁移对每条规则幂等执行：可冻结的已发布版本会生成发布修订与制品；无法解析完整依赖的规则会保留原线上发布记录，并生成带问题说明的草稿，等待人工处理。

## 11. 开发校验命令

启动就绪、故障降级和容量门禁的可重复流程见[运行质量门禁](docs/quality-gates/runtime-quality-gates.md)。容量报告仅写入被忽略的 `target/quality-gates/`，不得提交本地请求凭据。

后端：

```bash
mvn clean install -DskipTests
cd rule-engine-server
mvn spring-boot:run
cd ..
mvn test
```

默认 `mvn test` 不依赖仓库外或被忽略的真实 ONNX 模型，因而适合干净 checkout 和 CI；需要真实模型/图片的推理用例会明确标记为 skipped。要执行真实 ONNX 集成门禁，先在仓库根目录准备以下资产：

```text
assets/docs/face.jpg
assets/onnx/yunet/detector.onnx
assets/onnx/facenox/best_model.onnx
assets/onnx/mn3/anti-spoof-mn3.onnx
assets/onnx/buffalo_l/det_10g.onnx
assets/onnx/buffalo_l/w600k_r50.onnx
assets/onnx/buffalo_l/2d106det.onnx
assets/onnx/buffalo_l/1k3d68.onnx
assets/onnx/buffalo_l/genderage.onnx
```

然后运行：

```bash
mvn -Ponnx-integration test
```

`onnx-integration` Profile 会把根目录 `assets/` 复制为测试 classpath 的 `/assets/` 并启用真实推理用例；任一必需图片或模型缺失/为空都会使测试失败，不会静默跳过。该目录已被 `.gitignore` 排除，不得把有许可证限制或体积较大的模型误提交到仓库。`-Ponnx-integration` 只控制真实测试资产，与选择 CPU/GPU 运行时的 `-Ponnx-gpu` 是两个独立 Profile。

前端：

```bash
cd rule-engine-builder-ui
npm run dev
npm run lint
npm test
npm run test:coverage
npm run build
npm run test:e2e:dist
npm run docs:screenshots
# 设置 E2E_BASE_URL 后运行真实前后端联调
npm run test:e2e:full
```

Playwright 首次使用需执行 `npx playwright install chromium`；也可设置 `PLAYWRIGHT_CHANNEL=chrome` 使用本机 Chrome。`test:e2e:dist` 直接加载真实 `dist/` 并模拟 API，不依赖本地监听端口；`docs:screenshots` 使用固定人脸风控样例重建 `docs/project-usage/` 下的页面验收截图，并在接口未匹配、关键内容缺失或紧凑工作台横向溢出时失败；`test:e2e:full` 只有在设置 `E2E_BASE_URL` 后才执行真实后端联调。

本次新增图库可单独重建：先在前端目录运行 `npm run build`，再在仓库根目录运行 `node scripts/capture-latest-docs.cjs`。该脚本使用固定文档 API 示例，实际操作主题预览、取消、自定义渐变、保存、刷新恢复和数据对象展开；发现未匹配 API、浏览器运行错误或关键界面缺失时失败。它不修改真实业务数据，完成后主动关闭浏览器。

2026-08-13 在 Microsoft OpenJDK 17.0.20 下，默认 `mvn test` 共记录 1211 个测试：1196 个通过、15 个 skipped，0 failures、0 errors；15 个 skipped 为 14 个显式 ONNX 真实资产用例和 1 个 CUDA 环境诊断。显式 `-Ponnx-integration` 在当前无模型资产 checkout 上按预期硬失败，证明资产门禁有效。在 Node.js 24.14.1 下验证前端 153 个测试文件、1702 个单元测试、ESLint 10 flat config、Vite 8 生产构建以及 Playwright `dist` 52/52 全部通过。测试数量会随代码演进，以最新命令输出为准。

## 12. 生产交付状态与边界

当前版本定位为预发布的生产候选版本。代码和自动化测试基础已经较完整，但正式交付前至少需要关闭以下门禁：

- 在允许监听端口的标准部署环境完成真实后端、MySQL、Redis、HTTPS 与浏览器全链路验收；当前 Playwright `dist` 生产包烟测已通过，但不能替代部署联调。
- 仓库和 CI 不提供任何共享默认凭据，也不连接外部业务数据。`.env.example` 只有占位值；`.env`、ONNX 测试资产、MySQL/Redis 数据目录均被忽略。生产必须通过 Secret/KMS 注入数据库、Redis、控制台引导账号、`RULE_AUTH_MASTER_KEY`、项目 Token/API Key/HMAC、外数和模型相关秘密，并建立最小权限、轮换、吊销和审计流程。
- 默认 CI 只运行可在干净 checkout 复现的 Maven、前端单元、lint、build 和模拟 API 的 `dist` E2E；真实 ONNX 资产、真实第三方 API/数据库/名单、HTTPS 和容量/灾备验证属于显式受控环境门禁，不能用默认 CI 绿色替代。
- JPMML 已确定采用 AGPL-3.0 开源交付；每个发布版本仍必须落实 Corresponding Source 下载入口、许可证保留和合规复核，不能满足时不得交付。
- 完成密钥托管与轮换、HTTPS、备份恢复、容量压测、灾备和发布回滚演练。
- 前端已迁移到 Vite 8、Vitest 4 和 ESLint 10；LogicFlow 间接依赖已通过 `uuid@11.1.1` override 修复，生产依赖审计为 0 漏洞。发布时仍需持续运行审计、SBOM 和许可证扫描。
- 血缘分析对结构化引用较可靠，但脚本中的复杂动态引用仍只能静态尽力识别。
- 外数、数据库和名单变量的在线测试依赖目标数据源；生产数据库数据源必须使用只读账号并限制为查询类 SQL。

完整的风险分级、验收清单、发布状态机和阶段性规划见[生产就绪复盘报告](docs/research/2026-07-22-production-readiness-review.md)。

## 13. 文档索引

- 运行质量门禁：[启动就绪、故障降级与容量验收](docs/quality-gates/runtime-quality-gates.md)
- 变量来源并行基准：[变量来源分层并行基准](docs/quality-gates/variable-source-resolution-benchmark.md)
- 生产就绪复盘与路线图：[《天枢决策引擎生产就绪复盘与后续规划》](docs/research/2026-07-22-production-readiness-review.md)
- 当前实现研究：[《天枢决策引擎当前实现研究报告》](docs/research/2026-07-17-tianshu-decision-engine-current-state.md)
- 数据库结构：`rule-engine-server/src/main/resources/sql/schema.sql`
- 当前初始化数据：`rule-engine-server/src/main/resources/sql/export_202607161151.sql`
- 前端单元测试：`rule-engine-builder-ui/tests/unit/`
- 前端浏览器测试：`rule-engine-builder-ui/tests/e2e/`
- 后端测试：`rule-engine-core`、`rule-engine-client`、`rule-engine-server` 各模块的 `src/test/`
- 第三方许可证与开源交付：[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)


## 14. 参考引用

> `QLExpress` 脚本表达式: https://github.com/alibaba/QLExpress
>
> `qlexpress-rule`规则引擎: https://github.com/xiachongbu/qlexpress-rule
