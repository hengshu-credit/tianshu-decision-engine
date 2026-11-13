# Data Object Variable Reference Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将变量管理的数据对象改为逐行可展开表格，并让数据对象字段通过稳定变量 ID 自动接收调用参数或变量来源解析值。

**Architecture:** `rule_data_object_field.ref_variable_id` 保存值来源，`RuleFieldAnalyzer` 将映射字段展开为真实变量依赖，新的 `DataObjectFieldReferenceResolver` 在变量来源解析后把值装配到数据对象路径。决策制品冻结字段映射和变量依赖，活动执行、制品执行和嵌套规则执行复用同一装配语义。

**Tech Stack:** Java 17、Spring Boot 3.5、MyBatis Plus、JUnit 4、Vue 3 Options API、Element Plus、Vitest、Playwright、MySQL 8。

**Spec:** `docs/superpowers/specs/2026-08-28-data-object-variable-reference-design.md`

## Global Constraints

- 所有引用关联必须通过 ID；禁止通过变量编码、名称或脚本名称回溯匹配。
- 同时传入引用变量和目标路径时，显式目标路径（例如 `request.age`）优先；显式 `null` 也不得被覆盖。
- `ref_object_id` 继续表示 OBJECT/LIST 结构引用，`ref_variable_id` 仅表示字段值来源。
- LIST 字段允许整体引用 LIST 变量；列表元素内部子字段不做隐式按索引拼装。
- 不恢复当前工作区已经移除的脚本名称行内失焦保存；脚本名称仍只在编辑弹窗修改。
- 不新增第三方依赖，不改变一级菜单、导入入口或治理审批主流程。
- 前后端代码遵守现有风格；前端必须通过 ESLint。
- 长期本地服务统一使用 1800000ms 超时，验证完成后主动停止。
- 当前工作区已有用户修改；每次编辑前复核 `git diff`，不得覆盖或混入无关变更。

---

### Task 1: 字段持久化、数据库兼容和治理校验

**Files:**
- Modify: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/RuleDataObjectField.java`
- Modify: `rule-engine-server/src/main/resources/sql/schema.sql`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/SchemaSyncService.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleDataObjectService.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/SimpleEntityGovernedResourceAdapter.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/DataObjectGovernedResourceAdapter.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceResourceAdapterConfiguration.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/SchemaSyncServiceTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleDataObjectServiceTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/AggregateGovernedResourceAdapterTest.java`

**Interfaces:**
- Consumes: `RuleVariableMapper.selectById(Long)` 和现有治理聚合快照。
- Produces: `RuleDataObjectField.getRefVariableId()/setRefVariableId(Long)`；审批快照中的 `refVariableId`；治理依赖 `VARIABLE:<id>`。

- [ ] **Step 1: 写数据库和行映射失败测试**

在 `SchemaSyncServiceTest` 增加测试，反射调用 `ensureDataObjectFieldReferenceSchema()`，断言缺列/缺索引时执行：

```java
assertTrue(containsSql(jdbcTemplate.sqlList,
        "ADD COLUMN `ref_variable_id` BIGINT DEFAULT NULL"));
assertTrue(containsSql(jdbcTemplate.sqlList,
        "ADD INDEX `idx_ref_variable_id` (`ref_variable_id`)"));
```

在 `RuleDataObjectServiceTest` 断言 `toVariableRow(field)` 返回同一个 `refVariableId`。

- [ ] **Step 2: 写治理失败测试**

在 `AggregateGovernedResourceAdapterTest` 覆盖：有效同项目/全局变量通过；全局对象引用项目变量失败；跨项目变量失败；停用变量失败；STRING 字段引用 NUMBER 变量失败；NUMBER/INTEGER 数值家族通过；带 `parentFieldId` 且父字段为 LIST 时失败；`collectDependencies` 返回变量根资源 ID。

- [ ] **Step 3: 运行测试确认失败**

Run:

