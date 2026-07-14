# 统一表达式、名单函数与全量内置方法实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**目标：** 将全部可读字段选择位置统一升级为递归表达式 AST，补齐服务端名单查询、四种名单组合模式、类型化条件操作符、URule 安全等价内置方法，并通过真实浏览器完成需求 8 额度公式的配置、保存、回显和执行。

**架构：** 继续以 `Operand` 作为唯一表达式载体，在现有四种叶节点上增加 `OPERATION`、`ACCESS`、`CAST`、`ARRAY`、`LIST_QUERY`。前端所有入口复用 `OperandPicker + ExpressionEditorDialog`；后端规则编译统一走 `OperandCompiler`，Java 场景统一走 `OperandValueResolver`，依赖统一走递归访问器。新 AST 和名单配置是唯一格式，不实现旧字段兼容。

**技术栈：** Vue 2.6、Element UI 2.15、Jest/Vue Test Utils、Spring Boot 2.3、Java 8、QLExpress 4、FastJSON、JUnit 4、Mockito。

---

## Task 1：建立前端递归表达式 AST 与契约测试

**文件：**
- 修改：`rule-engine-builder-ui/src/utils/operand.js`
- 新建：`rule-engine-builder-ui/src/constants/expressionContexts.js`
- 修改：`rule-engine-builder-ui/tests/unit/utils/operand.spec.js`
- 新建：`rule-engine-builder-ui/tests/unit/constants/expressionContexts.spec.js`

**步骤 1：先写失败测试**

覆盖 `OPERATION` 嵌套函数、`ACCESS(KEY/INDEX)`、`CAST`、`ARRAY`、`LIST_QUERY`、深克隆、类型推导、节点校验、递归引用同步，以及 `READ_EXPRESSION`/`WRITE_TARGET` 上下文约束。额度公式的核心断言：

```js
expect(compileOperand(createOperationOperand('*', [
  createFunctionOperand({ funcCode: 'numCeil' }, [
    createOperationOperand('/', [amount, createLiteralOperand('500', 'NUMBER')])
  ]),
  createLiteralOperand('500', 'NUMBER')
]))).toBe('(numCeil((amount / 500)) * 500)')
```

**步骤 2：运行测试确认红灯**

```powershell
cd rule-engine-builder-ui
npm test -- --runInBand tests/unit/utils/operand.spec.js tests/unit/constants/expressionContexts.spec.js
```

**步骤 3：实现最小递归 AST**

统一使用 `operandChildren(node)` 遍历子节点；编译、显示、引用收集、ID 同步和校验均复用该入口。表达式编译始终使用必要括号，受管引用只依赖 `refId + refType`。写目标只允许 `PATH/REFERENCE`。

**步骤 4：跑绿灯并提交**

```powershell
npm test -- --runInBand tests/unit/utils/operand.spec.js tests/unit/constants/expressionContexts.spec.js
git add src/utils/operand.js src/constants/expressionContexts.js tests/unit/utils/operand.spec.js tests/unit/constants/expressionContexts.spec.js
git commit -m "feat: add recursive expression ast"
```

## Task 2：扩展核心编译器、Java 运行时解析器和依赖访问器

**文件：**
- 修改：`rule-engine-core/src/main/java/com/hengshucredit/rule/core/compiler/OperandCompiler.java`
- 修改：`rule-engine-core/src/test/java/com/hengshucredit/rule/core/compiler/OperandCompilerTest.java`
- 修改：`rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/OperandValueResolver.java`
- 修改：`rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/OperandDependencyCollector.java`
- 修改：`rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/OperandValueResolverTest.java`

**步骤 1：写失败测试**

用完整额度公式 AST 断言编译结果和确定输入下的运行结果；分别覆盖 Key、Index、Cast、Array、未知 kind、缺少必填子节点、受管引用缺少 ID。

**步骤 2：确认红灯**

```powershell
mvn -pl rule-engine-core -Dtest=OperandCompilerTest test
mvn -pl rule-engine-server -am -Dtest=OperandValueResolverTest test
```

**步骤 3：实现递归编译和执行**

