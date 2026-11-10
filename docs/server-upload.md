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

## 日常更新：五行命令

ZIP 和同名校验文件上传到外层固定目录。完成下面“一次性目录整理”后，每次更新只需要这五行，替换包文件名即可：

```bash
cd /root/tianshu-decision-engine &&
PKG=包文件名.zip &&
sha256sum -c "$PKG.sha256" && test ! -L deploy-prod && mkdir -p deploy-prod/releases && test ! -L deploy-prod/releases &&
RELEASE_DIR=$(mktemp -d "$PWD/deploy-prod/releases/update-XXXXXXXX") && python3 -X utf8 -m zipfile -e "$PKG" "$RELEASE_DIR" &&
python3 -X utf8 "$RELEASE_DIR/deploy.py" --target "$PWD"
```

新包入口文档使用 ASCII 文件名 `README-DEPLOY.md`，正文仍为 UTF-8 中文。推荐按上述命令用 Python 标准库解压，避免解压工具与 locale 对中文文件名处理不一致。旧包若报 `README-部署.md` 缺失或校验失败，先核对 ZIP 的 SHA256，再用上述命令重新解压到新的 releases 子目录；不要覆盖运行目录、手工修改校验清单或跳过校验。

脚本读取 `config/` 内的 env 和外层固定 Compose；只替换 `deploy-prod/current` 的 JAR、前端及版本信息，按原项目名和副本数重建应用并等待健康检查。MySQL、Redis、Kafka 不停止、不重建，外层 env 备份不复制、不比对、不覆盖。

**只有新版健康检查通过，更新前的 current 才成为 previous。** 已有 previous 移到本次 `history/update-*/older-previous/`，不自动删除。升级失败会恢复当前应用文件并保持应用停止，previous 保持原样；文件恢复不代表数据库回退。无 SDK 的环境不启动 SDK，已有 SDK 自动更新；独立 SDK 可使用 `--client-only`。

## 一次性目录整理：适用于现有 deploy-prod 环境

这一步只搬迁目录并恢复原来正在部署的版本，**不安装新 JAR，不运行本次包的数据库迁移脚本**。不要把外层备份 env 复制进 deploy-prod。

先按上面的前四行校验并解压新包，然后把第五行改为：

```bash
python3 -X utf8 "$RELEASE_DIR/deploy.py" --target "$PWD" --adopt-layout
```

整理完成后，在同一会话执行普通更新即可：

```bash
python3 -X utf8 "$RELEASE_DIR/deploy.py" --target "$PWD"
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

本页的自动整理面向已经运行的旧 deploy-prod 安装。尚未安装的机器先按 [Docker 部署说明](../docker/README.md) 初始化原部署；旧平铺安装的手工操作与回退保留在 [旧版维护说明](deployment-flat-legacy.md)，不要把其中根目录 env 的用法套到整理后的环境。

## 有数据库迁移的应用更新

迁移 SQL 单独位于本次包的 `migrations/`。选择适用且尚未执行的脚本后，在普通更新的最后一行追加：

```text
--migration-script /root/tianshu-decision-engine/本次迁移.sh
```

迁移脚本在应用停止后、替换制品前执行，须先完成数据库备份，遇错非零退出；跨机器旧节点须提前停止。Bash 脚本可读取 `TIANSHU_RELEASE_DIR`、`TIANSHU_DEPLOY_DIR`、`TIANSHU_BACKUP_DIR`。共享基础设施在普通更新和 SQL 迁移期间保持运行，不自动遍历历史 SQL。若本包有 `migrations/upgrade.sh` 而没有显式指定迁移脚本，普通更新会在停机前拒绝继续。

目录整理参数 `--adopt-layout` 不能与数据库迁移参数一起使用。历史迁移说明见包内 `migrations/README.md`；例如尚未完成 2026-11-04 账户记录隔离的旧库，升级前需执行对应 SQL。

## 验收与回退

更新成功后检查登录、项目列表、规则详情、设计器、规则测试和执行日志，并由调用方验证项目鉴权、SDK 同步及日志上报。两个 server 可能同时重启，本流程不承诺零停机。

`previous/` 始终用于保存上一次成功更新前的应用内容，不能通过换回文件来撤销数据库迁移。需要回退时先确认数据库兼容性，在维护窗口停止应用，保留失败版本后恢复 previous 中的制品，再以原 Compose/项目名重建并验收。不要直接对整个安装目录做删除或覆盖。失败或强制中断遗留 `.deploy.lock` 时，先确认没有其他部署进程；不能在另一个更新仍运行时删除锁。
