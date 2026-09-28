import { flushPromises, mount } from '@test-utils'
import * as runtimeLogApi from '@/api/runtimeLog'
import ExecutionMetricsPanel from '@/components/dashboard/ExecutionMetricsPanel.vue'

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
})
