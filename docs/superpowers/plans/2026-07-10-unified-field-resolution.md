# Unified Field Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用一套字段依赖、测试结构、操作数和执行参数语义修复规则/模型测试链路，并覆盖九类规则设计器、嵌套规则和所有变量来源。

**Architecture:** 保留现有模型 JSON 和公开执行接口；将 `RuleFieldAnalyzer` 已有递归能力暴露为无副作用解析入口，由 `RuleTestSchemaService` 统一生成测试字段和嵌套 JSON。编译期以显式引用元数据区分引用与字面量，执行期以 `ExecutionParameterBinder` 按字段类型绑定参数，并由请求级 `RuleRuntimeInvoker` 同步计算变量供嵌套规则读取。前端通过共享引用目录、测试结果归一化和测试结构 API 适配现有页面。

**Tech Stack:** Java 8、Spring Boot 2.3、QLExpress 4、Fastjson、JUnit 4、Vue 2、Element UI、Jest、Vue Test Utils

## Global Constraints

- 所有持久化引用必须使用 `refId/varId + refType`；不得以名称或编码替代稳定 ID。
- 用户输入的变量编码、模型编码和对象字段名必须原样保存，不做大小写或命名转换。
- 外部输入叶子只包含 `INPUT` 和 `DATA_OBJECT`；`COMPUTED` 是运行时中间节点；API、DB、LIST、MODEL 递归展开依赖。
- 字段槽位手输 code 必须唯一解析为引用；值槽位手输内容是字面量；选择器选择的值是引用。
- 旧函数 `_argRefs[index]` 缺失或为 `null` 时，参数按字面量编译。
- 不新增第三方依赖，不改变现有规则发布和客户端执行公开契约。
- 每项生产代码变更前必须先写失败测试并确认按预期失败。

---

### Task 1: 统一函数参数操作数与运行时结果同步脚本

**Files:**
- Create: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/compiler/ActionOperandCompiler.java`
- Modify: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/compiler/ActionDataCompiler.java`
- Modify: `rule-engine-core/src/test/java/com/hengshucredit/rule/core/compiler/ActionDataCompilerTest.java`

**Interfaces:**
- Produces: `ActionOperandCompiler.compileLiteral(String)`；`ActionDataCompiler` 对显式 `_argRefs` 编译引用，对空引用编译字面量，并在目标赋值后生成 `setRuntimeValue(path, value)`。

- [ ] **Step 1: 写失败测试**

```java
@Test
public void compileFunctionTreatsUnreferencedIdentifierAsLiteralAndSyncsTarget() {
    JSONArray actions = JSON.parseArray("[{\"type\":\"func-call\",\"target\":\"age\",\"funcName\":\"idCardAge\",\"args\":[\"idcard_no\",\"credit_time\",\"DAY\"],\"_argRefs\":[{\"_varId\":6},{\"_varId\":8},null]}]");
    Map<Long, String> names = new LinkedHashMap<>();
    names.put(6L, "idcard_no");
    names.put(8L, "credit_time");
    String script = ActionDataCompiler.compile(actions, new VarContext(names));
    assertTrue(script.contains("idCardAge(idcard_no, credit_time, \"DAY\")"));
    assertTrue(script.contains("setRuntimeValue(\"age\", age)"));
}
```

- [ ] **Step 2: 运行红灯**

Run: `mvn -pl rule-engine-core -Dtest=ActionDataCompilerTest test`

Expected: FAIL，脚本当前包含未加引号的 `DAY`，且没有 `setRuntimeValue`。

- [ ] **Step 3: 最小实现**

```java
static String compileLiteral(String value) {
    String text = value == null ? "" : value.trim();
    if (text.matches("-?\\d+(\\.\\d+)?") || "true".equals(text) || "false".equals(text) || "null".equals(text)) return text;
    if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) return text;
    return quoteString(text);
}
```

在 `assign`、有目标的 `func-call`、`ternary`、`in-check`、`template-str`、有目标的 `rule-call` 末尾追加同步调用；舍入赋值只在最终值后同步。

- [ ] **Step 4: 运行绿灯和编译器回归**

Run: `mvn -pl rule-engine-core -Dtest=ActionDataCompilerTest,DecisionFlowCompilerTest,DecisionTreeCompilerTest,RuleSetCompilerTest test`

