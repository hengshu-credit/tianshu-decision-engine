# 天枢决策引擎 Docker 部署

本目录支持四种相互独立的部署方案。每套方案只使用自己的进程、Compose 项目、网络、端口和数据目录；部署时选择一种即可。

Ubuntu 公网部署使用服务器上独立运行的 Nginx Proxy Manager 管理 HTTPS、证书和反向代理；本项目不安装或修改 UFW、Fail2ban、宿主机 Nginx 及现有端口规则。NPM 直接使用服务器 IP 和宿主机端口配置 Proxy Host。MySQL、Redis 按 `.env` 绑定到 `0.0.0.0` 以便远程调试，访问控制由现有安全组/防火墙策略负责。

| 方案 | 启动内容 | MySQL/Redis | 适用场景 |
|---|---|---|---|
| JAR 包 | 宿主机 Java + Nginx | 外接或先启动依赖 Compose | 已有 Java/Nginx 或 systemd |
| Docker | `docker run` 启动 JAR、runtime、Nginx 容器 | 外接或先启动依赖 Compose | 不使用 Compose，按容器分别管理 |
| 外接数据库 Compose | `docker-compose.yml` 的 server、runtime、web | 外接 MySQL/Redis | 应用容器化，数据库由云服务或其他环境提供 |
| 自带数据库 Compose | `docker-compose.full.yml` 的全部服务 | Compose 内置 MySQL/Redis | 单机演示、测试或完整一体化部署 |

不要同时启动 `docker-compose.full.yml` 与 `docker-compose.mysql.yml`/`docker-compose.redis.yml`。如果确实需要多套环境并行运行，为每套环境设置不同的 `DEPLOY_PROJECT` 和宿主机端口。

默认执行日志通过 HTTP 上报；需要 Kafka 日志链路时，full Compose 和 `docker-compose.kafka.yml` 都可以启动单节点 KRaft Kafka，也可以改为接入外部 Kafka 集群。配置方法见本文末尾的 Kafka 小节和 [`docs/kafka-execution-logs.md`](../docs/kafka-execution-logs.md)。

## 共同准备

在仓库根目录执行，生成 server JAR、runtime JAR 和前端 `dist`：

```powershell
Copy-Item .env.server.example .env.server
Copy-Item .env.client.example .env.client
# 分别编辑 .env.server 和 .env.client，替换所有 replace-with-* 占位值
node scripts/package-runtime.mjs
```

需要在 Docker 内完成构建并直接生成可上传 ZIP 时，执行：

```bash
node scripts/package-deployment.mjs
```

该脚本使用 Maven、Node.js 和 zip 容器构建，不依赖宿主机的 JDK、Maven 或前端 `node_modules`；产物位于 `output/tianshu-deployment-*.zip`。

生成目录为 `docker/runtime/`：

```text
server.jar       管理端 API
runtime.jar      HTTP/SDK runtime，共用同一个 JAR，通过 profile 区分
dist/            Vue 控制台静态文件
```

`schema.sql` 在 server JAR 内，服务端默认每次启动幂等执行。JDBC URL 使用 `createDatabaseIfNotExist=true`，首次连接时会自动创建 `MYSQL_DATABASE`；因此首次部署账号需要建库权限，后续运行至少需要目标库的建表/变更权限。所有表显式使用 `utf8mb4`。样例数据不会自动导入；需要演示数据时，只使用 `docker/rule-engine-mysql/data-tianshu-example.sql`。

## 推荐：server/client 分离部署

正式部署建议把服务端和客户端 runtime 分开管理，分别使用两个配置文件：

```bash
cp .env.server.example .env.server
cp .env.client.example .env.client
vi .env.server
vi .env.client
```

`.env.server` 只配置 server 多节点、web、控制台、MySQL、Redis、Kafka 和服务端密钥；`.env.client` 只配置 HTTP/SDK runtime、`RULE_SERVER_URL`、项目凭据、客户端令牌，以及 SDK 需要访问的 Redis。SDK runtime 必须连接与 server 共用的 Redis，才能接收规则和函数变更推送；启用 Kafka 日志时还要把 `KAFKA_BOOTSTRAP_SERVERS` 配成客户端可达地址。

