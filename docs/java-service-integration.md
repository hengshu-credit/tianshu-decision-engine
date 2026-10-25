# Java 业务服务接入决策引擎

对外公司或无需本地规则缓存执行的业务系统，直接调用 Server API，不需要安装客户端、部署转发服务或连接 Redis。接口文档从项目「API」按钮下载。

以下第 1–5、7 节描述可选 Java SDK `rule-engine-client-sdk`；只有本地执行模式需要 Redis 和规则同步。直接 HTTP 调用见第 6 节。

## 1. 确定规则范围与执行位置

- 默认客户端使用服务端 Ed25519 全局鉴权，可同步和执行全部已发布项目规则；全局私钥不提交到仓库，也不放入浏览器。
- 项目级客户端可选配置所属项目的凭据、`project-id`、`project-code` 和规则白名单，只加载该项目规则。
- 多项目客户端可以按项目分别创建实例；不能通过业务请求中的项目字段扩大项目级权限。
- 只有已发布规则可以通过开放接口访问；草稿、审核中及下线规则不可调用。

`server-side-execution: true` 表示 SDK 通过 HTTP 请求引擎服务端执行。涉及 API、数据库、名单、模型、派生变量或服务端函数等运行依赖时，应使用此模式。

`server-side-execution: false` 表示 Java 服务执行本地缓存的脚本。该模式不执行服务端变量解析流程，需要业务服务准备完整输入并提供所需函数和运行依赖；只有这个本地纯计算模式需要配置 Redis，SDK 启动时才会同步规则/函数并建立项目频道订阅。`server-side-execution: true` 不下载规则、不订阅 Redis，通过 HTTP 调用服务端执行，实验和跨请求执行状态也仅在该模式可用。SDK 两种模式都不直连引擎 MySQL。

## 2. 引入 SDK

在 JDK 17、Spring Boot 3 的 Web 服务中增加依赖：

```xml
<dependency>
    <groupId>com.hengshucredit.rule</groupId>
    <artifactId>rule-engine-client-sdk</artifactId>
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

项目凭据在“项目管理 → 调用鉴权”中取得；实际入口以控制台为准。凭据仅放在 Java 服务的 Secret/环境变量中，不交给浏览器或移动端。Redis 地址、密码和 database 必须与引擎服务端一致。全局模式不填写 `project-code`，客户端会全量同步并订阅所有规则推送；项目模式必须填写真实 `project-code`，它是实时推送路由键。`app-name` 只标识业务应用，不授予权限。HTTP 超时应覆盖规则、外数调用及轮询总耗时。

账号密码、API Key、HMAC-SHA256 的配置见 [部署与接入](deployment.html#sdk)，也可直接复制该项目导出 API 文档中的鉴权配置。

### 全局规则访问（Ed25519）

先在任意 JDK 17 程序中生成一对密钥，并把 `publicKey` 放入服务端配置；私钥只注入客户端：

```java
var pair = com.hengshucredit.rule.model.security.GlobalAccessSignature.generateKeyPair();
System.out.println("privateKey=" + pair.privateKey());
System.out.println("publicKey=" + pair.publicKey());
```

服务端：

```dotenv
RULE_GLOBAL_ACCESS_ENABLED=true
RULE_GLOBAL_ACCESS_KEY_ID=default
RULE_GLOBAL_ACCESS_PUBLIC_KEY=<publicKey>
```

完整 SDK：

```java
RuleEngineClient client = RuleEngineClient.builder()
        .serverUrl(serverUrl)
        .globalKeyAuth("default", privateKey)
        .connectionFactory(redis)
        .build();
