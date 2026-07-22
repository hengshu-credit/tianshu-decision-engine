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
      $router: { push: jest.fn() },
      $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() }
    },
    stubs: {
      'el-form': { template: '<form><slot /></form>', methods: { validate: jest.fn(cb => cb(true)) } },
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
      'monaco-editor': true
    }
  })
}

describe('DatabaseDetail — 项目选择', () => {
  afterEach(() => {
    jest.clearAllMocks()
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
})
