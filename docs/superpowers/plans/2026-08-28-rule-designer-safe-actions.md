# Rule Designer Safe Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将九类规则设计器收敛为“仅保存草稿 / 保存并检查 / 进入测试”单一动作体系，并提供一致的未保存提醒、会话级草稿恢复和发布前检查反馈。

**Architecture:** 以 `ruleDraftMixin` 作为状态机与保存编排中心，以纯函数序列化接口隔离九类设计器差异；新增通用动作栏、草稿指纹/恢复工具和页面离开守卫。保存并检查复用现有草稿保存、编译与 `preflightRuleRevision` 接口，不修改服务端生命周期语义，也不引入后台自动保存。

**Tech Stack:** Vue 3.5、Vue Router 4、Vuex 4、Element Plus、Vitest、Vue Test Utils、Playwright。

**Spec:** `docs/superpowers/specs/2026-08-28-rule-designer-safe-actions-design.md`

## Global Constraints

- [ ] 直接在当前 `master` 工作区修改，不创建或切换分支、worktree。
- [ ] 变量引用继续以 `_varId`/实体 ID 持久化；不得用编码、名称回溯关联。
- [ ] 序列化只能生成保存副本，不得为保存而修改设计器当前响应式模型。
- [ ] 不改变一级菜单结构，不移除任何直接 SQL 配置能力。
- [ ] 所有新增前端代码通过 ESLint；测试不得以恒真断言或无关断言“假通过”。

---

### Task 1: 用纯函数锁定动作状态和草稿指纹语义

**Files:**
- Create: `rule-engine-builder-ui/src/utils/ruleDesignerDraft.js`
- Test: `rule-engine-builder-ui/tests/unit/utils/ruleDesignerDraft.spec.js`

- [ ] **Step 1: 写出状态推导和规范化指纹的失败测试**

覆盖以下行为：对象键顺序不影响指纹；数组顺序保持；`undefined` 被稳定处理；状态严格区分 `CLEAN`、`DIRTY`、`SAVING`、`SAVED_UNCHECKED`、`CHECK_FAILED`、`READY_TO_TEST`、`SAVE_CONFLICT`；只有 `READY_TO_TEST` 可进入测试。

- [ ] **Step 2: 运行单测并确认失败原因是实现缺失**

Run: `cd rule-engine-builder-ui; npm test -- --run tests/unit/utils/ruleDesignerDraft.spec.js`

Expected: FAIL，提示找不到 `@/utils/ruleDesignerDraft` 或导出函数。

- [ ] **Step 3: 实现最小纯函数 API**

导出固定常量和函数：

```js
export const DESIGNER_ACTION_STATE = Object.freeze({
  CLEAN: 'CLEAN',
  DIRTY: 'DIRTY',
  SAVING: 'SAVING',
  SAVED_UNCHECKED: 'SAVED_UNCHECKED',
  CHECK_FAILED: 'CHECK_FAILED',
  READY_TO_TEST: 'READY_TO_TEST',
  SAVE_CONFLICT: 'SAVE_CONFLICT',
})

export function canonicalStringify(value) {}
export function draftFingerprint(value) {}
export function canEnterRuleTest(state) {}
```

`canonicalStringify` 递归排序普通对象键但不排序数组；`draftFingerprint` 返回可同步比较的确定性字符串，不增加散列依赖。

- [ ] **Step 4: 运行测试确认通过**

Run: `cd rule-engine-builder-ui; npm test -- --run tests/unit/utils/ruleDesignerDraft.spec.js`

Expected: PASS。

### Task 2: 实现会话草稿恢复和跨入口离开守卫

**Files:**
- Create: `rule-engine-builder-ui/src/utils/ruleDraftRecovery.js`
- Create: `rule-engine-builder-ui/src/utils/designerLeaveGuard.js`
- Test: `rule-engine-builder-ui/tests/unit/utils/ruleDraftRecovery.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/utils/designerLeaveGuard.spec.js`

- [ ] **Step 1: 写草稿存储契约失败测试**

