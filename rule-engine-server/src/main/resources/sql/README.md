# 数据库初始化脚本

`schema.sql` 是服务端启动使用的权威初始化脚本，包含表结构、逻辑执行日志视图和按变量唯一索引幂等补齐的系统常量。服务端每次启动由 Spring 执行；旧库缺少兼容字段时，`SchemaSyncService` 负责补齐结构并再次创建视图。脚本使用当前 MySQL 连接选中的数据库，不创建或切换数据库，因此可以直接复用 `MYSQL_DATABASE`。

```bash
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USERNAME" -p "$MYSQL_DATABASE" < schema.sql
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USERNAME" -p "$MYSQL_DATABASE" < docker/rule-engine-mysql/data-tianshu-example.sql
```

样例数据只保留 `docker/rule-engine-mysql/data-tianshu-example.sql`，需在服务端启动完成并确认目标数据库后单独导入；Docker Compose 默认不导入，启用 `example-data` profile 后才会执行只插入的导入。
