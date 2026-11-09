# 上传服务器部署与应用更新

部署分成长期运行的共享环境和应用制品：**MySQL、Redis、Kafka 共用一套，日常更新只替换 `server.jar`、可选 SDK 的 `runtime.jar` 和前端 `dist/`。** 原 `.env.server`、`.env.client`、Compose、Nginx 配置、数据库数据、Kafka 卷和恢复 journal 保留，不删除整个安装目录，不需要重新复制 env。

自动部署入口优先使用 `docker compose`，不可用时使用 `docker-compose`；两者均要求现代 Compose v2+（支持 `config --format json`、`up --wait`），不支持旧 Python Compose v1。服务器还需 Python 3.8+，不需安装第三方 Python 包。本页以 Linux 固定目录 `/root/tianshu-decision-engine`、服务端项目 `tianshu-server`、客户端项目 `tianshu-client` 为例；**已有部署沿用实际项目名、目录、网络、端口、数据卷和密钥**。Kafka 原属 `tianshu-kafka` 就继续保留，不能为统一名称重建。构建入口见 [跨平台发布构建](release-packaging.md)。

```text
tianshu-decision-engine/
├── tianshu-deployment-*.zip    # 上传包和同名 .zip.sha256 均放在部署目录
├── releases/                  # 每次更新独立解压，不覆盖运行文件
├── .env.server                 # 服务端与共享基础设施的长期配置
├── .env.client                 # SDK 长期配置，连接同一套 Redis/Kafka
├── docker/
│   ├── docker-compose.*.yml    # 已安装的部署定义，普通更新不覆盖
│   ├── nginx/                  # 已安装的前端代理配置
│   ├── rule-engine-mysql/      # 原 MySQL 配置、数据和日志
│   ├── rule-engine-redis/      # 原 Redis 配置、数据和日志
│   ├── rule-engine-kafka/      # 原 Kafka 启动配置，数据在原命名卷
│   └── runtime/               # 日常只替换这里的应用制品
│       ├── server.jar
│       ├── runtime.jar
│       └── dist/
└── backups/                    # 更新前的应用制品与版本记录
```

新 ZIP 仍携带完整部署材料、源码和许可证，便于首次安装与重建。已有环境只取其中的应用制品和适用迁移；保留完整 ZIP 作为该版本源码与许可证的交付记录。首次安装需能拉取运行镜像，服务器不需要 Maven、Node.js 或重新编译。

## 快速更新：五行命令（推荐）

新发布包自带 `deploy.py`。将 ZIP 与同名 `.zip.sha256` 直接上传到 `/root/tianshu-decision-engine/`。下面只替换包文件名；上传包、解压目录、备份都保留在该部署目录内，不在 `/root` 散放文件，也不删除部署目录。每次自动创建新的解压子目录，重复更新不会覆盖上次暂存文件。

```bash
cd /root/tianshu-decision-engine &&
PKG=包文件名.zip &&
sha256sum -c "$PKG.sha256" && mkdir -p releases && test ! -L releases &&
RELEASE_DIR=$(mktemp -d "$PWD/releases/update-XXXXXXXX") && unzip -q "$PKG" -d "$RELEASE_DIR" &&
python3 "$RELEASE_DIR/deploy.py" --target "$PWD"
```

脚本自动完成逐文件校验、读取原 env/Compose、识别原项目名与副本数、备份旧 JAR/dist、停止应用、替换制品、重建应用容器和等待健康检查。MySQL、Redis、Kafka 不停不重建；env、Nginx、Compose、数据卷不覆盖。已有 SDK 自动一起更新；独立 SDK 机器在末尾加 `--client-only`。

**首次部署**：先创建 `/root/tianshu-decision-engine` 并把包和校验文件上传到这里。共享 MySQL/Redis/Kafka 已就绪时，确认尚无已安装的 docker 和 env，再在该目录初始化，填写一次 env；已有环境使用上面的更新命令。以下初始化命令带覆盖保护：

```bash
cd /root/tianshu-decision-engine && PKG=包文件名.zip &&
sha256sum -c "$PKG.sha256" && test ! -e docker && test ! -e .env.server && test ! -e .env.client && unzip -q "$PKG" &&
(test -f .env.server || cp .env.server.example .env.server) &&
vi .env.server &&
python3 deploy.py --target "$PWD"
```

