import AdvancedCrossTable from '@/views/designer/AdvancedCrossTable.vue'

describe('AdvancedCrossTable', () => {
  test('测试参数仅保留模型输出所需的底层字段', () => {
    const context = {
      model: {
        rowDimensions: [{
          varCode: 'score_f1.score',
          segments: [{ value: '350' }]
        }],
        colDimensions: [{
          varCode: 'age',
          segments: [{ value: '55' }]
        }]
      },
      projectRefs: [{
        refCode: 'score_f1.score',
        refType: 'MODEL_OUTPUT',
        modelCode: 'score_f1',
        varType: 'DOUBLE',
        varObj: {},
        modelInputFields: [
          { scriptName: 'HYBASE_X115', fieldType: 'DOUBLE' },
          { scriptName: 'HYDK_X760', fieldType: 'DOUBLE' }
        ]
      }, {
        refCode: 'age',
        refType: 'VARIABLE',
        varType: 'NUMBER',
        varObj: { varSource: 'INPUT' }
      }]
    }

    expect(AdvancedCrossTable.methods.buildTestParamsTemplate.call(context)).toEqual({
      score_f1_fields: {
        HYBASE_X115: 0,
        HYDK_X760: 0
      },
      age: 55
    })
  })
})
