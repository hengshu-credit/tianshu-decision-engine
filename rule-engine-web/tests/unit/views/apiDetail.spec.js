import ApiDetail from '@/views/datasource/ApiDetail.vue'
import { emptyExecution } from '@/utils/apiExecution'
import { mount, flushPromises } from '@test-utils'
import * as dataObjectApi from '@/api/dataObject'
import * as datasourceApi from '@/api/datasource'
import * as governanceApi from '@/api/governance'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElAlert } from 'element-plus/es/components/alert/index.mjs'

vi.mock('@/api/datasource', () => ({
  getDatasource: vi.fn(), createApiConfig: vi.fn(), getApiConfig: vi.fn(), updateApiConfig: vi.fn(),
  invokeApiConfigPreview: vi.fn(), createApiConfigTrial: vi.fn(), previewApiConfigRequest: vi.fn(),
  getApiConfigTrial: vi.fn(), resumeApiConfigTrial: vi.fn(), cancelApiConfigTrial: vi.fn(),
  listApiConfigTrials: vi.fn().mockResolvedValue({ data: [] }),
  listDatasources: vi.fn().mockResolvedValue({ data: { records: [] } }),
}))

afterEach(() => { vi.clearAllMocks() })

function createContext(overrides = {}) {
  const ctx = { $route: { params: {}, query: {} }, markConfigurationSaved: vi.fn() }
  Object.keys(ApiDetail.methods).forEach(name => { ctx[name] = ApiDetail.methods[name].bind(ctx) })
  Object.assign(ctx, ApiDetail.data.call(ctx), { initializing: false }, overrides)
  return ctx
}

