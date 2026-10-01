# 外数配置与执行统一契约

本文件记录本次实现与验收范围。配置继续经审批生效，资源关联使用稳定 ID，旧配置保持可执行。

## 调用身份

一次根规则执行（包含子规则）内，同一个 API 的同一组生效请求参数只执行一次完整链路，成功和失败均记忆；接口重试、轮询属于这条链路内部。显式允许覆盖的参数绑定到不同值时，形成不同调用。请求身份不能包含无关规则输出，也不能使用响应 TTL 缓存代替请求内去重。

## 配置职责

- API 管理：请求参数定义、可覆盖开关、必填与传空策略、鉴权、请求/响应条件分支、多步协议、重试、熔断、计费。
- API 异常条件：每个接口可配置异常条件树，支持路径缺失、存在、为空、非空、类型等于和类型变化等判断；命中后统一进入异常策略、重试和等待恢复链路。多步链路中的每个 HTTP 步骤还可配置独立的步骤异常条件树，内部规则数据源同样经过该判断，不会因协议不同而绕过条件。
- 变量与对象：按 API ID 选择来源，覆盖已开放入参，选择统一结果路径；对象子字段可独立选择原始响应路径。
- 统一结果：保留兼容的 body（组装结果），增加原始 response、request、authentication、steps、status 和调用元信息；字段取值不依赖日志采集开关。
- 预览只做解析与校验，禁止 HTTP、Token、回调注册及计费。真实测试进入正式调用链并展示追踪树。
- N 步链路以稳定步骤 ID 保存，请求/轮询/回调均在总截止时间内执行；后续步骤可引用前面全部步骤结果，禁止前向和循环引用。

## 主要代码入口

| 职责 | 入口 |
| --- | --- |
| 变量、对象统一调用与执行内复用 | `ExternalApiConsumerService` |
| 字段 ID 绑定、参数覆盖、默认值、必填及传空 | `ExternalApiRequestPlan` |
| 请求、鉴权、重试、熔断、条件分支与结果封装 | `ExternalApiInvokeService` |
| N 步请求、轮询和回调等待 | `ExternalApiWorkflow`、`ExternalApiCallbackStore` |
| 配置与引用校验 | `ExternalApiExecutionConfig`、治理适配器 |
| API 条件计费项自动同步、调用流水去重 | `RuleBillingService` |
| 统一配置与入参覆盖 UI | `ApiExecutionEditor`、`ApiSourceBinding` |

## 结果与绑定示例

API 返回 `body`（组装结果）、`response`（原始状态码、响应头、响应体）、`request`（实际 URL、方法、Header、Query、JSON/Form）、`authentication`（本次鉴权调用列表）、`steps`（稳定步骤 ID 对应结果）、`status`、`callId`、`costTimeMs`。

变量通过 `apiConfigId` 关联 API，按 `resultPath` 取值。对象使用相同入口；`bindingMode=OBJECT` 整体绑定，`FIELDS` 逐字段取值。子字段 `sourcePath` 可以指定原始响应路径，例如 `response.body.data.mobile`。对象已有显式字段值优先，子字段的请求覆盖继承对象上其余覆盖项。

```json
{
  "apiConfigId": 42,
  "resultPath": "body.data.mobileStatus",
  "requestOverrides": {
    "phone-parameter-id": {
      "kind": "REFERENCE",
      "refType": "VARIABLE",
      "refId": 123
    }
  }
}
```

上例仅替换 API 明确开放的手机号参数。字段编码或显示名称变化时，按 `refId` 查找最新字段。覆盖后的值相同则共用结果，值不同则形成独立调用。对象字段的覆盖存于字段 `sourceConfig`。

规则可使用来源状态条件，或 `sourceStatusValue("VARIABLE", "字段ID", "HTTP_STATUS")` 读取状态。支持 `OUTCOME`、`HTTP_STATUS`、`BILLED`、`RETRY_COUNT`、`RETRIED`、`CIRCUIT_OPEN`、`EXCEPTION` 等维度。

## 多步与配置约定

