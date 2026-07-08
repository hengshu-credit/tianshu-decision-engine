import ApiDetail from '@/views/datasource/ApiDetail.vue'
import * as dataObjectApi from '@/api/dataObject'

afterEach(() => { jest.clearAllMocks() })

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
    ctx.loadDetail = jest.fn().mockResolvedValue()

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

  test('billing mode serializes query and hit configs', () => {
    const queryCtx = createContext({
      billingConfig: { mode: 'QUERY', path: '', operator: '==', value: '' }
    })
    const hitCtx = createContext({
      billingConfig: { mode: 'HIT', path: 'body.hit', operator: '==', value: 'true' }
    })

    expect(queryCtx.buildBillingConditionConfig()).toEqual({ mode: 'QUERY' })
    expect(hitCtx.buildBillingConditionConfig()).toEqual({
      mode: 'HIT',
      path: 'body.hit',
      operator: '==',
      value: true
    })
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
})
