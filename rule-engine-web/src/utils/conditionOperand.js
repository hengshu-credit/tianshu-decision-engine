import {
  compileConditionExpression,
  compileSourceStatusExpression,
  isSourceStatusOperator
} from '@/constants/conditionOperators'
import { compileListQueryOperand, compileOperand, inferOperandType, OPERAND_KINDS } from '@/utils/operand'
import { isRangeOperator } from '@/utils/conditionRange'
import { ConditionCollectionError, isTypedCollection, requireCollectionItems } from '@/utils/conditionCollection'

export function compileConditionOperands(leftOperand, operator, rightOperand) {
  if (isSourceStatusOperator(operator)) {
    return compileSourceStatusExpression(leftOperand, operator)
  }
  const collection = isTypedCollection(operator, rightOperand)
  if (collection) requireCollectionItems(rightOperand)
  const left = compileOperand(leftOperand)
  if (!left && collection) throw new ConditionCollectionError('集合判断必须选择比较字段')
  if (!left && !isRangeOperator(operator)) return 'true'
  const op = operator || '=='
  if (collection) {
    const predicate = left + ' in ' + compileOperand(rightOperand)
    return op === 'not_in' ? '(!isBlank(' + left + ') && !(' + predicate + '))' : predicate
  }
  if ((op === 'in_list' || op === 'not_in_list') && rightOperand && rightOperand.kind === OPERAND_KINDS.LIST_QUERY) {
    const expression = compileListQueryOperand(rightOperand, [left])
    return op === 'not_in_list' ? '!' + expression : expression
  }
  const literal = rightOperand && rightOperand.kind === OPERAND_KINDS.LITERAL
  const right = literal ? rightOperand.value : compileOperand(rightOperand)
  return compileConditionExpression(left, inferOperandType(leftOperand), op, right, literal || !rightOperand ? 'CONST' : 'VAR')
}
