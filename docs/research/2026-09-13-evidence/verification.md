# 天枢本轮验证证据

验证日期：2026-09-13。起始分支 `master`，提交 `f5fa537`；结果对应本轮未提交的代码变更。运行环境为 Windows、Node.js 26.4.0、Maven 3.9.16、Microsoft JDK 17.0.19，CPU 为 Intel i9-13900KF。

## 自动验证

| 检查 | 命令或范围 | 结果 |
|---|---|---|
| 前端修改前基线 | `npm test -- --reporter=dot` | 175 文件，2077 测试通过 |
| 新增前端问题复现 | 工作台组件 + 项目详情定向测试 | 修改前新增断言失败，包含错误就绪及旧响应覆盖 |
| 后端修改前基线 | `ProjectWorkbenchServiceTest` | 原有 4 测试通过 |
| 新增后端问题复现 | 工作台服务测试 | 修改前 3 新增测试失败：READY/ATTENTION 与 BLOCKED/UNAVAILABLE 不符 |
| 前端修改后定向测试 | 工作台组件、项目详情、规则列表 | 50 测试通过 |
| 前端最终全量 | `npm test -- --reporter=dot` | 175 文件，2086 测试通过 |
| ESLint | `npm run lint` | 通过，无报错 |
| 前端生产构建 | `npm run build` | 通过；仍有大于 500 kB 的分块警告 |
| 新旧引导流程浏览器测试 | `npx playwright test tests/e2e/guided-workflows.spec.js` | 9 测试通过 |
| 完整 dist 浏览器测试集 | `npm run test:e2e:dist` | 112 测试通过，使用模拟 API |
| 后端全模块构建 | `mvn clean install -DskipTests` | 六个 reactor 项目全部成功 |
| 后端默认全量首次运行 | `mvn test` | SDK HTTP 测试服务创建时出现 12 个环境错误 |
| 后端全量复跑 | `mvn test -DargLine=-Djdk.net.unixdomain.tmpdir=E:/workspace/tianshu-decision-engine/.codex-run-logs` | 1335 总测试，1319 通过、16 跳过，0 failures、0 errors |
| 后端实际启动 | `mvn spring-boot:run`，修改前后均尝试 | 失败：本机 MySQL 3306 连接被拒绝 |
| 改动格式 | `git diff --check` | 通过 |

后端复跑分模块为：core 224 项全部通过；server 1051 项，其中 1035 通过、16 跳过；client 60 项全部通过。跳过项按仓库现有测试条件保留，未删除测试或降低断言标准。

### 本机 JDK 测试环境问题

默认全量测试的错误为 `Unable to establish loopback connection`，底层为 `UnixDomainSockets.connect0` 的 `Invalid argument: connect`，出现在 `HttpLogReporterTest.startServer` 创建测试用 HTTP 服务时。测试尚未进入被测日志上报逻辑。

检查了本机 JDK 17 `src.zip` 中 `PipeImpl` 和 `UnixDomainSocketsUtil` 的实现，后者支持系统属性 `jdk.net.unixdomain.tmpdir` 指定套接字临时目录。保持业务代码、JDK 和测试断言不变，仅为测试命令指定已存在的工作区目录后，SDK 13 项定向测试及后端全量测试均通过。切换 SelectorProvider 的尝试未解决问题；该参数未保留。

这个结果证明本轮测试可在明确的本地环境参数下运行，不证明原默认临时目录问题已在系统层解决。没有修改系统环境变量、POM 或生产配置。

## 页面验证

手工验证启动的是 `npm run dev` 的实际开发页面，监听 `127.0.0.1:9090`；API 来自仓库现有文档模拟服务，监听 `127.0.0.1:19081`。通过会话级 `VITE_DEV_PROXY` 指向模拟服务，没有修改 `.env`。

手工路径为项目工作台 → 项目规则 → 新建规则 → 填写编码和名称 → 选择评分卡 → 查看说明 → 提交；另检查规则管理的新建入口、变量管理的新建字段及数据库来源配置。浏览器控制台无 error。模拟服务不承担真实持久化，因此“创建成功”的页面提示不等于实际数据库创建成功。

