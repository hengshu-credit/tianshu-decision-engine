# Console Account and RBAC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single configured console login with database-backed accounts, multi-role permissions, user ALLOW/DENY overrides, and backend-enforced menu/action authorization.

**Architecture:** Database accounts are the authority once any account exists. Role grants are unioned, then user ALLOW/DENY overrides are applied with DENY taking precedence. The backend exposes the final permission set and enforces permission codes through an annotation interceptor; the Vue console uses the same codes for menus and buttons.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis Plus, HttpSession, BCrypt, Vue 3, Element Plus, Vitest, JUnit 4.

## Global Constraints

- No shared default credentials.
- Passwords are stored only as BCrypt hashes and are never returned.
- Existing builtin credentials are accepted only while no database account exists; the first successful login provisions the database super administrator.
- Role permissions are unioned; user ALLOW adds permissions; user DENY removes permissions.
- Frontend visibility is advisory; protected backend operations always enforce the same permission code.
- At least one enabled user must retain account and role management permission.

---

### Task 1: Add account and permission persistence

**Files:**
- Modify: `rule-engine-server/src/main/resources/sql/schema.sql`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/SchemaSyncService.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/ConsoleUser.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/ConsoleRole.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/ConsolePermission.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/ConsoleUserRole.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/ConsoleRolePermission.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/ConsoleUserPermissionOverride.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/ConsoleSecurityAuditLog.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/mapper/ConsoleUserMapper.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/mapper/ConsoleRoleMapper.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/mapper/ConsolePermissionMapper.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/mapper/ConsoleUserRoleMapper.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/mapper/ConsoleRolePermissionMapper.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/mapper/ConsoleUserPermissionOverrideMapper.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/mapper/ConsoleSecurityAuditLogMapper.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/sql/ConsoleRbacSchemaSqlTest.java`

**Interfaces:**
- Produces: MyBatis entities and mappers for users, roles, permissions, membership, grants, overrides, and audit events.

- [ ] **Step 1: Write the failing schema test**

```java
@Test
public void schemaContainsConsoleRbacTables() throws Exception {
    String sql = Files.readString(Path.of("src/main/resources/sql/schema.sql"));
    Assert.assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `console_user`"));
    Assert.assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `console_user_permission_override`"));
    Assert.assertTrue(sql.contains("UNIQUE KEY `uk_console_user_username`"));
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `mvn -pl rule-engine-server -Dtest=ConsoleRbacSchemaSqlTest test`

Expected: FAIL because the tables do not exist.

- [ ] **Step 3: Add the seven tables, entities, mappers, indexes, and idempotent schema sync**

Use `effect VARCHAR(8) NOT NULL` for `ALLOW`/`DENY`, `password_hash VARCHAR(100) NOT NULL`, and unique keys on username, role code, permission code, memberships, grants, and overrides.

- [ ] **Step 4: Run the schema test**

Run: `mvn -pl rule-engine-server -Dtest=ConsoleRbacSchemaSqlTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/Console*.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/mapper/Console*.java rule-engine-server/src/main/resources/sql/schema.sql rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/SchemaSyncService.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/sql/ConsoleRbacSchemaSqlTest.java
git commit -m "feat: add console RBAC persistence"
```

### Task 2: Implement database authentication and bootstrap migration

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/consolelogin/DatabaseConsoleAccountService.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/consolelogin/ConsoleAuthController.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/consolelogin/ConsoleLoginConfiguration.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/ConsoleOperatorResolver.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/consolelogin/DatabaseConsoleAccountServiceTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/consolelogin/ConsoleAuthControllerTest.java`

**Interfaces:**
- Produces: `ConsoleLoginResult authenticate(String username, String rawPassword)`.
- Produces: session attributes `RULE_CONSOLE_USER_ID`, `RULE_CONSOLE_USERNAME`, and `RULE_CONSOLE_PERMISSION_VERSION`.

- [ ] **Step 1: Write failing tests for BCrypt login, disabled accounts, and first-login bootstrap**

```java
@Test
public void builtinLoginBootstrapsDatabaseOnlyWhenNoAccountsExist() {
    service.setBuiltinAuthenticator((u, p) -> "admin".equals(u) && "secret".equals(p));
    ConsoleLoginResult result = service.authenticate("admin", "secret");
    Assert.assertTrue(result.isAuthenticated());
    Assert.assertTrue(result.isBootstrapped());
    Assert.assertTrue(result.getPasswordHash().startsWith("$2"));
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `mvn -pl rule-engine-server -Dtest=DatabaseConsoleAccountServiceTest,ConsoleAuthControllerTest test`

Expected: FAIL because database authentication is not implemented.

- [ ] **Step 3: Implement database-first authentication**

When any user row exists, never fall back to builtin authentication. On the first successful builtin login, insert the user, system roles and permission catalog in one transaction.

- [ ] **Step 4: Update login, logout, and `/console/me`**

`/console/me` returns `userId`, `username`, `displayName`, roles, and final permission codes.

- [ ] **Step 5: Run the authentication tests**

Run: `mvn -pl rule-engine-server -Dtest=DatabaseConsoleAccountServiceTest,ConsoleAuthControllerTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add rule-engine-server/src/main/java/com/hengshucredit/rule/server/consolelogin rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/ConsoleOperatorResolver.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/consolelogin
git commit -m "feat: add database console authentication"
```

### Task 3: Resolve and enforce effective permissions

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/security/ConsolePermissionCodes.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/security/RequirePermission.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/security/ConsolePermissionService.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/security/ConsolePermissionInterceptor.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/consolelogin/ConsoleLoginConfiguration.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/security/ConsolePermissionServiceTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/security/ConsolePermissionInterceptorTest.java`

**Interfaces:**
- Produces: `Set<String> effectivePermissions(Long userId)`.
- Produces: `boolean hasPermission(Long userId, String permissionCode)`.
- Produces: `@RequirePermission("approval:approve")`.

- [ ] **Step 1: Write failing permission merge tests**

```java
@Test
public void userDenyOverridesRoleGrantAndUserAllowAddsPermission() {
    Set<String> effective = service.merge(
            Set.of("rule:view", "rule:edit"),
            Map.of("rule:edit", "DENY", "approval:approve", "ALLOW"));
    Assert.assertEquals(Set.of("rule:view", "approval:approve"), effective);
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `mvn -pl rule-engine-server -Dtest=ConsolePermissionServiceTest,ConsolePermissionInterceptorTest test`

Expected: FAIL because the permission service and annotation do not exist.

- [ ] **Step 3: Implement merge and annotation enforcement**

Return HTTP 403 with error code `PERMISSION_DENIED` for missing permissions. Exclude login, sync, open rule, and log-report endpoints from console permission evaluation.

- [ ] **Step 4: Run the permission tests**

Run: `mvn -pl rule-engine-server -Dtest=ConsolePermissionServiceTest,ConsolePermissionInterceptorTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add rule-engine-server/src/main/java/com/hengshucredit/rule/server/security rule-engine-server/src/main/java/com/hengshucredit/rule/server/consolelogin/ConsoleLoginConfiguration.java rule-engine-server/src/test/java/com/hengshucredit/rule/server/security
git commit -m "feat: enforce console permissions"
```

### Task 4: Add account and role management APIs

**Files:**
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/ConsoleUserSaveRequest.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/ConsoleRoleSaveRequest.java`
- Create: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/ConsoleUserPermissionRequest.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/ConsoleAccountManagementService.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ConsoleAccountController.java`
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ConsoleRoleController.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/ConsoleAccountManagementServiceTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/controller/mgmt/ConsoleAccountControllerTest.java`

**Interfaces:**
- Produces: `/api/rule/console/accounts`, `/api/rule/console/roles`, and `/api/rule/console/permissions`.

- [ ] **Step 1: Write failing tests for multiple roles, overrides, password reset, and last-admin protection**

```java
@Test
public void roleUnionAndUserOverridesProduceFinalPermissions() {
    ConsoleUserDetail detail = service.savePermissions(9L,
            List.of(2L, 3L),
            Map.of("rule:delete", "DENY", "approval:approve", "ALLOW"));
    Assert.assertTrue(detail.getEffectivePermissions().contains("approval:approve"));
    Assert.assertFalse(detail.getEffectivePermissions().contains("rule:delete"));
}

@Test(expected = LastAdministratorException.class)
public void cannotDisableLastAccountAdministrator() {
    service.disableUser(1L);
}
```

- [ ] **Step 2: Run the focused tests**

Run: `mvn -pl rule-engine-server -Dtest=ConsoleAccountManagementServiceTest,ConsoleAccountControllerTest test`

Expected: FAIL.

- [ ] **Step 3: Implement transactional account and role operations**

Use `@RequirePermission("account:manage")` and `@RequirePermission("role:manage")`. Every mutation appends a security audit event.

- [ ] **Step 4: Run the focused tests**

Run: `mvn -pl rule-engine-server -Dtest=ConsoleAccountManagementServiceTest,ConsoleAccountControllerTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add rule-engine-model/src/main/java/com/hengshucredit/rule/model/dto/Console*.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/ConsoleAccountManagementService.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ConsoleAccountController.java rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/ConsoleRoleController.java rule-engine-server/src/test/java/com/hengshucredit/rule/server
git commit -m "feat: add account and role management APIs"
```

### Task 5: Add frontend permission state, menus, and account management

**Files:**
- Create: `rule-engine-builder-ui/src/api/consoleAccount.js`
- Create: `rule-engine-builder-ui/src/security/permissionState.js`
- Create: `rule-engine-builder-ui/src/directives/permission.js`
- Create: `rule-engine-builder-ui/src/views/account/AccountManagement.vue`
- Modify: `rule-engine-builder-ui/src/main.js`
- Modify: `rule-engine-builder-ui/src/router/index.js`
- Modify: `rule-engine-builder-ui/src/router/authGuard.js`
- Modify: `rule-engine-builder-ui/src/layout/layoutState.js`
- Modify: `rule-engine-builder-ui/src/layout/components/LayoutSidebar.vue`
- Test: `rule-engine-builder-ui/tests/unit/security/permissionState.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/views/accountManagement.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/views/layout.spec.js`

**Interfaces:**
- Produces: `hasPermission(code)`, `filterMenus(menus, permissions)`, and `v-permission`.

- [ ] **Step 1: Write failing permission state and menu filtering tests**

```javascript
it('removes denied menus and keeps user-level allows', () => {
  setCurrentUser({ permissions: ['approval:view', 'account:view'] })
  expect(filterMenus(SIDEBAR_MENUS).map(item => item.index))
    .toContain('/account')
  expect(filterMenus(SIDEBAR_MENUS).map(item => item.index))
    .not.toContain('/rule')
})
```

- [ ] **Step 2: Run the focused frontend tests**

Run: `npm test -- --run tests/unit/security/permissionState.spec.js tests/unit/views/accountManagement.spec.js tests/unit/views/layout.spec.js`

Expected: FAIL.

- [ ] **Step 3: Implement account and role tabs**

The account detail exposes assigned roles, inherited permissions, ALLOW/DENY overrides, and the final effective result. The role detail uses a menu-grouped permission tree.

- [ ] **Step 4: Run lint and focused tests**

Run: `npm run lint`

Run: `npm test -- --run tests/unit/security/permissionState.spec.js tests/unit/views/accountManagement.spec.js tests/unit/views/layout.spec.js`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add rule-engine-builder-ui/src/api/consoleAccount.js rule-engine-builder-ui/src/security rule-engine-builder-ui/src/directives rule-engine-builder-ui/src/views/account rule-engine-builder-ui/src/main.js rule-engine-builder-ui/src/router rule-engine-builder-ui/src/layout rule-engine-builder-ui/tests/unit
git commit -m "feat: add console account and permission UI"
```

### Task 6: Verify the RBAC subsystem

**Files:**
- Modify: `README.md`
- Test: `rule-engine-builder-ui/tests/e2e/console-rbac.spec.js`

**Interfaces:**
- Consumes: all RBAC APIs and frontend permission controls.

- [ ] **Step 1: Add an E2E test for account creation, multi-role union, user DENY, hidden buttons, and backend 403**

```javascript
test('user overrides role permissions in UI and API', async ({ page, request }) => {
  await page.goto('/#/account')
  await page.getByRole('button', { name: '新增账户' }).click()
  await page.getByLabel('用户名').fill('reviewer')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('reviewer')).toBeVisible()
  const denied = await request.post('/api/rule/definition/publish/1')
  expect(denied.status()).toBe(403)
})
```

- [ ] **Step 2: Run backend compilation and tests**

Run: `mvn clean install -DskipTests`

Run: `mvn test`

Expected: all non-CUDA tests pass.

- [ ] **Step 3: Start the backend and verify HTTP 200**

Run from `rule-engine-server`: `mvn spring-boot:run`

Expected: server starts on port 8080 and the health endpoint returns 200. Stop it explicitly after verification.

- [ ] **Step 4: Start the frontend and perform the account/role flow**

Run from `rule-engine-builder-ui`: `npm run dev`

Expected: account and role pages load without console errors; a denied operation is hidden and its direct API call returns 403. Stop it explicitly.

- [ ] **Step 5: Run frontend tests**

Run: `npm test`

Expected: all unit tests pass.

- [ ] **Step 6: Commit**

```bash
git add README.md rule-engine-builder-ui/tests/e2e/console-rbac.spec.js
git commit -m "test: verify console RBAC workflow"
```
