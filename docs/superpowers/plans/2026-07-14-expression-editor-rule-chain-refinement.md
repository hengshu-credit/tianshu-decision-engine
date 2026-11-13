# Expression Editor and Amount Rule Chain Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make complex expressions easy to locate and collapse, support manual-path field resolution, and configure the three amount inputs as reusable global cross-table rules called by the amount decision flow.

**Architecture:** Keep Operand AST and runtime contracts unchanged. Refactor `ExpressionPalette` into a stable two-column category browser that reuses picker category metadata and `resolvePathOperand`; keep collapse state as editor-only path keys managed by `ExpressionEditorDialog`. Configure three GLOBAL `CROSS_ADV` rules through the browser and call them sequentially from `AMOUNT_CALC` before the final assignment.

**Tech Stack:** Vue 2.6, Element UI 2.15, Jest + Vue Test Utils, Spring Boot 2.3, QLExpress 4, MySQL 8, Playwright browser automation.

## Global Constraints

- Preserve variable, model, and field names exactly as entered; all persisted references use stable ID plus explicit `refType`.
- Do not add frontend or backend dependencies.
- Reuse existing global variables wherever their business meaning matches.
- Data configuration must be performed through the real frontend UI; backend and SQL may only be used for read-only inspection and verification.
- Complete frontend tests, backend clean build/tests, service startup, browser replay, console inspection, and post-merge verification.

---

### Task 1: Shared picker categories and two-column expression resource browser

**Files:**
- Create: `rule-engine-builder-ui/src/utils/pickerCategories.js`
- Modify: `rule-engine-builder-ui/src/components/common/VarPicker.vue`
- Modify: `rule-engine-builder-ui/src/components/expression/ExpressionPalette.vue`
- Create: `rule-engine-builder-ui/tests/unit/utils/pickerCategories.spec.js`
- Modify: `rule-engine-builder-ui/tests/unit/components/expressionPalette.spec.js`

**Interfaces:**
- Produces: `REFERENCE_PICKER_CATEGORIES`, `pickerReferenceCategory(item)`, and `pickerCategoryLabel(key)`.
- `ExpressionPalette` continues to emit one complete Operand through `insert` and adds no persisted fields.

- [ ] **Step 1: Write failing category and layout tests**

```js
test('分类数量独立于搜索且普通变量和常量分栏', async () => {
  const wrapper = mountPalette({
    vars: [variable(1, 'age', 'standalone'), variable(2, 'ONE', 'constant')],
    functions: [{ id: 7, funcCode: 'numMax', funcName: '最大值' }]
  })
  expect(wrapper.findAll('.palette-category')).toHaveLength(9)
  expect(wrapper.find('.palette-category--active').text()).toContain('普通变量')
  wrapper.vm.keyword = 'not-found'
  await wrapper.vm.$nextTick()
  expect(wrapper.vm.categories.find(item => item.key === 'standalone').count).toBe(1)
})
```

```js
test('每页只展示50项并保持分类总数', () => {
  const vars = Array.from({ length: 120 }, (_, index) => variable(index + 1, `field_${index}`, 'standalone'))
  const wrapper = mountPalette({ vars })
  expect(wrapper.vm.activeItems).toHaveLength(120)
  expect(wrapper.vm.pagedItems).toHaveLength(50)
  expect(wrapper.vm.categories.find(item => item.key === 'standalone').count).toBe(120)
})
```

- [ ] **Step 2: Run focused tests and verify failure**

Run:

```powershell
cd rule-engine-builder-ui
npm.cmd test -- --runInBand tests/unit/components/expressionPalette.spec.js tests/unit/utils/pickerCategories.spec.js
```

Expected: FAIL because category utility and `.palette-category` do not exist.

- [ ] **Step 3: Add shared category metadata**

```js
export const REFERENCE_PICKER_CATEGORIES = Object.freeze([
  { key: 'standalone', label: '普通变量' },
  { key: 'constant', label: '常量' },
  { key: 'object', label: '数据对象' },
  { key: 'model', label: '模型' }
])

export function pickerReferenceCategory(item) {
  return (item && item._ref && item._ref.category) || 'standalone'
}

export function pickerCategoryLabel(key) {
  const found = REFERENCE_PICKER_CATEGORIES.find(item => item.key === key)
  return found ? found.label : key
}
```

