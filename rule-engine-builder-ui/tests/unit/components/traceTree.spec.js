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
})
