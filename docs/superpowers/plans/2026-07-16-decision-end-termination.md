# Decision End Termination Semantics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让决策树和决策流支持“跳出当前规则”和“跳出整体规则”两种结束节点，并保证服务端与客户端 SDK 的嵌套规则终止语义一致。

**Architecture:** 继续使用 `end-event`/`type=end`，以 `terminationScope` 区分 `CURRENT_RULE` 与 `ALL_RULES`。当前规则结束由编译器生成 QLExpress `return`；整体结束通过 core 专用 `RuleTerminationSignal` 穿透子规则调用栈，只在根执行边界转换为成功结果。根输出脚本名随发布规则同步到客户端，不增加数据库列。

**Tech Stack:** Java 8、Spring Boot 2.3、QLExpress 4.1.0、JUnit 4、Vue 2.6、Element UI 2.15、Jest、Vue Test Utils。

## Global Constraints

- 所有引用仍通过稳定 ID 关联，禁止按变量名或模型名回溯。
- 历史结束节点没有 `terminationScope` 时必须按 `CURRENT_RULE` 处理。
- 不新增数据库列，不增加第三方依赖。
- 服务端执行和 `rule-engine-client` 本地执行必须具有相同终止语义。
- 现有未跟踪的 `rule-engine-mysql/logs/` 不得修改或提交。
- 每个行为变更必须先写失败测试并确认按预期失败，再写最小实现。

---

### Task 1: 图编译器结束节点语义

**Files:**
- Modify: `rule-engine-core/src/test/java/com/hengshucredit/rule/core/compiler/DecisionFlowCompilerTest.java`
- Modify: `rule-engine-core/src/test/java/com/hengshucredit/rule/core/compiler/DecisionTreeCompilerTest.java`
- Modify: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/compiler/DecisionFlowCompiler.java`
- Modify: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/compiler/DecisionTreeCompiler.java`
- Modify: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/compiler/GraphScriptGenerator.java`
- Modify: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/compiler/RuleScriptResultCollector.java`

**Interfaces:**
- Consumes: graph node field `terminationScope` with values `CURRENT_RULE` and `ALL_RULES`.
- Produces: `GraphScriptGenerator.generate(..., Collection<String> outputVars)`; current end emits result-map `return`, global end emits `terminateAllRules()`.

- [ ] **Step 1: Write failing compiler tests**

Add tests equivalent to:

```java
@Test
public void compileFlowWithoutEndNodeSuccessfully() {
    CompileResult result = compiler.compile("{\"nodes\":[{\"id\":\"start\",\"type\":\"start\"},{\"id\":\"task\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"decisionResult\",\"value\":\"\\\"PASS\\\"\"}]}],\"edges\":[{\"source\":\"start\",\"target\":\"task\"}]}");
    assertTrue(result.getErrorMessage(), result.isSuccess());
}

@Test
public void currentRuleEndReturnsBeforeFollowingTask() {
    // start -> first -> end(CURRENT_RULE), plus a downstream task that must not execute
    // assert compiled script contains "return _result" and execution keeps downstream value null
}

@Test
public void globalEndCompilesTerminationFunction() {
    // assert compiled script contains "terminateAllRules()"
}
```

