import { mount } from '@test-utils'
import { nextTick } from 'vue'
import * as ruleListApi from '@/api/ruleList'
import ListDetail from '@/views/ruleList/ListDetail.vue'

const FormStub = {
  template: '<form><slot /></form>',
  methods: { validate: vi.fn(cb => cb(true)) }
}

async function flush() {
  await nextTick()
  await new Promise(resolve => setTimeout(resolve, 0))
}

function batchResult(overrides = {}) {
  return {
    submittable: true,
    approvalRequestId: 91,
    totalCount: 1,
    addCount: 1,
    updateCount: 0,
    deleteCount: 0,
    duplicateCount: 0,
    invalidCount: 0,
    errors: [],
    ...overrides
  }
}

async function mountPage(permissionValues) {
  ruleListApi.getLibrary.mockResolvedValue({
    data: {
      id: 9,
      listCode: 'mobile_black',
      listName: 'Mobile blacklist',
      listType: 'BLACK'
    }
  })
  ruleListApi.listRecords.mockResolvedValue({
    data: {
      records: [{
        id: 1,
        itemContent: '13800138000',
        itemType: 'MOBILE',
        status: 1,
        lastOperation: 'ADD'
      }],
      total: 1
    }
  })
  ruleListApi.listRecordLogs.mockResolvedValue({
    data: {
      records: [{
        id: 1,
        itemContent: '13800138000',
        operation: 'UPDATE',
        effectiveTime: '2026-07-01T00:00:00',
        expireTime: '2026-12-31T23:59:59',
        changeContent: 'validity period changed'
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
      'el-switch': true,
      'el-alert': true
    },
    directives: permissionValues ? {
      permission: {
        mounted: (_element, binding) => permissionValues.push(binding.value)
      }
    } : undefined
  })
  await flush()
  return wrapper
}

describe('ListDetail governed record changes', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountPage() })
  afterEach(() => {
    if (wrapper) wrapper.unmount()
    vi.clearAllMocks()
  })

  test('loads effective library, records, and audit logs', () => {
    expect(ruleListApi.getLibrary).toHaveBeenCalledWith(9)
    expect(ruleListApi.listRecords).toHaveBeenCalled()
    expect(ruleListApi.listRecordLogs).toHaveBeenCalled()
    expect(wrapper.vm.records[0].itemContent).toBe('13800138000')
    expect(wrapper.vm.formatPeriod(wrapper.vm.logs[0]))
      .toBe('2026-07-01 00:00:00 \u81f3 2026-12-31 23:59:59')
  })

  test('名单内容变更与导入入口受字段编辑权限控制', async () => {
    wrapper.unmount()
    const permissions = []
    wrapper = await mountPage(permissions)

    expect(permissions).toContain('field:edit')
  })

  test('日志组合筛选和翻页保留条件，重置回到第一页', async () => {
    Object.assign(wrapper.vm.logQuery, { pageNum: 3, pageSize: 30, itemType: 'MOBILE', operation: 'UPDATE', keyword: '核验' })
    wrapper.vm.logTimeRange = ['2026-08-01 00:00:00', '2026-08-31 23:59:59']
    wrapper.vm.handleLogQuery()
    await flush()
    const filters = { pageNum: 1, pageSize: 30, itemType: 'MOBILE', operation: 'UPDATE', keyword: '核验', startTime: '2026-08-01 00:00:00', endTime: '2026-08-31 23:59:59' }
    expect(ruleListApi.listRecordLogs).toHaveBeenLastCalledWith(9, filters)
    wrapper.vm.onLogPageChange(2)
    await flush()
    expect(ruleListApi.listRecordLogs).toHaveBeenLastCalledWith(9, { ...filters, pageNum: 2 })
    wrapper.vm.resetLogQuery()
    await flush()
    expect(ruleListApi.listRecordLogs).toHaveBeenLastCalledWith(9, { pageNum: 1, pageSize: 30 })
    expect(wrapper.vm.logTimeRange).toEqual([])
  })

  test('日志候选使用当前名单和筛选条件，不改变列表分页', async () => {
    Object.assign(wrapper.vm.logQuery, { pageNum: 4, operation: 'DELETE', itemType: 'IP' })
    await wrapper.vm.fetchLogKeywordOptions({ query: '192.', pageNum: 1, pageSize: 20 })
    expect(ruleListApi.listRecordLogs).toHaveBeenLastCalledWith(9, { pageNum: 1, pageSize: 20, itemType: 'IP', operation: 'DELETE', keyword: '192.' })
    expect(wrapper.vm.logQuery.pageNum).toBe(4)
  })

  test('进入追踪清除旧筛选，重置仍保留精确记录，查看全部才退出追踪', async () => {
    Object.assign(wrapper.vm.logQuery, { itemType: 'IP', operation: 'DELETE', keyword: '旧条件' })
    wrapper.vm.logTimeRange = ['2026-08-01 00:00:00', '2026-08-02 00:00:00']
    wrapper.vm.handleTrace({ itemType: 'MOBILE', itemContent: '13800138000' })
    await flush()
    const identity = { pageNum: 1, pageSize: 10, itemType: 'MOBILE', itemContent: '13800138000' }
    expect(ruleListApi.listRecordLogs).toHaveBeenLastCalledWith(9, identity)
    wrapper.vm.logQuery.operation = 'UPDATE'
    wrapper.vm.resetLogQuery()
    await flush()
    expect(ruleListApi.listRecordLogs).toHaveBeenLastCalledWith(9, identity)
    wrapper.vm.clearTrace()
    await flush()
    expect(ruleListApi.listRecordLogs).toHaveBeenLastCalledWith(9, { pageNum: 1, pageSize: 10 })
  })

  test('new record stages approval with normalized validity period', async () => {
    ruleListApi.stageRecordChange.mockResolvedValue({ data: batchResult() })
    wrapper.vm.handleCreate()
    wrapper.vm.form.itemContent = '110101199001010011'
    wrapper.vm.form.itemType = 'ID_CARD'
    wrapper.vm.validRange = [
      '2026-01-01 00:00:00',
      '2026-12-31 23:59:59'
    ]

    wrapper.vm.submit()
    await flush()

    expect(ruleListApi.stageRecordChange).toHaveBeenCalledWith(
      9,
      'ADD',
      expect.objectContaining({
        itemContent: '110101199001010011',
        itemType: 'ID_CARD',
        effectiveTime: '2026-01-01T00:00:00',
        expireTime: '2026-12-31T23:59:59'
      })
    )
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/approval/91')
  })

  test('edit record stages update approval with normalized dates', async () => {
    ruleListApi.stageRecordChange.mockResolvedValue({
      data: batchResult({
        approvalRequestId: 92,
        addCount: 0,
        updateCount: 1
      })
    })
    wrapper.vm.handleEdit({
      id: 1,
      itemContent: '13800138000',
      itemType: 'MOBILE',
      effectiveTime: '2026-06-22 00:00:00',
      expireTime: '2026-12-31 23:59:59',
      status: 1
    })

    wrapper.vm.submit()
    await flush()

    expect(ruleListApi.stageRecordChange).toHaveBeenCalledWith(
      9,
      'UPDATE',
      expect.objectContaining({
        id: 1,
        effectiveTime: '2026-06-22T00:00:00',
        expireTime: '2026-12-31T23:59:59'
      })
    )
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/approval/92')
  })

  test('record trace keeps server pagination and selected identity', async () => {
    ruleListApi.listRecordLogs.mockClear()
    wrapper.vm.handleTrace({
      id: 1,
      itemType: 'MOBILE',
      itemContent: '13800138000'
    })
    await flush()

    expect(wrapper.vm.activeTab).toBe('logs')
    expect(ruleListApi.listRecordLogs).toHaveBeenCalledWith(9, {
      pageNum: 1,
      pageSize: 10,
      itemType: 'MOBILE',
      itemContent: '13800138000'
    })

    ruleListApi.listRecordLogs.mockClear()
    wrapper.vm.onLogPageChange(2)
    await flush()
    expect(ruleListApi.listRecordLogs).toHaveBeenCalledWith(9, {
      pageNum: 2,
      pageSize: 10,
      itemType: 'MOBILE',
      itemContent: '13800138000'
    })

    ruleListApi.listRecordLogs.mockClear()
    wrapper.vm.clearTrace()
    await flush()
    expect(ruleListApi.listRecordLogs).toHaveBeenCalledWith(9, {
      pageNum: 1,
      pageSize: 10
    })
  })

  test('delete stages approval instead of mutating record directly', async () => {
    ruleListApi.stageRecordChange.mockResolvedValue({
      data: batchResult({
        approvalRequestId: 93,
        addCount: 0,
        deleteCount: 1
      })
    })

    wrapper.vm.handleDelete({ id: 1, itemContent: '13800138000' })
    await flush()

    expect(ruleListApi.stageRecordChange).toHaveBeenCalledWith(
      9, 'DELETE', expect.objectContaining({ id: 1 })
    )
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/approval/93')
  })

  test('invalid import stays on page and exposes actionable row errors', async () => {
    ruleListApi.importRecords.mockResolvedValue({
      data: batchResult({
        submittable: false,
        approvalRequestId: null,
        totalCount: 2,
        invalidCount: 1,
        errors: ['row 3: expiration is before effective time']
      })
    })
    const event = {
      target: {
        files: [new File(['x'], 'list.xlsx')],
        value: 'x'
      }
    }

    await wrapper.vm.handleFileChange(event)

    expect(wrapper.vm.batchFeedback.errors[0]).toContain('row 3')
    expect(wrapper.vm.$router.push).not.toHaveBeenCalled()
    expect(ruleListApi.listRecords).toHaveBeenCalledTimes(1)
  })
})

