# Java 业务服务接入决策引擎

目标链路：业务端请求 Java 服务，Java 服务通过 `rule-engine-client` 执行已发布规则，读取决策结果并返回业务端。可运行参考为 `rule-engine-example`，项目管理页面导出的 API 文档也包含对应项目的 Java 接入配置和 Controller 示例。

## 1. 确定规则范围与执行位置

- 项目规则：使用所属项目的凭据。
- 全局规则：先在调用项目中关联全局规则，再使用该项目凭据调用。全局不代表匿名可用，也不代表任何项目均可执行。
- 多项目：每个项目使用独立凭据与 SDK 实例；`project-id`、`project-code` 必须与凭据所属项目一致。不能通过业务请求中的项目字段扩大权限。
- 只有已发布规则可以通过开放接口访问；草稿、审核中及下线规则不可调用。

`server-side-execution: true` 表示 SDK 通过 HTTP 请求引擎服务端执行。涉及 API、数据库、名单、模型、派生变量或服务端函数等运行依赖时，应使用此模式。

`server-side-execution: false` 表示 Java 服务执行本地缓存的脚本。该模式不执行服务端变量解析流程，需要业务服务准备完整输入并提供所需函数和运行依赖。两种模式都需要配置 Redis；当前 SDK 启动会同步规则/函数并建立订阅。SDK 不直连引擎 MySQL。

## 2. 引入 SDK

在 JDK 17、Spring Boot 3 的 Web 服务中增加依赖：

```xml
<dependency>
    <groupId>com.hengshucredit.rule</groupId>
    <artifactId>rule-engine-client</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

SDK 尚未发布公共 Maven 仓库。先在引擎源码根目录执行 `mvn clean install -DskipTests` 安装本地依赖，或由团队提供内部 Maven 仓库。Web 依赖版本由业务项目的 Spring Boot parent / BOM 管理。

## 3. 配置 Java 服务

```yaml
rule-engine:
  client:
    server-url: ${RULE_SERVER_URL}
    app-name: risk-service
    project-id: ${RULE_PROJECT_ID}
    project-code: ${RULE_PROJECT_CODE}
    token: ${PROJECT_ACCESS_TOKEN}
    server-side-execution: true
    http-timeout-ms: 10000
    trace-enabled: false
    log-report-enabled: false
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}
      database: ${REDIS_DATABASE:0}
```

项目凭据在“项目管理 → 调用鉴权”中取得；实际入口以控制台为准。凭据仅放在 Java 服务的 Secret/环境变量中，不交给浏览器或移动端。Redis 地址、密码和 database 必须与引擎服务端一致。`project-code` 是实时推送路由键；`app-name` 只标识业务应用，不授予权限。HTTP 超时应覆盖规则、外数调用及轮询总耗时。

账号密码、API Key、HMAC-SHA256 的配置见 [部署与接入](deployment.md#业务系统-sdk-集成)，也可直接复制该项目导出 API 文档中的鉴权配置。

## 4. 暴露业务接口并处理结果

将以下类放入业务应用的 Spring 扫描包中，把 `RISK_RULE` 替换为授权范围内的已发布规则编码。生产应固定或校验规则白名单，不能把任意规则执行接口无鉴权地对外开放。

```java
import com.hengshucredit.rule.client.RuleEngineClient;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/decision")
public class DecisionController {
    private final RuleEngineClient ruleClient;

    public DecisionController(RuleEngineClient ruleClient) {
        this.ruleClient = ruleClient;
    }

    @PostMapping
    public ResponseEntity<RuleResult> decide(@RequestBody Map<String, Object> params) {
        RuleResult result = ruleClient.execute("RISK_RULE", params);
        return ResponseEntity.status(result.isSuccess() ? 200 : 422).body(result);
    }
}
```

调用业务接口时，请求体直接使用规则文档 `params` 内的结构，例如 `{"age":18}`；SDK 也接受字段名与规则输入一致的 Java DTO。SDK 返回的 `RuleResult` 包含 `success`、`result`、`errorMessage`、`traceId`、`traces`、`executeTimeMs`，没有引擎 HTTP 接口外层的 `code/message/data`。

`success=true` 仅代表规则执行成功，实际通过、拒绝、人工复核取决于 `result` 中定义的业务字段。示例将执行失败映射为 HTTP 422；业务服务应另行提供自身鉴权、输入校验、异常映射和明确的失败策略，不能在引擎错误或超时时默认放行。默认不要把包含敏感输入的追踪明细直接返回终端用户。

如果绕过 SDK 直接调用引擎，请求如下（Shell 示例）：

```bash
curl -X POST https://engine.example.com/api/rule/sync/execute/RISK_RULE \
  -H 'X-Rule-Token: <PROJECT_TOKEN>' \
  -H 'Content-Type: application/json' \
  -d '{"clientAppName":"risk-service","traceEnabled":false,"params":{"age":18}}'