Add the same current/global end assertions to `DecisionTreeCompilerTest`, including a legacy end node without scope that compiles as `CURRENT_RULE`.

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
mvn -pl rule-engine-core -Dtest=DecisionFlowCompilerTest,DecisionTreeCompilerTest test
```

Expected: failures show flow without end is rejected and compiled scripts do not contain the required return/termination statements.

- [ ] **Step 3: Implement minimal compiler behavior**

Move output-variable collection before graph generation in both compilers. Remove only `DecisionFlowCompiler`'s `endCount == 0` failure. Extend `GraphScriptGenerator` so an `end` node uses:

```java
String scope = node.getString("terminationScope");
if ("ALL_RULES".equals(scope)) {
    appendIndent(script, indent);
    script.append("terminateAllRules()\n");
} else {
    RuleScriptResultCollector.appendResultMapAssignment(script, outputVars, indent);
    appendIndent(script, indent);
    script.append("return _result\n");
}
```

For no outputs emit `return null`. Preserve the existing final result collector for paths that end naturally.

- [ ] **Step 4: Run targeted tests and verify GREEN**

Run the command from Step 2. Expected: all decision tree/flow compiler tests pass.

- [ ] **Step 5: Commit**

```powershell
git add rule-engine-core
git commit -m "feat: add decision end compilation semantics"
```

### Task 2: Core controlled termination signal

**Files:**
- Create: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/engine/RuleTerminationSignal.java`
- Create: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/engine/RuleTerminationResultBuilder.java`
- Create: `rule-engine-core/src/test/java/com/hengshucredit/rule/core/engine/RuleTerminationSignalTest.java`
- Create: `rule-engine-core/src/test/java/com/hengshucredit/rule/core/engine/RuleTerminationResultBuilderTest.java`
- Modify: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/engine/QLExpressEngine.java`
- Modify: `rule-engine-core/src/test/java/com/hengshucredit/rule/core/engine/QLExpressEngineTest.java`

**Interfaces:**
- Produces: `new RuleTerminationSignal(Map<String,Object> result)`, `RuleTerminationSignal.find(Throwable)`, `RuleTerminationResultBuilder.build(Object context, List<String> outputScriptNames)`.
- Consumed by: server and client runtime tasks.

- [ ] **Step 1: Write failing core tests**

Test that `find` locates a signal nested in wrapped causes, ordinary exceptions return `null`, and result construction preserves ordered keys, nested paths, and missing values as `null`. Register a test function that throws the signal and assert `QLExpressEngine.execute` rethrows the exact controlled signal instead of returning `success=false`.

- [ ] **Step 2: Run tests and verify RED**

```powershell
mvn -pl rule-engine-core -Dtest=RuleTerminationSignalTest,RuleTerminationResultBuilderTest,QLExpressEngineTest test
```

Expected: compilation fails because the two new core types do not exist.

- [ ] **Step 3: Implement signal, result builder, and engine unwrapping**

`RuleTerminationSignal` carries an immutable result object and walks `Throwable.getCause()` with identity-cycle protection. `RuleTerminationResultBuilder` converts Map/JSONObject/Java bean contexts to readable values and resolves dotted paths. In both `QLExpressEngine.execute` overloads, check `RuleTerminationSignal.find(e)` before the generic failure conversion and rethrow the signal.

- [ ] **Step 4: Run targeted tests and verify GREEN**

Run Step 2 command. Expected: all tests pass and ordinary execution-error tests remain unchanged.

- [ ] **Step 5: Commit**

```powershell
git add rule-engine-core
git commit -m "feat: propagate controlled rule termination"
```

### Task 3: Publish and synchronize root output metadata

**Files:**
- Modify: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/RulePublished.java`
- Modify: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/RulePushMessage.java`
- Modify: `rule-engine-client/src/main/java/com/hengshucredit/rule/client/cache/CachedRule.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/sync/RuleSyncController.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RulePublishService.java`
- Modify: `rule-engine-client/src/main/java/com/hengshucredit/rule/client/sync/HttpSyncClient.java`
- Modify: `rule-engine-client/src/main/java/com/hengshucredit/rule/client/sync/RedisSubscriber.java`
- Modify: corresponding tests under `rule-engine-server/src/test/.../RuleSyncControllerTest.java`, `rule-engine-client/src/test/.../sync/HttpSyncClientTest.java`, and `RedisSubscriberTest.java`.

**Interfaces:**
- Produces: ordered `List<String> outputScriptNames` on published/sync/cache payloads.
- Consumes: `RuleDefinitionService.listOutputFields(definitionId)` and `RuleDefinitionOutputField.scriptName`.

- [ ] **Step 1: Write failing metadata tests**

Assert HTTP JSON mapping and Redis push mapping preserve `outputScriptNames=["decisionResult","risk.score"]`; assert controller enrichment filters blank script names and preserves `sortOrder` order.

- [ ] **Step 2: Run tests and verify RED**

