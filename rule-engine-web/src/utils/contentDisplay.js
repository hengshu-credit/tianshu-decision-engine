import { inferOperandType, operandKindMeta, operandDisplay } from './operand'
import { declaredContentType, varTypeLabel } from '@/constants/varTypes'
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
export function contentTypeTitle(type) {
  if (!type) return ''
  const key = type.toUpperCase()
  const label = varTypeLabel(key)
  return label === key ? type : `${label} · ${key}`
}
export function contentDataType(value, options = []) {
  return contentTypeTitle(contentTypeCode(value, options))
}
export function contentDataTypeIcon(value, options = []) {
  const type = contentTypeCode(value, options)
  return type ? fieldTypeIcon(type) : ''
}
export function currentReferenceOption(refId, refType, relativePath, options) {
  if (refId == null || !refType) return undefined
  return options.find(item => item.versionMode !== 'FIXED'
    && String(item._varId ?? item.refId ?? item.function?.functionId ?? item.function?.id ?? item.id) === String(refId)
    && (item._refType || item.refType || (item._function && 'FUNCTION')) === refType
    && (!['EXTERNAL_API', 'API_CONTEXT'].includes(refType) || (item.relativePath || item._ref?.relativePath || '') === (relativePath || '')))
}

export function recordedValueType(value) {
  if (value == null) return ''
  if (Array.isArray(value)) return 'LIST'
  return { number: 'NUMBER', boolean: 'BOOLEAN', object: 'OBJECT', string: 'STRING' }[typeof value] || ''
}

const resourceTypes = { MODEL: 'MODEL', RULE: 'RULE', PROJECT: 'PROJECT', DATA_OBJECT_ROOT: 'OBJECT',
  EXTERNAL_API: 'API', EXTERNAL_DATASOURCE: 'DATASOURCE', DATABASE: 'DATABASE', SFTP: 'SFTP', LIST_LIBRARY: 'LIST' }

export function contentTypeCode(value, options = [], pathMatch) {
  if (!value) return ''
  let type = declaredContentType(value)
  if (value.kind === 'OPERATION') type = inferOperandType(value)
  if (value.kind === 'REFERENCE' || value.kind === 'FUNCTION') {
    const item = value.versionMode !== 'FIXED' && currentReferenceOption(value.refId ?? value.functionId,
      value.kind === 'FUNCTION' ? 'FUNCTION' : value.refType, value.relativePath, options)
    type = declaredContentType(item) || type
  }
  if (value.kind === 'PATH') type = (pathMatch && !pathMatch.suffix && pathMatch.valueType) || value.pathValueType || type
  if (!type && value.kind === 'LITERAL') type = value.value === null ? 'NULL' : recordedValueType(value.value)
  if (!type && value.kind === 'REFERENCE' && !value.relativePath && Object.hasOwn(resourceTypes, value.refType)) type = resourceTypes[value.refType]
  const code = String(type || (!value.valueType && inferOperandType(value)) || '').trim()
  return code === '?' ? '' : code
}
