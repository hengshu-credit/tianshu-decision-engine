import { createExpressionSessionId, createExpressionSessionTitle } from '@/utils/expressionSession'

describe('expression session id', () => {
  test('同一规则和选择器在组件生命周期内生成稳定 ID', () => {
    expect(createExpressionSessionId(7, 'picker-12')).toBe(createExpressionSessionId(7, 'picker-12'))
  })

  test('不同规则或选择器不会共用会话', () => {
    expect(createExpressionSessionId(7, 'picker-12')).not.toBe(createExpressionSessionId(7, 'picker-13'))
    expect(createExpressionSessionId(7, 'picker-12')).not.toBe(createExpressionSessionId(8, 'picker-12'))
  })

  test('使用设计器和输入位置生成业务可读标题', () => {
    expect(createExpressionSessionTitle('决策表设计器', '配置表达式', '选择右操作数...'))
      .toBe('决策表 · 右操作数')
    expect(createExpressionSessionTitle('决策流设计器', '配置节点条件', '选择字段'))
      .toBe('决策流 · 节点条件')
  })

  test('标题信息缺失时回退为配置表达式', () => {
    expect(createExpressionSessionTitle('', '配置表达式', '')).toBe('配置表达式')
  })
})