```powershell
mvn -pl rule-engine-server -am -Dtest=SchemaSyncServiceTest,RuleDataObjectServiceTest,AggregateGovernedResourceAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，缺少 `refVariableId`、schema 同步方法和引用变量治理校验。

- [ ] **Step 4: 实现最小持久化与兼容升级**

实体新增：

```java
/** 字段值直接引用的变量 ID（指向 rule_variable.id）。 */
private Long refVariableId;
```

`schema.sql` 在 `ref_object_id` 后增加列并增加 `idx_ref_variable_id`。`SchemaSyncService.run()` 调用新方法：

```java
private void ensureDataObjectFieldReferenceSchema() {
    String table = "rule_data_object_field";
    if (!tableExists(table)) return;
    addColumnIfMissing(table, "ref_variable_id",
            "`ref_variable_id` BIGINT DEFAULT NULL COMMENT '字段值直接引用的变量ID' AFTER `ref_object_id`");
    addIndexIfMissing(table, "idx_ref_variable_id", "`ref_variable_id`");
}
```

`RuleDataObjectService.toVariableRow` 加入 `m.put("refVariableId", f.getRefVariableId())`。

- [ ] **Step 5: 实现治理依赖和校验**

`SimpleEntityGovernedResourceAdapter.dependencyType` 将 `refVariableId` 映射到 `VARIABLE`。`DataObjectGovernedResourceAdapter` 注入 `RuleVariableMapper`，校验变量存在、启用、作用域可见和类型兼容。数值家族固定为 `NUMBER/INTEGER/INT/LONG/DOUBLE/FLOAT/DECIMAL/PROBABILITY`；其他类型归一化后必须相同。通过字段父链判断任一祖先是否为 LIST/ARRAY/SET，若是则拒绝子字段直接引用。

- [ ] **Step 6: 运行测试并提交**

Run:

```powershell
mvn -pl rule-engine-server -am -Dtest=SchemaSyncServiceTest,RuleDataObjectServiceTest,AggregateGovernedResourceAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS。

Commit only Task 1 files:

```powershell
git commit -m "feat: validate data object variable references"
```

---

### Task 2: 将映射字段展开为真实规则输入依赖

**Files:**
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleFieldAnalyzer.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleFieldAnalyzerTest.java`

**Interfaces:**
- Consumes: `RuleDataObjectField.refVariableId` 和 `RuleVariable` 稳定元数据。
- Produces: `resolveFields(...)`/`resolveInputFields(...)` 对映射字段输出真实变量依赖；未映射字段保持 `DATA_OBJECT` 输入。

- [ ] **Step 1: 写输入展开失败测试**

构造 `request.age`（`DATA_OBJECT:42`）引用 `VARIABLE:7 age`，断言 `resolveInputFields` 返回 `VARIABLE:7` 且 `scriptName=age`，不再返回 `request.age`。再覆盖未映射字段仍返回 `DATA_OBJECT:42`、API/DB/LIST 引用仍展开上游依赖、同一变量被多个字段引用时去重。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
mvn -pl rule-engine-server -am -Dtest=RuleFieldAnalyzerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，映射字段仍被当作数据对象叶子保留。

- [ ] **Step 3: 实现稳定 ID 展开**

在数据对象字段元数据中携带 `refVariableId`。`expandFieldRecursive` 在处理 `DATA_OBJECT` 叶子时按 ID 查找引用变量元数据，构造新的 `RuleDefinitionInputField` 后递归调用现有变量展开逻辑：

```java
if ("DATA_OBJECT".equals(refType) && meta != null
        && meta.get("refVariableId") instanceof Long refVariableId) {
    Map<String, Object> variableMeta = findMetaById(
            refVariableId, "VARIABLE", varMetaMap);
    if (variableMeta != null) {
        RuleDefinitionInputField source = inputFromMeta(variableMeta);
        expandFieldRecursive(source, varMetaMap, seen, visited, result);
        visited.remove(visitKey);
        return;
    }
}
```

查找必须只用 `VARIABLE:<id>`，不得按编码回溯。引用缺失时保留原数据对象字段，使发布治理能够报告稳定引用错误。

- [ ] **Step 4: 运行测试并提交**

Run the Task 2 test command; expected PASS。

```powershell
git commit -m "feat: expand object fields to variable inputs"
```

---

### Task 3: 统一运行时引用装配并保证目标路径优先

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/DataObjectFieldReferenceResolver.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/ExecutionParameterBinder.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleExecuteService.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleRuntimeInvoker.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/DataObjectFieldReferenceResolverTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleExecuteServiceTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleRuntimeInvokerTest.java`

**Interfaces:**
- Consumes: 规则模型中的直接 `DATA_OBJECT` 字段引用、字段/变量元数据、解析后的参数 Map。
- Produces: `ReferencePlan livePlan(...)`、`ReferencePlan snapshotPlan(...)`、`captureExplicitTargets(...)`、`addRequiredSourceNames(...)`、`apply(...)`。

- [ ] **Step 1: 写装配器失败测试**

新增独立测试覆盖：

```java
Map<String, Object> params = JSON.parseObject("{\"age\":22}");
Set<String> explicit = resolver.captureExplicitTargets(plan, params);
resolver.apply(plan, params, explicit);
assertEquals(22, ((Map<?, ?>) params.get("request")).get("age"));
```

同时覆盖 `request.age=30` 优先、显式 `null` 优先、多个目标引用同一变量、来源值为嵌套脚本路径、目标中间节点为非 Map 时抛出包含 `request.age` 的错误、LIST 整体映射。

