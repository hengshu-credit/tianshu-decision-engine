# 函数计算可靠性修复设计

## 目标

修复身份证出生日期提取和年龄计算不稳定的问题，新增按概率转换评分的内置函数，并为函数管理目录建立可执行的完整性校验，确保目录中的内置函数能够被 QLExpress 和函数管理测试入口实际调用。

## 已确认问题

- `idCardBirthDate` 和 `idCardAge` 仅存在于示例数据中的 SCRIPT 函数，未进入 Java 内置函数注册与内置函数目录；未导入示例数据、不同数据库或客户端运行时可能无法使用。
- `idCardBirthDate` 返回 `java.util.Date`，接口层没有统一 Date 输出格式，可能显示为时间戳或因时区产生日期偏移。
- SCRIPT 版 `idCardAge` 只接受 `yyyyMMdd` 和 `yyyy-MM-dd`，常见的 `yyyy-MM-dd HH:mm:ss` 会返回 `-1`。
- 当前只有 odds/PDO 评分函数，没有直接使用概率 `p` 计算 `A +/- B * ln(p/(1-p))` 的函数。
- 内置函数目录测试只校验元数据字段，没有逐项校验 Java 方法签名和真实执行能力。

## 实现方案

采用 Java 内置函数作为唯一运行实现。函数定义加入 `BuiltinFunctionCatalog`，运行时加入 `AggregateBuiltinFunctionRegistry`。服务启动时，`BuiltinFunctionMetadataInitializer` 会将数据库中同编码的旧 SCRIPT 元数据更新为 JAVA 元数据，使服务端、客户端和函数管理使用同一份实现。

不修改全局 Date 序列化设置，避免影响其他接口。不改变现有 odds/PDO 评分函数的方向语义，避免破坏已有规则。

## 函数行为

### 身份证出生日期

`idCardBirthDate(String idCard)` 返回 `yyyy-MM-dd` 字符串：

- 支持 18 位身份证和 15 位旧身份证。
- 校验号码结构和身份证内的出生日期是否合法。
- 输入为空、结构不合法或出生日期不合法时返回 `null`。
- 不校验行政区划和校验码，保持与原函数兼容，不扩大本次需求。

### 身份证年龄

`idCardAge(String idCard, Object currentDate, String calcMode)` 返回整数年龄：

- `currentDate` 支持 `Date`、`LocalDate`、`LocalDateTime`、毫秒时间戳，以及 `yyyyMMdd`、`yyyy-MM-dd`、`yyyy-MM-dd HH:mm:ss` 等现有日期格式。
- `currentDate` 为空时使用系统当前日期。
- `YEAR` 只计算年份差。
- `FULL` 按生日是否已过计算周岁；历史配置中的 `DAY` 作为 `FULL` 的兼容别名。
- 身份证、计算日期无效，或出生日期晚于计算日期时返回 `-1`。

### 概率转评分

新增 `scoreByProbability(double probability, double a, double b, String direction)`：

- 默认、空值、`HIGH_GOOD`、`ASC`、`越大越好` 使用 `A - B * ln(p/(1-p))`。
- `LOW_GOOD`、`DESC`、`越小越好` 使用 `A + B * ln(p/(1-p))`。
- `p` 必须严格位于 `(0,1)`；否则返回 `null`。
- 结果按现有评分函数约定四舍五入保留两位小数。

## 元数据与兼容性

- 三个函数均作为全局 JAVA 内置函数展示在函数管理页面。
- 参数元数据带有可直接运行的示例值，方向示例使用 `HIGH_GOOD`。
- 现有 `scoreByOdds`、`scoreByOddsPdo`、`scoreByBadRatePdo` 保持行为不变。
- 示例数据中的同名身份证 SCRIPT 函数改为 JAVA 元数据，避免在服务运行后手工导入示例数据时重新覆盖内置实现。

## 测试与验证

- 先添加身份证日期、年龄、概率评分的失败测试，确认现状确实失败。
- 添加身份证非法日期、15 位号码、生日边界、未来出生日期、日期时间字符串、概率边界和双方向测试。
- 增加目录契约测试，校验每个 JAVA 内置函数的方法存在、参数签名可解析，并使用目录示例参数通过函数管理执行链路真实执行。
- 运行后端目标测试、全量测试、完整构建并启动服务。
- 前端不新增专用逻辑；运行前端测试并启动页面，通过函数管理依次测试身份证日期、年龄和概率评分，再通过设计器函数动作完成一次规则执行。

## 变更边界

只修改内置函数实现、注册、目录元数据、相关测试和示例数据中的同名函数定义。不重构函数管理页面，不修改全局日期序列化，不触碰当前工作区的统一操作数改动。
