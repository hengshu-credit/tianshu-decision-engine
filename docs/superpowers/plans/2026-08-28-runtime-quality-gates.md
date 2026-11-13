# Runtime Quality Gates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为天枢建立可重复执行的启动就绪、故障降级、性能和容量验证入口，让服务“进程存活”与“可安全接流量”可区分，并让 CI 持续阻止可靠性语义回退。

**Architecture:** 使用 Spring Boot Actuator 暴露标准 liveness/readiness；数据库和 Redis 使用框架 health contributor，新增 ONNX warmup 状态与 HealthIndicator。启动 runner 负责从 `NOT_STARTED` 进入 `WARMING`，无预加载目标或全部成功为 `READY`，任一真实预加载失败为 `FAILED/OUT_OF_SERVICE`；已有 GPU→CPU 回退若最终成功仍视为 UP 并在 details 标注。Node 20 脚本通过内置 `fetch` 运行容量场景、计算延迟/吞吐/错误率并输出脱敏报告；故障矩阵由自动化测试验证，CI 将正确性与就绪语义作为硬门禁，容量阈值保留为显式环境门禁。

**Tech Stack:** Spring Boot 3.5 Actuator、Java 17、JUnit 4、Node.js >=20.19、GitHub Actions。

**Spec:** `docs/superpowers/specs/2026-08-28-runtime-quality-gates-design.md`

## Global Constraints

- [ ] 直接在当前 `master` 工作区修改，不创建或切换分支、worktree。
- [ ] 只允许访问已授权 `.env` 来启动本地服务；报告、日志、测试快照和提交中不得出现密码、token、连接串或请求敏感字段。
- [ ] liveness 不依赖 MySQL、Redis、ONNX 或下游外数；依赖失败只能影响 readiness。
- [ ] 不制造新的业务 `/health` 私有协议；管理探针统一走 Actuator。
- [ ] 容量脚本没有明确目标 URL、请求样例和阈值时必须拒绝运行，不能用隐含生产地址。
- [ ] 正式启动/容量验证完成后主动停止服务；长期服务工具超时统一 `1800000ms`。

---

### Task 1: 接入 Actuator 并锁定探针暴露范围

**Files:**
- Modify: `rule-engine-server/pom.xml`
- Modify: `rule-engine-server/src/main/resources/application.yml`
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/health/HealthProbeConfigurationTest.java`

- [ ] **Step 1: 写配置契约失败测试**

用 Spring 测试上下文/MockMvc 验证 `/actuator/health/liveness` 与 `/actuator/health/readiness` 存在；liveness 返回状态且不包含 db/redis/onnxWarmup；readiness group 包含 `readinessState`、`db`、`redis`、`onnxWarmup`；除 `health`/`info` 外不暴露 env、beans、configprops 等端点。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl rule-engine-server -am -Dtest=HealthProbeConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，Actuator 未接入。

- [ ] **Step 3: 增加官方 Actuator 依赖**

在 server dependencies 增加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

- [ ] **Step 4: 配置最小端点和健康组**

在 `application.yml` 配置 `management.endpoints.web.exposure.include: health,info`、`management.endpoint.health.probes.enabled: true`，并显式定义：

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState,ping
        readiness:
          include: readinessState,db,redis,onnxWarmup
  endpoints:
    web:
      exposure:
        include: health,info
```

若 Spring 测试环境没有 Redis contributor，测试 profile 提供受控 stub，而生产配置仍要求真实 `redis` contributor，不能从 readiness 删除依赖。

- [ ] **Step 5: 运行配置测试确认通过**

