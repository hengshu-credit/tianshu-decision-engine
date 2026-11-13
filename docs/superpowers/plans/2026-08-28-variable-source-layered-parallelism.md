# Variable Source Layered Parallelism Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变变量依赖、失败策略、来源状态和追踪顺序的前提下，对同一依赖层内互不依赖的 API、数据库和名单变量进行有界并行解析，并用可重复基准决定是否启用大于 1 的默认并行度。

**Architecture:** 保留现有依赖迭代框架，将每轮“已就绪变量”划成一个 wave；主线程创建不可变参数快照并提交纯任务，任务只返回 `SourceResolutionResult`，不写共享 `resolvedParams`、`VariableResolveOptions.sourceStates` 或追踪帧。主线程等待整层结束后按变量原始顺序合并值、来源状态和追踪事件，再继续顺序执行模型。专用 `SourceResolutionExecutor` 使用固定大小、有限队列和 `CallerRunsPolicy`，API 相同响应在单次调用内用 `CompletableFuture` singleflight 去重。

**Tech Stack:** Java 17、Spring Boot 3.5、JUnit 4、Maven、JDK `ThreadPoolExecutor`/`CompletableFuture`。

**Spec:** `docs/superpowers/specs/2026-08-28-variable-source-layered-parallelism-design.md`

## Global Constraints

- [ ] 直接在当前 `master` 工作区修改，不创建或切换分支、worktree。
- [ ] 常量仍在来源解析前处理；模型本期保持顺序解析。
- [ ] 变量/模型引用关系只按 ID 和现有结构化依赖处理，不使用名称推断新的关联。
- [ ] Worker 禁止直接写 `LinkedHashMap resolvedParams`、`VariableResolveOptions.sourceStates`、`RuleTraceFrame.events`。
- [ ] 默认并行度初始保持 1；只有正确性测试全通过且可重复基准中位数提升不少于 20% 才改为 4。
- [ ] 不引入第三方并发或基准库。

---

### Task 1: 建立有界执行器及其生命周期

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/SourceResolutionExecutor.java`
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/SourceResolutionExecutorTest.java`
- Modify: `rule-engine-server/src/main/resources/application.yml`

- [ ] **Step 1: 写执行器失败测试**

覆盖：parallelism=1 时同一调用线程顺序执行；parallelism>1 时最多只有配置数量的任务并行；队列满时 `CallerRunsPolicy` 提供背压且不丢任务；非法并行度被拒绝；Spring 销毁时等待已有任务并关闭线程。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl rule-engine-server -am -Dtest=SourceResolutionExecutorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，类尚不存在。

- [ ] **Step 3: 实现专用 Spring 组件**

构造器读取：

```java
@Value("${rule-engine.source-resolution.parallelism:1}") int parallelism
```

parallelism=1 走直接执行器，不创建线程；大于 1 时建立固定大小 `ThreadPoolExecutor`，队列容量采用与并行度成比例的有限值（固定 `parallelism * 4`），线程名 `rule-source-resolver-N`，拒绝策略 `CallerRunsPolicy`。用 `@PreDestroy` 关闭并在有限时间后 `shutdownNow()`。

- [ ] **Step 4: 写入保守默认配置**