```

全局模式下不设置 `project-code`；SDK 的 `/sync/all` 和 `/sync/functions/all` 会同步全部已发布规则与启用函数，并订阅所有规则推送。服务端仍按每条规则所属项目执行外数、数据库、名单、模型和计费逻辑。签名包含 HTTP 方法、原始路径、原始查询串、请求体摘要、时间戳和随机 nonce，服务端会校验时间窗并拒绝 nonce 重放。

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

HTTP 响应先检查外层 `success === true`，最后读取 `data.result`。未配置结果码映射时，成功码为字符串 `"000000"`；启用映射后不能仅依赖 code 判断成功。401/403 检查鉴权；404 检查所属项目、全局关联与发布状态。`traceEnabled` 位于请求体顶层，不在 `params` 中，默认 true；JSON 使用布尔值，multipart 可使用字符串 `true` / `false`，其他值返回标准码 100001（对外 code 可映射）。

### 外数原始报文关联

规则中的 API 外数变量结果会带有统一的 `externalCall` 信息：`externalCall.callId` 是一次逻辑调用的稳定关联键，`externalCall.request.rawBody`、`externalCall.rawResponseBody` 是供应商请求体和原始响应体，`rawRequestAvailable` / `rawResponseAvailable` 标明对应报文是否实际存在。`externalCall.traceSteps` 还按顺序记录规则入参、API 请求拼装、脱敏鉴权、外部请求、响应处理、字段映射和 API 变量实际赋值。顶层也提供同名 `callId`、`rawRequestBody`、`rawResponseBody`，业务系统可直接落库或透传，不需要根据缓存、重试或 responseMapping 进行分支判断。

需要在规则执行结束后补查报文时，使用同一项目令牌调用：

```text
GET /api/rule/runtime/external-calls/{callId}
```

该接口只返回调用所属项目的报文；缓存命中没有真实的供应商请求，`rawRequestAvailable=false` 属于正常情况，响应仍可通过 `callId` 关联。

外数接口的报文留存策略还可以在控制台按请求/响应分别配置：选择实际报文或脚本显式写入的 `state.logRequestBody`/`state.logResponseBody` 中间副本，配置 JSONPath 排除字段和单字段字节上限；Base64、3DES Base64 解密只生成分析副本，失败时不会回退覆盖未处理密文，`originalRequestBody`/`originalResponseBody` 仍保存供应商实际报文，规则实时结果不受影响。

## 5. 日志追踪开关

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `trace-enabled` | `true` | 控制本地及服务端执行的表达式追踪；关闭后不返回表达式 `traces`，仍保留 `traceId` |
| `log-report-enabled` | `true` | 控制 SDK **本地执行**日志上报；关闭后 HTTP、Kafka、自定义 reporter 均不接收日志 |

两个开关独立：关闭追踪但保留上报时，可保留基础执行日志；开启追踪但关闭上报时，可在 Java 服务读取追踪而不从 SDK 上报。服务端执行不重复从客户端上报日志。服务端基础执行日志、鉴权审计、外数日志及计费不受 `log-report-enabled` 控制；两个开关都关闭不等于平台不留任何日志。

本地上报默认使用有界 HTTP 异步队列。启用上报且有自定义 `ExecutionLogReporter` 时使用自定义实现；使用内置 Kafka 通道必须显式配置 `log-report-transport: KAFKA`，不会因宿主存在 `KafkaTemplate` 自动切换。Kafka 配置、项目隔离、失败补报与旧版迁移见 [Kafka 执行日志上报](kafka-execution-logs.md)。日志上报失败不应改变决策结果。程序化 Builder 支持 `.traceEnabled(false).logReportEnabled(false)`；即使显式传入 reporter，关闭上报仍然生效。

## 6. 直接调用 Server API

请求直接发往 Server（默认 8080，或反向代理后的 HTTPS 地址），不需要单独部署客户端服务。

- 标准规则执行：`POST /api/rule/sync/execute/{ruleCode}`，请求体为 `{"params":{},"clientAppName":"business-service","traceEnabled":false}`，实际字段以项目 API 文档为准。
- 开放接口执行：`POST /api/rule/openapi/{ruleCode}`，需先启用并发布规则开放接口；携带项目调用凭据及匹配的 `X-Auth-Code`，请求和响应按发布契约映射。
- 查询状态：`GET /api/rule/runtime/executions/{traceId}`；恢复等待中的执行：`POST /api/rule/runtime/executions/{traceId}/resume`。

鉴权使用项目配置的兼容令牌、Basic、API Key、HMAC 或交换后的短期令牌；OpenAPI 契约入口还需对应项目鉴权配置的 `X-Auth-Code`。凭据不应下发浏览器。项目「API」按钮继续下载包含字段、鉴权及多语言调用样例的 API 文档。

```bash
curl -X POST 'https://engine-api.example.com/api/rule/sync/execute/RISK_RULE' \
  -H 'Content-Type: application/json' \
  -H 'X-Rule-Token: <项目调用令牌>' \
  --data '{"params":{},"clientAppName":"business-service","traceEnabled":false}'
```

网络超时可能意味着执行状态未知；应按 traceId 查询状态，避免盲目重放产生重复外数调用或计费。

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

