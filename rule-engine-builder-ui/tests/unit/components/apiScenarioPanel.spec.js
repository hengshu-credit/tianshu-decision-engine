import { shallowMount } from '@test-utils'
import * as definitionApi from '@/api/definition'
import ApiScenarioPanel from '@/components/rule/ApiScenarioPanel.vue'
function mountPanel() {
  definitionApi.listApiScenarios.mockResolvedValue({ code: 200, data: [] })
  definitionApi.getRuleTestSchema.mockResolvedValue({ code: 200, data: {
    inputs: [{ refId: 1, scriptName: 'age', label: '年龄', valueType: 'INTEGER' }],
    sampleParams: { age: 17 }
  } })
  const wrapper = shallowMount(ApiScenarioPanel, {
    props: {
      rule: { id: 7, currentVersion: 4, publishedVersion: 3, outputFieldsJson: [] }
    },
    mocks: {
      $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
      $confirm: vi.fn().mockResolvedValue('confirm'),
      $prompt: vi.fn().mockResolvedValue({ value: '复制场景' })
    },
    stubs: {
      MonacoEditor: { name: 'MonacoEditor', template: '<textarea />' },
      'el-alert': true,
      'el-button': true,
      'el-dialog': true,
      'el-form': true,
      'el-form-item': true,
      'el-input': true,
      'el-input-number': true,
      'el-radio-button': true,
      'el-radio-group': true,
      'el-switch': true,
      'el-table': true,
      'el-table-column': true,
      'el-tag': true
    }
  })
  return wrapper
}

describe('ApiScenarioPanel', () => {
  let wrapper

  beforeEach(() => {
    vi.clearAllMocks()
    wrapper = mountPanel()
  })

  afterEach(() => {
    if (wrapper) wrapper.unmount()
  })

  test('loads scenarios for current rule', async () => {
    await wrapper.vm.$nextTick()
    await Promise.resolve()

    expect(definitionApi.listApiScenarios).toHaveBeenCalledWith(7)
  })

  test('executes schema-generated request and saves selected scenario', async () => {
    const executionResponse = {
      code: 200,
      message: 'success',
      data: { success: true, result: { code: 'REJECT' } }
    }
    definitionApi.executeApiScenario.mockResolvedValue(executionResponse)
    definitionApi.createApiScenario.mockResolvedValue({ code: 200, data: { id: 31 } })

    await wrapper.vm.openCreate()
    wrapper.vm.draft.scenarioName = '风险拒绝'
    wrapper.vm.draft.includeInDoc = 1
    expect(JSON.parse(wrapper.vm.draft.requestJson)).toEqual({
      clientAppName: 'api-doc-example',
      params: { age: 17 }
    })

    await wrapper.vm.executeDraft()
    await wrapper.vm.saveDraft()

    expect(definitionApi.executeApiScenario).toHaveBeenCalledWith(7, {
      clientAppName: 'api-doc-example',
      params: { age: 17 }
    }, 180000)
    expect(definitionApi.createApiScenario).toHaveBeenCalledWith(7, expect.objectContaining({
      scenarioName: '风险拒绝',
      requestJson: expect.any(String),
      responseJson: expect.any(String),
      responseSource: 'EXECUTED',
      includeInDoc: 1
    }))
  })

  test('manual response editing marks the source as manual', () => {
    wrapper.vm.draft.responseSource = 'EXECUTED'

    wrapper.vm.onResponseEdited('{"code":200}')

    expect(wrapper.vm.draft.responseJson).toBe('{"code":200}')
    expect(wrapper.vm.draft.responseSource).toBe('MANUAL')
  })

  test('editor echo of an executed response keeps the executed source', () => {
    wrapper.vm.draft.responseJson = '{"code":200}'
    wrapper.vm.draft.responseSource = 'EXECUTED'

    wrapper.vm.onResponseEdited('{"code":200}')

    expect(wrapper.vm.draft.responseSource).toBe('EXECUTED')
  })

  test('invalid JSON blocks save', async () => {
    wrapper.vm.draftVisible = true
    wrapper.vm.draft.scenarioName = '非法报文'
    wrapper.vm.draft.requestJson = '{'

    await wrapper.vm.saveDraft()

    expect(definitionApi.createApiScenario).not.toHaveBeenCalled()
    expect(wrapper.vm.$message.error).toHaveBeenCalledWith('请求报文不是合法 JSON 对象')
  })

  test('failed execution preserves the previous response', async () => {
    await wrapper.vm.openCreate()
    const previous = '{"code":200,"message":"已保存内容"}'
    wrapper.vm.draft.responseJson = previous
    wrapper.vm.draft.responseSource = 'MANUAL'
    definitionApi.executeApiScenario.mockRejectedValue(new Error('网络错误'))

    await expect(wrapper.vm.executeDraft()).rejects.toThrow('网络错误')

    expect(wrapper.vm.draft.responseJson).toBe(previous)
    expect(wrapper.vm.draft.responseSource).toBe('MANUAL')
  })

  test('business code path is opt-in for a new scenario', async () => {
    await wrapper.vm.openCreate()

    expect(wrapper.vm.draft.businessCodePath).toBe('')
  })
})
