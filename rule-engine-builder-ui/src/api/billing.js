import request from './request'

export function listBillingConfigs(params) {
  return request({ url: '/rule/billing/config/list', method: 'get', params })
}

export function createBillingConfig(data) {
  return request({ url: '/rule/billing/config', method: 'post', data })
}

export function updateBillingConfig(data) {
  return request({ url: '/rule/billing/config', method: 'put', data })
}

export function deleteBillingConfig(id) {
  return request({ url: `/rule/billing/config/${id}`, method: 'delete' })
}

export function listBillingRecords(params) {
  return request({ url: '/rule/billing/record/list', method: 'get', params })
}

export function listBillingSummaries(params) {
  return request({ url: '/rule/billing/summary/list', method: 'get', params })
}

export function refreshBillingSummary(data) {
  return request({ url: '/rule/billing/summary/refresh', method: 'post', data })
}
