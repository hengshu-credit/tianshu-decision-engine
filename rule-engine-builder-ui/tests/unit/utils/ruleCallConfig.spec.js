import {
  collectRuleCallBlocks,
  isRuleOutputMappingEnabled,
  normalizeRuleOption,
  repairLegacyRuleCallRefs,
  validateRuleCallBlock
} from '@/utils/ruleCallConfig'

describe('ruleCallConfig', () => {
  const rules = [
    { id: 8, ruleCode: 'score_card', ruleName: '评分卡', modelType: 'SCORE', status: 1 }
  ]

  test('规则元数据保持用户编码并归一化字段列表', () => {
    expect(normalizeRuleOption({
      id: 8,
      ruleCode: 'Score_Card',
      inputFieldsJson: '[{"scriptName":"CREDIT_AMOUNT"}]',
      outputFieldsJson: [{ scriptName: 'score' }]
    })).toMatchObject({
      id: 8,
      ruleCode: 'Score_Card',
      inputFields: [{ scriptName: 'CREDIT_AMOUNT' }],
      outputFields: [{ scriptName: 'score' }]
    })
  })

  test('递归收集嵌套动作中的规则调用', () => {
    const calls = collectRuleCallBlocks([{
      type: 'if-block',
      branches: [{ actions: [{ type: 'rule-call', ruleId: 8 }] }]
    }])

    expect(calls).toHaveLength(1)
    expect(calls[0].ruleId).toBe(8)
  })

  test('只有唯一编码匹配时才修复旧规则引用', () => {
    const model = { nodes: [{ actionData: [{ type: 'rule-call', ruleCode: 'score_card' }] }] }

    expect(repairLegacyRuleCallRefs(model, rules)).toBe(1)
    expect(model.nodes[0].actionData[0]).toMatchObject({
      ruleId: 8,
      ruleCode: 'score_card',
      ruleName: '评分卡',
      modelType: 'SCORE'
    })
  })

  test('单字段映射必须同时配置输出字段和目标字段', () => {
    expect(validateRuleCallBlock({ ruleId: 8, outputField: 'score', targetOperand: null }, {
      rules,
      currentRuleId: 3
    })).toContain('输出字段和目标字段必须同时配置')
  })

  test('显式关闭映射时保留字段但不参与映射校验', () => {
    expect(isRuleOutputMappingEnabled({
      enableOutputMapping: false,
      outputField: 'score',
      targetOperand: null
    })).toBe(false)
    expect(validateRuleCallBlock({
      ruleId: 8,
      enableOutputMapping: false,
      outputField: 'score',
      targetOperand: null
    }, { rules, currentRuleId: 3 })).toBe('')
  })

  test('旧配置按已有映射字段推断为开启', () => {
    expect(isRuleOutputMappingEnabled({ outputField: 'score' })).toBe(true)
  })

  test('显式开启映射后必须完整选择输出字段和目标字段', () => {
    expect(validateRuleCallBlock({
      ruleId: 8,
      enableOutputMapping: true,
      outputField: '',
      targetOperand: null
    }, { rules, currentRuleId: 3 })).toContain('输出字段和目标字段必须同时配置')
  })

  test('拒绝调用当前规则自身', () => {
    expect(validateRuleCallBlock({ ruleId: 8 }, {
      rules,
      currentRuleId: 8
    })).toContain('不能调用当前规则自身')
  })
})
