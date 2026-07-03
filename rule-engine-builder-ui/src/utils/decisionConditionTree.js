import {
  compileConditionExpression,
  normalizeConditionOperator
} from '@/constants/conditionOperators'

export function createEmptyGroup(op = 'AND') {
  return { type: 'group', op, children: [] }
}

export function createEmptyActionItem() {
  return {
    varCode: '',
    varLabel: '',
    varType: 'STRING',
    enumOptions: '',
    value: ''
  }
}

export function createEmptyLeaf() {
  return {
    type: 'leaf',
    varCode: '',
    varLabel: '',
    varType: 'STRING',
    enumOptions: '',
    operator: '==',
    valueKind: 'CONST',
    value: ''
  }
}

export function normalizeConditionLeafOperator(leaf) {
  if (!leaf || typeof leaf !== 'object') return leaf
  const nextOperator = normalizeConditionOperator(leaf.operator || '==', leaf.varType || 'STRING')
  if (nextOperator !== leaf.operator) leaf.operator = nextOperator
  return leaf
}

export function migrateRuleConditionsToTree(legacyConds, colDefs) {
  const children = []
  const n = Math.max(legacyConds && legacyConds.length ? legacyConds.length : 0, colDefs && colDefs.length ? colDefs.length : 0)
  for (let j = 0; j < n; j++) {
    const cond = (legacyConds && legacyConds[j]) || { operator: '==', value: '' }
    const def = (colDefs && colDefs[j]) || {}
    children.push(normalizeConditionLeafOperator({
      type: 'leaf',
      varCode: def.varCode || '',
      varLabel: def.varLabel || '',
      varType: def.varType || 'STRING',
      enumOptions: def.enumOptions || '',
      _varId: def._varId,
      operator: cond.operator || '==',
      valueKind: 'CONST',
      value: cond.value != null ? String(cond.value) : ''
    }))
  }
  return { type: 'group', op: 'AND', children }
}

export function collectVarCodesFromConditionTree(node, out) {
  const s = out || new Set()
  if (!node || typeof node !== 'object') return s
  if (node.type === 'leaf') {
    if (node.varCode) s.add(node.varCode)
    if (node.valueKind === 'VAR' && node.value) s.add(node.value)
    return s
  }
  if (node.type === 'group' && Array.isArray(node.children)) {
    node.children.forEach(c => collectVarCodesFromConditionTree(c, s))
  }
  return s
}

export function walkConditionLeaves(node, fn) {
  if (!node || typeof node !== 'object') return
  if (node.type === 'leaf') {
    fn(node)
    return
  }
  if (node.type === 'group' && Array.isArray(node.children)) {
    node.children.forEach(c => walkConditionLeaves(c, fn))
  }
}

export function hasUsableConditionLeaf(node) {
  let usable = false
  walkConditionLeaves(node, leaf => {
    if (usable) return
    if (!leaf.varCode || leaf.operator === '*') return
    if (['is_null', 'not_null', 'is_empty', 'not_empty', 'is_true', 'is_false'].includes(leaf.operator)) {
      usable = true
      return
    }
    if (leaf.valueKind === 'VAR') {
      usable = !!leaf.value
      return
    }
    usable = leaf.value !== undefined && leaf.value !== null && String(leaf.value) !== ''
  })
  return usable
}

export function compileConditionTreeExpression(node) {
  if (!node || typeof node !== 'object') return 'true'
  if (node.type === 'group') {
    const children = Array.isArray(node.children) ? node.children : []
    const expressions = children.map(compileConditionTreeExpression).filter(Boolean)
    if (!expressions.length) return 'true'
    const joiner = node.op === 'OR' ? ' || ' : ' && '
    return '(' + expressions.join(joiner) + ')'
  }
  if (node.type === 'leaf') {
    return compileConditionLeafExpression(node)
  }
  return 'true'
}

function compileConditionLeafExpression(leaf) {
  if (!leaf || !leaf.varCode) return 'true'
  return compileConditionExpression(
    leaf.varCode,
    leaf.varType,
    leaf.operator || '==',
    leaf.value,
    leaf.valueKind
  )
}
