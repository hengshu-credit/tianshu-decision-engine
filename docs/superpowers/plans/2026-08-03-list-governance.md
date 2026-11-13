# 名单库与名单变更批次治理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将名单库元数据和名单记录变更纳入统一审批，确保审批通过前不改变生效名单，并让批量导入具备可校验、可追踪、可回滚的原子应用能力。

**Architecture:** 名单库使用 `LIST_LIBRARY` 治理适配器直接管理生效投影；名单记录使用 `rule_list_change_batch` 与 `rule_list_change_item` 暂存完整变更，再以 `LIST_RECORD_BATCH` 审批资源保存统计、摘要和有限脱敏样例。审批通过时重新核对暂存批次与记录基线，在同一事务中应用全部有效项；取消、驳回或冲突只封存暂存批次。

**Tech Stack:** Java 17、Spring Boot 3.5、MyBatis Plus、MySQL 8、Apache POI、Vue 3、Element Plus、Vitest、JUnit 4、Playwright。

## Global Constraints

- 初始数据快照中的明文数据库凭据不在本计划范围内，不做脱敏改造。
- 技术模块导航继续平铺，不增加收起按钮；侧栏仍由拖拽宽度自动判断折叠或展开。
- 所有关联只保存稳定 ID；审批摘要中的名称仅供展示，不能参与关联或回溯匹配。
- 用户输入的名单编码、名称和名单内容原样保存，不做大小写、驼峰或下划线转换。
- 审批 JSON 不保存完整名单数据，只保存批次 ID、计数、内容摘要和最多 5 条服务端脱敏样例。
- 单条新增、修改、删除也必须转成一条记录的批次，审批前不得修改 `rule_list_record` 或 `rule_list_record_log`。
- 批次应用必须在一个数据库事务内完成，任一写入失败时全部回滚。
- 前端修改必须通过 ESLint、构建、全量 Vitest；后端必须通过完整编译、服务启动和全量 Maven 测试；最后用浏览器走完真实业务 UI。

---

## File Structure

- `rule-engine-model/.../RuleListChangeBatch.java`：批次头、统计、摘要、审批关联和终态。
- `rule-engine-model/.../RuleListChangeItem.java`：暂存的逐行变更、目标记录 ID、基线摘要和校验结果。
- `rule-engine-server/.../mapper/RuleListChangeBatchMapper.java`、`RuleListChangeItemMapper.java`：批次持久化。
- `rule-engine-server/.../service/RuleListChangeBatchService.java`：单条/Excel 校验暂存、批次摘要、审批草稿创建、原子应用和终态封存。
- `rule-engine-server/.../governance/RuleListLibraryGovernedResourceAdapter.java`：名单库治理校验、依赖、差异和投影写入。
- `rule-engine-server/.../governance/RuleListRecordBatchGovernedResourceAdapter.java`：只接受 `CREATE`，验证批次不变性并委托应用/封存。
- `rule-engine-builder-ui/src/api/ruleList.js`：移除直接写接口，改为名单库审批草稿和名单批次暂存接口。
- `ListLibrary.vue`、`ListDetail.vue`：把“保存/删除/导入成功”改为“审批草稿已创建”，提供审批前不生效说明并跳转审批详情。
- `ApprovalList.vue`、`ApprovalDetail.vue`：增加名单库和名单批次的业务名称、标题和摘要展示。

### Task 1: 新增批次暂存数据模型与数据库兼容升级

**Files:**
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/RuleListChangeBatch.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/RuleListChangeItem.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/mapper/RuleListChangeBatchMapper.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/mapper/RuleListChangeItemMapper.java`
- Modify: `rule-engine-server/src/main/resources/sql/schema.sql`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/SchemaSyncService.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/sql/GovernanceSchemaSqlTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/SchemaSyncServiceTest.java`

**Interfaces:**
- Produces: `RuleListChangeBatch.status` values `STAGED`, `VALIDATION_FAILED`, `APPLIED`, `REJECTED`, `CANCELLED`, `CONFLICT`.
- Produces: `RuleListChangeItem.validationStatus` values `VALID`, `DUPLICATE`, `INVALID`.
- Produces: unique batch item order `(batch_id, row_number)` and indexes on `(list_id, status)`、`approval_request_id`.

- [ ] **Step 1: Write failing schema tests**

```java
assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS `rule_list_change_batch`"));
assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS `rule_list_change_item`"));
assertTrue(schema.contains("`content_digest` CHAR(64) NOT NULL"));
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `mvn -pl rule-engine-server -Dtest=GovernanceSchemaSqlTest,SchemaSyncServiceTest test`

Expected: FAIL because the two staging tables and upgrade statements do not exist.

- [ ] **Step 3: Add entities, mappers, schema DDL and idempotent schema synchronization**

The batch table stores counts and lifecycle metadata; the item table stores complete staged content and the record baseline digest. Neither table is read by the rule execution path.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run: `mvn -pl rule-engine-server -Dtest=GovernanceSchemaSqlTest,SchemaSyncServiceTest test`

- [ ] **Step 5: Commit**

```text
feat: add list change batch staging schema
```

### Task 2: 将名单库元数据纳入统一治理

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/RuleListLibraryGovernedResourceAdapter.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceResourceTypes.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceResourceAdapterConfiguration.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceApprovalService.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceImpactService.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleListService.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleListController.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/RuleListLibraryGovernedResourceAdapterTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/GovernanceImpactServiceTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/GovernanceApprovalServiceTest.java`

