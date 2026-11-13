# 字段校验与计费配置审批治理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将字段校验规则与计费配置的新增、修改、启停、删除统一接入审批治理，确保审批通过前生效数据不变，并保留现有查询、计费汇总和鉴权密钥即时轮换能力。

**Architecture:** 为 `FIELD_VALIDATION` 与 `BILLING_CONFIG` 建立领域专用 `GovernedResourceAdapter`，审批预检把生命周期动作传给适配器，以便准确校验内置规则和使用中规则的停用/删除。旧写接口只作为审批投影落地边界并由拦截器禁止控制台直调；前端业务页只创建审批草稿并跳转审批详情。

**Tech Stack:** Spring Boot 3.5、MyBatis Plus、JUnit 4、Vue 3、Element Plus、Vitest、Playwright。

## Global Constraints

- 所有资源与依赖关联只使用数据库 ID，不用编码或名称回溯。
- 用户输入的校验编码、计费编码、名称和值原样持久化；仅规范化枚举型字段。
- `CREATE/UPDATE/ENABLE/DISABLE/DELETE/RESTORE` 只有审批通过后才能修改生效表。
- 系统内置字段校验不可修改、启停、删除或恢复。
- 被规则输入字段引用的字段校验不可停用或删除。
- 项目鉴权密钥轮换不纳入审批，继续即时执行并记录审计。
- 前端修改必须通过 ESLint、单元测试和浏览器完整操作；后端修改必须通过编译、启动和全量测试。

---

### Task 1: 让领域适配器获得生命周期动作

**Files:**
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernedResourceAdapter.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceApprovalService.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/GovernanceApprovalServiceTest.java`

**Interfaces:**
- Produces: `default List<GovernanceIssue> validate(ResourceSnapshot draft, String action)`，默认委托现有 `validate(draft)`，保持已有适配器兼容。
- Consumes: 审批申请的 `request.getAction()`。

- [ ] **Step 1: 写失败测试**

  增加记录动作的测试适配器，断言预检 `DELETE` 草稿时收到 `DELETE`，并可返回阻断问题。

- [ ] **Step 2: 运行目标测试确认失败**

  Run: `mvn -pl rule-engine-server -Dtest=GovernanceApprovalServiceTest test`

- [ ] **Step 3: 最小实现**

  ```java
  default List<GovernanceIssue> validate(ResourceSnapshot draft,
                                         String action) {
      return validate(draft);
  }
  ```

  `GovernanceApprovalService.evaluate()` 调用 `adapter.validate(snapshot, request.getAction())`。

- [ ] **Step 4: 运行目标测试并提交**

  Run: `mvn -pl rule-engine-server -Dtest=GovernanceApprovalServiceTest test`

  Commit: `feat: pass lifecycle action to governance validation`

### Task 2: 字段校验规则审批适配器

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/FieldValidationGovernedResourceAdapter.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceResourceTypes.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceResourceAdapterConfiguration.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleFieldValidationController.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleFieldValidationService.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/FieldValidationGovernedResourceAdapterTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/GovernedResourceAdapterRegistryTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleFieldValidationServiceTest.java`

**Interfaces:**
- Produces: 资源类型 `FIELD_VALIDATION`，归入 `FIELD` 审批页签。
- Consumes: `RuleFieldValidationMapper`、`RuleProjectMapper`、`RuleDefinitionInputFieldMapper`。

- [ ] **Step 1: 写适配器失败测试**

  覆盖：原样保留编码和值、剔除展示字段；项目依赖；非法类型/值/项目/状态；重复编码；不可改变编码与范围；内置规则所有变更被拒；被引用规则停用/删除被拒；新增、更新、启停和软删除只在 `apply` 修改投影。

- [ ] **Step 2: 运行测试确认失败**

  Run: `mvn -pl rule-engine-server -Dtest=FieldValidationGovernedResourceAdapterTest test`

- [ ] **Step 3: 实现适配器及注册**

  ```java
  public static final String FIELD_VALIDATION = "FIELD_VALIDATION";
  // FIELD tab: VARIABLE, DATA_OBJECT, FIELD_VALIDATION
  ```

  校验类型限定为 `REQUIRED/REGEX/MIN_VALUE/MAX_VALUE/MIN_LENGTH/MAX_LENGTH/IN/NOT_IN`；删除使用 `status=-1`，停用使用 `status=0`。

- [ ] **Step 4: 封住旧写入口并隐藏软删除记录**

  给字段校验 POST、PUT、DELETE 增加 `@GovernedProjectionMutation`；`pageList` 排除 `status=-1`，唯一性检查也排除软删除记录。

- [ ] **Step 5: 运行目标测试并提交**

  Run: `mvn -pl rule-engine-server -Dtest=FieldValidationGovernedResourceAdapterTest,GovernedResourceAdapterRegistryTest,RuleFieldValidationServiceTest test`

  Commit: `feat: govern field validation lifecycle`

### Task 3: 计费配置审批适配器

