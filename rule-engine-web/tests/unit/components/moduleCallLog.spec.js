import { shallowMount } from '@test-utils'
import { nextTick } from 'vue'

import {
  getExternalApiStats,
  getRuntimeCallPayload,
  listRuntimeLogs,
} from '@/api/runtimeLog'
import ModuleCallLog from '@/components/common/ModuleCallLog.vue'

describe('ModuleCallLog', () => {
  beforeEach(() => {
    listRuntimeLogs.mockResolvedValue({ data: { records: [], total: 0 } })
    getExternalApiStats.mockResolvedValue({ data: { overview: {}, providers: [] } })
    getRuntimeCallPayload.mockResolvedValue({ data: {} })
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

  test('按项目编码筛选日志并将项目 ID 传入详情报文查询', async () => {
    const wrapper = shallowMount(ModuleCallLog, {
      props: { moduleType: 'DATASOURCE' },
      stubs: [
        'el-button', 'el-form', 'el-form-item', 'el-select', 'el-option', 'el-input',
        'el-table', 'el-table-column', 'el-tag', 'el-pagination', 'el-drawer',
        'el-descriptions', 'el-descriptions-item', 'el-alert'
      ]
    })
    await wrapper.vm.$nextTick()
    wrapper.vm.query.projectCode = 'PROJECT_A'
    await wrapper.vm.load()

    expect(listRuntimeLogs).toHaveBeenLastCalledWith(expect.objectContaining({
      projectCode: 'PROJECT_A',
      moduleType: 'DATASOURCE'
    }))

    await wrapper.vm.openDetail({ id: 19, projectId: 7, actionType: 'API_INVOKE', success: 1 })
    expect(getRuntimeCallPayload).toHaveBeenLastCalledWith(19, 7)
    wrapper.unmount()
  })

  test('外数日志详情按日志 ID 拉取原始报文并展示稳定关联键', async () => {
    getRuntimeCallPayload.mockResolvedValueOnce({
      data: {
        id: 17,
        callId: 'call-17',
        rootTraceId: 'root-17',
        rawPayloadAvailable: true,
        rawRequestAvailable: true,
        rawResponseAvailable: true,
        requestBody: '{"userId":"u-1"}',
        responseBody: '{"risk":"low"}',
      },
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

    await wrapper.vm.openDetail({ id: 17, actionType: 'API_INVOKE', success: 1 })

    expect(getRuntimeCallPayload).toHaveBeenCalledWith(17)
    expect(wrapper.vm.apiPayload.callId).toBe('call-17')
    expect(wrapper.vm.apiPayload.rootTraceId).toBe('root-17')
    expect(wrapper.vm.apiPayload.rawPayloadAvailable).toBe(true)
    expect(wrapper.vm.pretty(wrapper.vm.apiPayload.requestBody)).toContain('"userId": "u-1"')
    expect(wrapper.vm.pretty(wrapper.vm.apiPayload.responseBody)).toContain('"risk": "low"')
    wrapper.unmount()
  })

  test('外数日志详情展示从规则入参到引擎赋值的阶段链', async () => {
    getRuntimeCallPayload.mockResolvedValueOnce({
      data: {
        callId: 'call-trace',
        traceSteps: [
          { sequence: 1, type: 'REQUEST_INPUT', label: '规则/变量入参', status: 'SUCCESS', input: { uid: 'u-1' } },
          { sequence: 2, type: 'AUTHENTICATION', label: '外数鉴权（已脱敏）', status: 'SUCCESS', output: { headers: { Authorization: 'Bearer ****' } } },
          { sequence: 3, type: 'ENGINE_ASSIGNMENT', label: '引擎变量和对象赋值', status: 'SUCCESS', output: { mappingCount: 1 } }
        ]
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

    await wrapper.vm.openDetail({ id: 18, actionType: 'API_INVOKE', success: 1 })

    expect(wrapper.vm.apiPayload.traceSteps).toHaveLength(3)
    expect(wrapper.vm.traceStepStatusLabel('SUCCESS')).toBe('成功')
    expect(wrapper.vm.traceStepTagType('FAILED')).toBe('danger')
    expect(wrapper.vm.pretty(wrapper.vm.apiPayload.traceSteps[2].output)).toContain('mappingCount')
    wrapper.unmount()
  })
})
