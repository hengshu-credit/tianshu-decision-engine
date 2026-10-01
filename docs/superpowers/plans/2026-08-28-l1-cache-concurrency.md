# L1 Cache Concurrency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `L1MemoryCache` 的热点读取从全局同步锁中移出，同时保持全量快照替换、增量推送和容量淘汰之间的原子语义。

**Architecture:** 缓存字段改为 `volatile ConcurrentHashMap` 快照引用；`get`、`size`、`getVersions` 捕获一次当前引用并无锁读取；`put/remove/clear/replaceSnapshot` 继续由 `cacheLock` 串行化。全量快照在写锁内构建并一次发布，保证同步期间到达的增量写不会落到即将废弃的旧 Map。

**Tech Stack:** Java 17、JUnit 4、Maven。

**Spec:** `docs/superpowers/specs/2026-08-28-l1-cache-concurrency-design.md`

## Global Constraints

- [ ] 直接在当前 `master` 工作区修改，不创建或切换分支、worktree。
- [ ] 不改变 `L1MemoryCache` 的公共调用接口和当前容量淘汰语义。
- [ ] 并发测试必须使用锁存器确定顺序，不使用 `Thread.sleep` 猜测时序。
- [ ] 任何线程都必须在测试结束前 join，失败路径也要释放锁存器，避免测试进程悬挂。

---

### Task 1: 用确定性测试复现读操作被快照写锁阻塞

**Files:**
- Modify: `rule-engine-client-sdk/src/test/java/com/hengshucredit/rule/client/cache/L1MemoryCacheTest.java`

- [ ] **Step 1: 增加构造参数校验测试**

断言 `new L1MemoryCache(0)` 和负数容量抛出 `IllegalArgumentException`，正数容量正常工作。

- [ ] **Step 2: 增加快照构建期间无锁读取测试**

先放入 `current`；用现有 `BlockingRuleList` 让 `replaceSnapshot` 持有写锁并停在构建阶段；分别在线程中执行 `get("current")`、`size()`、`getVersions()`，要求它们在允许快照提交前完成且读取同一个旧快照。

- [ ] **Step 3: 增加原子发布可见性测试**

释放快照后，等待替换线程结束，再断言后续三类读取只看到新快照；任何一次 `getVersions()` 结果都不得混合新旧快照键。

- [ ] **Step 4: 保留并强化增量写顺序测试**

保留 `incrementalPutWaitsForSnapshotCommitInsteadOfWritingTheReplacedMap`，增加线程存活/超时断言，证明 `put` 在快照提交后写入新 Map。

- [ ] **Step 5: 运行测试确认旧实现失败**

Run: `mvn -pl rule-engine-client-sdk -am -Dtest=L1MemoryCacheTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，快照锁未释放前读取线程无法完成；容量校验测试也失败。

### Task 2: 实现 volatile 快照和无锁读取

**Files:**
- Modify: `rule-engine-client-sdk/src/main/java/com/hengshucredit/rule/client/cache/L1MemoryCache.java`

- [ ] **Step 1: 校验容量并安全初始化**

构造器在分配 Map 前执行：

```java
if (maxSize <= 0) {
    throw new IllegalArgumentException("L1 cache maxSize must be greater than zero");
}
```

- [ ] **Step 2: 将缓存引用改为 volatile**

```java
private volatile ConcurrentHashMap<String, CachedRule> cache;
```

保留 `cacheLock` 与全部写方法的同步边界。

- [ ] **Step 3: 将热点读改为单快照引用读取**

```java
public CachedRule get(String ruleCode) {
    return cache.get(ruleCode);
}

public int size() {
    return cache.size();
}

public Map<String, Integer> getVersions() {
    ConcurrentHashMap<String, CachedRule> snapshot = cache;
    Map<String, Integer> versions = new LinkedHashMap<>();
    snapshot.forEach((key, value) -> versions.put(key, value.getVersion()));
    return versions;
}
```

`getVersions` 必须只捕获一次引用，避免在一次返回中跨越快照。

- [ ] **Step 4: 核对写方法只访问锁内当前引用**

`put/remove/clear` 在进入 `synchronized (cacheLock)` 后操作当前 `cache`；`replaceSnapshot` 继续在同一锁内完整构建后赋值。不要将快照构建移出锁，否则会重新引入“增量写丢进旧 Map”的竞态。

- [ ] **Step 5: 运行定向测试确认通过**

Run: `mvn -pl rule-engine-client-sdk -am -Dtest=L1MemoryCacheTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 3: 并发回归与后端完整门禁

**Files:**
- Test: `rule-engine-client-sdk/src/test/java/com/hengshucredit/rule/client/cache/L1MemoryCacheTest.java`

- [ ] **Step 1: 重复执行并发测试排除偶发通过**

Run: `1..20 | ForEach-Object { mvn -q -pl rule-engine-client-sdk -am -Dtest=L1MemoryCacheTest -Dsurefire.failIfNoSpecifiedTests=false test; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }`

Expected: 20 次全部 PASS，进程不悬挂。

- [ ] **Step 2: 编译所有模块**

Run: `mvn clean install -DskipTests`

Expected: BUILD SUCCESS。

- [ ] **Step 3: 启动后端进行启动验证**

Run: `cd rule-engine-server; mvn spring-boot:run`

Expected: 服务完成启动且无缓存初始化异常。工具超时设置 `1800000ms`；验证后主动停止进程。

- [ ] **Step 4: 运行后端全量测试**

Run: `mvn test`

Expected: 0 failures、0 errors；仅允许仓库已声明的外部 ONNX/CUDA 诊断测试按条件跳过。

- [ ] **Step 5: 审查最终差异**

Run: `git diff --check`

Run: `git diff -- rule-engine-client-sdk/src/main/java/com/hengshucredit/rule/client/cache/L1MemoryCache.java rule-engine-client-sdk/src/test/java/com/hengshucredit/rule/client/cache/L1MemoryCacheTest.java`

确认所有读路径均无 `cacheLock`，所有写路径仍共享同一锁，且没有引入额外依赖。

