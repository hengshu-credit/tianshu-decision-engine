# 天枢决策引擎 Docker 部署

上传 ZIP、保护原配置、停机替换、启动验证及回退步骤见 [服务器部署与升级](../docs/server-upload.md)。所有服务操作直接使用完整的 `docker-compose` 命令。推荐 server/client 分开管理，默认 server 2 副本、web 1 个、可选 SDK runtime 1 个；MySQL、Redis 复用现有服务。

Ubuntu 继续使用现有 Nginx Proxy Manager 管理 HTTPS、证书和反向代理。本项目不安装或修改 UFW、Fail2ban、宿主机 Nginx 和现有端口规则。NPM 通过服务器 IP 和已配置的宿主机端口转发。

不要同时启动 `docker-compose.full.yml` 与 `docker-compose.mysql.yml`/`docker-compose.redis.yml`。如果确实需要多套环境并行运行，为每套环境设置不同的 `DEPLOY_PROJECT` 和宿主机端口。

默认执行日志通过 HTTP 上报；需要 Kafka 日志链路时，full Compose 和 `docker-compose.kafka.yml` 都可以启动单节点 KRaft Kafka，也可以改为接入外部 Kafka 集群。配置方法见本文的 Kafka 小节和 [`docs/kafka-execution-logs.md`](../docs/kafka-execution-logs.md)。

远程执行直接请求 Server：标准接口为 `POST /api/rule/sync/execute/{ruleCode}`，已启用并发布的开放接口为 `POST /api/rule/openapi/{ruleCode}`。接口文档仍从项目「API」按钮下载。已有部署升级时需停止并移除原 HTTP 转发容器，将业务请求改为 Server 地址、对应路径和项目调用凭据；原转发服务入口令牌不能用作 Server 项目凭据。SDK 本地执行服务按需保留。

## 共同准备

开发机在仓库根目录执行，使用 Docker 构建并运行后端测试、前端 lint 和单元测试：

```bash
node scripts/package-deployment.mjs --with-tests
```

开发机需要 Node.js 20.19+、Git、Docker Linux 容器及可直接执行的 `docker-compose` v2+（含 v5）。JDK、Maven 和前端依赖均在 Docker 内使用。Windows、macOS、Linux 的入口、架构与配置见 [跨平台发布构建](../docs/release-packaging.md)。产物为 `output/tianshu-deployment-时间戳.zip`、同名 `.zip.sha256` 及独立暂存目录，历史 ZIP 保留。包内含逐文件 `SHA256SUMS.txt`、版本信息、对应源码 `source/` 与许可证。

可选环境变量 `PACKAGE_CONFIG_DIR` 指定已有生产配置目录，复制其中的 `.env.server`、`.env.client`；默认只包含示例模板，不携带本机凭据。含生产凭据的 ZIP 不公开发布。服务器直接使用包内制品，无需 Maven/Node.js，不重新编译；包不含 Docker 镜像，首次启动需能拉取配置中的镜像。

制品位于发布目录/ZIP 内的 `docker/runtime/`，不覆盖开发工作区的同名目录：

```text
server.jar       管理端 API
runtime.jar      SDK 本地执行 runtime，默认 sdk profile
dist/            Vue 控制台静态文件
```

`schema.sql` 在 server JAR 内，服务端默认每次启动幂等执行。JDBC URL 使用 `createDatabaseIfNotExist=true`，首次连接时会自动创建 `MYSQL_DATABASE`；因此首次部署账号需要建库权限，后续运行至少需要目标库的建表/变更权限。所有表显式使用 `utf8mb4`。样例数据不会自动导入；需要演示数据时，只使用 `docker/rule-engine-mysql/data-tianshu-example.sql`。

生产 Compose 默认只把日志写到 Docker stdout，不再在 runtime 的 `/data/logs/application.log` 生成文件；Docker 日志默认按 `10m × 3` 轮转。`SERVER_LOG_LEVEL` 和 `RUNTIME_LOG_LEVEL` 支持 `ERROR`、`WARN`、`INFO`、`DEBUG`，生产建议保持 `WARN`，临时排障再调高。客户端 runtime 默认关闭执行 trace（可通过 `RUNTIME_TRACE_ENABLED=true` 开启），服务端持久化 trace 默认不限制大小，可用 `RULE_TRACE_MAX_PERSIST_BYTES` 设置正数上限。执行恢复 journal 属于故障恢复数据，数据库正常时会自动消费和压缩，不应当作普通日志删除。

## 推荐：server/client 分离部署

正式部署建议把服务端和客户端 runtime 分开管理。以下复制仅用于尚无配置的全新部署；已有环境保留原配置和主密钥：

```bash
cp .env.server.example .env.server
cp .env.client.example .env.client
vi .env.server
vi .env.client
```

