# 规则历史查看与项目关联治理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 让没有修订记录的历史规则仍可在全部设计器中查看真实内容，并把全局规则加入项目、移出项目改造成可审计、审批后生效的关联生命周期，消除项目页误删共享规则和成功语义失真的风险。

**Architecture:** 历史内容兼容放在九类设计器共用的 `ruleDraftMixin` 中，只在“未指定精确版本且修订列表确实为空”时读取旧内容投影，并生成不可编辑、不可派生的前端只读节点。项目规则关联作为独立治理资源 `RULE_PROJECT_BINDING` 接入现有统一审批内核；关联表仍是唯一生效投影，创建与删除只有在审批通过后才执行。项目规则列表返回关联记录 ID，让前端能以 ID 发起移出审批；项目自有规则与关联的全局规则显示不同操作和确认文案。

**Tech Stack:** Java 17、Spring Boot 3.5、MyBatis Plus、JUnit 4、Vue 3 Options API、Element Plus、Vitest、Vue Test Utils、Playwright。

## Global Constraints

- 不修改初始数据快照中的明文数据库凭据。
- 不修改侧边栏技术模块平铺设计，不增加收起按钮；保留拖拽宽度触发自动折叠/展开。
- 规则、项目与关联关系只通过数值 ID 建立引用，不用编码或名称反查。
- 用户输入的规则编码、名称和模型内容原样保留。
- 先写会失败的行为测试，确认失败原因与目标缺口一致，再写最小实现。
- 前端改动必须通过 ESLint、单元测试、构建、开发服务启动检查和浏览器完整操作验收。
- 后端改动必须通过模块测试、`mvn clean install -DskipTests`、服务启动检查和 `mvn test`。
- 本地验证服务使用隔离端口，工具超时 1800000ms，完成后主动停止，只停止本次启动的进程。

---

## Task 1: 历史规则只读内容回退

**Files:**

- Modify: `rule-engine-builder-ui/tests/unit/ruleDraftMixin.spec.js`
- Modify: `rule-engine-builder-ui/tests/unit/components/ruleDraftReadOnly.spec.js`
- Modify: `rule-engine-builder-ui/tests/unit/views/designerDraftLifecycle.spec.js`
- Modify: `rule-engine-builder-ui/src/mixins/ruleDraftMixin.js`
- Modify: `rule-engine-builder-ui/src/components/rule/RuleDraftReadOnly.vue`

### Step 1: 写历史内容回退失败测试

在 `ruleDraftMixin.spec.js` 增加以下行为测试：

```js
test('falls back to legacy effective content when no revision exists', async () => {
  definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
  definitionApi.getContent.mockResolvedValueOnce({
    data: { definitionId: 30, modelJson: '{"rules":[{"id":"legacy"}]}' },
  })
  const wrapper = mountHost()
  await flushPromises()

  expect(definitionApi.getContent).toHaveBeenCalledWith(30)
  expect(wrapper.vm.viewRevision).toMatchObject({
    id: 'legacy-content:30',
    state: 'LEGACY',
    sourceType: 'LEGACY_CONTENT',
    sourceId: '30',
    modelJson: '{"rules":[{"id":"legacy"}]}',
  })
  expect(wrapper.vm.viewRevisionLabel).toBe('历史生效内容')
  expect(wrapper.vm.canEditDraft).toBe(false)
  expect(wrapper.vm.canForkViewRevision).toBe(false)
})
```

再覆盖三条边界：修订列表查询失败时不读取旧内容；显式 `REVISION`/`VERSION` 加载失败时不回退；旧内容为空时保持无节点并给出明确错误。

### Step 2: 运行目标测试并确认失败

Run:

```powershell
cd rule-engine-builder-ui
& 'C:\Program Files\nodejs\npm.cmd' test -- tests/unit/ruleDraftMixin.spec.js
```

Expected: 新增回退测试因 `getContent` 未被调用而失败，既有精确节点测试仍通过。

### Step 3: 实现严格受限的旧内容回退

在 `refreshViewedRevision()` 的无显式 source 分支中：

1. 正常取得并排序修订。
2. 有修订时保持现有 DRAFT 优先行为。
3. 只有修订数组为空时调用 `definitionApi.getContent(definitionId)`。
4. 内容存在且 `modelJson` 非空时构造前端只读节点：

```js
{
  id: `legacy-content:${definitionId}`,
  definitionId,
  state: 'LEGACY',
  sourceType: 'LEGACY_CONTENT',
  sourceId: String(definitionId),
  modelJson: content.modelJson,
}
```

5. 旧内容不存在时抛出 `当前规则没有可查看的历史内容`，由现有错误状态接管。
6. 回退请求结束前后都复用 `isCurrentSource()` 防止旧响应覆盖新路由。

