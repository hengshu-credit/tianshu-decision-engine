import AdvancedCrossTable from '@/views/designer/AdvancedCrossTable.vue'

describe('AdvancedCrossTable', () => {
  test('将维度、分段和结果字段统一转换为操作数', () => {
    const context = {
      model: {
        rowDimensions: [{ varCode: 'age', _varId: 1, _refType: 'VARIABLE', segments: [{ operator: '==', value: '18' }] }],
        colDimensions: [],
        resultVar: { varCode: 'result', _varId: 2, _refType: 'VARIABLE' }
      },
      $set(target, key, value) { target[key] = value }
    }

    AdvancedCrossTable.methods.normalizeModel.call(context)

    expect(context.model.rowDimensions[0].operand).toMatchObject({ kind: 'REFERENCE', refId: 1 })
    expect(context.model.rowDimensions[0].segments[0].valueOperand).toMatchObject({ kind: 'LITERAL', value: '18' })
    expect(context.model.resultVar.operand).toMatchObject({ kind: 'REFERENCE', refId: 2 })
  })

  test('规范化区间边界并兼容旧模型', () => {
    const context = {
      model: {
        rowDimensions: [{ segments: [
          { operator: 'range', min: '0', max: '100' },
          { operator: 'range', rangeBoundary: 'invalid' },
          { operator: 'range', rangeBoundary: '()' },
          { operator: 'range', rangeBoundary: '[]' },
          { operator: 'range', rangeBoundary: '(]' }
        ] }],
        colDimensions: [],
        resultVar: {}
      },
      $set(target, key, value) { target[key] = value }
    }

    AdvancedCrossTable.methods.normalizeModel.call(context)

    expect(context.model.rowDimensions[0].segments.map(segment => segment.rangeBoundary)).toEqual([
      '[)', '[)', '()', '[]', '(]'
    ])
  })

  test('新增维度和分段默认使用左闭右开边界', () => {
    const context = {
      model: { rowDimensions: [], colDimensions: [] }
    }

    AdvancedCrossTable.methods.addDimension.call(context, 'row')
    AdvancedCrossTable.methods.addSegment.call(context, context.model.rowDimensions[0])

    expect(context.model.rowDimensions[0].segments.map(segment => segment.rangeBoundary)).toEqual(['[)', '[)'])
  })

  test('切换为区间操作符时补充合法默认边界', () => {
    const segment = { operator: 'range', rangeBoundary: 'invalid' }

    AdvancedCrossTable.methods.onSegmentOperatorChange.call({}, segment)

    expect(segment.rangeBoundary).toBe('[)')
  })

  test('保存模型时保留区间边界', () => {
    const context = {
      model: {
        rowDimensions: [{ segments: [{ operator: 'range', rangeBoundary: '()' }] }],
        colDimensions: [],
        resultVar: {}
      },
      cellData: []
    }

    const saved = AdvancedCrossTable.methods.buildSaveModel.call(context)

    expect(saved.rowDimensions[0].segments[0].rangeBoundary).toBe('()')
  })

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

  test('测试参数不会把路径操作数当作维度样例值', () => {
    const context = {
      model: {
        rowDimensions: [{
          varCode: 'age',
          segments: [{ value: 'request.limit', valueOperand: { kind: 'PATH', value: 'request.limit' } }]
        }],
        colDimensions: []
      },
      projectRefs: [{ refCode: 'age', refType: 'VARIABLE', varType: 'NUMBER', varObj: { varSource: 'INPUT' } }]
    }

    expect(AdvancedCrossTable.methods.buildTestParamsTemplate.call(context)).toEqual({
      age: 0,
      request: { limit: '' }
    })
  })
})
