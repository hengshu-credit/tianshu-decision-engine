import { isValidDecimalText } from '@/utils/conditionRange'
import { inferOperandType } from '@/utils/operand'

export class ConditionCollectionError extends Error {
  constructor(message) { super(message); this.name = 'ConditionCollectionError' }
}

export function isCollectionOperator(operator) { return operator === 'in' || operator === 'not_in' }

export function isTypedCollection(operator, operand) {
  return isCollectionOperator(operator) && operand?.kind === 'ARRAY'
}

export function collectionMemberType(item) {
  if (item?.kind === 'CAST' && item.targetType === 'NUMBER' && item.operand?.kind === 'LITERAL' && item.operand.valueType === 'STRING') return 'NUMBER'
  if (item?.kind !== 'LITERAL') return 'ORIGINAL'
  const type = String(item.valueType || 'STRING').toUpperCase()
  if (type === 'STRING' || type === 'ENUM') return 'STRING'
  if (type === 'BOOLEAN' || type === 'BOOL') return 'BOOLEAN'
  return 'ORIGINAL'
}

export function newCollectionMember(type, value = '') {
  if (type === 'NUMBER') return { kind: 'CAST', targetType: 'NUMBER', operand: { kind: 'LITERAL', valueType: 'STRING', value: String(value) } }
  return { kind: 'LITERAL', valueType: type, value: type === 'BOOLEAN' ? value === true || value === 'true' : String(value) }
}

export function requireCollectionItems(operand) {
  const items = operand?.items
  if (!Array.isArray(items) || !items.length) throw new ConditionCollectionError('集合至少需要填写一项')
  items.forEach((item, index) => {
    if (!item?.kind) throw new ConditionCollectionError(`集合第 ${index + 1} 项内容不能为空`)
    if (collectionMemberType(item) === 'NUMBER' && !isValidDecimalText(item.operand.value)) {
      throw new ConditionCollectionError(`集合第 ${index + 1} 项必须填写有效数字`)
    }
    if (item.kind === 'LITERAL' && inferOperandType(item) === 'NUMBER' && !isValidDecimalText(item.value)) {
      throw new ConditionCollectionError(`集合第 ${index + 1} 项必须填写有效数字`)
    }
  })
  return items
}
