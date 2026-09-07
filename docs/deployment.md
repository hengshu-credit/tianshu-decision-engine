# 天枢决策引擎部署与接入

本文介绍运行环境、基础设施配置与业务系统接入。

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
cp .env.example .env
# 编辑 .env，替换全部 replace-with-* 占位值
docker compose --env-file .env up -d
```

PowerShell 可使用 `Copy-Item .env.example .env`。Compose 不再提供 MySQL/Redis 共享默认密码；全新数据卷会根据 `MYSQL_USERNAME`、`MYSQL_PASSWORD` 创建应用账号。已有数据卷升级时，需由数据库管理员按最小权限原则预先创建或更新该账号。

`schema.sql` 只包含数据库、表和索引等结构 DDL，不创建用户、不修改 root 账号、也不执行全局授权；`export_202607161151.sql` 是当前唯一的初始数据快照。空 Docker 数据卷首次启动时会依次执行 `01-schema.sql` 和 `02-export.sql`。根编排中的 `mysql-init` 对已有数据卷只重复执行结构 DDL，不会自动重放会覆盖业务数据的 export。项目鉴权、临时 Token 及其访问审计数据与部署主密钥绑定，不写入初始快照；服务启动后会把项目表中的兼容访问令牌按当前主密钥迁移为默认鉴权记录。

需要手工完整恢复时，固定顺序为：删除 `rule_engine` 数据库，执行 `schema.sql`，再执行 `export_202607161151.sql`。export 会清空并重建其覆盖的全部数据表，因此不得直接用于需要保留现有业务数据的数据库。

### 后端

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

后端启动时会将当前目录的 `.env` 和上级目录的 `.env` 作为可选配置源读取：在仓库根目录运行 JAR 时读取 `./.env`，在 `rule-engine-server` 目录运行 `mvn spring-boot:run` 时读取 `../.env`。操作系统环境变量和命令行参数的优先级高于 `.env`，因此生产环境仍应通过 Secret/KMS 注入真实凭据；缺少必填密钥时，现有安全校验仍会拒绝启动。关键配置如下：

| 配置 | 要求 |
|------|------|
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 必填；生产使用仅具备 `rule_engine` 所需权限的独立账号 |
| `REDIS_PASSWORD` | 必填；与 server 和 SDK 实际连接的 Redis 实例一致 |
| `RULE_AUTH_MASTER_KEY` | 必填；至少 32 位的私有随机密钥，生产通过 Secret/KMS 注入 |
| `RULE_AUTH_LEGACY_MASTER_KEY` / `RULE_AUTH_LEGACY_V2_MASTER_KEY` | 仅升级旧部署时配置；分别用于读取历史 `v1` 密文和曾复用 `v2` 标识的旧密文 |
| `CONSOLE_USERNAME` / `CONSOLE_PASSWORD` | 必填；密码默认按 BCrypt 校验 |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | 生产控制台的精确 HTTPS 来源，不使用任意来源 |
| `SESSION_COOKIE_SECURE` | HTTPS 生产环境设为 `true` |

本地临时开发如需使用明文控制台密码，可显式设置 `CONSOLE_PASSWORD_ENCODING=PLAIN`；生产必须使用 BCrypt 或外部身份系统，不应提交密码或密钥到仓库。

ONNX 神经网络模型可在“模型管理”中逐个选择 CPU 或 CUDA，并配置 GPU 设备号、显存上限、显存扩展策略、cuDNN 卷积算法搜索和默认 CUDA 流。CPU 版后端即使读取到历史 CUDA 配置也会自动回退 CPU，不影响服务启动；CUDA 版后端会实际检查 CUDA 共享库及其依赖是否可加载。服务按“模型文件内容 + 运行配置”缓存推理会话；开启“启动预加载”后会在服务启动阶段创建对应 CPU/CUDA 会话。配置 CUDA 的模型在 GPU 会话初始化或推理失败时会自动重试 CPU，CPU 成功后同一服务进程内后续调用会直接使用 CPU；修复 GPU 环境后需重启服务以重新尝试 CUDA。YuNet 人脸检测同样通过 ONNX Runtime 执行，OpenCV 仅保留图片解码、缩放和检测结果后处理。

模型运行时当前使用 JPMML Evaluator Metro 1.7.7 和 ONNX Runtime 1.26.0。PMML 输入字段按模型声明精确匹配，不自动转换大小写、驼峰或下划线；PMML/ONNX 缓存键均包含模型内容或运行配置，替换文件后不会继续复用旧模型。本项目已选择以 AGPL-3.0 开源方式交付 JPMML：凡对外提供包含 JPMML 的制品或网络服务，都必须同步提供与运行版本一致的完整 Corresponding Source、许可证和重建材料，详见 [THIRD_PARTY_LICENSES.md](../THIRD_PARTY_LICENSES.md)。

### 前端

```bash
cd rule-engine-builder-ui
npm ci
npm run dev
```

开发访问地址为 `http://localhost:9090/`，`/api` 会代理到 `http://localhost:8080`。


## 业务系统 SDK 集成

业务系统引入 `rule-engine-client` 后，可继续使用项目原有访问令牌，也可按项目配置账号密码、API Key 或 HMAC-SHA256。非旧令牌方式默认先调用 `/api/rule/auth/token` 换取短期 Bearer Token，再同步或执行规则；调用方不需要也不能传 `authCode`，服务端会根据凭证自动识别鉴权配置。

