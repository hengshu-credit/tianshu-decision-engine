# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

所有回答以及中间的思考过程，尽可能全部使用中文进行解答。

所有实现的代码，在完成后都需要反复核对逻辑是否正确，是否与用户提出的需求匹配，如果有问题修复后需要再次核对。

## 项目概述

QLExpress 可视化规则引擎，基于 Spring Boot 2.3 + QLExpress 4，支持决策表、决策树、决策流、交叉表、评分卡、复杂交叉表、复杂评分卡、QL脚本等模型的可视化编排。

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

- `RuleEngineClient` - 客户端入口类，负责规则同步和执行
- `RuleCompiler` - 规则编译，将定义编译为可执行表达式
- `RuleExecutor` - 规则执行器
- `FunctionRegistrar` - 函数注册器，支持 QLExpress 脚本/Java 类/Spring Bean

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

### 客户端鉴权

`TokenAuthInterceptor` 对 `/api/sync/` 等路径校验令牌：请求头 `X-Rule-Token` 或 Query `token`，须与 `rule_project.access_token` 匹配。

## 控制台登录

默认账号: `admin` / `1qaz@WSX`，可通过环境变量 `CONSOLE_USERNAME`、`CONSOLE_PASSWORD` 修改。关闭登录可将 `rule-engine.console-login.enabled` 设为 `false`。

## 模型类型路由

| 类型 | 前端路由 |
|------|----------|
| 决策表 | `#/designer/table/{id}` |
| 决策树 | `#/designer/tree/{id}` |
| 决策流 | `#/designer/flow/{id}` |
| 交叉表 | `#/designer/cross/{id}` |
| 评分卡 | `#/designer/score/{id}` |
| 复杂交叉表 | `#/designer/cross-adv/{id}` |
| 复杂评分卡 | `#/designer/score-adv/{id}` |
| QL 脚本 | `#/designer/script/{id}` |

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
- API 层: `src/api/`（按模块拆分）
- 全局样式覆盖: `src/styles/element-override.scss`

## 已知注意事项

### Vue SFC 中使用 SCSS 变量

`element-override.scss` 中定义了 `$secondary-color` 等 SCSS 变量，全局生效。在 Vue SFC 的 `<style scoped lang="scss">` 中可直接使用这些变量。但若在非 scoped style 中使用，需确保该 SFC 已正确配置 SCSS 或导入变量定义。

### Redis 必须一致

Redis 必须与 rule-engine-server 使用同一实例（含密码、database）。服务端在规则发布等事件时向频道 `rule:push:{appName}` 发布消息。