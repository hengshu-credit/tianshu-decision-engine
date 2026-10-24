import { createPathOperand, resolvePathOperand } from '@/utils/operand'
import { resourceVersionIdentity } from '@/utils/resourceVersion'
import { fieldTypeIcon } from '@/utils/fieldPicker'
import { contentReferenceParts, contentPartClass, contentTypeFamily } from './contentReference'

// 保留源码偏移；字符串仅在静态下标或 _result 输出 key 位置参与引用匹配。
function tokenize(script) {
  const pattern = /\/\/[^\r\n]*|\/\*[\s\S]*?(?:\*\/|$)|"(?:\\[\s\S]|[^"\\])*(?:"|$)|'(?:\\[\s\S]|[^'\\])*(?:'|$)|[\p{L}_$][\p{L}\p{N}_$]*|\d+(?:\.\d+)?|[^\s]/gu
  return [...script.matchAll(pattern)].filter(match => !/^\/[/*]/.test(match[0])).map(match => ({
    text: match[0], start: match.index, end: match.index + match[0].length,
    identifier: /^[\p{L}_$][\p{L}\p{N}_$]*$/u.test(match[0]),
  }))
}

function readPath(tokens, start, options) {
  const segments = [{ code: tokens[start].text, end: tokens[start].end }]
  let code = tokens[start].text
  let index = start + 1
  while (index < tokens.length) {
    if (tokens[index].text === '.' && tokens[index + 1]?.identifier) {
      code += '.' + tokens[index + 1].text
      segments.push({ code, end: tokens[index + 1].end })
      index += 2
    } else if (tokens[index].text === '[') {
      let depth = 1
      let close = index + 1
      while (close < tokens.length && depth) {
        if (tokens[close].text === '[') depth++
        if (tokens[close].text === ']') depth--
        if (depth) close++
      }
      if (depth) break
      const key = tokens[index + 1]?.text
      if (close === index + 2 && /^(['"])[\p{L}_$][\p{L}\p{N}_$]*\1$/u.test(key)) {
        code += '.' + key.slice(1, -1)
        segments.push({ code, end: tokens[close].end })
      } else if (!options.some(option => option.varCode === code && ['LIST', 'ARRAY'].includes(String(option.varType).toUpperCase()))) break
      // 数组下标不属于字段编码；继续匹配下标之后的字段，源码中的下标原样保留。
      index = close + 1
    } else break
  }
  return { code, segments, call: tokens[index]?.text === '(' }
}

function resultObjectReferences(tokens) {
  const keys = new Map()
  const bodyTokens = new Set()
  tokens.forEach((token, index) => {
    if (bodyTokens.has(index) || token.text !== '_result' || tokens[index - 1]?.text === '.' || tokens[index + 1]?.text !== '=') return
    let start = index + 2
    while (tokens[start]?.text === '(') start++
    if (tokens[start]?.text !== '{') return
    let depth = 1
    let expectingKey = true
    for (let cursor = start + 1; cursor < tokens.length && depth; cursor++) {
      const current = tokens[cursor]
      bodyTokens.add(cursor)
      if (depth === 1 && expectingKey) {
        if (tokens[cursor + 1]?.text === ':') {
          if (current.identifier) keys.set(cursor, current.text)
          else if (/^(['"])[\s\S]*\1$/.test(current.text)) {
            const key = current.text.slice(1, -1).replace(/\\(['"\\])/g, '$1')
            keys.set(cursor, key)
          }
        }
        expectingKey = false
      }
      if (['{', '[', '('].includes(current.text)) depth++
      else if (['}', ']', ')'].includes(current.text)) depth--
      else if (depth === 1 && current.text === ',') expectingKey = true
    }
  })
  return { keys, bodyTokens }
}

function identity(option) {
  return `${option._refType}:${option._varId}`
}

const engineMethods = {
  resourceVersionValue: '固定版本取值', invokeFunctionVersionById: '固定版本方法调用', resourceVersionStatus: '固定版本状态',
}
const syntaxCalls = new Set(['if', 'for', 'while', 'switch', 'catch', 'synchronized', 'return', 'throw', 'new', 'try', 'else', 'do'])
function staticCallArguments(tokens, index) {
  const open = tokens.findIndex((token, i) => i > index && token.text === '(')
  if (open < 0) return []
  const args = []; let depth = 0, begin = open + 1
  for (let i = begin; i < tokens.length; i++) {
    const token = tokens[i]
    if (!depth && (token.text === ',' || token.text === ')')) {
      args.push(i === begin + 1 ? tokens[begin] : null)
      if (token.text === ')') return args
      begin = i + 1
    } else if (['(', '[', '{'].includes(token.text)) depth++
    else if ([')', ']', '}'].includes(token.text)) depth--
  }
  return []
}
function staticString(token) {
  const value = token?.text || ''
  try {
    if (value.startsWith('"')) return JSON.parse(value)
    if (/^'[^'\\]*'$/.test(value)) return value.slice(1, -1)
  } catch { /* 不把动态表达式或不完整字符串当成固定绑定。 */ }
  return null
}

function pinnedCallReference(code, tokens, index, pinned) {
  if (!Object.hasOwn(engineMethods, code)) return null
  const args = staticCallArguments(tokens, index)
  const functionCall = code === 'invokeFunctionVersionById'
  const refType = functionCall ? 'FUNCTION' : staticString(args[0])
  const idToken = args[functionCall ? 0 : 1]
  const refId = staticString(idToken), versionToken = staticString(args[functionCall ? 1 : 2])
  if (!refType || !/^[1-9]\d*$/.test(refId || '') || !/^(?:(?:BUSINESS_VERSION|FUNCTION_HISTORY):)?[1-9]\d*$/.test(versionToken || '')) return null
  const [versionSource, versionId] = versionToken.includes(':') ? versionToken.split(':') : ['', versionToken]
  const candidates = pinned.filter(item => (item.refType || item._refType) === refType && String(item.refId ?? item._varId ?? item.id) === refId
    && String(item.versionId) === versionId && (item.versionSource || '') === versionSource)
  if (candidates.length !== 1) return null
  const item = candidates[0]
  const option = { ...item, _varId: refId, _refType: refType, varCode: item.code || item.varCode || item.functionCode,
    varLabelText: item.label || item.varLabel || item.funcName, varType: item.valueType || item.varType || item.returnType,
    versionMode: 'FIXED', versionId, versionSource,
    ...(code === 'resourceVersionValue' ? { relativePath: staticString(args[3]) || '' } : {}) }
  return { start: idToken.end, end: idToken.end, inlineMetadata: true, code: option.varCode, label: option.varLabelText,
    version: item.displayVersion ?? item.version, option, bound: true }
}

/** 只生成展示标注，不从编码反推或写入 scriptVarRefs。 */
export function collectQlScriptReferences(script, options = [], functions = [], bindings = [], versionReferences = []) {
  const tokens = tokenize(String(script || ''))
  const functionOptions = functions.map(fn => ({
    varCode: fn.funcCode, varLabelText: fn.funcName, varType: fn.returnType || 'FUNCTION',
    _varId: fn.id, _refType: 'FUNCTION', varObj: fn,
  }))
  const allOptions = [...options, ...functionOptions]
  const boundByCode = new Map()
  for (const binding of bindings) {
    if (!binding.refCode || binding.varId == null || !binding.refType) continue
    const list = boundByCode.get(binding.refCode) || []
    list.push(binding)
    boundByCode.set(binding.refCode, list)
  }
  const displayOptions = allOptions.filter(option => !boundByCode.has(option.varCode))
  // 显式绑定优先；已删除或冲突的 ID 不允许被同名资源替代。
  for (const [code, refs] of boundByCode) {
    const identities = new Set(refs.map(ref => `${ref.refType}:${ref.varId}`))
    if (identities.size !== 1) continue
    const binding = refs[0]
    const option = allOptions.find(item => identity(item) === `${binding.refType}:${binding.varId}`)
    if (option) displayOptions.push({ ...option, varCode: code, binding })
  }
  const byRoot = new Map()
  for (const option of displayOptions) {
    if (!option.varCode || option._varId == null || !option._refType) continue
    const root = option.varCode.split(/[.[]/, 1)[0]
    const group = byRoot.get(root) || []
    group.push(option)
    byRoot.set(root, group)
  }
  const locals = new Set(tokens.filter((token, index) => token.identifier &&
    /^(var|boolean|byte|short|int|long|float|double|char|String|Object|List|Map)$/.test(tokens[index - 1]?.text)).map(token => token.text))
  const { keys: outputKeys, bodyTokens: resultBodyTokens } = resultObjectReferences(tokens)
  const result = []
  let decoratedCallEnd = -1
  tokens.forEach((token, index) => {
    const outputKey = outputKeys.has(index)
    // 输出只映射 key；value 可能是同名临时变量或任意表达式，保持原始代码。
    if (token.start < decoratedCallEnd) return
    if (!outputKey && (!token.identifier || tokens[index - 1]?.text === '.' && tokens[index + 1]?.text !== '(')) return
    const code = outputKey ? outputKeys.get(index) : token.text
    const rootOptions = byRoot.get(code.split(/[.[]/, 1)[0]) || []
    const path = outputKey ? { code, segments: [{ code, end: token.end }], call: false } : readPath(tokens, index, rootOptions)
    if (!outputKey && path.call && !syntaxCalls.has(path.code) && tokens[index - 1]?.text !== 'new') {
      const bindingsForCode = boundByCode.get(path.code)
      const matches = tokens[index - 1]?.text === '.' ? [] : functionOptions.filter(item => item.varCode === path.code
        && (!bindingsForCode || bindingsForCode.length === 1 && bindingsForCode[0].refType === 'FUNCTION' && String(bindingsForCode[0].varId) === String(item._varId)))
      const known = matches.length === 1 ? matches[0] : null
      const builtin = Object.hasOwn(engineMethods, path.code)
      const option = known || { _refType: 'FUNCTION', varCode: path.code, varLabelText: builtin ? engineMethods[path.code] : path.code, varType: builtin ? 'ANY' : '', builtin }
      decoratedCallEnd = path.segments.at(-1).end
      result.push({ start: token.start, end: path.segments.at(-1).end, code: script.slice(token.start, path.segments.at(-1).end), option,
        label: option.varLabelText || path.code, version: option.varObj?.currentVersion ?? option.varObj?.version,
        bound: !!known && bindings.some(binding => binding.refType === 'FUNCTION' && String(binding.varId) === String(known._varId)) })
      const pinned = pinnedCallReference(path.code, tokens, index, versionReferences)
      if (pinned) result.push(pinned)
      return
    }
    if (!outputKey && resultBodyTokens.has(index)) return
    if (!outputKey && locals.has(token.text)) return
    const candidates = rootOptions.filter(option =>
      outputKey ? option.varCode === code && option._refType !== 'FUNCTION'
        : option._refType === 'FUNCTION' ? path.call && path.code === token.text : !(path.call && path.code === token.text))
    const { operand } = resolvePathOperand(createPathOperand(path.code), candidates)
    if (!operand.resolved) return
    const segment = path.segments.find(item => item.code === operand.anchorPath)
    const option = candidates.find(item => identity(item) === `${operand.refType}:${operand.refId}` && item.varCode === operand.anchorPath)
    if (!segment || !option) return
    const binding = option.binding
    result.push({
      start: token.start, end: segment.end, code: script.slice(token.start, segment.end), option,
      outputKey,
      label: binding?.label || option.varLabelText || option._ref?.refLabel?.label || option.varLabel || option.varCode,
      bound: Boolean(binding),
      version: binding?.versionMode === 'FIXED' ? binding.version
        : option._ref?.modelVersion ?? option._ref?.objectVersion ?? option.modelVersion ?? option.objectVersion
          ?? option.varObj?.currentVersion ?? option.varObj?.version,
    })
  })
  return result
}

export function qlReferenceVersionIdentity(reference, catalogMode) {
  if (catalogMode !== 'LIVE' || reference.option.versionMode === 'FIXED' || reference.option.binding?.versionMode === 'FIXED') return null
  return resourceVersionIdentity(reference.option)
}

export function qlReferenceVersionKey(reference, catalogMode) {
  const target = qlReferenceVersionIdentity(reference, catalogMode)
  return target ? `${target.module}:${target.ownerId}` : ''
}

function plainLabel(value) {
  return String(value || '').replace(/[\r\n\t]+/g, ' ')
}

// Monaco 按文档字符度量换行和定位光标；留白也必须是注入文本，不能用 CSS 宽度挤偏源码。
function injectedContent(part) {
  const text = plainLabel(part.text)
  if (part.key === 'source') return ' ' + text
  if (part.key === 'type') return text.padEnd(4, ' ')
  return ' ' + text + ' '
}

export function referenceTypeDisplay(reference) {
  const option = reference?.option || reference
  const valueType = option?.varType || option?.valueType || 'OBJECT'
  return fieldTypeIcon(valueType)
}

export function referenceTypeFamily(reference) {
  const option = reference?.option || reference
  const type = String(option?.varType || option?.valueType || 'OBJECT').toUpperCase()
  return contentTypeFamily(type)
}

function escapeMarkdown(value) {
  return plainLabel(value).replace(/[\\`*_{}[\]()<>#+.!|~-]/g, '\\$&')
}

export function buildQlReferenceDecorations(references, model, versions = {}, catalogMode = 'LIVE') {
  return references.flatMap(ref => {
    const state = versions[qlReferenceVersionKey(ref, catalogMode)]
    const version = state?.version ?? ref.version
    const parts = contentReferenceParts({ ...ref.option, varCode: ref.code, varLabelText: ref.label,
      version, versionMode: ref.option.versionMode || (ref.option.binding?.versionMode === 'FIXED' ? 'FIXED' : undefined) })
    const versionPart = parts.find(part => part.key === 'version')
    if (versionPart && state?.loading && version == null) versionPart.text = '版本加载中'
    const start = model.getPositionAt(ref.start), end = model.getPositionAt(ref.end)
    const bindingHint = ref.option.builtin ? '引擎内置方法，无需绑定资源 ID'
      : ref.option._refType === 'FUNCTION' && ref.option._varId == null ? '脚本方法调用，未提供独立资源元信息'
        : ref.bound ? '已绑定引擎引用' : '尚未绑定，请从左侧字段或代码补全中选择以记录引用'
    const hoverMessage = { value: parts.map(part => escapeMarkdown(part.text)).join(' ') + (ref.outputKey ? '\n\n规则输出字段' : '')
      + '\n\n' + bindingHint
      + (state?.error ? '\n\n版本读取失败，可关闭后重新开启引用展示以重试。' : ''), isTrusted: false, supportHtml: false }
    return parts.map(part => ({
      range: { startLineNumber: (part.key === 'version' ? end : start).lineNumber,
        startColumn: (part.key === 'version' ? end : start).column,
        endLineNumber: part.key === 'code' || part.key === 'version' ? end.lineNumber : start.lineNumber,
        endColumn: part.key === 'code' || part.key === 'version' ? end.column : start.column },
      options: { description: 'engine-content-' + part.key, hoverMessage,
        ...(part.key === 'code' && !ref.inlineMetadata ? { inlineClassName: contentPartClass(part) }
          : { showIfCollapsed: true, [part.key === 'version' && !ref.inlineMetadata ? 'after' : 'before']: { content: injectedContent(part), inlineClassName: contentPartClass(part), inlineClassNameAffectsLetterSpacing: true } }),
      },
    }))
  })
}
