import { formatConstantValue } from '@/utils/constantValue'

export const OPERAND_KINDS = Object.freeze({
  LITERAL: 'LITERAL',
  PATH: 'PATH',
  REFERENCE: 'REFERENCE',
  FUNCTION: 'FUNCTION',
  OPERATION: 'OPERATION',
  ACCESS: 'ACCESS',
  CAST: 'CAST',
  ARRAY: 'ARRAY',
  LIST_QUERY: 'LIST_QUERY'
})

export const READ_OPERAND_KINDS = Object.freeze([
  OPERAND_KINDS.PATH,
  OPERAND_KINDS.REFERENCE,
  OPERAND_KINDS.FUNCTION,
  OPERAND_KINDS.OPERATION,
  OPERAND_KINDS.ACCESS,
  OPERAND_KINDS.CAST,
  OPERAND_KINDS.ARRAY
])

export const VALUE_OPERAND_KINDS = Object.freeze([
  OPERAND_KINDS.LITERAL,
  OPERAND_KINDS.PATH,
  OPERAND_KINDS.REFERENCE,
  OPERAND_KINDS.FUNCTION,
  OPERAND_KINDS.OPERATION,
  OPERAND_KINDS.ACCESS,
  OPERAND_KINDS.CAST,
  OPERAND_KINDS.ARRAY
])

export const WRITE_OPERAND_KINDS = Object.freeze([
  OPERAND_KINDS.PATH,
  OPERAND_KINDS.REFERENCE
])

export function createLiteralOperand(value, valueType = 'STRING') {
  return {
    kind: OPERAND_KINDS.LITERAL,
    value: value == null ? '' : String(value),
    valueType: valueType || 'STRING'
  }
}

export function createPathOperand(path) {
  const value = String(path == null ? '' : path).trim()
  return {
    kind: OPERAND_KINDS.PATH,
    value,
    code: value,
    label: value,
    valueType: '',
    refId: null,
    refType: '',
    resolved: false
  }
}

export function createReferenceOperand(option) {
  if (!option) return null
  const code = optionCode(option)
  const refId = optionRefId(option)
  const refType = optionRefType(option)
  const operand = {
    kind: OPERAND_KINDS.REFERENCE,
    value: code,
    code,
    label: optionLabel(option) || code,
    valueType: option.varType || option.valueType || '',
    refId,
    refType,
    resolved: refId != null && !!refType
  }
  if (refType === 'CONSTANT') {
    operand.constantValue = option.constantValue !== undefined
      ? option.constantValue
      : (option.defaultValue !== undefined ? option.defaultValue : option.varObj && option.varObj.defaultValue)
  }
  return operand
}

export function createFunctionOperand(fn, args = []) {
  if (!fn) return null
  const functionCode = fn.functionCode || fn.funcCode || fn.functionName || fn.funcName || fn.name || ''
  const functionLabel = fn.functionLabel || fn.funcName || fn.functionName || fn.label || functionCode
  const parameters = functionParameterDefinitions(fn)
  const hasParameterMetadata = Array.isArray(fn.params) || Array.isArray(fn.parameters) || fn.paramsJson != null
  return {
    kind: OPERAND_KINDS.FUNCTION,
    functionId: fn.functionId != null ? fn.functionId : fn.id,
    functionCode,
    label: functionLabel,
    valueType: fn.returnType || fn.valueType || fn.varType || '',
    parameterCount: hasParameterMetadata ? parameters.length : null,
    parameterTypes: parameters.map(param => normalizeType(param.type || param.valueType || 'OBJECT')),
    args: Array.isArray(args) ? args : []
  }
}

export function createOperationOperand(operator, operands = [], valueType = '') {
  return {
    kind: OPERAND_KINDS.OPERATION,
    operator: operator || '',
    operands: Array.isArray(operands) ? operands : [],
    valueType: valueType || ''
  }
}