测试键由 `definitionId + revisionId` 唯一确定；记录只含 `modelJson`、`fingerprint`、`savedAt`、`lockVersion`；定义、修订或锁版本不匹配时拒绝恢复；保存、明确放弃、切换修订后清除；损坏 JSON 安全忽略并清除。

- [ ] **Step 2: 写离开守卫失败测试**

测试注册/注销、相同路由覆盖注册、非脏页直接放行、脏页只调用一次确认函数、取消时拒绝关闭工作区标签或路由离开。

- [ ] **Step 3: 运行测试并确认失败**

Run: `cd rule-engine-builder-ui; npm test -- --run tests/unit/utils/ruleDraftRecovery.spec.js tests/unit/utils/designerLeaveGuard.spec.js`

Expected: FAIL，模块尚不存在。

- [ ] **Step 4: 实现浏览器存储适配器和守卫注册表**

提供可注入 `Storage`/确认函数的 API，生产默认使用 `window.sessionStorage` 与 Element Plus 确认框调用方；工具本身不得直接依赖组件实例。所有读取返回副本，避免调用方修改注册表内部状态。

- [ ] **Step 5: 运行测试确认通过**

Run: `cd rule-engine-builder-ui; npm test -- --run tests/unit/utils/ruleDraftRecovery.spec.js tests/unit/utils/designerLeaveGuard.spec.js`

Expected: PASS。

### Task 3: 建立统一动作栏及发布前检查展示

**Files:**
- Create: `rule-engine-builder-ui/src/components/rule/RuleDesignerActionBar.vue`
- Modify: `rule-engine-builder-ui/src/components/rule/RuleValidationReport.vue`
- Test: `rule-engine-builder-ui/tests/unit/components/ruleDesignerActionBar.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/components/ruleValidationReport.spec.js`

- [ ] **Step 1: 写动作层级和可访问性失败测试**

断言主按钮唯一为“保存并检查”，次按钮为“仅保存草稿”；“进入测试”在非 `READY_TO_TEST` 状态禁用并给出原因；保存中禁用重复提交；状态文本可读；生命周期入口是文本链接；按钮发出 `save-draft`、`save-check`、`enter-test`、`open-lifecycle` 事件。

- [ ] **Step 2: 写检查报告的设计器内嵌场景测试**

断言编译错误、前置检查阻断、警告和通过状态都能在当前设计器内显示，且不触发自动路由跳转。

- [ ] **Step 3: 运行组件测试并确认失败**

Run: `cd rule-engine-builder-ui; npm test -- --run tests/unit/components/ruleDesignerActionBar.spec.js tests/unit/components/ruleValidationReport.spec.js`

Expected: FAIL。

- [ ] **Step 4: 实现无业务副作用的展示组件**

动作栏只接受 `state`、`readOnly`、`validationReport`、`statusText` 等 props 并发出事件；保存、编译、前置检查均留在 mixin。复用 `RuleValidationReport`，只补充设计器上下文需要的紧凑布局或空状态。

- [ ] **Step 5: 运行测试确认通过**

Run: `cd rule-engine-builder-ui; npm test -- --run tests/unit/components/ruleDesignerActionBar.spec.js tests/unit/components/ruleValidationReport.spec.js`

Expected: PASS。

### Task 4: 将 `ruleDraftMixin` 改造成统一保存与检查编排器

**Files:**
- Modify: `rule-engine-builder-ui/src/mixins/ruleDraftMixin.js`
- Modify: `rule-engine-builder-ui/src/api/definition.js`（仅在返回值适配确有需要时）
- Test: `rule-engine-builder-ui/tests/unit/ruleDraftMixin.spec.js`

- [ ] **Step 1: 为 mixin 新契约写失败测试**

覆盖：加载完成建立保存基线；编辑后为 `DIRTY`；`Ctrl+S` 仅保存草稿；保存成功为 `SAVED_UNCHECKED` 并清理恢复记录；“保存并检查”严格按保存→编译→前置检查执行；任一步失败保留当前页面和报告；全部通过为 `READY_TO_TEST`；再次编辑立即回到 `DIRTY` 并禁用测试；409/锁版本冲突进入 `SAVE_CONFLICT`。