```powershell
mvn -pl rule-engine-client,rule-engine-server -am -Dtest=HttpSyncClientTest,RedisSubscriberTest,RuleSyncControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: failures show missing getters/setters and missing mapping.

- [ ] **Step 3: Implement non-persistent metadata propagation**

Add `@TableField(exist = false) private List<String> outputScriptNames;` to `RulePublished`, matching fields on push/cache types. Enrich single/all sync responses from output-field rows, populate publish messages, and copy the field in `HttpSyncClient.toCachedRule` and `RedisSubscriber.resolvePublishedRule`. Do not change SQL.

- [ ] **Step 4: Run targeted tests and verify GREEN**

Run Step 2 command. Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```powershell
git add rule-engine-model rule-engine-server rule-engine-client
git commit -m "feat: sync published rule output metadata"
```

### Task 4: Server and client nested whole-rule termination

**Files:**
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleExecutionSession.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleRuntimeInvoker.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleExecuteService.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleRuntimeInvokerTest.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleExecuteServiceTest.java`
- Modify: `rule-engine-client/src/main/java/com/hengshucredit/rule/client/ClientRuleRuntimeInvoker.java`
- Modify: `rule-engine-client/src/main/java/com/hengshucredit/rule/client/RuleEngineClient.java`
- Modify: `rule-engine-client/src/test/java/com/hengshucredit/rule/client/ClientRuleRuntimeInvokerTest.java`

**Interfaces:**
- Consumes: `outputScriptNames`, `RuleTerminationResultBuilder`, `RuleTerminationSignal`.
- Produces: QL function `terminateAllRules()` in both runtime environments; root `RuleResult(success=true, result=rootOutputMap)`.

- [ ] **Step 1: Write failing nested execution tests**

Create parent scripts shaped as `before = 1; executeRule("CHILD"); after = 2; after` and child scripts as `childValue = 9; terminateAllRules(); childAfter = 10`. Assert `after` and `childAfter` are absent, result keys follow root `outputScriptNames`, assigned values survive, and missing output values are `null`. Repeat for server and client invokers. Add a `RuleExecuteServiceTest` assertion that controlled termination is successful while an ordinary exception remains failed.

- [ ] **Step 2: Run tests and verify RED**

```powershell
mvn -pl rule-engine-server,rule-engine-client -am -Dtest=RuleRuntimeInvokerTest,RuleExecuteServiceTest,ClientRuleRuntimeInvokerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: QLExpress reports unknown `terminateAllRules` or returns a failed result.

- [ ] **Step 3: Implement server runtime propagation**

Store root output names in `RuleExecutionSession`, register the zero-argument `terminateAllRules` service method, build the root result from the shared values map, and throw `RuleTerminationSignal`. In child invocation catches, rethrow controlled signals without converting them to `IllegalStateException`. In both test and published root execution catches, convert only this signal to a successful `RuleResult`.

- [ ] **Step 4: Implement client runtime propagation**

Store `CachedRule.outputScriptNames` in the root `ExecutionFrame`, register `terminateAllRules`, and throw a signal containing `RuleTerminationResultBuilder.build(frame.context, frame.outputScriptNames)`. In both `RuleEngineClient.doExecute` overloads, catch the signal at the root and return a successful result before trace completion and log reporting.

- [ ] **Step 5: Run targeted tests and verify GREEN**

Run Step 2 command. Expected: all server/client nested execution tests pass.

- [ ] **Step 6: Commit**

```powershell
git add rule-engine-server rule-engine-client
git commit -m "feat: terminate nested rule chains on demand"
```

### Task 5: Frontend end-node selection and presentation

**Files:**
- Create: `rule-engine-builder-ui/src/components/flow/EndNodeScopeDialog.vue`
- Create: `rule-engine-builder-ui/src/utils/endNodeScope.js`
- Create: `rule-engine-builder-ui/tests/unit/components/endNodeScopeDialog.spec.js`
- Create: `rule-engine-builder-ui/tests/unit/utils/endNodeScope.spec.js`
- Modify: `rule-engine-builder-ui/src/components/flow/nodes.js`
- Modify: `rule-engine-builder-ui/src/views/designer/DecisionTree.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/DecisionFlow.vue`
- Modify: `rule-engine-builder-ui/tests/unit/views/flowDesignerStyle.spec.js`

**Interfaces:**
- Produces: `CURRENT_RULE`, `ALL_RULES`, `normalizeTerminationScope(scope)`, `endNodeAppearance(scope)` and dialog event `confirm(scope)`.
- Consumes: existing LogicFlow `end-event` node and designer `addNode` flow.

- [ ] **Step 1: Write failing frontend tests**

Test that `getDefaultFlowData().nodes` contains only a start node; scope normalization defaults missing values to `CURRENT_RULE`; current appearance is orange/“返回”; global appearance is red/“终止”; dialog defaults to current, cancel emits no confirmation, and confirm emits the selected scope. Add source/component assertions that both designers serialize/restore `terminationScope` and DecisionFlow no longer reports “缺少结束节点”.

- [ ] **Step 2: Run tests and verify RED**

```powershell
cd rule-engine-builder-ui
npm test -- --runInBand tests/unit/utils/endNodeScope.spec.js tests/unit/components/endNodeScopeDialog.spec.js tests/unit/views/flowDesignerStyle.spec.js
```

Expected: new modules are missing and the default template still contains an end node.

- [ ] **Step 3: Implement shared scope utility and dialog**

Use exact constants and presentation data:

```js
export const CURRENT_RULE = 'CURRENT_RULE'
export const ALL_RULES = 'ALL_RULES'

