// tests/unit/views/ruleDetail.spec.js
import { shallowMount } from '@test-utils'
import { h, nextTick } from 'vue'
// 直接 import API 模块（不写 vi.mock，依赖 setup.js 的预置 mock）
import * as definitionApi from '@/api/definition'
import * as variableApi from '@/api/variable'
import RuleDetail from '@/views/rule/RuleDetail.vue'

afterEach(() => { vi.clearAllMocks() })

// ─── Mock 数据 ───────────────────────────────────────────
function mockRuleDetail(id = 1) {
  return {
    id,
    ruleName: '年龄判断规则',
    ruleCode: 'age_rule',
    modelType: 'TABLE',
    scope: 'PROJECT',
    projectId: 1,
    currentVersion: 1,
    publishedVersion: null,
    status: 0,
    description: '根据年龄判断是否通过',
    projectName: '项目A',
    inputFieldsJson: [
      { id: 1, varId: 1, fieldLabel: '年龄', fieldType: 'INTEGER', scriptName: 'age', validationRuleIds: '[11]', validationOverride: 0 },
      { id: 2, varId: 2, fieldLabel: '收入', fieldType: 'DOUBLE', scriptName: 'income' }
    ],
    outputFieldsJson: [
      { id: 10, varId: 3, fieldLabel: '结果', fieldType: 'STRING', scriptName: 'result' }
    ]
  }
}

describe('RuleDetail 生命周期治理', () => {
  test('详情页加载修订和审计时间线', async () => {
    const wrapper = await mountAndWait()
    expect(definitionApi.listRuleRevisions).toHaveBeenCalledWith(1)
    expect(definitionApi.getRuleLifecycleTimeline).toHaveBeenCalledWith(1)
    expect(wrapper.vm.activeRevision.state).toBe('DRAFT')
  })
})

function mockVariables() {
  return [
    { id: 1, varCode: 'age', varLabel: '年龄', varType: 'INTEGER', varSource: 'INPUT', scriptName: 'age' },
    { id: 2, varCode: 'income', varLabel: '收入', varType: 'DOUBLE', varSource: 'INPUT', scriptName: 'income' },
    { id: 3, varCode: 'result', varLabel: '结果', varType: 'STRING', varSource: 'OUTPUT', scriptName: 'result' }
  ]
}

// ─── 测试用例 ─────────────────────────────────────────────


function makeStub(tag) {
  return { render: () => h(tag) }
}

function makeSlotStub(tag) {
  return {
    render() {
      const label = this.$slots.label ? this.$slots.label() : []
      const content = this.$slots.default ? this.$slots.default() : []
      return h(tag, [...label, ...content])
    }
  }
}

