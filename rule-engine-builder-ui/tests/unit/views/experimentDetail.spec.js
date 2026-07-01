import ExperimentDetail from '@/views/experiment/ExperimentDetail.vue'
import { listExperimentLogs, saveExperiment } from '@/api/experiment'

function createContext(overrides = {}) {
  const ctx = {
    $set(target, key, value) { target[key] = value },
    $forceUpdate: jest.fn(),
    $message: { success: jest.fn(), error: jest.fn() },
    $router: { replace: jest.fn(), push: jest.fn() },
    $route: { params: {} },
    $refs: { form: { validate: cb => cb(true) } },
    projectRefs: [],
    projectVars: [],
    rulesForProject: [],
    nextUid: 1,
    logs: [],
    logTotal: 0,
    logLoading: false,
    logQuery: { pageNum: 1, pageSize: 10, requestKey: '', stage: '', groupCode: '', success: '' },
    saving: false,
    ...overrides
  }
  Object.keys(ExperimentDetail.methods).forEach(name => {
    ctx[name] = ExperimentDetail.methods[name].bind(ctx)
  })
  ctx.form = overrides.form || ctx.emptyForm()
  Object.defineProperty(ctx, 'productionFormGroups', {
    get() { return ExperimentDetail.computed.productionFormGroups.call(ctx) }
  })
  Object.defineProperty(ctx, 'testFormGroups', {
    get() { return ExperimentDetail.computed.testFormGroups.call(ctx) }
  })
  Object.defineProperty(ctx, 'ratioTotal', {
    get() { return ExperimentDetail.computed.ratioTotal.call(ctx) }
  })
  Object.defineProperty(ctx, 'testRatioTotal', {
    get() { return ExperimentDetail.computed.testRatioTotal.call(ctx) }
  })
  return ctx
}

describe('ExperimentDetail', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  test('允许调整冠军组类型，但保存前必须只有一个冠军组', () => {
    const ctx = createContext()
    ctx.form.groups[0].ruleCode = 'rule_challenger'
    ctx.form.groups[0].trafficRatio = 40
    ctx.form.groups[0].groupType = 'CHALLENGER'
    ctx.form.groups.push(ctx.withUid(ctx.newGroup('CHAMPION', 'champion_2', '冠军组2', 60)))
    ctx.form.groups[1].ruleCode = 'rule_champion'

    expect(ctx.validateGroups()).toBe('')

    ctx.form.groups.push(ctx.withUid(ctx.newGroup('CHAMPION', 'champion_3', '冠军组3', 0)))
    ctx.form.groups[2].ruleCode = 'rule_champion_2'
    expect(ctx.validateGroups()).toBe('必须且只能配置一组冠军组')
  })

  test('随机分流保存时清空条件配置，避免和条件分流混用', () => {
    const ctx = createContext()
    ctx.form.routingMode = 'RATIO'
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].conditionConfig.children[0] = {
      type: 'leaf',
      varCode: 'amount',
      varType: 'NUMBER',
      operator: '>=',
      valueKind: 'CONST',
      value: '1000'
    }

    const groups = ctx.prepareGroupsForSave()

    expect(groups[0].conditionExpression).toBe('')
    expect(groups[0].conditionConfig).toBe('')
  })

  test('条件分流按左条件右动作保存可执行表达式和兜底动作', () => {
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
    expect(JSON.parse(groups[1].conditionConfig).fallback).toBe(true)
  })

  test('分流日志按当前实验 ID 查询', async () => {
    listExperimentLogs.mockResolvedValue({ data: { records: [{ requestKey: 'REQ001' }], total: 1 } })
    const ctx = createContext()
    ctx.form.id = 9
    ctx.logQuery.requestKey = 'REQ'

    await ctx.loadLogs()

    expect(listExperimentLogs).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10, requestKey: 'REQ', experimentId: 9 })
    expect(ctx.logs[0].requestKey).toBe('REQ001')
    expect(ctx.logTotal).toBe(1)
  })

  test('新建保存成功后跳转到详情页', async () => {
    saveExperiment.mockResolvedValue({ data: { id: 12 } })
    const ctx = createContext()
    ctx.form.projectId = 1
    ctx.form.experimentCode = 'EXP_A'
    ctx.form.experimentName = '实验A'
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].conditionConfig = null

    await ctx.handleSave()

    expect(saveExperiment).toHaveBeenCalled()
    expect(ctx.$router.replace).toHaveBeenCalledWith('/experiment/detail/12')
  })
})
