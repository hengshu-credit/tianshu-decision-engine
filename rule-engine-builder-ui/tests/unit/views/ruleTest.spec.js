// tests/unit/views/ruleTest.spec.js
import { shallowMount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'
import ElementUI from 'element-ui'

// 直接 import API 模块（不写 jest.mock，依赖 setup.js 的预置 mock）
import * as definitionApi from '@/api/definition'
import * as variableApi from '@/api/variable'
import * as projectApi from '@/api/project'
import RuleTest from '@/views/test/RuleTest.vue'

afterEach(() => { jest.clearAllMocks() })

// ─── Mock 数据 ───────────────────────────────────────────
function mockProjects() {
  return [
    { id: 1, projectName: '项目A', projectCode: 'project_a' },
    { id: 2, projectName: '项目B', projectCode: 'project_b' }
  ]
}

function mockRules() {
  return [
    { id: 1, ruleCode: 'age_rule', ruleName: '年龄判断', modelType: 'TABLE', status: 1 },
    { id: 2, ruleCode: 'score_card', ruleName: '评分卡', modelType: 'SCORE', status: 1 }
  ]
}

function mockModelJson() {
  return {
    name: '测试决策表',
    rules: [
      {
        conditionRoot: { id: 'root-1', type: 'AND', children: [] },
        actions: []
      }
    ]
  }
}

function mockVariables() {
  return [
    { id: 1, varCode: 'age', varLabel: '年龄', varType: 'INTEGER', varSource: 'INPUT', scriptName: 'age' },
    { id: 2, varCode: 'income', varLabel: '收入', varType: 'NUMBER', varSource: 'INPUT', scriptName: 'income' }
  ]
}

function mockExecutionResult() {
  return {
    success: true,
    ruleCode: 'age_rule',
    executeTimeMs: 15,
    result: {
      age: 30,
      income: 5000,
      decision: 'PASS'
    },
    trace: [
      { step: 1, node: '条件判断', expr: 'age >= 18', result: true },
      { step: 2, node: '输出', expr: 'decision = "PASS"', result: 'PASS' }
    ]
  }
}

// ─── 测试用例 ─────────────────────────────────────────────
function createTestVue() {
  const localVue = createLocalVue()
  localVue.use(ElementUI)
  return localVue
}

function makeStub(tag) {
  return { render: h => h(tag) }
}

describe('RuleTest — 初始化与数据加载', () => {
  let wrapper

  beforeEach(async () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: mockProjects() } })
    definitionApi.listDefinitions.mockResolvedValueOnce({ data: { records: mockRules() } })
    variableApi.listVariablesByProject.mockResolvedValueOnce({ data: mockVariables() })

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

  test('testResult 初始为 null', () => {
    expect(wrapper.vm.testResult).toBeNull()
  })
})

describe('RuleTest — 辅助方法', () => {
  let wrapper

  beforeEach(async () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: mockProjects() } })
    definitionApi.listDefinitions.mockResolvedValueOnce({ data: { records: mockRules() } })
    variableApi.listVariablesByProject.mockResolvedValueOnce({ data: mockVariables() })

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
        'el-loading': makeStub('div'), 'monaco-editor': makeStub('div'),
        'trace-tree': makeStub('div'), 'var-picker': makeStub('div'),
        'script-panel': makeStub('div')
      }
    })

    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('modelTypeLabel 返回正确的模型类型标签', () => {
    expect(wrapper.vm.modelTypeLabel('TABLE')).toBe('决策表')
    expect(wrapper.vm.modelTypeLabel('TREE')).toBe('决策树')
    expect(wrapper.vm.modelTypeLabel('FLOW')).toBe('决策流')
    expect(wrapper.vm.modelTypeLabel('SCORE')).toBe('评分卡')
    expect(wrapper.vm.modelTypeLabel('CROSS')).toBe('交叉表')
    expect(wrapper.vm.modelTypeLabel('SCRIPT')).toBe('QL 脚本')
  })

  test('modelTypeLabel 对未知类型返回原值', () => {
    expect(wrapper.vm.modelTypeLabel('UNKNOWN')).toBe('UNKNOWN')
  })
})

describe('RuleTest — 执行与结果展示', () => {
  let wrapper

  beforeEach(async () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: mockProjects() } })
    definitionApi.listDefinitions.mockResolvedValueOnce({ data: { records: mockRules() } })
    variableApi.listVariablesByProject.mockResolvedValueOnce({ data: mockVariables() })

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
        'el-loading': makeStub('div'), 'monaco-editor': makeStub('div'),
        'trace-tree': makeStub('div'), 'var-picker': makeStub('div'),
        'script-panel': makeStub('div')
      }
    })

    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('handleExecute 成功时设置 testResult', async () => {
    definitionApi.executeRule.mockResolvedValueOnce({ data: mockExecutionResult() })

    wrapper.vm.selectedRule = { id: 1, ruleCode: 'age_rule' }
    wrapper.vm.testParams = { age: 30 }
    wrapper.vm.$message = { success: jest.fn(), error: jest.fn() }

    await wrapper.vm.handleExecute()
    await Vue.nextTick()

    expect(wrapper.vm.testResult).toBeDefined()
    expect(wrapper.vm.testResult.success).toBe(true)
  })

  test('handleExecute 失败时设置错误信息', async () => {
    definitionApi.executeRule.mockRejectedValueOnce(new Error('规则编译失败'))
    wrapper.vm.$message = { success: jest.fn(), error: jest.fn() }

    wrapper.vm.selectedRule = { id: 1, ruleCode: 'age_rule' }
    wrapper.vm.testParams = { age: 30 }

    await wrapper.vm.handleExecute()
    await Vue.nextTick()

    expect(wrapper.vm.testResult).toBeNull()
    expect(wrapper.vm.$message.error).toHaveBeenCalled()
  })

  test('formatResult 格式化输出结果', () => {
    const outputs = { age: 30, income: 5000, decision: 'PASS' }
    const formatted = wrapper.vm.formatResult(outputs)
    expect(formatted).toContain('age')
    expect(formatted).toContain('30')
    expect(formatted).toContain('decision')
  })
})