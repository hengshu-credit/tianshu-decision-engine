<p align="center">
  <img src="https://hengshucredit.com/images/hengshucredit_animated.svg" alt="衡枢真信" width="200">
</p>

<h3 align="center">

🔍 鉴真伪 · 📊 斟信用 · ⚖️ 衡风险 · 🎯 枢定策

</h3>



# 天枢决策引擎使用说明

天枢决策引擎是一套基于 Spring Boot 2.3、QLExpress 4、Vue 2 和 Element UI 的可视化风控决策平台。系统面向业务人员提供规则项目、变量、名单、外数 API、外部数据库、模型、函数、规则测试、血缘分析、分流实验、执行日志和账单管理等能力；面向业务系统提供 `rule-engine-client` SDK，用于拉取、缓存并执行已发布规则。

## 交流

|  微信 |  微信公众号 |
| :---: | :----: |
| <img src="https://itlubber.art/upload/itlubber.png" alt="itlubber.png" width="50%" border=0/> | <img src="https://itlubber.art/upload/hengshucredit-com.png" alt="hengshucredit-com.png" width="50%" border=0/> |
|  itlubber  | hengshucredit-com |

## 1. 功能总览

| 模块 | 主要能力 |
|------|----------|
| 项目管理 | 管理规则项目、访问令牌和项目级接口说明 |
| 规则管理 | 新建、设计、编译、发布、下线和版本回滚规则 |
| 变量管理 | 管理输入、计算、常量、API、数据库、名单等变量；API/DB/名单变量支持在线测试 |
| 名单管理 | 维护名单库、名单记录、导入导出和变更日志 |
| 外数管理 | 配置外部 API 数据源、接口请求映射、响应映射、鉴权和调用日志 |
| 数据库管理 | 配置外部数据库连接池、测试连接、只读查询和数据库调用日志 |
| 模型管理 | 管理模型入参、出参、执行测试和模型调用日志 |
| 函数管理 | 管理 QLExpress 脚本、Java 类、Spring Bean 等函数 |
| 规则测试 | 按项目和规则加载输入字段，执行测试并查看追踪结果 |
| 血缘分析 | 从项目、变量、规则、模型、API、DB、名单等节点查看上下游依赖图 |
| 分流实验 | 配置冠军/挑战/测试组，按条件或流量执行实验 |
| 执行日志 | 查看服务端和客户端规则执行记录、耗时、结果和追踪树 |
| 账单管理 | 配置计费项、查看明细记录和聚合汇总 |

## 2. 模块结构

| 模块 | 说明 | 默认端口 |
|------|------|----------|
| `rule-engine-model` | 公共实体、DTO 和数据库映射模型 | - |
| `rule-engine-core` | 规则编译器和 QLExpress 执行核心 | - |
| `rule-engine-server` | 管理端 REST API、同步接口、日志、外数和数据库服务 | 8080 |
| `rule-engine-client` | 业务系统 SDK，负责规则同步、L1 缓存和本地执行 | - |
| `rule-engine-example` | SDK 集成示例服务 | 7070 |
| `rule-engine-builder-ui` | Vue 2 控制台，独立构建和部署 | 9090 |
| `rule-engine-mysql` | MySQL 配置与初始化脚本 | 3306 |
| `rule-engine-redis` | Redis Pub/Sub 配置 | 6379 |

## 3. 架构与运行链路

```mermaid
flowchart TB
  Browser["浏览器"] --> UI["rule-engine-builder-ui 控制台"]
  UI --> Server["rule-engine-server API"]
  Server --> MySQL[("MySQL")]
  Server --> Redis[("Redis Pub/Sub")]
  Biz["业务系统"] --> SDK["rule-engine-client SDK"]
  SDK -->|HTTP 拉取已发布规则| Server
  SDK -->|订阅 rule:push:{appName}| Redis
  SDK -->|本地 QLExpress 执行| Biz
  SDK -->|执行日志上报| Server
```

要点：

- 前后端分离部署，`rule-engine-builder-ui` 的构建产物在 `dist/`，不混入 `rule-engine-server`。
- 业务系统不直连 MySQL 获取规则，通过 SDK 调用服务端同步接口。
- Redis 需要与 `rule-engine-server` 使用同一实例。规则发布、下线、函数变更会向 `rule:push:{appName}` 推送消息。
- 执行日志默认 HTTP 上报；classpath 中存在 `KafkaTemplate` Bean 时可切换到 Kafka。

## 4. 环境要求

- JDK 8
- Maven 3.6+
- MySQL 8
- Redis
- Node.js 14+，建议 22.14.x

## 5. 本地启动

### 5.1 后端

```bash
mvn clean install -DskipTests
cd rule-engine-server
mvn spring-boot:run
```

默认配置读取：

- MySQL: `jdbc:mysql://localhost:3306/rule_engine`
- 用户名: `root`
- 密码: `1qaz@WSX`
- Redis: `localhost:6379`

控制台登录默认启用，账号密码：

- 用户名: `admin`
- 密码: `1qaz@WSX`

可通过环境变量 `CONSOLE_USERNAME`、`CONSOLE_PASSWORD` 覆盖。

### 5.2 前端

```bash
cd rule-engine-builder-ui
npm install
npm run dev
```

开发访问地址为 `http://localhost:9090/`，`/api` 会代理到 `http://localhost:8080`。

## 6. 核心模型类型

