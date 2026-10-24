import { createReferenceOperand, inferOperandType } from './operand'
import { contentSourceIcon, contentDataTypeIcon, contentDataType, contentSummary } from './contentDisplay'
import { operandKindMeta } from './operand'
import { compactVersion } from './resourceVersion'
import { matchPathContent } from './pathContentMatch'
import { pickerTypeMatches } from './fieldPicker'
import { formatExpressionFormula } from './expressionDisplay'

/** 展示适配仅补元信息，绝不修改引用身份、历史版本或脚本。 */
export function contentReferenceValue(value = {}, options = []) {
  if (value == null || typeof value !== 'object') return { kind: 'LITERAL', value, valueType: typeof value === 'number' ? 'NUMBER' : typeof value === 'boolean' ? 'BOOLEAN' : 'STRING' }
  if (value.kind) return value
  const rawType = value._refType || value.refType || value._ref?.refType
    || (value.modelCode ? 'MODEL' : value.objectCode ? 'DATA_OBJECT_ROOT' : value.apiCode ? 'EXTERNAL_API' : value.ruleCode ? 'RULE' : value.funcCode || value.functionCode ? 'FUNCTION' : value.listCode ? 'LIST_LIBRARY' : 'VARIABLE')
  const refType = ({ API: 'EXTERNAL_API', DB: 'DATABASE', LIST: 'LIST_LIBRARY', DATASOURCE: 'EXTERNAL_DATASOURCE' })[rawType] || rawType
  const id = value._varId ?? value.refId ?? value.varId ?? value.id ?? value.function?.id ?? value.function?.functionId
  const selected = id != null && options.find(item => String(item._varId ?? item.refId ?? item.id) === String(id) && (item._refType || item.refType) === refType)
  const source = value.versionMode === 'FIXED' ? value : { ...selected, ...value }
  return { ...createReferenceOperand({ ...source, _varId: id, _refType: refType,
    varCode: source.varCode || source.refCode || source.fieldCode || source.fieldPath || source.modelCode || source.objectCode || source.apiCode || source.ruleCode || source.funcCode || source.functionCode || source.listCode || source.code || source.fieldName || '',
    varLabel: source.varLabelText || source.varLabel || source.refLabel?.label || source.fieldLabel || source.fieldName || source.modelName || source.objectLabel || source.objectName || source.apiName || source.ruleName || source.funcName || source.functionLabel || source.listName || source.name || source.label || '',
    varType: source.varType || source.valueType || source.fieldType || source.returnType || '',
  }), builtin: source.builtin, version: source.displayVersion ?? source.version ?? source._ref?.modelVersion ?? source._ref?.objectVersion ?? source.varObj?.version ?? source.resource?.version ?? source.function?.version,
  }
}

export function contentTypeFamily(type) {
  if (pickerTypeMatches(type, 'NUMBER')) return 'number'
  if (['LIST', 'ARRAY', 'MAP', 'OBJECT', 'MODEL'].includes(type)) return 'collection'
  if (['BOOLEAN', 'BOOL'].includes(type)) return 'boolean'
  return 'text'
}

