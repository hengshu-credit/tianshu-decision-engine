import request from './request'

export function listDefinitions(params) {
  return request({ url: '/rule/definition/list', method: 'get', params })
}

export function getDefinition(id) {
  return request({ url: `/rule/definition/${id}`, method: 'get' })
}

export function getContent(definitionId) {
  return request({ url: `/rule/definition/content/${definitionId}`, method: 'get' })
}

export function createDefinition(data) {
  return request({ url: '/rule/definition', method: 'post', data })
}

export function updateDefinition(data) {
  return request({ url: '/rule/definition', method: 'put', data })
}

export function deleteDefinition(id) {
  return request({ url: `/rule/definition/${id}`, method: 'delete' })
}

export function saveContent(data) {
  return request({ url: '/rule/definition/save', method: 'post', data })
}

export function compileRule(id) {
  return request({ url: `/rule/definition/compile/${id}`, method: 'post' })
}

export function publishRule(id, data) {
  return request({ url: `/rule/definition/publish/${id}`, method: 'post', data })
}

export function unpublishRule(id) {
  return request({ url: `/rule/definition/unpublish/${id}`, method: 'post' })
}

export function executeRule(data) {
  return request({ url: '/rule/definition/execute', method: 'post', data })
}

/** 技术人员直接保存脚本（脚本模式），跳过可视化编译器 */
export function saveScript(definitionId, script) {
  return request({ url: `/rule/definition/script/${definitionId}`, method: 'post', data: { script } })
}

/** 更新编辑模式（visual/script） */
export function updateScriptMode(definitionId, scriptMode) {
  return request({ url: `/rule/definition/scriptMode/${definitionId}`, method: 'post', data: { scriptMode } })
}

/** 脚本模式下验证脚本语法（不覆盖可视化模型） */
export function validateScript(definitionId, script) {
  return request({ url: `/rule/definition/validateScript/${definitionId}`, method: 'post', data: { script } })
}

/** 获取规则详情（含输入输出字段） */
export function getDefinitionDetail(id) {
  return request({ url: `/rule/definition/detail/${id}`, method: 'get' })
}

/** 获取规则输入字段列表 */
export function listInputFields(definitionId) {
  return request({ url: `/rule/definition/inputFields/${definitionId}`, method: 'get' })
}

/** 获取规则输出字段列表 */
export function listOutputFields(definitionId) {
  return request({ url: `/rule/definition/outputFields/${definitionId}`, method: 'get' })
}

/** 更新规则输入字段 */
export function updateInputField(fieldId, data) {
  return request({ url: `/rule/definition/inputField/${fieldId}`, method: 'put', data })
}

/** 更新规则输出字段 */
export function updateOutputField(fieldId, data) {
  return request({ url: `/rule/definition/outputField/${fieldId}`, method: 'put', data })
}

/** 迁移旧 JSON 字段到独立表 */
export function migrateFields(body) {
  return request({ url: '/rule/definition/migrateFields', method: 'post', data: body })
}

/** 刷新规则的输入/输出字段（从模型内容重新解析） */
export function refreshFields(definitionId) {
  return request({ url: `/rule/definition/refreshFields/${definitionId}`, method: 'post' })
}
