import request from './request'

/** 健康检查 */
export function checkModelHealth() {
  return request({ url: '/rule/model/health', method: 'get' })
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
export function uploadModel(formData) {
  return request({
    url: '/rule/model/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
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
export function updateModel(data) {
  return request({ url: '/rule/model', method: 'put', data })
}

/** 删除模型 */
export function deleteModel(id) {
  return request({ url: `/rule/model/${id}`, method: 'delete' })
}

/** 发布模型 */
export function publishModel(id, changeLog) {
  return request({ url: `/rule/model/publish/${id}`, method: 'post', params: { changeLog } })
}

/** 下线模型 */
export function unpublishModel(id) {
  return request({ url: `/rule/model/unpublish/${id}`, method: 'post' })
}

/** 查询项目下所有模型（非分页） */
export function listAllModelsByProject(projectId) {
  return request({ url: `/rule/model/project/${projectId}/all`, method: 'get' })
}

/** 执行模型测试 */
export function executeModel(id, params) {
  return request({ url: `/rule/model/execute/${id}`, method: 'post', data: params })
}

/** 保存模型测试参数（JSON） */
export function saveTestParams(id, testParams) {
  return request({ url: `/rule/model/testParams/${id}`, method: 'post', data: { testParams } })
}

/** 获取模型测试参数（JSON） */
export function getTestParams(id) {
  return request({ url: `/rule/model/testParams/${id}`, method: 'get' })
}

/** 更新模型输入字段（关联变量映射） */
export function updateModelInputField(id, data) {
  return request({ url: `/rule/model/inputField/${id}`, method: 'put', data })
}

/** 更新模型输出字段（关联变量映射） */
export function updateModelOutputField(id, data) {
  return request({ url: `/rule/model/outputField/${id}`, method: 'put', data })
}

/** 将项目级模型转为全局模型 */
export function toGlobalModel(id, modelCode) {
  return request({ url: `/rule/model/toGlobal/${id}`, method: 'post', data: { modelCode } })
}