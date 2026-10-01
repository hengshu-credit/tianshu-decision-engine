import request from './request'
import { createResourceDraft } from './governance'

export function listLibraries(params) {
  return request({ url: '/rule/list/library', method: 'get', params })
}

export function getLibrary(id) {
  return request({ url: `/rule/list/library/${id}`, method: 'get' })
}

export function createLibraryDraft(data) {
  return createResourceDraft('LIST_LIBRARY', data, 'CREATE', {
    projectId: data && data.scope === 'GLOBAL' ? 0 : data && data.projectId,
    changeSummary: '新建名单库'
  })
}

export function updateLibraryDraft(data) {
  return createResourceDraft('LIST_LIBRARY', data, 'UPDATE', {
    changeSummary: '修改名单库配置'
  })
}

export function changeLibraryStatusDraft(data, status) {
  return createResourceDraft(
    'LIST_LIBRARY',
    null,
    status === 1 ? 'ENABLE' : 'DISABLE',
    {
      resourceId: data && data.id,
      projectId: data && data.projectId,
      changeSummary: status === 1 ? '启用名单库' : '停用名单库'
    }
  )
}

export function deleteLibraryDraft(data) {
  return createResourceDraft('LIST_LIBRARY', null, 'DELETE', {
    resourceId: data && data.id,
    projectId: data && data.projectId,
    changeSummary: '删除名单库'
  })
}

export function listRecords(listId, params) {
  return request({ url: `/rule/list/${listId}/record`, method: 'get', params })
}

export function stageRecordChange(listId, operation, record) {
  return request({
    url: `/rule/list/${listId}/change-batch`,
    method: 'post',
    data: { operation, record }
  })
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