Expected: PASS。

### Task 2: 修正模型字段提取中的函数字面量

**Files:**
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleModelVarParser.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleFieldAnalyzer.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleModelVarParserTest.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleFieldAnalyzerTest.java`

**Interfaces:**
- Consumes: `_argRefs[index]` 操作数语义。
- Produces: 函数参数只有显式引用进入输入字段；`rule-call` 输入递归合并子规则字段。

- [ ] **Step 1: 写 JCLTest 失败测试**

```java
@Test
public void functionLiteralOptionIsNotAnInputField() {
    String json = "{\"nodes\":[{\"actionData\":[{\"type\":\"func-call\",\"target\":\"age\",\"args\":[\"idcard_no\",\"credit_time\",\"DAY\"],\"_argRefs\":[{\"_varId\":6},{\"_varId\":8},null]}]}]}";
    ParseResult result = parser.parse(json, "FLOW");
    assertTrue(result.getInputCodes().contains("idcard_no"));
    assertFalse(result.getInputCodes().contains("DAY"));
}
```

并在 `RuleFieldAnalyzerTest` 断言输出变量 `age` 被移出嵌套规则外部输入，而子规则数据对象字段保留完整路径。

- [ ] **Step 2: 运行红灯**

Run: `mvn -pl rule-engine-server -Dtest=RuleModelVarParserTest,RuleFieldAnalyzerTest test`

Expected: FAIL，`DAY` 当前被识别为输入。

- [ ] **Step 3: 最小实现**

仅当 `_argRefs[index]` 是包含有效 `_varId` 的对象时，将对应 `args[index]` 加入输入引用；缺失引用的参数不再按标识符启发式提取。`RuleFieldAnalyzer` 的 actionData 解析遵守同一规则。

- [ ] **Step 4: 运行绿灯**

Run: `mvn -pl rule-engine-server -Dtest=RuleModelVarParserTest,RuleFieldAnalyzerTest test`

Expected: PASS。

### Task 3: 统一无副作用字段计划和测试结构 API

**Files:**
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/ResolutionPlan.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/RuleTestSchemaRequest.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/RuleTestSchema.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/FieldDependencyResolver.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleTestSchemaService.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleTestSchemaController.java`
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/FieldDependencyResolverTest.java`
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleTestSchemaServiceTest.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleFieldAnalyzer.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/VariableSourceResolver.java`

**Interfaces:**
- Produces: `FieldDependencyResolver.resolve(RuleTestSchemaRequest)`；不可变语义的 `ResolutionPlan.externalInputs/runtimeNodes/outputs/diagnostics`；`POST /api/rule/test-schema`；响应 `inputs/outputs/sampleParams/diagnostics`。

- [ ] **Step 1: 写失败测试**

先在 `FieldDependencyResolverTest` 覆盖规则、模型、变量三个入口：规则入口递归合并 `rule-call`；模型入口读取模型输入字段；变量入口将 API/DB/LIST/MODEL/COMPUTED 递归展开到 INPUT/DATA_OBJECT，并检测 `A -> B -> A` 环。再用可替换的 resolver 返回 `score_f1_fields.HYBASE_X115`、`score_f1_fields.HYDK_X760` 和 `age`，断言服务生成：

```java
assertEquals(0, ((Map<?, ?>) schema.getSampleParams().get("score_f1_fields")).get("HYBASE_X115"));
assertEquals(0, schema.getSampleParams().get("age"));
assertFalse(schema.getSampleParams().containsKey("score_f1.score"));
assertFalse(schema.getSampleParams().containsKey("DAY"));
```

- [ ] **Step 2: 运行红灯**

Run: `mvn -pl rule-engine-server -Dtest=FieldDependencyResolverTest,RuleTestSchemaServiceTest test`

Expected: FAIL，因为服务和 DTO 尚不存在。

- [ ] **Step 3: 提取解析入口并实现服务**

```java
public ResolvedFields resolveFields(String modelJson, String modelType, Long projectId) {
    List<RuleDefinitionInputField> inputs = prepareInputFields(modelJson, modelType, projectId);
    List<RuleDefinitionOutputField> outputs = prepareOutputFields(modelJson, modelType, projectId);
    return new ResolvedFields(removeOutputFields(inputs, outputs), outputs);
}
```