- [ ] **Step 2: 写恢复和离开行为失败测试**

覆盖：脏草稿写入 sessionStorage；重载时显式询问恢复而非静默覆盖；恢复后仍为 `DIRTY`；`beforeunload` 只在脏状态阻止；路由离开取消时停留；保存/放弃/修订切换清理记录。

- [ ] **Step 3: 运行 mixin 测试并确认失败**

Run: `cd rule-engine-builder-ui; npm test -- --run tests/unit/ruleDraftMixin.spec.js`

Expected: FAIL，新状态和方法尚未实现。

- [ ] **Step 4: 实现统一 mixin API**

新增并固定以下调用边界：

```js
methods: {
  serializeDesignerDraft() {
    throw new Error('设计器必须实现 serializeDesignerDraft()')
  },
  async saveDesignerDraft() {},
  async saveAndCheckDesignerDraft() {},
  async enterRuleTest() {},
  markDesignerLoadedBaseline() {},
  refreshDesignerDirtyState() {},
}
```

`saveAndCheckDesignerDraft` 调用现有保存/编译，再用返回的 `definitionId/revisionId` 调 `preflightRuleRevision`。修改 `completeRuleCompile`：只归一化编译结果和状态，不再调用 `goRuleLifecycle()`。恢复提示由 mixin 调用组件的 `$confirm`，工具层保持可测试。

- [ ] **Step 5: 将深层编辑变更统一接入脏状态检测**

对各设计器的规范化序列化结果建立 watcher 或显式刷新，不直接 deep-watch 大型 LogicFlow 实例；图设计器在节点/边持久化模型更新后刷新指纹。确保保存过程中的内部数据回填不会产生伪脏状态。

- [ ] **Step 6: 运行 mixin 测试确认通过**

Run: `cd rule-engine-builder-ui; npm test -- --run tests/unit/ruleDraftMixin.spec.js`

Expected: PASS。

### Task 5: 九类设计器统一实现无副作用序列化和动作栏

**Files:**
- Modify: `rule-engine-builder-ui/src/views/designer/ScriptEditor.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/DecisionTable.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/DecisionTree.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/DecisionFlow.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/RuleSet.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/CrossTable.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/Scorecard.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/AdvancedCrossTable.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/AdvancedScorecard.vue`
- Test: `rule-engine-builder-ui/tests/unit/views/designerDraftLifecycle.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/views/decisionTable.spec.js`

- [ ] **Step 1: 把九个设计器的共同契约写成失败测试**

对每个组件断言：存在 `serializeDesignerDraft()`；调用前后响应式模型深相等；输出可 JSON 序列化；动作栏只有一个主动作；旧文案“临时保存配置/脚本、保存并编译、编译后测试”不再出现；只读历史修订不能保存或检查。

- [ ] **Step 2: 运行设计器契约测试并确认失败**

Run: `cd rule-engine-builder-ui; npm test -- --run tests/unit/views/designerDraftLifecycle.spec.js tests/unit/views/decisionTable.spec.js`

Expected: FAIL。

- [ ] **Step 3: 迁移脚本、表格与规则集设计器**

`ScriptEditor` 复用 `buildModelJson()`；`DecisionTable`、`CrossTable` 基于模型深拷贝；`RuleSet` 将现有保存前组装逻辑移动到序列化方法。保留原有 `_varId`、`scriptVarRefs` 和输出引用语义。

- [ ] **Step 4: 迁移树/流图设计器**

`DecisionTree`、`DecisionFlow` 复用 `buildBackendModel()`，确认该方法不写回 LogicFlow/响应式节点；若现有方法有写回，先在副本上完成规范化。嵌入脚本面板的 `onBeforeCompile` 改为仅保存当前草稿的统一方法。

- [ ] **Step 5: 迁移评分卡与复杂表设计器**

`Scorecard` 将当前保存时对条件的原地修补改为克隆后规范化；`AdvancedCrossTable`、`AdvancedScorecard` 复用现有 `saveModel` 克隆逻辑。九页均接入同一动作栏和 `RuleValidationReport`。

