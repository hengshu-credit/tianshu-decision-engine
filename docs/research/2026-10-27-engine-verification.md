# 2026-10-27 天枢审查与修复交付验证

关联：[研究报告](2026-10-27-engine-product-review.md) · [36 项新需求计划](../plans/2026-10-27-engine-optimization-requirements.md) · [代码入口索引](2026-10-27-engine-surface-index.md)。

本文件保留首次研究与 A01—A09 修复的验证快照。后续逐项修复和最新完整验证见[实施与复核记录](2026-10-27-optimization-implementation.md)。

## 1. 最终结果

| 验证 | 最终结果 | 本地证据（仓库根目录下） |
|---|---|---|
| 前端全量单元测试 | 305 个文件、3568 个用例全部通过 | `.codex-validation/review-20261027/web-final.log` |
| 后端全量测试 | 2923 个用例：2856 通过、67 跳过；0 failures、0 errors | `.codex-validation/review-20261027/backend-final.log` |
| 后端完整安装构建 | `mvn clean install -DskipTests` 成功，五个子模块通过 | `backend-install-final.log` |
| 前端生产构建 | `npm run build` 成功 | `web-build-final.log` |
| ESLint | 全项目 lint 通过，包含最终增强的测试断言 | `web-lint-final.log` |
| Playwright | 74/74；另对最终 SFTP 排序、隐藏、刷新持久化断言重跑 6/6 | `web-e2e-final.log`、`sftp-e2e-final.log` |
| 部署质量工具 | 43/43 | `quality-gates.log` |
| 本地后端启动 | 最终 readiness=`UP`；规则预热 38 个有效版本、0 拒绝 | `server.stdout.log`、`readiness-final.json` |
| 本地前端启动 | `npm run dev` 在 19090 启动并代理本次 18080 后端 | `web.stdout.log` |
| 最终浏览器控制台 | warn/error 列表为空 | `final-browser-console.json` |

除首行给出完整路径外，证据文件均位于 `.codex-validation/review-20261027/`。运行上下文 JFR 数据位于 `.codex-validation/runtime-cache/review-20261027-*`。

本次使用真实 MyBatis + 独立 H2 库校验工作台查询归属，没有在业务库中造同码项目或修改项目编码。

## 2. 缺陷复现与回归

| 缺陷 | 修复前 | 修复后 |
|---|---|---|
| UNIQUE 冲突动作 | 两行均命中，动作函数被调用 2 次，之后才报错 | 动作调用 0 次，返回冲突；单命中执行一次；零命中保留原语义 |
| 编译临时变量 | 复核发现 `_uniqueMatchedRow` 业务条件可能被编译临时变量覆盖 | 从已编译业务表达式选择不冲突的前缀；同名输入测试通过 |
| 枚举选项响应 | 普通变量和对象字段均出现 `opts.map is not a function` | 平台外层正确解包，编码和标签不转换 |
| 复杂交叉表分段 | 自动刷新将自定义标签、`!=` 条件和顺序替换成枚举默认项 | 自动同步只更新字段元信息，既有分段与单元格保持 |
| SFTP 未保存保护 | 取消后无提醒，重新打开输入丢失 | 取消离开保留输入；确认离开清空；工作区保护生效 |
| SFTP 连续保存 | 等待表单校验时发出两次创建请求 | 保存锁覆盖校验和请求；只提交一次 |
| SFTP 慢编辑读取 | 旧详情覆盖新建表单并带入旧资源 ID | 新建/关闭/卸载使旧请求失效 |
| 项目执行摘要 | 按当前编码读取了其他归属的成功日志 | 按稳定项目 ID 读取；排除其他项目及未知归属记录 |
| SFTP 列设置 | 缺少入口；初次接入公共组件后 E2E 又发现 DOM 回退不能隐藏字段 | 改用项目列表同款声明式列绑定；隐藏、重排、重载与宽窄布局通过 |
| 决策表默认策略 | 历史配置未填 hitPolicy，界面显示未选择，编译器却用 FIRST | 展示与运行默认一致，显式 UNIQUE 等值不变 |

复现日志分别为 `unique-red.log`、`unique-identity-red.log`、`enum-red.log`、`cross-red.log`、`sftp-red.log`、`ownership-red.log`、`table-ui-red.log`。测试过程中出现的失败均保留，最终结果以表 1 为准。

