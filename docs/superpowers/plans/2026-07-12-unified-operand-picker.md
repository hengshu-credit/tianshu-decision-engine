# Unified Operand Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every execution-semantic field/value input in rule designers, model field bindings/defaults, and experiment conditions with one extensible operand picker that compiles and resolves literals, manual paths, managed references, and functions consistently.

**Architecture:** A frontend `OperandPicker` edits a normalized operand object and uses the existing reference catalog for variables, constants, data-object fields, and model outputs. Shared frontend and Java operand utilities compile expressions and collect dependencies. Model field operands are persisted as JSON; the server resolves source/default operands before model execution and expands both into rule input dependencies.

**Tech Stack:** Vue 2.6, Element UI 2.15, Jest + Vue Test Utils, Java 8, Spring Boot 2.3, Fastjson, MyBatis Plus, JUnit 4, QLExpress 4.

## Global Constraints

- The repository is not released; replace the old interaction and model structures directly without old-version compatibility branches.
- Every managed reference must persist `refId + refType`; a manual unmatched PATH is an external runtime input, not a managed-reference association.
- Preserve user-entered codes and paths exactly except trimming outer whitespace; never change case or naming style.
- Do not add dependencies; use Vue, Element UI, Fastjson, and existing project helpers.
- Preserve the user's current unrelated working-tree changes in test schema/sample value files and `RuleFieldAnalyzer` sample-value work.
- Frontend completion requires `npm run dev`, stop the process, then `npm test`.
- Backend completion requires `mvn clean install -DskipTests`, start `rule-engine-server` with `mvn spring-boot:run`, stop it, then `mvn test`.
- Final acceptance requires browser UI operations through rule designers, model detail, and experiment condition routing without creating data through backend shortcuts.

---

### Task 1: Frontend operand domain model

**Files:**
- Create: `rule-engine-builder-ui/src/utils/operand.js`
- Create: `rule-engine-builder-ui/tests/unit/utils/operand.spec.js`
- Modify: `rule-engine-builder-ui/src/utils/referenceCatalog.js`
- Test: `rule-engine-builder-ui/tests/unit/utils/referenceCatalog.spec.js`

**Interfaces:**
- Produces: `createLiteralOperand(value, valueType)`, `createPathOperand(path)`, `createReferenceOperand(option)`, `resolvePathOperand(operand, options)`, `compileOperand(operand)`, `collectOperandReferences(operand, out)`, and `operandDisplay(operand)`.
- `resolvePathOperand` returns `{ operand, candidates }`; zero candidates leaves `resolved:false`, one candidate binds it, and multiple candidates do not mutate the input.

- [ ] **Step 1: Write failing operand tests**

```js
test('唯一完整路径匹配后保留 PATH 并补齐 ID', () => {
  const source = createPathOperand('request.customer.age')
  const result = resolvePathOperand(source, [{
    varCode: 'request.customer.age', varType: 'INTEGER', _varId: 12,
    _refType: 'DATA_OBJECT', varLabel: '客户年龄'
  }])
  expect(result.candidates).toHaveLength(0)
  expect(result.operand).toMatchObject({
    kind: 'PATH', value: 'request.customer.age', refId: 12,
    refType: 'DATA_OBJECT', valueType: 'INTEGER', resolved: true
  })
})

test('重复路径不猜测资源', () => {
  const result = resolvePathOperand(createPathOperand('score'), [
    { varCode: 'score', _varId: 1, _refType: 'VARIABLE' },
    { varCode: 'score', _varId: 2, _refType: 'MODEL_OUTPUT' }
  ])
  expect(result.candidates).toHaveLength(2)
  expect(result.operand.resolved).toBe(false)
})
```

- [ ] **Step 2: Run the new tests and verify RED**

Run: `cd rule-engine-builder-ui; npm test -- --runInBand tests/unit/utils/operand.spec.js`

Expected: FAIL because `@/utils/operand` does not exist.

- [ ] **Step 3: Implement the minimal operand utilities**

