import BillingList from '@/views/billing/BillingList.vue'
import { nextTick } from 'vue'
import { shallowMount, mount, flushPromises } from '@test-utils'
import * as billingApi from '@/api/billing'
import { listDefinitions, getDefinition } from '@/api/definition'
import { listApiConfigs, getApiConfigTrial } from '@/api/datasource'
import { ElTable, ElTableColumn } from 'element-plus/es/components/table/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElAlert } from 'element-plus/es/components/alert/index.mjs'
import { ElTag } from 'element-plus/es/components/tag/index.mjs'
import { listDbDatasources } from '@/api/database'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'

vi.mock('@/api/datasource', () => ({ listApiConfigs: vi.fn(), getApiConfig: vi.fn(), getApiConfigTrial: vi.fn() }))

test('计费汇总接入公共版本筛选并在查询和重置中保留正确语义', async () => {
  const wrapper = shallowMount(BillingList)
  await wrapper.setData({ activeTab: 'summary' })
  await flushPromises()
  const selector = wrapper.findAllComponents({ name: 'ExecutionVersionFilter' }).find(component => component.props('modelValue') === wrapper.vm.summaryQuery)
  expect(selector, '汇总页必须能选择规则版本').toBeDefined()
  await wrapper.setData({ summaryQuery: { ...wrapper.vm.summaryQuery, ruleId: '164', versionMode: 'FIXED', ruleVersion: 1 } })
  await wrapper.vm.loadSummaries()
  expect(billingApi.listBillingSummaries).toHaveBeenLastCalledWith(expect.objectContaining({ ruleId: '164', versionMode: 'FIXED', ruleVersion: 1 }))
  wrapper.vm.resetSummaryQuery(); await flushPromises()
  expect(wrapper.vm.summaryQuery.ruleId).toBeFalsy()
  expect(wrapper.vm.summaryQuery.ruleVersion).toBeFalsy()
  wrapper.unmount()
})

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

const trialBill = (id = 'trial-poll') => ({
  id, billingBasis: 'DRAFT_TRIAL', rootTraceId: id, billingCode: 'Unchanged_Bill',
  apiCode: 'Unchanged_API', occurTime: '2026-10-09T03:35:21', success: 0,
  quantity: 1, unitPrice: '0.120000', amount: '0.120000', currency: 'CNY',
  errorMessage: '待恢复步骤',
})

async function mountRecords(records = [trialBill()]) {
  billingApi.listBillingConfigs.mockResolvedValue({ data: { records: [], total: 0 } })
  const wrapper = mount(BillingList, {
    stubs: {
      'el-table': ElTable, 'el-table-column': ElTableColumn, 'el-alert': ElAlert,
      'el-button': ElButton, 'el-tag': ElTag,
      'el-dialog': { props: ['modelValue', 'title'], template: '<section v-if="modelValue" :aria-label="title"><slot /><slot name="footer" /></section>' },
      RemoteFilterSelect: true, TableColumnSettings: true, ProjectFilterSelect: true,
    },
  })
  await wrapper.setData({ activeTab: 'record', recordList: records })
  await flushPromises()
  return wrapper
}

