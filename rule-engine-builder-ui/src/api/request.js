import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const service = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true
})

service.interceptors.request.use(config => {
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
    const res = response.data
    const reqUrl = (response.config && response.config.url) || ''
    if (res.code === 401) {
      if (reqUrl.includes('/auth/console/login')) {
        ElMessage.error(res.message || '登录失败')
        return Promise.reject(new Error(res.message || '登录失败'))
      }
      if (router.currentRoute.value.path !== '/login') {
        router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      }
      ElMessage.error(res.message || '未登录')
      return Promise.reject(new Error(res.message || '未登录'))
    }
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    const status = error.response && error.response.status
    const url = (error.config && error.config.url) || ''
    const data = error.response && error.response.data
    const msg = (data && data.message) || error.message || '网络异常'
    if (status === 401 && !url.includes('/auth/console/login')) {
      if (router.currentRoute.value.path !== '/login') {
        router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      }
    }
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default service
