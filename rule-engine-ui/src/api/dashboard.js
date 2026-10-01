import request from './request'

export function getDashboardApplications(params) {
  return request({ url: '/rule/dashboard/applications', method: 'get', params })
}

export function getDashboardOperations(params) {
  return request({ url: '/rule/dashboard/operations', method: 'get', params })
}

export function getDashboardGovernance(params) {
  return request({ url: '/rule/dashboard/governance', method: 'get', params })
}

export function getDashboardSettings(params) {
  return request({ url: '/rule/dashboard/settings', method: 'get', params })
}

export function saveDashboardSettings(params, data) {
  return request({ url: '/rule/dashboard/settings', method: 'put', params, data })
}

export function deleteDashboardSetting(metricField, params) {
  return request({
    url: `/rule/dashboard/settings/${metricField}`,
    method: 'delete',
    params
  })
}
