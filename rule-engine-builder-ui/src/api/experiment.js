import request from './request'

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
  return request({ url: '/rule/experiment', method: 'post', data })
}

export function deleteExperiment(id) {
  return request({ url: `/rule/experiment/${id}`, method: 'delete' })
}

export function executeExperiment(experimentCode, data) {
  return request({ url: `/rule/experiment/execute/${experimentCode}`, method: 'post', data })
}
