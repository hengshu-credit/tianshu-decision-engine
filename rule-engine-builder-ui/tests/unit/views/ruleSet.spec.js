jest.unmock('@/views/designer/RuleSet.vue')

const RuleSet = require('@/views/designer/RuleSet.vue').default

function createRuleSetContext(model = { executionMode: 'SERIAL', rules: [] }) {
  const ctx = {
    model,
    varPickerOptions: [],
    $message: { warning: jest.fn(), error: jest.fn(), success: jest.fn() },
    $set(target, key, value) { target[key] = value },
    $delete(target, key) { delete target[key] },
    $forceUpdate: jest.fn()
  }
  Object.keys(RuleSet.methods).forEach(name => {
    ctx[name] = RuleSet.methods[name].bind(ctx)
  })
  return ctx
}

const listOperand = (overrides = {}) => ({
  kind: 'REFERENCE',
  value: 'hit_rules',
  code: 'hit_rules',
  label: '命中规则',
  valueType: 'LIST',
  refId: 12,
  refType: 'VARIABLE',
  resolved: true,
  ...overrides
})

describe('RuleSet 命中结果输出字段', () => {
  test('旧规则集无需补选输出字段且保存时不生成空配置', () => {
    const ctx = createRuleSetContext()

    ctx.normalizeModel()
    const serialized = ctx.serializeModel()

    expect(ctx.model.resultVar).toEqual({})
    expect(serialized).not.toHaveProperty('resultVar')
  })

  test('旧 resultVar 引用字段迁移为统一 Operand', () => {
    const ctx = createRuleSetContext({
      executionMode: 'SERIAL',
      resultVar: { varCode: 'hit_rules', varLabel: '命中规则', varType: 'LIST', _varId: 12, _refType: 'VARIABLE' },
      rules: []
    })

    ctx.normalizeModel()

    expect(ctx.model.resultVar.operand).toMatchObject({
      kind: 'REFERENCE', code: 'hit_rules', valueType: 'LIST', refId: 12, refType: 'VARIABLE', resolved: true
    })
  })

  test('LIST 普通变量可配置并按稳定 ID 保存', () => {
    const ctx = createRuleSetContext()

    ctx.onResultVarInput(listOperand())

    expect(ctx.model.resultVar).toMatchObject({
      varCode: 'hit_rules', varType: 'LIST', _varId: 12, _refType: 'VARIABLE',
      operand: { refId: 12, refType: 'VARIABLE' }
    })
    expect(ctx.validateResultVar()).toBe('')
  })

  test('LIST 数据对象字段可作为输出目标', () => {
    const ctx = createRuleSetContext()
    const operand = listOperand({
      kind: 'PATH', value: 'request.hitRules', code: 'request.hitRules',
      refId: 33, refType: 'DATA_OBJECT'
    })

    ctx.onResultPathResolve({ operand, candidates: [] })

    expect(ctx.model.resultVar).toMatchObject({
      varCode: 'request.hitRules', varType: 'LIST', _varId: 33, _refType: 'DATA_OBJECT',
      operand: { kind: 'PATH', refId: 33, refType: 'DATA_OBJECT' }
    })
  })

  test('手输路径反查到非 LIST 字段时拒绝覆盖已有配置', () => {
    const ctx = createRuleSetContext()
    ctx.onResultVarInput(listOperand())
    const previous = JSON.parse(JSON.stringify(ctx.model.resultVar))

    ctx.onResultPathResolve({
      operand: listOperand({ value: 'request.score', code: 'request.score', valueType: 'NUMBER', refId: 34, refType: 'DATA_OBJECT' }),
      candidates: []
    })

    expect(ctx.model.resultVar).toEqual(previous)
    expect(ctx.$message.warning).toHaveBeenCalledWith(expect.stringContaining('LIST'))
  })

  test('清空输出字段后恢复为可选的空配置', () => {
    const ctx = createRuleSetContext()
    ctx.onResultVarInput(listOperand())

    ctx.onResultVarInput(null)

    expect(ctx.model.resultVar).toEqual({})
    expect(ctx.validateResultVar()).toBe('')
  })
})
