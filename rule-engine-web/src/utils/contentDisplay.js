import { inferOperandType, operandKindMeta, operandDisplay } from './operand'
import { varTypeLabel } from '@/constants/varTypes'
import { fieldTypeIcon } from './fieldPicker'
import { pickerReferenceDisplay } from './pickerReferenceDisplay'
import { compactVersion } from './resourceVersion'

/** 两字符来源标识；完整含义由调用方提供 title 和 aria-label。 */
export function contentSourceIcon(value) {
  const { label } = operandKindMeta(value)
  if (value?.kind === 'PATH') return '路径'
  return ({ 数据对象: '对象', '外数 API': '接口', 外数源: '外数', HTTP上下文: '协议', 数据库: '库源', SFTP: '文件' })[label] || label
}

export function contentSummary(value, options = []) {
  if (['PATH', 'REFERENCE'].includes(value?.kind)) return pickerReferenceDisplay(value, options)
  if (value?.kind === 'FUNCTION') return [value.label === value.functionCode ? '' : value.label, value.functionCode,
    compactVersion(value) || (value.functionId != null ? '最新' : '')].filter(Boolean).join(' ')
  return operandDisplay(value)
}

/** 来源类型与数据类型分开显示，保留 LONG/INTEGER 等具体类型。 */
export function contentDataType(value, options = []) {
  const type = contentTypeCode(value, options)
  const label = varTypeLabel(type)
  return type ? `${label} · ${type}` : ''
}
export function contentDataTypeIcon(value, options = []) {
  const type = contentTypeCode(value, options)
  return type ? fieldTypeIcon(type) : ''
}
function contentTypeCode(value, options) {
  if (!value) return ''
  let type = value.valueType || value.varType
  if (value.kind === 'OPERATION') type = inferOperandType(value)
  if (value.kind === 'REFERENCE' && value.refId != null && value.refType && value.versionMode !== 'FIXED') {
    const item = options.find(option => String(option._varId ?? option.id) === String(value.refId) && (option._refType || option.refType) === value.refType
      && (!['EXTERNAL_API', 'API_CONTEXT'].includes(value.refType) || (option.relativePath || option._ref?.relativePath || '') === (value.relativePath || '')))
    type = item?.varType || type
  }
  type = String(type || inferOperandType(value) || '').toUpperCase()
  return type
}
