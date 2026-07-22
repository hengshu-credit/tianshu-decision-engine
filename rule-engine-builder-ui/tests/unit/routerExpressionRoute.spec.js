vi.unmock('@/router')

import router from '@/router'

describe('表达式配置路由', () => {
  const layoutRoute = router.options.routes.find(route => route.path === '/')
  const childRoutes = layoutRoute.children

  test('注册 layout-main 内的独立表达式页面', () => {
    const route = childRoutes.find(item => item.name === 'ExpressionEditor')

    expect(route).toMatchObject({
      path: 'designer/expression/:ruleId/:sessionId',
      meta: { title: '配置表达式', keepAlive: true, menu: '/rule' }
    })
  })

  test('所有规则设计器都启用页面缓存', () => {
    const designerNames = [
      'DecisionTable', 'DecisionTree', 'DecisionFlow', 'RuleSet', 'CrossTable',
      'Scorecard', 'AdvancedCrossTable', 'AdvancedScorecard', 'ScriptEditor'
    ]

    designerNames.forEach(name => {
      expect(childRoutes.find(item => item.name === name).meta.keepAlive).toBe(true)
    })
  })
})
