# Unified Governance Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reusable approval, immutable version, dependency snapshot, conflict, restore, and audit engine for every governed resource.

**Architecture:** Business tables remain the current effective projection. A generic governance service owns request/version state and delegates module-specific snapshot, validation, dependency, diff, and apply behavior to registered adapters.

**Tech Stack:** Java 17, Spring Boot transactions, MyBatis Plus, Fastjson, AES-GCM `CredentialCipher`, SHA-256 canonical JSON, JUnit 4.

## Global Constraints

- One active EDITING or PENDING request per resource.
- PENDING snapshots and terminal requests are immutable.
- Approval success creates the next immutable version and applies it in one transaction.
- Rejection terminates the draft and leaves the effective version unchanged.
- Restore copies an old snapshot into a new version.
- Dependencies must be currently effective; pending requests never satisfy dependencies.
- Missing or invalid dependencies cannot be force-approved.
- All references use IDs and explicit ref types.

---

### Task 1: Add governance persistence and domain types

**Files:**
- Modify: `rule-engine-server/src/main/resources/sql/schema.sql`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/SchemaSyncService.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/enums/GovernanceRequestStatus.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/enums/GovernanceAction.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/GovernedResource.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/GovernedResourceVersion.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/GovernanceApprovalRequest.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/GovernanceApprovalEvent.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/GovernanceDependencySnapshot.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/mapper/GovernedResourceMapper.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/mapper/GovernedResourceVersionMapper.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/mapper/GovernanceApprovalRequestMapper.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/mapper/GovernanceApprovalEventMapper.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/mapper/GovernanceDependencySnapshotMapper.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/sql/GovernanceSchemaSqlTest.java`

**Interfaces:**
- Produces: the generic governance persistence model.

- [ ] **Step 1: Write the failing schema and enum transition tests**

```java
@Test
public void rejectedIsTerminal() {
    Assert.assertFalse(GovernanceRequestStatus.REJECTED.canTransitionTo(
            GovernanceRequestStatus.EDITING));
}
```

- [ ] **Step 2: Run the focused tests**

Run: `mvn -pl rule-engine-server -Dtest=GovernanceSchemaSqlTest test`

Expected: FAIL.

- [ ] **Step 3: Add tables, indexes, entities, mappers, and transitions**

Enforce a database uniqueness strategy for active requests using `active_resource_key`, set only for EDITING/PENDING and cleared on terminal transition.

- [ ] **Step 4: Run the tests**

Run: `mvn -pl rule-engine-server -Dtest=GovernanceSchemaSqlTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add rule-engine-model/src/main/java/com/hengshucredit/rule/model/enums/Governance*.java rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/Govern*.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/mapper/Govern*.java rule-engine-server/src/main/resources/sql/schema.sql rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/SchemaSyncService.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/sql/GovernanceSchemaSqlTest.java
git commit -m "feat: add unified governance persistence"
```

### Task 2: Define adapter, snapshot, diff, and validation contracts

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernedResourceAdapter.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernedResourceAdapterRegistry.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/ResourceSnapshot.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/ResourceDependencyRef.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/ResourceDiff.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceIssue.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/ApprovalApplyContext.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/GovernedResourceAdapterRegistryTest.java`

**Interfaces:**
- Produces: the exact `GovernedResourceAdapter` interface in the design specification.

- [ ] **Step 1: Write failing duplicate and unknown adapter tests**

```java
@Test(expected = IllegalStateException.class)
public void duplicateResourceTypeIsRejected() {
    new GovernedResourceAdapterRegistry(List.of(adapter("RULE"), adapter("RULE")));
}

@Test(expected = IllegalArgumentException.class)
public void unknownResourceTypeIsRejected() {
    new GovernedResourceAdapterRegistry(List.of(adapter("RULE"))).require("MODEL");
}
```

- [ ] **Step 2: Run the registry test**

Run: `mvn -pl rule-engine-server -Dtest=GovernedResourceAdapterRegistryTest test`

