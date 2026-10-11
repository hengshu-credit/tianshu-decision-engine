import { pickerReferenceCategory } from './pickerCategories'

export const FIELD_ROW_HEIGHT = 36
export const FIELD_PAGE_SIZES = [25, 50, 100, 200, 500]

const numericTypes = new Set(['NUMBER', 'INTEGER', 'INT', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL', 'BIGDECIMAL', 'PROBABILITY'])
export function pickerTypeMatches(actual, expected) {
  const type = String(actual || '').toUpperCase()
  const wanted = String(expected || '').toUpperCase()
  if (!wanted || wanted === 'ANY') return true
  if (numericTypes.has(wanted)) return numericTypes.has(type)
  if (wanted === 'LIST' || wanted === 'ARRAY') return ['LIST', 'ARRAY'].includes(type)
  return type === wanted
}

export function fieldTypeIcon(type) {
  return ({ STRING: 'Abc', CHAR: 'a', NUMBER: 'N', INTEGER: '123', INT: '123', LONG: '123', FLOAT: '1.2', DOUBLE: '1.0', DECIMAL: '1.2', BIGDECIMAL: '1.2', PROBABILITY: 'N', BOOLEAN: 'T/F', DATE: 'D', DATETIME: 'DT', LIST: '[]', ARRAY: '[]', OBJECT: '{}', MAP: '{k}', MODEL: 'M', FUNCTION: 'ƒ', RULE: 'R', OPERATOR: '∑', API: 'API', ENUM: 'E' })[String(type).toUpperCase()] || '?'
}

export function fieldIdentity(item) {
  if (item._function) {
    const fn = item.function || {}
    return `FUNCTION:${fn.functionId ?? fn.id ?? item.varCode}`
  }
  const id = item._varId ?? item.refId ?? item.id ?? item.varObj?.id
  const type = item._refType || item.refType || item._ref?.refType || item.varObj?.refType || 'REF'
  const path = ['EXTERNAL_API', 'API_CONTEXT'].includes(type) ? `:${item.relativePath || ''}` : ''
  return id != null ? `${type}:${id}${path}` : `${pickerReferenceCategory(item)}:${item.varCode}`
}

function source(item) {
  if (item._resource) return null
  const ref = item._ref || {}
  const category = pickerReferenceCategory(item)
  const prefix = { object: 'object', model: 'model', external: 'api' }[category]
  if (!prefix) return null
  const id = ref[`${prefix}Id`] ?? item[`${prefix}Id`] ?? item.varObj?.[`${prefix}Id`]
  const code = ref[`${prefix}Code`] || item[`${prefix}Code`] || String(item.varCode || '').split('.')[0]
  const label = ref[`${prefix}Label`] || ref[`${prefix}Name`] || item[`${prefix}Label`] || code
  return { key: `${category}:${id ?? code}`, id, code, label, category }
}

/** Build a display index once per catalogue change. References always retain their original IDs. */
export function buildFieldTree(items) {
  const roots = []
  const nodes = new Map()
  const groups = new Map()
  for (const item of items) {
    const key = fieldIdentity(item)
    if (nodes.has(key)) continue
    const group = source(item)
    const node = { key, item, name: item.varLabelText || item.varLabel || item.varCode || '', code: item.varCode || '', type: String(item.varType || 'STRING').toUpperCase(), category: item._function ? 'function' : pickerReferenceCategory(item), parent: null, children: [], selectable: !item.disabled }
    nodes.set(key, node)
    if (!group) { roots.push(node); continue }
    if (!groups.has(group.key)) {
      const parent = { key: `group:${group.key}`, name: group.label, code: group.code, category: group.category, type: group.category === 'model' ? 'MODEL' : 'OBJECT', item: null, version: item._ref?.modelVersion, catalogMode: item.catalogMode, parent: null, children: [], selectable: false }
      if (group.id != null && ['model', 'object'].includes(group.category)) parent.item = {
        _versionGroup: true, _varId: group.id, _refType: group.category === 'model' ? 'MODEL' : 'DATA_OBJECT',
        versionOwnerId: group.id, varCode: group.code, varLabel: group.label, varType: parent.type,
        _ref: { category: group.category, modelVersion: item._ref?.modelVersion }, ...item._groupVersion,
        catalogMode: item.catalogMode,
      }
      groups.set(group.key, { parent, fields: [] })
      nodes.set(parent.key, parent)
      roots.push(parent)
    }
    groups.get(group.key).fields.push(node)
    if (node.name.startsWith(group.label + '/')) node.name = node.name.slice(group.label.length + 1)
  }
  for (const { parent, fields } of groups.values()) {
    const byId = new Map(fields.map(node => [String(node.item._varId ?? node.item.id), node]))
    const byCode = new Map(fields.map(node => [node.code, node]))
    for (const node of fields) {
      const parentId = node.item.parentFieldId ?? node.item.varObj?.parentFieldId ?? node.item._ref?.varObj?.parentFieldId
      let owner = parentId != null ? byId.get(String(parentId)) : null
      // Protocol paths and model schemas have no parent IDs: prefixes are only display hierarchy.
      if (!owner) {
        let path = node.code
        while (path.includes('.') || path.includes('[')) {
          path = path.slice(0, Math.max(path.lastIndexOf('.'), path.lastIndexOf('[')))
          const candidate = byCode.get(path)
          if (candidate && ['OBJECT', 'LIST', 'ARRAY', 'MAP'].includes(candidate.type)) { owner = candidate; break }
        }
      }
      // A malformed/cyclic source schema must not make the picker recurse forever.
      let ancestor = owner
      const seen = new Set([node.key])
      while (ancestor && !seen.has(ancestor.key)) { seen.add(ancestor.key); ancestor = ancestor.parent }
      if (ancestor) owner = null
      node.parent = owner || parent
      node.parent.children.push(node)
    }
  }
  return { roots, nodes }
}

export function fieldAncestors(node) {
  const parents = []
  let parent = node?.parent
  while (parent) { parents.unshift(parent); parent = parent.parent }
  return parents
}

export function filterFieldTree(tree, expectedType) {
  if (!expectedType) return tree
  const nodes = new Map()
  const keep = node => {
    node.children = node.children.filter(keep)
    const compatible = node.item && pickerTypeMatches(node.type, expectedType)
    node.selectable = node.selectable && !!compatible
    if (!compatible && !node.children.length) return false
    nodes.set(node.key, node)
    return true
  }
  return { roots: tree.roots.filter(keep), nodes }
}

/** Query matching preserves ancestors without materializing any DOM. */
export function visibleFieldRows(tree, expanded, category, query) {
  const keyword = String(query || '').trim().toLowerCase()
  const matches = new Set()
  const ranks = new Map()
  if (keyword) {
    for (const node of tree.nodes.values()) {
      if (category !== 'all' && node.category !== category) continue
      if ([node.name, node.code].some(value => value.toLowerCase().includes(keyword))) {
        matches.add(node.key)
        const rank = [node.name, node.code].some(value => value.toLowerCase().startsWith(keyword)) ? 1 : 2
        ranks.set(node.key, Math.min(ranks.get(node.key) || 3, rank))
        fieldAncestors(node).forEach(parent => { matches.add(parent.key); ranks.set(parent.key, Math.min(ranks.get(parent.key) || 3, rank)) })
      }
    }
  }
  const result = []
  const ordered = nodes => keyword ? nodes.slice().sort((a, b) => (ranks.get(a.key) || 3) - (ranks.get(b.key) || 3)) : nodes
  const visit = (node, depth, inheritedMatch = false) => {
    if (category !== 'all' && node.category !== category) return
    if (keyword && !inheritedMatch && !matches.has(node.key)) return
    result.push({ node, depth })
    const ownMatch = inheritedMatch || (keyword && [node.name, node.code].some(value => value.toLowerCase().includes(keyword)))
    if (keyword || expanded.has(node.key)) ordered(node.children).forEach(child => visit(child, depth + 1, ownMatch))
  }
  ordered(tree.roots).forEach(node => visit(node, 0))
  return result
}
