import DatasourceList from '@/views/datasource/DatasourceList.vue'

function createContext(overrides = {}) {
  const ctx = {
    datasourceOptions: [],
    apiForm: {},
    ...overrides
  }
  Object.keys(DatasourceList.methods).forEach(name => {
    ctx[name] = DatasourceList.methods[name].bind(ctx)
  })
  return ctx
}

describe('DatasourceList helpers', () => {
  test('normalizeApi rejects invalid JSON fields', () => {
    const ctx = createContext()
    const form = {
      ...ctx.emptyApiForm(),
      headerConfig: '{bad json}'
    }

    expect(() => ctx.normalizeApi(form)).toThrow(/JSON/)
  })

  test('emptyApiForm defaults response cache to disabled', () => {
    const ctx = createContext()

    expect(ctx.emptyApiForm().responseCacheSeconds).toBe(0)
  })

  test('normalizeApi keeps response cache seconds', () => {
    const ctx = createContext()
    const form = {
      ...ctx.emptyApiForm(),
      responseCacheSeconds: 86400
    }

    expect(ctx.normalizeApi(form).responseCacheSeconds).toBe(86400)
  })

  test('normalizeDatasource rejects invalid auth JSON', () => {
    const ctx = createContext()
    const form = {
      ...ctx.emptyDatasourceForm(),
      baseUrl: 'https://api.example.com',
      authType: 'CUSTOM',
      authConfig: '{bad json}'
    }

    expect(() => ctx.normalizeDatasource(form)).toThrow(/JSON/)
  })

  test('buildAuthConfig serializes token api fields including response header paths', () => {
    const ctx = createContext({
      datasourceAuthConfig: {
        ...DatasourceList.methods.emptyAuthConfig('TOKEN_API'),
        tokenUrl: '/oauth/token',
        method: 'POST',
        contentType: 'application/json',
        headers: '{"X-App-Id":"${appId}"}',
        body: '{"grant_type":"client_credentials"}',
        tokenPath: 'headers.Authorization',
        expiresInPath: 'headers.X-Expires-In'
      }
    })

    expect(JSON.parse(ctx.buildAuthConfig('TOKEN_API'))).toEqual({
      tokenUrl: '/oauth/token',
      method: 'POST',
      contentType: 'application/json',
      headers: { 'X-App-Id': '${appId}' },
      body: { grant_type: 'client_credentials' },
      tokenPath: 'headers.Authorization',
      expiresInPath: 'headers.X-Expires-In'
    })
  })

  test('parseAuthConfig fills editable defaults from saved auth JSON', () => {
    const ctx = createContext()

    const config = ctx.parseAuthConfig(
      '{"tokenUrl":"/token","method":"GET","headers":{"X-App":"app"},"body":{"grant_type":"client_credentials"},"tokenPath":"body.data.token"}',
      'TOKEN_API'
    )

    expect(config.tokenUrl).toBe('/token')
    expect(config.method).toBe('GET')
    expect(config.headers).toContain('"X-App"')
    expect(config.body).toContain('grant_type')
    expect(config.tokenPath).toBe('body.data.token')
  })

  test('normalizeDatasource supplies local address for rule engine datasource', () => {
    const ctx = createContext()
    const form = {
      ...ctx.emptyDatasourceForm(),
      protocol: 'RULE_ENGINE',
      baseUrl: ''
    }

    expect(ctx.normalizeDatasource(form).baseUrl).toBe('rule-engine://local')
  })

  test('normalizeDatasource requires base url for http datasource', () => {
    const ctx = createContext()
    const form = {
      ...ctx.emptyDatasourceForm(),
      protocol: 'HTTPS',
      baseUrl: ''
    }

    expect(() => ctx.normalizeDatasource(form)).toThrow('请输入基础地址')
  })

  test('onApiDatasourceChange resets data object references and loads selected project objects', () => {
    const ctx = createContext({
      datasourceOptions: [{ id: 3, projectId: 12 }],
      apiForm: { requestObjectId: 1, responseObjectId: 2 }
    })
    ctx.loadDataObjectOptions = jest.fn()

    ctx.onApiDatasourceChange(3)

    expect(ctx.apiForm.requestObjectId).toBeNull()
    expect(ctx.apiForm.responseObjectId).toBeNull()
    expect(ctx.loadDataObjectOptions).toHaveBeenCalledWith(12)
  })

  test('applyApiTemplate fills rule engine mapping template', () => {
    const ctx = createContext({
      apiForm: {
        ...DatasourceList.methods.emptyApiForm(),
        endpointUrl: 'RC_PRICING_TABLE'
      }
    })

    ctx.applyApiTemplate('RULE_ENGINE')

    expect(ctx.apiForm.requestMethod).toBe('POST')
    expect(JSON.parse(ctx.apiForm.requestMapping)).toEqual({
      ruleCode: 'RC_PRICING_TABLE',
      params: {
        customerType: '$.customerType',
        productLine: '$.productLine'
      }
    })
    expect(JSON.parse(ctx.apiForm.responseMapping)).toEqual({
      decision: 'body.decision',
      rate: 'body.rate',
      score: 'body.score'
    })
  })

  test('applyApiTemplate fills http mapping template', () => {
    const ctx = createContext({
      apiForm: DatasourceList.methods.emptyApiForm()
    })

    ctx.applyApiTemplate('HTTP')

    expect(JSON.parse(ctx.apiForm.requestMapping).idNo).toBe('$.customer.idNo')
    expect(JSON.parse(ctx.apiForm.responseMapping).score).toBe('body.data.score')
    expect(JSON.parse(ctx.apiForm.bodyTemplate).certNo).toBe('${customer.idNo}')
  })

  test('isRuleEngineDatasource checks selected datasource protocol', () => {
    const ctx = createContext({
      datasourceOptions: [
        { id: 1, protocol: 'HTTPS' },
        { id: 2, protocol: 'RULE_ENGINE' }
      ],
      apiForm: { datasourceId: 2 }
    })

    expect(ctx.isRuleEngineDatasource()).toBe(true)
    expect(ctx.isRuleEngineDatasource(1)).toBe(false)
  })

  test('formatCacheSeconds displays common units', () => {
    const ctx = createContext()

    expect(ctx.formatCacheSeconds(0)).toBe('不缓存')
    expect(ctx.formatCacheSeconds(60)).toBe('1分钟')
    expect(ctx.formatCacheSeconds(3600)).toBe('1小时')
    expect(ctx.formatCacheSeconds(86400)).toBe('1天')
  })
})
