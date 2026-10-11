import { shallowMount } from '@test-utils'
import * as definitionApi from '@/api/definition'
import ApiScenarioPanel from '@/components/rule/ApiScenarioPanel.vue'
import { ElAlert } from 'element-plus/es/components/alert/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
function validatedResponse(response) {
  return { code: 200, data: { execution: response.data, responseJson: JSON.stringify(response), validation: { status: 'NOT_CONFIGURED' } } }
}
function mountPanel(stubs = {}) {
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
      'el-tag': true,
      ...stubs,
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

  test('当前修订来源同时用于场景表单和实际调用，不能退回管理投影', async () => {
    const source = { sourceType: 'REVISION', sourceId: '12', lockVersion: 3 }
    const executionIdentity = { ...source, definitionId: '7', revisionId: '12' }
    await wrapper.setProps({ executionSource: source })
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: { inputs: [], sampleParams: {}, executionIdentity } })
    await wrapper.vm.openEdit({ id: 71, scenarioName: '修订用例', requestJson: '{"params":{"age":25}}', responseJson: '{}', responseSource: 'MANUAL' })
    expect(definitionApi.getRuleTestSchema).toHaveBeenLastCalledWith(expect.objectContaining({ executionSource: source }))
    definitionApi.executeApiScenarioValidated.mockResolvedValueOnce(validatedResponse({ data: { success: true, result: 5, executionIdentity } }))
    await wrapper.vm.executeDraft()
    expect(definitionApi.executeApiScenarioValidated.mock.lastCall[1].executionSource).toEqual(source)
    expect(JSON.parse(wrapper.vm.draft.responseJson).data.result).toBe(5)
  })

  test('同规则切换修订后旧场景调用响应不能写入当前响应编辑器', async () => {
    const source = { sourceType: 'VERSION', sourceId: '81' }
    await wrapper.setProps({ executionSource: source })
    definitionApi.getRuleTestSchema.mockResolvedValue({ data: { inputs: [], sampleParams: {}, executionIdentity: { ...source, definitionId: '7' } } })
    await wrapper.vm.openEdit({ id: 71, scenarioName: '切来源', requestJson: '{"params":{"age":25}}', responseJson: '{"manual":true}', responseSource: 'MANUAL' })
    let finish
    definitionApi.executeApiScenarioValidated.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    const executing = wrapper.vm.executeDraft()
    await wrapper.setProps({ executionSource: { sourceType: 'VERSION', sourceId: '82' } })
    finish(validatedResponse({ data: { success: true, result: 5, executionIdentity: { ...source, definitionId: '7' } } }))
    await executing
    expect(wrapper.vm.draft.responseJson).toBe('{"manual":true}')
  })

  test('旧场景深追踪快照修改说明后可原文保存，不自动迁移', async () => {
    let debug = { leaf: true }
    for (let i = 0; i < 160; i++) debug = { next: debug }
    const raw = JSON.stringify({ code: 200, data: { success: true, result: { value: 88, traces: ['business'] }, traces: [debug] } })
    definitionApi.updateApiScenario.mockResolvedValueOnce({ data: { id: 71 } })
    await wrapper.vm.openEdit({ id: 71, scenarioName: '历史深追踪', requestJson: '{"params":{"age":18}}', responseJson: raw, responseSource: 'MANUAL', expectationJson: null })
    await wrapper.vm.$nextTick(); await Promise.resolve()
    wrapper.vm.draft.description = '只修改说明'
    await wrapper.vm.saveDraft()
    expect(definitionApi.updateApiScenario).toHaveBeenCalledWith(7, 71, expect.objectContaining({ responseJson: raw, description: '只修改说明' }))
    expect(definitionApi.updateApiScenario.mock.lastCall[2]).not.toHaveProperty('expectationJson')
  })

  test('普通手工JSON及result内部同名traces仍执行完整深度校验', async () => {
    let deep = { leaf: true }
    for (let i = 0; i < 160; i++) deep = { next: deep }
    for (const value of [{ traces: deep }, { data: { success: true, result: { traces: deep } } }]) {
      await wrapper.vm.openEdit({ id: 72, scenarioName: '超深业务数据', requestJson: '{"params":{}}', responseJson: JSON.stringify(value), responseSource: 'MANUAL' })
      await wrapper.vm.$nextTick(); await Promise.resolve()
      await wrapper.vm.saveDraft()
    }
    expect(definitionApi.updateApiScenario).not.toHaveBeenCalled()
    expect(wrapper.vm.$message.error).toHaveBeenCalledWith('响应报文不是合法 JSON 对象')
  })

  test('loads scenarios for current rule', async () => {
    await wrapper.vm.$nextTick()
    await Promise.resolve()

    expect(definitionApi.listApiScenarios).toHaveBeenCalledWith(7)
  })

  test('规则切换时只采用最后一次场景列表响应', async () => {
    let resolveFirst
    let resolveSecond
    definitionApi.listApiScenarios
      .mockReset()
      .mockReturnValueOnce(new Promise(resolve => { resolveFirst = resolve }))
      .mockReturnValueOnce(new Promise(resolve => { resolveSecond = resolve }))

    const first = wrapper.vm.loadScenarios()
    await wrapper.setProps({ rule: { id: 8, currentVersion: 1, publishedVersion: 1 } })
    resolveSecond({ code: 200, data: [{ id: 82, scenarioName: '新规则场景' }] })
    await Promise.resolve()
    await Promise.resolve()
    resolveFirst({ code: 200, data: [{ id: 71, scenarioName: '旧规则场景' }] })
    await first

    expect(wrapper.vm.scenarios).toEqual([{ id: 82, scenarioName: '新规则场景' }])
    expect(wrapper.vm.loading).toBe(false)
  })

  test('executes schema-generated request and saves selected scenario', async () => {
    const executionResponse = {
      code: 200,
      message: 'success',
      data: { success: true, result: { code: 'REJECT' } }
    }
    definitionApi.executeApiScenarioValidated.mockResolvedValue(validatedResponse(executionResponse))
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

    expect(definitionApi.executeApiScenarioValidated).toHaveBeenCalledWith(7, {
      requestJson: wrapper.vm.draft.requestJson,
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
    definitionApi.executeApiScenarioValidated.mockRejectedValue(new Error('网络错误'))

    await expect(wrapper.vm.executeDraft()).resolves.toBe(false)

    expect(wrapper.vm.draft.responseJson).toBe(previous)
    expect(wrapper.vm.draft.responseSource).toBe('MANUAL')
    expect(wrapper.vm.$message.error).toHaveBeenCalledWith('网络错误')
  })

  test('business code path is opt-in for a new scenario', async () => {
    await wrapper.vm.openCreate()

    expect(wrapper.vm.draft.businessCodePath).toBe('')
  })

  test('加入文档前同时校验业务版本、修订和制品摘要', () => {
    const currentRule = {
      id: 7,
      publishedVersion: 4,
      publishedRevisionId: 44,
      publishedArtifactDigest: 'digest-current',
    }
    wrapper.unmount()
    wrapper = shallowMount(ApiScenarioPanel, {
      props: { rule: currentRule },
      mocks: {
        $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
      },
      stubs: {
        MonacoEditor: { name: 'MonacoEditor', template: '<textarea />' },
      },
    })

    expect(wrapper.vm.scenarioDocState({
      status: 1,
      includeInDoc: 1,
      ruleVersion: 4,
      revisionId: 43,
      artifactDigest: 'digest-current',
    })).toEqual({ label: '修订已过期', type: 'warning' })
    expect(wrapper.vm.scenarioDocState({
      status: 1,
      includeInDoc: 1,
      ruleVersion: 4,
      revisionId: 44,
      artifactDigest: 'digest-old',
    })).toEqual({ label: '制品已变化', type: 'warning' })
    expect(wrapper.vm.scenarioDocState({
      status: 1,
      includeInDoc: 1,
      ruleVersion: 4,
      revisionId: 44,
      artifactDigest: 'digest-current',
    })).toEqual({ label: '已加入', type: 'success' })
  })

  test('调用期间修改请求表单，旧响应不得标记为新输入的调用结果', async () => {
    await wrapper.vm.openCreate()
    let finish
    definitionApi.executeApiScenarioValidated.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const running = wrapper.vm.executeDraft()
    wrapper.vm.formParams.age = 25
    wrapper.vm.syncFormRequest()
    finish(validatedResponse({ code: 200, data: { success: true, result: 'REJECT_FOR_17' } }))
    await running
    expect(JSON.parse(wrapper.vm.draft.requestJson).params.age).toBe(25)
    expect(wrapper.vm.draft.responseJson).not.toContain('REJECT_FOR_17')
    expect(wrapper.vm.draft.responseSource).not.toBe('EXECUTED')
    expect(wrapper.text()).toContain('请求已变化')
  })

  test('新建Schema迟到时不能覆盖随后打开的已有场景', async () => {
    let finish
    definitionApi.getRuleTestSchema.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const opening = wrapper.vm.openCreate()
    const existing = { id: 99, scenarioName: '已有场景', requestJson: '{"params":{"age":99}}', responseJson: '{"code":200}', responseSource: 'MANUAL' }
    await wrapper.vm.openEdit(existing)
    finish({ data: { inputs: [{ refId: 1, scriptName: 'age', valueType: 'INTEGER' }], sampleParams: { age: 17 } } })
    await opening
    expect(wrapper.vm.draft.id).toBe(99)
    expect(wrapper.vm.draft.requestJson).toBe(existing.requestJson)
  })

  test('调用途中手工修改响应，迟到的成功结果不能覆盖手工预期', async () => {
    await wrapper.vm.openCreate()
    let finish
    definitionApi.executeApiScenarioValidated.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const running = wrapper.vm.executeDraft()
    wrapper.vm.onResponseEdited('{"code":200,"data":{"expected":"manual"}}')
    finish(validatedResponse({ code: 200, data: { success: true, result: 'late' } }))
    await running
    expect(wrapper.vm.draft.responseJson).toBe('{"code":200,"data":{"expected":"manual"}}')
    expect(wrapper.vm.draft.responseSource).toBe('MANUAL')
  })

  test('关弹窗再开另一场景时旧执行完成不污染新场景或结束新调用状态', async () => {
    await wrapper.vm.openCreate()
    let finishOld, finishNew
    definitionApi.executeApiScenarioValidated
      .mockReturnValueOnce(new Promise(resolve => { finishOld = resolve }))
      .mockReturnValueOnce(new Promise(resolve => { finishNew = resolve }))
    const old = wrapper.vm.executeDraft()
    wrapper.vm.draftVisible = false
    await wrapper.vm.$nextTick()
    await wrapper.vm.openEdit({ id: 22, scenarioName: '新场景', requestJson: '{"params":{"age":22}}', responseJson: '{"code":200,"data":null}' })
    const current = wrapper.vm.executeDraft()
    finishOld(validatedResponse({ code: 200, data: { success: true, result: 'old' } }))
    await old
    expect(wrapper.vm.executing).toBe(true)
    expect(wrapper.vm.draft.id).toBe(22)
    expect(wrapper.vm.draft.responseJson).not.toContain('old')
    finishNew(validatedResponse({ code: 200, data: { success: true, result: 'current' } }))
    await current
    expect(wrapper.vm.draft.responseJson).toContain('current')
  })

  test('切换规则使旧编辑会话失效，旧Schema返回不再打开或覆盖草稿', async () => {
    let finish
    definitionApi.getRuleTestSchema.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const opening = wrapper.vm.openCreate()
    await wrapper.setProps({ rule: { id: 8, currentVersion: 1, publishedVersion: 1 } })
    finish({ data: { inputs: [{ refId: 1, scriptName: 'oldField', valueType: 'STRING' }], sampleParams: { oldField: 'old' } } })
    await opening
    expect(wrapper.vm.draftVisible).toBe(false)
    expect(wrapper.vm.draft.requestJson).not.toContain('oldField')
  })

  test('保存期间继续编辑时保留修改和窗口，新建成功接回id避免重复创建', async () => {
    await wrapper.vm.openCreate()
    wrapper.vm.draft.scenarioName = '保存时的名称'
    let finish
    definitionApi.createApiScenario.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const saving = wrapper.vm.saveDraft()
    wrapper.vm.draft.scenarioName = '后来修改的名称'
    finish({ code: 200, data: { id: 31 } })
    await saving
    expect(definitionApi.createApiScenario.mock.calls[0][1].scenarioName).toBe('保存时的名称')
    expect(wrapper.vm.draftVisible).toBe(true)
    expect(wrapper.vm.draft.id).toBe(31)
    expect(wrapper.vm.draft.scenarioName).toBe('后来修改的名称')
    expect(wrapper.text()).toContain('尚未保存')
    definitionApi.updateApiScenario.mockResolvedValueOnce({ data: { id: 31 } })
    await wrapper.vm.saveDraft()
    expect(definitionApi.createApiScenario).toHaveBeenCalledTimes(1)
    expect(definitionApi.updateApiScenario).toHaveBeenCalledWith(7, 31, expect.objectContaining({ scenarioName: '后来修改的名称' }))
  })

  test('保存旧场景期间打开新场景，迟到完成不能关闭新窗口', async () => {
    await wrapper.vm.openCreate()
    wrapper.vm.draft.scenarioName = '原场景'
    let finish
    definitionApi.createApiScenario.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const saving = wrapper.vm.saveDraft()
    await wrapper.vm.openEdit({ id: 22, scenarioName: '另一个场景', requestJson: '{"params":{"age":22}}', responseJson: '{"code":200}' })
    finish({ code: 200, data: { id: 31 } })
    await saving
    expect(wrapper.vm.draftVisible).toBe(true)
    expect(wrapper.vm.draft.id).toBe(22)
    expect(wrapper.vm.draft.scenarioName).toBe('另一个场景')
  })

  test('编辑已有场景默认表单且仅修改目标字段，保留未知字段、缺省和null', async () => {
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: { inputs: [
      { refId: 1, scriptName: 'age', valueType: 'INTEGER' },
      { refId: 2, scriptName: 'profile.enabled', valueType: 'BOOLEAN' },
      { refId: 3, scriptName: 'profile.name', valueType: 'STRING', defaultValue: '不应填入' },
    ], sampleParams: { age: 17, profile: { enabled: true, name: '样例' } } } })
    const original = '{"clientAppName":"custom","traceHint":"keep","params":{"age":21,"profile":{"enabled":null,"extra":"keep"},"unknown":[1,2]}}'
    await wrapper.vm.openEdit({ id: 9, scenarioName: '原参数', requestJson: original, responseJson: '{"code":200}' })
    expect(wrapper.vm.requestMode).toBe('FORM')
    expect(wrapper.vm.draft.requestJson).toBe(original)
    wrapper.vm.formParams.age = 25
    wrapper.vm.syncFormRequest()
    expect(JSON.parse(wrapper.vm.draft.requestJson)).toEqual({ clientAppName: 'custom', traceHint: 'keep', params: {
      age: 25, profile: { enabled: null, extra: 'keep' }, unknown: [1, 2],
    } })
  })

  test('已经生成的响应在请求修改后须重新调用或明确转为手工响应再保存', async () => {
    definitionApi.updateApiScenario.mockResolvedValue({ data: { id: 9 } })
    await wrapper.vm.openEdit({ id: 9, scenarioName: '已执行', requestJson: '{"params":{"age":17}}', responseJson: '{"code":200,"data":{"success":true}}', responseSource: 'EXECUTED' })
    wrapper.vm.onRequestEdited('{"params":{"age":25}}')
    await wrapper.vm.saveDraft()
    expect(definitionApi.updateApiScenario).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请求或测试版本已变化')
    await wrapper.find('[data-testid="scenario-keep-manual-response"]').trigger('click')
    await wrapper.vm.saveDraft()
    expect(definitionApi.updateApiScenario).toHaveBeenCalledWith(7, 9, expect.objectContaining({ responseSource: 'MANUAL' }))
  })

  test('真实数字输入保持大整数与长小数，未知数字及已有预期原样保留', async () => {
    wrapper.unmount()
    wrapper = mountPanel({ 'el-input': ElInput, 'el-alert': ElAlert })
    const expectationJson = '{"mode":"FIELDS","checks":[{"value":"9007199254740993"}]}'
    await wrapper.vm.openEdit({ id: 9, scenarioName: '精确数字', requestJson: '{"traceHint":1e+40,"params":{"age":9007199254740993,"unknown":0.123456789012345678901}}', responseJson: '{"code":200}', expectationJson })
    const input = wrapper.find('[data-field="age"] input')
    expect(input.element.value).toBe('9007199254740993')
    await input.setValue('9007199254740995.123456789012345678901')
    await wrapper.vm.saveDraft()
    const saved = definitionApi.updateApiScenario.mock.calls[0][2]
    expect(saved.requestJson).toContain('9007199254740995.123456789012345678901')
    expect(saved.requestJson).toContain('0.123456789012345678901')
    expect(saved.requestJson).toContain('1e+40')
    expect(saved.expectationJson).toBe(expectationJson)
  })

  test('执行发送原始请求且直接使用服务器原始响应，不重新序列化普通JS结果', async () => {
    const requestJson = '{"params":{"age":9007199254740993}}'
    const responseJson = '{"code":200,"data":{"success":true,"result":{"value":9007199254740993}}}'
    await wrapper.vm.openEdit({ id: 9, scenarioName: '原样执行', requestJson, responseJson: '{"code":200}' })
    definitionApi.executeApiScenarioValidated.mockResolvedValueOnce({ data: { execution: { success: true, result: { value: 9007199254740992 } }, responseJson } })
    await wrapper.vm.executeDraft()
    expect(definitionApi.executeApiScenarioValidated).toHaveBeenCalledWith(7, { scenarioId: 9, requestJson }, 180000)
    expect(wrapper.vm.draft.responseJson).toBe(responseJson)
  })

  test('未提供和传入null是不同操作，切回未提供不新增原先缺失的params', async () => {
    await wrapper.vm.openEdit({ id: 9, scenarioName: '缺省', requestJson: '{"traceHint":"keep"}', responseJson: '{"code":200}' })
    expect(wrapper.vm.formStates.age).toBe('MISSING')
    wrapper.vm.formStates.age = 'VALUE'
    wrapper.vm.formParams.age = 'invalid'
    wrapper.vm.syncFormRequest()
    expect(wrapper.vm.hasFormErrors).toBe(true)
    wrapper.vm.formStates.age = 'MISSING'
    wrapper.vm.syncFormRequest()
    expect(wrapper.vm.draft.requestJson).toBe('{"traceHint":"keep"}')
    expect(wrapper.vm.hasFormErrors).toBe(false)
    wrapper.vm.formStates.age = 'NULL'
    wrapper.vm.syncFormRequest()
    expect(JSON.parse(wrapper.vm.draft.requestJson)).toEqual({ traceHint: 'keep', params: { age: null } })
    wrapper.vm.formStates.age = 'MISSING'
    wrapper.vm.syncFormRequest()
    expect(JSON.parse(wrapper.vm.draft.requestJson)).toEqual({ traceHint: 'keep', params: {} })
  })

  test('原params为null或路径父级为null时明确提示且不覆盖原值', async () => {
    await wrapper.vm.openEdit({ id: 9, scenarioName: 'null参数', requestJson: '{"params":null}', responseJson: '{"code":200}' })
    expect(wrapper.text()).toContain('params 不是对象')
    expect(wrapper.vm.draft.requestJson).toBe('{"params":null}')
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: { inputs: [{ scriptName: 'profile.age', valueType: 'INTEGER' }] } })
    await wrapper.vm.openEdit({ id: 10, scenarioName: 'null父级', requestJson: '{"params":{"profile":null}}', responseJson: '{"code":200}' })
    expect(wrapper.vm.formBlocked['profile.age']).toContain('上级字段不是对象')
    wrapper.vm.formParams['profile.age'] = '25'
    wrapper.vm.formStates['profile.age'] = 'VALUE'
    wrapper.vm.syncFormRequest()
    expect(wrapper.vm.draft.requestJson).toBe('{"params":{"profile":null}}')
  })

  test('JSON切换回表单映射当前请求，非法数字表单不得执行或保存旧值', async () => {
    await wrapper.vm.openCreate()
    wrapper.vm.changeRequestMode('JSON')
    wrapper.vm.onRequestEdited('{"params":{"age":9007199254740993,"extra":null}}')
    wrapper.vm.changeRequestMode('FORM')
    expect(wrapper.vm.formParams.age).toBe('9007199254740993')
    wrapper.vm.formParams.age = ''
    wrapper.vm.syncFormRequest()
    wrapper.vm.draft.scenarioName = '无效输入'
    await wrapper.vm.executeDraft()
    await wrapper.vm.saveDraft()
    expect(definitionApi.executeApiScenarioValidated).not.toHaveBeenCalled()
    expect(definitionApi.createApiScenario).not.toHaveBeenCalled()
    wrapper.vm.changeRequestMode('JSON')
    expect(wrapper.vm.requestMode).toBe('FORM')
    expect(wrapper.vm.$message.warning).toHaveBeenCalledWith(expect.stringContaining('修正表单'))
  })

  test('Schema失败重试只加载字段，不能用样例填入已有缺失字段', async () => {
    definitionApi.getRuleTestSchema.mockRejectedValueOnce(new Error('网络错误'))
    const original = '{"params":{"unknown":"keep"}}'
    await wrapper.vm.openEdit({ id: 9, scenarioName: '重试', requestJson: original, responseJson: '{"code":200}' })
    expect(wrapper.text()).toContain('重新加载')
    await wrapper.vm.loadDraftSchema(false)
    expect(wrapper.vm.formStates.age).toBe('MISSING')
    expect(wrapper.vm.draft.requestJson).toBe(original)
  })

  test('同一新建窗口Schema迟到也不能覆盖用户已经编辑的原始请求', async () => {
    let finish
    definitionApi.getRuleTestSchema.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const opening = wrapper.vm.openCreate()
    wrapper.vm.onRequestEdited('{"params":{"age":33,"custom":null}}')
    finish({ data: { inputs: [{ scriptName: 'age', valueType: 'INTEGER' }], sampleParams: { age: 17 } } })
    await opening
    expect(wrapper.vm.draft.requestJson).toBe('{"params":{"age":33,"custom":null}}')
    expect(wrapper.vm.formParams.age).toBe('33')
  })

  test('旧执行失败不能覆盖修改后的响应，也不能对另一个场景显示错误', async () => {
    let fail
    await wrapper.vm.openCreate()
    definitionApi.executeApiScenarioValidated.mockReturnValueOnce(new Promise((resolve, reject) => { fail = reject }))
    const running = wrapper.vm.executeDraft()
    await wrapper.vm.openEdit({ id: 9, scenarioName: '新场景', requestJson: '{"params":{}}', responseJson: '{"code":200,"data":"keep"}' })
    fail(new Error('旧请求失败'))
    await running
    expect(wrapper.vm.draft.responseJson).toBe('{"code":200,"data":"keep"}')
    expect(wrapper.vm.$message.error).not.toHaveBeenCalled()
  })

  test('重复点击不重复调用，执行中不能保存尚未配对的请求响应', async () => {
    let finish
    await wrapper.vm.openCreate()
    wrapper.vm.draft.scenarioName = '进行中'
    definitionApi.executeApiScenarioValidated.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const running = wrapper.vm.executeDraft()
    await wrapper.vm.executeDraft()
    await wrapper.vm.saveDraft()
    expect(definitionApi.executeApiScenarioValidated).toHaveBeenCalledTimes(1)
    expect(definitionApi.createApiScenario).not.toHaveBeenCalled()
    finish(validatedResponse({ code: 200, data: { success: true, result: 1 } }))
    await running
  })

  test('请求改变后迟到失败也明确属于旧调用，真实Alert不把错误归到新输入', async () => {
    wrapper.unmount()
    wrapper = mountPanel({ 'el-alert': ElAlert })
    await wrapper.vm.openCreate()
    let fail
    definitionApi.executeApiScenarioValidated.mockReturnValueOnce(new Promise((resolve, reject) => { fail = reject }))
    const running = wrapper.vm.executeDraft()
    wrapper.vm.onRequestEdited('{"params":{"age":35}}')
    fail(new Error('旧输入无效'))
    await running
    expect(wrapper.vm.$message.error).not.toHaveBeenCalled()
    expect(wrapper.findAll('.el-alert').some(alert => alert.text().includes('请求已变化'))).toBe(true)
  })

  test('字段名与对象内建属性同名时仍按原字段精确编辑，不能改成别的名字', async () => {
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: { inputs: [{ refId: 1, scriptName: '__proto__', valueType: 'STRING' }] } })
    await wrapper.vm.openEdit({ id: 9, scenarioName: '原字段名', requestJson: '{"params":{"__proto__":"before","unknown":"keep"}}', responseJson: '{"code":200}' })
    expect(wrapper.vm.formStates.__proto__).toBe('VALUE')
    wrapper.vm.formParams.__proto__ = 'after'
    wrapper.vm.syncFormRequest()
    expect(JSON.parse(wrapper.vm.draft.requestJson)).toEqual(JSON.parse('{"params":{"__proto__":"after","unknown":"keep"}}'))
  })

  test('首次新建优先使用Schema样例原文，大整数与长小数不采用已舍入样例', async () => {
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: {
      inputs: [{ refId: '9007199254740993', scriptName: 'age', valueType: 'NUMBER' }, { refId: '2', scriptName: 'ratio', valueType: 'NUMBER' }],
      sampleParams: { age: 9007199254740992, ratio: 0.12345678901234568 },
      sampleParamsJson: '{"age":9007199254740993,"ratio":0.123456789012345678901}',
    } })
    await wrapper.vm.openCreate()
    expect(wrapper.vm.formParams.age).toBe('9007199254740993')
    expect(wrapper.vm.formParams.ratio).toBe('0.123456789012345678901')
    expect(wrapper.vm.draft.requestJson).toContain('9007199254740993')
    expect(wrapper.vm.draft.requestJson).toContain('0.123456789012345678901')
  })

  test('Schema样例原文损坏时保留空稿并提示，不能静默回退已舍入样例', async () => {
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: {
      inputs: [{ scriptName: 'age', valueType: 'NUMBER' }], sampleParams: { age: 123 }, sampleParamsJson: '{',
    } })
    await wrapper.vm.openCreate()
    expect(wrapper.vm.draft.requestJson).not.toContain('123')
    expect(wrapper.vm.schemaError).toContain('原报文已保留')
    expect(wrapper.vm.schemaLoading).toBe(false)
  })

  test('精确样例原文直接保留特殊字段、点号键和额外样例，不由字段映射重建', async () => {
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: {
      inputs: [{ scriptName: '__proto__', valueType: 'STRING' }, { scriptName: 'a.b', valueType: 'NUMBER' }],
      sampleParamsJson: '{"__proto__":"business-data","a.b":9007199254740993,"a":{"b":2},"unknown":null}',
    } })
    await wrapper.vm.openCreate()
    expect(wrapper.vm.formParams.__proto__).toBe('business-data')
    expect(wrapper.vm.formParams['a.b']).toBe('9007199254740993')
    expect(wrapper.vm.draft.requestJson).toContain('"__proto__": "business-data"')
    expect(wrapper.vm.draft.requestJson).toContain('"a.b": 9007199254740993')
    expect(wrapper.vm.draft.requestJson).toContain('"unknown": null')
    expect(JSON.parse(wrapper.vm.draft.requestJson).params.a).toEqual({ b: 2 })
  })
})
