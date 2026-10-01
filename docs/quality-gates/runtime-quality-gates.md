# 运行质量门禁

天枢使用 Spring Boot Actuator 区分“进程存活”和“可以接收业务流量”：

- `GET /actuator/health/liveness`：只检查应用进程状态，不依赖 MySQL、Redis、ONNX 或外数。
- `GET /actuator/health/readiness`：组合 Spring readiness、MySQL、Redis 和 ONNX 启动预热状态；任一必要依赖不可用时不接流量。

ONNX 启动预热状态为 `NOT_STARTED → WARMING → READY|FAILED`。没有需要处理的 ONNX 模型时直接进入 READY；配置 CUDA 的模型回退 CPU 且 CPU 初始化成功仍为 READY；摘要修复、模型解码、CUDA 与 CPU 均失败或目标模型在预热期间消失时为 FAILED。

## 启动就绪检查

先使用部署环境的 Secret/KMS 或本地已授权 `.env` 启动 MySQL、Redis 和后端，然后在仓库根目录执行：

```powershell
node scripts/quality-gates/wait-for-readiness.mjs `
  --base-url http://127.0.0.1:8080 `
  --timeout-seconds 120
```

等待器会容忍应用端口尚未监听以及 readiness 返回 503/OUT_OF_SERVICE，持续轮询到超时；一旦已获得明确的非 UP liveness 状态则立即失败。它只输出聚合状态，不输出健康详情或连接信息。验证结束后必须主动停止后端和临时基础设施进程。

## 容量门禁

复制示例请求为被 Git 忽略的本地文件，填入测试环境专用规则和凭据：

```powershell
Copy-Item scripts/quality-gates/fixtures/request.example.json `
  scripts/quality-gates/fixtures/request.local.json
```

随后使用明确的本地或测试地址与部署规格阈值运行：

```powershell
node scripts/quality-gates/run-capacity-gate.mjs `
  --url http://127.0.0.1:8080/api/rule/openapi `
  --request-file scripts/quality-gates/fixtures/request.local.json `
  --concurrency 16 `
  --warmup-seconds 10 `
  --duration-seconds 60 `
  --max-error-rate 0.01 `
  --max-p95-ms 300 `
  --min-throughput 20 `
  --report-dir target/quality-gates
```

横向扩容前可用 `--urls` 并行压测多个节点；脚本会分别记录节点摘要，并生成聚合的 `capacity-report.multi.json/md`，聚合吞吐为各节点之和，错误率和 P95 按最差节点判断：

```powershell
node scripts/quality-gates/run-capacity-gate.mjs `
  --urls http://10.0.0.31:8080/api/rule/sync/execute/RISK_RULE,http://10.0.0.32:8080/api/rule/sync/execute/RISK_RULE `
  --request-file scripts/quality-gates/fixtures/request.local.json `
  --concurrency 16 `
  --warmup-seconds 10 `
  --duration-seconds 60 `
  --max-error-rate 0.01 `
  --max-p95-ms 300 `
  --min-throughput 40 `
  --report-dir target/quality-gates
```

脚本使用 Node 20 内置 `fetch`，预热样本不计入统计；报告写到 `target/quality-gates/capacity-report.json` 和 `.md`。报告不保存 Header 值、请求体、响应体或 URL 查询参数原值。任一错误率、p95 或吞吐阈值不满足时返回非零退出码。

容量阈值依赖 CPU、内存、JVM、数据库、Redis 和网络规格，默认 CI 只验证脚本和可靠性语义，不在共享 runner 上设置绝对性能阈值。

## 自动化门禁

```powershell
node --test scripts/quality-gates/test/*.test.mjs
mvn -pl rule-engine-server -am `
  "-Dtest=HealthProbeConfigurationTest,OnnxWarmupHealthIndicatorTest,OnnxModelWarmupRunnerTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test
```

故障验收矩阵：MySQL 或 Redis 不可用、ONNX WARMING/FAILED 时 liveness 仍为 UP、readiness 不为 UP；无 ONNX 预加载目标或 GPU 失败但 CPU fallback 成功时两者均为 UP。