describe('BillingList 草稿试运行计费事实与当前状态', () => {
  beforeEach(() => getApiConfigTrial.mockReset())
  afterEach(() => vi.clearAllMocks())

  test('真实表格区分计费依据和当时未完成，普通失败语义保留且列表不批量请求会话', async () => {
    const wrapper = await mountRecords([trialBill(), { ...trialBill('normal'), billingBasis: 'EXECUTION_SNAPSHOT' }, { ...trialBill('success'), success: 1 }])
    try {
      const table = wrapper.findAllComponents(ElTable).find(item => item.props('data') === wrapper.vm.recordList)
      expect(table.text()).toContain('计费依据')
      expect(table.text()).toContain('关联 ID')
      expect(table.text()).toContain('计费时执行情况')
      expect(table.text()).toContain('草稿试运行')
      const rows = table.findAll('.el-table__body tbody tr')
      expect(rows[0].find('.el-tag--warning').text()).toBe('当时未完成或未成功')
      expect(rows[1].find('.el-tag--danger').text()).toBe('失败')
      expect(rows[2].find('.el-tag--success').text()).toBe('成功')
      expect(getApiConfigTrial).not.toHaveBeenCalled()
      expect(wrapper.vm.recordColumns.map(item => item.key)).toEqual(expect.arrayContaining(['billingBasis', 'rootTraceId', 'trialStatus', 'unitPrice']))
    } finally { wrapper.unmount() }
  })

  test('成功会话对照原账单时间金额和未完成记录，不改写费用事实', async () => {
    const row = trialBill()
    getApiConfigTrial.mockResolvedValue({ data: { trialId: row.rootTraceId, status: 'SUCCEEDED' } })
    const wrapper = await mountRecords([row])
    try {
      await wrapper.get('[data-testid="billing-trial-trial-poll"]').trigger('click')
      await flushPromises()
      expect(getApiConfigTrial).toHaveBeenCalledExactlyOnceWith('trial-poll')
      const dialog = wrapper.get('[data-testid="billing-trial-detail"]')
      expect(dialog.text()).toContain('当时未完成或未成功')
      expect(dialog.text()).toContain('执行完成')
      expect(dialog.text()).toContain('2026-10-09 03:35:21')
      expect(dialog.text()).toContain('0.120000')
      expect(dialog.find('[role="alert"]').text()).toContain('不代表当前试运行状态')
      expect(wrapper.vm.recordList[0]).toEqual(row)
    } finally { wrapper.unmount() }
  })

  test('当前状态入口固定右侧且数据列不叠加固定宽度遮挡窄屏', async () => {
    const wrapper = await mountRecords()
    try {
      const columns = wrapper.findAllComponents(ElTable).find(item => item.props('data') === wrapper.vm.recordList).findAllComponents(ElTableColumn)
      expect(columns.find(column => column.props('label') === '当前试运行状态').props('fixed')).toBe('right')
      for (const key of ['quantity', 'amount', 'costTimeMs']) {
        expect(columns.find(column => column.props('prop') === key).props('fixed')).not.toBe('right')
      }
    } finally { wrapper.unmount() }
  })

  test('汇总按计费时结果统计，不能把未完成的试运行计入最终失败率展示', async () => {
    const wrapper = await mountRecords()
    try {
      await wrapper.setData({ activeTab: 'summary', summaryList: [{ totalCount: 4, successCount: 1, failCount: 3 }] })
      const table = wrapper.findAllComponents(ElTable).find(item => item.props('data') === wrapper.vm.summaryList)
      expect(table.text()).toContain('计费时成功')
      expect(table.text()).toContain('计费时未成功')
      expect(wrapper.get('[data-testid="billing-summary-basis"]').text()).toContain('不代表试运行最终成功率')
      expect(wrapper.vm.summaryList[0]).toEqual({ totalCount: 4, successCount: 1, failCount: 3 })
    } finally { wrapper.unmount() }
  })

  test('权限或会话缺失明确无法查看且可重试，不伪装成当前执行失败', async () => {
    getApiConfigTrial.mockRejectedValueOnce(new Error('无权查看该会话'))
      .mockResolvedValueOnce({ data: { trialId: 'trial-poll', status: 'CANCELLED' } })
    const wrapper = await mountRecords()
    try {
      await wrapper.get('[data-testid="billing-trial-trial-poll"]').trigger('click')
      await flushPromises()
      const dialog = wrapper.get('[data-testid="billing-trial-detail"]')
      expect(dialog.find('.el-alert--warning').text()).toContain('无法查看当前试运行状态')
      expect(dialog.text()).toContain('本人')
      expect(dialog.text()).not.toContain('执行失败')
      await wrapper.get('[data-testid="billing-trial-refresh"]').trigger('click')
      await flushPromises()
      expect(dialog.text()).toContain('已取消')
      expect(dialog.find('.el-alert--warning').exists()).toBe(false)
    } finally { wrapper.unmount() }
  })

  test('切换账单时旧请求迟到不会覆盖新会话或费用', async () => {
    const first = deferred()
    getApiConfigTrial.mockReturnValueOnce(first.promise).mockResolvedValueOnce({ data: { trialId: 'second', status: 'FAILED' } })
    const wrapper = await mountRecords([trialBill(), { ...trialBill('second'), amount: '0.24' }])
    try {
      await wrapper.get('[data-testid="billing-trial-trial-poll"]').trigger('click')
      await wrapper.get('[data-testid="billing-trial-second"]').trigger('click')
      await flushPromises()
      first.resolve({ data: { trialId: 'trial-poll', status: 'SUCCEEDED' } })
      await flushPromises()
      const dialog = wrapper.get('[data-testid="billing-trial-detail"]')
      expect(dialog.text()).toContain('second')
      expect(dialog.text()).toContain('0.24')
      expect(dialog.text()).toContain('执行失败')
      expect(dialog.text()).not.toContain('执行完成')
    } finally { wrapper.unmount() }
  })

  test('关窗使未完成查询失效，再打开同一账单也不能接收旧响应', async () => {
    const first = deferred()
    getApiConfigTrial.mockReturnValueOnce(first.promise).mockResolvedValueOnce({ data: { trialId: 'trial-poll', status: 'WAITING' } })
    const wrapper = await mountRecords()
    try {
      await wrapper.get('[data-testid="billing-trial-trial-poll"]').trigger('click')
      await wrapper.get('[data-testid="billing-trial-close"]').trigger('click')
      expect(wrapper.find('[data-testid="billing-trial-detail"]').exists()).toBe(false)
      await wrapper.get('[data-testid="billing-trial-trial-poll"]').trigger('click')
      await flushPromises()
      first.resolve({ data: { trialId: 'trial-poll', status: 'SUCCEEDED' } })
      await flushPromises()
      expect(wrapper.get('[data-testid="billing-trial-detail"]').text()).toContain('等待结果')
      expect(wrapper.get('[data-testid="billing-trial-detail"]').text()).not.toContain('执行完成')
    } finally { wrapper.unmount() }
  })

  test('刷新中与失败后不继续把上次查询状态当成当前状态', async () => {
    const pending = deferred()
    getApiConfigTrial.mockResolvedValueOnce({ data: { trialId: 'trial-poll', status: 'WAITING' } }).mockReturnValueOnce(pending.promise)
    const wrapper = await mountRecords()
    try {
      await wrapper.get('[data-testid="billing-trial-trial-poll"]').trigger('click')
      await flushPromises()
      const dialog = wrapper.get('[data-testid="billing-trial-detail"]')
      await wrapper.get('[data-testid="billing-trial-refresh"]').trigger('click')
      expect(dialog.text()).not.toContain('等待结果')
      expect(dialog.text()).toContain('正在查询')
      pending.reject(new Error('网络断开'))
      await flushPromises()
      expect(dialog.text()).not.toContain('等待结果')
      expect(dialog.text()).toContain('无法查看当前试运行状态')
    } finally { wrapper.unmount() }
  })

  test('缺失关联ID或普通账单不按编码猜测试运行，错误响应ID不串用', async () => {
    getApiConfigTrial.mockResolvedValue({ data: { trialId: 'different', status: 'SUCCEEDED' } })
    const wrapper = await mountRecords([trialBill(), { ...trialBill('no-id'), rootTraceId: '' }, { ...trialBill('formal'), billingBasis: 'CURRENT_CONFIG' }])
    try {
      expect(wrapper.findAll('[data-testid^="billing-trial-"]').length).toBe(1)
      await wrapper.get('[data-testid="billing-trial-trial-poll"]').trigger('click')
      await flushPromises()
      expect(wrapper.get('[data-testid="billing-trial-detail"]').text()).toContain('无法查看当前试运行状态')
      expect(wrapper.get('[data-testid="billing-trial-detail"]').text()).not.toContain('执行完成')
    } finally { wrapper.unmount() }
  })
})

