import { createLiteralOperand, inferOperandType, operandFromReferenceFields } from './operand'
import { formatConditionDescription } from './expressionDisplay'

export const RANGE_BOUNDARIES = ['[)', '()', '[]', '(]']

export function dimensionType(dim) {
  return (
    inferOperandType(dim && dim.operand) || (dim && dim.varType) || 'STRING'
  )
}

export function segmentSummary(dim, segment) {
  const leftOperand = dim.operand || operandFromReferenceFields(dim)
  const right = (field, legacy) => segment[field] || createLiteralOperand(segment[legacy], dimensionType(dim))
  const describe = (operator, rightOperand) => formatConditionDescription({ leftOperand, operator, rightOperand }, { includeLeft: false })
  let summary
  if (segment.operator === 'range') {
    const min = right('minOperand', 'min'), max = right('maxOperand', 'max')
    const missing = operand => operand.kind === 'LITERAL' && (operand.value == null || String(operand.value).trim() === '')
    if (missing(min) || missing(max)) {
      summary = '条件未完成：请填写区间' + (missing(min) ? '下界' : '上界')
    } else {
      const boundary = RANGE_BOUNDARIES.includes(segment.rangeBoundary) ? segment.rangeBoundary : '[)'
      summary = describe(boundary.startsWith('[') ? '>=' : '>', min) + ' 且 ' + describe(boundary.endsWith(']') ? '<=' : '<', max)
    }
  } else {
    summary = describe(segment.operator, right('valueOperand', 'value'))
  }
  return segment.label ? `${segment.label}（${summary}）` : summary
}

export function cartesianProduct(dimensions) {
  if (!dimensions || dimensions.length === 0) return []
  let result = [[]]
  for (const dim of dimensions) {
    const segs = dim.segments || []
    if (segs.length === 0) return []
    const newResult = []
    for (const existing of result) {
      for (const seg of segs) {
        newResult.push([...existing, seg])
      }
    }
    result = newResult
  }
  return result
}

export function flattenCells(cells, resultType = 'STRING') {
  if (!Array.isArray(cells)) return []
  return cells.map((row) => {
    if (!Array.isArray(row)) return []
    return row.map((cell) => {
      if (Array.isArray(cell)) cell = cell[0]
      if (cell && cell.kind) return cell
      return createLiteralOperand(
        cell != null ? cell : '',
        resultType
      )
    })
  })
}