async function mountAndWait(content = { modelJson: '{}', openApiConfigJson: null }) {
  // load() 先调用 refreshFields 重新解析字段，再调用 getDefinitionDetail 获取详情
  definitionApi.refreshFields.mockResolvedValueOnce({ data: {} })
  definitionApi.getDefinitionDetail.mockResolvedValueOnce({ data: mockRuleDetail(1) })
  definitionApi.getContent.mockResolvedValueOnce({ data: content })
  definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [{
    id: 5, definitionId: 1, revisionNo: 1, state: 'DRAFT'
  }] })
  definitionApi.getRuleLifecycleTimeline.mockResolvedValueOnce({ data: [] })
  variableApi.listVariablesByProject.mockResolvedValueOnce({ data: mockVariables() })
  variableApi.listVariables.mockResolvedValueOnce({ data: { records: [] } })
  variableApi.listAvailableFieldValidations.mockResolvedValueOnce({ data: [{
    id: 11, validationCode: 'age_required', validationName: '年龄必填', validationType: 'REQUIRED'
  }, {
    id: 12, validationCode: 'age_min', validationName: '最小年龄', validationType: 'MIN_VALUE'
  }] })

  const wrapper = shallowMount(RuleDetail, {
    props: { id: '1' },
    mocks: {
      $route: { params: { id: 1 } },
      $router: { push: vi.fn(), replace: vi.fn() },
      $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
      $confirm: vi.fn().mockResolvedValue('confirm')
    },
    directives: {
      loading: vi.fn()
    },
    stubs: {
      'el-dialog': makeStub('div'),
      'el-card': makeStub('div'),
      'el-descriptions': makeStub('div'),
      'el-descriptions-item': makeStub('div'),
      'el-form': makeStub('form'),
      'el-form-item': makeStub('div'),
      'el-select': makeStub('select'),
      'el-option': makeStub('option'),
      'el-input': makeStub('input'),
      'el-button': makeStub('button'),
      'el-tag': makeStub('span'),
      'el-alert': makeStub('div'),
      'el-tabs': makeSlotStub('div'),
      'el-tab-pane': makeSlotStub('div'),
      'el-table': makeStub('table'),
      'el-table-column': makeStub('td'),
      'el-input-number': makeStub('input'),
      'el-switch': makeStub('input'),
      'el-row': makeSlotStub('div'),
      'el-col': makeSlotStub('div'),
      'el-date-picker': makeStub('div'),
      'el-divider': makeStub('hr'),
      'el-tooltip': makeStub('span'),
      'el-collapse': makeStub('div'),
      'el-collapse-item': makeStub('div'),
      'el-loading': makeStub('div'),
      'monaco-editor': makeStub('div')
    }
  })

  await nextTick()
  await new Promise(r => setTimeout(r, 100))
  return wrapper
}

describe('RuleDetail — 初始化与数据加载', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('mounted 后调用 getDefinitionDetail', () => {
    expect(definitionApi.getDefinitionDetail).toHaveBeenCalledWith(1)
  })

  test('rule 详情正确赋值', () => {
    expect(wrapper.vm.rule).toBeDefined()
    expect(wrapper.vm.rule.ruleName).toBe('年龄判断规则')
    expect(wrapper.vm.rule.ruleCode).toBe('age_rule')
  })

  test('inputFieldsJson 正确解析为数组', () => {
    expect(wrapper.vm.rule.inputFieldsJson).toBeInstanceOf(Array)
    expect(wrapper.vm.rule.inputFieldsJson.length).toBe(2)
  })

  test('outputFieldsJson 正确解析为数组', () => {
    expect(wrapper.vm.rule.outputFieldsJson).toBeInstanceOf(Array)
    expect(wrapper.vm.rule.outputFieldsJson.length).toBe(1)
  })

  test('loading 初始值为 false（加载完成后）', () => {
    expect(wrapper.vm.loading).toBe(false)
  })

  test('testVisible 初始值为 false', () => {
    expect(wrapper.vm.testVisible).toBe(false)
  })

  test('testParams 初始化为空对象', () => {
    expect(wrapper.vm.testParams).toBeDefined()
    expect(typeof wrapper.vm.testParams).toBe('object')
    expect(Object.keys(wrapper.vm.testParams).length).toBe(0)
  })

  test('renders API test scenario tab with current rule', () => {
    const panel = wrapper.findComponent({ name: 'ApiScenarioPanel' })
    expect(panel.exists()).toBe(true)
    expect(panel.props('rule').id).toBe(1)
    expect(wrapper.text()).toContain('API 测试用例')
  })
})

