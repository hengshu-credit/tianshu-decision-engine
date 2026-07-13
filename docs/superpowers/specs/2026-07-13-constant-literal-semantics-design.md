# 决策引擎常量字面量语义设计

## 目标

统一变量管理、操作数选择器、规则编译和模型运行时的常量语义。常量仍通过 `refId + refType=CONSTANT` 持久化关联，但在 QLExpress 编译结果中直接替换为类型正确、可执行的值表达式；常量不得被请求参数覆盖。

常量列表中的值和脚本名称只展示，不允许在表格内失焦即保存，修改必须进入“编辑”对话框。

## 已确认问题

1. `RuleVariableService.buildRefScriptNameMap()` 当前把常量映射为 `scriptName`，`OperandCompiler` 因而输出未定义变量名，而不是常量值。
2. `OperandValueResolver` 当前把 `CONSTANT` 当普通路径读取；模型字段直接测试时无法稳定取得常量。
3. `VariableSourceResolver` 仅在上下文缺少同名键时补常量，调用方可以用请求参数覆盖常量。
4. 现有空字符串保存为 `""`，再按 STRING 引号编译后会得到包含两个引号字符的字符串，并非空字符串。
5. QLExpress 4.1.0 实测：`Infinity` 是未定义变量，`-Infinity` 报一元运算错误，空映射 `{}` 单独执行触发异常。
6. 常量列表中的 `scriptName` 和 `defaultValue` 当前均可直接编辑并在失焦时保存。

## 常量引用与数据流

选择常量时，前端 Operand 保存以下稳定引用及展示快照：

```json
{
  "kind": "REFERENCE",
  "refId": 10,
  "refType": "CONSTANT",
  "code": "EMPTY_STRING",
  "label": "空字符串",
  "valueType": "STRING",
  "constantValue": ""
}
```

- `refId + refType` 是唯一关联依据。
- `constantValue` 只用于界面显示，不参与后端可信编译；页面重新加载时按 ID 从当前常量元数据刷新。
- 后端编译必须按常量 ID 查询当前 `varType + defaultValue`，生成 QLExpress 表达式。
- 缺少 ID、引用不存在、常量停用或值不合法时编译失败，不允许静默回退到常量编码。
- 已发布脚本保持版本不可变；修改常量后，新值在下一次测试、编译或重新发布时进入脚本。

## 类型与 QLExpress 表达式

| 常量类型和值 | 编译结果 | 运行时 Java 值 |
|---|---|---|
| STRING 空串 | `''` | `""` |
| STRING 普通文本 | 转义后的单引号字符串 | `String` |
| 空值 `null` | `null` | `null` |
| LIST `[]` | `[]` | 空列表 |
| MAP/OBJECT `{}` | `jsonParse('{}')` | 空映射 |
| BOOLEAN `true/false` | `true` / `false` | `Boolean` |
| 整数或小数 | 原始规范数字文本 | 对应数值 |
| DOUBLE `Infinity` | `1.0 / 0.0` | `Double.POSITIVE_INFINITY` |
| DOUBLE `-Infinity` | `-1.0 / 0.0` | `Double.NEGATIVE_INFINITY` |

已用仓库锁定的 QLExpress 4.1.0 验证：整数形式 `1 / 0`、`-1 / 0` 会报 `Division by zero`，浮点形式可返回正负无穷，因此实现只能生成带小数点的表达式。

## 校验规则

常量在新增、编辑和导入时使用同一套校验及规范化规则：

1. STRING 原样保存，允许真正的空字符串；不自动增加或剥除引号。
2. 空值只接受规范文本 `null`，编译时优先识别为空值，不受 `OBJECT` 类型分支干扰。
3. BOOLEAN 只接受大小写不敏感的 `true` 或 `false`，保存为小写。
4. 数字类型接受合法十进制数；DOUBLE 额外接受 `Infinity`、`-Infinity`。
5. LIST/ARRAY 必须是合法 JSON 数组。
6. MAP/OBJECT 必须是合法 JSON 对象。
7. 非 STRING 常量不能是空白文本；非法值通过现有 API 错误响应返回明确原因。
8. 不对常量编码、名称或导入字段名做大小写、驼峰或下划线转换。

