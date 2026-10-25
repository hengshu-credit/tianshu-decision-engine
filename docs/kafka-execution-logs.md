# Kafka 执行日志上报

Kafka 用于完整 Java SDK **本地执行**后的日志上报。规则同步仍走 HTTP，变更通知仍走 Redis；Server API 执行不重复发送客户端日志。

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

两个应用 Compose 文件将 Kafka 地址映射给 server 和 SDK 容器；full Compose 默认使用内部 `kafka:19092`，外部数据库 Compose 使用 `KAFKA_BOOTSTRAP_SERVERS`。独立 `docker-compose.kafka.yml` 提供单节点 broker；server 的主题绑定必须由部署方配置，不能用消息中提交的项目编号替代绑定。

## 各部署方案的接入方式

Kafka 为可选依赖，默认 HTTP 上报。以下配置可以配合 JAR、独立 Docker、外接数据库 Compose、自带数据库 Compose 使用；启用前先准备 Kafka 集群和主主题、死信主题，取得实际项目 ID、项目凭证和已发布的本地计算规则。Kafka 的 `advertised.listeners` 必须公布客户端可达的地址，不能只保证 bootstrap 地址能连通。

### JAR 包

将上文“服务端外部配置”保存为 `/opt/tianshu/config/server-kafka.yml`，在服务进程环境中设置 `KAFKA_BOOTSTRAP_SERVERS`，加载配置启动 server：

```bash
java -jar server.jar --spring.config.additional-location=file:/opt/tianshu/config/server-kafka.yml
```

SDK runtime 进程使用上节列出的 `SDK_*` 和 `SPRING_KAFKA_BOOTSTRAP_SERVERS` 环境变量，同时配置 `RULE_SERVER_URL`、`RULE_PROJECT_ID`、`RULE_PROJECT_CODE`、`PROJECT_ACCESS_TOKEN`、`RULE_ALLOWED_CODES`、`RUNTIME_ACCESS_TOKEN`、`RUNTIME_CORS_ALLOWED_ORIGINS` 及与 server 一致的 Redis 连接，然后执行：

```bash
java -jar runtime.jar --spring.profiles.active=sdk --server.port=7071
```

SDK runtime 没有 server 的 `.env` 自动导入配置；使用进程环境变量，或显式追加 `--spring.config.additional-location=file:/实际路径/runtime.properties`。业务应用内嵌完整 Java SDK 时使用上文 `rule-engine.client` 配置。

### 独立 Docker

在原 `docker run` 命令中为 server 追加配置挂载和加载参数：

```bash
# 添加到镜像名之前
-v /opt/tianshu/config/server-kafka.yml:/config/server-kafka.yml:ro
-e SPRING_CONFIG_ADDITIONAL_LOCATION=file:/config/server-kafka.yml
-e KAFKA_BOOTSTRAP_SERVERS=kafka.internal:9092
```

SDK 容器的 `--env-file` 使用 `SPRING_KAFKA_BOOTSTRAP_SERVERS` 和上节 `SDK_*` 参数；`RULE_PROJECT_ID` 与 server 主题绑定保持一致。`KAFKA_SERVER_APPLICATION_JSON`、`KAFKA_SDK_APPLICATION_JSON` 是 Compose 别名，直接 `docker run` 需改用各容器独立的 `SPRING_APPLICATION_JSON` 或配置挂载。Linux 容器使用宿主机 broker 时需要网关映射及可达的 advertised 地址。

### 两种应用 Compose

在仓库根目录 `.env` 中配置（实际部署替换项目 ID `1`）：

```dotenv
KAFKA_BOOTSTRAP_SERVERS=kafka.internal:9092
RULE_KAFKA_ENABLED=true
RULE_KAFKA_GROUP_ID=rule-engine-server
KAFKA_SERVER_APPLICATION_JSON={"rule-engine":{"kafka":{"topic-projects":{"rule-execution-log-risk":1}}}}
KAFKA_SDK_APPLICATION_JSON={}
SDK_LOG_REPORT_ENABLED=true
SDK_LOG_REPORT_TRANSPORT=KAFKA
SDK_KAFKA_LOG_TOPIC=rule-execution-log-risk
SDK_KAFKA_HTTP_FALLBACK_ENABLED=true
RUNTIME_RULE_PROJECT_ID=1
```

server 和 SDK 分别读取两个 JSON，经 Compose 映射到各容器的 `SPRING_APPLICATION_JSON`。主主题、项目 ID、项目编码、凭证和规则白名单须指向同一实际项目；多个项目在 server JSON 的 `topic-projects` 中追加对应项。现有 `SPRING_APPLICATION_JSON` 内容需合并进去。先启用 server 消费，再切换 SDK 通道，具体 `up -d` 命令见 [Docker 部署说明](../docker/README.md#可选接入-kafka-执行日志)。

### TLS/SASL 配置与证书

server（读取主主题、写入死信）和 SDK（写入主主题）分别使用各自 Kafka 身份。JAR/独立 Docker 可在外部 YAML 中扩展 `spring.kafka`；Compose 则在相应 JSON 中增加同样的 `spring.kafka` 配置，例如下面的结构按角色合并，真实凭据通过部署环境注入：

```json
{"spring":{"kafka":{"security":{"protocol":"SASL_SSL"},"properties":{"sasl.mechanism":"SCRAM-SHA-512","sasl.jaas.config":"org.apache.kafka.common.security.scram.ScramLoginModule required username=\"实际账号\" password=\"实际密码\";"}}}}
```

server JSON 必须同时保留 `rule-engine.kafka.topic-projects`。使用自签 CA/双向 TLS 时添加集群要求的 `spring.kafka.ssl` 参数，并在 `docker run`/Compose override 中只读挂载证书到相应服务；配置路径使用容器内路径。仅把 `SPRING_KAFKA_*` 写在 Compose `.env` 中不会自动传入容器；也不能把 broker ACL 当成客户端环境变量。[Spring Boot 外部配置](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html)、[Compose 环境变量](https://docs.docker.com/compose/how-tos/environment-variables/set-environment-variables/)说明了上述注入规则。

### 验收与关闭

检查 server 已订阅目标主题、SDK 启用 KAFKA 后执行一条已发布的本地计算规则，并在控制台执行日志中按同一 `traceId` 核对项目归属。检查消费积压、`.DLT` 和 `rule.engine.kafka.log.records` 指标；应用 readiness 为 UP 不能单独证明 Kafka 投递成功。测试发送失败时，核实已鉴权 HTTP 补报实际落库；同一 trace 的重复投递不应产生重复计费。

关闭时先将全部生产者切回 HTTP，等待消费者处理完积压，再设置 `RULE_KAFKA_ENABLED=false` 并重建 server 容器或重启 JAR；数据库部署方式无需随之改变。

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
