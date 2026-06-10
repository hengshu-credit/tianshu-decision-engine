# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

所有回答以及中间的思考过程，尽可能全部使用中文进行解答。

所有实现的代码，在完成后都需要反复核对逻辑是否正确，是否与用户提出的需求匹配，如果有问题修复后需要再次核对。

任何修改都应该遵守这个校验逻辑: 前端代码修改完成后需要通过 `npm run dev` 验证下项目是否有问题（验证无误后必须关掉进程），后端代码修改完成后需要通过 `mvn clean install -DskipTests` 编译项目无误 且 `mvn spring-boot:run` 能正常启动 rule-engine-server 后端服务。

修复任何代码或修改内容之前，必须先完整研究项目整体结构和相关功能的实现方式，所有改动必须与现有实现风格保持一致且逻辑连贯。不得在未理解上下文的情况下孤立修改某一处代码，导致同一功能出现多种实现方式或前后端逻辑不一致。

## 项目概述

衡枢规则引擎是基于 QLExpress 的可视化风控决策系统，基于 Spring Boot 2.3 + QLExpress 4，支持决策表、决策树、决策流、交叉表、评分卡、复杂交叉表、复杂评分卡、QL 脚本等模型的可视化编排，涵盖变量管理、模型管理、函数管理、规则测试、执行日志等功能，是一套支撑信贷风控产品全流程的风控决策引擎系统。

## 环境要求

- JDK 8
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
| `rule-engine-builder-ui` | Vue 2 前端控制台（独立部署） | 9090（dev）|
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

### 执行时序

1. 客户端注入 `RuleEngineClient` 后 `start()`：HTTP 全量同步规则 + 订阅 Redis 频道
2. 业务调用 `execute(ruleCode, params)`：先查 L1 缓存，未命中则 HTTP 单条拉取
3. 本地 QLExpress 执行，返回 `RuleResult`
4. 异步上报执行日志（HTTP 或 Kafka）
5. Redis 实时推送规则变更，客户端更新/失效本地缓存

## 开发命令

### 后端 (Maven)

```bash
# 编译所有模块
mvn clean compile

# 运行测试 / 单个测试类
mvn test
mvn test -Dtest=ClassName

# 启动 rule-engine-server（端口 8080）
cd rule-engine-server && mvn spring-boot:run

# 启动 rule-engine-example（端口 7070）
cd rule-engine-example && mvn spring-boot:run

# 完整构建（跳过前端）
mvn clean package -DskipTests
```

### 前端 (Vue 2)

```bash
cd rule-engine-builder-ui

npm install
npm run dev      # 开发模式（9090，/api 代理到后端 8080）
npm run build    # 生产构建
npm run lint     # 代码检查
```

### 基础设施

```bash
cd rule-engine-mysql && docker-compose up -d   # MySQL
cd rule-engine-redis && docker-compose up -d    # Redis
```

### 截图采集

```bash
# 依赖 scripts/ 下的 node_modules + playwright
node scripts/capture-designer-screenshots.cjs
```

## 关键类

### 核心编译器（rule-engine-core）

`RuleCompiler` 接口定义 `compile(modelJson, varContext)` 方法，各模型有独立实现：

| 编译器 | 对应模型 |
|--------|----------|
| `DecisionTableCompiler` | 决策表 |
| `DecisionTreeCompiler` | 决策树 |
| `DecisionFlowCompiler` | 决策流 |
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

### 服务端（rule-engine-server）

- 管理端 Controller: `controller/mgmt/`（RuleProject、RuleDefinition、RuleVariable、RuleFunction、RuleModel、ExecutionLog、RuleDataObject）
- 同步 Controller: `controller/sync/`（RuleSync、LogReport）
- 控制台登录: `consolelogin/ConsoleAuthController`
- 核心服务: `service/`（RuleCompileService、RulePublishService、RuleExecuteService）

### 客户端 SDK（rule-engine-client）

