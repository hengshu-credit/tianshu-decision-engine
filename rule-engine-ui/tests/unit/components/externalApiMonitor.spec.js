import { shallowMount, flushPromises } from '@test-utils'
import { getExternalApiStats, listRuntimeLogs } from '@/api/runtimeLog'
import ExternalApiMonitor from '@/components/common/ExternalApiMonitor.vue'
import AsyncState from '@/components/common/AsyncState.vue'

describe('ExternalApiMonitor', () => {
  let wrapper
  beforeEach(() => {
    vi.clearAllMocks()
    getExternalApiStats.mockResolvedValue({ data: { overview: {}, providers: [] } })
  })
  afterEach(() => wrapper?.unmount())

  test('独立加载八项统计与接口汇总，不请求调用日志', async () => {
    getExternalApiStats.mockResolvedValue({ data: {
      overview: { queryCount: 20, cacheHitRate: 0.25, requestSuccessRate: 0.9, foundRate: 0.6 },
      providers: [{ targetCode: 'vendor_a', queryCount: 20, foundRate: 0.6 }]
    } })
    wrapper = shallowMount(ExternalApiMonitor)
    await flushPromises()
    expect(getExternalApiStats).toHaveBeenCalledTimes(1)
    expect(listRuntimeLogs).not.toHaveBeenCalled()
    expect(wrapper.findAll('.datasource-stat-cell')).toHaveLength(8)
    expect(wrapper.text()).toContain('25.00%')
    expect(wrapper.vm.externalStats.providers[0].targetCode).toBe('vendor_a')
    expect(wrapper.vm.formatMs(12.34)).toBe('12.34 ms')
  })

  test('失败显示错误，重试能够恢复指标并清除错误', async () => {
    getExternalApiStats.mockRejectedValueOnce(new Error('指标读取失败'))
    wrapper = shallowMount(ExternalApiMonitor)
    await flushPromises()
    expect(wrapper.findComponent(AsyncState).props('error')).toBe('指标读取失败')
    expect(wrapper.vm.statsLoading).toBe(false)
    getExternalApiStats.mockResolvedValueOnce({ data: { overview: { queryCount: 3 }, providers: [{ targetCode: 'restored' }] } })
    wrapper.findComponent(AsyncState).vm.$emit('retry')
    await flushPromises()
    expect(wrapper.findComponent(AsyncState).props('error')).toBe('')
    expect(wrapper.vm.externalStats.overview.queryCount).toBe(3)
    expect(getExternalApiStats).toHaveBeenCalledTimes(2)
  })

  test('刷新空结果保留原有空态和数值格式', async () => {
    wrapper = shallowMount(ExternalApiMonitor)
    await flushPromises()
    expect(wrapper.findComponent(AsyncState).props('empty')).toBe(true)
    expect(wrapper.vm.formatRate(undefined)).toBe('0.00%')
    expect(wrapper.vm.formatMs(0)).toBe('0 ms')
    await wrapper.find('el-button-stub').trigger('click')
    await flushPromises()
    expect(getExternalApiStats).toHaveBeenCalledTimes(2)
    expect(listRuntimeLogs).not.toHaveBeenCalled()
  })
})
