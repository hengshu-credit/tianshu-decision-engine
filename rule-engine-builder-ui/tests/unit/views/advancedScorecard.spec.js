import AdvancedScorecard from '@/views/designer/AdvancedScorecard.vue'

describe('AdvancedScorecard', () => {
  test('将结果、维度、条件和等级结果统一转换为操作数', () => {
    const context = {
      model: {
        initialScore: 100,
        resultVar: { varCode: 'score', _varId: 1, _refType: 'VARIABLE' },
        dimensionGroups: [{
          weight: 1,
          dimensions: [{
            varCode: 'age', _varId: 2, _refType: 'VARIABLE', weight: 1,
            rules: [{ conditions: [{ varCode: 'age', _varId: 2, _refType: 'VARIABLE', operator: '>=', value: '18' }], score: 10 }]
          }]
        }],
        thresholds: [{ min: 0, max: 100, result: 'LOW' }]
      },
      $set(target, key, value) { target[key] = value }
    }

    AdvancedScorecard.methods.normalizeModel.call(context)

    const condition = context.model.dimensionGroups[0].dimensions[0].rules[0].conditions[0]
    expect(context.model.resultVar.operand).toMatchObject({ kind: 'REFERENCE', refId: 1 })
    expect(context.model.dimensionGroups[0].dimensions[0].operand).toMatchObject({ kind: 'REFERENCE', refId: 2 })
    expect(condition.leftOperand).toMatchObject({ kind: 'REFERENCE', refId: 2 })
    expect(condition.rightOperand).toMatchObject({ kind: 'LITERAL', value: '18' })
    expect(context.model.thresholds[0].resultOperand).toMatchObject({ kind: 'LITERAL', value: 'LOW' })
  })

  test('测试参数不会把引用操作数当作条件样例值', () => {
    const context = {
      model: {
        dimensionGroups: [{ dimensions: [{
          varCode: 'age',
          rules: [{ conditions: [{
            varCode: 'age',
            value: 'income',
            rightOperand: { kind: 'REFERENCE', code: 'income', refId: 2, refType: 'VARIABLE' }
          }] }]
        }] }]
      },
      projectRefs: [{ refCode: 'age', refType: 'VARIABLE', varType: 'NUMBER', varObj: { varSource: 'INPUT' } }]
    }

    expect(AdvancedScorecard.methods.buildTestParamsTemplate.call(context)).toEqual({ age: 0, income: '' })
  })

  test('条件按模型输出来源展示状态操作符并移除无用右值', () => {
    const condition = {
      leftOperand: {
        kind: 'REFERENCE', code: 'risk.score', valueType: 'DOUBLE', refId: 21,
        refType: 'MODEL_OUTPUT', sourceType: 'model'
      },
      operator: 'source_output_missing',
      rightOperand: { kind: 'LITERAL', value: '0', valueType: 'NUMBER' }
    }
    const context = {
      $set(target, key, value) { target[key] = value }
    }

    expect(AdvancedScorecard.methods.conditionOperatorGroups.call(context, condition).map(group => group.label)).toEqual(['值判断', '来源状态'])
    AdvancedScorecard.methods.onConditionOperatorChange.call(context, condition)

    expect(condition.rightOperand).toBeNull()
    expect(AdvancedScorecard.methods.conditionRequiresValue.call(context, condition)).toBe(false)
  })
})