- `RuleEngineClient` - 入口类，负责规则同步和执行
- `L1MemoryCache` - L1 内存缓存，按规则编码查找
- `HttpSyncClient` - 启动时全量拉取规则
- `RedisSubscriber` - 订阅 `rule:push:{appName}` 实时接收增量更新，含启动重试和守护线程心跳
- `HttpLogReporter` / `KafkaLogReporter` - 执行日志上报（自动根据 classpath 选择）
- `TokenAuthInterceptor` - 校验 `X-Rule-Token` 或 Query `token`，须与 `rule_project.access_token` 匹配

客户端 SDK 通过 `META-INF/spring.factories` 注册 `RuleEngineAutoConfiguration`：配置了 `rule-engine.client.server-url` 且存在 `RedisConnectionFactory` Bean 时自动创建 `RuleEngineClient`，启动时自动 `start()`。当 `project-id > 0` 时，还会同步控制台「函数管理」中的函数定义。

## 数据库

- 建表脚本: `rule-engine-server/src/main/resources/sql/schema.sql`
- 示例数据: `rule-engine-server/src/main/resources/sql/data-example.sql`
- MySQL 默认凭证: root / 1qaz@WSX（可通过环境变量 `MYSQL_USERNAME`、`MYSQL_PASSWORD` 覆盖）

## 客户端集成

在业务工程 `pom.xml` 中依赖 `rule-engine-client`（通过 `META-INF/spring.factories` 自动配置）。

```yaml
rule-engine:
  client:
    server-url: http://localhost:8080
    app-name: your-service-name
    token: <项目访问令牌>
    project-id: 1
```

```java
@Resource
private RuleEngineClient ruleClient;

public void example() {
    Map<String, Object> params = new HashMap<>();
    params.put("amount", 10000);
    RuleResult r = ruleClient.execute("YOUR_RULE_CODE", params);
}
```

常用方法：`refreshRule` / `refreshAll` 主动刷新；`getRuleInfo` 查看缓存元数据；`getEngine` / `getFunctionRegistrar` 扩展自定义函数。

客户端 Token 鉴权针对 `/api/sync/` 等路径，请求头 `X-Rule-Token` 或 Query `token` 须与 `rule_project.access_token` 匹配。

## 控制台登录

默认账号: `admin` / `1qaz@WSX`（可通过环境变量 `CONSOLE_USERNAME`、`CONSOLE_PASSWORD` 修改）。关闭登录可将 `rule-engine.console-login.enabled` 设为 `false`。

## 前端路由

| 类型 | 前端路由 |
|------|----------|
| 项目管理 | `/#/project` |
| 规则管理 | `/#/rule` |
| 决策表 | `/#/designer/table/:id` |
| 决策树 | `/#/designer/tree/:id` |
| 决策流 | `/#/designer/flow/:id` |
| 交叉表 | `/#/designer/cross/:id` |
| 评分卡 | `/#/designer/score/:id` |
| 复杂交叉表 | `/#/designer/cross-adv/:id` |
| 复杂评分卡 | `/#/designer/score-adv/:id` |
| QL 脚本 | `/#/designer/script/:id` |
| 变量管理 | `/#/variable` |
| 模型管理 | `/#/model` |
| 函数管理 | `/#/function` |
| 规则测试 | `/#/test` |
| 执行日志 | `/#/log` |

> 登录页 `/login` 是顶层路由（不在 Layout 下），由 `router/index.js` 中 `beforeEach` 守卫在 `loginEnabled=false` 时使用 `window.location.replace` 跳转，避免嵌套导航错误。

## 前后端关键路径

### 后端

- REST Controller: `rule-engine-server/src/main/java/com/bjjw/rule/server/controller/`
- MyBatis Mapper: `rule-engine-server/src/main/java/com/bjjw/rule/server/mapper/`
- 核心编译执行: `rule-engine-core/src/main/java/com/bjjw/rule/core/`
- 函数注册: `rule-engine-core/src/main/java/com/bjjw/rule/core/function/`

### 前端

- 设计器视图: `src/views/designer/`（8 个单文件组件）
- 页面视图: `src/views/`（project、rule、variable、model、function、test、log 等）
- API 层: `src/api/`（auth、dataObject、definition、function、model、project、request、variable）
- 全局样式覆盖: `src/styles/element-override.scss`
- **变量选择 Mixin**: `src/mixins/varPickerMixin.js`
- 侧边栏菜单: `src/layout/index.vue`（el-menu 组件，新建页面需在此添加菜单项）