`viewRevisionLabel` 对 `LEGACY_CONTENT` 返回“历史生效内容”；`canForkViewRevision` 不包含 `LEGACY`。

`RuleDraftReadOnly.vue` 对 `revisionState === 'LEGACY'` 显示“该规则来自旧版历史内容，只读展示；如需修改，请先前往规则生命周期创建草稿。”

### Step 4: 覆盖九类设计器共享行为

在 `designerDraftLifecycle.spec.js` 的九类设计器参数化用例中，令修订接口返回空、旧内容接口返回各设计器可解析的最小模型，断言设计器加载的是旧内容而不是空白初始模型，保存按钮保持禁用，且页面显示“历史生效内容”。

### Step 5: 运行目标测试与 ESLint

Run:

```powershell
cd rule-engine-builder-ui
& 'C:\Program Files\nodejs\npm.cmd' test -- tests/unit/ruleDraftMixin.spec.js tests/unit/views/designerDraftLifecycle.spec.js
& 'C:\Program Files\nodejs\npm.cmd' run lint
```

Expected: 全部通过且无 ESLint 错误。

### Step 6: 提交历史查看修复

```powershell
git add rule-engine-builder-ui/src/mixins/ruleDraftMixin.js rule-engine-builder-ui/src/components/rule/RuleDraftReadOnly.vue rule-engine-builder-ui/tests/unit/ruleDraftMixin.spec.js rule-engine-builder-ui/tests/unit/views/designerDraftLifecycle.spec.js
git commit -m "fix: 支持历史规则只读查看"
```

---

## Task 2: 建立项目规则关联治理适配器

**Files:**

- Create: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/RuleProjectBindingGovernedResourceAdapter.java`
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/RuleProjectBindingGovernedResourceAdapterTest.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceResourceTypes.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceResourceAdapterConfiguration.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/GovernedResourceAdapterRegistryTest.java`

### Step 1: 写适配器失败测试

测试使用记录型 Mapper 代理，覆盖：

- `loadEffective(bindingId)` 只按关联 ID 读取并返回 `{id, definitionId, projectId}`。
- `normalizeDraft` 只保留关联字段，不接受名称或编码替代 ID。
- `validate` 拒绝空 ID、不存在规则、不存在项目、非全局规则、重复关联；合法关联无错误。
- `collectDependencies` 明确产生 `RULE -> definitionId` 与 `PROJECT -> projectId` 两条必需依赖。
- `apply(CREATE)` 写入一条关联并返回数据库生成的关联 ID。
- `apply(DELETE)` 只删除指定关联 ID，不删除规则、不删除项目。
- `UPDATE/ENABLE/DISABLE/RESTORE` 被拒绝，避免关联资源出现无意义动作。

### Step 2: 运行目标测试并确认缺类失败

Run:

```powershell
mvn -pl rule-engine-server -Dtest=RuleProjectBindingGovernedResourceAdapterTest test
```

Expected: 因适配器和资源类型尚不存在而失败。

### Step 3: 实现最小适配器

新增 `GovernanceResourceTypes.RULE_PROJECT_BINDING`，并把它纳入 `RULE` 页签类型集合。

适配器直接依赖 `RuleDefinitionRefMapper`、`RuleDefinitionMapper`、`RuleProjectMapper`：

- 快照只保留 `id`、`definitionId`、`projectId`，并按 ID 从服务端补充只用于展示的 `ruleName`、`projectName` 摘要；不接受客户端名称作为关联依据，使用 `CanonicalJson` 规范化。
- 创建时清空客户端传入的 `id`，由数据库生成。
- 删除时以 `ApprovalApplyContext.resourceId()` 为唯一目标。
- 通过数据库唯一键处理并发重复，审批服务把 `DuplicateKeyException` 转为冲突。
- 生效状态：创建 `ACTIVE`，删除 `DELETED`。

在配置类注册 Bean，保持现有 registry 自动收集方式不变。

### Step 4: 运行适配器与注册表测试

Run:

```powershell
mvn -pl rule-engine-server -Dtest=RuleProjectBindingGovernedResourceAdapterTest,GovernedResourceAdapterRegistryTest test
```

Expected: 全部通过。

---

## Task 3: 保证关联审批身份唯一与现有关联可引导治理

**Files:**

- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/governance/GovernanceApprovalService.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/GovernanceApprovalServiceTest.java`
- Create: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/governance/GovernanceResourceBootstrapServiceTest.java`

### Step 1: 写失败测试

覆盖：

