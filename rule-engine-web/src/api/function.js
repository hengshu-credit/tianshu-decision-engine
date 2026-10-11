import request from './request'
import { createResourceDraft } from './governance'
import { metadataResponse } from '@/utils/metadataResponse'
import { hasPermission } from '@/security/permissionState'

export function listFunctionsByProject(projectId, params) {
  return request.get('/rule/function/project/' + projectId, { ...metadataResponse, params })
}

/** 查询项目下全部启用函数（非分页，设计器变量面板使用） */
export function listAllFunctionsByProject(projectId) {
  if (!hasPermission('function:view')) return Promise.resolve({ data: [] })
  return request.get('/rule/function/project/' + projectId + '/all', metadataResponse)
}

/** 查询函数列表（支持全局查询） */
export function listFunctions(params) {
  return request.get('/rule/function/list', { ...metadataResponse, params })
}

export function getFunctionById(id) {
  return request.get('/rule/function/' + id, metadataResponse)
}

/** 读取已部署实现的方法和精确参数类型，不执行函数。 */
export function inspectFunctionMethods(data) {
  return request.post('/rule/function/methods', data)
}

export function createFunction(data) {
  return createResourceDraft('FUNCTION', data, 'CREATE', {
    changeSummary: '新建函数'
  })
}

export function updateFunction(data) {
  return createResourceDraft('FUNCTION', data, 'UPDATE', {
    changeSummary: '修改函数配置'
  })
}

export function deleteFunction(id) {
  return createResourceDraft('FUNCTION', null, 'DELETE', {
    resourceId: id,
    changeSummary: '删除函数'
  })
}

export function testFunction(id, params) {
  return request.post('/rule/function/' + id + '/test', params)
}

export function listVersions(functionId) {
  return request.get('/rule/function/versions/' + functionId, metadataResponse)
}

export function getVersion(functionId, version) {
  return request.get('/rule/function/version/' + functionId + '/' + version, metadataResponse)
}

export function compareVersions(functionId, leftVersion, rightVersion) {
  return request.get('/rule/function/versionCompare/' + functionId, { ...metadataResponse, params: { leftVersion, rightVersion } })
}

export function rollbackVersion(functionId, version, restoreMode = 'NEW') {
  return request.post('/rule/function/rollback/' + functionId + '/' + version, null, { params: { restoreMode } })
}
