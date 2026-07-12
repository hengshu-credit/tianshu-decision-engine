export const OPERAND_KINDS = Object.freeze({
  LITERAL: 'LITERAL',
  PATH: 'PATH',
  REFERENCE: 'REFERENCE',
  FUNCTION: 'FUNCTION'
})

export const READ_OPERAND_KINDS = Object.freeze([
  OPERAND_KINDS.PATH,
  OPERAND_KINDS.REFERENCE,
  OPERAND_KINDS.FUNCTION
])

export const VALUE_OPERAND_KINDS = Object.freeze([
  OPERAND_KINDS.LITERAL,
  OPERAND_KINDS.PATH,
  OPERAND_KINDS.REFERENCE,
  OPERAND_KINDS.FUNCTION
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
  return {
    kind: OPERAND_KINDS.REFERENCE,
    value: code,
    code,
    label: optionLabel(option) || code,
    valueType: option.varType || option.valueType || '',
    refId,
    refType,
    resolved: refId != null && !!refType
  }
}

export function createFunctionOperand(fn, args = []) {
  if (!fn) return null
  const functionCode = fn.functionCode || fn.funcCode || fn.functionName || fn.funcName || fn.name || ''
  const functionLabel = fn.functionLabel || fn.funcName || fn.functionName || fn.label || functionCode
  return {
    kind: OPERAND_KINDS.FUNCTION,
    functionId: fn.functionId != null ? fn.functionId : fn.id,
    functionCode,
    label: functionLabel,
    valueType: fn.returnType || fn.valueType || fn.varType || '',
    args: Array.isArray(args) ? args : []
  }
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
  return ''
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
    if (current.kind === OPERAND_KINDS.FUNCTION) {
      ;(current.args || []).forEach(visit)
    }
  }
  visit(operand)
  return result
}

export function operandDisplay(operand) {
  if (!operand) return ''
  if (operand.kind === OPERAND_KINDS.LITERAL) return String(operand.value == null ? '' : operand.value)
  if (operand.kind === OPERAND_KINDS.FUNCTION) {
    return (operand.label || operand.functionCode || '函数') + '(...)'
  }
  const code = operand.code || operand.value || ''
  const label = operand.label || ''
  return label && label !== code ? label + ' ' + code : code
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

function normalizeType(valueType) {
  const type = String(valueType || 'STRING').toUpperCase()
  if (['BYTE', 'SHORT', 'INT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL', 'BIGDECIMAL', 'PROBABILITY'].includes(type)) return 'NUMBER'
  if (type === 'BOOL') return 'BOOLEAN'
  if (['ARRAY', 'SET', 'COLLECTION'].includes(type)) return 'LIST'
  return type
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
