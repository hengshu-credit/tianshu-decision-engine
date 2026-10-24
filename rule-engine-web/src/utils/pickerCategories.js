export const REFERENCE_PICKER_CATEGORIES = Object.freeze([
  Object.freeze({ key: 'standalone', label: '普通变量' }),
  Object.freeze({ key: 'constant', label: '常量' }),
  Object.freeze({ key: 'object', label: '数据对象' }),
  Object.freeze({ key: 'model', label: '模型' }),
  Object.freeze({ key: 'external', label: '外数 API' }),
  Object.freeze({ key: 'rule', label: '规则' }),
  Object.freeze({ key: 'list', label: '名单' }),
  Object.freeze({ key: 'database', label: '数据库' }),
  Object.freeze({ key: 'sftp', label: 'SFTP' })
])

export const API_PICKER_CATEGORIES = Object.freeze([
  { key: 'api-auth', label: '当前 API · 鉴权', icon: '⚿' },
  { key: 'api-request', label: '当前 API · 请求', icon: '↗' },
  { key: 'api-headers', label: '请求 Headers', icon: 'H' },
  { key: 'api-body', label: '请求 Body / Form', icon: '{}' },
  { key: 'api-query', label: '请求 Query', icon: '?' },
  { key: 'api-cookies', label: '请求 Cookies', icon: 'C' },
  { key: 'api-response', label: '当前 API · 响应', icon: '↙' },
  { key: 'api-result', label: '自定义响应结构', icon: '◇' },
  { key: 'api-input', label: '链路入参', icon: '→' },
  { key: 'api-steps', label: '已完成步骤', icon: '≡' },
  { key: 'api-callback', label: '回调地址', icon: '↩' },
  { key: 'api-status', label: '调用状态与追踪', icon: 'i' },
])

export function pickerReferenceCategory(item) {
  return item?._ref?.category || item?.category || item?.groupCategory || 'standalone'
}

export function pickerCategoryLabel(key) {
  const found = [...REFERENCE_PICKER_CATEGORIES, ...API_PICKER_CATEGORIES].find(item => item.key === key)
  return found ? found.label : key
}