## Monaco Editor 加载方式

前端使用 **AMD loader 方式**加载 monaco-editor，不依赖 `monaco-editor-webpack-plugin`：

- `vue.config.js` 通过 `CopyWebpackPlugin` 将 `node_modules/monaco-editor/min/vs` 复制到输出目录的 `vs/`
- `src/main.js` 中手动配置 `window.MonacoEnvironment.getWorkerUrl`，指向各语言 worker 文件
- `src/components/MonacoEditor.vue` 使用 `<base>` 配置动态加载 worker

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

| 字段 | ref_type 值 | var_id 指向 |
|------|------------|------------|
| `rule_definition_output_field` | `VARIABLE` | `rule_variable.id` |
| `rule_definition_output_field` | `MODEL` | `rule_model.id` |
| `rule_model_output_field` | `VARIABLE` | `rule_variable.id` |
| `rule_model_output_field` | `MODEL` | `rule_model.id` |
| 输入字段（`*_input_field`） | 固定为 `VARIABLE`，无需 `ref_type` | - |

### 铁律三：变量、模型、导入内容原样保留

> ⚠️ **铁律**：变量编码（varCode）、模型编码（modelCode）、导入的 Java 实体/JSON/DDL 中的字段名等，用户输入什么就原样保存什么。**禁止**对名称做任何自动转换（驼峰、下划线、大小写等），**禁止**自动填充 scriptName、修改内容或生成派生字段。

具体约束：
- 变量编码 `varCode` → 用户输入什么就保存什么，数据库原样落库
- 脚本名称 `scriptName` → 由用户手动填写或从导入内容中原样提取，不自动生成
- Java 实体类字段名 → 从 `.java` 源码中解析出的字段名原样保存为 `varCode`，不转驼峰
- JSON / DDL 导入字段名 → 原样保存为 `varCode`，不转驼峰
- `rule_data_object.object_code` → 用户输入什么就保存什么，不自动转换

> ⚠️ **注意**：QLExpress 脚本中引用变量时使用 `scriptName`（由用户填写或原样提取），与 `varCode` 的格式无关，两者各自独立。用户可自行决定是否使用驼峰式命名，代码层面不做任何强制转换。

### 铁律四：数据对象导入与嵌套结构

> ⚠️ **铁律**：单个数据对象应直接支持嵌套逻辑，而非生成多个平级子对象。

**当前实现问题（待改进）**：
- Java 实体导入：每个类（含嵌套类）生成一个独立 `RuleDataObject`，通过 `parentObjectId` 关联
- JSON 导入：嵌套 `JSONObject` 递归生成子 `ParsedObject`，每个子对象创建独立 `RuleDataObject`
- DDL 导入：不支持嵌套（每个表对应一个独立对象）
- 同名对象通过 `objectCode` 去重只生成一条记录，无法表达"同一类型在不同位置有不同嵌套结构"

**改进设计目标**：
- 单个 `RuleDataObject` 的 `rule_data_object_field` 字段中，嵌套字段（`varType=OBJECT`）通过 `ref_object_id` 关联到被引用的另一个 `RuleDataObject`（而非 `ref_object_code`）
- `RuleDataObjectField` 新增字段：`ref_object_id`（指向 `rule_data_object.id`），替代 `ref_object_code`
- 同一对象类型可被多个字段引用，支持"不同入口有不同嵌套"场景
- JSON/Java 导入时，若嵌套对象已在数据库中存在，直接引用（通过 `ref_object_id`）；若不存在则创建新对象
- DDL 导入支持外键 `REFERENCES` 语法，自动解析表间关联生成嵌套引用

**涉及改动**：
- `RuleDataObjectField` 新增 `ref_object_id` 字段（Long），与 `ref_object_code` 共存，过渡期兼容
- `ParsedField` 新增 `refObjectId` 字段
- `JavaEntityParser` / `JsonSchemaParser` 支持嵌套时填充 `refObjectId`
- `RuleDataObjectService.batchCreateFields()` 写入字段时解析 `refObjectId`
- 前端展示时，`ref_object_id` 优先展示关联对象名，无 `ref_object_id` 时回退 `ref_object_code`

