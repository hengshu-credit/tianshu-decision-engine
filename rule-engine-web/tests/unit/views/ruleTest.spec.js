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
    expect(wrapper.vm.buildParamMap()).toEqual({ score_f1_fields: { HYBASE_X115: 0 } })
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
    definitionApi.executeRule.mockResolvedValueOnce({ data: mockExecutionResult() })
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
    expect(definitionApi.executeRule).toHaveBeenCalledWith({
      definitionId: 4,
      params: { taxpayerType: '小规模纳税人', goodsCategory: '服务' }
    }, 180000)
  })

  test('追踪入参固定为执行时快照，不随结果页编辑变化', async () => {
    definitionApi.executeRule.mockResolvedValueOnce({ data: mockExecutionResult() })
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.params = [{ key: 'age', value: '18', type: 'INTEGER' }]

    await wrapper.vm.handleExecute()
    wrapper.vm.params[0].value = '99'

    expect(JSON.parse(wrapper.vm.inputParamsJson)).toEqual({ age: 18 })
  })

  test('project-scoped execution sends the effective project ID', async () => {
    definitionApi.executeRule.mockResolvedValueOnce({ data: mockExecutionResult() })
    wrapper.vm.selectedRuleId = 4
    wrapper.vm.selectedProjectId = 7

    await wrapper.vm.handleExecute()

    expect(definitionApi.executeRule).toHaveBeenCalledWith({
      definitionId: 4,
      projectId: 7,
      params: {}
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
    wrapper.vm.$message = { success: vi.fn(), error: vi.fn() }

    await wrapper.vm.handleExecute()
    await nextTick()

    expect(wrapper.vm.result).not.toBeNull()
    expect(wrapper.vm.result.success).toBe(false)
    expect(wrapper.vm.result.errorMessage).toContain('规则编译失败')
  })

  test('执行期间切换规则不会保留旧响应或覆盖新规则结果', async () => {
    let resolveExecution
    definitionApi.executeRule.mockReturnValueOnce(
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
    definitionApi.executeApiScenario
      .mockResolvedValueOnce({ code: 200, data: { success: true, result: { level: 'A' }, executeTimeMs: 10 } })
      .mockResolvedValueOnce({ code: 200, data: { success: true, result: { level: 'B' }, executeTimeMs: 20 } })

    await wrapper.vm.executeSelectedTestCases()

    expect(definitionApi.executeApiScenario).toHaveBeenCalledTimes(2)
    expect(definitionApi.executeApiScenario.mock.calls[0][1].projectId).toBe(7)
    expect(wrapper.vm.batchResults[0].diffs).toEqual([])
    expect(wrapper.vm.batchResults[1].diffs[0]).toMatchObject({ path: '$.result.level', expected: 'A', actual: 'B' })
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
    definitionApi.executeApiScenario.mockReturnValueOnce(
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
    definitionApi.executeRule.mockResolvedValueOnce({ data: mockExecutionResult() })
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
    definitionApi.executeRule.mockResolvedValueOnce({ data: mockExecutionResult() })
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
