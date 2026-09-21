function parseParams(value) {
  if (Array.isArray(value)) return value
  try {
    const parsed = JSON.parse(value || '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch (error) {
    return []
  }
}

function completionPrefix(linePrefix) {
  const match = String(linePrefix || '').match(/[A-Za-z_$][\w$]*(?:\.[\w$]*)*$/)
  return match ? match[0] : ''
}

export function buildQlCompletionItems(refs = [], functions = [], linePrefix = '') {
  const prefix = completionPrefix(linePrefix)
  const memberIndex = prefix.lastIndexOf('.')
  const memberRoot = memberIndex >= 0 ? prefix.slice(0, memberIndex + 1) : ''
  const typedValue = memberIndex >= 0 ? prefix.slice(memberIndex + 1) : prefix
  const referenceItems = refs
    .filter((ref) => ref && ref.refCode)
    .filter((ref) => {
      if (memberRoot) {
        const memberPath = ref.refCode.slice(memberRoot.length)
        return (
          ref.refCode.startsWith(memberRoot) &&
          (!typedValue ||
            memberPath.toLowerCase().startsWith(typedValue.toLowerCase()))
        )
      }
      return !typedValue || ref.refCode.toLowerCase().startsWith(typedValue.toLowerCase())
    })
    .map((ref) => {
      const label = memberRoot ? ref.refCode.slice(memberRoot.length) : ref.refCode
      return {
        label,
        insertText: label,
        detail: `${ref.varLabel || ref.refCode} · ${ref.varType || 'OBJECT'}`,
        documentation: `${ref.refCode}\n类型：${ref.varType || 'OBJECT'}`,
        valueType: ref.varType || 'OBJECT',
        refCode: ref.refCode,
        kind: 'FIELD',
        stableReference:
          ref._varId != null && (ref._refType || ref.refType)
            ? {
                refCode: ref.refCode,
                varId: ref._varId,
                refType: ref._refType || ref.refType,
              }
            : null,
      }
    })

  if (memberRoot) return referenceItems
  const functionItems = functions
    .filter((func) => func && func.funcCode)
    .filter(
      (func) =>
        !typedValue ||
        func.funcCode.toLowerCase().startsWith(typedValue.toLowerCase())
    )
    .map((func) => {
      const params = parseParams(func.paramsJson)
      return {
        label: func.funcCode,
        insertText: `${func.funcCode}(${params
          .map((param, index) => `\${${index + 1}:${param.name || `arg${index + 1}`}}`)
          .join(', ')})`,
        detail: `${func.funcName || func.funcCode} · ${func.returnType || 'OBJECT'}`,
        documentation: params
          .map((param) => `${param.name || '-'}: ${param.type || 'OBJECT'}`)
          .join('\n'),
        kind: 'FUNCTION',
        snippet: true,
        stableReference: func.id == null ? null : {
          refCode: func.funcCode,
          varId: func.id,
          refType: 'FUNCTION',
        },
      }
    })
  return [...referenceItems, ...functionItems]
}

function isBoundary(character) {
  return !character || !/[\w$.]/.test(character)
}

export function buildQlHover(line, column, refs = []) {
  const source = String(line || '')
  const offset = Math.max(0, Number(column || 1) - 1)
  const matches = refs
    .filter((ref) => ref && ref.refCode)
    .flatMap((ref) => {
      const result = []
      let start = source.indexOf(ref.refCode)
      while (start >= 0) {
        const end = start + ref.refCode.length
        if (
          offset >= start &&
          offset <= end &&
          isBoundary(source[start - 1]) &&
          isBoundary(source[end])
        ) {
          result.push({ ref, start, end })
        }
        start = source.indexOf(ref.refCode, start + 1)
      }
      return result
    })
    .sort((left, right) => right.ref.refCode.length - left.ref.refCode.length)
  if (!matches.length) return null
  const ref = matches[0].ref
  return {
    refCode: ref.refCode,
    label: ref.varLabel || ref.refCode,
    valueType: ref.varType || 'OBJECT',
    refType: ref._refType || ref.refType || '',
  }
}

export function buildQlSignatureHelp(linePrefix, functions = []) {
  const source = String(linePrefix || '')
  const match = source.match(/([A-Za-z_$][\w$]*)\s*\(([^()]*)$/)
  if (!match) return null
  const func = functions.find((item) => item && item.funcCode === match[1])
  if (!func) return null
  const params = parseParams(func.paramsJson)
  const formattedParams = params.map((param, index) => ({
    label: `${param.name || `arg${index + 1}`}: ${param.type || 'OBJECT'}`,
    documentation: param.description || param.label || '',
  }))
  return {
    functionCode: func.funcCode,
    label: `${func.funcCode}(${formattedParams
      .map((param) => param.label)
      .join(', ')}): ${func.returnType || 'OBJECT'}`,
    activeParameter: Math.min(
      Math.max(0, match[2].split(',').length - 1),
      Math.max(0, formattedParams.length - 1)
    ),
    parameters: formattedParams,
  }
}