export function createAccessOperand(target, accessType, accessor, valueType = '') {
  return {
    kind: OPERAND_KINDS.ACCESS,
    target: target || null,
    accessType: String(accessType || 'KEY').toUpperCase(),
    accessor: accessor || null,
    valueType: valueType || ''
  }
}

export function createCastOperand(targetType, operand) {
  const normalized = normalizeType(targetType)
  return {
    kind: OPERAND_KINDS.CAST,
    targetType: normalized,
    operand: operand || null,
    valueType: normalized
  }
}

export function createArrayOperand(items = []) {
  return {
    kind: OPERAND_KINDS.ARRAY,
    items: Array.isArray(items) ? items : [],
    valueType: 'LIST'
  }
}

export function createListQueryOperand(config = {}) {
  return {
    kind: OPERAND_KINDS.LIST_QUERY,
    listIds: Array.isArray(config.listIds) ? config.listIds.slice() : [],
    itemTypes: Array.isArray(config.itemTypes) ? config.itemTypes.slice() : [],
    combinationMode: config.combinationMode || '',
    matchMode: config.matchMode || '',
    valueType: 'BOOLEAN'
  }
}

export function cloneOperand(operand) {
  if (operand == null) return operand
  return JSON.parse(JSON.stringify(operand))
}

export function operandChildren(operand) {
  if (!operand || !operand.kind) return []
  if (operand.kind === OPERAND_KINDS.FUNCTION) return operand.args || []
  if (operand.kind === OPERAND_KINDS.OPERATION) return operand.operands || []
  if (operand.kind === OPERAND_KINDS.ACCESS) return [operand.target, operand.accessor]
  if (operand.kind === OPERAND_KINDS.CAST) return [operand.operand]
  if (operand.kind === OPERAND_KINDS.ARRAY) return operand.items || []
  return []
}

export function inferOperandType(operand) {
  if (!operand || !operand.kind) return ''
  if (operand.kind === OPERAND_KINDS.CAST) return normalizeType(operand.targetType)
  if (operand.kind === OPERAND_KINDS.ARRAY) return 'LIST'
  if (operand.kind === OPERAND_KINDS.LIST_QUERY) return 'BOOLEAN'
  if (operand.kind === OPERAND_KINDS.OPERATION && ['&&', '||', '!', '==', '!=', '>', '>=', '<', '<='].includes(operand.operator)) return 'BOOLEAN'
  if (operand.valueType) return normalizeType(operand.valueType)
  if (operand.kind === OPERAND_KINDS.OPERATION) {
    const first = (operand.operands || []).find(Boolean)
    return inferOperandType(first)
  }
  return ''
}

