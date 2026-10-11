import { getCurrentUser } from './permissionState'

// 与服务端同样只保留定位所需的身份参数，测试输入和凭据不进入页面路径。
export function operationPagePath(route) {
  if (!route?.path) return ''
  const query = new URLSearchParams()
  for (const key of ['projectId', 'ruleId', 'definitionId', 'sourceType', 'sourceId', 'version', 'versionId', 'revisionId', 'focus']) {
    const value = route.query?.[key]
    if (typeof value !== 'string' && typeof value !== 'number') continue
    const text = String(value)
    const valid = key === 'sourceType' ? ['REVISION', 'VERSION', 'WORKSPACE'].includes(text)
      : key === 'focus' ? /^[A-Za-z][A-Za-z0-9_-]{0,31}$/.test(text) : /^[0-9]{1,19}$/.test(text)
    if (valid) query.set(key, text)
  }
  const suffix = query.toString()
  return route.path + (suffix ? `?${suffix}` : '')
}

export function recordPageVisit(route) {
  return recordConsoleActivity({ action: 'PAGE_VIEW', pagePath: operationPagePath(route), name: route.meta?.title || route.name })
}

// API 操作由服务端记录；缓存页面和本地字段预览需补充前端呈报的访问事件。
export function recordConsoleActivity(event) {
  if (!getCurrentUser() || typeof fetch !== 'function') return
  return fetch('/api/rule/console/activity', {
    method: 'POST', credentials: 'same-origin', keepalive: true,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(event),
  }).catch(() => { /* 网络中断时不能确认访问事件已送达，API 操作日志仍由服务端独立保存。 */ })
}

export function recordTabVisit(event, route) {
  const tab = event.target?.closest?.('[role="tab"]')
  if (!tab || tab.getAttribute('aria-disabled') === 'true' || !route?.path || route.path === '/login') return
  const name = (tab.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 120)
  if (name) recordConsoleActivity({ action: 'PAGE_VIEW', pagePath: operationPagePath(route), name: `页签 · ${name}` })
}
