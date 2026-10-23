function routeSegment(value) {
  return encodeURIComponent(String(value == null ? '' : value))
}

export function expressionSourceIdentity(route = {}) {
  const ordered = value => Object.fromEntries(Object.entries(value || {}).sort(([left], [right]) => left.localeCompare(right)))
  return JSON.stringify({ name: route.name || '', params: ordered(route.params), query: ordered(route.query) })
}

export function createExpressionSessionId(ruleId, pickerUid) {
  return `expression-${routeSegment(ruleId)}-${routeSegment(pickerUid)}`
}

export function createExpressionSessionTitle(routeTitle, editorTitle, placeholder) {
  const moduleTitle = String(routeTitle || '').trim().replace(/(设计器|编辑器)$/, '')
  const explicitTitle = String(editorTitle || '').trim()
  const sourceTitle = explicitTitle && explicitTitle !== '配置表达式'
    ? explicitTitle.replace(/^配置/, '')
    : String(placeholder || '').trim().replace(/[.…]+$/, '').replace(/^(请选择|选择|请输入|输入)/, '')

  if (moduleTitle) return `${moduleTitle} · ${sourceTitle || '表达式'}`
  return sourceTitle || '配置表达式'
}
