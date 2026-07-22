# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

> ⚠️ 本文件与 `CLAUDE.md` 内容应保持一致（两者是同一份指引的镜像）。修改本文件时同步更新 `CLAUDE.md`。
> ⚠️ 项目仍是「未发布版」（见 `README.md` 顶部提示），部分功能逻辑仍在修缮中。研究现状时以实际代码为准，本文件的功能状态描述可能滞后。

所有回答以及中间的思考过程，尽可能全部使用中文进行解答。

所有实现的代码，在完成后都需要反复核对逻辑是否正确，是否与用户提出的需求匹配，如果有问题修复后需要再次核对。

任何修改都应该遵守这个完整校验流程：

**前端代码修改完成后**：
1. `npm run dev` 启动前端，验证页面正常无报错
2. 验证无误后关掉进程
3. `npm test` 运行前端测试用例（`tests/unit/`），确保所有测试通过

**后端代码修改完成后**：
1. `mvn clean install -DskipTests` 编译所有模块无误
2. `mvn spring-boot:run` 正常启动 rule-engine-server 后端服务
3. `mvn test` 运行后端测试用例，确保所有测试通过

**Codex 本地验证服务超时**：
- 启动前端、后端、MOCK 等长期本地验证服务时，工具调用超时统一设置为 `1800000ms`（30 分钟），不得继续使用 `600000ms`（10 分钟）。
- 服务验证完成后必须主动停止相关进程，避免依赖超时自动清理。

**测试用例编写与修改规范**：
- 新增或修改测试用例时，必须仔细校对测试逻辑是否正确覆盖目标功能点
- 测试用例的断言条件、输入数据、预期结果必须与实际需求完全匹配
- 不得编写"假通过"的测试用例（断言永远为 true，或测试目标与断言无关）
- 修改现有测试用例前，先理解原有测试意图，避免破坏有效覆盖

修复任何代码或修改内容之前，必须先完整研究项目整体结构和相关功能的实现方式，所有改动必须与现有实现风格保持一致且逻辑连贯。不得在未理解上下文的情况下孤立修改某一处代码，导致同一功能出现多种实现方式或前后端逻辑不一致。

## 项目概述

天枢决策引擎（com.hengshucredit.rule，仓库名 qlexpress-rule）是一套基于 **Spring Boot 3.5** 与 **QLExpress 4** 的可视化风控决策系统：支持 **决策表、决策树、决策流、规则集、交叉表、评分卡、复杂交叉表、复杂评分卡、QL 脚本** 等 9 种模型的可视化编排，涵盖项目、变量、名单、外数 API、外部数据库、模型、函数、规则测试、血缘分析、分流实验、执行日志和账单管理等能力。

> 后端 Java 包名前缀统一为 `com.hengshucredit.rule.*`（注意不是 hscredit），Maven `groupId` 为 `com.hengshucredit.rule`，`artifactId: qlexpress-rule`。

## 环境要求

- JDK 17
- Maven 3.6+
- MySQL 8
- Redis（与 server 使用同一实例，含密码和 database）
- Node.js 14+（前端开发，建议 22.14.x）

## 模块架构

| 模块 | 说明 | 端口 |
|------|------|------|
| `rule-engine-model` | 公共实体与 DTO | - |
| `rule-engine-core` | 规则编译与执行核心 | - |
| `rule-engine-server` | 管理端 REST API | 8080 |
| `rule-engine-client` | 客户端 SDK | - |
| `rule-engine-example` | 集成示例服务 | 7070 |
| `rule-engine-builder-ui` | Vue 3 前端控制台（独立部署） | 9090（dev）|
| `rule-engine-mysql` | MySQL docker-compose 配置 | - |
| `rule-engine-redis` | Redis docker-compose 配置 | - |

### 部署架构

```
浏览器 ←→ rule-engine-builder-ui（前端，dist/ 独立部署）
         ↓
      rule-engine-server（后端 API，8080）
      ↙              ↘
   MySQL           Redis（Pub/Sub 规则推送）
                       ↓
            业务应用（rule-engine-client SDK）
```