```js
export const OPERAND_KINDS = Object.freeze({
  LITERAL: 'LITERAL', PATH: 'PATH', REFERENCE: 'REFERENCE', FUNCTION: 'FUNCTION'
})

export function createPathOperand(path) {
  const value = String(path == null ? '' : path).trim()
  return { kind: 'PATH', value, code: value, valueType: '', refId: null, refType: '', label: value, resolved: false }
}

export function createReferenceOperand(option) {
  return {
    kind: 'REFERENCE', value: option.varCode, code: option.varCode,
    label: option.varLabel || option.varCode, valueType: option.varType || '',
    refId: option._varId != null ? option._varId : option.id,
    refType: option._refType || option.refType || '', resolved: true
  }
}
```

Implement exact-path matching against `varCode`, `scriptName`, `varCodeText`, and object/model full paths already produced by `buildReferenceCatalog`. Do not lower-case or rewrite the entered path.

- [ ] **Step 4: Run operand and reference catalog tests**

Run: `cd rule-engine-builder-ui; npm test -- --runInBand tests/unit/utils/operand.spec.js tests/unit/utils/referenceCatalog.spec.js`

Expected: PASS.

- [ ] **Step 5: Commit the operand domain**

```powershell
git add rule-engine-builder-ui/src/utils/operand.js rule-engine-builder-ui/src/utils/referenceCatalog.js rule-engine-builder-ui/tests/unit/utils/operand.spec.js rule-engine-builder-ui/tests/unit/utils/referenceCatalog.spec.js
git commit -m "feat: add unified operand domain model"
```

### Task 2: Unified OperandPicker component

**Files:**
- Create: `rule-engine-builder-ui/src/components/common/OperandPicker.vue`
- Create: `rule-engine-builder-ui/tests/unit/components/operandPicker.spec.js`
- Modify: `rule-engine-builder-ui/src/components/common/VarPicker.vue`
- Test: `rule-engine-builder-ui/tests/unit/components/varPicker.spec.js`

**Interfaces:**
- Consumes: Task 1 operand constructors and path resolver.
- Produces: Vue component with `value:Object`, `vars:Array`, `functions:Array`, `allowedKinds:Array`, `writableOnly:Boolean`, `expectedType:String`, `selectedVars:Array`; emits `input`, `select`, and `clear` with normalized operands.

- [ ] **Step 1: Write failing component behavior tests**

```js
test('手动输入分类始终位于最前方', () => {
  const wrapper = mountPicker({ allowedKinds: ['LITERAL', 'PATH', 'REFERENCE'] })
  expect(wrapper.vm.categoryList.map(item => item.key).slice(0, 3))
    .toEqual(['manual', 'standalone', 'constant'])
})

test('手输路径唯一命中后发出带 ID 的 PATH', () => {
  const wrapper = mountPicker({ vars: referenceOptions })
  wrapper.vm.selectManualKind('PATH')
  wrapper.vm.manualValue = 'request.age'
  wrapper.vm.confirmManual()
  expect(wrapper.emitted().input[0][0]).toMatchObject({
    kind: 'PATH', refId: 8, refType: 'DATA_OBJECT', resolved: true
  })
})

test('多项命中时不能确认并展示候选项', () => {
  const wrapper = mountPicker({ vars: duplicateScoreOptions })
  wrapper.vm.selectManualKind('PATH')
  wrapper.vm.manualValue = 'score'
  wrapper.vm.confirmManual()
  expect(wrapper.emitted().input).toBeUndefined()
  expect(wrapper.vm.pathCandidates).toHaveLength(2)
})
```

- [ ] **Step 2: Run component tests and verify RED**

Run: `cd rule-engine-builder-ui; npm test -- --runInBand tests/unit/components/operandPicker.spec.js`

Expected: FAIL because `OperandPicker.vue` does not exist.

- [ ] **Step 3: Implement OperandPicker using the existing catalog panel behavior**

The template must use a read-only trigger and explicit click:

```vue
<div class="operand-picker">
  <el-popover ref="popover" v-model="visible" trigger="manual" popper-class="operand-picker-popover">
    <div class="op-panel" @mousedown.stop @click.stop>
      <div class="op-categories">
        <button v-for="item in categoryList" :key="item.key" @click="activeCategory = item.key">
          {{ item.label }}
        </button>
      </div>
      <manual-operand-editor v-if="activeCategory === 'manual'" />
      <reference-catalog v-else />
    </div>
    <el-input slot="reference" :value="displayValue" readonly @click.native.stop="open" />
  </el-popover>
</div>
```

