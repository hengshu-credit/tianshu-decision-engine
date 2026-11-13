# 模型字段展示与输出函数转换设计

## 目标

统一模型详情中的字段语义和显示格式：模型输入只保留默认值；数据对象字段统一显示为“变量名称 脚本路径”；模型输出使用项目函数完成转换，并允许用户显式配置全部函数参数及查看完整公式。

## 变更边界

- 只删除 `rule_model_input_field` 的缺失值能力，不删除规则定义输入字段 `rule_definition_input_field` 的同名能力。
- 删除模型输出旧的 `transformType` 枚举能力，以新的函数 Operand 完全替代，不迁移旧值，也不保留兼容读取。
- 保留当前分支已经引入的模型输入 `sourceOperand`、`defaultOperand` 和模型输出 `targetOperand`。
- 不增加新的前端或后端依赖。

## 模型输入默认值

模型详情输入字段表删除“缺失值”列，只展示“默认值”。默认值编辑继续使用统一 Operand，可显式选择阈值、路径、变量、数据对象字段或函数。

默认值标题旁展示提示：来源为空或未取到值时使用默认值；未配置则按空值传入模型。

后端删除 `RuleModelInputField.missingValue`、`rule_model_input_field.missing_value` 和 `RuleModelService.applyMissingValues`。模型执行时由 `defaultOperand`（以及同步保存的标量 `defaultValue`）提供唯一默认值逻辑。

## 数据对象字段显示

统一引用目录中的数据对象字段标签只使用字段的变量名称，编码使用完整脚本路径：

```text
身份证号 customer.idCard
```

不再把数据对象名称拼进变量名称。选择器、模型详情和规则详情等所有复用统一引用目录或 Operand 展示组件的页面都采用相同格式。引用仍持久化数据对象字段 ID 和 `refType=DATA_OBJECT`，不通过显示名称关联。

## 模型输出转换配置

`RuleModelOutputField` 新增 `transformOperand`，数据库新增 `rule_model_output_field.transform_operand JSON`。该字段只接受顶层 `FUNCTION` Operand，结构包含：

```json
{
  "kind": "FUNCTION",
  "functionId": 123,
  "functionCode": "scoreByProbability",
  "label": "概率转评分",
  "valueType": "NUMBER",
  "args": [
    {
      "kind": "REFERENCE",
      "refId": 456,
      "refType": "MODEL_OUTPUT",
      "code": "risk_model.probability",
      "label": "概率",
      "valueType": "DOUBLE"
    },
    { "kind": "LITERAL", "value": "600", "valueType": "NUMBER" }
  ]
}
```

函数必须通过 `functionId` 关联 `rule_function.id`。`functionCode` 和 `label` 只用于公式展示与快照，不作为关联依据。保存和执行时以 ID 查询当前函数定义及编码。

旧的 `RuleModelOutputField.transformType` 和 `rule_model_output_field.transform_type` 直接删除，不迁移 `NONE/RENAME/SCALE/OHE` 数据。

## 前端交互

模型输出字段进入编辑状态后，“转换方法”单元格包含：

1. 可清空、可搜索的函数选择框，仅展示当前项目可用的项目级和全局函数。
2. 按函数 `paramsJson` 顺序生成参数编辑框；每个参数必须由用户显式选择，不自动注入当前模型输出。
3. 参数编辑框使用统一 Operand 选择器，支持阈值、路径、变量、数据对象字段和模型输出；不支持在参数内继续选择函数，避免嵌套函数增加配置复杂度。
4. 单元格底部实时展示完整公式，例如 `scoreByProbability(risk_model.probability, 600, 50, "HIGH_GOOD")`。
5. 非编辑状态展示同一完整公式；未配置时显示 `-`。

用户若要转换当前输出，必须在参数中显式选择当前模型输出字段。当前模型的原始输出字段会加入可选引用目录，并保留输出字段 ID。所有输出转换均以完整的原始模型输出快照作为参数上下文，避免多个输出字段之间因执行顺序产生差异。

## 运行时数据流

1. 执行模型得到完整原始输出 `rawOutputs`。
2. 构建转换上下文：已解析的引擎参数、模型输入参数，以及 `{modelCode: rawOutputs}`。
3. 对每个配置了 `transformOperand` 的输出字段，逐个解析用户选择的参数 Operand。
4. 按 `functionId` 查询启用函数，校验作用域和参数数量，使用现有 QLExpress/函数注册链路执行函数。
5. 将转换结果写回该输出字段；未配置转换函数的字段保留原始值。
6. `targetOperand` 写入的是转换后的值；模型测试接口返回的 `outputs` 也展示转换后的结果。

模型依赖分析同时收集转换参数中的路径引用，确保执行模型前已解析转换所需的变量和模型输出依赖。

## 校验和错误处理

- 保存时，顶层 Operand 不是 `FUNCTION`、缺少 `functionId`、函数不存在、函数不在当前模型作用域、参数数量与 `paramsJson` 不一致或存在空参数时拒绝保存。
- 发布时重新执行同样的完整性校验，避免绕过前端写入无效配置。
- 执行时函数不存在、已停用、参数解析失败或函数执行失败时，模型执行明确失败并返回包含模型字段名和函数名称的错误信息，不静默使用原始值。
- 未配置 `transformOperand` 合法，表示不转换。

## 数据库结构

初始化 Schema：

```sql
ALTER TABLE `rule_model_input_field`
  DROP COLUMN `missing_value`;

ALTER TABLE `rule_model_output_field`
  DROP COLUMN `transform_type`,
  ADD COLUMN `transform_operand` JSON DEFAULT NULL COMMENT '模型输出函数转换 Operand' AFTER `feature_name`;
```

`CREATE TABLE` 定义直接移除上述旧列并加入新列；升级段使用 `information_schema` 守卫，保证重复执行初始化脚本时不会因列已删除或已新增而失败。

## 测试与验收

- 前端先添加失败测试，覆盖缺失值列消失、默认值提示、数据对象“变量名称 脚本路径”、函数选择生成参数、全部参数显式选择、公式展示、保存和取消恢复。
- 后端先添加失败测试，覆盖字段更新持久化 `transformOperand`、旧缺失值逻辑删除、函数 ID 校验、参数数量校验、原始输出引用、函数执行、转换结果写入目标字段和转换依赖收集。
- 运行前端目标测试及全量 `npm test`；执行 `npm run dev` 并通过浏览器完整创建/编辑/保存/测试模型字段流程。
- 运行 `mvn clean install -DskipTests`、启动 `rule-engine-server`、执行目标测试及全量 `mvn test`。
- 最终逐项审查需求、数据库 Schema、前后端数据流和 Git diff，再合并当前分支与 `master`，提交并推送远程 `master`。
