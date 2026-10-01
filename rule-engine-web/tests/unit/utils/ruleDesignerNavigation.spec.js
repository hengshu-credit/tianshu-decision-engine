import {
  resolveDesignerRouteName,
  ruleDesignerLocation,
} from '@/utils/ruleDesignerNavigation'

describe('ruleDesignerNavigation', () => {
  test('为九类规则生成命名路由，避免错误兜底到决策表', () => {
    expect(ruleDesignerLocation({ id: 12, modelType: 'SCRIPT' })).toEqual({
      name: 'ScriptEditor',
      params: { id: '12' },
    })
    expect(resolveDesignerRouteName('RULE_SET')).toBe('RuleSet')
  })

  test('兼容已有数据中的历史模型类型别名', () => {
    expect(resolveDesignerRouteName('DECISION_TABLE')).toBe('DecisionTable')
    expect(resolveDesignerRouteName('DECISION_TREE')).toBe('DecisionTree')
    expect(resolveDesignerRouteName('DECISION_FLOW')).toBe('DecisionFlow')
  })

  test('未知模型类型返回空入口而不是打开错误设计器', () => {
    expect(ruleDesignerLocation({ id: 12, modelType: 'UNKNOWN' })).toBeNull()
  })

  test('项目关联行优先使用规则定义 ID 而不是关联记录 ID', () => {
    expect(
      ruleDesignerLocation({ id: 12, definitionId: 98, modelType: 'TABLE' })
    ).toEqual({
      name: 'DecisionTable',
      params: { id: '98' },
    })
  })
})
