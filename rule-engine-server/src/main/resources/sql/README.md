# 数据库初始化脚本

`schema.sql` 是完整结构脚本，`data.sql` 是对应的基础数据快照。两个脚本都使用当前 MySQL 连接选中的数据库，不创建或切换数据库，因此可以直接复用 `MYSQL_DATABASE`。

```bash
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USERNAME" -p "$MYSQL_DATABASE" < schema.sql
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USERNAME" -p "$MYSQL_DATABASE" < data.sql
```

`data-example.sql`、`data-tianshu-example.sql` 和 `data-third-party-api.sql` 是可选示例或模板数据，需在 `schema.sql` 之后单独导入。Docker Compose 的空 MySQL 数据卷会自动按 `01-schema.sql`、`02-data.sql` 顺序执行结构和基础数据。