## 内置全局常量

内置集合保持小而稳定：

| 编码 | 名称 | 类型 | 保存值 |
|---|---|---|---|
| `NULL_VALUE` | 空值 | OBJECT | `null` |
| `EMPTY_STRING` | 空字符串 | STRING | 真正的空字符串 |
| `EMPTY_LIST` | 空列表 | LIST | `[]` |
| `EMPTY_MAP` | 空映射 | MAP | `{}` |
| `TRUE_VALUE` | 布尔真 | BOOLEAN | `true` |
| `FALSE_VALUE` | 布尔假 | BOOLEAN | `false` |
| `ZERO` | 零 | NUMBER | `0` |
| `ONE` | 一 | NUMBER | `1` |
| `NEGATIVE_ONE` | 负一 | NUMBER | `-1` |
| `POSITIVE_INFINITY` | 正无穷 | DOUBLE | `Infinity` |
| `NEGATIVE_INFINITY` | 负无穷 | DOUBLE | `-Infinity` |

“空对象”和“空映射”合并，仅保留 `EMPTY_MAP`，因为两者在本项目 JSON/QLExpress 数据语义中都是空键值映射。

初始化与示例 SQL 使用幂等 upsert，修正已有同编码系统常量并新增缺失项；不重写历史 `export_*.sql` 文件。

## 编译与运行时范围

1. 扩展 `VarContext`，分别保存普通引用脚本名和常量 ID 对应的可信表达式。
2. `OperandCompiler` 在 `refType=CONSTANT` 时只读取常量表达式；条件、动作、函数参数、规则集、决策树、决策流、交叉表和评分卡因共用 Operand 编译入口而获得一致行为。
3. 旧格式中带 `_varId/_refType` 的条件和动作仍按稳定 ID 解析；无 ID 的历史常量引用保留兼容诊断，但不允许新数据依赖名称关联。
4. 模型输入默认值、来源 Operand 和输出转换 Operand 在 Java 运行时按同一类型规则解析常量。
5. `VariableSourceResolver` 无条件用持久化常量覆盖请求中的同名键，确保固定值不可被调用方篡改。
6. 字段依赖分析继续排除常量，不将常量显示为规则测试入参。

## 前端交互

1. 常量列表把脚本名称和常量值改为只读文本，保留“编辑”“删除”操作。
2. 空字符串显示为 `''`，空值显示为 `null`，空列表/空映射显示为 `[]`/`{}`，正负无穷显示为 `Infinity`/`-Infinity`。
3. 编辑对话框按类型给出输入提示并允许 STRING 空字符串通过校验。
4. 操作数选择器选择常量后显示名称、编码和格式化值预览，但保存时仍保留稳定 ID。
5. 常量不能作为赋值目标；现有写操作数限制保持不变。

## 测试与验收

1. 后端先新增失败测试，覆盖所有内置常量的编译文本和 QLExpress 真实执行结果。
2. 覆盖非法常量保存、STRING 空串保存、导入校验和同名请求参数不可覆盖常量。
3. 覆盖模型输入、默认 Operand、函数参数和输出转换中的常量解析。
4. 前端先新增失败测试，覆盖常量表无可编辑输入框、编辑对话框修改、值格式化和选择器值预览。
5. 运行 `mvn clean install -DskipTests`、启动 `rule-engine-server`、运行 `mvn test`。
6. 启动前端 `npm run dev`，通过浏览器从变量管理进入常量列表，使用编辑操作修改常量，再在规则/模型界面选择常量并完成测试；确认编译脚本和值符合本设计，浏览器控制台无错误，随后停止开发进程。
7. 运行前端 `npm test`，并执行 `git diff --check`。

## 非目标

- 不新增第三方依赖。
- 不把常量名称或编码作为关联键。
- 不在常量编辑时自动修改或重新发布已发布规则。
- 不批量改写历史数据库导出文件。