describe('ListDetail route reuse', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountPage() })
  afterEach(() => {
    if (wrapper) wrapper.unmount()
    vi.clearAllMocks()
  })

  test('stages against the latest list id after route change', async () => {
    ruleListApi.stageRecordChange.mockResolvedValue({
      data: batchResult({ approvalRequestId: 94 })
    })
    wrapper.vm.logQuery = { pageNum: 3, pageSize: 30 }
    wrapper.vm.query.keyword = '前一个名单'
    wrapper.vm.logTimeRange = ['2026-08-01 00:00:00', '2026-08-31 23:59:59']
    wrapper.vm.$options.watch['$route.params.id'].call(wrapper.vm, 12, 9)
    await flush()

    wrapper.vm.handleCreate()
    wrapper.vm.form.itemContent = '110101199001010011'
    wrapper.vm.form.itemType = 'ID_CARD'
    wrapper.vm.submit()
    await flush()

    expect(wrapper.vm.listId).toBe(12)
    expect(wrapper.vm.logQuery).toEqual({ pageNum: 1, pageSize: 30, itemType: '', operation: '', keyword: '' })
    expect(wrapper.vm.query.keyword).toBe('')
    expect(wrapper.vm.logTimeRange).toEqual([])
    expect(ruleListApi.stageRecordChange).toHaveBeenLastCalledWith(
      12,
      'ADD',
      expect.objectContaining({
        itemContent: '110101199001010011',
        itemType: 'ID_CARD'
      })
    )
  })
})