- [ ] **Step 2: 写执行入口失败测试**

在 `RuleExecuteServiceTest` 和 `RuleRuntimeInvokerTest` 覆盖活动规则、制品规则及嵌套规则都在 `variableSourceResolver` 之后装配，并把引用变量脚本名加入 `VariableResolveOptions.requiredScriptNames`。

- [ ] **Step 3: 运行测试确认失败**

Run:

```powershell
mvn -pl rule-engine-server -am -Dtest=DataObjectFieldReferenceResolverTest,RuleExecuteServiceTest,RuleRuntimeInvokerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，装配器不存在或规则仍缺少 `request.age`。

- [ ] **Step 4: 实现引用计划和路径语义**

`ReferencePlan.Binding` 固定保存 `fieldId`、`targetPath`、`fieldType`、`sourceVariableId`、`sourceScriptName`。`captureExplicitTargets` 在变量解析前记录目标路径是否存在，优先识别直接点号键，再读取嵌套 Map；显式 `null` 也算存在。

`apply` 仅对未显式目标执行：从 `sourceScriptName` 读取最终变量值，存在时写入 `targetPath`；中间节点已存在但不是 Map 时抛错。写入后用 `ExecutionParameterBinder.bindRuleInputs(directDataObjectFields, params, options)` 复用字段类型转换。

- [ ] **Step 5: 接入所有规则执行入口**

在 `RuleExecuteService` 两个执行流和 `RuleRuntimeInvoker.executeRule` 中按同一顺序执行：

```java
ReferencePlan plan = dataObjectFieldReferenceResolver.livePlan(directFields, projectId);
Set<String> explicitTargets = dataObjectFieldReferenceResolver.captureExplicitTargets(plan, params);
dataObjectFieldReferenceResolver.addRequiredSourceNames(plan, requiredNames);
Map<String, Object> executeParams = executionParameterBinder.bindRuleInputs(inputFields, params, options);
variableSourceResolver.resolveInto(projectId, executeParams, options);
dataObjectFieldReferenceResolver.apply(plan, executeParams, explicitTargets);
executeParams = executionParameterBinder.bindRuleInputs(directFields, executeParams, options);
```

制品路径使用 `snapshotPlan`。日志中的原始输入继续记录调用方输入，不把 API/DB 派生值伪装成原始传参。

- [ ] **Step 6: 运行测试并提交**

Run the Task 3 test command; expected PASS。

```powershell
git commit -m "feat: assemble referenced variables into objects"
```

---

### Task 4: 冻结制品字段映射和变量依赖

**Files:**
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/artifact/RuleDependencyClosureService.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/artifact/ArtifactRuntimeSnapshotService.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/artifact/RuleDependencyClosureServiceTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/artifact/ArtifactRuntimeSnapshotServiceTest.java`

**Interfaces:**
- Consumes: Task 1 的 `refVariableId` 和 Task 3 的 `snapshotPlan`。
- Produces: `RuntimeSnapshot.getDataObjectFields()`；数据对象字段制品组件内冻结的 `refVariableId`；引用变量组件依赖。

- [ ] **Step 1: 写制品失败测试**

断言 `addDataObjectField` 生成的 JSON 包含 `refVariableId`，并额外产生 `VARIABLE:<id>` 依赖；快照加载器把 `resourceType=DATA_OBJECT` 组件解析为 `RuleDataObjectField`。历史组件缺少 `refVariableId` 时列表仍可加载且字段值为 null。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
mvn -pl rule-engine-server -am -Dtest=RuleDependencyClosureServiceTest,ArtifactRuntimeSnapshotServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，组件未冻结映射或运行时忽略数据对象字段组件。

- [ ] **Step 3: 实现依赖闭包和快照加载**

`RuleDependencyClosureService.addDataObjectField` 写入 `refVariableId`，存在时加载真实变量确定 `VARIABLE/CONSTANT` 类型；本功能只接受普通变量，遇到常量或不存在变量生成发布错误。随后调用现有 `addVariable("VARIABLE", id, ...)`。

`ArtifactRuntimeSnapshotService` 对 `resourceType=DATA_OBJECT` 的组件解析 `RuleDataObjectField`，加入 `RuntimeSnapshot.dataObjectFields`，并暴露只读 getter。活动配置和制品配置的装配计划产生相同绑定。

- [ ] **Step 4: 运行测试并提交**

Run the Task 4 test command; expected PASS。

```powershell
git commit -m "feat: freeze object variable bindings in artifacts"
```

---

### Task 5: 数据对象逐行表格与字段引用变量选择器

**Files:**
- Modify: `rule-engine-builder-ui/src/views/variable/VariableList.vue`
- Modify: `rule-engine-builder-ui/tests/unit/views/variableList.spec.js`

