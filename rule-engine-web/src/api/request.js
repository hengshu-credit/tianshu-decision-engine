import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { operationPagePath } from '@/security/operationActivity'
import { PLATFORM_CODES, PLATFORM_SUCCESS_CODE, platformResponseMessage, acceptsExecutionFailure, isPlatformResponse } from '@/utils/platformResponse'

const REQUEST_ERROR_DEDUPE_MS = 1500
const recentRequestErrors = new Map()

export function isRequestErrorNotified(error) {
  return Boolean(error && error.requestErrorNotified)
}

function showRequestError(message, error) {
  if (error) error.requestErrorNotified = true
  const text = message || '请求失败'
  const now = Date.now()
  for (const [recentText, shownAt] of recentRequestErrors) {
    if (now - shownAt >= REQUEST_ERROR_DEDUPE_MS) {
      recentRequestErrors.delete(recentText)
    }
  }
  if (recentRequestErrors.has(text)) return
  recentRequestErrors.set(text, now)
  ElMessage.error(text)
}

const service = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true
})

service.interceptors.request.use(config => {
  const pagePath = operationPagePath(router.currentRoute.value)
  if (pagePath && pagePath !== '/login') config.headers['X-Console-Page'] = encodeURI(pagePath)
  // 过滤 params 中的 null/undefined，避免被 axios 序列化为字面量 "null" 导致后端 Long 类型转换失败
  if (config.params) {
    Object.keys(config.params).forEach(key => {
      if (config.params[key] === null || config.params[key] === undefined) {
        delete config.params[key]
      }
    })
  }
  return config
}, error => Promise.reject(error))

service.interceptors.response.use(
  response => {
    if (response.config && response.config.responseType === 'blob') {
      return response
    }
    const res = response.data || {}
    if (!isPlatformResponse(res, response.status)) {
      const error = Object.assign(new Error(PLATFORM_CODES.UPSTREAM_RESPONSE_INVALID.message), {
        code: PLATFORM_CODES.UPSTREAM_RESPONSE_INVALID.code, response, config: response.config,
      })
      showRequestError(error.message, error)
      return Promise.reject(error)
    }
    const reqUrl = (response.config && response.config.url) || ''
    if ([PLATFORM_CODES.LOGIN_REQUIRED.code, PLATFORM_CODES.TOKEN_INVALID.code].includes(res.code)) {
      import('@/security/permissionState').then(({ clearCurrentUser }) => clearCurrentUser())
      if (reqUrl.includes('/auth/console/login')) {
        const error = new Error(platformResponseMessage(res, '登录失败'))
        Object.assign(error, { code: res.code, response, config: response.config })
        showRequestError(error.message, error)
        return Promise.reject(error)
      }
      if (router.currentRoute.value.path !== '/login') {
        router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      }
      const error = new Error(platformResponseMessage(res, '未登录'))
      Object.assign(error, { code: res.code, response, config: response.config })
      showRequestError(error.message, error)
      return Promise.reject(error)
    }
    if (acceptsExecutionFailure(response.config, res)) return res
    if (res.code !== PLATFORM_SUCCESS_CODE) {
      const error = new Error(platformResponseMessage(res))
      error.code = res.code
      error.response = response
      error.config = response.config
      showRequestError(error.message, error)
      return Promise.reject(error)
    }
    return res
  },
  async error => {
    const status = error.response && error.response.status
    const url = (error.config && error.config.url) || ''
    let data = error.response && error.response.data
    if (data instanceof Blob && /json/i.test(data.type)) {
      try {
        const text = await new Promise((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = () => resolve(reader.result)
          reader.onerror = () => reject(reader.error)
          reader.readAsText(data)
        })
        data = JSON.parse(text)
        error.response.data = data
      } catch { /* 非 JSON 下载错误仍使用网络错误信息。 */ }
    }
    if (data != null && !isPlatformResponse(data, status)) {
      data = { ...PLATFORM_CODES.UPSTREAM_RESPONSE_INVALID }
    }
    if (acceptsExecutionFailure(error.config, data)) return data
    const msg = platformResponseMessage(data, error.message || '网络异常')
    error.message = msg
    if (data?.code) error.code = data.code
    if (status === 401 && !url.includes('/auth/console/login')) {
      import('@/security/permissionState').then(({ clearCurrentUser }) => clearCurrentUser())
      if (router.currentRoute.value.path !== '/login') {
        router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      }
    }
    showRequestError(msg, error)
    return Promise.reject(error)
  }
)

export default service
