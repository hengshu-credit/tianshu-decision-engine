// tests/unit/views/ruleTest.spec.js
// 规则测试页面 RuleTest.vue 单元测试
// 覆盖：初始化、辅助方法、加载变量（决策表/交叉表）、执行与结果展示

import { shallowMount } from '@test-utils'
import { h, nextTick } from 'vue'
import fs from 'fs'
import path from 'path'

import * as definitionApi from '@/api/definition'
import * as variableApi from '@/api/variable'
import * as projectApi from '@/api/project'
import * as modelApi from '@/api/model'
import RuleTest from '@/views/test/RuleTest.vue'
import { ElAlert } from 'element-plus/es/components/alert/index.mjs'
import { ElDescriptions, ElDescriptionsItem } from 'element-plus/es/components/descriptions/index.mjs'
import { stringifyExactJson } from '@/utils/exactJson'

function validatedResult(response, validation = { status: 'NOT_CONFIGURED', message: '未设置预期', checks: [], diffs: [] }) {
  return { data: { execution: response.data || response, responseJson: JSON.stringify(response), validation } }
}

afterEach(() => { vi.clearAllMocks() })

// ─── Mock 数据 ───────────────────────────────────────────
function mockProjects() {
  return [
    { id: 1, projectName: '综合风控示例项目', projectCode: 'RISK_DEMO', scope: 'PROJECT' }
  ]
}

function mockRules() {
  return [
    { id: 1, ruleCode: 'RC_PRICING_TABLE', ruleName: '客商×产品总线定价表', modelType: 'TABLE', status: 1, projectId: 1, scope: 'PROJECT' },
    { id: 4, ruleCode: 'RC_RATE_MATRIX', ruleName: '风险定价交叉表', modelType: 'CROSS', status: 1, projectId: 1, scope: 'PROJECT' }
  ]
}

function mockCrossTableSchema() {
  return {
    data: {
      inputs: [
        { refId: 1, refType: 'VARIABLE', scriptName: 'taxpayerType', label: '客商类型', valueType: 'STRING' },
        { refId: 2, refType: 'VARIABLE', scriptName: 'goodsCategory', label: '产品总线', valueType: 'STRING' }
      ],
      sampleParams: { taxpayerType: '', goodsCategory: '' }
    }
  }
}

// 项目变量（对应数据库中 id=1,2,3 的变量）
function mockVariables() {
  return [
    { id: 1, varCode: 'taxpayerType', varLabel: '客商类型', varType: 'STRING', varSource: 'INPUT', scriptName: 'taxpayerType' },
    { id: 2, varCode: 'goodsCategory', varLabel: '产品总线', varType: 'STRING', varSource: 'INPUT', scriptName: 'goodsCategory' },
    { id: 3, varCode: 'taxRate', varLabel: '风险定价费率', varType: 'NUMBER', varSource: 'COMPUTED', scriptName: 'taxRate' }
  ]
}

// 执行结果（与 RuleTest.vue 的 result 字段一致）
function mockExecutionResult() {
  return {
    success: true,
    executeTimeMs: 15,
    result: { taxRate: 0.03 },
    hasOutput: true,
    output: { taxRate: 0.03 },
    traces: [
      {
        input: { taxpayerType: '小规模纳税人', goodsCategory: '服务' },
        output: { taxRate: 0.03 },
        steps: [
          { expr: 'taxpayerType == "小规模纳税人" && goodsCategory == "服务"', result: true, matched: true },
          { expr: 'taxRate = 0.03', result: 0.03 }
        ]
      }
    ],
    errorMessage: null
  }
}

// ─── 测试辅助 ─────────────────────────────────────────────
function makeStub(tag) {
  return { render: () => h(tag) }
}


// ─── 测试用例 ─────────────────────────────────────────────