- 两次对同一 `definitionId + projectId` 发起 CREATE，命中同一个活动草稿键而不是生成重复审批。
- 不同项目或不同规则得到不同活动键。
- 对已有 `RuleDefinitionRef.id` 发起 DELETE 时，`findResource` 会通过 bootstrap 建立基准治理版本，并以关联 ID 作为资源 ID。
- 审批 CREATE 后 request/resource/version 绑定数据库生成的关联 ID；审批 DELETE 后治理资源状态为 `DELETED`。

### Step 2: 运行测试确认失败

```powershell
mvn -pl rule-engine-server -Dtest=GovernanceApprovalServiceTest,GovernanceResourceBootstrapServiceTest test
```

### Step 3: 实现复合活动键

在 `GovernanceApprovalService.createActiveResourceKey()` 中为 `RULE_PROJECT_BINDING` 返回：

```text
RULE_PROJECT_BINDING:definitionId:projectId
```

两者都必须是大于零的数值 ID，否则拒绝草稿。现有资源 DELETE 仍使用 `RULE_PROJECT_BINDING:<bindingId>`，保证创建与删除各自的资源锁定语义清晰。

### Step 4: 运行治理核心测试

```powershell
mvn -pl rule-engine-server -Dtest=GovernanceApprovalServiceTest,GovernanceResourceBootstrapServiceTest test
```

Expected: 全部通过。

---

## Task 4: 在项目规则列表暴露关联 ID

**Files:**

- Modify: `rule-engine-model/src/main/java/com/hengshucredit/rule/model/entity/RuleDefinition.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/RuleDefinitionService.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleDefinitionServiceTest.java`
- Modify: `rule-engine-server/src/main/java/com/hengshucredit/rule/server/controller/mgmt/RuleDefinitionController.java`
- Modify: `rule-engine-server/src/test/java/com/hengshucredit/rule/server/controller/mgmt/RuleDefinitionControllerTest.java`

### Step 1: 写列表映射失败测试

给项目列表返回一条项目自有规则和一条已关联全局规则，模拟 `rule_definition_ref` 查询结果，断言：

- 项目自有规则 `projectBindingId == null`。
- 全局规则 `projectBindingId == ref.id`。
- 关联查询以 `projectId` 和当前页规则 ID 集合过滤。

### Step 2: 确认测试失败

```powershell
mvn -pl rule-engine-server -am "-Dtest=RuleDefinitionServiceTest,RuleDefinitionControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

### Step 3: 实现非持久化字段与批量映射

在 `RuleDefinition` 增加：

```java
@TableField(exist = false)
private Long projectBindingId;
```

`pageListForProject()` 在字段元数据补齐后，批量查询当前项目与当前页规则 ID 对应的 `RuleDefinitionRef`，按 `definitionId` 设置 `projectBindingId`。不逐行查询，不通过编码或名称匹配。

移除旧 `/add-global-to-project` 直接写接口及 `RuleDefinitionService.addGlobalRuleToProject()`，避免绕开治理审批；前端改造后不再引用它。保留 `project-list`、`global-list` 读接口。

### Step 4: 运行服务与控制器测试

```powershell
mvn -pl rule-engine-server -am "-Dtest=RuleDefinitionServiceTest,RuleDefinitionControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: 全部通过。

---

## Task 5: 修正项目页操作与审批展示语义

**Files:**

- Modify: `rule-engine-builder-ui/src/api/definition.js`
- Modify: `rule-engine-builder-ui/src/views/project/ProjectDetail.vue`
- Modify: `rule-engine-builder-ui/tests/unit/views/projectDetail.spec.js`
- Modify: `rule-engine-builder-ui/src/views/approval/ApprovalList.vue`
- Modify: `rule-engine-builder-ui/src/views/approval/ApprovalDetail.vue`
- Modify: `rule-engine-builder-ui/tests/unit/views/approvalList.spec.js`
- Modify: `rule-engine-builder-ui/tests/unit/views/approvalDetail.spec.js`

### Step 1: 写项目页失败测试

覆盖：

- `addGlobalToProject()` 调用 `createResourceDraft('RULE_PROJECT_BINDING', { definitionId, projectId }, 'CREATE')`，成功后提示“已生成加入项目审批草稿”并进入审批详情。
- 全局规则行显示“移出项目”，确认文案明确不会删除共享规则；以 `projectBindingId` 发起 DELETE 草稿，并进入审批详情。
- 项目自有规则行显示“申请删除规则”，继续调用规则 DELETE 草稿；提示“已生成规则删除审批草稿”，不再显示“删除成功”。
- 缺少 `projectBindingId` 时禁止移出并提示刷新，不按规则 ID 猜测关联。

### Step 2: 确认项目页测试失败

