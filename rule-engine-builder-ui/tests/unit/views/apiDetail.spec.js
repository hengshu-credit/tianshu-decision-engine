import ApiDetail from '@/views/datasource/ApiDetail.vue'

function createContext(overrides = {}) {
  const ctx = {
    datasourceOptions: [],
    dataObjectOptions: [],
    form: ApiDetail.methods.emptyForm(),
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
    const ctx = createContext()
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
})