describe('RuleTest — 初始化与数据加载', () => {
  let wrapper

  beforeEach(async () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: mockProjects() } })
    definitionApi.listDefinitions.mockResolvedValueOnce({ data: { records: mockRules() } })
    variableApi.listVariables.mockResolvedValueOnce({ data: { records: mockVariables() } })

    wrapper = shallowMount(RuleTest, {
      mocks: {
        $route: { params: {} },
        $router: { push: vi.fn(), replace: vi.fn() }
      },
      stubs: {
        'el-form': makeStub('form'),
        'el-form-item': makeStub('div'),
        'el-select': makeStub('select'),
        'el-option': makeStub('option'),
        'el-input': makeStub('input'),
        'el-input-number': makeStub('input'),
        'el-button': makeStub('button'),
        'el-tag': makeStub('span'),
        'el-table': makeStub('table'),
        'el-table-column': makeStub('td'),
        'el-tabs': makeStub('div'),
        'el-tab-pane': makeStub('div'),
        'el-dialog': makeStub('div'),
        'el-card': makeStub('div'),
        'el-alert': makeStub('div'),
        'el-divider': makeStub('hr'),
        'el-loading': makeStub('div'),
        'el-descriptions': makeStub('div'),
        'el-descriptions-item': makeStub('div'),
        'el-radio-group': makeStub('div'),
        'el-radio-button': makeStub('div'),
        'el-collapse': makeStub('div'),
        'el-collapse-item': makeStub('div'),
        'monaco-editor': makeStub('div'),
        'trace-tree': makeStub('div'),
        'var-picker': makeStub('div'),
        'script-panel': makeStub('div')
      }
    })

    await nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('真实摘要显示所选测试版本而非管理内容版本，切版本同步且上线状态明确区分', async () => {
    const page = shallowMount(RuleTest, {
      mocks: { $route: { params: {} }, $router: { push: vi.fn(), replace: vi.fn() } },
      stubs: {
        'el-descriptions': ElDescriptions, 'el-descriptions-item': ElDescriptionsItem,
        ElDescriptionsRow: false, ElDescriptionsCell: false,
      },
    })
    try {
      await page.setData({
        selectedRuleId: 1,
        selectedRule: { ...mockRules()[0], currentVersion: 5 },
        testSourceOptions: [
          { value: 'REVISION:33', label: '草稿 3 · 未发布', source: { sourceType: 'REVISION', sourceId: '33', lockVersion: 2 } },
          { value: 'VERSION:91', label: '发布版本 v1', source: { sourceType: 'VERSION', sourceId: '91' } },
        ],
        selectedTestSourceValue: 'REVISION:33',
      })
      const summary = page.find('.rule-info')
      expect(summary.find('.el-descriptions__body').exists()).toBe(true)
      expect(summary.text()).toContain('测试版本')
      expect(summary.text()).toContain('草稿 3 · 未发布')
      expect(summary.text()).toContain('规则上线状态')
      expect(summary.text()).toContain('已发布')
      expect(summary.text()).not.toContain('v5')
      await page.setData({ selectedTestSourceValue: 'VERSION:91' })
      expect(summary.text()).toContain('发布版本 v1')
      expect(summary.text()).not.toContain('草稿 3 · 未发布')
      await page.setData({ selectedTestSourceValue: '' })
      expect(summary.text()).toContain('尚未选择')
      expect(summary.text()).not.toContain('v5')
    } finally { page.unmount() }
  })

  test('真实输入状态仅成功空Schema提示可直接执行，失败和加载中不显示正常空态', async () => {
    const page = shallowMount(RuleTest, {
      mocks: { $route: { params: {} }, $router: { push: vi.fn(), replace: vi.fn() } },
    })
    try {
      await page.setData({ selectedRuleId: 1, selectedRule: mockRules()[0], params: [], inputLoadState: 'ERROR', inputLoadError: '冻结版本字段读取失败' })
      const inputs = page.find('.input-parameters-card')
      expect(inputs.find('.input-load-state').text()).toContain('测试输入加载失败')
      expect(inputs.find('.input-load-state').text()).toContain('冻结版本字段读取失败')
      expect(inputs.find('.input-load-state').text()).toContain('重新加载')
      expect(inputs.text()).not.toContain('该规则暂无结构化输入，可直接执行或手动添加参数')
      for (const state of ['IDLE', 'LOADING', 'READY']) {
        await page.setData({ inputLoadState: state })
        expect(inputs.text()).not.toContain('该规则暂无结构化输入，可直接执行或手动添加参数')
        if (state === 'LOADING') expect(inputs.find('.input-load-state').text()).toContain('正在准备测试输入')
      }
      await page.setData({ inputLoadState: 'EMPTY' })
      expect(inputs.text()).toContain('该规则暂无结构化输入，可直接执行或手动添加参数')
      await page.setData({ selectedRuleId: null, selectedRule: null, inputLoadState: 'IDLE' })
      expect(inputs.text()).toContain('请选择规则后加载字段，或手动添加参数')
      expect(inputs.text()).not.toContain('该规则暂无结构化输入，可直接执行或手动添加参数')
    } finally { page.unmount() }
  })

  test('选择规则后单次和批量执行采用明确草稿来源及其Schema', async () => {
    const source = { sourceType: 'REVISION', sourceId: '22', lockVersion: 4 }
    const executionIdentity = { ...source, definitionId: '1', revisionId: '22' }
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [
      { id: '21', definitionId: '1', revisionNo: 1, state: 'PUBLISHED', artifactId: '91' },
      { id: '22', definitionId: '1', revisionNo: 2, state: 'DRAFT', lockVersion: 4, sourceType: 'REVISION', sourceId: '21' },
    ] })
    definitionApi.listPublishedVersions.mockResolvedValueOnce({ data: [] })
    definitionApi.getRuleRevision.mockResolvedValueOnce({ data: { id: '22', definitionId: '1', modelJson: '{}' } })
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: { inputs: [], outputs: [], sampleParams: {}, executionIdentity } })
    wrapper.vm.selectedRuleId = 1
    await wrapper.vm.onRuleChange()
    expect(definitionApi.getRuleTestSchema).toHaveBeenLastCalledWith(expect.objectContaining({ executionSource: source }))
    definitionApi.executeApiScenarioValidated.mockResolvedValue(validatedResult({ data: { success: true, result: 9, executionIdentity } }))
    await wrapper.vm.handleExecute()
    expect(definitionApi.executeApiScenarioValidated.mock.lastCall[1].executionSource).toEqual(source)
    wrapper.vm.testCases = [{ id: 71, scenarioName: '同修订样例', requestJson: '{"params":{}}', responseJson: '{"data":{"success":true,"result":9}}' }]
    wrapper.vm.selectedTestCaseIds = [71]
    await wrapper.vm.executeSelectedTestCases()
    expect(definitionApi.executeApiScenarioValidated.mock.lastCall[1].executionSource).toEqual(source)
    expect(wrapper.vm.batchResults[0].success).toBe(true)
  })

  test('测试版本响应格式损坏不能伪装成无修订并改测工作区', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: { broken: true } })
    definitionApi.listPublishedVersions.mockResolvedValueOnce({ data: [] })
    wrapper.vm.selectedRuleId = 1
    await wrapper.vm.onRuleChange()
    expect(wrapper.vm.testSourcesError).toContain('版本列表')
    expect(wrapper.vm.selectedExecutionSource).toBeNull()
    expect(definitionApi.getRuleTestSchema).not.toHaveBeenCalled()
    await wrapper.vm.handleExecute()
    expect(definitionApi.executeApiScenarioValidated).not.toHaveBeenCalled()
  })

  test('卸载后迟到的测试版本列表不能写回页面状态', async () => {
    let finish
    definitionApi.listRuleRevisions.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    definitionApi.listPublishedVersions.mockResolvedValueOnce({ data: [] })
    wrapper.vm.selectedRuleId = 1
    const loading = wrapper.vm.onRuleChange()
    const vm = wrapper.vm
    wrapper.unmount(); wrapper = null
    finish({ data: [{ id: '22', definitionId: '1', revisionNo: 2, state: 'DRAFT', lockVersion: 4 }] })
    await loading
    expect(vm.testSourceOptions).toEqual([])
    expect(definitionApi.getRuleTestSchema).not.toHaveBeenCalled()
  })

  test('精确测试接口保留输入数字原文并把执行成功与未设置预期分开', async () => {
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.params = [{ key: 'amount', value: '9007199254740993', type: 'NUMBER' }]
    definitionApi.executeApiScenarioValidated.mockResolvedValueOnce(validatedResult({ data: { success: true, result: 5 } }))
    await wrapper.vm.handleExecute()
    expect(definitionApi.executeApiScenarioValidated).toHaveBeenCalledOnce()
    expect(definitionApi.executeApiScenarioValidated.mock.calls[0][1].requestJson).toContain('"amount":9007199254740993')
    expect(wrapper.vm.result.success).toBe(true)
    expect(wrapper.vm.testValidation.status).toBe('NOT_CONFIGURED')
  })

  test('执行结果读取按业务值128层预算，响应封装不占层数且129层拒绝', () => {
    const nested = count => '{"next":'.repeat(count) + '9007199254740993' + '}'.repeat(count)
    const raw = count => '{"code":200,"data":{"success":true,"result":' + nested(count) + '}}'
    const response = responseJson => ({ data: { execution: { success: true }, responseJson, validation: { status: 'NOT_CONFIGURED' } } })
    const accepted = wrapper.vm.unwrapValidatedResponse(response(raw(128)))
    expect(accepted.responseJson).toBe(raw(128))
    expect(() => wrapper.vm.unwrapValidatedResponse(response(raw(129)))).toThrow(/深度/)
  })

  test('深边界业务输出显示保留大整数原文，不退回已舍入的execution对象', () => {
    const business = '{"next":'.repeat(128) + '9007199254740993' + '}'.repeat(128)
    const raw = '{"code":200,"data":{"success":true,"result":' + business + '}}'
    wrapper.vm.lastRawResponseJson = raw
    wrapper.vm.result = { success: true, hasOutput: true, output: JSON.parse(business) }
    expect(wrapper.vm.rawOutputJson()).toBe(business)
    expect(wrapper.vm.outputResultJson).toBe(business)
    expect(wrapper.vm.exactOutputDisplay).toContain('9007199254740993')
    expect(wrapper.vm.exactOutputDisplay).not.toContain('9007199254740992')
    expect(wrapper.vm.lastRawResponseJson).toBe(raw)
    wrapper.vm.lastRawResponseJson = '{"data":{"success":true,"result":{"outer":' + business + '}}}'
    expect(wrapper.vm.rawOutputJson()).toBe('')
  })

  test('载入用例恢复ID预期及原JSON缺失/null/未知大数，不由执行结果自动生成预期', async () => {
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedRule = mockRules()[1]
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: { inputs: [], outputs: [], sampleParams: {} } })
    definitionApi.listInputFields.mockResolvedValueOnce({ data: [] })
    const expectation = '{"version":1,"mode":"FIELDS","checks":[{"refType":"VARIABLE","refId":"19","valueType":"NUMBER","operator":"==","expected":{"kind":"LITERAL","valueType":"NUMBER","value":"5"}}]}'
    const requestJson = '{"metadata":{"keep":true},"params":{"n":9007199254740993,"nil":null,"a.b":1,"a":{"b":2}}}'
    await wrapper.vm.applyTestCase({ id: 71, scenarioName: '精确用例', requestJson, responseJson: '{}', expectationJson: expectation })
    expect(wrapper.vm.expectationJson).toBe(expectation)
    expect(wrapper.vm.buildRequestJson()).toBe(requestJson)
    expect(stringifyExactJson(wrapper.vm.buildParamMap())).toContain('"n":9007199254740993')
    expect(wrapper.vm.loadedTestCase.id).toBe(71)
  })

  test('批量使用服务器校验状态，执行成功但断言失败不得显示通过', async () => {
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.testCases = [{ id: 71, scenarioName: '预期不符', requestJson: '{"params":{}}', responseJson: '{}' }]
    wrapper.vm.selectedTestCaseIds = [71]
    definitionApi.executeApiScenarioValidated.mockResolvedValueOnce(validatedResult({ data: { success: true, result: 5 } },
      { status: 'FAILED', message: '实际输出不符合预期', checks: [], diffs: [] }))
    await wrapper.vm.executeSelectedTestCases()
    expect(wrapper.vm.batchResults[0]).toMatchObject({ success: true, validationStatus: 'FAILED' })
  })

  test('输出ID预期与精确快照经过新增、载入、更新与批量执行保持同一协议', async () => {
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedRule = mockRules()[1]
    wrapper.vm.params = [{ key: 'amount', value: '9007199254740993', type: 'NUMBER' }]
    const config = '{"version":1,"mode":"FIELDS","checks":[{"refType":"VARIABLE","refId":"9007199254740993","valueType":"NUMBER","operator":"==","expected":{"kind":"LITERAL","valueType":"NUMBER","value":"9007199254740993"}}]}'
    wrapper.vm.expectationJson = config
    const raw = '{"code":200,"data":{"success":true,"result":{"amount":9007199254740993}}}'
    definitionApi.executeApiScenarioValidated.mockResolvedValueOnce({ data: {
      execution: JSON.parse(raw).data, responseJson: raw,
      validation: { status: 'PASSED', message: '所有输出字段预期均满足', checks: [], diffs: [] },
    } })
    await wrapper.vm.handleExecute()
    expect(wrapper.vm.outputResultJson).toBe('{"amount":9007199254740993}')
    expect(wrapper.vm.validationStale).toBe(false)
    wrapper.vm.$prompt = vi.fn().mockResolvedValue({ value: '精确业务用例' })
    let stored
    definitionApi.createApiScenario.mockImplementationOnce(async (id, data) => {
      stored = { ...data, id: 71 }; return { data: stored }
    })
    definitionApi.listApiScenarios.mockImplementation(async () => ({ data: stored ? [stored] : [] }))
    await wrapper.vm.saveCurrentTestCase()
    expect(stored.expectationJson).toBe(config)
    expect(stored.responseJson).toBe(raw)
    expect(stored.requestJson).toContain('"amount":9007199254740993')
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: { inputs: [], outputs: [], sampleParams: {} } })
    definitionApi.listInputFields.mockResolvedValueOnce({ data: [] })
    await wrapper.vm.applyTestCase(wrapper.vm.testCases[0])
    expect(wrapper.vm.expectationJson).toBe(config)
    wrapper.vm.expectationJson = '{"version":1,"mode":"NONE"}'
    definitionApi.updateApiScenario.mockImplementationOnce(async (id, caseId, data) => {
      stored = { ...data, id: caseId }; return { data: stored }
    })
    await wrapper.vm.saveCurrentTestCase('UPDATE')
    expect(definitionApi.createApiScenario).toHaveBeenCalledTimes(1)
    expect(definitionApi.updateApiScenario.mock.lastCall.slice(0, 2)).toEqual([4, 71])
    expect(stored.responseJson).toBe(raw)
    wrapper.vm.selectedTestCaseIds = [71]
    definitionApi.executeApiScenarioValidated.mockResolvedValueOnce(validatedResult({ data: { success: true, result: 7 } }))
    await wrapper.vm.executeSelectedTestCases()
    expect(definitionApi.executeApiScenarioValidated.mock.lastCall[1]).toMatchObject({ scenarioId: 71, expectationJson: stored.expectationJson, requestJson: stored.requestJson, expectedResponseJson: raw })
    expect(wrapper.vm.batchResults[0]).toMatchObject({ success: true, validationStatus: 'NOT_CONFIGURED' })
  })

  test('同规则载入另一用例立即释放旧命名保存状态且不写入旧请求', async () => {
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedRule = mockRules()[1]
    let finishPrompt
    wrapper.vm.$prompt = vi.fn().mockReturnValue(new Promise(resolve => { finishPrompt = resolve }))
    const saving = wrapper.vm.saveCurrentTestCase()
    expect(wrapper.vm.testCaseSaving).toBe(true)
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: { inputs: [], outputs: [], sampleParams: {} } })
    definitionApi.listInputFields.mockResolvedValueOnce({ data: [] })
    await wrapper.vm.applyTestCase({ id: 72, scenarioName: '另一个用例', requestJson: '{"params":{}}', responseJson: '{}', expectationJson: '{"version":1,"mode":"NONE"}' })
    expect(wrapper.vm.testCaseSaving).toBe(false)
    finishPrompt({ value: '过时用例' }); await saving
    expect(definitionApi.createApiScenario).not.toHaveBeenCalled()
    expect(wrapper.vm.loadedTestCase.id).toBe(72)
  })

  test('载入期间预期同步切换且保留之后的新编辑，不可将旧输入提交到新用例', async () => {
    wrapper.vm.selectedRuleId = 4; wrapper.vm.selectedRule = mockRules()[1]
    wrapper.vm.params = [{ key: 'old', value: 'old-input', type: 'STRING' }]
    let finishSchema
    definitionApi.getRuleTestSchema.mockReturnValueOnce(new Promise(resolve => { finishSchema = resolve }))
    const loading = wrapper.vm.applyTestCase({ id: 71, requestJson: '{"params":{"age":18}}', responseJson: '{}', expectationJson: '{"version":1,"mode":"NONE"}' })
    expect(wrapper.vm.expectationJson).toBe('{"version":1,"mode":"NONE"}')
    const edited = '{"version":1,"mode":"SNAPSHOT"}'
    wrapper.vm.expectationJson = edited
    wrapper.vm.expectedSnapshotJson = '{"result":"user typed while loading"}'
    wrapper.vm.$message = { warning: vi.fn() }
    await wrapper.vm.handleExecute(); await wrapper.vm.saveCurrentTestCase('UPDATE')
    expect(definitionApi.executeApiScenarioValidated).not.toHaveBeenCalled()
    expect(definitionApi.updateApiScenario).not.toHaveBeenCalled()
    finishSchema({ data: { inputs: [{ scriptName: 'age', valueType: 'NUMBER' }], outputs: [], sampleParams: { age: 0 } } })
    await loading
    expect(wrapper.vm.expectationJson).toBe(edited)
    expect(wrapper.vm.expectedSnapshotJson).toContain('user typed')
    expect(wrapper.vm.loadedTestCase.id).toBe(71)
    expect(wrapper.vm.testCaseLoading).toBe(false)
  })

  test('执行期间修改预期保留本次请求快照并把结果标记为待重新校验', async () => {
    wrapper.vm.selectedRuleId = 4
    let resolveExecution
    const config = wrapper.vm.expectationJson
    definitionApi.executeApiScenarioValidated.mockReturnValueOnce(new Promise(resolve => { resolveExecution = resolve }))
    const executing = wrapper.vm.handleExecute()
    wrapper.vm.expectationJson = '{"version":1,"mode":"FIELDS","checks":[]}'
    resolveExecution(validatedResult({ data: { success: true, result: 5 } }))
    await executing
    expect(definitionApi.executeApiScenarioValidated.mock.lastCall[1].expectationJson).toBe(config)
    expect(wrapper.vm.validationStale).toBe(true)
    expect(wrapper.vm.expectationJson).toContain('FIELDS')
  })

  test('初次加载优先使用Schema精确样例原文，不采用已舍入的兼容字段', async () => {
    wrapper.vm.selectedRuleId = 4; wrapper.vm.selectedRule = mockRules()[1]
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: {
      inputs: [{ refId: '9007199254740993', refType: 'VARIABLE', scriptName: 'amount', valueType: 'NUMBER' }],
      outputs: [{ refId: '9007199254740993', refType: 'VARIABLE', scriptName: 'result', valueType: 'NUMBER' }],
      sampleParams: { amount: 9007199254740992 }, sampleParamsJson: '{"amount":9007199254740993}',
    } })
    await wrapper.vm.loadVariables()
    expect(wrapper.vm.params[0].value).toBe('9007199254740993')
    expect(wrapper.vm.buildRequestJson()).toContain('9007199254740993')
    expect(wrapper.vm.testOutputFields[0].refId).toBe('9007199254740993')
  })

  test('Schema样例中同名原型字段作为普通业务输入保留', async () => {
    wrapper.vm.selectedRuleId = 4; wrapper.vm.selectedRule = mockRules()[1]
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: {
      inputs: [{ refId: '19', refType: 'VARIABLE', scriptName: '__proto__', valueType: 'STRING' }],
      outputs: [], sampleParams: {}, sampleParamsJson: '{"__proto__":"business-data"}',
    } })
    await wrapper.vm.loadVariables()
    expect(wrapper.vm.params[0].value).toBe('business-data')
    expect(wrapper.vm.buildRequestJson()).toContain('"__proto__":"business-data"')
  })

  test('兼容接口已舍入的JS大整数不能被重新包装成精确数字后执行', async () => {
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.params = [{ key: 'amount', value: JSON.parse('9007199254740993'), type: 'NUMBER' }]
    wrapper.vm.$message = { warning: vi.fn() }
    await wrapper.vm.handleExecute()
    expect(definitionApi.executeApiScenarioValidated).not.toHaveBeenCalled()
    expect(wrapper.vm.$message.warning).toHaveBeenCalledWith(expect.stringContaining('原始 JSON'))
  })

  test('配置编辑器提示无效预期时阻止执行和保存，不沿用旧配置', async () => {
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedRule = mockRules()[1]
    await nextTick()
    wrapper.vm.$refs.expectationEditor.validate = vi.fn(() => ({ valid: false, errors: ['请填写区间上限'] }))
    wrapper.vm.$message = { warning: vi.fn() }
    await wrapper.vm.handleExecute()
    await wrapper.vm.saveCurrentTestCase()
    expect(definitionApi.executeApiScenarioValidated).not.toHaveBeenCalled()
    expect(definitionApi.createApiScenario).not.toHaveBeenCalled()
    expect(wrapper.vm.$message.warning).toHaveBeenCalledWith('请填写区间上限')
  })

  test('mounted 后调用 listProjects', () => {
    expect(projectApi.listProjects).toHaveBeenCalled()
  })

  test('mounted 后调用 listDefinitions', () => {
    expect(definitionApi.listDefinitions).toHaveBeenCalled()
  })

  test('rules 列表正确赋值', () => {
    expect(wrapper.vm.rules).toBeInstanceOf(Array)
    expect(wrapper.vm.rules.length).toBe(2)
  })

  test('项目上下文直接进入项目规则测试范围', async () => {
    definitionApi.listProjectDefinitions.mockResolvedValueOnce({
      data: { records: mockRules() }
    })

    await wrapper.vm.applyProjectContext(1)

    expect(wrapper.vm.ruleScope).toBe('PROJECT')
    expect(wrapper.vm.selectedProjectId).toBe(1)
    expect(definitionApi.listProjectDefinitions).toHaveBeenCalledWith(1, {
      pageNum: 1,
      pageSize: 1000
    })
  })

  test('项目上下文切换全局规则时保留项目并只加载已绑定规则', async () => {
    definitionApi.listProjectDefinitions.mockResolvedValue({
      data: { records: mockRules() }
    })
    await wrapper.vm.applyProjectContext(1)
    definitionApi.listProjectDefinitions.mockClear()
    wrapper.vm.ruleScope = 'GLOBAL'

    await wrapper.vm.onScopeChange()

    expect(wrapper.vm.contextProjectId).toBe(1)
    expect(wrapper.vm.selectedProjectId).toBe(1)
    expect(definitionApi.listProjectDefinitions).toHaveBeenCalledWith(1, {
      pageNum: 1,
      pageSize: 1000,
      scope: 'GLOBAL'
    })
    expect(definitionApi.listDefinitions).not.toHaveBeenCalledWith(
      expect.objectContaining({ scope: 'GLOBAL' })
    )
  })

  test('result 初始为 null（注意：字段名是 result 不是 testResult）', () => {
    expect(wrapper.vm.result).toBeNull()
  })

  test('规则列表加载失败时保留原因和重试状态', async () => {
    definitionApi.listDefinitions.mockRejectedValueOnce(
      new Error('规则服务暂时不可用')
    )
    wrapper.vm.ruleScope = 'ALL'

    await wrapper.vm.loadRulesByScope()

    expect(wrapper.vm.rules).toEqual([])
    expect(wrapper.vm.ruleListError).toContain('规则服务暂时不可用')
    expect(wrapper.vm.ruleListLoading).toBe(false)
  })

  test('快速切换项目时只采用最后一次规则列表响应', async () => {
    let resolveFirst
    let resolveSecond
    definitionApi.listProjectDefinitions
      .mockReturnValueOnce(new Promise(resolve => { resolveFirst = resolve }))
      .mockReturnValueOnce(new Promise(resolve => { resolveSecond = resolve }))

    wrapper.vm.selectedProjectId = 1
    const first = wrapper.vm.onProjectChange()
    wrapper.vm.selectedProjectId = 2
    const second = wrapper.vm.onProjectChange()
    resolveSecond({ data: { records: [{ id: 22, ruleName: '项目二规则' }] } })
    await second
    resolveFirst({ data: { records: [{ id: 11, ruleName: '项目一规则' }] } })
    await first

    expect(wrapper.vm.rules).toEqual([{ id: 22, ruleName: '项目二规则' }])
    expect(wrapper.vm.ruleListLoading).toBe(false)
  })

  test('项目列表重试时旧请求不能覆盖最新结果', async () => {
    let resolveFirst
    let resolveSecond
    projectApi.listProjects
      .mockReturnValueOnce(new Promise(resolve => { resolveFirst = resolve }))
      .mockReturnValueOnce(new Promise(resolve => { resolveSecond = resolve }))

    const first = wrapper.vm.loadProjects()
    const second = wrapper.vm.loadProjects()
    resolveSecond({ data: { records: [{ id: 2, projectName: '最新项目' }] } })
    await second
    resolveFirst({ data: { records: [{ id: 1, projectName: '旧项目' }] } })
    await first

    expect(wrapper.vm.projects).toEqual([{ id: 2, projectName: '最新项目' }])
    expect(wrapper.vm.projectsLoading).toBe(false)
  })
})

