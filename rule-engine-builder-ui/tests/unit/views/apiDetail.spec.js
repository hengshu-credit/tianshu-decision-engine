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
  test('applyTemplate fills hscredit v1 mapping without body template', () => {
    const ctx = createContext()

    ctx.applyTemplate('HSCREDIT_V1')

    const requestMapping = JSON.parse(ctx.form.requestMapping)
    expect(requestMapping.model_params.br_applyloanstr_v2).toBe('$.model_params.br_applyloanstr_v2')
    const responseMapping = JSON.parse(ctx.form.responseMapping)
    expect(responseMapping.swiftNumber).toEqual([
      'body.model_params.br_applyloanstr_v2.swift_number',
      'body.data.swift_number',
      'body.swift_number'
    ])
    expect(responseMapping.alsM12CellNbankAllnum.default).toBeNull()
    expect(ctx.form.bodyTemplate).toBe('')
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