- **前后端分离**：`rule-engine-server/pom.xml` 中 `skip.ui.build` 默认为 `true`，前端 `npm run build` 产物在 `dist/` 独立部署，不混入后端目录
- **客户端不直连 MySQL**：通过 HTTP 拉取服务端已编译规则，缓存在进程内 L1 内存
- **Redis 必须与 server 同一实例**：规则发布等事件时向频道 `rule:push:{appName}` 发布消息
- **执行日志**：默认 HTTP 上报；classpath 中存在 `KafkaTemplate` Bean 时自动切换为 Kafka（主题 `rule-execution-log`）

## 开发命令

### 后端 (Maven)

```bash
# 编译所有模块
mvn clean compile

# 运行测试 / 单个测试类 / 单个测试方法
mvn test                                    # 运行所有后端测试
mvn test -Dtest=ClassName                   # 运行单个测试类
mvn test -Dtest=ClassName#methodName        # 运行单个测试方法

# 启动 rule-engine-server（端口 8080）
cd rule-engine-server && mvn spring-boot:run

# 启动 rule-engine-example（端口 7070）
cd rule-engine-example && mvn spring-boot:run

# 完整构建（跳过前端）
mvn clean package -DskipTests
```

### 前端 (Vue 3)

```bash
cd rule-engine-builder-ui

npm install
npm run dev      # 开发模式（9090，/api 代理到后端 8080）
npm run build    # 生产构建
npm run lint     # 代码检查
npm test         # 运行所有单元测试（tests/unit/）
npm run test:watch   # 监听模式
npm run test:coverage # 覆盖率报告
```

### 基础设施

```bash
# 根目录 docker-compose 已包含 mysql + redis + 初始化（空数据卷依次执行 schema 与 export）
docker-compose up -d
# 单独启动：
cd rule-engine-mysql && docker-compose up -d   # MySQL（仅 compose 文件，无 dockerfile）
cd rule-engine-redis && docker-compose up -d    # Redis
```

### 数据库初始化

- `schema.sql` 只包含数据库、表和索引等结构 DDL；`export_202607161151.sql` 是当前唯一的初始数据快照，不生产 `data-system.sql`
- 空 Docker 数据卷首次启动依次执行 `01-schema.sql` 和 `02-export.sql`；根编排的 `mysql-init` 对已有数据卷只重复执行 schema，不自动重放会覆盖数据的 export
- 手工完整恢复顺序：删除 `rule_engine` 数据库、执行 `schema.sql`、执行 `export_202607161151.sql`；export 会清空其覆盖的全部数据表
- `data-example.sql` / `data-tianshu-example.sql` 仅作为可选示例数据脚本手动导入，不属于系统初始数据来源
- 仅在 README 的 12 节「实现边界」中保留的已知限制（如血缘仅静态识别脚本引用）才是真实待修缮项

## 核心编译器（rule-engine-core）

`RuleCompiler` 接口定义 `compile(modelJson, varContext)` 方法，各模型有独立实现：

| 编译器 | 对应模型 |
|--------|----------|
| `DecisionTableCompiler` | 决策表 |
| `DecisionTreeCompiler` | 决策树 |
| `DecisionFlowCompiler` | 决策流 |
| `RuleSetCompiler` | 规则集 |
| `CrossTableCompiler` | 交叉表 |
| `ScorecardCompiler` | 评分卡 |
| `AdvancedCrossTableCompiler` | 复杂交叉表 |
| `AdvancedScorecardCompiler` | 复杂评分卡 |
| `ScriptPassthroughCompiler` | QL 脚本（直接透传） |

