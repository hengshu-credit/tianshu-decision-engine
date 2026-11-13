# Function Calculation Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复身份证日期与年龄函数，新增概率转评分函数，并保证函数目录中的 Java 内置函数可被真实执行。

**Architecture:** 将身份证逻辑放入现有 `DecisionBuiltinFunctions`，由现有注册表和函数目录统一暴露。目录契约测试通过 `RuleFunctionService.testFunction` 使用每个定义的示例参数执行，覆盖函数管理实际链路。

**Tech Stack:** Java 8、QLExpress 4、JUnit 4、Spring Boot 2.3、Vue 2/Jest

## Global Constraints

- 不新增依赖。
- `idCardBirthDate` 返回 `yyyy-MM-dd` 字符串。
- `idCardAge` 的 `DAY` 与 `FULL` 同义，无效输入返回 `-1`。
- `scoreByProbability` 默认使用减号，`p` 不在 `(0,1)` 时返回 `null`。
- 不改变现有 odds/PDO 函数语义，不触碰工作区已有统一操作数改动。

---

### Task 1: 身份证出生日期与年龄

**Files:**
- Modify: `rule-engine-core/src/test/java/com/hengshucredit/rule/core/function/DecisionBuiltinFunctionsTest.java`
- Modify: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/function/DecisionBuiltinFunctions.java`
- Modify: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/function/AggregateBuiltinFunctionRegistry.java`

**Interfaces:**
- Produces: `String idCardBirthDate(String idCard)`
- Produces: `long idCardAge(String idCard, Object currentDate, String calcMode)`

- [ ] **Step 1: 写失败测试**

```java
assertEquals("1990-01-02", functions.idCardBirthDate("110105199001022317"));
assertEquals("1990-01-02", functions.idCardBirthDate("110105900102231"));
assertNull(functions.idCardBirthDate("110105199013022317"));
assertEquals(34L, functions.idCardAge("110105199001022317", "2025-01-01 12:30:00", "FULL"));
assertEquals(35L, functions.idCardAge("110105199001022317", "2025-01-02", "DAY"));
assertEquals(-1L, functions.idCardAge("110105209001022317", "2025-01-02", "FULL"));
```

- [ ] **Step 2: 确认测试因方法缺失而失败**

Run: `mvn -pl rule-engine-core -am "-Dtest=DecisionBuiltinFunctionsTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: test compilation fails because `idCardBirthDate` and `idCardAge` do not exist.

- [ ] **Step 3: 最小实现并注册**

```java
public String idCardBirthDate(String idCard) {
    LocalDate birthDate = idCardBirthLocalDate(idCard);
    return birthDate == null ? null : birthDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
}