**Files:**
- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/BillingConfigGovernedResourceAdapter.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceResourceTypes.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceResourceAdapterConfiguration.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/BillingController.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleBillingService.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/BillingConfigGovernedResourceAdapterTest.java`
- Test: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleBillingServiceTest.java`

**Interfaces:**
- Produces: 资源类型 `BILLING_CONFIG`，归入 `PROJECT` 审批页签。
- Consumes: 项目、规则、外数 API、数据库资源 ID；输出对应治理依赖。

- [ ] **Step 1: 写适配器失败测试**

  覆盖：编码原样保留；全局项目归零；项目/计费对象依赖；非法对象类型、计费方式、金额、状态、币种和时间范围；对象 ID 与类型不匹配；重复编码；已有配置编码与范围不可变；新增、更新、启停、软删除。

- [ ] **Step 2: 运行测试确认失败**

  Run: `mvn -pl rule-engine-server -Dtest=BillingConfigGovernedResourceAdapterTest test`

- [ ] **Step 3: 实现适配器及注册**

  ```java
  public static final String BILLING_CONFIG = "BILLING_CONFIG";
  // PROJECT tab: PROJECT, BILLING_CONFIG
  ```

  `billingTarget` 限定 `ENGINE/API/DB`，`chargeType` 限定 `COUNT/SUCCESS/DURATION/FIXED`，单价不得为负，生效时间不得晚于失效时间。

- [ ] **Step 4: 封住旧写入口并隐藏软删除记录**

  给计费配置 POST、PUT、DELETE 增加 `@GovernedProjectionMutation`；保留汇总刷新接口即时执行；配置分页排除 `status=-1`。

- [ ] **Step 5: 运行目标测试并提交**

  Run: `mvn -pl rule-engine-server -Dtest=BillingConfigGovernedResourceAdapterTest,GovernedResourceAdapterRegistryTest,RuleBillingServiceTest test`

  Commit: `feat: govern billing configuration lifecycle`

### Task 4: 业务页面改为生成审批草稿

**Files:**
- Modify: `rule-engine-builder-ui/src/api/variable.js`
- Modify: `rule-engine-builder-ui/src/api/billing.js`
- Modify: `rule-engine-builder-ui/src/views/variable/VariableList.vue`
- Modify: `rule-engine-builder-ui/src/views/billing/BillingList.vue`
- Modify: `rule-engine-builder-ui/src/views/approval/ApprovalList.vue`
- Modify: `rule-engine-builder-ui/src/views/approval/ApprovalDetail.vue`
- Test: `rule-engine-builder-ui/tests/unit/views/variableList.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/views/billingList.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/views/approvalListResources.spec.js`
- Test: `rule-engine-builder-ui/tests/unit/views/approvalDetail.spec.js`

**Interfaces:**
- Produces: `create/update/deleteFieldValidationDraft` 与 `create/update/deleteBillingConfigDraft`，返回审批申请。
- Consumes: `createResourceDraft(resourceType, snapshot, action, options)`。

- [ ] **Step 1: 写前端失败测试**

  断言新增、编辑、删除只调用审批草稿 API，成功后跳转 `/approval/{requestId}`，不刷新生效列表；审批列表和详情正确显示资源名称与类型。

- [ ] **Step 2: 运行目标测试确认失败**

  Run: `npm test -- --run tests/unit/views/variableList.spec.js tests/unit/views/billingList.spec.js tests/unit/views/approvalListResources.spec.js tests/unit/views/approvalDetail.spec.js`

- [ ] **Step 3: 实现 API 与页面流程**

  页面提示“审批前不影响生效配置”，弹窗标题与主按钮改为“审批草稿”，删除确认说明审批后生效。编辑时禁用稳定身份字段；密钥轮换相关界面和 API 不修改。

- [ ] **Step 4: 运行目标测试、Lint、构建并提交**

  Run: `npm test -- --run tests/unit/views/variableList.spec.js tests/unit/views/billingList.spec.js tests/unit/views/approvalListResources.spec.js tests/unit/views/approvalDetail.spec.js`

  Run: `npm run lint`

  Run: `npm run build`

  Commit: `feat: route validation and billing changes to approval`

### Task 5: 全量回归与浏览器验收

**Files:**
- Modify only if verification exposes a defect in the files above.

- [ ] **Step 1: 后端完整校验**

  Run: `mvn clean install -DskipTests`

  启动 `rule-engine-server` 到 8180，确认健康接口 HTTP 200 后主动停止。

  Run: `mvn test`

- [ ] **Step 2: 前端完整校验**

  启动前端到 9190，确认页面无启动错误后主动停止。

  Run: `npm test -- --run`

  Run: `npm run lint`

  Run: `npm run build`

- [ ] **Step 3: 浏览器完整业务验收**

  使用页面依次新建项目级字段校验与计费配置，验证审批前列表不可见、审批详情预检有效、提交审批后列表可见；再从页面发起编辑或停用，验证审批前旧配置仍生效、审批后新状态生效。全程不通过后端直接造数据跳步。

- [ ] **Step 4: 最终复核与提交**

  检查 `git diff`、前后端数据契约、ID 关联、资源类型页签、错误文案和浏览器截图；修复问题后重复对应测试。

