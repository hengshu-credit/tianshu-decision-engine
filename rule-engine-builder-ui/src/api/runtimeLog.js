import request from './request'

export function listRuntimeLogs(params) {
  return request({ url: '/rule/runtime-log/list', method: 'get', params })
}

export function getExternalApiStats(params) {
  return request({ url: '/rule/runtime-log/external-api-stats', method: 'get', params })
}

export function getRuleSetStats(params) {
  return request({ url: '/rule/log/rule-set-stats', method: 'get', params })
}