export function validateOperand(operand, options = {}) {
  const errors = []
  const allowedKinds = Array.isArray(options.allowedKinds) ? options.allowedKinds : null
  const visit = (current, path) => {
    if (!current || !current.kind) {
      errors.push({ path, message: '表达式参数不能为空' })
      return
    }
    if (allowedKinds && !allowedKinds.includes(current.kind)) {
      errors.push({ path, message: '当前配置位置不支持' + operandKindName(current.kind) })
      return
    }
    if (current.kind === OPERAND_KINDS.REFERENCE && (current.refId == null || !current.refType)) {
      errors.push({ path, message: '受管字段引用缺少 ID 或引用类型' })
    }
    if (current.kind === OPERAND_KINDS.FUNCTION && !current.functionCode) {
      errors.push({ path, message: '方法编码不能为空' })
    }
    if (current.kind === OPERAND_KINDS.FUNCTION && Number.isInteger(current.parameterCount) && (current.args || []).length !== current.parameterCount) {
      errors.push({ path, message: '方法 ' + current.functionCode + ' 需要 ' + current.parameterCount + ' 个参数' })
    }
    if (current.kind === OPERAND_KINDS.FUNCTION && Array.isArray(current.parameterTypes)) {
      (current.args || []).forEach((arg, index) => {
        const declaredType = current.parameterTypes[index]
        if (!declaredType) return
        const expected = normalizeType(declaredType)
        const actual = inferOperandType(arg)
        if (expected && expected !== 'OBJECT' && expected !== 'ANY' && actual && !typesCompatible(expected, actual)) {
          errors.push({
            path: path + '.args[' + index + ']',
            message: '方法 ' + current.functionCode + ' 的第 ' + (index + 1) + ' 个参数需要 ' + expected + '，当前为 ' + actual
          })
        }
      })
    }
    if (current.kind === OPERAND_KINDS.OPERATION && !current.operator) {
      errors.push({ path, message: '运算符不能为空' })
    }
    if (current.kind === OPERAND_KINDS.OPERATION && (current.operands || []).length !== 2) {
      errors.push({ path, message: '运算符 ' + current.operator + ' 需要 2 个运算项' })
    }
    if (current.kind === OPERAND_KINDS.ACCESS && !current.accessType) {
      errors.push({ path, message: '访问方式不能为空' })
    }
    if (current.kind === OPERAND_KINDS.CAST && !current.targetType) {
      errors.push({ path, message: '转换目标类型不能为空' })
    }
    if (current.kind === OPERAND_KINDS.LIST_QUERY) {
      if (!(current.listIds || []).length) errors.push({ path, message: '名单查询至少选择一个名单' })
      if (!current.combinationMode) errors.push({ path, message: '名单组合模式不能为空' })
      if (!current.matchMode) errors.push({ path, message: '名单匹配模式不能为空' })
    }
    operandChildEntries(current).forEach(entry => visit(entry.value, path + entry.path))
  }
  visit(operand, 'root')
  return errors
}

export function resolvePathOperand(operand, options = []) {
  const source = operand && operand.kind === OPERAND_KINDS.PATH
    ? { ...operand }
    : createPathOperand(operand && operand.value)
  const value = source.value
  const candidates = (options || []).filter(option => optionPaths(option).includes(value))

  if (candidates.length !== 1) {
    return {
      operand: {
        ...source,
        code: value,
        label: value,
        valueType: '',
        refId: null,
        refType: '',
        resolved: false
      },
      candidates
    }
  }

  const match = createReferenceOperand(candidates[0])
  return {
    operand: {
      ...source,
      value,
      code: match.code || value,
      label: match.label || value,
      valueType: match.valueType,
      refId: match.refId,
      refType: match.refType,
      resolved: match.resolved
    },
    candidates: []
  }
}

export function compileOperand(operand) {
  if (!operand || !operand.kind) return ''
  if (operand.kind === OPERAND_KINDS.LITERAL) {
    return compileLiteral(operand.value, operand.valueType)
  }
  if (operand.kind === OPERAND_KINDS.PATH || operand.kind === OPERAND_KINDS.REFERENCE) {
    return operand.code || operand.value || ''
  }
  if (operand.kind === OPERAND_KINDS.FUNCTION) {
    const args = (operand.args || []).map(compileOperand).join(', ')
    return (operand.functionCode || '') + '(' + args + ')'
  }
  if (operand.kind === OPERAND_KINDS.OPERATION) {
    const values = (operand.operands || []).map(compileOperand)
    if (values.length === 1) return '(' + (operand.operator || '') + values[0] + ')'
    return '(' + values.join(' ' + (operand.operator || '') + ' ') + ')'
  }
  if (operand.kind === OPERAND_KINDS.ACCESS) {
    const fn = String(operand.accessType || '').toUpperCase() === 'INDEX' ? 'arrGet' : 'objGet'
    return fn + '(' + compileOperand(operand.target) + ', ' + compileOperand(operand.accessor) + ')'
  }
  if (operand.kind === OPERAND_KINDS.CAST) {
    const fn = castFunction(operand.targetType)
    return fn + '(' + compileOperand(operand.operand) + ')'
  }
  if (operand.kind === OPERAND_KINDS.ARRAY) {
    return '[' + (operand.items || []).map(compileOperand).join(', ') + ']'
  }
  if (operand.kind === OPERAND_KINDS.LIST_QUERY) {
    return 'listQuery(' + compileListLiteral(operand.listIds, 'NUMBER') + ', '
      + compileListLiteral(operand.itemTypes, 'STRING') + ', '
      + compileLiteral(operand.combinationMode, 'STRING') + ', '
      + compileLiteral(operand.matchMode, 'STRING') + ')'
  }
  return ''
}

