# 上传服务器部署与升级（2026-10-26）

本流程用于 **server 2 副本 + web 1 个 + 可选 SDK runtime 1 个 + 独立 Kafka 1 个**，复用服务器已有 MySQL、Redis 和 Nginx Proxy Manager。所有服务操作直接使用完整的 `docker compose` 命令。

部署包已包含 Docker 构建的 CPU 版 `server.jar`、`runtime.jar`、前端 `dist`、Compose、文档、许可证及对应源码 `source/`。服务器无需安装 Maven、Node.js 或重新编译；包内不含 Docker 镜像，首次启动需要能够拉取配置中的镜像。源码中的配置模板不包含本次生产凭据；向服务使用者提供与当前版本对应的源码获取入口，详见 `THIRD_PARTY_LICENSES.md`。

以下沿用目录 `/root/tianshu-decision-engine`、服务端项目 `tianshu-server`、客户端项目 `tianshu-client`。**已有部署必须沿用实际目录、项目名、网络、端口、数据卷和密钥**；如果 Kafka 原来属于 `tianshu-kafka`，下列 Kafka 命令的项目名应继续使用 `tianshu-kafka`，不能直接切换。

## 1. 上传、校验并解压到暂存目录

将 ZIP 和同名 `.zip.sha256` 上传到 `/root/`，保持原文件名。把下面的 `包文件名.zip` 改为本次实际名称：

```bash
set -euo pipefail
cd /root
PACKAGE_ZIP=/root/包文件名.zip
sha256sum -c "${PACKAGE_ZIP}.sha256"
RELEASE_DIR=$(mktemp -d /root/tianshu-release-XXXXXXXX)
unzip -q "$PACKAGE_ZIP" -d "$RELEASE_DIR"
cd "$RELEASE_DIR"
sha256sum -c SHA256SUMS.txt
docker compose version
```

任一校验失败即停止。以下步骤在同一 Bash 会话执行，继续使用 `RELEASE_DIR`。先暂存再停机替换，避免直接覆盖运行中的 JAR。

## 2. 保留配置与备份

已有系统先由数据库运维完成可恢复备份。包含决策制品的 MySQL 备份必须保留二进制内容（`mysqldump --hex-blob`，配合 `--result-file` 或容器内原始管道），恢复到不同且不存在的库，按逐表行数及内容验收。应用文件备份不能代替数据库备份。

```bash
mkdir -p /root/tianshu-decision-engine
cd /root/tianshu-decision-engine
BACKUP_DIR=$(mktemp -d /root/tianshu-backup-XXXXXXXX)
chmod 700 "$BACKUP_DIR"
for item in .env.server .env.client docker; do
  if [ -e "$item" ]; then
    tar --exclude='docker/rule-engine-mysql/data' \
        --exclude='docker/rule-engine-mysql/logs' \
        --exclude='docker/rule-engine-redis/data' \
        --exclude='docker/rule-engine-redis/logs' \
        -cpf "$BACKUP_DIR/$(basename "$item").tar" "$item"
  fi
done
printf '本次应用备份目录：%s\n' "$BACKUP_DIR"
```

如果原系统仍有 `runtime-http`，先将调用方迁移到 Server：`POST /api/rule/sync/execute/{ruleCode}` 或已发布的 `POST /api/rule/openapi/{ruleCode}`，改用项目调用凭据。旧 HTTP runtime 的入口令牌不能用作 Server 项目凭据。迁移验收后，在**覆盖旧 Compose 文件之前**停止并移除该旧服务：

```bash
# 仅旧 client Compose 的 config --services 确实包含 runtime-http 时执行
docker compose --project-name tianshu-client --env-file .env.client -f docker/docker-compose.client.yml config --services
docker compose --project-name tianshu-client --env-file .env.client -f docker/docker-compose.client.yml stop runtime-http
docker compose --project-name tianshu-client --env-file .env.client -f docker/docker-compose.client.yml rm -f runtime-http
```

新部署或已无此服务时跳过以上三条。旧版使用其他 Compose 方案时，必须沿用原方案的文件和项目名，不能拿本例操作另一套环境。

## 3. 停止应用并安装新版文件

已有部署安排维护窗口，在原目录执行；首次安装跳过停止命令。若未部署 SDK，跳过客户端停止命令。

```bash
docker compose --project-name tianshu-client --env-file .env.client -f docker/docker-compose.client.yml stop runtime-sdk
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.server.yml stop web server
```

Kafka、MySQL、Redis 保持原状。执行以下文件安装操作：

```bash
cd /root/tianshu-decision-engine
# 移走旧制品，避免旧前端资源混入新版；本次包只包含应用配置及制品，不包含数据库数据。
if [ -d docker/runtime ]; then
  mv docker/runtime "$BACKUP_DIR/runtime"
fi
cp -a "$RELEASE_DIR/docker" ./
cp -a "$RELEASE_DIR/docs" "$RELEASE_DIR/licenses" "$RELEASE_DIR/source" ./
cp -a "$RELEASE_DIR/BUILD-INFO.txt" "$RELEASE_DIR/SHA256SUMS.txt" \
      "$RELEASE_DIR/README-部署.md" "$RELEASE_DIR/LICENSE" \
      "$RELEASE_DIR/NOTICE" "$RELEASE_DIR/THIRD_PARTY_LICENSES.md" ./
cp -a "$RELEASE_DIR/.env.server.example" "$RELEASE_DIR/.env.client.example" ./
for name in .env.server .env.client; do
  if [ ! -f "$name" ]; then
    if [ -f "$RELEASE_DIR/$name" ]; then
      cp "$RELEASE_DIR/$name" "$name"
    else
      cp "$RELEASE_DIR/$name.example" "$name"
    fi
  fi
done
chmod 600 .env.server .env.client
```

