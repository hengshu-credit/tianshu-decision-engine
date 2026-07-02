import request from './request'

export function listLineageOptions(params) {
  return request({ url: '/rule/lineage/options', method: 'get', params })
}

export function getLineageGraph(params) {
  return request({ url: '/rule/lineage/graph', method: 'get', params })
}
