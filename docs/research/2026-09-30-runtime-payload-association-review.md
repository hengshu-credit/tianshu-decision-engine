# 外数调用报文关联复核（2026-09-30）

## 发现

规则执行中的外数日志存在三种 Trace 身份：`rootTraceId`、`ruleTraceId`、`traceId`。原来的按根 Trace 查询只使用 `root_trace_id`，当调用日志只写入另外两列时，业务系统拿规则执行 Trace 去回溯会得到空列表，不能满足“拿到请求和原始响应后简单关联”的要求。

管理端按根 Trace 的查询接口原来没有接收项目范围参数；同一控制台账号可以看到跨项目的调用摘要，虽然业务令牌接口已有项目隔离，但管理端回溯也应保持显式项目过滤能力。

## 修复

- `RuleRuntimeCallLogService.payloadsByRootTraceId` 使用 `rootTraceId OR ruleTraceId OR traceId` 联合匹配，并继续对 `projectId`、模块和 `API_INVOKE` 做过滤。
- `RuntimeCallLogController` 的 `/payload/by-root-trace-id` 增加可选 `projectId`，调用带项目隔离的 service 方法。
- 原始请求/响应、解密副本、字段排除元数据、Trace steps 仍通过原来的 `callId` 汇总接口返回；本轮没有改变报文留存策略或日志长期保存策略。

## 验证

- `RuleRuntimeCallLogServiceTest` 新增用例覆盖仅写入 `traceId` 的调用，验证结果可回溯且查询参数包含项目 ID；7 项服务测试全部通过。
- 业务令牌接口仍通过 `ProjectAuthContext` 使用调用所属项目隔离；管理端接口新增过滤参数，不改变原有调用兼容性。