`analyzeAndPersist` 和 `FieldDependencyResolver` 都消费该无副作用解析入口。resolver 按 `targetType=RULE/MODEL/VARIABLE` 选择根节点，但使用同一个依赖遍历器、`visiting/visited` 环检测和 `refId + refType` 去重；`VariableSourceResolver.collectVariableDependencies` 作为同包共享入口，禁止在测试结构服务中复制 sourceConfig 解析。`RuleTestSchemaService` 使用统一 `setPathValue` 生成嵌套 JSON，类型样例规则为数值 `0`、布尔 `false`、数组 `[]`、对象 `{}`、其他空字符串。

- [ ] **Step 4: 运行绿灯及分析器回归**

Run: `mvn -pl rule-engine-server -Dtest=FieldDependencyResolverTest,RuleTestSchemaServiceTest,RuleFieldAnalyzerTest,VariableSourceResolverTest,RuleDefinitionServiceTest test`

Expected: PASS。

### Task 4: 执行参数类型绑定和模型入口复用

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/ExecutionParameterBinder.java`
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/ExecutionParameterBinderTest.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleExecuteService.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleModelService.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleExecuteServiceTest.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleModelServiceTest.java`

**Interfaces:**
- Produces: `bindRuleInputs(List<RuleDefinitionInputField>, Map<String,Object>)` 和 `bindModelInputs(List<RuleModelInputField>, Map<String,Object>)`。

- [ ] **Step 1: 写失败测试**

```java
@Test
public void bindsNumericStringsAtNestedPathsWithoutChangingStrings() {
    Map<String,Object> input = JSON.parseObject("{\"age\":\"22\",\"card\":{\"score\":\"350.5\"},\"idcard_no\":\"0012\"}");
    RuleDefinitionInputField age = inputField("age", "INTEGER");
    RuleDefinitionInputField score = inputField("card.score", "DECIMAL");
    RuleDefinitionInputField idcard = inputField("idcard_no", "STRING");
    Map<String,Object> bound = binder.bindRuleInputs(Arrays.asList(age, score, idcard), input);
    assertEquals(Integer.valueOf(22), bound.get("age"));
    assertEquals("0012", bound.get("idcard_no"));
}

private RuleDefinitionInputField inputField(String path, String type) {
    RuleDefinitionInputField field = new RuleDefinitionInputField();
    field.setScriptName(path);
    field.setFieldType(type);
    return field;
}
```

- [ ] **Step 2: 运行红灯**

Run: `mvn -pl rule-engine-server -Dtest=ExecutionParameterBinderTest test`

Expected: FAIL，因为绑定器尚不存在。

- [ ] **Step 3: 最小实现并接入**

深拷贝 Map/List 容器；按完整路径读写；支持 INTEGER/INT、LONG、NUMBER/DOUBLE/FLOAT/DECIMAL、BOOLEAN、ARRAY/LIST、OBJECT/MAP；非法值抛出包含字段路径、期望类型和实际类型的 `IllegalArgumentException`。规则执行在 `VariableSourceResolver.resolve` 前绑定，模型执行在构造模型输入前绑定。

- [ ] **Step 4: 运行绿灯和执行回归**

Run: `mvn -pl rule-engine-server -Dtest=ExecutionParameterBinderTest,RuleExecuteServiceTest,RuleModelServiceTest,RuleExperimentServiceTest test`

Expected: PASS，JCZR 的 `age:"22"` 可作为整数比较。

### Task 5: 计算变量同步到嵌套规则执行帧

**Files:**
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleRuntimeInvoker.java`
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleRuntimeInvokerTest.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleExecuteServiceTest.java`

**Interfaces:**
- Consumes: Task 1 生成的 `setRuntimeValue(path, value)`。
- Produces: QLExpress 函数 `setRuntimeValue(String,Object)`，写入请求级当前执行上下文；嵌套规则先绑定自身字段再解析派生变量。

- [ ] **Step 1: 写失败测试**