服务端启动：

```bash
docker compose --project-name tianshu-server --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.server.yml up -d --scale server=2
docker compose --project-name tianshu-server --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.server.yml ps
```

如果使用本包提供的 Kafka，先用同一份 `.env.server` 启动：

```bash
docker compose --project-name tianshu-server --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.kafka.yml up -d
```

客户端 runtime 启动：

```bash
docker compose --project-name tianshu-client --env-file .env.client --env-file .env.server.client \
  -f docker/docker-compose.client.yml up -d
docker compose --project-name tianshu-client --env-file .env.client --env-file .env.server.client \
  -f docker/docker-compose.client.yml ps
```

客户端可以独立扩容或部署到其他服务器：

```bash
docker compose --project-name tianshu-client --env-file .env.client --env-file .env.server.client \
  -f docker/docker-compose.client.yml up -d --scale runtime-http=3 --scale runtime-sdk=2
```

客户端部署在其他机器时，`RUNTIME_RULE_SERVER_URL` 使用服务端 IP+端口或 Nginx Proxy Manager 的 HTTPS 域名；`REDIS_HOST` 使用 server 侧 Redis 的可达 IP，不能填写客户端容器内的 `localhost`。

## 方案一：JAR 包 + Nginx

适合已有 Java 17、Nginx 或 systemd 的服务器。MySQL 和 Redis 可以是云服务、物理机或依赖 Compose。

### 1. 准备服务端配置

宿主机运行 JAR 和外接数据库 Compose 统一使用 `MYSQL_HOST`、`MYSQL_PORT`、`REDIS_HOST`、`REDIS_PORT`。完整 Compose 会在容器内自动使用 `mysql:3306` 和 `redis:6379`。将配置保存到仓库根目录 `.env`（或按生产路径复制到 JAR 工作目录）：

```dotenv
SERVER_PORT=8080
MYSQL_HOST=数据库地址
MYSQL_PORT=3306
MYSQL_DATABASE=rule_engine
MYSQL_USERNAME=rule_engine_app
MYSQL_PASSWORD=数据库账号密码
REDIS_HOST=Redis地址
REDIS_PORT=6379
REDIS_PASSWORD=Redis密码
SPRING_SQL_INIT_MODE=always
RULE_AUTH_MASTER_KEY=当前环境至少32位随机密钥
CONSOLE_USERNAME=admin
CONSOLE_PASSWORD=BCrypt哈希
CONSOLE_PASSWORD_ENCODING=BCRYPT
CORS_ALLOWED_ORIGIN_PATTERNS=https://console.example.com
```

### 2. 启动 server

```bash
# 在仓库根目录运行，确保根目录 .env 被 Spring Boot 读取
java -jar docker/runtime/server.jar
```

生产环境建议使用 systemd、Windows 服务或其他进程管理器。server readiness 返回 `UP` 后再接收流量：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health/liveness
curl -i http://127.0.0.1:8080/actuator/health/readiness
```

### 3. 可选启动 HTTP/SDK runtime

需要独立运行时接口时，在仓库根目录分别启动同一个 `runtime.jar` 的两个 profile：

```bash
java -Dspring.profiles.active=http -Dserver.port=7070 \
  -DRULE_SERVER_URL=http://127.0.0.1:8080 \
  -jar docker/runtime/runtime.jar
java -Dspring.profiles.active=sdk -Dserver.port=7071 \
  -DRULE_SERVER_URL=http://127.0.0.1:8080 \
  -jar docker/runtime/runtime.jar
```

两个 runtime 的项目凭证、规则白名单和访问令牌通过环境变量或 `.env` 注入；生产环境不要把示例令牌直接用于业务流量。

### 4. 启动前端

Nginx 的 `/api/` 反向代理到 `http://127.0.0.1:8080`，静态根目录指向 `docker/runtime/dist`。必须保留 Monaco 的 `dist/vs/` 目录，并配置 `/vs/css!vs/`、`/vs/nls!vs/` 重写；完整配置见 `docs/deployment.md` 的 Nginx 示例。