安装不会覆盖已有 `.env.server`、`.env.client`。在暂存目录校验完整包；安装目录保留原配置，不能要求其 env 摘要与新包一致。对照 `.example` 补充所需参数，不要整份覆盖已有配置，也不要 `source` 环境文件。

检查两份配置：

- `RUNTIME_DIR` 指向 `/root/tianshu-decision-engine/docker/runtime`，或使用相对 Compose 文件的 `./runtime`。
- MySQL 使用原地址、库名、账号及密码；两端 Redis 指向同一实例、密码及 database。
- 已有数据库必须保留原 `RULE_AUTH_MASTER_KEY`。全局 Ed25519 公私钥继续成对保留，SDK 入口令牌保持与调用端一致。`.example` 是公开的体验配置，新生产环境必须替换。
- `RUNTIME_RULE_SERVER_URL` 使用客户端容器可达的 Server 根地址，不带 `/api`，不能使用指向客户端自身的 `127.0.0.1`。
- 保留 `DEPLOY_PROJECT`、`ENGINE_NETWORK`、端口绑定。`--project-name` 不会覆盖显式 `ENGINE_NETWORK`；不要随意改名。
- Kafka 保留原 `KAFKA_PASSWORD`、广播地址和卷；使用 Kafka 日志时还须同步两端 SASL 配置及真实项目主题绑定。跨项目 SDK 默认仍通过 HTTP 上报。
- `SPRING_SQL_INIT_MODE=always`、`RULE_ENGINE_INITIAL_DATA_ENABLED=false`；正常启动幂等更新结构。生产不导入样例、不启用 `example-data`。

## 4. 解析配置并分步启动

先解析配置；这一步不代表远程密码和连通性已经验证：

```bash
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.server.yml config --quiet
docker compose --project-name tianshu-client --env-file .env.client -f docker/docker-compose.client.yml config --quiet
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.kafka.yml config --quiet
```

首次需要本包 Kafka 时先启动；已有 Kafka 未改配置可跳过，沿用当前实例。SDK 为可选服务，只直接调用 Server API 时可跳过客户端启动。

```bash
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.kafka.yml up -d --wait --wait-timeout 300
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.server.yml up -d --force-recreate --scale server=2 --wait --wait-timeout 300
docker compose --project-name tianshu-client --env-file .env.client -f docker/docker-compose.client.yml up -d --force-recreate --wait --wait-timeout 300
```

每条成功才执行下一条。升级可能同时重启两个 server，不承诺零停机。不要同时运行 full/mysql/redis 方案；两个文件分别操作同一项目可能提示 orphan，不要加 `--remove-orphans`，也不要执行 `down -v`。

## 5. 状态、日志与浏览器验收

```bash
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.server.yml ps -a
docker compose --project-name tianshu-client --env-file .env.client -f docker/docker-compose.client.yml ps -a
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.kafka.yml ps -a
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.server.yml logs --tail 100 server web
docker compose --project-name tianshu-client --env-file .env.client -f docker/docker-compose.client.yml logs --tail 100 runtime-sdk
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.kafka.yml logs --tail 100 kafka
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.server.yml exec -T web nginx -t
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.server.yml exec -T server wget -qO- http://127.0.0.1:8080/actuator/health/readiness
docker compose --project-name tianshu-client --env-file .env.client -f docker/docker-compose.client.yml exec -T runtime-sdk wget -qO- http://127.0.0.1:7071/actuator/health/readiness
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.kafka.yml exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:19092 --command-config /tmp/tianshu-client.properties --list
```

`ps -a` 应显示两个 server 均 healthy；单次 exec 只抽查一个副本。未启动的可选服务不执行对应检查。Kafka 命令只验证内部监听和认证，外部广播地址还需由实际客户端验收。

NPM 沿用现有 Proxy Host：控制台域名代理到服务器 IP 和 `.env.server` 的 `FRONTEND_PORT`；同域 `/api/` 已由 web 代理到 server。公网 HTTPS、证书和访问控制继续由 NPM 管理，不改 UFW、Fail2ban、宿主机 Nginx 或原安全组。API 专用入口使用 `SERVER_PUBLIC_PORT`，不要在公网暴露 actuator。

打开控制台 HTTPS 地址，依次确认登录、项目列表、规则详情、设计器、规则测试与执行日志。已有库使用原账号；仅空库首次注册超级管理员。业务方验证真实项目鉴权、已发布规则执行、SDK 同步及日志上报后恢复流量。忘记超管账号密码时：

```bash
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.server.yml exec -T server java -jar /app/app.jar --console-admin-recovery-code
```

在登录页“管理员应急恢复”使用30分钟一次性恢复码；不更换主密钥、不清空数据。

## 6. 启动失败与回退

先保留日志，核对数据库权限、两端 Redis、原主密钥、配对签名密钥、容器可达地址和端口占用。不要用删除卷或重新导入样例排障。结构升级已经写入数据库时，先评估旧版兼容性；需要数据库回退时使用经过验收的备份恢复到新库，不能只换旧 JAR 就宣称回退完成。

确认数据库兼容后，在维护窗口停止应用，将第 2 步的应用/config 备份恢复，再用原项目的 `up -d --force-recreate --wait` 启动并重复第 5 步。原 Kafka 数据卷、MySQL 和 Redis 不随应用文件回退删除。保留本次 ZIP、哈希、配置备份及数据库备份，直到服务器验收完成。