export function contentReferenceParts(value, options = [], sensitive = false, mode = 'configuration') {
  const node = contentReferenceValue(value, options)
  const meta = operandKindMeta(node)
  const parts = [
    { key: 'source', text: contentSourceIcon(node) || '引用', title: meta.label || '内容引用' },
    { key: 'type', text: contentDataTypeIcon(node, options) || '?', title: contentDataType(node, options) || '未提供数据类型', family: contentTypeFamily(node.valueType || inferOperandType(node)) },
  ]
  if (node.kind === 'LITERAL') {
    const literal = node.value == null ? 'null' : typeof node.value === 'object' ? JSON.stringify(node.value) : String(node.value)
    parts.push({ key: 'value', text: sensitive ? '••••••••' : literal === '' ? '""' : literal })
  } else if (['REFERENCE', 'PATH', 'FUNCTION'].includes(node.kind)) {
    const match = matchPathContent(node, options)
    const option = node.refId != null && node.refType && node.versionMode !== 'FIXED' && options.find(item => String(item._varId ?? item.id) === String(node.refId) && (item._refType || item.refType) === node.refType
      && (!['EXTERNAL_API', 'API_CONTEXT'].includes(node.refType) || (item.relativePath || item._ref?.relativePath || '') === (node.relativePath || '')))
    const code = match?.code || option?.varCode || node.functionCode || node.code || node.value || ''
    const name = match?.label || option?.varLabelText || option?.varLabel || node.label || code
    parts.push({ key: 'name', text: name }, { key: 'code', text: code })
    const rawVersion = node.versionMode === 'FIXED' ? node.displayVersion ?? node.version
      : option?.displayVersion ?? option?.version ?? option?._ref?.modelVersion ?? option?._ref?.objectVersion ?? option?.varObj?.version ?? node.displayVersion ?? node.version
    const protocol = node.protocolPath || node.refType === 'API_CONTEXT'
    if (!protocol) {
      let text = match?.version || compactVersion({ ...node, version: rawVersion }) || (rawVersion != null ? `v${rawVersion}` : '版本未提供')
      if (node.builtin) text = '内置'
      let title = text
      if (mode === 'trace' && node.versionMode === 'LATEST') {
        text = node.resolvedVersion != null ? `v${node.resolvedVersion}` : '跟随最新'
        title = node.resolvedVersion != null ? '执行时解析的实际版本' : '执行时跟随最新；日志未记录解析后的版本号，不能用配置时的版本号替代'
      } else if (mode === 'trace' && node.versionMode === 'FIXED') { text = '固定 ' + text; title = '本次执行配置保存的版本绑定' }
      parts.push({ key: 'version', text, title })
    }
    if (match?.suffix) parts.push({ key: 'path', text: match.suffix })
    else if (node.kind === 'REFERENCE' && node.relativePath && node.relativePath !== '$' && !String(code).endsWith(node.relativePath)) parts.push({ key: 'path', text: node.relativePath })
    if (node.refType === 'CONSTANT' && Object.hasOwn(node, 'constantValue')) parts.push({ key: 'value', text: sensitive ? '••••••••' : String(node.constantValue) })
  } else parts.push({ key: 'value', text: sensitive ? '••••••••' : formatExpressionFormula(node, { humanReadable: true }) || contentSummary(node, options) })
  return parts.filter(part => part.text !== undefined && part.text !== '').map(part => ({ ...part, text: String(part.text), title: part.title || String(part.text) }))
}

export const contentPartClass = part => `content-ref__segment content-ref__${part.key}${part.family ? ' content-ref__type--' + part.family : ''}`

/** 追踪仅使用日志已有身份；旧记录缺少类型或版本时明确显示未知，不能借同名内容补造关联。 */
export function traceContentValue(node, labels = {}) {
  if (node?.kind) return node
  const label = labels[node?.token]
  const metadata = label && typeof label === 'object' ? label : {}
  const type = node?.valueType || node?.varType || metadata.varType || (node?.value == null ? '' : typeof node.value === 'number' ? 'NUMBER' : typeof node.value === 'boolean' ? 'BOOLEAN' : typeof node.value === 'object' ? Array.isArray(node.value) ? 'LIST' : 'OBJECT' : 'STRING')
  if (['VALUE', 'PRIMARY'].includes(node?.type)) return { kind: 'LITERAL', value: node.value === undefined ? node.token : node.value, valueType: type }
  return { ...metadata, ...node, kind: 'REFERENCE', refType: node?.refType || metadata.refType || (['FUNCTION', 'METHOD'].includes(node?.type) ? 'FUNCTION' : 'VARIABLE'),
    refId: node?.refId ?? metadata.refId ?? metadata.id, code: node?.token || node?.code, label: node?.label || metadata.varLabel || (typeof label === 'string' ? label : '') || node?.token, valueType: type }
}

export function renderContentReference(h, value, options = []) {
  return h('span', { class: 'content-ref' }, contentReferenceParts(value, options).map(part => h('span', { class: contentPartClass(part), title: part.title, 'data-content-part': part.key }, part.text)))
}
