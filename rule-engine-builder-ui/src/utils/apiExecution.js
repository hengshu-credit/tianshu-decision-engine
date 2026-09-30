import { buildPickerOptions, buildReferenceCatalog } from '@/utils/referenceCatalog'

export const API_MODULE_FIELDS = [
  ['request.url', '请求 · URL', 'STRING'], ['request.method', '请求 · 方法', 'STRING'],
  ['request.headers', '请求 · Header', 'OBJECT'], ['request.query', '请求 · Query', 'OBJECT'],
  ['request.form', '请求 · 表单', 'OBJECT'], ['request.json', '请求 · JSON', 'OBJECT'],
  ['request.body', '请求 · 请求体', 'OBJECT'], ['response.httpStatus', '响应 · HTTP 状态码', 'INTEGER'],
  ['response.headers', '响应 · Header', 'OBJECT'], ['response.body', '响应 · 原始响应体', 'OBJECT'],
  ['body', '响应 · 组装结果', 'OBJECT'], ['authentication', '鉴权 · 完整调用记录', 'LIST'],
  ['authentication.0.request', '鉴权 · 请求', 'OBJECT'], ['authentication.0.response', '鉴权 · 返回', 'OBJECT'],
  ['status.outcome', '状态 · 调用结果', 'STRING'], ['status.httpStatus', '状态 · HTTP 状态', 'INTEGER'],
  ['status.billed', '状态 · 是否计费', 'BOOLEAN'], ['status.retryCount', '状态 · 重试次数', 'INTEGER'],
  ['status.circuitOpen', '状态 · 是否熔断', 'BOOLEAN'], ['status.exception', '状态 · 是否异常', 'BOOLEAN'],
  ['callId', '追踪 · 调用 ID', 'STRING'], ['costTimeMs', '追踪 · 耗时毫秒', 'NUMBER'],
].map(([value, label, type]) => ({ value, label, type }))