- [ ] **Step 6: 运行设计器测试确认通过**

Run: `cd rule-engine-builder-ui; npm test -- --run tests/unit/views/designerDraftLifecycle.spec.js tests/unit/views/decisionTable.spec.js`

Expected: PASS。

### Task 6: 阻止工作区标签关闭绕过未保存提醒

**Files:**
- Modify: `rule-engine-builder-ui/src/layout/index.vue`
- Modify: `rule-engine-builder-ui/src/layout/components/WorkspaceTabs.vue`（仅在事件参数需补充时）
- Test: `rule-engine-builder-ui/tests/unit/views/layout.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/components/workspaceTabs.spec.js`

- [ ] **Step 1: 写工作区关闭失败测试**

覆盖关闭当前页、关闭其他页、关闭左侧/右侧/其他/全部：操作触及注册的脏设计器时先询问；取消时不 dispatch `workspaceTabs/close`；确认后只执行一次原操作；刷新当前脏页也经过守卫。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd rule-engine-builder-ui; npm test -- --run tests/unit/views/layout.spec.js tests/unit/components/workspaceTabs.spec.js`

Expected: FAIL。

- [ ] **Step 3: 在 store 变更之前调用离开守卫**

在 `handleTabOperation` 中先根据 operation/targetPath 计算将被移除或刷新的路由集合，再调用 `designerLeaveGuard`；只有放行后才 dispatch 现有 close/refresh action。不得先删除标签再依赖 Vue Router 守卫补救。

- [ ] **Step 4: 运行测试确认通过**

Run: `cd rule-engine-builder-ui; npm test -- --run tests/unit/views/layout.spec.js tests/unit/components/workspaceTabs.spec.js`

Expected: PASS。

### Task 7: 浏览器级验收与前端完整门禁

**Files:**
- Modify: `rule-engine-builder-ui/tests/e2e/designer-pages.spec.js`
- Modify: `rule-engine-builder-ui/tests/e2e/guided-workflows.spec.js`
- Modify: `rule-engine-builder-ui/tests/e2e/ui-quality.spec.js`（若现有 UI 契约在此维护）

- [ ] **Step 1: 增加真实操作烟测**

至少覆盖决策表、决策流和脚本三种代表形态：编辑→脏状态→仅保存→再次编辑→保存并检查→检查通过→进入测试；编译失败留在设计器；刷新恢复；关闭标签取消/确认。测试通过页面操作创建和修改数据，不通过后端预造中间状态跳步。

- [ ] **Step 2: 运行定向单测**

Run: `cd rule-engine-builder-ui; npm test -- --run tests/unit/utils/ruleDesignerDraft.spec.js tests/unit/utils/ruleDraftRecovery.spec.js tests/unit/utils/designerLeaveGuard.spec.js tests/unit/components/ruleDesignerActionBar.spec.js tests/unit/ruleDraftMixin.spec.js tests/unit/views/designerDraftLifecycle.spec.js tests/unit/views/layout.spec.js`

Expected: PASS。

- [ ] **Step 3: 启动前端开发服务并进行页面检查**

Run: `cd rule-engine-builder-ui; npm run dev -- --host 127.0.0.1`

Expected: 9090 端口可访问、控制台无错误。工具超时设置 `1800000ms`；检查完成后主动停止进程。

- [ ] **Step 4: 运行前端全量质量门禁**

Run: `cd rule-engine-builder-ui; npm test`

Run: `cd rule-engine-builder-ui; npm run lint`

Run: `cd rule-engine-builder-ui; npm run build`

Run: `cd rule-engine-builder-ui; npm run test:e2e:dist`

Expected: 全部 PASS。

- [ ] **Step 5: 审查最终差异**

Run: `git diff --check`

Run: `git diff -- rule-engine-builder-ui/src rule-engine-builder-ui/tests`

确认无旧动作文案、无自动跳转生命周期、无敏感值写入 sessionStorage、无设计器保存时原地修改模型。
