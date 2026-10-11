# Kafka 执行日志上报

Kafka 用于完整 Java SDK **本地执行**后的日志上报。规则同步仍走 HTTP，变更通知仍走 Redis；HTTP-only SDK 和服务端执行不重复发送客户端日志。

## 通道选择与交付边界

- 默认 `log-report-transport: HTTP`。宿主应用拥有 `KafkaTemplate` 不会自动改变上报通道。
- 显式选择 `KAFKA` 时，SDK 将日志放入有界发送队列，由后台线程提交 Kafka；同步异常和异步发送失败均被观察。
- Kafka 发送失败可交给已有的有界 HTTP 上报器补报，必须配置有效的项目鉴权。HTTP 补报使用正常鉴权接口，不发送凭据到 Kafka。
- 关闭 `log-report-enabled` 或使用 `server-side-execution` 时，不发送客户端日志；应用自定义 `ExecutionLogReporter` 仍优先。
- Kafka 接收成功后的重投以及 HTTP 补报可能重复到达。服务端按稳定的 `traceId` 校验身份并幂等接收；日志、追踪登记和计费在同一数据库事务内处理。
- SDK 队列和 HTTP 补报队列是内存队列，进程崩溃、满队列或补报最终失败仍可能丢日志。本实现不承诺端到端零丢失；需要强审计保证时应采用服务端执行及其持久化恢复链路。

## 按项目隔离主题

服务端通过配置将每个 Kafka 主题绑定到真实项目 ID。SDK 在 `X-Rule-Project-Id` 消息头中发送其执行项目 ID，必须与主题绑定一致。服务端按 ID 查询当前项目、验证规则访问范围，并填写当前项目编码；不根据可修改的项目名称或消息携带的鉴权字段确定身份。

Kafka 的发布者身份由集群的 TLS/SASL 和 ACL 保证。每个业务应用只能向自己项目的主题写入；仅校验消息头不能替代 broker ACL。消费者需要读取这些主题及消费组、写入对应 `.DLT` 主题的权限。Kafka 日志保留项目归属，不伪造 HTTP 的账号或 Token 鉴权归因。

示例使用项目 ID `1`，主题 `rule-execution-log-risk`。部署前替换成实际项目 ID，并提前创建主主题及 `rule-execution-log-risk.DLT`；复制因子、分区、保留期和 ACL 按集群策略设置。

服务端外部配置：

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    # TLS/SASL 等连接参数也通过 spring.kafka 配置，由部署环境注入。
rule-engine:
  kafka:
    enabled: true
    group-id: rule-engine-server
    topic-projects:
      rule-execution-log-risk: 1
      # rule-execution-log-other: 2
    max-retries: 2
    retry-interval-ms: 1000
```

`max-retries: 2` 表示首次失败后最多再尝试两次。服务端使用专用的单条消息监听容器，固定字符串反序列化、关闭自动提交，不依赖或改变其他业务监听器的 batch 配置。关闭 `rule-engine.kafka.enabled` 时不创建日志监听容器。

## Spring Boot 业务应用配置

应用需直接引入 `org.springframework.kafka:spring-kafka`，版本沿用 Spring Boot 的依赖管理；SDK 中该依赖为 optional。配置项目凭据及正常规则同步所需的 HTTP、Redis 参数后，增加：

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
      properties:
        enable.idempotence: true
        delivery.timeout.ms: 30000
        request.timeout.ms: 10000
        max.block.ms: 5000
rule-engine:
  client:
    server-url: ${RULE_SERVER_URL}
    project-id: 1
    project-code: ${RULE_PROJECT_CODE}
    app-name: risk-service
    token: ${RULE_PROJECT_TOKEN}
    server-side-execution: false
    log-report-enabled: true
    log-report-transport: KAFKA
    kafka-log-topic: rule-execution-log-risk
    kafka-http-fallback-enabled: true
    log-buffer-size: 500
```

项目鉴权也可以使用 BASIC、API Key 或 HMAC-SHA256，见 [Java 业务服务接入指南](java-service-integration.md)。未提供自定义 reporter 时，显式 Kafka 配置若缺少 `KafkaTemplate` 或真实项目 ID，启动会失败，不能悄悄降级成 HTTP。禁用 HTTP 补报时，发送失败仍记录计数和告警。

