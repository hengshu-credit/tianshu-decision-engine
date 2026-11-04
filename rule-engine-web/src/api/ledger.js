import request from './request'
import { metadataResponse } from '@/utils/metadataResponse'

const base = '/rule/ledger'
const params = value => ({ ...value, eventIds: value?.eventIds?.length ? value.eventIds.join(',') : undefined })
export const listLedgerTimeline = query => request.get(`${base}/timeline`, { ...metadataResponse, params: params(query) })
export const getLedgerCalendar = query => request.get(`${base}/calendar`, { ...metadataResponse, params: params(query) })
export const listLedgerArticles = query => request.get(`${base}/articles`, { ...metadataResponse, params: query })
export const getLedgerArticleDraft = id => request.get(`${base}/articles/${id}/edit`, metadataResponse)
export const saveLedgerDraft = (id, data) => id ? request.put(`${base}/articles/${id}/draft`, data, metadataResponse) : request.post(`${base}/articles`, data, metadataResponse)
export const publishLedgerArticle = (id, version) => request.post(`${base}/articles/${id}/publish`, { version }, metadataResponse)
export const discardLedgerDraft = (id, version) => request.delete(`${base}/articles/${id}/draft`, { params: { version } })
export const restoreLedgerVersion = (id, revisionId, version) => request.post(`${base}/articles/${id}/restore/${revisionId}`, { version }, metadataResponse)
export const getLedgerVersions = id => request.get(`${base}/articles/${id}/versions`, metadataResponse)
export const getLedgerVersionEvents = id => request.get(`${base}/articles/${id}/version-events`, metadataResponse)
export const getLedgerRevision = id => request.get(`${base}/revisions/${id}`, metadataResponse)
export const listLedgerEvents = query => request.get(`${base}/events`, { ...metadataResponse, params: query })
export const getLedgerEvent = id => request.get(`${base}/events/${id}`, metadataResponse)
export const saveLedgerEvent = (id, data) => id ? request.put(`${base}/events/${id}`, data, metadataResponse) : request.post(`${base}/events`, data, metadataResponse)
export const deleteLedgerEvent = (id, version) => request.delete(`${base}/events/${id}`, { params: { version } })
export const listLedgerAssets = query => request.get(`${base}/assets`, { ...metadataResponse, params: query })
export function uploadLedgerAsset(file) {
  const data = new FormData()
  data.append('file', file)
  return request.post(`${base}/assets`, data, { ...metadataResponse, timeout: 40000 })
}
export const updateLedgerAsset = (id, data) => request.put(`${base}/assets/${id}`, data, metadataResponse)
export const scanLedgerAsset = (id, version) => request.post(`${base}/assets/${id}/scan`, { version }, { ...metadataResponse, timeout: 40000 })
export const deleteLedgerAssets = ids => request.delete(`${base}/assets`, { data: ids })
export const getLedgerAssetReferences = id => request.get(`${base}/assets/${id}/references`, metadataResponse)
export const downloadLedgerAsset = id => request.get(`${base}/assets/${id}/content`, { params: { download: true }, responseType: 'blob' })
