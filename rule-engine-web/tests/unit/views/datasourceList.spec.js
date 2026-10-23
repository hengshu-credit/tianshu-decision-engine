import DatasourceList from '@/views/datasource/DatasourceList.vue'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import * as projectApi from '@/api/project'

function createContext(overrides = {}) {
  const ctx = {
    datasourceOptions: [],
    apiForm: {},
    ...overrides
  }
  Object.keys(DatasourceList.methods).forEach(name => {
    ctx[name] = DatasourceList.methods[name].bind(ctx)
  })
  ctx.apiGuideTemplates = overrides.apiGuideTemplates || DatasourceList.data.call(ctx).apiGuideTemplates
  return ctx
}

describe('DatasourceList helpers', () => {
  test('列表新建和编辑统一跳转当前配置页面', async () => {
    const ctx = createContext({ contextProjectId: 7, $router: { push: vi.fn() } })
    ctx.handleCreateDatasource()
    expect(ctx.$router.push).toHaveBeenLastCalledWith({ path: '/datasource/source/new', query: { projectId: 7 } })
    await ctx.handleCreateApi({ id: 21 })
    expect(ctx.$router.push).toHaveBeenLastCalledWith({ path: '/datasource/api/new', query: { projectId: 7, datasourceId: 21 } })
    await ctx.handleEditApi({ id: 22 })
    expect(ctx.$router.push).toHaveBeenLastCalledWith('/datasource/api/22')
  })
  test('uses project fuzzy filters in datasource and API lists', () => {
    const context = {}
    Object.keys(DatasourceList.methods).forEach(name => {
      context[name] = DatasourceList.methods[name].bind(context)
    })
    const data = DatasourceList.data.call(context)

    expect(DatasourceList.components.ProjectFilterSelect).toBe(ProjectFilterSelect)
    expect(data.datasourceQuery).toEqual(expect.objectContaining({ projectCode: '', projectName: '' }))
    expect(data.apiQuery).toEqual(expect.objectContaining({ projectCode: '', projectName: '' }))
    expect(data.apiQuery.projectId).toBe('')
  })

  test('created loads api config list on first entry', () => {
    expect(DatasourceList.created.toString()).toContain('this.loadApiConfigs()')
  })

  test('project context scopes datasource and API queries by stable project identity', () => {
    const ctx = createContext({
      datasourceQuery: {},
      apiQuery: {}
    })

    ctx.applyProjectContext({ id: 7, projectCode: 'RISK' })

    expect(ctx.contextProjectId).toBe(7)
    expect(ctx.datasourceQuery.projectId).toBe(7)
    expect(ctx.apiQuery.projectId).toBe(7)
    expect(ctx.apiQuery.projectCode).toBe('RISK')
  })

  test('project context metadata failure keeps datasource and API lists empty', async () => {
    projectApi.getProject.mockRejectedValueOnce(new Error('project missing'))
    const ctx = createContext({
      $route: { query: { projectId: '7' } },
      $store: { state: { currentProject: null } },
      $message: { error: vi.fn() },
    })
    ctx.loadProjects = vi.fn()
    ctx.loadDatasources = vi.fn()
    ctx.loadApiConfigs = vi.fn()
    ctx.loadDatasourceOptions = vi.fn()
    ctx.loadDataObjectOptions = vi.fn()

    await DatasourceList.created.call(ctx)

    expect(ctx.contextProjectId).toBe(7)
    expect(ctx.loadProjects).toHaveBeenCalled()
    expect(ctx.loadDatasources).not.toHaveBeenCalled()
    expect(ctx.loadApiConfigs).not.toHaveBeenCalled()
    expect(ctx.$message.error).toHaveBeenCalled()
  })

test('buildApiInvokeParamTemplate extracts request references for http api test', () => {
    const ctx = createContext()
    const row = {
      executionConfig: JSON.stringify({ version: 2, requestFields: ['requestId', 'trace.id', 'customer.idNo', 'customer.mobile', 'customer.name'].map((code, index) => ({ id: 'field' + index, value: { kind: 'REFERENCE', refType: 'VARIABLE', refId: index + 1, code } })) }),
      authApiConfig: JSON.stringify({ headers: { 'X-App-Id': { kind: 'REFERENCE', refType: 'VARIABLE', refId: 9, code: 'appId' } } })
    }

    expect(JSON.parse(ctx.buildApiInvokeParamTemplate(row))).toEqual({
      requestId: '',
      trace: { id: '' },
      customer: { idNo: '', mobile: '', name: '' },
      appId: ''
    })
  })

  test('buildApiInvokeParamTemplate extracts rule engine params without ruleCode literal', () => {
    const ctx = createContext()
    const row = {
      executionConfig: JSON.stringify({ version: 2, requestFields: [
        { id: 'code', value: { kind: 'LITERAL', valueType: 'STRING', value: 'RC_PRICING_TABLE' } },
        ...['customerType', 'productLine'].map((code, index) => ({ id: 'field' + index, value: { kind: 'REFERENCE', refType: 'VARIABLE', refId: index + 1, code } })),
      ] })
    }

    expect(JSON.parse(ctx.buildApiInvokeParamTemplate(row))).toEqual({
      customerType: '',
      productLine: ''
    })
  })

  test('buildApiInvokeParamTemplate prefers saved test sample params', () => {
    const ctx = createContext()
    const row = {
      executionConfig: JSON.stringify({ version: 2, requestFields: [], samples: [{ id: 'case1', name: '请求样例', params: { request: { mobile: '13800000000' } } }] })
    }

    expect(JSON.parse(ctx.buildApiInvokeParamTemplate(row))).toEqual({
      request: { mobile: '13800000000' }
    })
  })

test('formatCacheSeconds displays common units', () => {
    const ctx = createContext()

    expect(ctx.formatCacheSeconds(0)).toBe('不缓存')
    expect(ctx.formatCacheSeconds(60)).toBe('1分钟')
    expect(ctx.formatCacheSeconds(3600)).toBe('1小时')
    expect(ctx.formatCacheSeconds(86400)).toBe('1天')
  })

  test('apiGuideTemplates 解释外数 API 模板和变量读取路径', () => {
    const ctx = createContext()

    expect(ctx.apiGuideTemplates.map(item => item.title)).toEqual([
      'HTTP 外数模板',
      '内部规则模板',
      '变量与对象取值'
    ])
    expect(ctx.apiGuideTemplates[0].text).toContain('多步请求、轮询与回调')
    expect(ctx.apiGuideTemplates[2].text).toContain('整体绑定')
    expect(ctx.apiGuideTemplates[2].text).toContain('无需逐字段重复映射')
  })
})
