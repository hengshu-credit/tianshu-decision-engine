import request from './request'

export function listFunctionsByProject(projectId, params) {
  return request.get('/rule/function/project/' + projectId, { params })
}

/** 查询项目下全部启用函数（非分页，设计器变量面板使用） */
export function listAllFunctionsByProject(projectId) {
  return request.get('/rule/function/project/' + projectId + '/all')
}

/** 查询函数列表（支持全局查询） */
export function listFunctions(params) {
  return request.get('/rule/function/list', { params })
}

export function getFunctionById(id) {
  return request.get('/rule/function/' + id)
}

export function createFunction(data) {
  return request.post('/rule/function', data)
}

export function updateFunction(data) {
  return request.put('/rule/function', data)
}

export function deleteFunction(id) {
  return request.delete('/rule/function/' + id)
}