`OPERATION` 支持 `+ - * / % && || !`；`ACCESS` 编译到 `objGet/arrGet`；`CAST` 编译到安全转换函数；`ARRAY` 递归编译元素；`LIST_QUERY` 标记仅服务端。Java 运行时保持相同语义且不使用字符串 eval，依赖访问器遍历所有子节点。

**步骤 4：跑绿灯并提交**

```powershell
mvn -pl rule-engine-core -Dtest=OperandCompilerTest test
mvn -pl rule-engine-server -am -Dtest=OperandValueResolverTest test
git add rule-engine-core/src/main/java/com/hengshucredit/rule/core/compiler/OperandCompiler.java rule-engine-core/src/test/java/com/hengshucredit/rule/core/compiler/OperandCompilerTest.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/OperandValueResolver.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/OperandDependencyCollector.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/OperandValueResolverTest.java
git commit -m "feat: execute recursive expressions"
```

## Task 3：补齐 URule 安全等价内置方法和当前日期时间

**文件：**
- 修改：`rule-engine-core/src/main/java/com/hengshucredit/rule/core/function/DecisionBuiltinFunctions.java`
- 修改：`rule-engine-core/src/main/java/com/hengshucredit/rule/core/function/AggregateBuiltinFunctions.java`
- 修改：`rule-engine-core/src/main/java/com/hengshucredit/rule/core/function/AggregateBuiltinFunctionRegistry.java`
- 修改：`rule-engine-core/src/test/java/com/hengshucredit/rule/core/function/DecisionBuiltinFunctionsTest.java`
- 修改：`rule-engine-core/src/test/java/com/hengshucredit/rule/core/function/AggregateBuiltinFunctionsTest.java`
- 修改：`rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/BuiltinFunctionCatalog.java`
- 修改：`rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/BuiltinFunctionCatalogTest.java`
- 修改：`rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/FunctionRegistrarTest.java`

**步骤 1：写目录契约和真实执行失败测试**

逐个锁定设计稿中的日期、字符串、数学、List、Map、类型转换和安全对象方法；验证目录编码、参数数量、Java 方法签名、返回类型和真实 QLExpress 结果，并断言不存在任意类名反射构造函数。

**步骤 2：确认红灯**

```powershell
mvn -pl rule-engine-core -Dtest=DecisionBuiltinFunctionsTest,AggregateBuiltinFunctionsTest test
mvn -pl rule-engine-server -am -Dtest=BuiltinFunctionCatalogTest,FunctionRegistrarTest test
```

**步骤 3：实现、注册、补齐中文元数据**

新增 `currentDate/currentDateTime`、细粒度日期方法、字符串截取/大小写、数学三角/对数/取整、集合增删排序取列、Map 增删查及 `newMap/newList/newLike`。修改型方法返回新结构，避免隐式副作用。

**步骤 4：跑绿灯并提交**

```powershell
mvn -pl rule-engine-core -Dtest=DecisionBuiltinFunctionsTest,AggregateBuiltinFunctionsTest test
mvn -pl rule-engine-server -am -Dtest=BuiltinFunctionCatalogTest,FunctionRegistrarTest test
git add rule-engine-core/src rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/BuiltinFunctionCatalog.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/BuiltinFunctionCatalogTest.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/FunctionRegistrarTest.java
git commit -m "feat: expand builtin expression functions"
```

## Task 4：实现服务端名单命中矩阵、名单函数和名单变量唯一结构

**文件：**
- 新建：`rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/ListMatchMatrix.java`
- 新建：`rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/ListMatchMatrixTest.java`
- 新建：`rule-engine-server/src/main/java/com/hengshucredit/rule/server/functions/RuleListFunctions.java`
- 新建：`rule-engine-server/src/test/java/com/hengshucredit/rule/server/functions/RuleListFunctionsTest.java`
- 修改：`rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/VariableSourceResolver.java`
- 修改：`rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/VariableSourceResolverTest.java`
- 修改：`rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/FunctionRegistrar.java`

**步骤 1：写四模式真值矩阵失败测试**

