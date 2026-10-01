import request from './request'
import { createResourceDraft } from './governance'

export function listExperiments(params) {
  return request({ url: '/rule/experiment/list', method: 'get', params })
}

export function getExperiment(id) {
  return request({ url: `/rule/experiment/${id}`, method: 'get' })
}

export function listExperimentLogs(params) {
  return request({ url: '/rule/experiment/logs', method: 'get', params })
}

export function saveExperiment(data) {
  return createResourceDraft(
    'EXPERIMENT',
    data,
    data.id ? 'UPDATE' : 'CREATE',
    { changeSummary: data.id ? '修改分流实验' : '新建分流实验' }
  )
}

export function deleteExperiment(id) {
  return createResourceDraft('EXPERIMENT', null, 'DELETE', {
    resourceId: id,
    changeSummary: '删除分流实验'
  })
}

export function executeExperiment(experimentCode, data) {
  return request({ url: `/rule/experiment/execute/${experimentCode}`, method: 'post', data })
}

export function listVersions(experimentId) {
  return request({ url: `/rule/experiment/versions/${experimentId}`, method: 'get' })
}

export function getVersion(experimentId, version) {
  return request({ url: `/rule/experiment/version/${experimentId}/${version}`, method: 'get' })
}

export function compareVersions(experimentId, leftVersion, rightVersion) {
  return request({ url: `/rule/experiment/versionCompare/${experimentId}`, method: 'get', params: { leftVersion, rightVersion } })
}

export function rollbackVersion(experimentId, version) {
  return request({ url: `/rule/experiment/rollback/${experimentId}/${version}`, method: 'post' })
}
