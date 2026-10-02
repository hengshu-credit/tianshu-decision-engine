# MySQL 示例数据

服务端启动时会自动执行 JAR 内的 `schema.sql` 建表。`data-tianshu-example.sql` 是可选的天枢示例数据，不由 Docker Compose 自动导入。

确认服务端已就绪并选中目标数据库后，从仓库根目录执行：

```bash
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USERNAME" -p "$MYSQL_DATABASE" \
  < docker/rule-engine-mysql/data-tianshu-example.sql
```

该脚本会写入演示数据；已有业务数据环境请先备份并确认导入范围。
