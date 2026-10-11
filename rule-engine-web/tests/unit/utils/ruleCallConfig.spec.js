import {
  collectRuleCallBlocks,
  isRuleOutputMappingEnabled,
  normalizeRuleOption,
  repairLegacyRuleCallRefs,
  validateRuleCallBlock
} from '@/utils/ruleCallConfig'

describe('ruleCallConfig', () => {
  test('最新版输出校验使用已发布字段，不用另一个未发布草稿的字段', () => {
    const rule = { id: 8, status: 1, outputFields: [{ scriptName: 'draft_score' }], executionVersions: [
      { bindingId: '81', version: 1, available: true, outputFields: [{ scriptName: 'old_score' }] },
      { bindingId: '82', version: 2, available: true, outputFields: [{ scriptName: 'published_score' }] }
    ] }
    const call = { ruleId: 8, versionMode: 'LATEST', outputField: 'published_score', targetOperand: { kind: 'REFERENCE', refId: 9 } }
    expect(validateRuleCallBlock(call, { rules: [rule] })).toBe('')
    expect(validateRuleCallBlock({ ...call, outputField: 'draft_score' }, { rules: [rule] })).toContain('所选发布版本')
    expect(validateRuleCallBlock({ ...call, versionMode: 'FIXED', versionBindingId: '81', outputField: 'published_score' }, { rules: [rule] })).toContain('所选发布版本')
  })

  test('缺少可验证发布字段不能把草稿字段当成合法的输出映射', () => {
    const call = { ruleId: 8, outputField: 'draft_score', targetOperand: { kind: 'REFERENCE', refId: 9 } }
    const rule = { id: 8, status: 1, outputFields: [{ scriptName: 'draft_score' }] }
    expect(validateRuleCallBlock(call, { rules: [rule] })).toContain('发布字段')
    rule.executionVersions = [{ bindingId: '81', version: 1, available: false, outputFields: [] }]
    expect(validateRuleCallBlock(call, { rules: [rule] })).toContain('可验证')
  })
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

  test('配置输出字段时必须同时配置目标字段', () => {
    expect(validateRuleCallBlock({ ruleId: 8, outputField: 'score', targetOperand: null }, {
      rules,
      currentRuleId: 3
    })).toContain('必须配置目标字段')
  })

  test('仅配置目标字段时接收子规则整体结果', () => {
    expect(validateRuleCallBlock({
      ruleId: 8,
      outputField: '',
      targetOperand: { kind: 'REF', value: 'hit_ruleset' }
    }, { rules, currentRuleId: 3 })).toBe('')
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

  test('显式开启映射后必须选择目标字段', () => {
    expect(validateRuleCallBlock({
      ruleId: 8,
      enableOutputMapping: true,
      outputField: '',
      targetOperand: null
    }, { rules, currentRuleId: 3 })).toContain('必须配置目标字段')
  })

  test('拒绝调用当前规则自身', () => {
    expect(validateRuleCallBlock({ ruleId: 8 }, {
      rules,
      currentRuleId: 8
    })).toContain('不能调用当前规则自身')
  })
})
