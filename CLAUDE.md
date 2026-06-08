# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

所有回答以及中间的思考过程，尽可能全部使用中文进行解答。

所有实现的代码，在完成后都需要反复核对逻辑是否正确，是否与用户提出的需求匹配，如果有问题修复后需要再次核对。

## 项目概述

衡枢规则引擎是基于 QLExpress 可视化规则引擎改造的风控决策系统，基于 Spring Boot 2.3 + QLExpress 4，支持决策表、决策树、决策流、交叉表、评分卡、复杂交叉表、复杂评分卡、QL 脚本等模型的可视化编排，涵盖变量管理、模型管理、函数管理、规则测试、执行日志、冠军挑战分流实验、ABTest 等功能，是一套能够支撑信贷风控产品全流程的风控决策引擎系统。

## 环境要求

- JDK 8
- Maven 3.6+
- MySQL 8
- Redis
- Node.js (前端开发, 22.14.x 或 14+)

## 开发命令

### 后端 (Maven)

```bash
# 编译所有模块
mvn clean compile

# 运行测试
mvn test

# 运行单个测试类
mvn test -Dtest=ClassName

# 启动 rule-engine-server (端口 8080)
cd rule-engine-server && mvn spring-boot:run

# 启动 rule-engine-example (端口 7070)
cd rule-engine-example && mvn spring-boot:run

# 完整构建 (跳过前端)
mvn clean package -DskipTests
```

### 前端 (Vue 2)

```bash
cd rule-engine-builder-ui

# 安装依赖
npm install

# 开发模式 (端口 9090, /api 代理到后端 8080)
npm run dev

# 生产构建
npm run build

# 代码检查
npm run lint
```

### 基础设施

```bash
# 启动 MySQL
cd rule-engine-mysql && docker-compose up -d

# 启动 Redis
cd rule-engine-redis && docker-compose up -d
```

## 模块架构

| 模块 | 说明 | 端口 |
|------|------|------|
| `rule-engine-model` | 公共实体与 DTO | - |
| `rule-engine-core` | 规则编译与执行核心 | - |
| `rule-engine-server` | 管理端 REST API | 8080 |
| `rule-engine-client` | 客户端 SDK | - |
| `rule-engine-example` | 集成示例服务 | 7070 |
| `rule-engine-builder-ui` | Vue 2 前端控制台 | 9090 |

### 执行流程

1. **管理端** (`rule-engine-server`) 提供 REST API 管理规则定义
2. **前端** (`rule-engine-builder-ui`) 可视化编排规则模型
3. **客户端** (`rule-engine-client`) 在业务应用中执行规则：
   - 启动时通过 HTTP 全量同步规则到 L1 缓存
   - 订阅 Redis `rule:push:{appName}` 频道实时接收更新
   - 执行时调用 `rule-engine-core` 中的 QLExpress 引擎

### 关键类

**后端核心：**

- `RuleCompiler` - 规则编译接口，各模型均有对应实现（`DecisionTableCompiler`、`DecisionTreeCompiler`、`DecisionFlowCompiler`、`CrossTableCompiler`、`ScorecardCompiler`、`AdvancedCrossTableCompiler`、`AdvancedScorecardCompiler`、`ScriptPassthroughCompiler`）
- `CompileResult` - 编译结果，包含生成的脚本和输出变量信息
- `QLExpressEngine` - QLExpress 执行引擎
- `QLExpressEngineFactory` - 执行引擎工厂
- `AggregateBuiltinFunctionRegistry` - 内置聚合函数（sum/count/max/min/avg）注册器
- `FunctionRegistrar` - 自定义函数注册器，支持 QLExpress 脚本/Java 类/Spring Bean

**客户端：**

- `RuleEngineClient` - 客户端入口类，负责规则同步和执行
- `L1MemoryCache` - L1 内存缓存
- `HttpSyncClient` - HTTP 同步客户端
- `RedisSubscriber` - Redis 实时推送订阅

### 客户端鉴权

`TokenAuthInterceptor` 对 `/api/sync/` 等路径校验令牌：请求头 `X-Rule-Token` 或 Query `token`，须与 `rule_project.access_token` 匹配。

## 数据库

- 建表脚本: `rule-engine-server/src/main/resources/sql/schema.sql`
- 示例数据: `rule-engine-server/src/main/resources/sql/data-example.sql`
- MySQL 默认凭证: root / 1qaz@WSX（可通过环境变量 `MYSQL_USERNAME`、`MYSQL_PASSWORD` 覆盖）

