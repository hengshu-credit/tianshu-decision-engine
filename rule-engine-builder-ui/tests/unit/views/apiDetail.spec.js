import ApiDetail from '@/views/datasource/ApiDetail.vue'
import { mount, flushPromises } from '@test-utils'
import * as dataObjectApi from '@/api/dataObject'
import * as datasourceApi from '@/api/datasource'

vi.mock('@/api/datasource', () => ({
  createApiConfig: vi.fn(), getApiConfig: vi.fn(), updateApiConfig: vi.fn(),
  invokeApiConfigPreview: vi.fn(), previewApiConfigRequest: vi.fn(),
  listDatasources: vi.fn().mockResolvedValue({ data: { records: [] } }),
}))

afterEach(() => { vi.clearAllMocks() })

function createContext(overrides = {}) {
  const ctx = {
    datasourceOptions: [],
    dataObjectOptions: [],
    dataObjectTree: [],
    form: ApiDetail.methods.emptyForm(),
    headerRows: [ApiDetail.methods.emptyNameValueRow()],
    queryRows: [ApiDetail.methods.emptyNameValueRow()],
    requestMappingRows: [ApiDetail.methods.emptyRequestMappingRow()],
    responseMappingRows: [ApiDetail.methods.emptyResponseMappingRow()],
    responseConditionRows: [],
    requestBodyMode: 'MAPPING',
    responseMappingMode: 'MAPPING',
    requestMappingJsonText: '{}',
    responseMappingJsonText: '{}',
    syncingMapping: false,
    apiAuthConfig: ApiDetail.methods.emptyAuthConfig('INHERIT'),
    scriptVariableRows: [ApiDetail.methods.emptyScriptVariableRow()],
    cacheKeyRows: [ApiDetail.methods.emptyCacheKeyRow()],
    successConditionRoot: ApiDetail.methods.emptyApiConditionRoot('httpStatus', 'starts_with', '2'),
    retryConditionRoot: ApiDetail.methods.emptyApiConditionRoot('body.response_code', '==', '10000429'),
    billingConfig: ApiDetail.methods.emptyBillingConfig(),
    asyncShared: ApiDetail.methods.emptyAsyncShared(),
    asyncPollConfig: ApiDetail.methods.emptyAsyncPollConfig(),
    asyncCallbackConfig: ApiDetail.methods.emptyAsyncCallbackConfig(),
    $route: { params: {}, query: {} },
    ...overrides
  }
  Object.keys(ApiDetail.methods).forEach(name => {
    ctx[name] = ApiDetail.methods[name].bind(ctx)
  })
  return ctx
}