**Interfaces:**
- Produces: governance resource type `LIST_LIBRARY`, approval tab `LIST`.
- Consumes: stable snapshot fields `id`, `projectId`, `scope`, `listCode`, `listName`, `listType`, `description`, `status`.
- Produces: create active key identity `scope + projectId + listCode`.
- Produces: `LIST_LIBRARY -> PROJECT` dependency and `LIST` downstream impact analysis for disable/delete.

- [ ] **Step 1: Write failing adapter and impact tests**

```java
ResourceSnapshot normalized = adapter.normalizeDraft(snapshot(Map.of(
        "projectId", 7L, "scope", "PROJECT",
        "listCode", "Mobile_Black", "listName", "手机号黑名单",
        "listType", "BLACK", "status", 1)));
assertEquals("Mobile_Black", read(normalized).get("listCode"));
assertEquals("PROJECT", adapter.collectDependencies(normalized).get(0).targetType());
```

Also assert that duplicate scoped codes、missing projects and disabling a list with downstream variables are rejected before submit.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl rule-engine-server -Dtest=RuleListLibraryGovernedResourceAdapterTest,GovernanceImpactServiceTest,GovernanceApprovalServiceTest test`

- [ ] **Step 3: Implement adapter registration, identity key and impact mapping**

The adapter must use mapper IDs for all references, keep display names out of dependency resolution, map `DELETE` to status `-1`, and make ordinary list queries exclude status `-1`.

- [ ] **Step 4: Remove direct mutation routes for library POST/PUT/DELETE**

Retain GET/list endpoints. All UI writes will use `/api/rule/governance/drafts`.

- [ ] **Step 5: Run focused tests and verify GREEN**

- [ ] **Step 6: Commit**

```text
feat: govern list library lifecycle
```

### Task 3: 校验并暂存单条与 Excel 名单变更

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleListChangeBatchService.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/RuleListRecordChangeRequest.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/RuleListChangeBatchResult.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleListController.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleListService.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleListChangeBatchServiceTest.java`
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/controller/mgmt/RuleListControllerTest.java`

**Interfaces:**
- Produces: `stageSingle(Long listId, RuleListRecordChangeRequest request, String actor)`.
- Produces: `stageImport(Long listId, MultipartFile file, String actor)`.
- Returns: batch ID, approval request ID, six counts, digest, up to 20 row errors and `submittable`.
- Consumes: operation `ADD|UPDATE|DELETE`; update/delete require `record.id`, add requires no effective duplicate.

- [ ] **Step 1: Write failing behavior tests**

Tests must prove that staging:

```java
assertNull(recordMapper.inserted);       // effective table unchanged
assertNull(recordLogMapper.inserted);    // effective log unchanged
assertEquals(1, result.getAddCount());
assertNotNull(result.getApprovalRequestId());
```

Also cover duplicate rows, invalid dates, update/delete of a missing record, same-file duplicates, digest determinism and a file with invalid rows returning `submittable=false` without creating an approval.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl rule-engine-server -Dtest=RuleListChangeBatchServiceTest,RuleListControllerTest test`

- [ ] **Step 3: Implement normalization, validation, staging and server-derived summary**

The approval snapshot shape is fixed:

```json
{
  "batchId": 101,
  "listId": 9,
  "listCode": "mobile_black",
  "listName": "手机号黑名单",
  "sourceType": "IMPORT",
  "totalCount": 120,
  "addCount": 100,
  "updateCount": 10,
  "deleteCount": 5,
  "duplicateCount": 3,
  "invalidCount": 2,
  "contentDigest": "sha256",
  "samples": [{"operation":"ADD","itemType":"MOBILE","itemContent":"138****8000"}]
}
```

- [ ] **Step 4: Replace record mutation endpoints with one batch endpoint**

Expose JSON single-change and multipart import staging endpoints; both resolve the console operator server-side. Keep list、export、template and log queries unchanged.

- [ ] **Step 5: Run focused tests and verify GREEN**

- [ ] **Step 6: Commit**

```text
feat: stage list record changes for approval
```

