# 天枢决策引擎 Docker 部署

服务端可接入 Elasticsearch，将执行日志、调用日志、追踪与历史特征分开索引。配置项、独立 Compose、存量补齐和回切流程见[日志混合存储](../docs/elasticsearch-log-storage.md)。

ES 主文件为 `docker/docker-compose.elasticsearch.yml`，索引和文件日志分别位于安装目录的 `docker/rule-engine-elasticsearch/data/` 和 `logs/`。在已有 ES 上更换挂载前，按[独立 ES 部署与迁移](rule-engine-elasticsearch/README.md)完成快照及恢复核验。

生产环境采用外层共享服务、内层应用版本布局：`config/.env.server`、`config/.env.client` 是生效配置，根目录原 env 仅为备份；应用位于 `deploy-prod/current`，上一版在 `previous`。已有 deploy-prod 先按 [一次性目录整理](../docs/server-upload.md) 操作，之后在安装目录运行 `bash install-release.sh 发布包.zip` 更新。本文下方是源码检出/旧平铺模式的 Compose 参考；已整理环境手工运维时，env 参数必须改用 `--env-file config/.env.server` 或 `config/.env.client`，并沿用原项目名。

MySQL、Redis、Kafka 共用长期保留的一套环境；日常只替换 JAR 与前端 dist，保留原 env、Compose、Nginx 配置及数据。独立迁移 SQL 随包交付到 migrations/，在停止旧应用并完成备份后、新版启动前按需执行。上传、更新与回退步骤见 [服务器部署与升级](../docs/server-upload.md)。统一入口调用本次包内的 `deploy.py` 完成校验、备份、替换与重启，兼容 `docker compose` 和 `docker-compose`。以下完整 Compose 命令用于手工运维。推荐 server/client 分开管理，默认 server 2 副本、web 1 个、可选 SDK runtime 1 个；MySQL、Redis 复用现有服务。

Ubuntu 继续使用现有 Nginx Proxy Manager 管理 HTTPS、证书和反向代理。本项目不安装或修改 UFW、Fail2ban、宿主机 Nginx 和现有端口规则。NPM 通过服务器 IP 和已配置的宿主机端口转发。

不要同时启动 `docker-compose.full.yml` 与 `docker-compose.mysql.yml`/`docker-compose.redis.yml`。如果确实需要多套环境并行运行，为每套环境设置不同的 `DEPLOY_PROJECT` 和宿主机端口。

默认执行日志通过 HTTP 上报；需要 Kafka 日志链路时，full Compose 和 `docker-compose.kafka.yml` 都可以启动单节点 KRaft Kafka，也可以改为接入外部 Kafka 集群。配置方法见本文的 Kafka 小节和 [`docs/kafka-execution-logs.md`](../docs/kafka-execution-logs.md)。

远程执行直接请求 Server：标准接口为 `POST /api/rule/sync/execute/{ruleCode}`，已启用并发布的开放接口为 `POST /api/rule/openapi/{ruleCode}`。接口文档仍从项目「API」按钮下载。已有部署升级时需停止并移除原 HTTP 转发容器，将业务请求改为 Server 地址、对应路径和项目调用凭据；原转发服务入口令牌不能用作 Server 项目凭据。SDK 本地执行服务按需保留。

## 共同准备

开发机在仓库根目录执行，使用 Docker 构建并运行后端测试、前端 lint 和单元测试：

```bash
node scripts/package-deployment.mjs --with-tests
```

开发机需要 Node.js 20.19+、Git、Docker Linux 容器及现代 `docker compose` 或 `docker-compose`（自动探测）。JDK、Maven 和前端依赖均在 Docker 内使用。Windows、macOS、Linux 的入口、架构与配置见 [跨平台发布构建](../docs/release-packaging.md)。产物为 `output/tianshu-deployment-时间戳.zip`、同名 `.zip.sha256` 及独立暂存目录，历史 ZIP 保留。包内含逐文件 `SHA256SUMS.txt`、版本信息、对应源码 `source/` 与许可证。

可选环境变量 `PACKAGE_CONFIG_DIR` 指定已有生产配置目录，复制其中的 `.env.server`、`.env.client`；默认只包含示例模板，不携带本机凭据。含生产凭据的 ZIP 不公开发布。服务器直接使用包内制品，无需 Maven/Node.js，不重新编译；包不含 Docker 镜像，首次启动需能拉取配置中的镜像。