```java
invoker.enter("JCLTest", 1L, "P", context);
invoker.setRuntimeValue("age", 22);
assertEquals(Integer.valueOf(22), context.get("age"));
invoker.setRuntimeValue("result.decision", "PASS");
assertEquals("PASS", ((Map<?,?>) context.get("result")).get("decision"));
```

另用伪发布规则断言 `executeRule("JCZR")` 接收同步后的 `age`。

- [ ] **Step 2: 运行红灯**

Run: `mvn -pl rule-engine-server -Dtest=RuleRuntimeInvokerTest test`

Expected: FAIL，当前未注册和实现同步函数。

- [ ] **Step 3: 最小实现**

注册 `setRuntimeValue`，`enter` 总是使用可变请求级 Map；嵌套执行使用 `ExecutionParameterBinder` 按子规则输入字段绑定 `previousContext`，错误消息保留子规则编码和根因。

- [ ] **Step 4: 运行绿灯**

Run: `mvn -pl rule-engine-server -Dtest=RuleRuntimeInvokerTest,RuleExecuteServiceTest test`

Expected: PASS。

### Task 6: 前端统一引用目录、结果归一化和测试结构客户端

**Files:**
- Create: `rule-engine-builder-ui/src/utils/referenceCatalog.js`
- Create: `rule-engine-builder-ui/src/utils/testResult.js`
- Create: `rule-engine-builder-ui/tests/unit/utils/referenceCatalog.spec.js`
- Create: `rule-engine-builder-ui/tests/unit/utils/testResult.spec.js`
- Modify: `rule-engine-builder-ui/src/utils/varDisplay.js`
- Modify: `rule-engine-builder-ui/src/mixins/varPickerMixin.js`
- Modify: `rule-engine-builder-ui/src/api/definition.js`

**Interfaces:**
- Produces: `buildReferenceCatalog(variables, objectTree, models)`；`normalizeTestResult(response, targetType)`；`getRuleTestSchema(data)`。

- [ ] **Step 1: 写失败测试**

```js
expect(catalog.object[0].varLabel).toBe('银行卡信息/银行卡号 bankcard.bank_card_no')
expect(normalizeTestResult({ success: true, result: false }, 'RULE')).toMatchObject({ hasOutput: true, output: false })
expect(normalizeTestResult({ success: true, outputs: {} }, 'MODEL')).toMatchObject({ hasOutput: true, output: {} })
```

- [ ] **Step 2: 运行红灯**

Run: `cd rule-engine-builder-ui; npm test -- --runInBand tests/unit/utils/referenceCatalog.spec.js tests/unit/utils/testResult.spec.js`

Expected: FAIL，因为共享工具尚不存在。

- [ ] **Step 3: 最小实现并让 mixin 复用目录**

目录条目统一包含 `id/refType/refCode/refLabel/varType/sourceType/objectCode/objectLabel/varObj`；对象字段 code 必须是对象脚本名加字段相对路径。结果归一化使用 `hasOwnProperty` 判断结果存在，禁止真值判断。

- [ ] **Step 4: 运行绿灯**

Run: `cd rule-engine-builder-ui; npm test -- --runInBand tests/unit/utils/referenceCatalog.spec.js tests/unit/utils/testResult.spec.js tests/unit/varPickerMixin.spec.js tests/unit/utils/varDisplay.spec.js`

Expected: PASS。

### Task 7: 规则/模型详情和设计器统一消费测试结构

**Files:**
- Modify: `rule-engine-builder-ui/src/components/flow/ActionBlockEditor.vue`
- Modify: `rule-engine-builder-ui/src/components/common/DesignerTestDialog.vue`
- Modify: `rule-engine-builder-ui/src/utils/actionDataCodegen.js`
- Modify: `rule-engine-builder-ui/src/views/rule/RuleDetail.vue`
- Modify: `rule-engine-builder-ui/src/views/model/ModelDetail.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/DecisionTable.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/DecisionTree.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/DecisionFlow.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/RuleSet.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/CrossTable.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/Scorecard.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/AdvancedCrossTable.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/AdvancedScorecard.vue`
- Modify: `rule-engine-builder-ui/src/views/designer/ScriptEditor.vue`
- Modify: `rule-engine-builder-ui/tests/unit/views/ruleDetail.spec.js`
- Modify: `rule-engine-builder-ui/tests/unit/views/modelDetail.spec.js`
- Create: `rule-engine-builder-ui/tests/unit/components/designerTestDialog.spec.js`
- Modify: `rule-engine-builder-ui/tests/unit/components/actionBlockEditor.spec.js`
- Modify: `rule-engine-builder-ui/tests/unit/utils/actionDataCodegen.spec.js`
- Modify: `rule-engine-builder-ui/tests/unit/views/advancedCrossTable.spec.js`