首次还需 SDK 时，同样准备 `.env.client`，最后一行追加 `--sdk`。脚本默认只启动应用，不创建第二套基础设施。首次没有容器记录时使用 env 中的项目名；已有容器则从 Docker 标签恢复原项目名，包括原来通过 `-p` 指定的名称。使用额外 Compose 文件的现有部署按容器记录保留，缺失文件会在停止应用前报错。

**有数据库迁移时**，在最后一条命令加 `--migration-script /root/tianshu-decision-engine/本次迁移.sh`。脚本停止本机应用后、替换制品前运行此 Bash 脚本；迁移脚本负责先完成数据库备份，再执行已选定的 SQL，遇错必须非零退出。跨机器旧节点需提前协调停止。脚本可读取 `TIANSHU_RELEASE_DIR`、`TIANSHU_DEPLOY_DIR`、`TIANSHU_BACKUP_DIR`，不自动遍历历史 SQL。若发布包提供 `migrations/upgrade.sh`，未显式指定迁移脚本时会在停机前阻止更新，避免漏跑。

入口会在操作 Docker 前拒绝把 `/root` 或文件系统根目录用作安装目标；更新包必须位于该安装目录的 `releases/` 下。备份位于原安装目录 `backups/update-*`。健康检查失败会停止新应用并恢复原制品，但不会自动启动旧版，因为数据库可能已升级。中断后若遗留 `.deploy.lock`，确认没有其他部署进程后再移除锁。详细迁移、手工更新与回退步骤如下，仅排障或特殊部署时需要。

## 1. 上传并校验新包

把 ZIP 和同名 `.zip.sha256` 上传到 `/root/tianshu-decision-engine/`，修改实际包文件名。以下步骤在同一 Bash 会话执行；任一命令失败即停止。

```bash
set -euo pipefail
cd /root/tianshu-decision-engine
PACKAGE_ZIP="$PWD/包文件名.zip"
sha256sum -c "${PACKAGE_ZIP}.sha256"
mkdir -p releases
test ! -L releases
RELEASE_DIR=$(mktemp -d "$PWD/releases/update-XXXXXXXX")
unzip -q "$PACKAGE_ZIP" -d "$RELEASE_DIR"
cd "$RELEASE_DIR"
sha256sum -c SHA256SUMS.txt
docker compose version
```

不要直接解压覆盖运行目录。新包中的 `migrations/README.md` 列出独立数据库迁移的适用范围、顺序和执行时机；没有尚未执行的适用迁移就跳过迁移步骤。

## 2. 首次安装（仅执行一次）

已有部署跳过本节，直接进入第 3 节。全新安装将已校验包放进固定目录，配置两份 env；仅部署 server/web 时不需要启用 SDK。

```bash
mkdir -p /root/tianshu-decision-engine
cd /root/tianshu-decision-engine
# 防止把首次安装步骤误用于已有环境
test ! -e docker
test ! -e .env.server
test ! -e .env.client
cp -a "$RELEASE_DIR/." ./
for name in .env.server .env.client; do
  if [ ! -f "$name" ]; then cp "$name.example" "$name"; fi
done
chmod 600 .env.server .env.client
vi .env.server
vi .env.client
```

`.env.server` 管理服务端与共享 MySQL/Redis/Kafka；`.env.client` 的 Redis 地址、密码及 database 必须指向同一实例，启用 Kafka 时连接同一 broker。跨机器可使用不同的可达地址，但必须对应同一环境。保留服务端加密主密钥与配对签名密钥；正式部署替换公开体验凭据。`RUNTIME_DIR=./runtime` 相对于 `docker/` 中的 Compose 文件解析。

已有 MySQL、Redis、Kafka 直接连接，不再创建。只有需要本项目首次创建这三项共享基础设施时，才执行下面的组合命令；它复用现有三个文件，不引入另一套环境：

```bash
docker compose --project-name tianshu-server --env-file .env.server \
  -f docker/docker-compose.mysql.yml -f docker/docker-compose.redis.yml -f docker/docker-compose.kafka.yml \
  up -d --wait --wait-timeout 300 mysql redis kafka
```

