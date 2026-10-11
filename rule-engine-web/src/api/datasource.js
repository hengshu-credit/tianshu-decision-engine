import request from './request'
import { createResourceDraft } from './governance'

export function listDatasources(params) {
  return request({ url: '/rule/datasource/list', method: 'get', params })
}

export function getDatasource(id) {
  return request({ url: `/rule/datasource/${id}`, method: 'get' })
}

export function createDatasource(data) {
  return createResourceDraft('EXTERNAL_DATASOURCE', data, 'CREATE', {
    changeSummary: '新建外数数据源'
  })
}

export function updateDatasource(data) {
  return createResourceDraft('EXTERNAL_DATASOURCE', data, 'UPDATE', {
    changeSummary: '修改外数数据源'
  })
}

export function deleteDatasource(id) {
  return createResourceDraft('EXTERNAL_DATASOURCE', null, 'DELETE', {
    resourceId: id,
    changeSummary: '删除外数数据源'
  })
}

export function listApiConfigs(params) {
  return request({ url: '/rule/datasource/api-config/list', method: 'get', params })
}

export function getApiConfig(id) {
  return request({ url: `/rule/datasource/api-config/${id}`, method: 'get' })
}

export async function createApiConfig(data) {
  const projectId = await apiProjectId(data)
  return createResourceDraft('EXTERNAL_API', data, 'CREATE', {
    projectId,
    changeSummary: '新建外数 API'
  })
}

export async function updateApiConfig(data) {
  const projectId = await apiProjectId(data)
  return createResourceDraft('EXTERNAL_API', data, 'UPDATE', {
    projectId,
    changeSummary: '修改外数 API'
  })
}

export function deleteApiConfig(id) {
  return createResourceDraft('EXTERNAL_API', null, 'DELETE', {
    resourceId: id,
    changeSummary: '删除外数 API'
  })
}

async function apiProjectId(data) {
  if (!data || !data.datasourceId) return null
  const response = await getDatasource(data.datasourceId)
  return response.data ? response.data.projectId : null
}

export function invokeApiConfig(id, data) {
  return request({ url: `/rule/datasource/api-config/${id}/invoke`, method: 'post', data, timeout: 0 })
}

export function invokeApiConfigPreview(id, data) {
  return request({ url: `/rule/datasource/api-config/${id}/invoke-preview`, method: 'post', data, timeout: 0 })
}

export function createApiConfigTrial(data) {
  return request({ url: '/rule/datasource/api-config/trials', method: 'post', data })
}

export function listApiConfigTrials() {
  return request({ url: '/rule/datasource/api-config/trials', method: 'get' })
}

export function getApiConfigTrial(trialId) {
  return request({ url: `/rule/datasource/api-config/trials/${trialId}`, method: 'get' })
}

export function resumeApiConfigTrial(trialId) {
  return request({ url: `/rule/datasource/api-config/trials/${trialId}/resume`, method: 'post', data: {} })
}

export function cancelApiConfigTrial(trialId) {
  return request({ url: `/rule/datasource/api-config/trials/${trialId}/cancel`, method: 'post', data: {} })
}

export function previewApiConfigRequest(id, data) {
  return request({ url: `/rule/datasource/api-config/${id}/request-preview`, method: 'post', data })
}

export function testDatasourceAuth(id, data) {
  return request({ url: `/rule/datasource/${id}/auth-test`, method: 'post', data })
}