describe('RuleDetail — 辅助方法', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('modelTypeLabel 返回正确的中文标签', () => {
    expect(wrapper.vm.modelTypeLabel('TABLE')).toBe('决策表')
    expect(wrapper.vm.modelTypeLabel('TREE')).toBe('决策树')
    expect(wrapper.vm.modelTypeLabel('FLOW')).toBe('决策流')
    expect(wrapper.vm.modelTypeLabel('RULE_SET')).toBe('规则集')
    expect(wrapper.vm.modelTypeLabel('SCORE')).toBe('评分卡')
    expect(wrapper.vm.modelTypeLabel('CROSS')).toBe('交叉表')
    expect(wrapper.vm.modelTypeLabel('CROSS_ADV')).toBe('复杂交叉表')
    expect(wrapper.vm.modelTypeLabel('SCORE_ADV')).toBe('复杂评分卡')
    expect(wrapper.vm.modelTypeLabel('SCRIPT')).toBe('QL 脚本')
  })

  test('statusType 返回正确的 tag 类型', () => {
    expect(wrapper.vm.statusType(0)).toBe('info')
    expect(wrapper.vm.statusType(1)).toBe('success')
    expect(wrapper.vm.statusType(2)).toBe('warning')
  })

  test('statusLabel 返回正确的状态标签', () => {
    expect(wrapper.vm.statusLabel(0)).toBe('草稿')
    expect(wrapper.vm.statusLabel(1)).toBe('已发布')
    expect(wrapper.vm.statusLabel(2)).toBe('已下线')
  })

  test('typeLabel 返回正确的类型标签', () => {
    expect(wrapper.vm.typeLabel('INTEGER')).toBe('整数')
    expect(wrapper.vm.typeLabel('DOUBLE')).toBe('浮点')
    expect(wrapper.vm.typeLabel('STRING')).toBe('字符串')
    expect(wrapper.vm.typeLabel('BOOLEAN')).toBe('布尔')
    expect(wrapper.vm.typeLabel('DATE')).toBe('日期')
    expect(wrapper.vm.typeLabel('ENUM')).toBe('枚举')
    expect(wrapper.vm.typeLabel('OBJECT')).toBe('对象')
  })

  test('缺少 ID 的数据对象字段不会按路径回退关联', () => {
    wrapper.vm.buildVarOptions([], [{
      object: { objectCode: 'bankcard', objectLabel: '银行卡信息', scriptName: 'bankcard' },
      flatVariables: [{ id: 11, varCode: 'bank_card_no', varLabel: '银行卡号', scriptName: 'bank_card_no', varType: 'STRING' }]
    }])
    const row = { refType: 'DATA_OBJECT', scriptName: 'bankcard.bank_card_no', fieldLabel: '银行卡号' }

    expect(wrapper.vm.fieldDisplayLabel(row)).toBe('银行卡号')
    expect(wrapper.vm.getFieldVarMap(row)).toBeNull()
  })
})