```yaml
rule-engine:
  source-resolution:
    parallelism: ${SOURCE_RESOLUTION_PARALLELISM:1}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -pl rule-engine-server -am -Dtest=SourceResolutionExecutorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 2: 建立不可变的层任务结果和合并契约

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/SourceResolutionResult.java`
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/SourceResolutionResultTest.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/VariableResolveOptions.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/VariableSourceResolverTest.java`

- [ ] **Step 1: 写不可变性和稳定合并失败测试**

结果对象包含变量原始序号、scriptName、值、来源状态副本、追踪事件副本和可选异常/失败策略结果。构造后修改输入 Map/List 不影响结果；按序号合并时输出键、sourceStates 和 trace events 的顺序与原始变量顺序一致。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl rule-engine-server -am -Dtest=SourceResolutionResultTest,VariableSourceResolverTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL。

- [ ] **Step 3: 实现结果载体和批量状态合并方法**

`SourceResolutionResult` 采用 final 字段、构造时防御性复制、只读 getter。`VariableResolveOptions` 增加仅供主线程调用的 `mergeSourceStates(Map<...>)`，内部仍使用现有 `LinkedHashMap` 并保持维度写入顺序；不要把共享 Map 改成并发 Map 来掩盖顺序问题。

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -pl rule-engine-server -am -Dtest=SourceResolutionResultTest,VariableSourceResolverTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 3: 为 Worker 提供最小运行时上下文快照和追踪回放

**Files:**
- Modify: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/engine/RuntimeContextBridge.java`
- Create: `rule-engine-core/src/test/java/com/hengshucredit/rule/core/engine/RuntimeContextBridgeTest.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuntimeTraceService.java`（仅在回放接口需适配时）
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuntimeTraceServiceTest.java`

- [ ] **Step 1: 创建 Bridge 测试文件并写上下文隔离失败测试**

覆盖：主线程规则上下文可复制到 worker；worker 使用独立 trace listener 收集事件；worker `clear()` 后线程池线程不残留前一次上下文；worker 不能回写主线程 CURRENT_RULE、SOURCE_STATES 或 runtime listener；主线程按提交顺序回放事件。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl rule-engine-core,rule-engine-server -am -Dtest=RuntimeContextBridgeTest,RuntimeTraceServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，新快照 API 缺失。

- [ ] **Step 3: 实现最小快照 API**

在 `RuntimeContextBridge` 增加不可变 `ContextSnapshot`，只复制来源解析实际需要的 currentRule、matchedConditions 和 sourceStates；提供 `capture()`、`install(snapshot, traceListener)`，调用方必须在 `finally` 中 `clear()`。不要传播运行时写监听器和常量写保护状态，除非测试证明来源解析确实依赖它们。

- [ ] **Step 4: 实现主线程追踪回放**

worker 将 `RuntimeTraceService` 产生的事件写入本地 `List<Map<String,Object>>`；主线程合并 `SourceResolutionResult` 时依序调用 `RuntimeContextBridge.addTraceEvent`，保证现有 `RuleTraceFrame.events` 仍由请求线程串行写入。

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -pl rule-engine-core,rule-engine-server -am -Dtest=RuntimeContextBridgeTest,RuntimeTraceServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 4: 实现单次解析调用内的 API singleflight

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/VariableResolutionInvocationCache.java`
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/VariableResolutionInvocationCacheTest.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/VariableSourceResolver.java`

- [ ] **Step 1: 写并发去重失败测试**

同一 API 响应键的两个并发变量只触发一次 supplier；两个调用者获得相同不可变响应副本；失败的 future 向所有等待者传播同一根因；不同响应键可以并行；每次顶层 `resolve/resolveIntoSnapshot` 创建独立 cache，不跨请求复用。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl rule-engine-server -am -Dtest=VariableResolutionInvocationCacheTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL。

- [ ] **Step 3: 实现 future singleflight**

内部使用：

```java
private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> apiResponses;
```

首个调用者通过 `putIfAbsent` 成为 owner 并完成 future；等待者 join 同一 future。失败时完成 exceptionally，并保留到本次解析结束，避免同一请求内故障风暴重试。返回值防御性复制。

- [ ] **Step 4: 替换现有 `LinkedHashMap apiResponseCache` 参数**

让顶层入口创建 `VariableResolutionInvocationCache`，`resolveApiVariable` 使用它；不得将 cache 变成 singleton Bean。

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -pl rule-engine-server -am -Dtest=VariableResolutionInvocationCacheTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 5: 将变量解析重构为依赖 wave 内并行、wave 间串行

**Files:**
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/VariableSourceResolver.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/VariableSourceResolverTest.java`

- [ ] **Step 1: 写核心正确性失败测试**

覆盖：同层独立 API/DB/LIST 在 parallelism=4 下同时进入并在闩锁释放后完成；B 依赖 A 时绝不与 A 同层；模型等待所需变量后仍顺序执行；parallelism=1 与旧顺序一致；输入 Map 在 worker 中不可变；同层完成顺序颠倒时最终 Map、sourceStates、trace events 仍按原变量顺序；超时/default/null/throw 策略结果与顺序模式完全一致。

- [ ] **Step 2: 写异常与清理失败测试**

一个任务失败不造成线程泄漏或 future 永久等待；现有 `resolveOneSourceVariable` 的错误分类、API failure 状态、运行时调用日志和默认值逻辑不丢失；循环依赖错误文本仍包含待解析名称。

- [ ] **Step 3: 运行测试确认失败**

