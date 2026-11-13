# 天枢决策引擎正确性与状态反馈修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复字段配置进度、规则设计器草稿副作用、FIRST 决策表追踪、模型实际运行状态和规则测试页识别/布局五类问题，使页面只展示真实配置与运行状态。

**Architecture:** 不新增数据库结构或跨模块框架。字段状态在 `VariableList.vue` 内按依赖链计算；九个设计器通过 `ruleDraftMixin.js` 与 `RuleDraftReadOnly.vue` 共享只读/开始编辑行为；追踪树按规则顺序合并静态 `definitionModel.rules` 与动态表达式 AST；模型状态由现有 `OnnxRuntimeSessionManager` 只读查询并由 `RuleModelService` 聚合；规则测试页仅增加展示派生与模板顺序调整。

**Tech Stack:** Vue 3.5、Element Plus 2.14、Vitest 4、Spring Boot 3.5、Java 17、JUnit 4、MyBatis Plus、ONNX Runtime 1.26。

## Global Constraints

- 直接在 `master` 实施；用户已明确授权，不创建分支或 worktree。
- 一级菜单继续平铺，不做菜单收拢、分组、角色模式切换或专家模式。
- 业务人员可直接配置 SQL；本批次不隐藏、不改写、不自动转换 SQL。
- 所有变量、模型、规则引用继续通过稳定 ID 关联；输出引用继续依赖 `ref_type`。
- 用户输入的编码、名称、导入字段与 SQL 原样保留，不做命名转换或派生字段回写。
- 不新增第三方依赖，不新增数据库迁移，不改变既有 REST 路径。
- 每项生产代码必须先有能因目标缺陷而失败的行为测试，再写最小实现。

---

### Task 1: 字段配置就绪依赖链与预览反馈

**Files:**
- Modify: `rule-engine-builder-ui/src/views/variable/VariableList.vue`
- Test: `rule-engine-builder-ui/tests/unit/views/variableList.spec.js`

**Interfaces:**
- Consumes: 现有 `form`、`draftPreviewResult`、`previewDraftVariable()`、`resetDraftPreview()`。
- Produces: `draftPreviewError: string`；`variableConfigurationChecklist` 严格依次依赖 `scopeReady → definitionReady → sourceReady → validationReady`。

- [ ] **Step 1: 写空白表单和依赖链失败测试**

```js
test('空白字段从 0/4 开始且后续步骤不能越过前置步骤', () => {
  wrapper.vm.form = wrapper.vm.initForm()
  expect(wrapper.vm.variableConfigurationChecklist.map(item => item.ready))
    .toEqual([false, false, false, false])
  expect(wrapper.vm.variableConfigurationReadyCount).toBe(0)
})
```

- [ ] **Step 2: 写预览失效和失败信息失败测试**

```js
test('来源配置变化后旧预览立即失效', async () => {
  wrapper.vm.dialogVisible = true
  wrapper.vm.form = {
    ...wrapper.vm.initForm(), scope: 'PROJECT', projectId: 1,
    varCode: 'riskScore', varLabel: '风险分', varType: 'NUMBER',
    varSource: 'DB', dbDatasourceId: 21, dbSql: 'select 1', dbParams: '[]'
  }
  wrapper.vm.draftPreviewResult = { resolvedValue: 1 }
  wrapper.vm.form.dbSql = 'select 2'
  await nextTick()
  expect(wrapper.vm.draftPreviewResult).toBeNull()
  expect(wrapper.vm.variableConfigurationChecklist[3].ready).toBe(false)
})

test('预览失败保留可见错误但验证步骤保持未完成', async () => {
  const error = new Error('数据库不可用')
  variableApi.previewVariableDraft.mockRejectedValueOnce(error)
  await wrapper.vm.previewDraftVariable()
  expect(wrapper.vm.draftPreviewError).toBe('数据库不可用')
  expect(wrapper.vm.variableConfigurationChecklist[3].ready).toBe(false)
})
```

