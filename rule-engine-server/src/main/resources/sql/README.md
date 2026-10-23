# 数据库初始化脚本

`schema.sql` 是服务端启动使用的权威结构初始化脚本，包含表结构、逻辑执行日志视图和按变量唯一索引幂等补齐的系统常量。服务端每次启动由 Spring 执行；旧库缺少兼容字段时，`SchemaSyncService` 负责补齐结构并再次创建视图。JDBC URL 使用 `createDatabaseIfNotExist=true`，首次连接时会创建 `MYSQL_DATABASE`；脚本随后使用当前连接选中的数据库，不在 SQL 中创建或切换数据库。所有表显式使用 `utf8mb4`，首次部署账号需要建库权限，日常运行账号至少需要目标库的建表/变更权限。

`initial-data.sql` 是可选的变量与数据对象初始配置。服务端设置 `RULE_ENGINE_INITIAL_DATA_ENABLED=true` 后，每次启动执行该脚本；变量目标写入 `GLOBAL + project_id=0`，但只要已有任意 `GLOBAL` 同 `var_code` 就跳过，数据对象按 `GLOBAL + project_id=0 + object_code`、对象字段按对象编码和字段编码查找，只插入缺失配置，已有记录（包括人工修改）不会被覆盖，也不依赖自增 ID。需要替换内容时通过 `RULE_ENGINE_INITIAL_DATA_LOCATION` 指定另一个幂等 SQL 资源。

```bash
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USERNAME" -p "$MYSQL_DATABASE" < schema.sql
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USERNAME" -p "$MYSQL_DATABASE" < docker/rule-engine-mysql/data-tianshu-example.sql
```

样例数据只保留 `docker/rule-engine-mysql/data-tianshu-example.sql`，需在服务端启动完成并确认目标数据库后单独导入；Docker Compose 默认不导入，启用 `example-data` profile 后才会执行只插入的导入。