覆盖 `ANY_FIELD_ANY_LIST`、`ALL_FIELDS_ANY_LIST`、`ANY_FIELD_ALL_LISTS`、`ALL_FIELDS_ALL_LISTS`，以及空字段、空名单、单字段/单名单退化、`BOOLEAN/NUMBER` 返回。

**步骤 2：写新名单配置测试并确认红灯**

只接受 `listIds + queryOperands + combinationMode + matchMode + itemTypes + returnMode`；`listId/queryField/queryPath/field/hitValue/missValue` 必须明确报错。

```powershell
mvn -pl rule-engine-server -am -Dtest=ListMatchMatrixTest,RuleListFunctionsTest,VariableSourceResolverTest test
```

**步骤 3：实现共享矩阵、Spring Bean 函数和变量解析**

名单函数与 `VariableSourceResolver` 都复用 `ListMatchMatrix`；查询字段由 `OperandValueResolver` 递归求值；函数只在服务端注册。

**步骤 4：跑绿灯并提交**

```powershell
mvn -pl rule-engine-server -am -Dtest=ListMatchMatrixTest,RuleListFunctionsTest,VariableSourceResolverTest,FunctionRegistrarTest test
git add rule-engine-server/src
git commit -m "feat: add server list match expressions"
```

## Task 5：扩展类型化条件操作符并保证前后端同码同义

**文件：**
- 修改：`rule-engine-builder-ui/src/constants/conditionOperators.js`
- 修改：`rule-engine-builder-ui/tests/unit/constants/conditionOperators.spec.js`
- 修改：`rule-engine-core/src/main/java/com/hengshucredit/rule/core/compiler/ConditionExpressionBuilder.java`
- 修改：`rule-engine-core/src/test/java/com/hengshucredit/rule/core/compiler/ConditionCompilerTest.java`
- 修改：`rule-engine-core/src/main/java/com/hengshucredit/rule/core/function/AggregateBuiltinFunctions.java`

**步骤 1：写失败的成对契约测试**

覆盖正则、候选数组、Map key/value、List 元素包含/前缀/后缀、集合长度、空集合和名单内/外；每个操作符断言适用类型、右值要求、右值上下文和编译结果。

**步骤 2：确认红灯**

```powershell
cd rule-engine-builder-ui
npm test -- --runInBand tests/unit/constants/conditionOperators.spec.js
cd ..
mvn -pl rule-engine-core -Dtest=ConditionCompilerTest test
```

**步骤 3：集中实现元数据、builder 和运行 helper**

组件不硬编码操作符。名单操作符右侧使用 `LIST_QUERY`，普通右值使用完整表达式，无右值操作符清空右侧 AST。

**步骤 4：跑绿灯并提交**

```powershell
cd rule-engine-builder-ui
npm test -- --runInBand tests/unit/constants/conditionOperators.spec.js
cd ..
mvn -pl rule-engine-core -Dtest=ConditionCompilerTest,AggregateBuiltinFunctionsTest test
git add rule-engine-builder-ui/src/constants/conditionOperators.js rule-engine-builder-ui/tests/unit/constants/conditionOperators.spec.js rule-engine-core/src
git commit -m "feat: expand typed condition operators"
```

## Task 6：实现全屏三栏表达式编辑器

**文件：**
- 新建：`rule-engine-builder-ui/src/components/expression/ExpressionEditorDialog.vue`
- 新建：`rule-engine-builder-ui/src/components/expression/ExpressionPalette.vue`
- 新建：`rule-engine-builder-ui/src/components/expression/ExpressionCanvas.vue`
- 新建：`rule-engine-builder-ui/src/components/expression/ExpressionNodeInspector.vue`
- 新建：`rule-engine-builder-ui/src/components/expression/expressionTree.js`
- 新建：`rule-engine-builder-ui/tests/unit/components/expressionEditorDialog.spec.js`
- 新建：`rule-engine-builder-ui/tests/unit/components/expressionNodeInspector.spec.js`
- 新建：`rule-engine-builder-ui/tests/unit/utils/expressionTree.spec.js`

**步骤 1：写失败交互测试**

