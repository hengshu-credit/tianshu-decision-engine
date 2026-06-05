import request from './request'

/** 健康检查 */
export function checkModelHealth() {
  return request({ url: '/rule/model/health', method: 'get' })
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

/** 添加全局模型到项目 */
export function addModelRef(modelId, projectId) {
  return request({ url: '/rule/model/ref', method: 'post', data: { modelId, projectId } })
}

/** 从项目移除全局模型 */
export function removeModelRef(modelId, projectId) {
  return request({ url: '/rule/model/ref', method: 'delete', data: { modelId, projectId } })
}