其他核心类：
- `CompileResult` - 编译结果，含生成的脚本和输出变量信息
- `VarContext` - 编译时变量上下文（`varId → scriptName` 映射，解决大小写不一致）
- `ConditionCompiler` / `ActionDataCompiler` - 条件与动作的子编译单元
- `QLExpressEngine` / `QLExpressEngineFactory` - QLExpress 执行引擎
- `AggregateBuiltinFunctionRegistry` - 内置聚合函数（sum/count/max/min/avg）

## 前端关键路径

- 设计器视图: `src/views/designer/`（9 个单文件组件）
- 页面视图: `src/views/`（project、rule、variable、model、function、test、log、billing、database、datasource、lineage、experiment、ruleList 等）
- API 层: `src/api/`（auth、billing、database、datasource、dataObject、definition、experiment、function、lineage、model、project、request、ruleList、runtimeLog、variable）
- 全局样式覆盖: `src/styles/element-override.scss`
- **变量选择 Mixin**: `src/mixins/varPickerMixin.js`
- 侧边栏菜单: `src/layout/index.vue`（el-menu 组件，新建页面需在此添加菜单项）

## 前端测试框架 (Jest + Vue Test Utils)

测试文件位于 `tests/unit/`：
- `varPickerMixin.spec.js` — 变量选择器 Mixin 单元测试
- `views/*.spec.js` — 各页面组件测试，含 layout、decisionTable、ruleTest、executionLog、functionList、modelDetail、modelList、projectList、ruleDetail、ruleList、variableList
- `utils/*.spec.js` — 工具函数测试（varDisplay、varTypes、decisionConditionTree、flowGraphCycle、actionDataCodegen）
- `constants/*.spec.js` — 常量测试

Jest 配置（`jest.config.js`）关键点：
- 使用 `jsdom` 测试环境，`@` 别名映射到 `src/`
- `monaco-editor` / `@logicflow/core` 已 mock，SCSS 文件 mock 为空模块
- `setup.js` 预置所有 API mock 和 Element Plus mock

**当前测试状态**：前端约 615 个用例（36 个 spec 文件）全部通过 ✅，后端约 186 个用例（core / client / server 三模块均有）全部通过 ✅（具体数字随用例增长变化，以 `npm test` / `mvn test` 实际输出为准）。

## 后端测试框架 (JUnit 4)

后端 `rule-engine-core`、`rule-engine-client`、`rule-engine-server` 三个模块均含测试。覆盖编译器（各 `*CompilerTest`）、执行引擎、内置函数、服务端控制器（`RuleSyncControllerTest`、`LogReportControllerTest`）、鉴权拦截器、`VariableSourceResolver`、各 `*ServiceTest` 等。

## 核心设计约束

### 铁律一：所有引用关联必须通过 ID

> ⚠️ 变量、模型、规则的引用关联**必须通过 ID**，禁止通过变量名称、变量编码、模型名称等业务字段进行关联。这些字段由客户自填且可随时修改，通过名称/编码关联会导致引用关系断裂。

| 引用方向 | 正确关联方式 | 禁止方式 |
|----------|-------------|----------|
| 规则引用变量 | `rule_definition_input_field.var_id` → `rule_variable.id` | `varCode` / `varLabel` |
| 规则引用模型 | `rule_definition_output_field.var_id` + `ref_type=MODEL` → `rule_model.id` | `modelCode` / `modelName` |
| 变量引用数据对象 | `rule_data_object_field.ref_object_id` → `rule_data_object.id` | `ref_object_code` |
| 设计器引用变量 | `_varId` 持久化到 modelJson | 仅保存 `varCode` |
| 函数引用变量 | 参数定义中关联 `rule_variable.id` | 变量名称回溯 |

### 铁律二：输出字段通过 `ref_type` 区分类型

> ⚠️ `rule_definition_output_field.var_id` 和 `rule_model_output_field.var_id` 可能是变量 ID 也可能是模型 ID，从值本身无法区分。**必须通过 `ref_type` 字段显式标注**，禁止仅凭 var_id 值猜测类型。

### 铁律三：变量、模型、导入内容原样保留