describe('ApiDetail helpers', () => {
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
      finish({ data: { id: 20, endpointUrl: '/saved', apiCode: 'EXISTING_API' } })
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

  test('提交任务号与回调任务号配置分别保存，不互相覆盖', () => {
    const ctx = createContext({
      form: { ...ApiDetail.methods.emptyForm(), requestMode: 'ASYNC', asyncResultMode: 'CALLBACK', asyncCallbackUrl: 'https://engine.example/api/external-callback/${invocationId}' },
      asyncShared: { taskIdPath: 'body.receipt.id' },
      asyncCallbackConfig: { ...ApiDetail.methods.emptyAsyncCallbackConfig(), taskIdPath: 'body.report.job', signatureHeader: 'X-Signature', signatureSecret: 'test-secret' }
    })
    const data = ctx.normalizeForm(ctx.form)
    expect(JSON.parse(data.asyncCallbackConfig)).toMatchObject({ submissionTaskIdPath: 'body.receipt.id', taskIdPath: 'body.report.job' })
    ctx.form = data
    ctx.syncAsyncConfigFromForm()
    expect(ctx.asyncShared.taskIdPath).toBe('body.receipt.id')
    expect(ctx.asyncCallbackConfig.taskIdPath).toBe('body.report.job')
  })

  test('轮询请求体与失败状态能保存，缺少结果查询地址时阻止保存', () => {
    const ctx = createContext({
      form: { ...ApiDetail.methods.emptyForm(), requestMode: 'ASYNC', asyncResultMode: 'POLL' },
      asyncPollRequestText: '{"requestMapping":{"job":"$.taskId"}}'
    })
    expect(() => ctx.normalizeForm(ctx.form)).toThrow('结果查询地址')
    ctx.asyncPollConfig.resultEndpointUrl = '/query'
    ctx.asyncPollConfig.requestMethod = 'POST'
    const data = ctx.normalizeForm(ctx.form)
    expect(JSON.parse(data.asyncPollConfig)).toMatchObject({ requestMapping: { job: '$.taskId' }, failureValue: 'FAILED' })
  })
  test('默认只展示业务配置并把稳定性与高级能力分层', () => {
    const ctx = createContext({ activeConfigGroup: 'business' })
    ctx.configTabs = ApiDetail.data.call(ctx).configTabs

    const visible = ApiDetail.computed.visibleConfigTabs.call(ctx)

    expect(visible.map(item => item.name)).toEqual([
      'auth', 'headers', 'query', 'request', 'response', 'test'
    ])
    ctx.visibleConfigTabs = visible
    ctx.switchConfigGroup('reliability')
    ctx.visibleConfigTabs = ApiDetail.computed.visibleConfigTabs.call(ctx)
    expect(ctx.visibleConfigTabs.map(item => item.name)).toEqual([
      'connection', 'retry', 'billing'
    ])
  })

  test('异步接口在稳定性分组中显示回调配置', () => {
    const ctx = createContext({
      activeConfigGroup: 'reliability',
      form: {
        ...ApiDetail.methods.emptyForm(),
        requestMode: 'ASYNC'
      }
    })
    ctx.configTabs = ApiDetail.data.call(ctx).configTabs

    expect(ApiDetail.computed.visibleConfigTabs.call(ctx).map(item => item.name))
      .toEqual(['connection', 'async', 'retry', 'billing'])
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

  test('loadDatasourceOptions includes disabled datasources for configuration', async () => {
    const disabledDatasource = { id: 2, datasourceCode: 'icekredit_qingyun', status: 0 }
    datasourceApi.listDatasources.mockResolvedValue({ data: { records: [disabledDatasource] } })
    const ctx = createContext()

    await ctx.loadDatasourceOptions()

    expect(datasourceApi.listDatasources).toHaveBeenCalledWith({ pageNum: 1, pageSize: 500 })
    expect(ctx.datasourceOptions).toEqual([disabledDatasource])
  })

  test('applyTemplate fills rule engine mapping template', () => {
    const ctx = createContext({
      form: {
        ...ApiDetail.methods.emptyForm(),
        endpointUrl: 'RC_PRICING_TABLE'
      }
    })

    ctx.applyTemplate('RULE_ENGINE')

    expect(ctx.form.requestMethod).toBe('POST')
    expect(JSON.parse(ctx.form.requestMapping)).toEqual({
      ruleCode: 'RC_PRICING_TABLE',
      params: {
        customerType: '$.customerType',
        productLine: '$.productLine'
      }
    })
    expect(JSON.parse(ctx.form.responseMapping)).toEqual({
      decision: 'body.decision',
      rate: 'body.rate',
      score: 'body.score'
    })
  })

  test('normalizeForm accepts dynamic response mapping JSON', () => {
    const ctx = createContext({ responseMappingMode: 'JSON' })
    ctx.form = {
      ...ctx.emptyForm(),
      responseMapping: JSON.stringify({
        score: {
          cases: [
            { when: { path: 'body.code', value: '00' }, path: 'body.data.score' }
          ],
          default: 0
        }
      })
    }

    expect(ctx.normalizeForm(ctx.form).responseMapping).toContain('cases')
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

  test('request mapping form and json modes keep each other synchronized', () => {
    const row = { targetPath: '$.customer.mobile', sourcePath: '', remark: '' }
    const ctx = createContext({
      requestMappingRows: [row]
    })

    ctx.onRequestTargetInput(row)
    expect(row.sourcePath).toBe('$.customer.mobile')
    expect(JSON.parse(ctx.requestMappingJsonText)).toEqual({
      customer: { mobile: '$.customer.mobile' }
    })

    ctx.requestBodyMode = 'JSON'
    ctx.syncRequestRowsFromJson('{"customer":{"certNo":"$.customer.idNo"}}', false)
    expect(ctx.requestMappingRows).toEqual([
      { targetPath: 'customer.certNo', sourcePath: '$.customer.idNo', remark: '' }
    ])
  })

  test('request source path can fill target path automatically', () => {
    const row = { targetPath: '', sourcePath: '$.apply.customer.idNo', remark: '' }
    const ctx = createContext({
      requestMappingRows: [row]
    })

    ctx.onRequestSourceInput(row)

    expect(row.targetPath).toBe('apply.customer.idNo')
    expect(JSON.parse(ctx.requestMappingJsonText)).toEqual({
      apply: { customer: { idNo: '$.apply.customer.idNo' } }
    })
  })

  test('response conditional rows serialize to cases mapping', () => {
    const ctx = createContext({
      responseMappingRows: [{ outputField: 'score', sourcePath: 'body.data.score', defaultValue: '' }],
      responseConditionRows: [{
        outputField: 'score',
        conditionRoot: {
          type: 'group',
          op: 'AND',
          children: [{ type: 'leaf', varCode: 'body.code', operator: '==', valueKind: 'CONST', value: '00', varType: 'STRING' }]
        },
        sourcePath: 'body.data.score',
        defaultValue: 0,
        fallback: false
      }]
    })

    const config = ctx.buildResponseMappingConfig()

    expect(config.score.cases[0]).toMatchObject({
      when: {
        type: 'group',
        op: 'AND',
        children: [{ type: 'leaf', varCode: 'body.code', operator: '==', value: '00' }]
      },
      path: 'body.data.score'
    })
    expect(config.score.default).toBe(0)
  })

  test('response fallback branch serializes without when condition', () => {
    const ctx = createContext({
      responseConditionRows: [{
        outputField: 'score',
        conditionRoot: { type: 'group', op: 'AND', children: [] },
        sourcePath: 'body.backup.score',
        defaultValue: '',
        fallback: true
      }]
    })

    const config = ctx.buildResponseMappingConfig()

    expect(config.score.cases[0]).toEqual({ path: 'body.backup.score' })
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

  test('normalizeForm rejects enabled cache without configured key components', () => {
    const ctx = createContext({ cacheKeyRows: [ApiDetail.methods.emptyCacheKeyRow()] })
    ctx.form.responseCacheSeconds = 60

    expect(() => ctx.normalizeForm(ctx.form)).toThrow('缓存键')
  })

  test('buildRequestMappingConfig nests dotted api field paths', () => {
    const ctx = createContext({
      requestMappingRows: [
        { targetPath: 'customer.certNo', sourcePath: '$.customer.idNo', remark: '' },
        { targetPath: '$.customer.mobile', sourcePath: '$.customer.mobile', remark: '' }
      ]
    })

    expect(ctx.buildRequestMappingConfig()).toEqual({
      customer: {
        certNo: '$.customer.idNo',
        mobile: '$.customer.mobile'
      }
    })
  })

  test('normalizeForm serializes async polling config for async api', () => {
    const ctx = createContext({
      form: {
        ...ApiDetail.methods.emptyForm(),
        requestMode: 'ASYNC',
        asyncResultMode: 'POLL'
      },
      asyncShared: { taskIdPath: 'body.taskId' },
      asyncPollConfig: {
        ...ApiDetail.methods.emptyAsyncPollConfig(),
        resultEndpointUrl: '/result/${taskId}',
        intervalMs: 2000
      }
    })

    const data = ctx.normalizeForm(ctx.form)

    expect(data.asyncResultMode).toBe('POLL')
    expect(JSON.parse(data.asyncPollConfig)).toMatchObject({
      resultEndpointUrl: '/result/${taskId}',
      intervalMs: 2000,
      taskIdPath: 'body.taskId'
    })
    expect(data.asyncCallbackConfig).toBeNull()
  })

  test('buildApiInvokeParamTemplate uses mappings and field types to create sample json', () => {
    const ctx = createContext({
      form: {
        ...ApiDetail.methods.emptyForm(),
        requestObjectId: 10
      },
      dataObjectOptions: [{ id: 10, scriptName: 'customer' }],
      dataObjectTree: [{
        object: { id: 10, scriptName: 'customer' },
        flatVariables: [
          { scriptName: 'customer.idNo', varLabel: '证件号', varType: 'STRING' },
          { scriptName: 'customer.age', varLabel: '年龄', varType: 'NUMBER' },
          { scriptName: 'customer.tags', varLabel: '标签', varType: 'LIST' },
          { scriptName: 'customer.ext', varLabel: '扩展', varType: 'OBJECT' }
        ]
      }]
    })
    const row = {
      headerConfig: '{"X-Trace":"${traceId}"}',
      requestMapping: '{"certNo":"$.customer.idNo","age":"$.customer.age","tags":"$.customer.tags","ext":"$.customer.ext"}'
    }

    expect(JSON.parse(ctx.buildApiInvokeParamTemplate(row))).toEqual({
      traceId: '',
      customer: {
        idNo: '',
        age: 0,
        tags: [],
        ext: {}
      }
    })
  })

  test('buildApiInvokeParamTemplate includes request object fields without mappings', () => {
    const ctx = createContext({
      form: {
        ...ApiDetail.methods.emptyForm(),
        requestObjectId: 7
      },
      dataObjectOptions: [{ id: 7, objectCode: 'TSRequestBody', scriptName: '' }],
      dataObjectTree: [{
        object: { id: 7, objectCode: 'TSRequestBody', scriptName: '' },
        flatVariables: [
          { scriptName: 'clientAppName', varType: 'STRING' },
          { scriptName: 'params', varType: 'OBJECT' }
        ]
      }]
    })

    expect(JSON.parse(ctx.buildApiInvokeParamTemplate(ctx.form))).toEqual({
      clientAppName: '',
      params: {}
    })
  })

  test('loadDataObjectOptions accepts variable tree wrapped in response data tree', async () => {
    const ctx = createContext()
    const requestNode = {
      object: { id: 10, scriptName: 'customer' },
      flatVariables: [
        { scriptName: 'customer.idNo', varLabel: '证件号', varType: 'STRING' }
      ]
    }
    dataObjectApi.listDataObjects.mockResolvedValueOnce({
      data: [{ id: 10, scriptName: 'customer' }]
    })
    dataObjectApi.getVariableTree.mockResolvedValueOnce({
      data: {
        objectIdMap: { 10: requestNode },
        tree: [requestNode]
      }
    })

    await ctx.loadDataObjectOptions(1)

    expect(ctx.dataObjectTree).toEqual([requestNode])
    expect(ctx.fieldsForObject(10)).toEqual(requestNode.flatVariables)
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
    const draft = { id: 8, datasourceId: 2, endpointUrl: '/draft-score' }
    ctx.normalizeForm = vi.fn().mockReturnValue(draft)
    datasourceApi.invokeApiConfigPreview.mockResolvedValue({ data: { success: true } })

    await ctx.runInvokeApi()

    expect(datasourceApi.invokeApiConfigPreview).toHaveBeenCalledWith(8, {
      config: draft,
      params: { customer: { idNo: 'A001' } }
    })
    expect(ctx.invokeResultText).toContain('"success": true')
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