export function compileListQueryOperand(operand, queryExpressions = []) {
  const queries = (queryExpressions || []).filter(Boolean)
  return 'listMatch([' + queries.join(', ') + '], '
    + compileListLiteral(operand && operand.listIds, 'NUMBER') + ', '
    + compileLiteral(operand && operand.combinationMode, 'STRING') + ', '
    + compileLiteral(operand && operand.matchMode, 'STRING') + ', '
    + compileListLiteral(operand && operand.itemTypes, 'STRING') + ')'
}

export function collectOperandReferences(operand, out = []) {
  const result = out
  const seen = new Set(result.map(referenceKey))
  const visit = current => {
    if (!current || !current.kind) return
    if (current.kind === OPERAND_KINDS.PATH || current.kind === OPERAND_KINDS.REFERENCE) {
      const dependency = {
        kind: current.kind,
        path: current.value || current.code || '',
        code: current.code || current.value || '',
        label: current.label || current.code || current.value || '',
        valueType: current.valueType || '',
        refId: current.refId == null ? null : current.refId,
        refType: current.refType || '',
        resolved: current.refId != null && !!current.refType
      }
      const key = referenceKey(dependency)
      if (!seen.has(key)) {
        seen.add(key)
        result.push(dependency)
      }
      return
    }
    operandChildren(current).forEach(visit)
  }
  visit(operand)
  return result
}

export function syncOperandReference(operand, options = []) {
  if (!operand || !operand.kind) return { operand, changed: false }
  const nested = syncOperandChildren(operand, options)
  if (nested) return nested
  if (operand.kind !== OPERAND_KINDS.PATH && operand.kind !== OPERAND_KINDS.REFERENCE) {
    return { operand, changed: false }
  }

  let match = null
  if (operand.refId != null && operand.refType) {
    match = (options || []).find(option => String(optionRefId(option)) === String(operand.refId) && optionRefType(option) === operand.refType)
  }
  if (!match && operand.kind === OPERAND_KINDS.PATH) {
    const resolved = resolvePathOperand(operand, options)
    return { operand: resolved.operand, changed: JSON.stringify(resolved.operand) !== JSON.stringify(operand) }
  }
  if (!match) return { operand, changed: false }

  const current = createReferenceOperand(match)
  const next = operand.kind === OPERAND_KINDS.PATH
    ? { ...operand, code: current.code, label: current.label, valueType: current.valueType, refId: current.refId, refType: current.refType, resolved: current.resolved }
    : current
  return { operand: next, changed: JSON.stringify(next) !== JSON.stringify(operand) }
}

export function operandDisplay(operand) {
  if (!operand) return ''
  if (operand.kind === OPERAND_KINDS.LITERAL) return String(operand.value == null ? '' : operand.value)
  if (operand.kind === OPERAND_KINDS.FUNCTION) {
    return compileOperand(operand)
  }
  if ([OPERAND_KINDS.OPERATION, OPERAND_KINDS.ACCESS, OPERAND_KINDS.CAST, OPERAND_KINDS.ARRAY, OPERAND_KINDS.LIST_QUERY].includes(operand.kind)) {
    return compileOperand(operand)
  }
  const code = operand.code || operand.value || ''
  const label = operand.label || ''
  if (operand.refType === 'CONSTANT' && Object.prototype.hasOwnProperty.call(operand, 'constantValue')) {
    const name = [label, code].filter(Boolean).join(' ')
    return name + ' = ' + formatConstantValue(operand.constantValue, operand.valueType)
  }
  return label && label !== code ? label + ' ' + code : code
}