- [ ] **Step 3: 运行目标测试并确认按缺陷失败**

Run: `npm test -- tests/unit/views/variableList.spec.js`

Expected: 空白表单实际得到 `2/4`，且 `draftPreviewError` 尚不存在或未赋值。

- [ ] **Step 4: 实现链式就绪与预览错误状态**

```js
const scopeReady = this.form.scope === 'GLOBAL' ||
  (this.form.scope === 'PROJECT' && !!this.form.projectId)
const definitionReady = scopeReady && Boolean(
  this.form.varCode && this.form.varLabel && this.form.varType
)
const sourceReady = definitionReady && sourced
const validationReady = sourceReady && (
  !requiresPreview || this.draftPreviewResult !== null
)
```

在 `data()` 中增加 `draftPreviewError: ''`；成功预览清空错误，失败时保存裁剪后的 `error.message`，切换来源、项目、范围或其他表单配置时同时清空旧结果和旧错误；预览面板使用现有 `el-alert` 显示该错误。

- [ ] **Step 5: 运行目标测试并确认通过**

Run: `npm test -- tests/unit/views/variableList.spec.js`

Expected: `variableList.spec.js` 全部通过。

- [ ] **Step 6: 提交字段修复**

```powershell
git add rule-engine-builder-ui/src/views/variable/VariableList.vue rule-engine-builder-ui/tests/unit/views/variableList.spec.js
git commit -m "fix: correct variable configuration readiness"
```

### Task 2: 规则设计器显式开始编辑

**Files:**
- Modify: `rule-engine-builder-ui/src/mixins/ruleDraftMixin.js`
- Modify: `rule-engine-builder-ui/src/components/rule/RuleDraftReadOnly.vue`
- Test: `rule-engine-builder-ui/tests/unit/ruleDraftMixin.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/views/designerDraftLifecycle.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/components/ruleDraftReadOnly.spec.js`

**Interfaces:**
- Consumes: `listRuleRevisions()`、`createDraftRevision(definitionId, baseRevisionId?)`、`createDraftFromSource(definitionId, source)`。
- Produces: `refreshViewedRevision()` 纯读取；`forkViewRevision()` 作为显式“开始编辑”入口，默认修订/旧版内容与显式历史来源使用正确创建 API。

- [ ] **Step 1: 将现有“进入即创建”测试改写为只读失败测试**

```js
test.each(designers)('%s 打开无草稿的已发布规则只读且不创建草稿', async (_, component) => {
  definitionApi.listRuleRevisions.mockResolvedValueOnce({
    data: [{ id: 5, revisionNo: 5, state: 'PUBLISHED', modelJson: '{}' }]
  })
  const wrapper = mountDesigner(component)
  await flushPromises()
  expect(definitionApi.createDraftRevision).not.toHaveBeenCalled()
  expect(wrapper.vm.canEditDraft).toBe(false)
  expect(wrapper.find('[data-testid="draft-read-only"]').exists()).toBe(true)
})
```

- [ ] **Step 2: 写显式开始编辑三条路径及并发失败测试**

```js
test('默认已发布修订点击开始编辑时才创建基线草稿', async () => {
  await wrapper.vm.forkViewRevision()
  expect(definitionApi.createDraftRevision).toHaveBeenCalledWith(30, 5)
})

test('旧版生效内容点击开始编辑时创建无基线草稿', async () => {
  await wrapper.vm.forkViewRevision()
  expect(definitionApi.createDraftRevision).toHaveBeenCalledWith(30)
})

test('显式历史节点继续使用来源校验派生接口', async () => {
  await wrapper.vm.forkViewRevision()
  expect(definitionApi.createDraftFromSource).toHaveBeenCalledWith(30, {
    sourceType: 'REVISION', sourceId: '41'
  })
})
```

失败场景断言当前 `viewRevision` 和已加载内容不被清空，并显示服务端错误。

- [ ] **Step 3: 运行目标测试并确认按自动创建缺陷失败**

