import { compileOperand, OPERAND_KINDS } from '@/utils/operand'

function labelAndCode(label, code) {
  const normalizedLabel = String(label || '')
  const normalizedCode = String(code || '')
  if (normalizedLabel && normalizedLabel !== normalizedCode) return normalizedLabel + ' ' + normalizedCode
  return normalizedCode || normalizedLabel
}

function formatNode(operand, rootOperation) {
  if (!operand || !operand.kind) return ''
  if (operand.kind === OPERAND_KINDS.LITERAL) return compileOperand(operand)
  if (operand.kind === OPERAND_KINDS.PATH || operand.kind === OPERAND_KINDS.REFERENCE) {
    return labelAndCode(operand.label, operand.code || operand.value)
  }
  if (operand.kind === OPERAND_KINDS.FUNCTION) {
    const name = labelAndCode(operand.label, operand.functionCode)
    return name + '(' + (operand.args || []).map(item => formatNode(item, false)).join(', ') + ')'
  }
  if (operand.kind === OPERAND_KINDS.OPERATION) {
    const content = (operand.terms || []).map((term, index) => {
      const value = formatNode(term && term.operand, false)
      return index === 0 ? value : ((term && term.operator) || '') + ' ' + value
    }).join(' ')
    return rootOperation ? content : '(' + content + ')'
  }
  if (operand.kind === OPERAND_KINDS.ACCESS) {
    const code = String(operand.accessType || '').toUpperCase() === 'INDEX' ? 'arrGet' : 'objGet'
    const label = code === 'arrGet' ? '数组取值' : '字典取值'
    return `${label} ${code}(${formatNode(operand.target, false)}, ${formatNode(operand.accessor, false)})`
  }
  if (operand.kind === OPERAND_KINDS.CAST) {
    const script = compileOperand(operand)
    const index = script.indexOf('(')
    const code = index === -1 ? script : script.substring(0, index)
    return `类型转换 ${code}(${formatNode(operand.operand, false)})`
  }
  if (operand.kind === OPERAND_KINDS.ARRAY) {
    return '[' + (operand.items || []).map(item => formatNode(item, false)).join(', ') + ']'
  }
  if (operand.kind === OPERAND_KINDS.LIST_QUERY) return '名单查询 ' + compileOperand(operand)
  return compileOperand(operand)
}

export function formatExpressionFormula(operand, options = {}) {
  const omitRootParentheses = options.omitRootParentheses !== false
  return formatNode(operand, omitRootParentheses && operand && operand.kind === OPERAND_KINDS.OPERATION)
}
