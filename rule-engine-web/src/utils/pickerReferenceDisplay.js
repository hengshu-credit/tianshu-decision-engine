import { operandDisplay, resolvePathOperand } from './operand'
import { compactVersion } from './resourceVersion'
import { matchPathContent } from './pathContentMatch'

/** 仅补展示信息，不改写持久化的引用 ID、协议路径或历史版本。 */
export function pickerReferenceDisplay(value, options = []) {
  if (!value || !['PATH', 'REFERENCE'].includes(value.kind)) return operandDisplay(value)
  const matched = matchPathContent(value, options)
  if (matched) {
    const tail = matched.suffix ? matched.suffix.startsWith('[') ? matched.suffix : `[${matched.suffix}]` : ''
    const text = [matched.label === matched.code ? '' : matched.label, matched.code, matched.version, tail].filter(Boolean).join(' ')
    return value.refType === 'CONSTANT' && Object.hasOwn(value, 'constantValue') ? `${text} = ${value.constantValue}` : text
  }
  const fixed = value.versionMode === 'FIXED'
  const fullCode = value.code || value.value || ''
  const sourcePath = value.kind === 'PATH' ? value.anchorRelativePath ?? value.relativePath : value.relativePath
  let option = !fixed && value.refId != null && options.find(item =>
    String(item._varId ?? item.id) === String(value.refId) && (item._refType || item.refType) === value.refType
    && (value.refType !== 'EXTERNAL_API' || (item.relativePath || item._ref?.relativePath || '') === (sourcePath || '')))
  let suffix = value.kind === 'PATH' && value.anchorPath && fullCode.startsWith(value.anchorPath)
    ? fullCode.slice(value.anchorPath.length) : value.refType === 'EXTERNAL_API' ? '' : value.relativePath || ''
  if (!option && !fixed && value.refId == null && value.kind === 'PATH') {
    const resolved = resolvePathOperand(value, options).operand
    if (resolved?.refId != null) {
      option = options.find(item => String(item._varId ?? item.id) === String(resolved.refId) && (item._refType || item.refType) === resolved.refType)
      suffix = resolved.relativePath || ''
    }
  }
  const code = option?.varCode || value.anchorPath || (suffix && fullCode.endsWith(suffix) ? fullCode.slice(0, -suffix.length) : fullCode)
  const label = option?.varLabelText || option?.varLabel || value.label || ''
  const protocol = value.protocolPath || value.refType === 'API_CONTEXT' || option?._refType === 'API_CONTEXT'
  const rawVersion = option?.displayVersion ?? option?._ref?.modelVersion ?? option?._ref?.objectVersion ?? option?.varObj?.version
  const version = protocol ? '' : value.versionMode
    ? compactVersion(value)
    : rawVersion != null ? `v${rawVersion}` : value.refType === 'CONSTANT' ? '' : value.refId != null || option ? '最新' : ''
  const name = label === code || label === fullCode ? '' : label
  const pathText = suffix ? (suffix.startsWith('[') ? suffix : `[${suffix}]`) : ''
  const text = [name, code, version, pathText].filter(Boolean).join(' ')
  return value.refType === 'CONSTANT' && Object.hasOwn(value, 'constantValue') ? `${text} = ${value.constantValue}` : text
}
