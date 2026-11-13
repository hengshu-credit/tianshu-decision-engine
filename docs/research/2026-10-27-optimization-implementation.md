# 天枢逐项修复与逻辑、UI 复核记录

日期：2026-10-27。关联：[36 项计划与当前状态](../plans/2026-10-27-engine-optimization-requirements.md)。本文件记录上轮研究后的实施，不将全部规划项标为完成。

内容选择器和 Monaco 的后续交互调整、最新前端验证结果，见[内容选择器与编辑交互修复](2026-10-27-content-picker-refinement.md)。下文的整体引擎测试数字保留为本阶段快照。

## 本轮处理范围

| 计划项 | 实际修复 | 复核结果与边界 |
|---|---|---|
| R33 表格字段设置 | 将原来依赖 DOM 隐藏的入口统一接入声明式列顺序；补齐目录与表头不一致的字段；账户/角色分开存偏好；空字段偏好可恢复；固定操作列保留原位置 | 20 个入口验证隐藏、排序、查询刷新、重载、560px 和键盘；保留账单当前状态入口和日志时间固定列；实机数据库字段隐藏刷新后仍生效 |
| R12 矩阵正确性 | 复杂交叉表按条件组合身份重映射单元格，删除中间分段不再错配；新组合留空；增删维度、换字段和删除有结果的分段先说明影响；普通交叉表删除/换维度也保护已有结果 | 单元测试覆盖重排、插入、取消、零值和 false；1440/560px E2E；实机 8×6 删除分段后，与原矩阵剩余组合逐项比对一致。矩阵规模预算仍未完成 |
| R10 执行问题定位 | UNIQUE 冲突返回中文原因、对应规则行和可展开技术详情；测试弹窗定位到实际规则行；只有画布与测试快照一致时允许定位；执行结束自动滚动到结果 | 实机两行命中显示第 1、2 行，点击第二行定位后焦点为 `$.rules[1]`；1280×720 和 560×720 均验证定位按钮在可视区。旧无参数编译脚本兼容；诊断最多返回 20 行。其他运行时错误类型未统一结构化 |
| R03 / R34 执行日志索引 | 新库和旧库补齐 `(execution_project_id, create_time, id)` 索引；先补字段，再建索引；重复启动不重复建索引 | 迁移测试通过；本地 MySQL EXPLAIN 的候选索引包含新索引。小样本下优化器仍选择历史索引，不能据此宣称生产性能提升；大日志容量仍待验证 |
| R18 / R25 模型预热恢复 | 提供重新验证已发布 ONNX 模型的接口和运行状态入口；失败后可重试，预热中不重复启动；保留摘要校验和 readiness 门禁 | 失败后恢复、并发重试、CPU 回退、只读发布快照均有测试；本地真实重试 10 个模型全部成功，1 个 CPU 回退，最终 readiness=UP |
| R29 节点操作权限 | 规则/模型全局预热要求全局及所有项目的读写权限；项目限制、项目拒绝和全局只读不能绕过；受限账号只收到节点汇总，不收到其他项目失败资源详情 | 服务端控制器及权限测试、前端权限测试、受限/非受限账号浏览器场景覆盖；完整业务模块的角色验收仍待后续执行 |
| R23 空跑子规则 | 直接子规则和内部规则数据源均继承根执行的 dryRun、外数开关、离线回放、强制刷新及名单匹配时间；工作线程携带不可变策略；内部执行完成恢复外层会话；受限执行不读写正式响应缓存 | 真实 QLExpress 的子规则名单动作返回 SKIPPED_DRY_RUN / SKIPPED_REPLAY；另复现并修复工作线程策略丢失、正式缓存混用及内层退出清空外层会话，覆盖内层结束后的外层赋值。实验推演向导尚未实施 |
| R20 测试行为说明 | 规则测试、设计器测试和 API 场景说明真实来源调用、名单动作和计费 | 保留现有执行行为并明确影响；**离线试算模式尚未实现**，不能将该提示当作副作用隔离功能 |

## 代码逻辑复核重点

1. 条件组合使用编辑会话内对象身份关联，不向用户模型写入自动派生字段。加载已保存模型时按原坐标初始化；后续增删重排才按组合映射。更换维度需要用户确认，原结果不会无提示地对应到新含义。
2. UNIQUE 判定先于动作；临时变量同时避让实际表达式和现有 VarContext 保留名称目录，避免覆盖未在当前条件使用的业务字段。常量保护、FIRST/ALL 行为和旧脚本调用形式保留。
3. 错误定位使用发起测试时的画布快照。测试后改变配置会阻止旧位置定位；等测试弹窗完全关闭后才移动焦点，避免弹窗焦点恢复把定位抢回去。
4. 表格操作与固定位置仍由 Vue/Element Plus 管理。没有通过修改表头和单元格 DOM 伪造显示状态。字段定义覆盖实际数据列，固定列在设置中标注位置并限制排序。
5. 预热重试复用现有模型加载器，只读发布版本快照，不修改模型内容来使预热成功。重试前清理上一轮状态，当前轮全部通过后才变为 READY；同时到来的重试复用当前进度。
6. 节点级操作的权限检查在服务端执行；前端仅反映权限。返回受限汇总时按允许字段重建结果，不把原失败对象直接返回。
7. 空跑等执行策略以不可变值绑定请求上下文，直接子规则、内部规则数据源和来源工作线程使用相同策略，但保留独立的字段、快照和依赖解析。工作线程快照不会随调用方后续修改而变化。内部规则退出后恢复原会话；特殊策略的内部规则调用不与正式请求共用响应缓存及 single-flight。普通 HTTP 数据源原有缓存行为不受影响。
8. 模型预热重试返回后的状态不会被先发后到的旧指标请求覆盖。关闭面板后旧响应失效。
9. 测试结果自动滚动在 Vue 完成渲染后执行，且再次检查请求编号、弹窗打开状态和结果存在，关闭后的迟到响应不能滚动其他页面；结果页保留“本次输入”用于核对测试参数。

