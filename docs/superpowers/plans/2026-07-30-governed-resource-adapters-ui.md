# Governed Resource Adapters and Approval UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect every requested module to the governance core and deliver the approval/account-facing Vue workflows, semantic version comparisons, history, restore, and dependency conflict guidance.

**Architecture:** Each resource aggregate has a focused backend adapter. Existing business pages read only effective projections and write governance drafts. A single approval list/detail shell delegates differences to resource-specific Vue components.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis Plus, Vue 3, Element Plus, Monaco, Vitest, Playwright.

## Global Constraints

- Tabs: 字段、模型、外数、数据库、函数、规则、分流、项目.
- New and rejected resources do not appear in effective business lists.
- Existing resources remain effective until approval succeeds.
- Restore creates a request and a new version; it never overwrites history.
- Sensitive values never appear in snapshots, diffs, logs, or API responses.
- Existing rule visual diff components are reused and extended.
- Frontend code must pass ESLint.

---

### Task 1: Implement project and field aggregate adapters

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/ProjectGovernanceAdapter.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/VariableGovernanceAdapter.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/DataObjectGovernanceAdapter.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/adapter/ProjectGovernanceAdapterTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/adapter/VariableGovernanceAdapterTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/adapter/DataObjectGovernanceAdapterTest.java`

**Interfaces:**
- Produces resource types `PROJECT`, `VARIABLE`, and `DATA_OBJECT`.

- [ ] **Step 1: Write failing snapshot, dependency, diff, apply, soft-delete, and restore tests**

```java
@Test
public void dataObjectSnapshotKeepsFieldIdsAndHierarchy() {
    ResourceSnapshot snapshot = adapter.loadEffective(3L);
    Assert.assertTrue(snapshot.getSnapshotJson().contains("\"parentFieldId\":11"));
    Assert.assertTrue(snapshot.getSnapshotJson().contains("\"id\":12"));
}
```

- [ ] **Step 2: Run the focused adapter tests**

Run: `mvn -pl rule-engine-server -Dtest=ProjectGovernanceAdapterTest,VariableGovernanceAdapterTest,DataObjectGovernanceAdapterTest test`

Expected: FAIL.

- [ ] **Step 3: Implement project and variable aggregates**

Variable snapshots include options. Data object snapshots include all fields and parent IDs, preserving user-provided names exactly.

- [ ] **Step 4: Run the focused tests**

Run: `mvn -pl rule-engine-server -Dtest=ProjectGovernanceAdapterTest,VariableGovernanceAdapterTest,DataObjectGovernanceAdapterTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/ProjectGovernanceAdapter.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/VariableGovernanceAdapter.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/DataObjectGovernanceAdapter.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/adapter
git commit -m "feat: govern projects and fields"
```

### Task 2: Implement external and database adapters with secret isolation

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/ExternalDatasourceGovernanceAdapter.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/ExternalApiGovernanceAdapter.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/DbDatasourceGovernanceAdapter.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/SecretSnapshotService.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/adapter/ExternalDatasourceGovernanceAdapterTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/adapter/DbDatasourceGovernanceAdapterTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/GovernanceSecretLeakTest.java`

**Interfaces:**
- Produces resource types `EXTERNAL_DATASOURCE`, `EXTERNAL_API`, and `DB_DATASOURCE`.

- [ ] **Step 1: Write failing tests that scan persisted JSON, diff JSON, logs, and responses for submitted secrets**

```java
@Test
public void databasePasswordNeverAppearsInSnapshotOrDiff() {
    ResourceSnapshot draft = fixture.databaseDraft("db-password");
    Assert.assertFalse(adapter.normalizeDraft(draft).getSnapshotJson().contains("db-password"));
    Assert.assertFalse(adapter.diff(fixture.effective(), draft).toJson().contains("db-password"));
}
```

- [ ] **Step 2: Run the focused tests**

Run: `mvn -pl rule-engine-server -Dtest=ExternalDatasourceGovernanceAdapterTest,DbDatasourceGovernanceAdapterTest,GovernanceSecretLeakTest test`

Expected: FAIL.

- [ ] **Step 3: Implement encrypted secret payloads and masked diffs**

Only changed flags and secret digests enter normal snapshots. Apply decrypts the request payload inside the transaction.

- [ ] **Step 4: Run the focused tests**

Run: `mvn -pl rule-engine-server -Dtest=ExternalDatasourceGovernanceAdapterTest,DbDatasourceGovernanceAdapterTest,GovernanceSecretLeakTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/SecretSnapshotService.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/External* rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/DbDatasourceGovernanceAdapter.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance
git commit -m "feat: govern external and database resources safely"
```

