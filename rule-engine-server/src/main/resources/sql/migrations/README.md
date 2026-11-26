# 独立数据库迁移

这里是已有数据库的增量迁移脚本唯一维护位置。发布时原样复制到 ZIP 顶层的 `migrations/`，并纳入 `SHA256SUMS.txt`；无需解开 JAR 或从源码中寻找 SQL。本目录不是初始化或样例数据，服务不会自动遍历执行这些文件。常规的新增表/字段/索引、无损扩容、结构注释等随 server 启动对照包内 schema 自动升级；需要依赖数据语义的变更仍在此提供显式迁移。

自动部署时在安装目录用 `bash install-release.sh 发布包.zip --migration-script /绝对路径/本次迁移.sh` 接入已选定的迁移脚本，在停应用后、替换前执行；Bash 脚本须先完成数据库备份，遇错非零退出。可通过 `TIANSHU_RELEASE_DIR` 访问本包 SQL，`TIANSHU_DEPLOY_DIR` 访问外层 `config/` 生效 env 与 `docker/` Compose，`TIANSHU_BACKUP_DIR` 记录备份与迁移结果。不要把所有历史 SQL 自动拼接运行。

## 日志检索投递（2026-11-26）

服务启动时为执行和调用日志补齐修订、领取时间、尝试次数及待办索引。外部维护结构时执行 `2026-11-26-elasticsearch-log-delivery.sql`，然后启动新版 server 以重建逻辑日志视图。存量数据由后台按批投递。

## 后台离线迁移（2026-11-25）

服务启动时建立迁移任务、文件分块和执行动作表。外部维护结构时可执行 `2026-11-25-offline-transfer-task.sql`。临时文件和动作明细由服务定期回收，业务迁移审计继续保存在原迁移日志中。

## 账单实际单价范围（2026-11-24）

服务启动时补齐日汇总的最低、最高实际单价。外部维护结构时可执行 `2026-11-24-billing-unit-price-range.sql`。已有单一价格的汇总可直接出账；历史混价汇总需刷新对应日期，才能补齐当时的价格范围。

## 账单公司抬头（2026-11-23）

服务启动时按 schema 补齐可选的公司抬头。外部维护表结构时可执行 `2026-11-23-billing-company.sql`。留空或清除后，发送时读取系统基本信息中已生效的系统名称。

## 月度账单邮件（2026-11-23）

启动自动补齐收件邮箱和日汇总的计费来源、请求数、计费次数及历史单价。已有库可执行 `2026-11-23-billing-mail.sql`，按历史调用的 API ID、专属计费编码、项目范围和配置创建时间补齐唯一匹配的旧外数明细。配置收件邮箱并审批后，按日期刷新需要寄送的历史汇总。只有能确定来源计费配置的记录可发送。

## 数据对象脚本名称（2026-11-23）

服务端使用内置 schema 启动时会补齐历史对象的空脚本名称。使用外部初始化脚本的已有库可执行 `2026-11-23-data-object-script-name.sql`。补齐值为原样的对象编码，已有别名、字段脚本名和冻结版本均保留。

## 控制台单点登录（2026-11-21）

服务端启动时会创建账户会话序号表，已有库也可执行 `2026-11-21-console-single-session.sql`。单点登录默认关闭。审批启用后，每个账户保留最近一次成功登录的会话。升级前签发的会话需重新登录。关闭后允许多处登录，已经失效的会话仍需重新登录。

## 控制台登录时间（2026-11-21）

已有库可执行 `2026-11-21-console-login-activity.sql`；服务端启动时也会幂等建表。新登录记录写入独立的账户活动表，账户列表和详情取新旧记录中较晚的时间。旧库的成功导出日志如已保存完整血缘节点数组，服务端启动时会据此校正资源数量。

## 模型文件迁移（2026-11-20）

新写入的模型及含模型制品使用私有文件仓库。原件字段仍兼容旧版内联内容，字段注释随 schema 自动升级。历史原件的移动涉及文件完整性、扫描和版本快照，使用新服务端 JAR 的 `--migrate-model-files` 命令。先备份数据库和文件仓库，停写后使用相同的配置及挂载执行。原件或制品校验失败会中止迁移，已成功的记录可以安全重跑。操作说明见 [模型存储部署文档](../../../../../../docs/deployment.md)。