Run: `npm test -- tests/unit/ruleDraftMixin.spec.js tests/unit/views/designerDraftLifecycle.spec.js tests/unit/components/ruleDraftReadOnly.spec.js`

Expected: 已发布/旧版进入时仍调用 `createDraftRevision`，新只读断言失败。

- [ ] **Step 4: 删除刷新路径中的写操作并实现显式分流**

`refreshViewedRevision()` 只选择现有 DRAFT、最新治理修订或旧版内容，不再调用 `createEditableEntryDraft()`。`forkViewRevision()` 按以下分流：

```js
if (this.requestedSource) {
  response = await definitionApi.createDraftFromSource(definitionId, this.requestedSource)
} else if (this.viewRevision.state === 'LEGACY') {
  response = await definitionApi.createDraftRevision(definitionId)
} else {
  response = await definitionApi.createDraftRevision(definitionId, this.viewRevision.id)
}
```

只有响应含 DRAFT 且动作仍属于当前查看节点时才切换；失败保留原 `viewRevision`。`canForkViewRevision` 增加 `LEGACY`，遮罩按钮文案统一为“开始编辑”。

- [ ] **Step 5: 运行目标测试并确认九个设计器共享行为通过**

Run: `npm test -- tests/unit/ruleDraftMixin.spec.js tests/unit/views/designerDraftLifecycle.spec.js tests/unit/components/ruleDraftReadOnly.spec.js`

Expected: 三个测试文件全部通过。

- [ ] **Step 6: 提交草稿副作用修复**

```powershell
git add rule-engine-builder-ui/src/mixins/ruleDraftMixin.js rule-engine-builder-ui/src/components/rule/RuleDraftReadOnly.vue rule-engine-builder-ui/tests/unit/ruleDraftMixin.spec.js rule-engine-builder-ui/tests/unit/views/designerDraftLifecycle.spec.js rule-engine-builder-ui/tests/unit/components/ruleDraftReadOnly.spec.js
git commit -m "fix: require explicit rule draft creation"
```

### Task 3: FIRST 决策表静态规则与执行三态追踪

**Files:**
- Modify: `rule-engine-builder-ui/src/components/common/TraceTree.vue`
- Test: `rule-engine-builder-ui/tests/unit/components/traceTree.spec.js`

**Interfaces:**
- Consumes: `definitionModel.rules`、`definitionModel.hitPolicy`、表达式追踪 IF/ELSE AST。
- Produces: `tableRules[]` 每项稳定包含 `{ no, ruleName, conds, configuredConditionText, acts, condNode, status, statusText, hit }`。

- [ ] **Step 1: 写首条命中后保留后续静态配置的失败测试**

```js
test('FIRST 首条命中后后续规则保留配置并标记未执行', () => {
  const wrapper = mountTraceTree({
    modelType: 'TABLE',
    definitionModel: {
      hitPolicy: 'FIRST',
      rules: [
        { ruleName: '高分通过', conditionRoot: condition('score', '>=', 90), actions: [action('decision', 'PASS')] },
        { ruleName: '中分复核', conditionRoot: condition('score', '>=', 60), actions: [action('decision', 'REVIEW')] }
      ]
    },
    traceInfo: JSON.stringify([firstHitIfAst])
  })
  expect(wrapper.vm.tableRules[1]).toMatchObject({
    ruleName: '中分复核', status: 'skipped', statusText: '未执行（首次命中后终止）'
  })
  expect(wrapper.vm.tableRules[1].acts.decision).toBe('REVIEW')
})
```

- [ ] **Step 2: 写中间命中、无命中和默认规则测试**

分别使用手工 AST 断言状态序列为 `[miss, hit, skipped]`、`[miss, miss]`，以及默认 ELSE 命中时 `[miss, hit]`；静态条件/动作始终来自 `definitionModel`，实际取值和求值状态来自 AST。

- [ ] **Step 3: 运行追踪组件测试并确认静态规则缺失导致失败**

Run: `npm test -- tests/unit/components/traceTree.spec.js`