Use this metadata in `VarPicker.categoryList` and `ExpressionPalette.categories` so both components share labels and category keys.

- [ ] **Step 4: Replace the long palette with stable two-column navigation**

Implement `activeCategory`, `keyword`, `page`, `pageSize: 50`, `categories`, `activeItems`, `filteredItems`, and `pagedItems`. Use a fixed 132px category rail and a flexible result pane; reset search and page when the category changes. Render compact rows for references/functions and compact button groups for operators/access/cast/list query.

```js
selectCategory(key) {
  this.activeCategory = key
  this.keyword = ''
  this.page = 1
}
```

```css
.expression-palette { display: grid; min-width: 0; grid-template-columns: 132px minmax(0, 1fr); }
.palette-category { display: flex; width: 100%; white-space: nowrap; }
.palette-category__count { margin-left: auto; font-variant-numeric: tabular-nums; }
```

- [ ] **Step 5: Run focused tests and commit**

Run the Task 1 test command; expected all focused suites PASS.

```powershell
git add rule-engine-builder-ui/src/utils/pickerCategories.js rule-engine-builder-ui/src/components/common/VarPicker.vue rule-engine-builder-ui/src/components/expression/ExpressionPalette.vue rule-engine-builder-ui/tests/unit/utils/pickerCategories.spec.js rule-engine-builder-ui/tests/unit/components/expressionPalette.spec.js
git commit -m "feat: add categorized expression resources"
```

---

### Task 2: Manual path resolution inside the expression palette

**Files:**
- Modify: `rule-engine-builder-ui/src/components/expression/ExpressionPalette.vue`
- Modify: `rule-engine-builder-ui/tests/unit/components/expressionPalette.spec.js`

**Interfaces:**
- Consumes: `createPathOperand(path)` and `resolvePathOperand(operand, vars)` from `@/utils/operand`.
- Produces: a resolved `PATH` with `refId/refType`, an unresolved `PATH`, or a candidate-selection UI; all outcomes emit through `insert`.

- [ ] **Step 1: Write failing path-resolution tests**

```js
test('手输唯一路径反解为带稳定ID的PATH', () => {
  const wrapper = mountPalette({ vars: [variable(12, 'request.customer.age', 'object')] })
  wrapper.vm.manualKind = 'PATH'
  wrapper.vm.manualValue = 'request.customer.age'
  wrapper.vm.confirmManual()
  expect(wrapper.emitted().insert[0][0]).toMatchObject({
    kind: 'PATH', refId: 12, refType: 'DATA_OBJECT', resolved: true
  })
})

test('同路径多候选必须明确选择', () => {
  const wrapper = mountPalette({ vars: [variable(1, 'score', 'standalone'), variable(2, 'score', 'model')] })
  wrapper.vm.manualKind = 'PATH'
  wrapper.vm.manualValue = 'score'
  wrapper.vm.confirmManual()
  expect(wrapper.emitted().insert).toBeUndefined()
  expect(wrapper.vm.pathCandidates).toHaveLength(2)
  wrapper.vm.confirmPathCandidate(wrapper.vm.pathCandidates[1])
  expect(wrapper.emitted().insert[0][0].refId).toBe(2)
})
```

- [ ] **Step 2: Run tests and verify failure**

Run the focused `expressionPalette.spec.js`; expected FAIL because the palette has no manual-path state.

- [ ] **Step 3: Reuse existing operand path resolver**

Add `manualKind`, `manualValue`, and `pathCandidates`. `confirmManual()` emits literals directly; for paths it calls `resolvePathOperand(createPathOperand(manualValue), vars)`. Candidate selection re-runs the resolver with one option and emits the resolved path.

```js
confirmManual() {
  if (this.manualKind === 'LITERAL') return this.emitInsert(createLiteralOperand(this.manualValue, this.expectedType || 'STRING'))
  const result = resolvePathOperand(createPathOperand(this.manualValue), this.vars)
  this.pathCandidates = result.candidates
  if (!result.candidates.length) this.emitInsert(result.operand)
}
```

- [ ] **Step 4: Run focused tests and commit**