### Task 4: 审批通过后原子应用批次并封存终止批次

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/RuleListRecordBatchGovernedResourceAdapter.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernedResourceAdapter.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceApprovalService.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceResourceAdapterConfiguration.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleListChangeBatchService.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/RuleListRecordBatchGovernedResourceAdapterTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/GovernanceApprovalServiceTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleListChangeBatchServiceTest.java`

**Interfaces:**
- Produces: governance resource type `LIST_RECORD_BATCH`, approval tab `LIST`.
- Produces: `applyBatch(long batchId, String expectedDigest, String actor)` with `@Transactional`.
- Produces: create-approval termination hook receiving the request snapshot so `batchId` can be sealed on reject/cancel/conflict.

- [ ] **Step 1: Write failing transaction and lifecycle tests**

Prove that a write failure on item N rolls back items 1..N-1 and all logs; baseline changes become approval `CONFLICT`; reject/cancel/conflict set the staging batch to the same terminal status without touching effective records.

- [ ] **Step 2: Run focused tests and verify RED**

- [ ] **Step 3: Add create-request termination hook and batch adapter**

Existing non-create adapter termination behavior remains unchanged. The batch adapter only supports `CREATE`, reloads the server-owned batch, verifies the digest and status, and never trusts counts or samples sent by the browser.

- [ ] **Step 4: Implement baseline revalidation and atomic application**

`ADD` requires the staged key still absent; `UPDATE`/`DELETE` require the same target ID and baseline digest. Only after all checks pass may writes begin.

- [ ] **Step 5: Run focused and governance regression tests**

Run: `mvn -pl rule-engine-server -Dtest=RuleListRecordBatchGovernedResourceAdapterTest,GovernanceApprovalServiceTest,RuleListChangeBatchServiceTest test`

- [ ] **Step 6: Commit**

```text
feat: apply approved list batches atomically
```

### Task 5: 改造名单库与名单详情业务界面

**Files:**
- Modify: `rule-engine-builder-ui/src/api/ruleList.js`
- Modify: `rule-engine-builder-ui/src/views/ruleList/ListLibrary.vue`
- Modify: `rule-engine-builder-ui/src/views/ruleList/ListDetail.vue`
- Modify: `rule-engine-builder-ui/src/views/approval/ApprovalList.vue`
- Modify: `rule-engine-builder-ui/src/views/approval/ApprovalDetail.vue`
- Test: `rule-engine-builder-ui/tests/unit/views/listLibrary.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/views/listDetail.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/views/approvalList.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/views/approvalDetail.spec.js`

**Interfaces:**
- Consumes: `createResourceDraft('LIST_LIBRARY', ...)`.
- Consumes: `stageRecordChange(listId, operation, record)` and `stageRecordImport(listId, file)`.
- Produces: success navigation `/approval/{requestId}` and explicit copy “审批通过后才会生效”。

- [ ] **Step 1: Rewrite unit expectations first and verify RED**

```javascript
expect(ruleListApi.createLibraryDraft).toHaveBeenCalled()
expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/approval/81')
expect(wrapper.vm.$message.success).toHaveBeenCalledWith('已生成名单库审批草稿')
```

Tests also assert that status-switch failure restores the original value; import validation errors stay on the page and do not refresh effective records; approval labels render “名单库”和“名单变更批次”。

- [ ] **Step 2: Implement business-language workflow and restrained layout changes**

Use one neutral information banner, keep list density and current spacing scale, make “发起审批” the primary dialog action, and keep destructive styling for the confirmation modal rather than the table link.

- [ ] **Step 3: Run focused tests and verify GREEN**

Run: `C:\Program Files\nodejs\npm.cmd test -- tests/unit/views/listLibrary.spec.js tests/unit/views/listDetail.spec.js tests/unit/views/approvalList.spec.js tests/unit/views/approvalDetail.spec.js`

- [ ] **Step 4: Commit**

```text
feat: route list changes through approval UI
```

### Task 6: 完整验证与浏览器业务验收

**Files:**
- Modify only if validation reveals a real defect; every defect requires a failing regression test first.

**Interfaces:**
- Acceptance flow: UI 新建名单库草稿 → 预检 → 提交 → 审批 → 名单库出现；UI 新增/修改/删除单条记录和导入文件 → 审批前记录不变 → 审批后整批生效；取消或驳回批次后记录不变。

- [ ] **Step 1: Frontend static and automated verification**

Run in `rule-engine-builder-ui`:

```text
C:\Program Files\nodejs\npm.cmd run lint
C:\Program Files\nodejs\npm.cmd run build
C:\Program Files\nodejs\npm.cmd test
```

- [ ] **Step 2: Backend build, startup and tests**

Run:

```text
mvn clean install -DskipTests
mvn test
```

Start `rule-engine-server` on isolated port `8180`, verify HTTP health, and stop only that process after acceptance.

- [ ] **Step 3: Browser UI acceptance on isolated frontend port `9190`**

Do not create test data through direct backend mutation APIs. Exercise every step through visible UI controls, inspect approval summary/diff, verify pre-approval effective rows remain unchanged, and capture screenshots for visual review.

- [ ] **Step 4: Review diff and commit any test-backed acceptance fixes**

```text
fix: polish list governance acceptance flow
```