Expected: 追踪仅有 AST 中可见规则，后续规则数量、状态或动作断言失败。

- [ ] **Step 4: 实现按顺序合并和三态展示**

增加私有方法 `_modelTableRules()`、`_modelConditionText()`、`_modelConditionCells()`、`_modelActionCells()` 与 `_tableRuleStatus()`。`tableRules` 先读取静态规则，再以同索引动态规则补充 `condNode` 与 `evaluated/value`；FIRST 中前序已命中且后续无求值时标记 `skipped`。模板增加规则名称列，状态显示“命中 / 未命中 / 未执行（首次命中后终止）”，并用文本与样式共同区分状态。

- [ ] **Step 5: 运行目标测试并确认通过**

Run: `npm test -- tests/unit/components/traceTree.spec.js`

Expected: `traceTree.spec.js` 全部通过。

- [ ] **Step 6: 提交追踪修复**

```powershell
git add rule-engine-builder-ui/src/components/common/TraceTree.vue rule-engine-builder-ui/tests/unit/components/traceTree.spec.js
git commit -m "fix: preserve skipped first-hit table rules"
```

### Task 4: ONNX 模型实际运行状态契约与列表展示

**Files:**
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/onnx/OnnxRuntimeSessionManager.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleModelService.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/onnx/OnnxRuntimeSessionManagerTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleModelServiceTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/controller/RuleModelControllerTest.java`
- Modify: `rule-engine-builder-ui/src/views/model/ModelList.vue`
- Test: `rule-engine-builder-ui/tests/unit/views/modelList.spec.js`

**Interfaces:**
- Consumes: 会话键 `modelDigest + ':' + OnnxRuntimeConfig.cacheKey()`、`rule_model.model_digest`、`rule_model.model_config`。
- Produces: 现有 `/api/rule/model/runtimeCapabilities` 增加 `modelRuntimeStates`，每项仅含 `modelId/configuredProvider/effectiveProvider/state/reason`；前端以 `runtimeCapabilitiesRequestId` 拒绝过期响应。

- [ ] **Step 1: 写会话管理器状态查询失败测试**

```java
@Test
public void reportsRecordedCudaFallbackForExactDigestAndConfig() {
    Map<String, Object> state = manager.runtimeState(digest, cudaConfig);
    assertEquals("CPU_FALLBACK", state.get("state"));
    assertEquals("CPU", state.get("effectiveProvider"));
    assertEquals("CUDA failed", state.get("reason"));
}
```

另写 CPU、CUDA 已缓存、CUDA 未初始化和空摘要 UNKNOWN 测试；相同摘要但不同 CUDA 配置必须互不串状态。

- [ ] **Step 2: 写服务聚合安全字段失败测试**

构造 CPU、CUDA 回退、无效配置和 PMML 模型，断言只返回三个 ONNX 状态项，状态对象不含 `modelDigest`、`modelContent` 或堆栈。

- [ ] **Step 3: 运行后端目标测试并确认方法缺失失败**

Run: `mvn -pl rule-engine-server -Dtest=OnnxRuntimeSessionManagerTest,RuleModelServiceTest,RuleModelControllerTest test`

Expected: `runtimeState` 或 `modelRuntimeStates` 尚不存在导致断言失败。

- [ ] **Step 4: 实现只读运行状态查询与聚合**

`OnnxRuntimeSessionManager.runtimeState(String digest, OnnxRuntimeConfig config)` 不创建会话：CPU 配置返回 `CPU`；精确 fallback key 返回 `CPU_FALLBACK`；精确 session key 存在返回 `CUDA`；否则返回 `NOT_INITIALIZED`；摘要或配置无效返回 `UNKNOWN`。`RuleModelService.runtimeCapabilities()` 查询未删除 ONNX 模型的 ID、摘要和配置，逐项解析并追加安全状态列表。

- [ ] **Step 5: 运行后端目标测试并确认通过**

Run: `mvn -pl rule-engine-server -Dtest=OnnxRuntimeSessionManagerTest,RuleModelServiceTest,RuleModelControllerTest test`

Expected: 三个后端测试类全部通过。

- [ ] **Step 6: 写模型列表配置/实际状态分列失败测试**

```js
test('CUDA 配置发生 CPU 回退时分开展示配置和实际状态', async () => {
  wrapper.vm.runtimeCapabilities.modelRuntimeStates = [{
    modelId: 9, configuredProvider: 'CUDA', effectiveProvider: 'CPU',
    state: 'CPU_FALLBACK', reason: '缺少 CUDA 库'
  }]
  expect(wrapper.vm.modelConfiguredProvider({ id: 9, modelFormat: 'ONNX', modelConfig: cudaConfig })).toBe('CUDA')
  expect(wrapper.vm.modelRuntimeState({ id: 9 }).label).toBe('已回退 CPU')
})
```

另断言能力接口失败时显示“状态未知”，不能默认 CPU。

- [ ] **Step 7: 实现模型列表运行状态映射**

将“执行设备”改名“配置设备”，新增“实际运行状态”列；实现 `modelRuntimeState(row)` 对 `CPU/CUDA/CPU_FALLBACK/NOT_INITIALIZED/UNKNOWN` 的中文标签、Element Plus tag 类型和原因 tooltip 映射。`load()` 同步刷新能力接口，`loadRuntimeCapabilities()` 以递增的 `runtimeCapabilitiesRequestId` 只接纳最后一次响应；失败时设置 `runtimeCapabilitiesLoadError`，所有 ONNX 行显示未知。

- [ ] **Step 8: 运行前端目标测试并确认通过**

Run: `npm test -- tests/unit/views/modelList.spec.js`

Expected: `modelList.spec.js` 全部通过。

- [ ] **Step 9: 提交模型状态修复**

```powershell
git add rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/onnx/OnnxRuntimeSessionManager.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleModelService.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/onnx/OnnxRuntimeSessionManagerTest.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleModelServiceTest.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/controller/RuleModelControllerTest.java rule-engine-builder-ui/src/views/model/ModelList.vue rule-engine-builder-ui/tests/unit/views/modelList.spec.js
git commit -m "fix: expose effective onnx model runtime state"
```

### Task 5: 规则测试项目标识与首屏内容顺序

**Files:**
- Modify: `rule-engine-builder-ui/src/views/test/RuleTest.vue`
- Test: `rule-engine-builder-ui/tests/unit/views/ruleTest.spec.js`

**Interfaces:**
- Consumes: `projects[]`、`rules[]`、`ruleScope`、`selectedProjectId`、现有固定用例 API。
- Produces: `ruleOptionLabel(rule)`；页面顺序为规则摘要 → 输入参数 → 固定测试用例 → 执行结果/追踪。

- [ ] **Step 1: 写全项目规则标签失败测试**

```js
test('全部范围规则标签包含稳定项目身份且不出现空占位', () => {
  wrapper.vm.projects = [{ id: 1, projectName: '风控项目', projectCode: 'RISK' }]
  expect(wrapper.vm.ruleOptionLabel({
    id: 9, scope: 'PROJECT', projectId: 1,
    ruleName: '额度规则', ruleCode: 'LIMIT'
  })).toBe('[风控项目 / RISK] 额度规则 (LIMIT)')
  expect(wrapper.vm.ruleOptionLabel({
    id: 10, scope: 'GLOBAL', ruleName: '全局规则', ruleCode: 'GLOBAL_RULE'
  })).toBe('[全局] 全局规则 (GLOBAL_RULE)')
})
```

- [ ] **Step 2: 写模板顺序和紧凑空状态失败测试**

挂载页面后用 `.input-parameters-card` 与 `.fixed-cases-card` 的 DOM 位置断言输入区在固定用例前；无固定用例时 `.fixed-cases-card.is-empty` 存在且仍能找到“收藏当前输入与结果”。

- [ ] **Step 3: 运行目标测试并确认旧标签/旧顺序失败**

Run: `npm test -- tests/unit/views/ruleTest.spec.js`

Expected: 标签仍为 `[项目]`，固定用例仍位于输入参数之前。

- [ ] **Step 4: 实现稳定标签与布局顺序**

规则选项改用 `:label="ruleOptionLabel(r)"`。项目身份优先使用规则响应自带 `projectName/projectCode`，缺失时按 `projectId` 从 `projects` 查找；只省略缺失片段，不输出 `undefined` 或空括号。将固定用例卡片移动到输入参数卡片之后，增加语义 class；空状态仅保留单行说明和收藏入口，复用 4/8/12/16/24 间距体系。

- [ ] **Step 5: 运行目标测试并确认通过**

Run: `npm test -- tests/unit/views/ruleTest.spec.js`

Expected: `ruleTest.spec.js` 全部通过。

- [ ] **Step 6: 提交规则测试页修复**

```powershell
git add rule-engine-builder-ui/src/views/test/RuleTest.vue rule-engine-builder-ui/tests/unit/views/ruleTest.spec.js
git commit -m "fix: clarify rule test context and layout"
```

### Task 6: 全量验证、真实服务启动与浏览器复测

**Files:**
- Verify only: all modified files and existing suites.

**Interfaces:**
- Consumes: Tasks 1–5 的提交。
- Produces: 可复核的编译、测试、启动和 UI 操作证据；验证期间启动的进程全部主动停止。

- [ ] **Step 1: 审查改动边界和静态问题**

Run: `git diff 485df7e..HEAD --check`

Run: `git diff 485df7e..HEAD --stat`

逐条核对规格五项，确认未修改侧边栏菜单、未新增专家模式、未移除 SQL 编辑能力、未引入按名称关联。

- [ ] **Step 2: 前端 lint、单测和构建**

Run: `npm run lint`

Run: `npm test`

Run: `npm run build`

Workdir: `rule-engine-builder-ui`

Expected: 命令均 exit 0，无失败用例。

- [ ] **Step 3: 启动前端开发服务并主动停止**

Run: `npm run dev`

Timeout: `1800000ms`。访问页面确认无编译错误后主动终止 Vite 进程。

- [ ] **Step 4: 前端 dist Playwright 烟测**

Run: `npm run test:e2e:dist`

Workdir: `rule-engine-builder-ui`

Expected: 全部烟测通过。

- [ ] **Step 5: 后端完整编译**

Run: `mvn clean install -DskipTests`

Expected: Reactor BUILD SUCCESS。

- [ ] **Step 6: 启动后端并主动停止**

Run: `mvn spring-boot:run`

Workdir: `rule-engine-server`

Timeout: `1800000ms`。确认应用完成启动或准确记录外部 MySQL/Redis 阻塞后，主动停止 Java 进程。

- [ ] **Step 7: 后端完整测试**

Run: `mvn test`

Expected: 0 failures、0 errors；外部 ONNX/CUDA 诊断测试只允许按既有条件 skip。

- [ ] **Step 8: 浏览器完整复测五条业务链路**

通过前端 UI 依次操作：

1. 新建字段，确认空白为 `0/4`，补齐范围/定义/来源，在线预览失败与配置变更后的失效反馈正确。
2. 打开无 DRAFT 的已发布规则，确认纯浏览不创建草稿；点击“开始编辑”后才进入 DRAFT。
3. 执行 FIRST 决策表，确认命中、未命中、未执行三态和后续静态规则内容完整。
4. 查看 CUDA 配置模型，确认配置设备和实际运行状态分列，未知/未初始化/回退不伪装成功。
5. 在全部项目规则测试范围识别同名规则，确认输入参数优先且空固定用例区域紧凑。

不得通过后端直接造数据绕过 UI 步骤；完成后停止前端、后端与 mock 服务。

- [ ] **Step 9: 最终状态核对**

Run: `git status --short --branch`

Expected: `master` 工作区干净，提交均位于 `485df7e` 之后。