### Task 3: Implement model and function adapters

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/ModelGovernanceAdapter.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/FunctionGovernanceAdapter.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/adapter/ModelGovernanceAdapterTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/adapter/FunctionGovernanceAdapterTest.java`

**Interfaces:**
- Produces resource types `MODEL` and `FUNCTION`.

- [ ] **Step 1: Write failing tests for model binary digest/version, exact I/O matching, function script diff, dependency blocking, and restore**

```java
@Test
public void restoringModelCopiesContentIntoNewVersion() {
    AppliedResource applied = adapter.apply(fixture.restoreContext(5L, 2, 6));
    Assert.assertEquals(Integer.valueOf(6), applied.getVersionNo());
    Assert.assertEquals(fixture.versionDigest(5L, 2), applied.getSnapshotDigest());
}
```

- [ ] **Step 2: Run the focused tests**

Run: `mvn -pl rule-engine-server -Dtest=ModelGovernanceAdapterTest,FunctionGovernanceAdapterTest test`

Expected: FAIL.

- [ ] **Step 3: Implement adapters using existing validators and registrar refresh**

- [ ] **Step 4: Run the focused tests**

Run: `mvn -pl rule-engine-server -Dtest=ModelGovernanceAdapterTest,FunctionGovernanceAdapterTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/ModelGovernanceAdapter.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/FunctionGovernanceAdapter.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/adapter/ModelGovernanceAdapterTest.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/adapter/FunctionGovernanceAdapterTest.java
git commit -m "feat: govern models and functions"
```

### Task 4: Implement rule and experiment adapters

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/RuleGovernanceAdapter.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/ExperimentGovernanceAdapter.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleLifecycleService.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/artifact/RulePreflightValidationService.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/adapter/RuleGovernanceAdapterTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/adapter/ExperimentGovernanceAdapterTest.java`

**Interfaces:**
- Produces resource types `RULE` and `EXPERIMENT`.

- [ ] **Step 1: Write failing tests for all nine rule model snapshots, artifact publication, outbox consistency, experiment groups, rejection, and restore**

```java
@Test
public void approvingRulePublishesArtifactAndOutboxInSameTransaction() {
    AppliedResource applied = adapter.apply(fixture.ruleApprovalContext("TABLE"));
    Assert.assertNotNull(applied.getArtifactId());
    Assert.assertEquals(1, fixture.outboxRows(applied.getResourceId()).size());
    Assert.assertEquals(applied.getVersionNo(), fixture.publishedVersion(applied.getResourceId()));
}
```

- [ ] **Step 2: Run the focused tests**

Run: `mvn -pl rule-engine-server -Dtest=RuleGovernanceAdapterTest,ExperimentGovernanceAdapterTest test`

Expected: FAIL.

- [ ] **Step 3: Implement rule approval through existing preflight and artifact services**

The generic approval becomes authoritative; legacy `RuleRevisionState` writes are kept only for migration compatibility.

- [ ] **Step 4: Implement experiment aggregate application**

- [ ] **Step 5: Run the focused tests**

Run: `mvn -pl rule-engine-server -Dtest=RuleGovernanceAdapterTest,ExperimentGovernanceAdapterTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/RuleGovernanceAdapter.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/adapter/ExperimentGovernanceAdapter.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleLifecycleService.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/artifact/RulePreflightValidationService.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/adapter
git commit -m "feat: govern rules and experiments"
```

### Task 5: Route module mutations through governance

