/**
 * 决策表条件树：数据结构工厂、旧版矩阵迁移、变量编码收集（用于测试入参）。
 */

/** @returns {{ type: 'group', op: 'AND'|'OR', children: [] }} */
export function createEmptyGroup(op = 'AND') {
  return { type: 'group', op, children: [] }
}

/** @returns {Object} 规则内一条 THEN 动作（变量 + 赋值） */
export function createEmptyActionItem() {
  return {
    varCode: '',
    varLabel: '',
    varType: 'STRING',
    enumOptions: '',
    value: ''
  }
}

/** @returns {Object} 默认可编辑叶节点 */
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

/**
 * 将单条旧版规则（conditions 数组与列定义对齐）转为 conditionRoot。
 * @param {Array} legacyConds - rule.conditions
 * @param {Array} colDefs - model.conditions
 */
export function migrateRuleConditionsToTree(legacyConds, colDefs) {
  const children = []
  const n = Math.max(legacyConds && legacyConds.length ? legacyConds.length : 0, colDefs && colDefs.length ? colDefs.length : 0)
  for (let j = 0; j < n; j++) {
    const cond = (legacyConds && legacyConds[j]) || { operator: '==', value: '' }
    const def = (colDefs && colDefs[j]) || {}
    children.push({
      type: 'leaf',
      varCode: def.varCode || '',
      varLabel: def.varLabel || '',
      varType: def.varType || 'STRING',
      enumOptions: def.enumOptions || '',
      _varId: def._varId,
      operator: cond.operator || '==',
      valueKind: 'CONST',
      value: cond.value != null ? String(cond.value) : ''
    })
  }
  return { type: 'group', op: 'AND', children }
}

/**
 * 深度优先收集条件树中出现的变量编码（左侧 varCode + 右侧 VAR 的 value）。
 * @param {Object|null} node
 * @param {Set<string>} [out]
 * @returns {Set<string>}
 */
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

/**
 * 遍历条件树，对每个叶节点执行回调。
 * @param {Object|null} node
 * @param {(leaf: Object) => void} fn
 */
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

/**
 * 判断条件树里是否至少有一条可执行的叶条件。
 * @param {Object|null} node
 * @returns {boolean}
 */
export function hasUsableConditionLeaf(node) {
  let usable = false
  walkConditionLeaves(node, leaf => {
    if (usable) return
    if (!leaf.varCode || leaf.operator === '*') return
    if (leaf.valueKind === 'VAR') {
      usable = !!leaf.value
      return
    }
    usable = leaf.value !== undefined && leaf.value !== null && String(leaf.value) !== ''
  })
  return usable
}

/**
 * 将决策表条件树编译为 QLExpress 布尔表达式。
 * @param {Object|null} node
 * @returns {string}
 */
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
  const operator = leaf.operator || '=='
  if (operator === '*') return 'true'

  if (leaf.valueKind === 'VAR') {
    if (!leaf.value) return 'true'
    return leaf.varCode + ' ' + operator + ' ' + leaf.value
  }

  if (leaf.value === undefined || leaf.value === null || String(leaf.value) === '') {
    return 'true'
  }
  return leaf.varCode + ' ' + operator + ' ' + formatConditionConstant(leaf.varType, leaf.value)
}

function formatConditionConstant(varType, value) {
  const text = String(value)
  if (varType === 'STRING' || varType === 'ENUM' || varType === 'DATE') {
    return '"' + text.replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"'
  }
  return text
}