| 模型类型 | 设计器路由 | 说明 |
|----------|------------|------|
| 决策表 | `#/designer/table/{definitionId}` | 条件树加动作列，支持 FIRST、ALL、UNIQUE 命中策略 |
| 决策树 | `#/designer/tree/{definitionId}` | 节点、连线条件和任务动作编排 |
| 决策流 | `#/designer/flow/{definitionId}` | 流程节点、网关、连线和任务动作编排 |
| 交叉表 | `#/designer/cross/{definitionId}` | 行变量、列变量和二维矩阵结果 |
| 评分卡 | `#/designer/score/{definitionId}` | 评分项、权重、分数等级和结果变量 |
| 复杂交叉表 | `#/designer/cross-adv/{definitionId}` | 多行维度、多列维度和矩阵结果 |
| 复杂评分卡 | `#/designer/score-adv/{definitionId}` | 维度组、维度规则、权重和等级 |
| QL 脚本 | `#/designer/script/{definitionId}` | 直接编辑 QLExpress 脚本并维护脚本变量引用 |

各设计器的“测试”入口会按当前模型引用生成测试样例。决策表、规则集、树、流、交叉表、评分卡、复杂模型和脚本会优先使用规则实际输入字段，不把结果变量作为默认入参。

## 7. 当前功能截图

### 7.1 登录

![控制台登录页](docs/project-usage/project-usage-01-login.png)

### 7.2 项目管理

![项目管理](docs/project-usage/project-usage-02-project-list.png)

### 7.3 项目详情与规则列表

![项目详情与规则列表](docs/project-usage/project-usage-07-project-detail.png)

### 7.4 变量管理

变量管理支持项目级和全局变量。变量来源包括输入、计算、常量、API、数据库和名单。API、数据库、名单来源变量可在列表操作中点击“测试”，输入上下文 JSON 后直接触发对应外部取数或匹配逻辑，并写入对应模块调用日志。

![变量管理](docs/project-usage/project-usage-03-variable.png)

### 7.5 名单管理

![名单管理](docs/project-usage/project-usage-09-list.png)

### 7.6 外数管理

外数管理用于统一配置外部 API 数据源、接口、鉴权、请求映射、响应映射、超时、重试、缓存和计费项。

![外数管理](docs/project-usage/project-usage-10-datasource.png)

### 7.7 数据库管理

数据库管理用于维护后端集中连接池。数据库变量执行时通过后端查询外部数据库，不由前端或客户端直连数据库。

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

### 7.11 血缘分析

血缘分析支持从项目、变量、规则、模型、API、数据库、名单和外数源出发，查看上游依赖、下游引用或全量关系。不同类型节点用不同颜色展示。

![血缘分析](docs/project-usage/project-usage-14-lineage.png)

### 7.12 分流实验

![分流实验](docs/project-usage/project-usage-15-experiment.png)

### 7.13 执行日志

![执行日志](docs/project-usage/project-usage-06-execution-log.png)

### 7.14 账单管理

![账单管理](docs/project-usage/project-usage-16-billing.png)

## 8. 设计器截图

### 8.1 决策表

![决策表设计器](docs/project-usage/project-usage-designer-table.png)

### 8.2 决策树

![决策树设计器](docs/project-usage/project-usage-designer-tree.png)

### 8.3 决策流

![决策流设计器](docs/project-usage/project-usage-designer-flow.png)

### 8.4 交叉表

![交叉表设计器](docs/project-usage/project-usage-designer-cross.png)

### 8.5 评分卡

![评分卡设计器](docs/project-usage/project-usage-designer-score.png)

### 8.6 复杂交叉表

![复杂交叉表设计器](docs/project-usage/project-usage-designer-cross-adv.png)

### 8.7 复杂评分卡

![复杂评分卡设计器](docs/project-usage/project-usage-designer-score-adv.png)

### 8.8 QL 脚本

![QL 脚本设计器](docs/project-usage/project-usage-designer-script.png)

## 9. 业务系统 SDK 集成

业务系统引入 `rule-engine-client` 后配置服务端地址、项目令牌和应用名即可同步规则。

```yaml
rule-engine:
  client:
    server-url: http://localhost:8080
    app-name: your-service-name
    token: <项目访问令牌>
    project-id: 1
    trace-enabled: true
```

执行示例：

```java
RuleResult result = ruleEngineClient.execute("RC_PRICING_TABLE", requestMap);
```

SDK 行为：

- 启动时全量同步规则到 L1 缓存。
- 订阅 Redis 推送，规则发布或下线后刷新本地缓存。
- 缓存未命中时可按规则编码单条拉取。
- 本地使用 QLExpress 执行脚本。
- 可异步上报执行日志。

## 10. 版本、日志和计费

- 规则、函数、分流实验均有版本记录，可查看版本内容、对比差异并回滚。
- 执行日志记录规则执行结果、耗时、输入输出和表达式追踪。
- 外数 API、数据库查询、名单匹配和模型执行会写入模块调用日志。
- 账单模块可对引擎执行、API 调用和数据库调用配置计费项，查看明细与汇总。

## 11. 开发校验命令

后端：

```bash
mvn clean install -DskipTests
cd rule-engine-server
mvn spring-boot:run
mvn test
```

前端：

```bash
cd rule-engine-builder-ui
npm run dev
npm test
```

## 12. 当前实现边界

- 控制台以业务配置和规则编排为主，生产部署时需要按实际安全要求替换默认登录账号密码。
- 数据库管理只应配置只读查询账号；数据库变量和测试查询均应限制为查询类 SQL。
- 血缘图基于当前结构化字段和变量来源配置生成，脚本中极复杂的动态引用无法保证完全静态识别。
- 外数、数据库、名单变量测试依赖对应数据源配置可用；测试失败会写入模块日志，便于排查配置、网络、SQL 或参数映射问题。
