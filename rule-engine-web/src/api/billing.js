import request from './request'
import { createResourceDraft } from './governance'

export function listBillingConfigs(params) {
  return request({ url: '/rule/billing/config/list', method: 'get', params })
}

export function createBillingConfigDraft(data) {
  return createResourceDraft('BILLING_CONFIG', data, 'CREATE', {
    projectId: data && data.scope === 'GLOBAL' ? 0 : data && data.projectId,
    changeSummary: '新建计费配置'
  })
}

export function updateBillingConfigDraft(data) {
  return createResourceDraft('BILLING_CONFIG', data, 'UPDATE', {
    changeSummary: '修改计费配置'
  })
}

export function changeBillingConfigStatusDraft(data, status) {
  return createResourceDraft(
    'BILLING_CONFIG',
    data,
    status === 1 ? 'ENABLE' : 'DISABLE',
    {
      changeSummary: status === 1 ? '启用计费配置' : '停用计费配置'
    }
  )
}

export function deleteBillingConfigDraft(data) {
  return createResourceDraft('BILLING_CONFIG', null, 'DELETE', {
    resourceId: data && data.id,
    projectId: data && data.projectId,
    changeSummary: '删除计费配置'
  })
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
