import {
  actionDataToBlocks,
  BLOCK_TYPES,
  blocksToActionData,
  generateScript,
  newBlock,
  normalizeGraphActionData
} from '@/utils/actionDataCodegen'
import { createLiteralOperand, createPathOperand } from '@/utils/operand'

const literal = (value, type = 'STRING') => createLiteralOperand(value, type)
const path = value => createPathOperand(value)
const ref = (code, valueType = 'STRING') => ({
  kind: 'REFERENCE', value: code, code, label: code, valueType,
  refId: 1, refType: 'VARIABLE', resolved: true
})
const assign = (target, value) => ({ type: 'assign', targetOperand: target, valueOperand: value })

describe('generateScript', () => {
  test.each([null, undefined, []])('空动作返回空脚本', value => {
    expect(generateScript(value)).toBe('')
  })

  test('赋值明确区分目标引用与字符串阈值', () => {
    expect(generateScript([assign(ref('result'), literal('threshold'))])).toBe('result = "threshold"')
  })

  test('赋值支持路径、数字与精度处理', () => {
    const script = generateScript([{
      ...assign(path('result.amount'), literal('10.25', 'NUMBER')),
      enableRounding: true,
      decimalPlaces: 2,
      roundingMode: 'HALF_UP'
    }])
    expect(script).toBe('result.amount = 10.25\nresult.amount = roundScale(result.amount, 2, "HALF_UP")')
  })

  test('赋值缺少任一 Operand 不生成代码', () => {
    expect(generateScript([assign(null, literal('1', 'NUMBER'))])).toBe('')
    expect(generateScript([assign(ref('x'), null)])).toBe('')
  })

  test('条件分支支持 Operand 条件和嵌套动作', () => {
    const script = generateScript([{
      type: 'if-block',
      branches: [
        { type: 'if', leftOperand: ref('score', 'NUMBER'), operator: '>=', rightOperand: literal('90', 'NUMBER'), actions: [assign(ref('grade'), literal('A'))] },
        { type: 'elseif', leftOperand: ref('score', 'NUMBER'), operator: '>=', rightOperand: literal('60', 'NUMBER'), actions: [assign(ref('grade'), literal('B'))] },
        { type: 'else', actions: [assign(ref('grade'), literal('C'))] }
      ]
    }])
    expect(script).toContain('if (score >= 90) {')
    expect(script).toContain('} else if (score >= 60) {')
    expect(script).toContain('} else {')
    expect(script).toContain('grade = "A"')
  })

  test('空条件左值按 true 处理', () => {
    expect(generateScript([{
      type: 'if-block',
      branches: [{ type: 'if', leftOperand: null, operator: '==', rightOperand: null, actions: [assign(ref('x'), literal('1', 'NUMBER'))] }]
    }])).toContain('if (true) {')
  })

  test('动作条件的名单配置生成可执行服务端名单函数', () => {
    const script = generateScript([{
      type: 'if-block',
      branches: [{
        type: 'if',
        leftOperand: ref('mobile'),
        operator: 'in_list',
        rightOperand: {
          kind: 'LIST_QUERY', listIds: [9, 10], itemTypes: ['MOBILE'],
          combinationMode: 'ANY_FIELD_ANY_LIST', matchMode: 'IN_LIST', valueType: 'BOOLEAN'
        },
        actions: [assign(ref('hit'), literal('true', 'BOOLEAN'))]
      }]
    }])

    expect(script).toContain('listMatch([mobile], [9, 10], "ANY_FIELD_ANY_LIST", "IN_LIST", ["MOBILE"])')
  })

  test('Switch 的匹配值可以是阈值或字段路径', () => {
    const script = generateScript([{
      type: 'switch-block',
      matchOperand: path('request.status'),
      cases: [
        { valueOperand: literal('PASS'), actions: [assign(ref('flag'), literal('true', 'BOOLEAN'))] },
        { valueOperand: path('request.fallbackStatus'), actions: [assign(ref('flag'), literal('false', 'BOOLEAN'))] }
      ],
      defaultActions: []
    }])
    expect(script).toContain('if (request.status == "PASS") {')
    expect(script).toContain('} else if (request.status == request.fallbackStatus) {')
  })

  test('函数参数支持路径和数值阈值', () => {
    expect(generateScript([{
      type: 'func-call', functionCode: 'max', args: [path('request.score'), literal('600', 'NUMBER')]
    }])).toBe('max(request.score, 600)')
  })

  test('函数结果可写入目标字段', () => {
    expect(generateScript([{
      type: 'func-call', targetOperand: ref('maxScore'), functionCode: 'max', args: [ref('a', 'NUMBER'), ref('b', 'NUMBER')]
    }])).toBe('maxScore = max(a, b)')
  })

  test('ForEach 列表使用 Operand', () => {
    const script = generateScript([{
      type: 'foreach', itemVar: 'item', listOperand: path('request.items'), actions: [assign(ref('lastItem'), path('item'))]
    }])
    expect(script).toContain('for (item : request.items) {')
    expect(script).toContain('lastItem = item')
  })

  test('三元表达式的条件和结果均使用 Operand', () => {
    expect(generateScript([{
      type: 'ternary', targetOperand: ref('label'), leftOperand: ref('score', 'NUMBER'), operator: '>=',
      rightOperand: literal('60', 'NUMBER'), trueOperand: literal('pass'), falseOperand: literal('fail')
    }])).toBe('label = score >= 60 ? "pass" : "fail"')
  })

  test('IN 判断的列表项与结果均使用 Operand', () => {
    expect(generateScript([{
      type: 'in-check', targetOperand: ref('flag'), checkOperand: path('request.status'),
      inOperands: [literal('A'), literal('B')], trueOperand: literal('true', 'BOOLEAN'), falseOperand: literal('false', 'BOOLEAN')
    }])).toBe('flag = request.status in ["A", "B"] ? true : false')
  })

  test('动态字符串的表达式片段使用 Operand', () => {
    expect(generateScript([{
      type: 'template-str', targetOperand: ref('message'),
      parts: [{ type: 'text', operand: literal('Hello, ') }, { type: 'expr', operand: path('request.name') }]
    }])).toBe('message = "Hello, ${request.name}"')
  })

  test('规则调用结果可选写入目标 Operand', () => {
    expect(generateScript([{
      type: 'rule-call', targetOperand: ref('score'), ruleCode: 'score_card', outputField: 'score'
    }])).toBe('score = executeRuleField("score_card", "score")')
    expect(generateScript([{ type: 'rule-call', targetOperand: null, ruleCode: 'credit_flow' }])).toBe('executeRule("credit_flow")')
  })
})