更新时先替换 JAR 或 `dist`，再重启 Java 服务或 reload Nginx。不要在运行中的 JAR 文件上直接覆盖写入。

## 方案二：独立 Docker 容器

此方案不使用 Compose。server、runtime 和 Nginx 分别由 `docker run` 管理，MySQL/Redis 使用外接地址或依赖 Compose。

### 1. 创建应用网络和目录

```bash
docker network create tianshu-app
mkdir -p /opt/tianshu/runtime /opt/tianshu/nginx
```

把 `docker/runtime/` 复制到 `/opt/tianshu/runtime/`，并把 server 配置保存为 `/opt/tianshu/runtime/.env`。容器内连接宿主机依赖时，Linux 需要使用 `host.docker.internal` 并通过 `--add-host` 映射；云数据库直接填写容器可访问的地址。

### 2. 启动 server 容器

```bash
docker run -d \
  --name tianshu-server \
  --restart unless-stopped \
  --network tianshu-app \
  --add-host=host.docker.internal:host-gateway \
  --env-file /opt/tianshu/runtime/.env \
  -p 8080:8080 \
  -v /opt/tianshu/runtime:/opt/tianshu/runtime:ro \
  eclipse-temurin:17-jre-jammy \
  java -jar /opt/tianshu/runtime/server.jar
```

运行时的 `RULE_SERVER_URL` 不使用 Compose 内部的 `server` 服务名。请填写客户端能够实际访问的服务端 IP+端口，例如 `http://192.168.1.10:8080`；跨机器部署时也可以填写 Nginx Proxy Manager 提供的 `https://api.example.com`。

### 3. 启动 HTTP/SDK runtime（可选）

```bash
docker run -d \
  --name tianshu-http \
  --restart unless-stopped \
  --network tianshu-app \
  --env-file /opt/tianshu/runtime/.env \
  -e SPRING_PROFILES_ACTIVE=http \
  -e SERVER_PORT=7070 \
  -e RULE_SERVER_URL=http://192.168.1.10:8080 \
  -p 7070:7070 \
  -v /opt/tianshu/runtime/runtime.jar:/app/app.jar:ro \
  eclipse-temurin:17-jre-jammy java -jar /app/app.jar
```

SDK runtime 使用同样的命令，把容器名、profile 和端口改为 `tianshu-sdk`、`sdk`、`7071`。

### 4. 启动 Nginx 容器

先把下面配置保存为 `/opt/tianshu/nginx/default.conf`。其中 `proxy_pass` 使用服务端实际可访问的 `http://192.168.1.10:8080`，并保留 Monaco 编辑器资源重写：

```nginx
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    location /api/ {
        proxy_pass http://192.168.1.10:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location ~ ^/vs/css!vs/(.*)$ {
        rewrite ^/vs/css!vs/(.*)$ /vs/$1 break;
        try_files $uri =404;
    }
    location ~ ^/vs/nls!vs/(.*\.js)$ {
        rewrite ^/vs/nls!vs/(.*\.js)$ /vs/$1 break;
        try_files $uri =404;
    }
    location ~ ^/vs/nls!vs/(.*)$ {
        rewrite ^/vs/nls!vs/(.*)$ /vs/$1.js break;
        try_files $uri =404;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

然后启动容器：

```bash
docker run -d \
  --name tianshu-web \
  --restart unless-stopped \
  --network tianshu-app \
  -p 9090:80 \
  -v /opt/tianshu/runtime/dist:/usr/share/nginx/html:ro \
  -v /opt/tianshu/nginx/default.conf:/etc/nginx/conf.d/default.conf:ro \
  nginx:1.29.1-alpine