覆盖左侧点击插入当前槽、函数按元数据生成参数、可变参数增删、节点替换/删除、Key/Index、Cast、撤销/重做、应用/取消、非法节点阻止应用和实时公式预览。

**步骤 2：确认红灯**

```powershell
cd rule-engine-builder-ui
npm test -- --runInBand tests/unit/components/expressionEditorDialog.spec.js tests/unit/components/expressionNodeInspector.spec.js tests/unit/utils/expressionTree.spec.js
```

**步骤 3：实现三栏编辑器**

对话框全屏；左栏提供字段、阈值、函数、运算符、访问器和类型转换；中栏展示递归树与参数槽；右栏按当前节点配置。草稿深克隆，取消不改父值，应用一次性发出完整 AST。

**步骤 4：跑绿灯并提交**

```powershell
npm test -- --runInBand tests/unit/components/expressionEditorDialog.spec.js tests/unit/components/expressionNodeInspector.spec.js tests/unit/utils/expressionTree.spec.js
git add src/components/expression tests/unit/components/expressionEditorDialog.spec.js tests/unit/components/expressionNodeInspector.spec.js tests/unit/utils/expressionTree.spec.js
git commit -m "feat: add full screen expression editor"
```

## Task 7：接入 OperandPicker，修复函数参数、焦点、默认定位和 200 字段布局

**文件：**
- 修改：`rule-engine-builder-ui/src/components/common/OperandPicker.vue`
- 修改：`rule-engine-builder-ui/src/components/common/VarPicker.vue`
- 修改：`rule-engine-builder-ui/tests/unit/components/operandPicker.spec.js`
- 修改：`rule-engine-builder-ui/tests/unit/components/varPicker.spec.js`
- 修改：`rule-engine-builder-ui/src/styles/element-override.scss`

**步骤 1：写失败测试**

覆盖空值打开默认 `manual/LITERAL` 并聚焦；已有引用按 `refId + refType` 恢复分类、展开、分页和滚动；带参函数打开编辑器并选中首个参数；参数可删减；关闭前迁移焦点且只关闭当前 popover；200 字段时计数不换行、搜索前后左栏宽度不变。

**步骤 2：确认红灯**

```powershell
cd rule-engine-builder-ui
npm test -- --runInBand tests/unit/components/operandPicker.spec.js tests/unit/components/varPicker.spec.js
```

**步骤 3：实现紧凑入口、焦点生命周期和稳定布局**

`OperandPicker` 显示公式摘要和 `fx`；简单叶节点可快速选择，函数或复杂节点进入全屏编辑器。移除全局隐藏 popover，关闭前迁移焦点，隐藏面板同步 `inert`。分类列使用固定全集宽度和 `white-space: nowrap`。

**步骤 4：跑绿灯并提交**

```powershell
npm test -- --runInBand tests/unit/components/operandPicker.spec.js tests/unit/components/varPicker.spec.js
git add src/components/common/OperandPicker.vue src/components/common/VarPicker.vue src/styles/element-override.scss tests/unit/components/operandPicker.spec.js tests/unit/components/varPicker.spec.js
git commit -m "fix: unify expression picker interactions"
```

## Task 8：升级名单变量前端为多名单、多表达式和四模式

**文件：**
- 修改：`rule-engine-builder-ui/src/views/variable/VariableList.vue`
- 修改：`rule-engine-builder-ui/tests/unit/views/variableList.spec.js`
- 新建：`rule-engine-builder-ui/src/constants/listMatchModes.js`
- 新建：`rule-engine-builder-ui/tests/unit/constants/listMatchModes.spec.js`

**步骤 1：写失败测试**

断言多选 `listIds`、可增删 `queryOperands`、四个组合模式、动态说明、帮助提示、布尔/数字返回、字段选择器和唯一 payload；旧字段不得进入 payload。

**步骤 2：确认红灯**

```powershell
cd rule-engine-builder-ui
npm test -- --runInBand tests/unit/views/variableList.spec.js tests/unit/constants/listMatchModes.spec.js
```

**步骤 3：实现简洁 UI、详情和在线测试**

模式下拉只显示短标签，帮助图标给出定义，下方显示一句动态说明。查询表达式行复用 `OperandPicker`，详情和依赖递归读取 `queryOperands`。

