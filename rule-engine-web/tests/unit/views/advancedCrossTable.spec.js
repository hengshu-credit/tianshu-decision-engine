import AdvancedCrossTable from '@/views/designer/AdvancedCrossTable.vue'

describe('AdvancedCrossTable', () => {
  function matrixContext() {
    const context = {
      ...AdvancedCrossTable.data(),
      model: { rowDimensions: [{ segments: [{ value: 'A' }, { value: 'B' }, { value: 'C' }] }], colDimensions: [{ segments: [{ value: 'X' }, { value: 'Y' }] }], resultVar: { varType: 'NUMBER' } },
      cellData: [['AX', 'AY'], ['BX', 'BY'], ['CX', 'CY']].map(row => row.map(value => ({ kind: 'LITERAL', value, valueType: 'STRING' }))),
      $confirm: vi.fn().mockResolvedValue(true),
    }
    for (const [name, method] of Object.entries(AdvancedCrossTable.methods)) context[name] = method.bind(context)
    for (const name of ['rowCombinations', 'colCombinations', 'totalRowCount', 'totalColCount']) {
      Object.defineProperty(context, name, { get: () => AdvancedCrossTable.computed[name].call(context) })
    }
    context.syncCellData(true)
    return context
  }

  test('删除中间分段后结果仍跟随原条件组合，不按旧下标错配', () => {
    const context = matrixContext()
    context.model.rowDimensions[0].segments.splice(1, 1)
    context.syncCellData()
    expect(context.cellData.map(row => row.map(cell => cell.value))).toEqual([['AX', 'AY'], ['CX', 'CY']])
  })

  test('分段重排和插入保持原组合结果，新组合留空', () => {
    const context = matrixContext()
    context.model.rowDimensions[0].segments.reverse()
    context.model.colDimensions[0].segments.splice(1, 0, { value: 'NEW' })
    context.syncCellData()
    expect(context.cellData.map(row => row.map(cell => cell.value))).toEqual([['CX', '', 'CY'], ['BX', '', 'BY'], ['AX', '', 'AY']])
  })

  test('删除有结果的分段可以取消，确认后只移除对应组合', async () => {
    const context = matrixContext()
    const dimension = context.model.rowDimensions[0]
    context.$confirm.mockRejectedValueOnce('cancel')
    await context.removeSegment(dimension, 1)
    expect(dimension.segments.map(segment => segment.value)).toEqual(['A', 'B', 'C'])
    await context.removeSegment(dimension, 1)
    context.syncCellData()
    expect(context.cellData.map(row => row.map(cell => cell.value))).toEqual([['AX', 'AY'], ['CX', 'CY']])
    expect(context.$confirm).toHaveBeenLastCalledWith(expect.stringContaining('2'), expect.any(String), expect.any(Object))
  })

  test('自动同步枚举字段元信息不能重建业务分段或改变单元格含义', () => {
    const segments = [
      { label: '仅重点客户', operator: '==', value: 'VIP', valueOperand: { kind: 'LITERAL', value: 'VIP', valueType: 'STRING' } },
      { label: '其余客户', operator: '!=', value: 'VIP', valueOperand: { kind: 'LITERAL', value: 'VIP', valueType: 'STRING' } },
    ]
    const dimension = {
      varCode: 'customerType', varType: 'ENUM', _varId: 7, _refType: 'VARIABLE', segments,
      operand: { kind: 'REFERENCE', refId: 7, refType: 'VARIABLE', code: 'customerType', label: '旧名称', valueType: 'ENUM' },
    }
    const context = {
      model: { resultVar: {}, rowDimensions: [dimension], colDimensions: [] },
      cellData: [[{ kind: 'LITERAL', value: '90', valueType: 'NUMBER' }]],
      varPickerOptions: [{ id: 7, refType: 'VARIABLE', varCode: 'customerType', varLabel: '客户类型', varType: 'ENUM' }],
      getVarOptions: () => [{ label: '普通', value: 'NORMAL' }, { label: '重点', value: 'VIP' }],
    }
    context.setDimensionOperand = AdvancedCrossTable.methods.setDimensionOperand.bind(context)
    const originalCells = structuredClone(context.cellData)

    AdvancedCrossTable.methods.syncAllOperands.call(context)

    expect(dimension.operand.label).toBe('客户类型')
    expect(dimension.segments).toEqual(segments)
    expect(dimension.segments).toBe(segments)
    expect(context.cellData).toEqual(originalCells)
  })

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

  test('分段按数据对象来源展示字段状态操作符并清除值', () => {
    const dim = {
      operand: {
        kind: 'REFERENCE', code: 'request.age', valueType: 'NUMBER', refId: 11,
        refType: 'DATA_OBJECT', sourceType: 'dataObject'
      },
      varType: 'NUMBER'
    }
    const segment = {
      operator: 'source_field_missing',
      valueOperand: { kind: 'LITERAL', value: '18', valueType: 'NUMBER' }
    }
    const context = {
      $set(target, key, value) { target[key] = value }
    }

    expect(AdvancedCrossTable.methods.segmentOperatorGroups.call(context, dim)[1].options.map(item => item.value)).toContain('source_field_missing')
    AdvancedCrossTable.methods.onSegmentOperatorChange.call(context, segment, dim)

    expect(segment.valueOperand).toBeNull()
    expect(AdvancedCrossTable.methods.segmentRequiresValue.call(context, segment, dim)).toBe(false)
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
