import request from './request'
import { notifyDraftChange } from '@/utils/governanceWorkspace'
import { metadataResponse, parseMetadataResponse } from '@/utils/metadataResponse'
export { notifyDraftChange } from '@/utils/governanceWorkspace'

export function listGovernanceRequests(params) {
  return request.get('/rule/governance/requests', { ...metadataResponse, params })
}

export function listWorkspaceDrafts(params) {
  return request.get('/rule/governance/workspaces', { ...metadataResponse, params })
}

export function getWorkspaceDraft(type, id) {
  return request.get(`/rule/governance/workspaces/${type}/${id}`, metadataResponse)
}

export async function editableResource(type, id, fallback) {
  const response = await getWorkspaceDraft(type, id)
  const json = response?.data?.draftSnapshotJson
  return json ? parseMetadataResponse(json) : fallback
}

export function getGovernanceSummary(params) {
  return request.get('/rule/governance/requests/summary', { params })
}

export function getGovernanceRequest(id) {
  return request.get(`/rule/governance/requests/${id}`, metadataResponse)
}

export async function createGovernanceDraft(data) {
  const response = await request.post('/rule/governance/drafts', data, metadataResponse)
  notifyDraftChange()
  return response
}

export function createResourceDraft(resourceType, resource, action, options = {}) {
  const resourceId =
    options.resourceId == null ? resource && resource.id : options.resourceId
  if (action === 'DELETE') return deleteResource(resourceType, resourceId)
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

export async function deleteResource(type, id) {
  const response = await request.post(`/rule/governance/resources/${type}/${id}/delete`, undefined, metadataResponse)
  notifyDraftChange()
  return { ...response, data: { ...response.data, id: response.data?.approvalRequestId, action: 'DELETE' } }
}

export function saveGovernanceDraft(id, data) {
  return changed(request.put(`/rule/governance/requests/${id}/draft`, data, metadataResponse))
}

export function preflightGovernanceRequest(id) {
  return request.post(`/rule/governance/requests/${id}/preflight`, undefined, metadataResponse)
}

export function submitGovernanceRequest(id, comment, expectedSnapshotDigest) {
  return changed(request.post(`/rule/governance/requests/${id}/submit`, { comment, ...(expectedSnapshotDigest ? { expectedSnapshotDigest } : {}) }, metadataResponse))
}

export function approveGovernanceRequest(id, comment) {
  return changed(request.post(`/rule/governance/requests/${id}/approve`, { comment }, metadataResponse))
}

export function rejectGovernanceRequest(id, comment) {
  return changed(request.post(`/rule/governance/requests/${id}/reject`, { comment }, metadataResponse))
}

export function cancelGovernanceRequest(id, comment) {
  return changed(request.post(`/rule/governance/requests/${id}/cancel`, { comment }, metadataResponse))
}

async function changed(pending) {
  const response = await pending
  notifyDraftChange()
  return response
}

export function listGovernanceVersions(type, id) {
  return request.get(`/rule/governance/resources/${type}/${id}/versions`, metadataResponse)
}

export function restoreGovernanceVersion(type, id, sourceVersionId, restoreMode = 'NEW') {
  return request.post(`/rule/governance/resources/${type}/${id}/restore`, {
    sourceVersionId, restoreMode
  }, metadataResponse)
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
