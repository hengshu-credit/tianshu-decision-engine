# 决策引擎容量、备份恢复与真实环境 E2E 门禁

## 1. 容量与性能

使用现有容量门禁，所有请求定义和报告都会自动脱敏：

```powershell
node scripts/quality-gates/run-capacity-gate.mjs `
  --url http://127.0.0.1:8080/api/rule/open/execute/RISK_RULE `
  --request-file scripts/quality-gates/fixtures/request.example.json `
  --concurrency 20 `
  --warmup-seconds 10 `
  --duration-seconds 60 `
  --max-error-rate 0.01 `
  --max-p95-ms 500 `
  --min-throughput 10 `
  --report-dir target/quality-gates
```

验收要求：错误率、P95、吞吐量全部满足项目阈值；同时读取管理端 `/api/rule/ops/execution-metrics`，确认异步持久化、来源解析、开放执行和数据库连接池队列没有持续增长，`failed` 为 0，`fallback` 只在瞬时峰值出现。

## 2. 数据库备份与恢复

生产演练必须使用只读检查和临时恢复库，不直接覆盖线上库：

```powershell
mysqldump --single-transaction --routines --events --triggers `
  --host $env:MYSQL_HOST --port $env:MYSQL_PORT `
  --user $env:MYSQL_BACKUP_USER --password `
  $env:MYSQL_DATABASE > target/backup/rule_engine.sql

mysql --host $env:MYSQL_HOST --port $env:MYSQL_PORT `
  --user $env:MYSQL_RESTORE_USER --password `
  --database rule_engine_restore < target/backup/rule_engine.sql
```

恢复后必须核对：规则制品摘要、发布版本、变量/模型 ID 引用、实验版本、执行日志分区和账单汇总。Redis 需要单独导出项目推送相关键或重新触发全量同步，不能只恢复 MySQL。

## 3. 真实环境浏览器 E2E

推荐使用组合门禁脚本，一次执行 readiness、容量、备份和浏览器验证：

```powershell
$env:E2E_USERNAME = 'staging-minimal-user'
$env:E2E_PASSWORD = '<temporary-password>'
$env:MYSQL_HOST = 'staging-mysql.internal'
$env:MYSQL_PORT = '3306'
$env:MYSQL_BACKUP_USER = 'backup_user'
$env:MYSQL_DATABASE = 'rule_engine'

.\scripts\quality-gates\run-staging-gate.ps1 `
  -BaseUrl 'https://staging-engine.example.com' `
  -RequestFile 'scripts/quality-gates/fixtures/request.example.json'
```

只执行浏览器门禁时：

```powershell
$env:E2E_BASE_URL = 'https://staging-engine.example.com'
cd rule-engine-builder-ui
npm run test:e2e:full
```

真实环境门禁至少覆盖：登录与权限、项目创建、变量来源测试、数据库只读查询、规则六项发布进度、审批差异、发布后执行、执行日志、实验版本切换和回滚。测试账号使用临时最小权限账号，测试数据使用独立项目并在演练后清理。

## 4. 发布前证据

每次发布保留以下证据：

- 容量报告与阈值配置；
- 数据库备份文件校验和、恢复库核对结果；
- 真实环境 E2E 报告和失败截图；
- `/api/rule/ops/execution-metrics` 快照；
- `target/quality-gates/staging-gate-summary.json` 组合门禁结果；
- 当前发布制品摘要和规则/实验版本对比。