## 坏账率 PDO 评分函数（2026-11-19）

已有库使用 `2026-11-19-bad-rate-pdo.sql` 将全局内置函数的原厂五参数定义升级为七参数，并保留原函数 ID。升级前检查使用该 ID 的规则草稿、转换函数和待审批配置，按新版参数顺序调整后再发布。已冻结的五参数制品和直接按函数名调用的五参数脚本仍使用原评分口径。自行修改过的定义由管理员通过函数管理的草稿和审批流程调整。

七参数依次为 `current_bad_rate, base_score, base_odds, bad_rate, rate, pdo, direction`。基础好坏比和基础坏账率只填一个，另一个位置传 `null`。例如 `scoreByBadRatePdo(0.1, 600, null, 0.25, 3, 20, "ASC")` 返回 620，基础好坏比为 3，待评分好坏比为 9。

## 执行时机

1. 对照当前已部署版本、数据库结构与运维执行记录，选择本次跨版本升级尚未执行的适用脚本。不要盲目遍历目录执行全部历史 SQL。
2. 在维护窗口暂停业务流量，停止连接这套数据库的所有旧 server 和相关 SDK runtime，等待写入退出；MySQL、Redis、Kafka 保持运行。完成数据库可恢复备份后执行迁移。
3. 使用选定的目标业务库逐个执行 SQL；同一文件必须在一个 MySQL 连接内执行。客户端遇错即停，不使用 `--force`。MySQL DDL 可能已隐式提交，失败后先检查已完成的部分，不能假定整个脚本已回滚。
4. 每条成功后记录脚本文件名、SHA256、目标库、执行时间和结果，再安装/启动新版 JAR 与前端。失败时保持应用停止，不继续启动新版。

若已完成全部适用迁移，本次只更新应用制品；全新数据库由服务启动执行 `schema.sql`，不执行这些面向旧库的迁移。按 `schema.sql` 的结构比对不能代替有明确要求的升级脚本。

## 本次自动升级的兼容说明（2026-11-17）

默认部署只更新包并重启 server。名单、实验和调用日志的十张表已将旧版启动程序要求的 `utf8mb4_unicode_ci` 明确写入建表定义。已经由旧版正常启动并完成排序规则修正的库不需要为此执行 SQL；只用旧初始化脚本手工建过表的库，应检查本次日志指出的表。若排序规则不同，先核对现有唯一键在目标排序规则下有无冲突、外键两端以及自定义列的排序要求，再在维护窗口迁移。不要对整个库统一转换字符集。逻辑分区调整、历史数据回填、不能无损推导的改名等仍应交付独立版本脚本，不能仅删除或更名 schema 中的字段来搬移旧数据。

## 现有脚本及适用条件

全局 `trace_id`、`create_time` 随启动时的 `schema.sql` 幂等补齐。已有同名全局字段保留 ID，启用并转为引擎只读来源，分别从根执行读取追踪 ID 和进件时间。此前由调用方提供这两个字段的已发布规则，应核对入参契约并重新发布。

统一入口 `bash install-release.sh 发布包.zip` 启动 server 后，会按包内 `schema.sql` 补齐账户归属和账单汇总键、操作日志区间、决策结果系统常量、执行队列、断点恢复结构、分流异常策略及名单动作来源。旧记录缺少的归属和来源继续为空，已有实验组保留其外数调用开关。

| 文件 | 使用场景 |
|---|---|
| [历史字段索引回填](2026-11-13-history-field-index.sql) | 已有可读的逻辑执行日志视图、稳定字段快照，且历史索引尚未完整建立时，在停写维护窗口运行。脚本从历史行重建统计汇总，完成后登记就绪版本。缺少字段身份快照的旧实验从升级后的正式执行开始计入。 |
| [外键整库预检与迁移](2026-11-11-er-foreign-keys.sql) | 需要在添加第一条外键前检查整批旧关系并定位孤立引用时使用，见下节。常规建表与外键补齐由启动升级执行。 |
| [中文注释工具](2026-11-11-schema-comments.py) | 需要沿用目标库的实际列定义、只补中文注释时使用，见「已有库中文注释」。 |