```

停止或更新：

```bash
docker stop tianshu-web tianshu-http tianshu-server
docker rm tianshu-web tianshu-http tianshu-server
```

## 方案三：Docker Compose + 外接 MySQL/Redis

`docker-compose.yml` 只启动 server、HTTP runtime、SDK runtime 和 web；它不会创建 MySQL 或 Redis。数据库地址必须从容器内可达：

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
  -f docker/docker-compose.yml logs --tail 200 server runtime-http runtime-sdk web
```

停止：

```bash
docker compose --project-name tianshu-external --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.yml down
```

该方案不会删除外部数据库或 Redis 数据。首次部署时，JDBC 会尝试创建 `MYSQL_DATABASE`，因此账号需要建库权限；已有数据库或后续启动至少需要执行 `schema.sql` 所需的建表/变更权限。该方案不创建 Kafka，使用外部 Broker 时配置 `KAFKA_BOOTSTRAP_SERVERS` 和可访问的 `advertised.listeners`。

## Kafka 独立 Compose

`docker-compose.kafka.yml` 启动一个单节点 KRaft Kafka，宿主机端口默认为 `9092`，外部客户端使用 `KAFKA_ADVERTISED_HOST:KAFKA_HOST_PORT`，容器内应用使用 `kafka:19092`。启动前在 `.env` 配置 `KAFKA_ADVERTISED_HOST`、`KAFKA_USERNAME`、`KAFKA_PASSWORD`：

```bash
docker compose --project-name tianshu-kafka --env-file .env.server \
  -f docker/docker-compose.kafka.yml up -d
```

默认 Kafka 外部监听为 SASL/PLAINTEXT，适合受控调试网络；生产环境应通过 Compose override 配置 SASL_SSL/TLS。Kafka 是 TCP 服务，Nginx Proxy Manager 不代理 Kafka 端口，外部可达性由 Ubuntu 现有端口策略和安全组决定。

## 方案四：Docker Compose + 自带 MySQL/Redis/Kafka

`docker-compose.full.yml` 是完整单机方案，包含 MySQL、Redis、Kafka、server、HTTP、SDK 和 web，都在同一个 Compose 项目和网络内。server 默认启动 2 个副本；不要先启动 `docker-compose.mysql.yml` 或 `docker-compose.redis.yml`。

```bash
docker compose --project-name tianshu-full --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.full.yml up -d

# 显式指定 server 副本数时：
docker compose --project-name tianshu-full --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.full.yml up -d --scale server=2
docker compose --project-name tianshu-full --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.full.yml ps
docker compose --project-name tianshu-full --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.full.yml logs --tail 200 server http sdk web kafka
```

数据库和 Redis 数据分别持久化到 `docker/rule-engine-mysql/data`、`docker/rule-engine-redis/data`；停止 Compose 不会删除数据目录。停止：

```bash
docker compose --project-name tianshu-full --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.full.yml down
```

需要演示数据时，额外启用 `example-data` profile；该 profile 等待 schema 完成后只插入 `docker/rule-engine-mysql/data-tianshu-example.sql`：

```bash
docker compose --project-name tianshu-full --profile example-data \
  --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml up -d
```

已有数据目录不会被示例导入自动清空。生产环境不要启用 `example-data`，也不要把 MySQL 数据目录直接复制到另一套实例。

## 只启动依赖组件（可选）

这两个文件只用于给 JAR 或独立 Docker 方案提供依赖，不能代替应用启动：

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

Kafka 只服务于完整 Java SDK 或 `rule-engine-runtime` 的本地执行日志，以及 server 对 Kafka 日志的消费。HTTP-only runtime、服务端本地执行和规则同步仍不依赖 Kafka。full Compose 会提供一个单节点 broker；生产环境也可以通过 `KAFKA_BOOTSTRAP_SERVERS` 接入外部 Kafka 集群。

两个 Compose 方案使用 Kafka 时，server 配置写入 `.env.server`，SDK 配置写入 `.env.client`（示例项目 ID `1` 必须替换为实际值）：

