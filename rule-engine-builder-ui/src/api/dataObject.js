import request from './request'

export function importJavaEntity(projectId, objectType, javaSource, scope = 'PROJECT') {
  return request({ url: '/rule/dataobject/import/java', method: 'post', data: { projectId, objectType, javaSource, scope } })
}

export function importJavaFile(projectId, objectType, file, scope = 'PROJECT') {
  const formData = new FormData()
  formData.append('projectId', projectId)
  formData.append('objectType', objectType)
  formData.append('file', file)
  formData.append('scope', scope)
  return request({ url: '/rule/dataobject/import/java-file', method: 'post', data: formData, headers: { 'Content-Type': 'multipart/form-data' } })
}

export function importJsonObject(projectId, objectType, objectCode, jsonContent, scope = 'PROJECT') {
  return request({ url: '/rule/dataobject/import/json', method: 'post', data: { projectId, objectType, objectCode, jsonContent, scope } })
}

export function importDdlTable(projectId, objectType, ddlSource, scope = 'PROJECT') {
  return request({ url: '/rule/dataobject/import/ddl', method: 'post', data: { projectId, objectType, ddlSource, scope } })
}

export function listDataObjects(projectId) {
  return request({ url: `/rule/dataobject/project/${projectId}`, method: 'get' })
}

export function getDataObject(id) {
  return request({ url: `/rule/dataobject/${id}`, method: 'get' })
}

export function getVariableTree(projectId) {
  return request({ url: `/rule/dataobject/tree/${projectId}`, method: 'get' })
}

export function createOrUpdateDataObject(obj) {
  return request({ url: '/rule/dataobject', method: 'post', data: obj })
}

export function toGlobalDataObject(id) {
  return request({ url: `/rule/dataobject/toGlobal/${id}`, method: 'post' })
}

export function updateObjectType(id, objectType) {
  return request({ url: `/rule/dataobject/${id}/type`, method: 'put', data: { objectType } })
}

/** 更新数据对象的脚本引用名 */
export function updateObjectScriptName(id, scriptName) {
  return request({ url: `/rule/dataobject/${id}/script-name`, method: 'put', data: { scriptName } })
}

export function deleteDataObject(id) {
  return request({ url: `/rule/dataobject/${id}`, method: 'delete' })
}

/** 在数据对象下新增字段 */
export function createDataObjectField(objectId, field) {
  return request({ url: `/rule/dataobject/${objectId}/field`, method: 'post', data: field })
}

/** 更新数据对象字段 */
export function updateDataObjectField(field) {
  return request({ url: '/rule/dataobject/field', method: 'put', data: field })
}

/** 删除数据对象字段 */
export function deleteDataObjectField(fieldId) {
  return request({ url: `/rule/dataobject/field/${fieldId}`, method: 'delete' })
}

export function getDataObjectFieldOptions(fieldId) {
  return request({ url: `/rule/dataobject/field/${fieldId}/options`, method: 'get' })
}

export function saveDataObjectFieldOptions(fieldId, options) {
  return request({ url: `/rule/dataobject/field/${fieldId}/options`, method: 'post', data: options })
}

export function batchValidateRules(projectId) {
  if (!projectId) return batchValidateAll()
  return request({ url: `/rule/variable/batch-validate/${projectId}`, method: 'post' })
}

export function batchValidateAll() {
  return request({ url: `/rule/variable/batch-validate`, method: 'post' })
}
