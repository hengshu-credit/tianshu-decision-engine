import { VAR_TYPE_FORM_OPTIONS } from '@/constants/varTypes'
import { isValidDecimalText } from './conditionRange'

export const LITERAL_TYPE_OPTIONS = [...VAR_TYPE_FORM_OPTIONS, { label: '日期时间 (DATETIME)', value: 'DATETIME' }]
export function literalTypeFamily(type) {
  const name = String(type || '').toUpperCase()
  if (['BYTE', 'SHORT', 'INT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL', 'BIGDECIMAL', 'PROBABILITY'].includes(name)) return 'NUMBER'
  if (['ARRAY', 'SET', 'COLLECTION'].includes(name)) return 'LIST'
  if (['CHAR', 'ENUM', 'TEXT'].includes(name)) return 'STRING'
  if (name === 'BOOL') return 'BOOLEAN'
  if (name === 'MAP') return 'OBJECT'
  return name
}
export function literalTypeCompatible(type, expected) {
  return !expected || String(expected).toUpperCase() === 'ANY' || literalTypeFamily(type) === literalTypeFamily(expected)
}
export function recommendLiteralType(value, expected = '') {
  if (expected && String(expected).toUpperCase() !== 'ANY') {
    const family = literalTypeFamily(expected)
    return family === 'OBJECT' && String(expected).toUpperCase() === 'MAP' ? 'MAP' : family
  }
  const text = String(value ?? ''), trimmed = text.trim()
  if (trimmed.startsWith('[')) return 'LIST'
  if (trimmed.startsWith('{')) return 'OBJECT'
  if (/^(true|false)$/i.test(trimmed)) return 'BOOLEAN'
  // 保留编码的前导零和文本空格；数值使用 NUMBER，避免缩窄为整数或浮点数。
  if (text === trimmed && !/^[+-]?0\d/.test(text) && isValidDecimalText(text)) return 'NUMBER'
  return 'STRING'
}
export function literalValidationError(operand, expected = '') {
  if (!literalTypeCompatible(operand.valueType, expected)) return `当前位置需要 ${expected} 类型`
  const type = literalTypeFamily(operand.valueType), value = operand.value
  if (type === 'NUMBER' && !isValidDecimalText(value)) return '请输入有效数值'
  if (type === 'BOOLEAN' && !/^(true|false)$/i.test(String(value).trim())) return '布尔值请填写 true 或 false'
  if (['LIST', 'OBJECT'].includes(type)) {
    try {
      const parsed = typeof value === 'string' ? JSON.parse(value) : value
      if (type === 'LIST' ? !Array.isArray(parsed) : !parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error()
    } catch { return type === 'LIST' ? '请输入合法的 JSON 数组' : '请输入合法的 JSON 对象' }
  }
  return ''
}

/** 只把纯常量数组转换为阈值草稿；数字保留文本，避免长整数经 Number 丢精度。 */
export function arrayLiteralDraft(operand) {
  if (operand?.kind === 'PATH' && operand.refId == null && !operand.protocolPath) {
    try {
      if (Array.isArray(JSON.parse(operand.value))) return { kind: 'LITERAL', valueType: 'LIST', value: operand.value }
    } catch { return null }
  }
  const encode = node => {
    if (node?.kind === 'ARRAY') {
      const items = node.items || []
      const values = items.map(encode)
      // 新建数组默认带一个空槽位；此时应直接进入“输入阈值”的 JSON
      // 编辑器，而不是把用户送回组合表达式页面。混合了字段/计算项的
      // 数组仍保留原表达式语义，避免转换时丢失引用。
      if (values.some(value => value == null)) return items.every(item => item == null) ? '[]' : null
      return '[' + values.join(', ') + ']'
    }
    if (node?.kind !== 'LITERAL') return null
    const type = literalTypeFamily(node.valueType)
    if (type === 'NULL') return 'null'
    if (literalValidationError(node)) return null
    if (['NUMBER', 'BOOLEAN', 'LIST', 'OBJECT'].includes(type)) {
      const text = typeof node.value === 'string' ? node.value : JSON.stringify(node.value)
      try { JSON.parse(text); return text } catch { return null }
    }
    return JSON.stringify(String(node.value ?? ''))
  }
  if (operand?.kind !== 'ARRAY') return null
  const value = encode(operand)
  return value == null ? null : { kind: 'LITERAL', valueType: 'LIST', value }
}
