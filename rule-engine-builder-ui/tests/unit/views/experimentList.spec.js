import ExperimentList from '@/views/experiment/ExperimentList.vue'
import { executeExperiment, listExperiments, saveExperiment } from '@/api/experiment'
import { getRuleTestSchema, listDefinitions } from '@/api/definition'

function createContext(overrides = {}) {
  const ctx = {
    form: { groups: [] },
    query: { pageNum: 1, pageSize: 10, projectId: '', status: '', keyword: '' },
    experiments: [],
    total: 0,
    loading: false,
    rulesForProject: [],
    testJson: '{}',
    testRequest: { requestKey: '', requestTime: '' },
    testExperiment: { experimentCode: 'EXP_A' },
    testResult: null,
    testing: false,
    $message: { success: jest.fn(), error: jest.fn() },
    $refs: { form: { validate: cb => cb(true) } },
    ...overrides
  }
  Object.keys(ExperimentList.methods).forEach(name => {
    ctx[name] = ExperimentList.methods[name].bind(ctx)
  })
  ctx.form = overrides.form || ctx.emptyForm()
  Object.defineProperty(ctx, 'productionFormGroups', {
    get() { return ExperimentList.computed.productionFormGroups.call(ctx) }
  })
  Object.defineProperty(ctx, 'testFormGroups', {
    get() { return ExperimentList.computed.testFormGroups.call(ctx) }
  })
  Object.defineProperty(ctx, 'ratioTotal', {
    get() { return ExperimentList.computed.ratioTotal.call(ctx) }
  })
  Object.defineProperty(ctx, 'testRatioTotal', {
    get() { return ExperimentList.computed.testRatioTotal.call(ctx) }
  })
  return ctx
}