describe('动作数据转换', () => {
  test('空数据返回空数组', () => {
    expect(actionDataToBlocks(null)).toEqual([])
    expect(blocksToActionData([])).toEqual([])
  })

  test('双向转换都深拷贝 Operand', () => {
    const source = [assign(ref('x'), literal('1', 'NUMBER'))]
    const blocks = actionDataToBlocks(source)
    blocks[0].targetOperand.code = 'y'
    expect(source[0].targetOperand.code).toBe('x')
    const output = blocksToActionData(source)
    output[0].valueOperand.value = '2'
    expect(source[0].valueOperand.value).toBe('1')
  })

  test('载入现有赋值动作时归一化为可区分的目标引用和阈值操作数', () => {
    const blocks = actionDataToBlocks([{
      type: 'assign', target: 'result', value: '1',
      _targetVarId: 141, _targetRefType: 'VARIABLE'
    }])

    expect(blocks[0].targetOperand).toMatchObject({
      kind: 'REFERENCE', code: 'result', refId: 141, refType: 'VARIABLE'
    })
    expect(blocks[0].valueOperand).toMatchObject({ kind: 'LITERAL', value: '1', valueType: 'NUMBER' })
    expect(blocks[0]).not.toHaveProperty('target')
    expect(blocks[0]).not.toHaveProperty('value')
  })

  test('载入现有函数动作时保留参数引用关系', () => {
    const blocks = actionDataToBlocks([{
      type: 'func-call', target: 'birthday', funcName: 'idCardBirthDate', args: ['idcard_no'],
      _targetVarId: 197, _targetRefType: 'VARIABLE',
      _argRefs: [{ _varId: 6, _refType: 'VARIABLE' }]
    }])

    expect(blocks[0].functionCode).toBe('idCardBirthDate')
    expect(blocks[0].targetOperand).toMatchObject({ kind: 'REFERENCE', refId: 197 })
    expect(blocks[0].args[0]).toMatchObject({ kind: 'REFERENCE', code: 'idcard_no', refId: 6 })
    expect(blocks[0]).not.toHaveProperty('funcName')
    expect(blocks[0]).not.toHaveProperty('_argRefs')
  })

  test('图模型的后端节点和画布节点共用动作归一化', () => {
    const model = {
      nodes: [{ actionData: [{ type: 'assign', target: 'result', value: '1' }] }],
      logicflow: { nodes: [{ properties: { actionData: [{ type: 'assign', target: 'result', value: '1' }] } }] }
    }

    normalizeGraphActionData(model)

    expect(model.nodes[0].actionData[0].targetOperand).toMatchObject({ kind: 'PATH', value: 'result' })
    expect(model.logicflow.nodes[0].properties.actionData[0].valueOperand).toMatchObject({ kind: 'LITERAL', value: '1' })
  })
})

describe('newBlock', () => {
  test('赋值块只初始化 Operand 字段', () => {
    expect(newBlock('assign')).toEqual({ type: 'assign', targetOperand: null, valueOperand: null })
  })

  test('条件块的分支使用标准 Operand 字段', () => {
    expect(newBlock('if-block').branches[0]).toMatchObject({ leftOperand: null, rightOperand: null })
  })

  test.each([
    ['switch-block', 'matchOperand'],
    ['func-call', 'targetOperand'],
    ['foreach', 'listOperand'],
    ['ternary', 'rightOperand'],
    ['in-check', 'checkOperand'],
    ['template-str', 'targetOperand'],
    ['rule-call', 'targetOperand']
  ])('%s 使用标准 Operand 字段', (type, key) => {
    expect(newBlock(type)).toHaveProperty(key)
  })

  test('未知类型退化为标准赋值块', () => {
    expect(newBlock('unknown')).toEqual(newBlock('assign'))
  })
})

describe('BLOCK_TYPES', () => {
  test('保留全部九种动作类型和展示元信息', () => {
    expect(BLOCK_TYPES).toHaveLength(9)
    BLOCK_TYPES.forEach(item => {
      expect(item.label).toBeTruthy()
      expect(item.color).toMatch(/^#/)
    })
  })
})
