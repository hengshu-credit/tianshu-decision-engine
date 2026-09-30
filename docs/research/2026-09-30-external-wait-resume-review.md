# 外数异常等待与订单恢复设计

## 目标

外数供应商在维护、排队或暂时不可用时，规则不能因为一次 HTTP 超时而跳过关键查询，也不能在恢复时重新提交产生重复费用。该机制按通用异步任务协议工作，不绑定具体供应商。

## 配置

- `requestMode=ASYNC`：先提交任务，再按 `asyncResultMode` 使用轮询或回调取得最终结果。
- `asyncTimeoutMs`：本次同步请求允许等待的预算；`0` 表示由 `maxAttempts=0` 的轮询配置持续等待，适合由业务侧通过状态接口恢复的长任务。
- `asyncPollConfig`：支持 `taskIdPath` 或 `traceIdPath`、`statusPath`、`successValue`、`failureValue/failureCondition`、`intervalMs`、`backoffMultiplier`、`maxIntervalMs`、`maxAttempts`。`maxAttempts=0` 表示不限制轮询次数。
- `failureMode=CONTINUE`：轮询遇到可恢复失败继续等待；其他值按终态失败处理。
- 变量外数配置的 `exceptionStrategy` 支持 `SKIP`、`BREAK`、`WAIT`、`RETRY`，并保留既有 `FAIL_FAST`、`RETURN_DEFAULT`、`IGNORE`、`USE_CACHE`。API 配置的策略会在变量未覆盖时生效。

## 检查点与恢复

提交响应中的任务号、轮询请求上下文、提交回执和调用关联键会作为 `WAITING_EXTERNAL` 来源步骤写入规则幂等检查点。恢复时从检查点读取任务号，只调用供应商的查询端点，不再调用提交端点；已成功的变量、模型和外数步骤从检查点恢复，不会重复执行。

V2 多步执行配置同样保存已完成步骤和当前等待步骤。恢复时跳过已经提交的步骤，仅继续当前轮询/回调步骤；回调注册信息在等待期间保留，恢复成功后再清理。

## 业务查询接口

`GET /api/rule/runtime/executions/{traceId}` 是状态和结果的统一查询接口：

- 未完成（例如 `RUNNING`、`WAITING_EXTERNAL`）返回状态、尝试次数、错误信息、来源状态和脱敏的步骤中间状态；
- 完成后返回同一个状态对象，并在 `result` 字段返回规则结果。

`POST /api/rule/runtime/executions/{traceId}/resume` 使用原订单入参摘要校验后恢复执行。订单检查点已保存原始入参时，请求体可以为空；需要更换入参时必须提供与原订单一致的完整入参，否则拒绝恢复。恢复成功后仍通过同一个 GET 接口读取最终状态和结果。服务端另保留 `/result` 兼容入口，但业务不需要额外判断或关联接口。

## 安全与诊断

中间状态只返回任务号、来源类型、变量 ID、调用 ID 等诊断字段，不返回轮询协议中的鉴权、请求参数和提交报文。完整请求/响应原始报文继续按 API 报文留存策略保存，并可通过外数调用关联键查询。