describe('RuleTest — 辅助方法', () => {
  let wrapper

  beforeEach(async () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: mockProjects() } })
    definitionApi.listDefinitions.mockResolvedValueOnce({ data: { records: mockRules() } })
    variableApi.listVariables.mockResolvedValueOnce({ data: { records: mockVariables() } })

    wrapper = shallowMount(RuleTest, {
      mocks: {
        $route: { params: {} },
        $router: { push: vi.fn(), replace: vi.fn() }
      },
      stubs: {
        'el-form': makeStub('form'), 'el-form-item': makeStub('div'),
        'el-select': makeStub('select'), 'el-option': makeStub('option'),
        'el-input': makeStub('input'), 'el-input-number': makeStub('input'),
        'el-button': makeStub('button'), 'el-tag': makeStub('span'),
        'el-table': makeStub('table'), 'el-table-column': makeStub('td'),
        'el-tabs': makeStub('div'), 'el-tab-pane': makeStub('div'),
        'el-dialog': makeStub('div'), 'el-card': makeStub('div'),
        'el-alert': makeStub('div'), 'el-divider': makeStub('hr'),
        'el-loading': makeStub('div'), 'el-descriptions': makeStub('div'),
        'el-descriptions-item': makeStub('div'),
        'el-radio-group': makeStub('div'), 'el-radio-button': makeStub('div'),
        'el-collapse': makeStub('div'), 'el-collapse-item': makeStub('div'),
        'monaco-editor': makeStub('div'),
        'trace-tree': makeStub('div'), 'var-picker': makeStub('div'),
        'script-panel': makeStub('div')
      }
    })

    await nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('mtl 返回正确的模型类型标签（注意：方法名是 mtl 不是 modelTypeLabel）', () => {
    expect(wrapper.vm.mtl('TABLE')).toBe('决策表')
    expect(wrapper.vm.mtl('TREE')).toBe('决策树')
    expect(wrapper.vm.mtl('FLOW')).toBe('决策流')
    expect(wrapper.vm.mtl('RULE_SET')).toBe('规则集')
    expect(wrapper.vm.mtl('SCORE')).toBe('评分卡')
    expect(wrapper.vm.mtl('CROSS')).toBe('交叉表')
    expect(wrapper.vm.mtl('SCRIPT')).toBe('QL脚本')
  })

  test('全部范围的项目规则标签包含项目名称和项目编码', () => {
    expect(wrapper.vm.ruleOptionLabel({
      id: 8,
      scope: 'PROJECT',
      projectId: 1,
      projectName: '响应项目',
      projectCode: 'RESPONSE_PROJECT',
      ruleName: '同名规则',
      ruleCode: 'SAME_RULE'
    })).toBe('[响应项目 / RESPONSE_PROJECT] 同名规则 (SAME_RULE)')

    expect(wrapper.vm.ruleOptionLabel(mockRules()[0]))
      .toBe('[综合风控示例项目 / RISK_DEMO] 客商×产品总线定价表 (RC_PRICING_TABLE)')
  })

  test('规则标签省略缺失片段且全局规则保持明确标识', () => {
    expect(wrapper.vm.ruleOptionLabel({
      scope: 'GLOBAL', ruleName: '全局规则', ruleCode: 'GLOBAL_RULE'
    })).toBe('[全局] 全局规则 (GLOBAL_RULE)')
    expect(wrapper.vm.ruleOptionLabel({
      scope: 'PROJECT', projectName: '仅名称', ruleName: '项目规则'
    })).toBe('[仅名称] 项目规则')
    expect(wrapper.vm.ruleOptionLabel({ scope: 'PROJECT', ruleName: '无项目元数据' }))
      .toBe('无项目元数据')
  })

  test('表单和 JSON 双向切换保留精度、对象、数组及额外字段', () => {
    wrapper.vm.params = [{ key: 'customer.age', value: '18', type: 'INTEGER', fromVar: true }]
    wrapper.vm.switchInputMode('json')
    expect(JSON.parse(wrapper.vm.paramsJson)).toEqual({ customer: { age: 18 } })
    wrapper.vm.paramsJson = '{"customer":{"age":21},"id":9007199254740993,"amount":0.1234567890123456789,"flag":false,"items":[1,null],"empty":{},"nil":null,"a.b":"原样字段"}'
    const expected = wrapper.vm.paramsJson
    wrapper.vm.switchInputMode('form')
    expect(wrapper.vm.inputMode).toBe('form')
    expect(stringifyExactJson(wrapper.vm.buildParamMap())).toBe(expected)
    expect(wrapper.vm.params.find(param => param.key === 'customer.age').fromVar).toBe(true)
    wrapper.vm.switchInputMode('json')
    expect(stringifyExactJson(wrapper.vm.buildParamMap())).toBe(expected)
  })

  test.each(['{"age":', '[]', 'null', '18', '"text"'])('无效 JSON %s 不切换表单、不执行且保留原文', async (json) => {
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.inputMode = 'json'
    wrapper.vm.paramsJson = json
    wrapper.vm.$message = { warning: vi.fn() }
    wrapper.vm.switchInputMode('form')
    expect(wrapper.vm.inputMode).toBe('json')
    expect(wrapper.vm.paramsJson).toBe(json)
    await wrapper.vm.handleExecute()
    expect(definitionApi.executeApiScenarioValidated).not.toHaveBeenCalled()
    expect(wrapper.vm.$message.warning).toHaveBeenCalled()
  })

  test('生成 JSON 样例复用统一 Schema，按选中规则生成且保留精确数字', async () => {
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.selectedRule = mockRules()[0]
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: {
      inputs: [{ scriptName: 'customer.age', label: '年龄', valueType: 'INTEGER' }],
      sampleParamsJson: '{"customer":{"age":18},"id":9007199254740993}',
    } })
    await wrapper.vm.loadVariables()
    wrapper.vm.params[0].value = '99'
    wrapper.vm.generateInputSample()
    expect(stringifyExactJson(wrapper.vm.buildParamMap())).toBe('{"customer":{"age":18},"id":9007199254740993}')
    wrapper.vm.switchInputMode('json')
    expect(JSON.parse(wrapper.vm.paramsJson).customer.age).toBe(18)
    wrapper.vm.handleClear()
    expect(wrapper.vm.paramsJson).toBe('{}')
    expect(wrapper.vm.params).toEqual([])
  })

  test('JSON 模式执行直接提交编辑后的原始数字并绑定追踪快照', async () => {
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.inputMode = 'json'
    wrapper.vm.paramsJson = '{"id":9007199254740993,"flag":false}'
    definitionApi.executeApiScenarioValidated.mockResolvedValueOnce(validatedResult({ data: mockExecutionResult() }))
    await wrapper.vm.handleExecute()
    expect(definitionApi.executeApiScenarioValidated.mock.lastCall[1].requestJson).toContain('"params":{"id":9007199254740993,"flag":false}')
    wrapper.vm.paramsJson = '{}'
    expect(wrapper.vm.inputParamsJson).toBe('{"id":9007199254740993,"flag":false}')
    expect(wrapper.vm.validationStale).toBe(true)
  })

  test('JSON 模式重新加载字段保留编辑值，加载期间新输入不会被旧响应覆盖', async () => {
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.selectedRule = mockRules()[0]
    wrapper.vm.inputMode = 'json'
    wrapper.vm.paramsJson = '{"age":21,"extra":false}'
    const schema = { data: { inputs: [{ scriptName: 'age', label: '年龄', valueType: 'INTEGER' }], sampleParams: { age: 18 } } }
    definitionApi.getRuleTestSchema.mockResolvedValueOnce(schema)
    await wrapper.vm.loadVariables()
    expect(JSON.parse(wrapper.vm.paramsJson)).toEqual({ age: 21, extra: false })
    let resolveSchema
    definitionApi.getRuleTestSchema.mockReturnValueOnce(new Promise(resolve => { resolveSchema = resolve }))
    const loading = wrapper.vm.loadVariables()
    wrapper.vm.paramsJson = '{"age":27,"editedDuringLoad":true}'
    resolveSchema(schema)
    await loading
    expect(wrapper.vm.paramsJson).toBe('{"age":27,"editedDuringLoad":true}')
  })

  test('JSON 模式载入收藏会完整替换输入并清除旧的无效 JSON', async () => {
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.selectedRule = mockRules()[0]
    wrapper.vm.inputMode = 'json'
    wrapper.vm.paramsJson = '{invalid'
    definitionApi.getRuleTestSchema.mockResolvedValueOnce(mockCrossTableSchema())
    await wrapper.vm.applyTestCase({ requestJson: '{"params":{"id":9007199254740993,"empty":{}}}' })
    expect(wrapper.vm.inputJsonError).toBe('')
    expect(stringifyExactJson(wrapper.vm.buildParamMap())).toBe('{"id":9007199254740993,"empty":{}}')
    expect(wrapper.vm.params.map(param => param.key)).toEqual(['id', 'empty'])
  })

  test('输入参数位于固定测试用例之前且空用例卡片紧凑', async () => {
    wrapper.vm.selectedRule = mockRules()[0]
    wrapper.vm.testCases = []
    wrapper.vm.testCasesLoading = false
    wrapper.vm.testCasesError = ''
    await nextTick()

    const input = wrapper.find('.input-parameters-card')
    const fixed = wrapper.find('.fixed-cases-card')
    const html = wrapper.find('.test-left').html()

    expect(input.exists()).toBe(true)
    expect(fixed.exists()).toBe(true)
    expect(html.indexOf('input-parameters-card'))
      .toBeLessThan(html.indexOf('fixed-cases-card'))
    expect(fixed.classes()).toContain('is-empty')
  })

  test('mtl 对未知类型返回原值', () => {
    expect(wrapper.vm.mtl('UNKNOWN')).toBe('UNKNOWN')
  })

  test('formatJson 格式化对象', () => {
    const obj = { taxRate: 0.03 }
    const formatted = wrapper.vm.formatJson(obj)
    expect(formatted).toContain('taxRate')
    expect(formatted).toContain('0.03')
  })

  test('formatJson 处理字符串 JSON', () => {
    const jsonStr = '{"taxRate":0.03}'
    const formatted = wrapper.vm.formatJson(jsonStr)
    expect(formatted).toContain('taxRate')
  })

  test('formatJson 处理 null', () => {
    expect(wrapper.vm.formatJson(null)).toBe('(空)')
  })

  test('buildParamMap 将点号路径组装为嵌套数据对象入参', () => {
    wrapper.vm.params = [
      { key: 'request.definitionId', value: '1', type: 'NUMBER' },
      { key: 'request.params.taxpayerType', value: '一般纳税人', type: 'STRING' },
      { key: 'request.params.goodsCategory', value: '货物', type: 'STRING' },
      { key: 'mockModelScore', value: '88', type: 'NUMBER' }
    ]

    expect(JSON.parse(stringifyExactJson(wrapper.vm.buildParamMap()))).toEqual({
      request: {
        definitionId: 1,
        params: {
          taxpayerType: '一般纳税人',
          goodsCategory: '货物'
        }
      },
      mockModelScore: 88
    })
  })

  test('applyRiskDemoParams 填充完整综合风控演示样例', () => {
    wrapper.vm.result = { success: true }

    wrapper.vm.applyRiskDemoParams()

    const keys = wrapper.vm.params.map(p => p.key)
    expect(keys).toContain('taxpayerType')
    expect(keys).toContain('yearsInBusiness')
    expect(keys).toContain('taxBurdenDeviation')
    expect(keys).toContain('serviceType')
    expect(keys).toContain('taxpayerQualification')
    expect(keys).toContain('billingAmount')
    expect(wrapper.vm.result).toBeNull()
    expect(JSON.parse(stringifyExactJson(wrapper.vm.buildParamMap()))).toMatchObject({
      taxpayerType: '一般纳税人',
      goodsCategory: '货物',
      totalAmount: 113000,
      isExempt: false,
      yearsInBusiness: 10,
      serviceType: 'ICT服务',
      taxpayerQualification: '一般纳税人'
    })
  })

  test('applyInputFieldsToParams 根据模型输出字段类型转换模型引用入参', async () => {
    modelApi.getModel.mockResolvedValueOnce({
      data: {
        id: 2,
        modelCode: 's',
        outputFields: [
          { fieldName: 'score', fieldType: 'DOUBLE' }
        ]
      }
    })

    await wrapper.vm.applyInputFieldsToParams([
      { scriptName: 's', fieldLabel: 'ss', fieldType: 'MODEL', refType: 'MODEL', varId: 2 }
    ])
    wrapper.vm.params[0].value = '1'

    expect(modelApi.getModel).toHaveBeenCalledWith(2)
    expect(wrapper.vm.params[0].type).toBe('DOUBLE')
    expect(JSON.parse(stringifyExactJson(wrapper.vm.buildParamMap()))).toEqual({ s: 1 })
  })
})

