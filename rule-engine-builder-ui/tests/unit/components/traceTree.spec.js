import { shallowMount } from '@vue/test-utils'

const TraceTree = jest.requireActual('../../../src/components/common/TraceTree.vue').default

function mountTraceTree(propsData) {
  return shallowMount(TraceTree, {
    propsData,
    stubs: {
      'trace-node': true,
      'decision-tree-trace-node': true,
      'el-button': true,
      'el-badge': true
    }
  })
}

function assignNode(target, value, evaluated = true, rhsValue = value) {
  return {
    type: 'OPERATOR',
    token: '=',
    evaluated,
    value,
    children: [
      { type: 'VARIABLE', token: target, evaluated: true, value: rhsValue },
      { type: 'VALUE', evaluated: true, value: rhsValue }
    ]
  }
}

function variableNode(token, value, evaluated = true) {
  return { type: 'VARIABLE', token, value, evaluated, children: [] }
}

function valueNode(value, token = String(value), evaluated = true) {
  return { type: 'VALUE', token, value, evaluated, children: [] }
}

function compareNode(varCode, operator, actual, threshold, result) {
  return {
    type: 'OPERATOR',
    token: operator,
    evaluated: true,
    value: result,
    children: [
      variableNode(varCode, actual),
      valueNode(threshold)
    ]
  }
}

function fieldCompareNode(rootCode, field, operator, actual, threshold, result) {
  return {
    type: 'OPERATOR',
    token: operator,
    evaluated: true,
    value: result,
    children: [
      {
        type: 'FIELD',
        token: field,
        evaluated: true,
        value: actual,
        children: [variableNode(rootCode, { [field]: actual })]
      },
      valueNode(threshold)
    ]
  }
}

function ruleSetIfNode(condNode, hit, hitInfo) {
  return {
    type: 'IF',
    token: 'if',
    evaluated: true,
    value: hit,
    children: [
      {
        type: 'OPERATOR',
        token: '&&',
        evaluated: true,
        value: hit,
        children: [
          {
            type: 'OPERATOR',
            token: '!',
            evaluated: true,
            value: true,
            children: [variableNode('_ruleSetMatched', false)]
          },
          condNode
        ]
      },
      {
        type: 'BLOCK',
        token: '{',
        evaluated: hit,
        children: hit
          ? [
              assignNode('result', 1),
              assignNode('_ruleSetHit', hitInfo)
            ]
          : []
      }
    ]
  }
}

