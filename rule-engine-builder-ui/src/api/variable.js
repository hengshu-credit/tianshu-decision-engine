import request from './request'

/** 健康检查，验证变量管理接口可用性 */
export function checkVariableHealth() {
  return request({ url: '/rule/variable/health', method: 'get' })
}

export function listVariables(params) {
  return request({ url: '/rule/variable/list', method: 'get', params })
}

export function listVariablesByProject(projectId) {
  return request({ url: `/rule/variable/project/${projectId}`, method: 'get' })
}

export function getVariable(id) {
  return request({ url: `/rule/variable/${id}`, method: 'get' })
}

export function createVariable(data) {
  return request({ url: '/rule/variable', method: 'post', data })
}

export function updateVariable(data) {
  return request({ url: '/rule/variable', method: 'put', data })
}

export function toGlobalVariable(id) {
  return request({ url: `/rule/variable/toGlobal/${id}`, method: 'post' })
}

export function deleteVariable(id) {
  return request({ url: `/rule/variable/${id}`, method: 'delete' })
}

export function testVariable(id, params) {
  return request({ url: `/rule/variable/${id}/test`, method: 'post', data: params || {} })
}

export function getVariableOptions(variableId) {
  return request({ url: `/rule/variable/${variableId}/options`, method: 'get' })
}

export function saveVariableOptions(variableId, options) {
  return request({ url: `/rule/variable/${variableId}/options`, method: 'post', data: options })
}

export function listFieldValidations(params) {
  return request({ url: '/rule/field-validation/list', method: 'get', params })
}

export function listAvailableFieldValidations(projectId) {
  return request({
    url: '/rule/field-validation/available',
    method: 'get',
    params: projectId ? { projectId } : {}
  })
}

export function createFieldValidation(data) {
  return request({ url: '/rule/field-validation', method: 'post', data })
}

export function updateFieldValidation(data) {
  return request({ url: '/rule/field-validation', method: 'put', data })
}

export function deleteFieldValidation(id) {
  return request({ url: `/rule/field-validation/${id}`, method: 'delete' })
}

/** 从 Java 常量类批量导入（写入变量表，来源为 CONSTANT） */
export function importJavaConstants(javaSource, scope, projectId) {
  return request({ url: '/rule/variable/import/constants/java', method: 'post', data: { javaSource, scope, projectId } })
}

/** 从扁平 JSON 批量导入常量 */
export function importJsonConstants(jsonContent, scope, projectId) {
  return request({ url: '/rule/variable/import/constants/json', method: 'post', data: { jsonContent, scope, projectId } })
}
