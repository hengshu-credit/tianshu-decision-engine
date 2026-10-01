import request from './request'

const RESOURCE_TYPES = new Set(['EXTERNAL_API', 'DATABASE', 'VARIABLE', 'MODEL'])

/**
 * 检查资源当前已保存的生效配置。
 * 该接口只做配置级检查，不会提交草稿，也不会执行真实网络/数据库调用。
 */
export function getResourcePreflight(resourceType, resourceId) {
  const normalizedType = String(resourceType || '').trim().toUpperCase()
  if (!RESOURCE_TYPES.has(normalizedType)) {
    return Promise.reject(new Error('不支持的资源类型'))
  }
  if (resourceId === null || resourceId === undefined || resourceId === '') {
    return Promise.reject(new Error('资源尚未保存'))
  }
  return request({
    url: `/rule/preflight/${normalizedType}/${encodeURIComponent(resourceId)}`,
    method: 'get'
  })
}