Reuse the current VarPicker search, grouping, paging, resize, outside-click, and selected-fields methods. Remove `customMode`, `autoSwitchCustom`, mode-switch icons, and focus-triggered opening from the new component. Keep VarPicker temporarily as a thin reference-only adapter until all callers migrate in Tasks 3-5.

- [ ] **Step 4: Run picker tests**

Run: `cd rule-engine-builder-ui; npm test -- --runInBand tests/unit/components/operandPicker.spec.js tests/unit/components/varPicker.spec.js`

Expected: PASS with no automatic-open assertion.

- [ ] **Step 5: Commit the picker**

```powershell
git add rule-engine-builder-ui/src/components/common/OperandPicker.vue rule-engine-builder-ui/src/components/common/VarPicker.vue rule-engine-builder-ui/tests/unit/components/operandPicker.spec.js rule-engine-builder-ui/tests/unit/components/varPicker.spec.js
git commit -m "feat: add unified operand picker"
```

### Task 3: Condition tree migration

**Files:**
- Modify: `rule-engine-builder-ui/src/components/decision/ConditionGroupEditor.vue`
- Modify: `rule-engine-builder-ui/src/utils/decisionConditionTree.js`
- Modify: `rule-engine-builder-ui/src/constants/conditionOperators.js`
- Test: `rule-engine-builder-ui/tests/unit/components/conditionGroupEditor.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/utils/decisionConditionTree.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/utils/conditionOperatorsTree.spec.js`

**Interfaces:**
- Consumes: `OperandPicker`, `compileOperand`, and operand dependency helpers.
- Produces: condition leaf `{ type:'leaf', leftOperand, operator, rightOperand }` and `compileConditionTreeExpression` using operands.

- [ ] **Step 1: Replace tests with new leaf behavior and run RED**

```js
test('条件叶节点只渲染左右操作数选择器，不渲染值类型下拉', () => {
  const wrapper = mountEditor()
  expect(wrapper.findAllComponents(OperandPickerStub)).toHaveLength(2)
  expect(wrapper.find('.cg-field--kind').exists()).toBe(false)
  expect(wrapper.find('.cg-manual-value').exists()).toBe(false)
})

test('编译路径与阈值条件', () => {
  const tree = { type: 'leaf', operator: '>=',
    leftOperand: { kind: 'PATH', value: 'request.age' },
    rightOperand: { kind: 'LITERAL', value: '18', valueType: 'NUMBER' } }
  expect(compileConditionTreeExpression(tree)).toBe('request.age >= 18')
})
```

Run: `cd rule-engine-builder-ui; npm test -- --runInBand tests/unit/components/conditionGroupEditor.spec.js tests/unit/utils/decisionConditionTree.spec.js tests/unit/utils/conditionOperatorsTree.spec.js`

Expected: FAIL because leaves still use `varCode/valueKind/value`.

- [ ] **Step 2: Implement the new leaf and editor**

```js
export function createEmptyLeaf() {
  return {
    type: 'leaf',
    leftOperand: null,
    operator: '==',
    rightOperand: null
  }
}
```

`ConditionGroupEditor` must pass `allowedKinds="['PATH','REFERENCE','FUNCTION']"` on the left and `['LITERAL','PATH','REFERENCE','FUNCTION']` on the right. No-value operators set `rightOperand=null`. There must be no `valueKind` select, `switchRightToVar`, picker ref, or `openRightVarPicker`.

- [ ] **Step 3: Update JS condition compilation**

Change `compileConditionExpression(left, varType, operator, value, valueKind)` to accept compiled operand strings through a new `compileConditionOperands(leftOperand, operator, rightOperand)` wrapper. Preserve operator-specific behavior for lists/between by reading LITERAL raw values; non-literal operands are disabled for operators marked `noVarValue`.

- [ ] **Step 4: Run condition tests**

Run: `cd rule-engine-builder-ui; npm test -- --runInBand tests/unit/components/conditionGroupEditor.spec.js tests/unit/utils/decisionConditionTree.spec.js tests/unit/utils/conditionOperatorsTree.spec.js`

