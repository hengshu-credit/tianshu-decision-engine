import { shallowMount } from '@test-utils'
import { nextTick } from 'vue'

import * as databaseApi from '@/api/database'
import * as projectApi from '@/api/project'
import DatabaseDetail from '@/views/database/DatabaseDetail.vue'

function flushPromises() {
  return new Promise(resolve => setTimeout(resolve, 0))
}

function mountPage(route) {
  return shallowMount(DatabaseDetail, {
    mocks: {
      $route: route || { params: { id: '1' }, query: {} },
      $router: { push: vi.fn() },
      $message: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
    },
    stubs: {
      'el-form': { template: '<form><slot /></form>', methods: { validate: vi.fn(cb => cb(true)) } },
      'el-form-item': true,
      'el-select': true,
      'el-option': true,
      'el-input': true,
      'el-input-number': true,
      'el-button': true,
      'el-row': true,
      'el-col': true,
      'el-switch': true,
      'el-radio-group': true,
      'el-radio-button': true,
      'el-checkbox': true,
      'el-alert': true,
      'el-collapse': true,
      'el-collapse-item': true,
      'monaco-editor': true
    }
  })
}

describe('DatabaseDetail — 项目选择', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  test('编辑项目级数据源时 projectId 为 0 不自动选中项目', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [{ id: 1, projectName: '项目A' }] } })
    databaseApi.getDbDatasource.mockResolvedValue({
      data: {
        id: 10,
        scope: 'PROJECT',
        projectId: 0,
        datasourceCode: 'risk_db',
        datasourceName: '风险库',
        jdbcUrl: 'jdbc:mysql://127.0.0.1:3306/risk'
      }
    })

    const wrapper = mountPage()
    await flushPromises()
    await nextTick()

    expect(wrapper.vm.form.scope).toBe('PROJECT')
    expect(wrapper.vm.form.projectId).toBeNull()
  })

  test('从全局切回项目级时清空 projectId，要求用户重新选择项目', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    databaseApi.getDbDatasource.mockResolvedValue({
      data: {
        id: 11,
        scope: 'GLOBAL',
        projectId: 0,
        datasourceCode: 'global_db',
        datasourceName: '全局库',
        jdbcUrl: 'jdbc:mysql://127.0.0.1:3306/rule'
      }
    })

    const wrapper = mountPage()
    await flushPromises()
    await nextTick()

    wrapper.vm.onScopeChange('PROJECT')

    expect(wrapper.vm.form.projectId).toBeNull()
  })

  test('项目内新建数据库返回列表时保留显式项目范围', async () => {
    projectApi.listProjects.mockResolvedValue({
      data: { records: [{ id: 3, projectName: '综合风控示例项目' }] }
    })
    const wrapper = mountPage({
      path: '/database/new',
      params: { id: 'new' },
      query: { projectId: '3' }
    })
    await flushPromises()

    wrapper.vm.goBack()

    expect(wrapper.vm.$router.push).toHaveBeenCalledWith({
      path: '/database',
      query: { projectId: 3 }
    })
  })

  test('连接测试成功后保留可见的成功状态', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    databaseApi.testDbDatasourceDraft.mockResolvedValue({ data: '连接成功' })
    const wrapper = mountPage({ path: '/database/new', params: { id: 'new' }, query: {} })
    await flushPromises()

    wrapper.vm.form.host = '127.0.0.1'
    wrapper.vm.form.databaseName = 'risk'
    wrapper.vm.form.datasourceCode = 'risk_db'
    wrapper.vm.form.datasourceName = '风险库'
    wrapper.vm.handleTestDraft()
    await flushPromises()

    expect(databaseApi.testDbDatasourceDraft).toHaveBeenCalled()
    expect(wrapper.vm.connectionTestStatus).toBe('success')
    expect(wrapper.vm.connectionTesting).toBe(false)
  })

  test('连接测试失败后展示可读错误且允许再次测试', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    databaseApi.testDbDatasourceDraft.mockRejectedValue(new Error('账号无权访问'))
    const wrapper = mountPage({ path: '/database/new', params: { id: 'new' }, query: {} })
    await flushPromises()

    wrapper.vm.form.host = '127.0.0.1'
    wrapper.vm.form.databaseName = 'risk'
    wrapper.vm.form.datasourceCode = 'risk_db'
    wrapper.vm.form.datasourceName = '风险库'
    wrapper.vm.handleTestDraft()
    await flushPromises()

    expect(wrapper.vm.connectionTestStatus).toBe('error')
    expect(wrapper.vm.connectionTestMessage).toBe('账号无权访问')
    expect(wrapper.vm.connectionTesting).toBe(false)
  })
})
