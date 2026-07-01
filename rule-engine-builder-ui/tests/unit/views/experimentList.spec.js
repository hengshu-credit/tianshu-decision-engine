import ExperimentList from '@/views/experiment/ExperimentList.vue'
import { executeExperiment, listExperiments, saveExperiment } from '@/api/experiment'
import { listDefinitions } from '@/api/definition'

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

  test('handleTest fills executable demo params for browser testing', () => {
    const ctx = createContext()

    ctx.handleTest({ experimentCode: 'EXP_DEMO' })

    const params = JSON.parse(ctx.testJson)
    expect(params.requestId).toBe('EXP_DEMO_REQ_001')
    expect(params.taxpayerType).toBe('一般纳税人')
    expect(params.goodsCategory).toBe('货物')
    expect(params.invoiceDeviationRate).toBe(0.05)
    expect(params.billingAmount).toBe(100000)
    expect(params.vasServiceRatio).toBe(0.4)
  })
})