配置应用可达的 MySQL/Redis 地址、Kafka 广播地址，以及容器可达的 `RUNTIME_RULE_SERVER_URL`（Server 根地址，不带 `/api`）。然后按第 5 节解析并启动应用。空库由 `schema.sql` 初始化，不运行面向已有库的迁移，不导入样例数据。

## 3. 更新准备与停止应用

日常更新继续使用原安装目录和原 Compose。先阅读迁移说明，确认包与当前部署配置兼容；若某个版本确实需要新增必填环境变量、Compose 或 Nginx 配置，发布说明必须单独列明，按差异升级一次，不能把整目录覆盖当成日常更新。

下面以 `RUNTIME_DIR=./runtime` 或 `/root/tianshu-decision-engine/docker/runtime` 为例。使用自定义 `RUNTIME_DIR` 时，把 `APP_RUNTIME_DIR` 改成该配置当前实际挂载的绝对目录；不能复制到另一个未被挂载的位置。

```bash
APP_DIR=/root/tianshu-decision-engine
cd "$APP_DIR"
APP_RUNTIME_DIR="$APP_DIR/docker/runtime"
test -f "$APP_RUNTIME_DIR/server.jar"
test -f "$RELEASE_DIR/docker/runtime/server.jar"
test -f "$RELEASE_DIR/docker/runtime/runtime.jar"
test -f "$RELEASE_DIR/docker/runtime/dist/index.html"
mkdir -p backups
BACKUP_DIR=$(mktemp -d "$APP_DIR/backups/update-XXXXXXXX")
chmod 700 "$BACKUP_DIR"
# 先完整复制到相邻暂存目录，旧应用此时仍可运行
NEXT_RUNTIME_DIR=$(mktemp -d "$(dirname "$APP_RUNTIME_DIR")/runtime-next-XXXXXXXX")
chmod 755 "$NEXT_RUNTIME_DIR"
cp -a "$RELEASE_DIR/docker/runtime/server.jar" "$RELEASE_DIR/docker/runtime/runtime.jar" \
  "$RELEASE_DIR/docker/runtime/dist" "$NEXT_RUNTIME_DIR/"
cp "$RELEASE_DIR/BUILD-INFO.txt" "$BACKUP_DIR/new-BUILD-INFO.txt"
printf '本次应用备份目录：%s\n' "$BACKUP_DIR"
```

安排维护窗口，暂停业务流量，停止旧应用。部署在其他机器的 server/SDK 也必须协调停止，尤其不能让旧节点在迁移时继续写同一个数据库。未部署 SDK 时跳过客户端命令。

```bash
docker compose --project-name tianshu-client --env-file .env.client -f docker/docker-compose.client.yml stop runtime-sdk
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.server.yml stop web server
```

MySQL、Redis、Kafka 保持运行。由数据库运维完成可恢复备份；包含决策制品时使用 `mysqldump --hex-blob` 配合 `--result-file` 或容器内原始管道，不能经文本解码中转。备份应有恢复到不同且不存在的库、逐表行数及内容校验的验收记录。

## 4. 按需迁移数据库，然后替换应用制品

**迁移执行点就在这里：旧应用停止且数据库备份完成后、新版应用启动前。** 查看 `$RELEASE_DIR/migrations/README.md`，按当前版本与运维记录逐条选择尚未执行的适用 SQL。没有适用迁移则直接替换制品；不要循环执行全部历史脚本，也不要用 `schema.sql` 或样例数据代替增量迁移。

例如，从尚未完成账户记录隔离的旧库升级时，使用原 MySQL 容器执行独立 SQL：

```bash
docker compose --project-name tianshu-server --env-file .env.server \
  -f docker/docker-compose.mysql.yml exec -T mysql sh -ec \
  'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 --binary-mode=1 --host=127.0.0.1 --user="$MYSQL_USER" --database="$MYSQL_DATABASE"' \
  < "$RELEASE_DIR/migrations/2026-11-04-account-record-isolation.sql"
```