自动浏览器测试新增的四个场景如下：

1. 工作台请求处于等待时显示检查中；模拟 503 后显示失败；原位重试成功后显示当前检查项就绪。
2. 工作台存在失败执行时显示待处理和失败原因；点击排查后进入带项目 ID 的日志页面。
3. 项目规则入口通过 UI 填写、切换类型并提交；验证混合大小写编码、名称、类型及项目 ID 保真。
4. 规则管理入口执行同样操作，验证共享帮助组件与提交行为一致。

以下截图由通过的浏览器用例生成。它们使用测试夹具，不是线上业务数据。

### 失败执行的工作台

![工作台显示失败次数与排查入口](E:/workspace/tianshu-decision-engine/docs/research/2026-09-13-evidence/workbench-failed-run.png)

### 规则创建的业务说明

![规则管理创建入口及评分卡说明](E:/workspace/tianshu-decision-engine/docs/research/2026-09-13-evidence/rule-rule-guide.png)

另保存了[项目创建入口](E:/workspace/tianshu-decision-engine/docs/research/2026-09-13-evidence/project-rule-guide.png)和[工作台重试恢复](E:/workspace/tianshu-decision-engine/docs/research/2026-09-13-evidence/workbench-recovered.png)截图。

开发服务、模拟 API 和本轮挂起的 Docker 只读查询进程均已主动停止。未停止原本运行的 Docker Desktop 或其后台服务。失败的后端启动进程已经退出。

## 纯引擎微基准

程序为 [EngineAuditBenchmark.java](E:/workspace/tianshu-decision-engine/docs/research/2026-09-13-evidence/EngineAuditBenchmark.java)。固定脚本为年龄与分数判断，结果必须为 true；每次执行构建新输入 Map，避免跨次共享结果状态。脚本缓存已热，每种追踪模式预热 5000 次、测量 20000 次。

本机单次输出：

```text
trace=false samples=20000 mean_us=1.360 p50_us=1.200 p95_us=1.500 p99_us=3.000
trace=true samples=20000 mean_us=3.937 p50_us=3.400 p95_us=5.600 p99_us=11.500
```

该程序用于建立可复现的观察起点。它不是生产压测，不含服务端制品加载、数据库、网络、外数、推理、日志存储或并发争用。JIT、测量顺序、GC 和系统背景负载均可能影响结果，后续应采用多轮独立进程及代表性规则集。

在仓库根目录、后端已经 install 后，可用 PowerShell 复现：

```powershell
New-Item -ItemType Directory -Force -Path .codex-run-logs | Out-Null
mvn -pl rule-engine-core dependency:build-classpath '-Dmdep.outputFile=E:/workspace/tianshu-decision-engine/.codex-run-logs/core-classpath.txt'
$auditClasspath = (Get-Content -LiteralPath '.codex-run-logs/core-classpath.txt' -Raw).Trim() + ';E:\workspace\tianshu-decision-engine\rule-engine-core\target\classes;E:\workspace\tianshu-decision-engine\.codex-run-logs'
javac -encoding UTF-8 -cp $auditClasspath -d .codex-run-logs docs/research/2026-09-13-evidence/EngineAuditBenchmark.java
java -cp $auditClasspath EngineAuditBenchmark
```

若工作区位置变化，需调整类路径与输出路径；不需要连接 MySQL 或 Redis。输出包含 SLF4J 无 provider 的提示，该提示不影响微基准断言。

## 仍需真实环境完成的检查

本轮无法证明的事项包括真实数据库持久化、真实账户权限矩阵、审批与冻结生效、外数成本与降级、发布后服务端/SDK 版本一致性、容量与灾备恢复，以及业务人员的独立配置能力。后续应按新需求计划中的 R04—R18 完成，不将本证据文件中的模拟页面测试视为替代。

本地原始输出保存在 `.codex-run-logs`，该目录不纳入 Git；本文件、微基准源码与四张截图为可保留的验证摘要。
