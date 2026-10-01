import request from './request'
import { createResourceDraft } from './governance'

/** 健康检查 */
export function checkModelHealth() {
  return request({ url: '/rule/model/health', method: 'get' })
}

/** 查询 ONNX Runtime execution provider 能力 */
export function getRuntimeCapabilities() {
  return request({ url: '/rule/model/runtimeCapabilities', method: 'get' })
}

/** 检查模型编码是否冲突 */
export function checkModelCode(modelCode, scope, projectId, excludeId) {
  return request({
    url: '/rule/model/checkCode',
    method: 'get',
    params: { modelCode, scope, projectId, excludeId }
  })
}

/** 上传模型文件 */
export function uploadModel(formData, onUploadProgress) {
  return request({
    url: '/rule/model/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 30 * 60 * 1000,
    onUploadProgress
  })
}

/** 分页查询模型列表 */
export function listModels(params) {
  return request({ url: '/rule/model/list', method: 'get', params })
}

/** 获取模型详情（含字段信息） */
export function getModel(id) {
  return request({ url: `/rule/model/${id}`, method: 'get' })
}

/** 更新模型 */
export async function updateModel(data) {
  const response = await getModel(data.id)
  return createResourceDraft(
    'MODEL',
    { ...(response.data || {}), ...data },
    'UPDATE',
    { changeSummary: '修改模型配置' }
  )
}

/** 删除模型 */
export function deleteModel(id, impactToken) {
  return createResourceDraft('MODEL', null, 'DELETE', {
    resourceId: id,
    changeSummary: impactToken ? '影响分析确认后删除模型' : '删除模型'
  })
}

/** 发布模型 */
export function publishModel(id, changeLog) {
  return createResourceDraft('MODEL', null, 'ENABLE', {
    resourceId: id,
    changeSummary: changeLog || '启用模型'
  })
}

/** 下线模型 */
export function unpublishModel(id, impactToken) {
  return createResourceDraft('MODEL', null, 'DISABLE', {
    resourceId: id,
    changeSummary: impactToken ? '影响分析确认后停用模型' : '停用模型'
  })
}

export function analyzeModelImpact(id, action) {
  return request({ url: `/rule/model/impact/${id}`, method: 'post', params: { action } })
}

export function replaceModel(id, formData, impactToken, onUploadProgress) {
  formData.set('impactToken', impactToken)
  return request({
    url: `/rule/model/replace/${id}`,
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 30 * 60 * 1000,
    onUploadProgress
  })
}

/** 查询项目下所有模型（非分页） */
export function listAllModelsByProject(projectId) {
  return request({ url: `/rule/model/project/${projectId}/all`, method: 'get' })
}

/** 执行模型测试 */
export function executeModel(id, params, timeoutMs) {
  return request({ url: `/rule/model/execute/${id}`, method: 'post', data: params, timeout: timeoutMs })
}

/** 保存模型测试参数（JSON） */
export async function saveTestParams(id, testParams) {
  const response = await getModel(id)
  const model = { ...(response.data || {}) }
  let modelConfig = {}
  if (model.modelConfig) {
    try {
      modelConfig =
        typeof model.modelConfig === 'string'
          ? JSON.parse(model.modelConfig)
          : { ...model.modelConfig }
    } catch (error) {
      throw new Error('模型配置格式异常，无法安全保存测试参数')
    }
  }
  if (!modelConfig || typeof modelConfig !== 'object' || Array.isArray(modelConfig)) {
    throw new Error('模型配置格式异常，无法安全保存测试参数')
  }
  delete model.testParams
  model.modelConfig = JSON.stringify({
    ...modelConfig,
    testParams,
    testParamsSource: 'SAVED',
  })
  return createResourceDraft(
    'MODEL',
    model,
    'UPDATE',
    { changeSummary: '修改模型测试参数' }
  )
}

/** 获取模型测试参数（JSON） */
export function getTestParams(id) {
  return request({ url: `/rule/model/testParams/${id}`, method: 'get' })
}

/** 更新模型输入字段（关联变量映射） */
export async function updateModelInputField(id, data, modelId) {
  const response = await getModel(modelId)
  const model = response.data || {}
  model.inputFields = (model.inputFields || []).map(field =>
    field.id === id ? { ...field, ...data } : field
  )
  return createResourceDraft('MODEL', model, 'UPDATE', {
    changeSummary: '修改模型输入字段'
  })
}

/** 更新模型输出字段（关联变量映射） */
export async function updateModelOutputField(id, data, modelId) {
  const response = await getModel(modelId)
  const model = response.data || {}
  model.outputFields = (model.outputFields || []).map(field =>
    field.id === id ? { ...field, ...data } : field
  )
  return createResourceDraft('MODEL', model, 'UPDATE', {
    changeSummary: '修改模型输出字段'
  })
}

/** 将项目级模型转为全局模型 */
export async function toGlobalModel(id, modelCode) {
  const response = await getModel(id)
  return createResourceDraft(
    'MODEL',
    {
      ...(response.data || {}),
      modelCode,
      scope: 'GLOBAL',
      projectId: 0
    },
    'UPDATE',
    { changeSummary: '将模型转为全局资源' }
  )
}

export function listVersions(modelId) {
  return request({ url: `/rule/model/versions/${modelId}`, method: 'get' })
}

export function getVersion(modelId, version) {
  return request({ url: `/rule/model/version/${modelId}/${version}`, method: 'get' })
}

export function compareVersions(modelId, leftVersion, rightVersion) {
  return request({ url: `/rule/model/versionCompare/${modelId}`, method: 'get', params: { leftVersion, rightVersion } })
}

export function rollbackVersion(modelId, version) {
  return request({ url: `/rule/model/rollback/${modelId}/${version}`, method: 'post' })
}
