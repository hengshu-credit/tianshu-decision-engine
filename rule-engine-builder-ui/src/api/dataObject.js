import request from './request'
import { createResourceDraft } from './governance'

export function importJavaEntity(projectId, objectType, javaSource, scope = 'PROJECT') {
  return request({ url: '/rule/dataobject/import/java', method: 'post', data: { projectId, objectType, javaSource, scope } })
}

export function importJavaFile(projectId, objectType, file, scope = 'PROJECT') {
  const formData = new FormData()
  if (projectId != null && projectId !== '') formData.append('projectId', projectId)
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

export function validateDataObjectFieldReference(objectId, field) {
  return request({ url: `/rule/dataobject/${objectId}/field/validate-reference`, method: 'post', data: field })
}

export function getVariableTree(projectId) {
  return request({ url: `/rule/dataobject/tree/${projectId}`, method: 'get' })
}

export async function createOrUpdateDataObject(obj) {
  const aggregate = obj.id
    ? { ...(await loadDataObjectAggregate(obj.id)), ...obj }
    : { ...obj, fields: obj.fields || [] }
  return createResourceDraft(
    'DATA_OBJECT',
    aggregate,
    obj.id ? 'UPDATE' : 'CREATE',
    { changeSummary: obj.id ? '修改数据对象' : '新建数据对象' }
  )
}

export async function toGlobalDataObject(id) {
  const aggregate = await loadDataObjectAggregate(id)
  aggregate.scope = 'GLOBAL'
  aggregate.projectId = 0
  aggregate.fields = aggregate.fields.map(field => ({
    ...field,
    scope: 'GLOBAL',
    projectId: 0
  }))
  return createResourceDraft('DATA_OBJECT', aggregate, 'UPDATE', {
    changeSummary: '将数据对象转为全局资源'
  })
}

export async function updateObjectType(id, objectType) {
  const aggregate = await loadDataObjectAggregate(id)
  aggregate.objectType = objectType
  return createResourceDraft('DATA_OBJECT', aggregate, 'UPDATE', {
    changeSummary: '修改数据对象类型'
  })
}

export function deleteDataObject(id) {
  return createResourceDraft('DATA_OBJECT', null, 'DELETE', {
    resourceId: id,
    changeSummary: '删除数据对象'
  })
}

/** 在数据对象下新增字段 */
export async function createDataObjectField(objectId, field) {
  const aggregate = await loadDataObjectAggregate(objectId)
  aggregate.fields.push({ ...field, id: null, objectId })
  return createResourceDraft('DATA_OBJECT', aggregate, 'UPDATE', {
    changeSummary: '新增数据对象字段'
  })
}

/** 更新数据对象字段 */
export async function updateDataObjectField(field) {
  const aggregate = await loadDataObjectAggregate(field.objectId)
  aggregate.fields = aggregate.fields.map(item =>
    item.id === field.id ? { ...item, ...field } : item
  )
  return createResourceDraft('DATA_OBJECT', aggregate, 'UPDATE', {
    changeSummary: '修改数据对象字段'
  })
}

/** 删除数据对象字段 */
export async function deleteDataObjectField(fieldId, objectId) {
  const aggregate = await loadDataObjectAggregate(objectId)
  const removed = new Set([fieldId])
  let changed = true
  while (changed) {
    changed = false
    aggregate.fields.forEach(field => {
      if (removed.has(field.parentFieldId) && !removed.has(field.id)) {
        removed.add(field.id)
        changed = true
      }
    })
  }
  aggregate.fields = aggregate.fields.filter(field => !removed.has(field.id))
  return createResourceDraft('DATA_OBJECT', aggregate, 'UPDATE', {
    changeSummary: '删除数据对象字段'
  })
}

export function getDataObjectFieldOptions(fieldId) {
  return request({ url: `/rule/dataobject/field/${fieldId}/options`, method: 'get' })
}

export async function saveDataObjectFieldOptions(fieldId, options, objectId) {
  const aggregate = await loadDataObjectAggregate(objectId)
  aggregate.fields = aggregate.fields.map(field =>
    field.id === fieldId ? { ...field, options } : field
  )
  return createResourceDraft('DATA_OBJECT', aggregate, 'UPDATE', {
    changeSummary: '修改数据对象字段选项'
  })
}

export function batchValidateRules(projectId) {
  if (!projectId) return batchValidateAll()
  return request({ url: `/rule/variable/batch-validate/${projectId}`, method: 'post' })
}

export function batchValidateAll() {
  return request({ url: `/rule/variable/batch-validate`, method: 'post' })
}

async function loadDataObjectAggregate(objectId) {
  const response = await getDataObject(objectId)
  const detail = response.data || {}
  const object = detail.object || detail
  const fields = detail.fields || detail.variables || []
  const enriched = await Promise.all(
    fields.map(async field => {
      const optionResponse = await getDataObjectFieldOptions(field.id)
      return { ...field, options: optionResponse.data || [] }
    })
  )
  return { ...object, fields: enriched }
}
