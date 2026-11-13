# Model Field Display and Output Transform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 模型输入只保留默认值，数据对象字段统一显示“变量名称 脚本路径”，模型输出通过按 ID 引用的函数 Operand 完成可执行转换。

**Architecture:** 前端沿用统一 Operand 数据结构和选择器，模型输出新增顶层 FUNCTION `transformOperand`，参数由用户逐个选择并用 `compileOperand` 展示公式。后端删除模型缺失值和旧转换枚举，使用 `RuleFunctionService` 按函数 ID 执行转换，并在模型依赖解析中纳入转换参数引用。

**Tech Stack:** Vue 2.6、Element UI、Jest/Vue Test Utils、Spring Boot 2.3、MyBatis Plus、QLExpress 4、JUnit 4、MySQL 8。

## Global Constraints

- 所有变量、数据对象字段、模型输出和函数引用必须保存资源 ID 及明确 `refType`；名称和编码只用于展示。
- 用户输入的变量编码、模型编码、函数编码和脚本路径必须原样保留。
- 不新增依赖，不迁移旧 `missing_value` 或 `transform_type` 数据，不保留兼容读取。
- 只删除模型字段旧能力，不删除规则定义字段中的 `missing_value`、`transform_type` 或 `transform_params`。
- 所有生产代码必须先有能按预期失败的测试，改动保持最小且匹配现有风格。

---

### Task 1: 统一数据对象字段展示并简化模型输入默认值

**Files:**
- Modify: `rule-engine-builder-ui/src/utils/referenceCatalog.js`
- Modify: `rule-engine-builder-ui/tests/unit/utils/referenceCatalog.spec.js`
- Modify: `rule-engine-builder-ui/src/views/model/ModelDetail.vue`
- Modify: `rule-engine-builder-ui/tests/unit/views/modelDetail.spec.js`

**Interfaces:**
- Consumes: `buildReferenceCatalog(variables, objectTree, models)` 与现有 `OperandValueDisplay`。
- Produces: 数据对象条目的 `refLabel.label` 只含字段变量名称，`refCode` 保持完整对象脚本路径；模型输入表仅展示 `defaultOperand`。

- [ ] **Step 1: 写失败的展示测试**

在 `referenceCatalog.spec.js` 将数据对象断言改为：

```js
expect(catalog.options.dataObject[0].refLabel).toEqual({
  label: '身份证号',
  code: 'customer.idCard'
})
expect(catalog.options.dataObject[0].displayName).toBe('身份证号 customer.idCard')
```

在 `modelDetail.spec.js` 新增模板行为断言：组件不存在“缺失值”表头，存在默认值提示文本 `来源为空或未取到值时使用默认值；未配置则按空值传入模型。`，且输入字段保存请求不再包含 `missingValue`。

- [ ] **Step 2: 验证测试按预期失败**

Run:

```powershell
cd rule-engine-builder-ui
npm.cmd test -- --runInBand tests/unit/utils/referenceCatalog.spec.js tests/unit/views/modelDetail.spec.js
```

Expected: 数据对象标签仍为 `客户资料/身份证号`，模板仍有“缺失值”列且保存参数仍含 `missingValue`。

- [ ] **Step 3: 实现最小展示改动**

在 `referenceCatalog.js` 的数据对象条目中保持完整 `refCode`，只把标签改为字段标签：

```js
const entry = refEntry({
  id: field.id,
  refType: 'DATA_OBJECT',
  refCode,
  label: fieldLabel,
  // 其余现有字段保持不变
})
```

在 `ModelDetail.vue` 删除缺失值列和 `missingValueHint`，把默认值表头改为带 `el-tooltip` 的 header slot；`editInputField`、`saveInputField`、`cancelEditInput` 不再读写 `missingValue`。

- [ ] **Step 4: 验证目标测试通过**

Run: Task 1 Step 2 的同一命令。

Expected: 两个 suite 全部通过。

### Task 2: 模型输出函数选择、显式参数和公式展示

**Files:**
- Modify: `rule-engine-builder-ui/src/views/model/ModelDetail.vue`
- Modify: `rule-engine-builder-ui/tests/unit/views/modelDetail.spec.js`
- Modify: `rule-engine-builder-ui/src/utils/operand.js`
- Modify: `rule-engine-builder-ui/tests/unit/utils/operand.spec.js`

**Interfaces:**
- Consumes: `createFunctionOperand(fn, args)`、`compileOperand(operand)`、`OperandPicker`、函数 `paramsJson`。
- Produces: `row.transformOperand`，顶层类型固定为 FUNCTION；参数类型仅允许 `LITERAL/PATH/REFERENCE`；保存请求序列化为 JSON。

- [ ] **Step 1: 写失败的前端交互测试**

覆盖以下行为：