public long idCardAge(String idCard, Object currentDate, String calcMode) {
    LocalDate birthDate = idCardBirthLocalDate(idCard);
    LocalDate effectiveDate = toLocalDate(currentDate);
    if (birthDate == null || effectiveDate == null || birthDate.isAfter(effectiveDate)) return -1L;
    int years = effectiveDate.getYear() - birthDate.getYear();
    if ("YEAR".equalsIgnoreCase(normalizedMode)) return years;
    return effectiveDate.isBefore(birthDate.plusYears(years)) ? years - 1L : years;
}
```

在 `AggregateBuiltinFunctionRegistry.register` 中用 `STRING` 和 `STRING_OBJECT_STRING` 签名注册两个函数。

- [ ] **Step 4: 运行目标测试确认通过**

Run: Task 1 Step 2 command

Expected: `DecisionBuiltinFunctionsTest` passes.

### Task 2: 概率转评分

**Files:**
- Modify: `rule-engine-core/src/test/java/com/hengshucredit/rule/core/function/DecisionBuiltinFunctionsTest.java`
- Modify: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/function/DecisionBuiltinFunctions.java`
- Modify: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/function/AggregateBuiltinFunctionRegistry.java`

**Interfaces:**
- Produces: `BigDecimal scoreByProbability(double probability, double a, double b, String direction)`

- [ ] **Step 1: 写失败测试**

```java
assertEquals(600.0d, functions.scoreByProbability(0.5, 600, 20, null).doubleValue(), 0.000001d);
assertEquals(556.06d, functions.scoreByProbability(0.9, 600, 20, "HIGH_GOOD").doubleValue(), 0.000001d);
assertEquals(643.94d, functions.scoreByProbability(0.9, 600, 20, "LOW_GOOD").doubleValue(), 0.000001d);
assertNull(functions.scoreByProbability(0, 600, 20, "HIGH_GOOD"));
assertNull(functions.scoreByProbability(1, 600, 20, "HIGH_GOOD"));
```

- [ ] **Step 2: 确认测试因方法缺失而失败**

Run: Task 1 Step 2 command

Expected: test compilation fails because `scoreByProbability` does not exist.

- [ ] **Step 3: 最小实现并注册**

```java
public BigDecimal scoreByProbability(double probability, double a, double b, String direction) {
    if (probability <= 0d || probability >= 1d) return null;
    double sign = probabilityDirectionSign(direction);
    double logOdds = Math.log(probability / (1d - probability));
    return decimal(a).add(decimal(sign * b * logOdds)).setScale(2, RoundingMode.HALF_UP);
}
```

方向默认 `-1`，仅 `LOW_GOOD`、`DESC`、`越小越好` 返回 `+1`；以 `DOUBLE_DOUBLE_DOUBLE_STRING` 注册。

- [ ] **Step 4: 运行目标测试确认通过**

Run: Task 1 Step 2 command

Expected: all `DecisionBuiltinFunctionsTest` cases pass.

### Task 3: 函数目录、示例数据与完整执行契约

**Files:**
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/BuiltinFunctionCatalogTest.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/BuiltinFunctionCatalog.java`
- Modify: `rule-engine-server/src/main/resources/sql/data-tianshu-example.sql`

**Interfaces:**
- Consumes: Task 1 and Task 2 Java methods
- Produces: function-management metadata for `idCardBirthDate`, `idCardAge`, `scoreByProbability`

- [ ] **Step 1: 写失败目录测试**

断言目录包含三个函数，身份证日期返回类型为 `STRING`，并遍历全部定义：读取 `paramsJson` 的 `example`，通过 `RuleFunctionService.testFunction` 执行，断言 `success == true`。

- [ ] **Step 2: 确认目录测试失败**

Run: `mvn -pl rule-engine-server -am "-Dtest=BuiltinFunctionCatalogTest,RuleFunctionServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: missing function definitions cause assertions to fail.

- [ ] **Step 3: 添加目录元数据并更新示例 SQL**

目录示例：身份证 `110105199001022317`、计算日 `2025-01-02 12:30:00`、模式 `FULL`；概率 `0.05`、A `600`、B `20`、方向 `HIGH_GOOD`。将示例 SQL 中同名身份证函数改为 JAVA 类和方法元数据，删除 SCRIPT 实现正文。

- [ ] **Step 4: 运行服务端目标测试确认通过**

Run: Task 3 Step 2 command

Expected: all selected server tests pass and every catalog function example executes successfully.

### Task 4: 全量验证与 UI 流程

**Files:**
- No production file changes unless verification exposes a reproducible defect.

- [ ] **Step 1: 后端构建与全量测试**

Run: `mvn clean install -DskipTests`

Run: `mvn test`

Expected: all modules build and all tests pass.

- [ ] **Step 2: 启动后端并验证健康启动**

Run from `rule-engine-server`: `mvn spring-boot:run`

Expected: application starts on port 8080 without function registration errors; then stop process.

- [ ] **Step 3: 前端启动与测试**

Run from `rule-engine-builder-ui`: `npm run dev`

Expected: page starts on port 9090 without compile errors; then stop process.

Run from `rule-engine-builder-ui`: `npm test`

Expected: all Jest suites pass.

- [ ] **Step 4: 浏览器完成业务操作**

登录后进入函数管理，依次运行三个新函数的示例；再在规则设计器中用函数动作计算身份证出生日期、年龄和概率评分，保存并执行规则，核对输出分别为日期字符串、整数年龄和两位小数评分。