### 铁律五：出入参与依赖关系

规则、模型、项目均需记录完整的出入参信息和依赖图/血缘图。

**项目（`rule_project`）需记录**：
- 引用规则 ID 列表 → `rule_project_ref`（项目关联全局规则）

**规则（`rule_definition`）需记录**：
- 输入变量 ID 列表 → `rule_definition_input_field.var_id`（`ref_type=VARIABLE`）
- 输出引用 ID 列表 → `rule_definition_output_field.var_id`（`ref_type=VARIABLE` 或 `ref_type=MODEL`）
- 中间过程变量（仅在规则内部计算，不出参）→ 在 modelJson 中以 `_varId` 保存但不出参
- 引用的项目 ID → `rule_definition.project_id`

**模型（`rule_model`）需记录**：
- 输入变量 ID 列表 → `rule_model_input_field.var_id`（`ref_type=VARIABLE`）
- 输出引用 ID 列表 → `rule_model_output_field.var_id`（`ref_type=VARIABLE` 或 `ref_type=MODEL`）
- 引用的规则 ID 列表 → `rule_model_ref`（模型关联全局规则）

## 设计器变量引用链路

`varPickerMixin.js` 是设计器页面的核心 mixin，统一加载项目变量/常量/对象字段到 `projectRefs`。

`projectRefs` 三层结构：

| category | 来源 | refCode 格式 | 示例 |
|----------|------|-------------|------|
| `standalone` | 普通变量（varSource ≠ CONSTANT） | `scriptName \|\| varCode` | `taxAmount` |
| `constant` | 常量（varSource = CONSTANT） | `scriptName \|\| varCode` | `MAX_RETRY_COUNT` |
| `object` | 数据对象字段 | `对象scriptName.字段scriptName` | `TaxRequest.amount` |

变量引用链路（**唯一正确方式**）：
1. 设计器中通过 `varPicker` 选择变量 → **必须同时持久化 `_varId`（`rule_variable.id`）**到 modelJson
2. 加载设计器数据时通过 `_varId` 查询变量元信息，同步填充 `varCode` / `varLabel` / `varType` 等展示字段
3. 后端编译时通过 `VarContext`（`varId → scriptName` 映射）解析变量引用
4. **禁止**在步骤 1 中仅保存 `varCode` 而不保存 `_varId`；**禁止**在步骤 2 中通过 `varLabel` 回溯匹配

> ⚠️ **已知问题**：DecisionTree / DecisionFlow / AdvancedCrossTable / ScriptEditor 的 var-picker 选择时仅保存 `varCode`，未持久化 `_varId`，会导致客户修改变量编码后引用断裂。此为待修复项。

## 已知注意事项

### Vue SFC 中使用 SCSS 变量

`element-override.scss` 中定义了 `$secondary-color` 等 SCSS 变量，全局生效。在 Vue SFC 的 `<style scoped lang="scss">` 中可直接使用这些变量。

### router beforeEach 中避免嵌套导航

在 `beforeEach` 守卫中若检测到需要重定向（如 `loginEnabled=false`），应使用 `window.location.replace()` 而非 `next(path)`，否则 Vue Router 3 会报错 "Navigation cancelled"。

### `scope` 字段与 `projectId` 的对应关系

- `scope = GLOBAL` 时，`projectId = 0`，表示全局资源
- `scope = PROJECT` 时，`projectId > 0`，表示项目级资源
- 查询时：GLOBAL 资源对所有项目可见；PROJECT 资源仅对所属项目可见

### 规则编译结果缓存

`rule_definition_content.compiled_script` 字段存储编译产物（QLExpress 脚本），发布时复制到 `rule_published.compiled_script`。编译时依赖 `VarContext` 将 `varId` 映射为 `scriptName`，生成的脚本中所有变量引用均使用 `scriptName`。

## 技术栈

- Spring Boot 2.3.0
- QLExpress 4.1.0
- MyBatis Plus 3.4.3.4
- Vue 2.6.14 + Element UI 2.15.14
- Redis (Pub/Sub 规则推送)
- Kafka (可选执行日志上报)