Run: `mvn -pl rule-engine-server -am -Dtest=VariableSourceResolverTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL。

- [ ] **Step 4: 注入执行器并提取 ready wave**

每轮先扫描 pendingVariables：依赖未满足的保留，已满足的记录原始序号并加入 ready wave。用 `Collections.unmodifiableMap(new LinkedHashMap<>(resolvedParams))` 作为本层共享只读快照。常量变量跳过任务；只允许 API/DB/LIST 进入 executor。

- [ ] **Step 5: 让 worker 只生成结果**

把现有 `resolveOneSourceVariable` 拆为“在局部 Map/局部 options 中复用原解析与失败策略”并转成 `SourceResolutionResult`。每个 worker 安装上下文快照、本地 trace listener，并在 finally 清理。

- [ ] **Step 6: 主线程稳定合并后再解析模型**

等待本层所有任务，按原始序号排序；依次写 `resolvedParams`、merge source states、回放 trace。随后执行现有 pendingModels 顺序循环。只有本轮至少合并一个变量或模型才算 progressed。

- [ ] **Step 7: 运行定向正确性测试**

Run: `mvn -pl rule-engine-server -am -Dtest=VariableSourceResolverTest,VariableResolutionInvocationCacheTest,SourceResolutionExecutorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 6: 建立可重复基准并决定默认并行度

**Files:**
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/VariableSourceResolverBenchmarkTest.java`
- Modify: `rule-engine-server/src/main/resources/application.yml`（仅当门禁通过）
- Create: `docs/quality-gates/variable-source-resolution-benchmark.md`

- [ ] **Step 1: 实现显式开启的阻塞型基准**

测试仅在 `-Dtianshu.source-resolution.benchmark=true` 时运行；构造固定数量同层 API/DB/LIST 延迟源，同时比较 parallelism=1 和 4。每种模式预热 3 次、测量至少 9 次，记录中位数、p95、任务数、机器/JDK 信息；每轮核对完整输出、sourceStates 和 trace 顺序，不只计时。

- [ ] **Step 2: 运行正确性全量定向测试**

Run: `mvn -pl rule-engine-core,rule-engine-server -am -Dtest=RuntimeContextBridgeTest,RuntimeTraceServiceTest,SourceResolutionExecutorTest,SourceResolutionResultTest,VariableResolutionInvocationCacheTest,VariableSourceResolverTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

- [ ] **Step 3: 运行基准门禁三次**

Run: `1..3 | ForEach-Object { mvn -pl rule-engine-server -am -Dtest=VariableSourceResolverBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false -Dtianshu.source-resolution.benchmark=true test; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }`

Expected: 每次输出 parallelism=4 相对 1 的中位数提升；三次均不少于 20%，且正确性断言全部通过。

- [ ] **Step 4: 按门禁结果设置默认值**

若且仅若 Step 2、3 全部通过，将配置改为 `${SOURCE_RESOLUTION_PARALLELISM:4}`；否则保留默认 1，并在基准文档记录结果和未启用原因。无论结果如何，都保留环境变量显式调整能力。

- [ ] **Step 5: 写入可审计基准记录**

记录命令、日期、JDK、逻辑处理器、样本规模、三轮中位数/p95、正确性结论和最终默认值；不得记录 `.env` 或数据源凭据。

### Task 7: 后端启动与完整回归门禁

**Files:**
- Verify only

- [ ] **Step 1: 编译所有模块**

Run: `mvn clean install -DskipTests`

Expected: BUILD SUCCESS。

- [ ] **Step 2: 启动后端验证执行器生命周期**

Run: `cd rule-engine-server; mvn spring-boot:run`

Expected: 服务正常启动；线程名和配置符合预期；停止后无 `rule-source-resolver-*` 线程残留。工具超时设置 `1800000ms`，验证后主动停止。

- [ ] **Step 3: 运行后端全量测试**

Run: `mvn test`

Expected: 0 failures、0 errors；仅允许已声明的外部 ONNX/CUDA 测试条件跳过。

- [ ] **Step 4: 审查并发边界**

Run: `git diff --check`

Run: `git diff -- rule-engine-core/src/main/java/com/hengshucredit/rule/core/engine/RuntimeContextBridge.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/service rule-engine-server/src/test/java/com/hengshucredit/rule/server/service rule-engine-server/src/main/resources/application.yml`

逐项确认 worker 无共享可变写入、模型未并行、异常策略未改变、executor 可关闭、默认并行度有基准证据。