## 3. 真实浏览器操作

使用用户提供的现有账户登录本地控制台。实际访问 19 个一级菜单，等待页面完成加载，保存 `ui-模块名.txt/png`。九类设计器的页面快照为 `designer-类型.txt/png`。这属于真实页面可用性和代表流程验证；各隐藏面板、全部权限角色和每个外部供应商的组合仍需按需求表验收。

### 决策表

1. 从规则列表进入现有测试样例的设计器，确认缺省策略回显“首次命中”。
2. 在未保存画布选择“唯一命中”，通过“复制”生成第二行相同条件。
3. 在测试弹窗输入合成 JSON：`TSRequestBody.params.customer.score=80`，两条 `>60` 条件命中，返回 UNIQUE 冲突。
4. 输入 `score=0`，执行成功，输出为 `{"amount": null}`。
5. 关闭弹窗，通过 UI 删除临时复制行，再输入 `score=80`，执行成功，输出为 `{"amount": 123}`。
6. 离开时选择放弃临时画布修改。没有保存覆盖原规则，没有提交审批或发布。测试执行产生正常的测试日志。

对应证据：`unique-conflict-live.*`、`unique-no-match-live.txt`、`unique-single-live.*`。

### 复杂交叉表

打开本地 8×6 样例，修复前未操作就出现“有未保存修改”和离开确认。修复枚举解包及元信息同步后，重新加载显示“已加载，尚未修改”，原 8×6 单元格和条件保留；切换模块没有错误提醒。

对应证据：`cross-adv-false-dirty.*`、`cross-adv-after.*`。初次修复后继续复核发现枚举响应问题，未将第一次尝试当作完成。

### SFTP

真实页面执行新建、填写名称、取消、继续编辑、确认放弃，验证输入保留和清理。没有创建可用远程凭据或发布 SFTP 数据源。

保存草稿、连接测试、分页筛选、字段排序/隐藏/重载与 1440px/560px 布局由真实前端页面 + 模拟 API 的 Playwright 流程验证。模拟连接成功不能作为真实 SFTP 网络连通性结论。

对应证据：`sftp-before-cancel.txt`、`sftp-unsaved-reopened.png`、`sftp-unsaved-after.png`、`sftp-after.txt`及 SFTP E2E 日志。

## 4. 性能、跳过项与环境异常

- 来源解析基准：8 个独立来源，每个模拟阻塞 30 ms；串行中位 251 ms、P95 280 ms；并行度 4 中位 62 ms、P95 77 ms。测试显式开启基准开关并实际运行通过。
- JFR：运行仓库已有 `RuntimeContextProfile`；记录常量复制、比较、上下文快照及预编译表达式开销。没有据此宣称生产 QPS 或本次修复的全链路性能提升。
- 后端默认全量测试跳过 67 项，涉及真实 MySQL 的计费/日志/恢复/外数会话集成、容量、样例导入、ONNX 资产/CUDA 诊断及默认关闭的基准。逐类数量见 `skipped-tests.json`；其中来源解析基准另行显式执行。
- 后端编译日志未出现 deprecated API、unchecked/unsafe 类警告。前端构建仍提示大 bundle，Node 测试环境有 localStorage 实验提示，均未通过隐藏告警处理。
- 一次重启的模型预热受 MySQL `Communications link failure` 影响，readiness 返回 503。保留 `server-retry-needed.stdout.log`，主动停止该验证节点后重新启动，最终 `readiness-final.json` 为 UP。
- 当前 ONNX Runtime 无 CUDA Provider，部分模型按既有配置回退 CPU；本次没有完成 GPU 性能验收。

## 5. 服务与文件清理

本次独立验证端口为 18080/19090，启动看护使用 `WaitForExit(1800000)`。第一轮持续审查超过 30 分钟，触发看护退出；最终验证重新启动，并在健康与 UI 检查后主动终止本次进程树。收尾检查确认 18080、19090 均无监听。

开始任务时已有的 8080、9090 开发进程仍在运行。所有代码位于原 `master` 工作区，未新建分支。任务开始前已有的部署文档和打包脚本修改保持原状态，本次没有替用户提交或发布。

已有发布制品保留原脚本；使用本次 UNIQUE 修复需要按既有流程重编译、回归、审批和发布。