export const newApiId = () => {
  const cryptoApi = globalThis.crypto
  if (typeof cryptoApi?.randomUUID === 'function') return cryptoApi.randomUUID()
  if (typeof cryptoApi?.getRandomValues === 'function') {
    const bytes = cryptoApi.getRandomValues(new Uint8Array(16))
    bytes[6] = (bytes[6] & 0x0f) | 0x40
    bytes[8] = (bytes[8] & 0x3f) | 0x80
    const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
  }
  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}`
}
export const emptyExecution = () => ({ version: 2, nullPolicy: 'OMIT', requestFields: [], requestBranches: [], responseBranches: [], exceptionBranches: [], billingBranches: [], retryBranches: [], steps: [], samples: [] })
export function parseExecution(text) {
  const config = { ...emptyExecution(), ...(text ? JSON.parse(text) : {}) }
  if (!config.nullPolicy || config.nullPolicy === 'DEFAULT') config.nullPolicy = 'OMIT'
  const normalizeBranch = branch => ({
    condition: newCondition(),
    requestFields: [],
    outputFields: [],
    retryCount: 0,
    retryIntervalMs: 200,
    retryBackoffMultiplier: 2,
    ...branch,
  })
  config.requestBranches = (config.requestBranches || []).map(normalizeBranch)
  config.responseBranches = (config.responseBranches || []).map(branch => ({ mode: 'FIELDS', ...normalizeBranch(branch) }))
  config.exceptionBranches = (config.exceptionBranches || []).map(normalizeBranch)
  config.billingBranches = (config.billingBranches || []).map(normalizeBranch)
  config.retryBranches = (config.retryBranches || []).map(normalizeBranch)
  config.steps = (config.steps || []).map(step => ({ successConditionTree: newCondition(), exceptionConditionTree: newCondition(), retryConditionTree: newCondition(), ...step, ...(step.callback ? { callback: { successCondition: newCondition(), failureCondition: newCondition(), ...step.callback } } : {}), ...(step.poll ? { poll: { failure: newCondition(), ...step.poll } } : {}) }))
  return config
}
export const newApiField = () => ({ id: newApiId(), location: 'JSON', path: '', value: null, required: false, nullPolicy: 'DEFAULT', overridable: false })
export const newCondition = () => ({ type: 'group', operator: 'AND', children: [] })
export const literal = value => ({ kind: 'LITERAL', valueType: typeof value === 'number' ? 'NUMBER' : typeof value === 'boolean' ? 'BOOLEAN' : 'STRING', value: String(value) })

export function sampleFields(sample, prefix = 'response.body', target = '', result = []) {
  if (sample && typeof sample === 'object' && !Array.isArray(sample)) {
    Object.entries(sample).forEach(([key, value]) => {
      const segment = /^[A-Za-z_][A-Za-z0-9_]*$/.test(key) ? `.${key}` : `[${JSON.stringify(key)}]`
      const path = target ? `${target}${segment}` : segment.startsWith('.') ? key : segment
      if (value && typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length) sampleFields(value, `${prefix}${segment}`, path, result)
      else result.push({ id: newApiId(), path, value: { kind: 'PATH', value: `${prefix}${segment}`, protocolPath: true, resolved: true } })
    })
  }
  return result
}

export function samplePathOptions(sample, prefix = 'response.body') {
  const result = []
  const visit = (value, path) => {
    const type = value === null ? 'NULL' : Array.isArray(value) ? 'LIST' : typeof value === 'object' ? 'OBJECT' : typeof value === 'boolean' ? 'BOOLEAN' : typeof value === 'number' ? 'NUMBER' : 'STRING'
    result.push({ value: path, label: path, type })
    if (Array.isArray(value)) { if (value.length) visit(value[0], `${path}[0]`); return }
    if (value && typeof value === 'object') Object.entries(value).forEach(([key, item]) => {
      const segment = /^[A-Za-z_][A-Za-z0-9_]*$/.test(key) ? `.${key}` : `[${JSON.stringify(key)}]`
      visit(item, `${path}${segment}`)
    })
  }
  visit(sample, prefix)
  return result
}

export function prepareResponseSample(sample, previous = {}, strategy = 'AUTO') {
  const fieldCount = samplePathOptions(sample).filter(item => !['OBJECT', 'LIST'].includes(item.type)).length
  const whole = strategy === 'VALUE' || !sample || typeof sample !== 'object' || Array.isArray(sample) || (strategy === 'AUTO' && (previous.mode === 'VALUE' || fieldCount > 200))
  const ids = new Map((previous.outputFields || []).map(field => [field.path, field.id]))
  const value = previous.mode === 'VALUE' && previous.value ? previous.value : { kind: 'PATH', value: 'response.body', protocolPath: true, resolved: true }
  return {
    sample, mode: whole ? 'VALUE' : 'FIELDS',
    value: whole ? value : null,
    outputFields: whole ? [] : sampleFields(sample).map(field => ({ ...field, id: ids.get(field.path) || field.id })),
  }
}

export function filterApiPaths(options, query = '', selected = '') {
  const text = query.trim().toLowerCase()
  const matching = options.filter(item => !text || `${item.value} ${item.label}`.toLowerCase().includes(text))
  const visible = matching.slice(0, 100)
  const current = options.find(item => item.value === selected)
  if (current && !visible.some(item => item.value === selected)) visible.unshift(current)
  return visible
}

export function apiFieldOptions(variables, objects) {
  return buildPickerOptions(buildReferenceCatalog(variables || [], objects || [], []))
}

export function validateExecution(spec, requestMode) {
  if (requestMode === 'ASYNC' && !(spec.steps || []).length) return '异步接口请至少添加一个链路步骤'
  const seen = new Set()
  for (const field of spec.requestFields || []) {
    if (!field.path?.trim()) return '请填写请求字段路径'
    if (!field.id || seen.has(field.id)) return '请求字段 ID 重复或缺失'
    seen.add(field.id)
    if (!field.value && !field.defaultValue && field.required) return `请配置必填字段 ${field.path} 的取值`
  }
  if ((spec.steps || []).length && requestMode !== 'ASYNC') return '多步链路需要选择异步模式'
  const ids = new Set()
  for (const step of spec.steps || []) {
    if (!step.id || ids.has(step.id)) return '步骤 ID 重复或缺失'
    ids.add(step.id)
    if (step.type === 'HTTP' && !step.apiConfigId && !step.endpointUrl) return '请填写步骤请求地址或关联 API'
    if (step.type === 'CALLBACK' && !step.callback?.url) return '请填写回调地址模板'
  }
  return ''
}
