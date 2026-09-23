import { h } from 'vue'
import { shallowMount } from '@test-utils'

const TraceTree = (await vi.importActual('../../../src/components/common/TraceTree.vue')).default

function mountTraceTree(propsData) {
  return shallowMount(TraceTree, {
    props: propsData,
    stubs: {
      'trace-node': true,
      'decision-tree-trace-node': true,
      'rule-set-condition-trace-node': {
        name: 'RuleSetConditionTraceNode',
        props: ['node'],
        render: () => h('div', { class: 'rule-set-condition-trace-node-stub' })
      },
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

function compareNode(varCode, operator, actual, threshold, result, evaluated = true) {
  return {
    type: 'OPERATOR',
    token: operator,
    evaluated,
    value: result,
    children: [
      variableNode(varCode, actual, evaluated),
      valueNode(threshold, String(threshold), evaluated)
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

function logicalNode(operator, children, result, evaluated = true) {
  return {
    type: 'OPERATOR',
    token: operator,
    evaluated,
    value: result,
    children
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

function tableCondition(varCode, operator, value) {
  return {
    type: 'group',
    op: 'AND',
    children: [{
      type: 'leaf',
      operator,
      leftOperand: {
        kind: 'REFERENCE', code: varCode, value: varCode, valueType: 'NUMBER'
      },
      rightOperand: { kind: 'LITERAL', value, valueType: 'NUMBER' }
    }]
  }
}

function tableAction(varCode, value) {
  return {
    targetOperand: {
      kind: 'REFERENCE', code: varCode, value: varCode, valueType: 'STRING'
    },
    valueOperand: { kind: 'LITERAL', value, valueType: 'STRING' }
  }
}

function tableIfNode(condNode, actionValue, elseIf, elseEvaluated = true) {
  const hit = condNode && condNode.evaluated !== false && condNode.value === true
  const children = [
    condNode,
    {
      type: 'BLOCK',
      evaluated: hit,
      children: hit ? [assignNode('decision', actionValue)] : []
    }
  ]
  if (elseIf) {
    children.push({
      type: 'BLOCK',
      evaluated: elseEvaluated,
      children: [elseIf]
    })
  }
  return { type: 'IF', token: 'if', evaluated: true, value: hit, children }
}

function skippedTableCondition(operator) {
  return {
    type: 'OPERATOR',
    token: operator,
    evaluated: false,
    children: [variableNode('score', null, false), valueNode(null, 'null', false)]
  }
}

// RuleSetCompiler 先赋值 _ruleSetEvalN，再记录规则标识并执行 if (_ruleSetEvalN)。
function compiledRuleTrace(code, index, predicate, mode = 'PARALLEL', evaluated = true) {
  const name = '_ruleSetEval' + index
  const body = [
    { type: 'OPERATOR', token: '=', evaluated, value: predicate.value,
      children: [variableNode(name, undefined, evaluated), predicate] },
    { type: 'FUNCTION', token: 'recordRuleSetItem', evaluated,
      children: [valueNode(evaluated ? code : undefined, JSON.stringify(code), evaluated),
        valueNode(code), variableNode(name, predicate.value, evaluated)] },
    { type: 'IF', evaluated, children: [variableNode(name, predicate.value, evaluated),
      { type: 'BLOCK', evaluated: predicate.value === true, children: [] }] }
  ]
  return mode === 'PARALLEL' ? body : [{ type: 'IF', evaluated: true, children: [
    { type: 'OPERATOR', token: '!', evaluated: true, value: evaluated, children: [variableNode('_ruleSetMatched', !evaluated)] },
    { type: 'BLOCK', evaluated, children: body }
  ] }]
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
    expect(wrapper.findComponent('trace-tree-stub').props('traceInfo')).not.toContain('$ref')
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
      wrapper.unmount()
    }
  )

  test('FIRST 首条命中后后续规则保留静态配置并标记未执行', () => {
    const secondIf = tableIfNode(skippedTableCondition('>='), 'REVIEW')
    const firstIf = tableIfNode(
      compareNode('score', '>=', 95, 90, true),
      'PASS',
      secondIf,
      false
    )
    const wrapper = mountTraceTree({
      modelType: 'TABLE',
      definitionModel: {
        hitPolicy: 'FIRST',
        rules: [
          {
            ruleName: '高分通过',
            conditionRoot: tableCondition('score', '>=', 90),
            actions: [tableAction('decision', 'PASS')]
          },
          {
            ruleName: '中分复核',
            conditionRoot: tableCondition('score', '>=', 60),
            actions: [tableAction('decision', 'REVIEW')]
          }
        ]
      },
      traceInfo: JSON.stringify([firstIf])
    })

    expect(wrapper.vm.tableRules).toHaveLength(2)
    expect(wrapper.vm.tableRules[0]).toMatchObject({
      ruleName: '高分通过', status: 'hit', statusText: '命中'
    })
    expect(wrapper.vm.tableRules[1]).toMatchObject({
      ruleName: '中分复核',
      status: 'skipped',
      statusText: '未执行（首次命中后终止）'
    })
    expect(wrapper.vm.tableRules[1].acts).toEqual({ decision: 'REVIEW' })
    expect(wrapper.vm.tableRules[1].configuredConditionText).toContain('60')
    expect(wrapper.vm.tableCondSummaryText(wrapper.vm.tableRules[1])).toContain('60')
    expect(wrapper.text()).toContain('未执行（首次命中后终止）')
  })

  test('FIRST 中间命中时按执行顺序展示未命中命中和未执行', () => {
    const thirdIf = tableIfNode(skippedTableCondition('>='), 'REJECT')
    const secondIf = tableIfNode(
      compareNode('score', '>=', 75, 60, true),
      'REVIEW',
      thirdIf,
      false
    )
    const firstIf = tableIfNode(
      compareNode('score', '>=', 75, 90, false),
      'PASS',
      secondIf
    )
    const wrapper = mountTraceTree({
      modelType: 'TABLE',
      definitionModel: {
        hitPolicy: 'FIRST',
        rules: [
          { conditionRoot: tableCondition('score', '>=', 90), actions: [tableAction('decision', 'PASS')] },
          { conditionRoot: tableCondition('score', '>=', 60), actions: [tableAction('decision', 'REVIEW')] },
          { conditionRoot: tableCondition('score', '>=', 0), actions: [tableAction('decision', 'REJECT')] }
        ]
      },
      traceInfo: JSON.stringify([firstIf])
    })

    expect(wrapper.vm.tableRules.map(rule => rule.status))
      .toEqual(['miss', 'hit', 'skipped'])
  })

  test('FIRST 无命中和默认规则命中都保留真实三态', () => {
    const noHit = mountTraceTree({
      modelType: 'TABLE',
      definitionModel: {
        hitPolicy: 'FIRST',
        rules: [
          { conditionRoot: tableCondition('score', '>=', 90), actions: [tableAction('decision', 'PASS')] },
          { conditionRoot: tableCondition('score', '>=', 60), actions: [tableAction('decision', 'REVIEW')] }
        ]
      },
      traceInfo: JSON.stringify([
        tableIfNode(
          compareNode('score', '>=', 20, 90, false),
          'PASS',
          tableIfNode(compareNode('score', '>=', 20, 60, false), 'REVIEW')
        )
      ])
    })
    expect(noHit.vm.tableRules.map(rule => rule.status)).toEqual(['miss', 'miss'])
    noHit.unmount()

    const defaultHit = mountTraceTree({
      modelType: 'TABLE',
      definitionModel: {
        hitPolicy: 'FIRST',
        rules: [
          { conditionRoot: tableCondition('score', '>=', 60), actions: [tableAction('decision', 'REVIEW')] },
          {
            ruleName: '默认拒绝',
            conditionRoot: { type: 'group', op: 'AND', children: [] },
            actions: [tableAction('decision', 'REJECT')]
          }
        ]
      },
      traceInfo: JSON.stringify([
        tableIfNode(
          compareNode('score', '>=', 20, 60, false),
          'REVIEW',
          tableIfNode(valueNode(true, 'true'), 'REJECT')
        )
      ])
    })
    expect(defaultHit.vm.tableRules.map(rule => rule.status)).toEqual(['miss', 'hit'])
    expect(defaultHit.vm.tableRules[1]).toMatchObject({
      ruleName: '默认拒绝', acts: { decision: 'REJECT' }
    })
  })

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
    expect(functionCard.resultDisplay).toBe(JSON.stringify(faces, null, 2))
    expect(wrapper.vm._displayVal(faces)).toBe(JSON.stringify(faces))
    expect(wrapper.text()).not.toContain('[object Object]')
  })

  test.each(['FLOW', 'SCRIPT'])('%s 长结果与表达式分区且完整保留内容', modelType => {
    const result = { hits: Array.from({ length: 30 }, (_, i) => ({ name: '命中规则' + i })), tail: '完整结果末尾' }
    const wrapper = mountTraceTree({
      modelType,
      traceInfo: JSON.stringify([
        { type: 'FUNCTION', token: 'setRuntimeValue', evaluated: true, value: result,
          children: [valueNode('策略结果'), variableNode('策略结果', result)] },
        assignNode('长文本', '长文本结果'.repeat(500)),
        assignNode('短结果', false),
        assignNode('_result', { decision: 'REJECT' })
      ])
    })

    const functionCard = wrapper.find('.fc-card--function')
    expect(functionCard.find('.fc-expr-panel').text()).toContain('规则表达式')
    expect(functionCard.find('.fc-expr').text()).toContain('setRuntimeValue')
    expect(functionCard.find('.fc-result-panel').text()).toContain('执行结果')
    expect(JSON.parse(functionCard.find('.fc-expr-result').text())).toEqual(result)
    expect(wrapper.findAll('.fc-expr-result')[1].text()).toBe('长文本结果'.repeat(500))
    expect(wrapper.findAll('.fc-expr-result')[2].text()).toBe('false')
    const final = wrapper.find('.fc-expr-row--result-only')
    expect(final.find('.fc-expr-panel').exists()).toBe(false)
    expect(final.find('.fc-expr-result').text()).toBe('decision:REJECT')
    wrapper.unmount()
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
    const conditionNode = wrapper.findComponent({ name: 'RuleSetConditionTraceNode' })
    expect(conditionNode.props('node').children[0]).toMatchObject({ actualText: '1', thresholdText: '1' })
  })

  test('规则集事件优先校准命中状态且非命中规则不展示动作', () => {
    const missInfo = { ruleCode: 'R0001', ruleName: '黑名单规则', priority: 10, order: 1 }
    const hitInfo = { ruleCode: 'R0002', ruleName: '额度规则', priority: 1, order: 2 }
    const missTrace = ruleSetIfNode(
      compareNode('black_hit', '==', 0, 1, false),
      false,
      null
    )
    missTrace.children[1].evaluated = true
    missTrace.children[1].children = [assignNode('_ruleSetHit', missInfo)]
    const wrapper = mountTraceTree({
      modelType: 'RULE_SET',
      definitionModel: {
        executionMode: 'SERIAL',
        rules: [
          {
            ruleCode: 'R0001',
            ruleName: '黑名单规则',
            priority: 10,
            conditionRoot: {
              type: 'group',
              op: 'AND',
              children: [{ type: 'leaf', varCode: 'black_hit', operator: '==', value: '1' }]
            },
            actionData: [{ type: 'assign', target: 'result', value: '101' }]
          },
          {
            ruleCode: 'R0002',
            ruleName: '额度规则',
            priority: 1,
            conditionRoot: {
              type: 'group',
              op: 'AND',
              children: [{ type: 'leaf', varCode: 'amount', operator: '>=', value: '0' }]
            },
            actionData: [{ type: 'assign', target: 'amount', value: '5500' }]
          }
        ]
      },
      traceInfo: JSON.stringify([
        missTrace,
        ruleSetIfNode(compareNode('amount', '>=', 5000, 0, true), true, hitInfo)
      ]),
      outputResult: JSON.stringify({ result: 100, amount: 5500 }),
      ruleSetEvents: [
        { type: 'RULE_SET_ITEM', ruleCode: 'R0001', evaluated: true, hit: false },
        { type: 'RULE_SET_ITEM', ruleCode: 'R0002', evaluated: true, hit: true }
      ]
    })

    expect(wrapper.vm.ruleSetRows[0]).toMatchObject({ status: 'miss', hit: false, actions: [] })
    expect(wrapper.vm.ruleSetRows[1]).toMatchObject({ status: 'hit', hit: true })
    expect(wrapper.vm.ruleSetRows[1].actions).toHaveLength(1)
    expect(wrapper.vm.ruleSetHitRows.map(row => row.ruleCode)).toEqual(['R0002'])
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

    expect(item).toMatchObject({ varCode: 'request.age', varName: '年龄', actualText: '20', actualSource: 'input', thresholdText: '成年年龄 adultAge = 18', result: null })
  })

  test.each(['SERIAL', 'PARALLEL'])('%s 当前编译格式按规则标识读取条件赋值中的实际值', mode => {
    const root = { type: 'group', op: 'AND', children: [{ type: 'leaf', varCode: 'idcard_validity_days', operator: '<=', value: '30' }] }
    const wrapper = mountTraceTree({ modelType: 'RULE_SET',
      definitionModel: { executionMode: mode, rules: [{ ruleCode: 'R-DAYS', conditionRoot: root }] },
      traceInfo: JSON.stringify(compiledRuleTrace('R-DAYS', 0, compareNode('idcard_validity_days', '<=', 12, 30, true), mode))
    })
    expect(wrapper.vm.ruleSetRows[0]).toMatchObject({ status: 'hit', conditionTree: { result: true,
      children: [{ actualText: '12', actualSource: 'trace', result: true }] } })
    expect(wrapper.vm.ruleSetRows[0].conditions[0]).toMatchObject({ actualText: '12', result: true })
    wrapper.unmount()
  })

  test('函数参数不同的同名调用读取各自返回值，右侧也使用当时的执行值', () => {
    const call = (index, name) => ({ type: 'FUNCTION', token: 'toStringValue', evaluated: true, value: name,
      children: [{ type: 'FUNCTION', token: 'jsonGet', evaluated: true, value: name,
        children: [variableNode('contacts', []), valueNode('$[' + index + '].联系人姓名')] }] })
    const root = { type: 'group', op: 'OR', children: [0, 1].map(i => ({ type: 'leaf',
      varCode: 'toStringValue(jsonGet(contacts, "$[' + i + '].联系人姓名"))', operator: '==', valueKind: 'VAR', value: 'name' })) }
    const predicate = logicalNode('||', ['测试乙', '测试甲'].map((name, i) => ({ type: 'OPERATOR', token: '==',
      evaluated: true, value: i === 1, children: [call(i, name), variableNode('name', '测试甲')] })), true)
    const wrapper = mountTraceTree({ modelType: 'RULE_SET', inputParams: '{"name":"旧值"}',
      definitionModel: { rules: [{ ruleCode: 'R-CONTACT', conditionRoot: root }] },
      traceInfo: JSON.stringify(compiledRuleTrace('R-CONTACT', 0, predicate)) })
    expect(wrapper.vm.ruleSetRows[0].conditionTree.children).toMatchObject([
      { actualText: '测试乙', thresholdText: 'name = 测试甲', result: false },
      { actualText: '测试甲', thresholdText: 'name = 测试甲', result: true }
    ])
    wrapper.unmount()
  })

  test('同一变量的三个 OR 条件按二叉追踪顺序对应，短路条件不可用输入值推算', () => {
    const root = { type: 'group', op: 'OR', children: [1, 2, 3].map(value => ({ type: 'leaf', varCode: 'count', operator: '==', value })) }
    const predicate = logicalNode('||', [logicalNode('||', [compareNode('count', '==', 2, 1, false), compareNode('count', '==', 2, 2, true)], true),
      compareNode('count', '==', undefined, undefined, undefined, false)], true)
    const wrapper = mountTraceTree({ modelType: 'RULE_SET', inputParams: '{"count":3}',
      definitionModel: { rules: [{ ruleCode: 'R-OR', conditionRoot: root }] },
      traceInfo: JSON.stringify(compiledRuleTrace('R-OR', 0, predicate)) })
    const row = wrapper.vm.ruleSetRows[0]
    expect(row.conditionTree.children).toMatchObject([
      { actualText: '2', result: false }, { actualText: '2', result: true },
      { actualText: '3', actualSource: 'input', result: null, traceStatus: 'skipped' }
    ])
    expect(row.conditions.filter(item => item.kind !== 'join').map(item => item.result)).toEqual([false, true, null])
    wrapper.unmount()
  })

  test('串行首次命中后跳过的规则保留未执行状态，空值不会误报未取值', () => {
    const leaf = { type: 'leaf', varCode: 'optional', operator: 'is_null' }
    const nullTrace = { type: 'OPERATOR', token: '==', evaluated: true, value: true,
      children: [variableNode('optional', undefined), valueNode(null)] }
    const wrapper = mountTraceTree({ modelType: 'RULE_SET',
      definitionModel: { rules: ['R-FIRST', 'R-SKIP'].map(ruleCode => ({ ruleCode, conditionRoot: leaf })) },
      traceInfo: JSON.stringify([...compiledRuleTrace('R-FIRST', 0, nullTrace, 'SERIAL'),
        ...compiledRuleTrace('R-SKIP', 1, { type: 'OPERATOR', token: '==', evaluated: false, children: [] }, 'SERIAL', false)]) })
    expect(wrapper.vm.ruleSetRows[0].conditionTree).toMatchObject({ actualText: '空', result: true })
    expect(wrapper.vm.ruleSetRows[1]).toMatchObject({ status: 'skipped', conditionTree: { actualText: '未执行', result: null } })
    wrapper.unmount()
  })

  test('排序和禁用规则不影响当前追踪关联，旧字段编码不会遮蔽执行值', () => {
    const rules = [
      { enabled: false, priority: 99 },
      { priority: 1, conditionRoot: { type: 'leaf', varCode: 'oldCode', operator: '<', value: '20' } },
      { ruleCode: 'R-HIGH', priority: 9, conditionRoot: { type: 'leaf', varCode: 'score', operator: '>', value: '60' } }
    ]
    const wrapper = mountTraceTree({ modelType: 'RULE_SET', definitionModel: { rules },
      traceInfo: JSON.stringify([...compiledRuleTrace('R-HIGH', 0, compareNode('score', '>', 90, 60, true)),
        ...compiledRuleTrace('R0002', 1, compareNode('latestCode', '<', 10, 20, true))]) })
    expect(wrapper.vm.ruleSetRows.map(row => [row.ruleCode, row.conditionTree.actualText])).toEqual([
      ['R-HIGH', '90'], ['R0002', '10']
    ])
    wrapper.unmount()
  })

  test('复合范围及否定函数读取原始条件结果，不把辅助函数值当作最终布尔值', () => {
    const wrapper = mountTraceTree({})
    const between = logicalNode('&&', [compareNode('age', '>=', 32, 18, true), compareNode('age', '<=', 32, 55, true)], true)
    expect(wrapper.vm._buildRuleSetConditionLeaf({ varCode: 'age', operator: 'between', value: '18,55' }, between))
      .toMatchObject({ actualText: '32', result: true })
    const notContains = { type: 'OPERATOR', token: '!', evaluated: true, value: true, children: [
      { type: 'FUNCTION', token: 'containsValue', evaluated: true, value: false, children: [variableNode('tags', ['A']), valueNode('B')] }
    ] }
    expect(wrapper.vm._buildRuleSetConditionLeaf({ varCode: 'tags', operator: 'not_contains', value: 'B' }, notContains))
      .toMatchObject({ actualText: '["A"]', result: true })
    wrapper.unmount()
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
    expect(childWrapper.findComponent('trace-tree-stub').props('modelType')).toBe('RULE_SET')
    expect(childWrapper.findComponent('trace-tree-stub').props('definitionModel')).toEqual({
      executionMode: 'SERIAL',
      rules: [{ ruleCode: 'CREDIT_CHILD', ruleName: '授信额度子规则' }]
    })
    childWrapper.unmount()
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
    const routingTree = wrapper.findComponent('decision-tree-trace-node-stub')
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
    simple.unmount()

    const advanced = mountTraceTree({
      modelType: 'CROSS_ADV',
      definitionModel: {
        rowDimensions: [{ segments: [{ operator: '==', value: 'A' }] }],
        colDimensions: [{ segments: [{ operator: '==', value: 'B' }] }],
        cells: [[{ kind: 'LITERAL', value: 1.147, valueType: 'DOUBLE' }]]
      }
    })
    expect(advanced.vm.traceAdvCellDisplay(0, 0)).toBe('1.147')
    advanced.unmount()
  })

  test('复杂评分卡追踪使用组权重和维度权重', () => {
    const wrapper = mountTraceTree({
      modelType: 'SCORE_ADV',
      definitionModel: {
        dimensionGroups: [{
          weight: 0.5,
          dimensions: [{ weight: 2, rules: [] }]
        }]
      }
    })

    expect(wrapper.vm._scoreAdvWeight('_dim_0_0')).toBe(1)
    expect(wrapper.vm._scoreAdvWeight('_dim_9_9')).toBe(1)
  })

  test('规则集追踪保留三层 AND OR 条件组结构并传给递归节点', () => {
    const hitInfo = { ruleCode: 'R-NESTED', ruleName: '多层条件规则', priority: 5, order: 1 }
    const wrapper = mountTraceTree({
      modelType: 'RULE_SET',
      definitionModel: {
        rules: [{
          ruleCode: 'R-NESTED',
          ruleName: '多层条件规则',
          conditionRoot: {
            type: 'group',
            op: 'AND',
            children: [
              { type: 'leaf', varCode: 'age', varLabel: '年龄', varType: 'NUMBER', operator: '>=', value: '18' },
              {
                type: 'group',
                op: 'OR',
                children: [
                  { type: 'leaf', varCode: 'score', varLabel: '评分', varType: 'NUMBER', operator: '>=', value: '60' },
                  { type: 'leaf', varCode: 'vip', varLabel: 'VIP', varType: 'BOOLEAN', operator: '==', value: 'true' }
                ]
              }
            ]
          },
          actionData: []
        }]
      },
      traceInfo: JSON.stringify([
        ruleSetIfNode(logicalNode('&&', [
          compareNode('age', '>=', 20, 18, true),
          logicalNode('||', [
            compareNode('score', '>=', 50, 60, false),
            compareNode('vip', '==', true, true, true)
          ], true)
        ], true), true, hitInfo)
      ]),
      outputResult: JSON.stringify([hitInfo])
    })

    const tree = wrapper.vm.ruleSetRows[0].conditionTree
    expect(tree).toMatchObject({ kind: 'group', operator: 'AND', result: true })
    expect(tree.children[0]).toMatchObject({ kind: 'condition', varCode: 'age', result: true })
    expect(tree.children[1]).toMatchObject({ kind: 'group', operator: 'OR', result: true })
    expect(tree.children[1].children[0]).toMatchObject({ kind: 'condition', varCode: 'score', result: false })
    expect(tree.children[1].children[1]).toMatchObject({ kind: 'condition', varCode: 'vip', result: true })
    const node = wrapper.findComponent({ name: 'RuleSetConditionTraceNode' })
    expect(node.exists()).toBe(true)
    expect(node.props('node')).toEqual(tree)
  })

  test('规则集短路后保留内层结构但不把未执行条件推算为已执行', () => {
    const rule = {
      ruleCode: 'R-SHORT',
      conditionRoot: {
        type: 'group',
        op: 'AND',
        children: [
          { type: 'leaf', varCode: 'age', varType: 'NUMBER', operator: '>=', value: '18' },
          {
            type: 'group',
            op: 'OR',
            children: [
              { type: 'leaf', varCode: 'score', varType: 'NUMBER', operator: '>=', value: '60' },
              { type: 'leaf', varCode: 'vip', varType: 'BOOLEAN', operator: '==', value: 'true' }
            ]
          }
        ]
      },
      actionData: []
    }
    const skippedOr = logicalNode('||', [
      compareNode('score', '>=', 80, 60, undefined, false),
      compareNode('vip', '==', true, true, undefined, false)
    ], undefined, false)
    const wrapper = mountTraceTree({
      modelType: 'RULE_SET',
      definitionModel: { rules: [rule] },
      traceInfo: JSON.stringify([
        ruleSetIfNode(logicalNode('&&', [compareNode('age', '>=', 16, 18, false), skippedOr], false), false, null)
      ]),
      inputParams: JSON.stringify({ age: 16, score: 80, vip: true }),
      outputResult: JSON.stringify([])
    })

    const nested = wrapper.vm.ruleSetRows[0].conditionTree.children[1]
    expect(nested).toMatchObject({ kind: 'group', operator: 'OR', result: null })
    expect(nested.children.map(child => child.result)).toEqual([null, null])
  })
})
