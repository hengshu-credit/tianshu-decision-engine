import { mount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'

jest.unmock('element-ui')
import ElementUI from 'element-ui'

import * as ruleListApi from '@/api/ruleList'
import ListDetail from '@/views/ruleList/ListDetail.vue'

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
  ruleListApi.getLibrary.mockResolvedValue({ data: { id: 9, listCode: 'mobile_black', listName: '手机号黑名单', listType: 'BLACK' } })
  ruleListApi.listRecords.mockResolvedValue({
    data: { records: [{ id: 1, itemContent: '13800138000', itemType: 'MOBILE', status: 1, lastOperation: 'ADD' }], total: 1 }
  })
  ruleListApi.listRecordLogs.mockResolvedValue({
    data: {
      records: [{
        id: 1,
        itemContent: '13800138000',
        operation: 'UPDATE',
        effectiveTime: '2026-07-01T00:00:00',
        expireTime: '2026-12-31T23:59:59',
        changeContent: '修改：有效期：2026-06-01 00:00:00 至 2026-06-30 23:59:59 -> 2026-07-01 00:00:00 至 2026-12-31 23:59:59'
      }],
      total: 1
    }
  })
  const wrapper = mount(ListDetail, {
    localVue: createTestVue(),
    mocks: {
      $route: { params: { id: 9 } },
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
      'el-tabs': true,
      'el-tab-pane': true,
      'el-checkbox': true,
      'el-date-picker': true,
      'el-switch': true
    }
  })
  await Vue.nextTick()
  await new Promise(resolve => setTimeout(resolve, 0))
  return wrapper
}

describe('ListDetail — 名单内容管理', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountPage() })
  afterEach(() => { if (wrapper) wrapper.destroy(); jest.clearAllMocks() })

  test('初始化加载名单详情、记录和日志', () => {
    expect(ruleListApi.getLibrary).toHaveBeenCalledWith(9)
    expect(ruleListApi.listRecords).toHaveBeenCalled()
    expect(ruleListApi.listRecordLogs).toHaveBeenCalled()
    expect(wrapper.vm.records[0].itemContent).toBe('13800138000')
  })

  test('日志展示有效期和后端返回的变更内容', () => {
    expect(wrapper.vm.formatPeriod(wrapper.vm.logs[0])).toBe('2026-07-01 00:00:00 至 2026-12-31 23:59:59')
    expect(wrapper.vm.logs[0].changeContent).toContain('2026-06-01 00:00:00 至 2026-06-30 23:59:59')
  })

  test('保存新增记录时合并有效期并调用创建接口', async () => {
    ruleListApi.createRecord.mockResolvedValue({ data: { id: 2 } })
    wrapper.vm.handleCreate()
    wrapper.vm.form.itemContent = '110101199001010011'
    wrapper.vm.form.itemType = 'ID_CARD'
    wrapper.vm.validRange = ['2026-01-01 00:00:00', '2026-12-31 23:59:59']
    wrapper.vm.submit()
    await Vue.nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(ruleListApi.createRecord).toHaveBeenCalledWith(9, expect.objectContaining({
      itemContent: '110101199001010011',
      itemType: 'ID_CARD',
      effectiveTime: '2026-01-01T00:00:00',
      expireTime: '2026-12-31T23:59:59',
      lastOperation: 'ADD'
    }))
  })

  test('编辑记录时将空格日期规范化为后端可解析格式', async () => {
    ruleListApi.updateRecord.mockResolvedValue({ data: { id: 1 } })
    wrapper.vm.handleEdit({
      id: 1,
      itemContent: '13800138000',
      itemType: 'MOBILE',
      effectiveTime: '2026-06-22 00:00:00',
      expireTime: '2026-12-31 23:59:59',
      status: 1
    })
    wrapper.vm.submit()
    await Vue.nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(ruleListApi.updateRecord).toHaveBeenCalledWith(9, expect.objectContaining({
      id: 1,
      effectiveTime: '2026-06-22T00:00:00',
      expireTime: '2026-12-31T23:59:59',
      lastOperation: 'UPDATE'
    }))
  })

  test('追踪记录时按 recordId 加载该 value 的历史变更', async () => {
    ruleListApi.listRecordLogs.mockClear()
    wrapper.vm.handleTrace({ id: 1, itemContent: '13800138000' })
    await Vue.nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(wrapper.vm.activeTab).toBe('logs')
    expect(wrapper.vm.traceRecord.itemContent).toBe('13800138000')
    expect(ruleListApi.listRecordLogs).toHaveBeenCalledWith(9, { pageNum: 1, pageSize: 100, recordId: 1 })

    ruleListApi.listRecordLogs.mockClear()
    wrapper.vm.clearTrace()
    await Vue.nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(wrapper.vm.traceRecord).toBe(null)
    expect(ruleListApi.listRecordLogs).toHaveBeenCalledWith(9, { pageNum: 1, pageSize: 100 })
  })

  test('删除记录调用删除接口并刷新数据', async () => {
    ruleListApi.deleteRecord.mockResolvedValue({ data: true })
    wrapper.vm.handleDelete({ id: 1, itemContent: '13800138000' })
    await Vue.nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(ruleListApi.deleteRecord).toHaveBeenCalledWith(9, 1)
  })
})

describe('ListDetail route id change', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountPage() })
  afterEach(() => { if (wrapper) wrapper.destroy(); jest.clearAllMocks() })

  test('uses the latest listId when saving after a reused-route change', async () => {
    ruleListApi.createRecord.mockResolvedValue({ data: { id: 3 } })
    wrapper.vm.$options.watch['$route.params.id'].call(wrapper.vm, 12, 9)
    await Vue.nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    wrapper.vm.handleCreate()
    wrapper.vm.form.itemContent = '110101199001010011'
    wrapper.vm.form.itemType = 'ID_CARD'
    wrapper.vm.submit()
    await Vue.nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(wrapper.vm.listId).toBe(12)
    expect(ruleListApi.createRecord).toHaveBeenLastCalledWith(12, expect.objectContaining({
      itemContent: '110101199001010011',
      itemType: 'ID_CARD',
      lastOperation: 'ADD'
    }))
  })
})