确认容器内目标库/账号与 server 一致且有 DDL 权限。外部 MySQL 由运维使用受保护的客户端配置执行同一 SQL。每条成功后记录文件名、SHA256、目标库和结果。禁止 `mysql --force`；失败时保持应用停止，检查已完成的 DDL，不继续启动新版。

适用迁移全部成功后，替换应用目录；原目录作为备份保留，因此旧前端文件不会混进新版本：

```bash
mv "$APP_RUNTIME_DIR" "$BACKUP_DIR/runtime"
if ! mv "$NEXT_RUNTIME_DIR" "$APP_RUNTIME_DIR"; then
  mv "$BACKUP_DIR/runtime" "$APP_RUNTIME_DIR"
  exit 1
fi
```

整个更新过程不复制或覆盖 env、Compose、Nginx、MySQL、Redis、Kafka 文件。`server-logs` / `runtime-sdk-logs` 卷中可能包含故障恢复数据，也必须保留。独立机器上的 SDK 只需按其原挂载路径备份并替换 `runtime.jar`，连接这套共享环境。

## 5. 启动应用并验收

先解析原配置，再重建应用容器以重新挂载新版文件；**不能只执行 `restart`**。每条成功才执行下一条，未部署 SDK 时跳过客户端命令。副本数沿用 `.env.server` 的 `SERVER_REPLICAS`（默认 2）；原部署使用命令行覆盖副本数的，继续显式保留原值。

```bash
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.server.yml config --quiet
docker compose --project-name tianshu-client --env-file .env.client -f docker/docker-compose.client.yml config --quiet
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.server.yml up -d --force-recreate --wait --wait-timeout 300 server web
docker compose --project-name tianshu-client --env-file .env.client -f docker/docker-compose.client.yml up -d --force-recreate --wait --wait-timeout 300 runtime-sdk
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.server.yml ps -a
docker compose --project-name tianshu-client --env-file .env.client -f docker/docker-compose.client.yml ps -a
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.server.yml logs --tail 100 server web
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.server.yml exec -T web nginx -t
```

所有 server 副本均应 healthy。打开控制台，验证登录、项目、规则详情、设计器、规则测试和执行日志，并由业务侧验收项目鉴权、SDK 同步及日志上报后恢复流量。本流程可能同时重启两个副本，不承诺零停机。不同 Compose 文件操作同一项目可能提示 orphan，不能加 `--remove-orphans` 或执行 `down -v`。

Nginx Proxy Manager 继续管理 HTTPS、证书与原端口，不改宿主机 Nginx、UFW、Fail2ban 或安全组。管理员恢复仍通过原 server 执行下列维护命令，不更换主密钥、不清空数据：

```bash
docker compose --project-name tianshu-server --env-file .env.server -f docker/docker-compose.server.yml exec -T server java -jar /app/app.jar --console-admin-recovery-code
```

## 6. 回退与旧部署兼容

启动失败先保留日志并核对依赖和迁移结果。确认数据库兼容旧版后，在维护窗口用第 3 节命令停止应用，把失败版 runtime 移到一个新的保留目录，再将 `$BACKUP_DIR/runtime` 恢复到原 `APP_RUNTIME_DIR`，执行第 5 节启动和验收。文件回退不会撤销 SQL；不兼容时使用验收过的备份恢复到新库，再安排应用回退。保留旧制品、完整发布包、迁移记录与数据库备份直到验收结束。

- 旧环境使用 `docker-compose.full.yml` 或 `docker-compose.yml` 时，继续使用原项目和文件，停止/重建时只指定应用服务，并在 `up` 加 `--no-deps`，防止依赖被带起或重建；full 的 SDK 服务名是 `sdk`，external 是 `runtime-sdk`。不要直接切换部署方案。
- 原系统仍有 `runtime-http` 时，先把调用方迁移到 Server API 和项目凭据，按旧 Compose 停止并移除这个旧服务，再单独升级部署定义；这属于一次性兼容迁移，不在普通 JAR/dist 更新中自动执行。
- MySQL/Redis 的数据目录是相对原 Compose 文件的绑定挂载；Kafka 使用原项目的命名卷。它们只维护一套，不能随着应用包迁移目录、改项目名或重复启动 full 方案。
