import { parseTree } from 'jsonc-parser'

class ExactNumber {
  constructor(value) {
    if (!/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(value)) throw new TypeError('请输入有效的 JSON 数字')
    this.value = value
    Object.freeze(this)
  }

  toString() { return this.value }
}

function strictJsonAst(text) {
  if (typeof text !== 'string') throw new TypeError('原始 JSON 必须是字符串')
  const errors = []
  const root = parseTree(text, errors, { disallowComments: true, allowTrailingComma: false, allowEmptyContent: false })
  if (!root || errors.length) throw new SyntaxError('JSON 格式错误，请检查内容')
  // 即使快照不比较某个分支，该分支仍必须是无重复键的合法JSON。
  const pending = [root]
  while (pending.length) {
    const node = pending.pop()
    if (node.type === 'object') {
      const keys = new Set()
      for (const property of node.children || []) {
        const [key, value] = property.children
        if (keys.has(key.value)) throw new SyntaxError(`JSON 包含重复字段：${key.value}`)
        keys.add(key.value)
        pending.push(value)
      }
    } else if (node.type === 'array') {
      for (const child of node.children || []) pending.push(child)
    }
  }
  return root
}

function readExactNode(node, text, depth = 0) {
  if (depth > 128) throw new TypeError('JSON 嵌套超过支持深度')
  if (node.type === 'number') return createExactNumber(text.slice(node.offset, node.offset + node.length))
  if (node.type === 'array') return (node.children || []).map(child => readExactNode(child, text, depth + 1))
  if (node.type === 'object') {
    const object = Object.create(null)
    for (const property of node.children || []) object[property.children[0].value] = readExactNode(property.children[1], text, depth + 1)
    return object
  }
  return node.value
}

// 只用于测试配置的原始JSON；不要把无损数字传给普通JSON.stringify或转换成Number。
export function parseExactJson(text) {
  return readExactNode(strictJsonAst(text), text)
}

/** 只生成结果校验视图，绝不能用返回值重写用户保存的responseJson。 */
export function parseExecutionSnapshotJson(text) {
  const root = strictJsonAst(text)
  if (root.type !== 'object') throw new TypeError('结果快照必须是 JSON 对象')
  const properties = node => new Map((node.children || []).map(property => [property.children[0].value, property.children[1]]))
  const outer = properties(root)
  const payload = outer.get('data')?.type === 'object' ? properties(outer.get('data')) : outer
  const outputKey = ['result', 'outputs', 'value', 'resolvedValue'].find(key => payload.has(key))
  const errorKey = ['errorMessage', 'error'].find(key => payload.has(key))
  // 手工响应仍可为普通业务对象；仅明确的执行结果适用排除调试元数据的契约。
  if (payload.get('success')?.type !== 'boolean' || (!outputKey && !errorKey)) return readExactNode(root, text)
  const view = Object.create(null)
  view.success = readExactNode(payload.get('success'), text)
  if (outputKey) view.result = readExactNode(payload.get(outputKey), text)
  if (errorKey) view.errorMessage = readExactNode(payload.get(errorKey), text)
  return view
}

export function stringifyExactJson(value, space = 0) {
  const indentation = typeof space === 'number' ? ' '.repeat(Math.max(0, Math.min(10, Math.floor(space)))) : String(space).slice(0, 10)
  const ancestors = new Set()
  const write = (current, depth = 0) => {
    if (depth > 128) throw new TypeError('JSON 嵌套超过支持深度')
    if (isExactNumber(current)) return current.value
    if (typeof current === 'number' || typeof current === 'bigint') return exactNumberText(current)
    if (current === null || typeof current === 'string' || typeof current === 'boolean') return JSON.stringify(current)
    if (typeof current !== 'object') throw new TypeError('存在无法保存为 JSON 的值')
    if (ancestors.has(current)) throw new TypeError('JSON 不能包含循环引用')
    ancestors.add(current)
    try {
      const array = Array.isArray(current)
      if (!array && ![null, Object.prototype].includes(Object.getPrototypeOf(current))) throw new TypeError('仅支持 JSON 对象、数组和字面量')
      const children = array
        ? Array.from(current, item => item === undefined ? 'null' : write(item, depth + 1))
        : Object.keys(current).filter(key => current[key] !== undefined).map(key => JSON.stringify(key) + (indentation ? ': ' : ':') + write(current[key], depth + 1))
      const [open, close] = array ? ['[', ']'] : ['{', '}']
      if (!children.length) return open + close
      return indentation
        ? open + '\n' + indentation.repeat(depth + 1) + children.join(',\n' + indentation.repeat(depth + 1)) + '\n' + indentation.repeat(depth) + close
        : open + children.join(',') + close
    } finally { ancestors.delete(current) }
  }
  return write(value)
}

export function isExactNumber(value) {
  return value instanceof ExactNumber
}

export function exactNumberText(value) {
  if (isExactNumber(value)) return value.value
  if (typeof value === 'bigint') return String(value)
  if (typeof value === 'number' && Number.isFinite(value)) {
    if (Number.isInteger(value) && !Number.isSafeInteger(value)) throw new TypeError('数字已超出安全精度，请使用原始 JSON')
    return String(value)
  }
  throw new TypeError('不是有效数字')
}

export function createExactNumber(text) {
  return new ExactNumber(String(text).trim())
}

export function cloneExactJson(value) {
  return parseExactJson(stringifyExactJson(value))
}
