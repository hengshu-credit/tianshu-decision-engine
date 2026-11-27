# 数据库初始化脚本

表结构统一由 server 启动时对照 `schema.sql` 升级和复核。运行时所需的数据对象脚本名和内置 Mock 目录由 server 在同一数据库命名锁内补齐，保留用户已有别名、绑定和业务修改时间。中文注释比对工具位于 `scripts/quality-gates/schema-comments.py`。

`schema.sql` 是服务端启动使用的权威结构初始化脚本，包含表结构、逻辑执行日志视图和按变量唯一索引幂等补齐的系统常量。服务端每次启动在目标库命名锁内按真实表、列、索引、外键和分区比对包内 schema；先补齐无损结构差异并复核，再执行视图和常量。日常运行账号还需当前库的 `CREATE TEMPORARY TABLES`；操作日志前缀为 `[SchemaUpgrade]`。JDBC URL 使用 `createDatabaseIfNotExist=true`，首次连接时会创建 `MYSQL_DATABASE`；脚本随后使用当前连接选中的数据库，不在 SQL 中创建或切换数据库。所有表显式使用 `utf8mb4`，首次部署账号需要建库权限，日常运行账号至少需要目标库的建表/变更权限。

两个逻辑视图的定义统一在 `schema.sql` 中。自定义初始化脚本完成兼容补列后，服务端使用同一定义先创建日志视图，再创建统计视图。内置 Mock 基础规则在结构复核后由启动初始化器在同一数据库命名锁内补齐，旧规则的 ID、绑定与用户修改继续保留。

`initial-data.sql` 是可选的变量与数据对象初始配置。服务端设置 `RULE_ENGINE_INITIAL_DATA_ENABLED=true` 后，每次启动执行该脚本；变量目标写入 `GLOBAL + project_id=0`，但只要已有任意 `GLOBAL` 同 `var_code` 就跳过，数据对象按 `GLOBAL + project_id=0 + object_code`、对象字段按对象编码和字段编码查找，只插入缺失配置，已有记录（包括人工修改）不会被覆盖，也不依赖自增 ID。需要替换内容时通过 `RULE_ENGINE_INITIAL_DATA_LOCATION` 指定另一个幂等 SQL 资源。

样例数据只保留 `docker/rule-engine-mysql/data-tianshu-example.sql`，需在服务端启动完成并确认目标数据库后单独导入；Docker Compose 默认不导入，启用 `example-data` profile 后才会执行只插入的导入。

旧库如果此前尚未完成历史统计索引、账单归属回填或五参数 PDO 函数转换，这些按版本选择的操作不会随新包自动执行。可在停写和备份后按对应旧版本的条件处理。[最后一次单独交付这些工具的版本](https://github.com/hengshu-credit/tianshu-decision-engine/tree/e2cb29fe0297952b938908e2890db609f048628f)