function createContext(overrides = {}) {
  const ctx = {
    ...BillingList.data.call({ emptyConfigForm: BillingList.methods.emptyConfigForm }),
    targetOptions: [],
    targetLoading: false,
    configOriginalStatus: null,
    configDialogVisible: false,
    configSaving: false,
    configForm: {
      id: null,
      scope: 'PROJECT',
      projectId: 7,
      billingCode: 'Engine_Count',
      billingName: '规则计费',
      billingTarget: 'ENGINE',
      targetRefId: 9,
      chargeType: 'COUNT',
      unitPrice: 0,
      currency: 'CNY',
      effectiveTime: '',
      expireTime: '',
      status: 1
    },
    $refs: { configForm: { validate: callback => callback(true) } },
    $router: { push: vi.fn() },
    $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    $confirm: vi.fn().mockResolvedValue(true),
    ...overrides
  }
  Object.keys(BillingList.methods).forEach(name => {
    ctx[name] = BillingList.methods[name].bind(ctx)
  })
  return ctx
}

describe('BillingList target selector', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    for (const api of [listDefinitions, listApiConfigs, listDbDatasources,
      billingApi.listBillingConfigs, billingApi.listBillingRecords, billingApi.listBillingSummaries]) {
      api.mockReset().mockResolvedValue({ data: { records: [], total: 0 } })
    }
  })

  test('切换计费类型时清空旧候选，迟到的规则响应不能覆盖数据库候选', async () => {
    const oldRequest = deferred(), currentRequest = deferred()
    listDefinitions.mockReturnValueOnce(oldRequest.promise)
    listDbDatasources.mockReturnValueOnce(currentRequest.promise)
    const ctx = createContext({ targetOptions: [{ id: 8, ruleName: '旧规则' }] })
    const first = ctx.loadTargetOptions()
    ctx.configForm.billingTarget = 'DB'
    const second = ctx.loadTargetOptions()
    expect(ctx.targetOptions).toEqual([])
    currentRequest.resolve({ data: { records: [{ id: 22, datasourceName: '数据库' }] } })
    await second
    oldRequest.resolve({ data: { records: [{ id: 11, ruleName: '迟到规则' }] } })
    await first
    expect(ctx.targetOptions).toEqual([{ id: 22, datasourceName: '数据库' }])
    expect(ctx.targetLoading).toBe(false)
  })

  test('切换项目后旧请求失败不能结束新请求的加载状态', async () => {
    const oldRequest = deferred(), currentRequest = deferred()
    listDefinitions.mockReturnValueOnce(oldRequest.promise).mockReturnValueOnce(currentRequest.promise)
    const ctx = createContext()
    const first = ctx.loadTargetOptions()
    ctx.configForm.projectId = 9
    const second = ctx.loadTargetOptions()
    oldRequest.reject(new Error('旧项目网络失败'))
    await first
    expect(ctx.targetLoading).toBe(true)
    expect(ctx.targetError).toBe('')
    currentRequest.resolve({ data: { records: [{ id: 33, ruleName: '新项目规则' }] } })
    await second
    expect(ctx.targetOptions).toEqual([{ id: 33, ruleName: '新项目规则' }])
  })

  test('候选加载失败明确提示并可重试，不能保留旧选项', async () => {
    listDefinitions.mockRejectedValueOnce(new Error('网络异常'))
      .mockResolvedValueOnce({ data: { records: [{ id: 44, ruleName: '重试成功' }] } })
    const ctx = createContext({ targetOptions: [{ id: 8 }] })
    await ctx.loadTargetOptions()
    expect(ctx.targetOptions).toEqual([])
    expect(ctx.targetError).toContain('加载失败')
    expect(ctx.targetLoading).toBe(false)
    await ctx.loadTargetOptions()
    expect(ctx.targetError).toBe('')
    expect(ctx.targetOptions).toEqual([{ id: 44, ruleName: '重试成功' }])
  })

  test('关闭配置弹窗使未完成的候选查询失效', async () => {
    const oldRequest = deferred()
    listDefinitions.mockReturnValueOnce(oldRequest.promise)
    const ctx = createContext()
    const loading = ctx.loadTargetOptions()
    ctx.closeConfigDialog()
    oldRequest.resolve({ data: { records: [{ id: 11, ruleName: '已关闭的候选' }] } })
    await loading
    expect(ctx.targetOptions).toEqual([])
    expect(ctx.targetLoading).toBe(false)
  })

  test.each([
    ['config', 'loadConfigs', 'listBillingConfigs'],
    ['record', 'loadRecords', 'listBillingRecords'],
    ['summary', 'loadSummaries', 'listBillingSummaries'],
  ])('%s 查询只显示最后提交条件的结果，失败可重试', async (kind, method, api) => {
    const oldRequest = deferred(), currentRequest = deferred()
    billingApi[api].mockReturnValueOnce(oldRequest.promise).mockReturnValueOnce(currentRequest.promise)
    const ctx = createContext({ [kind + 'List']: [{ id: 99 }] })
    ctx[kind + 'Query'].projectCode = 'A'
    const first = ctx[method]()
    ctx[kind + 'Query'].projectCode = 'B'
    const second = ctx[method]()
    expect(ctx[kind + 'List']).toEqual([])
    currentRequest.resolve({ data: { records: [{ id: 2, projectCode: 'B' }], total: 22 } })
    await second
    oldRequest.resolve({ data: { records: [{ id: 1, projectCode: 'A' }], total: 11 } })
    await first
    expect(ctx[kind + 'List']).toEqual([{ id: 2, projectCode: 'B' }])
    expect(ctx[kind + 'Total']).toBe(22)
    billingApi[api].mockRejectedValueOnce(new Error('最新查询失败'))
    await ctx[method]()
    expect(ctx[kind + 'List']).toEqual([])
    expect(ctx[kind + 'Error']).toContain('加载失败')
    expect(ctx[kind + 'Loading']).toBe(false)
    billingApi[api].mockResolvedValueOnce({ data: { records: [], total: 0 } })
    await ctx[method]()
    expect(ctx[kind + 'Error']).toBe('')
  })

  test.each([
    ['config', 'loadConfigs', 'listBillingConfigs'],
    ['record', 'loadRecords', 'listBillingRecords'],
    ['summary', 'loadSummaries', 'listBillingSummaries'],
  ])('%s 旧查询失败不清除当前加载状态或显示旧错误', async (kind, method, api) => {
    const oldRequest = deferred(), currentRequest = deferred()
    billingApi[api].mockReturnValueOnce(oldRequest.promise).mockReturnValueOnce(currentRequest.promise)
    const ctx = createContext()
    const first = ctx[method](), second = ctx[method]()
    oldRequest.reject(new Error('过期查询失败'))
    await first
    expect(ctx[kind + 'Loading']).toBe(true)
    expect(ctx[kind + 'Error']).toBe('')
    currentRequest.resolve({ data: { records: [{ id: 2 }], total: 1 } })
    await second
    expect(ctx[kind + 'List']).toEqual([{ id: 2 }])
  })

  test('计费配置和刷新汇总入口受账单编辑权限控制', async () => {
    billingApi.listBillingConfigs.mockResolvedValue({
      data: { records: [], total: 0 }
    })
    const permissions = []
    const wrapper = shallowMount(BillingList, {
      mocks: { $message: { error: vi.fn() } },
      directives: {
        permission: {
          mounted: (_element, binding) => permissions.push(binding.value)
        }
      }
    })
    await nextTick()

    expect(permissions).toContain('billing:edit')
    wrapper.unmount()
  })

  test('uses project code and name fuzzy filters in all list queries', () => {
    const data = BillingList.data.call({ emptyConfigForm: BillingList.methods.emptyConfigForm })

    expect(BillingList.components.ProjectFilterSelect).toBe(ProjectFilterSelect)
    expect(data.configQuery).toEqual(expect.objectContaining({ projectCode: '', projectName: '' }))
    expect(data.recordQuery).toEqual(expect.objectContaining({ projectCode: '', projectName: '' }))
    expect(data.summaryQuery).toEqual(expect.objectContaining({ projectCode: '', projectName: '' }))
  })

  test('loadTargetOptions loads rule definitions for ENGINE target', async () => {
    listDefinitions.mockResolvedValue({ data: { records: [{ id: 11, ruleName: 'Rule A', ruleCode: 'rule_a' }] } })
    const ctx = createContext()

    await ctx.loadTargetOptions()

    expect(listDefinitions).toHaveBeenCalledWith({ pageNum: 1, pageSize: 20, status: 1, projectId: 7 })
    expect(ctx.targetOptions).toEqual([{ id: 11, ruleName: 'Rule A', ruleCode: 'rule_a' }])
    expect(ctx.targetLoading).toBe(false)
  })

  test('指定对象支持第 26 页和搜索，并按 ID 回显不在本页的旧选择', async () => {
    listDefinitions.mockResolvedValue({ data: { records: [{ id: 501, ruleName: '目标规则' }], total: 1001 } })
    getDefinition.mockResolvedValue({ data: { id: 900, ruleName: '已选择规则', ruleCode: 'Keep_Code' } })
    const ctx = createContext({ configForm: { scope: 'PROJECT', projectId: 7, billingTarget: 'ENGINE', targetRefId: 900 } })
    const response = await ctx.loadTargetOptions({ query: '目标规则', pageNum: 26, pageSize: 20 })
    expect(listDefinitions).toHaveBeenCalledWith({ pageNum: 26, pageSize: 20, status: 1, projectId: 7, keyword: '目标规则' })
    expect(response.data.total).toBe(1001)
    expect(getDefinition).toHaveBeenCalledWith(900)
    expect(BillingList.computed.selectedTargetOption.call(ctx)).toMatchObject({ id: 900, displayName: '已选择规则 / Keep_Code' })
  })

  test('loadTargetOptions loads API configs for API target', async () => {
    listApiConfigs.mockResolvedValue({ data: { records: [{ id: 21, apiName: 'Credit API', apiCode: 'credit_api' }] } })
    const ctx = createContext({ configForm: { scope: 'PROJECT', projectId: 7, billingTarget: 'API', targetRefId: null } })

    await ctx.loadTargetOptions()

    expect(listApiConfigs).toHaveBeenCalledWith({ pageNum: 1, pageSize: 20, status: 1 })
    expect(ctx.targetOptions[0].id).toBe(21)
  })

  test('loadTargetOptions loads DB datasources for DB target', async () => {
    listDbDatasources.mockResolvedValue({ data: { records: [{ id: 31, datasourceName: 'Risk DB', datasourceCode: 'risk_db' }] } })
    const ctx = createContext({ configForm: { scope: 'PROJECT', projectId: 7, billingTarget: 'DB', targetRefId: null } })

    await ctx.loadTargetOptions()

    expect(listDbDatasources).toHaveBeenCalledWith({ pageNum: 1, pageSize: 20, status: 1, projectId: 7 })
    expect(ctx.targetOptions[0].id).toBe(31)
  })

  test('target change clears selected reference before reloading options', () => {
    const ctx = createContext()
    ctx.loadTargetOptions = vi.fn()

    ctx.onBillingTargetChange()

    expect(ctx.configForm.targetRefId).toBeNull()
    expect(ctx.loadTargetOptions).toHaveBeenCalled()
  })

  test('normalizeConfig clears global project and blank optional fields', () => {
    const ctx = createContext()
    const data = ctx.normalizeConfig({
      scope: 'GLOBAL',
      projectId: 7,
      targetRefId: null,
      effectiveTime: '',
      expireTime: ''
    })

    expect(data.projectId).toBe(0)
    expect(data.targetRefId).toBeNull()
    expect(data.effectiveTime).toBeNull()
    expect(data.expireTime).toBeNull()
  })

  test('targetOptionLabel renders a readable name and code', () => {
    const ctx = createContext()

    expect(ctx.targetOptionLabel({ id: 1, apiName: 'Credit API', apiCode: 'credit_api' })).toBe('Credit API / credit_api')
  })

  test('billing detail keeps authentication and token filters', async () => {
    billingApi.listBillingRecords.mockResolvedValue({ data: { records: [], total: 0 } })
    const ctx = createContext({
      recordLoading: false,
      recordList: [],
      recordTotal: 0,
      recordQuery: {
        pageNum: 1, pageSize: 10, authType: 'BASIC', authCode: 'BASIC_MAIN', tokenCode: 'TOKEN_A'
      }
    })

    await ctx.loadRecords()

    expect(billingApi.listBillingRecords).toHaveBeenCalledWith(expect.objectContaining({
      authType: 'BASIC', authCode: 'BASIC_MAIN', tokenCode: 'TOKEN_A'
    }))
  })

  test('billing summary groups and filters by authentication but not token', async () => {
    billingApi.listBillingSummaries.mockResolvedValue({ data: { records: [], total: 0 } })
    const ctx = createContext({
      summaryLoading: false,
      summaryList: [],
      summaryTotal: 0,
      summaryQuery: { pageNum: 1, pageSize: 10, authType: 'API_KEY', authCode: 'PARTNER_API' }
    })

    await ctx.loadSummaries()

    expect(billingApi.listBillingSummaries).toHaveBeenCalledWith(expect.objectContaining({
      authType: 'API_KEY', authCode: 'PARTNER_API'
    }))
    expect(billingApi.listBillingSummaries.mock.calls[0][0].tokenCode).toBeUndefined()
  })

  test('tab click uses Element Plus paneName and loads the selected tab', async () => {
    const ctx = createContext({ activeTab: 'config' })
    ctx.loadConfigs = vi.fn()
    ctx.loadRecords = vi.fn()
    ctx.loadSummaries = vi.fn()

    await ctx.onTabChange({ paneName: 'record' })

    expect(ctx.activeTab).toBe('record')
    expect(ctx.loadRecords).toHaveBeenCalledTimes(1)
    expect(ctx.loadConfigs).not.toHaveBeenCalled()
  })

  test('creating billing config saves a workspace without submitting approval', async () => {
    billingApi.createBillingConfigDraft.mockResolvedValue({
      data: { id: 72 }
    })
    const ctx = createContext()

    ctx.handleSaveConfig()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(billingApi.createBillingConfigDraft).toHaveBeenCalledWith(
      expect.objectContaining({ billingCode: 'Engine_Count' })
    )
    expect(ctx.$router.push).not.toHaveBeenCalled()
    expect(ctx.$message.success).toHaveBeenCalledWith(expect.stringContaining('草稿已保存'))
  })

  test('status change uses explicit disable lifecycle action', async () => {
    billingApi.changeBillingConfigStatusDraft.mockResolvedValue({
      data: { id: 73 }
    })
    const ctx = createContext({
      configOriginalStatus: 1,
      configForm: {
        ...createContext().configForm,
        id: 21,
        status: 0
      }
    })

    ctx.handleSaveConfig()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(billingApi.changeBillingConfigStatusDraft)
      .toHaveBeenCalledWith(expect.objectContaining({ id: 21 }), 0)
    expect(billingApi.updateBillingConfigDraft).not.toHaveBeenCalled()
    expect(ctx.$router.push).not.toHaveBeenCalled()
    expect(ctx.$message.success).toHaveBeenCalledWith(expect.stringContaining('草稿已保存'))
  })
})