`.env.server` 只配置 server 多节点、web、控制台、MySQL、Redis、Kafka 和服务端密钥；`.env.client` 只配置 SDK runtime、`RULE_SERVER_URL`、客户端令牌，以及 SDK 需要访问的 Redis。示例默认使用 `GLOBAL_KEY` 全局鉴权，直接同步全部已发布项目；切换项目级客户端时再填写项目凭据、项目 ID/编码和规则白名单。SDK runtime 必须连接与 server 共用的 Redis，才能接收规则和函数变更推送；启用 Kafka 日志时还要把 `KAFKA_BOOTSTRAP_SERVERS` 配成客户端可达地址。

服务端启动：

```bash
docker-compose --project-name tianshu-server --env-file .env.server \
  -f docker/docker-compose.server.yml up -d --scale server=2
docker-compose --project-name tianshu-server --env-file .env.server \
  -f docker/docker-compose.server.yml ps
```

如果使用本包提供的 Kafka，先用同一份 `.env.server` 启动：

```bash
docker-compose --project-name tianshu-server --env-file .env.server \
  -f docker/docker-compose.kafka.yml up -d
```

客户端 runtime 启动：

```bash
docker-compose --project-name tianshu-client --env-file .env.client \
  -f docker/docker-compose.client.yml up -d
docker-compose --project-name tianshu-client --env-file .env.client \
  -f docker/docker-compose.client.yml ps
```

客户端部署在其他机器时，`RUNTIME_RULE_SERVER_URL` 使用服务端 IP+端口或 Nginx Proxy Manager 的 HTTPS 域名；`REDIS_HOST` 使用 server 侧 Redis 的可达 IP，不能填写客户端容器内的 `localhost`。

## 方案三：Docker Compose + 外接 MySQL/Redis

`docker-compose.yml` 只启动 server、SDK runtime 和 web；它不会创建 MySQL 或 Redis。数据库地址必须从容器内可达：

```dotenv
DEPLOY_PROJECT=tianshu-external
MYSQL_HOST=host.docker.internal
MYSQL_PORT=3306
REDIS_HOST=host.docker.internal
REDIS_PORT=6379
RUNTIME_DIR=./runtime
```

云数据库或远程 Redis 直接把 `MYSQL_HOST`、`MYSQL_PORT`、`REDIS_HOST`、`REDIS_PORT` 改为容器可访问的域名/IP。启动：

```bash
docker-compose --project-name tianshu-external --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.yml up -d
docker-compose --project-name tianshu-external --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.yml ps
docker-compose --project-name tianshu-external --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.yml logs --tail 200 server runtime-sdk web
```

停止：

```bash
docker-compose --project-name tianshu-external --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.yml down
```

该方案不会删除外部数据库或 Redis 数据。首次部署时，JDBC 会尝试创建 `MYSQL_DATABASE`，因此账号需要建库权限；已有数据库或后续启动至少需要执行 `schema.sql` 所需的建表/变更权限。该方案不创建 Kafka，使用外部 Broker 时配置 `KAFKA_BOOTSTRAP_SERVERS` 和可访问的 `advertised.listeners`。

## Kafka 独立 Compose

`docker-compose.kafka.yml` 启动一个单节点 KRaft Kafka，宿主机端口默认为 `9092`，外部客户端使用 `KAFKA_ADVERTISED_HOST:KAFKA_HOST_PORT`，容器内应用使用 `kafka:19092`。启动前在 `.env.server` 配置 `KAFKA_ADVERTISED_HOST`、`KAFKA_USERNAME`、`KAFKA_PASSWORD`：

```bash
docker-compose --project-name tianshu-kafka --env-file .env.server \
  -f docker/docker-compose.kafka.yml up -d
```

默认 Kafka 外部监听为 SASL/PLAINTEXT，适合受控调试网络；生产环境应通过 Compose override 配置 SASL_SSL/TLS。Kafka 是 TCP 服务，Nginx Proxy Manager 不代理 Kafka 端口，外部可达性由 Ubuntu 现有端口策略和安全组决定。

## 方案四：Docker Compose + 自带 MySQL/Redis/Kafka

`docker-compose.full.yml` 是完整单机方案，包含 MySQL、Redis、Kafka、server、SDK 和 web，都在同一个 Compose 项目和网络内。server 默认启动 2 个副本；不要先启动 `docker-compose.mysql.yml` 或 `docker-compose.redis.yml`。

```bash
docker-compose --project-name tianshu-full --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.full.yml up -d

# 显式指定 server 副本数时：
docker-compose --project-name tianshu-full --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.full.yml up -d --scale server=2
docker-compose --project-name tianshu-full --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.full.yml ps
docker-compose --project-name tianshu-full --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.full.yml logs --tail 200 server sdk web kafka
```