```js
wrapper.vm.projectFunctions = [{
  id: 7,
  funcCode: 'scoreByProbability',
  funcName: '概率转评分',
  returnType: 'NUMBER',
  paramsJson: '[{"name":"probability","type":"NUMBER"},{"name":"base","type":"NUMBER"}]'
}]
wrapper.vm.onTransformFunctionSelect(row, 7)
expect(row.transformOperand.functionId).toBe(7)
expect(row.transformOperand.args).toEqual([null, null])
wrapper.vm.onTransformArgSelect(row, 0, modelOutputOperand)
wrapper.vm.onTransformArgSelect(row, 1, { kind: 'LITERAL', value: '600', valueType: 'NUMBER' })
expect(wrapper.vm.transformFormula(row)).toBe('scoreByProbability(risk_model.probability, 600)')
```

并断言 `saveOutputField` 发送 `transformOperand: JSON.stringify(row.transformOperand)`，取消编辑恢复原 Operand，未配置时公式为 `-`。在 `operand.spec.js` 断言 FUNCTION 展示使用完整公式而不是 `函数名(...)`。

- [ ] **Step 2: 验证测试按预期失败**

Run:

```powershell
cd rule-engine-builder-ui
npm.cmd test -- --runInBand tests/unit/views/modelDetail.spec.js tests/unit/utils/operand.spec.js
```

Expected: `onTransformFunctionSelect` 等方法不存在，旧转换下拉仍保存 `transformType`，函数显示仍省略参数。

- [ ] **Step 3: 实现函数编辑单元格**

引入 `compileOperand` 和 `createFunctionOperand`，新增：

```js
transformArgKinds: ['LITERAL', 'PATH', 'REFERENCE']
```

以及方法：

```js
onTransformFunctionSelect(row, functionId)
onTransformArgSelect(row, index, operand)
transformFunctionParams(row)
transformFormula(row)
```

函数选择按 `functionId` 查找 `projectFunctions`，按 `paramsJson` 数量创建空参数；每个参数显示名称和 OperandPicker。加载时解析 `transformOperand`；编辑快照、取消恢复和保存请求只处理 `transformOperand`，完全移除 `transformType` UI 与请求字段。`operandDisplay(FUNCTION)` 返回 `compileOperand(operand)`。

- [ ] **Step 4: 保证当前模型输出可显式选择**

构建模型引用目录前，如果 `listAllModelsByProject` 未返回当前模型，则把包含当前 `model.outputFields` 的模型详情追加到模型列表；当前模型输出引用继续使用输出字段 ID、`refType=MODEL_OUTPUT` 和完整路径 `<modelCode>.<outputCode>`。

- [ ] **Step 5: 验证目标测试通过**

Run: Task 2 Step 2 的同一命令。

Expected: 两个 suite 全部通过，公式包含每个用户选择的参数。

### Task 3: 删除旧模型字段并持久化函数 Operand

**Files:**
- Modify: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/RuleModelInputField.java`
- Modify: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/RuleModelOutputField.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleModelService.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleFieldAnalyzer.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleModelServiceTest.java`
- Modify: `rule-engine-server/src/main/resources/sql/schema.sql`
- Modify: `rule-engine-server/src/main/resources/sql/data-tianshu-example.sql`

**Interfaces:**
- Consumes: `RuleFunctionService.getById(functionId)` 与函数 `paramsJson`。
- Produces: `RuleModelOutputField.getTransformOperand()`；模型输入不再有 `missingValue`，模型输出不再有 `transformType`。

- [ ] **Step 1: 写失败的持久化和校验测试**

在 `RuleModelServiceTest` 增加：更新输出字段会原样保存 `transformOperand`；缺失函数 ID、函数不存在、停用函数、参数数目不匹配、参数为空、嵌套 FUNCTION 参数均被拒绝；合法的空转换允许保存。测试函数对象必须设置真实 ID、scope、projectId、status 和 paramsJson。

- [ ] **Step 2: 验证测试按预期失败**

Run:

```powershell
mvn -pl rule-engine-server -am "-Dtest=RuleModelServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: `transformOperand` getter/setter或校验行为不存在。

- [ ] **Step 3: 修改实体和服务**

`RuleModelInputField` 删除：

```java
private String missingValue;
```

`RuleModelOutputField` 删除 `transformType`，新增：

```java
/** 函数转换 Operand；顶层必须是 FUNCTION。 */
private String transformOperand;
```

`RuleModelService.updateInputField` 不再复制缺失值，删除 `applyMissingValues` 和仅服务于它的类型转换方法；PMML 执行直接使用绑定并默认值处理后的输入。`updateOutputField` 校验后保存 `transformOperand`；发布校验重复验证函数 ID、作用域、启用状态、参数数量和参数 Operand 完整性。`RuleFieldAnalyzer.copyModelInputField` 不再复制模型缺失值。

- [ ] **Step 4: 修改数据库结构和示例数据**

`CREATE TABLE rule_model_input_field` 删除 `missing_value`；`CREATE TABLE rule_model_output_field` 删除 `transform_type` 并新增：

```sql
`transform_operand` JSON DEFAULT NULL COMMENT '模型输出函数转换 Operand',
```

`ensure_operand_columns` 增加幂等删列/增列逻辑；`data-tianshu-example.sql` 的模型字段 INSERT 同步删除旧列及对应值，不修改历史 `export_*.sql`。

- [ ] **Step 5: 验证目标测试与编译通过**

Run: Task 3 Step 2 命令，并执行：

```powershell
mvn -pl rule-engine-server -am -DskipTests compile
```

Expected: 编译成功，`RuleModelServiceTest` 全部通过。

### Task 4: 按函数 ID 执行输出转换并解析依赖

**Files:**
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleFunctionService.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleFunctionServiceTest.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleModelService.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleModelServiceTest.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/VariableSourceResolver.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/VariableSourceResolverTest.java`

