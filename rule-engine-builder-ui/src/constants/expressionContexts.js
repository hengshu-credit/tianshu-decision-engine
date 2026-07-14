import { OPERAND_KINDS, VALUE_OPERAND_KINDS, WRITE_OPERAND_KINDS } from '@/utils/operand'

export const EXPRESSION_CONTEXTS = Object.freeze({
  READ_EXPRESSION: Object.freeze({
    key: 'READ_EXPRESSION',
    allowedKinds: Object.freeze(VALUE_OPERAND_KINDS.slice()),
    expectedType: ''
  }),
  BOOLEAN_EXPRESSION: Object.freeze({
    key: 'BOOLEAN_EXPRESSION',
    allowedKinds: Object.freeze(VALUE_OPERAND_KINDS.slice()),
    expectedType: 'BOOLEAN'
  }),
  LIST_QUERY_VALUE: Object.freeze({
    key: 'LIST_QUERY_VALUE',
    allowedKinds: Object.freeze(VALUE_OPERAND_KINDS.filter(kind => kind !== OPERAND_KINDS.LIST_QUERY)),
    expectedType: ''
  }),
  WRITE_TARGET: Object.freeze({
    key: 'WRITE_TARGET',
    allowedKinds: Object.freeze(WRITE_OPERAND_KINDS.slice()),
    expectedType: ''
  }),
  SCRIPT_INSERT: Object.freeze({
    key: 'SCRIPT_INSERT',
    allowedKinds: Object.freeze(VALUE_OPERAND_KINDS.slice()),
    expectedType: ''
  })
})

export function getExpressionContext(contextKey) {
  const source = EXPRESSION_CONTEXTS[contextKey] || EXPRESSION_CONTEXTS.READ_EXPRESSION
  return {
    ...source,
    allowedKinds: source.allowedKinds.slice()
  }
}

export function isExpressionKindAllowed(contextKey, kind) {
  return getExpressionContext(contextKey).allowedKinds.includes(kind)
}