## 待实现

### 外数管理（`/#/datasource`）

通过 API 获取外部数据的统一接入模块，对应变量中的 **接口变量**（`rule_variable.var_source = 'API'`）。

**设计目标**：
- 在外数管理模块中，根据外部合作方提供的接口文档配置数据源
- 支持多种鉴权方式（API Key / OAuth2 / Bearer Token / Basic Auth / 自定义 Header）
- 自动刷新 Token 管理（支持配置 Token 过期提前量）
- 调用 API 获取数据后，将响应结果映射到接口变量的字段
- 与变量管理联动：接口变量的 `varSource = 'API'` 时，自动关联到对应的外数配置

**涉及数据库表**（需新建）：
- `rule_external_datasource` - 外数数据源定义（名称、描述、基础 URL、连接超时等）
- `rule_external_api_config` - API 配置（路径、HTTP 方法、鉴权类型、鉴权参数、请求参数映射、响应字段映射等）

**涉及后端**：
- `rule-engine-model` 新增 ExternalDatasource / ExternalApiConfig 实体类
- `rule-engine-server` 新增 `ExternalDataSourceService`（管理数据源和 API 配置）
- `ExternalDataCaller` - 统一 HTTP 调用组件，支持多鉴权方式
- Token 刷新策略：定时任务或懒刷新 + 缓存

**外数 API 配置字段说明**：

| 字段 | 说明 |
|------|------|
| `billing_type` | 计费方式：PER_CALL-按次计费 / PER_HIT-按查得计费 / SUBSCRIPTION-包月套餐 |
| `unit_price` | 调用单价（元/次 或 元/查得），按次和按查得时必填 |
| `retry_enabled` | 是否启用异常重试 |
| `retry_max_attempts` | 最大重试次数（默认 3） |
| `retry_backoff_multiplier` | 重试间隔倍数（退避策略，如 2 表示 1s→2s→4s） |
| `retry_max_interval_ms` | 最大重试间隔（毫秒，默认 30000） |
| `retry_on_status_codes` | 需要重试的 HTTP 状态码（如 429, 500, 502, 503, 504） |
| `connect_timeout_ms` | 连接超时（毫秒） |
| `read_timeout_ms` | 读取超时（毫秒） |
| `hit_condition` | 查得判定条件（JSON 表达式），满足时计入查得次数 |

**外数调用计费链路**：
1. `ExternalDataCaller` 调用外部 API
2. 根据 HTTP 状态码和 `hit_condition` 判定是否查得
3. 记录 `rule_billing_record` 明细（含调用时间、项目、API 配置 ID、是否查得、费用）
4. `BillingAggregationJob` 定时聚合到 `rule_billing_summary`（按时间周期 + 项目 + API 配置维度）

**涉及前端**：
- `src/views/datasource/` 新建页面（数据源列表、API 配置弹窗含计费与重试配置）
- `src/api/datasource.js` 新建 API 层
- `src/layout/index.vue` 侧边栏添加「外数管理」菜单项
- 变量管理中 `varSource = 'API'` 的变量编辑时，支持选择关联外数 API 配置

### 数据库管理（`/#/database`）

在后端集中配置数据库连接池，数据查询变量（`rule_variable.var_source = 'DB'`）执行时通过后端查询。

**设计目标**：
- 在数据库管理模块中，通过常规 JDBC 配置项（驱动类名、URL、用户名、密码）配置连接池选项（HikariCP）
- 后端在应用启动时初始化连接池
- 数据查询变量执行时，由后端查询对应数据库并返回结果
- 支持多种数据库类型（MySQL、Oracle、PostgreSQL 等）

**涉及数据库表**（需新建）：
- `rule_db_datasource` - 数据库数据源定义（名称、数据库类型、JDBC URL、用户名、加密密码、连接池配置）

**涉及后端**：
- `rule-engine-model` 新增 DbDatasource 实体类
- `rule-engine-server` 新增 `DatabaseDatasourceService`（管理连接池）
- `DynamicDataSourcePool` - 动态管理多数据源连接池（HikariCP），按需创建/销毁
- 数据查询执行：规则执行前/中由后端查询外部数据库，结果注入 QLExpress 上下文

