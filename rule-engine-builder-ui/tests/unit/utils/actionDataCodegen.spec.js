/**
 * actionDataCodegen.spec.js
 * 动作数据 JSON → QLExpress 脚本生成器的单元测试
 *
 * 注意：wrapValue 语义为 QLExpress 脚本中的值字面量处理。
 * - null / undefined / '' → '""' (QLExpress 空字符串字面量)
 * - 标识符 (符合 [a-zA-Z_]\w*) → 不加引号 (变量引用)
 * - 数字字面量 → 不加引号
 * - 带引号字符串 → 保留引号
 * - 其他字符串 → 加引号
 */

import {
  generateScript,
  actionDataToBlocks,
  blocksToActionData,
  newBlock,
  BLOCK_TYPES
} from '@/utils/actionDataCodegen'

// ============================================================
// generateScript
// ============================================================
describe('generateScript', () => {
  test('空数组返回空字符串', () => {
    expect(generateScript([])).toBe('')
  })

  test('null 输入返回空字符串', () => {
    expect(generateScript(null)).toBe('')
  })

  test('undefined 输入返回空字符串', () => {
    expect(generateScript(undefined)).toBe('')
  })

  // -------- assign --------
  test('assign: 基础赋值', () => {
    const script = generateScript([{ type: 'assign', target: 'result', value: '100' }])
    expect(script).toBe('result = 100')
  })

  test('assign: 空 target 不生成代码', () => {
    const script = generateScript([{ type: 'assign', target: '', value: '100' }])
    expect(script).toBe('')
  })

  test('assign: 空 value 不生成代码', () => {
    const script = generateScript([{ type: 'assign', target: 'x', value: '' }])
    expect(script).toBe('')
  })

  test('assign: 标识符作为 value 不加引号', () => {
    const script = generateScript([{ type: 'assign', target: 'msg', value: 'hello' }])
    expect(script).toBe('msg = hello')
  })

  test('assign: 表达式作为 value 直接透传', () => {
    const script = generateScript([{ type: 'assign', target: 'x', value: 'a + b * 2' }])
    expect(script).toBe('x = a + b * 2')
  })

  test('assign: 带引号字符串保留引号', () => {
    const script = generateScript([{ type: 'assign', target: 'x', value: '"hello"' }])
    expect(script).toBe('x = "hello"')
  })

  // -------- if-block --------
  test('if-block: 单 if 分支', () => {
    const script = generateScript([{
      type: 'if-block',
      branches: [{
        type: 'if',
        condVar: 'score',
        condOp: '>=',
        condValue: '60',
        actions: [{ type: 'assign', target: 'grade', value: '"pass"' }]
      }]
    }])
    expect(script).toContain('if (score >= 60) {')
    expect(script).toContain('grade = "pass"')
    expect(script).toContain('}')
  })

  test('if-block: if + elseif + else', () => {
    const script = generateScript([{
      type: 'if-block',
      branches: [
        { type: 'if', condVar: 's', condOp: '>', condValue: '90', actions: [{ type: 'assign', target: 'g', value: '"A"' }] },
        { type: 'elseif', condVar: 's', condOp: '>', condValue: '60', actions: [{ type: 'assign', target: 'g', value: '"B"' }] },
        { type: 'else', actions: [{ type: 'assign', target: 'g', value: '"C"' }] }
      ]
    }])
    expect(script).toContain('if (s > 90) {')
    expect(script).toContain('} else if (s > 60) {')
    expect(script).toContain('} else {')
  })

  test('if-block: 空 branches 不生成代码', () => {
    const script = generateScript([{ type: 'if-block', branches: [] }])
    expect(script).toBe('')
  })

  test('if-block: condVar 为空时使用 true', () => {
    const script = generateScript([{
      type: 'if-block',
      branches: [{ type: 'if', condVar: '', condOp: '==', condValue: '', actions: [{ type: 'assign', target: 'x', value: '1' }] }]
    }])
    expect(script).toContain('if (true) {')
  })

  test('if-block: 嵌套多个动作', () => {
    const script = generateScript([{
      type: 'if-block',
      branches: [{
        type: 'if', condVar: 'flag', condOp: '==', condValue: 'true', actions: [
          { type: 'assign', target: 'a', value: '1' },
          { type: 'assign', target: 'b', value: '2' }
        ]
      }]
    }])
    expect(script).toContain('a = 1')
    expect(script).toContain('b = 2')
  })

  // -------- switch-block --------
  test('switch-block: 单 case + default', () => {
    const script = generateScript([{
      type: 'switch-block',
      matchVar: 'status',
      cases: [
        { value: 1, actions: [{ type: 'assign', target: 'msg', value: '"ok"' }] }
      ],
      defaultActions: [{ type: 'assign', target: 'msg', value: '"unknown"' }]
    }])
    expect(script).toContain('switch (status) {')
    expect(script).toContain('case 1 -> {')
    expect(script).toContain('default -> {')
  })

  test('switch-block: 多 case', () => {
    const script = generateScript([{
      type: 'switch-block',
      matchVar: 'code',
      cases: [
        { value: 1, actions: [{ type: 'assign', target: 'x', value: '1' }] },
        { value: 2, actions: [{ type: 'assign', target: 'x', value: '2' }] }
      ]
    }])
    expect(script).toContain('case 1 -> {')
    expect(script).toContain('case 2 -> {')
  })

  test('switch-block: 空 matchVar 不生成代码', () => {
    const script = generateScript([{ type: 'switch-block', matchVar: '', cases: [{ value: 1, actions: [] }] }])
    expect(script).toBe('')
  })

  test('switch-block: value=0 仍然生成（0 是有效值）', () => {
    const script = generateScript([{
      type: 'switch-block',
      matchVar: 'flag',
      cases: [{ value: 0, actions: [{ type: 'assign', target: 'x', value: '0' }] }]
    }])
    expect(script).toContain('case 0 -> {')
  })

  test('switch-block: value 为空字符串时不生成', () => {
    const script = generateScript([{
      type: 'switch-block',
      matchVar: 'x',
      cases: [{ value: '', actions: [] }]
    }])
    expect(script).not.toContain('case "" ->')
  })

  // -------- func-call --------
  test('func-call: 无目标变量', () => {
    const script = generateScript([{ type: 'func-call', funcName: 'print', args: ['msg'] }])
    expect(script).toBe('print(msg)')
  })

  test('func-call: 有目标变量', () => {
    const script = generateScript([{ type: 'func-call', target: 'maxVal', funcName: 'max', args: ['a', 'b'] }])
    expect(script).toBe('maxVal = max(a, b)')
  })

  test('func-call: 空 funcName 不生成', () => {
    const script = generateScript([{ type: 'func-call', target: 'x', funcName: '', args: [] }])
    expect(script).toBe('')
  })

  test('func-call: 多参数', () => {
    const script = generateScript([{ type: 'func-call', funcName: 'sum', args: ['a', 'b', 'c'] }])
    expect(script).toBe('sum(a, b, c)')
  })

  // -------- foreach --------
  test('foreach: 基本循环', () => {
    const script = generateScript([{
      type: 'foreach',
      itemVar: 'item',
      listExpr: 'items',
      actions: [{ type: 'assign', target: 'sum', value: 'sum + item' }]
    }])
    expect(script).toContain('for (item : items) {')
    expect(script).toContain('sum = sum + item')
    expect(script).toMatch(/\}$/)
  })

  test('foreach: 空 itemVar 不生成', () => {
    const script = generateScript([{ type: 'foreach', itemVar: '', listExpr: 'items', actions: [] }])
    expect(script).toBe('')
  })

  test('foreach: 空 listExpr 不生成', () => {
    const script = generateScript([{ type: 'foreach', itemVar: 'item', listExpr: '', actions: [] }])
    expect(script).toBe('')
  })

  // -------- ternary --------
  test('ternary: 三元表达式', () => {
    const script = generateScript([{
      type: 'ternary',
      target: 'label',
      condVar: 'score',
      condOp: '>=',
      condValue: '60',
      trueValue: '"pass"',
      falseValue: '"fail"'
    }])
    expect(script).toBe('label = score >= 60 ? "pass" : "fail"')
  })

  test('ternary: 空 target 不生成', () => {
    const script = generateScript([{ type: 'ternary', target: '', condVar: 'x', condValue: '0', trueValue: '1', falseValue: '2' }])
    expect(script).toBe('')
  })

  test('ternary: 空 condVar 不生成', () => {
    const script = generateScript([{ type: 'ternary', target: 'x', condVar: '', condValue: '0', trueValue: '1', falseValue: '2' }])
    expect(script).toBe('')
  })

  // -------- in-check --------
  test('in-check: IN 判断（标识符格式不加引号，符合 QLExpress 脚本语义）', () => {
    const script = generateScript([{
      type: 'in-check',
      target: 'flag',
      checkVar: 'status',
      inValues: ['S1', 'S2', 'S3'],
      trueValue: 'true',
      falseValue: 'false'
    }])
    expect(script).toBe('flag = status in [S1, S2, S3] ? true : false')
  })

  test('in-check: 空 inValues 只生成 false 分支', () => {
    const script = generateScript([{
      type: 'in-check',
      target: 'x',
      checkVar: 'v',
      inValues: [],
      trueValue: 'true',
      falseValue: 'false'
    }])
    expect(script).toBe('x = v in [] ? true : false')
  })

  test('in-check: 过滤 null 和空字符串', () => {
    const script = generateScript([{
      type: 'in-check',
      target: 'x',
      checkVar: 'v',
      inValues: ['AA', null, '', 'BB'],
      trueValue: '1',
      falseValue: '0'
    }])
    expect(script).toBe('x = v in [AA, BB] ? 1 : 0')
  })

  // -------- template-str --------
  test('template-str: 动态字符串', () => {
    const script = generateScript([{
      type: 'template-str',
      target: 'msg',
      parts: [
        { type: 'text', content: 'Hello, ' },
        { type: 'expr', content: 'name' }
      ]
    }])
    expect(script).toContain('msg = "Hello, ${name}"')
  })

  test('template-str: 纯文本', () => {
    const script = generateScript([{
      type: 'template-str',
      target: 'msg',
      parts: [{ type: 'text', content: 'fixed text' }]
    }])
    expect(script).toContain('msg = "fixed text"')
  })

  test('template-str: 空 target 不生成', () => {
    const script = generateScript([{ type: 'template-str', target: '', parts: [{ type: 'text', content: 'x' }] }])
    expect(script).toBe('')
  })

  test('rule-call: 调用整条规则结果', () => {
    const script = generateScript([{ type: 'rule-call', target: 'riskResult', ruleCode: 'credit_flow' }])
    expect(script).toBe('riskResult = executeRule("credit_flow")')
  })

  test('rule-call: 调用规则具体输出字段', () => {
    const script = generateScript([{ type: 'rule-call', target: 'score', ruleCode: 'score_card', outputField: 'score' }])
    expect(script).toBe('score = executeRuleField("score_card", "score")')
  })

  // -------- 多块组合 --------
  test('多块按顺序拼接', () => {
    const script = generateScript([
      { type: 'assign', target: 'a', value: '1' },
      { type: 'assign', target: 'b', value: '2' }
    ])
    expect(script).toBe('a = 1\nb = 2')
  })

  // -------- 边界值 --------
  test('assign: null value 不生成代码（falsy 拦截）', () => {
    const script = generateScript([{ type: 'assign', target: 'x', value: null }])
    expect(script).toBe('')
  })

  test('assign: undefined value 不生成代码（falsy 拦截）', () => {
    const script = generateScript([{ type: 'assign', target: 'x', value: undefined }])
    expect(script).toBe('')
  })

  test('wrapValue: true/false/null 作为关键字保留', () => {
    expect(generateScript([{ type: 'assign', target: 'x', value: 'true' }])).toBe('x = true')
    expect(generateScript([{ type: 'assign', target: 'x', value: 'false' }])).toBe('x = false')
    expect(generateScript([{ type: 'assign', target: 'x', value: 'null' }])).toBe('x = null')
  })

  test('wrapValue: 数字直接透传', () => {
    const script = generateScript([{ type: 'assign', target: 'x', value: '123.45' }])
    expect(script).toBe('x = 123.45')
  })

  test('未知块类型返回空', () => {
    const script = generateScript([{ type: 'unknown-block', target: 'x', value: '1' }])
    expect(script).toBe('')
  })
})

