# 数据库初始化脚本

`schema.sql` 是完整结构脚本，服务端启动时由 Spring 自动执行。脚本使用当前 MySQL 连接选中的数据库，不创建或切换数据库，因此可以直接复用 `MYSQL_DATABASE`。

```bash
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USERNAME" -p "$MYSQL_DATABASE" < schema.sql
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USERNAME" -p "$MYSQL_DATABASE" < docker/rule-engine-mysql/data-tianshu-example.sql
```

`data.sql`、`data-example.sql` 和 `data-third-party-api.sql` 是可选快照、示例或模板数据。`data-tianshu-example.sql` 已移到 `docker/rule-engine-mysql/`，需在服务端启动完成并确认目标数据库后单独导入；Docker Compose 默认不导入，启用 `example-data` profile 后才会执行只插入的导入。