数据库和 Redis 数据分别持久化到 `docker/rule-engine-mysql/data`、`docker/rule-engine-redis/data`；停止 Compose 不会删除数据目录。停止：

```bash
docker-compose --project-name tianshu-full --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.full.yml down
```

需要演示数据时，额外启用 `example-data` profile；该 profile 等待 schema 完成后只插入 `docker/rule-engine-mysql/data-tianshu-example.sql`：

```bash
docker-compose --project-name tianshu-full --profile example-data \
  --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml up -d
```

已有数据目录不会被示例导入自动清空。生产环境不要启用 `example-data`，也不要把 MySQL 数据目录直接复制到另一套实例。

样例脚本兼容两种顺序：`schema.sql → 样例`，或 `schema.sql → initial-data.sql → 样例`；样例之后再开启可选初始化也不会重复创建配置。它按当前库实际 ID 写入对象字段、枚举、变量来源、模型入出参和规则 JSON 引用，不依赖空库或连续自增 ID。重复导入只补齐缺失数据，保留已有配置。

`black_hit`、`engine_bdrules`、`black_user_count` 依赖演示名单、API 和数据库，由样例脚本在依赖就绪后创建。名单变量使用当前的 `listIds + queryOperands` 配置，查询手机号绑定实际变量 ID。对于旧版 `initial-data.sql` 已写入的三条默认配置，导入只在全部原始字段、配置及时间均匹配时修正引用；用户修改过的配置保持原样。若同编码存在多条 GLOBAL 记录，脚本会拒绝歧义，不任意合并或改绑。

手动导入应在同一个连接中完整执行脚本并遇错停止，不要使用 `mysql --force`。脚本使用与服务端可选初始化相同的命名锁和一个事务。首次启动服务完成结构初始化后再导入；演示外数地址、数据库连接、凭据及实际规则发布仍须按使用环境配置，SQL 导入不代替发布审批。

## 只启动依赖组件（可选）

仅当没有现有 MySQL/Redis 且需要单独提供依赖时使用；不能与 full 方案同时启动：

```bash
docker-compose --project-name tianshu-mysql --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.mysql.yml up -d
docker-compose --project-name tianshu-redis --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.redis.yml up -d
```

JAR/容器从宿主机访问时使用 `127.0.0.1` 和映射端口；同一 Docker 网络中的容器使用 Compose 服务名和容器端口。停止依赖组件：

```bash
docker-compose --project-name tianshu-mysql --env-file .env.client --env-file .env.server -f docker/docker-compose.mysql.yml down
docker-compose --project-name tianshu-redis --env-file .env.client --env-file .env.server -f docker/docker-compose.redis.yml down
```

## 可选：接入 Kafka 执行日志

Kafka 只服务于完整 Java SDK 或 `rule-engine-runtime` 的本地执行日志，以及 server 对 Kafka 日志的消费。Server API 执行和规则同步仍不依赖 Kafka。full Compose 会提供一个单节点 broker；生产环境也可以通过 `KAFKA_BOOTSTRAP_SERVERS` 接入外部 Kafka 集群。

两个 Compose 方案使用 Kafka 时，server 配置写入 `.env.server`，SDK 配置写入 `.env.client`。默认全局客户端不需要项目 ID、项目编码或规则白名单；只有切换到项目级鉴权并使用项目级 Kafka 日志时才填写实际项目 ID：

```dotenv
KAFKA_BOOTSTRAP_SERVERS=kafka.internal:9092
SDK_LOG_REPORT_ENABLED=true
SDK_LOG_REPORT_TRANSPORT=KAFKA
SDK_KAFKA_LOG_TOPIC=rule-execution-log-risk
SDK_KAFKA_HTTP_FALLBACK_ENABLED=true
# 项目级客户端 + Kafka 日志时才需要：
# RUNTIME_RULE_PROJECT_ID=1
```

server 消费 Kafka 时追加：

```dotenv
RULE_KAFKA_ENABLED=true
RULE_KAFKA_GROUP_ID=rule-engine-server
KAFKA_SERVER_APPLICATION_JSON={"rule-engine":{"kafka":{"topic-projects":{"rule-execution-log-risk":1}}}}
```

`KAFKA_BOOTSTRAP_SERVERS` 同时映射到 server 和 SDK 容器的 `SPRING_KAFKA_BOOTSTRAP_SERVERS`。server 的 `KAFKA_SERVER_APPLICATION_JSON` 写入 `.env.server`，SDK 的 `KAFKA_SDK_APPLICATION_JSON` 写入 `.env.client`，分别映射到各自的 `SPRING_APPLICATION_JSON`；可在 JSON 的 `spring.kafka` 下分别配置消费者和生产者的 TLS/SASL 参数。两个 env 文件中任意新增的 `SPRING_KAFKA_*` 不会自动进入 Compose 容器，须通过这两个 JSON 或 Compose override 明确注入；证书路径需对应容器内实际挂载路径。已有 `SPRING_APPLICATION_JSON` 配置应合并到相应 JSON 中。

