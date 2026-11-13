# Data Object Display Name Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将所有数据对象字段引用统一展示为“数据对象名称/字段名称 完整字段路径”，完成真实 UI 验证、数据库导出并提交远程 `master`。

**Architecture:** 公共引用目录负责构造数据对象字段的语义标签和完整路径，所有选择器、详情页、公式及追踪展示继续消费同一引用结构。模型详情保留的备用映射同步使用相同格式，后端实体、接口、数据库结构和引用 ID 均不改变。

**Tech Stack:** Vue 2.6、Jest、Spring Boot 2.3、MySQL 8、Docker Compose、Git。

## Global Constraints

- 数据对象格式固定为：`数据对象名称/字段名称 完整字段路径`。
- 普通变量、常量、模型输出和函数展示格式不变。
- 所有引用继续通过 `refId + refType` 关联，禁止通过名称关联。
- 不增加依赖，不修改数据库结构，不批量改写历史导出文件。
- 先验证测试失败，再写生产代码。

---

### Task 1: 公共数据对象引用名称

**Files:**
- Modify: `rule-engine-builder-ui/tests/unit/utils/referenceCatalog.spec.js`
- Modify: `rule-engine-builder-ui/src/utils/referenceCatalog.js`

**Interfaces:**
- Consumes: `buildReferenceCatalog(variables, objectTree, models)`。
- Produces: 数据对象引用的 `refLabel.label` 为 `objectLabel + '/' + fieldLabel`，`displayName` 为该标签加 `refCode`。

- [ ] **Step 1: Write the failing test**

将数据对象断言改为：

```js
expect(bankCard.refLabel).toEqual({
  label: '银行卡信息/银行卡号',
  code: 'bankcard.bank_card_no'
})
expect(bankCard.displayName).toBe('银行卡信息/银行卡号 bankcard.bank_card_no')
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd rule-engine-builder-ui
npm test -- --runInBand tests/unit/utils/referenceCatalog.spec.js
```

Expected: FAIL，实际标签仍为 `银行卡号`。

- [ ] **Step 3: Write minimal implementation**

在数据对象分支构造完整标签：

```js
const displayLabel = [objectLabel, fieldLabel].filter(Boolean).join('/')
const entry = refEntry({
  id: field.id,
  refType: 'DATA_OBJECT',
  refCode,
  label: displayLabel,
  // 其余现有字段保持不变
})
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
cd rule-engine-builder-ui
npm test -- --runInBand tests/unit/utils/referenceCatalog.spec.js
```

Expected: PASS。

### Task 2: 模型详情备用映射与消费页面回归

**Files:**
- Modify: `rule-engine-builder-ui/tests/unit/views/modelDetail.spec.js`
- Modify: `rule-engine-builder-ui/tests/unit/views/ruleDetail.spec.js`
- Modify: `rule-engine-builder-ui/tests/unit/views/decisionTable.spec.js`
- Modify: `rule-engine-builder-ui/src/views/model/ModelDetail.vue`

**Interfaces:**
- Consumes: 公共引用目录的 `displayName`，以及模型详情 `buildReferenceState()` 的备用对象树。
- Produces: `varLabel` 为完整显示名，`varLabelText` 为 `对象名称/字段名称`，`varCodeText` 为完整路径。

- [ ] **Step 1: Write failing view tests**

关键断言：

```js
expect(item.varLabel).toBe('银行卡信息/银行卡号 bankcard.bank_card_no')
expect(wrapper.vm.fieldDisplayLabel(row)).toBe('银行卡信息/银行卡号')
expect(amountOpt.varLabel).toBe('税务请求/金额 TaxRequest.amount')
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd rule-engine-builder-ui
npm test -- --runInBand tests/unit/views/modelDetail.spec.js tests/unit/views/ruleDetail.spec.js tests/unit/views/decisionTable.spec.js
```

Expected: FAIL，页面仍显示字段名称加路径。

- [ ] **Step 3: Write minimal fallback implementation**

模型详情对象字段映射改为：

```js
const displayLabel = [objLabel, labelText].filter(Boolean).join('/')
const item = {
  // 其余现有字段保持不变
  varLabel: displayLabel + (codeText ? ' ' + codeText : ''),
  varLabelText: displayLabel,
  varCodeText: codeText
}
```

- [ ] **Step 4: Run targeted tests**

Run:

```bash
cd rule-engine-builder-ui
npm test -- --runInBand tests/unit/views/modelDetail.spec.js tests/unit/views/ruleDetail.spec.js tests/unit/views/decisionTable.spec.js
```

Expected: PASS。

### Task 3: 全量验证与真实 UI 检查

**Files:**
- Verify only: frontend and backend source trees。

**Interfaces:**
- Consumes: Tasks 1-2 的展示结果。
- Produces: 可复现的构建、测试和浏览器验收证据。

- [ ] **Step 1: Run frontend tests**

```bash
cd rule-engine-builder-ui
npm test -- --runInBand --silent
```

Expected: 所有测试通过。

- [ ] **Step 2: Run backend tests**

```bash
mvn test
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 3: Start services and verify UI**

启动后端与前端，登录控制台，进入模型详情并检查数据对象字段显示为：

```text
数据对象名称/字段名称 完整字段路径
```

同时确认浏览器控制台无 error，然后停止服务。

### Task 4: 导出数据库并提交 master

**Files:**
- Create: `rule-engine-server/src/main/resources/sql/export_202607130935.sql`

**Interfaces:**
- Consumes: 本地 Docker MySQL 容器 `rule-engine-mysql` 中的 `rule_engine` 数据库。
- Produces: 包含表结构和数据的 UTF-8 SQL 导出文件。

- [ ] **Step 1: Generate database dump**

在容器内执行：

```bash
mysqldump -uroot --default-character-set=utf8mb4 --single-transaction --routines --triggers --set-gtid-purged=OFF rule_engine --result-file=/tmp/export_202607130935.sql
docker cp rule-engine-mysql:/tmp/export_202607130935.sql rule-engine-server/src/main/resources/sql/export_202607130935.sql
```

- [ ] **Step 2: Verify database dump**

检查文件非空，包含 `CREATE TABLE`、`INSERT INTO`、`rule_model_output_field` 和 `transform_operand`，且不包含 `ERROR`。

- [ ] **Step 3: Commit and push**

```bash
git add --all
git diff --cached --check
git commit -m "feat: show full data object display names"
git push origin master
```

Expected: 本地 `master` 与 `origin/master` 指向同一提交，工作区干净。