export function operandKindMeta(operand) {
  if (!operand || !operand.kind) return { label: '', tone: 'empty' }
  if (operand.kind === OPERAND_KINDS.LITERAL) return { label: '阈值', tone: 'literal' }
  if (operand.kind === OPERAND_KINDS.FUNCTION) return { label: '方法', tone: 'function' }
  if (operand.kind === OPERAND_KINDS.OPERATION) return { label: '运算', tone: 'operation' }
  if (operand.kind === OPERAND_KINDS.ACCESS) return { label: '取值', tone: 'access' }
  if (operand.kind === OPERAND_KINDS.CAST) return { label: '转换', tone: 'cast' }
  if (operand.kind === OPERAND_KINDS.ARRAY) return { label: '数组', tone: 'array' }
  if (operand.kind === OPERAND_KINDS.LIST_QUERY) return { label: '名单', tone: 'list-query' }
  const source = referenceTypeMeta(operand.refType)
  if (operand.kind === OPERAND_KINDS.PATH) {
    return source.label
      ? { label: '路径→' + source.label, tone: 'path-resolved' }
      : { label: '路径', tone: 'path' }
  }
  return source.label ? source : { label: '字段', tone: 'reference' }
}

export function operandFromReferenceFields(source, codeKey = 'varCode', idKey = '_varId', refTypeKey = '_refType') {
  if (!source) return null
  const code = source[codeKey]
  if (!code) return null
  return {
    kind: OPERAND_KINDS.REFERENCE,
    value: code,
    code,
    label: source.varLabel || source.label || code,
    valueType: source.varType || source.valueType || '',
    refId: source[idKey] == null ? null : source[idKey],
    refType: source[refTypeKey] || '',
    resolved: source[idKey] != null && !!source[refTypeKey]
  }
}

function optionPaths(option) {
  return [
    option && option.varCode,
    option && option.refCode,
    option && option.scriptName,
    option && option.varCodeText,
    option && option.code,
    option && option._ref && option._ref.fullPath
  ].filter((value, index, list) => value && list.indexOf(value) === index)
}

function optionCode(option) {
  return option.varCode || option.refCode || option.scriptName || option.varCodeText || option.code || ''
}

function optionLabel(option) {
  const refLabel = option.refLabel
  if (refLabel && typeof refLabel === 'object') return refLabel.label || refLabel.code || ''
  return option.varLabelText || option.labelText || option.varLabel || option.displayName || option.label || ''
}

function optionRefId(option) {
  if (option._varId != null) return option._varId
  if (option.refId != null) return option.refId
  if (option.id != null) return option.id
  if (option.varObj && option.varObj.id != null) return option.varObj.id
  return null
}

function optionRefType(option) {
  return option._refType || option.refType || (option.varObj && option.varObj.refType) || (option._ref && option._ref.refType) || ''
}

function compileLiteral(value, valueType) {
  const text = value == null ? '' : String(value)
  const type = normalizeType(valueType)
  if (type === 'NUMBER') return text.trim()
  if (type === 'BOOLEAN') return text === 'true' || value === true ? 'true' : 'false'
  if (type === 'LIST' || type === 'MAP' || type === 'OBJECT') return text.trim()
  return quoteString(text)
}

function compileListLiteral(values, valueType) {
  return '[' + (values || []).map(value => compileLiteral(value, valueType)).join(', ') + ']'
}

function castFunction(targetType) {
  const type = normalizeType(targetType)
  if (type === 'NUMBER') return 'toNumberValue'
  if (type === 'BOOLEAN') return 'toBooleanValue'
  if (type === 'LIST') return 'toListValue'
  if (type === 'MAP' || type === 'OBJECT') return 'toMapValue'
  return 'toStringValue'
}

function normalizeType(valueType) {
  const type = String(valueType || 'STRING').toUpperCase()
  if (['BYTE', 'SHORT', 'INT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL', 'BIGDECIMAL', 'PROBABILITY'].includes(type)) return 'NUMBER'
  if (type === 'BOOL') return 'BOOLEAN'
  if (['ARRAY', 'SET', 'COLLECTION'].includes(type)) return 'LIST'
  return type
}

