# MySQL 示例数据

服务端启动时会自动执行 JAR 内的 `schema.sql` 建表。`data-tianshu-example.sql` 是可选的天枢示例数据，默认不导入；启用 Compose 的 `example-data` profile 时会在 schema 完成后执行插入。

手工导入时，确认服务端已就绪并选中目标数据库后，从仓库根目录执行：

```bash
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USERNAME" -p "$MYSQL_DATABASE" \
  < docker/rule-engine-mysql/data-tianshu-example.sql
```

该脚本会写入演示数据；已有业务数据环境请先备份并确认导入范围。

Compose 可选导入：

```bash
docker compose --profile example-data --env-file .env \
  -f docker/docker-compose.full.yml up -d
```

该 profile 默认关闭，只执行 `INSERT IGNORE`，不会删除、更新或重建表；重复执行会跳过已有唯一键记录。
如果 MySQL 使用独立 Compose，则在 server 已启动并完成 schema 初始化后执行：

```bash
docker compose --profile example-data --env-file .env \
  -f docker/docker-compose.mysql.yml up -d
```