> ⚠️ **铁律**：变量编码（varCode）、模型编码（modelCode）、导入的 Java 实体/JSON/DDL 中的字段名等，用户输入什么就原样保存什么。**禁止**对名称做任何自动转换（驼峰、下划线、大小写等），**禁止**自动填充 scriptName、修改内容或生成派生字段。

### 铁律四：所有代码修改都必须校验逻辑正确性、与整体代码结构和样式一致性、要通过前后端测试、在确认完代码无误后，要模拟用户前端UI完整操作一遍确保流程顺畅、功能无误且适合业务人员使用。

> ⚠️ **铁律**：代码修改完要核对逻辑是否正确、要review代码实现、要通过对应测试、且需要通过浏览器前端UI界面完整操作一遍确保流程顺畅，功能无误且符合需求，浏览器前端操作时禁止通过后端生成数据直接跳过某步操作。

### 铁律五：前端代码修改需要符合 eslint 规范。

> ⚠️ **铁律**：前端代码修改需要符合 eslint 规范。



## 设计器变量引用链路

`varPickerMixin.js` 是设计器页面的核心 mixin，统一加载项目变量/常量/对象字段到 `projectRefs`。

变量引用链路（**唯一正确方式**）：
1. 设计器中通过 `varPicker` 选择变量 → **必须同时持久化 `_varId`（`rule_variable.id`）**到 modelJson
2. 加载设计器数据时通过 `_varId` 查询变量元信息，同步填充 `varCode` / `varLabel` / `varType` 等展示字段
3. 后端编译时通过 `VarContext`（`varId → scriptName` 映射）解析变量引用
4. **禁止**在步骤 1 中仅保存 `varCode` 而不保存 `_varId`；**禁止**在步骤 2 中通过 `varLabel` 回溯匹配

**各设计器 `_varId` 持久化状态**：

| 设计器 | 条件变量 | 动作变量 | 备注 |
|--------|---------|---------|------|
| DecisionTable | ✅ `syncVarItem()` | ✅ `syncVarItem()` | 已完整实现 |
| DecisionTree | ✅ `leftVarId`/`rightVarId` 持久化到 nodes/edges | ✅ `actionData` | 节点/连线条件变量已正确持久化 |
| DecisionFlow | ✅ `leftVarId`/`rightVarId` 持久化到 nodes/edges | ✅ `actionData` | 已与 DecisionTree 保持一致 |
| RuleSet | ✅ `syncVarItem()`（条件+动作叶子节点） | ✅ `syncVarItem()` | 已完整实现 |
| CrossTable | — | — | 简单交叉表，无变量选择 |
| Scorecard | — | — | 简单评分卡，无变量选择 |
| AdvancedCrossTable | ✅ `dim._varId` | ✅ `resultVar._varId` | 已完整实现 |
| AdvancedScorecard | ✅ `dim._varId` | ✅ `resultVar._varId` | 已完整实现 |
| ScriptEditor | ✅ `_varId` 通过 `scriptVarRefs` 映射持久化 | ✅ `insertVar` 记录 `varId`，保存时同步脚本中实际引用，`modelJson` 中同时保存 `script` 和 `scriptVarRefs`，加载时用 `varId` 同步变量编码变更 |

> ✅ **已修复**：ScriptEditor 的 var-picker 选择时，通过 `scriptVarRefs` 数组同时持久化 `_varId`（`varId`）到 `modelJson.scriptVarRefs`。保存时 `syncScriptVarRefsFromScript()` 从脚本中提取实际引用并更新映射；加载时 `_syncModelVarRefs()` 用 `varId` 查找最新的 `refCode`，自动同步变量编码变更。

## 功能现状（已实现 / 待修缮）

> ⚠️ 以下为实测代码现状。README 顶部声明项目仍为「未发布版」，部分功能逻辑仍在修缮。修改前先读对应控制器 `rule-engine-server/.../controller/mgmt`（Billing、DbDatasource、ExecutionLog、ExternalDatasource、RuleDataObject、RuleDefinition、RuleExperiment、RuleFunction、RuleLineage、RuleList、RuleProject、RuleVariable、RuntimeCallLog）与 `controller/sync`（LogReport、RuleSync），确认实际接口，不要凭旧文档猜测。