**步骤 4：跑绿灯并提交**

```powershell
npm test -- --runInBand tests/unit/views/variableList.spec.js tests/unit/constants/listMatchModes.spec.js
git add src/views/variable/VariableList.vue src/constants/listMatchModes.js tests/unit/views/variableList.spec.js tests/unit/constants/listMatchModes.spec.js
git commit -m "feat: configure multi field list variables"
```

## Task 9：接入条件、动作、九种设计器和 80% 属性面板

**文件：**
- 修改：`rule-engine-builder-ui/src/components/decision/ConditionGroupEditor.vue`
- 修改：`rule-engine-builder-ui/src/components/flow/ActionBlockEditor.vue`
- 修改：`rule-engine-builder-ui/src/components/flow/ActionAssignmentRow.vue`
- 修改：`rule-engine-builder-ui/src/views/designer/*.vue`
- 新建：`rule-engine-builder-ui/src/utils/designerPanelWidth.js`
- 新建：`rule-engine-builder-ui/tests/unit/utils/designerPanelWidth.spec.js`
- 修改：相关 `tests/unit/views/*.spec.js` 与 `tests/unit/components/conditionGroupEditor.spec.js`

**步骤 1：先写各入口失败测试**

每个设计器至少断言一处完整表达式可保存并回显；条件左右值使用 AST；动作写目标只允许可写引用、动作值允许完整 AST；foreach 支持 break。面板测试断言最大宽度为实际可用宽度 80%。

**步骤 2：确认红灯**

```powershell
cd rule-engine-builder-ui
npm test -- --runInBand tests/unit/components/conditionGroupEditor.spec.js tests/unit/views/decisionTable.spec.js tests/unit/views/advancedCrossTable.spec.js tests/unit/views/advancedScorecard.spec.js tests/unit/utils/designerPanelWidth.spec.js
```

**步骤 3：统一入口并实现共享面板宽度**

所有可读值使用 `READ_EXPRESSION`，写目标使用 `WRITE_TARGET`。`ActionBlockEditor` 的函数调用也使用同一 `FUNCTION` AST。DecisionTree/DecisionFlow 从容器读取宽度并夹紧到 `[320, available * 0.8]`。

**步骤 4：跑绿灯并提交**

```powershell
npm test -- --runInBand tests/unit/components/conditionGroupEditor.spec.js tests/unit/views/decisionTable.spec.js tests/unit/views/advancedCrossTable.spec.js tests/unit/views/advancedScorecard.spec.js tests/unit/utils/designerPanelWidth.spec.js tests/unit/views/flowDesignerStyle.spec.js
git add src/components/decision src/components/flow src/views/designer src/utils/designerPanelWidth.js tests/unit
git commit -m "feat: use expressions across rule designers"
```

## Task 10：接入模型、分流、API/DB/名单变量出入参解析

**文件：**
- 修改：`rule-engine-builder-ui/src/views/model/ModelDetail.vue`
- 修改：`rule-engine-builder-ui/tests/unit/views/modelDetail.spec.js`
- 修改：`rule-engine-builder-ui/src/views/experiment/ExperimentDetail.vue`
- 修改：`rule-engine-builder-ui/src/views/experiment/ExperimentList.vue`
- 修改：`rule-engine-builder-ui/tests/unit/views/experimentDetail.spec.js`
- 修改：`rule-engine-builder-ui/src/views/variable/VariableList.vue`
- 修改：`rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/VariableSourceResolver.java`
- 修改：`rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleExperimentService.java`
- 修改：对应服务测试

**步骤 1：写失败测试并确认红灯**

模型输入来源、默认值、输入/输出转换、分流条件左右值、API 参数映射、DB 参数和结果提取都使用完整 AST；输出目标保持 `WRITE_TARGET`，依赖递归发现全部受管字段 ID。

```powershell
cd rule-engine-builder-ui
npm test -- --runInBand tests/unit/views/modelDetail.spec.js tests/unit/views/experimentDetail.spec.js tests/unit/views/variableList.spec.js
cd ..
mvn -pl rule-engine-server -am -Dtest=RuleExperimentServiceTest,VariableSourceResolverTest test
```

