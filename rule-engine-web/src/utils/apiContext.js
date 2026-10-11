import { API_MODULE_FIELDS, samplePathOptions } from './apiExecution'

const rawResponse = Object.freeze([
  { value: 'httpStatus', label: 'HTTP 状态码', type: 'INTEGER' },
  { value: 'headers', label: '响应头', type: 'MAP' },
  { value: 'body', label: '响应体', type: 'OBJECT' },
])
export const uniqueApiPaths = paths => Object.freeze([...new Map(paths.filter(item => item?.value).map(item => [item.value, item])).values()])
const protocolFieldsCache = new WeakMap()

function responseStructure(branch, prefix = '') {
  const samples = Object.hasOwn(branch, 'sample') ? samplePathOptions(branch.sample) : []
  const sampleByPath = new Map(samples.map(field => [field.value, field]))
  const outputs = branch.mode === 'VALUE' ? [{ path: '', value: branch.value }] : branch.outputFields || []
  return [...samples.map(field => ({ ...field, value: prefix + field.value })), ...outputs.flatMap(field => {
    const target = prefix + 'body' + (field.path ? (field.path.startsWith('[') ? '' : '.') + field.path : '')
    const operand = field.value
    if (operand?.kind === 'LITERAL') return samplePathOptions(operand.value, target)
    if (operand?.kind === 'PATH' && !operand.refId) {
      const source = operand.value?.replace(/^body(?=\.|\[|$)/, 'response.body')
      const sample = sampleByPath.get(source)
      if (sample && !['OBJECT', 'LIST'].includes(sample.type)) return [{ ...sample, value: target }]
      const matching = samples.filter(item => item.value === source || item.value.startsWith(source + '.') || item.value.startsWith(source + '['))
      if (matching.length) return matching.map(item => ({ ...item, value: target + item.value.slice(source.length) }))
    }
    return [{ value: target, label: field.path || '组装结果', type: operand?.valueType || 'OBJECT' }]
  })]
}

export function apiStagePaths(config = {}, stage = 'RESULT', index = 0, branch = null) {
  if (stage === 'REQUEST') return []
  if (stage === 'STEP_CONDITION' || stage === 'CALLBACK') return rawResponse
  if (stage === 'RETRY') return uniqueApiPaths([...rawResponse.filter(field => field.value !== 'headers'), ...API_MODULE_FIELDS.filter(field => field.value === 'response' || field.value.startsWith('response.')),
    { value: 'errorMessage', label: '异常信息', type: 'STRING' }])
  const steps = config.steps || []
  const completed = steps.slice(0, ['STEP_REQUEST', 'STEP_RESPONSE', 'STEP_POLL'].includes(stage) ? index : steps.length)
  const stepPaths = completed.flatMap(step => [
    { value: `steps.${step.id}`, label: step.name || step.id, type: 'OBJECT' },
    ...API_MODULE_FIELDS.map(field => ({ ...field, value: `steps.${step.id}.${field.value}`, label: `${step.name || step.id} · ${field.label}` })),
    ...(step.responseBranches || []).flatMap(response => responseStructure(response, `steps.${step.id}.`)),
  ])
  const requestFields = [...(config.requestFields || []), ...(config.requestBranches || []).flatMap(branch => branch.requestFields || [])]
  const requestPaths = requestFields.map(field => ({ value: `input.__apiFields.${field.id}`, label: `链路入参 · ${field.path}`, type: field.value?.valueType || 'OBJECT' }))
  const callbacks = steps.filter(step => step.type === 'CALLBACK').map(step => ({ value: `callbacks.${step.id}.url`, label: `${step.name || step.id} · 回调地址`, type: 'STRING' }))
  if (stage === 'STEP_REQUEST') return uniqueApiPaths([...requestPaths, ...stepPaths, ...callbacks])
  const base = stage === 'RESULT' ? API_MODULE_FIELDS : API_MODULE_FIELDS.filter(field => field.value !== 'status.billed').map(field => field.value === 'body' && stage !== 'STEP_POLL' && stage !== 'POLICY' ? { ...field, label: '响应体（组装前）' } : field)
  const outputBranches = stage === 'STEP_POLL' ? steps[index]?.responseBranches || [] : ['POLICY', 'RESULT'].includes(stage) ? config.responseBranches || [] : []
  return uniqueApiPaths([...base, ...stepPaths, ...(['STEP_RESPONSE', 'STEP_POLL'].includes(stage) ? [...requestPaths, ...callbacks] : []),
    ...(branch && Object.hasOwn(branch, 'sample') ? samplePathOptions(branch.sample) : []), ...outputBranches.flatMap(response => responseStructure(response))])
}

/** 协议路径保留原文；补齐结构父级，只有实际可取值的路径允许选择。 */
export function apiProtocolFields(paths, contextLabel = '当前外数 API · HTTP 上下文', apiId = 'current-api') {
  const cacheKey = `${apiId}:${contextLabel}`
  if (Object.isFrozen(paths) && protocolFieldsCache.get(paths)?.has(cacheKey)) return protocolFieldsCache.get(paths).get(cacheKey)
  const fields = new Map()
  const declared = new Set(paths.map(path => path.value))
  const available = value => declared.has(value) || value.split('').some((char, index) => (char === '.' || char === '[') && declared.has(value.slice(0, index)))
  const add = (path, structural = false) => fields.set(path.value, {
    _varId: path.value, _refType: 'API_CONTEXT', varCode: path.value, varLabel: path.label || path.value,
    varType: path.type || 'OBJECT', disabled: structural && !available(path.value) || path.disabled === true,
    tags: ['http-context', ...(path.tags || [])],
    _ref: { category: 'external', apiId, apiCode: 'http', apiLabel: contextLabel },
  })
  for (const path of uniqueApiPaths(paths)) {
    let quote = '', escaped = false, depth = 0
    for (let index = 0; index < path.value.length; index++) {
      const char = path.value[index]
      if (quote) { if (escaped) escaped = false; else if (char === '\\') escaped = true; else if (char === quote) quote = ''; continue }
      if (char === '"' || char === "'") { quote = char; continue }
      if ((char === '.' && depth === 0 || char === '[' && depth === 0) && index > 0) {
        const parent = path.value.slice(0, index)
        if (!fields.has(parent)) add({ value: parent, label: ({ steps: '步骤结果', callbacks: '回调地址', input: '链路输入', 'input.__apiFields': '链路入参' })[parent] || parent,
          type: char === '[' && /\d/.test(path.value[index + 1]) ? 'LIST' : 'OBJECT' }, true)
      }
      if (char === '[') depth++
      if (char === ']') depth--
    }
    add(path)
  }
  const result = [...fields.values()]
  if (Object.isFrozen(paths)) {
    if (!protocolFieldsCache.has(paths)) protocolFieldsCache.set(paths, new Map())
    protocolFieldsCache.get(paths).set(cacheKey, result)
  }
  return result
}

export function isAvailableApiPath(value, paths) {
  return typeof value === 'string' && paths.some(item => item.value === value || value.startsWith(item.value + '.') || value.startsWith(item.value + '['))
}