## 真实 UI 操作与证据

证据目录：`.codex-validation/optimization-20261027/`。

- `database-columns-live.png`：数据库列表隐藏 JDBC URL 后刷新，仍不显示该列；随后恢复默认。
- `matrix-remap-live.json` / `.png`：本地 8×6 样例删除第二个分段前后的完整值矩阵。先取消确认，值保持；再次确认删除后保留原第 1、2、5、6、7、8 行对应的值。未保存这些验收修改。
- `execution-diagnostic-live.png`：合成输入触发 UNIQUE 冲突，显示中文原因及两个规则行。
- `execution-diagnostic-visible.png`：补充可见性修复后的截图；720px 高窗口中，无需人工滚动即可看到中文冲突原因及两行定位按钮，按钮实际下边界分别约 528px、595px。
- `execution-location-live.png`：点击第二行定位，弹窗关闭并聚焦真实第二行。离开时放弃临时画布修改。
- `root-policy-ui-smoke.png`：最后一版后端启动后，通过 UI 输入合成参数，规则执行成功且返回 `amount: 123`；浏览器控制台无错误。
- `model-warmup-live.png` / `model-warmup-live-result.json`：模型预热指标的实机显示与真实重试结果。
- `workbench-index-explain.log`：只读 JDBC 执行计划检查；未通过创建业务数据或更改项目编码伪造索引命中。

模型失败重试的 UI 分支用模拟 API 的浏览器场景验证；失败恢复和并发由故障注入的隔离单元测试验证。真实本地模型均健康，额外通过已鉴权接口重试并读取 UI 状态，没有为了触发失败去破坏发布模型。

## 可复核的关键用例

| 关注点 | 用例入口 | 检查的行为 |
|---|---|---|
| 唯一命中 | [DecisionTableCompilerTest](../../rule-engine-core/src/test/java/com/hengshucredit/rule/core/compiler/DecisionTableCompilerTest.java) | 多行冲突不执行动作、条件只求值一次、诊断行正确、旧无参数脚本兼容、临时变量不覆盖用户字段 |
| 矩阵结果 | [advancedCrossTable.spec.js](../../rule-engine-web/tests/unit/views/advancedCrossTable.spec.js)、[matrix-editing.spec.js](../../rule-engine-web/tests/e2e/matrix-editing.spec.js) | 删除中间分段、重排、插入及取消确认后，结果仍对应原条件组合 |
| 字段设置 | [table-column-preferences.spec.js](../../rule-engine-web/tests/e2e/table-column-preferences.spec.js) | 20 个页面/表格场景的真实渲染顺序、隐藏、重载、键盘和窄屏；另通过 Vue AST 扫描确认 21 个组件引用位置全部绑定列状态，见 `column-binding-audit.json` |
| 执行结果与定位 | [execution-issue-location.spec.js](../../rule-engine-web/tests/e2e/execution-issue-location.spec.js) | 短窗口自动显示成功/失败结果；两种宽度下定位按钮在可视区；关闭弹窗后焦点落到实际规则行 |
| 嵌套执行隔离 | [RuleRuntimeInvokerTest](../../rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/RuleRuntimeInvokerTest.java)、[ExternalApiInvokeServiceTest](../../rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/ExternalApiInvokeServiceTest.java) | 名单动作遵守空跑/离线限制、工作线程策略不丢失、内层退出恢复外层赋值、试运行不污染正式响应缓存 |
| 预热与权限 | [OnnxModelWarmupRunnerTest](../../rule-engine-server/src/test/java/com/hengshucredit/rule/server/service/OnnxModelWarmupRunnerTest.java)、[ExecutionMetricsGlobalPermissionTest](../../rule-engine-server/src/test/java/com/hengshucredit/rule/server/controller/mgmt/ExecutionMetricsGlobalPermissionTest.java)、[model-warmup-retry.spec.js](../../rule-engine-web/tests/e2e/model-warmup-retry.spec.js) | 故障恢复、并发重试、发布快照约束、全局操作鉴权及受限账号信息隔离 |

## 验证与未完成边界

