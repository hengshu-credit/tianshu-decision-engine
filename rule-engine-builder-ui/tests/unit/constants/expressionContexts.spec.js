import {
  EXPRESSION_CONTEXTS,
  getExpressionContext,
  isExpressionKindAllowed
} from '@/constants/expressionContexts'

describe('expressionContexts', () => {
  test('读取表达式允许全部组合节点', () => {
    const context = getExpressionContext('READ_EXPRESSION')
    expect(context.allowedKinds).toEqual(expect.arrayContaining([
      'LITERAL', 'PATH', 'REFERENCE', 'FUNCTION', 'OPERATION', 'ACCESS', 'CAST', 'ARRAY'
    ]))
  })

  test('写目标只允许路径和受管引用', () => {
    expect(EXPRESSION_CONTEXTS.WRITE_TARGET.allowedKinds).toEqual(['PATH', 'REFERENCE'])
    expect(isExpressionKindAllowed('WRITE_TARGET', 'REFERENCE')).toBe(true)
    expect(isExpressionKindAllowed('WRITE_TARGET', 'FUNCTION')).toBe(false)
  })

  test('名单配置是独立上下文，不混入普通读表达式', () => {
    expect(EXPRESSION_CONTEXTS.READ_EXPRESSION.allowedKinds).not.toContain('LIST_QUERY')
    expect(EXPRESSION_CONTEXTS.LIST_QUERY_CONFIG.allowedKinds).toEqual(['LIST_QUERY'])
    expect(EXPRESSION_CONTEXTS.LIST_QUERY_CONFIG.expectedType).toBe('BOOLEAN')
  })

  test('未知上下文回退为读取表达式并返回独立副本', () => {
    const first = getExpressionContext('UNKNOWN')
    first.allowedKinds.push('BROKEN')
    expect(getExpressionContext('UNKNOWN').allowedKinds).not.toContain('BROKEN')
  })
})
