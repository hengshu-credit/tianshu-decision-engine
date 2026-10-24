import { getConditionOperatorOptions, compileConditionExpression, normalizeVarType } from '@/constants/conditionOperators'

export const NATIVE_OPERATORS = ['+', '-', '*', '/', '%', '==', '!=', '>', '>=', '<', '<=', '&&', '||']
const excluded = new Set(['*', 'in_list', 'not_in_list', 'between', 'not_between', 'in', 'not_in'])
const predicates = new Map(['STRING', 'NUMBER', 'BOOLEAN', 'LIST', 'MAP', 'OBJECT', 'DATE'].flatMap(type => getConditionOperatorOptions(type)).filter(item => !excluded.has(item.value)).map(item => [item.value, item]))
export const CONTENT_OPERATORS = [...new Set([...NATIVE_OPERATORS, ...predicates.keys()])]
export const noRightOperand = operator => !!predicates.get(operator)?.noValue
export const predicateOperator = operator => predicates.has(operator) && !NATIVE_OPERATORS.includes(operator)
export const operationLabel = operator => predicates.get(operator)?.label || ({ '+': '加 / 拼接', '-': '减', '*': '乘', '/': '除', '%': '取余', '&&': '并且', '||': '或者' })[operator] || operator
export function operationOptions(type) {
  const normalized = type ? normalizeVarType(type) : ''
  const arithmetic = !normalized ? NATIVE_OPERATORS : normalized === 'NUMBER' ? ['+', '-', '*', '/', '%'] : normalized === 'STRING' ? ['+'] : normalized === 'BOOLEAN' ? ['&&', '||'] : []
  const conditions = getConditionOperatorOptions(normalized === 'OBJECT' ? 'MAP' : normalized).filter(item => !excluded.has(item.value))
  return [...arithmetic.map(value => ({ value, label: operationLabel(value) })), ...conditions.filter(item => !arithmetic.includes(item.value))]
}
export function operationRightType(operator, leftType) {
  if (['contains_any', 'contains_all', 'in_array', 'not_in_array'].includes(operator)) return 'LIST'
  return predicates.get(operator)?.rightValueType || (['has_key', 'not_has_key', 'regex_match', 'not_regex_match'].includes(operator) ? 'STRING'
    : ['contains', 'not_contains', 'has_value', 'not_has_value'].includes(operator) && ['LIST', 'MAP', 'OBJECT'].includes(normalizeVarType(leftType)) ? '' : leftType)
}
export function operationPrecedence(operator) {
  if (noRightOperand(operator)) return 7
  if (['*', '/', '%'].includes(operator)) return 6
  if (['+', '-'].includes(operator)) return 5
  if (['>', '>=', '<', '<='].includes(operator) || predicateOperator(operator)) return 4
  if (['==', '!='].includes(operator)) return 3
  if (operator === '&&') return 2
  if (operator === '||') return 1
  return -1
}
export function compileContentOperation(terms, compile) {
  if (!terms.some(term => predicateOperator(term.operator))) return '(' + terms.map((term, i) => (i ? term.operator + ' ' : '') + compile(term.operand)).join(' ') + ')'
  const values = [], operators = []
  const apply = () => {
    const op = operators.pop(), right = values.pop(), left = values.pop()
    let expression
    if (op === 'contains_any' || op === 'contains_all') expression = `${op === 'contains_any' ? 'containsAnyValue' : 'containsAllValues'}(${left}, ${right})`
    else if (predicateOperator(op)) {
      expression = compileConditionExpression(left, '', op, right, 'VAR')
      if (op.startsWith('not_') || op === 'array_element_not_contains') expression = `(!isBlank(${left}) && ${expression})`
    } else expression = `(${left} ${op} ${right})`
    values.push(expression)
  }
  terms.forEach((term, i) => {
    if (!i) { values.push(compile(term.operand)); return }
    if (noRightOperand(term.operator)) { values.push(compileConditionExpression(values.pop(), '', term.operator, null, 'VAR')); return }
    while (operators.length && operationPrecedence(operators.at(-1)) >= operationPrecedence(term.operator)) apply()
    operators.push(term.operator); values.push(compile(term.operand))
  })
  while (operators.length) apply()
  return '(' + values[0] + ')'
}
