import request from './request'

export function listRuntimeLogs(params) {
  return request({ url: '/rule/runtime-log/list', method: 'get', params })
}