function functionParameterDefinitions(fn) {
  if (!fn) return []
  if (Array.isArray(fn.params)) return fn.params
  if (Array.isArray(fn.parameters)) return fn.parameters
  if (!fn.paramsJson) return []
  try {
    const parsed = typeof fn.paramsJson === 'string' ? JSON.parse(fn.paramsJson) : fn.paramsJson
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
}

function typesCompatible(expected, actual) {
  const left = normalizeType(expected)
  const right = normalizeType(actual)
  if (left === right) return true
  if ((left === 'MAP' && right === 'OBJECT') || (left === 'OBJECT' && right === 'MAP')) return true
  if ((left === 'STRING' && right === 'ENUM') || (left === 'ENUM' && right === 'STRING')) return true
  return false
}

function operandChildEntries(operand) {
  if (!operand || !operand.kind) return []
  if (operand.kind === OPERAND_KINDS.FUNCTION) return (operand.args || []).map((value, index) => ({ path: '.args[' + index + ']', value }))
  if (operand.kind === OPERAND_KINDS.OPERATION) return (operand.operands || []).map((value, index) => ({ path: '.operands[' + index + ']', value }))
  if (operand.kind === OPERAND_KINDS.ACCESS) return [{ path: '.target', value: operand.target }, { path: '.accessor', value: operand.accessor }]
  if (operand.kind === OPERAND_KINDS.CAST) return [{ path: '.operand', value: operand.operand }]
  if (operand.kind === OPERAND_KINDS.ARRAY) return (operand.items || []).map((value, index) => ({ path: '.items[' + index + ']', value }))
  return []
}

function syncOperandChildren(operand, options) {
  let key = null
  if (operand.kind === OPERAND_KINDS.FUNCTION) key = 'args'
  if (operand.kind === OPERAND_KINDS.OPERATION) key = 'operands'
  if (operand.kind === OPERAND_KINDS.ARRAY) key = 'items'
  if (key) {
    let changed = false
    const children = (operand[key] || []).map(child => {
      const result = syncOperandReference(child, options)
      if (result.changed) changed = true
      return result.operand
    })
    return { operand: changed ? { ...operand, [key]: children } : operand, changed }
  }
  if (operand.kind === OPERAND_KINDS.ACCESS) {
    const target = syncOperandReference(operand.target, options)
    const accessor = syncOperandReference(operand.accessor, options)
    const changed = target.changed || accessor.changed
    return { operand: changed ? { ...operand, target: target.operand, accessor: accessor.operand } : operand, changed }
  }
  if (operand.kind === OPERAND_KINDS.CAST) {
    const child = syncOperandReference(operand.operand, options)
    return { operand: child.changed ? { ...operand, operand: child.operand } : operand, changed: child.changed }
  }
  return null
}

function operandKindName(kind) {
  const names = {
    LITERAL: '阈值',
    PATH: '路径',
    REFERENCE: '字段',
    FUNCTION: '方法',
    OPERATION: '运算',
    ACCESS: '取值',
    CAST: '类型转换',
    ARRAY: '数组',
    LIST_QUERY: '名单查询'
  }
  return names[kind] || '表达式节点'
}

function quoteString(value) {
  return '"' + String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"'
}

function referenceKey(reference) {
  if (reference && reference.refId != null && reference.refType) {
    return reference.refType + ':' + reference.refId
  }
  return 'PATH:' + ((reference && (reference.path || reference.value || reference.code)) || '')
}

function referenceTypeMeta(refType) {
  const type = String(refType || '').toUpperCase()
  if (type === 'CONSTANT') return { label: '常量', tone: 'constant' }
  if (type === 'DATA_OBJECT' || type === 'DATA_FIELD') return { label: '数据对象', tone: 'object' }
  if (type === 'MODEL' || type === 'MODEL_OUTPUT') return { label: '模型', tone: 'model' }
  if (type === 'VARIABLE') return { label: '变量', tone: 'variable' }
  return { label: '', tone: 'reference' }
}
