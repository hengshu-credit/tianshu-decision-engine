import Scorecard from '@/views/designer/Scorecard.vue'

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
