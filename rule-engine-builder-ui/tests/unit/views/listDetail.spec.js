import { mount } from '@test-utils'
import { nextTick } from 'vue'
import * as ruleListApi from '@/api/ruleList'
import ListDetail from '@/views/ruleList/ListDetail.vue'



const FormStub = {
  template: '<form><slot /></form>',
  methods: { validate: vi.fn(cb => cb(true)) }
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
    mocks: {
      $route: { params: { id: 9 } },
      $router: { push: vi.fn() },
      $message: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
      $confirm: vi.fn().mockResolvedValue(true)
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
  await nextTick()
  await new Promise(resolve => setTimeout(resolve, 0))
  return wrapper
}

describe('ListDetail — 名单内容管理', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountPage() })
  afterEach(() => { if (wrapper) wrapper.unmount(); vi.clearAllMocks() })

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
    await nextTick()
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
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(ruleListApi.updateRecord).toHaveBeenCalledWith(9, expect.objectContaining({
      id: 1,
      effectiveTime: '2026-06-22T00:00:00',
      expireTime: '2026-12-31T23:59:59',
      lastOperation: 'UPDATE'
    }))
  })

  test('追踪记录时按 itemType 和 itemContent 加载全部匹配日志', async () => {
    ruleListApi.listRecordLogs.mockClear()
    wrapper.vm.handleTrace({ id: 1, itemType: 'MOBILE', itemContent: '13800138000' })
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(wrapper.vm.activeTab).toBe('logs')
    expect(wrapper.vm.traceRecord.itemContent).toBe('13800138000')
    expect(ruleListApi.listRecordLogs).toHaveBeenCalledWith(9, {
      pageNum: 1,
      pageSize: 10,
      itemType: 'MOBILE',
      itemContent: '13800138000'
    })

    ruleListApi.listRecordLogs.mockClear()
    wrapper.vm.clearTrace()
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(wrapper.vm.traceRecord).toBe(null)
    expect(ruleListApi.listRecordLogs).toHaveBeenCalledWith(9, { pageNum: 1, pageSize: 10 })
  })

  test('名单日志使用服务端分页且翻页保留追踪条件', async () => {
    wrapper.vm.handleTrace({ id: 1, itemType: 'MOBILE', itemContent: '13800138000' })
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))
    ruleListApi.listRecordLogs.mockClear()

    wrapper.vm.onLogPageChange(2)
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(ruleListApi.listRecordLogs).toHaveBeenCalledWith(9, {
      pageNum: 2,
      pageSize: 10,
      itemType: 'MOBILE',
      itemContent: '13800138000'
    })
    expect(wrapper.vm.logTotal).toBe(1)

    ruleListApi.listRecordLogs.mockClear()
    wrapper.vm.onLogSizeChange(30)
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(ruleListApi.listRecordLogs).toHaveBeenCalledWith(9, {
      pageNum: 1,
      pageSize: 30,
      itemType: 'MOBILE',
      itemContent: '13800138000'
    })
  })

  test('删除记录调用删除接口并刷新数据', async () => {
    ruleListApi.deleteRecord.mockResolvedValue({ data: true })
    wrapper.vm.handleDelete({ id: 1, itemContent: '13800138000' })
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(ruleListApi.deleteRecord).toHaveBeenCalledWith(9, 1)
  })
})

describe('ListDetail route id change', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountPage() })
  afterEach(() => { if (wrapper) wrapper.unmount(); vi.clearAllMocks() })

  test('uses the latest listId when saving after a reused-route change', async () => {
    ruleListApi.createRecord.mockResolvedValue({ data: { id: 3 } })
    wrapper.vm.logQuery = { pageNum: 3, pageSize: 30 }
    wrapper.vm.$options.watch['$route.params.id'].call(wrapper.vm, 12, 9)
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    wrapper.vm.handleCreate()
    wrapper.vm.form.itemContent = '110101199001010011'
    wrapper.vm.form.itemType = 'ID_CARD'
    wrapper.vm.submit()
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(wrapper.vm.listId).toBe(12)
    expect(wrapper.vm.logQuery).toEqual({ pageNum: 1, pageSize: 30 })
    expect(ruleListApi.createRecord).toHaveBeenLastCalledWith(12, expect.objectContaining({
      itemContent: '110101199001010011',
      itemType: 'ID_CARD',
      lastOperation: 'ADD'
    }))
  })
})
