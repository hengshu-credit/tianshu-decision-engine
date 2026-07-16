import { createExpressionSessionId } from '@/utils/expressionSession'

describe('expression session id', () => {
  test('同一规则和选择器在组件生命周期内生成稳定 ID', () => {
    expect(createExpressionSessionId(7, 'picker-12')).toBe(createExpressionSessionId(7, 'picker-12'))
  })

  test('不同规则或选择器不会共用会话', () => {
    expect(createExpressionSessionId(7, 'picker-12')).not.toBe(createExpressionSessionId(7, 'picker-13'))
    expect(createExpressionSessionId(7, 'picker-12')).not.toBe(createExpressionSessionId(8, 'picker-12'))
  })
})
