import ExperimentList from '@/views/experiment/ExperimentList.vue'
import experimentListTemplate from '@/views/experiment/ExperimentList.vue?raw'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import { executeExperiment, listExperiments, saveExperiment } from '@/api/experiment'
import { getRuleTestSchema, listProjectDefinitions } from '@/api/definition'

const leaf = (code, value) => ({
  type: 'leaf',
  leftOperand: { kind: 'PATH', value: code, code, valueType: 'NUMBER' },
  operator: '>=',
  rightOperand: { kind: 'LITERAL', value, valueType: 'NUMBER' }
})

function createContext(overrides = {}) {
  const ctx = {
    form: { groups: [] },
    query: { pageNum: 1, pageSize: 10, projectId: '', status: '', keyword: '' },
    experiments: [],
    total: 0,
    loading: false,
    rulesForProject: [],
    testVisible: false,
    testReady: false,
    testLoadStatus: 'IDLE',
    testLoadError: '',
    testLoadWarnings: [],
    testParamSource: 'DEFAULTS',
    testSetupRequestId: 0,
    testExecutionRequestId: 0,
    testMode: 'manual',
    testFields: [],
    testParams: {},
    complexTestFieldDrafts: {},
    complexTestFieldErrors: {},
    testJson: '{}',
    jsonError: '',
    testRequest: { requestKey: '', requestTime: '' },
    testExperiment: { experimentCode: 'EXP_A' },
    testResult: null,
    testing: false,
    $message: { success: vi.fn(), error: vi.fn() },
    $refs: { form: { validate: cb => cb(true) } },
    $set(target, key, value) { target[key] = value },
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

function deferred() {
  let resolve
  const promise = new Promise((done) => { resolve = done })
  return { promise, resolve }
}

describe('ExperimentList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('uses project code and name fuzzy filters for list queries', () => {
    const context = {}
    Object.keys(ExperimentList.methods).forEach(name => {
      context[name] = ExperimentList.methods[name].bind(context)
    })
    const query = ExperimentList.data.call(context).query

    expect(ExperimentList.components.ProjectFilterSelect).toBe(ProjectFilterSelect)
    expect(query).toEqual(expect.objectContaining({ projectCode: '', projectName: '' }))
    expect(query.projectId).toBe('')
  })

  test('验证执行弹窗提供业务状态、字段入参和高级 JSON 入口', () => {
    expect(experimentListTemplate).toContain('>验证执行</el-button')
    expect(experimentListTemplate).toContain('执行准备状态')
    expect(experimentListTemplate).toContain('字段化入参')
    expect(experimentListTemplate).toContain('JSON 高级模式')
    expect(experimentListTemplate).toContain('分流上下文')
    expect(experimentListTemplate).toContain('原始执行结果')
    expect(experimentListTemplate).toContain("testLoadStatus === 'ERROR'")
    expect(experimentListTemplate).toContain('{{ testLoadError }}')
    expect(experimentListTemplate).toContain('>重新加载</el-button')
    expect(experimentListTemplate).toMatch(
      /<style lang="scss">\r?\n\.experiment-execution-dialog/
    )
    expect(experimentListTemplate).toContain('max-width: calc(100vw - 24px)')
    expect(experimentListTemplate).toContain('max-width: 100%')
    expect(experimentListTemplate).toContain('overflow-x: auto')
    expect(experimentListTemplate).toContain('overflow-wrap: anywhere')
  })

  test('typed enum options render before primitive controls and keep field value types', () => {
    const enumBranch = experimentListTemplate.indexOf(
      'field.validValues && field.validValues.length'
    )
    const numberBranch = experimentListTemplate.indexOf('isNumberTestField(field)')
    const ctx = createContext()

    expect(enumBranch).toBeGreaterThan(-1)
    expect(enumBranch).toBeLessThan(numberBranch)
    expect(experimentListTemplate).toContain(
      ':value="testFieldOptionValue(field, value)"'
    )
    expect(ctx.testFieldOptionValue).toBeTypeOf('function')
    expect(ctx.testFieldOptionValue({ fieldType: 'NUMBER' }, '12.5')).toBe(12.5)
    expect(ctx.testFieldOptionValue({ fieldType: 'INTEGER' }, '7')).toBe(7)
    expect(ctx.testFieldOptionValue({ fieldType: 'BOOLEAN' }, 'false')).toBe(false)
    expect(ctx.testFieldOptionValue({ fieldType: 'BOOLEAN' }, '1')).toBe(true)
    expect(ctx.testFieldOptionValue({ fieldType: 'DATE' }, '2026-08-04')).toBe(
      '2026-08-04'
    )
  })

  test('项目上下文限定实验列表并传递给新建页面', async () => {
    const push = vi.fn()
    const ctx = createContext({ $router: { push }, contextProjectId: 9 })
    ctx.query.projectId = 9
    listExperiments.mockResolvedValue({ data: { records: [], total: 0 } })

    await ctx.loadExperiments()
    await ctx.handleCreate()

    expect(listExperiments).toHaveBeenCalledWith(
      expect.objectContaining({ projectId: 9 })
    )
    expect(push).toHaveBeenCalledWith({
      path: '/experiment/new',
      query: { projectId: 9 }
    })
  })

  test('validateGroups rejects production ratio that is not 100 percent', () => {
    const ctx = createContext()
    ctx.form.groups[0].ruleId = 1
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].trafficRatio = 80
    ctx.form.groups.push(ctx.newGroup('CHALLENGER', 'challenger_1', '挑战组1', 10))
    ctx.form.groups[1].ruleId = 2
    ctx.form.groups[1].ruleCode = 'challenger_rule'

    expect(ctx.validateGroups()).toBe('冠军组和挑战组分流比例之和必须为100%')
  })

  test('validateGroups allows one champion and multiple challengers when ratio totals 100', () => {
    const ctx = createContext()
    ctx.form.groups[0].ruleId = 1
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].trafficRatio = 70
    ctx.form.groups.push(ctx.newGroup('CHALLENGER', 'challenger_1', '挑战组1', 30))
    ctx.form.groups[1].ruleId = 2
    ctx.form.groups[1].ruleCode = 'challenger_rule'

    expect(ctx.validateGroups()).toBe('')
  })

  test('validateGroups rejects multiple champion groups after type edits', () => {
    const ctx = createContext()
    ctx.form.groups[0].ruleId = 1
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups.push(ctx.newGroup('CHAMPION', 'champion_2', '冠军组2', 0))
    ctx.form.groups[1].ruleId = 2
    ctx.form.groups[1].ruleCode = 'champion_rule_2'

    expect(ctx.validateGroups()).toBe('必须且只能配置一组冠军组')
  })

  test('validateGroups checks test ratio separately from production ratio', () => {
    const ctx = createContext()
    ctx.form.groups[0].ruleId = 1
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.testRoutingMode = 'RATIO'
    ctx.form.groups.push(ctx.newGroup('TEST', 'test_1', '测试组1', 60))
    ctx.form.groups[1].ruleId = 2
    ctx.form.groups[1].ruleCode = 'test_rule_1'
    ctx.form.groups.push(ctx.newGroup('TEST', 'test_2', '测试组2', 30))
    ctx.form.groups[2].ruleId = 3
    ctx.form.groups[2].ruleCode = 'test_rule_2'

    expect(ctx.validateGroups()).toBe('测试组分流比例之和必须为100%')
  })

  test('validateGroups allows independent condition routing with fallback actions', () => {
    const ctx = createContext()
    ctx.form.routingMode = 'CONDITION'
    ctx.form.testRoutingMode = 'CONDITION'
    ctx.form.groups[0].ruleId = 1
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].trafficRatio = 10
    ctx.form.groups[0].conditionConfig.children[0] = leaf('amount', '1000')
    ctx.addProductionFallback()
    ctx.productionFormGroups[1].ruleId = 2
    ctx.productionFormGroups[1].ruleCode = 'fallback_rule'
    ctx.form.groups.push(ctx.newGroup('TEST', 'test_1', '测试组1', 0))
    ctx.testFormGroups[0].ruleId = 3
    ctx.testFormGroups[0].ruleCode = 'test_rule_1'
    ctx.testFormGroups[0].conditionConfig.children[0] = leaf('score', '80')
    ctx.addTestFallback()
    ctx.testFormGroups[1].ruleId = 4
    ctx.testFormGroups[1].ruleCode = 'test_fallback_rule'

    expect(ctx.validateGroups()).toBe('')
  })

  test('validateGroups requires fallback for visual condition routing', () => {
    const ctx = createContext()
    ctx.form.routingMode = 'CONDITION'
    ctx.form.groups[0].ruleId = 1
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].conditionConfig.children[0] = { ...leaf('amount', '100'), operator: '>' }

    expect(ctx.validateGroups()).toBe('冠军挑战条件分流必须配置兜底动作')
  })

  test('prepareGroupsForSave serializes visual conditions and fallback markers', () => {
    const ctx = createContext()
    ctx.form.routingMode = 'CONDITION'
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].conditionConfig.children[0] = leaf('amount', '1000')
    ctx.addProductionFallback()
    ctx.productionFormGroups[1].ruleCode = 'fallback_rule'

    const groups = ctx.prepareGroupsForSave()

    expect(groups[0].conditionExpression).toBe('(amount >= 1000)')
    expect(JSON.parse(groups[0].conditionConfig).children[0].leftOperand.value).toBe('amount')
    expect(groups[1].conditionExpression).toBe('')
    expect(JSON.parse(groups[1].conditionConfig).fallback).toBe(true)
  })

  test('loadRules requests enabled project and linked global rules with field metadata', async () => {
    listProjectDefinitions.mockResolvedValue({ data: { records: [{ id: 11, ruleCode: 'rule_a', modelType: 'FLOW', inputFieldsJson: [{ scriptName: 'amount' }] }] } })
    const ctx = createContext()

    await ctx.loadRules(7)

    expect(listProjectDefinitions).toHaveBeenCalledWith(7, { pageNum: 1, pageSize: 1000, status: 1 })
    expect(ctx.rulesForProject[0].id).toBe(11)
    expect(ctx.rulesForProject[0].ruleCode).toBe('rule_a')
    expect(ctx.rulesForProject[0].inputFields[0].scriptName).toBe('amount')
  })

  test('experiment group keeps stable rule ID and code snapshot', () => {
    const ctx = createContext()
    const group = ctx.newGroup('TEST', 'test_1', '测试组1', 0)

    expect(group.ruleId).toBeNull()

    ctx.onGroupRuleSelect(group, { id: 21, ruleCode: 'score_rule' })

    expect(group.ruleId).toBe(21)
    expect(group.ruleCode).toBe('score_rule')
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
    ctx.form.groups[0].ruleId = 1
    ctx.form.groups[0].ruleCode = 'champion_rule'
    ctx.form.groups[0].conditionConfig = null

    await ctx.handleSave()

    expect(saveExperiment).toHaveBeenCalled()
    expect(saveExperiment.mock.calls[0][0].groups[0].sortOrder).toBe(0)
  })

  test('doExecute submits parsed params and request metadata', async () => {
    executeExperiment.mockResolvedValue({ data: { success: true, tags: ['champion'] } })
    const ctx = createContext({
      testReady: true,
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

  test('handleTest enters LOADING first, then creates nested Schema form fields and SCHEMA_SAMPLE params', async () => {
    const ctx = createContext()
    const schema = deferred()
    getRuleTestSchema.mockReturnValueOnce(schema.promise)

    const opening = ctx.handleTest({ id: 9, experimentCode: 'EXP_SCHEMA' })

    expect(ctx.testVisible).toBe(true)
    expect(ctx.testLoadStatus).toBe('LOADING')

    schema.resolve({ data: {
      inputs: [
        { refId: 1, refType: 'VARIABLE', scriptName: 'profile.age', valueType: 'INTEGER' },
        { refId: 2, refType: 'VARIABLE', scriptName: 'profile.active', valueType: 'BOOLEAN' }
      ],
      sampleParams: { profile: { age: 36, active: true } }
    } })
    await opening

    expect(getRuleTestSchema).toHaveBeenCalledWith({ targetType: 'EXPERIMENT', targetId: 9 })
    expect(ctx.testLoadStatus).toBe('READY')
    expect(ctx.testReady).toBe(true)
    expect(ctx.testParamSource).toBe('SCHEMA_SAMPLE')
    expect(ctx.testFields.map(field => field.fieldName)).toEqual(['profile.age', 'profile.active'])
    expect(ctx.testParams).toEqual({ 'profile.age': 36, 'profile.active': true })
    expect(JSON.parse(ctx.testJson)).toEqual({ profile: { age: 36, active: true } })
  })

  test('JSON advanced mode changes after Schema loading are submitted to the experiment API', async () => {
    getRuleTestSchema.mockResolvedValueOnce({ data: {
      inputs: [{ refId: 1, scriptName: 'profile.age', valueType: 'INTEGER' }],
      sampleParams: { profile: { age: 36 } }
    } })
    executeExperiment.mockResolvedValueOnce({ data: { success: true } })
    const ctx = createContext()

    await ctx.handleTest({ id: 9, experimentCode: 'EXP_JSON_EDIT' })
    expect(ctx.testMode).toBe('manual')
    ctx.switchToJsonMode()
    ctx.testJson = '{"profile":{"age":37}}'
    ctx.onJsonInput()
    await ctx.doExecute()

    expect(ctx.testMode).toBe('json')
    expect(executeExperiment).toHaveBeenCalledWith('EXP_JSON_EDIT', {
      params: { profile: { age: 37 } },
      requestKey: '',
      requestTime: null
    })
  })

  test('doExecute does not send a request while Schema loading has not completed', async () => {
    const schema = deferred()
    getRuleTestSchema.mockReturnValueOnce(schema.promise)
    const ctx = createContext()

    const opening = ctx.handleTest({ id: 9, experimentCode: 'EXP_LOADING' })
    await ctx.doExecute()

    expect(ctx.testLoadStatus).toBe('LOADING')
    expect(executeExperiment).not.toHaveBeenCalled()

    schema.resolve({ data: { inputs: [], sampleParams: {} } })
    await opening
  })

  test('handleTest uses typed field defaults and FIELD_DEFAULTS when Schema has no sample', async () => {
    getRuleTestSchema.mockResolvedValueOnce({ data: {
      inputs: [
        { refId: 1, scriptName: 'credit.score', valueType: 'NUMBER' },
        { refId: 2, scriptName: 'credit.approved', valueType: 'BOOLEAN' },
        { refId: 3, scriptName: 'credit.tags', valueType: 'ARRAY' },
        { refId: 4, scriptName: 'credit.meta', valueType: 'OBJECT' },
        { refId: 5, scriptName: 'credit.note', valueType: 'STRING' }
      ],
      sampleParams: {}
    } })
    const ctx = createContext()

    await ctx.handleTest({ id: 9, experimentCode: 'EXP_DEFAULTS' })

    expect(ctx.testLoadStatus).toBe('READY')
    expect(ctx.testParamSource).toBe('FIELD_DEFAULTS')
    expect(ctx.testParams).toEqual({
      'credit.score': 0,
      'credit.approved': false,
      'credit.tags': [],
      'credit.meta': {},
      'credit.note': ''
    })
    expect(JSON.parse(ctx.testJson)).toEqual({
      credit: { score: 0, approved: false, tags: [], meta: {}, note: '' }
    })
  })

  test('handleTest degrades to JSON with a readable reason when Schema fields cannot be formized', async () => {
    getRuleTestSchema.mockResolvedValueOnce({ data: {
      inputs: [{ refId: 1, refType: 'VARIABLE', valueType: 'NUMBER' }],
      sampleParams: {},
      diagnostics: []
    } })
    const ctx = createContext()

    await ctx.handleTest({ id: 9, experimentCode: 'EXP_JSON_ONLY' })

    expect(ctx.testLoadStatus).toBe('DEGRADED')
    expect(ctx.testReady).toBe(true)
    expect(ctx.testMode).toBe('json')
    expect(ctx.testFields).toEqual([])
    expect(ctx.testParams).toEqual({})
    expect(ctx.testJson).toBe('{}')
    expect(ctx.testLoadWarnings.join(' ')).toContain('无法生成字段化入参')
  })

  test('handleTest enters ERROR with details when Schema initialization fails and blocks execution', async () => {
    getRuleTestSchema.mockRejectedValueOnce({
      response: { data: { message: '找不到有效规则，无法建立执行上下文' } }
    })
    const ctx = createContext()

    await ctx.handleTest({ id: 9, experimentCode: 'EXP_ERROR' })
    await ctx.doExecute()

    expect(ctx.testLoadStatus).toBe('ERROR')
    expect(ctx.testReady).toBe(false)
    expect(ctx.testLoadError).toContain('找不到有效规则')
    expect(ctx.testLoadWarnings).toEqual([])
    expect(executeExperiment).not.toHaveBeenCalled()
  })

  test('form and JSON modes preserve nested Schema fields, while invalid JSON retains an error and blocks execution', async () => {
    const ctx = createContext({
      testVisible: true,
      testReady: true,
      testMode: 'manual',
      testFields: [
        { fieldName: 'profile.age', fieldType: 'INTEGER' },
        { fieldName: 'profile.active', fieldType: 'BOOLEAN' }
      ],
      testParams: { 'profile.age': 36, 'profile.active': true }
    })

    expect(ctx.switchToJsonMode).toBeTypeOf('function')
    ctx.switchToJsonMode()
    expect(JSON.parse(ctx.testJson)).toEqual({ profile: { age: 36, active: true } })

    ctx.testJson = '{"profile":{"age":37,"active":false}}'
    ctx.switchToManualMode()
    expect(ctx.testParams).toEqual({ 'profile.age': 37, 'profile.active': false })

    ctx.switchToJsonMode()
    ctx.testJson = '{invalid json'
    ctx.onJsonInput()
    await ctx.doExecute()

    expect(ctx.jsonError).toContain('JSON 格式错误')
    expect(executeExperiment).not.toHaveBeenCalled()
  })

  test('JSON advanced mode rejects mismatched complex values and stays in JSON mode', async () => {
    const ctx = createContext({
      testVisible: true,
      testReady: true,
      testMode: 'json',
      testFields: [{ fieldName: 'profile.tags', fieldType: 'ARRAY' }],
      testParams: { 'profile.tags': [] },
      testJson: '{"profile":{"tags":{"not":"an array"}}}',
      testExperiment: { experimentCode: 'EXP_TYPE' }
    })

    ctx.switchToManualMode()
    await ctx.doExecute()

    expect(ctx.testMode).toBe('json')
    expect(ctx.jsonError).toContain('JSON 数组')
    expect(executeExperiment).not.toHaveBeenCalled()
  })

  test('removing a Schema field in JSON mode omits its stale value from field-form execution', async () => {
    executeExperiment.mockResolvedValueOnce({ data: { success: true } })
    const ctx = createContext({
      testVisible: true,
      testReady: true,
      testMode: 'json',
      testFields: [
        { fieldName: 'profile.age', fieldType: 'INTEGER' },
        { fieldName: 'profile.note', fieldType: 'STRING' }
      ],
      testParams: { 'profile.age': 36, 'profile.note': 'stale' },
      testJson: '{"profile":{"age":37}}',
      testExperiment: { experimentCode: 'EXP_REMOVED_FIELD' }
    })

    ctx.switchToManualMode()
    await ctx.doExecute()

    expect(ctx.testParams).toEqual({ 'profile.age': 37 })
    expect(executeExperiment).toHaveBeenCalledWith('EXP_REMOVED_FIELD', {
      params: { profile: { age: 37 } },
      requestKey: '',
      requestTime: null
    })
  })

  test('field-form execution submits nested params and request context without concurrent duplicate submission', async () => {
    const request = deferred()
    executeExperiment.mockReturnValueOnce(request.promise)
    const ctx = createContext({
      testVisible: true,
      testReady: true,
      testMode: 'manual',
      testFields: [{ fieldName: 'profile.age', fieldType: 'INTEGER' }],
      testParams: { 'profile.age': 36 },
      testRequest: { requestKey: 'REQ-36', requestTime: '2026-08-04T09:30:00' },
      testExperiment: { experimentCode: 'EXP_SUBMIT' }
    })

    const first = ctx.doExecute()
    const repeated = ctx.doExecute()

    expect(executeExperiment).toHaveBeenCalledTimes(1)
    expect(executeExperiment).toHaveBeenCalledWith('EXP_SUBMIT', {
      params: { profile: { age: 36 } },
      requestKey: 'REQ-36',
      requestTime: '2026-08-04T09:30:00'
    })

    request.resolve({ data: { success: true, tags: ['champion'] } })
    await Promise.all([first, repeated])
    expect(ctx.testResult.tags).toEqual(['champion'])
  })

  test('field form keeps complex ARRAY values typed and blocks malformed JSON', async () => {
    executeExperiment.mockResolvedValueOnce({ data: { success: true } })
    const ctx = createContext({
      testVisible: true,
      testReady: true,
      testMode: 'manual',
      testFields: [{ fieldName: 'profile.tags', fieldType: 'ARRAY' }],
      testParams: { 'profile.tags': [] },
      testExperiment: { experimentCode: 'EXP_COMPLEX' }
    })

    ctx.updateComplexTestField(ctx.testFields[0], '["new","priority"]')
    await ctx.doExecute()

    expect(executeExperiment).toHaveBeenCalledWith('EXP_COMPLEX', {
      params: { profile: { tags: ['new', 'priority'] } },
      requestKey: '',
      requestTime: null
    })

    ctx.updateComplexTestField(ctx.testFields[0], '{invalid json')
    await ctx.doExecute()

    expect(ctx.complexTestFieldErrors['profile.tags']).toContain('JSON 格式错误')
    expect(executeExperiment).toHaveBeenCalledTimes(1)
  })

  test('complex fields keep independent raw drafts and errors until every field is valid', async () => {
    const ctx = createContext({
      testVisible: true,
      testReady: true,
      testMode: 'manual',
      testFields: [
        { fieldName: 'profile.tags', fieldType: 'ARRAY' },
        { fieldName: 'profile.meta', fieldType: 'OBJECT' }
      ],
      testParams: { 'profile.tags': [], 'profile.meta': {} },
      testExperiment: { experimentCode: 'EXP_COMPLEX_ERRORS' }
    })

    ctx.updateComplexTestField(ctx.testFields[0], '["unfinished"')
    expect(ctx.complexTestFieldDrafts['profile.tags']).toBe('["unfinished"')
    expect(ctx.complexTestFieldErrors['profile.tags']).toContain('JSON 格式错误')

    ctx.updateComplexTestField(ctx.testFields[1], '{"level":"A"}')
    expect(ctx.complexTestFieldDrafts['profile.tags']).toBe('["unfinished"')
    expect(ctx.formatComplexTestField(ctx.testFields[0])).toBe('["unfinished"')
    expect(ctx.complexTestFieldErrors['profile.tags']).toContain('JSON 格式错误')
    expect(ctx.complexTestFieldErrors['profile.meta']).toBeUndefined()

    ctx.switchToJsonMode()
    await ctx.doExecute()

    expect(ctx.testMode).toBe('manual')
    expect(executeExperiment).not.toHaveBeenCalled()

    ctx.updateComplexTestField(ctx.testFields[0], '["complete"]')
    ctx.switchToJsonMode()

    expect(ctx.complexTestFieldErrors).toEqual({})
    expect(ctx.testMode).toBe('json')
    expect(JSON.parse(ctx.testJson)).toEqual({
      profile: { tags: ['complete'], meta: { level: 'A' } }
    })
  })

  test('closing the dialog invalidates an in-flight execution so its late response cannot write state', async () => {
    const request = deferred()
    executeExperiment.mockReturnValueOnce(request.promise)
    const ctx = createContext({
      testVisible: true,
      testReady: true,
      testMode: 'manual',
      testFields: [{ fieldName: 'amount', fieldType: 'NUMBER' }],
      testParams: { amount: 100 },
      testExperiment: { experimentCode: 'EXP_CLOSE' }
    })

    const executing = ctx.doExecute()
    expect(ctx.closeTestDialog).toBeTypeOf('function')
    ctx.closeTestDialog()
    request.resolve({ data: { success: true, tags: ['late-result'] } })
    await executing

    expect(ctx.testVisible).toBe(false)
    expect(ctx.testResult).toBeNull()
    expect(ctx.testing).toBe(false)
  })

  test('switching to another experiment keeps the new session intact when the old execution resolves late', async () => {
    const oldExecution = deferred()
    executeExperiment.mockReturnValueOnce(oldExecution.promise)
    getRuleTestSchema.mockResolvedValueOnce({ data: {
      inputs: [{ refId: 10, scriptName: 'session.version', valueType: 'STRING' }],
      sampleParams: { session: { version: 'B' } }
    } })
    const ctx = createContext({
      testVisible: true,
      testReady: true,
      testMode: 'manual',
      testFields: [{ fieldName: 'amount', fieldType: 'NUMBER' }],
      testParams: { amount: 100 },
      testExperiment: { id: 9, experimentCode: 'EXP_A' }
    })
    const experimentB = { id: 10, experimentCode: 'EXP_B' }

    const executing = ctx.doExecute()
    await ctx.handleTest(experimentB)
    oldExecution.resolve({ data: { success: true, tags: ['old-result'] } })
    await executing

    expect(ctx.testVisible).toBe(true)
    expect(ctx.testExperiment).toEqual(experimentB)
    expect(ctx.testParams).toEqual({ 'session.version': 'B' })
    expect(ctx.testResult).toBeNull()
  })

  test('buildExecutionResultSummary distinguishes overall success, production outcomes, and test group outcomes', () => {
    const ctx = createContext()

    expect(ctx.buildExecutionResultSummary).toBeTypeOf('function')
    expect(ctx.buildExecutionResultSummary({
      success: true,
      productionGroup: { groupCode: 'champion', skipped: false, success: true },
      testGroups: [
        { groupCode: 'test_hit', matched: true, skipped: false, success: true },
        { groupCode: 'test_skipped', matched: false, skipped: true, success: true },
        { groupCode: 'test_failed', matched: true, skipped: false, success: false, errorMessage: 'rule failed' }
      ]
    })).toEqual({
      overall: { status: 'SUCCESS' },
      groups: [
        { groupCode: 'champion', stage: 'PRODUCTION', status: 'SUCCESS' },
        { groupCode: 'test_hit', stage: 'TEST', status: 'MATCHED' },
        { groupCode: 'test_skipped', stage: 'TEST', status: 'SKIPPED' },
        { groupCode: 'test_failed', stage: 'TEST', status: 'FAILED', errorMessage: 'rule failed' }
      ]
    })
    expect(ctx.buildExecutionResultSummary({
      success: false,
      errorMessage: 'production failed',
      productionGroup: { groupCode: 'champion', skipped: false, success: false, errorMessage: 'production failed' },
      testGroups: []
    })).toEqual({
      overall: { status: 'FAILED', errorMessage: 'production failed' },
      groups: [{ groupCode: 'champion', stage: 'PRODUCTION', status: 'FAILED', errorMessage: 'production failed' }]
    })
    expect(ctx.buildExecutionResultSummary({
      success: true,
      productionGroup: { groupCode: 'champion', skipped: true, success: true },
      testGroups: []
    })).toEqual({
      overall: { status: 'SUCCESS' },
      groups: [{ groupCode: 'champion', stage: 'PRODUCTION', status: 'SKIPPED' }]
    })
  })
})