describe('RuleDetail — 开放接口契约', () => {
  test('统一响应编辑项位于 Element Plus 表单内', async () => {
    const wrapper = await mountAndWait()

    expect(wrapper.find('.open-api-response-form').exists()).toBe(true)
    wrapper.unmount()
  })

  test('加载并按稳定引用 ID 保存请求映射和可配置外层响应', async () => {
    const contract = {
      enabled: true,
      recordTrace: true,
      returnTrace: false,
      requestMappings: [{
        targetVarId: 1,
        targetRefType: 'VARIABLE',
        sourceType: 'BODY',
        sourcePath: '$.customer.age',
        required: true,
        targetType: 'INTEGER'
      }],
      responseMappings: [{
        sourceVarId: 3,
        sourceRefType: 'VARIABLE',
        targetField: 'decision_result'
      }],
      envelopeTemplate: { retCode: '${status.code}', payload: '${data}' },
      dataPath: '$.payload',
      successDataTemplate: { result: '${output.VARIABLE.3}' },
      errorDataTemplate: { errorCode: '${status.code}' },
      responseHeaders: { 'X-Business-Code': '${status.code}' }
    }
    const wrapper = await mountAndWait({ modelJson: '{"type":"table"}', openApiConfigJson: JSON.stringify(contract) })
    definitionApi.saveContent.mockResolvedValueOnce({})

    expect(wrapper.vm.openApiForm.requestMappings[0].targetKey).toBe('VARIABLE:1')
    expect(wrapper.vm.openApiForm.responseMappings[0].sourceKey).toBe('VARIABLE:3')
    expect(wrapper.vm.openEnvelopeText).toContain('payload')
    await wrapper.vm.saveOpenApiConfig()

    const payload = definitionApi.saveContent.mock.calls[0][0]
    const saved = JSON.parse(payload.openApiConfigJson)
    expect(payload.modelJson).toBe('{"type":"table"}')
    expect(saved.requestMappings[0]).toEqual(expect.objectContaining({
      targetVarId: 1,
      targetRefType: 'VARIABLE',
      sourcePath: '$.customer.age'
    }))
    expect(saved.envelopeTemplate.payload).toBe('${data}')
    expect(saved.responseHeaders['X-Business-Code']).toBe('${status.code}')
    expect(saved.responseMappings[0]).toEqual({
      sourceVarId: 3,
      sourceRefType: 'VARIABLE',
      targetField: 'decision_result'
    })
    wrapper.unmount()
  })

  test('新契约默认按内部字段名生成请求和响应映射', async () => {
    const wrapper = await mountAndWait()

    expect(wrapper.vm.openApiForm.requestMappings).toEqual(expect.arrayContaining([
      expect.objectContaining({ targetKey: 'VARIABLE:1', sourcePath: '$.age' }),
      expect.objectContaining({ targetKey: 'VARIABLE:2', sourcePath: '$.income' })
    ]))
    expect(wrapper.vm.openApiForm.responseMappings).toEqual([
      expect.objectContaining({ sourceKey: 'VARIABLE:3', targetField: 'result' })
    ])
    expect(JSON.parse(wrapper.vm.openSuccessDataText)).toBe('${response}')
    expect(wrapper.vm.openApiStatusCodes.map(item => item.code)).toEqual(expect.arrayContaining([
      '000000', '100001', '100002', '200001', '300001', '300002', '300003',
      '300004', '300005', '400001', '400002', '400003', '500001', '500002',
      '500003', '500004'
    ]))
    wrapper.unmount()
  })

  test('data object input defaults to the same external leaf field', async () => {
    const wrapper = await mountAndWait()
    wrapper.vm.rule.inputFieldsJson = [{
      varId: 125,
      refType: 'DATA_OBJECT',
      fieldName: 'mobile',
      fieldLabel: 'mobile',
      fieldType: 'STRING',
      scriptName: 'ICEKREDIT_REQUEST.mobile'
    }]
    wrapper.vm.loadOpenApiConfig(null)

    expect(wrapper.vm.openApiForm.requestMappings).toEqual([
      expect.objectContaining({ targetKey: 'DATA_OBJECT:125', sourcePath: '$.mobile' })
    ])
    wrapper.unmount()
  })

  test('校验预览使用同一外层结构并分别渲染成功和异常 data', async () => {
    const wrapper = await mountAndWait()
    wrapper.vm.openApiForm.enabled = true
    wrapper.vm.openEnvelopeText = JSON.stringify({ code: '${status.code}', payload: '${data}' })
    wrapper.vm.openApiForm.dataPath = '$.payload'
    wrapper.vm.openSuccessDataText = JSON.stringify({ decision: '${output.VARIABLE.3}' })
    wrapper.vm.openErrorDataText = JSON.stringify({ errorCode: '${status.code}' })

    wrapper.vm.previewOpenApiConfig()

    expect(wrapper.vm.openApiPreviewVisible).toBe(true)
    expect(Object.keys(wrapper.vm.openApiSuccessPreview)).toEqual(Object.keys(wrapper.vm.openApiErrorPreview))
    expect(wrapper.vm.openApiSuccessPreview.payload.decision).toBe('<VARIABLE:3>')
    expect(wrapper.vm.openApiErrorPreview.payload.errorCode).toBe('100001')
    wrapper.unmount()
  })
})