// ============================================================
// actionDataToBlocks
// ============================================================
describe('actionDataToBlocks', () => {
  test('空数组返回空数组', () => {
    expect(actionDataToBlocks([])).toEqual([])
  })

  test('null 输入返回空数组', () => {
    expect(actionDataToBlocks(null)).toEqual([])
  })

  test('普通块直接透传', () => {
    const input = [{ type: 'assign', target: 'x', value: '1' }]
    expect(actionDataToBlocks(input)).toEqual(input)
  })

  test('if-block: 标准化 branches.actions', () => {
    const input = [{
      type: 'if-block',
      branches: [
        { type: 'if', condVar: 'x', actions: null }
      ]
    }]
    const out = actionDataToBlocks(input)
    expect(out[0].branches[0].actions).toEqual([])
  })

  test('switch-block: 标准化 cases 和 defaultActions', () => {
    const input = [{
      type: 'switch-block',
      matchVar: 'x',
      cases: [{ value: 'A' }],
      defaultActions: undefined
    }]
    const out = actionDataToBlocks(input)
    expect(out[0].cases[0].actions).toEqual([])
    expect(out[0].defaultActions).toEqual([])
  })

  test('foreach: 标准化 actions', () => {
    const input = [{ type: 'foreach', itemVar: 'i', listExpr: 'arr', actions: null }]
    const out = actionDataToBlocks(input)
    expect(out[0].actions).toEqual([])
  })

  test('in-check: 过滤空值', () => {
    const input = [{
      type: 'in-check',
      target: 'x',
      checkVar: 'v',
      inValues: ['A', null, '', 'B']
    }]
    const out = actionDataToBlocks(input)
    expect(out[0].inValues).toEqual(['A', 'B'])
  })
})