新增显式迁移时在此记录前置版本、预检、执行顺序和回退限制。历史版本的手工脚本随对应版本的发布包和 Git 提交保留。

## 数据库客户端 ER 图

备份并暂停写入后，使用支持 `DELIMITER` 的 MySQL 客户端，在选中的业务库内执行外键预检脚本。迁移账号需要读取表结构与数据、`ALTER`、`REFERENCES`、`CREATE TEMPORARY TABLES` 及创建、调用、删除迁移过程的权限。保持 `FOREIGN_KEY_CHECKS=1`。旧版本尚未创建的子表由新版服务按 `schema.sql` 首次建表。

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

注释工具从本次发布的 `docker/runtime/server.jar` 读取权威中文注释，从目标库的结构导出读取实际列定义。同名表、列缺少中文注释时生成 `ALTER TABLE`，并更新四条能精确识别的旧表注释。已有自定义中文说明、额外列及尚未建表的旧版模块均保留原状。

停止写入、完成备份及适用的结构迁移后，在同一维护窗口导出、生成并执行。MySQL 的 `MODIFY COLUMN` 需要重述列定义，因此每次执行和失败重试都要重新导出目标库。下面的 `RELEASE_DIR` 指向本次解压目录，`BACKUP_DIR` 指向此次维护的备份目录。

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

## 执行示例（Linux）

在已整理的外层固定部署目录执行（旧平铺模式仍使用原 env 路径），`RELEASE_DIR` 是已校验的新包解压目录。以下示例适用于已有稳定字段快照、需要回填历史统计索引的库。使用当前 MySQL 所属的 Compose 项目名、文件与配置，不能为迁移新建另一套 MySQL。

```bash
docker compose --project-name tianshu-server --env-file config/.env.server \
  -f docker/docker-compose.mysql.yml exec -T mysql sh -ec \
  'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 --binary-mode=1 --host=127.0.0.1 --user="$MYSQL_USER" --database="$MYSQL_DATABASE"' \
  < "$RELEASE_DIR/migrations/2026-11-13-history-field-index.sql"
```

以上使用现有容器内的应用账号，需有对应 DDL 权限，且其数据库/账号应与 server 的实际连接一致；已有卷的实际账号以数据库为准。外部 MySQL 由运维使用受保护的客户端连接配置执行同一 SQL，勿输出密码或把 `.env.server` 当 Shell 脚本执行。后续适用脚本逐条执行，确认返回码为 0 再继续。

回退 JAR 不会撤销已执行的数据库迁移。先确认旧版兼容性；不兼容时按已验收备份恢复到不同且不存在的库，再安排应用回退。

- `2026-11-20-resource-test-cases.sql` 新增变量、外数和模型测试用例表，重复执行保留已有用例。

- `2026-11-20-shared-model-files.sql` 建立模型和制品共用的文件目录、二进制载荷和作用域引用表，既有模型数据由显式迁移命令校验后写入。

- `2026-11-21-test-mock.sql` 建立测试生成规则与变量、对象及子字段的绑定外键，重复执行保留已编辑的规则。

- `2026-11-21-test-examples.sql` 补齐示例方式、对象示例及方法关联表，移除旧版内置方法的出厂字段匹配。

- `2026-11-22-test-scope.sql` 增加 Mock 规则的所属项目、规则和变量的默认测试输入。既有 Mock 规则继续全局可用。

- `2026-11-23-example-case-binding.sql` 在测试示例迁移后执行，支持变量、对象和子字段绑定固定用例，并扩大变量固定示例的容量。

- `2026-11-26-engine-sftp.sql` 建立内置 SFTP 授权和素材文件引用表。在共享模型文件仓迁移后执行，历史素材仍可按原方式读取。

- `2026-11-26-sftp-service-security.sql` 在引擎 SFTP 账户表建立后执行。为已有账户补齐读写模式，建立服务策略与认证封禁表，重复执行保留现场配置。升级独立 SFTP 进程前先完成此脚本。