describe('RuleDetail — 出入参字段管理', () => {
  let wrapper

  beforeEach(async () => {
    definitionApi.getDefinitionDetail.mockResolvedValueOnce({ data: mockRuleDetail(1) })
    variableApi.listVariablesByProject.mockResolvedValueOnce({ data: mockVariables() })
    variableApi.listVariables.mockResolvedValueOnce({ data: { records: [] } })
    definitionApi.updateInputField.mockResolvedValueOnce({ data: { success: true } })
    definitionApi.updateOutputField.mockResolvedValueOnce({ data: { success: true } })
    wrapper = await mountAndWait()
  })
  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('inputFieldsJson 每项包含 varId 和 fieldType', () => {
    const field = wrapper.vm.rule.inputFieldsJson[0]
    expect(field).toHaveProperty('varId')
    expect(field).toHaveProperty('fieldType')
    expect(field.fieldType).toBe('INTEGER')
  })

  test('outputFieldsJson 每项包含 varId 和 fieldType', () => {
    const field = wrapper.vm.rule.outputFieldsJson[0]
    expect(field).toHaveProperty('varId')
    expect(field).toHaveProperty('fieldType')
    expect(field.fieldType).toBe('STRING')
  })

  test('editInputField 设置行的 _editing 标志', () => {
    const field = wrapper.vm.rule.inputFieldsJson[0]
    wrapper.vm.editInputField(field)
    expect(field._editing).toBe(true)
  })

  test('cancelEditInput 取消编辑并恢复 _editing 状态', () => {
    const field = wrapper.vm.rule.inputFieldsJson[0]
    field._editing = true
    field._originalData = { fieldLabel: '年龄' }
    wrapper.vm.cancelEditInput(field)
    expect(field._editing).toBe(false)
  })

  test('editOutputField 设置行的 _editing 标志', () => {
    const field = wrapper.vm.rule.outputFieldsJson[0]
    wrapper.vm.editOutputField(field)
    expect(field._editing).toBe(true)
  })
})

describe('RuleDetail — 测试弹窗', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('openTestDialog 打开测试弹窗', async () => {
    definitionApi.getRuleTestSchema.mockResolvedValue({ data: {
      inputs: [{ refId: 9, refType: 'DATA_OBJECT', scriptName: 'bankcard.bank_card_no', label: '银行卡信息/银行卡号', valueType: 'STRING' }],
      sampleParams: { bankcard: { bank_card_no: '6222' } }
    } })
    await wrapper.vm.openTestDialog()
    expect(wrapper.vm.testVisible).toBe(true)
    expect(definitionApi.getRuleTestSchema).toHaveBeenCalledWith({ targetType: 'RULE', targetId: 1 })
    expect(JSON.parse(wrapper.vm.testJsonStr)).toEqual({ bankcard: { bank_card_no: '6222' } })
  })

  test('testMode 默认为 manual', () => {
    expect(wrapper.vm.testMode).toBe('manual')
  })

  test('switchToJsonMode 切换到 JSON 模式', () => {
    wrapper.vm.switchToJsonMode()
    expect(wrapper.vm.testMode).toBe('json')
  })

  test('switchToManualMode 切换到表单模式', () => {
    wrapper.vm.switchToManualMode()
    expect(wrapper.vm.testMode).toBe('manual')
  })

  test('handleClearParams 清空 testParams', () => {
    wrapper.vm.testParams = { age: 30, score: 85.5 }
    wrapper.vm.handleClearParams()
    expect(Object.keys(wrapper.vm.testParams).length).toBe(0)
  })

  test('formatResult 格式化输出结果', () => {
    const outputs = { result: 'PASS', score: 100 }
    const formatted = wrapper.vm.formatResult(outputs)
    expect(formatted).toContain('result')
    expect(formatted).toContain('PASS')
  })
})

