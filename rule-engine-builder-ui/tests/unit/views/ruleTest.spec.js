// tests/unit/views/ruleTest.spec.js
// 规则测试页面 RuleTest.vue 单元测试
// 覆盖：初始化、辅助方法、加载变量（决策表/交叉表）、执行与结果展示

import { shallowMount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'
import ElementUI from 'element-ui'

import * as definitionApi from '@/api/definition'
import * as variableApi from '@/api/variable'
import * as projectApi from '@/api/project'
import * as modelApi from '@/api/model'
import RuleTest from '@/views/test/RuleTest.vue'

afterEach(() => { jest.clearAllMocks() })

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
  return { render: h => h(tag) }
}

function createTestVue() {
  const localVue = createLocalVue()
  localVue.use(ElementUI)
  localVue.component('el-checkbox', makeStub('label'))
  localVue.component('el-checkbox-group', makeStub('div'))
  return localVue
}

// ─── 测试用例 ─────────────────────────────────────────────

describe('RuleTest — 初始化与数据加载', () => {
  let wrapper

  beforeEach(async () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: mockProjects() } })
    definitionApi.listDefinitions.mockResolvedValueOnce({ data: { records: mockRules() } })
    variableApi.listVariables.mockResolvedValueOnce({ data: { records: mockVariables() } })

    wrapper = shallowMount(RuleTest, {
      localVue: createTestVue(),
      mocks: {
        $route: { params: {} },
        $router: { push: jest.fn(), replace: jest.fn() }
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

    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

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

  test('result 初始为 null（注意：字段名是 result 不是 testResult）', () => {
    expect(wrapper.vm.result).toBeNull()
  })
})

describe('RuleTest — 辅助方法', () => {
  let wrapper

  beforeEach(async () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: mockProjects() } })
    definitionApi.listDefinitions.mockResolvedValueOnce({ data: { records: mockRules() } })
    variableApi.listVariables.mockResolvedValueOnce({ data: { records: mockVariables() } })

    wrapper = shallowMount(RuleTest, {
      localVue: createTestVue(),
      mocks: {
        $route: { params: {} },
        $router: { push: jest.fn(), replace: jest.fn() }
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

    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('mtl 返回正确的模型类型标签（注意：方法名是 mtl 不是 modelTypeLabel）', () => {
    expect(wrapper.vm.mtl('TABLE')).toBe('决策表')
    expect(wrapper.vm.mtl('TREE')).toBe('决策树')
    expect(wrapper.vm.mtl('FLOW')).toBe('决策流')
    expect(wrapper.vm.mtl('RULE_SET')).toBe('规则集')
    expect(wrapper.vm.mtl('SCORE')).toBe('评分卡')
    expect(wrapper.vm.mtl('CROSS')).toBe('交叉表')
    expect(wrapper.vm.mtl('SCRIPT')).toBe('QL脚本')
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

    expect(wrapper.vm.buildParamMap()).toEqual({
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
    expect(wrapper.vm.buildParamMap()).toMatchObject({
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
    expect(wrapper.vm.buildParamMap()).toEqual({ s: 1 })
  })
})

describe('RuleTest — 加载变量（loadVariables）', () => {
  let wrapper

  beforeEach(async () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: mockProjects() } })
    definitionApi.listDefinitions.mockResolvedValueOnce({ data: { records: mockRules() } })
    variableApi.listVariables.mockResolvedValueOnce({ data: { records: mockVariables() } })

    wrapper = shallowMount(RuleTest, {
      localVue: createTestVue(),
      mocks: {
        $route: { params: {} },
        $router: { push: jest.fn(), replace: jest.fn() }
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

    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('旧交叉表只有 varCode 时拒绝回退匹配变量', async () => {
    // 选中"风险定价交叉表"（id=4，模型类型 CROSS）
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedRule = mockRules().find(r => r.id === 4)

    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: { inputs: [], sampleParams: {} } })
    definitionApi.refreshFields.mockResolvedValueOnce({ data: null })
    definitionApi.listInputFields.mockResolvedValueOnce({ data: [] })

    await wrapper.vm.loadVariables()
    await Vue.nextTick()

    expect(wrapper.vm.params).toEqual([])
    expect(variableApi.listVariablesByProject).not.toHaveBeenCalled()
  })

  test('决策表规则：通过服务端 ID + ref_type 测试结构加载变量', async () => {
    // 选中"客商×产品总线定价表"（id=1，模型类型 TABLE）
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.selectedRule = mockRules().find(r => r.id === 1)

    definitionApi.getRuleTestSchema.mockResolvedValueOnce(mockCrossTableSchema())

    await wrapper.vm.loadVariables()
    await Vue.nextTick()

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
    await Vue.nextTick()

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
    expect(wrapper.vm.buildParamMap()).toEqual({ score_f1_fields: { HYBASE_X115: 0 } })
  })

  test('未选择规则时 loadVariables 不调用 API', async () => {
    wrapper.vm.selectedRule = null
    await wrapper.vm.loadVariables()
    expect(definitionApi.getContent).not.toHaveBeenCalled()
  })
})

describe('RuleTest — 执行与结果展示', () => {
  let wrapper

  beforeEach(async () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: mockProjects() } })
    definitionApi.listDefinitions.mockResolvedValueOnce({ data: { records: mockRules() } })
    variableApi.listVariables.mockResolvedValueOnce({ data: { records: mockVariables() } })

    wrapper = shallowMount(RuleTest, {
      localVue: createTestVue(),
      mocks: {
        $route: { params: {} },
        $router: { push: jest.fn(), replace: jest.fn() }
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

    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('handleExecute 成功时设置 result', async () => {
    definitionApi.executeRule.mockResolvedValueOnce({ data: mockExecutionResult() })
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.params = [
      { key: 'taxpayerType', value: '小规模纳税人', type: 'STRING' },
      { key: 'goodsCategory', value: '服务', type: 'STRING' }
    ]
    wrapper.vm.$message = { success: jest.fn(), error: jest.fn() }

    await wrapper.vm.handleExecute()
    await Vue.nextTick()

    expect(wrapper.vm.result).not.toBeNull()
    expect(wrapper.vm.result.success).toBe(true)
    expect(wrapper.vm.result.result.taxRate).toBe(0.03)
    expect(definitionApi.executeRule).toHaveBeenCalledWith({
      definitionId: 4,
      params: { taxpayerType: '小规模纳税人', goodsCategory: '服务' }
    }, 180000)
  })

  test('handleExecute 保留 false 输出', async () => {
    definitionApi.executeRule.mockResolvedValueOnce({ data: { success: true, result: false } })
    wrapper.vm.selectedRuleId = 4
    await wrapper.vm.handleExecute()
    expect(wrapper.vm.result).toMatchObject({ hasOutput: true, output: false })
  })

  test('handleExecute 失败时设置错误信息到 result', async () => {
    definitionApi.executeRule.mockRejectedValueOnce(new Error('规则编译失败'))
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.params = [{ key: 'taxpayerType', value: '小规模纳税人', type: 'STRING' }]
    wrapper.vm.$message = { success: jest.fn(), error: jest.fn() }

    await wrapper.vm.handleExecute()
    await Vue.nextTick()

    expect(wrapper.vm.result).not.toBeNull()
    expect(wrapper.vm.result.success).toBe(false)
    expect(wrapper.vm.result.errorMessage).toContain('规则编译失败')
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
    definitionApi.listApiScenarios.mockResolvedValueOnce({ data: cases })
    await wrapper.vm.loadTestCases()
    wrapper.vm.selectedTestCaseIds = [11, 12]
    definitionApi.executeApiScenario
      .mockResolvedValueOnce({ code: 200, data: { success: true, result: { level: 'A' }, executeTimeMs: 10 } })
      .mockResolvedValueOnce({ code: 200, data: { success: true, result: { level: 'B' }, executeTimeMs: 20 } })

    await wrapper.vm.executeSelectedTestCases()

    expect(definitionApi.executeApiScenario).toHaveBeenCalledTimes(2)
    expect(wrapper.vm.batchResults[0].diffs).toEqual([])
    expect(wrapper.vm.batchResults[1].diffs[0]).toMatchObject({ path: '$.result.level', expected: 'A', actual: 'B' })
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
      localVue: createTestVue(),
      mocks: {
        $route: { params: {} },
        $router: { push: jest.fn(), replace: jest.fn() }
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

    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('步骤1：选中"风险定价交叉表"规则，加载变量后自动填入参数', async () => {
    // 1. 选择规则
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedRule = mockRules().find(r => r.id === 4)
    expect(wrapper.vm.selectedRule.ruleCode).toBe('RC_RATE_MATRIX')
    expect(wrapper.vm.selectedRule.modelType).toBe('CROSS')

    // 2. 模拟服务端基于 ID + ref_type 解析出的测试结构
    definitionApi.getRuleTestSchema.mockResolvedValueOnce(mockCrossTableSchema())

    await wrapper.vm.loadVariables()
    await Vue.nextTick()

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
    await Vue.nextTick()

    // 3. 填入测试数据：小规模纳税人 + 服务 → taxRate = 0.03
    const taxpayerTypeParam = wrapper.vm.params.find(p => p.key === 'taxpayerType')
    const goodsCategoryParam = wrapper.vm.params.find(p => p.key === 'goodsCategory')
    taxpayerTypeParam.value = '小规模纳税人'
    goodsCategoryParam.value = '服务'

    // 4. 模拟执行结果
    definitionApi.executeRule.mockResolvedValueOnce({ data: mockExecutionResult() })
    wrapper.vm.$message = { success: jest.fn(), error: jest.fn() }

    await wrapper.vm.handleExecute()
    await Vue.nextTick()

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
    await Vue.nextTick()

    // 3. 填入测试数据
    wrapper.vm.params.find(p => p.key === 'taxpayerType').value = '小规模纳税人'
    wrapper.vm.params.find(p => p.key === 'goodsCategory').value = '服务'

    // 4. 执行
    definitionApi.executeRule.mockResolvedValueOnce({ data: mockExecutionResult() })
    wrapper.vm.$message = { success: jest.fn(), error: jest.fn() }
    await wrapper.vm.handleExecute()
    await Vue.nextTick()

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
    await Vue.nextTick()

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
