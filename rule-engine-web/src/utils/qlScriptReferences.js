import { createPathOperand, resolvePathOperand } from '@/utils/operand'
import { resourceVersionIdentity } from '@/utils/resourceVersion'
import { fieldTypeIcon } from '@/utils/fieldPicker'

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

/** 只生成展示标注，不从编码反推或写入 scriptVarRefs。 */
export function collectQlScriptReferences(script, options = [], functions = [], bindings = []) {
  const tokens = tokenize(String(script || ''))
  const functionOptions = functions.map(fn => ({
    varCode: fn.funcCode, varLabelText: fn.funcName, varType: 'FUNCTION',
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
  tokens.forEach((token, index) => {
    const outputKey = outputKeys.has(index)
    // 输出只映射 key；value 可能是同名临时变量或任意表达式，保持原始代码。
    if (!outputKey && resultBodyTokens.has(index)) return
    if (!outputKey && (!token.identifier || tokens[index - 1]?.text === '.' || locals.has(token.text))) return
    const code = outputKey ? outputKeys.get(index) : token.text
    const rootOptions = byRoot.get(code.split(/[.[]/, 1)[0]) || []
    const path = outputKey ? { code, segments: [{ code, end: token.end }], call: false } : readPath(tokens, index, rootOptions)
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
  if (catalogMode !== 'LIVE' || reference.option.binding?.versionMode === 'FIXED') return null
  return resourceVersionIdentity(reference.option)
}

export function qlReferenceVersionKey(reference, catalogMode) {
  const target = qlReferenceVersionIdentity(reference, catalogMode)
  return target ? `${target.module}:${target.ownerId}` : ''
}

function plainLabel(value) {
  return String(value || '').replace(/[\r\n\t]+/g, ' ')
}

export function referenceTypeDisplay(reference) {
  const option = reference?.option || reference
  const valueType = option?.varType || option?.valueType || 'OBJECT'
  return fieldTypeIcon(valueType)
}

function escapeMarkdown(value) {
  return plainLabel(value).replace(/[\\`*_{}[\]()<>#+.!|~-]/g, '\\$&')
}

export function buildQlReferenceDecorations(references, model, versions = {}, catalogMode = 'LIVE', displayOptions = {}) {
  return references.flatMap(ref => {
    const state = versions[qlReferenceVersionKey(ref, catalogMode)]
    const version = state?.version ?? ref.version
    const versionLabel = version != null ? `v${version}` : state?.loading ? '版本加载中' : '版本未提供'
    const name = plainLabel(ref.label)
    const start = model.getPositionAt(ref.start)
    const end = model.getPositionAt(ref.end)
    const hoverMessage = {
      value: `${escapeMarkdown(referenceTypeDisplay(ref))} ${escapeMarkdown(ref.code)} ${escapeMarkdown(name)} ${escapeMarkdown(versionLabel)}${ref.outputKey ? '\n\n规则输出字段' : ''}\n\n${ref.bound ? '已绑定引擎引用' : '尚未绑定，请从左侧字段或代码补全中选择以记录引用'}${state?.error ? '\n\n版本读取失败，可关闭后重新开启引用展示以重试。' : ''}`,
      isTrusted: false, supportHtml: false,
    }
    const decorations = [{
      range: { startLineNumber: start.lineNumber, startColumn: start.column, endLineNumber: end.lineNumber, endColumn: end.column },
      options: {
        description: 'ql-engine-reference',
        inlineClassName: 'se-reference-code',
        hoverMessage,
      },
    }, {
      range: { startLineNumber: end.lineNumber, startColumn: end.column, endLineNumber: end.lineNumber, endColumn: end.column },
      options: {
        description: 'ql-engine-reference-label',
        showIfCollapsed: true,
        // 同一位置的 before / after 保证名称、版本顺序稳定，且各自使用主题颜色。
        before: { content: ` ${name} `, inlineClassName: 'se-reference-name', inlineClassNameAffectsLetterSpacing: true },
        after: { content: versionLabel, inlineClassName: 'se-reference-version', inlineClassNameAffectsLetterSpacing: true },
        hoverMessage,
      },
    }]
    if (displayOptions.includeType) {
      decorations.unshift({
        range: { startLineNumber: start.lineNumber, startColumn: start.column, endLineNumber: start.lineNumber, endColumn: start.column },
        options: {
          description: 'ql-engine-reference-type',
          showIfCollapsed: true,
          before: {
            content: `${referenceTypeDisplay(ref)} `,
            inlineClassName: 'se-reference-type',
            inlineClassNameAffectsLetterSpacing: true,
          },
          hoverMessage,
        },
      })
    }
    return decorations
  })
}
