import ExperimentDetail from '@/views/experiment/ExperimentDetail.vue'
import { listExperimentLogs, saveExperiment, listVersions, compareVersions } from '@/api/experiment'
import { listProjectDefinitions } from '@/api/definition'

const leaf = (code, value, label = code) => ({
  type: 'leaf',
  leftOperand: { kind: 'PATH', value: code, code, label, valueType: 'NUMBER' },
  operator: '>=',
  rightOperand: { kind: 'LITERAL', value, valueType: 'NUMBER' }
})

function createContext(overrides = {}) {
  const ctx = {
    $set(target, key, value) { target[key] = value },
    $forceUpdate: vi.fn(),
    $message: { success: vi.fn(), error: vi.fn() },
    $confirm: vi.fn().mockResolvedValue(),
    $router: { replace: vi.fn(), push: vi.fn() },
    $route: { params: {} },
    $refs: { form: { validate: cb => cb(true) } },
    projectRefs: [],
    projectVars: [],
    projectFunctions: [],
    projectLists: [],
    varPickerOptions: [],
    rulesForProject: [],
    ruleFieldMap: {},
    nextUid: 1,
    logs: [],
    logTotal: 0,
    logLoading: false,
    logQuery: { pageNum: 1, pageSize: 10, requestKey: '', traceId: '', stage: '', groupCode: '', success: '' },
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
  ctx.requestKeyKinds = ExperimentDetail.data.call(ctx).requestKeyKinds
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
    vi.clearAllMocks()
  })

  test('允许调整冠军组类型，但保存前必须只有一个冠军组', () => {
    const ctx = createContext()
    ctx.form.groups[0].ruleCode = 'rule_challenger'
    ctx.form.groups[0].ruleId = 1
    ctx.form.groups[0].trafficRatio = 40
    ctx.form.groups[0].groupType = 'CHALLENGER'
    ctx.form.groups.push(ctx.withUid(ctx.newGroup('CHAMPION', 'champion_2', '冠军组2', 60)))
    ctx.form.groups[1].ruleCode = 'rule_champion'
    ctx.form.groups[1].ruleId = 2

    expect(ctx.validateGroups()).toBe('')

    ctx.form.groups.push(ctx.withUid(ctx.newGroup('CHAMPION', 'champion_3', '冠军组3', 0)))
    ctx.form.groups[2].ruleCode = 'rule_champion_2'
    ctx.form.groups[2].ruleId = 3
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
    ctx.logQuery.traceId = 'EXP0001'

    await ctx.loadLogs()

    expect(listExperimentLogs).toHaveBeenCalledWith({
      pageNum: 1, pageSize: 10, requestKey: 'REQ', traceId: 'EXP0001', experimentId: 9
    })
    expect(ctx.logs[0].requestKey).toBe('REQ001')
    expect(ctx.logTotal).toBe(1)
  })

  test('输入输出字段归集条件字段和执行规则字段', () => {
    const ctx = createContext({
      ruleFieldMap: {
        8: {
          input: [{ scriptName: 'age', fieldLabel: '年龄', fieldType: 'NUMBER' }],
          output: [{ scriptName: 'riskLevel', fieldLabel: '风险等级', fieldType: 'STRING' }]
        }
      }
    })
    ctx.form.routingMode = 'CONDITION'
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].ruleId = 8
    ctx.form.groups[0].conditionConfig.children[0] = leaf('amount', '1000', '申请金额')

    expect(ctx.experimentInputFields.map(f => f.fieldName)).toEqual(['amount', 'age'])
    expect(ctx.experimentOutputFields.map(f => f.fieldName)).toEqual(['riskLevel'])
  })

  test('请求键使用统一表达式保存并归集引用字段', async () => {
    saveExperiment.mockResolvedValue({ data: { id: 12 } })
    const ctx = createContext()
    ctx.form.projectId = 1
    ctx.form.experimentCode = 'EXP_KEY'
    ctx.form.experimentName = '组合请求键'
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].ruleId = 8
    ctx.form.groups[0].conditionConfig = null
    ctx.form.requestKeyOperand = {
      kind: 'CAST',
      targetType: 'STRING',
      operand: {
        kind: 'OPERATION',
        terms: [
          { operand: { kind: 'REFERENCE', refId: 8, refType: 'VARIABLE', code: 'customerId', label: '客户ID', valueType: 'STRING' } },
          { operator: '+', operand: { kind: 'LITERAL', value: '-RISK', valueType: 'STRING' } }
        ]
      }
    }

    expect(ctx.experimentInputFields.map(field => field.fieldName)).toContain('customerId')

    await ctx.handleSave()

    const payload = saveExperiment.mock.calls[0][0]
    expect(JSON.parse(payload.requestKeyPath)).toEqual(ctx.form.requestKeyOperand)
    expect(payload.requestKeyOperand).toBeUndefined()
  })

  test('新建保存成功后跳转到详情页', async () => {
    saveExperiment.mockResolvedValue({ data: { id: 12 } })
    const ctx = createContext()
    ctx.form.projectId = 1
    ctx.form.experimentCode = 'EXP_A'
    ctx.form.experimentName = '实验A'
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].ruleId = 8
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

  test('项目规则列表直接使用 ID 键控的输入输出元数据', async () => {
    listProjectDefinitions.mockResolvedValue({ data: { records: [{
      id: 8,
      projectId: 1,
      scope: 'PROJECT',
      ruleCode: 'score_card',
      modelType: 'SCORE',
      status: 1,
      inputFieldsJson: [{ scriptName: 'CREDIT_AMOUNT' }],
      outputFieldsJson: [{ scriptName: 'score' }]
    }] } })
    const ctx = createContext()
    ctx.form.groups[0].ruleCode = 'score_card'

    await ctx.loadRules(1)

    expect(listProjectDefinitions).toHaveBeenCalledWith(1, { pageNum: 1, pageSize: 1000, status: 1 })
    expect(ctx.form.groups[0].ruleId).toBe(8)
    expect(ctx.ruleFieldMap[8].input[0].scriptName).toBe('CREDIT_AMOUNT')
    expect(ctx.ruleFieldMap[8].output[0].scriptName).toBe('score')
  })

  test('切换项目会同时清空实验组规则 ID 和编码快照', () => {
    const ctx = createContext({ projects: [{ id: 2, projectCode: 'P2' }] })
    ctx.form.groups[0].ruleId = 8
    ctx.form.groups[0].ruleCode = 'score_card'
    ctx.loadRules = vi.fn()
    ctx.loadExperimentRefs = vi.fn()

    ctx.onProjectChange(2)

    expect(ctx.form.groups[0].ruleId).toBeNull()
    expect(ctx.form.groups[0].ruleCode).toBe('')
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
