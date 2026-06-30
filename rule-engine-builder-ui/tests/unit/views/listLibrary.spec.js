import { mount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'

jest.unmock('element-ui')
import ElementUI from 'element-ui'

import * as projectApi from '@/api/project'
import * as ruleListApi from '@/api/ruleList'
import ListLibrary from '@/views/ruleList/ListLibrary.vue'

function createTestVue() {
  const localVue = createLocalVue()
  localVue.use(ElementUI)
  return localVue
}

const FormStub = {
  template: '<form><slot /></form>',
  methods: { validate: jest.fn(cb => cb(true)) }
}

async function mountPage() {
  projectApi.listProjects.mockResolvedValue({ data: { records: [{ id: 1, projectName: '项目A' }] } })
  ruleListApi.listLibraries.mockResolvedValue({
    data: { records: [{ id: 9, listCode: 'mobile_black', listName: '手机号黑名单', listType: 'BLACK', status: 1, recordCount: 2 }], total: 1 }
  })
  const wrapper = mount(ListLibrary, {
    localVue: createTestVue(),
    mocks: {
      $router: { push: jest.fn() },
      $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() },
      $confirm: jest.fn().mockResolvedValue(true)
    },
    stubs: {
      'el-form': FormStub,
      'el-form-item': true,
      'el-select': true,
      'el-option': true,
      'el-input': true,
      'el-button': true,
      'el-tag': true,
      'el-table': true,
      'el-table-column': true,
      'el-pagination': true,
      'el-dialog': true,
      'el-row': true,
      'el-col': true,
      'el-switch': true
    }
  })
  await Vue.nextTick()
  await new Promise(resolve => setTimeout(resolve, 0))
  return wrapper
}

describe('ListLibrary — 名单库管理', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountPage() })
  afterEach(() => { if (wrapper) wrapper.destroy(); jest.clearAllMocks() })

  test('初始化加载项目和名单库', () => {
    expect(projectApi.listProjects).toHaveBeenCalled()
    expect(ruleListApi.listLibraries).toHaveBeenCalled()
    expect(wrapper.vm.tableData[0].listCode).toBe('mobile_black')
  })

  test('保存新名单库时校验项目并调用创建接口', async () => {
    ruleListApi.createLibrary.mockResolvedValue({ data: { id: 10 } })
    wrapper.vm.handleCreate()
    wrapper.vm.form.projectId = 1
    wrapper.vm.form.listCode = 'id_black'
    wrapper.vm.form.listName = '身份证黑名单'
    wrapper.vm.submit()
    await Vue.nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(ruleListApi.createLibrary).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 1,
      listCode: 'id_black',
      listName: '身份证黑名单'
    }))
  })

  test('切换状态会调用更新接口', async () => {
    ruleListApi.updateLibrary.mockResolvedValue({ data: true })
    const row = { ...wrapper.vm.tableData[0], status: 0 }
    await wrapper.vm.toggleStatus(row)

    expect(ruleListApi.updateLibrary).toHaveBeenCalledWith(expect.objectContaining({ id: 9, status: 0 }))
  })
})
