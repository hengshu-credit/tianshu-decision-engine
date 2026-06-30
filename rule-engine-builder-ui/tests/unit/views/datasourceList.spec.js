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

  test('normalizeDatasource rejects invalid auth JSON', () => {
    const ctx = createContext()
    const form = {
      ...ctx.emptyDatasourceForm(),
      authConfig: '{bad json}'
    }

    expect(() => ctx.normalizeDatasource(form)).toThrow(/JSON/)
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
})
