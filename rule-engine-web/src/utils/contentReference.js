import { createReferenceOperand, inferOperandType } from './operand'
import { contentSourceIcon, contentDataTypeIcon, contentDataType, contentSummary, currentReferenceOption } from './contentDisplay'
import { operandKindMeta } from './operand'
import { compactVersion } from './resourceVersion'
import { matchPathContent } from './pathContentMatch'
import { pickerTypeMatches } from './fieldPicker'
import { formatExpressionFormula } from './expressionDisplay'

/** 展示适配仅补元信息，绝不修改引用身份、历史版本或脚本。 */
export function contentReferenceValue(value = {}, options = []) {
  if (value == null || typeof value !== 'object') return { kind: 'LITERAL', value, valueType: typeof value === 'number' ? 'NUMBER' : typeof value === 'boolean' ? 'BOOLEAN' : 'STRING' }
  if (value.kind) {
    if ((value.kind === 'FUNCTION' || value.refType === 'FUNCTION') && value.returnType) return { ...value, valueType: value.returnType }
    return value
  }
  const rawType = value._refType || value.refType || value._ref?.refType
    || (value.modelCode ? 'MODEL' : value.objectCode ? 'DATA_OBJECT_ROOT' : value.apiCode ? 'EXTERNAL_API' : value.ruleCode ? 'RULE' : value.funcCode || value.functionCode ? 'FUNCTION' : value.listCode ? 'LIST_LIBRARY' : 'VARIABLE')
  const refType = ({ API: 'EXTERNAL_API', DB: 'DATABASE', LIST: 'LIST_LIBRARY', DATASOURCE: 'EXTERNAL_DATASOURCE' })[rawType] || rawType
  const id = value._varId ?? value.refId ?? value.varId ?? value.id ?? value.function?.id ?? value.function?.functionId
  const source = value.versionMode === 'FIXED' ? value : {
    ...currentReferenceOption(id, refType, value.relativePath ?? value._ref?.relativePath, options), ...value,
  }
  return { ...createReferenceOperand({ ...source, _varId: id, _refType: refType,
    varCode: source.varCode || source.refCode || source.fieldCode || source.fieldPath || source.modelCode || source.objectCode || source.apiCode || source.ruleCode || source.funcCode || source.functionCode || source.listCode || source.code || source.fieldName || '',
    varLabel: source.varLabelText || source.varLabel || source.refLabel?.label || source.fieldLabel || source.fieldName || source.modelName || source.objectLabel || source.objectName || source.apiName || source.ruleName || source.funcName || source.functionLabel || source.listName || source.name || source.label || '',
    varType: (refType === 'FUNCTION' && (source.returnType || source.function?.returnType)) || source.varType || source.valueType || source.fieldType || source.returnType || '',
  }), builtin: source.builtin, resolvedVersion: source.resolvedVersion,
    version: source.displayVersion ?? source.version ?? source._ref?.modelVersion ?? source._ref?.objectVersion ?? source.varObj?.version ?? source.resource?.version ?? source.function?.version,
  }
}

export function contentTypeFamily(type) {
  if (pickerTypeMatches(type, 'NUMBER')) return 'number'
  if (['LIST', 'ARRAY', 'MAP', 'OBJECT', 'MODEL'].includes(type)) return 'collection'
  if (['BOOLEAN', 'BOOL'].includes(type)) return 'boolean'
  return 'text'
}

function traceBindingKey(refType, refId, versionId, source) {
  if (!refType || refId == null || versionId == null) return null
  return `${refType}:${refId}@${source || 'SNAPSHOT'}:${versionId}`
}

export function traceResourceVersions(events) {
  const versions = new Map()
  if (!Array.isArray(events)) return versions
  for (const event of events) {
    if (event?.type !== 'RESOURCE_VERSION' || event.version == null) continue
    const key = traceBindingKey(event.refType, event.refId, event.versionId, event.versionSource)
    if (key == null) continue
    const version = String(event.version)
    versions.set(key, versions.has(key) && versions.get(key) !== version ? null : version)
  }
  return versions
}

