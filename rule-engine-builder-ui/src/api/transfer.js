import request from './request'

export function exportResourceTransfer(roots) {
  return request({
    url: '/rule/transfer/export',
    method: 'post',
    data: roots,
    responseType: 'blob'
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
