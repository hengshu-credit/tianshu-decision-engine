import DatasourceDetail from '@/views/datasource/DatasourceDetail.vue'

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
  test('token api serializes custom header name and empty prefix', () => {
    const ctx = createContext()
    ctx.authConfig.tokenHeaderName = 'token_id'
    ctx.authConfig.tokenPrefix = ''

    const config = JSON.parse(ctx.buildAuthConfig('TOKEN_API', ''))

    expect(config.tokenHeaderName).toBe('token_id')
    expect(config.tokenPrefix).toBe('')
  })

  test('old token config receives bearer-compatible editable defaults', () => {
    const ctx = createContext()

    const config = ctx.parseAuthConfig(
      '{"tokenUrl":"/token","tokenPath":"body.token"}',
      'TOKEN_API'
    )

    expect(config.tokenHeaderName).toBe('Authorization')
    expect(config.tokenPrefix).toBe('Bearer ')
  })

  test('token response script is preserved for xml or encrypted token responses', () => {
    const ctx = createContext()
    ctx.authConfig.tokenResponseScript = 'jsonParse(strRegexExtract(rawBody, "\\{.*\\}", 0))'

    const saved = JSON.parse(ctx.buildAuthConfig('TOKEN_API', ''))
    const loaded = ctx.parseAuthConfig(JSON.stringify(saved), 'TOKEN_API')

    expect(saved.tokenResponseScript).toBe(ctx.authConfig.tokenResponseScript)
    expect(loaded.tokenResponseScript).toBe(ctx.authConfig.tokenResponseScript)
  })
})