## 配置说明

### 服务端 (application.yml)

主要配置数据源 (MySQL) 和缓存 (Redis)，Redis 必须与服务端使用同一实例。

### 客户端 (application.yml)

```yaml
rule-engine:
  client:
    server-url: http://localhost:8080
    app-name: your-service-name
    token: <项目访问令牌>
    project-id: 1
```

## 客户端集成

客户端通过 HTTP 同步规则，通过 Redis 订阅实时更新：

```java
@Resource
private RuleEngineClient ruleClient;

public void example() {
    Map<String, Object> params = new HashMap<>();
    params.put("amount", 10000);
    RuleResult r = ruleClient.execute("YOUR_RULE_CODE", params);
}
```

常用方法：`refreshRule` / `refreshAll` 主动刷新缓存；`getRuleInfo` 查看本地缓存元数据。

## 控制台登录

默认账号: `admin` / `1qaz@WSX`，可通过环境变量 `CONSOLE_USERNAME`、`CONSOLE_PASSWORD` 修改。关闭登录可将 `rule-engine.console-login.enabled` 设为 `false`。

## 前端路由

| 类型 | 前端路由 |
|------|----------|
| 项目管理 | `/#/project` |
| 项目详情 | `/#/project/:id` |
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

## 技术栈

- Spring Boot 2.3.0
- QLExpress 4.1.0
- MyBatis Plus 3.4.3.4
- Vue 2.6.14 + Element UI 2.15.14
- Redis (Pub/Sub 规则推送)
- Kafka (可选执行日志)

## 前后端关键路径

### 后端

- REST Controller: `rule-engine-server/src/main/java/com/bjjw/rule/server/controller/`
- MyBatis Mapper: `rule-engine-server/src/main/java/com/bjjw/rule/server/mapper/`
- 核心编译执行: `rule-engine-core/src/main/java/com/bjjw/rule/core/`
- 函数注册: `rule-engine-core/src/main/java/com/bjjw/rule/core/function/`

### 前端

- 设计器视图: `src/views/designer/`（8个单文件组件）
- 页面视图: `src/views/`（包含 project、rule、variable、model、function、test、log 等）
- API 层: `src/api/`（按模块拆分: auth、dataObject、definition、function、model、project、request、variable）
- 全局样式覆盖: `src/styles/element-override.scss`
- 前端路由守卫中集成了控制台登录校验逻辑

## 已知注意事项

### Vue SFC 中使用 SCSS 变量

`element-override.scss` 中定义了 `$secondary-color` 等 SCSS 变量，全局生效。在 Vue SFC 的 `<style scoped lang="scss">` 中可直接使用这些变量。但若在非 scoped style 中使用，需确保该 SFC 已正确配置 SCSS 或导入变量定义。

### Redis 必须一致

Redis 必须与 rule-engine-server 使用同一实例（含密码、database）。服务端在规则发布等事件时向频道 `rule:push:{appName}` 发布消息。

## 未来规划

以下功能已在 `rule_examples/` 下参考了多个开源决策引擎后规划，需要逐步实现。

### 第一阶段：基础能力增强

- [ ] **实时指标计算** — 基于 Redis ScoredSortedSet 的滑动窗口指标（sum/count/avg/max/min/his），参考 coolGuard 的 `AbstractIndicator` 设计
  - 新增 `rule_indicator` 表定义指标
  - 实现 `IndicatorFactory` + 多种 `AbstractIndicator` 子类
  - 支持时间窗口：滑动窗口（LAST）和固定窗口（CUR）
- [ ] **数据丰富化框架** — 外部数据解析（IP归属地、手机号、身份证、地理位置），参考 coolGuard 的 `analysis` 模块
  - 统一分析器接口 `DataAnalyzer<T>`
  - 实现 `IpAnalyzer`、`PhoneNoAnalyzer`、`IdCardAnalyzer`、`GeoAnalyzer`
  - 支持在规则中调用分析结果作为变量输入
- [ ] **规则影响分析** — 变更规则时自动分析影响范围，参考 rule_engine 的 `EngineRelaCpnt` 设计
  - 实现 `RuleImpactAnalyzer` 分析组件依赖关系
  - 在规则修改页面展示"影响哪些规则"

### 第二阶段：实验与监控

- [ ] **冠军挑战（Champion-Challenger）** — 多规则版本流量分配实验，参考 rule_engine 的组件分流和 risk_engine 的 `AbtestNode`
  - 新增 `rule_experiment` / `rule_experiment_variant` / `rule_experiment_metrics` 表
  - 实现 `ExperimentRouter` 分流路由，支持按流量比例自动分配
  - 实时统计各变体的执行量和效果指标
