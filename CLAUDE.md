# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

所有回答以及中间的思考过程，尽可能全部使用中文进行解答。

所有实现的代码，在完成后都需要反复核对逻辑是否正确，是否与用户提出的需求匹配，如果有问题修复后需要再次核对。

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

## varPickerMixin 与变量引用机制

`varPickerMixin.js` 是设计器页面的核心 mixin，统一加载项目变量/常量/对象字段到 `projectRefs`。

`projectRefs` 三层结构：

| category | 来源 | refCode 格式 | 示例 |
|----------|------|-------------|------|
| `standalone` | 普通变量（varSource ≠ CONSTANT） | `scriptName \|\| varCode` | `taxAmount` |
| `constant` | 常量（varSource = CONSTANT） | `scriptName \|\| varCode` | `MAX_RETRY_COUNT` |
| `object` | 数据对象字段 | `对象scriptName.字段scriptName` | `TaxRequest.amount` |

变量引用回溯链路：
1. 设计器中通过 `varPicker` 选择变量 → 设置 `_varId`（变量数据库 ID）
2. 加载设计器数据时调用 `_syncModelVarRefs()` → `syncVarItem()` → 通过 `_varId` 或 `varLabel` 回溯匹配，更新 `varCode` 和 `varLabel`
3. 后端编译时通过 `VarContext` 将 `varId → scriptName` 映射解决大小写不一致

> **注意**：部分设计器（DecisionTree/DecisionFlow/AdvancedCrossTable/ScriptEditor）的 var-picker 选择时仅设置 `varCode`，未保存 `_varId`，可能导致编译时回溯不稳定。

## 已知注意事项

### Vue SFC 中使用 SCSS 变量

`element-override.scss` 中定义了 `$secondary-color` 等 SCSS 变量，全局生效。在 Vue SFC 的 `<style scoped lang="scss">` 中可直接使用这些变量。

### scriptName 大小写敏感

QLExpress 脚本中需保持一致。生成规则：将 `varCode` 转为 camelCase（首字母小写）。常量通过 `scriptName || varCode` 直接引用（不再使用 `组.常量` 两段式）。

## 技术栈

- Spring Boot 2.3.0
- QLExpress 4.1.0
- MyBatis Plus 3.4.3.4
- Vue 2.6.14 + Element UI 2.15.14
- Redis (Pub/Sub 规则推送)
- Kafka (可选执行日志上报)

## 待完善 / 待实现

### 部分修复 / 待完善

- **批量导入错误处理** — 后端已返回详细错误信息，但前端未展示具体失败行和错误原因

### 待实现

- **规则版本回滚** — `rule_definition_version` 表已存储历史版本，但前端无回滚入口，后端无回滚 API
- **执行日志过期清理** — `rule_execution_log` 按月分区，分区需手动添加（已预定义到 2032 年）
- **规则影响分析** — 修改规则时无法查看"哪些规则依赖此变量/函数"
- **版本对比** — `rule_definition_version` 有历史数据，但无 Diff UI 和对比 API

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