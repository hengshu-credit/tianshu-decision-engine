// tests/unit/views/ruleDetail.spec.js
import { mount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'
import ElementUI from 'element-ui'

// 直接 mock API 模块（避免真实 axios 依赖链）
jest.mock('@/api/definition', () => ({
  getDefinitionDetail: jest.fn(),
  updateInputField: jest.fn(),
  updateOutputField: jest.fn(),
  executeRule: jest.fn()
}))

jest.mock('@/api/variable', () => ({
  listVariablesByProject: jest.fn(),
  listVariables: jest.fn()
}))

jest.mock('@/api/dataObject', () => ({
  getVariableTree: jest.fn()
}))

import * as definitionApi from '@/api/definition'
import * as variableApi from '@/api/variable'
import RuleDetail from '@/views/rule/RuleDetail.vue'

afterEach(() => { jest.clearAllMocks() })

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
      { id: 1, varId: 1, fieldLabel: '年龄', fieldType: 'INTEGER', scriptName: 'age' },
      { id: 2, varId: 2, fieldLabel: '收入', fieldType: 'DOUBLE', scriptName: 'income' }
    ],
    outputFieldsJson: [
      { id: 10, varId: 3, fieldLabel: '结果', fieldType: 'STRING', scriptName: 'result' }
    ]
  }
}

function mockVariables() {
  return [
    { id: 1, varCode: 'age', varLabel: '年龄', varType: 'INTEGER', varSource: 'INPUT', scriptName: 'age' },
    { id: 2, varCode: 'income', varLabel: '收入', varType: 'DOUBLE', varSource: 'INPUT', scriptName: 'income' },
    { id: 3, varCode: 'result', varLabel: '结果', varType: 'STRING', varSource: 'OUTPUT', scriptName: 'result' }
  ]
}

// ─── 测试用例 ─────────────────────────────────────────────
function createTestVue() {
  const localVue = createLocalVue()
  localVue.use(ElementUI)
  return localVue
}

describe('RuleDetail — 初始化与数据加载', () => {
  let wrapper

  beforeEach(async () => {
    definitionApi.getDefinitionDetail.mockResolvedValue({ data: mockRuleDetail(1) })
    variableApi.listVariablesByProject.mockResolvedValue({ data: mockVariables() })
    variableApi.listVariables.mockResolvedValue({ data: { records: [] } })

    wrapper = mount(RuleDetail, {
      localVue: createTestVue(),
      propsData: { id: '1' },
      mocks: {
        $route: { params: { id: 1 } },
        $router: { push: jest.fn(), replace: jest.fn() }
      },
      stubs: {
        'el-descriptions': true, 'el-descriptions-item': true,
        'el-form': true, 'el-form-item': true,
        'el-select': true, 'el-option': true,
        'el-input': true, 'el-button': true, 'el-tag': true,
        'el-alert': true, 'el-tabs': true, 'el-tab-pane': true,
        'el-table': true, 'el-table-column': true,
        'el-input-number': true, 'el-date-picker': true,
        'el-divider': true, 'el-tooltip': true,
        'el-loading': true,
        'monaco-editor': true
      }
    })

    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

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
})

describe('RuleDetail — 辅助方法', () => {
  let wrapper

  beforeEach(async () => {
    definitionApi.getDefinitionDetail.mockResolvedValue({ data: mockRuleDetail(1) })
    variableApi.listVariablesByProject.mockResolvedValue({ data: mockVariables() })
    variableApi.listVariables.mockResolvedValue({ data: { records: [] } })

    wrapper = mount(RuleDetail, {
      localVue: createTestVue(),
      propsData: { id: '1' },
      mocks: {
        $route: { params: { id: 1 } },
        $router: { push: jest.fn(), replace: jest.fn() }
      },
      stubs: {
        'el-descriptions': true, 'el-descriptions-item': true,
        'el-form': true, 'el-form-item': true,
        'el-select': true, 'el-option': true,
        'el-input': true, 'el-button': true, 'el-tag': true,
        'el-alert': true, 'el-tabs': true, 'el-tab-pane': true,
        'el-table': true, 'el-table-column': true,
        'el-input-number': true, 'el-date-picker': true,
        'el-divider': true, 'el-tooltip': true,
        'el-loading': true,
        'monaco-editor': true
      }
    })

    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('modelTypeLabel 返回正确的中文标签', () => {
    expect(wrapper.vm.modelTypeLabel('TABLE')).toBe('决策表')
    expect(wrapper.vm.modelTypeLabel('TREE')).toBe('决策树')
    expect(wrapper.vm.modelTypeLabel('FLOW')).toBe('决策流')
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
})

describe('RuleDetail — 出入参字段管理', () => {
  let wrapper

  beforeEach(async () => {
    definitionApi.getDefinitionDetail.mockResolvedValue({ data: mockRuleDetail(1) })
    variableApi.listVariablesByProject.mockResolvedValue({ data: mockVariables() })
    variableApi.listVariables.mockResolvedValue({ data: { records: [] } })
    definitionApi.updateInputField.mockResolvedValue({ data: { success: true } })
    definitionApi.updateOutputField.mockResolvedValue({ data: { success: true } })

    wrapper = mount(RuleDetail, {
      localVue: createTestVue(),
      propsData: { id: '1' },
      mocks: {
        $route: { params: { id: 1 } },
        $router: { push: jest.fn(), replace: jest.fn() }
      },
      stubs: {
        'el-descriptions': true, 'el-descriptions-item': true,
        'el-form': true, 'el-form-item': true,
        'el-select': true, 'el-option': true,
        'el-input': true, 'el-button': true, 'el-tag': true,
        'el-alert': true, 'el-tabs': true, 'el-tab-pane': true,
        'el-table': true, 'el-table-column': true,
        'el-input-number': true, 'el-date-picker': true,
        'el-divider': true, 'el-tooltip': true,
        'el-loading': true,
        'monaco-editor': true
      }
    })

    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

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

  beforeEach(async () => {
    definitionApi.getDefinitionDetail.mockResolvedValue({ data: mockRuleDetail(1) })
    variableApi.listVariablesByProject.mockResolvedValue({ data: mockVariables() })
    variableApi.listVariables.mockResolvedValue({ data: { records: [] } })

    wrapper = mount(RuleDetail, {
      localVue: createTestVue(),
      propsData: { id: '1' },
      mocks: {
        $route: { params: { id: 1 } },
        $router: { push: jest.fn(), replace: jest.fn() }
      },
      stubs: {
        'el-descriptions': true, 'el-descriptions-item': true,
        'el-form': true, 'el-form-item': true,
        'el-select': true, 'el-option': true,
        'el-input': true, 'el-button': true, 'el-tag': true,
        'el-alert': true, 'el-tabs': true, 'el-tab-pane': true,
        'el-table': true, 'el-table-column': true,
        'el-input-number': true, 'el-date-picker': true,
        'el-divider': true, 'el-tooltip': true,
        'el-loading': true,
        'monaco-editor': true
      }
    })

    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('openTestDialog 打开测试弹窗', async () => {
    wrapper.vm.openTestDialog()
    await Vue.nextTick()
    expect(wrapper.vm.testVisible).toBe(true)
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