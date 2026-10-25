# 天枢决策引擎部署与接入

本文介绍运行环境、基础设施配置、四种部署方式与业务系统接入。部署前先阅读本文开头的参数表；后面的每个部署模块都可以单独照着执行。

Docker 相关命令的单一入口是 [`docker/README.md`](../docker/README.md)，其中按 JAR、独立 Docker、外接数据库 Compose、自带数据库 Compose 分开说明了准备、启动、验证、停止和更新流程；本文保留参数、安全和业务接入细节。

## 管理员初始化与恢复

首次启动后访问控制台，会自动进入注册页面。设置超级管理员用户名、显示名称和密码即可完成初始化，不依赖 SMTP；无需在环境文件填写管理员账号或密码。初始化标记及账户保存于当前 MySQL 数据库，重启保留原账户和引擎内容。已有系统升级后不会重新开放首次初始化。

系统仅保留一个 `SUPER_ADMIN` 身份。历史重复超管在升级启动或签发恢复码时合并：优先保留初始化标记指向的有效账号，否则选择最早的有效超管；全部停用时保留标记账号，标记无效则保留最早的超管供恢复。主账号 ID 和授权不变，其余超管归档并撤销登录及令牌；原操作人名称保留，草稿和迁移记录归属由主账号继承。合并后账户列表不展示归档账号，不能重新启用或向其他账号分配超管角色；普通账号及其授权不受影响。

普通用户随后仍按邮箱注册和管理员授权流程使用系统。只使用 `.env.server`、`.env.client` 及对应 `.example` 模板，其他环境文件不参与配置。

若超管账号和密码都忘记，由具备服务器权限的运维生成一次性恢复码：

```bash
# JAR：在能读取 .env.server 的工作目录执行；按实际路径替换 server.jar
java -jar server.jar --console-admin-recovery-code

# 本仓库服务端 Compose：沿用原项目名和配置，只执行维护命令
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.server.yml exec -T server java -jar /app/app.jar --console-admin-recovery-code
```

维护命令使用服务端原数据库连接，不启动第二个 Web 服务。它先确认并合并历史重复超管，再签发账户恢复令牌和记录安全审计，不修改业务内容。输出的随机恢复码30分钟有效、只能使用一次，再次签发会撤销旧码。到登录页的“管理员应急恢复”中填写恢复码、新用户名和新密码，无需原用户名；可以使用已合并超管原来的登录名，恢复仍更新同一个主账号，不新增账号。

恢复保留原账户ID和授权，仅更改登录凭证并启用该账户，撤销旧会话与旧令牌。项目、规则、变量、模型、名单、版本及日志不删除；历史记录继续保留原操作人名称。恢复不修改 `RULE_AUTH_MASTER_KEY`，也不删除数据卷、清空数据库或重新导入样例数据。

## 一、部署参数总览

参数可以来自操作系统环境变量、JAR 启动参数或仓库根目录（JAR 从 `rule-engine-server` 目录启动时为上一级目录）的 `.env.server`。优先级为：命令行参数/操作系统环境变量 > `.env.server` > `application.yml` 默认值。生产环境请把密码和密钥放在 Secret/KMS 或容器编排的 Secret 中，不要提交到 Git。

### 1.1 服务、前端与数据库参数

| 参数名 | 默认值 | 含义与具体作用 |
|---|---:|---|
| `SERVER_PORT` | `8080` | Spring Boot 监听端口；容器内端口和负载均衡器后端端口应保持一致。 |
| `VITE_PORT` | `9090` | 仅用于 `npm run dev` 的 Vite 开发端口；不会改变生产 `dist` 静态站点端口。 |
| `VITE_DEV_PROXY` | 空 | Vite 开发服务器 `/api` 代理目标；设置后优先于 `SERVER_PORT` 推导出的后端地址。 |
| `MYSQL_HOST` | `localhost` | MySQL 主机名。后端在 Compose 网络中运行时填 `mysql`，不能填容器内的 `127.0.0.1`。 |
| `MYSQL_PORT` | `3306` | MySQL 端口。 |
| `MYSQL_DATABASE` | `rule_engine` | 后端连接的数据库名；JDBC 首次连接使用 `createDatabaseIfNotExist=true` 自动创建，完整 Compose 也按此值初始化；首次部署账号需要建库权限。 |
| `MYSQL_USERNAME` | 空 | 后端连接 MySQL 的应用账号；应只授予 `rule_engine` 所需权限。 |
| `MYSQL_PASSWORD` | 空 | 后端应用账号密码。 |
| `MYSQL_ROOT_PASSWORD` | 无 | 仅由 MySQL 容器初始化使用的 root 密码；不能替代 `MYSQL_PASSWORD`。 |
| `MYSQL_ALLOW_MULTI_QUERIES` | `false` | 是否在 JDBC URL 中开启多语句；只有确认 SQL 来源可信且确实需要时才开启。 |
| `REDIS_HOST` | `localhost` | Redis 主机名。多节点必须指向同一个 Redis 实例或同一高可用服务。 |
| `REDIS_PORT` | `6379` | Redis 端口。 |
| `REDIS_PASSWORD` | 空 | Redis 认证密码；所有 Docker Compose 运行模式都会强制要求非空。 |
| `SERVER_LOG_LEVEL` / `RUNTIME_LOG_LEVEL` | `WARN` | 容器 stdout 日志级别，可设为 `ERROR`、`WARN`、`INFO`、`DEBUG`；生产建议使用 `WARN`。 |
| `DOCKER_LOG_MAX_SIZE` / `DOCKER_LOG_MAX_FILES` | `10m` / `3` | Docker `json-file` 日志轮转上限；达到大小后自动轮转，避免 stdout 日志无限增长。 |
| `RUNTIME_TRACE_ENABLED` | `false` | SDK runtime 是否收集执行 trace；生产默认关闭，按请求仍可由业务侧显式开启时需评估日志量。 |

### 1.2 鉴权、控制台与安全参数

| 参数名 | 默认值 | 含义与具体作用 |
|---|---:|---|
| `RULE_AUTH_MASTER_KEY` | 无 | 项目长期凭证的活动加密主密钥，至少 32 个字符；缺失或使用公开示例值时服务拒绝启动。所有节点必须一致。 |
| （自动生成） | `auto-<master-key-hash>` | 服务根据 `RULE_AUTH_MASTER_KEY` 稳定生成密钥版本 ID；不需要配置环境变量。同一主密钥在多节点上会得到相同 ID，并保留 `v2` 作为历史密文兼容别名。 |
| `RULE_GLOBAL_ACCESS_ENABLED` | `true`（示例默认） | 是否启用 Ed25519 全局规则访问；启用后必须同时配置公钥、Key ID 和时间窗。关闭后客户端改用项目级凭据。 |
| `RULE_GLOBAL_ACCESS_KEY_ID` | `default` | 全局访问公钥的 Key ID，客户端必须使用相同 ID。 |
| `RULE_GLOBAL_ACCESS_PUBLIC_KEY` | 空 | Ed25519 X.509 公钥的 Base64 值；私钥只放在 业务服务或 Java SDK。 |
| `RULE_GLOBAL_ACCESS_TIME_WINDOW_SECONDS` | `300` | 全局签名允许的时间偏差，服务端同时按 nonce 防重放。 |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | `*` | 允许访问后端的控制台来源，多个来源用英文逗号分隔；模板默认允许所有来源，生产建议改为实际 HTTPS 域名/IP。 |
| 会话 Cookie Secure | 按请求协议自动设置 | HTTPS 请求自动带 `Secure`，HTTP 请求不带；域名 HTTPS 和 IP+端口 HTTP 不需要两套配置。 |

