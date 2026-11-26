# 共享环境与应用版本部署

**当前实现为重启式更新，会中断应用。** 针对 16 GB / 4 核或更高配置，目标方案采用常驻网关和蓝绿双版本，设计及实施缺口见 [蓝绿部署设计](blue-green-deployment-design.md)。本页命令尚不具备在线切流能力。

固定外层目录为 `/root/tianshu-decision-engine`。外层原有 `.env.server`、`.env.client` 只是备份，**不作为启动配置**；当前运行配置来自旧 `deploy-prod` 内的 env。一次性整理后，生效配置放到外层 `config/`，MySQL、Redis、Kafka 和固定部署定义放到外层 `docker/`，内层只管理应用版本。

```text
/root/tianshu-decision-engine/
├── .env.server / .env.client        # 原来的备份，保留不改、不用于启动
├── config/
│   ├── .env.server                  # 从原 deploy-prod 复制的生效配置
│   └── .env.client                  # SDK 生效配置
├── docker/                          # 固定部署定义，普通更新不覆盖
│   ├── docker-compose.server.yml
│   ├── docker-compose.client.yml
│   ├── docker-compose.mysql.yml
│   ├── docker-compose.redis.yml
│   ├── docker-compose.kafka.yml
│   ├── nginx/
│   ├── rule-engine-mysql/           # 原配置、数据和日志
│   ├── rule-engine-redis/           # 原配置、数据和日志
│   └── rule-engine-kafka/           # 原启动配置；数据继续使用原命名卷
├── deploy-prod/
│   ├── current/                     # 当前运行版本
│   │   ├── server.jar
│   │   ├── runtime.jar
│   │   ├── dist/
│   │   └── BUILD-INFO.txt
│   ├── previous/                    # 上一次成功更新前的完整 current
│   ├── releases/update-XXXXXXXX/    # 新包独立解压目录，含 deploy.py 和 migrations/
│   └── history/update-XXXXXXXX/     # 历史备份、失败尝试及更早的 previous
├── legacy/before-layout-XXXXXXXX/   # 一次性整理前的旧 env、文档及源码
├── .deployment-layout.json          # 原项目名、部署文件和服务记录，不含密码
├── tianshu-deployment-版本.zip
└── tianshu-deployment-版本.zip.sha256
```

Compose 自动兼容现代 `docker compose` 与 `docker-compose`，优先前者；要求支持 `config --format json`、`up --wait`，不支持旧 Python Compose v1。服务器需要 Python 3.8+ 标准库和 Docker，无需 Maven、Node.js 或第三方 Python 包。构建入口见 [跨平台发布构建](release-packaging.md)。

## 模型与素材上传扫描

含 PMML、ONNX 原件的导入需要每个 server 副本都能访问 ClamD。已有可信内网扫描器时，直接填写该服务的主机和端口。需要随本安装部署扫描器时，将包内的 `docker/docker-compose.clamd.yml` 放到安装目录的 `docker/`。该服务保留病毒库卷，使用引擎现有网络，未发布宿主机扫描端口。

1. 运行 `docker ps --filter label=com.docker.compose.service=server --format '{{.Names}} {{.Networks}}'` 核对当前 server 的网络名。在生效的服务端 env 中将 `ENGINE_NETWORK` 填为同一个网络，`UPLOAD_ANTIVIRUS_HOST` 填为 `upload-clamd`，`UPLOAD_ANTIVIRUS_PORT` 填为 `3310`，`UPLOAD_ANTIVIRUS_TIMEOUT_MS` 填为 `30000`。已有扫描服务使用其实际地址。多套独立安装在同一 Docker 主机上时，请给各自的扫描器设置独立的 `CLAMD_PROJECT`。
2. 平铺安装使用下面的命令启动扫描器。已整理为共享布局的安装，把 `--env-file .env.server` 改为 `--env-file config/.env.server`。独立 Compose v2+ 可使用对应的 `docker-compose` 入口。

```bash
docker compose --env-file .env.server -f docker/docker-compose.clamd.yml up -d --wait --wait-timeout 900 upload-clamd
```

3. 待扫描器健康后，在维护窗口使用本安装的 `install-release.sh` 和发布 ZIP 重建应用，使全部 server 副本读取新环境。沿用原 Compose 项目、覆盖文件和副本数。普通容器 restart 不会更新环境变量。可在每个 server 容器内执行 `printenv UPLOAD_ANTIVIRUS_HOST` 核对实际值。若仍为空，请检查原 server Compose 的 environment 段是否传入上述三个变量，以及最后一个覆盖文件是否改写它们。
4. 返回「离线迁移」重新预检。扫描服务的可用性检查使用小样本，正式写入时仍逐个检测模型原件。当前三项目合集的最大原件约 249 MiB，配置文件为单文件与传输流预留 256 MiB。扫描器健康依赖可用的病毒库，请确保首次初始化及后续更新具备获取病毒库的条件。