**步骤 2：接入共享编辑器与解析器**

前端只声明上下文；后端都通过 `OperandValueResolver` 求值，输出转换后再写入目标。结构化配置不再依赖字符串扫描。

**步骤 3：跑绿灯并提交**

```powershell
cd rule-engine-builder-ui
npm test -- --runInBand tests/unit/views/modelDetail.spec.js tests/unit/views/experimentDetail.spec.js tests/unit/views/variableList.spec.js
cd ..
mvn -pl rule-engine-server -am -Dtest=RuleExperimentServiceTest,VariableSourceResolverTest,OperandValueResolverTest test
git add rule-engine-builder-ui/src/views rule-engine-builder-ui/tests/unit/views rule-engine-server/src/main/java/com/hengshucredit/rule/server/service rule-engine-server/src/test/java/com/hengshucredit/rule/server/service
git commit -m "feat: evaluate expressions across model inputs"
```

## Task 11：更新仓库样例并完成九种编译器回归

**文件：**
- 修改：`rule-engine-server/src/main/resources/sql/data-example.sql`
- 修改：`rule-engine-server/src/main/resources/sql/data-tianshu-example.sql`
- 修改：相关 core 编译器测试夹具
- 修改：`README.md`

**步骤 1：枚举旧字段**

```powershell
rg -n '"listId"|"queryField"|"queryPath"|"hitValue"|"missValue"' rule-engine-server rule-engine-core rule-engine-builder-ui --glob '!target/**' --glob '!node_modules/**'
```

**步骤 2：先让测试要求新结构，再更新样例和文档**

九种编译器分别增加复杂表达式或共享表达式回归；样例 SQL 只出现新名单结构。README 说明名单函数仅服务端、四模式和表达式画布。

**步骤 3：运行定向回归并提交**

```powershell
mvn -pl rule-engine-core test
cd rule-engine-builder-ui
npm test -- --runInBand tests/unit/views/designerTestSchemaContract.spec.js tests/unit/utils/decisionConditionTree.spec.js tests/unit/utils/conditionOperatorsTree.spec.js
cd ..
git add rule-engine-server/src/main/resources/sql rule-engine-core/src/test README.md rule-engine-builder-ui/tests
git commit -m "test: migrate examples to expression ast"
```

## Task 12：完整构建、服务启动、浏览器逐项验收和额度公式执行

**文件：**
- 新建：`docs/verification/2026-07-14-unified-expression-browser-checklist.md`
- 修改：仅修复验证发现且与需求直接相关的问题

**步骤 1：后端完整校验**

```powershell
mvn clean install -DskipTests
mvn test
```

启动 `rule-engine-server`，确认 8080 健康、登录、名单在线测试和规则测试可用，记录日志后停止本轮进程。

**步骤 2：前端完整校验**

```powershell
cd rule-engine-builder-ui
npm run dev
```

确认实际端口和编译无误；浏览器完成后停止本轮进程，再执行 `npm test`。

**步骤 3：真实浏览器逐项验收**

逐项操作名单变量四模式、名单函数、函数参数增删、200 字段布局、默认阈值和 ID 定位、类型化操作符、九种设计器、模型、分流、API/DB/名单变量、80% 面板宽度。全程检查 Console，不允许有 `Blocked aria-hidden`、Vue error 或未捕获异常。

**步骤 4：只通过 UI 配置并执行额度公式**

```text
CEIL(max(4200,min((min(max(月成功还款额,已使用额度),9000)*风险系数*0.3+风险额度*0.5),7000))/500)*500
```

在决策流动作值中用三栏画布逐个点击素材并配置参数；保存，重新打开确认公式和四个字段 ID；在规则测试 UI 输入固定值并核对预期额度。不得直接写 modelJson 跳过 UI。

**步骤 5：记录证据并最终检查**

```powershell
git diff --check
git status --short
```

记录页面、操作、输入、预期、实际结果和 Console 状态；调用 `superpowers:verification-before-completion` 后才能报告完成并提交验收记录。