**Files:**
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleProjectController.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleVariableController.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleDataObjectController.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/RuleModelController.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ExternalDatasourceController.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/DbDatasourceController.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleFunctionController.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleDefinitionController.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleExperimentController.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/controller/mgmt/GovernedMutationControllerTest.java`

**Interfaces:**
- Consumes: `GovernanceApprovalService`.
- Produces: mutation responses containing `requestId`, `requestNo`, and `status`.

- [ ] **Step 1: Write failing contract tests proving direct mutations no longer alter effective rows**

```java
@Test
public void variableUpdateCreatesRequestWithoutChangingEffectiveRow() throws Exception {
    mockMvc.perform(put("/api/rule/variable")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":7,\"varLabel\":\"新名称\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("EDITING"));
    Assert.assertEquals("旧名称", variableMapper.selectById(7L).getVarLabel());
}
```

- [ ] **Step 2: Run the controller contract test**

Run: `mvn -pl rule-engine-server -Dtest=GovernedMutationControllerTest test`

Expected: FAIL.

- [ ] **Step 3: Replace direct management mutations with draft/request operations**

Keep adapter-only projection methods package-private or internal-service scoped.

- [ ] **Step 4: Run existing controller and service tests, updating expectations to approval semantics**

Run: `mvn -pl rule-engine-server -Dtest=*ControllerTest,*ServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt rule-engine-server/src/test/java/com/hengshucredit/rule/server/controller/mgmt
git commit -m "refactor: route governed mutations through approval"
```

### Task 6: Build the approval list and detail shell

**Files:**
- Create: `rule-engine-builder-ui/src/api/governance.js`
- Create: `rule-engine-builder-ui/src/views/approval/ApprovalList.vue`
- Create: `rule-engine-builder-ui/src/views/approval/ApprovalDetail.vue`
- Create: `rule-engine-builder-ui/src/components/approval/ApprovalTimeline.vue`
- Create: `rule-engine-builder-ui/src/components/approval/DependencyReport.vue`
- Create: `rule-engine-builder-ui/src/components/approval/GenericFieldDiff.vue`
- Modify: `rule-engine-builder-ui/src/router/index.js`
- Modify: `rule-engine-builder-ui/src/layout/layoutState.js`
- Test: `rule-engine-builder-ui/tests/unit/views/approvalList.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/views/approvalDetail.spec.js`

**Interfaces:**
- Produces routes `/approval` and `/approval/:requestId`.

- [ ] **Step 1: Write failing tests for eight tabs, filters, badges, navigation, state actions, and conflict display**

```javascript
it('renders the eight governed module tabs', () => {
  const wrapper = shallowMount(ApprovalList)
  expect(wrapper.vm.moduleTabs.map(item => item.label)).toEqual([
    '字段', '模型', '外数', '数据库', '函数', '规则', '分流', '项目'
  ])
})
```

- [ ] **Step 2: Run the focused tests**

Run: `npm test -- --run tests/unit/views/approvalList.spec.js tests/unit/views/approvalDetail.spec.js`

Expected: FAIL.

- [ ] **Step 3: Implement the list and detail shell**

Tabs map to the exact resource-type groups in the design. Action buttons use `v-permission`.

- [ ] **Step 4: Run lint and focused tests**

Run: `npm run lint`

Run: `npm test -- --run tests/unit/views/approvalList.spec.js tests/unit/views/approvalDetail.spec.js`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add rule-engine-builder-ui/src/api/governance.js rule-engine-builder-ui/src/views/approval rule-engine-builder-ui/src/components/approval rule-engine-builder-ui/src/router/index.js rule-engine-builder-ui/src/layout/layoutState.js rule-engine-builder-ui/tests/unit/views/approvalList.spec.js rule-engine-builder-ui/tests/unit/views/approvalDetail.spec.js
git commit -m "feat: add approval management pages"
```

### Task 7: Add resource-specific semantic diff renderers

**Files:**
- Create: `rule-engine-builder-ui/src/components/approval/diff/FieldResourceDiff.vue`
- Create: `rule-engine-builder-ui/src/components/approval/diff/ModelResourceDiff.vue`
- Create: `rule-engine-builder-ui/src/components/approval/diff/ExternalResourceDiff.vue`
- Create: `rule-engine-builder-ui/src/components/approval/diff/DatabaseResourceDiff.vue`
- Create: `rule-engine-builder-ui/src/components/approval/diff/FunctionResourceDiff.vue`
- Create: `rule-engine-builder-ui/src/components/approval/diff/ExperimentResourceDiff.vue`
- Create: `rule-engine-builder-ui/src/components/approval/diff/ProjectResourceDiff.vue`
- Modify: `rule-engine-builder-ui/src/components/rule/versionDiff/RuleVersionDiff.vue`
- Modify: `rule-engine-builder-ui/src/views/approval/ApprovalDetail.vue`
- Test: `rule-engine-builder-ui/tests/unit/components/approvalResourceDiff.spec.js`

**Interfaces:**
- Consumes: backend `ResourceDiff`.
- Produces: two-column semantic comparison with modified/added/removed states.

- [ ] **Step 1: Write failing renderer tests, including secret changed flags with no submitted secret text**

```javascript
it('shows only the secret changed marker', () => {
  const wrapper = mount(DatabaseResourceDiff, {
    props: { diff: { fields: [{ key: 'password', sensitive: true, changed: true }] } }
  })
  expect(wrapper.text()).toContain('密码已变更')
  expect(wrapper.text()).not.toContain('db-password')
})
```

- [ ] **Step 2: Run the renderer test**

Run: `npm test -- --run tests/unit/components/approvalResourceDiff.spec.js`

Expected: FAIL.

- [ ] **Step 3: Implement the seven renderers and reuse `RuleVersionDiff` for rules**

- [ ] **Step 4: Run lint and component tests**

Run: `npm run lint`

Run: `npm test -- --run tests/unit/components/approvalResourceDiff.spec.js tests/unit/components/ruleVersionDiff.spec.js`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add rule-engine-builder-ui/src/components/approval/diff rule-engine-builder-ui/src/components/rule/versionDiff/RuleVersionDiff.vue rule-engine-builder-ui/src/views/approval/ApprovalDetail.vue rule-engine-builder-ui/tests/unit/components
git commit -m "feat: visualize governed resource differences"
```

### Task 8: Update business pages for effective versions, pending banners, history, and restore

**Files:**
- Create: `rule-engine-builder-ui/src/components/governance/GovernanceBanner.vue`
- Create: `rule-engine-builder-ui/src/components/governance/ResourceVersionHistory.vue`
- Modify: project, variable, datasource, database, model, function, rule, experiment list/detail views under `rule-engine-builder-ui/src/views/`
- Modify: matching APIs under `rule-engine-builder-ui/src/api/`
- Test: `rule-engine-builder-ui/tests/unit/components/resourceVersionHistory.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/views/governedBusinessPages.spec.js`

**Interfaces:**
- Consumes: governance draft and version endpoints.

- [ ] **Step 1: Write failing tests for effective-only lists, pending banners, rejected fallback, and restore request navigation**

```javascript
it('keeps the effective version visible after rejection', async () => {
  governanceApi.getActiveRequest.mockResolvedValue({ data: null })
  governanceApi.listVersions.mockResolvedValue({ data: [{ versionNo: 4, effective: true }] })
  const wrapper = mountGovernedDetail()
  await flushPromises()
  expect(wrapper.text()).toContain('当前生效版本 V4')
  expect(wrapper.find('[data-testid="governance-banner"]').exists()).toBe(false)
})
```

- [ ] **Step 2: Run the focused tests**

Run: `npm test -- --run tests/unit/components/resourceVersionHistory.spec.js tests/unit/views/governedBusinessPages.spec.js`

Expected: FAIL.

- [ ] **Step 3: Update each business page without changing unrelated layout**

All edit forms save the governance draft. Rejection returns the user to the effective detail.

- [ ] **Step 4: Run lint and focused tests**

Run: `npm run lint`

Run: `npm test -- --run tests/unit/components/resourceVersionHistory.spec.js tests/unit/views/governedBusinessPages.spec.js`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add rule-engine-builder-ui/src/components/governance rule-engine-builder-ui/src/views rule-engine-builder-ui/src/api rule-engine-builder-ui/tests/unit
git commit -m "feat: connect business pages to governance"
```

### Task 9: Full verification and user-flow validation

**Files:**
- Modify: `README.md`
- Create: `rule-engine-builder-ui/tests/e2e/governance-lifecycle.spec.js`

- [ ] **Step 1: Add E2E flows**

```javascript
test('rule restore creates a new approved version', async ({ page }) => {
  await page.goto('/#/rule/101')
  await page.getByRole('button', { name: '历史版本' }).click()
  await page.getByRole('button', { name: '使用此版本' }).first().click()
  await page.getByRole('button', { name: '提交审批' }).click()
  await page.getByRole('button', { name: '审批通过' }).click()
  await expect(page.getByText('当前生效版本 V4')).toBeVisible()
})
```

Cover account/role configuration, field approval, missing dependency conflict, rule visual comparison, self-approval, artifact publication, restore to a historical rule version, secret masking, and downstream-delete blocking.

- [ ] **Step 2: Run frontend production build and lint**

Run: `npm run build`

Run: `npm run lint`

Expected: PASS.

- [ ] **Step 3: Start the frontend**

Run: `npm run dev`

Expected: port 9090 serves the console without errors. Perform the complete UI workflow and stop the process explicitly.

- [ ] **Step 4: Run frontend tests**

Run: `npm test`

Expected: all unit tests pass.

- [ ] **Step 5: Compile and start the backend**

Run: `mvn clean install -DskipTests`

Run from `rule-engine-server`: `mvn spring-boot:run`

Expected: the server starts on port 8080 and governance endpoints return HTTP 200. Stop the process explicitly.

- [ ] **Step 6: Run backend tests**

Run: `mvn test`

Expected: all non-CUDA tests pass.

- [ ] **Step 7: Run the stable dist E2E suite**

Run: `npm run test:e2e:dist`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add README.md rule-engine-builder-ui/tests/e2e/governance-lifecycle.spec.js
git commit -m "test: verify unified governance lifecycle"
```
