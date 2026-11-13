# 数据库结构与初始数据快照分离设计

## 背景

`schema.sql` 当前同时承担建表和内置常量写入。执行 `schema.sql` 后再导入
`export_202607161151.sql` 时，快照中的常量会与 schema 已写入的常量发生业务唯一键冲突。
第一次失败前已经自动提交的数据会留在数据库中，直接重试后又会在
`rule_data_object.uk_scope_project_object` 上报 `GLOBAL-0-contact` 重复。

原始 export 的所有 `INSERT` 都排除了自增主键 `id`。即使从空的
`rule_variable` 表导入不再报重复，实测得到的 `PASS=200`、
`hit_ruleset=202`、`EMPTY_MAP=205` 仍与规则 JSON 中保存的
`refId=204/206/209` 不一致，会形成不报错但运行语义错误的数据。

## 目标

1. `schema.sql` 只包含数据库、表、索引和结构兼容所需的 DDL，不允许出现
   `INSERT`、`DELETE`、`UPDATE` 或 `REPLACE`。
2. 不创建 `data-system.sql` 或其他新的种子数据文件。
3. `export_202607161151.sql` 是现阶段唯一的初始数据来源。
4. 快照导入后，规则、模型、函数和变量等所有基于 ID 的引用保持原始语义，
   不能只消除 1062 错误而留下静默错链。
5. 快照在失败后可以直接重跑，不因前一次的部分提交再次报重复键。

## 文件职责

### `schema.sql`

- 保留建库、建表、主键、唯一键、普通索引以及结构兼容过程。
- 删除 `rule_variable` 内置常量的 `DELETE` 和 `INSERT ... ON DUPLICATE KEY UPDATE`。
- 不包含任何初始业务数据或系统常量数据。
- 单独执行后的数据库只有结构，不承诺具备可运行的业务初始数据；完整初始化必须继续导入 export。

### `export_202607161151.sql`

- 定义为全量初始数据快照，不是增量合并脚本。
- 文件开头保存并关闭 `FOREIGN_KEY_CHECKS`，清空快照覆盖的所有表并重置自增序列。
- 清空表集合必须与文件实际 `INSERT INTO rule_engine.*` 的目标表集合一致。
- 所有导出的自增表都必须在列清单和数据行中显式包含原始 `id`，禁止依赖插入顺序重新生成 ID。
- 快照包含完整的标准内置常量；不再依赖 schema 补充数据。
- 文件结束时恢复原有 `FOREIGN_KEY_CHECKS`。
- 禁止使用 `INSERT IGNORE` 掩盖唯一键或引用错误。

## 当前快照修复方式

1. 在隔离临时库执行纯 DDL schema。
2. 导入原始快照时恢复已确认的变量 ID 断点，使 `NULL_NUMBER=199`、
   `REJECT=203`、`PASS=204`、`hit_ruleset=206`、`EMPTY_MAP=209`。
3. 对快照中的结构化引用执行语义核对：同时存在 ID 和编码的引用，必须能在对应表中
   找到同 ID、同编码的记录。重点覆盖变量/常量 `refId + code`、函数
   `functionId + functionCode`、规则 `ruleId + ruleCode`、模型及模型输出引用。
4. 清理未被规则引用的废弃常量，补齐当前标准内置常量目录。
5. 从验证后的临时库重新生成数据快照，导出时包含所有自增主键 ID，并替换当前
   `export_202607161151.sql`，不新增另一份 export 文件。
6. 再次从空库恢复新快照，确认重新生成过程没有改变业务字段原文或引用语义。

## Docker 初始化流程

空数据卷首次初始化按文件名顺序执行：

```text
01-schema.sql -> 02-export.sql
```

- MySQL 服务将 `schema.sql` 挂载为 `01-schema.sql`，将当前 export 挂载为
  `02-export.sql`。
- Docker entrypoint 只会在空数据目录首次初始化时执行这两份文件，因此可以写入初始数据。
- `mysql-init` 服务在已有数据卷上仍只执行纯 DDL 的 `schema.sql`，不得自动执行全量 export，
  避免每次 `docker compose up` 都覆盖已有业务数据。
- 手工完整恢复仍采用：删除 `rule_engine` -> 执行 `schema.sql` -> 执行 export。

## 自动化约束

新增或调整后端 SQL 契约测试：

1. 读取 `schema.sql`，去除注释后断言不存在 `INSERT`、`DELETE`、`UPDATE`、`REPLACE`。
2. 读取最新的 `export_*.sql`，断言 `TRUNCATE` 表集合与 `INSERT` 表集合完全一致。
3. 从 schema 提取含 `AUTO_INCREMENT` 的表；凡被 export 写入的自增表，`INSERT` 列清单必须包含 `id`。
4. 断言快照正确保存、关闭和恢复 `FOREIGN_KEY_CHECKS`，且不包含 `INSERT IGNORE`。
5. 断言标准内置常量全部存在于 export，并检查关键稳定映射
   `PASS=204`、`hit_ruleset=206`、`EMPTY_MAP=209`。
6. 调整现有 `BuiltInConstantSqlTest`，不再要求 `schema.sql` 包含常量，继续校验仍作为示例数据存在的 SQL 文件。

## 验证与验收

1. 测试先行：先修改/新增 SQL 契约测试并观察其在旧文件上因 schema 含 DML、export 缺少 ID 而失败。
2. 执行 `schema.sql` 后查询 `rule_variable`，确认行数为 0。
3. 导入修复后的 export，确认无 1062 错误。
4. 在同一数据库再次导入 export，确认失败重试仍无 1062 错误且关键表行数不变。
5. 核对 `contact=1`、`PASS=204`、`hit_ruleset=206`、`EMPTY_MAP=209`，并完成结构化引用的 ID/编码一致性检查。
6. 执行后端全量构建与测试，启动后端并确认健康响应。
7. 启动前端，通过真实页面检查项目、数据对象、变量、模型、规则详情和设计器数据能够正常加载，浏览器控制台无错误。

## 非目标

- 不支持将该 export 合并到需要保留现有业务数据的数据库。
- 不创建 `data-system.sql` 或其他新种子数据文件。
- 不修改引用关联的 ID 原则，也不通过名称或编码替代 ID 关联。
- 不用删除唯一键、放宽约束或忽略重复数据的方式换取表面导入成功。
- 不批量改写其他历史 `export_*.sql`。
