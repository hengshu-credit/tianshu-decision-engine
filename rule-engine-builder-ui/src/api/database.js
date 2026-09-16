import request from './request'
import { createResourceDraft } from './governance'

export function listDbDatasources(params) {
  return request({ url: '/rule/database/list', method: 'get', params })
}

export function getDbDatasource(id) {
  return request({ url: `/rule/database/${id}`, method: 'get' })
}

export function createDbDatasource(data) {
  return createResourceDraft('DATABASE', data, 'CREATE', {
    changeSummary: '新建数据库连接'
  })
}

export function updateDbDatasource(data) {
  return createResourceDraft('DATABASE', data, 'UPDATE', {
    changeSummary: '修改数据库连接'
  })
}

export function deleteDbDatasource(id) {
  return createResourceDraft('DATABASE', null, 'DELETE', {
    resourceId: id,
    changeSummary: '删除数据库连接'
  })
}

export function testDbDatasource(id) {
  return request({ url: `/rule/database/${id}/test`, method: 'post' })
}

export function testDbDatasourceDraft(data) {
  return request({ url: '/rule/database/test', method: 'post', data })
}

export function queryDbDatasource(id, data) {
  return request({ url: `/rule/database/${id}/query`, method: 'post', data, timeout: 0 })
}