```

HTTP 响应先检查外层 `code == 200`，再检查 `data.success`，最后读取 `data.result`。401/403 检查鉴权；404 检查所属项目、全局关联与发布状态。`traceEnabled` 位于请求体顶层，不在 `params` 中，默认 true；JSON 使用布尔值，multipart 可使用字符串 `true` / `false`，其他值返回业务码 400。

## 5. 日志追踪开关

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `trace-enabled` | `true` | 控制本地及服务端执行的表达式追踪；关闭后不返回表达式 `traces`，仍保留 `traceId` |
| `log-report-enabled` | `true` | 控制 SDK **本地执行**日志上报；关闭后 HTTP、Kafka、自定义 reporter 均不接收日志 |

两个开关独立：关闭追踪但保留上报时，可保留基础执行日志；开启追踪但关闭上报时，可在 Java 服务读取追踪而不从 SDK 上报。服务端执行不重复从客户端上报日志。服务端基础执行日志、鉴权审计、外数日志及计费不受 `log-report-enabled` 控制；两个开关都关闭不等于平台不留任何日志。

本地上报默认使用有界 HTTP 异步队列。启用上报且有自定义 `ExecutionLogReporter` 时使用自定义实现，否则存在 `KafkaTemplate` 时自动选择 Kafka。日志上报失败不应改变决策结果。程序化 Builder 支持 `.traceEnabled(false).logReportEnabled(false)`；即使显式传入 reporter，关闭上报仍然生效。

## 6. 运行现成示例

1. 启动引擎及 Redis；在控制台完成项目凭据配置、规则设计、审核发布，全局规则还需关联到调用项目。
2. 在仓库根目录 `.env` 或进程环境中配置 `PROJECT_ACCESS_TOKEN`、`RULE_PROJECT_ID`、`RULE_PROJECT_CODE`、`REDIS_PASSWORD`；按需指定 `RULE_SERVER_URL`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_DATABASE`。
3. 对实际项目规则设置 `RULE_SERVER_SIDE_EXECUTION=true`，构建依赖后，在 `rule-engine-example` 目录执行 `mvn spring-boot:run`，默认监听 7070。
4. 调用 `POST http://localhost:7070/api/example/execute`，请求 `{"ruleCode":"RISK_RULE","params":{"age":18}}`，替换为实际规则及入参。该接口返回原始 `RuleResult`；业务示例服务不是生产网关。
5. 设置 `RULE_TRACE_ENABLED=false` / `RULE_LOG_REPORT_ENABLED=false` 并重启示例服务，可验证关闭效果。示例保留原有本地执行默认值 `RULE_SERVER_SIDE_EXECUTION=false`；其中本地 Java/Bean 函数演示依赖示例进程注册的函数，不应直接切为服务端执行。

示例首页中的预设业务表单依赖可选 `data-example.sql`，不代表当前数据库一定存在对应规则；不要为运行示例覆盖现有数据库。通用执行接口可直接调用控制台已有的授权规则。

## 7. 一个 Java 服务连接多个项目

不要修改共享客户端的项目或凭据来处理不同请求。为每个项目声明独立的 Spring Bean，通过 `@Qualifier` 在业务服务中选择；凭据从服务端配置读取，不接受业务请求直接传入。下面的方法可按不同 Bean 名重复声明：

```java
@Bean(name = "creditRuleClient", initMethod = "start", destroyMethod = "close")
RuleEngineClient creditRuleClient(RedisConnectionFactory redis, Environment env) {
    return RuleEngineClient.builder()
            .serverUrl(env.getRequiredProperty("RULE_SERVER_URL"))
            .appName("risk-service")
            .projectId(Long.parseLong(env.getRequiredProperty("CREDIT_PROJECT_ID")))
            .projectCode(env.getRequiredProperty("CREDIT_PROJECT_CODE"))
            .token(env.getRequiredProperty("CREDIT_PROJECT_TOKEN"))
            .connectionFactory(redis)
            .serverSideExecution(true)
            .httpTimeoutMs(10000)
            .traceEnabled(false)
            .logReportEnabled(false)
            .build();
}
```

该方法放入 `@Configuration` 类，导入 `org.springframework.context.annotation.Bean`、`org.springframework.core.env.Environment`、`org.springframework.data.redis.connection.RedisConnectionFactory` 和 SDK 类。手动声明客户端后自动配置不会再创建默认客户端。每个实例有独立缓存、函数和订阅；若连接不同引擎部署，应分别注入对应的 Redis 连接工厂。
