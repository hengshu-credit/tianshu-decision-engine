import request from './request'

export function listRuntimeLogs(params) {
  return request({ url: '/rule/runtime-log/list', method: 'get', params })
}

export function getExternalApiStats(params) {
  return request({ url: '/rule/runtime-log/external-api-stats', method: 'get', params })
}

export function getExecutionMetrics() {
  return request({ url: '/rule/ops/execution-metrics', method: 'get' })
}

export function retryRuleWarmup() {
  return request({ url: '/rule/ops/rule-warmup/retry', method: 'post' })
}

export function getRuntimeCallPayload(id, projectId) {
  return request({
    url: `/rule/runtime-log/${id}/payload`,
    method: 'get',
    params: projectId == null ? undefined : { projectId },
  })
}

export function getRuntimeCallPayloadByCallId(callId) {
  return request({ url: '/rule/runtime-log/payload/by-call-id', method: 'get', params: { callId } })
}

export function getRuntimeCallPayloadsByRootTraceId(rootTraceId) {
  return request({ url: '/rule/runtime-log/payload/by-root-trace-id', method: 'get', params: { rootTraceId } })
}

export function getRuntimeExternalCallPayload(callId) {
  return request({ url: `/rule/runtime/external-calls/${encodeURIComponent(callId)}`, method: 'get' })
}

export function getRuleSetStats(params) {
  return request({ url: '/rule/log/rule-set-stats', method: 'get', params })
}

export function replayExecutionLog(id) {
  return request({ url: `/rule/log/${id}/replay`, method: 'post' })
}
