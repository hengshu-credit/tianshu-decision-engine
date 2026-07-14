import request from './request'

export function listProjects(params) {
  return request({ url: '/rule/project/list', method: 'get', params })
}

export function getProject(id) {
  return request({ url: `/rule/project/${id}`, method: 'get' })
}

export function createProject(data) {
  return request({ url: '/rule/project', method: 'post', data })
}

export function updateProject(data) {
  return request({ url: '/rule/project', method: 'put', data })
}

export function deleteProject(id) {
  return request({ url: `/rule/project/${id}`, method: 'delete' })
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
