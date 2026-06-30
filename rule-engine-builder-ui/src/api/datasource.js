import request from './request'

export function listDatasources(params) {
  return request({ url: '/rule/datasource/list', method: 'get', params })
}

export function getDatasource(id) {
  return request({ url: `/rule/datasource/${id}`, method: 'get' })
}

export function createDatasource(data) {
  return request({ url: '/rule/datasource', method: 'post', data })
}

export function updateDatasource(data) {
  return request({ url: '/rule/datasource', method: 'put', data })
}

export function deleteDatasource(id) {
  return request({ url: `/rule/datasource/${id}`, method: 'delete' })
}

export function listApiConfigs(params) {
  return request({ url: '/rule/datasource/api-config/list', method: 'get', params })
}

export function createApiConfig(data) {
  return request({ url: '/rule/datasource/api-config', method: 'post', data })
}

export function updateApiConfig(data) {
  return request({ url: '/rule/datasource/api-config', method: 'put', data })
}

export function deleteApiConfig(id) {
  return request({ url: `/rule/datasource/api-config/${id}`, method: 'delete' })
}

export function invokeApiConfig(id, data) {
  return request({ url: `/rule/datasource/api-config/${id}/invoke`, method: 'post', data })
}
