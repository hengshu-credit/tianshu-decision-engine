import { parseTree } from 'jsonc-parser'

const MAX_LONG = '9223372036854775807'
const MAX_SAFE = '9007199254740991'
const isIdentityKey = key => key === 'lockVersion' || key === 'id' || /Ids?$/.test(key)
const isIdentityMap = key => key === 'resourceIdMapping' || key === 'fieldIdMapping'
const unsafeNumber = value => typeof value === 'number' && (!Number.isFinite(value) || Math.abs(value) > Number.MAX_SAFE_INTEGER)

function unsafeToken(token) {
  const [, whole, fraction = '', exponent = '0'] = token.match(/^-?(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/)
  const digits = (whole + fraction).replace(/^0+/, '')
  if (!digits) return false
  // 比较原数字的十进制位数与边界，不使用已可能舍入的AST number.value。
  const integerLength = digits.length + Number(exponent) - fraction.length
  if (integerLength !== MAX_SAFE.length) return integerLength > MAX_SAFE.length
  const integer = digits.padEnd(MAX_SAFE.length, '0').slice(0, MAX_SAFE.length)
  return integer > MAX_SAFE || integer === MAX_SAFE && /[1-9]/.test(digits.slice(MAX_SAFE.length))
}

/** 仅明确元数据API使用；先保护原始数字标识，再按普通JSON语义读取其余数据。 */
export function parseMetadataResponse(data) {
  if (typeof data !== 'string') {
    const pending = [{ value: data, identity: false }]
    const visited = new Set()
    while (pending.length) {
      const { value, identity, identityMap = false } = pending.pop()
      if (identity && unsafeNumber(value)) throw new TypeError('标识数值已失去精度，需要原始 JSON 响应')
      if (!value || typeof value !== 'object' || visited.has(value)) continue
      visited.add(value)
      if (Array.isArray(value)) value.forEach(item => pending.push({ value: item, identity, identityMap }))
      else Object.entries(value).forEach(([key, item]) => pending.push({ value: item, identity: identityMap || isIdentityKey(key), identityMap: identityMap || isIdentityMap(key) }))
    }
    return data
  }
  const errors = []
  const root = parseTree(data, errors, { disallowComments: true, allowTrailingComma: false, allowEmptyContent: false })
  if (!root || errors.length) throw new SyntaxError('元数据响应不是有效 JSON')
  const replacements = []
  const pending = [{ node: root, identity: false }]
  while (pending.length) {
    const { node, identity, identityMap = false } = pending.pop()
    if (node.type === 'object') {
      for (const property of node.children || []) {
        const [key, value] = property.children
        pending.push({ node: value, identity: identityMap || isIdentityKey(key.value), identityMap: identityMap || isIdentityMap(key.value) })
      }
    } else if (node.type === 'array') {
      for (const child of node.children || []) pending.push({ node: child, identity, identityMap })
    } else if (identity && node.type === 'number') {
      const token = data.slice(node.offset, node.offset + node.length)
      if (!unsafeToken(token)) continue
      if (!/^(?:0|[1-9]\d*)$/.test(token) || token.length > MAX_LONG.length
        || token.length === MAX_LONG.length && token > MAX_LONG) {
        throw new TypeError('元数据标识必须是有效的非负整数，且不能超出服务端 ID 范围')
      }
      replacements.push({ offset: node.offset, end: node.offset + node.length, token })
    }
  }
  // 仅替换数字token；JSON字符串（modelJson、validationRuleIds等）从不再次解析。
  replacements.sort((left, right) => left.offset - right.offset)
  const parts = []
  let cursor = 0
  for (const replacement of replacements) {
    parts.push(data.slice(cursor, replacement.offset), JSON.stringify(replacement.token))
    cursor = replacement.end
  }
  parts.push(data.slice(cursor))
  return JSON.parse(parts.join(''))
}

export const metadataResponse = { transformResponse: [parseMetadataResponse] }
