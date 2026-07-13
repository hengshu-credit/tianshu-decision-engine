# Constant Literal Semantics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make managed constants compile and execute as immutable typed values across visual rules and model operands, while making constant-list values editable only through the edit action and providing a correct built-in constant set.

**Architecture:** Keep persisted references stable through `refId + refType=CONSTANT`. Add a shared core constant codec for validation, QLExpress expression generation, and Java runtime decoding; extend `VarContext` with a constant-expression map; have server services build trusted maps from persisted constants and overlay constants on runtime inputs. Frontend operands retain the stable reference and add a display-only value snapshot refreshed from the reference catalog.

**Tech Stack:** Java 8, Spring Boot 2.3, QLExpress 4.1.0, Fastjson 2 compatibility API, JUnit 4, Vue 2.6, Element UI 2.15, Jest + Vue Test Utils, MySQL 8.

## Global Constraints

- All associations use `refId + refType`; never resolve a constant by code or label.
- Preserve user-entered codes, names, and imported field names exactly.
- Add no new dependency.
- Apply TDD: run each new regression test red before production changes, then green.
- Published scripts remain immutable; changed constants enter scripts on the next test, compile, or publish.
- Run the full backend build/start/test and frontend dev/browser/test verification required by `AGENTS.md`.

---

### Task 1: Shared constant value codec

**Files:**
- Create: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/compiler/ConstantValueCodec.java`
- Create: `rule-engine-core/src/test/java/com/hengshucredit/rule/core/compiler/ConstantValueCodecTest.java`

**Interfaces:**
- Produces: `ConstantValueCodec.normalize(String varType, String rawValue)`, `toQlExpression(String varType, String rawValue)`, and `toRuntimeValue(String varType, String rawValue)`.
- Throws: `IllegalArgumentException` with a business-readable reason for invalid constant values.

- [ ] **Step 1: Write failing codec tests**

Cover empty/escaped strings, `null`, booleans, integers/decimals, JSON list/map, and the exact infinity expressions:

```java
Assert.assertEquals("''", ConstantValueCodec.toQlExpression("STRING", ""));
Assert.assertEquals("null", ConstantValueCodec.toQlExpression("OBJECT", "null"));
Assert.assertEquals("[]", ConstantValueCodec.toQlExpression("LIST", "[]"));
Assert.assertEquals("jsonParse('{}')", ConstantValueCodec.toQlExpression("MAP", "{}"));
Assert.assertEquals("1.0 / 0.0", ConstantValueCodec.toQlExpression("DOUBLE", "Infinity"));
Assert.assertEquals("-1.0 / 0.0", ConstantValueCodec.toQlExpression("DOUBLE", "-Infinity"));
```

Also assert invalid boolean/number/list/map values are rejected and STRING text remains unchanged by `normalize`.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
mvn -pl rule-engine-core -Dtest=ConstantValueCodecTest test
```

Expected: test compilation fails because `ConstantValueCodec` does not exist.

- [ ] **Step 3: Implement the minimal codec**

Use Fastjson to validate list/map JSON, `BigDecimal` for finite numbers, explicit `Infinity/-Infinity` branches for DOUBLE, and a single-quote QL string escaper. `toRuntimeValue` returns Java strings, booleans, numeric values, lists/maps, null, and `Double` infinities.

- [ ] **Step 4: Verify GREEN and real QLExpress execution**

Extend the test to execute every produced expression through a registered `QLExpressEngine`, then rerun the focused command and expect all tests to pass.

### Task 2: Compile constant references by stable ID

**Files:**
- Modify: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/compiler/VarContext.java`
- Modify: `rule-engine-core/src/main/java/com/hengshucredit/rule/core/compiler/OperandCompiler.java`
- Modify: `rule-engine-core/src/test/java/com/hengshucredit/rule/core/compiler/OperandCompilerTest.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleVariableService.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleCompileService.java`
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleVariableServiceTest.java`

