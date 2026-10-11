import request from './request'
import { metadataResponse } from '@/utils/metadataResponse'

export function listFieldVersions(identity) {
  return request({ url: `/rule/${identity.module}/picker-versions/${identity.ownerId}`, method: 'get', ...metadataResponse })
}

export function getFieldVersion(identity, versionId, versionSource) {
  return request({ url: `/rule/${identity.module}/picker-version/${versionId}`, method: 'get', params: { refType: identity.refType, refId: identity.refId, ...(versionSource ? { versionSource } : {}) }, ...metadataResponse })
}

export function getFieldVersionFields(identity, versionId, versionSource) {
  return request({ url: `/rule/${identity.module}/picker-version-fields/${versionId}`, method: 'get', params: { ownerId: identity.ownerId, ...(versionSource ? { versionSource } : {}) }, ...metadataResponse })
}