Expected: PASS.

- [ ] **Step 5: Commit condition migration**

```powershell
git add rule-engine-builder-ui/src/components/decision/ConditionGroupEditor.vue rule-engine-builder-ui/src/utils/decisionConditionTree.js rule-engine-builder-ui/src/constants/conditionOperators.js rule-engine-builder-ui/tests/unit/components/conditionGroupEditor.spec.js rule-engine-builder-ui/tests/unit/utils/decisionConditionTree.spec.js rule-engine-builder-ui/tests/unit/utils/conditionOperatorsTree.spec.js
git commit -m "feat: unify condition operands"
```

### Task 4: Rule designers and action operands

**Files:**
- Modify: `rule-engine-builder-ui/src/components/flow/ActionBlockEditor.vue`
- Modify: `rule-engine-builder-ui/src/utils/actionDataCodegen.js`
- Modify: `rule-engine-builder-ui/src/mixins/varPickerMixin.js`
- Modify: every file under `rule-engine-builder-ui/src/views/designer/` except the raw Monaco body in `ScriptEditor.vue`
- Test: `rule-engine-builder-ui/tests/unit/views/decisionTable.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/views/designerTestSchemaContract.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/utils/actionDataCodegen.spec.js`
- Test: relevant existing designer specs discovered with `rg --files rule-engine-builder-ui/tests/unit/views`

**Interfaces:**
- Consumes: normalized operands and condition tree from Tasks 1-3.
- Produces: every execution-semantic designer value as an operand object; action blocks use `targetOperand`, `valueOperand`, `condition.leftOperand/rightOperand`, and operand arrays for function args.

- [ ] **Step 1: Add failing action codegen tests**

```js
test('赋值值按操作数类型编译，不根据字符串形态猜测', () => {
  expect(generateScript([{ type: 'assign',
    targetOperand: { kind: 'REFERENCE', code: 'result', refId: 1, refType: 'VARIABLE' },
    valueOperand: { kind: 'LITERAL', value: 'threshold', valueType: 'STRING' }
  }])).toBe('result = "threshold"')
})

test('函数参数递归编译路径和阈值', () => {
  const block = { type: 'func-call', functionCode: 'max', args: [
    { kind: 'PATH', value: 'request.score' },
    { kind: 'LITERAL', value: '600', valueType: 'NUMBER' }
  ] }
  expect(generateScript([block])).toBe('max(request.score, 600)')
})
```

- [ ] **Step 2: Run action tests and verify RED**

Run: `cd rule-engine-builder-ui; npm test -- --runInBand tests/unit/utils/actionDataCodegen.spec.js`

Expected: FAIL because action blocks still use strings and `_argRefs`.

- [ ] **Step 3: Implement action operands**

Replace `wrapValue`, `wrapFunctionArg`, and `_argRefs` inference with `compileOperand`. Update `newBlock` to initialize operand properties to `null`. Update `ActionBlockEditor` so all semantic value controls are `OperandPicker`; target controls set `writable-only`.

- [ ] **Step 4: Migrate the nine designers**

Use these exact context rules:

```js
const READ_OPERANDS = ['PATH', 'REFERENCE', 'FUNCTION']
const VALUE_OPERANDS = ['LITERAL', 'PATH', 'REFERENCE', 'FUNCTION']
const WRITE_OPERANDS = ['PATH', 'REFERENCE']
```

Replace condition fields, dimensions, result fields, score thresholds, cross-table cells, action values, and graph edge operands. Do not replace labels, weights, traffic configuration, or raw script text. Update selected-reference collection and `_syncModelVarRefs` to traverse operands recursively.

- [ ] **Step 5: Run designer-focused tests**

Run: `cd rule-engine-builder-ui; npm test -- --runInBand tests/unit/utils/actionDataCodegen.spec.js tests/unit/views/decisionTable.spec.js tests/unit/views/designerTestSchemaContract.spec.js`

Expected: PASS.

- [ ] **Step 6: Commit designer migration**