```powershell
git add rule-engine-builder-ui/src/components/expression/ExpressionPalette.vue rule-engine-builder-ui/tests/unit/components/expressionPalette.spec.js
git commit -m "feat: resolve manual paths in expression palette"
```

---

### Task 3: Collapsible expression tree with automatic path reveal

**Files:**
- Modify: `rule-engine-builder-ui/src/components/expression/expressionTree.js`
- Modify: `rule-engine-builder-ui/src/components/expression/ExpressionCanvas.vue`
- Modify: `rule-engine-builder-ui/src/components/expression/ExpressionEditorDialog.vue`
- Modify: `rule-engine-builder-ui/tests/unit/utils/expressionTree.spec.js`
- Create: `rule-engine-builder-ui/tests/unit/components/expressionCanvas.spec.js`
- Modify: `rule-engine-builder-ui/tests/unit/components/expressionEditorDialog.spec.js`

**Interfaces:**
- Produces: `expressionPathKey(path)`, `collapsedExpressionPaths(root, maxDepth)`, `expressionAncestorKeys(path)`, `existingCollapsedPaths(root, keys)`, and `expressionDescendantCount(node)`.
- `ExpressionCanvas` receives `collapsedPathKeys: Array<string>` and emits `toggle-collapse(path)`.

- [ ] **Step 1: Write failing collapse-state tests**

```js
test('默认只展开两层且不修改AST', () => {
  const source = nestedExpression()
  const snapshot = JSON.stringify(source)
  const keys = collapsedExpressionPaths(source, 2)
  expect(keys.length).toBeGreaterThan(0)
  expect(JSON.stringify(source)).toBe(snapshot)
})
```

```js
test('选择深层节点时展开所有祖先', () => {
  const wrapper = mountEditor({ value: nestedExpression() })
  wrapper.vm.collapseToOverview()
  wrapper.vm.selectPath(['args', 0, 'operands', 1])
  expect(wrapper.vm.collapsedPathKeys).not.toContain('args.0')
})
```

- [ ] **Step 2: Run focused tests and verify failure**

Run the three expression suites; expected FAIL because collapse helpers and props do not exist.

- [ ] **Step 3: Implement pure collapse helpers**

Traverse `expressionChildEntries` with an explicit tree depth. Store path keys with `path.join('.')`; return new arrays without mutating the AST. Prune keys by comparing them with paths present in the new tree.

```js
export function expressionAncestorKeys(path = []) {
  const result = []
  for (let length = 0; length < path.length; length += 1) result.push(expressionPathKey(path.slice(0, length)))
  return result
}
```

- [ ] **Step 4: Add per-node and bulk collapse UI**

Add a chevron button only when the node has children, a hidden-descendant badge, and recursive propagation of `collapsedPathKeys`. In the editor add “展开全部” and “折叠到两层” beside formula preview. `reset()` initializes the overview; `selectPath()` and insert/update operations call `revealPath()`; `commit()` prunes invalid keys.

- [ ] **Step 5: Run focused tests and commit**

```powershell
git add rule-engine-builder-ui/src/components/expression rule-engine-builder-ui/tests/unit/components/expressionCanvas.spec.js rule-engine-builder-ui/tests/unit/components/expressionEditorDialog.spec.js rule-engine-builder-ui/tests/unit/utils/expressionTree.spec.js
git commit -m "feat: add collapsible expression tree"
```

---

### Task 4: Frontend regression and visual integration

**Files:**
- Modify only files already touched if the focused/full suite reveals a real regression.

**Interfaces:**
- Verifies all selector and expression call sites continue to consume the same Operand AST.

- [ ] **Step 1: Run the full frontend suite**

```powershell
cd rule-engine-builder-ui
npm.cmd test -- --runInBand --silent
```

Expected: all suites and tests PASS.

- [ ] **Step 2: Start the frontend and inspect compilation**

```powershell
npm.cmd run dev
```

Expected: webpack compiles without errors on port 9090. Keep it running for browser configuration.

- [ ] **Step 3: Commit any verified integration corrections**

Only commit corrections directly required by failing tests or browser evidence.

---

### Task 5: Backend clean verification and service startup

**Files:**
- No planned source changes.

- [ ] **Step 1: Build all backend modules**

```powershell
mvn clean install -DskipTests
```

Expected: reactor BUILD SUCCESS for all six modules.