制品位于发布目录/ZIP 内的 `docker/runtime/`，不覆盖开发工作区的同名目录：

```text
server.jar       管理端 API
runtime.jar      SDK 本地执行 runtime，默认 sdk profile
dist/            Vue 控制台静态文件
```

`schema.sql` 在 server JAR 内，每次启动会在数据库锁内比对并升级现有结构；目标库需要 `CREATE TEMPORARY TABLES` 权限，范围、日志和故障处理见[数据库结构自动升级](../docs/deployment.md#数据库结构自动升级)。JDBC URL 使用 `createDatabaseIfNotExist=true`，首次连接时会自动创建 `MYSQL_DATABASE`；因此首次部署账号需要建库权限，后续运行至少需要目标库的建表/变更权限。所有表显式使用 `utf8mb4`。样例数据不会自动导入；需要演示数据时，只使用 `docker/rule-engine-mysql/data-tianshu-example.sql`。

生产 Compose 默认只把日志写到 Docker stdout，不再在 runtime 的 `/data/logs/application.log` 生成文件；Docker 日志默认按 `10m × 3` 轮转。`SERVER_LOG_LEVEL` 和 `RUNTIME_LOG_LEVEL` 支持 `ERROR`、`WARN`、`INFO`、`DEBUG`，生产建议保持 `WARN`，临时排障再调高。客户端 runtime 默认关闭执行 trace（可通过 `RUNTIME_TRACE_ENABLED=true` 开启），服务端持久化 trace 默认不限制大小，可用 `RULE_TRACE_MAX_PERSIST_BYTES` 设置正数上限。执行恢复 journal 属于故障恢复数据，数据库正常时会自动消费和压缩，不应当作普通日志删除。

服务端的应用副本、MySQL、Redis、Nginx 和自建 Kafka 均支持在生效的 `.env.server` 中分别调整 CPU 与内存。资源变量、整机预算及已有安装的生效方式见[部署手册的服务端容器配额](../docs/deployment.md#服务端容器配额)。

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
docker compose --project-name tianshu-server --env-file .env.server \
  -f docker/docker-compose.server.yml up -d --scale server=2
docker compose --project-name tianshu-server --env-file .env.server \
  -f docker/docker-compose.server.yml ps
```

如果使用本包提供的 Kafka，先用同一份 `.env.server` 启动：

```bash
docker compose --project-name tianshu-server --env-file .env.server \
  -f docker/docker-compose.kafka.yml up -d
```

客户端 runtime 启动：

```bash
docker compose --project-name tianshu-client --env-file .env.client \
  -f docker/docker-compose.client.yml up -d
docker compose --project-name tianshu-client --env-file .env.client \
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
docker compose --project-name tianshu-external --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.yml up -d
docker compose --project-name tianshu-external --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.yml ps
docker compose --project-name tianshu-external --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.yml logs --tail 200 server runtime-sdk web
```

停止：

```bash
docker compose --project-name tianshu-external --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.yml down
```

该方案不会删除外部数据库或 Redis 数据。首次部署时，JDBC 会尝试创建 `MYSQL_DATABASE`，因此账号需要建库权限；已有数据库或后续启动至少需要执行 `schema.sql` 所需的建表/变更权限。该方案不创建 Kafka，使用外部 Broker 时配置 `KAFKA_BOOTSTRAP_SERVERS` 和可访问的 `advertised.listeners`。

## Kafka 独立 Compose

`docker-compose.kafka.yml` 启动一个单节点 KRaft Kafka，宿主机端口默认为 `9092`，外部客户端使用 `KAFKA_ADVERTISED_HOST:KAFKA_HOST_PORT`，容器内应用使用 `kafka:19092`。启动前在 `.env.server` 配置 `KAFKA_ADVERTISED_HOST`、`KAFKA_USERNAME`、`KAFKA_PASSWORD`：

```bash
docker compose --project-name tianshu-kafka --env-file .env.server \
  -f docker/docker-compose.kafka.yml up -d
```

默认 Kafka 外部监听为 SASL/PLAINTEXT，适合受控调试网络；生产环境应通过 Compose override 配置 SASL_SSL/TLS。Kafka 是 TCP 服务，Nginx Proxy Manager 不代理 Kafka 端口，外部可达性由 Ubuntu 现有端口策略和安全组决定。

## 方案四：Docker Compose + 自带 MySQL/Redis/Kafka

`docker-compose.full.yml` 是完整单机方案，包含 MySQL、Redis、Kafka、server、SDK 和 web，都在同一个 Compose 项目和网络内。server 默认启动 2 个副本；不要先启动 `docker-compose.mysql.yml` 或 `docker-compose.redis.yml`。

```bash
docker compose --project-name tianshu-full --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.full.yml up -d

# 显式指定 server 副本数时：
docker compose --project-name tianshu-full --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.full.yml up -d --scale server=2
docker compose --project-name tianshu-full --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.full.yml ps
docker compose --project-name tianshu-full --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.full.yml logs --tail 200 server sdk web kafka
```

数据库和 Redis 数据分别持久化到 `docker/rule-engine-mysql/data`、`docker/rule-engine-redis/data`；停止 Compose 不会删除数据目录。停止：

```bash
docker compose --project-name tianshu-full --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.full.yml down
```

从仓库或发布包根目录运行 `docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml --profile example-data run --rm --build example-data-import` 加载衡枢真信和模拟供应商。入口创建仅用于样例的数据库与 SFTP 只读账号，在目标库中校验并生成发布制品；重复执行复用已有配置。独立 MySQL Compose、地址和凭据说明见[样例导入与调用](rule-engine-mysql/README.md)。


## 只启动依赖组件（可选）

仅当没有现有 MySQL/Redis 且需要单独提供依赖时使用；不能与 full 方案同时启动：

```bash
docker compose --project-name tianshu-mysql --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.mysql.yml up -d
docker compose --project-name tianshu-redis --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.redis.yml up -d
```

JAR/容器从宿主机访问时使用 `127.0.0.1` 和映射端口；同一 Docker 网络中的容器使用 Compose 服务名和容器端口。停止依赖组件：

```bash
docker compose --project-name tianshu-mysql --env-file .env.client --env-file .env.server -f docker/docker-compose.mysql.yml down
docker compose --project-name tianshu-redis --env-file .env.client --env-file .env.server -f docker/docker-compose.redis.yml down
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
docker compose --project-name tianshu-external --env-file .env.client --env-file .env.server -f docker/docker-compose.yml up -d server
docker compose --project-name tianshu-external --env-file .env.client --env-file .env.server -f docker/docker-compose.yml up -d runtime-sdk
# 自带数据库 Compose
docker compose --project-name tianshu-full --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml up -d server
docker compose --project-name tianshu-full --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml up -d sdk
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

### 项目鉴权日志的客户端 IP

`rule_auth_access_log.client_ip` 记录发起本次项目 API 调用的一方。浏览器直接调用时通常是用户出口 IP；SDK 在容器里主动同步规则、上报日志时，容器地址或其 NAT 出口就是实际调用方。

直连 web 的部署可以留空 `WEB_TRUSTED_PROXY`。经 NPM 的部署，在生效的 `config/.env.server`（源码部署为 `.env.server`）设置以下两项。地址仅为示例。配置前经 NPM 发一次请求，按路径和时间查看天枢 web 访问日志中的连接来源，确认该地址专属于 NPM 后填入。

```dotenv
WEB_TRUSTED_PROXY=192.0.2.10
WEB_REAL_IP_HEADER=X-Real-IP
```

NPM 应覆盖所选地址头。若上游维护的是 `X-Forwarded-For` 链，则将 `WEB_REAL_IP_HEADER` 改为 `X-Forwarded-For`，web 从右往左跳过受信代理。可信范围可填写单个 IP 或仅由受控代理使用的 CIDR。若 NPM 和普通调用方经 NAT 后共用一个源地址，应先为代理提供可区分的入口。若 NPM 自己看到的也是网关地址，需从更外层保留客户端来源。

已有安装的日常更新会保留外层 `docker/`。请从新版合并当前 Compose 的 **web.environment 和 default.conf 模板挂载目标**，以及 `docker/nginx/default.conf`、`docker/nginx/proxy.conf` 的对应改动，再重建 web。以下以标准分离部署为例，在安装根目录执行，项目名和 Compose 文件沿用现场值。

```bash
docker compose --project-name tianshu-server --env-file config/.env.server -f docker/docker-compose.server.yml up -d --no-deps --force-recreate --wait web
docker compose --project-name tianshu-server --env-file config/.env.server -f docker/docker-compose.server.yml exec -T web nginx -t
```

通过实际入口发一次项目 API 请求，附上唯一的 `X-Request-Id`，用同一个值核对新记录。已有行的原始来源只能与当时保留的代理日志关联确认。

```sql
SELECT request_id, request_uri, client_ip, create_time
FROM rule_auth_access_log
WHERE request_id = '替换为本次请求的标识';
```

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
docker compose --project-name tianshu-external --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.yml up -d --force-recreate server runtime-sdk web
```

MySQL、Redis、Kafka 的更新、备份、恢复单独维护，应用升级不重建它们。不同应用部署可连接同一套基础设施；应用容器仍使用各自的宿主机端口和原 Compose 项目名。

## 内置 SFTP 交接目录

在「账户管理」新增账户时，选择「开通或更换公钥」并填写上传方的单行 OpenSSH 公钥。私钥由上传方保管。保存后界面给出登录名，端口和主机指纹由部署管理员交付。已有账户可在编辑页换钥或关闭登录，修改已有上传凭据还需要对应的重置密码权限。停用账户、撤销公钥或变更账户权限版本后，现有会话在下一次文件操作时失效。

| 用途 | 登录名示例 | 上传后的使用方式 |
| --- | --- | --- |
| 个人素材、模型 | `u42`（账户 ID 为 42） | 上传 `models/score.pmml` 后，在模型管理上传框选择「内置 SFTP」，填写 `models/score.pmml`。素材库「从 SFTP 导入」和模型详情「从 SFTP 替换」同样使用本人的个人目录。 |
| 项目进件、第三方结果 | `u42-p17`（账户已获项目 17 的访问权） | 上传 `reports/order-812.json` 后，向项目 17 的引擎传入 `u42/reports/order-812.json`。上传方只能操作自己的目录，项目内执行可按路径读取不同上传方交付的文件。 |

在变量管理选择 SFTP 来源和「引擎内置 SFTP」，远端路径可引用进件字段或上游接口的返回字段。第三方收到引擎调用后，先用获授权的上传账户将结果写入项目目录，再返回相对路径。建议每笔进件使用唯一文件名，先写临时文件再在同一目录重命名，成功后才返回路径。变量支持文本、JSON、Base64 和项目隔离的缓存；需要每次读取原件时，将跨规则缓存设为 0。个人上传单文件上限为 256 MiB，执行变量读取上限为 10 MiB，图片导入上限为 5 MiB。

上传目录是可写的交接区。素材导入会扫描原件并重编码为 PNG，模型新建和替换会校验 PMML/ONNX、记录摘要并创建审批草稿。入库后的内容存放在独立 SFTP 的受管目录，覆盖交接区的文件不会改变已有版本。

### 服务配置、账户与解封

进入「SFTP 管理 → 引擎 SFTP 服务配置」。页面沿用 SFTP 模块查看权限。在「上传账户」可为既有控制台账户登记或更换公钥、设为读写或只读、暂停登录、恢复权限或撤销公钥。暂停保留公钥和文件。撤销后再次开通需重新登记公钥。账户变更沿用账户管理和密码重置授权，修改自己的上传凭据还需本人改密权限。管理员在控制台预览项目交件时同样限于自己的项目子目录。持有项目凭证的引擎执行继续使用项目服务身份处理该项目的供应商交件。

拥有全局系统编辑权限的管理员可配置以下策略。部署连接地址、服务密码及主机指纹仍由服务端 env 提供。

| 配置项 | 初始值 | 生效范围 |
|---|---:|---|
| IP 认证请求数 | 120 次/分钟 | 取实际 SSH 对端 IP，跨连接共享计数，超出后到当前窗口结束或手动解封才允许认证 |
| 文件读写请求数 | 256 次/秒 | 一个上传账户的个人及项目会话合并，超额的数据包等待下一秒。引擎服务身份的受管文件读写独立运行 |
| 失败阈值和统计窗口 | 8 次，300 秒 | 来源 IP 累计失败、真实账户连续失败分别计数。仅完整 SSH 登录成功清零账户失败次数 |
| 失败封禁时长 | 900 秒 | 触发后暂停新的认证，到期自动恢复 |

「封禁记录」列出当前有效的来源、上传账户或引擎服务账户封禁。点击「解封」重置所选对象的计数。同一次登录同时命中来源和账户封禁时，两项独立生效。经 NAT 或 TCP 代理连接时，共用出口的用户可能被识别为同一来源 IP。策略与封禁写入同一个 MySQL，服务重启后保留。认证读取最新策略，文件速率配置最多缓存一秒。已登录会话的紧急撤权请使用「暂停登录」。若引擎服务账户或其来源 IP 被封禁，业务节点重启时会校验主机指纹和封禁记录，并保留控制台解封入口。文件请求继续执行 SSH 认证，解封后可重新连接。已有库升级时，先让新版 server 启动并完成 SFTP 表结构复核，再开放新版独立 SFTP 的上传账户服务。

### 启动独立 SFTP

在一台存储主机部署一个实例。把同版本发布包内的 server.jar 放到稳定路径，在生效的服务端 env 中填写其绝对路径 `SFTP_SERVER_JAR`、SFTP 宿主机内网监听地址 `ENGINE_SFTP_BIND_ADDRESS`、端口 `ENGINE_SFTP_PORT` 和独立生成的 `ENGINE_SFTP_PASSWORD`。该实例还需要能连接控制台使用的 MySQL，以便验证上传账户的公钥、状态和项目授权。以下命令在已整理的安装根目录执行，平铺安装将 env 路径改为 `.env.server`。

```bash
docker compose --env-file config/.env.server -f docker/docker-compose.sftp.yml up -d --wait sftp
docker compose --env-file config/.env.server -f docker/docker-compose.sftp.yml logs sftp
```

在每台业务节点及样例导入器的 env 中设置同一组 `ENGINE_SFTP_HOST`、`ENGINE_SFTP_PORT`、`ENGINE_SFTP_USERNAME`、`ENGINE_SFTP_PASSWORD` 和 `ENGINE_SFTP_HOST_FINGERPRINT`。最后一项取自存储主机可信日志中的完整 `SHA256:…`。容器内的主机地址必须能到达存储主机。业务节点启动时验证主机身份、认证并打开文件仓。单独运行 JAR 时使用 `java -jar server.jar --engine-sftp-server` 启动存储服务，普通 server 继续用原命令启动。JAR 模式的持久目录可通过 `ENGINE_SFTP_DIRECTORY` 设置。

更新独立服务时，将同版本校验通过的 JAR 放在存储主机的稳定路径，再用上述 Compose 配置加 `--force-recreate` 重建 sftp 容器，保留原数据卷和主机密钥。

独立服务的数据卷内，`host-key` 是主机密钥，`files/private` 和 `files/projects` 是个人与项目交接区，`files/managed` 保存模型和素材。上传账户只看到自己的根目录。备份应同时覆盖该卷和管理库。也可配置现有 SFTP，但需在该服务上配置专用账户根目录及目录隔离，禁止上传方创建链接、访问其他用户目录或受管文件。控制台自动开通、换钥和撤销上传账户由随包的独立服务提供。

### 从上一版升级

1. 停止写入，备份业务库、旧文件卷和主机密钥。将旧 `engine-sftp/private`、`engine-sftp/projects` 分别复制到新数据卷的 `files/private`、`files/projects`。要保留既有主机指纹，将旧 `host-key` 放在新卷顶层。旧卷分散在不同主机时，先核对同路径文件摘要，解决冲突后再合并。
2. 释放原 web 的 SFTP 映射端口，再启动独立服务。已有安装需合并新版 server 与导入器的 SFTP 环境配置，移除旧 web 的 SFTP 端口及 stream 模板挂载。部署脚本会保留现场 Compose，因此需要显式合并这些变更。
3. 核对指纹并配置所有节点。旧 MySQL 文件仓中的模型、冻结包和素材按原引用校验后复制到 SFTP。历史文件卷需在迁移节点保持可读，可用 `java -jar server.jar --migrate-model-files` 迁移模型。首次读取旧内联图片时会保存到 SFTP。验证新节点的冷读和备份恢复后，再决定旧卷和旧库的保留期限。
