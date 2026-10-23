import request from './request'
import { createResourceDraft } from './governance'

export function listSftpDatasources(params) {
  return request({ url: '/rule/sftp/list', method: 'get', params })
}

export function getSftpDatasource(id) {
  return request({ url: `/rule/sftp/${id}`, method: 'get' })
}

export function createSftpDatasource(data) {
  return createResourceDraft('SFTP', data, 'CREATE', { changeSummary: '新建 SFTP 连接池数据源' })
}

export function updateSftpDatasource(data) {
  return createResourceDraft('SFTP', data, 'UPDATE', { changeSummary: '修改 SFTP 连接池数据源' })
}

export function deleteSftpDatasource(id) {
  return createResourceDraft('SFTP', null, 'DELETE', { resourceId: id, changeSummary: '删除 SFTP 连接池数据源' })
}

export function testSftpDatasource(id) {
  return request({ url: `/rule/sftp/${id}/test`, method: 'post' })
}

export function testSftpDatasourceDraft(data) {
  return request({ url: '/rule/sftp/test', method: 'post', data })
}

export function readSftpDatasource(id, remotePath) {
  return request({ url: `/rule/sftp/${id}/read`, method: 'post', data: { remotePath } })
}

export function previewSftpLog(logId, options = {}) {
  return request({ url: `/rule/sftp/logs/${logId}/content`, method: 'get', responseType: 'blob', timeout: 0, signal: options.signal })
}
