import request from './request'

export function listGovernanceRequests(params) {
  return request.get('/rule/governance/requests', { params })
}

export function getGovernanceSummary(params) {
  return request.get('/rule/governance/requests/summary', { params })
}

export function getGovernanceRequest(id) {
  return request.get(`/rule/governance/requests/${id}`)
}

export function createGovernanceDraft(data) {
  return request.post('/rule/governance/drafts', data)
}

export function createResourceDraft(resourceType, resource, action, options = {}) {
  const resourceId =
    options.resourceId == null ? resource && resource.id : options.resourceId
  return createGovernanceDraft({
    resourceType,
    resourceId: resourceId == null ? null : resourceId,
    projectId:
      options.projectId == null
        ? resource && resource.projectId
        : options.projectId,
    action,
    snapshotJson: resource ? JSON.stringify(resource) : null,
    changeSummary: options.changeSummary || lifecycleSummary(action)
  })
}

export function saveGovernanceDraft(id, data) {
  return request.put(`/rule/governance/requests/${id}/draft`, data)
}

export function preflightGovernanceRequest(id) {
  return request.post(`/rule/governance/requests/${id}/preflight`)
}

export function submitGovernanceRequest(id, comment) {
  return request.post(`/rule/governance/requests/${id}/submit`, { comment })
}

export function approveGovernanceRequest(id, comment) {
  return request.post(`/rule/governance/requests/${id}/approve`, { comment })
}

export function rejectGovernanceRequest(id, comment) {
  return request.post(`/rule/governance/requests/${id}/reject`, { comment })
}

export function cancelGovernanceRequest(id, comment) {
  return request.post(`/rule/governance/requests/${id}/cancel`, { comment })
}

export function listGovernanceVersions(type, id) {
  return request.get(`/rule/governance/resources/${type}/${id}/versions`)
}

export function restoreGovernanceVersion(type, id, sourceVersionId) {
  return request.post(`/rule/governance/resources/${type}/${id}/restore`, {
    sourceVersionId
  })
}

function lifecycleSummary(action) {
  const labels = {
    CREATE: '新建资源',
    UPDATE: '修改资源配置',
    ENABLE: '启用资源',
    DISABLE: '停用资源',
    DELETE: '删除资源',
    RESTORE: '使用历史版本'
  }
  return labels[action] || '资源生命周期变更'
}