Run: `mvn -pl rule-engine-server -am -Dtest=HealthProbeConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 2: 建立 ONNX warmup 状态机和 readiness 指示器

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/health/OnnxWarmupState.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/health/OnnxWarmupStatus.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/health/OnnxWarmupHealthIndicator.java`
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/health/OnnxWarmupHealthIndicatorTest.java`

- [ ] **Step 1: 写状态机失败测试**

断言初始 `NOT_STARTED` 为 OUT_OF_SERVICE；`WARMING` 为 OUT_OF_SERVICE；无预加载目标完成为 READY/UP；全部成功为 READY/UP；任一摘要修复或预加载失败为 FAILED/OUT_OF_SERVICE；details 只含状态、目标数、成功数、失败数、CPU 回退次数和安全错误分类，不含模型内容、配置或堆栈。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl rule-engine-server -am -Dtest=OnnxWarmupHealthIndicatorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL。

- [ ] **Step 3: 实现线程安全状态对象**

状态转换只允许 `NOT_STARTED -> WARMING -> READY|FAILED`；状态和计数对健康检查线程可见。每次应用启动只有一个状态周期，不提供运行中任意重置接口。

- [ ] **Step 4: 实现名为 `onnxWarmup` 的 HealthIndicator**

`READY` 返回 `Health.up()`；其他状态返回 `Health.outOfService()`。失败详情使用固定枚举/简短消息，不输出原始异常对象。

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -pl rule-engine-server -am -Dtest=OnnxWarmupHealthIndicatorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 3: 让启动预热正确驱动 readiness

**Files:**
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/OnnxModelWarmupRunner.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/OnnxModelWarmupRunnerTest.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/onnx/OnnxModelExecutionService.java`（仅为暴露已有回退结果所必需）
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/onnx/OnnxModelExecutionServiceTest.java`（对应现有实际测试路径）

- [ ] **Step 1: 扩充 runner 失败测试**

覆盖候选查询返回 null/空列表时 READY；仅补摘要且无 preload 时 READY；预加载开始时 WARMING；一个模型失败仍继续尝试其余模型但最终 FAILED；所有预加载成功 READY；`RuntimeException` 与 `LinkageError` 均计入失败。

- [ ] **Step 2: 写 GPU→CPU 回退健康语义测试**

若 `OnnxModelExecutionService.preload` 内部 GPU 初始化失败但 CPU fallback 成功，runner 记录成功并增加 fallback 计数，最终 READY；只有 CPU fallback 也失败才 FAILED。优先复用已有执行结果/诊断信息，不复制一套 fallback 实现。

- [ ] **Step 3: 运行测试确认失败**

Run: `mvn -pl rule-engine-server -am -Dtest=OnnxModelWarmupRunnerTest,OnnxModelExecutionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，新状态尚未驱动。

- [ ] **Step 4: 注入并驱动 `OnnxWarmupStatus`**

runner 进入时 `start(targetCount)`；每个预加载/摘要修复结果更新计数；循环结束统一 `complete()` 或 `fail()`。保留“单模型失败后继续处理其他模型”的现有行为，但不再把总体 readiness 错报为成功。

- [ ] **Step 5: 暴露最小 fallback 结果**

若当前 `preload` 无法区分普通成功和 CPU 回退成功，增加兼容旧调用者的结果方法或诊断回调；不要改变模型推理的 GPU/CPU 策略，不要因健康指标重复加载模型。

- [ ] **Step 6: 运行测试确认通过**

Run: `mvn -pl rule-engine-server -am -Dtest=OnnxModelWarmupRunnerTest,OnnxWarmupHealthIndicatorTest,OnnxModelExecutionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 4: 建立数据库、Redis、ONNX 故障降级矩阵

**Files:**
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/health/RuntimeReadinessFailureMatrixTest.java`
- Create: `rule-engine-server/src/test/resources/application-test.yml`（仅在需隔离真实基础设施时创建）

- [ ] **Step 1: 写故障矩阵测试**

至少覆盖：

| 场景 | liveness | readiness |
|---|---|---|
| 全部依赖正常且 ONNX READY | UP | UP |
| MySQL DOWN | UP | DOWN/OUT_OF_SERVICE |
| Redis DOWN | UP | DOWN/OUT_OF_SERVICE |
| ONNX WARMING | UP | OUT_OF_SERVICE |
| ONNX FAILED | UP | OUT_OF_SERVICE |
| 无 ONNX 预加载目标 | UP | UP |
| GPU 失败但 CPU fallback 成功 | UP | UP |

