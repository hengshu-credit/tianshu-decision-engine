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

先安装门禁工具自身的锁定依赖（无需安装整个前端）。工具复用Microsoft的严格JSON AST解析器，按数字原文校验大整数和小数，避免先转为JavaScript Number后误判相等；对象请求体也保留原始JSON数字。解析器配置禁止注释、尾随逗号和重复对象键。

```powershell
npm ci --prefix scripts/quality-gates --ignore-scripts
```

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

横向扩容前可用 `--urls` 并行压测多个目标；脚本会在所有目标完成预热后使用共同测量起点，并生成 `capacity-report.multi.json/md`。实际聚合吞吐按“总成功数÷共同观测时长”计算，包含最后一个目标排空在途请求的时间；各目标速率之和仅单列供对照，不再作为集群实际吞吐。每个目标仍分别执行相同错误率、P95和最低吞吐阈值，聚合结果还会再次校验；P95/P99展示最差目标分位数，不冒充混合分布分位数：

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

默认采用固定并发闭环：每个并发槽等待响应后才发下一条请求。增加`--arrival-rate`后，按独立于响应时间的固定到达计划发压，单位为**每个目标每秒请求数**，`--concurrency`成为每个目标的在途上限。例如：

```powershell
node scripts/quality-gates/run-capacity-gate.mjs `
  --url http://127.0.0.1:8080/api/rule/openapi `
  --request-file scripts/quality-gates/fixtures/request.local.json `
  --arrival-rate 50 --max-schedule-lag-ms 25 --concurrency 32 `
  --warmup-seconds 10 --duration-seconds 60 `
  --max-error-rate 0.01 --max-p95-ms 300 --min-throughput 45 `
  --report-dir target/quality-gates/arrival-50
```

示例数字只是参数演示，需要按实际部署与业务峰值设定。到达时并发已满的请求不会进入无限等待队列；超过调度迟到预算（默认100ms）的时刻直接跳过，两种未发起请求均独立导致门禁FAIL，不能靠幸存请求全部HTTP成功掩盖过载。预算内允许有界补发，报告明确列出补发量、最大调度延迟以及**固定1ms桶**最大发起数；它不保证每一对实际发起请求都严格等间隔，也不把固定桶最大值称为滑动窗口峰值。两目标均配置50时，计划总到达率为100/s。

脚本使用Node 20内置`fetch`；HTTP200且响应断言匹配才计入成功，202、业务失败、错误JSON等不计成功。数字断言按原文的十进制值比较，支持大整数、长小数和指数，数值与字符串仍严格区分。完整请求包含读取和解析响应的实际截止时间，不能因事件循环阻塞导致abort timer迟到就把超时请求算作成功。

预热不计入测量；延迟采用3位有效数字HDR直方图，门禁先计算向上取整的样本秩，避免小样本尾部被HDR内部百分位取整遗漏。分位数为桶边界近似，统计不再保留或排序全部请求样本。请求完成速率的观测时长包含窗口末尾排空，报告另列排空耗时、HTTP/断言/解析/超时分类、负载进程CPU（单核百分比）、事件循环利用率及进程启动以来峰值RSS；这些是负载机指标，不是被测服务指标，多目标重叠窗口不能累加CPU。响应本身仍需读取为JSON，统计内存有界不代表任意大响应体都不占内存。

报告写到`capacity-report.json/md`或`capacity-report.multi.json/md`，不保存Header值、请求体、响应体或URL查询参数原值。任一阈值不满足或存在未发起到达时，返回非零退出码；未知CLI参数会直接报错，避免拼错参数后悄悄运行另一种压力模型。

设计依据：[k6开放与闭环模型说明](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/open-vs-closed/)、[Node直方图API](https://nodejs.org/api/perf_hooks.html#perf_hookscreatehistogramoptions)、[Microsoft JSON AST解析器](https://github.com/microsoft/node-jsonc-parser)。实际实现已用Node20.19.0与当前Node版本验证，未引入k6运行依赖。

容量阈值依赖 CPU、内存、JVM、数据库、Redis 和网络规格，默认 CI 只验证脚本和可靠性语义，不在共享 runner 上设置绝对性能阈值。

## 自动化门禁

```powershell
npm ci --prefix scripts/quality-gates --ignore-scripts
npm test --prefix scripts/quality-gates
mvn -pl rule-engine-server -am `
  "-Dtest=HealthProbeConfigurationTest,OnnxWarmupHealthIndicatorTest,OnnxModelWarmupRunnerTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test
```

故障验收矩阵：MySQL 或 Redis 不可用、ONNX WARMING/FAILED 时 liveness 仍为 UP、readiness 不为 UP；无 ONNX 预加载目标或 GPU 失败但 CPU fallback 成功时两者均为 UP。
