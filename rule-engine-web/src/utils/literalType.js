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