## 停止部署

将与发布包一同提供的 `compose-down.sh` 放在安装目录，在该目录运行 `bash compose-down.sh --dry-run` 可查看目标项目和服务。运行 `bash compose-down.sh` 停止并移除 server、web 及已部署的 SDK 容器。需要同时关闭本安装管理的 MySQL、Redis、Kafka 等服务时，使用 `bash compose-down.sh --all`，也可加 `--dry-run` 预览。两种模式均保留数据卷和宿主机文件。停机脚本优先使用 `docker compose`，也支持独立的 `docker-compose` v2+。对于只能整组执行 `down` 的旧版 v2，脚本按选定服务执行 `stop` 和 `rm --force`，保留网络。

## 首次部署和日常更新

在安装目录运行同一个 `install-release.sh`。首次将发布包旁的入口脚本、ZIP 和同名 `.zip.sha256` 上传到该目录并准备实际的服务端 env、主密钥和可连接的 MySQL、Redis。安装目录已有当前入口时，以后只需上传新 ZIP 和校验文件，替换命令中的包名即可。

```bash
cd /root/tianshu-decision-engine
bash install-release.sh tianshu-deployment-具体版本.zip
```

入口先校验指定 ZIP，再调用这次包内的 `deploy.py`。首次部署补齐应用 Compose 和 Nginx 配置，空库由 server 创建表和系统常量，随后在控制台注册超管。已有安装按原项目名和副本数更新 JAR 与前端。平铺目录读取根目录的有效 env，已整理布局读取 `config/`。可选变量和对象由生效 env 中的 `RULE_ENGINE_INITIAL_DATA_ENABLED` 控制。仍在使用强制关闭该开关的旧版入口时，可用本次随包脚本替换一次。

入口为本次运行启用 `SPRING_SQL_INIT_MODE=always`。server 根据新包中的 `schema.sql` 自动检查目标库。更新前请备份数据库并确认应用账号有 `CREATE TEMPORARY TABLES` 和所需 DDL 权限。server 健康检查结束时，终端显示实际改表对象和结果，`applied=0` 表示受管表已符合要求。完整的 `[SchemaUpgrade]` 日志在终端列出的本次备份目录里，文件名为 `schema-upgrade.log`。平铺安装的备份在 `backups/update-*/`，已整理布局在 `deploy-prod/history/update-*/`。发布说明要求回填历史数据等显式迁移时，在同一命令后追加 `--migration-script /绝对路径/本次迁移.sh`。

经 Nginx Proxy Manager 部署时，首次配置 web 或修复已有鉴权日志的客户端 IP，请同步合并外层 Nginx 与 Compose 的模板挂载改动，设置生效 env 中的 `WEB_TRUSTED_PROXY` 后单独重建 web。具体配置和按请求核对入库地址的步骤见包内 `docker/README.md` 的「项目鉴权日志的客户端 IP」。

**只有新版健康检查通过，更新前的 current 才成为 previous。** 已有 previous 移到本次 `history/update-*/older-previous/`，不自动删除。升级失败会恢复当前应用文件并保持应用停止，previous 保持原样；文件恢复不代表数据库回退。已有 SDK 随应用一起更新。独立 SDK 机器使用已解压包的 `deploy.py --client-only`。

<details>
<summary>已整理布局的手工解包步骤，供维护时直接调用包内工具</summary>

```bash
cd /root/tianshu-decision-engine &&
PKG=包文件名.zip &&
sha256sum -c "$PKG.sha256" && test ! -L deploy-prod && mkdir -p deploy-prod/releases && test ! -L deploy-prod/releases &&
RELEASE_DIR=$(mktemp -d "$PWD/deploy-prod/releases/update-XXXXXXXX") && python3 -X utf8 -m zipfile -e "$PKG" "$RELEASE_DIR" &&
python3 -X utf8 "$RELEASE_DIR/deploy.py" --target "$PWD"
```

`README-DEPLOY.md` 的正文使用 UTF-8。每次手工解压使用独立目录，并沿用安装目录中的配置和项目名。

</details>

## 一次性目录整理：适用于现有 deploy-prod 环境

这一步只搬迁目录并恢复原来正在部署的版本，**不安装新 JAR，不运行本次包的数据库迁移脚本**。不要把外层备份 env 复制进 deploy-prod。

展开上面的手工解包步骤，在同一 Bash 会话执行前四行，再运行目录整理命令。

```bash
python3 -X utf8 "$RELEASE_DIR/deploy.py" --target "$PWD" --adopt-layout
```

整理完成后，在同一安装目录运行统一入口。

```bash
bash install-release.sh "$PKG"
```

整理过程会：

