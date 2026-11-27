# 数据库升级与历史数据迁移

**业务表结构统一由 server 升级。** 新装和升级都使用发布包中的 `schema.sql`。server 在目标库命名锁内比对表、列、索引、外键和分区，执行可安全应用的变更并复核结构。不兼容或无法无损转换的差异会阻止启动，并在 `[SchemaUpgrade]` 日志中指出对象及原因。

日常更新在安装目录运行 `bash install-release.sh 发布包.zip`。安装入口使用 `SPRING_SQL_INIT_MODE=always`，数据库账号须具备目标库相应的 DDL 权限和 `CREATE TEMPORARY TABLES` 权限。应用就绪后核对结构升级日志。`applied=0` 表示已有结构符合当前定义。独立 SFTP 与 server 共用管理库，升级时先让 server 完成结构复核，再开放上传账户服务。

## 仍需按数据状况判断的操作

| 脚本 | 用途和执行条件 |
|---|---|
| `2026-11-11-er-foreign-keys.sql` | **升级前可选预检。** 核对既有单目标外键的引用、删除规则、项目生成列和孤立记录。出现冲突时报告对象或样本 ID 并中止。脚本使用临时表和短期存储过程。业务表上的外键由 server 创建。 |
| `2026-11-13-history-field-index.sql` | **历史索引回填。** 当旧日志已有稳定字段快照、历史索引尚未完成时，从当前逻辑日志回填根记录和字段投影，再重建小时统计桶。需要 server 已建好目标表和逻辑视图，所有写入节点处于停写状态。 |
| `2026-11-19-bad-rate-pdo.sql` | **旧版评分函数升级。** 仅转换未经修改的五参数出厂定义，保留函数 ID。先核对引用该函数的草稿和待审批配置。冻结制品继续使用原评分口径。 |
| `2026-11-21-test-examples.sql` | **历史 Mock 匹配转换。** 迁移旧内置标识，只清除仍与出厂值相同的匹配配置。常规 server 初始化已包含这项转换。 |
| `2026-11-23-billing-mail.sql` | **历史账单配置归属回填。** 在相关列就绪后，根据 API ID、计费编码、项目范围和生效时间补齐唯一匹配的配置，保留不确定的记录。回填后按需刷新对应日期的日汇总。 |
| `2026-11-23-data-object-script-name.sql` | **历史数据对象名称补齐。** 用原对象编码补齐空脚本名，保留已有别名和更新时间。内置 schema 的启动初始化器会在结构复核后执行。 |

新库启动会直接获得当前结构和内置数据。升级过的旧库可根据运维记录判断哪些数据操作已完成。已完成的历史回填按原发布包的 SHA-256 和运维记录核对，仅移除 DDL 不需要重跑。脚本随包原样复制到顶层 `migrations/` 并纳入 `SHA256SUMS.txt`。本次精简后，以上工具只负责预检或业务数据转换，数据库的 DDL 以本次 server 的 `schema.sql` 为准。

## 维护窗口与执行顺序

1. 核对源版本、目标库、实际列和既有回填记录，暂停流量和后台写入，完成可恢复备份。先处理可能阻止结构升级的孤立引用、非法数据和冲突约束。
2. 已具备所需表、列和视图的升级前操作，可由 `install-release.sh` 的 `--migration-script /绝对路径/本次迁移.sh` 调用。该参数执行自选 Bash 脚本，时机是旧应用停止、制品替换之前。`TIANSHU_RELEASE_DIR`、`TIANSHU_DEPLOY_DIR`、`TIANSHU_BACKUP_DIR` 分别指向已校验的新包目录、安装目录和本次备份目录。
3. **回填依赖新增表、列或视图时，先让新版 server 完成自动升级。** 在恢复业务流量前停止所有可能写入目标表的 server、SDK runtime、作业或消费端，再执行适用的回填。每份 SQL 在一个 MySQL 连接内遇错即停。核对回填结果后启动服务并恢复流量。
4. 记录实际执行的文件名、SHA-256、数据库和结果。若出现失败，保持停写，核查数据库中的实际进度。需要整体回退时先验证可恢复的数据库备份，JAR 回退不会撤销已提交的数据变更。

例如，在结构已就绪且停止写入后，使用已部署的 MySQL 项目执行历史索引回填。将项目名、env 和 Compose 路径换为现场值。

```bash
docker compose --project-name tianshu-server --env-file config/.env.server \
  -f docker/docker-compose.mysql.yml exec -T mysql sh -ec \
  'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 --binary-mode=1 --host=127.0.0.1 --user="$MYSQL_USER" --database="$MYSQL_DATABASE"' \
  < "$RELEASE_DIR/migrations/2026-11-13-history-field-index.sql"
```

## 数据库客户端 ER 图

升级前用外键预检脚本检查原始引用，包括全局项目 ID 为 0 或 NULL 的情形。脚本检查既有约束是否与目标一致。孤立引用须先核实业务归属。执行账号需有读取结构与数据、创建临时表和短期过程的权限。

server 完成结构升级后，可执行下列查询核对实际外键，再刷新客户端的表元数据。

```sql
SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY TABLE_NAME, COLUMN_NAME;
```

对有有效引用的主记录，`RESTRICT` 阻止物理删除。标为 `CASCADE` 的从属关系在父记录物理删除时清理，例如规则内容、变量选项、项目凭据及其令牌。业务的状态变更和逻辑删除不会触发级联。

## 注释检查与文件迁移

`2026-11-11-schema-comments.py` 是 CI 使用的中文注释比对工具，可对照 `schema.sql` 或本次 `server.jar` 与目标库结构导出，按实际列定义生成差异。普通结构注释由 server 自动核对。CI 还使用 `scripts/quality-gates/check-schema-comments.sql` 全量检查表、字段和生成列的中文说明。

全局 `trace_id` 和 `create_time` 随 server 启动补齐，已有同名全局字段保留 ID 并转为引擎只读来源。旧规则若依赖调用方传入这两个字段，应核对入参契约并重新发布。

历史模型原件和冻结制品移入共享文件仓时使用 `--migrate-model-files`，迁移前需同时备份数据库与文件仓。七参数评分函数的参数顺序为 `current_bad_rate, base_score, base_odds, bad_rate, rate, pdo, direction`，基础好坏比和基础坏账率选填其一。