**已实现（前后端均有对应页面/API，勿当作缺失来"补"）**：

| 能力 | 前端入口 | 后端控制器 | 备注 |
|------|----------|------------|------|
| 项目管理/令牌 | `views/project` | `RuleProjectController` | 含访问令牌、接口说明 |
| 规则设计 9 种模型 | `views/designer/*`（9 个） | `RuleDefinitionController` | 含 RuleSet 设计器 |
| 变量管理 | `views/variable` | `RuleVariableController` | API/DB/名单变量支持在线测试与详情 |
| 名单管理 | `views/ruleList` 旁 | `RuleListController` | 名单库/记录/导入导出/匹配日志 |
| 外数管理 | `views/datasource` | `ExternalDatasourceController` | 数据源/接口/鉴权/调用日志 |
| 数据库管理 | `views/database` | `DbDatasourceController` | 后端 HikariCP 连接池 |
| 模型管理 | `views/model` | `RuleModelController` | 入参/出参/执行测试/调用日志 |
| 函数管理 | `views/function` | `RuleFunctionController` | QLExpress/Java/Spring Bean |
| 规则测试 | `views/test` | `RuleDefinitionController` 测试 | 含追踪树 |
| 血缘分析 | `views/lineage` | `RuleLineageController` | 基于结构化字段（脚本动态引用仅静态识别） |
| 分流实验 | `views/experiment` | `RuleExperimentController` | 冠军/挑战/测试组；支持版本回滚 |
| 执行日志 | `views/log` | `ExecutionLogController` | 服务端+客户端追踪 |
| 账单管理 | `views/billing` | `BillingController` | 计费项/明细/汇总 |
| 版本回滚与对比 | `RuleDetail/ModelDetail/FunctionList/ExperimentDetail` | `RuleDefinitionController` 等 `/versions`、`/versionCompare`、`/rollback` | **已实现**：规则、模型、函数、实验均支持查看版本、对比差异、回滚 |

**仍为待修缮项（以 README 第 12 节「实现边界」为准）**：
- 血缘分析仅能静态识别结构化字段与变量来源配置，脚本中复杂的动态引用无法保证完全识别。
- 控制台默认 admin/`1qaz@WSX`，生产需替换；数据库管理只应配只读账号、查询类 SQL。
- 外数/数据库/名单变量在线测试依赖对应数据源可用，失败会写入各自模块日志。

> ⚠️ 旧版本文档曾把「外数/数据库/账单/实验/血缘/名单/版本回滚」列为待实现，现已全部落地。若任务需要新增能力，先确认是否已在表中，避免重复造轮子。

## 技术栈

- Spring Boot 3.5.16
- QLExpress 4.1.0
- MyBatis Plus 3.4.3.4
- Vue 3.5.40 + Element Plus 2.14.3
- Redis (Pub/Sub 规则推送)
- Kafka (可选执行日志上报)

## 参考资料

- 详细使用说明见 `README.md`
- 参考实现：`rule_examples/` 目录下的企业级/个人开源参考项目