**Interfaces:**
- Consumes: `POST /api/rule/test-schema` 和共享结果归一化。
- Produces: 所有设计器传 `definitionId/projectId/modelType/modelJson` 给同一个测试弹窗；详情页使用统一样例参数和输出视图。

- [ ] **Step 1: 写失败测试**

断言 JCLTest 请求当前 FLOW JSON 后采用服务端 `sampleParams.score_f1_fields`，且不出现 `DAY`；断言 RuleDetail 对 `{result:0}` 和 `{result:false}` 显示输出；断言 RuleDetail/ModelDetail 对数据对象条目显示完整名称。断言动作字段槽位选择后保存 `_targetVarId`，函数字段参数选择后保存 `_argRefs[index]`，手输参数后清空引用并保持字面量。

- [ ] **Step 2: 运行红灯**

Run: `cd rule-engine-builder-ui; npm test -- --runInBand tests/unit/components/designerTestDialog.spec.js tests/unit/components/actionBlockEditor.spec.js tests/unit/utils/actionDataCodegen.spec.js tests/unit/views/ruleDetail.spec.js tests/unit/views/modelDetail.spec.js tests/unit/views/advancedCrossTable.spec.js`

Expected: FAIL，页面当前仍使用各自模板和 `outputs` 真值判断。

- [ ] **Step 3: 最小迁移**

`DesignerTestDialog` 打开时调用测试结构 API；失败时才使用传入模板并显示诊断。九类设计器只负责传当前模型，不再发现依赖。`ActionBlockEditor` 继续以字段级 ID 和 `_argRefs` 保存选择器引用，手输函数参数清空引用；前端脚本预览与后端字面量规则一致。RuleDetail/ModelDetail 通过 `normalizeTestResult` 展示 `output`，并用 `buildReferenceCatalog` 构造绑定选项。项目接口继续消费规则持久化字段，变量测试使用 VARIABLE 测试结构，分流实验通过统一规则执行入口，因此不增加平行解析逻辑。

- [ ] **Step 4: 运行绿灯和前端全量测试**

Run: `cd rule-engine-builder-ui; npm test -- --runInBand`

Expected: 全部 PASS。

### Task 8: 全覆盖回归、启动和浏览器验收

**Files:**
- Modify as required by failures: only files already in Tasks 1-7.

**Interfaces:**
- Produces: 可复现的构建、测试、运行日志和真实 UI 验收证据。

- [ ] **Step 1: 后端完整构建**

Run: `mvn clean install -DskipTests`

Expected: BUILD SUCCESS。

- [ ] **Step 2: 后端完整测试**

Run: `mvn test`

Expected: BUILD SUCCESS，0 failures，0 errors。

- [ ] **Step 3: 前端开发启动和生产构建**

Run: `cd rule-engine-builder-ui; npm run dev`

Expected: dev server 编译成功且无错误；验证后停止进程。

Run: `cd rule-engine-builder-ui; npm run build`

Expected: build 成功。

- [ ] **Step 4: 后端启动烟测**

Run: `cd rule-engine-server; mvn spring-boot:run`

Expected: 应用启动；登录、健康接口、`/api/rule/test-schema`、规则执行接口可用；验证后停止进程。

- [ ] **Step 5: 真实浏览器业务验收**

按 UI 完成：JCZR 字符串年龄执行、JCLTest 生成 `score_f1_fields` 且无 `DAY` 并嵌套执行、SXED 无 `score_f1.score`；逐一验证九类设计器“打开、编辑、生成 JSON、执行、查看输出”；检查规则/模型详情完整对象字段名称和 `0/false/null/{}/[]` 输出。

- [ ] **Step 6: 最终差异审查**

Run: `git diff --check; git status --short; git diff --stat`

Expected: 无空白错误；所有改动都能映射到设计规格和本计划任务。
