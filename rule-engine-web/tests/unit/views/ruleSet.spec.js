import { shallowMount } from '@test-utils'

import * as definitionApi from '@/api/definition'

vi.unmock('@/views/designer/RuleSet.vue')

const RuleSet = (await vi.importActual('@/views/designer/RuleSet.vue')).default

function createRuleSetContext(model = { executionMode: 'SERIAL', rules: [] }) {
  const ctx = {
    model,
    varPickerOptions: [],
    $message: { warning: vi.fn(), error: vi.fn(), success: vi.fn() },
    $set(target, key, value) { target[key] = value },
    $delete(target, key) { delete target[key] },
    $forceUpdate: vi.fn()
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

describe('RuleSet 版本发布入口', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  test('进入生命周期时不回滚编译或调用旧发布接口', async () => {
    definitionApi.getDefinition.mockResolvedValue({
      id: 30,
      projectId: null,
      scope: 'PROJECT',
    })
    definitionApi.getContent.mockResolvedValue({
      modelJson: JSON.stringify({
        executionMode: 'SERIAL',
        rules: [],
      }),
    })
    const router = { push: vi.fn(), replace: vi.fn() }
    const wrapper = shallowMount(RuleSet, {
      mocks: {
        $route: { params: { id: 30 }, query: {} },
        $router: router,
        $confirm: vi.fn().mockResolvedValue('confirm'),
        $message: {
          success: vi.fn(),
          warning: vi.fn(),
          error: vi.fn(),
        },
      },
    })
    await new Promise((resolve) => setTimeout(resolve, 0))
    vi.clearAllMocks()

    expect(wrapper.vm.openLifecycle).toEqual(expect.any(Function))
    await wrapper.vm.openLifecycle()

    expect(definitionApi.rollbackVersion).not.toHaveBeenCalled()
    expect(definitionApi.compileRule).not.toHaveBeenCalled()
    expect(definitionApi.publishRule).not.toHaveBeenCalled()
    expect(router.push).toHaveBeenCalledWith({
      name: 'RuleDetail',
      params: { id: 30 },
      query: { focus: 'lifecycle' },
    })
    wrapper.unmount()
  })
})