Expected: FAIL.

- [ ] **Step 3: Implement immutable value objects and deterministic registry lookup**

- [ ] **Step 4: Run the registry test**

Run: `mvn -pl rule-engine-server -Dtest=GovernedResourceAdapterRegistryTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/GovernedResourceAdapterRegistryTest.java
git commit -m "feat: define governed resource adapter contracts"
```

### Task 3: Implement draft, submit, reject, cancel, approve, and restore

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceApprovalService.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/GovernanceDraftRequest.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/GovernanceSubmitRequest.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/GovernanceReviewRequest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/GovernanceApprovalServiceTest.java`

**Interfaces:**
- Produces: `createDraft`, `saveDraft`, `submit`, `approve`, `reject`, `cancel`, and `createRestoreDraft`.

- [ ] **Step 1: Write failing state-machine tests**

```java
@Test
public void rejectLeavesEffectiveVersionAndTerminatesDraft() {
    GovernanceApprovalRequest pending = fixture.pendingUpdate(7L, 3L);
    service.reject(pending.getId(), review("字段配置不符合要求"));
    Assert.assertEquals("REJECTED", fixture.request(pending.getId()).getStatus());
    Assert.assertEquals(Long.valueOf(3L), fixture.resource(7L).getEffectiveVersionId());
    Assert.assertNull(fixture.request(pending.getId()).getActiveResourceKey());
}
```

- [ ] **Step 2: Run the service test**

Run: `mvn -pl rule-engine-server -Dtest=GovernanceApprovalServiceTest test`

Expected: FAIL.

- [ ] **Step 3: Implement EDITING and PENDING behavior**

Use optimistic `lock_version` checks and freeze `submitted_snapshot_json` on submit.

- [ ] **Step 4: Implement approval and restore behavior**

Lock the request and resource, create version N+1, call the adapter, update the effective pointer, and append the event in one transaction.

- [ ] **Step 5: Run the service test**

Run: `mvn -pl rule-engine-server -Dtest=GovernanceApprovalServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/Governance*.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceApprovalService.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/GovernanceApprovalServiceTest.java
git commit -m "feat: implement governance approval state machine"
```

### Task 4: Add dependency preflight and conflict detection

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceDependencyService.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/GovernancePreflightReport.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceApprovalService.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/GovernanceDependencyServiceTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/GovernanceConflictTest.java`

**Interfaces:**
- Produces: `GovernancePreflightReport preflight(requestId)`.
- Produces: deterministic dependency digest from sorted ID-based references.

- [ ] **Step 1: Write failing tests for missing, disabled, deleted, type-mismatched, changed-version, and changed-digest dependencies**

```java
@Test
public void missingDependencyBlocksSubmission() {
    fixture.adapterReturnsDependency("VARIABLE", 404L);
    GovernancePreflightReport report = service.preflight(fixture.editingRequestId());
    Assert.assertFalse(report.isValid());
    Assert.assertEquals("DEPENDENCY_NOT_FOUND", report.getErrors().get(0).getCode());
}

@Test
public void dependencyVersionChangeConflictsApproval() {
    Long requestId = fixture.submittedRequestDependingOn("MODEL", 8L, 2);
    fixture.activateVersion("MODEL", 8L, 3);
    service.approve(requestId, review("通过"));
    Assert.assertEquals("CONFLICT", fixture.request(requestId).getStatus());
}
```

- [ ] **Step 2: Run the focused tests**

Run: `mvn -pl rule-engine-server -Dtest=GovernanceDependencyServiceTest,GovernanceConflictTest test`

Expected: FAIL.

- [ ] **Step 3: Implement submit-time snapshots and approval-time revalidation**

Errors block submission. Base or dependency changes move PENDING requests to CONFLICT.

- [ ] **Step 4: Run the focused tests**

Run: `mvn -pl rule-engine-server -Dtest=GovernanceDependencyServiceTest,GovernanceConflictTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/GovernancePreflightReport.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance
git commit -m "feat: validate governance dependencies and conflicts"
```