```dotenv
KAFKA_BOOTSTRAP_SERVERS=kafka.internal:9092
SDK_LOG_REPORT_ENABLED=true
SDK_LOG_REPORT_TRANSPORT=KAFKA
SDK_KAFKA_LOG_TOPIC=rule-execution-log-risk
SDK_KAFKA_HTTP_FALLBACK_ENABLED=true
RUNTIME_RULE_PROJECT_ID=1
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

JAR 和独立 `docker run` 直接使用 Spring Boot 的 `SPRING_KAFKA_BOOTSTRAP_SERVERS`、`SPRING_APPLICATION_JSON` 或外部 YAML，不能直接使用上述两个 Compose JSON 别名。具体启动命令、TLS/SASL 配置、验收及关闭顺序见 [Kafka 部署接入](../docs/kafka-execution-logs.md#各部署方案的接入方式)。

## Ubuntu 与独立 Nginx Proxy Manager

本项目不会安装或修改 Ubuntu 的 UFW、Fail2ban、宿主机 Nginx、Certbot 或既有端口规则。先准备配置：

```bash
cp .env.server.example .env.server`ncp .env.client.example .env.client
vi .env
```

如果独立 NPM 通过服务器 IP 访问容器映射端口，应用端口需要绑定到可被 NPM 访问的地址：

```dotenv
APP_BIND_ADDRESS=0.0.0.0
SERVER_PUBLIC_BIND_ADDRESS=0.0.0.0
FRONTEND_PORT=9090
SERVER_PUBLIC_PORT=8080
RUNTIME_HTTP_PORT=7070
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
| HTTP runtime | `api.example.com` | `192.168.1.10` | `7070` |
| SDK runtime（确需公网访问时） | `sdk.example.com` | `192.168.1.10` | `7071` |

每个 Proxy Host 在 SSL 页面申请对应证书，并启用 `Force SSL`、HTTP/2 和 HSTS。Advanced 配置保留以下转发头：

```nginx
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header Host $host;
```

项目执行请求仍需携带 runtime 的 `Authorization: Bearer ...`。MySQL、Redis、Kafka 是 TCP 服务，不能通过 NPM 的普通 Proxy Host 代理；它们的访问范围由现有 Ubuntu 防火墙、云安全组和 Compose 端口绑定控制。Kafka 默认外部调试端口为 `9092`，生产环境应使用 SASL_SSL/TLS。

### 服务器重启自动拉起

完整 Compose 需要随 Ubuntu 启动时自动拉起时，可创建 `/etc/systemd/system/tianshu-compose.service`：

```ini
[Unit]
Description=Tianshu Decision Engine Docker Compose
Requires=docker.service
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/tianshu
ExecStart=/usr/bin/docker compose --project-name tianshu-full --env-file /opt/tianshu/.env.client --env-file /opt/tianshu/.env.server -f /opt/tianshu/docker/docker-compose.full.yml up -d --scale server=2
ExecStop=/usr/bin/docker compose --project-name tianshu-full --env-file /opt/tianshu/.env.client --env-file /opt/tianshu/.env.server -f /opt/tianshu/docker/docker-compose.full.yml stop
TimeoutStartSec=0
TimeoutStopSec=180

[Install]
WantedBy=multi-user.target
```

启用服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now tianshu-compose.service
sudo systemctl status tianshu-compose.service
```

## 常用验证和更新

```bash
curl -fsS http://127.0.0.1:8080/actuator/health/liveness
curl -i http://127.0.0.1:8080/actuator/health/readiness
curl -I http://127.0.0.1:9090/
```

`readiness` 返回 `UP` 后再接收业务流量。更新时重新运行 `node scripts/package-runtime.mjs`，再替换 `RUNTIME_DIR` 中的 JAR/dist；Compose 只需重启应用服务：

```bash
docker compose --project-name tianshu-external --env-file .env.client --env-file .env.server \
  -f docker/docker-compose.yml up -d --force-recreate server runtime-http runtime-sdk web
```

数据库和 Redis 的更新、备份、恢复由其所属环境负责；应用方案之间不要共享同一宿主机端口或同一个 Compose 项目名。