测试应替换 contributor/status，不连接外部真实服务，确保可重复。

- [ ] **Step 2: 运行矩阵测试并确认失败**

Run: `mvn -pl rule-engine-server -am -Dtest=RuntimeReadinessFailureMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，readiness 组合语义尚未完整。

- [ ] **Step 3: 修正健康组和状态映射直至矩阵通过**

只修探针组合/状态，不通过让 liveness 依赖数据库或 Redis 来迎合断言。

- [ ] **Step 4: 运行健康定向测试**

Run: `mvn -pl rule-engine-server -am -Dtest=HealthProbeConfigurationTest,OnnxWarmupHealthIndicatorTest,OnnxModelWarmupRunnerTest,RuntimeReadinessFailureMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 5: 实现无第三方依赖的容量与性能门禁脚本

**Files:**
- Create: `scripts/quality-gates/run-capacity-gate.mjs`
- Create: `scripts/quality-gates/lib/capacity-config.mjs`
- Create: `scripts/quality-gates/lib/capacity-runner.mjs`
- Create: `scripts/quality-gates/test/capacity-config.test.mjs`
- Create: `scripts/quality-gates/test/capacity-runner.test.mjs`
- Create: `scripts/quality-gates/fixtures/request.example.json`
- Modify: `.gitignore`（仅确保报告目录不入库）

- [ ] **Step 1: 写配置与统计失败测试**

使用 Node 内置 `node:test` 覆盖：缺少 URL/请求文件/阈值时退出；只允许 http/https；并发数、预热时长、测量时长必须为正数；计算 total/success/errorRate/throughput/median/p95/p99；阈值等于边界时通过；超限时非零退出。

- [ ] **Step 2: 写脱敏失败测试**

输入 header/body 含 `Authorization`、cookie、token、password、secret、key 时，控制台和 JSON 报告均只出现字段名与掩码，不出现原值；URL 的 query 参数默认全部移除或掩码。

- [ ] **Step 3: 运行 Node 测试确认失败**

Run: `node --test scripts/quality-gates/test/*.test.mjs`

Expected: FAIL，脚本尚不存在。

- [ ] **Step 4: 实现配置解析与负载执行器**

CLI 明确接收 `--url`、`--request-file`、`--concurrency`、`--warmup-seconds`、`--duration-seconds`、`--max-error-rate`、`--max-p95-ms`、`--min-throughput`、`--report-dir`。用 Node 20 内置 `fetch`、`performance.now()` 和 AbortController；预热数据不计入报告；固定并发 worker 在测量窗口内持续请求。

- [ ] **Step 5: 输出机器可读和人类可读报告**

写入 `target/quality-gates/capacity-report.json` 与 `.md`，内容包括时间、脱敏目标、场景参数、样本数、吞吐、错误率、median/p95/p99、阈值和 PASS/FAIL。禁止写请求 body 原文和响应 body。

- [ ] **Step 6: 运行脚本测试确认通过**

Run: `node --test scripts/quality-gates/test/*.test.mjs`

Expected: PASS。

### Task 6: 提供本地启动就绪和容量验收流程

**Files:**
- Create: `scripts/quality-gates/wait-for-readiness.mjs`
- Create: `scripts/quality-gates/test/wait-for-readiness.test.mjs`
- Create: `docs/quality-gates/runtime-quality-gates.md`
- Modify: `README.md`

- [ ] **Step 1: 为 readiness 等待器写失败测试**

测试 NOT_READY 重试、READY 成功、超时非零退出、liveness DOWN 立即失败、响应非 JSON 的清晰错误；轮询间隔可配置但有安全下限。

- [ ] **Step 2: 实现启动等待器**

只访问调用方提供的本地/测试 base URL，轮询 `/actuator/health/liveness` 与 `/actuator/health/readiness`；输出状态变化，不输出响应 details 中潜在敏感数据。

- [ ] **Step 3: 运行 Node 测试**

Run: `node --test scripts/quality-gates/test/*.test.mjs`

Expected: PASS。

- [ ] **Step 4: 编写可复制的本地流程**