### Task 5: Add governance APIs and permission enforcement

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/GovernanceApprovalController.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/GovernanceApprovalQuery.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/controller/mgmt/GovernanceApprovalControllerTest.java`

**Interfaces:**
- Produces:
  - `GET /api/rule/governance/requests`
  - `GET /api/rule/governance/requests/{id}`
  - `POST /api/rule/governance/drafts`
  - `PUT /api/rule/governance/requests/{id}/draft`
  - `POST /api/rule/governance/requests/{id}/preflight`
  - `POST /api/rule/governance/requests/{id}/submit`
  - `POST /api/rule/governance/requests/{id}/approve`
  - `POST /api/rule/governance/requests/{id}/reject`
  - `POST /api/rule/governance/requests/{id}/cancel`
  - `GET /api/rule/governance/resources/{type}/{id}/versions`
  - `POST /api/rule/governance/resources/{type}/{id}/restore`

- [ ] **Step 1: Write failing controller tests for status codes, permission denial, pagination, and terminal immutability**

```java
@Test
public void approveRequiresPermission() throws Exception {
    mockMvc.perform(post("/api/rule/governance/requests/12/approve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"comment\":\"通过\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("PERMISSION_DENIED"));
}
```

- [ ] **Step 2: Run the controller test**

Run: `mvn -pl rule-engine-server -Dtest=GovernanceApprovalControllerTest test`

Expected: FAIL.

- [ ] **Step 3: Implement the controller and stable error mapping**

Use `approval:view`, `approval:submit`, and `approval:approve`.

- [ ] **Step 4: Run the controller test**

Run: `mvn -pl rule-engine-server -Dtest=GovernanceApprovalControllerTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/GovernanceApprovalQuery.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/GovernanceApprovalController.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/controller/mgmt/GovernanceApprovalControllerTest.java
git commit -m "feat: expose governance approval APIs"
```

### Task 6: Add legacy version migration

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceMigrationService.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/GovernanceMigrationController.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/GovernanceMigrationServiceTest.java`

**Interfaces:**
- Produces: idempotent migration from rule/model/function/experiment versions and V1 snapshots for resources without history.

- [ ] **Step 1: Write failing migration idempotency tests**

```java
@Test
public void migrationIsIdempotent() {
    service.migrateAll();
    long first = fixture.versionCount();
    service.migrateAll();
    Assert.assertEquals(first, fixture.versionCount());
}
```

- [ ] **Step 2: Run the migration test**

Run: `mvn -pl rule-engine-server -Dtest=GovernanceMigrationServiceTest test`

Expected: FAIL.

- [ ] **Step 3: Implement source-keyed migration**

Each migrated version stores `legacy_source_type` and `legacy_source_id`; unique indexing prevents duplicates.

- [ ] **Step 4: Run the migration test twice**

Run: `mvn -pl rule-engine-server -Dtest=GovernanceMigrationServiceTest test`

Expected: PASS with identical row counts on the second invocation.

- [ ] **Step 5: Commit**

```bash
git add rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceMigrationService.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/GovernanceMigrationController.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/GovernanceMigrationServiceTest.java
git commit -m "feat: migrate legacy versions into governance"
```

### Task 7: Verify the governance core

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Run backend compilation**

Run: `mvn clean install -DskipTests`

Expected: SUCCESS.

- [ ] **Step 2: Run governance-focused tests**

Run: `mvn -pl rule-engine-server -Dtest=Governance*Test test`

Expected: PASS.

- [ ] **Step 3: Run the full backend suite**

Run: `mvn test`

Expected: all non-CUDA tests pass.

- [ ] **Step 4: Start the server and smoke the governance endpoints**

Run from `rule-engine-server`: `mvn spring-boot:run`

Expected: HTTP 200 for request listing and a full create-submit-approve flow using a test adapter. Stop the process explicitly.

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: document unified governance core"
```