describe('ApiDetail helpers', () => {
  test('已保存审批草稿的对象形链路配置可以重新打开编辑', async () => {
    const ctx = createContext({ initializationId: 1, $route: { path: '/datasource/api/20', params: { id: '20' } } })
    datasourceApi.getApiConfig.mockResolvedValueOnce({ data: { id: 20, executionConfig: JSON.stringify(emptyExecution()) } })
    const editable = vi.spyOn(governanceApi, 'editableResource').mockResolvedValueOnce({ id: 20, apiCode: 'Score', executionConfig: emptyExecution() })
    ctx.ensureSelectedDatasource = vi.fn().mockResolvedValue()
    ctx.loadApiPickerOptions = vi.fn().mockResolvedValue()
    try {
      await ctx.loadDetail(1)
      expect(ctx.form.apiCode).toBe('Score')
      expect(JSON.parse(ctx.form.executionConfig).version).toBe(2)
    } finally { editable.mockRestore() }
  })
  test('当前配置只保存请求字段和响应分支，原样保留协议名称、引用 ID 和响应路径', () => {
    const ctx = createContext()
    const spec = { ...emptyExecution(), requestFields: [
      { id: 'cookie', location: 'COOKIE', path: 'Session.Id', value: { kind: 'REFERENCE', refType: 'VARIABLE', refId: '9007199254740993', code: 'renamed' } },
      { id: 'json', location: 'JSON', path: 'Customer["Mobile.Number"]', value: { kind: 'LITERAL', valueType: 'STRING', value: '0001' } },
    ], responseBranches: [{ id: 'success', mode: 'VALUE', outputPath: 'data', value: { kind: 'PATH', value: 'response.body.Payload', protocolPath: true } }] }
    ctx.form.executionConfig = JSON.stringify(spec)
    const saved = ctx.normalizeForm(ctx.form)
    expect(JSON.parse(saved.executionConfig)).toEqual(spec)
    for (const key of ['headerConfig', 'queryConfig', 'requestMapping', 'responseMapping', 'bodyTemplate', 'asyncPollConfig']) expect(saved).not.toHaveProperty(key)
  })
  test('缺少当前执行配置时阻止保存，异步必须通过链路步骤配置', () => {
    const ctx = createContext()
    ctx.form.executionConfig = ''
    expect(() => ctx.normalizeForm(ctx.form)).toThrow('请求与响应')
    ctx.form.executionConfig = JSON.stringify(emptyExecution())
    ctx.form.requestMode = 'ASYNC'
    expect(() => ctx.normalizeForm(ctx.form)).toThrow('链路步骤')
    const spec = { ...emptyExecution(), steps: [{ id: 'query', type: 'HTTP', endpointUrl: '/query', requestFields: [], poll: { until: { type: 'group', children: [{ path: 'body.State', operator: '==', value: 'DONE' }] }, maxAttempts: 0 } }] }
    ctx.form.executionConfig = JSON.stringify(spec)
    expect(JSON.parse(ctx.normalizeForm(ctx.form).executionConfig).steps[0].poll.maxAttempts).toBe(0)
    expect(ApiDetail.computed.executionTabs.call(ctx).map(tab => tab.name)).toEqual(['request', 'response', 'steps', 'samples'])
  })
  test('保存具名样例保留整个当前配置，不直接修改正式 API', async () => {
    const ctx = createContext({ invokeParamsText: '{"Customer":{"Id":"A"}}', $message: { success: vi.fn(), error: vi.fn() } })
    await ctx.saveCurrentSample()
    expect(JSON.parse(ctx.form.executionConfig).samples[0].params).toEqual({ Customer: { Id: 'A' } })
    expect(datasourceApi.updateApiConfig).not.toHaveBeenCalled()
    expect(datasourceApi.createApiConfig).not.toHaveBeenCalled()
  })
  test('缓存接口详情不将其他页面的 ID 当作 API ID 加载', async () => {
    datasourceApi.listDatasources.mockResolvedValue({ data: { records: [] } })
    datasourceApi.getApiConfig.mockResolvedValue({ data: { id: 20, apiCode: 'ORIGINAL', executionConfig: JSON.stringify(emptyExecution()) } })
    const route = { name: 'ApiDetail', path: '/datasource/api/20', fullPath: '/datasource/api/20', params: { id: '20' }, query: {} }
    const wrapper = mount(ApiDetail, { mocks: { $route: route } })
    await flushPromises()
    datasourceApi.getApiConfig.mockClear()
    try {
      route.path = '/designer/ruleset/104'
      route.fullPath = route.path
      route.params.id = '104'
      await ApiDetail.watch['$route.fullPath'].call(wrapper.vm, route.fullPath, '/datasource/api/20')
      expect(datasourceApi.getApiConfig).not.toHaveBeenCalled()
      expect(wrapper.vm.form.apiCode).toBe('ORIGINAL')
    } finally { wrapper.unmount() }
  })

  test('接口详情离开页面后迟到结果不覆盖原表单', async () => {
    const ctx = createContext({
      initializationId: 1, detailRoutePath: '/datasource/api/20', routeActive: true,
      $route: { path: '/datasource/api/20', params: { id: '20' }, query: {} },
    })
    ctx.form.apiCode = 'ORIGINAL'
    let finish
    datasourceApi.getApiConfig.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    const pending = ctx.loadDetail(1)
    ctx.$route.path = '/rule/20'
    finish({ data: { id: 20, apiCode: 'LATE' } })
    await pending
    expect(ctx.form.apiCode).toBe('ORIGINAL')
  })

  test('旧试运行顶层阶段为引用对象时保留externalCall中的真实阶段数组且不触发Array警告', async () => {
    const wrapper = mount(ApiDetail, { mocks: { $route: { params: {}, query: {} } } })
    await flushPromises()
    console.warn.mockClear()
    const response = {
      traceSteps: { $ref: '$.externalCall.traceSteps' },
      externalCall: { traceSteps: [{ type: 'REQUEST_INPUT', label: '真实请求入参', status: 'SUCCESS' }, { type: 'EXTERNAL_RESPONSE', label: '真实外数响应', status: 'SUCCESS', output: { $ref: '业务普通字段原文' } }] },
    }
    const original = JSON.stringify(response)
    try {
      await wrapper.setData({ invokeResultText: original })
      expect(wrapper.findAll('.external-call-trace-step-title span').map(item => item.text())).toEqual(['真实请求入参', '真实外数响应'])
      expect(wrapper.find('.external-call-trace').text()).toContain('业务普通字段原文')
      expect(wrapper.vm.invokeResultText).toBe(original)
      expect(console.warn.mock.calls.filter(args => args.join(' ').includes('prop "steps"'))).toHaveLength(0)
    } finally { wrapper.unmount() }
  })

  test('空顶层阶段不能遮蔽实际阶段数组，缺失阶段仍显示可解释空态', async () => {
    const wrapper = mount(ApiDetail, { mocks: { $route: { params: {}, query: {} } } })
    await flushPromises()
    try {
      await wrapper.setData({ invokeResultText: JSON.stringify({ traceSteps: [], externalCall: { traceSteps: [{ label: '已保存阶段', status: 'SUCCESS' }] } }) })
      expect(wrapper.find('.external-call-trace').text()).toContain('已保存阶段')
      await wrapper.setData({ invokeResultText: JSON.stringify({ traceSteps: null, externalCall: { traceSteps: null } }) })
      expect(wrapper.find('.external-call-trace-empty').text()).toContain('历史记录未保存')
    } finally { wrapper.unmount() }
  })

  test('条件分支已配置重试时不再提示必须增加默认次数', async () => {
    const wrapper = mount(ApiDetail, { mocks: { $route: { params: {}, query: {} } } })
    await flushPromises()
    wrapper.vm.goToConfigTab('retry')
    await wrapper.setData({ form: { ...wrapper.vm.form, exceptionStrategy: 'RETRY', retryCount: 0 } })
    expect(wrapper.find('.strategy-warning').exists()).toBe(true)
    await wrapper.setData({ form: { ...wrapper.vm.form, executionConfig: JSON.stringify({ version: 2, retryBranches: [{ id: 'maintenance', retryCount: 2 }] }) } })
    expect(wrapper.find('.strategy-warning').exists()).toBe(false)
    await wrapper.setData({ form: { ...wrapper.vm.form, executionConfig: JSON.stringify({ version: 2, retryBranches: [{ id: 'maintenance', retryCount: 0 }] }) } })
    expect(wrapper.find('.strategy-warning').exists()).toBe(true)
    await wrapper.setData({ form: { ...wrapper.vm.form, exceptionStrategy: 'FAIL_FAST' } })
    expect(wrapper.find('.strategy-warning').exists()).toBe(false)
    wrapper.unmount()
  })

  test('异常重试缺少次数时在本地阻止提交，有分支次数时保留原配置', () => {
    const ctx = createContext()
    ctx.form.exceptionStrategy = 'RETRY'
    expect(() => ctx.normalizeForm(ctx.form)).toThrow('至少 1 次')
    ctx.form.executionConfig = JSON.stringify({ version: 2, retryBranches: [{ id: 'retry', retryCount: 1 }] })
    expect(ctx.normalizeForm(ctx.form).retryCount).toBe(0)
    ctx.form.executionConfig = JSON.stringify(emptyExecution())
    ctx.form.exceptionStrategy = 'FAIL_FAST'
    expect(ctx.normalizeForm(ctx.form).retryCount).toBe(0)
  })

  test('接口配置加载完成前不暴露可编辑的空表单，避免迟到响应覆盖输入', async () => {
    let finish
    datasourceApi.listDatasources.mockResolvedValue({ data: { records: [] } })
    datasourceApi.getApiConfig.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    const wrapper = mount(ApiDetail, { mocks: { $route: { params: { id: '20' }, query: {} } } })
    await flushPromises()
    try {
      expect(wrapper.find('.detail-form').exists()).toBe(false)
      expect(wrapper.find('[data-testid="api-detail-loading"]').exists()).toBe(true)
    } finally {
      finish({ data: { id: 20, endpointUrl: '/saved', apiCode: 'EXISTING_API', executionConfig: JSON.stringify(emptyExecution()) } })
      await flushPromises()
    }
    expect(wrapper.find('.detail-form').exists()).toBe(true)
    expect(wrapper.vm.form.endpointUrl).toBe('/saved')
    wrapper.unmount()
  })

  test('接口加载失败保留错误提示，重试成功后才恢复编辑', async () => {
    const ctx = createContext({ isCreateMode: false })
    ctx.loadDatasourceOptions = vi.fn().mockResolvedValue()
    ctx.loadDetail = vi.fn().mockRejectedValueOnce(new Error('接口暂不可用')).mockResolvedValue()
    await ctx.initializeRoute()
    expect(ctx.initializing).toBe(false)
    expect(ctx.initializationError).toBe('接口暂不可用')
    await ctx.initializeRoute()
    expect(ctx.initializationError).toBe('')
  })

  test('Token 业务失效条件可以保存并重新加载', () => {
    const ctx = createContext({
      tokenFailureMode: 'CUSTOM',
      tokenFailureConditionRoot: { type: 'group', operator: 'OR', children: [
        { path: 'httpStatus', operator: 'in', values: ['401', '498'] },
        { path: 'body.code', operator: '==', value: 'TOKEN_EXPIRED' }
      ] }
    })
    const data = ctx.normalizeForm(ctx.form)
    expect(JSON.parse(data.tokenFailureCondition).children).toHaveLength(2)
    ctx.form = data
    ctx.syncEditableRowsFromForm()
    expect(ctx.tokenFailureMode).toBe('CUSTOM')
    expect(ctx.tokenFailureConditionRoot.children[1].value).toBe('TOKEN_EXPIRED')
  })

  

  
  test('默认只展示业务配置并把稳定性与高级能力分层', () => {
    const ctx = createContext({ activeConfigGroup: 'business' })
    ctx.configTabs = ApiDetail.data.call(ctx).configTabs

    const visible = ApiDetail.computed.visibleConfigTabs.call(ctx)

    expect(visible.map(item => item.name)).toEqual([
      'auth', 'payloadCapture', 'test'
    ])
    ctx.visibleConfigTabs = visible
    ctx.switchConfigGroup('reliability')
    ctx.visibleConfigTabs = ApiDetail.computed.visibleConfigTabs.call(ctx)
    expect(ctx.visibleConfigTabs.map(item => item.name)).toEqual([
      'connection', 'retry', 'billing'
    ])
  })

  test('接口测试预览鉴权按实际配置区分 Token/OAuth 与其他鉴权', () => {
    const ctx = createContext({ datasourceOptions: [{ id: 8, authType: 'BASIC' }], form: { ...ApiDetail.methods.emptyForm(), datasourceId: 8, authMode: 'INHERIT' } })
    expect(ApiDetail.computed.effectivePreviewAuthMode.call(ctx)).toBe('BASIC')
    expect(ApiDetail.computed.previewTokenRequired.call(ctx)).toBe(false)
    const oauth = createContext({ datasourceOptions: [], form: { ...ApiDetail.methods.emptyForm(), authMode: 'OAUTH2' } })
    expect(ApiDetail.computed.effectivePreviewAuthMode.call(oauth)).toBe('OAUTH2')
    expect(ApiDetail.computed.previewTokenRequired.call(oauth)).toBe(true)
  })

  

  test('异步接口只使用多步链路，不显示旧异步配置入口', () => {
    const ctx = createContext({
      activeConfigGroup: 'reliability',
      form: {
        ...ApiDetail.methods.emptyForm(),
        requestMode: 'ASYNC'
      }
    })
    ctx.configTabs = ApiDetail.data.call(ctx).configTabs

    expect(ApiDetail.computed.visibleConfigTabs.call(ctx).map(item => item.name))
      .toEqual(['connection', 'retry', 'billing'])
  })

  test('配置检查可把用户带到对应配置项', () => {
    const ctx = createContext({
      activeConfigGroup: 'business',
      activeConfigTab: 'auth'
    })
    ctx.configTabs = ApiDetail.data.call(ctx).configTabs

    ctx.goToConfigTab('retry')

    expect(ctx.activeConfigGroup).toBe('reliability')
    expect(ctx.activeConfigTab).toBe('retry')
  })

  test('返回外数列表时保留项目上下文', () => {
    const push = vi.fn()
    const ctx = createContext({
      contextProjectId: 9,
      $router: { push }
    })

    ctx.handleBack()

    expect(push).toHaveBeenCalledWith({
      path: '/datasource',
      query: { tab: 'api', projectId: 9 }
    })
  })

  test('loadDatasourceOptions only requests published active datasources for configuration', async () => {
    const publishedDatasource = { id: 2, datasourceCode: 'icekredit_qingyun', status: 1 }
    datasourceApi.listDatasources.mockResolvedValue({ data: { records: [publishedDatasource] } })
    const ctx = createContext()

    await ctx.loadDatasourceOptions()

    expect(datasourceApi.listDatasources).toHaveBeenCalledWith({ pageNum: 1, pageSize: 20, status: 1 })
    expect(ctx.datasourceOptions).toEqual([publishedDatasource])
  })

  

  

  test('initializeRoute resets form and reloads detail when route switches to edit mode', async () => {
    const ctx = createContext({
      isCreateMode: false,
      activeConfigTab: 'request',
      invokeResultText: '{"old":true}',
      form: {
        ...ApiDetail.methods.emptyForm(),
        apiCode: 'old_api'
      }
    })
    ctx.loadDetail = vi.fn().mockResolvedValue()
    ctx.loadDatasourceOptions = vi.fn().mockResolvedValue()

    await ctx.initializeRoute()

    expect(ctx.form.apiCode).toBe('')
    expect(ctx.activeConfigTab).toBe('auth')
    expect(ctx.invokeResultText).toBe('')
    expect(ctx.loadDetail).toHaveBeenCalled()
  })

  

  

  

  

  test('cache key config keeps selected component order and requires every component', () => {
    const ctx = createContext({
      cacheKeyRows: [{ path: 'customer.name' }, { path: 'customer.idCard' }, { path: 'customer.mobile' }]
    })

    expect(ctx.buildCacheKeyConfig()).toEqual({
      components: [
        { path: 'customer.name' },
        { path: 'customer.idCard' },
        { path: 'customer.mobile' }
      ]
    })
  })

  test('request success and billing hit serialize nested condition trees', () => {
    const conditionRoot = {
      type: 'group',
      operator: 'AND',
      children: [
        { type: 'condition', path: 'httpStatus', operator: 'starts_with', value: '2' },
        {
          type: 'group',
          operator: 'OR',
          children: [
            { type: 'condition', path: 'body.code', operator: 'in', values: ['2000', '2001'] },
            { type: 'condition', path: 'body.message', operator: 'regex', value: '^SUCCESS.*' }
          ]
        }
      ]
    }
    const queryCtx = createContext({
      successConditionRoot: conditionRoot,
      billingConfig: { mode: 'QUERY', conditionRoot: ApiDetail.methods.emptyApiConditionRoot() }
    })
    const hitCtx = createContext({
      billingConfig: { mode: 'HIT', conditionRoot }
    })

    expect(queryCtx.buildSuccessConditionConfig()).toEqual(conditionRoot)
    expect(queryCtx.buildBillingConditionConfig()).toEqual({ mode: 'QUERY' })
    expect(hitCtx.buildBillingConditionConfig()).toEqual({
      mode: 'HIT',
      condition: conditionRoot
    })
  })

  test('business retry condition serializes a nested response condition tree', () => {
    const retryConditionRoot = {
      type: 'group',
      operator: 'AND',
      children: [
        { type: 'condition', path: 'httpStatus', operator: '==', value: '200' },
        {
          type: 'group',
          operator: 'OR',
          children: [
            { type: 'condition', path: 'body.response_code', operator: '==', value: '10000429' },
            { type: 'condition', path: 'body.retryable', operator: 'is_true', value: '' }
          ]
        }
      ]
    }
    const ctx = createContext({ retryConditionRoot })

    expect(ctx.buildRetryConditionConfig()).toEqual(retryConditionRoot)
    ctx.syncStructuredConfigToForm()
    expect(JSON.parse(ctx.form.retryCondition)).toEqual(retryConditionRoot)
  })

  test('token api can write provider token to a custom header without prefix', () => {
    const ctx = createContext()
    ctx.form.authMode = 'TOKEN_API'
    ctx.apiAuthConfig = {
      ...ApiDetail.methods.emptyAuthConfig('TOKEN_API'),
      tokenUrl: '/api/login.do',
      tokenPath: 'body.token_id',
      tokenHeaderName: 'token_id',
      tokenPrefix: ''
    }

    expect(JSON.parse(ctx.buildApiAuthConfig())).toMatchObject({
      tokenUrl: '/api/login.do',
      tokenPath: 'body.token_id',
      tokenPlacement: 'HEADER',
      tokenHeaderName: 'token_id',
      tokenPrefix: ''
    })
  })

  test('token api defaults to no prefix and keeps user-entered spaces', () => {
    const ctx = createContext()
    ctx.form.authMode = 'TOKEN_API'
    ctx.apiAuthConfig.tokenPrefix = 'Custom  '

    const config = JSON.parse(ctx.buildApiAuthConfig())

    expect(config.tokenPrefix).toBe('Custom  ')
    expect(ApiDetail.methods.emptyAuthConfig('TOKEN_API').tokenPrefix).toBe('')
  })

  test('normalizeForm allows enabled cache without configured key components', () => {
    const ctx = createContext({ cacheKeyRows: [ApiDetail.methods.emptyCacheKeyRow()] })
    ctx.form.responseCacheSeconds = 60

    const normalized = ctx.normalizeForm(ctx.form)
    expect(normalized.responseCacheSeconds).toBe(60)
    expect(normalized.cacheKeyConfig).toBeNull()
  })

  test('报文留存配置按请求和响应分别保存并重新加载', () => {
    const ctx = createContext({
      payloadCapture: {
        request: {
          source: 'PROCESSED',
          excludePaths: ['$.items[*].Base64'],
          maxFieldBytes: 4096,
          oversizedFields: []
        },
        response: {
          source: 'ORIGINAL',
          excludePaths: ["$['含点.字段']"],
          maxFieldBytes: 0,
          oversizedFields: []
        }
      }
    })

    const normalized = ctx.normalizeForm(ctx.form)
    expect(JSON.parse(normalized.payloadCaptureConfig)).toEqual({
      request: { ...ctx.payloadCapture.request, saveOriginal: true },
      response: { ...ctx.payloadCapture.response, saveOriginal: true }
    })
    ctx.form = normalized
    ctx.syncPayloadCaptureFromForm()
    expect(ctx.payloadCapture.request.source).toBe('PROCESSED')
    expect(ctx.payloadCapture.request.excludePaths).toEqual(['$.items[*].Base64'])
    expect(ctx.payloadCapture.response.excludePaths).toEqual(["$['含点.字段']"])
    expect(ctx.payloadCapture.request.decrypt).toEqual({ enabled: false, mode: 'BASE64', path: '$', keyVariable: '' })
    expect(ctx.payloadCapture.response.decrypt.enabled).toBe(false)
  })

  test('报文留存路径和大小校验阻止错误配置保存', () => {
    const ctx = createContext()
    ctx.payloadCapture.request.excludePaths = ['$.items[bad]']
    expect(() => ctx.normalizeForm(ctx.form)).toThrow('请求排除路径格式不合法')
    ctx.payloadCapture.request.excludePaths = []
    ctx.payloadCapture.response.maxFieldBytes = -1
    expect(() => ctx.normalizeForm(ctx.form)).toThrow('响应单字段上限')
  })

  

  

  

  

  test('buildApiInvokeParamTemplate includes ID-bound unified API request operands', () => {
    const ctx = createContext({ form: { ...ApiDetail.methods.emptyForm() } })
    const row = {
      executionConfig: JSON.stringify({
        version: 2,
        requestFields: [
          { id: 'mobile', location: 'JSON', path: 'mobile', value: { kind: 'REFERENCE', refType: 'VARIABLE', refId: 7, code: 'customer.mobile' } },
          { id: 'score', location: 'JSON', path: 'score', defaultValue: { kind: 'REFERENCE', refType: 'VARIABLE', refId: 8, code: 'risk.score' } }
        ]
      })
    }

    expect(JSON.parse(ctx.buildApiInvokeParamTemplate(row))).toEqual({
      customer: { mobile: '' },
      risk: { score: '' }
    })
  })

  test('共享目录加载对象字段并保留稳定 ID', async () => {
    const ctx = createContext()
    const requestNode = {
      object: { id: 10, scriptName: 'customer' },
      flatVariables: [
        { id: 11, scriptName: 'customer.idNo', varLabel: '证件号', varType: 'STRING' }
      ]
    }
    dataObjectApi.getVariableTree.mockResolvedValueOnce({
      data: {
        objectIdMap: { 10: requestNode },
        tree: [requestNode]
      }
    })

    await ctx.loadApiPickerOptions(1)
    expect(ctx.apiPickerVars).toContainEqual(expect.objectContaining({ _varId: 11, _refType: 'DATA_OBJECT', varCode: 'customer.idNo' }))
  })

  test('script variables are saved independently from auth mode', () => {
    const ctx = createContext({
      form: { ...ApiDetail.methods.emptyForm(), authMode: 'NONE' },
      scriptVariableRows: [
        { name: 'appKey', value: 'A001', sensitive: false },
        { name: 'secret_key', value: 'S001', sensitive: true }
      ]
    })

    expect(JSON.parse(ctx.buildApiAuthConfig())).toEqual({
      scriptVariables: [
        { name: 'appKey', value: 'A001', sensitive: false },
        { name: 'secret_key', value: 'S001', sensitive: true }
      ]
    })
  })

  test('saved script variables load without changing names', () => {
    const ctx = createContext()
    ctx.form.authMode = 'NONE'
    ctx.form.authApiConfig = '{"scriptVariables":[{"name":"appKey","value":"A001","sensitive":false},{"name":"secret_key","value":"S001","sensitive":true}]}'

    ctx.syncAuthConfigFromForm()

    expect(ctx.scriptVariableRows).toEqual([
      { name: 'appKey', value: 'A001', sensitive: false },
      { name: 'secret_key', value: 'S001', sensitive: true }
    ])
  })

  test('script variable validation rejects duplicate and invalid names', () => {
    const ctx = createContext({
      scriptVariableRows: [
        { name: 'app-key', value: 'A', sensitive: false },
        { name: 'app-key', value: 'B', sensitive: true }
      ]
    })

    expect(() => ctx.buildScriptVariables()).toThrow('脚本变量名')
  })

  test('online invocation executes the current api draft used to generate parameters', async () => {
    const ctx = createContext({
      form: { ...ApiDetail.methods.emptyForm(), id: 8, datasourceId: 2, endpointUrl: '/draft-score' },
      invokeParamsText: '{"customer":{"idNo":"A001"}}',
      invokeLoading: false,
      invokeResultText: '',
      $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
    })
    const draft = {
      id: 8,
      datasourceId: 2,
      endpointUrl: '/draft-score',
      payloadCaptureConfig: JSON.stringify({
        request: { source: 'PROCESSED', excludePaths: ['$.secret'], maxFieldBytes: 2048 },
        response: { source: 'ORIGINAL', excludePaths: [], maxFieldBytes: 0 }
      })
    }
    ctx.normalizeForm = vi.fn().mockReturnValue(draft)
    datasourceApi.invokeApiConfigPreview.mockResolvedValue({ data: { success: true } })

    await ctx.runInvokeApi()

    expect(datasourceApi.invokeApiConfigPreview).toHaveBeenCalledWith(8, {
      config: draft,
      params: { customer: { idNo: 'A001' } }
    })
    expect(ctx.invokeResultText).toContain('"success": true')
  })

  test('新接口在没有正式ID时可试运行当前完整草稿，且不创建资源或审批', async () => {
    const ctx = createContext({
      form: { ...ApiDetail.methods.emptyForm(), datasourceId: 2, apiCode: 'Exact_API_Code', apiName: '待测试接口', endpointUrl: '/draft-score' },
      invokeParamsText: '{"客户":{"idNo":"A001"}}', invokeResultText: '', initializationId: 1,
      $refs: { form: { validate: vi.fn().mockImplementation(callback => { callback?.(true); return Promise.resolve(true) }) } },
      $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    })
    datasourceApi.createApiConfigTrial.mockResolvedValueOnce({ data: { trialId: 'trial-a', testMode: 'DRAFT', status: 'SUCCEEDED', result: { success: true, traceSteps: [] } } })
    await ctx.runInvokeApi()
    expect(datasourceApi.createApiConfigTrial).toHaveBeenCalledWith({
      requestKey: expect.any(String),
      config: expect.objectContaining({ datasourceId: 2, apiCode: 'Exact_API_Code', apiName: '待测试接口', endpointUrl: '/draft-score' }),
      params: { 客户: { idNo: 'A001' } },
    })
    expect(datasourceApi.invokeApiConfigPreview).not.toHaveBeenCalled()
    expect(datasourceApi.createApiConfig).not.toHaveBeenCalled()
    expect(datasourceApi.updateApiConfig).not.toHaveBeenCalled()
    expect(ctx.form.id).toBeNull()
    expect(ctx.trial.trialId).toBe('trial-a')
    expect(ctx.invokeResultText).toContain('"success": true')
  })

  test.each(['[]', '123', 'null'])('测试参数必须为JSON对象，%s不能被服务端静默当空参数', async invokeParamsText => {
    const ctx = createContext({
      form: { ...ApiDetail.methods.emptyForm(), id: 8, datasourceId: 2, endpointUrl: '/score' },
      invokeParamsText, invokeResultText: '',
      $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    })
    datasourceApi.invokeApiConfigPreview.mockResolvedValue({ data: { success: true } })
    await ctx.runInvokeApi()
    expect(datasourceApi.invokeApiConfigPreview).not.toHaveBeenCalled()
    expect(ctx.$message.error).toHaveBeenCalledWith(expect.stringContaining('JSON 对象'))
  })

  test('请求预览未返回时切换接口，旧预览不能覆盖新配置的结果', async () => {
    let finish
    const ctx = createContext({
      form: { ...ApiDetail.methods.emptyForm(), id: 8, datasourceId: 2, endpointUrl: '/before' },
      invokeParamsText: '{}', requestPreviewText: '', previewLoading: false, initializationId: 1,
      $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    })
    datasourceApi.previewApiConfigRequest.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const pending = ctx.runRequestPreview()
    ctx.initializationId++
    ctx.form = { ...ctx.form, id: 9, endpointUrl: '/after' }
    ctx.requestPreviewText = '{"url":"new-preview"}'
    finish({ data: { url: 'old-preview', networkCalled: false } })
    await pending
    expect(ctx.requestPreviewText).toBe('{"url":"new-preview"}')
  })

  test('试运行期间修改参数，迟到响应不得作为当前输入的结果显示', async () => {
    let finish
    const ctx = createContext({
      form: { ...ApiDetail.methods.emptyForm(), id: 8, datasourceId: 2, endpointUrl: '/score' },
      invokeParamsText: '{"amount":1}', invokeResultText: '', initializationId: 1,
      $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    })
    datasourceApi.invokeApiConfigPreview.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const pending = ctx.runInvokeApi()
    ctx.invokeParamsText = '{"amount":2}'
    finish({ data: { success: true, body: { result: 'FOR_AMOUNT_1' } } })
    await pending
    expect(ctx.invokeResultText).not.toContain('FOR_AMOUNT_1')
  })

  test('新建试运行网络不确定后重试沿用requestKey，不自动发起第二份供应商调用', async () => {
    const ctx = createContext({
      form: { ...ApiDetail.methods.emptyForm(), datasourceId: 2, apiCode: 'Draft', apiName: '草稿', endpointUrl: '/score' },
      invokeParamsText: '{}', initializationId: 1,
      $refs: { form: { validate: vi.fn().mockImplementation(callback => { callback?.(true); return Promise.resolve(true) }) } },
      $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    })
    datasourceApi.createApiConfigTrial.mockRejectedValueOnce(new Error('网络中断')).mockResolvedValueOnce({ data: {
      trialId: 'trial-uncertain', status: 'WAITING', canResume: true, canCancel: true, result: null,
    } })
    await ctx.runInvokeApi()
    const key = datasourceApi.createApiConfigTrial.mock.calls[0][0].requestKey
    expect(key).toBeTruthy()
    await ctx.runInvokeApi()
    expect(datasourceApi.createApiConfigTrial.mock.calls[1][0].requestKey).toBe(key)
    expect(ctx.trial.trialId).toBe('trial-uncertain')
    expect(ctx.form.id).toBeNull()
  })

  test('继续等待中的会话只按trialId恢复原快照，不发送当前修改后的配置', async () => {
    const ctx = createContext({
      form: { ...ApiDetail.methods.emptyForm(), datasourceId: 2, apiCode: '已经改名', endpointUrl: '/changed' },
      initializationId: 1, trial: { trialId: 'trial-wait', status: 'WAITING', canResume: true, canCancel: true },
    })
    datasourceApi.resumeApiConfigTrial.mockResolvedValueOnce({ data: { trialId: 'trial-wait', status: 'SUCCEEDED', canResume: false, canCancel: false, result: { success: true, body: 'original-snapshot' } } })
    await ctx.resumeTrial()
    expect(datasourceApi.resumeApiConfigTrial).toHaveBeenCalledWith('trial-wait')
    expect(datasourceApi.createApiConfigTrial).not.toHaveBeenCalled()
    expect(ctx.form.apiCode).toBe('已经改名')
    expect(ctx.trial.status).toBe('SUCCEEDED')
  })

  test('最近会话只恢复结果和控制权，不回填当前编辑配置或正式ID', async () => {
    const ctx = createContext({ form: { ...ApiDetail.methods.emptyForm(), apiCode: 'Current_API' }, initializationId: 1 })
    datasourceApi.getApiConfigTrial.mockResolvedValueOnce({ data: { trialId: 'trial-old', apiName: '旧快照', status: 'WAITING', canResume: true, canCancel: true, result: { body: 'old-snapshot' } } })
    await ctx.selectTrial('trial-old')
    expect(ctx.form.apiCode).toBe('Current_API')
    expect(ctx.form.id).toBeNull()
    expect(ctx.trialRecovered).toBe(true)
    expect(ctx.trial.trialId).toBe('trial-old')
  })

  test('UNKNOWN会话不可恢复，取消按服务端终态停止后续操作', async () => {
    const ctx = createContext({ initializationId: 1, trial: { trialId: 'trial-unknown', status: 'UNKNOWN', canResume: false, canCancel: true } })
    await ctx.resumeTrial()
    expect(datasourceApi.resumeApiConfigTrial).not.toHaveBeenCalled()
    datasourceApi.cancelApiConfigTrial.mockResolvedValueOnce({ data: { trialId: 'trial-unknown', status: 'CANCELLED', canResume: false, canCancel: false } })
    await ctx.cancelTrial()
    expect(datasourceApi.cancelApiConfigTrial).toHaveBeenCalledWith('trial-unknown')
    expect(ctx.trial.status).toBe('CANCELLED')
    expect(ctx.trial.canResume).toBe(false)
  })

  test('表单异步校验期间切换页面，旧按钮动作不能在新配置上开始试运行', async () => {
    let finishValidation
    const ctx = createContext({
      form: { ...ApiDetail.methods.emptyForm(), datasourceId: 2, apiCode: 'Before', apiName: '原配置', endpointUrl: '/before' },
      invokeParamsText: '{}', initializationId: 1,
      $refs: { form: { validate: vi.fn().mockReturnValue(new Promise(resolve => { finishValidation = resolve })) } },
      $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    })
    datasourceApi.createApiConfigTrial.mockResolvedValue({ data: { trialId: 'unexpected', status: 'SUCCEEDED' } })
    const running = ctx.runInvokeApi()
    ctx.initializationId++
    ctx.form = { ...ctx.form, apiCode: 'After', apiName: '新配置', endpointUrl: '/after' }
    finishValidation(true)
    await running
    expect(datasourceApi.createApiConfigTrial).not.toHaveBeenCalled()
  })

  test('真实按钮允许审批前试运行，找回UNKNOWN会话显示原快照提示并禁用恢复', async () => {
    const wrapper = mount(ApiDetail, { global: {
      mocks: { $route: { params: {}, query: {} } },
      stubs: { 'el-button': ElButton, 'el-alert': ElAlert },
    } })
    try {
      await flushPromises()
      await wrapper.setData({ form: { ...wrapper.vm.form, datasourceId: 2, apiCode: 'Trial_Code', apiName: '待审批', endpointUrl: '/score' } })
      wrapper.vm.goToConfigTab('test')
      await flushPromises()
      const start = wrapper.findAll('button').find(button => button.text() === '草稿试运行')
      expect(start.exists()).toBe(true)
      expect(start.element.disabled).toBe(false)
      await wrapper.setData({ trial: { trialId: 'unknown-ui', status: 'UNKNOWN', canResume: false, canCancel: true }, trialRecovered: true })
      expect(wrapper.find('[data-testid="api-trial-state"]').text()).toContain('状态待核实')
      expect(wrapper.find('[data-testid="api-trial-resume"]').element.disabled).toBe(true)
      expect(wrapper.findAll('.el-alert').some(alert => alert.text().includes('找回会话不会改写当前编辑表单'))).toBe(true)
      expect(wrapper.find('[data-testid="api-trial-state"]').text()).toContain('不会自动重发')
    } finally { wrapper.unmount() }
  })

  test.each(['CANCELLED', 'FAILED', 'UNKNOWN'])('真实Alert按%s状态区分取消前历史提示与当前错误，保留后台原文', async status => {
    const errorMessage = '外数链路等待超时，待恢复步骤：callback_step'
    const wrapper = mount(ApiDetail, { global: {
      mocks: { $route: { params: {}, query: {} } },
      stubs: { 'el-button': ElButton, 'el-alert': ElAlert },
    } })
    try {
      await flushPromises()
      wrapper.vm.goToConfigTab('test')
      await wrapper.setData({ trial: { trialId: 'history-hint', status, errorMessage, canResume: false, canCancel: false }, trialRecovered: true })
      const diagnostic = wrapper.findAll('.el-alert').find(alert => alert.text().includes(errorMessage))
      expect(diagnostic).toBeDefined()
      expect(diagnostic.classes()).toContain(status === 'CANCELLED' ? 'el-alert--info' : 'el-alert--warning')
      expect(wrapper.vm.trial.errorMessage).toBe(errorMessage)
      expect(wrapper.find('[data-testid="api-trial-resume"]').element.disabled).toBe(true)
      if (status === 'CANCELLED') {
        expect(diagnostic.text()).toContain('取消前的提示（仅供查看）')
        expect(wrapper.find('[data-testid="api-trial-state"]').text()).not.toContain('继续操作仍使用')
      } else expect(diagnostic.text()).not.toContain('取消前的提示')
    } finally { wrapper.unmount() }
  })

  test('执行中只定时GET状态，变为UNKNOWN后停止查询且不自动恢复或重发', async () => {
    vi.useFakeTimers()
    const ctx = createContext({ initializationId: 1, trial: { trialId: 'running', status: 'RUNNING' } })
    try {
      datasourceApi.getApiConfigTrial.mockResolvedValueOnce({ data: { trialId: 'running', status: 'RUNNING', canResume: false } })
        .mockResolvedValueOnce({ data: { trialId: 'running', status: 'UNKNOWN', canResume: false, canCancel: false } })
      await ctx.refreshTrial()
      expect(datasourceApi.getApiConfigTrial).toHaveBeenCalledTimes(1)
      await vi.advanceTimersByTimeAsync(1500)
      expect(ctx.trial.status).toBe('UNKNOWN')
      await vi.advanceTimersByTimeAsync(4500)
      expect(datasourceApi.getApiConfigTrial).toHaveBeenCalledTimes(2)
      expect(datasourceApi.createApiConfigTrial).not.toHaveBeenCalled()
      expect(datasourceApi.resumeApiConfigTrial).not.toHaveBeenCalled()
    } finally { ctx.stopTrialPolling(); vi.useRealTimers() }
  })

  test('离开原编辑上下文后迟到查询不覆盖新会话或重启轮询', async () => {
    let finish
    const ctx = createContext({ initializationId: 1, trial: { trialId: 'old', status: 'RUNNING' } })
    datasourceApi.getApiConfigTrial.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const old = ctx.refreshTrial()
    ctx.initializationId++
    ctx.invalidateTestRequests()
    ctx.trial = { trialId: 'new', status: 'WAITING', canResume: true }
    finish({ data: { trialId: 'old', status: 'RUNNING' } })
    await old
    expect(ctx.trial.trialId).toBe('new')
    expect(ctx.trialPollTimer).toBeNull()
    expect(ctx.trialLoading).toBe(false)
  })

  test('最近列表加载失败不会被当前会话的成功查询静默清除', async () => {
    const ctx = createContext({ initializationId: 1, trial: { trialId: 'current', status: 'WAITING' } })
    datasourceApi.listApiConfigTrials.mockRejectedValueOnce(new Error('历史列表暂不可用'))
    await ctx.loadTrialHistory()
    datasourceApi.getApiConfigTrial.mockResolvedValueOnce({ data: { trialId: 'current', status: 'WAITING', canResume: true } })
    await ctx.refreshTrial()
    expect(ctx.trialHistoryError).toBe('历史列表暂不可用')
    expect(ctx.trialError).toBe('')
  })

  test('试运行创建与到期时间以本地年月日时分秒展示，协议毫秒原值保持不变', async () => {
    const createdAt = new Date(2026, 9, 9, 2, 34, 56).getTime()
    const expiresAt = new Date(2026, 9, 9, 3, 4, 56).getTime()
    const trial = { trialId: 'dated-trial', apiName: '日期可读', status: 'SUCCEEDED', createdAt, expiresAt, canResume: false, canCancel: false }
    const wrapper = mount(ApiDetail, { global: { mocks: { $route: { params: {}, query: {} } } } })
    try {
      await flushPromises()
      wrapper.vm.goToConfigTab('test')
      await wrapper.setData({ trial, trialHistory: [trial] })
      expect(wrapper.vm.trialHistoryLabel(trial)).toContain('2026-10-09 02:34:56')
      expect(wrapper.find('[data-testid="api-trial-state"]').text()).toContain('有效期至 2026-10-09 03:04:56')
      expect(wrapper.find('[data-testid="api-trial-state"]').text()).not.toContain(String(expiresAt))
      expect(wrapper.vm.trial.createdAt).toBe(createdAt)
      expect(wrapper.vm.trial.expiresAt).toBe(expiresAt)
    } finally { wrapper.unmount() }
  })

  test('试运行非法或缺失时间使用空占位，毫秒字符串和epoch零值仍能显示', () => {
    const ctx = createContext()
    for (const value of [undefined, null, '', 'not-a-time', Infinity, {}]) expect(ctx.formatTrialTime(value)).toBe('—')
    const time = new Date(2026, 9, 9, 2, 34, 56).getTime()
    expect(ctx.formatTrialTime(String(time))).toBe('2026-10-09 02:34:56')
    expect(ctx.formatTrialTime(0)).not.toBe('—')
  })

  

  test('real invocation warning explains network and cost impact next to the test controls', async () => {
    const wrapper = mount(ApiDetail, {
      global: {
        mocks: { $route: { params: {}, query: {} } }
      }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('执行测试会真实访问外部接口')
    expect(wrapper.text()).toContain('生成请求预览')
    wrapper.unmount()
  })

  test('request preview uses non-network preparation endpoint', async () => {
    const ctx = createContext({
      form: { ...ApiDetail.methods.emptyForm(), id: 8, datasourceId: 2, endpointUrl: '/draft-score' },
      invokeParamsText: '{"mobile_no":"13800138000"}',
      previewLoading: false,
      requestPreviewText: '',
      previewToken: 'token-placeholder',
      $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
    })
    const draft = { id: 8, datasourceId: 2, endpointUrl: '/draft-score' }
    ctx.normalizeForm = vi.fn().mockReturnValue(draft)
    datasourceApi.previewApiConfigRequest.mockResolvedValue({ data: { networkCalled: false } })

    await ctx.runRequestPreview()

    expect(datasourceApi.previewApiConfigRequest).toHaveBeenCalledWith(8, {
      config: draft,
      params: { mobile_no: '13800138000' },
      previewToken: 'token-placeholder'
    })
    expect(ctx.requestPreviewText).toContain('"networkCalled": false')
  })
})
describe('ApiDetail resilience settings', () => {
  test('new API contains bounded connection, flow, retry, circuit and cache defaults', () => {
    const form = ApiDetail.methods.emptyForm()

    expect(form).toEqual(expect.objectContaining({
      maxConnections: 100,
      maxConnectionsPerRoute: 100,
      connectionRequestTimeoutMs: 100,
      connectTimeoutMs: 500,
      readTimeoutMs: 3000,
      qpsLimit: null,
      maxConcurrent: 50,
      tokenRefreshAheadSeconds: 60,
      retryStatusCodes: '502,503,504',
      retryCondition: '',
      retryOnConnectionError: 1,
      retryOnTimeout: 0,
      circuitBreakerEnabled: 1,
      responseCacheMaxSize: 10000,
      responseCacheRedisEnabled: 0,
      staleCacheSeconds: 0
    }))
  })
})
