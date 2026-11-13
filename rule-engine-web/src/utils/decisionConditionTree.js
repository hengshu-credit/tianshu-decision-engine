import { isSourceStatusOperator, normalizeConditionOperator } from '@/constants/conditionOperators'
import { compileConditionOperands } from '@/utils/conditionOperand'
import { ConditionRangeError, isRangeOperator } from '@/utils/conditionRange'
import { ConditionCollectionError, isTypedCollection } from '@/utils/conditionCollection'
import {
  collectOperandReferences,
  compileOperand,
  createLiteralOperand,
  operandFromReferenceFields
} from '@/utils/operand'

export function createEmptyGroup(op = 'AND') {
  return { type: 'group', op, children: [] }
}

export function createEmptyActionItem() {
  return {
    targetOperand: null,
    valueOperand: null
  }
}

export function createEmptyLeaf() {
  return {
    type: 'leaf',
    leftOperand: null,
    operator: '==',
    rightOperand: null
  }
}

export function normalizeConditionLeafOperator(leaf) {
  if (!leaf || typeof leaf !== 'object') return leaf
  const valueType = leaf.leftOperand && leaf.leftOperand.valueType
  const nextOperator = normalizeConditionOperator(leaf.operator || '==', valueType || 'STRING', leaf.leftOperand)
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
      leftOperand: operandFromReferenceFields(def),
      operator: cond.operator || '==',
      rightOperand: createLiteralOperand(cond.value != null ? String(cond.value) : '', def.varType || 'STRING')
    }))
  }
  return { type: 'group', op: 'AND', children }
}

export function collectVarCodesFromConditionTree(node, out) {
  const s = out || new Set()
  if (!node || typeof node !== 'object') return s
  if (node.type === 'leaf') {
    collectOperandReferences(node.leftOperand).forEach(ref => {
      if (ref.code || ref.path) s.add(ref.code || ref.path)
    })
    collectOperandReferences(node.rightOperand).forEach(ref => {
      if (ref.code || ref.path) s.add(ref.code || ref.path)
    })
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

export function normalizeConditionTreeOperands(node) {
  walkConditionLeaves(node, leaf => {
    if (!leaf.leftOperand && leaf.varCode) leaf.leftOperand = operandFromReferenceFields(leaf)
    if (!leaf.rightOperand && leaf.value !== undefined) {
      leaf.rightOperand = leaf.valueKind === 'VAR'
        ? operandFromReferenceFields({
          varCode: leaf.value,
          varLabel: leaf.rightVarLabel,
          varType: leaf.rightVarType,
          _varId: leaf._rightVarId,
          _refType: leaf._rightRefType
        })
        : createLiteralOperand(leaf.value, leaf.varType || 'STRING')
    }
    ['varCode', 'varLabel', 'varType', 'value', 'valueKind', '_varId', '_refType',
      'rightVarLabel', 'rightVarType', '_rightVarId', '_rightRefType'].forEach(key => delete leaf[key])
  })
  return node?.type === 'leaf' ? { type: 'group', op: 'AND', children: [node] } : node
}

export function hasUsableConditionLeaf(node) {
  let usable = false
  walkConditionLeaves(node, leaf => {
    if (usable) return
    if (!compileOperand(leaf.leftOperand) || leaf.operator === '*') return
    if (['is_null', 'not_null', 'is_empty', 'not_empty', 'is_true', 'is_false'].includes(leaf.operator) ||
      isSourceStatusOperator(leaf.operator)) {
      usable = true
      return
    }
    usable = !!compileOperand(leaf.rightOperand)
  })
  return usable
}

export function requiredConditionError(node, position = '条件') {
  if (node?.type === 'group') {
    if (node.op && !['AND', 'OR'].includes(node.op)) return position + '组的关系必须为 AND 或 OR'
    if (!Array.isArray(node.children) || !node.children.length) return position + '组至少需要一个条件'
    for (const [index, child] of node.children.entries()) {
      const error = requiredConditionError(child, position + '.' + (index + 1))
      if (error) return error
    }
    return ''
  }
  try {
    return node?.type === 'leaf' && node.operator && hasUsableConditionLeaf(node) ? '' : position + '未配置完整'
  } catch (error) { return position + '：' + error.message }
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

export function previewConditionTreeExpression(node) {
  try {
    return { expression: compileConditionTreeExpression(node), error: '' }
  } catch (error) {
    if (!(error instanceof ConditionRangeError) && !(error instanceof ConditionCollectionError)) throw error
    return { expression: '', error: error.message }
  }
}

function compileConditionLeafExpression(leaf) {
  if (!leaf || (!leaf.leftOperand && !isRangeOperator(leaf.operator) && !isTypedCollection(leaf.operator, leaf.rightOperand))) return 'true'
  return compileConditionOperands(leaf.leftOperand, leaf.operator, leaf.rightOperand)
}