1. 从旧容器读取原项目名、部署文件、服务和副本数；核对实际挂载与旧 Compose 一致。缺失容器、路径不符或数据卷名称不符时，在停止服务前拒绝整理，不猜测项目名。
2. 从 `deploy-prod/.env.server`、`.env.client` 复制生效配置到 `config/`，只把 `RUNTIME_DIR` 改为 `../deploy-prod/current`；外层旧备份原样保留。
3. 在维护窗口先停应用，再停本机相关 MySQL/Redis/Kafka。将原 `deploy-prod/docker` 移到外层 `docker`，其中 runtime 移到 `deploy-prod/current`；MySQL/Redis 数据随原目录迁移，不重新初始化，Kafka 继续使用原命名卷。
4. 再次核对搬迁后的挂载路径；若配置仍写死旧数据绝对路径则恢复旧布局，避免创建空数据目录。使用原项目名先恢复基础设施，再恢复原应用并检查健康；旧 env、文档、源码及许可证归档到外层 `legacy/`。原服务端和 SDK journal 命名卷保留。目录切换失败时，先停止新容器，再恢复旧目录和原部署；若停止或恢复失败，保留现场并明确报错，不删除数据。

如果旧 `runtime-sdk` 的容器仍绑定外层 `docker/runtime/runtime.jar`，而这个宿主机路径已消失，脚本支持恢复该已确认的历史布局：在停止任何服务前，先核对各 SDK 副本的 JAR 摘要和当前运行参数，再通过无 TTY 的二进制流从仍运行的容器取回 JAR，复核摘要后保存原内层 JAR 与 env，修正内层 `.env.client` 的 `RUNTIME_DIR=./runtime` 并继续整理。**以内核中仍挂载的运行版 JAR 为准，不能用摘要不同的发布目录 JAR 冒充旧版本。** 多个副本版本不同、复制摘要不符、运行参数与配置不符或其他自定义路径，仍在停机前拒绝处理。不要先停止/删除旧 SDK 容器，也不要直接改路径后强制重建。

原文件与恢复摘要保存在 `legacy/before-layout-*/sdk-mount-recovery/`，不会覆盖外层 env 备份。若 SDK 恢复已成功、后续目录整理失败，回退到旧目录时仍保留取回的运行版 JAR 和修正后的有效路径；不能再把 SDK 指向已失效的外层路径。原内层文件与配置始终保留在恢复备份中。此恢复不修改新发布包或其校验清单。

**第一次整理会有维护窗口，以后的普通更新只动应用。** 提前完成数据库可恢复备份；二进制决策制品的 MySQL 备份使用 `--hex-blob` 配合 `--result-file` 或容器内原始管道。外部 MySQL/Redis/Kafka 不会被此命令管理；只整理本机旧目录对应的容器。自定义制品路径、目录外 override、外层已有 docker/config 或其他非标准结构会被拒绝，避免覆盖现有环境。

整理后正常运维用 `--env-file config/.env.server` 或 `config/.env.client`，Compose 文件仍在 `docker/`。项目名以原部署和 `.deployment-layout.json` 记录为准，不能改名，也不要执行 `down -v` 或 `--remove-orphans`。Nginx Proxy Manager、HTTPS、端口和主密钥保持原值。

目录整理适用于已经运行的旧 deploy-prod 安装。新机器按 [Docker 部署说明](../docker/README.md) 准备共享服务后，直接使用本页的统一入口。旧平铺安装的手工操作与回退见 [旧版维护说明](deployment-flat-legacy.md)。

## 有数据库迁移的应用更新

迁移 SQL 单独位于本次包的 `migrations/`。选择适用且尚未执行的脚本后，在 `bash install-release.sh 发布包.zip` 后追加以下参数。

```text
--migration-script /root/tianshu-decision-engine/本次迁移.sh
```

迁移脚本在应用停止后、替换制品前执行，须先完成数据库备份，遇错非零退出；跨机器旧节点须提前停止。Bash 脚本可读取 `TIANSHU_RELEASE_DIR`、`TIANSHU_DEPLOY_DIR`、`TIANSHU_BACKUP_DIR`。共享基础设施在普通更新和 SQL 迁移期间保持运行，不自动遍历历史 SQL。若本包有 `migrations/upgrade.sh` 而没有显式指定迁移脚本，普通更新会在停机前拒绝继续。

目录整理参数 `--adopt-layout` 不能与数据库迁移参数一起使用。适用的历史数据回填脚本和前置条件见包内 `migrations/README.md`。

## 验收与回退

更新成功后检查登录、项目列表、规则详情、设计器、规则测试和执行日志，并由调用方验证项目鉴权、SDK 同步及日志上报。两个 server 可能同时重启，本流程不承诺零停机。

`previous/` 始终用于保存上一次成功更新前的应用内容，不能通过换回文件来撤销数据库迁移。需要回退时先确认数据库兼容性，在维护窗口停止应用，保留失败版本后恢复 previous 中的制品，再以原 Compose/项目名重建并验收。不要直接对整个安装目录做删除或覆盖。失败或强制中断遗留 `.deploy.lock` 时，先确认没有其他部署进程；不能在另一个更新仍运行时删除锁。