**Interfaces:**
- Consumes: `/rule/dataobject/tree` 返回的 `refVariableId`；`listVariablesByProject(projectId)` 返回当前范围可见变量。
- Produces: 每行一个数据对象的可展开表格；字段草稿载荷中的 `refVariableId`。

- [ ] **Step 1: 写表格布局失败测试**

断言数据对象页签包含一个绑定 `paginatedObjectTree` 的 `el-table` 和 `type="expand"` 列，不再使用 `v-for` 的 `.var-group-card`；展开内容仍包含树形字段表、字段分页和现有操作。

- [ ] **Step 2: 写引用变量交互失败测试**

覆盖字段编辑时 `refVariableId` 回显；选择器只展示非 CONSTANT、启用、作用域可见且类型兼容的变量；提交、行编辑聚合和转全局载荷携带/清空 `refVariableId`；字段表展示变量名称和编码。保留工作区现有的“脚本名称不再行内保存”断言。

- [ ] **Step 3: 运行测试确认失败**

Run:

```powershell
cd rule-engine-builder-ui
npm test -- --run tests/unit/views/variableList.spec.js
```

Expected: FAIL，数据对象仍为卡片且字段没有引用变量选择器。

- [ ] **Step 4: 实现逐行可展开表格**

用 Element Plus 展开表格替换卡片循环：

```vue
<el-table :data="paginatedObjectTree" row-key="object.id" border>
  <el-table-column type="expand">
    <template #default="{ row: node }">
      <!-- 复用现有字段树表格、分页和字段操作 -->
    </template>
  </el-table-column>
  <!-- 作用范围、项目、对象编码/名称、脚本名称、类型、来源、字段数、状态/更新、操作 -->
</el-table>
```

对象脚本名称保持只读代码样式，对象类型若需要修改仍走现有审批操作，不恢复失焦保存。

- [ ] **Step 5: 实现字段引用变量选择器**

字段弹窗打开时通过 `listVariablesByProject` 加载候选，过滤 `varSource !== 'CONSTANT' && status === 1`，再按数值家族/同类型兼容。表单新增：

```vue
<el-form-item v-if="isObjectField" label="引用变量">
  <el-select v-model="form.refVariableId" clearable filterable>
    <el-option
      v-for="item in compatibleReferenceVariables"
      :key="item.id"
      :value="item.id"
      :label="`${item.varLabel}（${item.varCode}）`"
    />
  </el-select>
</el-form-item>
```

新建、编辑、行载荷、聚合审批和显示映射均携带 `refVariableId`。OBJECT/LIST 的原引用对象文案改为“结构引用对象”。

- [ ] **Step 6: 运行单测和 ESLint 并提交**

Run:

```powershell
cd rule-engine-builder-ui
npm test -- --run tests/unit/views/variableList.spec.js
npm run lint
```

Expected: PASS。

```powershell
git commit -m "feat: show expandable object reference table"
```

---

### Task 6: 全链路回归和浏览器验收

**Files:**
- Modify only if verification exposes an implementation defect.
- Review: all files changed by Tasks 1-5 plus pre-existing user changes.

**Interfaces:**
- Consumes: Tasks 1-5 的完整功能。
- Produces: 编译、测试、启动和真实 UI 操作证据。

- [ ] **Step 1: 运行后端完整校验**

Run from repository root:

```powershell
mvn clean install -DskipTests
mvn test
```

Expected: 构建成功，全部非环境诊断测试通过。

- [ ] **Step 2: 启动后端并验证健康状态**

Run `mvn spring-boot:run` from `rule-engine-server` with 1800000ms timeout，确认 8080 正常启动且无启动异常，然后主动停止进程。

- [ ] **Step 3: 运行前端完整校验**

Run:

```powershell
cd rule-engine-builder-ui
npm run lint
npm test
npm run build
```

Expected: ESLint、全部 Vitest 和 Vite 生产构建通过。

- [ ] **Step 4: 启动前端并执行真实 UI 流程**

用 1800000ms 超时运行 `npm run dev`，通过浏览器完成：进入变量管理 → 数据对象页签确认逐行表格 → 展开对象 → 编辑字段选择引用变量 → 生成并审批草稿 → 在规则中使用对象字段 → 规则测试只传平铺引用变量 → 验证对象路径收到值 → 同时传平铺值与 `request.age` → 验证 `request.age` 优先。不得通过后端直接造数据跳过 UI 步骤。完成后主动停止前端与后端进程。

- [ ] **Step 5: 审查最终差异和提交**

运行 `git diff --check`、逐文件核对 ID 引用、制品快照、优先级和用户原有未提交修改。只提交本功能产生的剩余文件；若没有剩余文件则不创建空提交。
