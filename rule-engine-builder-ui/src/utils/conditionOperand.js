import { compileConditionExpression } from '@/constants/conditionOperators'
import { compileListQueryOperand, compileOperand, OPERAND_KINDS } from '@/utils/operand'

export function compileConditionOperands(leftOperand, operator, rightOperand) {
  const left = compileOperand(leftOperand)
  if (!left) return 'true'
  const op = operator || '=='
  if ((op === 'in_list' || op === 'not_in_list') && rightOperand && rightOperand.kind === OPERAND_KINDS.LIST_QUERY) {
    const expression = compileListQueryOperand(rightOperand, [left])
    return op === 'not_in_list' ? '!' + expression : expression
  }
  const literal = rightOperand && rightOperand.kind === OPERAND_KINDS.LITERAL
  const right = literal ? rightOperand.value : compileOperand(rightOperand)
  return compileConditionExpression(left, leftOperand && leftOperand.valueType, op, right, literal ? 'CONST' : 'VAR')
}
