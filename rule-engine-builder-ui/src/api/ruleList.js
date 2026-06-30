import request from './request'

export function listLibraries(params) {
  return request({ url: '/rule/list/library', method: 'get', params })
}

export function getLibrary(id) {
  return request({ url: `/rule/list/library/${id}`, method: 'get' })
}

export function createLibrary(data) {
  return request({ url: '/rule/list/library', method: 'post', data })
}

export function updateLibrary(data) {
  return request({ url: '/rule/list/library', method: 'put', data })
}

export function deleteLibrary(id) {
  return request({ url: `/rule/list/library/${id}`, method: 'delete' })
}

export function listRecords(listId, params) {
  return request({ url: `/rule/list/${listId}/record`, method: 'get', params })
}

export function createRecord(listId, data) {
  return request({ url: `/rule/list/${listId}/record`, method: 'post', data })
}

export function updateRecord(listId, data) {
  return request({ url: `/rule/list/${listId}/record`, method: 'put', data })
}

export function deleteRecord(listId, recordId) {
  return request({ url: `/rule/list/${listId}/record/${recordId}`, method: 'delete' })
}

export function listRecordLogs(listId, params) {
  return request({ url: `/rule/list/${listId}/log`, method: 'get', params })
}

export function importRecords(listId, file) {
  const data = new FormData()
  data.append('file', file)
  return request({ url: `/rule/list/${listId}/import`, method: 'post', data })
}

export const listTemplateUrl = '/api/rule/list/template'

export function listExportUrl(listId) {
  return `/api/rule/list/${listId}/export`
}