文档包含：使用 `.env` 启动依赖和 server；wait-for-readiness；使用显式本地 URL 与已脱敏请求样例运行 capacity gate；解释绝对阈值需按部署规格设定；故障矩阵和 ONNX 状态含义；如何停止服务和定位报告。不得粘贴 `.env` 内容。

- [ ] **Step 5: 在 README 链接质量门禁文档**

只增加入口和命令摘要，不复制整篇说明。

### Task 7: 将可靠性语义接入 CI

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: 增加 Node 质量脚本测试步骤**

在 frontend job 或独立轻量 job 中运行：

```yaml
- name: Test runtime quality-gate scripts
  run: node --test scripts/quality-gates/test/*.test.mjs
```

若沿用 frontend job，设置正确的根目录，不能在 `rule-engine-builder-ui` working-directory 下误找脚本。

- [ ] **Step 2: 保证健康/故障测试属于默认 Maven 门禁**

不对新增 JUnit 测试添加默认 skip；现有 `mvn test` 自动运行它们。容量压测不在共享 GitHub runner 上设绝对性能阈值，避免硬件抖动造成假失败。

- [ ] **Step 3: 校验 workflow 结构和命令**

Run: `node --test scripts/quality-gates/test/*.test.mjs`

Run: `mvn -pl rule-engine-server -am -Dtest=HealthProbeConfigurationTest,OnnxWarmupHealthIndicatorTest,OnnxModelWarmupRunnerTest,RuntimeReadinessFailureMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 8: 执行完整启动、故障与容量验收

**Files:**
- Verify: `target/quality-gates/capacity-report.json`
- Verify: `target/quality-gates/capacity-report.md`

- [ ] **Step 1: 编译所有后端模块**

Run: `mvn clean install -DskipTests`

Expected: BUILD SUCCESS。

- [ ] **Step 2: 使用已授权 `.env` 启动后端**

Run: `cd rule-engine-server; mvn spring-boot:run`

Expected: 服务启动；`.env` 值不打印到命令或提交内容。工具超时设置 `1800000ms`。

- [ ] **Step 3: 验证 liveness/readiness 转换**

Run: `node scripts/quality-gates/wait-for-readiness.mjs --base-url http://127.0.0.1:8080 --timeout-seconds 120`

Expected: liveness UP；数据库、Redis 与 ONNX 状态满足后 readiness UP。对可控测试配置执行至少一个 ONNX FAILED 或依赖 DOWN 场景，确认仅 readiness 降级。

- [ ] **Step 4: 对本地测试接口运行容量门禁**

Run 示例（阈值必须由验收环境明确给出后替换）：

```powershell
node scripts/quality-gates/run-capacity-gate.mjs `
  --url http://127.0.0.1:8080/api/rule/open/execute `
  --request-file scripts/quality-gates/fixtures/request.local.json `
  --concurrency 16 `
  --warmup-seconds 10 `
  --duration-seconds 60 `
  --max-error-rate 0.01 `
  --max-p95-ms 300 `
  --min-throughput 20 `
  --report-dir target/quality-gates
```

Expected: 脚本按阈值返回 0 或非零，报告完整且脱敏。`request.local.json` 只在本地创建并确保被忽略，不提交凭据。

- [ ] **Step 5: 主动停止后端与依赖进程**

确认 8080 端口释放；不要依赖工具超时自动清理。

- [ ] **Step 6: 运行后端全量测试**

Run: `mvn test`

Expected: 0 failures、0 errors；仅允许已声明的外部 ONNX/CUDA 诊断测试条件跳过。

- [ ] **Step 7: 审查最终差异和敏感信息**

Run: `git diff --check`

Run: `git diff -- rule-engine-server/pom.xml rule-engine-server/src/main rule-engine-server/src/test scripts/quality-gates .github/workflows/ci.yml README.md docs/quality-gates`

Run: `git status --short`

确认 `.env`、`request.local.json`、容量报告未进入 Git，liveness 不依赖外部组件，readiness 含 db/redis/onnxWarmup，CI 不使用不稳定的绝对容量阈值。
