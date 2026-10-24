import { pickerReferenceCategory, API_PICKER_CATEGORIES } from './pickerCategories'

// 这些标签是组件的配置协议，不在业务界面展示。
export const FIELD_CATEGORY_TAGS = Object.freeze({
  ...Object.fromEntries(API_PICKER_CATEGORIES.map(item => [item.key, ['http-context', 'reference', 'read']])),
  standalone: ['variable', 'reference', 'read', 'write'],
  constant: ['constant', 'reference', 'read'],
  object: ['object', 'reference', 'read', 'write'],
  model: ['model', 'reference', 'read'],
  function: ['function', 'callable', 'read'],
  operator: ['operator', 'read'],
  external: ['external', 'reference', 'read'],
  rule: ['rule', 'callable'],
  list: ['list', 'reference', 'read'],
  database: ['database', 'reference', 'read'],
  sftp: ['sftp', 'reference', 'read'],
})

export const FIELD_ACTION_TAGS = Object.freeze({
  LITERAL: ['literal', 'read'], PATH: ['path', 'read', 'write'], EXPRESSION: ['expression', 'read'],
})

/** null 不限制；数组为任一匹配，空数组不允许；对象支持 anyOf/allOf/noneOf 组合。 */
export function matchesFieldTags(tags, filter) {
  if (filter == null) return true
  const policy = Array.isArray(filter) ? { anyOf: filter } : filter
  const available = new Set(tags || [])
  return (policy.anyOf == null || policy.anyOf.some(tag => available.has(tag))) &&
    (policy.allOf == null || policy.allOf.every(tag => available.has(tag))) &&
    (policy.noneOf == null || policy.noneOf.every(tag => !available.has(tag)))
}

export function fieldPickerTags(item) {
  const category = item._function ? 'function' : pickerReferenceCategory(item)
  const ref = item._ref || {}
  const raw = item.function || item.resource || item.varObj || ref.varObj || {}
  const type = String(item.varType || raw.returnType || '').toLowerCase()
  const tags = [...(FIELD_CATEGORY_TAGS[category] || []), ...(item.tags || []), ...(ref.tags || [])]
  if (type) tags.push(`type:${type}`)
  if (['integer', 'int', 'long', 'float', 'double', 'decimal', 'bigdecimal', 'probability'].includes(type)) tags.push('type:number')
  const source = item.varSource || ref.varSource || raw.varSource
  if (source) tags.push(`source:${String(source).toLowerCase()}`)
  if (raw.scope) tags.push(`scope:${String(raw.scope).toLowerCase()}`)
  if ((item._refType || item.refType || ref.refType) === 'API_CONTEXT') tags.push('http-context')
  return [...new Set(tags)]
}

export function filterFieldTreeByTags(tree, visibleTags, selectableTags) {
  if (visibleTags == null && selectableTags == null) return tree
  const nodes = new Map()
  const keep = node => {
    node.children = node.children.filter(keep)
    const tags = node.item ? fieldPickerTags(node.item) : FIELD_CATEGORY_TAGS[node.category] || []
    const visible = node.item && matchesFieldTags(tags, visibleTags)
    if (!visible && !node.children.length) return false
    // 为可见子字段保留父级结构，但不能通过父级绕过选择约束。
    node.selectable = node.selectable && !!visible && matchesFieldTags(tags, selectableTags)
    nodes.set(node.key, node)
    return true
  }
  return { roots: tree.roots.filter(keep), nodes }
}

export function canUsePickerAction(kind, visibleTags, selectableTags) {
  const tags = FIELD_ACTION_TAGS[kind] || []
  return matchesFieldTags(tags, visibleTags) && matchesFieldTags(tags, selectableTags)
}