**涉及前端**：
- `src/views/database/` 新建页面（数据源列表、连接配置弹窗）
- `src/api/database.js` 新建 API 层
- `src/layout/index.vue` 侧边栏添加「数据库管理」菜单项
- 变量管理中 `varSource = 'DB'` 的变量编辑时，支持选择关联数据库配置和 SQL 查询语句

### 账单管理（`/#/billing`）

规则引擎与外部 API 调用费用的统一计费与统计模块。

**设计目标**：
- 统计不同时间段（每小时 / 每日 / 每周 / 每月 / 每季 / 每年）内的调用量与费用
- 覆盖两类调用：**引擎规则执行**（业务应用 → 引擎 → 返回结果）和 **外数 API 调用**（引擎 → 外部合作方 → 返回数据）
- 按项目维度汇总：项目编码、项目名称、调用类型、计费方式、调用单价、调用次数、查得次数、计费次数、成本费用、查得率

**涉及数据库表**（需新建）：
- `rule_billing_config` - 计费配置（项目 ID、调用类型、计费方式、单价等）
- `rule_billing_record` - 调用记录明细（时间、来源、次数、费用等，用于聚合统计）
- `rule_billing_summary` - 聚合汇总表（按时间段 + 项目 + 调用类型预聚合，支持快速查询）

**涉及后端**：
- `rule-engine-model` 新增 BillingConfig / BillingRecord / BillingSummary 实体类
- `rule-engine-server` 新增 `BillingService`（计费配置管理、费用统计查询）
- `BillingAggregationJob` - 定时任务（每小时/每日），从 `rule_execution_log` 和外数调用日志聚合费用数据到 `rule_billing_summary`
- `ExternalDataCaller` 中埋点记录外数 API 调用次数与费用（查得/未查得）
- `RuleExecuteService` 中埋点记录引擎规则执行次数
- 计费方式支持：**按次计费**（每次调用扣费）、**按查得计费**（仅成功返回数据时扣费）、**包月/包量套餐**

**涉及前端**：
- `src/views/billing/` 新建页面（计费配置、费用统计、报表导出）
- `src/api/billing.js` 新建 API 层
- `src/layout/index.vue` 侧边栏添加「账单管理」菜单项

### 规则影响分析（血缘图）

基于 ID 关联字段构建依赖图/血缘图，支持以下查询能力：
- 给定变量 ID → 查询所有引用了该变量的规则和模型
- 给定规则 ID → 查询该规则的输入输出变量及引用的模型
- 给定项目 ID → 查询该项目的所有规则及规则间的调用关系

当前已有 `rule_definition_ref` 但无血缘图 API/UI，待实现完整的血缘图服务。

### 其他待实现

- **规则版本回滚** — `rule_definition_version` 表已存储历史版本，但前端无回滚入口，后端无回滚 API
- **执行日志过期清理** — `rule_execution_log` 按月分区，分区需手动添加（已预定义到 2032 年）
- **版本对比** — `rule_definition_version` 有历史数据，但无 Diff UI 和对比 API
- **批量导入错误处理** — 后端已返回详细错误信息，但前端未展示具体失败行和错误原因

### 未来规划（参考 `rule_examples/`）

| 阶段 | 功能 |
|------|------|
| 基础能力 | 实时指标计算（Redis ScoredSortedSet）、数据丰富化框架（IP/手机/身份证/地理） |
| 实验监控 | 冠军挑战 ABTest 分流实验、管理驾驶舱 |
| 高级能力 | 版本对比回滚、发布审批工作流、外数管理、实时监控告警、规则链编排 |
| 扩展能力 | 权限管理、DSL 简化规则、Kryo 序列化规则包、TransmittableThreadLocal 上下文追踪 |

参考项目：
- `rule_engine/`（企业级）：组件依赖分析、版本对比、发布工作流、热部署
- `coolGuard/`（个人开源）：实时指标计算、数据丰富化框架、TransmittableThreadLocal
- `risk_engine/`（Go版）：DSL 简化规则、内置 ABTest 分流