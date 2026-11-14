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

历史统计索引升级需在停应用并备份后执行 [2026-11-13-history-field-index.sql](2026-11-13-history-field-index.sql)，从当前逻辑执行日志回填稳定字段索引。旧实验日志缺少完整的字段身份快照，从升级后的正式实验开始计入。

| 顺序 | 脚本 | 适用范围与执行要求 |
|---|---|---|
| 1 | `2026-11-04-account-record-isolation.sql` | 已有业务库升级到账户记录隔离版本前执行；要求旧版业务表已存在。补齐账户归属字段、系统常量标记及账单汇总索引，不推测历史记录归属。 |
| 2 | `2026-11-04-operation-log-intervals.sql` | 已有 `console_operation_log` 表且尚未完成操作日志区间升级的数据库；可在新版启动前预先执行，保留历史记录及未知离开时间。 |
| 3 | `2026-11-11-decision-result-system-constants.sql` | 已有库缺少通过、人审、拒绝系统常量时执行；要求 `rule_variable.system_constant` 及唯一索引已存在。幂等插入 `DECISION_PASS=0`、`DECISION_REVIEW=2`、`DECISION_REJECT=1`；保留已有同名字段的 ID、取值和系统标记。执行后核对末尾查询结果，同名业务字段冲突由管理员处理，不自动覆盖。 |
| 4 | [2026-11-11-er-foreign-keys.sql](2026-11-11-er-foreign-keys.sql) | 为已有业务表增加客户端 ER 图所读取的物理外键。要求 MySQL 8、InnoDB、稳定 ID 列及父表已就绪，操作见下节。 |
| 5 | [2026-11-11-schema-comments.py](2026-11-11-schema-comments.py) | 已有库的表或字段缺少中文注释，或使用本次修订的四条旧表注释时执行。使用 Python 3.8+ 标准库，按目标库的实际字段定义生成 SQL，步骤见「已有库中文注释」。 |

前两份脚本检查列/索引是否存在，第三份按唯一索引幂等补齐缺失常量；可在确认失败原因已修复后重试。这一性质不代表未来所有迁移都可重复执行。涉及历史数据变更时，须在对应脚本说明前置版本、预检、执行顺序及回退限制。每次新增迁移同步更新本表，已发布脚本不改写，修正另加新脚本。

## 数据库客户端 ER 图

备份并暂停写入后，使用支持 `DELIMITER` 的 MySQL 客户端，在选中的业务库内执行第 4 项。迁移账号需要读取表结构与数据、`ALTER`、`REFERENCES`、`CREATE TEMPORARY TABLES` 及创建、调用、删除迁移过程的权限。保持 `FOREIGN_KEY_CHECKS=1`。旧版本尚未创建的子表由新版服务按 `schema.sql` 首次建表。

脚本先检查整份关系清单。如果存在孤立引用，结果会列出子表、子列、父表以及一个失配的 ID，随后在业务表变更前退出。项目引用使用原 `project_id` 核对，允许全局值 0 或 NULL。应按业务记录核实并修复引用；脚本不会删行或猜测归属。同名异义或已有其他外键约束同一列时，先核对该约束定义。DDL 阶段中断后可在修复原因后重跑，已安装的匹配约束会被识别。为大表添加存储生成列、校验外键和建立索引可能重建或锁定表，应在维护窗口为操作预留时间和磁盘空间。

迁移成功后，脚本末尾返回已安装的关系列表。也可以在客户端执行以下查询，随后刷新表元数据，重新打开 ER 图。检查某条连线时可按 `TABLE_NAME` 过滤，例如 `rule_definition` 或 `rule_data_object_field`。

```sql
SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY TABLE_NAME, COLUMN_NAME;
```

对有有效引用的主记录，`RESTRICT` 阻止物理删除。标为 `CASCADE` 的从属关系在父记录物理删除时清理，例如规则内容、变量选项、项目凭据及其令牌。业务的状态变更和逻辑删除不会触发级联。

## 已有库中文注释

第 5 项从本次发布的 `docker/runtime/server.jar` 读取权威中文注释，从目标库的结构导出读取实际列定义。同名表、列缺少中文注释时生成 `ALTER TABLE`，并更新四条能精确识别的旧表注释。已有自定义中文说明、额外列及尚未建表的旧版模块均保留原状。

停止写入、完成备份及前面适用的结构迁移后，在同一维护窗口导出、生成并执行。MySQL 的 `MODIFY COLUMN` 需要重述列定义，因此每次执行和失败重试都要重新导出目标库。下面的 `RELEASE_DIR` 指向本次解压目录，`BACKUP_DIR` 指向此次维护的备份目录。

```bash
set -e
docker compose --project-name tianshu-server --env-file config/.env.server \
  -f docker/docker-compose.mysql.yml exec -T mysql sh -ec \
  'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --default-character-set=utf8mb4 --no-data --skip-triggers --no-tablespaces --set-gtid-purged=OFF --host=127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE"' \
  > "$BACKUP_DIR/schema-before.sql"
python3 "$RELEASE_DIR/migrations/2026-11-11-schema-comments.py" \
  "$RELEASE_DIR/docker/runtime/server.jar" "$BACKUP_DIR/schema-before.sql" > "$BACKUP_DIR/schema-comments.sql"
docker compose --project-name tianshu-server --env-file config/.env.server \
  -f docker/docker-compose.mysql.yml exec -T mysql sh -ec \
  'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 --binary-mode=1 --host=127.0.0.1 --user="$MYSQL_USER" --database="$MYSQL_DATABASE"' \
  < "$BACKUP_DIR/schema-comments.sql"
```

执行后重新导出结构并再次生成，输出文件应为零字节。全新库由 `schema.sql` 直接创建，仓库的 `scripts/quality-gates/check-schema-comments.sql` 可在 MySQL 8 中列出缺少中文注释的基本表和字段。

已有库升级断点和空间统计缓存时，在停应用、完成备份后执行 [2026-11-13-history-recovery.sql](2026-11-13-history-recovery.sql)。该迁移沿用现有历史字段索引，增加小时变更版本表及实验原始请求列，允许重复执行。已有实验缺少原请求时仍可携原请求和订单号重试。

升级生产分流异常策略时，执行 [2026-11-14-experiment-routing-error-policy.sql](2026-11-14-experiment-routing-error-policy.sql)。已有配置沿用兜底行为，新增实验可在冠军挑战页选择等待修复或执行兜底组。

升级名单动作执行条件和来源追踪时，执行 [2026-11-14-list-action-source.sql](2026-11-14-list-action-source.sql)，为变更日志和幂等回执补齐来源快照字段。历史记录保留原值。

升级到持久执行队列时，在上述迁移后执行 [2026-11-13-execution-queue.sql](2026-11-13-execution-queue.sql)。旧订单继续按已有断点恢复，后台仅领取新版本登记的待执行订单。

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