### 1.3 初始化、追踪与资源解析参数

| 参数名 | 默认值 | 含义与具体作用 |
|---|---:|---|
| `SPRING_SQL_INIT_MODE` | `always` | Spring 启动时是否执行 `schema.sql`；默认每次启动幂等执行结构、视图和系统常量补齐，已有数据库可临时设为 
ever`。 |
| `RULE_ENGINE_INITIAL_DATA_ENABLED` | `false` | 可选：设为 `true` 后，server 每次启动按编码执行 `sql/initial-data.sql`，补齐内置变量、数据对象及字段，不覆盖已有修改。 |
| `RULE_ENGINE_INITIAL_DATA_LOCATION` | `classpath:sql/initial-data.sql` | 可选：替换初始配置脚本位置；自定义脚本也必须按编码幂等插入。 |
| `SPRING_SQL_INIT_CONTINUE_ON_ERROR` | `false` | 初始化 SQL 出错时是否继续；生产保持 `false`，让错误阻止启动。 |
| `RULE_TRACE_MAX_PERSIST_BYTES` | `0` | 单条持久化追踪 JSON 的大小上限，单位是字节（Bytes）；`0` 表示不限制，设置为正数时只截断持久化副本，不截断本次请求结果。 |
| `SHUTDOWN_TIMEOUT` | `120s` | 容器停止时，Spring 每个生命周期阶段等待正在处理的请求、队列和资源释放的最长时间；可设 `30s`-`300s`。 |
| `RULE_TRACE_MASK_PATHS` | 空 | 持久化追踪副本的 JSONPath 脱敏列表，逗号分隔，例如 `$.input.idCard,$.items[*].phone`。 |
| `SOURCE_RESOLUTION_PARALLELISM` | `4` | API、数据库、名单等外部变量并行解析线程数；按外部依赖容量调整。 |

Compose 还会使用 `MYSQL_PUBLIC_PORT`、`REDIS_PUBLIC_PORT`、`CONSOLE_PUBLIC_PORT` 这类“宿主机映射参数”（如果在自定义 Compose 文件中定义），它们只改变宿主机暴露端口，不会改变应用在容器网络中的端口。Docker 的 `-p` 左侧同样只是宿主机端口。

### 1.4 四种独立部署方案

完整的 Docker 命令、停止方式、端口、数据目录和更新流程以 [`docker/README.md`](../docker/README.md) 为准。四种方案可以分别部署，不要求同时运行：

| 方案 | 启动内容 | 数据库/Redis | 入口 |
|---|---|---|---|
| JAR 包 | 宿主机 Java + Nginx | 外接服务或依赖 Compose | [JAR 包 + Nginx](#四后管静态文件--jar-包部署) |
| Docker | `docker run` 启动 server、runtime、Nginx | 外接服务或依赖 Compose | [Docker 运行时](#二docker-运行时部署) |
| 外接数据库 Compose | server、SDK runtime、web | 外接 MySQL/Redis | [外接数据库 Compose](../docker/README.md#方案三docker-compose--外接-mysqlredis) |
| 自带数据库 Compose | MySQL、Redis、server、SDK、web | Compose 内置 | [完整 Compose](../docker/README.md#方案四docker-compose--自带-mysqlredis) |

`docker-compose.mysql.yml` 和 `docker-compose.redis.yml` 只是可选依赖组件，适合给 JAR 或独立 Docker 提供 MySQL/Redis；它们不启动应用。不同环境并行运行时设置不同 `DEPLOY_PROJECT` 和宿主机端口，不要让 full Compose 与依赖组件 Compose 共用一套容器名、网络或端口。

**Docker 更新约定：**后管 `dist` 和服务端 JAR 都通过宿主机目录挂载到容器内，不把它们写死在运行时镜像中。更新时先替换容器外的 `dist` 或 JAR，再对后管执行 Nginx reload、对 JAR 容器执行 restart；这样可以在不重建镜像的情况下发布和回滚。

以上模块都依赖可访问的 MySQL 8 和 Redis；如果没有现成依赖，请选择第 0 步的独立依赖 Compose 或第 1.4 节的完整单机 Compose。第 1.3 节应用 Compose 只连接外部数据库，不会创建依赖容器。

### 1.5 可选 Kafka 执行日志

系统默认通过 HTTP 上报客户端执行日志；需要 Kafka 日志链路时，可使用部署包中的单节点 KRaft Kafka Compose，或接入外部 Kafka 集群，并配置生产者、服务端消费者、项目主题绑定和 TLS/SASL/ACL。Kafka 的独立 Compose 使用 `KAFKA_ADVERTISED_HOST`、`KAFKA_USERNAME`、`KAFKA_PASSWORD`，full Compose 会同时启动一个 broker。

完整配置、主题隔离、失败补报、重试、死信和迁移说明见 [`docs/kafka-execution-logs.md`](kafka-execution-logs.md)。部署时至少确认：

- Kafka broker 及其公布的地址从应用侧可访问。Compose 的 `KAFKA_BOOTSTRAP_SERVERS` 映射到 server、SDK 容器的 `SPRING_KAFKA_BOOTSTRAP_SERVERS`；直接运行 JAR 或 `docker run` 使用后者或外部 YAML。
- SDK 只有显式设置 `SDK_LOG_REPORT_TRANSPORT=KAFKA` 才会发送 Kafka；HTTP 补报需要保留有效项目鉴权。
- 服务端只有显式设置 `RULE_KAFKA_ENABLED=true` 才会消费 Kafka。Compose 使用 `KAFKA_SERVER_APPLICATION_JSON` 绑定主题到真实 `project_id`；JAR 和 `docker run` 使用 `SPRING_APPLICATION_JSON` 或外部 YAML。SDK 的项目 ID 必须与绑定一致。
- 主主题、对应的 `<topic>.DLT`、生产者 ACL、消费者组 ACL 和死信写入权限须由 Kafka 管理员提前准备；不能只依赖消息头中的项目编号。

## 环境要求

- JDK 17（Maven 构建会校验 Java 版本，低于 17 将直接失败）
- Maven 3.6+
- MySQL 8
- Redis
- Node.js 20.19+，不设置最高版本；建议使用当前维护中的 Node.js LTS，已验证 Node.js 26.4.0
- 可选 NVIDIA GPU：默认构建使用可移植的 ONNX Runtime CPU 包，不要求 NVIDIA 驱动、CUDA 或 cuDNN；只有使用 `-Ponnx-gpu` 构建 CUDA 版后端时，才需安装与项目 ONNX Runtime GPU 版本兼容的 NVIDIA 驱动、CUDA 和 cuDNN，并确保其动态库在服务进程的 `PATH`/`LD_LIBRARY_PATH` 中。

## 启动与配置

### 数据库与基础设施

```bash
cp .env.server.example .env.server
cp .env.client.example .env.client
# 编辑 .env.server，替换全部 replace-with-* 占位值
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.mysql.yml up -d
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.redis.yml up -d
# 选择外部数据库应用模式时单独执行：
# docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.yml up -d
# 选择完整单机模式时改为一条命令：
# docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml up -d
```

PowerShell 可使用 `Copy-Item .env.server.example .env.server
Copy-Item .env.client.example .env.client`。Compose 不再提供 MySQL/Redis 共享默认密码；全新数据卷会根据 `MYSQL_USERNAME`、`MYSQL_PASSWORD` 创建应用账号。已有数据卷升级时，需由数据库管理员按最小权限原则预先创建或更新该账号。

#### 复用已经运行的 Compose 容器

如果 MySQL 和 Redis 已由 Docker Compose 启动，不需要再次创建容器。确认端口和健康状态：

```powershell
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.mysql.yml ps
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.redis.yml ps
```

服务端在宿主机上用 Maven 启动时，将 `.env.server` 设置为：

```dotenv
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
```

如果服务端也运行在同一个 Compose 网络内，使用 Compose 服务名：

```dotenv
MYSQL_HOST=mysql
MYSQL_PORT=3306
REDIS_HOST=redis
REDIS_PORT=6379
```

此时只启动后端即可：

```powershell
cd rule-engine-server
mvn spring-boot:run
```

`MYSQL_HOST`、`MYSQL_PORT` 由 `application.yml` 读取；Redis 已通过 `REDIS_HOST`、`REDIS_PORT` 读取。不要在 `.env.server` 中写入 Spring 风格的 `${NAME:default}` 表达式，因为 Docker Compose 会把它解析为非法模板；`.env.server` 中应使用已经展开的实际值。

`schema.sql` 包含数据库表结构、逻辑执行日志视图和按变量唯一索引幂等补齐的系统常量，不创建用户、不修改 root 账号、也不执行全局授权；服务端启动时默认由 Spring 执行 classpath 中的 `schema.sql`。示例数据脚本 `data-tianshu-example.sql` 放在 `docker/rule-engine-mysql/`，只在需要演示数据时手工导入，不随服务端启动自动导入。项目鉴权、临时 Token 及其访问审计数据与部署主密钥绑定，不写入示例数据；服务启动后会把项目表中的兼容访问令牌按当前主密钥迁移为默认鉴权记录。

需要手工准备演示环境时，固定顺序为：启动服务自动执行 `schema.sql`，再按需执行 `docker/rule-engine-mysql/data-tianshu-example.sql`。示例数据脚本会写入演示数据，因此不得直接用于需要保留现有业务数据的数据库。

### 后端

Windows 本地开发可在仓库根目录执行以下命令，同时启动前后端：

```powershell
.\start-dev.cmd
```

需先配置根目录 `.env.server`，并保持 MySQL、Redis 运行，确保 Java、Maven、Node.js 可从 `PATH` 调用。命令会编译后端依赖、在缺少前端依赖时执行 `npm ci`，后台启动服务并等待健康检查及前端 API 代理检查通过；已就绪的服务会直接复用。端口沿用 `.env.server` 的 `SERVER_PORT` 和 `VITE_PORT`，日志及本次启动的进程 ID 保存在 `.codex-run-logs/`。命令只启动前后端，不会重启 MySQL、Redis；检测到 WSL 与项目 Docker 服务的本机端口冲突时，仅为本次后端进程调整连接地址。

Windows 本机如果 JDK 默认 Unix Domain Socket 临时目录导致 Redis/Lettuce 健康检查出现 `Unable to establish loopback connection`，先为本次进程指定工作区目录，再启动后端：

```powershell
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=E:/workspace/tianshu-run-logs'
cd rule-engine-server
mvn spring-boot:run
```

这是本机 JDK 临时目录兼容性配置，不应替代生产 Redis、网络和 readiness 检查。

```bash
mvn clean install -DskipTests
cd rule-engine-server
mvn spring-boot:run
```

以上命令构建并启动 CPU 版，适用于没有 NVIDIA GPU/CUDA 的机器。需要 CUDA 推理时，构建和启动必须使用同一个 Maven Profile：

```bash
mvn clean install -Ponnx-gpu -DskipTests
cd rule-engine-server
mvn spring-boot:run -Ponnx-gpu
```

GPU 构建完成后，可在仓库根目录执行真实 CUDA 诊断。诊断直接使用启动进程的 `PATH`，不需要额外传入 CUDA/cuDNN 目录：

```powershell
mvn "-Ponnx-gpu" "-Dtianshu.cuda.diagnostic=true" "-Dtest=CudaEnvironmentDiagnosticTest" "-Dsurefire.failIfNoSpecifiedTests=false" -pl rule-engine-server -am test
```

诊断通过后使用同一 `onnx-gpu` Profile 启动服务，并在模型管理页面将目标 ONNX 模型的执行设备显式设置为“GPU（CUDA）”。仅启用 GPU Profile 不会自动修改已有模型的 CPU/CUDA 配置。

后端启动时会将当前目录的 `.env.server` 和上级目录的 `.env.server` 作为可选配置源读取：在仓库根目录运行 JAR 时读取 `./.env.server`，在 `rule-engine-server` 目录运行 `mvn spring-boot:run` 时读取 `../.env.server`。操作系统环境变量和命令行参数的优先级高于 `.env.server`，因此生产环境仍应通过 Secret/KMS 注入真实凭据；缺少必填密钥时，现有安全校验仍会拒绝启动。关键配置如下：

| 配置 | 要求 |
|------|------|
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 必填；生产使用仅具备 `rule_engine` 所需权限的独立账号 |
| `REDIS_PASSWORD` | 必填；与 server 和 SDK 实际连接的 Redis 实例一致 |
| `RULE_AUTH_MASTER_KEY` | 必填；至少 32 位的私有随机密钥，生产通过 Secret/KMS 注入 |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | 模板默认 `*`；生产建议配置精确 HTTPS 域名/IP来源 |
| 会话 Cookie | 按请求协议自动设置 Secure；通过 Nginx Proxy Manager 的 HTTPS 域名和 HTTP IP+端口均可登录 |


ONNX 神经网络模型可在“模型管理”中逐个选择 CPU 或 CUDA，并配置 GPU 设备号、显存上限、显存扩展策略、cuDNN 卷积算法搜索和默认 CUDA 流。CPU 版后端即使读取到历史 CUDA 配置也会自动回退 CPU，不影响服务启动；CUDA 版后端会实际检查 CUDA 共享库及其依赖是否可加载。服务按“模型文件内容 + 运行配置”缓存推理会话；开启“启动预加载”后会在服务启动阶段创建对应 CPU/CUDA 会话。配置 CUDA 的模型在 GPU 会话初始化或推理失败时会自动重试 CPU，CPU 成功后同一服务进程内后续调用会直接使用 CPU；修复 GPU 环境后需重启服务以重新尝试 CUDA。YuNet 人脸检测同样通过 ONNX Runtime 执行，OpenCV 仅保留图片解码、缩放和检测结果后处理。

模型文件、测试参数和执行并发受服务端上限保护：`RULE_MODEL_MAX_FILE_SIZE_BYTES` 默认 256 MiB，`RULE_MODEL_MAX_TEST_PARAMS_BYTES` 默认 1 MiB，`RULE_MODEL_EXECUTION_PARALLELISM` 默认 8，`RULE_MODEL_EXECUTION_QUEUE_CAPACITY` 默认 256，`RULE_MODEL_MAX_CONCURRENT_PER_MODEL` 默认 8。队列或单模型并发达到上限时返回 503/`THROTTLED`，可通过模型运行时能力接口观察活动数、队列深度和完成数。

模型运行时当前使用 JPMML Evaluator Metro 1.7.7 和 ONNX Runtime 1.26.0。PMML 输入字段按模型声明精确匹配，不自动转换大小写、驼峰或下划线；PMML/ONNX 缓存键均包含模型内容或运行配置，替换文件后不会继续复用旧模型。本项目已选择以 AGPL-3.0 开源方式交付 JPMML：凡对外提供包含 JPMML 的制品或网络服务，都必须同步提供与运行版本一致的完整 Corresponding Source、许可证和重建材料，详见 [THIRD_PARTY_LICENSES.md](../THIRD_PARTY_LICENSES.md)。

### 前端

```bash
cd rule-engine-web
npm ci
npm run dev
```

开发访问地址为 `http://localhost:9090/`，`/api` 会代理到 `http://localhost:8080`。