describe('ExperimentList', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  test('validateGroups rejects production ratio that is not 100 percent', () => {
    const ctx = createContext()
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].trafficRatio = 80
    ctx.form.groups.push(ctx.newGroup('CHALLENGER', 'challenger_1', '挑战组1', 10))
    ctx.form.groups[1].ruleCode = 'challenger_rule'

    expect(ctx.validateGroups()).toBe('冠军组和挑战组分流比例之和必须为100%')
  })

  test('validateGroups allows one champion and multiple challengers when ratio totals 100', () => {
    const ctx = createContext()
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].trafficRatio = 70
    ctx.form.groups.push(ctx.newGroup('CHALLENGER', 'challenger_1', '挑战组1', 30))
    ctx.form.groups[1].ruleCode = 'challenger_rule'

    expect(ctx.validateGroups()).toBe('')
  })

  test('validateGroups rejects multiple champion groups after type edits', () => {
    const ctx = createContext()
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups.push(ctx.newGroup('CHAMPION', 'champion_2', '冠军组2', 0))
    ctx.form.groups[1].ruleCode = 'champion_rule_2'

    expect(ctx.validateGroups()).toBe('必须且只能配置一组冠军组')
  })

  test('validateGroups checks test ratio separately from production ratio', () => {
    const ctx = createContext()
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.testRoutingMode = 'RATIO'
    ctx.form.groups.push(ctx.newGroup('TEST', 'test_1', '测试组1', 60))
    ctx.form.groups[1].ruleCode = 'test_rule_1'
    ctx.form.groups.push(ctx.newGroup('TEST', 'test_2', '测试组2', 30))
    ctx.form.groups[2].ruleCode = 'test_rule_2'

    expect(ctx.validateGroups()).toBe('测试组分流比例之和必须为100%')
  })

  test('validateGroups allows independent condition routing with fallback actions', () => {
    const ctx = createContext()
    ctx.form.routingMode = 'CONDITION'
    ctx.form.testRoutingMode = 'CONDITION'
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].trafficRatio = 10
    ctx.form.groups[0].conditionConfig.children[0] = {
      type: 'leaf',
      varCode: 'amount',
      varType: 'NUMBER',
      operator: '>=',
      valueKind: 'CONST',
      value: '1000'
    }
    ctx.addProductionFallback()
    ctx.productionFormGroups[1].ruleCode = 'fallback_rule'
    ctx.form.groups.push(ctx.newGroup('TEST', 'test_1', '测试组1', 0))
    ctx.testFormGroups[0].ruleCode = 'test_rule_1'
    ctx.testFormGroups[0].conditionConfig.children[0] = {
      type: 'leaf',
      varCode: 'score',
      varType: 'NUMBER',
      operator: '>=',
      valueKind: 'CONST',
      value: '80'
    }
    ctx.addTestFallback()
    ctx.testFormGroups[1].ruleCode = 'test_fallback_rule'

    expect(ctx.validateGroups()).toBe('')
  })

  test('validateGroups requires fallback for visual condition routing', () => {
    const ctx = createContext()
    ctx.form.routingMode = 'CONDITION'
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].conditionConfig.children[0] = {
      type: 'leaf',
      varCode: 'amount',
      varType: 'NUMBER',
      operator: '>',
      valueKind: 'CONST',
      value: '100'
    }

    expect(ctx.validateGroups()).toBe('冠军挑战条件分流必须配置兜底动作')
  })

  test('prepareGroupsForSave serializes visual conditions and fallback markers', () => {
    const ctx = createContext()
    ctx.form.routingMode = 'CONDITION'
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].conditionConfig.children[0] = {
      type: 'leaf',
      varCode: 'amount',
      varType: 'NUMBER',
      operator: '>=',
      valueKind: 'CONST',
      value: '1000'
    }
    ctx.addProductionFallback()
    ctx.productionFormGroups[1].ruleCode = 'fallback_rule'

    const groups = ctx.prepareGroupsForSave()

    expect(groups[0].conditionExpression).toBe('(amount >= 1000)')
    expect(JSON.parse(groups[0].conditionConfig).children[0].varCode).toBe('amount')
    expect(groups[1].conditionExpression).toBe('')
    expect(JSON.parse(groups[1].conditionConfig).fallback).toBe(true)
  })

  test('loadRules requests enabled rules for selected project', async () => {
    listDefinitions.mockResolvedValue({ data: { records: [{ ruleCode: 'rule_a' }] } })
    const ctx = createContext()

    await ctx.loadRules(7)

    expect(listDefinitions).toHaveBeenCalledWith({ pageNum: 1, pageSize: 1000, projectId: 7, status: 1 })
    expect(ctx.rulesForProject[0].ruleCode).toBe('rule_a')
  })

  test('loadExperiments removes blank query params', async () => {
    listExperiments.mockResolvedValue({ data: { records: [{ experimentCode: 'EXP_A' }], total: 1 } })
    const ctx = createContext()

    await ctx.loadExperiments()

    expect(listExperiments).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 })
    expect(ctx.experiments[0].experimentCode).toBe('EXP_A')
  })

  test('handleSave posts groups with sort order after validation', async () => {
    saveExperiment.mockResolvedValue({ data: {} })
    const ctx = createContext()
    ctx.form.projectId = 1
    ctx.form.experimentCode = 'EXP_A'
    ctx.form.experimentName = '实验A'
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].conditionConfig = null

    await ctx.handleSave()

    expect(saveExperiment).toHaveBeenCalled()
    expect(saveExperiment.mock.calls[0][0].groups[0].sortOrder).toBe(0)
  })

  test('doExecute submits parsed params and request metadata', async () => {
    executeExperiment.mockResolvedValue({ data: { success: true, tags: ['champion'] } })
    const ctx = createContext({
      testJson: '{"requestId":"REQ001","amount":100}',
      testRequest: { requestKey: 'REQ001', requestTime: '2026-07-01T10:00:00' },
      testExperiment: { experimentCode: 'EXP_A' }
    })

    await ctx.doExecute()

    expect(executeExperiment).toHaveBeenCalledWith('EXP_A', {
      params: { requestId: 'REQ001', amount: 100 },
      requestKey: 'REQ001',
      requestTime: '2026-07-01T10:00:00'
    })
    expect(ctx.testResult.tags).toEqual(['champion'])
  })

  test('handleTest fills executable demo params for browser testing', async () => {
    const ctx = createContext()
    const row = { id: 9, experimentCode: 'EXP_DEMO' }
    const sampleParams = JSON.parse(ctx.defaultTestJson(row))
    sampleParams.score_f1_fields = { HYBASE_X115: 0 }
    getRuleTestSchema.mockResolvedValueOnce({ data: { inputs: [], sampleParams } })

    await ctx.handleTest(row)

    const params = JSON.parse(ctx.testJson)
    expect(getRuleTestSchema).toHaveBeenCalledWith({ targetType: 'EXPERIMENT', targetId: 9 })
    expect(params.score_f1_fields.HYBASE_X115).toBe(0)
    expect(params.requestId).toBe('EXP_DEMO_REQ_001')
    expect(params.taxpayerType).toBe('一般纳税人')
    expect(params.goodsCategory).toBe('货物')
    expect(params.invoiceDeviationRate).toBe(0.05)
    expect(params.billingAmount).toBe(100000)
    expect(params.vasServiceRatio).toBe(0.4)
  })
})