**Interfaces:**
- `VarContext` gains `Map<String, String> refIdToConstantExpression` and `resolveConstant(Long refId)`.
- `RuleVariableService.buildRefConstantExpressionMap(Long projectId)` returns keys in `CONSTANT:<id>` form.
- `OperandCompiler.compile` requires a trusted constant expression for `REFERENCE + CONSTANT` and never falls back to `code`.

- [ ] **Step 1: Add failing compiler and service tests**

Assert a constant reference such as `{"kind":"REFERENCE","refId":7,"refType":"CONSTANT","code":"EMPTY_STRING"}` compiles to `''`, and missing ID/expression throws a clear error. Assert the server map uses persisted type/value and excludes disabled constants.

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
mvn -pl rule-engine-core -Dtest=OperandCompilerTest test
mvn -pl rule-engine-server -am -Dtest=RuleVariableServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement the minimal context and server map**

Keep existing constructors source-compatible by delegating to a new constructor with an empty constant map. Build expressions exclusively from enabled persisted constants through `ConstantValueCodec` and pass the map from `RuleCompileService`.

- [ ] **Step 4: Verify focused tests GREEN**

Rerun both focused commands and confirm no failure.

### Task 3: Enforce typed constants in persistence and runtime

**Files:**
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleVariableService.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/VariableSourceResolver.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/OperandValueResolver.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleModelService.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/VariableSourceResolverTest.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/OperandValueResolverTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleVariableServiceTest.java`

**Interfaces:**
- Constant save/update/import normalizes through `ConstantValueCodec`; STRING empty values are allowed.
- `OperandValueResolver` overloads accept trusted `Map<String,Object>` reference values keyed by `CONSTANT:<id>`.
- `RuleModelService` builds the constant map for the model scope and uses it for source/default/transform operands.

- [ ] **Step 1: Add failing persistence/runtime tests**

Cover STRING empty save, invalid typed values, import normalization, request `{EMPTY_STRING: "tampered"}` being overwritten, and direct model/operand constant resolution by ID.

- [ ] **Step 2: Run focused server tests and verify RED**

```powershell
mvn -pl rule-engine-server -am '-Dtest=RuleVariableServiceTest,VariableSourceResolverTest,OperandValueResolverTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement persistence normalization and runtime overlay**

Replace the nonblank-only constant assertion with typed normalization. Constants always refresh in `VariableSourceResolver`. Load global plus project constants in `RuleModelService`; overlay trusted constant values after copying caller parameters so callers cannot override them.

- [ ] **Step 4: Verify focused server tests GREEN**

Rerun the focused server command and confirm all selected tests pass.

### Task 4: Correct and expand built-in constant data

**Files:**
- Modify: `rule-engine-server/src/main/resources/sql/schema.sql`
- Modify: `rule-engine-server/src/main/resources/sql/data-example.sql`
- Modify: `rule-engine-server/src/main/resources/sql/data-tianshu-example.sql`
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/sql/BuiltInConstantSqlTest.java`

**Interfaces:**
- Fresh schema and example imports contain the same 11 built-in global constants.
- SQL is idempotent by the existing unique scope/project/code key.

- [ ] **Step 1: Add a failing SQL-content regression test or verification script assertion**

Read the three classpath/filesystem SQL resources as UTF-8 and assert all 11 codes, raw empty STRING storage, `EMPTY_MAP`, and absence of the obsolete `EMPTY_OBJECT` seed. The test uses an explicit list of the three repository paths and an explicit list of expected codes, so a missing surface or missing constant fails independently.

- [ ] **Step 2: Verify the assertion fails against current SQL**

Expected failures include missing boolean/numeric constants, the old quoted empty string, and `EMPTY_OBJECT`.

Run:

```powershell
mvn -pl rule-engine-server -am -Dtest=BuiltInConstantSqlTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Update initialization and example SQL**

Use explicit column lists and `ON DUPLICATE KEY UPDATE` for the 11 rows. Store `EMPTY_STRING.default_value` as `''`, keep infinity storage values as `Infinity/-Infinity`, and remove only the obsolete seed row rather than rewriting historical exports.