export function normalizeTerminationScope(scope) {
  return scope === ALL_RULES ? ALL_RULES : CURRENT_RULE
}
```

The Element UI dialog contains two radio choices with the approved warnings, defaults to current on each open, emits `confirm(scope)` only after the user clicks confirm, and emits `update:visible=false` on close.

- [ ] **Step 4: Implement node rendering and designer persistence**

Remove the default end node from `getDefaultFlowData`. Make `EndEventView` derive fill/stroke/text from `endNodeAppearance(properties.terminationScope)`. Both designers open the shared dialog before adding an end node, persist scope in LogicFlow properties and backend nodes, restore missing scope as current, and show scope-aware labels in the property panel. Remove only DecisionFlow's missing-end validation.

- [ ] **Step 5: Run targeted tests and eslint**

```powershell
npm test -- --runInBand tests/unit/utils/endNodeScope.spec.js tests/unit/components/endNodeScopeDialog.spec.js tests/unit/views/flowDesignerStyle.spec.js
npm run lint
```

Expected: tests and lint pass without warnings introduced by these files.

- [ ] **Step 6: Commit**

```powershell
git add rule-engine-builder-ui
git commit -m "feat: add scoped decision end nodes"
```

### Task 6: Full verification and live UI acceptance

**Files:**
- Verify all files changed in Tasks 1-5.
- Do not modify unrelated files unless a failing regression is directly caused by this feature.

**Interfaces:**
- Consumes: completed feature.
- Produces: build, test, startup, runtime, and browser evidence.

- [ ] **Step 1: Run backend compile and full tests**

```powershell
mvn clean install -DskipTests
mvn test
```

Expected: all modules build and all backend tests pass.

- [ ] **Step 2: Start and smoke-test backend**

Start `rule-engine-server` with `mvn spring-boot:run`, verify port 8080 and a real authenticated health/API request, then stop it before further clean builds.

- [ ] **Step 3: Start frontend and inspect runtime**

Run `npm run dev`, open real decision tree and decision flow designer routes, confirm browser console has no runtime/accessibility errors, then stop the dev server.

- [ ] **Step 4: Run full frontend tests**

```powershell
cd rule-engine-builder-ui
npm test -- --runInBand
```

Expected: all Jest suites pass.

- [ ] **Step 5: Browser workflow acceptance**

Through the UI, create new decision tree and flow definitions and verify only a start node appears. Add each end scope through the confirmation dialog, verify orange/red presentation, save/compile/publish, run a current-rule child path that allows its parent to continue, and run an all-rules child path that prevents all parent follow-up actions while returning the root output Map.

- [ ] **Step 6: Review diff and commit verification fixes if any**

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors and only intended feature files plus the pre-existing untracked MySQL logs are present.
