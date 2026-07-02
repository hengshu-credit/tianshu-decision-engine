import request from './request'

export function listDbDatasources(params) {
  return request({ url: '/rule/database/list', method: 'get', params })
}

export function getDbDatasource(id) {
  return request({ url: `/rule/database/${id}`, method: 'get' })
}

export function createDbDatasource(data) {
  return request({ url: '/rule/database', method: 'post', data })
}

export function updateDbDatasource(data) {
  return request({ url: '/rule/database', method: 'put', data })
}

export function deleteDbDatasource(id) {
  return request({ url: `/rule/database/${id}`, method: 'delete' })
}

export function testDbDatasource(id) {
  return request({ url: `/rule/database/${id}/test`, method: 'post' })
}

export function testDbDatasourceDraft(data) {
  return request({ url: '/rule/database/test', method: 'post', data })
}

export function queryDbDatasource(id, data) {
  return request({ url: `/rule/database/${id}/query`, method: 'post', data })
}
