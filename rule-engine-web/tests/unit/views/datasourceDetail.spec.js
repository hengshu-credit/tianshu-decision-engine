import DatasourceDetail from '@/views/datasource/DatasourceDetail.vue'
import { getDatasource } from '@/api/datasource'

vi.mock('@/api/governance', () => ({ editableResource: vi.fn(async (_type, _id, fallback) => fallback) }))

function createContext() {
  const ctx = {
    authConfig: DatasourceDetail.methods.emptyAuthConfig('TOKEN_API')
  }
  Object.keys(DatasourceDetail.methods).forEach(name => {
    ctx[name] = DatasourceDetail.methods[name].bind(ctx)
  })
  return ctx
}

describe('DatasourceDetail token config', () => {
  test('配置读取完成前不允许保存空表单，读取完成后才进入编辑', async () => {
    const ctx = createContext()
    Object.assign(ctx, { form: DatasourceDetail.methods.emptyForm(), $route: { params: { id: '7' } },
      isCreateMode: false, detailLoading: false, detailLoaded: false, detailError: '', detailLoadSequence: 0,
      $refs: { form: { validate: vi.fn() } } })
    Object.defineProperty(ctx, 'configurationReady', { get: () => DatasourceDetail.computed.configurationReady.call(ctx) })
    let resolve
    getDatasource.mockReturnValueOnce(new Promise(done => { resolve = done }))
    const loading = ctx.loadDetail()
    try {
      expect(ctx.detailLoading).toBe(true)
      ctx.handleSave()
      expect(ctx.$refs.form.validate).not.toHaveBeenCalled()
    } finally {
      resolve({ data: { id: 7, datasourceCode: 'OriginalCode', datasourceName: '原内容', baseUrl: 'https://example.test' } })
      await loading
    }
    expect(ctx.configurationReady).toBe(true)
    expect(ctx.form.datasourceCode).toBe('OriginalCode')
  })

  test('已离开的数据源迟到响应不能覆盖当前表单', async () => {
    const ctx = createContext()
    Object.assign(ctx, { form: { datasourceName: '正在编辑B' }, $route: { params: { id: '7' } },
      isCreateMode: false, detailLoading: false, detailLoaded: false, detailError: '', detailLoadSequence: 0 })
    let resolve
    getDatasource.mockReturnValueOnce(new Promise(done => { resolve = done }))
    const loading = ctx.loadDetail()
    ctx.$route.params.id = '8'
    resolve({ data: { id: 7, datasourceName: '迟到A' } })
    await loading
    expect(ctx.form.datasourceName).toBe('正在编辑B')
  })
  test('new datasource inherits explicit project context', () => {
    const ctx = createContext()
    ctx.form = DatasourceDetail.methods.emptyForm()

    ctx.applyProjectContext(7)

    expect(ctx.form.scope).toBe('PROJECT')
    expect(ctx.form.projectId).toBe(7)
  })

  test('token api serializes custom header name and empty prefix', () => {
    const ctx = createContext()
    ctx.authConfig.tokenHeaderName = 'token_id'
    ctx.authConfig.tokenPrefix = ''

    const config = JSON.parse(ctx.buildAuthConfig('TOKEN_API', ''))

    expect(config.tokenHeaderName).toBe('token_id')
    expect(config.tokenPrefix).toBe('')
  })

  test('old token config receives raw-token editable defaults', () => {
    const ctx = createContext()

    const config = ctx.parseAuthConfig(
      '{"tokenUrl":"/token","tokenPath":"body.token"}',
      'TOKEN_API'
    )

    expect(config.tokenHeaderName).toBe('Authorization')
    expect(config.tokenPrefix).toBe('')
  })

  test('token prefix keeps user-entered spaces when serializing', () => {
    const ctx = createContext()
    ctx.authConfig.tokenPrefix = 'Custom  '

    const config = JSON.parse(ctx.buildAuthConfig('TOKEN_API', ''))

    expect(config.tokenPrefix).toBe('Custom  ')
  })

  test('token response script is preserved for xml or encrypted token responses', () => {
    const ctx = createContext()
    ctx.authConfig.tokenResponseScript = 'jsonParse(strSubstring(rawBody, 8, strLength(rawBody) - 9))'

    const saved = JSON.parse(ctx.buildAuthConfig('TOKEN_API', ''))
    const loaded = ctx.parseAuthConfig(JSON.stringify(saved), 'TOKEN_API')

    expect(saved.tokenResponseScript).toBe(ctx.authConfig.tokenResponseScript)
    expect(loaded.tokenResponseScript).toBe(ctx.authConfig.tokenResponseScript)
  })

  test('token placement can be configured as script only and defaults to header', () => {
    const ctx = createContext()
    ctx.authConfig.tokenPlacement = 'SCRIPT_ONLY'

    const saved = JSON.parse(ctx.buildAuthConfig('TOKEN_API', ''))
    const oldConfig = ctx.parseAuthConfig('{"tokenUrl":"/token"}', 'TOKEN_API')

    expect(saved.tokenPlacement).toBe('SCRIPT_ONLY')
    expect(oldConfig.tokenPlacement).toBe('HEADER')
  })
})
