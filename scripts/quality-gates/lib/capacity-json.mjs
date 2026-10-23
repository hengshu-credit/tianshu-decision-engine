import jsonc from 'jsonc-parser'

/** 用现有严格AST读取原始数字；不经Number中转，也不重写用户的请求body。 */
export function parseCapacityJson(text) {
  const errors = []
  const root = jsonc.parseTree(text, errors, { disallowComments: true, allowTrailingComma: false, allowEmptyContent: false })
  if (!root || errors.length) throw new Error('invalid strict JSON')
  const visit = node => {
    if (node.type === 'object') {
      const keys = new Set()
      for (const property of node.children || []) {
        const key = property.children[0].value
        if (keys.has(key)) throw new Error('duplicate JSON object key')
        keys.add(key)
        visit(property.children[1])
      }
    } else if (node.type === 'array') {
      for (const child of node.children || []) visit(child)
    }
  }
  visit(root)
  return { root, text }
}

export function capacityJsonNode(document, path) {
  let node = document.root
  for (const key of path) {
    if (node?.type === 'array' && /^(0|[1-9][0-9]*)$/.test(key)) {
      node = node.children?.[Number(key)]
    } else if (node?.type === 'object') {
      node = node.children?.find(property => property.children[0].value === key)?.children[1]
    } else return undefined
  }
  return node
}

export function capacityJsonText(document, node) {
  return document.text.slice(node.offset, node.offset + node.length)
}

function decimalIdentity(text) {
  const [mantissa, exponent = '0'] = text.toLowerCase().split('e')
  const sign = mantissa.startsWith('-') ? '-' : ''
  const fraction = mantissa.includes('.') ? mantissa.length - mantissa.indexOf('.') - 1 : 0
  let digits = mantissa.replace('-', '').replace('.', '').replace(/^0+/, '')
  if (!digits) return '0'
  const zeros = digits.length - digits.replace(/0+$/, '').length
  digits = digits.slice(0, digits.length - zeros)
  return `${sign}${digits}e${BigInt(exponent) - BigInt(fraction) + BigInt(zeros)}`
}

export function matchesCapacityResponse(document, request) {
  if (capacityJsonNode(document, ['code'])?.value !== '000000'
      || capacityJsonNode(document, ['success'])?.value !== true) return false;
  return Object.entries(request.expectedResponse || { code: '000000', success: true }).every(([path, expected]) => {
    const node = capacityJsonNode(document, path.split('.'))
    if (!node) return false
    if (typeof expected === 'number') {
      const raw = request.expectedResponseNumberText?.[path]
      // JS直接调用者若已丢失原文，不能替其猜测大整数；CLI文件路径始终保存数字token。
      if (!raw && (!Number.isFinite(expected) || (Number.isInteger(expected) && !Number.isSafeInteger(expected)))) return false
      return node.type === 'number'
        && decimalIdentity(raw || String(expected)) === decimalIdentity(capacityJsonText(document, node))
    }
    return expected === null ? node.type === 'null' : node.type === typeof expected && node.value === expected
  })
}
