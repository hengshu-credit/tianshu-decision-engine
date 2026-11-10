# 独立数据库迁移

这里是已有数据库的增量迁移脚本唯一维护位置。发布时原样复制到 ZIP 顶层的 `migrations/`，并纳入 `SHA256SUMS.txt`；无需解开 JAR 或从源码中寻找 SQL。本目录不是初始化或样例数据，服务不会自动遍历执行这些文件。

自动部署时用 `python3 deploy.py --target 原安装目录 --migration-script /绝对路径/本次迁移.sh` 接入已选定的迁移脚本，在停应用后、替换前执行；Bash 脚本须先完成数据库备份，遇错非零退出。可通过 `TIANSHU_RELEASE_DIR` 访问本包 SQL，`TIANSHU_DEPLOY_DIR` 访问外层 `config/` 生效 env 与 `docker/` Compose，`TIANSHU_BACKUP_DIR` 记录备份与迁移结果。不要把所有历史 SQL 自动拼接运行。

## 执行时机

1. 对照当前已部署版本、数据库结构与运维执行记录，选择本次跨版本升级尚未执行的适用脚本。不要盲目遍历目录执行全部历史 SQL。
2. 在维护窗口暂停业务流量，停止连接这套数据库的所有旧 server 和相关 SDK runtime，等待写入退出；MySQL、Redis、Kafka 保持运行。完成数据库可恢复备份后执行迁移。
3. 使用选定的目标业务库逐个执行 SQL；同一文件必须在一个 MySQL 连接内执行。客户端遇错即停，不使用 `--force`。MySQL DDL 可能已隐式提交，失败后先检查已完成的部分，不能假定整个脚本已回滚。
4. 每条成功后记录脚本文件名、SHA256、目标库、执行时间和结果，再安装/启动新版 JAR 与前端。失败时保持应用停止，不继续启动新版。

若已完成全部适用迁移，本次只更新应用制品；全新数据库由服务启动执行 `schema.sql`，不执行这些面向旧库的迁移。`schema.sql` 和 `SchemaSyncService` 的兼容补齐不能代替有明确要求的升级脚本。

## 已有脚本与顺序

| 顺序 | 脚本 | 适用范围与执行要求 |
|---|---|---|
| 1 | `2026-11-04-account-record-isolation.sql` | 已有业务库升级到账户记录隔离版本前执行；要求旧版业务表已存在。补齐账户归属字段、系统常量标记及账单汇总索引，不推测历史记录归属。 |
| 2 | `2026-11-04-operation-log-intervals.sql` | 已有 `console_operation_log` 表且尚未完成操作日志区间升级的数据库；可在新版启动前预先执行，保留历史记录及未知离开时间。 |
| 3 | `2026-11-11-decision-result-system-constants.sql` | 已有库缺少通过、人审、拒绝系统常量时执行；要求 `rule_variable.system_constant` 及唯一索引已存在。幂等插入 `DECISION_PASS=0`、`DECISION_REVIEW=2`、`DECISION_REJECT=1`；保留已有同名字段的 ID、取值和系统标记。执行后核对末尾查询结果，同名业务字段冲突由管理员处理，不自动覆盖。 |

前两份脚本检查列/索引是否存在，第三份按唯一索引幂等补齐缺失常量；可在确认失败原因已修复后重试。这一性质不代表未来所有迁移都可重复执行。涉及历史数据变更时，须在对应脚本说明前置版本、预检、执行顺序及回退限制。每次新增迁移同步更新本表，已发布脚本不改写，修正另加新脚本。

## 执行示例（Linux）

在已整理的外层固定部署目录执行（旧平铺模式仍使用原 env 路径），`RELEASE_DIR` 是已校验的新包解压目录。以下仅示范执行第一条适用迁移，已执行过则跳过。使用当前 MySQL 所属的 Compose 项目名、文件与配置，不能为迁移新建另一套 MySQL。

```bash
docker compose --project-name tianshu-server --env-file config/.env.server \
  -f docker/docker-compose.mysql.yml exec -T mysql sh -ec \
  'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 --binary-mode=1 --host=127.0.0.1 --user="$MYSQL_USER" --database="$MYSQL_DATABASE"' \
  < "$RELEASE_DIR/migrations/2026-11-04-account-record-isolation.sql"
```

以上使用现有容器内的应用账号，需有对应 DDL 权限，且其数据库/账号应与 server 的实际连接一致；已有卷的实际账号以数据库为准。外部 MySQL 由运维使用受保护的客户端连接配置执行同一 SQL，勿输出密码或把 `.env.server` 当 Shell 脚本执行。后续适用脚本逐条执行，确认返回码为 0 再继续。

回退 JAR 不会撤销已执行的数据库迁移。先确认旧版兼容性；不兼容时按已验收备份恢复到不同且不存在的库，再安排应用回退。
