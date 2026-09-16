import { shallowMount } from '@test-utils'
import { nextTick } from 'vue'

import { getExternalApiStats, listRuntimeLogs } from '@/api/runtimeLog'
import ModuleCallLog from '@/components/common/ModuleCallLog.vue'

describe('ModuleCallLog', () => {
  beforeEach(() => {
    listRuntimeLogs.mockResolvedValue({ data: { records: [], total: 0 } })
    getExternalApiStats.mockResolvedValue({ data: { overview: {}, providers: [] } })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  test('pretty 格式化对象和数组而不是渲染为 object Object', async () => {
    const wrapper = shallowMount(ModuleCallLog, {
      props: { moduleType: 'DATABASE' },
      stubs: [
        'el-button',
        'el-form',
        'el-form-item',
        'el-select',
        'el-option',
        'el-input',
        'el-table',
        'el-table-column',
        'el-tag',
        'el-pagination',
        'el-drawer',
        'el-descriptions',
        'el-descriptions-item'
      ]
    })
    await nextTick()

    const rows = [{ user_count: 1 }]
    expect(wrapper.vm.pretty(rows)).toBe(JSON.stringify(rows, null, 2))
    expect(wrapper.vm.pretty({ resultPath: '', extractedValue: 1 })).toContain('"extractedValue": 1')
    expect(wrapper.vm.pretty(rows)).not.toContain('[object Object]')
    expect(wrapper.vm.actionLabel('MODEL_EXECUTE')).toBe('规则内模型执行')
    expect(wrapper.vm.query.traceId).toBe('')
  })

  test('外数调用日志只加载日志，刷新不请求监控指标', async () => {
    const wrapper = shallowMount(ModuleCallLog, {
      props: { moduleType: 'DATASOURCE' },
      stubs: [
        'el-button', 'el-form', 'el-form-item', 'el-select', 'el-option', 'el-input',
        'el-table', 'el-table-column', 'el-tag', 'el-pagination', 'el-drawer',
        'el-descriptions', 'el-descriptions-item', 'el-alert'
      ]
    })
    await wrapper.vm.$nextTick()
    await Promise.resolve()

    expect(wrapper.find('.stats-panel').exists()).toBe(false)
    expect(listRuntimeLogs).toHaveBeenCalledTimes(1)
    await wrapper.vm.load()
    expect(listRuntimeLogs).toHaveBeenCalledTimes(2)
    expect(getExternalApiStats).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