```powershell
git add rule-engine-builder-ui/src/components/flow/ActionBlockEditor.vue rule-engine-builder-ui/src/utils/actionDataCodegen.js rule-engine-builder-ui/src/mixins/varPickerMixin.js rule-engine-builder-ui/src/views/designer rule-engine-builder-ui/tests/unit
git commit -m "feat: migrate rule designers to operands"
```

### Task 5: Java operand compiler and core rule compilers

**Files:**
- Create: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/compiler/OperandCompiler.java`
- Create: `rule-engine-core/src/test/java/com/hengshucredit/rule/core/compiler/OperandCompilerTest.java`
- Modify: `ConditionExpressionBuilder.java`, `ActionOperandCompiler.java`, `ActionDataCompiler.java`, `DecisionTableCompiler.java`, `GraphScriptGenerator.java`, `CrossTableCompiler.java`, `ScorecardCompiler.java`, `AdvancedCrossTableCompiler.java`, `AdvancedScorecardCompiler.java`, and `RuleSetCompiler.java`
- Test: all compiler tests under `rule-engine-core/src/test/java/com/hengshucredit/rule/core/compiler/`

**Interfaces:**
- Produces: `OperandCompiler.compile(JSONObject operand, VarContext context)` and `OperandCompiler.references(Object operand)`.

- [ ] **Step 1: Write Java RED tests**

```java
@Test
public void referenceUsesIdBeforeSnapshotCode() {
    JSONObject operand = JSON.parseObject("{\"kind\":\"REFERENCE\",\"refId\":7,\"refType\":\"VARIABLE\",\"code\":\"old\"}");
    VarContext context = new VarContext(Collections.emptyMap(), Collections.emptyMap(),
            Collections.singletonMap("VARIABLE:7", "currentCode"));
    assertEquals("currentCode", OperandCompiler.compile(operand, context));
}

@Test
public void stringLiteralIsAlwaysQuoted() {
    JSONObject operand = JSON.parseObject("{\"kind\":\"LITERAL\",\"value\":\"threshold\",\"valueType\":\"STRING\"}");
    assertEquals("\"threshold\"", OperandCompiler.compile(operand, new VarContext(Collections.emptyMap())));
}
```

- [ ] **Step 2: Run RED test**

Run: `mvn -pl rule-engine-core -am -Dtest=OperandCompilerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because `OperandCompiler` does not exist.

- [ ] **Step 3: Implement OperandCompiler**

```java
public final class OperandCompiler {
    public static String compile(JSONObject operand, VarContext context) {
        if (operand == null) return "";
        String kind = operand.getString("kind");
        if ("LITERAL".equals(kind)) return compileLiteral(operand.getString("value"), operand.getString("valueType"));
        if ("PATH".equals(kind) || "REFERENCE".equals(kind)) {
            return context.resolveVar(operand.getLong("refId"), operand.getString("refType"),
                    firstText(operand.getString("code"), operand.getString("value")));
        }
        if ("FUNCTION".equals(kind)) return compileFunction(operand, context);
        throw new IllegalArgumentException("不支持的操作数类型: " + kind);
    }
}
```

Function compilation must require `functionId` and `functionCode`, and recursively compile `args`.

- [ ] **Step 4: Migrate core compilers to operand objects**

Condition leaves read `leftOperand/rightOperand`. Actions and each table/scorecard dimension/result read their operand fields. Remove string-shape inference and old `valueKind` branches.

- [ ] **Step 5: Run all core compiler tests**

Run: `mvn -pl rule-engine-core -am test`

Expected: PASS.

- [ ] **Step 6: Commit core compiler migration**

```powershell
git add rule-engine-core/src/main/java/com/hengshucredit/rule/core/compiler rule-engine-core/src/test/java/com/hengshucredit/rule/core/compiler
git commit -m "feat: compile unified rule operands"
```

### Task 6: Model field operand persistence and runtime resolution