describe('RuleTest — 加载变量（loadVariables）', () => {
  let wrapper

  beforeEach(async () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: mockProjects() } })
    definitionApi.listDefinitions.mockResolvedValueOnce({ data: { records: mockRules() } })
    variableApi.listVariables.mockResolvedValueOnce({ data: { records: mockVariables() } })

    wrapper = shallowMount(RuleTest, {
      mocks: {
        $route: { params: {} },
        $router: { push: vi.fn(), replace: vi.fn() }
      },
      stubs: {
        'el-form': makeStub('form'), 'el-form-item': makeStub('div'),
        'el-select': makeStub('select'), 'el-option': makeStub('option'),
        'el-input': makeStub('input'), 'el-input-number': makeStub('input'),
        'el-button': makeStub('button'), 'el-tag': makeStub('span'),
        'el-table': makeStub('table'), 'el-table-column': makeStub('td'),
        'el-tabs': makeStub('div'), 'el-tab-pane': makeStub('div'),
        'el-dialog': makeStub('div'), 'el-card': makeStub('div'),
        'el-alert': makeStub('div'), 'el-divider': makeStub('hr'),
        'el-loading': makeStub('div'), 'el-descriptions': makeStub('div'),
        'el-descriptions-item': makeStub('div'),
        'el-radio-group': makeStub('div'), 'el-radio-button': makeStub('div'),
        'el-collapse': makeStub('div'), 'el-collapse-item': makeStub('div'),
        'monaco-editor': makeStub('div'),
        'trace-tree': makeStub('div'), 'var-picker': makeStub('div'),
        'script-panel': makeStub('div')
      }
    })

    await nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('旧交叉表只有 varCode 时拒绝回退匹配变量', async () => {
    // 选中"风险定价交叉表"（id=4，模型类型 CROSS）
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedRule = mockRules().find(r => r.id === 4)

    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: { inputs: [], sampleParams: {} } })
    definitionApi.listInputFields.mockResolvedValueOnce({ data: [] })

    await wrapper.vm.loadVariables()
    await nextTick()

    expect(wrapper.vm.params).toEqual([])
    expect(definitionApi.refreshFields).not.toHaveBeenCalled()
    expect(variableApi.listVariablesByProject).not.toHaveBeenCalled()
  })

  test('决策表规则：通过服务端 ID + ref_type 测试结构加载变量', async () => {
    // 选中"客商×产品总线定价表"（id=1，模型类型 TABLE）
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.selectedRule = mockRules().find(r => r.id === 1)

    definitionApi.getRuleTestSchema.mockResolvedValueOnce(mockCrossTableSchema())

    await wrapper.vm.loadVariables()
    await nextTick()

    // 服务端结构已经通过 ID + ref_type 完成解析。
    expect(wrapper.vm.params.length).toBe(2)
    const varCodes = wrapper.vm.params.map(p => p.key).sort()
    expect(varCodes).toEqual(['goodsCategory', 'taxpayerType'])
  })

  test('规则集规则：从条件树和动作块提取变量', async () => {
    wrapper.vm.selectedRuleId = 9
    wrapper.vm.selectedRule = { id: 9, ruleCode: 'RS_RISK', ruleName: '风险规则集', modelType: 'RULE_SET', projectId: 1 }

    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: {
      inputs: [{ refId: 1, refType: 'VARIABLE', scriptName: 'taxpayerType', label: '客商类型', valueType: 'STRING' }],
      sampleParams: { taxpayerType: '' }
    } })

    await wrapper.vm.loadVariables()
    await nextTick()

    const varCodes = wrapper.vm.params.map(p => p.key).sort()
    expect(varCodes).toEqual(['taxpayerType'])
  })

  test('统一测试结构直接生成嵌套规则参数', async () => {
    wrapper.vm.selectedRuleId = 7
    wrapper.vm.selectedRule = { id: 7, ruleCode: 'JCL_TEST', modelType: 'FLOW', projectId: 1 }
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: {
      inputs: [{ refId: 25, refType: 'DATA_OBJECT', scriptName: 'score_f1_fields.HYBASE_X115', label: '评分字段/HYBASE_X115', valueType: 'DOUBLE' }],
      sampleParams: { score_f1_fields: { HYBASE_X115: 0 } }
    } })

    await wrapper.vm.loadVariables()

    expect(definitionApi.getRuleTestSchema).toHaveBeenCalledWith({ targetType: 'RULE', targetId: 7 })
    expect(wrapper.vm.params).toEqual([expect.objectContaining({ key: 'score_f1_fields.HYBASE_X115', value: 0, type: 'DOUBLE' })])
    expect(JSON.parse(stringifyExactJson(wrapper.vm.buildParamMap()))).toEqual({ score_f1_fields: { HYBASE_X115: 0 } })
  })

  test('未选择规则时 loadVariables 不调用 API', async () => {
    wrapper.vm.selectedRule = null
    await wrapper.vm.loadVariables()
    expect(definitionApi.getContent).not.toHaveBeenCalled()
  })

  test('统一测试结构失败后回退到规则输入字段并显示降级说明', async () => {
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.selectedRule = mockRules()[0]
    definitionApi.getRuleTestSchema.mockRejectedValueOnce(
      new Error('测试结构服务不可用')
    )
    definitionApi.listInputFields.mockResolvedValueOnce({ data: [{
      varId: 1,
      refType: 'VARIABLE',
      scriptName: 'taxpayerType',
      fieldName: 'taxpayerType',
      fieldLabel: '客商类型',
      fieldType: 'STRING'
    }] })

    await wrapper.vm.loadVariables()

    expect(wrapper.vm.inputLoadState).toBe('READY')
    expect(wrapper.vm.params).toEqual([
      expect.objectContaining({ key: 'taxpayerType' })
    ])
    expect(wrapper.vm.inputLoadWarnings.join(' '))
      .toContain('测试结构服务不可用')
  })

  test('测试结构与回退字段都失败时显示可重试错误而非正常空状态', async () => {
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.selectedRule = mockRules()[0]
    definitionApi.getRuleTestSchema.mockRejectedValueOnce(
      new Error('测试结构加载失败')
    )
    definitionApi.listInputFields.mockRejectedValueOnce(
      new Error('规则字段加载失败')
    )

    await wrapper.vm.loadVariables()

    expect(wrapper.vm.inputLoadState).toBe('ERROR')
    expect(wrapper.vm.inputLoadError).toContain('规则字段加载失败')
    expect(wrapper.vm.params).toEqual([])
  })

  test('重新加载测试结构时保留用户已填写的同路径参数值', async () => {
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.selectedRule = mockRules()[0]
    wrapper.vm.params = [{
      key: 'taxpayerType', label: '客商类型', value: '用户已填写',
      type: 'STRING', fromVar: true, options: []
    }]
    definitionApi.getRuleTestSchema.mockResolvedValueOnce(mockCrossTableSchema())

    await wrapper.vm.loadVariables()

    expect(wrapper.vm.params.find(param => param.key === 'taxpayerType').value)
      .toBe('用户已填写')
    expect(wrapper.vm.inputLoadState).toBe('READY')
  })

  test('快速切换规则时旧测试结构响应不能覆盖新规则参数', async () => {
    let resolveFirst
    let resolveSecond
    definitionApi.getRuleTestSchema
      .mockReturnValueOnce(new Promise(resolve => { resolveFirst = resolve }))
      .mockReturnValueOnce(new Promise(resolve => { resolveSecond = resolve }))

    wrapper.vm.selectedRuleId = 1
    wrapper.vm.selectedRule = mockRules()[0]
    const first = wrapper.vm.loadVariables()
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedRule = mockRules()[1]
    const second = wrapper.vm.loadVariables()
    resolveSecond({ data: {
      inputs: [{ refId: 2, refType: 'VARIABLE', scriptName: 'newRuleField', label: '新规则字段', valueType: 'STRING' }],
      sampleParams: { newRuleField: 'new' }
    } })
    await second
    resolveFirst(mockCrossTableSchema())
    await first

    expect(wrapper.vm.params).toEqual([
      expect.objectContaining({ key: 'newRuleField', value: 'new' })
    ])
  })

  test('测试结构诊断作为非阻断提示保留', async () => {
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.selectedRule = mockRules()[0]
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: {
      ...mockCrossTableSchema().data,
      diagnostics: ['动态脚本引用需要手工补充参数']
    } })

    await wrapper.vm.loadVariables()

    expect(wrapper.vm.inputLoadState).toBe('READY')
    expect(wrapper.vm.inputLoadWarnings).toContain(
      '动态脚本引用需要手工补充参数'
    )
  })

  test('枚举选项加载失败不阻断回退字段并给出提示', async () => {
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.selectedRule = mockRules()[0]
    definitionApi.getRuleTestSchema.mockRejectedValueOnce(
      new Error('测试结构服务不可用')
    )
    definitionApi.listInputFields.mockResolvedValueOnce({ data: [{
      varId: 1, refType: 'VARIABLE', scriptName: 'customerLevel',
      fieldName: 'customerLevel', fieldLabel: '客户等级', fieldType: 'ENUM'
    }] })
    variableApi.getVariableOptions.mockRejectedValueOnce(
      new Error('枚举服务不可用')
    )

    await wrapper.vm.loadVariables()

    expect(wrapper.vm.inputLoadState).toBe('READY')
    expect(wrapper.vm.params[0]).toMatchObject({
      key: 'customerLevel', type: 'ENUM', options: []
    })
    expect(wrapper.vm.inputLoadWarnings.join(' ')).toContain('枚举选项')
  })
})

