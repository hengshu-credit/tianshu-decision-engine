import ExperimentDetail from '@/views/experiment/ExperimentDetail.vue'
import { listExperimentLogs, saveExperiment, listVersions, compareVersions } from '@/api/experiment'

const leaf = (code, value, label = code) => ({
  type: 'leaf',
  leftOperand: { kind: 'PATH', value: code, code, label, valueType: 'NUMBER' },
  operator: '>=',
  rightOperand: { kind: 'LITERAL', value, valueType: 'NUMBER' }
})

function createContext(overrides = {}) {
  const ctx = {
    $set(target, key, value) { target[key] = value },
    $forceUpdate: jest.fn(),
    $message: { success: jest.fn(), error: jest.fn() },
    $confirm: jest.fn().mockResolvedValue(),
    $router: { replace: jest.fn(), push: jest.fn() },
    $route: { params: {} },
    $refs: { form: { validate: cb => cb(true) } },
    projectRefs: [],
    projectVars: [],
    rulesForProject: [],
    ruleFieldMap: {},
    nextUid: 1,
    logs: [],
    logTotal: 0,
    logLoading: false,
    logQuery: { pageNum: 1, pageSize: 10, requestKey: '', stage: '', groupCode: '', success: '' },
    saving: false,
    versionVisible: false,
    versionList: [],
    versionCompare: null,
    versionPreview: '',
    ...overrides
  }
  Object.keys(ExperimentDetail.methods).forEach(name => {
    ctx[name] = ExperimentDetail.methods[name].bind(ctx)
  })
  ctx.experimentGuideCards = overrides.experimentGuideCards || ExperimentDetail.data.call(ctx).experimentGuideCards
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
  Object.defineProperty(ctx, 'experimentInputFields', {
    get() { return ExperimentDetail.computed.experimentInputFields.call(ctx) }
  })
  Object.defineProperty(ctx, 'experimentOutputFields', {
    get() { return ExperimentDetail.computed.experimentOutputFields.call(ctx) }
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
    ctx.form.groups[0].conditionConfig.children[0] = leaf('amount', '1000')

    const groups = ctx.prepareGroupsForSave()

    expect(groups[0].conditionExpression).toBe('')
    expect(groups[0].conditionConfig).toBe('')
  })

  test('条件分流按左条件右动作保存可执行表达式和兜底动作', () => {
    const ctx = createContext()
    ctx.form.routingMode = 'CONDITION'
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].conditionConfig.children[0] = leaf('amount', '1000')
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

  test('输入输出字段归集条件字段和执行规则字段', () => {
    const ctx = createContext({
      ruleFieldMap: {
        champion_rule: {
          input: [{ scriptName: 'age', fieldLabel: '年龄', fieldType: 'NUMBER' }],
          output: [{ scriptName: 'riskLevel', fieldLabel: '风险等级', fieldType: 'STRING' }]
        }
      }
    })
    ctx.form.routingMode = 'CONDITION'
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].conditionConfig.children[0] = leaf('amount', '1000', '申请金额')

    expect(ctx.experimentInputFields.map(f => f.fieldName)).toEqual(['amount', 'age'])
    expect(ctx.experimentOutputFields.map(f => f.fieldName)).toEqual(['riskLevel'])
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

  test('openVersionDialog loads versions and compareWithNext compares adjacent versions', async () => {
    listVersions.mockResolvedValue({ data: [{ version: 2 }, { version: 1 }] })
    compareVersions.mockResolvedValue({ data: { left: { version: 2 }, right: { version: 1 }, groupsChanged: true } })
    const ctx = createContext()
    ctx.form.id = 9

    await ctx.openVersionDialog()
    await ctx.compareWithNext(ctx.versionList[0], 0)

    expect(ctx.versionVisible).toBe(true)
    expect(listVersions).toHaveBeenCalledWith(9)
    expect(compareVersions).toHaveBeenCalledWith(9, 2, 1)
    expect(ctx.versionCompare.groupsChanged).toBe(true)
  })

  test('experimentGuideCards 解释冠军挑战和空跑测试', () => {
    const ctx = createContext()

    expect(ctx.experimentGuideCards.map(item => item.title)).toEqual([
      '冠军组',
      '挑战组',
      '空跑测试',
      '版本回滚'
    ])
    expect(ctx.experimentGuideCards[2].text).toContain('不影响生产结果')
  })
})