- 可覆盖的链路入参在 API「请求字段」统一声明，各步骤从 `input.__apiFields.<参数ID>` 读取；步骤也可直接选择业务字段。前置结果通过 `steps.<步骤ID>.<结果路径>` 读取。
- 每个 HTTP 步骤可继承根 API 鉴权、关联另一个 API 的鉴权或自行配置鉴权。失败重试受次数、间隔、退避和非幂等重试开关约束。
- 步骤异常条件树以 `response.body`、`response.httpStatus`、`body` 等本步响应上下文判断；路径缺失与显式 `null` 保持可区分，类型判断支持 STRING、NUMBER、BOOLEAN、OBJECT、ARRAY、NULL 和 MISSING。保存与执行前均校验条件结构，避免空路径被当成“全部异常”。
- 不需要重试时选择 `FAIL_FAST`，将 `retryCount` 设为 `0`，并清空重试条件和 `retryBranches`；HTTP 状态码、连接错误和退避参数本身不会触发重试。选择 `RETRY` 时必须配置至少一次重试，或在多步链路的 `retryBranches` 中配置重试次数。
- 回调在提交前注册随机调用 ID，使用 HMAC-SHA256 校验；完成后清理注册。等待、轮询、鉴权和重试均受本次调用总超时约束。
- 条件按顺序选择第一个匹配分支，空条件作为兜底。响应样例保留字段原名，支持字段组装和整体取值。
- 表达式编辑器复用 Operand 结构，允许无外部副作用的内置计算函数；API 脚本不提供隐藏的网络下载入口。预览不注册回调、不获取 Token、不调用上游、不计费。
- JSON 支持显式 null；Query、Header 和表单的 null 按 HTTP 空字符串发送。默认省略空值，字段显式设置优先。
- 步骤追踪保存本步生效参数与前置步骤 ID，避免反复嵌入之前的追踪内容；大报文仍遵守原有诊断留存上限，完整运行结果独立于诊断留存。

## 兼容与数据库

新配置保存为 `executionConfig.version=2`。原有未升级配置继续使用原协议解析，通过统一消费入口复用结果。直接外数调用函数已停用，需改用 API 变量或数据对象引用。

新增数据库字段为 `rule_external_api_config.execution_config`、`rule_data_object_field.source_config`；字段来源路径沿用 `source_path`。新库 `schema.sql` 已直接包含最终字段，存量库升级由应用启动同步负责。

## 验收记录（2026-09-30）

- 完成前端开发启动、ESLint、生产构建和完整前端测试：206 个文件、2490 项通过。
- 完成后端 `mvn clean install -DskipTests`、服务启动和全模块 `mvn test`。本机 JDK 17 套接字临时目录使用工作区临时目录参数。
- 最后针对参数路径、变量/对象复用、失败记忆、N 步调用、回调、脚本预览隔离、计费和完整步骤追踪，再以独立编译输出运行 145 项专项测试，全部通过。
- 浏览器通过正常页面创建 API、导入响应样例、配置参数覆盖、审批变量和对象、插入规则字段引用并执行测试。
- 联合规则返回申请人 `13000000001`、联系人 `13000000002`；重复读取两者时，模拟供应商仅新增两次请求，每组生效入参一次。
- 两步接口预览未增加上游请求；真实调用按 `/submit`、`/poll` 顺序完成。
- API 计费条件审批后自动生成 `API_9325`；真实模拟调用产生数量 1、金额 0 的计费明细。
- 回调签名、抢先到达、终态隔离、前置结果引用和失败不重放由自动化测试覆盖。真实供应商和公网回调部署未参与本地验收。

## 大字段留存与配置存储

外数配置中的鉴权、Header、Query、请求/响应映射、统一执行链路、轮询、回调、计费、报文留存和测试样例由应用层校验 JSON，数据库优先使用原生 `JSON` 列，便于后续按路径查询；请求体模板、请求脚本和响应脚本等非结构化内容继续使用 `LONGTEXT`。应用启动同步会将历史配置列转换回 JSON，发现非法旧 JSON 时只告警并保留原列，避免静默丢失。

报文留存默认保存原文和全部字段。可在请求或响应侧按 JSONPath 添加“超长字段策略”：未配置或标记“保存”的路径继续保存，标记“省略”的路径始终从诊断副本移除，不根据本次实际内容长度临时改变意图；规则实际请求和响应不受该诊断策略影响。

## 异步等待、状态与恢复

异步提交获得的 `taskId`/`traceId`、轮询上下文和提交回执会写入规则幂等检查点。外数变量配置的异常策略支持 `SKIP`、`BREAK`、`WAIT`、`RETRY`；`WAIT` 将订单置为 `WAITING_EXTERNAL`，恢复时只查询原任务，不重复提交。V2 多步链路保存已完成步骤和当前等待步骤，恢复会跳过已提交步骤；回调注册在等待期间保留，终态后清理。

`GET /api/rule/runtime/executions/{traceId}` 是状态与结果统一接口：未完成时返回 `status`、`attemptNo`、来源状态和脱敏步骤中间状态，完成时在同一对象的 `result` 中返回结果。`POST /api/rule/runtime/executions/{traceId}/resume` 可不带请求体（检查点已保存原始入参时），也可带原订单完整入参；服务端校验摘要后恢复原 revision 的规则。Java SDK 和 HTTP-only SDK 均提供对应 `resumeExecution` 方法。

## 客户端缓存与高并发

L1 缓存达到上限时优先淘汰固定版本，最新版规则不会被固定版本增量推送挤出；如果缓存全是最新版，固定版本只使用本次回源对象而不继续占用槽位。冷启动或缓存淘汰后的同一 `ruleCode` 使用单飞加载，多个并发请求共享一次 HTTP 回源结果，失败后释放加载槽位允许后续重试。