在仓库根目录 `.env.server` 中设置以下配置，可统一修改后端和前端开发端口：

```dotenv
SERVER_PORT=8081
VITE_PORT=9091
```

修改后重启后端和 `npm run dev`，访问 `http://localhost:9091/`，`/api` 会自动代理到 `http://localhost:8081`。未配置时仍使用后端 `8080`、前端 `9090`；前端端口被占用时会直接报错，不会自动切换端口。

后端运行在其他地址时，可额外设置 `VITE_DEV_PROXY=http://后端地址:端口`，该配置优先于 `SERVER_PORT` 推导的地址。系统环境变量优先于 `.env.server`。这些配置不会改变生产静态站点的监听端口，生产前端仍需配置 Nginx 或容器端口映射。

前端生产静态服务器需要保留 `dist/vs/` 下的 Monaco 资源，并将 AMD loader 生成的资源标识 URL 重写后再读取静态文件：`/vs/css!vs/<path>` 映射到 `/vs/<path>`，`/vs/nls!vs/<path>` 映射到 `/vs/<path>.js`（已有 `.js` 后缀时不重复添加）。若静态服务器不支持此重写，规则设计器、SQL 和 JSON 编辑器会出现资源 404，页面控制台会报 Monaco 加载错误。

未设置 `CORS_ALLOWED_ORIGIN_PATTERNS` 时，后端默认允许所有来源（`*`）；生产环境建议显式填写实际 HTTPS 域名/IP，多个来源用英文逗号分隔。会话 Cookie 会根据请求协议自动决定是否附加 `Secure`，不需要额外配置。


