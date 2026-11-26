import request from './request'
import { metadataResponse } from '@/utils/metadataResponse'

export function listLineageOptions(params) {
  return request({ ...metadataResponse, url: '/rule/lineage/options', method: 'get', params })
}

export function getLineageGraph(params) {
  return request({ ...metadataResponse, url: '/rule/lineage/graph', method: 'get', params })
}
