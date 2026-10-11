import request from './request'
import { metadataResponse } from '@/utils/metadataResponse'
import { notifyDraftChange } from '@/utils/governanceWorkspace'

const path = (type, id) => `/rule/resource-versions/${type}/${id}`
export const getResourceVersions = (type, id) => request.get(path(type, id), metadataResponse)
export const getResourceVersionEvents = (type, id) => request.get(`${path(type, id)}/events`, metadataResponse)
export const getVersionDeletionImpact = (type, id, versionId) => request.get(`${path(type, id)}/${versionId}/deletion-impact`, metadataResponse)
export async function createVersionDraft(type, id, data) {
  const response = await request.post(`${path(type, id)}/drafts`, data, metadataResponse)
  notifyDraftChange()
  return response
}
export async function deleteResourceVersion(type, id, versionId, expectedSnapshotId) {
  const response = await request.delete(`${path(type, id)}/${versionId}`, { params: { expectedSnapshotId } })
  notifyDraftChange()
  return response
}