- [ ] **Step 2: Run all backend tests**

```powershell
mvn test
```

Expected: zero failures and zero errors.

- [ ] **Step 3: Start management server**

```powershell
cd rule-engine-server
mvn spring-boot:run
```

Expected: `Started RuleEngineApplication` and port 8080 listening.

---

### Task 6: Configure three global fields and cross-table rules through UI

**Files:**
- Runtime MySQL data only, created through browser UI.

**Interfaces:**
- Produces global variables `monthly_success_repayment_amount`, `risk_factor`, `risk_amount`.
- Produces compiled global rules `MONTHLY_REPAYMENT_MATRIX`, `RISK_FACTOR_MATRIX`, `RISK_AMOUNT_MATRIX`.

- [ ] **Step 1: Create the three global NUMBER/COMPUTED variables**

Use 变量管理 → 新建变量. Set scope GLOBAL, exact codes/labels from the design, NUMBER, COMPUTED, enabled.

- [ ] **Step 2: Create and configure monthly repayment matrix**

Use 规则管理 → 新建规则 → GLOBAL → 复杂交叉表. Configure rows from `credit_limit`, columns from `available_credit_limit`, target `monthly_success_repayment_amount`, and enter all 44 values from the requirement. Save and compile.

- [ ] **Step 3: Create and configure risk factor matrix**

Configure rows `age`, columns `score`, target `risk_factor`, and enter all 16 decimal values. Save and compile.

- [ ] **Step 4: Create and configure risk amount matrix**

Configure rows `age`, columns `score`, target `risk_amount`, and enter all 16 amount values. Save and compile.

- [ ] **Step 5: Test representative and boundary values in UI**

Verify at least one interior cell and one upper/lower boundary for every matrix. Record UI result screenshots and confirm no console errors.

---

### Task 7: Rebuild AMOUNT_CALC as a four-node rule chain

**Files:**
- Runtime MySQL data only, changed through browser UI.

**Interfaces:**
- Consumes the three global compiled rules and global fields.
- Produces global `amount` from a stable-ID expression.

- [ ] **Step 1: Open decision flow 10 and replace the single action path**

Create four sequential action nodes named 月成功还款额计算、风险系数计算、风险额度计算、最终额度计算 between start and end.

- [ ] **Step 2: Configure the first three nodes as rule calls**

Select the matching global rule in each node. Do not hand-enter rule names; persist rule IDs from the UI selector.

- [ ] **Step 3: Configure final formula from global fields**

Target global `amount`. Build the formula using `monthly_success_repayment_amount`, `used_credit_limit`, `risk_factor`, and `risk_amount`, with the exact nested functions/operators from the design.

- [ ] **Step 4: Save, compile, and execute the full flow**

Use a representative input that exercises all three matrices. Manually recompute the expected cell values and final result; assert the UI output matches.

- [ ] **Step 5: Remove project-level temporary fields through UI**

Delete IDs 239–243 only after the flow compiles and executes with global references. Reopen the formula and confirm every reference still resolves to the intended global field.

---

### Task 8: Browser acceptance, cleanup, and merge to master

**Files:**
- No planned source files.

- [ ] **Step 1: Browser-replay editor interactions**

Verify category counts and no wrapping at 100+ items; search categories; enter a unique path and a duplicate path; collapse one node; expand all; collapse to two levels; edit a hidden deep parameter; save and reopen the formula.

- [ ] **Step 2: Inspect console and capture evidence**

Require no `aria-hidden`, duplicate key, Vue error, or failed API request. Capture final editor and amount result screenshots outside the Git worktree.

- [ ] **Step 3: Stop backend and frontend**

Confirm ports 8080 and 9090 no longer listen.

- [ ] **Step 4: Run final branch verification**

```powershell
npm.cmd test -- --runInBand --silent
mvn clean install -DskipTests
mvn test
git diff --check master..HEAD
git status --short
```

Expected: all commands succeed and worktree is clean.

- [ ] **Step 5: Merge into master**

From `C:\Users\18306\Documents\GitHub\qlexpress-rule`, confirm master is clean, then:

```powershell
git switch master
git merge --no-ff codex/unified-expression
```

Resolve no unrelated changes. Run focused frontend tests and `mvn test -q` on master, then confirm `git status --short` is clean.
