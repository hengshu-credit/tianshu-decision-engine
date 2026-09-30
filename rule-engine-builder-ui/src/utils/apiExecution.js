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
  let parsed = {}
  let parseError = ''
  if (text) {
    try {
      parsed = JSON.parse(text)
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) parsed = {}
    } catch (error) {
      parseError = error.message || '统一链路配置不是合法 JSON'
    }
  }
  const config = { ...emptyExecution(), ...parsed }
  if (parseError) Object.defineProperty(config, '__parseError', { value: parseError, enumerable: false })
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
  if (!spec || typeof spec !== 'object' || Array.isArray(spec)) return '统一链路配置必须是 JSON 对象'
  if (spec.version !== 2) return '统一链路配置版本必须为 2'
  if (requestMode === 'ASYNC' && !(spec.steps || []).length) return '异步接口请至少添加一个链路步骤'
  const validateFields = (fields, label = '请求字段') => {
    const seen = new Set()
    const targets = new Set()
    for (const field of fields || []) {
      if (!field.path?.trim()) return `${label}路径不能为空`
      if (!field.id || seen.has(field.id)) return `${label} ID 重复或缺失`
      if (!field.location || !['HEADER', 'QUERY', 'JSON', 'FORM', 'FORM_DATA'].includes(field.location)) return `${label}位置无效`
      const target = `${field.location}:${field.path}`
      if (targets.has(target)) return `${label}目标路径重复：${field.path}`
      if (field.location === 'HEADER' && field.overridable) return 'Header 字段不能由变量或对象覆盖'
      if (field.required && !field.value && !field.defaultValue) return `请配置必填字段 ${field.path} 的取值`
      seen.add(field.id)
      targets.add(target)
    }
    return ''
  }
  let error = validateFields(spec.requestFields)
  if (error) return error
  for (const key of ['requestBranches', 'responseBranches', 'exceptionBranches', 'billingBranches', 'retryBranches']) {
    const branches = spec[key] || []
    const branchIds = new Set()
    for (const branch of branches) {
      if (!branch.id || branchIds.has(branch.id)) return `${key} 分支 ID 重复或缺失`
      branchIds.add(branch.id)
      error = validateFields(branch.requestFields, `${key} 分支请求字段`)
      if (error) return error
    }
  }
  if ((spec.steps || []).length && requestMode !== 'ASYNC') return '多步链路需要选择异步模式'
  const ids = new Set()
  for (const [index, step] of (spec.steps || []).entries()) {
    if (!step.id || ids.has(step.id)) return '步骤 ID 重复或缺失'
    const prior = new Set(ids)
    ids.add(step.id)
    if (step.type === 'HTTP' && !step.apiConfigId && !step.endpointUrl) return '请填写步骤请求地址或关联 API'
    if (!['HTTP', 'CALLBACK'].includes(step.type)) return `第 ${index + 1} 个步骤类型无效`
    if (step.type === 'CALLBACK' && !/^https?:\/\/.+\/api\/external-callback\/\$\{invocationId\}$/.test(step.callback?.url || '')) return '回调步骤需要填写公网回调地址模板'
    if (step.type === 'HTTP' && step.poll && !step.poll.until?.children?.length) return `第 ${index + 1} 个轮询步骤必须配置完成条件`
    error = validateFields(step.requestFields, `第 ${index + 1} 个步骤请求字段`)
    if (error) return error
    const text = JSON.stringify(step)
    const matcher = /(?:\$\.|\$\{)?steps\.([A-Za-z0-9_-]+)\./g
    let match
    while ((match = matcher.exec(text)) !== null) {
      if (!prior.has(match[1])) return `步骤 ${step.id} 引用了尚未完成的步骤 ${match[1]}`
    }
  }
  return ''
}
