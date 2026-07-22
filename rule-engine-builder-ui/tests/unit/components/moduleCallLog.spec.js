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
    jest.clearAllMocks()
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

  test('外数模块加载 MySQL 统计并格式化供应商质量指标', async () => {
    getExternalApiStats.mockResolvedValue({
      data: {
        overview: { queryCount: 20, cacheHitRate: 0.25, requestSuccessRate: 0.9, foundRate: 0.6 },
        providers: [{ targetCode: 'vendor_a', queryCount: 20, foundRate: 0.6 }]
      }
    })
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

    expect(getExternalApiStats).toHaveBeenCalled()
    expect(wrapper.vm.externalStats.overview.queryCount).toBe(20)
    expect(wrapper.vm.formatRate(0.25)).toBe('25.00%')
    expect(wrapper.vm.externalStats.providers[0].targetCode).toBe('vendor_a')
  })
})