主主题和 `<topic>.DLT`、生产者/消费者组 ACL、死信写入权限须提前准备；broker 的 `advertised.listeners` 返回地址也必须从应用侧可达。保留实际项目凭证、项目编码和已发布规则白名单，先启动 server 完成主题绑定，再启动 SDK。修改后执行对应方案命令（项目名与原部署一致）：

```bash
# 外接数据库 Compose
docker-compose --project-name tianshu-external --env-file .env.client --env-file .env.server -f docker/docker-compose.yml up -d server
docker-compose --project-name tianshu-external --env-file .env.client --env-file .env.server -f docker/docker-compose.yml up -d runtime-sdk
# 自带数据库 Compose
docker-compose --project-name tianshu-full --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml up -d server
docker-compose --project-name tianshu-full --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml up -d sdk
```

源码开发直接运行 JAR 时使用 Spring Boot 的 `SPRING_KAFKA_BOOTSTRAP_SERVERS`、`SPRING_APPLICATION_JSON` 或外部 YAML，不能直接使用上述两个 Compose JSON 别名。具体启动命令、TLS/SASL 配置、验收及关闭顺序见 [Kafka 部署接入](../docs/kafka-execution-logs.md#各部署方案的接入方式)。

## Ubuntu 与独立 Nginx Proxy Manager

本项目不会安装或修改 Ubuntu 的 UFW、Fail2ban、宿主机 Nginx、Certbot 或既有端口规则。先准备配置：

```bash
cp .env.server.example .env.server
cp .env.client.example .env.client
vi .env.server
vi .env.client
```

如果独立 NPM 通过服务器 IP 访问容器映射端口，应用端口需要绑定到可被 NPM 访问的地址：

```dotenv
APP_BIND_ADDRESS=0.0.0.0
SERVER_PUBLIC_BIND_ADDRESS=0.0.0.0
FRONTEND_PORT=9090
SERVER_PUBLIC_PORT=8080
RUNTIME_SDK_PORT=7071
```

如果 NPM 在宿主机上运行，也可以将应用地址保持为 `127.0.0.1`，然后在 NPM 中代理到 `127.0.0.1`。不论哪种方式，`RULE_SERVER_URL` 都填写服务端实际地址，不填写 `server:8080`：

```dotenv
# 客户端通过 IP+端口访问
RUNTIME_RULE_SERVER_URL=http://192.168.1.10:8080

# 或客户端通过 NPM HTTPS 域名访问
# RUNTIME_RULE_SERVER_URL=https://engine-api.example.com
```

在 Nginx Proxy Manager 中创建 Proxy Host，Forward Host 填服务器 IP，Forward Port 填宿主机映射端口：

| 用途 | 域名示例 | Forward Host | Forward Port |
|---|---|---|---:|
| 控制台 | `console.example.com` | `192.168.1.10` | `9090` |
| server API / `RULE_SERVER_URL` | `engine-api.example.com` | `192.168.1.10` | `8080` |
| SDK runtime（确需公网访问时） | `sdk.example.com` | `192.168.1.10` | `7071` |

每个 Proxy Host 在 SSL 页面申请对应证书，并启用 `Force SSL`、HTTP/2 和 HSTS。Advanced 配置保留以下转发头：

```nginx
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header Host $host;
```

Server 执行请求使用项目调用凭据；SDK runtime 入口使用独立的 `SDK_ACCESS_TOKEN`。MySQL、Redis、Kafka 是 TCP 服务，不能通过 NPM 的普通 Proxy Host 代理；它们的访问范围由现有 Ubuntu 防火墙、云安全组和 Compose 端口绑定控制。Kafka 默认外部调试端口为 `9092`，生产环境应使用 SASL_SSL/TLS。

### 服务器重启自动拉起

Compose 已配置 `restart: unless-stopped`，Docker 重启时会恢复未手工停止的容器，无需新增 systemd 包装服务。

## 常用验证和更新

```bash
curl -fsS http://127.0.0.1:8080/actuator/health/liveness
curl -i http://127.0.0.1:8080/actuator/health/readiness
curl -I http://127.0.0.1:9090/
```

`readiness` 返回 `UP` 后再接收业务流量。更新时按 [上传指南](../docs/server-upload.md) 先备份、停止应用、替换制品，再重建容器。不要直接覆盖运行中的 JAR。以下外接方案命令须沿用原项目名：

```bash
docker-compose --project-name tianshu-external --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.yml up -d --force-recreate server runtime-sdk web
```

数据库和 Redis 的更新、备份、恢复由其所属环境负责；应用方案之间不要共享同一宿主机端口或同一个 Compose 项目名。