describe('RuleDetail execute test request', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('doTest 按执行接口契约传递 definitionId', async () => {
    definitionApi.executeRule.mockResolvedValueOnce({ data: { success: true, result: false } })
    wrapper.vm.testMode = 'json'
    wrapper.vm.testJsonStr = '{"age":55}'

    await wrapper.vm.doTest()

    expect(definitionApi.executeRule).toHaveBeenCalledWith({
      definitionId: 1,
      params: { age: 55 }
    }, 180000)
    expect(wrapper.vm.testResult).toMatchObject({ hasOutput: true, output: false })
  })

  test('数据对象字段使用完整对象名称和脚本路径', () => {
    wrapper.vm.buildVarOptions([], [{
      object: { objectLabel: '银行卡信息', objectCode: 'bankcard', scriptName: 'bankcard' },
      variables: [{ id: 88, varLabel: '银行卡号', varCode: 'bank_card_no', scriptName: 'bank_card_no', varType: 'STRING' }]
    }])

    const item = wrapper.vm.varMap['DATA_OBJECT:88']
    expect(item.varLabel).toBe('银行卡信息/银行卡号 bankcard.bank_card_no')
    expect(item.varCodeText).toBe('bankcard.bank_card_no')
  })
})

describe('RuleDetail version history', () => {
  let wrapper

  function deferred() {
    let resolve
    let reject
    const promise = new Promise((res, rej) => { resolve = res; reject = rej })
    return { promise, resolve, reject }
  }

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('打开版本历史后默认比较上一发布版与最新发布版', async () => {
    definitionApi.listVersions.mockResolvedValueOnce({
      data: [
        { version: 3, modelJson: '{"a":3}', compiledScript: 'a = 3' },
        { version: 2, modelJson: '{"a":2}', compiledScript: 'a = 2' },
        { version: 1, modelJson: '{"a":1}', compiledScript: 'a = 1' }
      ]
    })
    definitionApi.compareVersions.mockResolvedValueOnce({
      data: {
        left: { version: 2, modelJson: '{"a":2}' },
        right: { version: 3, modelJson: '{"a":3}' },
        modelJsonChanged: true,
        compiledScriptChanged: true
      }
    })

    await wrapper.vm.openVersionDialog()

    expect(wrapper.vm.versionVisible).toBe(true)
    expect(definitionApi.listVersions).toHaveBeenCalledWith(1)
    expect(wrapper.vm.versions.length).toBe(3)
    expect(wrapper.vm.leftVersionNumber).toBe(2)
    expect(wrapper.vm.rightVersionNumber).toBe(3)
    expect(definitionApi.compareVersions).toHaveBeenCalledWith(1, 2, 3)
  })

  test('对比上一版快捷入口保持旧版在左新版在右', async () => {
    definitionApi.compareVersions.mockResolvedValueOnce({
      data: {
        left: { version: 1, modelJson: '{"a":1}', compiledScript: 'a = 1' },
        right: { version: 2, modelJson: '{"a":2}', compiledScript: 'a = 2' },
        modelJsonChanged: true,
        compiledScriptChanged: true
      }
    })

    await wrapper.vm.compareVersion({ version: 2 }, { version: 1 })

    expect(wrapper.vm.leftVersionNumber).toBe(1)
    expect(wrapper.vm.rightVersionNumber).toBe(2)
    expect(definitionApi.compareVersions).toHaveBeenCalledWith(1, 1, 2)
    expect(wrapper.vm.versionCompare.modelJsonChanged).toBe(true)
  })

  test('可以选择任意两个已发布版本进行对比', async () => {
    definitionApi.compareVersions.mockResolvedValueOnce({
      data: { left: { version: 1 }, right: { version: 3 }, modelJsonChanged: true }
    })

    await wrapper.vm.selectVersionPair(1, 3)

    expect(definitionApi.compareVersions).toHaveBeenCalledWith(1, 1, 3)
    expect(wrapper.vm.versionCompare.right.version).toBe(3)
  })

  test('相同版本不发起对比请求', async () => {
    await wrapper.vm.selectVersionPair(2, 2)

    expect(definitionApi.compareVersions).not.toHaveBeenCalled()
    expect(wrapper.vm.$message.warning).toHaveBeenCalledWith('请选择两个不同的发布版本')
  })

  test('交换版本后按新方向重新对比', async () => {
    wrapper.vm.leftVersionNumber = 1
    wrapper.vm.rightVersionNumber = 3
    definitionApi.compareVersions.mockResolvedValueOnce({
      data: { left: { version: 3 }, right: { version: 1 }, modelJsonChanged: true }
    })

    await wrapper.vm.swapVersionCompare()

    expect(wrapper.vm.leftVersionNumber).toBe(3)
    expect(wrapper.vm.rightVersionNumber).toBe(1)
    expect(definitionApi.compareVersions).toHaveBeenCalledWith(1, 3, 1)
  })

  test('快速切换时只保留最后一次版本对比响应', async () => {
    const first = deferred()
    const second = deferred()
    definitionApi.compareVersions.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

    const firstRequest = wrapper.vm.selectVersionPair(1, 2)
    const secondRequest = wrapper.vm.selectVersionPair(2, 3)
    second.resolve({ data: { left: { version: 2 }, right: { version: 3 } } })
    await secondRequest
    first.resolve({ data: { left: { version: 1 }, right: { version: 2 } } })
    await firstRequest

    expect(wrapper.vm.versionCompare.left.version).toBe(2)
    expect(wrapper.vm.versionCompare.right.version).toBe(3)
  })

  test('发布版本不足两个时不自动对比', async () => {
    definitionApi.listVersions.mockResolvedValueOnce({ data: [{ version: 1 }] })

    await wrapper.vm.openVersionDialog()

    expect(wrapper.vm.leftVersionNumber).toBeNull()
    expect(wrapper.vm.rightVersionNumber).toBeNull()
    expect(definitionApi.compareVersions).not.toHaveBeenCalled()
  })

  test('rollbackVersion calls rollback and refreshes data', async () => {
    wrapper.vm.$confirm = vi.fn().mockResolvedValue('confirm')
    definitionApi.rollbackVersion.mockResolvedValueOnce({})
    definitionApi.refreshFields.mockResolvedValueOnce({ data: {} })
    definitionApi.getDefinitionDetail.mockResolvedValueOnce({ data: mockRuleDetail(1) })
    variableApi.listVariablesByProject.mockResolvedValueOnce({ data: mockVariables() })
    variableApi.listVariables.mockResolvedValueOnce({ data: { records: [] } })
    definitionApi.listVersions.mockResolvedValueOnce({ data: [{ version: 1 }] })

    await wrapper.vm.rollbackVersion({ version: 1 })

    expect(definitionApi.rollbackVersion).toHaveBeenCalledWith(1, 1)
    expect(definitionApi.getDefinitionDetail).toHaveBeenCalled()
    expect(definitionApi.listVersions).toHaveBeenCalled()
  })
})

describe('RuleDetail — 输入字段校验配置', () => {
  test('加载继承的规则，并保存为当前规则显式覆盖', async () => {
    const wrapper = await mountAndWait()
    definitionApi.updateInputField.mockResolvedValueOnce({})
    const row = wrapper.vm.rule.inputFieldsJson[0]

    expect(row.validationRuleIdList).toEqual([11])
    expect(wrapper.vm.fieldValidationOptions).toHaveLength(2)
    wrapper.vm.editInputField(row)
    row.validationRuleIdList = [12]
    await wrapper.vm.saveInputField(row)

    expect(definitionApi.updateInputField).toHaveBeenCalledWith(1, expect.objectContaining({
      validationRuleIds: '[12]', validationOverride: 1
    }))
    wrapper.unmount()
  })
})
