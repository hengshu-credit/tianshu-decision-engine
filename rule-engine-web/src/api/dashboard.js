import request from './request'
import { metadataResponse } from '@/utils/metadataResponse'

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
  return request({ ...metadataResponse, url: '/rule/dashboard/settings', method: 'get', params })
}

export function saveDashboardSettings(params, data) {
  return request({ ...metadataResponse, url: '/rule/dashboard/settings', method: 'put', params, data })
}

export function deleteDashboardSetting(metricField, params) {
  return request({
    ...metadataResponse,
    url: `/rule/dashboard/settings/${metricField}`,
    method: 'delete',
    params
  })
}

/** 管理员维护的公共默认，保存时必须提交读取到的版本。 */
export function getDashboardDefaults() {
  return request({ ...metadataResponse, url: '/rule/dashboard/settings/defaults', method: 'get' })
}

export function saveDashboardDefaults(data) {
  return request({ ...metadataResponse, url: '/rule/dashboard/settings/defaults', method: 'put', data })
}
