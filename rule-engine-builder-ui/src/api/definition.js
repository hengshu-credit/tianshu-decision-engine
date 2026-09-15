import request from './request'

export function compileDesignerModel(id, data) {
  return request({ url: `/rule/definition/${id}/designer/compile`, method: 'post', data })
}

export function saveDesignerDraft(id, data) {
  return request({ url: `/rule/definition/${id}/designer/drafts`, method: 'post', data })
}

export function deleteDesignerDraft(id, revisionId, lockVersion) {
  return request({ url: `/rule/definition/${id}/revisions/${revisionId}`, method: 'delete', params: { lockVersion } })
}

export function listPublishedVersions(id) {
  return request({ url: `/rule/definition/${id}/published-versions`, method: 'get' })
}

export function publishDesignerDraft(id, data) {
  return request({ url: `/rule/definition/${id}/designer/publish`, method: 'post', data })
}
import { createResourceDraft } from './governance'

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
  return createResourceDraft('RULE', null, 'DELETE', {
    resourceId: id,
    changeSummary: '删除规则'
  })
}

export function createProjectBinding(definitionId, projectId) {
  return createResourceDraft(
    'RULE_PROJECT_BINDING',
    { definitionId, projectId },
    'CREATE',
    {
      projectId,
      changeSummary: '将全局规则加入项目'
    }
  )
}

export function deleteProjectBinding(bindingId, projectId) {
  return createResourceDraft('RULE_PROJECT_BINDING', null, 'DELETE', {
    resourceId: bindingId,
    projectId,
    changeSummary: '将全局规则移出项目'
  })
}

export function saveContent(data) {
  return request({ url: '/rule/definition/save', method: 'post', data })
}

export function saveTemporaryDraft(definitionId, data) {
  return request({ url: `/rule/definition/${definitionId}/revisions/temporary-draft`, method: 'post', data })
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
  return createResourceDraft('RULE', null, 'ENABLE', {
    resourceId: id,
    changeSummary: '启用规则'
  })
}

export function unpublish(id) {
  return createResourceDraft('RULE', null, 'DISABLE', {
    resourceId: id,
    changeSummary: '下线规则'
  })
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
  return createResourceDraft('RULE', null, 'ENABLE', {
    resourceId: id,
    changeSummary: data && data.changeLog ? data.changeLog : '启用规则'
  })
}

export function unpublishRule(id) {
  return unpublish(id)
}

export function ensureDraftRevision(definitionId) {
  return request({ url: `/rule/definition/${definitionId}/revisions/draft`, method: 'post' })
}

export function createDraftRevision(definitionId, baseRevisionId) {
  return request({
    url: `/rule/definition/${definitionId}/revisions/draft`,
    method: 'post',
    data: baseRevisionId ? { baseRevisionId } : {}
  })
}

export function getRuleRevisionRepairPreview(definitionId) {
  return request({
    url: `/rule/definition/${definitionId}/revisions/repair-preview`,
    method: 'get'
  })
}

export function repairRuleRevision(definitionId, data) {
  return request({
    url: `/rule/definition/${definitionId}/revisions/repair`,
    method: 'post',
    data
  })
}

export function listRuleRevisions(definitionId) {
  return request({ url: `/rule/definition/${definitionId}/revisions`, method: 'get' })
}

export function getRuleRevision(definitionId, revisionId) {
  return request({ url: `/rule/definition/${definitionId}/revisions/${revisionId}`, method: 'get' })
}

export function getCurrentDraftRevision(definitionId) {
  return request({ url: `/rule/definition/${definitionId}/revisions/current-draft`, method: 'get' })
}

export function preflightRuleRevision(definitionId, revisionId) {
  return request({ url: `/rule/definition/${definitionId}/revisions/${revisionId}/preflight`, method: 'post' })
}

export function submitRuleRevision(definitionId, revisionId, data) {
  return lifecycleAction(definitionId, revisionId, 'submit', data)
}

export function rejectRuleRevision(definitionId, revisionId, data) {
  return lifecycleAction(definitionId, revisionId, 'reject', data)
}

export function approveRuleRevision(definitionId, revisionId, data) {
  return lifecycleAction(definitionId, revisionId, 'approve', data)
}

export function publishRuleRevision(definitionId, revisionId, data) {
  return lifecycleAction(definitionId, revisionId, 'publish', data)
}

export function offlineRuleRevision(definitionId, _revisionId, data) {
  return createResourceDraft('RULE', null, 'DISABLE', {
    resourceId: definitionId,
    changeSummary: data && data.comment ? data.comment : '下线规则'
  })
}

export function getRuleLifecycleTimeline(definitionId) {
  return request({ url: `/rule/definition/${definitionId}/revisions/timeline`, method: 'get' })
}

function lifecycleAction(definitionId, revisionId, action, data) {
  return request({
    url: `/rule/definition/${definitionId}/revisions/${revisionId}/${action}`,
    method: 'post',
    data
  })
}

export function listVersions(definitionId) {
  return request({ url: `/rule/definition/versions/${definitionId}`, method: 'get' })
}

export function getVersion(definitionId, version) {
  return request({ url: `/rule/definition/version/${definitionId}/${version}`, method: 'get' })
}

export function getVersionById(definitionId, versionId) {
  return request({ url: `/rule/definition/${definitionId}/versions/${versionId}`, method: 'get' })
}

export function createDraftFromSource(definitionId, data) {
  return request({
    url: `/rule/definition/${definitionId}/revisions/draft/from-source`,
    method: 'post',
    data,
  })
}

export function compareVersions(definitionId, leftVersion, rightVersion) {
  return request({ url: `/rule/definition/versionCompare/${definitionId}`, method: 'get', params: { leftVersion, rightVersion } })
}

export function rollbackVersion(definitionId, version) {
  return request({ url: `/rule/definition/rollback/${definitionId}/${version}`, method: 'post' })
}

export function scanReferenceIntegrity(definitionId) {
  return request({ url: `/rule/definition/reference-integrity/scan/${definitionId}`, method: 'get' })
}

export function scanAllReferenceIntegrity() {
  return request({ url: '/rule/definition/reference-integrity/scan-all', method: 'get' })
}

export function migrateReferences(data) {
  return request({ url: '/rule/definition/reference-integrity/migrate', method: 'post', data })
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