## 二、Docker 运行时部署

本模块不要求项目提供 Dockerfile，直接使用 `eclipse-temurin:17-jre` 运行 JAR、使用 
ginx:1.27-alpine` 提供后管静态文件。MySQL 和 Redis 可以是云服务、物理机服务或已经运行的容器；只要从应用容器内可访问即可。

### 2.1 准备宿主机目录和制品

以下命令在仓库根目录执行。生产环境请把 `/opt/tianshu` 换成实际发布目录，并让运行 Docker 的账号对该目录有读权限。

```bash
mkdir -p /opt/tianshu/server /opt/tianshu/console/dist /opt/tianshu/console

# 构建 JAR；默认跳过测试，正式发布前应在 CI 先完成完整测试
mvn clean package -DskipTests
cp rule-engine-server/target/rule-engine-server-*.jar /opt/tianshu/server/rule-engine-server.jar

# 构建后管
cd rule-engine-web
npm ci
npm run build
rm -rf /opt/tianshu/console/dist/*
cp -a dist/. /opt/tianshu/console/dist/
cd ..
```

在 `/opt/tianshu/server/.env.server` 写入服务端参数。`MYSQL_HOST`、`REDIS_HOST` 必须填写应用容器能够解析和访问的地址；如果依赖也在 Docker 中，请使用同一网络里的服务名，不要写 `localhost`。

```dotenv
SERVER_PORT=8080
MYSQL_HOST=10.0.0.20
MYSQL_PORT=3306
MYSQL_DATABASE=rule_engine
MYSQL_USERNAME=rule_engine_app
MYSQL_PASSWORD=替换为应用账号密码
REDIS_HOST=10.0.0.21
REDIS_PORT=6379
REDIS_PASSWORD=替换为Redis密码
RULE_AUTH_MASTER_KEY=至少32位的当前环境随机密钥
CORS_ALLOWED_ORIGIN_PATTERNS=*
```

### 2.2 启动 JAR 容器

挂载目录而不是单个 JAR 文件。这样替换宿主机目录中的 JAR 后，重启容器会重新打开新文件，避免文件替换后仍绑定旧 inode。

```bash
docker run -d \
  --name tianshu-server \
  --restart unless-stopped \
  --env-file /opt/tianshu/server/.env.server \
  -p 8080:8080 \
  -v /opt/tianshu/server:/opt/tianshu/server:ro \
  eclipse-temurin:17-jre \
  java -jar /opt/tianshu/server/rule-engine-server.jar
```

如果 MySQL/Redis 也是 Docker 容器，可先创建网络并在同一网络启动依赖，再把 `.env.server` 中的主机名改为容器名；不要把宿主机 `3306`/`6379` 映射端口误当成容器网络地址。

### 2.3 启动后管容器

后管需要把 `/api` 反向代理到 JAR 容器，并保留 Monaco 的 `vs` 资源重写。先创建 `/opt/tianshu/console/nginx.conf`：

``
ginx
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    location /api/ {
        proxy_pass http://host.docker.internal:8080;
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

上例假设 JAR 容器通过宿主机 `8080` 端口访问。Linux Docker 默认不一定提供 `host.docker.internal`，启动后管容器时补充宿主机网关映射：

```bash
docker run -d \
  --name tianshu-console \
  --restart unless-stopped \
  --add-host=host.docker.internal:host-gateway \
  -p 9090:80 \
  -v /opt/tianshu/console/dist:/usr/share/nginx/html:ro \
  -v /opt/tianshu/console/nginx.conf:/etc/nginx/conf.d/default.conf:ro \
  nginx:1.27-alpine
```

### 2.4 验证与更新

```bash
curl -fsS http://127.0.0.1:8080/actuator/health/liveness
curl -i http://127.0.0.1:8080/actuator/health/readiness
curl -I http://127.0.0.1:9090/
docker logs --tail 200 tianshu-server
```

后端必须等待 `readiness` 返回 `UP` 后再接收业务流量；它会检查数据库、Redis、ONNX 和已发布规则预热状态。发布更新时，在宿主机替换 `/opt/tianshu/server/rule-engine-server.jar`，然后执行 `docker restart tianshu-server`；不要重新构建运行时镜像。后管更新时把新 `dist` 内容同步到 `/opt/tianshu/console/dist/`，再执行 `docker exec tianshu-console nginx -s reload`。文件同步完成前不要删除旧目录，避免用户拿到不完整的静态资源。

## 三、Docker Compose 一体化部署

部署制品统一放在 docker/runtime：server.jar、runtime.jar 和前端 dist/。MySQL、Redis 配置、数据、日志和示例数据脚本分别放在 docker/rule-engine-mysql/、docker/rule-engine-redis/；子目录不再保留重复的 Compose。外部数据库 Compose 只启动应用，完整 Compose 启动全部服务，schema.sql 由 server 启动时自动执行。

运行时的 RULE_SERVER_URL 必须填写客户端实际可访问的服务端地址：同机或内网使用服务端 IP+SERVER_PUBLIC_PORT，跨机器可使用 Nginx Proxy Manager 配置的 HTTPS 域名或服务端 IP+端口。Compose 不再把 Docker 内部的 server:8080 作为隐式默认值。

```text
docker/
├── docker-compose.yml
├── docker-compose.mysql.yml
├── docker-compose.redis.yml
├── docker-compose.full.yml
├── rule-engine-mysql/                 # conf.d/、data/、logs/
├── rule-engine-redis/                 # redis.conf、data/、logs/
└── runtime/   # server.jar、runtime.jar、dist/
```

以下命令从仓库根目录执行，`.env.server` 仍保留在仓库根目录。Compose 的相对挂载路径以 `docker/` 为基准，因此 `RUNTIME_DIR` 默认填 `./runtime`，也可改为宿主机绝对路径。

### 3.1 启动方式

四种方式彼此独立，按部署环境选择一种即可：

**0. 只启动 MySQL/Redis。** 可以使用本项目提供的独立 Compose，也可以使用已有远程实例。独立容器默认发布到宿主机全部网卡，使用 `MYSQL_HOST_PORT`、`REDIS_HOST_PORT` 调整端口，防火墙只放行可信网段；应用连接远程实例时把 `MYSQL_HOST`、`REDIS_HOST` 改成容器可以访问的域名或 IP。

```powershell
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.mysql.yml up -d
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.redis.yml up -d
```

**1.1 源码启动 server + web + SDK。** 先完成第 0 步，再在一个终端运行统一启动脚本：

```powershell
node scripts/start-source.mjs
```

脚本会先执行 Maven 编译，再启动 server、SDK profile 和 Vite web；按 Ctrl+C 会停止本次启动的全部进程。SDK 的项目凭证、规则白名单、Redis 地址和运行时令牌从 `.env.client` 或系统环境变量读取；源码启动时默认使用 `http://127.0.0.1:8080`，不要把 Compose 服务名 `server` 带到宿主机进程。

**1.2 JAR + Nginx。** 编译后将server、SDK runtime 和前端分别启动；SDK 使用 7071 端口和 sdk profile，前端由 Nginx 指向 `dist/`。

```powershell
mvn clean package -DskipTests
cd rule-engine-web; npm ci; npm run build
Copy-Item rule-engine-server/target/rule-engine-server-*.jar docker/runtime/server.jar
Copy-Item rule-engine-runtime/target/rule-engine-runtime-*.jar docker/runtime/runtime.jar
New-Item -ItemType Directory -Force docker/runtime/dist | Out-Null
Copy-Item rule-engine-web/dist/* docker/runtime/dist -Recurse -Force
# 分别在两个终端执行：
java -jar docker/runtime/server.jar
java -Dspring.profiles.active=sdk -Dserver.port=7071 -DRULE_SERVER_URL=http://127.0.0.1:8080 -jar docker/runtime/runtime.jar
```

Nginx 的 `root` 指向 `rule-engine-web/dist`，`/api/` 反向代理到 `http://127.0.0.1:8080`；JAR 更新采用临时文件原子替换后逐个重启，避免读取半写入文件。

**1.3 Docker 应用 + 外部 MySQL/Redis。** 只需要准备 JAR/dist、`.env.server` 和 `.env.client`，将 `MYSQL_HOST`、`REDIS_HOST` 配置为远程地址；本文件不创建或依赖 MySQL/Redis Compose 服务。

```powershell
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.yml up -d
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.yml ps
```

**1.4 Docker 全量单机。** 这个文件自包含 MySQL、Redis、server、SDK 和 web，一条命令即可启动，不需要先启动另外三个 Compose 文件。

```powershell
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml up -d
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml ps
```

完整模式启动 MySQL 和 Redis 后直接启动 server；server 默认自动执行 `schema.sql`。需要演示数据时，默认不导入；启动时增加 `--profile example-data` 会等待 schema 完成后只执行插入。完整模式与第 0 步的独立 MySQL/Redis 不要同时占用相同端口和容器名。

所有 Compose 文件都设置了固定容器资源上限。完整模式按 2 个 server 副本计算，MySQL/Redis/Kafka/server/SDK/web 合计约 6.88G 内存、1.85C CPU；启用 `example-data` profile 时额外增加 256M/0.05C。外部数据库应用模式按 2 个 server 副本合计约 4.00G/1.10C；再同时启动独立 MySQL/Redis/Kafka 时仍按 8G/2C 预算。

Ubuntu 公网部署使用 Nginx Proxy Manager 管理 HTTPS、证书和反向代理；本项目不安装或修改 UFW、Fail2ban、宿主机 Nginx 及现有端口规则。Compose 中 server/web/runtime 默认绑定本机，MySQL、Redis、Kafka 可通过 `.env.server` 绑定宿主机端口供远程调试，访问控制由现有安全组/防火墙策略负责。full Compose 提供单节点 Kafka，外接 Compose/JAR 使用外部 Broker 的 `KAFKA_BOOTSTRAP_SERVERS` 和可访问的 `advertised.listeners`。控制台账号密码在首次注册页面设置并以 BCrypt 摘要保存到数据库，直接执行使用项目调用凭据；SDK runtime 使用至少 32 位的独立接入令牌；NPM 应配置 HTTPS、访问控制和请求限流。

### 3.2 构建制品

```powershell
node scripts/package-runtime.mjs
```

脚本会构建后端和前端，并清理后重新生成 `docker/runtime`。复制到服务器时至少带上 `docker/docker-compose*.yml`、该运行时目录、`docker/rule-engine-mysql/data-tianshu-example.sql` 和填写完成的 `.env.server`；server 会从 JAR 内读取 `schema.sql`，不再依赖源码目录。

默认启动不导入示例数据；需要导入时使用：

```powershell
docker compose --profile example-data --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml up -d
```

该导入脚本在最前面设置 `utf8mb4` 编码，只使用 `INSERT IGNORE`，不会删除数据、更新已有记录或重建表。
如果只启动独立 MySQL Compose，则在 server 已完成 schema 初始化后使用同一 profile 命令，并将文件替换为 `docker/docker-compose.mysql.yml`。

### 3.3 启动与更新

```powershell
Copy-Item .env.server.example .env.server
Copy-Item .env.client.example .env.client
# 编辑 .env.server，替换全部 replace-with-* 占位值和数据库密码
# 选择 1.3 或 1.4 其中一种，不要同时启动
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.yml up -d
# 或：docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml up -d
```

MySQL 数据目录为 docker/rule-engine-mysql/data，Redis 数据目录为 docker/rule-engine-redis/data；均通过宿主机目录挂载持久化，停止或删除容器不会删除这些文件。外部数据库模式要求服务端账号具备执行 `schema.sql` 的建表权限。数据与日志目录已排除在 Git 和镜像构建上下文之外。

迁移已有部署时保留原 DEPLOY_PROJECT；若需沿用旧 Docker 网络，在 .env.server 设置 ENGINE_NETWORK 为原网络名。移动数据前应停止对应容器，完成停机备份和文件校验后再启动；不要对仍在写入的数据目录直接复制或移动。MySQL/Redis 的监听端口仍由 MYSQL_HOST_PORT、REDIS_HOST_PORT 配置。

使用生产托管数据库时，把 MYSQL_HOST、MYSQL_PORT、REDIS_HOST、REDIS_PORT 改为生产地址，并确保服务端账号具备执行 `schema.sql` 的建表权限；如需跳过启动建表，可设置 `SPRING_SQL_INIT_MODE=never`。应用账号仍通过 MYSQL_USERNAME / MYSQL_PASSWORD 配置，Redis 密码通过 REDIS_PASSWORD 配置。

默认入口为前端 http://localhost:9090、Server API http://localhost:8080、SDK 运行时 http://localhost:7071。Docker Compose 不构建镜像，也不挂载源码，升级时替换运行目录中的 JAR/dist 后执行：

```powershell
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.yml up -d
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.yml ps
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.yml logs --tail 200 server runtime-sdk
```

全量模式对应命令为：

```powershell
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml up -d
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml logs --tail 200 server http sdk web
```

停止应用与基础设施分别执行：

```powershell
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.yml down
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml down
```

## 四、后管静态文件 + JAR 包部署

本模块不运行应用容器，适合已有 Nginx、systemd 或进程管理器的服务器。MySQL 和 Redis 可以继续使用 Docker Compose：

```bash
cp .env.server.example .env.server
cp .env.client.example .env.client
# 将 MYSQL_HOST、REDIS_HOST 保持为 127.0.0.1/localhost（服务在宿主机运行）
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.mysql.yml up -d
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.redis.yml up -d
```

### 4.1 构建并发布后管

```bash
cd rule-engine-web
npm ci
npm run build
sudo mkdir -p /var/www/tianshu
sudo rsync -a --delete dist/ /var/www/tianshu/
cd ..
```

Nginx 的 `server` 配置使用 [Docker 模块中的配置](#23-启动后管容器)，但把 `proxy_pass` 改为 `http://127.0.0.1:8080`，`root` 改为 `/var/www/tianshu`。配置检查并 reload：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

### 4.2 构建并启动 JAR

```bash
mvn clean package -DskipTests
sudo mkdir -p /opt/tianshu/server
sudo cp rule-engine-server/target/rule-engine-server-*.jar /opt/tianshu/server/rule-engine-server.jar
sudo cp .env.server /opt/tianshu/server/.env.server
cd /opt/tianshu/server
# application.yml 会从当前目录自动读取 .env.server；不需要在 shell 中 source，避免密钥中的 $ 被 shell 展开
java -jar ./rule-engine-server.jar
```

生产建议使用 systemd，创建 `/etc/systemd/system/tianshu-server.service`：

```ini
[Unit]
Description=Tianshu Rule Engine Server
After=network-online.target
Wants=network-online.target

[Service]
WorkingDirectory=/opt/tianshu/server
EnvironmentFile=/opt/tianshu/server/.env.server
ExecStart=/usr/bin/java -jar /opt/tianshu/server/rule-engine-server.jar
Restart=always
RestartSec=5
User=tianshu

[Install]
WantedBy=multi-user.target
```

如果系统还没有 `tianshu` 用户，先创建并授权运行目录：

```bash
sudo useradd --system --home /opt/tianshu --shell /usr/sbin/nologin tianshu 2>/dev/null || true
sudo chown -R tianshu:tianshu /opt/tianshu/server
```

启用并验证：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now tianshu-server
curl -fsS http://127.0.0.1:8080/actuator/health/liveness
curl -i http://127.0.0.1:8080/actuator/health/readiness
```

更新 JAR 时先复制到临时文件，再原子替换并重启：

```bash
install -m 0644 rule-engine-server/target/rule-engine-server-*.jar /opt/tianshu/server/rule-engine-server.jar.new
mv /opt/tianshu/server/rule-engine-server.jar.new /opt/tianshu/server/rule-engine-server.jar
sudo systemctl restart tianshu-server
```

确认 readiness 为 `UP` 后再恢复流量；如果失败，查看 `journalctl -u tianshu-server -n 200` 并用上一份 JAR 回滚。

## 五、多节点横向扩容

多节点扩容复制的是无状态的 JAR 执行节点，数据和分布式状态不能各自保存一份：所有节点必须连接同一个 MySQL 数据库和同一个 Redis 实例（或同一高可用 Redis 服务），并使用完全相同的 `RULE_AUTH_MASTER_KEY`、控制台账号和 CORS 配置。服务会根据主密钥自动生成相同的鉴权版本 ID。每个节点可以使用自己的 JAR 挂载目录和日志目录，但不要为每个节点创建独立的规则数据库或 Redis。

### 5.1 负载均衡器配置

以 Nginx 为例，后端节点监听内网地址 `10.0.0.31:8080`、`10.0.0.32:8080`：

``
ginx
upstream tianshu_server {
    # 管理端 HttpSession 需要粘性；这是 Nginx 开源版可直接使用的简单方案
    ip_hash;
    server 10.0.0.31:8080 max_fails=3 fail_timeout=10s;
    server 10.0.0.32:8080 max_fails=3 fail_timeout=10s;
}

server {
    listen 443 ssl;
    server_name console.example.com;
    # ssl_certificate /...;
    # ssl_certificate_key /...;

    location /api/ {
        proxy_pass http://tianshu_server;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

当前控制台登录使用节点本地 `HttpSession`，没有配置 Spring Session Redis。因此管理端流量必须启用负载均衡器的会话粘性（例如按 Cookie 粘性或 `ip_hash`），或者把管理端 API 固定路由到一个节点；否则登录后请求落到另一节点会被当作未登录。项目 Token、规则发布 outbox、跨节点执行保护和 Redis 推送使用共享 Redis，不依赖会话粘性。

### 5.2 启动、摘除和扩容节点

每台节点分别准备相同的 JAR、`.env.server` 和运行目录；节点可以都监听自己的 `8080`，由负载均衡器通过节点地址区分，不需要额外的实例标识参数：

```bash
# 节点 1/2 都执行，MYSQL_HOST 与 REDIS_HOST 指向共享依赖
cd /opt/tianshu/server
java -jar /opt/tianshu/server/rule-engine-server.jar

# 节点加入负载均衡前检查
curl -fsS http://127.0.0.1:8080/actuator/health/liveness
curl -i http://127.0.0.1:8080/actuator/health/readiness
```

扩容顺序如下：先部署新节点并等待 readiness 为 `UP`，再把它加入 upstream；缩容或升级时先从 upstream 摘除，等待正在处理的请求完成，再停止/重启节点。readiness 返回 `503` 或 `OUT_OF_SERVICE` 时不要把节点加入流量池；它可能表示 MySQL、Redis、ONNX 或已发布规则预热尚未就绪。

### 5.3 滚动更新与一致性检查

按节点逐台更新宿主机挂载的 JAR，重启后执行以下检查，确认通过再处理下一台：

```bash
sha256sum /opt/tianshu/server/rule-engine-server.jar
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```

发布规则后，所有节点会通过共享 Redis 收到项目频道或 GLOBAL 广播；新节点启动时还会执行 HTTP 全量同步。若 Redis 不可用，先修复共享 Redis，再恢复节点流量；不要通过给每个节点配置不同 Redis 来规避故障，否则节点缓存和跨节点限流/执行保护会分裂。扩容完成后至少验证：同一项目请求在不同节点均能鉴权和执行、规则发布后各节点最终使用相同版本、执行日志和计费没有重复写入、任一节点摘除后业务仍可用。

## 业务系统 SDK 集成

外部客户直接调用 Server API，接口与字段文档从项目「API」按钮下载。以下配置属于可选 Java SDK `rule-engine-client-sdk`，本地执行时才需要 Redis 和规则同步。

从业务 HTTP 接口到引擎结果返回的完整示例、全局规则关联、多项目客户端及日志开关语义见 [Java 业务服务接入指南](java-service-integration.html)。

业务系统引入 `rule-engine-client-sdk` 后，可继续使用项目原有访问令牌，也可按项目配置账号密码、API Key 或 HMAC-SHA256。非旧令牌方式默认先调用 `/api/rule/auth/token` 换取短期 Bearer Token，再同步或执行规则；调用方不需要也不能传 `authCode`，服务端会根据凭证自动识别鉴权配置。

```yaml
rule-engine:
  client:
    server-url: http://192.168.1.10:8080
    app-name: your-service-name
    project-code: your-project-code
    token: <项目访问令牌>
    project-id: 1
    trace-enabled: true
    log-report-enabled: true
    log-buffer-size: 500
    log-batch-size: 50
    log-flush-interval-ms: 5000
    # 规则依赖 API/DB/名单变量时必须开启服务端执行
    server-side-execution: true
```

项目鉴权下，`project-code` 是项目级实时推送和项目函数隔离的路由键，必须与服务端项目编码完全一致；`app-name` 只标识调用应用。使用 server 配置的 Ed25519 全局鉴权时，客户端改用 `auth-type: GLOBAL_KEY` 和私钥，不设置 `project-code`，SDK 的 `/sync/all` 和 `/sync/functions/all` 会同步全部已发布规则与启用函数，并订阅所有规则 Redis 频道。项目级客户端仍可按需配置项目 ID、编码和规则白名单。

```yaml
rule-engine:
  client:
    server-url: http://192.168.1.10:8080
    app-name: global-rule-service
    auth-type: GLOBAL_KEY
    global-key-id: default
    global-private-key: ${RULE_GLOBAL_PRIVATE_KEY}
```

账号密码方式示例（默认 Token 有效期 2 小时、失效后宽限 10 分钟，均可在项目鉴权配置中调整）：

```yaml
rule-engine:
  client:
    server-url: http://192.168.1.10:8080
    app-name: your-service-name
    project-code: your-project-code
    auth-type: BASIC
    username: caller-account
    password: caller-password
    token-exchange-enabled: true
    token-refresh-ahead-seconds: 60
```

API Key 使用 `auth-type: API_KEY` 并配置 `api-key`、`api-key-placement`（`HEADER` 或 `QUERY`）和 `api-key-parameter-name`；HMAC 使用 `auth-type: HMAC_SHA256` 并配置 `access-key` 与 `hmac-secret`。Java Builder 分别提供 `basicAuth(...)`、`apiKeyAuth(...)` 和 `hmacAuth(...)`。

HMAC 请求固定携带 `X-Rule-Access-Key`、`X-Rule-Timestamp`、`X-Rule-Nonce` 和 `X-Rule-Signature`。签名值为以下标准串使用 HMAC-SHA256 计算后的小写十六进制结果；Query 使用原始编码串，请求体使用原始字节。下面每行代表一个字段，字段之间使用 LF（换行符）连接，最后的 `NONCE` 后不再追加换行符。全局 Ed25519 请求对应携带 `X-Rule-Global-Key-Id`、`X-Rule-Global-Timestamp`、`X-Rule-Global-Nonce` 和 Base64 编码的 `X-Rule-Global-Signature`，使用同样的标准串由配对私钥签名。

```text
HTTP_METHOD
REQUEST_URI
RAW_QUERY
SHA256_HEX(REQUEST_BODY)
UNIX_TIMESTAMP_SECONDS
NONCE
```

直接调用 `/token` 时，只提交当前鉴权方式的凭证。例如账号密码使用 HTTP Basic：

```bash
curl -X POST http://localhost:8080/api/rule/auth/token \
  -H "Authorization: Basic <base64(username:password)>"

curl -X POST http://localhost:8080/api/rule/sync/execute/RC_PRICING_TABLE \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"clientAppName":"your-service-name","params":{}}'
```

执行示例：

```java
RuleResult result = ruleEngineClient.execute("RC_PRICING_TABLE", requestMap);
```

SDK 行为：

- 启动时先通过 HTTP 全量同步规则和函数，再建立 Redis 订阅；项目鉴权失败会直接使启动失败，不会留下半启动的订阅或定时任务。
- 成功的全量响应是权威快照：服务端已删除的规则/函数会从客户端移除，成功返回空数组会清空对应远端快照；HTTP 非 2xx、空响应、业务 `code !== "000000"`、非法 JSON 或网络失败不会清空旧快照。
- 订阅 `rule:push:{projectCode}` 和 `rule:push:broadcast`，规则发布/下线及函数更新/删除后增量刷新本地状态。项目推送只接受相同 `projectCode`；GLOBAL 推送对所有客户端生效。
- 缓存未命中时可按规则编码单条拉取。
- 默认本地使用 QLExpress 执行脚本，适合只依赖入参、常量、计算变量和已同步函数的规则。
- 同名远端函数按“当前项目 PROJECT → GLOBAL”解析；删除项目函数后自动回退同名 GLOBAL 函数，删除 GLOBAL 函数不会删除仍存在的项目函数。HTTP 成功快照与 Redis 删除消息都会真正撤销已删除函数，不需要重启业务应用。
- 如果规则依赖 API 变量、数据库变量或名单变量，必须开启 `server-side-execution: true`，或直接调用服务端接口 `POST /api/rule/sync/execute/{ruleCode}`。这些外部变量只在服务端通过 `VariableSourceResolver` 解析，本地 SDK 不会直连外部 API、数据库或名单库。
- SDK 在 Token 到期前 60 秒自动续期；续期失败时继续使用旧 Token，直到其宽限期结束。
- 没有外部 `ExecutionLogReporter` 时，日志通过有界 HTTP 队列异步上报，不阻塞规则结果；达到 `log-batch-size` 立即发送，否则最多等待 `log-flush-interval-ms`。队列达到 `log-buffer-size` 后采用 drop-newest 并记录丢弃计数/告警；HTTP 或业务响应失败最多尝试 3 次，最终失败记录批次和日志计数。
- `RuleEngineClient.close()` 会在有界等待内冲刷自己创建的 HTTP reporter；规则结果不会因 reporter 抛错而改为失败。应用提供的外部 reporter 生命周期归应用容器管理，客户端不会替它启动或关闭。
- `trace-enabled` 控制本地及服务端执行的表达式追踪，默认 true；直接 HTTP 调用通过顶层 `traceEnabled` 字段控制。关闭后仍保留 traceId 和服务端基础执行日志。
- 服务端持久化追踪默认不限制大小；通过 `RULE_TRACE_MAX_PERSIST_BYTES` 设置为正数后，超过上限的日志保存截断标记和原始大小，请求结果及内存中的本次追踪不受该持久化上限影响。
- `log-report-enabled=false` 禁止 SDK 本地日志上报，包括自定义、HTTP 和 Kafka reporter；不关闭服务端基础日志、鉴权审计或计费。开启上报时 Spring 容器中的自定义 `ExecutionLogReporter` 优先；内置通道默认 HTTP，Kafka 需显式设置 `log-report-transport: KAFKA`。服务端需启用 Kafka 消费并将主题绑定真实项目 ID；完整配置、失败补报和旧版迁移见 [Kafka 执行日志上报](kafka-execution-logs.md)。

项目鉴权配置、长期凭证和短期 Token 均可在控制台再次查看完整值。长期凭证在数据库中使用 AES-GCM 可逆加密存储；启动服务前必须通过 `RULE_AUTH_MASTER_KEY` 配置至少 32 位的独立主密钥并妥善保管，未配置或使用公开开发密钥时服务会拒绝启动。服务根据主密钥稳定生成当前密文版本 ID，历史 `v2` 密文仍可使用同一主密钥解密。所有节点必须使用相同的 `RULE_AUTH_MASTER_KEY`。访问审计记录所有受保护接口调用；只有实际规则执行进入计费，计费明细可区分 `authCode` 和 `tokenCode`，按日汇总到鉴权配置维度。

## 计费汇总币种升级与历史重建

计费日汇总按原始币种分别统计。同一天、同一计费对象和鉴权配置下的 CNY 与 USD 会产生两条汇总，金额和调用次数各自对应原始明细。

升级后，服务启动时由 `SchemaSyncService` 为 `rule_billing_summary.uk_billing_summary_key` 加入 `currency`。旧索引通过一条 `ALTER TABLE` 替换，已符合要求的索引保持原状。启动迁移只调整约束，不改写计费明细，也不自动重算历史日期。

如果旧版本曾在同一天更改计费项币种，需要重建受影响日期：

1. 在“账单管理 → 计费明细”核对该日期、计费编码和鉴权配置，分别统计每种币种的金额与调用次数。
2. 进入“计费汇总”，在右侧“选择日期”输入框中选择对应日期，点击“刷新汇总”。不填日期时刷新当天。
3. 等待页面显示“汇总已完成”，再按相同计费编码查询，确认各币种分别显示，金额与步骤 1 的明细一致。重复刷新不会把金额再次累加；服务端处理失败时事务回滚，原汇总保留。

刷新现在提交持久后台任务，立即返回任务 ID。页面显示等待、运行、完成或失败，离开页面后任务继续；重新进入“计费汇总”并选择原日期可恢复进度。进度查询失败时点击“重试查询”，不会重新发起汇总。任务处理失败后可重新点击“刷新汇总”。

同一日期正在等待或运行时，多人、多个节点的重复提交共用该任务；成功后再次刷新会创建新任务，纳入后续到达的明细。数据库事务以一次查询的一致快照聚合并原子替换当天汇总，读取者在完成前仍看到上次成功结果；不同币种与鉴权配置保持分组。运行进程终止后，数据库回滚未提交替换，存活或重启的节点恢复未完成任务。

服务启动会创建 `rule_billing_summary_task` 与 `rule_billing_summary_lock`。每节点同时运行一个汇总任务，数据库聚合语句设置 120 秒超时；不再把全天明细加载到 Java 内存。固定本机样本已验证 128 MiB Java 堆下处理 100 万条明细及 2 万分组，部署库的磁盘、索引、并发执行压力和长时间稳定性仍需按实际业务验收。实现采用 `READ COMMITTED` 使 `INSERT ... SELECT` 的来源查询使用一致读取，避免给账单明细加共享间隙锁；锁语义依据 [MySQL 官方说明](https://dev.mysql.com/doc/refman/8.0/en/innodb-locks-set.html)。

管理接口 `POST /api/rule/billing/summary/refresh` 的响应现为任务信息，包含 `taskId`、`summaryDate`、`status`；不能把初始响应当作完成结果。通过 `GET /api/rule/billing/summary/refresh/{taskId}` 查看该任务，或 `GET /api/rule/billing/summary/refresh?summaryDate=YYYY-MM-DD` 查询该日最新任务；只有 `SUCCEEDED` 的 `summaryCount` 和 `recordCount` 是本次完成统计。前后端应一起升级，提交继续要求 `billing:edit`，进度查询要求 `billing:view`。

## 名单导出、资源选择和审批依赖提示

名单内容导出使用分页查询和流式 Excel 响应，服务端不会先把整个名单加载为实体列表。导出过程会按创建时间和 ID 稳定排序，导出的内容是当前已保存记录，名单变更仍必须通过原审批入口。部署时应为应用临时目录预留 SXSSF 工作文件空间；导出失败或客户端中断时由框架清理临时文件，磁盘配额和超大名单并发导出仍需按生产规模压测。

项目级配置页面的项目选择使用服务端搜索分页，空搜索也只加载一页；编辑已有配置会按项目 ID 回填当前名称。项目编码、项目名称和配置引用继续按 ID 提交，切换项目后页面会清空并重新加载对应规则、目标或字段候选。若权限不包含项目完整管理，选择器只访问项目选项接口，不返回访问凭据。

审批详情中的依赖问题先显示中文资源类型、依赖状态、阻断原因和处理建议，JSON/结构化引用路径仅用于技术定位。`未发布/已停用`、`资源缺失`、`版本缺失`、`版本不可用`、`类型不匹配`和`自引用`都不能通过审批；先按建议修复上游并重新执行预检。资源名称跳转仍受当前账号授权范围约束，不能通过名称替代稳定资源 ID。

## 规则集命中统计升级

服务启动会补充执行日志的统计处理状态与格式版本，创建 `rule_execution_statistics`、`rule_set_statistics_event` 及逻辑统计视图。新日志在原日志的同一事务中提取规则集和规则项；重复上报和执行恢复更新仍以原逻辑日志身份为准，不累加重复统计。统计写入失败会使该次日志事务回滚，由原持久化恢复机制重试。

历史日志由后台线程每批最多处理 10 条，使用行锁和 `SKIP LOCKED` 支持多个节点共同补齐；格式版本为空或低于当前版本的记录会重建，原始追踪不被修改。补齐过程可以中断，已提交批次保留，未提交批次会再次处理。不要通过手工修改格式版本绕过补齐，否则页面可能把缺失统计当作已完成。

执行日志页与看板会说明所选项目、时间范围内尚待补齐、未记录追踪、无法解析及已截断的日志数量。统计值只反映当前可用的规则集追踪，不能把这些缺口解释为零命中；补齐或新增执行后重新查询即可更新。规则集的计数是执行次数，嵌套调用可以在一条根日志中产生多次规则集执行。P95/P99 按精确耗时频次计算，查询不再读取全部原始追踪到 Java 内存。

执行日志及其统计素材遵循当前业务证据永久保留策略，辅助日志清理器不会删除它们。备份恢复应同时覆盖原日志、逻辑身份和两张统计表；统计表可由原始追踪重建，但必须同步重置相应日志的统计格式版本并等待后台补齐，不能仅清空统计表。长期累积数据的存储容量、宽时间范围与并发查询仍需在部署环境验收；单次百万样本不代表多年数据和生产并发已达标。