describe('TraceTree', () => {
  test('默认表达式追踪按顶层语句拆成纵向步骤', () => {
    const wrapper = mountTraceTree({
      traceInfo: JSON.stringify([
        assignNode('_ruleSetHits', []),
        assignNode('_ruleSetHitCount', 0)
      ]),
      varMap: {
        _ruleSetHits: '命中规则列表',
        _ruleSetHitCount: '命中规则数'
      }
    })

    expect(wrapper.vm.rootTreeItems).toHaveLength(2)
    expect(wrapper.vm.rootTreeItems[0].title).toBe('赋值：命中规则列表')
    expect(wrapper.vm.rootTreeItems[1].title).toBe('赋值：命中规则数')
    expect(wrapper.findAll('.trace-step')).toHaveLength(2)
  })

  test('最终结果优先展示执行输出而不是 trace 中的 $ref 占位', () => {
    const wrapper = mountTraceTree({
      traceInfo: JSON.stringify([
        assignNode('_result', { $ref: '$[0].children[1].value' }, true, 'PASS')
      ]),
      outputResult: JSON.stringify({ decision: 'PASS' })
    })

    expect(wrapper.vm.finalText).toBe('{"decision":"PASS"}')
    expect(wrapper.vm.finalText).not.toContain('$ref')
  })

  test('无执行输出时解析 trace 内部 $ref 指向的实际值', () => {
    const wrapper = mountTraceTree({
      traceInfo: JSON.stringify([
        assignNode('_result', { $ref: '$[0].children[1].value' }, true, 'PASS')
      ])
    })

    expect(wrapper.vm.finalText).toBe('PASS')
  })

  test('完整规则帧先还原绝对引用再传递表达式追踪', () => {
    const faces = { faces: [{ score: 0.98 }] }
    const wrapper = mountTraceTree({
      traceInfo: JSON.stringify([{
        schemaVersion: 2,
        traceKind: 'RULE',
        traceId: 'DFG000020260717120000000000000000001',
        ruleCode: 'FACE_IDENTITY',
        ruleName: '人脸活体与证件一致性核验',
        modelType: 'FLOW',
        status: 'SUCCESS',
        expressionTrace: [
          assignNode('facenox_detector_face_faces', faces),
          assignNode('buffalo_det_face_faces', {
            $ref: '$[0].expressionTrace[0].children[1].value'
          })
        ],
        children: []
      }])
    })

    expect(wrapper.vm.ruleTraceFrame.expressionTrace[1].value).toEqual(faces)
    expect(wrapper.find('trace-tree-stub').props('traceInfo')).not.toContain('$ref')
  })

  test.each(['TABLE', 'TREE', 'FLOW', 'RULE_SET', 'CROSS', 'SCORE', 'CROSS_ADV', 'SCORE_ADV', 'SCRIPT'])(
    '%s 追踪在渲染前清除 Fastjson 引用占位',
    modelType => {
      const wrapper = mountTraceTree({
        modelType,
        traceInfo: JSON.stringify([
          assignNode('source', { decision: 'PASS' }),
          assignNode('result', { $ref: '$[0].children[1].value' })
        ])
      })

      expect(JSON.stringify(wrapper.vm.rawTraceData)).not.toContain('$ref')
      expect(wrapper.text()).not.toContain('$ref')
      wrapper.destroy()
    }
  )

  test('决策流对象参数和结果显示为可读 JSON', () => {
    const faces = { faces: [{ score: 0.98 }] }
    const wrapper = mountTraceTree({
      modelType: 'FLOW',
      traceInfo: JSON.stringify([
        assignNode('facenox_detector_face_faces', faces),
        {
          type: 'FUNCTION',
          token: 'setRuntimeValue',
          evaluated: true,
          value: { $ref: '$[0].children[1].value' },
          children: [
            valueNode('facenox_detector_face_faces', '"facenox_detector_face_faces"'),
            {
              type: 'VARIABLE',
              token: 'facenox_detector_face_faces',
              evaluated: true,
              value: { $ref: '$[0].children[1].value' },
              children: []
            }
          ]
        },
        assignNode('_result', 'DONE')
      ])
    })
    const functionCard = wrapper.vm.flowCards.find(card => card.stepType === 'function')

    expect(functionCard.funcArgs[1].value).toBe(JSON.stringify(faces))
    expect(functionCard.resultDisplay).toBe(JSON.stringify(faces))
    expect(wrapper.vm._displayVal(faces)).toBe(JSON.stringify(faces))
    expect(wrapper.text()).not.toContain('[object Object]')
  })

  test('规则集追踪按规则独立成行并展示命中摘要', () => {
    const hitInfo = { ruleCode: 'R0001', ruleName: '黑名单规则', priority: 10, order: 1 }
    const wrapper = mountTraceTree({
      modelType: 'RULE_SET',
      definitionModel: {
        executionMode: 'SERIAL',
        rules: [
          {
            ruleCode: 'R0001',
            ruleName: '黑名单规则',
            priority: 10,
            enabled: true,
            conditionRoot: {
              type: 'group',
              op: 'AND',
              children: [
                {
                  type: 'leaf',
                  varCode: 'black_hit',
                  varLabel: '是否命中黑名单 black_hit',
                  varType: 'NUMBER',
                  operator: '==',
                  valueKind: 'CONST',
                  value: '1'
                }
              ]
            },
            actionData: [{ type: 'assign', target: 'result', value: '1' }]
          },
          {
            ruleCode: 'R0002',
            ruleName: '低分规则',
            priority: 1,
            enabled: true,
            conditionRoot: {
              type: 'group',
              op: 'AND',
              children: [
                {
                  type: 'leaf',
                  varCode: 'score_f1.score',
                  varLabel: '欺诈分F1/评分 score',
                  varType: 'DOUBLE',
                  operator: '<',
                  valueKind: 'CONST',
                  value: '250'
                }
              ]
            },
            actionData: [{ type: 'assign', target: 'result', value: '1' }]
          }
        ]
      },
      traceInfo: JSON.stringify([
        assignNode('_ruleSetHits', []),
        assignNode('_ruleSetHitCount', 0),
        assignNode('_ruleSetMatched', false),
        ruleSetIfNode(compareNode('black_hit', '==', 1, 1, true), true, hitInfo),
        ruleSetIfNode(compareNode('score_f1.score', '<', 300, 250, false), false, null),
        assignNode('_ruleSetResult', [hitInfo])
      ]),
      outputResult: JSON.stringify([hitInfo]),
      varMap: {
        result: '决策结果 result',
        black_hit: '是否命中黑名单 black_hit',
        'score_f1.score': '欺诈分F1/评分 score'
      }
    })

    expect(wrapper.vm.ruleSetRows).toHaveLength(2)
    expect(wrapper.vm.ruleSetRows[0]).toMatchObject({ ruleCode: 'R0001', status: 'hit', hit: true })
    expect(wrapper.vm.ruleSetRows[0].conditions[0]).toMatchObject({
      varCode: 'black_hit',
      varName: '是否命中黑名单',
      actualText: '1',
      thresholdText: '1',
      result: true
    })
    expect(wrapper.vm.ruleSetRows[1]).toMatchObject({ ruleCode: 'R0002', status: 'miss', hit: false })
    expect(wrapper.vm.ruleSetHitRows.map(row => row.ruleCode)).toEqual(['R0001'])
    expect(wrapper.findAll('.rs-table tbody tr')).toHaveLength(2)
    expect(wrapper.text()).toContain('R0001 黑名单规则')
    expect(wrapper.text()).toContain('实际值 1')
    expect(wrapper.text()).toContain('阈值 1')
  })

  test('规则集追踪展示统一操作数中的路径与引用阈值', () => {
    const wrapper = mountTraceTree({ inputParams: JSON.stringify({ request: { age: 20 }, adultAge: 18 }) })
    const leaf = {
      type: 'leaf',
      leftOperand: { kind: 'PATH', value: 'request.age', code: 'request.age', label: '年龄', valueType: 'NUMBER' },
      operator: '>=',
      rightOperand: { kind: 'REFERENCE', value: 'adultAge', code: 'adultAge', label: '成年年龄', valueType: 'NUMBER' }
    }

    const item = wrapper.vm._buildRuleSetConditionLeaf(leaf, null)

    expect(item).toMatchObject({ varCode: 'request.age', varName: '年龄', actualText: '20', thresholdText: '成年年龄 adultAge = 18', result: true })
  })

  test('规则集统一操作数动作展示目标和值', () => {
    const wrapper = mountTraceTree({})
    const actions = wrapper.vm._buildRuleSetActionItems([{
      type: 'assign',
      targetOperand: {
        kind: 'PATH', value: 'result', code: 'result', label: '决策结果', valueType: 'ENUM'
      },
      valueOperand: { kind: 'LITERAL', value: '1', valueType: 'NUMBER' }
    }])

    expect(actions).toEqual([{
      targetCode: 'result', targetName: '决策结果', valueText: '1'
    }])
  })

  test('规则集对象字段从 FIELD 追踪取值并以输出结果校准命中状态', () => {
    const hit1 = { ruleCode: 'R0001', ruleName: '年龄规则', priority: 1, order: 1 }
    const hit2 = { ruleCode: 'R0002', ruleName: '评分规则', priority: 1, order: 2 }
    const wrapper = mountTraceTree({
      modelType: 'RULE_SET',
      definitionModel: {
        executionMode: 'PARALLEL',
        rules: [
          {
            ruleCode: 'R0001',
            ruleName: '年龄规则',
            priority: 1,
            enabled: true,
            conditionRoot: {
              type: 'group',
              op: 'AND',
              children: [{
                type: 'leaf',
                operator: '<',
                leftOperand: { kind: 'REFERENCE', value: 'age', code: 'age', label: '年龄', valueType: 'NUMBER' },
                rightOperand: { kind: 'LITERAL', value: '18', valueType: 'NUMBER' }
              }]
            },
            actionData: []
          },
          {
            ruleCode: 'R0002',
            ruleName: '评分规则',
            priority: 1,
            enabled: true,
            conditionRoot: {
              type: 'group',
              op: 'AND',
              children: [{
                type: 'leaf',
                operator: '>=',
                leftOperand: { kind: 'REFERENCE', value: 'score_f1.score', code: 'score_f1.score', label: '欺诈分F1/评分', valueType: 'DOUBLE' },
                rightOperand: { kind: 'LITERAL', value: '0', valueType: 'DOUBLE' }
              }]
            },
            actionData: []
          }
        ]
      },
      traceInfo: JSON.stringify([
        ruleSetIfNode(compareNode('age', '<', 17, 18, true), true, hit1),
        ruleSetIfNode(fieldCompareNode('score_f1', 'score', '>=', 260, 0, true), true, hit2)
      ]),
      outputResult: JSON.stringify([hit2])
    })

    expect(wrapper.vm.ruleSetRows[0]).toMatchObject({ ruleCode: 'R0001', status: 'miss', hit: false })
    expect(wrapper.vm.ruleSetRows[1]).toMatchObject({ ruleCode: 'R0002', status: 'hit', hit: true })
    expect(wrapper.vm.ruleSetRows[1].conditions[0]).toMatchObject({
      varCode: 'score_f1.score', actualText: '260', result: true
    })
    expect(wrapper.vm.ruleSetHitRows.map(row => row.ruleCode)).toEqual(['R0002'])
  })

  test('共享会话追踪复用各子规则原有表达式追踪样式', () => {
    const childTrace = {
      schemaVersion: 2,
      traceKind: 'RULE',
      traceId: 'RSP000120260715101112345000000000001',
      ruleCode: 'CHILD_RULE',
      ruleName: '子规则集',
      modelType: 'RULE_SET',
      modelJson: JSON.stringify({
        executionMode: 'SERIAL',
        rules: [{ ruleCode: 'CREDIT_CHILD', ruleName: '授信额度子规则' }]
      }),
      status: 'SUCCESS',
      durationMs: 6,
      events: [],
      expressionTrace: [assignNode('CREDIT_AMOUNT', 3000)],
      children: []
    }
    const wrapper = mountTraceTree({
      traceInfo: JSON.stringify([{
        schemaVersion: 2,
        traceKind: 'RULE',
        traceId: 'DFP000120260715101112345000000000002',
        ruleCode: 'CREDIT_FLOW',
        ruleName: '授信决策流',
        modelType: 'FLOW',
        status: 'SUCCESS',
        durationMs: 18,
        events: [{
          type: 'MODULE_CALL',
          moduleType: 'EXTERNAL_API',
          resourceCode: 'creditProfile',
          traceId: 'APP000120260715101112345000000000003',
          status: 'SUCCESS',
          durationMs: 4
        }],
        expressionTrace: [assignNode('result', 101)],
        children: [childTrace]
      }])
    })

    expect(wrapper.vm.ruleTraceFrame.ruleCode).toBe('CREDIT_FLOW')
    expect(wrapper.find('.rule-trace-frame--root').exists()).toBe(false)
    expect(wrapper.find('.rule-trace-module').exists()).toBe(true)
    expect(wrapper.find('.rule-trace-children').exists()).toBe(true)
    expect(wrapper.text()).toContain('授信决策流')
    expect(wrapper.text()).toContain('外数 API')
    expect(wrapper.text()).toContain('creditProfile')
    expect(wrapper.findAll('trace-tree-stub')).toHaveLength(2)

    const childWrapper = mountTraceTree({ traceInfo: JSON.stringify(childTrace), nested: true })
    expect(childWrapper.vm.ruleExpressionModelType).toBe('RULE_SET')
    expect(childWrapper.find('trace-tree-stub').props('modelType')).toBe('RULE_SET')
    expect(childWrapper.find('trace-tree-stub').props('definitionModel')).toEqual({
      executionMode: 'SERIAL',
      rules: [{ ruleCode: 'CREDIT_CHILD', ruleName: '授信额度子规则' }]
    })
    childWrapper.destroy()
  })

  test('分流实验条件和随机分流复用现有条件树样式', () => {
    const childTrace = {
      schemaVersion: 2,
      traceKind: 'RULE',
      traceId: 'TBP000120260715101112345000000000011',
      ruleCode: 'CREDIT_TABLE',
      ruleName: '授信决策表',
      modelType: 'TABLE',
      status: 'SUCCESS',
      durationMs: 7,
      events: [],
      expressionTrace: [assignNode('result', 101)],
      children: []
    }
    const wrapper = mountTraceTree({
      traceInfo: JSON.stringify({
        schemaVersion: 2,
        traceKind: 'EXPERIMENT_GROUP',
        experimentTraceId: 'EXP000120260715101112345000000000012',
        childTraceId: childTrace.traceId,
        stage: 'TEST',
        routingTrace: [
          { type: 'ROUTING_START', stage: 'TEST', routingMode: 'RATIO' },
          { type: 'RANDOM_VALUE', value: 37 },
          { type: 'GROUP_SELECTED', groupCode: 'TEST_A', reason: '测试组比例分流命中' }
        ],
        ruleExecution: { type: 'RULE_EXECUTION', traceId: childTrace.traceId, trace: [childTrace] }
      })
    })

    expect(wrapper.vm.experimentTraceFrame.stage).toBe('TEST')
    expect(wrapper.find('.experiment-trace-frame').exists()).toBe(false)
    const routingTree = wrapper.find('decision-tree-trace-node-stub')
    expect(routingTree.exists()).toBe(true)
    expect(routingTree.props('node').label).toContain('随机值 37')
    expect(routingTree.props('node').children[0]).toMatchObject({ branchLabel: 'TEST_A', status: 'hit' })
    expect(wrapper.findAll('.experiment-route-step')).toHaveLength(0)
    expect(wrapper.text()).toContain(childTrace.traceId)
    expect(wrapper.findAll('trace-tree-stub')).toHaveLength(1)
  })

  test('交叉表追踪将结构化单元格显示为业务值而不是对象字符串', () => {
    const simple = mountTraceTree({
      modelType: 'CROSS',
      definitionModel: {
        rowHeaders: ['A'],
        colHeaders: ['B'],
        cells: [['']],
        cellOperands: [[{ kind: 'LITERAL', value: 101, valueType: 'INTEGER' }]]
      }
    })
    expect(simple.vm.traceCrossSimpleCellDisplay(0, 0)).toBe('101')
    simple.destroy()

    const advanced = mountTraceTree({
      modelType: 'CROSS_ADV',
      definitionModel: {
        rowDimensions: [{ segments: [{ operator: '==', value: 'A' }] }],
        colDimensions: [{ segments: [{ operator: '==', value: 'B' }] }],
        cells: [[{ kind: 'LITERAL', value: 1.147, valueType: 'DOUBLE' }]]
      }
    })
    expect(advanced.vm.traceAdvCellDisplay(0, 0)).toBe('1.147')
    advanced.destroy()
  })
})
