import { shallowMount, flushPromises } from '@test-utils'
import OperationLogs from '@/views/account/OperationLogs.vue'
import { listConsoleOperationLogs, getConsoleOperationLog } from '@/api/consoleAccount'

vi.mock('@/api/consoleAccount', () => ({
  listConsoleOperationLogs: vi.fn(), getConsoleOperationLog: vi.fn(),
}))

describe('账户操作日志', () => {
  test('列表和详情分别展示进入与离开时间，缺失的历史离开时间显示横线', async() => {
    const record = { id: '15', createTime: '2026-11-04T17:37:16', leaveTime: '2026-11-04T17:38:20' }
    listConsoleOperationLogs.mockResolvedValue({ data: { records: [record], total: 1 } })
    getConsoleOperationLog.mockResolvedValue({ data: record })
    const wrapper = shallowMount(OperationLogs, { global: { stubs: {
      'el-table-column': {
        name: 'ElTableColumn', props: ['label'], data: () => ({ row: record }),
        template: '<div :data-column="label"><slot :row="row" /></div>',
      },
    } } })
    await flushPromises()
    expect(wrapper.get('[data-column="进入时间"]').text()).toBe('2026-11-04 17:37:16')
    expect(wrapper.get('[data-column="离开时间"]').text()).toBe('2026-11-04 17:38:20')
    await wrapper.vm.openDetail(record)
    expect(wrapper.get('.log-detail [label="进入时间"]').text()).toBe('2026-11-04 17:37:16')
    expect(wrapper.get('.log-detail [label="离开时间"]').text()).toBe('2026-11-04 17:38:20')
    getConsoleOperationLog.mockResolvedValue({ data: { id: '16', createTime: record.createTime } })
    await wrapper.vm.openDetail({ id: '16' })
    expect(wrapper.get('.log-detail [label="离开时间"]').text()).toBe('—')
    wrapper.unmount()
  })
  test('详情只展示版本和简短摘要，不展示旧的正文或前后对比', async() => {
    getConsoleOperationLog.mockResolvedValue({ data: {
      id: '13', targetVersion: 'v3', changeSummary: '更新模型输入字段配置',
      requestJson: 'PRIVATE_REQUEST', beforeJson: 'PRIVATE_BEFORE', afterJson: 'PRIVATE_AFTER',
      changesJson: '[{"field":"private","before":"PRIVATE_DIFF","after":"new"}]', resultJson: 'PRIVATE_RESULT',
    } })
    const wrapper = shallowMount(OperationLogs)
    await flushPromises()
    await wrapper.vm.openDetail({ id: '13' })
    expect(wrapper.text()).toContain('v3')
    expect(wrapper.text()).toContain('更新模型输入字段配置')
    expect(wrapper.text()).not.toContain('PRIVATE_')
    expect(wrapper.find('pre').exists()).toBe(false)
    wrapper.unmount()
  })
  beforeEach(() => {
    vi.clearAllMocks()
    listConsoleOperationLogs.mockResolvedValue({ data: { records: [{ id: '9007199254740993', username: 'editor', action: 'UPDATE' }], total: 1 } })
  })

  test('按账号、时间、操作结果查询，并在改筛选时回到第一页', async() => {
    const wrapper = shallowMount(OperationLogs)
    await flushPromises()
    Object.assign(wrapper.vm.query, { username: 'editor', module: 'model', outcome: 'DENIED', pageNum: 5 })
    wrapper.vm.timeRange = ['2026-10-10 08:00:00', '2026-10-10 18:00:00']
    await wrapper.vm.search()
    expect(listConsoleOperationLogs).toHaveBeenLastCalledWith(expect.objectContaining({
      username: 'editor', module: 'model', outcome: 'DENIED', pageNum: 1,
      startTime: '2026-10-10 08:00:00', endTime: '2026-10-10 18:00:00',
    }))
    expect(wrapper.vm.rows[0].id).toBe('9007199254740993')
    wrapper.unmount()
  })

  test('日志详情保留操作结果与精简摘要', async() => {
    getConsoleOperationLog.mockResolvedValue({ data: { id: '12', outcome: 'FAILED', resultCode: 409, changeSummary: '提交项目配置修改', targetVersion: 'v2' } })
    const wrapper = shallowMount(OperationLogs)
    await flushPromises()
    await wrapper.vm.openDetail({ id: '12' })
    expect(getConsoleOperationLog).toHaveBeenCalledWith('12')
    expect(wrapper.text()).toContain('失败（409）')
    expect(wrapper.text()).toContain('提交项目配置修改')
    expect(wrapper.text()).toContain('v2')
    wrapper.unmount()
  })
  test('详情展示精确页面路径，发布与调试可单独筛选', async() => {
    getConsoleOperationLog.mockResolvedValue({ data: { id: '14', action: 'PUBLISH', pagePath: '/designer/table/12?sourceType=REVISION&sourceId=99', targetName: '授信准入', targetId: '12', changeSummary: '提交发布审批：规则「授信准入」 #12 / 决策表设计页' } })
    const wrapper = shallowMount(OperationLogs)
    await flushPromises()
    await wrapper.vm.openDetail({ id: '14' })
    expect(wrapper.get('.location-path').text()).toBe('/designer/table/12?sourceType=REVISION&sourceId=99')
    expect(wrapper.text()).toContain('授信准入')
    expect(wrapper.vm.actions.PUBLISH).toBe('发布')
    expect(wrapper.vm.actions.DEBUG).toBe('调试')
    wrapper.vm.query.action = 'DEBUG'
    await wrapper.vm.search()
    expect(listConsoleOperationLogs).toHaveBeenLastCalledWith(expect.objectContaining({ action: 'DEBUG' }))
    wrapper.unmount()
  })
})