**Files:**
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/OperandDescriptor.java`
- Modify: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/RuleModelInputField.java`
- Modify: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/RuleModelOutputField.java`
- Modify: `rule-engine-server/src/main/resources/sql/schema.sql`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuntimeOperandResolver.java`
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuntimeOperandResolverTest.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleModelService.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/ExecutionParameterBinder.java`
- Test: existing RuleModelService and binder tests.

**Interfaces:**
- Model input fields add `sourceOperand` and `defaultOperand` JSON strings; output fields add `targetOperand`.
- Produces: `RuntimeOperandResolver.resolve(String operandJson, Map<String,Object> context)` returning `{present,value}` semantics.

- [ ] **Step 1: Write runtime resolver RED tests**

```java
@Test
public void resolvesNestedManualPath() {
    Map<String, Object> request = singletonMap("customer", singletonMap("age", 36));
    OperandValue value = resolver.resolve("{\"kind\":\"PATH\",\"value\":\"request.customer.age\"}",
            singletonMap("request", request));
    assertTrue(value.isPresent());
    assertEquals(36, value.getValue());
}

@Test
public void modelDefaultPathBecomesFeatureValue() {
    RuleModelInputField field = new RuleModelInputField();
    field.setFieldName("age");
    field.setFieldType("INTEGER");
    field.setSourceOperand("{\"kind\":\"PATH\",\"value\":\"request.age\"}");
    field.setDefaultOperand("{\"kind\":\"PATH\",\"value\":\"defaults.age\"}");
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("defaults", Collections.singletonMap("age", "21"));
    Map<String, Object> bound = binder.bindModelOperands(
            Collections.singletonList(field), context, resolver);
    assertEquals(21, bound.get("age"));
}
```

- [ ] **Step 2: Run RED tests**

Run: `mvn -pl rule-engine-server -am -Dtest=RuntimeOperandResolverTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because the resolver does not exist.

- [ ] **Step 3: Add schema and entity fields**

```sql
`source_operand`  JSON DEFAULT NULL COMMENT '输入来源操作数',
`default_operand` JSON DEFAULT NULL COMMENT '缺失时默认操作数'
```

Add `target_operand` to `rule_model_output_field`. Keep `var_id/ref_type/script_name` as synchronized root-reference projections, not as an alternate source of truth.

- [ ] **Step 4: Implement runtime resolution and model binding**

For each model input field, resolve `sourceOperand`; if absent/null/blank resolve `defaultOperand`; coerce through the existing binder type logic; put the final value under the model's original `fieldName`. Managed PATH/REFERENCE operands resolve using their current projected script name; unmatched PATH reads its exact value path.

- [ ] **Step 5: Run model service tests**

Run: `mvn -pl rule-engine-server -am -Dtest=RuntimeOperandResolverTest,ExecutionParameterBinderTest,RuleModelServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS.

- [ ] **Step 6: Commit model runtime support**

```powershell
git add rule-engine-model/src/main/java/com/hengshucredit/rule/model rule-engine-server/src/main/resources/sql/schema.sql rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuntimeOperandResolver.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleModelService.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/ExecutionParameterBinder.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/service
git commit -m "feat: resolve model field operands"
```

### Task 7: Model detail UI and automatic path binding

**Files:**
- Modify: `rule-engine-builder-ui/src/views/model/ModelDetail.vue`
- Modify: `rule-engine-builder-ui/src/api/model.js` only if request helpers require explicit DTO fields.
- Test: `rule-engine-builder-ui/tests/unit/views/modelDetail.spec.js`

**Interfaces:**
- Consumes: `OperandPicker` and model entity fields from Task 6.
- Produces: source/default/target operand editing with automatic PATH matching and no toggle buttons.

- [ ] **Step 1: Write ModelDetail RED tests**

```js
test('输入字段编辑同时显示来源和默认值操作数选择器', () => {
  const row = wrapper.vm.model.inputFields[0]
  wrapper.vm.editInputField(row)
  const pickers = wrapper.findAllComponents(OperandPickerStub)
  expect(pickers.wrappers.some(p => p.props('value') === row.sourceOperand)).toBe(true)
  expect(pickers.wrappers.some(p => p.props('value') === row.defaultOperand)).toBe(true)
})

