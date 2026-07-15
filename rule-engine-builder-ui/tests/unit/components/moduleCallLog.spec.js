import { shallowMount } from '@vue/test-utils'
import Vue from 'vue'

import { listRuntimeLogs } from '@/api/runtimeLog'
import ModuleCallLog from '@/components/common/ModuleCallLog.vue'

describe('ModuleCallLog', () => {
  beforeEach(() => {
    listRuntimeLogs.mockResolvedValue({ data: { records: [], total: 0 } })
  })

  afterEach(() => {
    jest.clearAllMocks()
  })

  test('pretty 格式化对象和数组而不是渲染为 object Object', async () => {
    const wrapper = shallowMount(ModuleCallLog, {
      propsData: { moduleType: 'DATABASE' },
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
    await Vue.nextTick()

    const rows = [{ user_count: 1 }]
    expect(wrapper.vm.pretty(rows)).toBe(JSON.stringify(rows, null, 2))
    expect(wrapper.vm.pretty({ resultPath: '', extractedValue: 1 })).toContain('"extractedValue": 1')
    expect(wrapper.vm.pretty(rows)).not.toContain('[object Object]')
    expect(wrapper.vm.actionLabel('MODEL_EXECUTE')).toBe('规则内模型执行')
    expect(wrapper.vm.query.traceId).toBe('')
  })
})