// ============================================================
// blocksToActionData
// ============================================================
describe('blocksToActionData', () => {
  test('空数组返回空数组', () => {
    expect(blocksToActionData([])).toEqual([])
  })

  test('深拷贝，不污染原数组', () => {
    const original = [{ type: 'assign', target: 'x', value: '1' }]
    const copy = blocksToActionData(original)
    copy[0].target = 'y'
    expect(original[0].target).toBe('x')
  })

  test('嵌套对象深拷贝', () => {
    const original = [{
      type: 'if-block',
      branches: [{ type: 'if', actions: [{ type: 'assign', target: 'a', value: '1' }] }]
    }]
    const copy = blocksToActionData(original)
    copy[0].branches[0].actions[0].target = 'b'
    expect(original[0].branches[0].actions[0].target).toBe('a')
  })
})

// ============================================================
// newBlock
// ============================================================
describe('newBlock', () => {
  test('assign: 标准结构', () => {
    const b = newBlock('assign')
    expect(b).toEqual({ type: 'assign', target: '', value: '' })
  })

  test('if-block: 带默认分支', () => {
    const b = newBlock('if-block')
    expect(b.type).toBe('if-block')
    expect(b.branches[0].type).toBe('if')
    expect(b.branches[0].actions[0].type).toBe('assign')
  })

  test('switch-block: 带 case + default', () => {
    const b = newBlock('switch-block')
    expect(b.type).toBe('switch-block')
    expect(b.cases[0].value).toBe('')
    expect(b.defaultActions[0].type).toBe('assign')
  })

  test('func-call: 默认参数为空字符串', () => {
    const b = newBlock('func-call')
    expect(b.args).toEqual([''])
  })

  test('foreach: itemVar 默认 item', () => {
    const b = newBlock('foreach')
    expect(b.itemVar).toBe('item')
    expect(b.listExpr).toBe('')
  })

  test('ternary: 全字段初始化', () => {
    const b = newBlock('ternary')
    expect(b.type).toBe('ternary')
    expect(b.condOp).toBe('==')
  })

  test('in-check: inValues 默认空数组，true/false 默认值', () => {
    const b = newBlock('in-check')
    expect(b.inValues).toEqual([])
    expect(b.trueValue).toBe('true')
    expect(b.falseValue).toBe('false')
  })

  test('template-str: 默认文本段落', () => {
    const b = newBlock('template-str')
    expect(b.parts).toEqual([{ type: 'text', content: '' }])
  })

  test('rule-call: 初始化规则调用块', () => {
    const b = newBlock('rule-call')
    expect(b).toMatchObject({ type: 'rule-call', target: '', ruleId: null, ruleCode: '', outputField: '' })
  })

  test('未知类型默认为 assign', () => {
    const b = newBlock('unknown')
    expect(b.type).toBe('assign')
  })
})

// ============================================================
// BLOCK_TYPES 常量
// ============================================================
describe('BLOCK_TYPES', () => {
  test('包含全部 9 种块类型', () => {
    const types = BLOCK_TYPES.map(b => b.type)
    expect(types).toContain('assign')
    expect(types).toContain('if-block')
    expect(types).toContain('switch-block')
    expect(types).toContain('func-call')
    expect(types).toContain('foreach')
    expect(types).toContain('ternary')
    expect(types).toContain('in-check')
    expect(types).toContain('template-str')
    expect(types).toContain('rule-call')
    expect(types.length).toBe(9)
  })

  test('每种类型有 label 和 color', () => {
    BLOCK_TYPES.forEach(b => {
      expect(typeof b.label).toBe('string')
      expect(b.label.length).toBeGreaterThan(0)
      expect(typeof b.color).toBe('string')
      expect(b.color.startsWith('#')).toBe(true)
    })
  })
})