test('页面不再包含手动输入变量编码切换按钮', () => {
  expect(wrapper.text()).not.toContain('切换为手动输入变量编码')
  expect(wrapper.vm.toggleCustomVarMode).toBeUndefined()
})
```

- [ ] **Step 2: Run RED test**

Run: `cd rule-engine-builder-ui; npm test -- --runInBand tests/unit/views/modelDetail.spec.js`

Expected: FAIL because ModelDetail still uses VarPicker/custom modes.

- [ ] **Step 3: Replace model field controls**

Input association uses `sourceOperand`; default value uses `defaultOperand`; output association uses `targetOperand`. Parse JSON strings returned by the API with `parseOperandJson(value)` before passing them to the picker, and serialize with `JSON.stringify(operand)` in the update request. On each change, update only the operand object. Remove `customVarModes`, `isCustomVarMode`, `toggleCustomVarMode`, `onVarCodeInput`, custom input markup, and switch-button styles.

- [ ] **Step 4: Run ModelDetail tests**

Run: `cd rule-engine-builder-ui; npm test -- --runInBand tests/unit/views/modelDetail.spec.js`

Expected: PASS.

- [ ] **Step 5: Commit model UI migration**

```powershell
git add rule-engine-builder-ui/src/views/model/ModelDetail.vue rule-engine-builder-ui/src/api/model.js rule-engine-builder-ui/tests/unit/views/modelDetail.spec.js
git commit -m "feat: unify model field operand editing"
```

### Task 8: Operand dependency expansion and experiment routing

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/OperandDependencyCollector.java`
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/OperandDependencyCollectorTest.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleFieldAnalyzer.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/FieldDependencyResolver.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleLineageService.java`
- Modify: `rule-engine-builder-ui/src/views/experiment/ExperimentList.vue`
- Modify: `rule-engine-builder-ui/src/views/experiment/ExperimentDetail.vue`
- Test: server analyzer/dependency tests and experiment view tests.

**Interfaces:**
- Produces: `OperandDependencyCollector.collect(Object json)` returning ordered dependencies with `path/refId/refType/valueType`.
- RuleFieldAnalyzer expands model `sourceOperand` and `defaultOperand` before persisting rule input fields.

- [ ] **Step 1: Write dependency RED tests**

```java
@Test
public void modelDefaultPathIsAddedToRuleInputs() {
    RuleModelInputField field = modelField("age");
    field.setSourceOperand("{\"kind\":\"PATH\",\"value\":\"request.age\"}");
    field.setDefaultOperand("{\"kind\":\"REFERENCE\",\"refId\":22,\"refType\":\"VARIABLE\",\"code\":\"defaultAge\"}");
    List<RuleDefinitionInputField> inputs = analyzer.resolveInputFields(
            Collections.singletonList(modelReferenceField()), 1L);
    assertEquals(Arrays.asList("request.age", "defaultAge"), scriptNames(inputs));
}
```

- [ ] **Step 2: Run RED server tests**

Run: `mvn -pl rule-engine-server -am -Dtest=OperandDependencyCollectorTest,RuleFieldAnalyzerTest,FieldDependencyResolverTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because operand dependencies are not collected.

- [ ] **Step 3: Implement recursive dependency collection**

LITERAL returns none. PATH with `refId/refType` returns a managed dependency; unmatched PATH returns an external path dependency. REFERENCE returns a managed dependency. FUNCTION recursively collects args. Merge dependencies using `refType:refId` for managed references and exact path for external inputs.

- [ ] **Step 4: Integrate experiment conditions**

Experiment List and Detail already share `ConditionGroupEditor`; update selected-reference traversal, model-ref synchronization, field summary, and save-time expression generation to `leftOperand/rightOperand`. There must be no experiment-specific CONST/VAR controls.

- [ ] **Step 5: Run dependency and experiment tests**

Run: `mvn -pl rule-engine-server -am -Dtest=OperandDependencyCollectorTest,RuleFieldAnalyzerTest,FieldDependencyResolverTest -Dsurefire.failIfNoSpecifiedTests=false test`

Run: `cd rule-engine-builder-ui; npm test -- --runInBand tests/unit/views/experimentList.spec.js tests/unit/views/experimentDetail.spec.js`

Expected: PASS.

- [ ] **Step 6: Commit dependency integration**

