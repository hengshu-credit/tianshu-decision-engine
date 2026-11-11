import axios from 'axios'
import { PLATFORM_SUCCESS_CODE } from '@/utils/platformResponse'

// 启动阶段使用独立客户端，公开接口的失败不触发业务请求的登录重定向。
const client = axios.create({ baseURL: '/api', withCredentials: true, timeout: 15000 })

export async function getPublicConsoleResource(path) {
  try {
    return await client.get(path, { timeout: 5000 })
  } catch (error) {
    const transient = ['ERR_NETWORK', 'ECONNABORTED', 'ETIMEDOUT'].includes(error.code)
      || [502, 503, 504].includes(error.response?.status)
    if (!transient) throw error
    return client.get(path, { timeout: 10000 })
  }
}

let pendingConfig
let cachedConfig
let fetchedAt = 0

export function fetchConsoleConfig() {
  if (cachedConfig && Date.now() - fetchedAt < 10000) return Promise.resolve(cachedConfig)
  if (!pendingConfig) {
    pendingConfig = getPublicConsoleResource('/auth/console/config')
      .then(({ data }) => {
        if (data?.code === PLATFORM_SUCCESS_CODE && typeof data.data?.loginEnabled === 'boolean' && !data.data.initializationRequired) {
          cachedConfig = data
          fetchedAt = Date.now()
        }
        return data
      })
      .catch(() => null)
      .finally(() => { pendingConfig = null })
  }
  return pendingConfig
}

export function fetchMe() {
  return client.get('/auth/console/me').then(response => response.data).catch(() => null)
}
