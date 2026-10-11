import Scorecard from '@/views/designer/Scorecard.vue'

test('结构化评分项序列化不再生成可能被误识别为字段的legacy condition，旧路径保持', () => {
  const item = { leftOperand: { kind: 'REFERENCE', refId: 37, refType: 'VARIABLE', code: 'age', valueType: 'NUMBER' },
    rightOperand: { kind: 'LITERAL', valueType: 'NUMBER', value: '18,60' }, condVar: 'age', condOperator: 'between', condValue: '18,60', condition: 'age between 18,60' }
  const legacy = { condVar: 'legacyAmount', condOperator: '>=', condValue: '10', condVarType: 'NUMBER' }
  const buildCondition = vi.fn(Scorecard.methods.buildCondition)
  const result = JSON.parse(Scorecard.methods.serializeDesignerDraft.call({ model: { scoreItems: [item, legacy] }, buildCondition }))
  expect(result.scoreItems[0].condition).toBeUndefined()
  expect(result.scoreItems[0].leftOperand.refId).toBe(37)
  expect(result.scoreItems[0].rightOperand.value).toBe('18,60')
  expect(item.condition).toBe('age between 18,60')
  expect(result.scoreItems[1].condition).toBe('legacyAmount >= 10')
  expect(buildCondition).toHaveBeenCalledTimes(1)
})

describe('Scorecard condition operators', () => {
  test('数据库变量按数值类型和来源状态提供操作符，并在状态判断时清除右值', () => {
    const item = {
      leftOperand: {
        kind: 'REFERENCE', code: 'dbAmount', valueType: 'DOUBLE', refId: 31,
        refType: 'VARIABLE', varSource: 'DB', sourceType: 'variable'
      },
      condOperator: 'source_no_data',
      rightOperand: { kind: 'LITERAL', value: '100', valueType: 'NUMBER' }
    }
    const context = {
      $set(target, key, value) { target[key] = value }
    }

    expect(Scorecard.methods.conditionOperatorGroups.call(context, item)[0].options.map(option => option.value)).toContain('between')
    expect(Scorecard.methods.conditionOperatorGroups.call(context, item)[1].options.map(option => option.value)).toContain('source_no_data')
    Scorecard.methods.onConditionOperatorChange.call(context, item)

    expect(item.rightOperand).toBeNull()
    expect(Scorecard.methods.conditionRequiresValue.call(context, item)).toBe(false)
  })
})