使用仓库要求的构建、启动、单元测试和浏览器验证流程。第一阶段完整 dist 浏览器回归为 474/474；随后增加执行定位、模型恢复与权限检查，并复现、修复短窗口结果不可见问题。最终统计见本目录日志及下方收尾记录。

最终验收日志：`backend-build-final.log`、`backend-final.log`、`web-build-final.log`、`web-final.log`、`all-dist-e2e-final.log`、`lint-final.log`。复现失败日志与修复后的结果分别保留。

### 收尾结果

| 校验 | 实际结果 |
|---|---|
| `mvn clean install -DskipTests` | 全部模块构建、安装成功；日志未出现 deprecated API、unchecked/unsafe 警告 |
| `mvn spring-boot:run` | 在 18081 启动成功，模型预热结束后 readiness 为 UP；对应 Vite 在 19091 启动并完成真实 UI 操作 |
| `mvn test` | 2936 项：2869 通过、67 按默认条件跳过，0 failures、0 errors；日志与 Surefire XML 统计一致 |
| `npm test` | 306 个文件、3579 项全部通过 |
| `npm run lint` / `npm run build` | 均通过；现有大 bundle 提示和 Node localStorage 实验提示保留，没有隐藏告警 |
| `npm run test:e2e:dist` | 479 / 479 通过，约 13.1 分钟；使用真实 dist 和模拟 API，包含本轮新增场景，不能等同于 479 条真实外部依赖联调 |
| 真实浏览器 | 矩阵取消/删除后对应关系、字段偏好重载、规则成功输出、中文冲突诊断、结果可见性、定位焦点和模型状态通过；最后一轮控制台错误为空 |
| 清理 | 临时画布修改未保存，临时页签关闭；18081/19091 无监听，原 8080/9090 服务仍在运行 |

67 项跳过清单保留于 `skipped-tests.json`，涵盖需要独立数据库、容量、恢复、外部模型资产或显式开启的测试，不能计为已通过。复现日志 `internal-policy-before.log` 与 `result-visibility-before.log` 分别证明嵌套策略/会话缺陷和短窗口结果不可见问题在修复前确实存在。

仍未完成：离线试算新模式、所有编辑弹窗的统一保护、大目录/大矩阵预算、生产容量与灾备、五名真实业务用户的任务实验，以及计划中其他尚未实施的产品增强。当前修改和测试结果不能用来宣称 36 项已全部完成。

开发验证使用独立端口；验证服务按 1800000ms 设置上限，完成后主动停止。原有开发服务与任务开始前已有的部署文档/打包脚本改动保持原状态。已有发布制品不可变，编译器修复需要正常重新编译和发布后生效。

本轮交付为工作区源码及验证记录，未替换原有运行服务。应用编译器和诊断契约的更新时，应同步使用本次构建的 server、SDK/runtime 核心依赖，再按正常回归、审批、发布流程更新相关规则；本轮未验收旧版客户端与新版编译脚本的混合部署。

## 提交前合并复核（2026-10-28）

复核当前工作区的引擎、权限、目录选择、矩阵编辑、列设置、加载交互及部署文档改动，补充修复两处问题：

- 历史 UNIQUE 编译脚本在动作之后调用无参数冲突函数，不能声称“本次未执行动作”。现在明确提示核对执行记录并重新编译发布；回归测试实际执行两次副作用后触发旧调用，确认提示与事实一致。新编译脚本仍先检查冲突，冲突时动作调用次数为 0。
- `PACKAGE_SKIP_JAVA=true` 与 `--with-tests` 同时启用时，构建记录明确只运行前端检查，不再把未运行的后端测试记为已执行。

当前代码的复核结果：

| 检查 | 结果 |
|---|---|
| `mvn clean install -DskipTests` | 全模块成功，未出现 deprecated API、unchecked/unsafe 类警告 |
| `mvn spring-boot:run` | 修复后的代码在独立端口 18089 启动，readiness 为 UP |
| `mvn test` | 2938 项：2871 通过、67 跳过，0 failures、0 errors |
| `npm run dev` | 独立 19099 开发页面加载成功，登录页控制台无 warn/error |
| `npm test` | 309 个文件、3599 项通过 |
| `npm run lint` / `npm run build` | 通过；保留已有大 bundle 和 Node localStorage 实验提示 |
| `npm run test:e2e:dist` | 507 项通过，约 13.3 分钟；真实前端与模拟 API 的完整浏览器回归 |
| 部署检查 | 质量工具 43/43；server、client、Kafka、full、external 五套 Compose `config --quiet` 通过；打包脚本语法通过 |
| 依赖与文档 | `patch-package --error-on-fail` 通过；232 个本地文档链接存在；上传指南 Markdown/HTML 的 9 组命令一致 |

证据目录为 `.codex-validation/prepush-20261027/`。此轮没有可复用的浏览器登录状态，未额外完成真实账户的业务操作；此前真实 UI 验证保留为上文及内容选择器记录中的阶段证据，不计入本轮已执行结果。67 项外部环境/显式开关测试仍为跳过；本轮没有重新生成 Docker ZIP 或部署服务器。规划中未实施项和生产容量、灾备边界保持原状态。