```yaml
rule-engine:
  client:
    server-url: http://localhost:8080
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

`project-code` 是项目级实时推送和项目函数隔离的必填路由键，必须与服务端项目编码完全一致；`app-name` 只标识调用应用，不会替代 `project-code` 订阅项目频道。未配置 `project-code` 时 SDK 仍可通过 HTTP 同步并接收 GLOBAL 广播，但不会订阅任何项目频道，因此不能获得项目规则/函数的实时变更。

账号密码方式示例（默认 Token 有效期 2 小时、失效后宽限 10 分钟，均可在项目鉴权配置中调整）：

```yaml
rule-engine:
  client:
    server-url: http://localhost:8080
    app-name: your-service-name
    project-code: your-project-code
    auth-type: BASIC
    username: caller-account
    password: caller-password
    token-exchange-enabled: true
    token-refresh-ahead-seconds: 60
```

API Key 使用 `auth-type: API_KEY` 并配置 `api-key`、`api-key-placement`（`HEADER` 或 `QUERY`）和 `api-key-parameter-name`；HMAC 使用 `auth-type: HMAC_SHA256` 并配置 `access-key` 与 `hmac-secret`。Java Builder 分别提供 `basicAuth(...)`、`apiKeyAuth(...)` 和 `hmacAuth(...)`。

HMAC 请求固定携带 `X-Rule-Access-Key`、`X-Rule-Timestamp`、`X-Rule-Nonce` 和 `X-Rule-Signature`。签名值为以下标准串使用 HMAC-SHA256 计算后的小写十六进制结果；Query 使用原始编码串，请求体使用原始字节：

```text
HTTP_METHOD\n
REQUEST_URI\n
RAW_QUERY\n
SHA256_HEX(REQUEST_BODY)\n
UNIX_TIMESTAMP_SECONDS\n
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
- 成功的全量响应是权威快照：服务端已删除的规则/函数会从客户端移除，成功返回空数组会清空对应远端快照；HTTP 非 2xx、空响应、业务 `code != 200`、非法 JSON 或网络失败不会清空旧快照。
- 订阅 `rule:push:{projectCode}` 和 `rule:push:broadcast`，规则发布/下线及函数更新/删除后增量刷新本地状态。项目推送只接受相同 `projectCode`；GLOBAL 推送对所有客户端生效。
- 缓存未命中时可按规则编码单条拉取。
- 默认本地使用 QLExpress 执行脚本，适合只依赖入参、常量、计算变量和已同步函数的规则。
- 同名远端函数按“当前项目 PROJECT → GLOBAL”解析；删除项目函数后自动回退同名 GLOBAL 函数，删除 GLOBAL 函数不会删除仍存在的项目函数。HTTP 成功快照与 Redis 删除消息都会真正撤销已删除函数，不需要重启业务应用。
- 如果规则依赖 API 变量、数据库变量或名单变量，必须开启 `server-side-execution: true`，或直接调用服务端接口 `POST /api/rule/sync/execute/{ruleCode}`。这些外部变量只在服务端通过 `VariableSourceResolver` 解析，本地 SDK 不会直连外部 API、数据库或名单库。
- SDK 在 Token 到期前 60 秒自动续期；续期失败时继续使用旧 Token，直到其宽限期结束。
- 没有外部 `ExecutionLogReporter` 时，日志通过有界 HTTP 队列异步上报，不阻塞规则结果；达到 `log-batch-size` 立即发送，否则最多等待 `log-flush-interval-ms`。队列达到 `log-buffer-size` 后采用 drop-newest 并记录丢弃计数/告警；HTTP 或业务响应失败最多尝试 3 次，最终失败记录批次和日志计数。
- `RuleEngineClient.close()` 会在有界等待内冲刷自己创建的 HTTP reporter；规则结果不会因 reporter 抛错而改为失败。应用提供的外部 reporter 生命周期归应用容器管理，客户端不会替它启动或关闭。
- Spring 容器中存在自定义 `ExecutionLogReporter` 时始终优先使用；存在 `KafkaTemplate` 且没有自定义 reporter 时自动创建 Kafka reporter，默认主题为 `rule-execution-log`。是否使用 BASIC、API Key、HMAC 或旧令牌鉴权不会强制覆盖该 reporter 选择。

项目鉴权配置、长期凭证和短期 Token 均可在控制台再次查看完整值。长期凭证在数据库中使用 AES-GCM 可逆加密存储；启动服务前必须通过 `RULE_AUTH_MASTER_KEY` 配置至少 32 位的独立主密钥并妥善保管，未配置或使用公开开发密钥时服务会拒绝启动。新密文默认使用 `v2` 密钥；升级前若已有旧 `v1` 密文，需通过 `RULE_AUTH_LEGACY_MASTER_KEY` 保留原主密钥；若历史版本曾在更换密钥材料时继续复用 `v2` 标识，还需通过 `RULE_AUTH_LEGACY_V2_MASTER_KEY` 配置当时的材料。解密会优先使用密文标识对应的密钥，再尝试已配置的历史密钥；待旧凭证全部修改或重置后应移除历史密钥。可通过 `RULE_AUTH_ACTIVE_KEY_ID` 显式选择活动密钥版本，后续轮换必须使用新的 key ID。访问审计记录所有受保护接口调用；只有实际规则执行进入计费，计费明细可区分 `authCode` 和 `tokenCode`，按日汇总到鉴权配置维度。