```powershell
cd rule-engine-builder-ui
& 'C:\Program Files\nodejs\npm.cmd' test -- tests/unit/views/projectDetail.spec.js
```

### Step 3: 实现 API 包装与分支操作

在 `definition.js` 增加：

```js
export function createProjectBinding(definitionId, projectId) {
  return createResourceDraft(
    'RULE_PROJECT_BINDING',
    { definitionId, projectId },
    'CREATE',
    { projectId, changeSummary: '将全局规则加入项目' }
  )
}

export function deleteProjectBinding(bindingId, projectId) {
  return createResourceDraft('RULE_PROJECT_BINDING', null, 'DELETE', {
    resourceId: bindingId,
    projectId,
    changeSummary: '将全局规则移出项目'
  })
}
```

`ProjectDetail.vue` 不再直接调用 raw request 写关联。操作列根据 `row.scope === 'GLOBAL'` 分支渲染；所有变更操作提示“生成审批草稿”，并在响应有审批 ID 时进入 `/approval/{id}`。

### Step 4: 补充审批资源文案

`ApprovalList.vue` 与 `ApprovalDetail.vue` 为 `RULE_PROJECT_BINDING` 显示“项目规则关联”；标题优先组合“将规则 #definitionId 加入项目 #projectId”，避免把创建态 `resourceId=0` 显示成资源名称。

### Step 5: 运行前端目标测试和 ESLint

```powershell
cd rule-engine-builder-ui
& 'C:\Program Files\nodejs\npm.cmd' test -- tests/unit/views/projectDetail.spec.js tests/unit/views/approvalList.spec.js tests/unit/views/approvalDetail.spec.js
& 'C:\Program Files\nodejs\npm.cmd' run lint
```

Expected: 全部通过且无 ESLint 错误。

### Step 6: 提交关联治理切片

```powershell
git add rule-engine-model rule-engine-server rule-engine-builder-ui
git commit -m "feat: 治理项目规则关联生命周期"
```

---

## Task 6: 第一切片全量验证与浏览器验收

**Files:**

- Modify if needed: `rule-engine-builder-ui/tests/e2e/full-stack.spec.js`
- Evidence only: browser screenshots under an ignored local validation directory; do not commit generated evidence unless repository convention already requires it.

### Step 1: 前端全量校验

```powershell
cd rule-engine-builder-ui
& 'C:\Program Files\nodejs\npm.cmd' run lint
& 'C:\Program Files\nodejs\npm.cmd' test
& 'C:\Program Files\nodejs\npm.cmd' run build
```

### Step 2: 前端开发服务启动检查

在隔离端口启动 `npm run dev -- --port 9190`，等待页面 HTTP 200 且浏览器控制台无启动错误，然后主动停止本次进程。

### Step 3: 后端全量校验

```powershell
mvn clean install -DskipTests
mvn test
```

### Step 4: 后端服务启动检查

在 `rule-engine-server` 使用隔离端口 `8180` 启动 `mvn spring-boot:run`，等待健康 API HTTP 200，确认无异常堆栈后主动停止本次进程。

### Step 5: 浏览器真实流程验收

使用隔离的真实前后端环境，通过 UI 完整操作，不从后端直接造业务数据跳步：

1. 登录控制台。
2. 打开至少一条没有修订记录但有旧内容的历史规则，分别抽验表格类设计器和图形类设计器，确认真实旧内容可见、不可保存、文案为“历史生效内容”。
3. 从项目详情打开“添加全局规则”，点击添加，确认进入“项目规则关联”审批草稿。
4. 在审批详情执行预检、提交；切换有审批权限的用户完成审批。
5. 返回项目详情，确认规则只在审批通过后出现，且操作为“移出项目”。
6. 点击“移出项目”，确认弹窗说明共享规则不会删除；完成审批后规则从项目列表消失，但在全局规则列表仍存在。
7. 对项目自有规则点击“申请删除规则”，确认只生成规则删除审批草稿，不显示“删除成功”。
8. 浏览器控制台无 error；网络请求无意外 4xx/5xx。

若现有数据不存在“无修订但有旧内容”的规则，只允许通过 UI 新建并走现有流程构造验收数据；不可直接写数据库或调用后端造数据跳过页面步骤。

### Step 6: 最终自审与提交

```powershell
git diff --check
git status --short
```

逐项核对本计划、产品规格、两项排除项、ID 引用铁律和测试证据。若 E2E 测试需要补强，先写失败场景再实现，完成后提交：

```powershell
git add rule-engine-builder-ui/tests/e2e/full-stack.spec.js
git commit -m "test: 覆盖历史规则与项目关联验收"
```