**Interfaces:**
- Produces: `RuleFunctionService.invoke(Long functionId, List<Object> args)`；模型执行返回已转换 `outputs`。
- Consumes: `OperandValueResolver.resolve(JSONObject, context)` 与 `collectPaths(transformOperand)`。

- [ ] **Step 1: 写失败的函数调用和模型转换测试**

`RuleFunctionServiceTest` 断言 `invoke(1L, Arrays.asList(12.345))` 返回 `12.35`，参数数量不匹配和停用函数抛出明确异常。

`RuleModelServiceTest` 通过反射调用输出转换方法，构造原始输出 `{probability: 0.2}`、转换上下文 `{risk_model: rawOutputs}` 和 FUNCTION Operand，断言函数服务收到字段 ID 引用解析出的 `0.2` 及用户阈值，结果替换原字段；未配置字段保持原值。

`VariableSourceResolverTest` 断言转换参数中的外部路径加入模型依赖，当前模型自身 `<modelCode>.<output>` 不形成自依赖，且 `buildModelParams` 保留转换所需的完整已解析上下文。

- [ ] **Step 2: 验证测试按预期失败**

Run:

```powershell
mvn -pl rule-engine-server -am "-Dtest=RuleFunctionServiceTest,RuleModelServiceTest,VariableSourceResolverTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: `invoke` 和输出转换不存在，转换依赖未收集。

- [ ] **Step 3: 实现函数调用**

在 `RuleFunctionService` 增加：

```java
public Object invoke(Long functionId, List<Object> args)
```

按 ID 获取启用函数，解析 `paramsJson` 的参数名，校验数量，构建参数上下文，复用现有函数注册和 `buildFunctionTestScript` 执行链；执行失败时抛出包含函数编码的 `IllegalArgumentException`。

- [ ] **Step 4: 实现模型输出转换**

`RuleModelService.executePmml` 得到 `rawOutputs` 后调用私有 `applyOutputTransforms(model, params, rawOutputs)`。转换上下文包含完整 params 和 `{modelCode: rawOutputs}`；所有字段从同一原始输出快照取值。顶层 FUNCTION 的每个参数用 `OperandValueResolver.resolve` 解析，再调用 `RuleFunctionService.invoke(functionId, args)`，结果写回原输出 key。

- [ ] **Step 5: 收集转换依赖**

`VariableSourceResolver.collectModelInputNames` 遍历输出字段 `transformOperand` 的路径引用，排除当前模型自身 `<modelCode>.` 前缀；`buildModelParams` 从完整 `resolvedParams` 初始化，再覆盖模型字段名，确保外部转换参数可在执行时解析。

- [ ] **Step 6: 验证目标测试通过**

Run: Task 4 Step 2 的同一命令。

Expected: 三个 suite 全部通过。

### Task 5: 全量验证、UI 验收和 Git 集成

**Files:**
- Review all modified files only; no speculative refactor.

**Interfaces:**
- Produces: 可启动、可测试且已推送到远程 `master` 的完整分支结果。

- [ ] **Step 1: 前端全量验证**

```powershell
cd rule-engine-builder-ui
npm.cmd test -- --runInBand
npm.cmd run dev
```

确认 9090 页面无编译错误后停止开发进程。

- [ ] **Step 2: 后端完整验证**

```powershell
mvn clean install -DskipTests
cd rule-engine-server
mvn spring-boot:run
```

确认服务完成启动后停止进程；回到根目录执行：

```powershell
mvn test
```

- [ ] **Step 3: 浏览器完整 UI 操作**

从前端 UI 登录，进入模型详情；验证默认值提示、数据对象名称/路径、输出函数选择、所有参数显式选择、公式预览、保存、刷新和模型测试。禁止通过后端直接生成数据绕过步骤。

- [ ] **Step 4: 差异审查**

```powershell
git diff --check
git status --short
git diff --stat
```

逐项核对设计规范，确认未误删规则定义字段能力，未修改历史导出 SQL，所有未提交文件均属于当前分支要求。

- [ ] **Step 5: 提交、合并并推送**

获取远程更新，将当前分支提交完整；安全合并 `origin/master`，解决冲突后再次运行前后端全量测试。随后切换本地 `master`，合并当前分支，推送：

```powershell
git fetch origin
git add --all
git commit -m "feat: add function-based model output transforms"
git merge origin/master
git checkout master
git merge codex/unified-operand-picker
git push origin master
```

Expected: `origin/master` 指向包含当前分支全部提交的合并结果；不使用 force push。
