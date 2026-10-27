import { flushPromises, mount } from '@test-utils'
import * as runtimeLogApi from '@/api/runtimeLog'
import ExecutionMetricsPanel from '@/components/dashboard/ExecutionMetricsPanel.vue'
import { ElAlert } from 'element-plus/es/components/alert/index.mjs'

const snapshot = {
  instanceId: 'node-a',
  persistence: { queueDepth: 3, queueCapacity: 50, fallback: 2, failed: 1, avgWriteMs: 12.5 },
  sourceResolution: { queueDepth: 4, active: 2, parallelism: 8, failed: 2 },
  openExecution: { queueDepth: 7, queueCapacity: 80, rejected: 1, timedOut: 3 },
  externalCircuitBreakers: { registeredApis: 9, open: 1, halfOpen: 2, closed: 6 },
  publishOutbox: { pending: 2, delivering: 1, retrying: 1, deadLetter: 0 },
  ruleWarmup: { state: 'FAILED', targetCount: 10, preparedCount: 8, failureCount: 2 }
}

describe('ExecutionMetricsPanel', () => {
  test('模型预热重试更新就绪状态，迟到的旧指标不能回写失败状态', async () => {
    let finishMetrics
    runtimeLogApi.getExecutionMetrics.mockImplementationOnce(() => new Promise(resolve => { finishMetrics = resolve }))
    runtimeLogApi.retryModelWarmup.mockResolvedValueOnce({ data: { state: 'READY', targetCount: 1, successCount: 1, failureCount: 0, cpuFallbackCount: 0, failures: [] } })
    const wrapper = mount(ExecutionMetricsPanel)
    await wrapper.vm.retryModels()
    finishMetrics({ data: { ...snapshot, onnxWarmup: { state: 'FAILED', failureCount: 1 } } })
    await flushPromises()
    expect(wrapper.vm.modelWarmup.state).toBe('READY')
    expect(wrapper.vm.modelWarmupRetrying).toBe(false)
    expect(wrapper.vm.loading).toBe(false)
    wrapper.unmount()
  })
  beforeEach(() => {
    runtimeLogApi.getExecutionMetrics.mockResolvedValue({ data: snapshot })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  test('加载并展示容量、熔断和预热快照', async () => {
    const wrapper = mount(ExecutionMetricsPanel, {
      global: { stubs: { 'el-button': true, 'el-alert': true, 'el-tag': true } }
    })
    await flushPromises()

    expect(runtimeLogApi.getExecutionMetrics).toHaveBeenCalledOnce()
    expect(wrapper.vm.persistence.queueDepth).toBe(3)
    expect(wrapper.vm.queueValue(wrapper.vm.persistence)).toBe('3 / 50')
    expect(wrapper.vm.externalCircuitBreakers.open).toBe(1)
    expect(wrapper.vm.instanceLabel).toBe('node-a')
    expect(wrapper.vm.outboxStatusLabel).toBe('正常')
    expect(wrapper.find('[data-testid="execution-metrics-outbox"]').exists()).toBe(true)
    expect(wrapper.vm.warmupLabel('FAILED')).toBe('预热失败')
    expect(wrapper.vm.warmupProgress).toBe('80.0%')
    expect(wrapper.find('[data-testid="execution-metrics-persistence"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="execution-metrics-circuit-breakers"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="execution-metrics-warmup"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="execution-metrics-retention"]').exists()).toBe(false)
    wrapper.unmount()
  })

  test('回退、失败和预热失败都使用 warning 状态，且支持刷新', async () => {
    const wrapper = mount(ExecutionMetricsPanel, {
      global: { stubs: { 'el-button': true, 'el-alert': true, 'el-tag': true } }
    })
    await flushPromises()

    expect(wrapper.vm.warningType(1)).toBe('warning')
    expect(wrapper.vm.warningTone(2)).toBe('warning')
    expect(wrapper.vm.warmupType('FAILED')).toBe('warning')
    runtimeLogApi.getExecutionMetrics.mockResolvedValueOnce({ data: {
      ...snapshot,
      persistence: { ...snapshot.persistence, queueDepth: 0, fallback: 0, failed: 0 },
      ruleWarmup: { ...snapshot.ruleWarmup, state: 'READY', failureCount: 0, preparedCount: 10 }
    } })
    await wrapper.vm.loadMetrics()
    expect(runtimeLogApi.getExecutionMetrics).toHaveBeenCalledTimes(2)
    expect(wrapper.vm.warmupProgress).toBe('100.0%')
    expect(wrapper.vm.warmupType('READY')).toBe('success')
    wrapper.unmount()
  })

  test('接口失败时保留已有快照并给出可重试错误', async () => {
    const wrapper = mount(ExecutionMetricsPanel, {
      global: { stubs: { 'el-button': true, 'el-alert': true, 'el-tag': true } }
    })
    await flushPromises()
    runtimeLogApi.getExecutionMetrics.mockRejectedValueOnce(new Error('指标服务不可用'))
    await wrapper.vm.loadMetrics()

    expect(wrapper.vm.error).toBe('指标服务不可用')
    expect(wrapper.vm.persistence.queueDepth).toBe(3)
    expect(wrapper.vm.loaded).toBe(true)
    expect(wrapper.vm.snapshotStatus).toBe('STALE')
    expect(wrapper.find('[data-testid="execution-metrics-snapshot-status"]').text()).toContain('最近快照')
    wrapper.unmount()
  })

  test('首次加载失败时不展示零值或正常状态', async () => {
    runtimeLogApi.getExecutionMetrics.mockRejectedValueOnce(new Error('指标服务不可用'))
    const wrapper = mount(ExecutionMetricsPanel, {
      global: { stubs: { 'el-button': true, 'el-alert': true, 'el-tag': true } }
    })
    await flushPromises()

    expect(wrapper.vm.snapshotStatus).toBe('UNKNOWN')
    expect(wrapper.vm.formatNumber(undefined)).toBe('未知')
    expect(wrapper.find('[data-testid="execution-metrics-snapshot-status"]').text()).toContain('暂无快照')
    expect(wrapper.find('[data-testid="execution-metrics-persistence"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('暂无可用指标快照')
    wrapper.unmount()
  })

  test('缺少指标分区时保持未知状态并要求重试', async () => {
    runtimeLogApi.getExecutionMetrics.mockResolvedValueOnce({ data: { persistence: {} } })
    const wrapper = mount(ExecutionMetricsPanel, {
      global: { stubs: { 'el-button': true, 'el-alert': true, 'el-tag': true } }
    })
    await flushPromises()

    expect(wrapper.vm.snapshotStatus).toBe('UNKNOWN')
    expect(wrapper.vm.error).toBe('执行指标快照不完整，请重试')
    expect(wrapper.find('[data-testid="execution-metrics-persistence"]').exists()).toBe(false)
    wrapper.unmount()
  })

  test('缺少字段时单项和状态标签显示未知，溢出策略使用中文文案', async () => {
    const incomplete = {
      ...snapshot,
      persistence: { queueDepth: 0, queueCapacity: 50, fallback: 0, failed: undefined, avgWriteMs: null },
      externalCircuitBreakers: { registeredApis: 1, open: undefined, halfOpen: 0, closed: 1 }
    }
    runtimeLogApi.getExecutionMetrics.mockResolvedValueOnce({ data: incomplete })
    const wrapper = mount(ExecutionMetricsPanel, {
      global: { stubs: { 'el-button': true, 'el-alert': true, 'el-tag': true } }
    })
    await flushPromises()

    expect(wrapper.vm.sectionStatusLabel(wrapper.vm.persistence, ['failed', 'fallback'])).toBe('未知')
    expect(wrapper.vm.warningTone(undefined)).toBe('info')
    expect(wrapper.vm.formatMs(null)).toBe('未知')
    expect(wrapper.vm.overflowStrategyLabel('SYNC_FALLBACK')).toBe('同步回退')
    expect(wrapper.vm.overflowStrategyLabel('DURABLE_OUTBOX_REPLAY')).toBe('持久化队列重放')
    expect(wrapper.vm.overflowStrategyLabel('JOURNAL_THEN_SYNC_FALLBACK')).toBe('本地写前日志后同步回退')
    expect(wrapper.vm.persistenceDetail).toContain('溢出策略 未知')
    wrapper.unmount()
  })

  test('预热失败展示可操作的修复建议', async () => {
    runtimeLogApi.getExecutionMetrics.mockResolvedValueOnce({ data: {
      ...snapshot,
      ruleWarmup: {
        ...snapshot.ruleWarmup,
        failures: [{ definitionId: 28, version: 5, code: 'FUNCTION_NOT_BOUND', message: '函数未绑定', nextAction: '在规则设计器中重新选择对应函数，保存并重新编译发布' }]
      }
    } })
    const wrapper = mount(ExecutionMetricsPanel)
    await flushPromises()

    expect(wrapper.vm.warmupFailures).toHaveLength(1)
    expect(wrapper.vm.failureTitle(wrapper.vm.warmupFailures[0])).toBe('脚本函数未绑定')
    expect(wrapper.find('[data-testid="execution-metrics-warmup-failures"]').text()).toContain('重新选择对应函数')
    wrapper.unmount()
  })

  test('可以在指标面板直接重新验证预热并更新状态', async () => {
    runtimeLogApi.retryRuleWarmup.mockResolvedValueOnce({ data: {
      state: 'READY', targetCount: 10, preparedCount: 10, failureCount: 0, failures: []
    } })
    const wrapper = mount(ExecutionMetricsPanel)
    await flushPromises()

    await wrapper.vm.retryWarmup()
    expect(runtimeLogApi.retryRuleWarmup).toHaveBeenCalledOnce()
    expect(wrapper.vm.ruleWarmup.state).toBe('READY')
    expect(wrapper.vm.warmupRetrying).toBe(false)
    wrapper.unmount()
  })

  test.each([
    ['WRITE_FAILED', '写入失败', '已有文件已保留'],
    ['RECOVERY_REQUIRED', '需要恢复', '不要删除'],
    ['OWNERSHIP_CONFLICT', '目录被占用', '独立的本地目录'],
  ])('本地恢复日志%s不会因待恢复数为0误报正常', async (status, label, advice) => {
    runtimeLogApi.getExecutionMetrics.mockResolvedValueOnce({ data: {
      ...snapshot,
      persistence: { ...snapshot.persistence, journal: { enabled: true, status, available: false, pending: 0, bytes: 2048 } },
    } })
    const wrapper = mount(ExecutionMetricsPanel, { stubs: { 'el-alert': ElAlert } })
    await flushPromises()
    const journal = wrapper.find('[data-testid="execution-metrics-journal"]')
    expect(journal.exists()).toBe(true)
    expect(journal.find('.el-alert--warning').text()).toContain(label)
    expect(journal.find('.el-alert--warning').text()).toContain(advice)
    expect(journal.find('[data-testid="journal-pending"]').text()).toContain('未知')
    wrapper.unmount()
  })

  test('本地恢复日志区分就绪下界、停用与缺失快照', async () => {
    runtimeLogApi.getExecutionMetrics.mockResolvedValueOnce({ data: {
      ...snapshot,
      persistence: { ...snapshot.persistence, journal: { status: 'READY', available: true, pending: 10000, pendingTruncated: true, bytes: 2048 } },
    } })
    const wrapper = mount(ExecutionMetricsPanel)
    await flushPromises()
    expect(wrapper.find('[data-testid="journal-pending"]').text()).toContain('至少 10,000')
    expect(wrapper.vm.journalStatusLabel).toBe('可写入')
    expect(wrapper.vm.journalStatusType).toBe('success')
    runtimeLogApi.getExecutionMetrics.mockResolvedValueOnce({ data: {
      ...snapshot, persistence: { journal: { status: 'READY', available: true, pending: 0, pendingTruncated: true } },
    } })
    await wrapper.vm.loadMetrics()
    expect(wrapper.find('[data-testid="journal-pending"]').text()).toContain('未知')
    runtimeLogApi.getExecutionMetrics.mockResolvedValueOnce({ data: { ...snapshot, persistence: { journal: { enabled: false, status: 'DISABLED' } } } })
    await wrapper.vm.loadMetrics()
    expect(wrapper.vm.journalStatusLabel).toBe('未启用')
    expect(wrapper.vm.journalStatusType).toBe('info')
    expect(wrapper.find('[data-testid="journal-pending"]').text()).toContain('未知')
    runtimeLogApi.getExecutionMetrics.mockResolvedValueOnce({ data: snapshot })
    await wrapper.vm.loadMetrics()
    expect(wrapper.vm.journalStatusLabel).toBe('未知')
    expect(wrapper.vm.journalStatusType).toBe('info')
    wrapper.unmount()
  })
})
