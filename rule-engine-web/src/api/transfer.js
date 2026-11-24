import request from './request'
import { metadataResponse } from '@/utils/metadataResponse'

// 含模型原件的迁移请求还需要等待安全扫描、审批及事务提交。
const transferFileTimeoutMs = 30 * 60 * 1000

export function listTransferResources(params) {
  return request({ ...metadataResponse, url: '/rule/transfer/resources', method: 'get', params })
}

export function exportResourceTransfer(roots, options = null) {
  return request({
    url: options ? '/rule/transfer/export/options' : '/rule/transfer/export',
    method: 'post',
    data: options ? { roots, ...options } : roots,
    timeout: transferFileTimeoutMs,
    responseType: 'blob'
  })
}

export function listTransferResourceFields(resourceType, resourceId) {
  return request({
    ...metadataResponse,
    url: '/rule/transfer/resource-fields',
    method: 'get',
    params: { resourceType, resourceId },
  })
}

export function listTransferVersionCandidates(refType, resourceId) {
  return request({ ...metadataResponse, url: '/rule/transfer/version-candidates', method: 'get', params: { refType, resourceId } })
}

export function transferProjectSet(file, digest = null) {
  const data = new FormData()
  data.append('file', file)
  if (digest) data.append('digest', digest)
  return request({ url: '/rule/transfer/project-set/' + (digest ? 'import' : 'preview'), method: 'post', data, timeout: transferFileTimeoutMs })
}

export function previewResourceTransfer(file, options = null) {
  const form = new FormData()
  form.append('file', file)
  if (options) form.append('options', JSON.stringify(options))
  return request({
    ...metadataResponse,
    url: options ? '/rule/transfer/preview/options' : '/rule/transfer/preview',
    timeout: transferFileTimeoutMs,
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function importResourceTransfer(file, options) {
  const form = new FormData()
  form.append('file', file)
  form.append('options', JSON.stringify(options || {}))
  return request({
    ...metadataResponse,
    url: '/rule/transfer/import',
    timeout: transferFileTimeoutMs,
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function listTransferLogs(params) {
  return request({ ...metadataResponse, url: '/rule/transfer/logs', method: 'get', params })
}

export function getTransferLog(id) {
  return request({ ...metadataResponse, url: `/rule/transfer/logs/${id}`, method: 'get' })
}

export function getTransferLogLineage(id) {
  return request({ ...metadataResponse, url: `/rule/transfer/logs/${id}/lineage`, method: 'get' })
}