function traceVersionPart(node, rawVersion, context) {
  const fixed = node.versionMode === 'FIXED'
  const refType = node.kind === 'FUNCTION' ? 'FUNCTION' : node.refType
  const key = traceBindingKey(refType, node.refId ?? node.functionId, node.versionId, node.versionSource)
  const resolved = node.resolvedVersion ?? (fixed ? context?.resourceVersions?.get(key) : null)
  if (resolved != null) return { text: `${fixed ? '固定 ' : ''}v${resolved}`, title: '执行时解析的实际版本' }
  if (fixed && rawVersion != null) return { text: '固定 ' + compactVersion(node), title: '本次执行配置保存的版本绑定' }
  if (node.versionMode !== 'LATEST' && rawVersion != null) return { text: `v${rawVersion}` }
  if (context) return { text: context.ruleVersionLabel, title: `${context.ruleVersionLabel}。日志未记录此引用的独立版本号` }
  return { text: fixed ? '版本未提供' : '最新', title: '日志未记录解析后的版本号' }
}

export function contentReferenceParts(value, catalog = [], sensitive = false, mode = 'configuration', traceContext) {
  const options = mode === 'trace' ? [] : catalog
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
    const option = node.versionMode !== 'FIXED' && currentReferenceOption(node.refId, node.refType, node.relativePath, options)
    const code = match?.code || option?.varCode || node.functionCode || node.code || node.value || ''
    const name = match?.label || option?.varLabelText || option?.varLabel || node.label || code
    parts.push({ key: 'name', text: name }, { key: 'code', text: code })
    const rawVersion = node.versionMode === 'FIXED' ? node.displayVersion ?? node.version
      : option?.displayVersion ?? option?.version ?? option?._ref?.modelVersion ?? option?._ref?.objectVersion ?? option?.varObj?.version ?? node.displayVersion ?? node.version
    const protocol = node.protocolPath || node.refType === 'API_CONTEXT'
    const unbound = node.refId == null && node.functionId == null
    if (!protocol && !(mode === 'editor' && (node.builtin || unbound && rawVersion == null && !node.versionMode))) {
      const fallback = node.versionMode === 'FIXED' ? '版本未提供' : '最新'
      const text = node.builtin ? '内置' : match?.version || compactVersion({ ...node, version: rawVersion }) || (rawVersion != null ? `v${rawVersion}` : fallback)
      const version = mode === 'trace' && (!node.builtin || traceContext) ? traceVersionPart(node, rawVersion, traceContext) : { text }
      parts.push({ key: 'version', ...version })
    }
    if (match?.suffix) parts.push({ key: 'path', text: match.suffix })
    else if (node.kind === 'REFERENCE' && node.relativePath && node.relativePath !== '$' && !String(code).endsWith(node.relativePath)) parts.push({ key: 'path', text: node.relativePath })
    if (node.refType === 'CONSTANT' && Object.hasOwn(node, 'constantValue')) parts.push({ key: 'value', text: sensitive ? '••••••••' : String(node.constantValue) })
  } else parts.push({ key: 'value', text: sensitive ? '••••••••' : formatExpressionFormula(node, { humanReadable: true }) || contentSummary(node, options) })
  return parts.filter(part => part.text !== undefined && part.text !== '').map(part => ({ ...part, text: String(part.text), title: part.title || String(part.text) }))
}

export const contentPartClass = part => `content-ref__segment content-ref__${part.key}${part.family ? ' content-ref__type--' + part.family : ''}`

function traceValueType(value) {
  if (value == null) return ''
  if (Array.isArray(value)) return 'LIST'
  return { number: 'NUMBER', boolean: 'BOOLEAN', object: 'OBJECT', string: 'STRING' }[typeof value] || ''
}

/** 追踪仅使用日志已有身份和版本证据。 */
export function traceContentValue(node, labels = {}, functionNames = {}) {
  if (node?.kind) return node
  const isCall = ['FUNCTION', 'METHOD'].includes(node?.type)
  const label = (isCall ? functionNames : labels)[node?.token]
  const metadata = label && typeof label === 'object' ? label : {}
  const type = (isCall && node.returnType) || node?.valueType || node?.varType || metadata.varType || traceValueType(node?.value)
  if (['VALUE', 'PRIMARY'].includes(node?.type)) return { kind: 'LITERAL', value: node.value === undefined ? node.token : node.value, valueType: type }
  return { ...metadata, ...node, kind: 'REFERENCE', refType: node?.refType || metadata.refType || (isCall ? 'FUNCTION' : 'VARIABLE'),
    refId: node?.refId ?? metadata.refId ?? metadata.id, code: node?.token || node?.code, label: node?.label || metadata.varLabel || (typeof label === 'string' ? label : '') || node?.token, valueType: type }
}

export function renderContentReference(h, value, options = []) {
  return h('span', { class: 'content-ref' }, contentReferenceParts(value, options).map(part => h('span', { class: contentPartClass(part), title: part.title, 'data-content-part': part.key }, part.text)))
}