```powershell
git add rule-engine-server/src/main/java/com/hengshucredit/rule/server/service rule-engine-server/src/test/java/com/hengshucredit/rule/server/service rule-engine-builder-ui/src/views/experiment rule-engine-builder-ui/tests/unit/views/experimentList.spec.js rule-engine-builder-ui/tests/unit/views/experimentDetail.spec.js
git commit -m "feat: expand operand dependencies"
```

### Task 9: Remove obsolete picker paths and update fixtures

**Files:**
- Delete `rule-engine-builder-ui/src/components/common/VarPicker.vue` if `rg "VarPicker" rule-engine-builder-ui/src` returns no consumers.
- Modify all frontend tests and SQL example fixtures that still contain `valueKind`, `_rightVarId`, `customResultVarMode`, or old scalar semantic values.
- Modify compiler/server fixtures under `rule-engine-core/src/test`, `rule-engine-server/src/test`, and `rule-engine-server/src/main/resources/sql/data-*.sql`.

**Interfaces:**
- Produces: one operand representation with no runtime old-format branches.

- [ ] **Step 1: Locate obsolete structures**

Run:

```powershell
rg -n "valueKind|switchRightToVar|customResultVarMode|toggleCustomVarMode|切换为手动输入变量编码|autoSwitchCustom" rule-engine-builder-ui rule-engine-core rule-engine-server
```

Expected: only deliberate historical SQL exports may remain; production code and active fixtures must have no hits.

- [ ] **Step 2: Update active examples and test fixtures**

Replace old leaves with `leftOperand/rightOperand`, old action scalar fields with operand fields, and model field associations/defaults with operand JSON. Do not edit `export_*.sql` historical dumps.

- [ ] **Step 3: Run frontend and backend focused suites**

Run: `cd rule-engine-builder-ui; npm test -- --runInBand`

Run: `mvn test`

Expected: all tests pass.

- [ ] **Step 4: Commit fixture cleanup**

```powershell
git add rule-engine-builder-ui rule-engine-core rule-engine-server rule-engine-model
git commit -m "test: update operand fixtures"
```

### Task 10: Full verification and browser acceptance

**Files:**
- No planned source additions; only targeted fixes discovered by verification.

**Interfaces:**
- Verifies every requirement from the design spec.

- [ ] **Step 1: Review the final diff and protected user changes**

Run: `git status --short; git diff --check; git diff --stat HEAD~9..HEAD`

Expected: no whitespace errors; the pre-existing test schema/sample-value changes remain present and are not accidentally reverted.

- [ ] **Step 2: Compile and start backend**

Run: `mvn clean install -DskipTests`

Expected: BUILD SUCCESS.

Run from `rule-engine-server`: `mvn spring-boot:run`

Expected: application starts on port 8080 without startup exceptions. Stop the process after the health/API check.

- [ ] **Step 3: Start frontend**

Run from `rule-engine-builder-ui`: `npm run dev`

Expected: compiled successfully and served on port 9090. Keep it running for browser acceptance, then stop it.

- [ ] **Step 4: Browser acceptance through UI only**

Perform these flows through visible UI:

1. Open a decision table/rule set and verify left/right operand pickers, manual threshold, managed reference, resolved manual path, unmatched path, save/reload, and rule test.
2. Open decision tree/flow, edit an edge condition and an action operand, save/reload, and execute a test.
3. Open cross-table, scorecard, advanced cross-table, and advanced scorecard; select semantic fields and values through OperandPicker and save.
4. Open Model Detail; bind a source path, set a default path/reference, save/reload, run model test, and confirm both dependencies appear in the rule test schema when the model is referenced.
5. Open Experiment condition routing; configure production and test group conditions with literal, path, and managed reference operands; save/reload and verify compiled expressions.
6. Confirm there is no “常量/变量” value-kind dropdown, manual-code switch button, or automatic picker popup.

- [ ] **Step 5: Run complete tests after UI fixes**

Run: `cd rule-engine-builder-ui; npm test`

Run: `mvn test`

Expected: all frontend and backend tests pass with zero failures.

- [ ] **Step 6: Final requirement audit**

Check every section of `docs/superpowers/specs/2026-07-12-unified-operand-picker-design.md` against code, tests, and browser evidence. Record any environmental limitation instead of claiming unperformed verification.
