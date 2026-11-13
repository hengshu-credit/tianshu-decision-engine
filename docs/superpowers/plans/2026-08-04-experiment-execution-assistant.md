# Experiment Execution Assistant Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将分流实验执行入口从原始 JSON 工具改造成面向业务人员的 Schema 字段化验证助手，并补齐可见的加载、降级、错误、执行和结果反馈。

**Architecture:** 保持现有实验执行 REST 协议和后端路由逻辑不变，在 `ExperimentList.vue` 内复用共享测试 Schema 工具维护字段表单与嵌套 JSON 的双向转换。使用请求序号隔离初始化和执行异步响应；浏览器 E2E 通过现有 operations fixtures 验证真实交互与请求载荷。

**Tech Stack:** Vue 3 Options API、Element Plus、Vitest、Vue Test Utils、Playwright、Spring Boot 3.5、JUnit 4。

---

### Task 1: 用失败测试定义执行助手状态和数据转换

**Files:**
- Modify: `rule-engine-builder-ui/tests/unit/views/experimentList.spec.js`

- [ ] 扩充测试上下文，使其包含加载状态、字段参数、模式、请求序号和弹窗状态。
- [ ] 新增测试：打开弹窗时先进入 `LOADING`，Schema 成功后生成字段表单、嵌套样例和 `SCHEMA_SAMPLE` 来源。
- [ ] 新增测试：Schema 无样例时使用字段类型默认值并标记 `FIELD_DEFAULTS`。
- [ ] 新增测试：Schema 请求失败时进入 `DEGRADED`、给出可读警告、只保留空 JSON 高级入口，不再生成固定业务样例。
- [ ] 新增测试：表单/JSON 切换保持嵌套字段一致，非法 JSON 保留错误并阻止执行。
- [ ] 新增测试：字段模式执行提交嵌套 `params` 与请求上下文；关闭弹窗和过期执行不会回写状态；重复点击不会并发提交。
- [ ] 新增测试：结果摘要辅助方法正确区分生产组、测试组命中、跳过和失败。
- [ ] Run: `C:\Program Files\nodejs\npm.cmd test -- --run tests/unit/views/experimentList.spec.js`
- [ ] 确认新增断言因功能尚未实现而失败，且失败点与需求一致。

### Task 2: 实现 Schema 驱动的状态与执行逻辑

**Files:**
- Modify: `rule-engine-builder-ui/src/views/experiment/ExperimentList.vue`

- [ ] 从 `@/utils/testSchema` 引入 `schemaFieldsToTestFields`、`flattenSchemaSample`、`buildNestedSchemaParams`、`readParamPath`。
- [ ] 新增加载状态、字段参数、编辑模式、来源、错误、警告和请求序号数据。
- [ ] 重写 `handleTest`：立即打开弹窗，重置状态，加载并规范化 Schema，生成字段和值，显式处理就绪/降级/失败。
- [ ] 删除与实验无关的固定税务/通信样例，只在没有字段时使用空对象。
- [ ] 实现 `switchToJsonMode`、`switchToManualMode`、`syncParamsToJson`、`syncJsonToParams`、`onTestJsonInput` 和 `closeTestDialog`。
- [ ] 重写 `doExecute`：按当前模式构建参数，校验 JSON，避免重复提交，捕获请求错误并防止过期响应回写。
- [ ] 添加结果标签和状态辅助方法，保持后端响应对象原样可查看。
- [ ] Run: `C:\Program Files\nodejs\npm.cmd test -- --run tests/unit/views/experimentList.spec.js`
- [ ] 确认 Task 1 的失败测试全部转绿。

### Task 3: 重构执行弹窗的业务信息层级

**Files:**
- Modify: `rule-engine-builder-ui/src/views/experiment/ExperimentList.vue`
- Modify: `rule-engine-builder-ui/tests/unit/views/experimentList.spec.js`

- [ ] 将列表操作文案改为“验证执行”，减少与生产执行混淆。
- [ ] 弹窗增加实验身份、加载/降级/错误状态和重试入口。
- [ ] 默认展示字段化入参；用次级按钮提供 JSON 高级模式。
- [ ] 将请求唯一键、进件时间放入独立“分流上下文”区域并增加业务解释。
- [ ] 执行结果使用整体状态、追踪信息、生产组卡片、测试组列表和折叠原始结果展示。
- [ ] 按 4/8/12/16/24 间距尺度补充 scoped 样式，限制弹窗内容高度并适配窄屏；不改变全局导航样式。
- [ ] 新增模板契约测试，确认关键文案、状态区和高级 JSON 入口存在。
- [ ] Run: `C:\Program Files\nodejs\npm.cmd test -- --run tests/unit/views/experimentList.spec.js`

### Task 4: 增加浏览器端业务流程覆盖

**Files:**
- Modify: `rule-engine-builder-ui/tests/e2e/support/operationsFixtures.cjs`
- Modify: `rule-engine-builder-ui/tests/e2e/operations-pages.spec.js`

- [ ] 为实验测试 Schema 和执行接口添加与后端 DTO 一致的 mock 响应。
- [ ] 新增 Playwright 场景：打开“验证执行”，确认字段表单和来源提示可见，填写嵌套字段与请求唯一键，执行后检查请求体。
- [ ] 验证生产组、测试组、追踪号和耗时摘要可见，页面无根级横向溢出和运行时错误。
- [ ] Run: `C:\Program Files\nodejs\npm.cmd run build`
- [ ] Run: `C:\Program Files\nodejs\npm.cmd run test:e2e:dist -- --grep "分流实验验证执行"`

### Task 5: 执行前端完整门禁和开发服务验收

**Files:**
- Verify only.

- [ ] Run: `C:\Program Files\nodejs\npm.cmd run lint`
- [ ] Run: `C:\Program Files\nodejs\npm.cmd test`
- [ ] Run: `C:\Program Files\nodejs\npm.cmd run build`
- [ ] 在 9190 启动 `npm run dev -- --host 127.0.0.1 --port 9190`，确认页面无编译和运行时报错后主动停止。
- [ ] Run: `C:\Program Files\nodejs\npm.cmd run test:e2e:dist`

### Task 6: 执行后端回归、全栈联调与真实浏览器验收

**Files:**
- Verify only unless发现真实缺陷需要按 TDD 修复。

- [ ] Run: `mvn clean install -DskipTests`。
- [ ] 在 8180 启动 `rule-engine-server`，带 9190 CORS 验证来源；确认健康接口或登录接口 HTTP 正常后主动停止。
- [ ] Run: `mvn test`。
- [ ] 同时启动隔离的 8180 后端与 9190 前端，设置 `E2E_BASE_URL=http://localhost:9190`，Run: `C:\Program Files\nodejs\npm.cmd run test:e2e:full`。
- [ ] 使用真实浏览器从登录、项目上下文、分流实验列表进入验证助手，逐步完成字段填写、请求上下文、执行和结果查看；禁止通过后端预造数据跳过页面步骤。
- [ ] 主动停止 8180/9190，确认保留的 8080/9090 服务未受影响。
- [ ] 运行 `git diff --check`、检查改动范围并按 `superpowers:requesting-code-review` 完成代码复审。
- [ ] 提交到 `master`，提交信息聚焦本阶段业务价值。
