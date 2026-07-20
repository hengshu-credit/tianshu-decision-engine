import {
  buildRuleVersionDiff,
  pairBusinessItems,
  parseRuleVersionModel
} from '@/utils/ruleVersionDiff'

describe('ruleVersionDiff', () => {
  test('非法 JSON 返回可展示错误而不是抛出异常', () => {
    expect(parseRuleVersionModel('{bad json')).toEqual({
      model: {},
      raw: '{bad json',
      error: '版本内容无法解析'
    })
  })

  test('中间新增规则不会打乱后续相同规则的左右位置', () => {
    const left = [
      { ruleCode: 'R1', value: 1 },
      { ruleCode: 'R2', value: 2 }
    ]
    const right = [
      { ruleCode: 'R1', value: 1 },
      { ruleCode: 'NEW', value: 9 },
      { ruleCode: 'R2', value: 2 }
    ]

    const lanes = pairBusinessItems(left, right, { kind: 'rule' })

    expect(lanes.map(item => [
      item.status,
      item.left && item.left.ruleCode,
      item.right && item.right.ruleCode
    ])).toEqual([
      ['unchanged', 'R1', 'R1'],
      ['added', null, 'NEW'],
      ['unchanged', 'R2', 'R2']
    ])
  })

  test('无规则 ID 且条件字段相同时仍能识别中间插入行', () => {
    const createRule = (threshold, result) => ({
      conditionRoot: {
        type: 'group',
        op: 'AND',
        children: [{
          type: 'leaf',
          leftOperand: { kind: 'REFERENCE', refId: 1, refType: 'VARIABLE', code: 'age' },
          operator: '>=',
          rightOperand: { kind: 'LITERAL', value: threshold }
        }]
      },
      actions: [{
        targetOperand: { kind: 'REFERENCE', refId: 2, refType: 'VARIABLE', code: 'result' },
        valueOperand: { kind: 'LITERAL', value: result }
      }]
    })
    const left = { hitPolicy: 'FIRST', rules: [createRule(18, 'ADULT'), createRule(60, 'SENIOR')] }
    const right = { hitPolicy: 'FIRST', rules: [createRule(18, 'ADULT'), createRule(30, 'MIDDLE'), createRule(60, 'SENIOR')] }

    const result = buildRuleVersionDiff({ modelType: 'TABLE', leftModelJson: left, rightModelJson: right })
    const ruleLanes = result.sections.find(section => section.key === 'rules').lanes

    expect(ruleLanes.map(item => item.status)).toEqual(['unchanged', 'added', 'unchanged'])
  })

  test('交叉表中间新增行后原有单元格仍与原位置配对', () => {
    const left = {
      rowHeaders: ['成年', '老年'],
      colHeaders: ['结果'],
      cells: [['ADULT'], ['SENIOR']]
    }
    const right = {
      rowHeaders: ['成年', '中年', '老年'],
      colHeaders: ['结果'],
      cells: [['ADULT'], ['MIDDLE'], ['SENIOR']]
    }

    const result = buildRuleVersionDiff({ modelType: 'CROSS', leftModelJson: left, rightModelJson: right })
    const rowLanes = result.sections.find(section => section.key === 'rowHeaders').lanes
    const cellLanes = result.sections.find(section => section.key === 'cells').lanes

    expect(rowLanes.map(item => item.status)).toEqual(['unchanged', 'added', 'unchanged'])
    expect(cellLanes.map(item => item.status)).toEqual(['unchanged', 'added', 'unchanged'])
  })

  test('复杂交叉表中间新增分段后按业务坐标对齐单元格', () => {
    const dimension = segments => ({ varCode: 'age', segments: segments.map(value => ({ value })) })
    const left = {
      rowDimensions: [dimension(['成年', '老年'])],
      colDimensions: [dimension(['结果'])],
      cells: [['ADULT'], ['SENIOR']]
    }
    const right = {
      rowDimensions: [dimension(['成年', '中年', '老年'])],
      colDimensions: [dimension(['结果'])],
      cells: [['ADULT'], ['MIDDLE'], ['SENIOR']]
    }

    const result = buildRuleVersionDiff({ modelType: 'CROSS_ADV', leftModelJson: left, rightModelJson: right })
    const cellLanes = result.sections.find(section => section.key === 'cells').lanes

    expect(cellLanes.map(item => item.status)).toEqual(['unchanged', 'added', 'unchanged'])
  })

  test('简单评分卡展示设计器持久化的条件字段', () => {
    const model = {
      scoreItems: [{
        _varId: 1,
        _refType: 'VARIABLE',
        condVar: 'age',
        conditionLabel: '年龄',
        condVarType: 'NUMBER',
        condOperator: '>=',
        condValue: 18,
        score: 10,
        weight: 1
      }],
      thresholds: []
    }

    const result = buildRuleVersionDiff({ modelType: 'SCORE', leftModelJson: model, rightModelJson: model })
    const lane = result.sections.find(section => section.key === 'scoreItems').lanes[0]

    expect(lane.fields.map(item => [item.key, item.leftText])).toEqual(expect.arrayContaining([
      ['leftOperand', '年龄（VARIABLE #1）'],
      ['operator', '>='],
      ['rightOperand', '18']
    ]))
  })

  test('复杂评分卡同一评分规则的匿名条件生成唯一渲染 key', () => {
    const model = {
      dimensionGroups: [{
        dimensions: [{
          rules: [{
            conditions: [
              { operator: '==', rightOperand: { kind: 'LITERAL', value: 'A' } },
              { operator: '!=', rightOperand: { kind: 'LITERAL', value: 'B' } }
            ]
          }]
        }]
      }],
      thresholds: []
    }

    const result = buildRuleVersionDiff({ modelType: 'SCORE_ADV', leftModelJson: model, rightModelJson: model })
    const conditionLanes = result.sections.find(section => section.key === 'dimensionGroups')
      .lanes[0].children[0].children[0].children

    expect(conditionLanes.map(item => item.key)).toEqual([
      'condition:score.0.0.0.0',
      'condition:score.0.0.0.1'
    ])
  })

  test('复合动作块内的分支动作变化也能精确展示', () => {
    const createModel = value => ({
      rules: [{
        id: 'rule-1',
        actions: [{
          type: 'if-block',
          branches: [{
            type: 'if',
            leftOperand: { kind: 'REFERENCE', refId: 1, refType: 'VARIABLE', code: 'age' },
            operator: '>=',
            rightOperand: { kind: 'LITERAL', value: 18 },
            actions: [{
              type: 'assign',
              targetOperand: { kind: 'REFERENCE', refId: 2, refType: 'VARIABLE', code: 'result' },
              valueOperand: { kind: 'LITERAL', value }
            }]
          }]
        }]
      }]
    })

    const result = buildRuleVersionDiff({
      modelType: 'TABLE',
      leftModelJson: createModel('PASS'),
      rightModelJson: createModel('REVIEW')
    })
    const ruleLane = result.sections.find(section => section.key === 'rules').lanes[0]
    const branchLane = ruleLane.children[0].children[0]
    const assignmentLane = branchLane.children[0]

    expect(assignmentLane.fields.find(item => item.key === 'valueOperand')).toMatchObject({
      status: 'modified',
      leftText: 'PASS',
      rightText: 'REVIEW'
    })
    expect(result.summary.modified).toBeGreaterThan(0)
  })

  test('历史决策表的列定义与行值可还原为业务条件和动作', () => {
    const model = {
      conditions: [{ varCode: 'age', varLabel: '年龄', varType: 'NUMBER', _varId: 1, _refType: 'VARIABLE' }],
      actions: [{ varCode: 'result', varLabel: '结果', varType: 'STRING', _varId: 2, _refType: 'VARIABLE' }],
      rules: [{ conditions: [{ operator: '>=', value: 18 }], actions: [{ value: 'PASS' }] }]
    }

    const result = buildRuleVersionDiff({ modelType: 'TABLE', leftModelJson: model, rightModelJson: model })
    const ruleLane = result.sections.find(section => section.key === 'rules').lanes[0]
    const conditionLane = ruleLane.children[0].children[0]
    const actionLane = ruleLane.children[1]

    expect(conditionLane.fields.find(item => item.key === 'leftOperand').leftText).toBe('年龄（VARIABLE #1）')
    expect(actionLane.fields.find(item => item.key === 'targetOperand').leftText).toBe('结果（VARIABLE #2）')
    expect(actionLane.fields.find(item => item.key === 'valueOperand').leftText).toBe('PASS')
  })

  test('同一个稳定标识的字段变化保持在同一修改行', () => {
    const lanes = pairBusinessItems(
      [{ id: 'node-1', name: '年龄判断', value: 18 }],
      [{ id: 'node-1', name: '年龄判断', value: 21 }],
      { kind: 'node' }
    )

    expect(lanes).toHaveLength(1)
    expect(lanes[0]).toMatchObject({
      status: 'modified',
      left: { id: 'node-1', value: 18 },
      right: { id: 'node-1', value: 21 }
    })
  })

  test('同名但不同引用 ID 仍识别为业务修改', () => {
    const left = {
      hitPolicy: 'FIRST',
      rules: [{
        id: 'rule-1',
        conditionRoot: {
          id: 'condition-1',
          type: 'leaf',
          leftOperand: { kind: 'REFERENCE', label: '年龄', code: 'age', refId: 1, refType: 'VARIABLE' },
          operator: '>=',
          rightOperand: { kind: 'LITERAL', value: 18 }
        },
        actions: []
      }]
    }
    const right = JSON.parse(JSON.stringify(left))
    right.rules[0].conditionRoot.leftOperand.refId = 2

    const result = buildRuleVersionDiff({
      modelType: 'TABLE',
      leftModelJson: left,
      rightModelJson: right
    })

    expect(result.summary.modified).toBeGreaterThan(0)
    const conditionSection = result.sections.find(section => section.key === 'rules')
    expect(conditionSection.lanes[0].status).toBe('modified')
    const referenceField = conditionSection.lanes[0].children[0].fields.find(item => item.key === 'leftOperand')
    expect(referenceField.leftText).toBe('年龄（VARIABLE #1）')
    expect(referenceField.rightText).toBe('年龄（VARIABLE #2）')
  })

  test('流程图仅坐标和 logicflow 快照变化时业务配置一致', () => {
    const left = {
      nodes: [{ id: 'n1', type: 'start-event', name: '开始', x: 10, y: 20 }],
      edges: [],
      logicflow: { nodes: [{ id: 'n1', x: 10, y: 20 }], edges: [] }
    }
    const right = {
      nodes: [{ id: 'n1', type: 'start-event', name: '开始', x: 90, y: 80 }],
      edges: [],
      logicflow: { nodes: [{ id: 'n1', x: 90, y: 80 }], edges: [] }
    }

    const result = buildRuleVersionDiff({ modelType: 'FLOW', leftModelJson: left, rightModelJson: right })

    expect(result.summary.total).toBe(0)
  })

  test('QL 脚本文字相同但变量引用 ID 不同仍显示引用变化', () => {
    const result = buildRuleVersionDiff({
      modelType: 'SCRIPT',
      leftModelJson: { script: 'result = age;', scriptVarRefs: [{ refCode: 'age', varId: 1 }] },
      rightModelJson: { script: 'result = age;', scriptVarRefs: [{ refCode: 'age', varId: 2 }] }
    })

    expect(result.script.leftScript).toBe('result = age;')
    expect(result.script.rightScript).toBe('result = age;')
    expect(result.script.refLanes).toHaveLength(1)
    expect(result.script.refLanes[0].status).toBe('modified')
    expect(result.summary.modified).toBe(1)
  })

  test('QL 脚本的历史纯文本 modelJson 仍可直接做代码差异', () => {
    const result = buildRuleVersionDiff({
      modelType: 'SCRIPT',
      leftModelJson: 'return 1;',
      rightModelJson: 'return 2;'
    })

    expect(result.script.leftScript).toBe('return 1;')
    expect(result.script.rightScript).toBe('return 2;')
    expect(result.errors).toEqual({ left: '', right: '' })
    expect(result.summary.modified).toBe(1)
  })

  test.each([
    ['TABLE', { hitPolicy: 'FIRST', rules: [] }, '规则'],
    ['TREE', { nodes: [], edges: [] }, '节点'],
    ['FLOW', { nodes: [], edges: [] }, '流程节点'],
    ['RULE_SET', { executionMode: 'SERIAL', rules: [] }, '业务规则'],
    ['CROSS', { rowHeaders: [], colHeaders: [], cells: [] }, '结果矩阵'],
    ['SCORE', { scoreItems: [], thresholds: [] }, '评分项'],
    ['CROSS_ADV', { rowDimensions: [], colDimensions: [], cells: [] }, '维度'],
    ['SCORE_ADV', { dimensionGroups: [], thresholds: [] }, '维度组'],
    ['SCRIPT', { script: '', scriptVarRefs: [] }, null]
  ])('%s 能生成业务模型差异结构', (modelType, model, expectedTitle) => {
    const result = buildRuleVersionDiff({ modelType, leftModelJson: model, rightModelJson: model })

    expect(result.modelType).toBe(modelType)
    expect(result.summary).toEqual({ modified: 0, added: 0, removed: 0, total: 0 })
    if (expectedTitle) {
      expect(result.sections.map(section => section.title).join(' ')).toContain(expectedTitle)
    } else {
      expect(result.script).toBeTruthy()
    }
  })
})