- [ ] **Step 4: Re-run SQL verification**

Confirm all three SQL surfaces have the intended codes and values and `git diff --check` succeeds.

Rerun the same Maven command and expect the test to pass.

### Task 5: Make the constant UI edit-only and show value previews

**Files:**
- Create: `rule-engine-builder-ui/src/utils/constantValue.js`
- Create: `rule-engine-builder-ui/tests/unit/utils/constantValue.spec.js`
- Modify: `rule-engine-builder-ui/src/views/variable/VariableList.vue`
- Modify: `rule-engine-builder-ui/tests/unit/views/variableList.spec.js`
- Modify: `rule-engine-builder-ui/src/utils/referenceCatalog.js`
- Modify: `rule-engine-builder-ui/src/utils/operand.js`
- Modify: `rule-engine-builder-ui/tests/unit/utils/referenceCatalog.spec.js`
- Modify: `rule-engine-builder-ui/tests/unit/utils/operand.spec.js`

**Interfaces:**
- `formatConstantValue(value, type)` renders `''`, `null`, `[]`, `{}`, and infinities predictably.
- Constant catalog options expose `constantValue` without changing `refId/refType`.
- Operand persistence carries display-only `constantValue`; compiler trust remains server-side.

- [ ] **Step 1: Add failing frontend tests**

Assert constant table rows contain plain text instead of `el-input`, clicking Edit opens the existing dialog, empty STRING passes submit validation, formatter output is correct, and a selected constant Operand displays its value preview while retaining ID/type.

- [ ] **Step 2: Run focused Jest tests and verify RED**

```powershell
cd rule-engine-builder-ui
npm.cmd test -- --runInBand tests/unit/utils/constantValue.spec.js tests/unit/utils/operand.spec.js tests/unit/utils/referenceCatalog.spec.js tests/unit/views/variableList.spec.js
```

- [ ] **Step 3: Implement minimal UI changes**

Replace constant-list inputs with formatted spans, remove obsolete blur-save methods only when no longer used, update empty STRING validation, and enrich/sync catalog operands with value previews.

- [ ] **Step 4: Verify focused Jest tests GREEN**

Rerun the focused Jest command and confirm all selected tests pass.

### Task 6: Full verification and browser acceptance

**Files:**
- Review all modified files; no unrelated edits.

**Interfaces:**
- Produces fresh build, tests, startup smoke, and browser-visible evidence.

- [ ] **Step 1: Backend build**

```powershell
mvn clean install -DskipTests
```

Expected: reactor `BUILD SUCCESS`.

- [ ] **Step 2: Start backend and smoke it**

Start `rule-engine-server` with `mvn spring-boot:run`, wait for readiness, verify console login and model health endpoints, then keep it running for browser acceptance.

- [ ] **Step 3: Run backend full tests**

```powershell
mvn test
```

Expected: all reactor tests pass with zero failures/errors.

- [ ] **Step 4: Start frontend and inspect startup**

```powershell
cd rule-engine-builder-ui
npm.cmd run dev
```

Wait for the actual bound port and confirm no compile error.

- [ ] **Step 5: Complete browser workflow without API shortcuts**

Log in through the UI, open variable management, verify the 11 constants and edit-only table, edit a constant through the operation button, create or edit a rule/model through the UI, select empty/null/infinity constants, run the UI test action, and inspect the compiled/result behavior and browser console.

- [ ] **Step 6: Stop frontend/backend development processes**

Stop only the processes started for this task after browser validation.

- [ ] **Step 7: Run frontend full tests**

```powershell
cd rule-engine-builder-ui
npm.cmd test -- --runInBand
```

Expected: all Jest suites pass.

- [ ] **Step 8: Final diff and requirement review**

```powershell
git status --short
git diff --check
git diff --stat
```

Confirm each design requirement maps to code/tests and every changed line is in scope.
