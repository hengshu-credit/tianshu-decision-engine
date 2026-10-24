import { compileOperand, inferOperandType, operandFromReferenceFields, createLiteralOperand, OPERAND_KINDS } from '@/utils/operand'
import { findConditionOperator } from '@/constants/conditionOperators'
import { noRightOperand, operationLabel, predicateOperator } from './contentOperation'
import { isRangeOperator, requireRangeBounds } from '@/utils/conditionRange'
import { isTypedCollection, requireCollectionItems } from '@/utils/conditionCollection'
import { LIST_COMBINATION_MODES, LIST_ITEM_TYPES, LIST_MATCH_MODES } from '@/constants/listMatchModes'

function labelAndCode(label, code) {
  const normalizedLabel = String(label || '')
  const normalizedCode = String(code || '')
  if (normalizedLabel && normalizedLabel !== normalizedCode) return normalizedLabel + ' ' + normalizedCode
  return normalizedCode || normalizedLabel
}

function formatNode(operand, rootOperation, humanReadable = false) {
  if (!operand || !operand.kind) return ''
  if (operand.kind === OPERAND_KINDS.LITERAL) {
    if (humanReadable && operand.value == null) return '固定值未填写'
    if (humanReadable && inferOperandType(operand) === 'BOOLEAN') {
      if (operand.value === true || operand.value === 'true') return '是（true）'
      if (operand.value === false || operand.value === 'false') return '否（false）'
      return '布尔值配置未完成'
    }
    return compileOperand(operand)
  }
  if (operand.kind === OPERAND_KINDS.PATH || operand.kind === OPERAND_KINDS.REFERENCE) {
    return labelAndCode(operand.label, operand.code || operand.value)
  }
  if (operand.kind === OPERAND_KINDS.FUNCTION) {
    const name = labelAndCode(operand.label, operand.functionCode)
    return name + '(' + (operand.args || []).map(item => formatNode(item, false, humanReadable)).join(', ') + ')'
  }
  if (operand.kind === OPERAND_KINDS.OPERATION) {
    const content = (operand.terms || []).map((term, index) => {
      const value = formatNode(term && term.operand, false, humanReadable)
      return index === 0 ? value : (predicateOperator(term?.operator) ? operationLabel(term.operator) : term?.operator || '') + (noRightOperand(term?.operator) ? '' : ' ' + value)
    }).join(' ')
    return rootOperation ? content : '(' + content + ')'
  }
  if (operand.kind === OPERAND_KINDS.ACCESS) {
    const code = String(operand.accessType || '').toUpperCase() === 'INDEX' ? 'arrGet' : 'objGet'
    const label = code === 'arrGet' ? '数组取值' : '字典取值'
    return `${label}${humanReadable ? '' : ' ' + code}(${formatNode(operand.target, false, humanReadable)}, ${formatNode(operand.accessor, false, humanReadable)})`
  }
  if (operand.kind === OPERAND_KINDS.CAST) {
    if (humanReadable) {
      const label = { NUMBER: '数值', BOOLEAN: '布尔值', STRING: '文本', LIST: '列表', MAP: '对象', OBJECT: '对象' }[operand.targetType]
      return label ? `转为${label}（${formatNode(operand.operand, false, true) || '未填写'}）` : '自定义转换（请查看原表达式）'
    }
    const script = compileOperand(operand)
    const index = script.indexOf('(')
    const code = index === -1 ? script : script.substring(0, index)
    return `类型转换 ${code}(${formatNode(operand.operand, false)})`
  }
  if (operand.kind === OPERAND_KINDS.ARRAY) {
    return '[' + (operand.items || []).map(item => formatNode(item, false, humanReadable) || (humanReadable ? '未填写' : '')).join(', ') + ']'
  }
  if (operand.kind === OPERAND_KINDS.LIST_QUERY) {
    if (!humanReadable) return '名单查询 ' + compileOperand(operand)
    const combination = LIST_COMBINATION_MODES.find(item => item.value === operand.combinationMode)
    const match = LIST_MATCH_MODES.find(item => item.value === operand.matchMode)
    const lists = operand.listIds?.length ? '名单ID ' + operand.listIds.join('、') : '未选择名单'
    const types = (operand.itemTypes || []).map(value => LIST_ITEM_TYPES.find(item => item.value === value)?.label || `自定义类型 ${value}`)
    return '名单查询（' + [lists, combination?.label || '组合方式未识别', match?.label || '匹配方式未识别',
      types.length ? types.join('、') : '任意内容类型'].join('；') + '）'
  }
  return humanReadable ? '自定义表达式（请查看原配置）' : compileOperand(operand)
}

export function formatExpressionFormula(operand, options = {}) {
  const omitRootParentheses = options.omitRootParentheses !== false
  return formatNode(operand, omitRootParentheses && operand && operand.kind === OPERAND_KINDS.OPERATION, options.humanReadable === true)
}

/** 根据已有结构化条件描述，不解析或猜测任意QL，也不修改字段引用。 */
export function formatConditionDescription(condition, options = {}) {
  if (!condition) return '条件未完成'
  const left = condition.leftOperand || operandFromReferenceFields({ ...condition, varLabel: condition.varLabel || options.labels?.[condition.varCode] })
  const type = inferOperandType(left) || condition.varType || 'STRING'
  const operator = condition.operator || '=='
  const option = findConditionOperator(operator, type, left)
  const leftText = formatExpressionFormula(left, { humanReadable: true })
  if (operator === '*') return '任意（无条件）'
  if (!leftText) return '条件未完成：请选择比较字段'
  if (!option) return '自定义条件（请查看原表达式）'
  const prefix = options.includeLeft === false ? '' : leftText + ' '
  if (option.noValue) return prefix + option.label
  const right = condition.rightOperand || (condition.value !== undefined ? createLiteralOperand(condition.value, type) : null)
  if (!right) return '条件未完成：请填写比较值'
  try {
    if (isRangeOperator(operator)) {
      const bounds = requireRangeBounds(right.value, type, right.kind === OPERAND_KINDS.LITERAL ? 'CONST' : 'VAR')
      return prefix + option.label + ' ' + bounds[0] + ' ～ ' + bounds[1] + '（含边界）'
    }
    if (isTypedCollection(operator, right)) requireCollectionItems(right)
  } catch (error) { return '条件未完成：' + error.message }
  const value = formatExpressionFormula(right, { humanReadable: true })
  return value ? prefix + option.label + ' ' + value : '条件未完成：请填写比较值'
}

export function formatConditionTreeDescription(node, options = {}) {
  if (!node) return '条件未完成'
  if (node.type !== 'group') return formatConditionDescription(node, options)
  const children = Array.isArray(node.children) ? node.children : []
  if (!children.length) return '无条件（始终满足）'
  const operator = node.op || 'AND'
  if (!['AND', 'OR'].includes(operator)) return '自定义条件组（请查看原配置）'
  return children.map(child => {
    const text = formatConditionTreeDescription(child, options)
    return child?.type === 'group' && child.children?.length > 1 ? '(' + text + ')' : text
  }).join(operator === 'OR' ? ' 或 ' : ' 且 ')
}