- [ ] **ABTest 分流实验** — 流量分流对比实验
  - 基于用户 ID 或随机值进行流量切分
  - 支持灰度发布：A/B 版本并行运行
  - 支持多种分流策略：随机、按比例、按用户特征
- [ ] **管理驾驶舱** — 规则执行大盘，参考 rule_engine_analy 模块
  - 规则执行统计：总次数、成功/失败率、平均耗时
  - 趋势图表：每日/每周执行量趋势
  - TOP N 规则排行：按执行量、耗时、失败率排序

### 第三阶段：高级能力

- [ ] **版本对比与回滚** — 规则版本差异对比和一键回滚
  - 参考 rule_engine 的 `compareCpntByCurrentAndVersion` 设计
  - 可视化 Diff 展示：新增/删除/修改节点
  - 支持版本间一键切换
- [ ] **发布工作流** — 申请→审核→发布三级审批流程
  - 参考 rule_engine 的 `applyRelease → auditRelease → release` 设计
  - 支持多级审批、驳回重新编辑
  - 审计日志记录完整发布历史
- [ ] **外数管理** — 外部数据源配置和外部函数
  - HTTP/API 数据源调用
  - 数据库直连查询（DB/JDBC）
  - 外部函数结果缓存（TTL 可配置）
- [ ] **实时监控与告警** — 规则执行 QPS、耗时分布、异常告警
  - 集成 Micrometer + Prometheus 指标暴露
  - 耗时超阈值自动告警（邮件/钉钉/企业微信）
- [ ] **规则链编排** — 子规则调用、规则组合
  - 支持在一个规则中调用其他规则
  - 实现 `RuleChainExecutor` 批量规则执行
  - 支持规则依赖 DAG 分析
- [ ] **批量测试** — 测试用例批量导入、执行、差异报告
  - 支持 Excel/JSON 批量导入测试用例
  - 一键执行并生成差异报告
  - CI/CD 集成支持

### 第四阶段：扩展能力

- [ ] **权限管理** — 项目级用户角色和权限控制
  - 角色：管理员、运维、分析师、普通用户
  - 权限：设计、发布、测试、查看等细粒度控制
- [ ] **DSL 简化规则定义** — YAML/JSON DSL 让非技术人员也能编写规则
  - 参考 risk_engine 的 DSL 设计
  - 降低规则编写门槛
- [ ] **Kryo 序列化规则包** — 规则整包导出/导入
  - 参考 rule_engine 的 `EngineFileContentSerialize` 设计
  - 支持跨环境规则迁移
- [ ] **TransmittableThreadLocal 统一上下文** — 执行链路全追踪
  - 参考 coolGuard 的 `DecisionContextHolder` 设计
  - 统一管理 FieldContext、IndicatorContext、PolicyContext、TraceContext
  - 便于在复杂规则链中传递上下文

## 参考项目

`rule_examples/` 目录下包含多个开源决策引擎的研究代码：

| 项目 | 来源 | 技术栈 | 借鉴价值 |
|------|------|--------|----------|
| `rule_engine/` | 企业级（mobanker） | Spring 4 / JDK 7 / MongoDB / Dubbo / Netty | ⭐⭐⭐ 组件依赖分析、版本对比、发布工作流、热部署 |
| `coolGuard/` | 个人开源（wnhyang） | Spring Boot 3 / JDK 17 / Redisson / Elasticsearch | ⭐⭐⭐ 实时指标计算、数据丰富化框架、TransmittableThreadLocal |
| `risk_engine/` | 个人开源（Go版） | Go / YAML DSL | ⭐⭐ DSL简化规则、内置ABTest分流 |
| `daleks/` | 开源 | Spring Boot / Ant Design Pro | ⭐ 简洁实时风控基础架构 |

**参考项目架构亮点速览：**

- **rule_engine**: `EngineCpnt` 组件体系 + `EnginePolicyFlow` 树形执行流 + `EngineStepTaskProcessor` 顺序执行器 + MongoDB 快照持久化
- **coolGuard**: `AbstractIndicator` 抽象指标工厂 + Redis ScoredSortedSet 滑动窗口 + `DecisionContextHolder` 四大上下文 + Kafka 事件流
- **risk_engine**: `Kernel` 内核加载 YAML DSL + `AbtestNode` 内置分流节点 + `PipelineContext` 流水线上下文
