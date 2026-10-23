# 决策引擎容量、备份恢复与真实环境 E2E 门禁

## 1. 容量与性能

使用现有容量门禁，所有请求定义和报告都会自动脱敏：

```powershell
node scripts/quality-gates/run-capacity-gate.mjs `
  --url http://127.0.0.1:8080/api/rule/openapi/RISK_RULE `
  --request-file scripts/quality-gates/fixtures/request.example.json `
  --concurrency 20 `
  --warmup-seconds 10 `
  --duration-seconds 60 `
  --max-error-rate 0.01 `
  --max-p95-ms 500 `
  --min-throughput 10 `
  --report-dir target/quality-gates
```

多节点上线前将 `--url` 替换为 `--urls`，以逗号分隔各节点地址。脚本会并行压测并生成 `capacity-report.multi.json/md`，聚合吞吐为各节点之和，错误率和 P95 以最差节点判定：

```powershell
node scripts/quality-gates/run-capacity-gate.mjs `
  --urls https://engine-1.example.com/api/rule/openapi/RISK_RULE,https://engine-2.example.com/api/rule/openapi/RISK_RULE `
  --request-file scripts/quality-gates/fixtures/request.local.json `
  --concurrency 20 `
  --warmup-seconds 10 `
  --duration-seconds 60 `
  --max-error-rate 0.01 `
  --max-p95-ms 500 `
  --min-throughput 20 `
  --report-dir target/quality-gates
```

验收要求：错误率、P95、吞吐量全部满足项目阈值；同时读取管理端 `/api/rule/ops/execution-metrics`，确认异步持久化、来源解析、开放执行和数据库连接池队列没有持续增长，`failed` 为 0，`fallback` 只在瞬时峰值出现。

## 2. 数据库备份与恢复

生产演练必须使用只读检查和临时恢复库，不直接覆盖线上库：

```powershell
mysqldump --single-transaction --routines --events --triggers --hex-blob `
  --host $env:MYSQL_HOST --port $env:MYSQL_PORT `
  --user $env:MYSQL_BACKUP_USER --password `
  --result-file=target/backup/rule_engine.sql $env:MYSQL_DATABASE

mysql --host $env:MYSQL_HOST --port $env:MYSQL_PORT `
  --user $env:MYSQL_RESTORE_USER --password `
  --database rule_engine_restore --execute="source target/backup/rule_engine.sql"
```

备份包含二进制决策制品，必须保留 `--hex-blob`，并由 mysqldump 的 `--result-file` 写文件，避免 PowerShell 或 UTF-8 文本转换损坏 BLOB。恢复库须预先单独创建，不能覆盖源库。恢复后必须核对：逐表行数和内容校验、规则制品摘要、发布版本、变量/模型 ID 引用、实验版本、执行日志分区和账单汇总。项目加密主密钥须单独备份并保持一致。Redis 需要单独导出项目推送相关键或重新触发全量同步，不能只恢复 MySQL。

Windows Docker 本地验证可使用容器内部管道，避免宿主机大文件重定向导致恢复中断：

```powershell
$env:MYSQL_ROOT_PASSWORD = '<local-secret>'
.\scripts\quality-gates\run-docker-backup-restore-gate.ps1 `
  -DockerContainer rule-engine-mysql `
  -Database rule_engine `
  -RestoreDatabase rule_engine_restore_gate
```

运行门禁前暂停业务写入，保证原库计数/校验和与备份处于同一稳定状态。脚本拒绝源库与目标库同名以及已存在的目标库；仅创建全新的临时恢复库。逐表核对行数与 CHECKSUM 后，清理本次创建的恢复库和临时 dump；报告写入 `target/quality-gates/backup-restore-summary.json`，不保存密码。在线持续写入场景应使用可重复读取的专用备份快照进行同等校验。

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
cd rule-engine-web
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