describe('RuleTest — 执行与结果展示', () => {
  let wrapper

  beforeEach(async () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: mockProjects() } })
    definitionApi.listDefinitions.mockResolvedValueOnce({ data: { records: mockRules() } })
    variableApi.listVariables.mockResolvedValueOnce({ data: { records: mockVariables() } })

    wrapper = shallowMount(RuleTest, {
      mocks: {
        $route: { params: {} },
        $router: { push: vi.fn(), replace: vi.fn() }
      },
      stubs: {
        'el-form': makeStub('form'), 'el-form-item': makeStub('div'),
        'el-select': makeStub('select'), 'el-option': makeStub('option'),
        'el-input': makeStub('input'), 'el-input-number': makeStub('input'),
        'el-button': makeStub('button'), 'el-tag': makeStub('span'),
        'el-table': makeStub('table'), 'el-table-column': makeStub('td'),
        'el-tabs': makeStub('div'), 'el-tab-pane': makeStub('div'),
        'el-dialog': makeStub('div'), 'el-card': makeStub('div'),
        'el-alert': makeStub('div'), 'el-divider': makeStub('hr'),
        'el-loading': makeStub('div'), 'el-descriptions': makeStub('div'),
        'el-descriptions-item': makeStub('div'),
        'el-radio-group': makeStub('div'), 'el-radio-button': makeStub('div'),
        'el-collapse': makeStub('div'), 'el-collapse-item': makeStub('div'),
        'monaco-editor': makeStub('div'),
        'trace-tree': makeStub('div'), 'var-picker': makeStub('div'),
        'script-panel': makeStub('div')
      }
    })

    await nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('执行结果包含独立滚动布局所需的高度链节点', async () => {
    wrapper.vm.result = mockExecutionResult()
    await nextTick()

    expect(wrapper.find('.rule-test-page').exists()).toBe(true)
    expect(wrapper.find('.test-result-card').exists()).toBe(true)
    expect(wrapper.find('.test-result-content').exists()).toBe(true)
    expect(wrapper.find('.trace-section').exists()).toBe(true)
    expect(wrapper.find('.trace-tabs').exists()).toBe(true)
  })

  test('窄屏以同等选择器优先级解除追踪面板滚动', () => {
    const source = fs.readFileSync(path.resolve(__dirname, '../../../src/views/test/RuleTest.vue'), 'utf8')
    const narrowStyles = source.slice(source.indexOf('@media screen and (max-width: 1000px)'))

    expect(narrowStyles).toMatch(/\.trace-tabs :deep\(#pane-json\),\s*\.trace-tabs :deep\(#pane-tree\)\s*\{[^}]*height: auto;[^}]*overflow: visible;/)
  })

  test('固定测试用例复选框使用 Element Plus value API', () => {
    const source = fs.readFileSync(path.resolve(__dirname, '../../../src/views/test/RuleTest.vue'), 'utf8')

    expect(source).toContain('<el-checkbox :value="testCase.id">')
    expect(source).not.toContain('<el-checkbox :label="testCase.id">')
  })

  test('handleExecute 成功时设置 result', async () => {
    definitionApi.executeApiScenarioValidated.mockResolvedValueOnce(validatedResult({ data: mockExecutionResult() }))
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.params = [
      { key: 'taxpayerType', value: '小规模纳税人', type: 'STRING' },
      { key: 'goodsCategory', value: '服务', type: 'STRING' }
    ]
    wrapper.vm.$message = { success: vi.fn(), error: vi.fn() }

    await wrapper.vm.handleExecute()
    await nextTick()

    expect(wrapper.vm.result).not.toBeNull()
    expect(wrapper.vm.result.success).toBe(true)
    expect(wrapper.vm.result.result.taxRate).toBe(0.03)
    expect(definitionApi.executeApiScenarioValidated).toHaveBeenCalledWith(4, expect.objectContaining({
      requestJson: '{"clientAppName":"rule-test","params":{"taxpayerType":"小规模纳税人","goodsCategory":"服务"}}',
      expectationJson: '{"version":1,"mode":"NONE","checks":[]}'
    }), 180000)
  })

  test('明确的THROTTLED结果使用容量提示与warning，普通失败仍显示错误', async () => {
    wrapper.vm.result = { success: false, executionStatus: 'THROTTLED', errorMessage: '模型暂时无法受理', executeTimeMs: 0 }
    await nextTick()
    let alert = wrapper.find('.test-result-content [title]')
    expect(alert.attributes('title')).toBe('容量受限')
    expect(alert.attributes('type')).toBe('warning')
    expect(wrapper.find('.result-section-title.is-throttled').text()).toBe('容量提示')
    expect(wrapper.find('.result-pre.is-throttled').text()).toBe('模型暂时无法受理')

    wrapper.vm.result = { success: false, errorMessage: 'THROTTLED 容量不足', executeTimeMs: 0 }
    await nextTick()
    alert = wrapper.find('.test-result-content [title]')
    expect(alert.attributes('title')).toBe('执行失败')
    expect(alert.attributes('type')).toBe('error')
    expect(wrapper.find('.result-pre.is-error').text()).toBe('THROTTLED 容量不足')
    expect(wrapper.find('.result-pre.is-throttled').exists()).toBe(false)
  })

  test('容量受限筛选入口保留匹配子规则和祖先', async () => {
    const page = shallowMount(RuleTest, { mocks: {
      $route: { params: {} }, $router: { push: vi.fn(), replace: vi.fn() }
    } })
    try {
      const child = { traceId: 'child', status: 'THROTTLED', children: [] }
      page.vm.result = { success: false, executionStatus: 'THROTTLED', traces: [{
        traceId: 'root', status: 'FAILED', children: [child, { traceId: 'ordinary', status: 'FAILED', children: [] }]
      }] }
      await nextTick()
      expect(page.find('.trace-filter-bar el-option-stub[value="THROTTLED"]').attributes('label')).toBe('容量受限')
      page.findComponent('.trace-filter-bar el-select-stub').vm.$emit('update:modelValue', 'THROTTLED')
      await nextTick()
      expect(JSON.parse(page.vm.traceInfoJson)).toEqual({ traceId: 'root', status: 'FAILED', children: [child] })
    } finally {
      page.unmount()
    }
  })

  test('真实Alert默认slot同时展示容量重试提示和耗时', async () => {
    const page = shallowMount(RuleTest, {
      mocks: { $route: { params: {} }, $router: { push: vi.fn(), replace: vi.fn() } },
      stubs: { 'el-alert': ElAlert },
    })
    try {
      page.vm.result = { success: false, executionStatus: 'THROTTLED', executeTimeMs: 12 }
      await nextTick()
      const alert = page.find('.el-alert--warning')
      expect(alert.text()).toContain('执行资源暂时不足，本次未完成决策，请稍后重试。')
      expect(alert.text()).toContain('耗时 12 ms')
    } finally {
      page.unmount()
    }
  })

  test('批量用例保留明确的容量状态供结果标签区分，普通失败不补容量状态', async () => {
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.testCases = [
      { id: 1, scenarioName: '资源不足', requestJson: '{}', responseJson: '{}' },
      { id: 2, scenarioName: '普通错误', requestJson: '{}', responseJson: '{}' },
    ]
    wrapper.vm.selectedTestCaseIds = [1, 2]
    definitionApi.executeApiScenarioValidated
      .mockResolvedValueOnce(validatedResult({ data: { success: false, executionStatus: 'THROTTLED' } }))
      .mockResolvedValueOnce(validatedResult({ data: { success: false, errorMessage: 'THROTTLED' } }))
    await wrapper.vm.executeSelectedTestCases()
    expect(wrapper.vm.batchResults[0]).toMatchObject({ success: false, executionStatus: 'THROTTLED' })
    expect(wrapper.vm.batchResults[1].executionStatus).not.toBe('THROTTLED')
  })

  test('追踪入参固定为执行时快照，不随结果页编辑变化', async () => {
    definitionApi.executeApiScenarioValidated.mockResolvedValueOnce(validatedResult({ data: mockExecutionResult() }))
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.params = [{ key: 'age', value: '18', type: 'INTEGER' }]

    await wrapper.vm.handleExecute()
    wrapper.vm.params[0].value = '99'

    expect(JSON.parse(wrapper.vm.inputParamsJson)).toEqual({ age: 18 })
  })

  test('project-scoped execution sends the effective project ID', async () => {
    definitionApi.executeApiScenarioValidated.mockResolvedValueOnce(validatedResult({ data: mockExecutionResult() }))
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedProjectId = 7

    await wrapper.vm.handleExecute()

    expect(definitionApi.executeApiScenarioValidated).toHaveBeenCalledWith(4, expect.objectContaining({
      projectId: 7,
      requestJson: '{"clientAppName":"rule-test","params":{}}'
    }), 180000)
  })

  test('handleExecute 保留 false 输出', async () => {
    definitionApi.executeApiScenarioValidated.mockResolvedValueOnce(validatedResult({ data: { success: true, result: false } }))
    wrapper.vm.selectedRuleId = 4
    await wrapper.vm.handleExecute()
    expect(wrapper.vm.result).toMatchObject({ hasOutput: true, output: false })
  })

  test('handleExecute 失败时设置错误信息到 result', async () => {
    definitionApi.executeApiScenarioValidated.mockRejectedValueOnce(new Error('规则编译失败'))
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.params = [{ key: 'taxpayerType', value: '小规模纳税人', type: 'STRING' }]
    wrapper.vm.$message = { success: vi.fn(), error: vi.fn() }

    await wrapper.vm.handleExecute()
    await nextTick()

    expect(wrapper.vm.result).not.toBeNull()
    expect(wrapper.vm.result.success).toBe(false)
    expect(wrapper.vm.result.errorMessage).toContain('规则编译失败')
  })

  test('执行期间切换规则不会保留旧响应或覆盖新规则结果', async () => {
    let resolveExecution
    definitionApi.executeApiScenarioValidated.mockReturnValueOnce(
      new Promise(resolve => { resolveExecution = resolve })
    )
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.selectedRule = mockRules()[0]
    wrapper.vm.lastRawResponse = { data: { result: '更早的结果' } }

    const executing = wrapper.vm.handleExecute()
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedRule = mockRules()[1]
    await wrapper.vm.onRuleChange()
    resolveExecution({ data: mockExecutionResult() })
    await executing

    expect(wrapper.vm.result).toBeNull()
    expect(wrapper.vm.lastRawResponse).toBeNull()
    expect(wrapper.vm.executing).toBe(false)
  })

  test('handleClear 清空 params 和 result', () => {
    wrapper.vm.params = [{ key: 'taxpayerType', value: '小规模纳税人' }]
    wrapper.vm.result = { success: true }
    wrapper.vm.handleClear()
    expect(wrapper.vm.params).toEqual([])
    expect(wrapper.vm.result).toBeNull()
  })

  test('加载固定测试用例并批量执行后生成结果 diff', async () => {
    const cases = [
      {
        id: 11,
        scenarioName: '命中用例',
        requestJson: '{"clientAppName":"rule-test","params":{"age":18}}',
        responseJson: '{"code":200,"data":{"success":true,"result":{"level":"A"}}}'
      },
      {
        id: 12,
        scenarioName: '差异用例',
        requestJson: '{"clientAppName":"rule-test","params":{"age":17}}',
        responseJson: '{"code":200,"data":{"success":true,"result":{"level":"A"}}}'
      }
    ]
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedProjectId = 7
    definitionApi.listApiScenarios.mockResolvedValueOnce({ data: cases })
    await wrapper.vm.loadTestCases()
    wrapper.vm.selectedTestCaseIds = [11, 12]
    definitionApi.executeApiScenarioValidated
      .mockResolvedValueOnce(validatedResult({ code: 200, data: { success: true, result: { level: 'A' }, executeTimeMs: 10 } },
        { status: 'PASSED', message: '与JSON预期快照一致', checks: [], diffs: [] }))
      .mockResolvedValueOnce(validatedResult({ code: 200, data: { success: true, result: { level: 'B' }, executeTimeMs: 20 } },
        { status: 'FAILED', message: '与JSON预期快照不同', checks: [], diffs: [{ path: '$.result.level', expectedJson: '"A"', actualJson: '"B"' }] }))

    await wrapper.vm.executeSelectedTestCases()

    expect(definitionApi.executeApiScenarioValidated).toHaveBeenCalledTimes(2)
    expect(definitionApi.executeApiScenarioValidated.mock.calls[0][1].projectId).toBe(7)
    expect(wrapper.vm.batchResults[0].diffs).toEqual([])
    expect(wrapper.vm.batchResults[1].diffs[0]).toMatchObject({ path: '$.result.level', expectedJson: '"A"', actualJson: '"B"' })
  })

  test('修改执行输入后不能把旧响应收藏成新输入的预期结果', async () => {
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.params = [{ key: 'age', value: '18', type: 'INTEGER' }]
    definitionApi.executeApiScenarioValidated.mockResolvedValueOnce(validatedResult({ data: { success: true, result: '通过' } }))
    await wrapper.vm.handleExecute()
    wrapper.vm.params[0].value = '17'
    wrapper.vm.$prompt = vi.fn().mockResolvedValue({ value: '未成年' })
    wrapper.vm.$message = { warning: vi.fn(), success: vi.fn() }

    await wrapper.vm.saveCurrentTestCase()

    expect(definitionApi.createApiScenario).not.toHaveBeenCalled()
    expect(wrapper.vm.$message.warning).toHaveBeenCalledWith('输入参数已修改，请重新执行后再收藏输入与结果')
  })

  test('批量执行收到规则失败响应时不能显示执行成功', async () => {
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.testCases = [{
      id: 11,
      scenarioName: '无效输入',
      requestJson: '{"params":{"age":-1}}',
      responseJson: '{"data":{"success":false,"errorMessage":"年龄不能小于零"}}'
    }]
    wrapper.vm.selectedTestCaseIds = [11]
    definitionApi.executeApiScenarioValidated.mockResolvedValueOnce(validatedResult({
      code: 200, data: { success: false, errorMessage: '年龄不能小于零' }
    }, { status: 'PASSED', message: '与错误场景快照一致', checks: [], diffs: [] }))

    await wrapper.vm.executeSelectedTestCases()

    expect(wrapper.vm.batchResults[0]).toMatchObject({ success: false, diffs: [] })
  })

  test('收藏保存的是同一次执行的输入与结果', async () => {
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.params = [{ key: 'age', value: '18', type: 'INTEGER' }]
    const response = { data: { success: true, result: '通过' } }
    definitionApi.executeApiScenarioValidated.mockResolvedValueOnce(validatedResult(response))
    await wrapper.vm.handleExecute()
    wrapper.vm.$prompt = vi.fn().mockResolvedValue({ value: '成年人' })
    wrapper.vm.$message = { success: vi.fn() }

    await wrapper.vm.saveCurrentTestCase()

    const [ruleId, saved] = definitionApi.createApiScenario.mock.lastCall
    expect(ruleId).toBe(4)
    expect(JSON.parse(saved.requestJson).params).toEqual({ age: 18 })
    expect(JSON.parse(saved.responseJson)).toEqual(response)
    expect(saved.responseSource).toBe('EXECUTED')
  })

  test('收藏命名期间切换规则不会把原输入收藏到另一规则', async () => {
    let finishPrompt
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.params = [{ key: 'age', value: '18', type: 'INTEGER' }]
    wrapper.vm.$prompt = vi.fn().mockReturnValue(new Promise(resolve => { finishPrompt = resolve }))

    const saving = wrapper.vm.saveCurrentTestCase()
    wrapper.vm.selectedRuleId = 1
    finishPrompt({ value: '测试用例' })
    await saving

    expect(definitionApi.createApiScenario).not.toHaveBeenCalled()
  })

  test('载入固定用例完整替换输入，不残留旧手动参数或缺省的可选字段', async () => {
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedRule = mockRules()[1]
    wrapper.vm.params = [
      { key: 'age', value: '65', type: 'INTEGER', fromVar: true },
      { key: 'optionalIncome', value: '20000', type: 'NUMBER', fromVar: true },
      { key: 'previousManual', value: '旧输入', type: 'STRING', fromVar: false }
    ]
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: {
      inputs: [
        { refId: 1, refType: 'VARIABLE', scriptName: 'age', label: '年龄', valueType: 'INTEGER' },
        { refId: 2, refType: 'VARIABLE', scriptName: 'optionalIncome', label: '选填收入', valueType: 'NUMBER' }
      ],
      sampleParams: { age: 0, optionalIncome: 0 }
    } })

    await wrapper.vm.applyTestCase({ requestJson: '{"params":{"age":18,"customer":{"region":"上海"},"extra":{}}}' })

    expect(JSON.parse(stringifyExactJson(wrapper.vm.buildParamMap()))).toEqual({ age: 18, customer: { region: '上海' }, extra: {} })
    expect(wrapper.vm.params.find(param => param.key === 'age')).toMatchObject({ label: '年龄', type: 'INTEGER', fromVar: true })
  })

  test('切换规则时旧固定用例响应不能覆盖当前规则', async () => {
    let resolveFirst
    let resolveSecond
    definitionApi.listApiScenarios
      .mockReturnValueOnce(new Promise(resolve => { resolveFirst = resolve }))
      .mockReturnValueOnce(new Promise(resolve => { resolveSecond = resolve }))

    wrapper.vm.selectedRuleId = 1
    const first = wrapper.vm.loadTestCases()
    wrapper.vm.selectedRuleId = 4
    const second = wrapper.vm.loadTestCases()
    resolveSecond({ data: [{ id: 42, scenarioName: '当前规则用例' }] })
    await second
    resolveFirst({ data: [{ id: 11, scenarioName: '旧规则用例' }] })
    await first

    expect(wrapper.vm.testCases).toEqual([
      { id: 42, scenarioName: '当前规则用例' }
    ])
    expect(wrapper.vm.testCasesLoading).toBe(false)
  })

  test('应用固定用例期间切换规则不会把旧参数写入新规则', async () => {
    let resolveSchema
    definitionApi.getRuleTestSchema.mockReturnValueOnce(
      new Promise(resolve => { resolveSchema = resolve })
    )
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.selectedRule = mockRules()[0]

    const applying = wrapper.vm.applyTestCase({
      requestJson: '{"params":{"legacyAmount":99}}'
    })
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedRule = mockRules()[1]
    wrapper.vm.params = [{
      key: 'goodsCategory',
      value: '服务',
      type: 'STRING',
      fromVar: true
    }]
    resolveSchema(mockCrossTableSchema())
    await applying

    expect(wrapper.vm.params).toEqual([{
      key: 'goodsCategory',
      value: '服务',
      type: 'STRING',
      fromVar: true
    }])
  })

  test('批量执行期间切换规则不会把旧结果写入新规则', async () => {
    let resolveExecution
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.testCases = [{
      id: 11,
      scenarioName: '旧规则用例',
      requestJson: '{"params":{"age":18}}',
      responseJson: '{"code":200}'
    }]
    wrapper.vm.selectedTestCaseIds = [11]
    definitionApi.executeApiScenarioValidated.mockReturnValueOnce(
      new Promise(resolve => { resolveExecution = resolve })
    )

    const executing = wrapper.vm.executeSelectedTestCases()
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.resetTestCases()
    resolveExecution({ code: 200 })
    await executing

    expect(wrapper.vm.batchResults).toEqual([])
    expect(wrapper.vm.batchExecuting).toBe(false)
  })

  test('追踪树按状态和关键字筛选并保留祖先', () => {
    wrapper.vm.result = {
      traces: [{
        status: 'SUCCESS',
        children: [
          { status: 'FAILED', ruleCode: 'R_FAIL', children: [] },
          { status: 'SUCCESS', ruleCode: 'R_PASS', children: [] }
        ]
      }]
    }
    wrapper.vm.traceStatusFilter = 'FAILED'
    wrapper.vm.traceKeyword = 'R_FAIL'

    expect(JSON.parse(wrapper.vm.traceInfoJson).children).toEqual([
      { status: 'FAILED', ruleCode: 'R_FAIL', children: [] }
    ])
  })
})

