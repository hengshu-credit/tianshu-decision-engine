import { mount, shallowMount } from '@test-utils'
import { nextTick } from 'vue'
import { flushPromises } from '@vue/test-utils'
import { getLibrary } from '@/api/ruleList'

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
    getLibrary.mockImplementation(async id => ({ data: { id, listName: `名单${id}`, listCode: `list_${id}` } }))
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  test('名单详情以业务语言展示匹配过程，同时完整保留原始报文', async () => {
    const wrapper = mount(ModuleCallLog, { props: { moduleType: 'LIST' } })
    const request = { listIds: [4, 2], queryValues: ['测试手机号', 0, false], combinationMode: 'ALL_FIELDS_ANY_LIST', itemTypes: ['MOBILE', 'ID_CARD'], matchMode: 'IN_LIST' }
    try {
      await wrapper.vm.openDetail({ success: 1, requestBody: JSON.stringify(request), responseBody: '{"hit":false}' })
      await flushPromises()
      const trace = wrapper.find('.list-match-trace')
      expect(trace.text()).toContain('未命中')
      expect(trace.text()).toContain('全部内容各自命中任一名单')
      expect(trace.text()).toContain('在名单内（精确匹配）')
      expect(trace.text()).toContain('手机号、身份证')
      expect(trace.findAll('.list-match-value').map(item => item.text())).toEqual(['测试手机号', '测试手机号', '0', '0', 'false', 'false'])
      expect(trace.findAll('.match-library .content-ref__name').map(item => item.text())).toEqual(['名单4', '名单2', '名单4', '名单2', '名单4', '名单2'])
      expect(trace.findAll('.match-library .content-ref__code').map(item => item.text())).toEqual(['list_4', 'list_2', 'list_4', 'list_2', 'list_4', 'list_2'])
      expect(trace.text()).toContain('未记录逐项匹配明细')
      expect(wrapper.find('.list-raw-payload summary').text()).toContain('原始报文')
      const reports = wrapper.findAll('.list-raw-payload pre')
      expect(JSON.parse(reports[0].text())).toEqual(request)
      expect(JSON.parse(reports[1].text())).toEqual({ hit: false })
    } finally { wrapper.unmount() }
  })

  test('名单执行失败或缺少结果不能显示为未命中，异常报文仍可排查', async () => {
    const wrapper = mount(ModuleCallLog, { props: { moduleType: 'LIST' } })
    try {
      const failed = { success: 0, errorMessage: '名单服务超时', requestBody: '{broken', responseBody: '{"hit":false}' }
      await wrapper.vm.openDetail(failed)
      expect(wrapper.find('.list-match-trace').text()).toContain('匹配失败')
      expect(wrapper.find('.list-match-trace').text()).not.toContain('未命中')
      expect(wrapper.find('.list-raw-payload pre').text()).toBe('{broken')
      expect(wrapper.vm.rowSummary(failed)).toContain('匹配失败')
      await wrapper.vm.openDetail({ success: 1, requestBody: 'null', responseBody: '{}' })
      expect(wrapper.find('.list-match-trace').text()).toContain('未记录匹配结果')
      expect(wrapper.find('.list-match-trace').text()).not.toContain('未命中')
    } finally { wrapper.unmount() }
  })

  test('草稿试运行与单次请求分别以中文展示并保留独立动作筛选值', async () => {
    const wrapper = shallowMount(ModuleCallLog, {
      props: { moduleType: 'DATASOURCE' },
      stubs: ['el-button', 'el-form', 'el-form-item', 'el-select', 'el-option', 'el-input',
        'el-table', 'el-table-column', 'el-tag', 'el-pagination', 'el-drawer',
        'el-descriptions', 'el-descriptions-item', 'el-alert'],
    })
    try {
      await wrapper.vm.openDetail({ id: 201, actionType: 'API_DRAFT_INVOKE', success: 1 })
      expect(wrapper.find('.log-detail').text()).toContain('草稿试运行')
      expect(wrapper.find('.log-detail').text()).not.toContain('API_DRAFT_INVOKE')
      await wrapper.vm.openDetail({ id: 202, actionType: 'API_DRAFT_ATTEMPT', success: 1 })
      expect(wrapper.find('.log-detail').text()).toContain('试运行请求')
      expect(wrapper.vm.actionOptions).toEqual(expect.arrayContaining([
        { label: '草稿试运行', value: 'API_DRAFT_INVOKE' },
        { label: '试运行请求', value: 'API_DRAFT_ATTEMPT' },
      ]))
      wrapper.vm.query.actionType = 'API_DRAFT_ATTEMPT'
      await wrapper.vm.load()
      expect(listRuntimeLogs).toHaveBeenLastCalledWith(expect.objectContaining({ actionType: 'API_DRAFT_ATTEMPT' }))
      expect(wrapper.vm.actionLabel('API_INVOKE')).toBe('API调用')
    } finally { wrapper.unmount() }
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