`log-buffer-size` 限制 SDK 待发送资源；HTTP 的批量大小和冲刷间隔仍由 `log-batch-size`、`log-flush-interval-ms` 控制，不能把它们理解成 Kafka broker 的批处理参数。

程序化接入使用 `new KafkaLogReporter(kafkaTemplate, ruleEngineClientConfig)` 并传给 Builder 的 `.logReporter(reporter)`；配置对象需包含项目 ID、主题，以及启用补报时的服务地址和项目凭据。外部 reporter 由调用方或 Spring 容器关闭，客户端不会代替调用方管理它的生命周期。关闭 reporter 会有界等待确认并尝试补报未确认日志，不关闭宿主共享的 `KafkaTemplate`。

## 独立 SDK runtime

`rule-engine-runtime` 的 `sdk` profile 支持以下环境变量，HTTP profile 不使用 Kafka 日志通道：

```dotenv
SDK_LOG_REPORT_ENABLED=true
SDK_LOG_REPORT_TRANSPORT=KAFKA
SDK_KAFKA_LOG_TOPIC=rule-execution-log-risk
SDK_KAFKA_HTTP_FALLBACK_ENABLED=true
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka.internal:9092
```

两个现有 Compose 文件将 `KAFKA_BOOTSTRAP_SERVERS` 映射给 SDK 容器；该地址必须能从容器访问。它们不会自动部署 Kafka 集群。服务端主题绑定通过外部 Spring 配置提供，不要用客户端提交的项目编号替代绑定。

## 消费失败、死信与重放

非法 JSON、项目头不匹配、无效追踪或越权规则等不可重试错误进入 `<原主题>.DLT`；数据库等暂时性错误在有限重试后进入死信主题。只有主事务成功，或死信消息确认写入后，才允许消费位置向前推进。死信发布失败会保留失败状态并继续由监听容器恢复，不能把它当作处理成功。

运维应同时关注 SDK 发送失败、排队拒绝、HTTP 补报失败，以及消费者重试、死信数和消费积压。SDK 的 `getSuccessCount()`、`getFailureCount()`、`getPendingLogCount()`、`getDroppedLogCount()` 提供 Kafka 状态；`getFallbackAttemptCount()` 仅表示补报已提交，`getFallbackSuccessCount()` / `getFallbackFailedLogCount()` 才表示 HTTP 处理结果。

服务端 Micrometer 指标 `rule.engine.kafka.log.records` 使用固定 `outcome` 标签：`accepted`、`duplicate`、`rejected`、`failed`、`retry`、`dead_letter`、`dead_letter_failure`。重试会增加多次失败计数，不等于新增多条业务日志。默认不公开 Actuator metrics 接口，若开放需按部署权限保护。

重放前先定位原因并修复，保留原始 `traceId`、项目消息头和业务载荷，重新投递到对应主主题。不要自动消费死信再写回主主题造成无限循环；不要为规避重复校验重新生成 trace。死信同样含执行输入、输出和追踪数据，应与主主题采用相同的访问控制和保留策略。

## 从旧版迁移

旧版依赖 `KafkaTemplate` 自动选通道，且服务端只有单个 `log-topic`。升级时必须显式配置客户端 `log-report-transport: KAFKA`、服务端 `enabled: true` 和 `topic-projects`。旧 `log-topic` 配置不能替代项目绑定。

旧客户端不发送项目 ID 头，不应与新消费者混用。先停止旧生产者并排空旧主题，再部署新客户端与消费者，或切换到新的项目专属主题；存量消息需确认项目归属后由运维补齐消息头再重放。迁移期间保留可用的已鉴权 HTTP 上报配置。

部分旧 HTTP 日志的追踪登记缺少资源 ID 或调用项目 ID。新接收服务无法证明这类旧身份与重投消息一致时会拒绝，不能将其误判为安全的重复消息；升级前应排空旧客户端的待上报队列。旧 HTTP 无 `traceId` 的载荷仍兼容，但不具备新的幂等保证。SDK 实际执行的修订与制品摘要继续保留，不用消费时的最新发布版本覆盖历史执行快照。

Spring Kafka 的重试与死信机制参考[官方异常处理文档](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)。
