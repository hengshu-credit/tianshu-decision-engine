import request from './request'

export function listTransferResources(params) {
  return request({ url: '/rule/transfer/resources', method: 'get', params })
}

export function exportResourceTransfer(roots, options = null) {
  return request({
    url: options ? '/rule/transfer/export/options' : '/rule/transfer/export',
    method: 'post',
    data: options ? { roots, ...options } : roots,
    responseType: 'blob'
  })
}

export function listTransferResourceFields(resourceType, resourceId) {
  return request({
    url: '/rule/transfer/resource-fields',
    method: 'get',
    params: { resourceType, resourceId },
  })
}

export function previewResourceTransfer(file, options = null) {
  const form = new FormData()
  form.append('file', file)
  if (options) form.append('options', JSON.stringify(options))
  return request({
    url: options ? '/rule/transfer/preview/options' : '/rule/transfer/preview',
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
    url: '/rule/transfer/import',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function listTransferLogs(params) {
  return request({ url: '/rule/transfer/logs', method: 'get', params })
}

export function getTransferLog(id) {
  return request({ url: `/rule/transfer/logs/${id}`, method: 'get' })
}