describe('RuleTest — 完整集成流程：风险定价交叉表', () => {
  let wrapper

  beforeEach(async () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: mockProjects() } })
    definitionApi.listDefinitions.mockResolvedValueOnce({ data: { records: mockRules() } })
    variableApi.listVariables.mockResolvedValueOnce({ data: { records: mockVariables() } })

    wrapper = shallowMount(RuleTest, {
      mocks: {
        $route: { params: {} },
        $router: { push: vi.fn(), replace: vi.fn() }
      },
      stubs: {
        'el-form': makeStub('form'), 'el-form-item': makeStub('div'),
        'el-select': makeStub('select'), 'el-option': makeStub('option'),
        'el-input': makeStub('input'), 'el-input-number': makeStub('input'),
        'el-button': makeStub('button'), 'el-tag': makeStub('span'),
        'el-table': makeStub('table'), 'el-table-column': makeStub('td'),
        'el-tabs': makeStub('div'), 'el-tab-pane': makeStub('div'),
        'el-dialog': makeStub('div'), 'el-card': makeStub('div'),
        'el-alert': makeStub('div'), 'el-divider': makeStub('hr'),
        'el-loading': makeStub('div'), 'el-descriptions': makeStub('div'),
        'el-descriptions-item': makeStub('div'),
        'el-radio-group': makeStub('div'), 'el-radio-button': makeStub('div'),
        'el-collapse': makeStub('div'), 'el-collapse-item': makeStub('div'),
        'monaco-editor': makeStub('div'),
        'trace-tree': makeStub('div'), 'var-picker': makeStub('div'),
        'script-panel': makeStub('div')
      }
    })

    await nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('步骤1：选中"风险定价交叉表"规则，加载变量后自动填入参数', async () => {
    // 1. 选择规则
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedRule = mockRules().find(r => r.id === 4)
    expect(wrapper.vm.selectedRule.ruleCode).toBe('RC_RATE_MATRIX')
    expect(wrapper.vm.selectedRule.modelType).toBe('CROSS')

    // 2. 模拟服务端基于 ID + ref_type 解析出的测试结构
    definitionApi.getRuleTestSchema.mockResolvedValueOnce(mockCrossTableSchema())

    await wrapper.vm.loadVariables()
    await nextTick()

    // 3. 验证只加载 2 个输入参数（taxpayerType, goodsCategory）
    expect(wrapper.vm.params.length).toBe(2)
    expect(wrapper.vm.params.some(p => p.key === 'taxpayerType')).toBe(true)
    expect(wrapper.vm.params.some(p => p.key === 'goodsCategory')).toBe(true)
    expect(wrapper.vm.params.some(p => p.key === 'taxRate')).toBe(false)
  })

  test('步骤2：填入测试数据并执行，验证 taxRate = 0.03', async () => {
    // 1. 选择规则
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedRule = mockRules().find(r => r.id === 4)

    // 2. 加载变量
    definitionApi.getRuleTestSchema.mockResolvedValueOnce(mockCrossTableSchema())
    await wrapper.vm.loadVariables()
    await nextTick()

    // 3. 填入测试数据：小规模纳税人 + 服务 → taxRate = 0.03
    const taxpayerTypeParam = wrapper.vm.params.find(p => p.key === 'taxpayerType')
    const goodsCategoryParam = wrapper.vm.params.find(p => p.key === 'goodsCategory')
    taxpayerTypeParam.value = '小规模纳税人'
    goodsCategoryParam.value = '服务'

    // 4. 模拟执行结果
    definitionApi.executeApiScenarioValidated.mockResolvedValueOnce(validatedResult({ data: mockExecutionResult() }))
    wrapper.vm.$message = { success: vi.fn(), error: vi.fn() }

    await wrapper.vm.handleExecute()
    await nextTick()

    // 5. 验证执行成功
    expect(wrapper.vm.result.success).toBe(true)
    expect(wrapper.vm.result.result.taxRate).toBe(0.03)
    expect(wrapper.vm.result.executeTimeMs).toBe(15)
  })

  test('步骤3：验证 traces 存在且格式正确（用于表达式追踪树）', async () => {
    // 1. 选择规则
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedRule = mockRules().find(r => r.id === 4)

    // 2. 加载变量
    definitionApi.getRuleTestSchema.mockResolvedValueOnce(mockCrossTableSchema())
    await wrapper.vm.loadVariables()
    await nextTick()

    // 3. 填入测试数据
    wrapper.vm.params.find(p => p.key === 'taxpayerType').value = '小规模纳税人'
    wrapper.vm.params.find(p => p.key === 'goodsCategory').value = '服务'

    // 4. 执行
    definitionApi.executeApiScenarioValidated.mockResolvedValueOnce(validatedResult({ data: mockExecutionResult() }))
    wrapper.vm.$message = { success: vi.fn(), error: vi.fn() }
    await wrapper.vm.handleExecute()
    await nextTick()

    // 5. 验证 traces 结构（用于 TraceTree 组件渲染表达式追踪树）
    expect(wrapper.vm.result.traces).toBeInstanceOf(Array)
    expect(wrapper.vm.result.traces.length).toBeGreaterThan(0)
    const trace = wrapper.vm.result.traces[0]
    expect(trace.input).toBeDefined()
    expect(trace.output).toBeDefined()
    expect(trace.input.taxpayerType).toBe('小规模纳税人')
    expect(trace.input.goodsCategory).toBe('服务')
    expect(trace.output.taxRate).toBe(0.03)
  })

  test('computed: inputParamsJson 正确构建入参 JSON', async () => {
    // 1. 选择规则
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedRule = mockRules().find(r => r.id === 4)

    // 2. 加载变量
    definitionApi.getRuleTestSchema.mockResolvedValueOnce(mockCrossTableSchema())
    await wrapper.vm.loadVariables()
    await nextTick()

    // 3. 填入测试数据
    wrapper.vm.params.find(p => p.key === 'taxpayerType').value = '小规模纳税人'
    wrapper.vm.params.find(p => p.key === 'goodsCategory').value = '服务'

    // 4. 验证 inputParamsJson
    const inputParams = JSON.parse(wrapper.vm.inputParamsJson)
    expect(inputParams.taxpayerType).toBe('小规模纳税人')
    expect(inputParams.goodsCategory).toBe('服务')
  })

  test('computed: outputResultJson 正确构建出参 JSON', async () => {
    wrapper.vm.result = mockExecutionResult()
    const output = JSON.parse(wrapper.vm.outputResultJson)
    expect(output.taxRate).toBe(0.03)
  })

  test('computed: traceInfoJson 返回 traces[0] 的 JSON 字符串', () => {
    wrapper.vm.result = mockExecutionResult()
    const traceInfo = JSON.parse(wrapper.vm.traceInfoJson)
    expect(traceInfo.input).toBeDefined()
  })
})