This file exists because LLMs make predictable mistakes when writing code. Not random mistakes. The same ones, over and over. I've watched it happen enough times to write them down.
These are not suggestions. These are rules. Follow them and you'll produce code that doesn't need to be rewritten. Ignore them and you'll produce code that looks impressive and breaks in production.
## 1. Read Before You Write
The single biggest source of bad LLM code is not reading the existing codebase before writing new code. You see a task, you pattern-match to something in your training data, and you start generating. This is almost always wrong.
Before writing anything:
- Read the files you're about to modify. Not skim. Read.
- Look at how similar things are done elsewhere in the project. If there's a pattern for API routes, follow that pattern. If there's a utility function that does half of what you need, use it.
- Check the imports at the top of the file. They tell you what libraries this project actually uses. Don't introduce axios if the project uses fetch everywhere. Don't introduce lodash if the project uses native methods.
- Look at the test files. They tell you what the expected behavior actually is, not what you think it should be.
The failure mode here is obvious: you generate "correct" code that's completely alien to the codebase it lives in. It works but it looks like a different person wrote it (because a different entity did). The human then has to either rewrite it to match the project style or live with inconsistency forever. Both are bad.
If you're not sure how something is done in this project, say so. "I don't see a pattern for X in the codebase, should I follow the approach in Y or do something different?" is always better than guessing.
## 2. Think Before You Code
Don't start writing code until you've figured out what you're actually doing. This sounds obvious but it's the most common failure mode.
What this looks like in practice:
**State your assumptions.** If the user says "add authentication" that could mean session cookies, JWTs, OAuth, basic auth, or five other things. Don't pick one silently. Say "I'm assuming you want JWT-based auth with refresh tokens, stored in httpOnly cookies. If you want something different, let me know." If you're wrong, you've lost 10 seconds. If you silently guess wrong, you've lost an hour.
**Name the tradeoffs.** Almost every implementation choice has a tradeoff. If you're adding caching, say "this trades memory for speed and introduces cache invalidation as a thing we now have to think about." The user might say "actually I don't want that complexity." Better to know before you write 200 lines.
**If multiple approaches exist, present them briefly.** Not five. Two, maybe three. With a recommendation. "There are two ways to do this. Option A is simpler but doesn't handle edge case X. Option B handles everything but adds a dependency on Z. I'd go with A unless you expect X to actually happen."
**If something is confusing, stop.** Don't fill confusion with plausible-sounding code. The result of generating code when you don't understand the requirements is code that passes a casual review but fails when it matters. Just say what's confusing and ask.
## 3. Simplicity
Write the minimum amount of code that solves the problem. Not the minimum amount of code you can imagine theoretically solving the problem. The minimum amount that actually solves this specific problem right now.
The instinct to over-engineer is strong. Resist it. Here's what over-engineering looks like in practice:
**Premature abstraction.** You need to send one type of email. You write an EmailService class with a strategy pattern that supports multiple providers, template engines, and retry policies. The user wanted `sendWelcomeEmail(user)`. Write that function. If they need more later, they'll ask.
```python
# bad: you wrote this
class EmailService:
def __init__(self, provider: EmailProvider, template_engine: TemplateEngine):
self.provider = provider
self.template_engine = template_engine
async def send(self, template: str, context: dict, recipient: str, **kwargs):
rendered = self.template_engine.render(template, context)
await self.provider.send(recipient, rendered, **kwargs)
# good: you should have written this
async def send_welcome_email(user):
body = f"Welcome {user.name}! Your account is ready."
await send_email(to=user.email, subject="Welcome", body=body)
```
**Speculative error handling.** You wrap everything in try/catch blocks for errors that can't happen. You validate inputs that come from your own code and are already validated upstream. You add null checks on values that are never null. Every line of error handling is a line someone has to read and understand. Only handle errors that can actually occur.
**Unnecessary configurability.** You make the batch size a parameter. You make the retry count configurable. You add environment variables for things that will never change. Configuration is not free. Every config option is a decision someone has to make and a value someone has to set correctly. Hardcode things until there's a real reason not to.
**Dead flexibility.** Interfaces with one implementation. Abstract base classes with one child. Generic type parameters that are only ever instantiated with one type. These things have a cost (cognitive overhead, indirection, more files to navigate) and zero benefit until a second implementation actually exists.
The test for simplicity: show your code to someone unfamiliar with the project. If they have to ask "why is this abstracted like this?" and the answer is "in case we need to..." then you've over-engineered it. "In case we need to" is not a requirement. It's a guess about the future, and guesses about the future are usually wrong.
## 4. Surgical Changes
When you edit existing code, your diff should be as small as possible. Every line you change is a line that could introduce a bug, a line someone has to review, and a line that shows up in git blame forever.
Rules:
**Don't touch what you weren't asked to touch.** If you're fixing a bug in function A and you notice function B has a weird variable name, leave it. If function C has a comment with a typo, leave it. If the import order doesn't match your preference, leave it. Your job is to fix the bug in function A.
**Match the existing style.** If the file uses single quotes, use single quotes. If the file uses `snake_case`, use `snake_case`. If the file has no semicolons, don't add semicolons. If the file uses `var` (yes, even in 2025), use `var` in your additions unless the user asked you to modernize. Consistency within a file beats your personal preference.
**Clean up after yourself, not after others.** If your change makes an import unused, remove that import. If your change makes a variable unused, remove that variable. If your change makes a function unused, remove that function. But only if YOUR change caused it. Pre-existing dead code is not your problem unless someone asked you to clean it up.
**Don't reformat.** Don't run prettier on a file that wasn't formatted with prettier. Don't change indentation from 4 spaces to 2. Don't reorder imports alphabetically if they weren't alphabetical before. Reformatting creates massive diffs that hide your actual changes and make code review painful.
The test: look at your diff. Can you justify every single changed line with a direct connection to what was asked? If any line is there because "while I was in there I thought I'd..." then revert it.
## 5. Verification
The difference between code that works and code you think works is testing. You should be paranoid about this distinction.
**Write the test first when fixing bugs.** Before you fix anything, write a test that reproduces the bug. Run it. Watch it fail. Then fix the bug. Run the test. Watch it pass. This is not optional and not TDD dogma. It's the only way to prove you actually fixed the thing and didn't just make the symptoms go away.
**Run existing tests before and after your changes.** If tests passed before your change and fail after, you broke something. This is obvious. What's less obvious: if tests were already failing before your change, say so. Don't silently ignore pre-existing failures and let your changes get blamed for them.
**Don't write tests for the sake of writing tests.** A test that checks whether a constructor sets properties is worthless. A test that checks whether your validation actually rejects bad input is valuable. Test behavior, not implementation. Test the interesting cases, not the trivial ones.
**If you can't write a test, say why.** Sometimes the architecture makes testing hard. That's useful information. "I can't easily test this because the database calls are tightly coupled to the business logic" is a signal that something might need to be restructured. Don't just skip testing and hope.
## 6. Goal-Driven Execution
Every task should have a clear success criterion before you start writing code. If the criterion is vague, make it specific. If you can't make it specific, ask.
Transform vague tasks into verifiable ones:
- "Add validation" becomes "reject inputs where email is missing or invalid, return 400 with a message that says what's wrong, add tests for both cases"
- "Fix the bug" becomes "write a test that reproduces the reported behavior, make the test pass, verify existing tests still pass"
- "Improve performance" becomes "profile first, identify the bottleneck, fix that specific thing, measure again"
For anything that takes more than one step, state the plan before executing:
```
Plan:
1. Add the new database column with a migration
2. Update the model to include the new field
3. Modify the API endpoint to accept and return the field
4. Add validation for the field
5. Write tests for the new behavior
6. Run full test suite to check for regressions
```
This does two things: it lets the user catch mistakes in your approach before you waste time implementing them, and it forces you to actually think through the steps instead of just diving in and figuring it out as you go.
## 7. Debugging
When something doesn't work, don't guess. Investigate.
**Read the error message.** The whole thing. Including the stack trace. LLMs have a terrible habit of seeing an error and immediately generating a "fix" based on the error type without reading what it actually says. A TypeError could mean a hundred different things. The message and stack trace tell you which one.
**Reproduce first.** Before you change anything, make sure you can reproduce the problem. If you can't reproduce it, you can't verify your fix. "I think this should fix it" is not debugging. It's gambling.
**Change one thing at a time.** If you change three things and the bug goes away, you don't know which change fixed it. You also don't know if the other two changes introduced new bugs. Change one thing. Test. Change another. Test.
**Don't add workarounds without understanding the root cause.** If a value is unexpectedly null, don't just add a null check and move on. Figure out why it's null. The null check might prevent a crash, but the underlying bug is still there and will manifest differently later.
**If you're stuck, say so.** "I've tried X and Y and neither worked. Here's what I'm seeing. I think the issue might be Z but I'm not sure." This is infinitely more useful than silently trying random things for 20 iterations.
## 8. Dependencies
Don't add dependencies without thinking about it.
Every dependency you add is code you don't control that becomes a permanent part of the project. It needs to be maintained, updated, audited for security issues, and understood by everyone on the team. The cost is almost always higher than it looks.
Before adding a package:
- Can you do this with what's already in the project? If the project has axios, don't add node-fetch. If the project uses date-fns, don't add moment.
- Can you do this with the standard library? You don't need lodash for `Array.prototype.map`. You don't need uuid if `crypto.randomUUID()` exists.
- Is this dependency actually maintained? Check the last commit date. Check the issue count. Check if the maintainer responds to issues.
- How big is it? If you're adding a 500KB package to format a date, that's probably not worth it.
When you do add a dependency, say why. "I'm adding zod because this project needs runtime schema validation and there's nothing in the existing dependencies that does this" is fine. Silently adding packages to package.json is not.
## 9. Communication
How you communicate about code matters as much as the code itself.
**Say what you did and why.** Don't just dump a code block. "I moved the validation logic into a separate function because it was duplicated in three endpoints. This also makes it testable independently." Now the user understands the change without reading every line.
**Flag concerns.** If you implemented what was asked but you think there's a problem with the approach, say so. "This works but it makes a database call for every item in the list. If the list gets large this will be slow. Want me to batch it?" is the kind of proactive communication that saves hours later.
**Be precise about what you're uncertain about.** "I'm not sure if this library supports streaming responses" is useful. "I think this should work" is not. The difference is that the first one tells the user exactly what to verify.
**Don't explain things the user already knows.** If they asked you to add a REST endpoint, don't explain what REST is. If they asked for a database index, don't explain what indexes do. Match your explanation level to the user's demonstrated knowledge.
**Commit messages matter.** If you're writing a commit message, make it specific. "Fix bug" is useless. "Fix null pointer in user lookup when email contains uppercase chars" tells the next person exactly what happened.
## 10. Common Failure Modes
These are the patterns I see most often. If you catch yourself doing any of these, stop and reconsider.
**The Kitchen Sink.** Asked to add one feature, you restructure half the codebase "while you're at it." Don't. Do the one thing.
**The Wrong Abstraction.** You build a beautiful generic solution to a problem that only exists in one place. Duplication is far cheaper than the wrong abstraction. Copy-paste twice before you abstract.
**The Invisible Decision.** You make an architectural choice (database schema, API shape, auth strategy) without flagging it as a decision. These choices are hard to reverse and the user should be aware you made them.
**The Optimistic Path.** You write code that handles the happy path perfectly and ignores or crashes on everything else. Think about what happens when the API returns 500. When the file doesn't exist. When the user submits an empty form.
**The Knowledge Hallucination.** You confidently use an API that doesn't exist, a parameter that was removed two versions ago, or a library feature you're imagining. If you're not 100% sure a method exists with this exact signature, say so. Check the docs. Look at the actual source code in the project.
**The Style Drift.** You write code in your "preferred" style instead of matching the project. Functional patterns in an OOP codebase. Classes in a functional codebase. TypeScript patterns in a JavaScript project. Match the codebase, not your preferences.
**The Runaway Refactor.** You start fixing one thing. It touches another thing. That touches another. Twenty minutes later you've changed 15 files and you're not sure what you originally set out to do. If a fix is cascading, stop. Tell the user what's happening. Get buy-in before continuing.
---
These guidelines work when they produce fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
