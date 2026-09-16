import request from './request'
import { createResourceDraft } from './governance'

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
  return createResourceDraft('VARIABLE', data, 'CREATE', {
    changeSummary: '新建字段'
  })
}

export function updateVariable(data) {
  return createResourceDraft('VARIABLE', data, 'UPDATE', {
    changeSummary: '修改字段配置'
  })
}

export async function toGlobalVariable(id) {
  const response = await getVariable(id)
  return createResourceDraft(
    'VARIABLE',
    {
      ...(response.data || {}),
      scope: 'GLOBAL',
      projectId: 0
    },
    'UPDATE',
    { changeSummary: '将字段转为全局资源' }
  )
}

export function deleteVariable(id) {
  return createResourceDraft('VARIABLE', null, 'DELETE', {
    resourceId: id,
    changeSummary: '删除字段'
  })
}

export function testVariable(id, params) {
  return request({ url: `/rule/variable/${id}/test`, method: 'post', data: params || {}, timeout: 0 })
}

export function getVariableSourceOptions(params) {
  return request({
    url: '/rule/variable/source-options',
    method: 'get',
    params
  })
}

export function previewVariableDraft(variable, params) {
  return request({
    url: '/rule/variable/preview',
    method: 'post',
    timeout: 0,
    data: { variable, params: params || {} }
  })
}

export function getVariableOptions(variableId) {
  return request({ url: `/rule/variable/${variableId}/options`, method: 'get' })
}

export async function saveVariableOptions(variableId, options) {
  const response = await getVariable(variableId)
  return createResourceDraft(
    'VARIABLE',
    { ...(response.data || {}), options },
    'UPDATE',
    { changeSummary: '修改字段选项' }
  )
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

export function createFieldValidationDraft(data) {
  return createResourceDraft('FIELD_VALIDATION', data, 'CREATE', {
    projectId: data && data.scope === 'GLOBAL' ? 0 : data && data.projectId,
    changeSummary: '新建字段校验规则'
  })
}

export function updateFieldValidationDraft(data) {
  return createResourceDraft('FIELD_VALIDATION', data, 'UPDATE', {
    changeSummary: '修改字段校验规则'
  })
}

export function changeFieldValidationStatusDraft(data, status) {
  return createResourceDraft(
    'FIELD_VALIDATION',
    data,
    status === 1 ? 'ENABLE' : 'DISABLE',
    {
      changeSummary: status === 1
        ? '启用字段校验规则'
        : '停用字段校验规则'
    }
  )
}

export function deleteFieldValidationDraft(data) {
  return createResourceDraft('FIELD_VALIDATION', null, 'DELETE', {
    resourceId: data && data.id,
    projectId: data && data.projectId,
    changeSummary: '删除字段校验规则'
  })
}

/** 从 Java 常量类批量导入（写入变量表，来源为 CONSTANT） */
export function importJavaConstants(javaSource, scope, projectId) {
  return request({ url: '/rule/variable/import/constants/java', method: 'post', data: { javaSource, scope, projectId } })
}

/** 从 JSON 顶层键值批量导入常量，支持基本类型、数组和对象 */
export function importJsonConstants(jsonContent, scope, projectId) {
  return request({ url: '/rule/variable/import/constants/json', method: 'post', data: { jsonContent, scope, projectId } })
}
