import request from './request'

export function listDefinitions(params) {
  return request({ url: '/rule/definition/list', method: 'get', params })
}

export function listProjectDefinitions(projectId, params) {
  return request({ url: `/rule/definition/project-list/${projectId}`, method: 'get', params })
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

export function refreshFields(definitionId, modelJson) {
  return request({
    url: `/rule/definition/refreshFields/${definitionId}`,
    method: 'post',
    data: modelJson,
    headers: { 'Content-Type': 'text/plain;charset=UTF-8' }
  })
}

export function getDetail(id) {
  return request({ url: `/rule/definition/detail/${id}`, method: 'get' })
}

export function inputFields(definitionId) {
  return request({ url: `/rule/definition/inputFields/${definitionId}`, method: 'get' })
}

export function outputFields(definitionId) {
  return request({ url: `/rule/definition/outputFields/${definitionId}`, method: 'get' })
}

export function publish(id) {
  return request({ url: `/rule/definition/publish/${id}`, method: 'post' })
}

export function unpublish(id) {
  return request({ url: `/rule/definition/unpublish/${id}`, method: 'post' })
}

export function copyRule(id) {
  return request({ url: `/rule/definition/copy/${id}`, method: 'post' })
}

export function compileRule(id) {
  return request({ url: `/rule/definition/compile/${id}`, method: 'post' })
}

export function validateCallCycle(definitionId, modelJson) {
  return request({ url: `/rule/definition/validateCallCycle/${definitionId}`, method: 'post', data: modelJson })
}

export function publishRule(id, data) {
  return request({ url: `/rule/definition/publish/${id}`, method: 'post', data })
}

export function unpublishRule(id) {
  return request({ url: `/rule/definition/unpublish/${id}`, method: 'post' })
}

export function listVersions(definitionId) {
  return request({ url: `/rule/definition/versions/${definitionId}`, method: 'get' })
}

export function getVersion(definitionId, version) {
  return request({ url: `/rule/definition/version/${definitionId}/${version}`, method: 'get' })
}

export function compareVersions(definitionId, leftVersion, rightVersion) {
  return request({ url: `/rule/definition/versionCompare/${definitionId}`, method: 'get', params: { leftVersion, rightVersion } })
}

export function rollbackVersion(definitionId, version) {
  return request({ url: `/rule/definition/rollback/${definitionId}/${version}`, method: 'post' })
}

export const DEFAULT_RULE_REQUEST_TIMEOUT_MS = 180000

export function executeRule(data, timeoutMs = DEFAULT_RULE_REQUEST_TIMEOUT_MS) {
  return request({ url: '/rule/definition/execute', method: 'post', data, timeout: timeoutMs })
}

export function getRuleTestSchema(data) {
  return request({ url: '/rule/test-schema', method: 'post', data })
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

export function listApiScenarios(definitionId) {
  return request({ url: `/rule/definition/${definitionId}/api-scenarios`, method: 'get' })
}

export function createApiScenario(definitionId, data) {
  return request({ url: `/rule/definition/${definitionId}/api-scenarios`, method: 'post', data })
}

export function updateApiScenario(definitionId, scenarioId, data) {
  return request({ url: `/rule/definition/${definitionId}/api-scenarios/${scenarioId}`, method: 'put', data })
}

export function deleteApiScenario(definitionId, scenarioId) {
  return request({ url: `/rule/definition/${definitionId}/api-scenarios/${scenarioId}`, method: 'delete' })
}

export function copyApiScenario(definitionId, scenarioId, scenarioName) {
  return request({
    url: `/rule/definition/${definitionId}/api-scenarios/${scenarioId}/copy`,
    method: 'post',
    data: { scenarioName }
  })
}

export function sortApiScenarios(definitionId, scenarioIds) {
  return request({
    url: `/rule/definition/${definitionId}/api-scenarios/sort`,
    method: 'put',
    data: { scenarioIds }
  })
}

export function executeApiScenario(definitionId, data, timeout = DEFAULT_RULE_REQUEST_TIMEOUT_MS) {
  return request({
    url: `/rule/definition/${definitionId}/api-scenarios/execute`,
    method: 'post',
    data,
    timeout
  })
}

