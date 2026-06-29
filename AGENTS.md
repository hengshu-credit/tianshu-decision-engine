# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

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

**测试用例编写与修改规范**：
- 新增或修改测试用例时，必须仔细校对测试逻辑是否正确覆盖目标功能点
- 测试用例的断言条件、输入数据、预期结果必须与实际需求完全匹配
- 不得编写"假通过"的测试用例（断言永远为 true，或测试目标与断言无关）
- 修改现有测试用例前，先理解原有测试意图，避免破坏有效覆盖

修复任何代码或修改内容之前，必须先完整研究项目整体结构和相关功能的实现方式，所有改动必须与现有实现风格保持一致且逻辑连贯。不得在未理解上下文的情况下孤立修改某一处代码，导致同一功能出现多种实现方式或前后端逻辑不一致。

## 项目概述

衡枢规则引擎（hscredit/qlexpress-rule）是一套基于 **Spring Boot 2.3** 与 **QLExpress 4** 的可视化风控决策系统：支持 **决策表、决策树、决策流、交叉表、评分卡、复杂交叉表、复杂评分卡、QL 脚本** 等 8 种模型的可视化编排，涵盖变量管理、模型管理、函数管理、规则测试、执行日志等功能。

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

### 前端 (Vue 2)

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
cd rule-engine-mysql && docker-compose up -d   # MySQL
cd rule-engine-redis && docker-compose up -d    # Redis
```

### 截图采集

```bash
# 依赖 scripts/ 下的 node_modules + playwright
node scripts/capture-designer-screenshots.cjs
```

## 核心编译器（rule-engine-core）

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

## 前端关键路径

- 设计器视图: `src/views/designer/`（8 个单文件组件）
- 页面视图: `src/views/`（project、rule、variable、model、function、test、log 等）
- API 层: `src/api/`（auth、dataObject、definition、function、model、project、request、variable）
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
- `setup.js` 预置所有 API mock 和 Element UI mock

**当前测试状态**：前端 470 个测试全部通过 ✅，后端 35 个测试全部通过 ✅

## 后端测试框架 (JUnit 4)

当前仅有 `rule-engine-core` 模块包含测试：`DecisionTableCompilerTest`（35 个用例）。

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
| CrossTable | — | — | 简单交叉表，无变量选择 |
| Scorecard | — | — | 简单评分卡，无变量选择 |
| AdvancedCrossTable | ✅ `dim._varId` | ✅ `resultVar._varId` | 已完整实现 |
| AdvancedScorecard | ✅ `dim._varId` | ✅ `resultVar._varId` | 已完整实现 |
| ScriptEditor | ✅ `_varId` 通过 `scriptVarRefs` 映射持久化 | ✅ `insertVar` 记录 `varId`，保存时同步脚本中实际引用，`modelJson` 中同时保存 `script` 和 `scriptVarRefs`，加载时用 `varId` 同步变量编码变更 |

> ✅ **已修复**：ScriptEditor 的 var-picker 选择时，通过 `scriptVarRefs` 数组同时持久化 `_varId`（`varId`）到 `modelJson.scriptVarRefs`。保存时 `syncScriptVarRefsFromScript()` 从脚本中提取实际引用并更新映射；加载时 `_syncModelVarRefs()` 用 `varId` 查找最新的 `refCode`，自动同步变量编码变更。

## 待实现功能

### 外数管理（`/#/datasource`）

通过 API 获取外部数据的统一接入模块，对应变量中的 **接口变量**（`rule_variable.var_source = 'API'`）。

涉及数据库表：`rule_external_datasource`（数据源定义）、`rule_external_api_config`（API 配置，含计费方式、重试策略等）。

涉及前端：`src/views/datasource/` 新建页面，`src/api/datasource.js` 新建 API 层，`src/layout/index.vue` 侧边栏添加菜单项。

### 数据库管理（`/#/database`）

在后端集中配置数据库连接池（HikariCP），数据查询变量（`rule_variable.var_source = 'DB'`）执行时通过后端查询外部数据库。

涉及数据库表：`rule_db_datasource`（数据库数据源定义）。

### 账单管理（`/#/billing`）

规则引擎与外部 API 调用费用的统一计费与统计模块，统计引擎规则执行和外数 API 调用的费用。

涉及数据库表：`rule_billing_config`（计费配置）、`rule_billing_record`（调用记录明细）、`rule_billing_summary`（聚合汇总表）。

### 规则版本回滚与版本对比

`rule_definition_version` 表已存储历史版本，但前端无回滚入口和对比 UI，后端无相关 API。

### 批量导入错误处理

后端已返回详细错误信息，但前端未展示具体失败行和错误原因。

## 技术栈

- Spring Boot 2.3.0
- QLExpress 4.1.0
- MyBatis Plus 3.4.3.4
- Vue 2.6.14 + Element UI 2.15.14
- Redis (Pub/Sub 规则推送)
- Kafka (可选执行日志上报)

## 参考资料

- 详细使用说明见 `README.md`
- 设计器截图采集脚本：`scripts/capture-designer-screenshots.cjs`
- 参考实现：`rule_examples/` 目录下的企业级/个人开源参考项目