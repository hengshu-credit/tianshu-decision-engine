# 业务日志长期留存与可选清理

业务日志默认永久保存。`RULE_LOG_RETENTION_ENABLED=false` 时不借用数据库连接、不执行删除；即使开启总开关，所有类别天数为 `0` 时也不执行删除。Java 运行日志由日志框架单独管理。

## 部署参数

| 环境变量 | 默认值 | 含义 |
|---|---|---|
| `RULE_LOG_RETENTION_ENABLED` | `false` | 是否启用可选清理 |
| `RULE_LOG_RETENTION_INTERVAL_MS` | `86400000` | 两轮任务完成与开始之间的间隔，范围 60000—604800000 毫秒；开启后第一轮可能在服务启动时执行 |
| `RULE_LOG_RETENTION_BATCH_SIZE` | `1000` | 每次 SQL 最多删除行数，范围 1—10000 |
| `RULE_LOG_RETENTION_MAX_BATCHES_PER_TABLE` | `10` | 每张表每轮最多批数，范围 1—100 |
| `RULE_LOG_RETENTION_QUERY_TIMEOUT_SECONDS` | `5` | 锁查询、索引检查和单批删除的 JDBC 超时，范围 1—30 秒 |
| `RULE_AUTH_ACCESS_LOG_RETENTION_DAYS` | `0` | 项目鉴权访问日志保留天数 |

所有保留天数允许 0—36500，`0` 始终表示永久保存。账单明细、规则请求参数/响应、外数鉴权与调用、数据库/名单/模型调用、模型评分出入参、生命周期事件、名单变更日志、分流实验执行明细由代码永久保留，不提供自动删除配置。保留周期与归档、备份、业务回溯要求一起制定；可清理参数不修改执行状态、检查点、恢复 Outbox、名单记录或账单汇总。

## 运行方式

- 一轮固定同一个截止时间，按 `< 截止时间` 删除，截止点上的记录保留。
- 每批独立提交，并按时间排序、绑定 `LIMIT`；默认每张启用的表每轮最多处理 10000 行。数据量超过预算时留到下一轮。
- 同一进程防止任务重入；连接同一 MySQL 实例及数据库的服务通过 `GET_LOCK` 互斥。锁与删除使用同一个连接，结束后释放。释放失败时中止连接，避免持锁连接留在池内。不同 MySQL 实例不共享该锁，部署中应指定同一写库。
- 未取得锁的节点报告“其他节点正在清理”。任务不会替其他节点报告删除数量。
- 清理前检查时间列是否存在可见的前导索引；没有索引的类别跳过并报告异常。其他类别继续执行。任务不在运行时对大表建索引。
- 单批异常保留已经确认的删除数量并报告失败。超时或连接中断时，最后一批是否已提交需由数据库侧核对；面板统计不包含无法确认的批次。

## 启用前的索引核对

在当前业务数据库执行以下只读查询，确认 `SEQ_IN_INDEX=1` 的字段与时间列一致：

```sql
SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, IS_VISIBLE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND SEQ_IN_INDEX = 1
  AND TABLE_NAME IN ('rule_auth_access_log');
```

可清理表仅包含项目鉴权访问日志并使用 `create_time`。生命周期事件、名单变更、账单、规则执行、运行时调用和分流实验表不会进入清理 SQL。既有部署可能只有“项目/模块 + 时间”的复合索引，该索引的时间列不在首位，不能满足本任务的检查。由部署迁移按实际数据规模建立时间索引，再开启鉴权访问日志清理。

## 观察结果

`GET /api/rule/ops/execution-metrics` 的 `logRetention` 仅提供当前节点运维快照；执行指标面板不展示日志清理入口。规则执行、外数/模块调用、模型评分、数据库/名单调用、生命周期/名单变更、账单和分流实验数据永不自动删除。

| `lastRunStatus` | 含义 |
|---|---|
| `NOT_RUN` | 本节点尚未执行 |
| `RUNNING` | 当前轮正在执行，计数尚未完成 |
| `COMPLETED` | 已配置类别完成本轮清理 |
| `BATCH_LIMIT_REACHED` | 至少一类达到本轮预算，不宣称存量已清空 |
| `SKIPPED_LOCKED` | 未取得数据库锁，本节点未执行 |
| `FAILED` | 锁、索引或删除出现异常，查看 `lastFailures` |

快照还包含 `lastRunAt`、`lastFinishedAt`、`lastDeleted`、`batchLimitedTables` 和配置预算。快照保存在当前节点内存，重启后重置。接口未返回指标时，页面显示“未知”。
