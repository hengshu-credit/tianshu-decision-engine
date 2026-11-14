export function typeSchema(value) {
  switch (String(value || '').toUpperCase()) {
    case 'INTEGER': case 'INT': case 'LONG': return { type: 'integer' }
    case 'NUMBER': case 'DOUBLE': case 'FLOAT': case 'DECIMAL': case 'PROBABILITY': return { type: 'number' }
    case 'BOOLEAN': case 'BOOL': return { type: 'boolean' }
    case 'ARRAY': case 'LIST': case 'VECTOR': return { type: 'array', items: {} }
    case 'OBJECT': case 'MAP': return { type: 'object' }
    default: return { type: 'string' }
  }
}

// 开放接口的 data 可以是对象、数组、单值或模板引用。动态结果类型未知时保留开放契约。
export function templateSchema(template, mappedFields) {
  if (Array.isArray(template)) return template.length
    ? { type: 'array', prefixItems: template.map(item => templateSchema(item, mappedFields)), minItems: template.length, maxItems: template.length }
    : { type: 'array', maxItems: 0 }
  if (template && typeof template === 'object') return { type: 'object',
    properties: Object.fromEntries(Object.entries(template).map(([key, value]) => [key, templateSchema(value, mappedFields)])),
    required: Object.keys(template) }
  if (template === '${response}') return { type: 'object', properties: Object.fromEntries(mappedFields.map(field => [field.name, field.schema])) }
  if (typeof template === 'string' && /^\$\{[A-Za-z0-9_.-]+\}$/.test(template)) return {}
  return { type: template === null ? 'null' : typeof template }
}

// 高位数组下标仍在文档中显示完整路径，避免为了构造请求示例分配巨型数组。
const MAX_EXAMPLE_INDEX = 255

function fieldPath(field, prefix) {
  if (field.parts) return field.parts
  const path = String(field.path || '')
  return (prefix && path.startsWith(prefix + '.') ? path.slice(prefix.length + 1) : path).split('.').filter(Boolean)
}

export function fieldsSchema(fields, prefix = '') {
  const root = {}
  for (const field of fields || []) {
    const parts = fieldPath(field, prefix)
    let node = root
    for (const part of parts) {
      if (typeof part === 'number') {
        node.type = 'array'
        if (field.required) node.minItems = Math.max(node.minItems || 0, part + 1)
        if (part > MAX_EXAMPLE_INDEX) { node = null; break }
        node.prefixItems ||= []
        while (node.prefixItems.length <= part) node.prefixItems.push({})
        node = node.prefixItems[part]
      } else {
        node.type = 'object'
        node.properties ||= Object.create(null)
        if (field.required) node.required = [...new Set([...(node.required || []), part])]
        node = node.properties[part] ||= {}
      }
    }
    if (node) {
      const leaf = typeSchema(field.type)
      node.type ||= leaf.type
      if (node.type === 'array') node.items ||= {}
      if (field.label || field.description) node.description = [field.label, field.description].filter(Boolean).join('。')
      if (field.exampleValue != null && field.exampleValue !== '') node.example = field.exampleValue
    }
  }
  return Object.keys(root).length ? root : { type: 'object', properties: {} }
}

export function buildExampleBody(fields) {
  let root = {}
  const ordered = (fields || []).map(field => ({ field, parts: fieldPath(field) })).sort((a, b) => a.parts.length - b.parts.length)
  for (const { field, parts } of ordered) {
    if (parts.some(part => typeof part === 'number' && part > MAX_EXAMPLE_INDEX)) return undefined
    if (!parts.length) {
      root = structuredClone(field.exampleValue)
      continue
    }
    if (!root || typeof root !== 'object' || Array.isArray(root) !== (typeof parts[0] === 'number')) {
      root = typeof parts[0] === 'number' ? [] : {}
    }
    let node = root
    parts.forEach((part, i) => {
      // 在序列化为 JSON 前明确填充空位，schema 允许这些位置为 null。
      if (Array.isArray(node)) while (node.length < part) node.push(null)
      const last = i === parts.length - 1
      if (last || !Object.hasOwn(node, part) || !node[part] || typeof node[part] !== 'object') {
        const child = typeof parts[i + 1] === 'number' ? [] : Object.create(null)
        Object.defineProperty(node, part, { value: last ? structuredClone(field.exampleValue) : child,
          enumerable: true, writable: true, configurable: true })
      }
      node = node[part]
    })
  }
  return root
}
