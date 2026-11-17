# 数据库初始化脚本

常规结构变更由服务启动时对照 `schema.sql` 自动处理。历史数据回填、外键整库预检和单独补注释的工具维护在 [`migrations/`](migrations/README.md)。发布包将其原样交付到顶层 `migrations/`，不需要从 JAR 中提取。按脚本适用范围，在停止旧应用并完成备份后、新版启动前单独执行；无适用迁移时只更新 JAR 和前端，MySQL、Redis、Kafka 保持原环境。

`schema.sql` 是服务端启动使用的权威结构初始化脚本，包含表结构、逻辑执行日志视图和按变量唯一索引幂等补齐的系统常量。服务端每次启动在目标库命名锁内按真实表、列、索引、外键和分区比对包内 schema；先补齐无损结构差异并复核，再执行视图和常量。日常运行账号还需当前库的 `CREATE TEMPORARY TABLES`；操作日志前缀为 `[SchemaUpgrade]`。JDBC URL 使用 `createDatabaseIfNotExist=true`，首次连接时会创建 `MYSQL_DATABASE`；脚本随后使用当前连接选中的数据库，不在 SQL 中创建或切换数据库。所有表显式使用 `utf8mb4`，首次部署账号需要建库权限，日常运行账号至少需要目标库的建表/变更权限。

`logical-execution-log-view.sql` 和 `logical-execution-statistics-view.sql` 供自定义初始化脚本的兼容流程使用，依次重建逻辑执行日志视图和统计视图。

`initial-data.sql` 是可选的变量与数据对象初始配置。服务端设置 `RULE_ENGINE_INITIAL_DATA_ENABLED=true` 后，每次启动执行该脚本；变量目标写入 `GLOBAL + project_id=0`，但只要已有任意 `GLOBAL` 同 `var_code` 就跳过，数据对象按 `GLOBAL + project_id=0 + object_code`、对象字段按对象编码和字段编码查找，只插入缺失配置，已有记录（包括人工修改）不会被覆盖，也不依赖自增 ID。需要替换内容时通过 `RULE_ENGINE_INITIAL_DATA_LOCATION` 指定另一个幂等 SQL 资源。

```bash
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USERNAME" -p "$MYSQL_DATABASE" < schema.sql
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USERNAME" -p "$MYSQL_DATABASE" < docker/rule-engine-mysql/data-tianshu-example.sql
```

样例数据只保留 `docker/rule-engine-mysql/data-tianshu-example.sql`，需在服务端启动完成并确认目标数据库后单独导入；Docker Compose 默认不导入，启用 `example-data` profile 后才会执行只插入的导入。
