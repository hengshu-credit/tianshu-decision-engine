import request from './request'
import { createResourceDraft } from './governance'

export function listProjects(params) {
  return request({ url: '/rule/project/list', method: 'get', params })
}

export function getProject(id) {
  return request({ url: `/rule/project/${id}`, method: 'get' })
}

export function getProjectWorkbench(id) {
  return request({ url: `/rule/project/${id}/workbench`, method: 'get' })
}

export function retryRuleWarmup() {
  return request({ url: '/rule/ops/rule-warmup/retry', method: 'post' })
}

export function createProject(data) {
  return createResourceDraft('PROJECT', data, 'CREATE', {
    projectId: null,
    changeSummary: '新建项目'
  })
}

export function updateProject(data) {
  return createResourceDraft('PROJECT', data, 'UPDATE', {
    projectId: data.id,
    changeSummary: '修改项目配置'
  })
}

export function deleteProject(id) {
  return createResourceDraft('PROJECT', null, 'DELETE', {
    resourceId: id,
    projectId: id,
    changeSummary: '删除项目'
  })
}

export function getMaskedToken(id) {
  return request({ url: `/rule/project/${id}/token/masked`, method: 'get' })
}

export function getFullToken(id) {
  return request({ url: `/rule/project/${id}/token/full`, method: 'get' })
}

export function regenerateToken(id) {
  return request({ url: `/rule/project/${id}/token/regenerate`, method: 'post' })
}

export function exportApiDoc(id) {
  return request({ url: `/rule/project/${id}/api-doc`, method: 'get' })
}

export function listProjectAuths(projectId) {
  return request({ url: `/rule/project/${projectId}/auth`, method: 'get' })
}

export function createProjectAuth(projectId, data) {
  return request({ url: `/rule/project/${projectId}/auth`, method: 'post', data })
}

export function updateProjectAuth(projectId, authId, data) {
  return request({ url: `/rule/project/${projectId}/auth/${authId}`, method: 'put', data })
}

export function updateProjectAuthStatus(projectId, authId, status) {
  return request({ url: `/rule/project/${projectId}/auth/${authId}/status`, method: 'put', params: { status } })
}

export function getProjectAuthFull(projectId, authId) {
  return request({ url: `/rule/project/${projectId}/auth/${authId}/full`, method: 'get' })
}

export function regenerateProjectAuthSecret(projectId, authId) {
  return request({ url: `/rule/project/${projectId}/auth/${authId}/regenerate`, method: 'post' })
}

export function listProjectAuthTokens(projectId, authId, params) {
  return request({ url: `/rule/project/${projectId}/auth/${authId}/tokens`, method: 'get', params })
}

export function getProjectAuthTokenFull(projectId, authId, tokenId) {
  return request({ url: `/rule/project/${projectId}/auth/${authId}/tokens/${tokenId}/full`, method: 'get' })
}

export function revokeProjectAuthToken(projectId, authId, tokenId) {
  return request({ url: `/rule/project/${projectId}/auth/${authId}/tokens/${tokenId}/revoke`, method: 'post' })
}

export function listProjectAuthAccessLogs(projectId, params) {
  return request({ url: `/rule/project/${projectId}/auth/access-logs`, method: 'get', params })
}
