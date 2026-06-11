// tests/unit/views/ruleTest.spec.js
import { mount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'

// 直接 mock API 模块（避免真实 axios 依赖链）
jest.mock('@/api/definition', () => ({
  listDefinitions: jest.fn(),
  getDefinition: jest.fn(),
  getContent: jest.fn(),
  executeRule: jest.fn()
}))

jest.mock('@/api/variable', () => ({
  listVariablesByProject: jest.fn(),
  getVariableOptions: jest.fn(),
  listVariables: jest.fn()
}))

jest.mock('@/api/project', () => ({
  listProjects: jest.fn()
}))

jest.mock('@/api/function', () => ({
  listAllFunctionsByProject: jest.fn()
}))

import * as definitionApi from '@/api/definition'
import * as variableApi from '@/api/variable'
import * as projectApi from '@/api/project'
import RuleTest from '@/views/test/RuleTest.vue'

afterEach(() => { jest.clearAllMocks() })

// ─── Mock 数据 ───────────────────────────────────────────
// 注意：组件通过 res.data.records 获取列表数据
function mockProjects() {
  return [
    { id: 1, projectName: '项目A', code: 'project_a' },
    { id: 2, projectName: '项目B', code: 'project_b' }
  ]
}

function mockRules() {
  return [
    { id: 1, ruleName: '年龄规则', ruleCode: 'age_rule', modelType: 'TABLE', projectId: 1, scope: 'PROJECT', currentVersion: 1, status: 0 },
    { id: 2, ruleName: '评分卡', ruleCode: 'score_card', modelType: 'SCORE', projectId: 1, scope: 'PROJECT', currentVersion: 1, status: 1 },
    { id: 3, ruleName: '决策树', ruleCode: 'decision_tree', modelType: 'TREE', projectId: 0, scope: 'GLOBAL', currentVersion: 2, status: 1 }
  ]
}

function mockVariables() {
  return [
    { id: 1, varCode: 'age', varLabel: '年龄', varType: 'INTEGER', varSource: 'INPUT', scriptName: 'age' },
    { id: 2, varCode: 'score', varLabel: '评分', varType: 'DOUBLE', varSource: 'INPUT', scriptName: 'score' },
    { id: 3, varCode: 'level', varLabel: '等级', varType: 'STRING', varSource: 'INPUT', scriptName: 'level' }
  ]
}

function mockModelJson() {
  return JSON.stringify({
    modelType: 'TABLE',
    rules: [{
      conditionRoot: { type: 'GROUP', logic: 'AND', children: [{ type: 'LEAF', _varId: 1, operator: '>', value: '18' }] },
      actions: [{ varCode: 'result', actionType: 'ASSIGN', value: 'PASS' }]
    }]
  })
}

function mockExecutionResult() {
  return {
    success: true,
    executeTimeMs: 15,
    result: { output: 'PASS' },
    traces: [{ step: 1, expression: 'age > 18' }]
  }
}

// ─── 测试用例 ─────────────────────────────────────────────
function createTestVue() {
  const localVue = createLocalVue()
  return localVue
}

describe('RuleTest — 初始化与数据加载', () => {
  let wrapper

  beforeEach(async () => {
    // 先设置 mock，再挂载组件（确保 created 钩子中的异步调用能拿到 mock）
    projectApi.listProjects.mockResolvedValue({ data: { records: mockProjects() } })
    definitionApi.listDefinitions.mockResolvedValue({ data: { records: mockRules() } })
    variableApi.listVariablesByProject.mockResolvedValue({ data: { records: mockVariables() } })

    wrapper = mount(RuleTest, {
      localVue: createTestVue(),
      stubs: {
        'el-descriptions': true, 'el-descriptions-item': true,
        'el-radio-group': true, 'el-radio-button': true,
        'el-form': true, 'el-form-item': true,
        'el-select': true, 'el-option': true,
        'el-input': true, 'el-button': true, 'el-tag': true,
        'el-alert': true, 'el-tabs': true, 'el-tab-pane': true,
        'el-collapse': true, 'el-collapse-item': true,
        'monaco-editor': true, 'trace-tree': true
      }
    })

    // 等待 created 中的异步 API 调用完成
    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('created 阶段加载项目列表', () => {
    expect(projectApi.listProjects).toHaveBeenCalled()
  })

  test('created 阶段加载规则列表（ALL 模式不带 scope 参数）', () => {
    // 组件实际行为：ALL 模式调用 listDefinitions({ pageNum: 1, pageSize: 1000 }) 不带 scope
    expect(definitionApi.listDefinitions).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 1000 }))
  })

  test('projects 数据正确赋值（从 res.data.records 读取）', () => {
    expect(wrapper.vm.projects).toBeInstanceOf(Array)
    expect(wrapper.vm.projects.length).toBe(2)
    expect(wrapper.vm.projects[0].projectName).toBe('项目A')
  })

  test('rules 数据正确赋值（从 res.data.records 读取）', () => {
    expect(wrapper.vm.rules).toBeInstanceOf(Array)
    expect(wrapper.vm.rules.length).toBe(3)
    expect(wrapper.vm.rules[0].ruleName).toBe('年龄规则')
  })

  test('ruleScope 默认值为 ALL', () => {
    expect(wrapper.vm.ruleScope).toBe('ALL')
  })

  test('params 初始化为空数组', () => {
    expect(wrapper.vm.params).toBeInstanceOf(Array)
    expect(wrapper.vm.params.length).toBe(0)
  })

  test('result 初始化为 null', () => {
    expect(wrapper.vm.result).toBeNull()
  })

  test('executing 初始化为 false', () => {
    expect(wrapper.vm.executing).toBe(false)
  })
})

describe('RuleTest — 规则筛选与切换', () => {
  let wrapper

  beforeEach(async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: mockProjects() } })
    definitionApi.listDefinitions.mockResolvedValue({ data: { records: mockRules() } })

    wrapper = mount(RuleTest, {
      localVue: createTestVue(),
      stubs: {
        'el-descriptions': true, 'el-descriptions-item': true,
        'el-radio-group': true, 'el-radio-button': true,
        'el-form': true, 'el-form-item': true,
        'el-select': true, 'el-option': true,
        'el-input': true, 'el-button': true, 'el-tag': true,
        'el-alert': true, 'el-tabs': true, 'el-tab-pane': true,
        'el-collapse': true, 'el-collapse-item': true,
        'monaco-editor': true, 'trace-tree': true
      }
    })

    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('onScopeChange 切换到 GLOBAL 时重新加载规则（传 scope=GLOBAL）', async () => {
    // onScopeChange() 读取 this.ruleScope（v-model），需要先修改 v-model 再调用方法
    wrapper.vm.ruleScope = 'GLOBAL'
    wrapper.vm.onScopeChange()
    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 50))
    expect(definitionApi.listDefinitions).toHaveBeenLastCalledWith(expect.objectContaining({ scope: 'GLOBAL' }))
  })

  test('onScopeChange 切换到 PROJECT 时清空 rules 和 selectedRule', async () => {
    const initialRules = [...wrapper.vm.rules]
    expect(initialRules.length).toBeGreaterThan(0)
    wrapper.vm.ruleScope = 'PROJECT'
    wrapper.vm.onScopeChange()
    await Vue.nextTick()
    expect(wrapper.vm.rules).toEqual([])
    expect(wrapper.vm.selectedRule).toBeNull()
    expect(wrapper.vm.selectedRuleId).toBeNull()
  })

  test('onProjectChange 切换项目后重新加载规则（带 projectId）', async () => {
    definitionApi.listDefinitions.mockResolvedValue({ data: { records: [] } })
    // onProjectChange() 读取 this.selectedProjectId，需要先设置
    wrapper.vm.selectedProjectId = 1
    wrapper.vm.onProjectChange()
    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 50))
    expect(definitionApi.listDefinitions).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: 1 }))
  })

  test('onRuleChange 选择规则后 selectedRule 正确赋值', async () => {
    // rules 已在 beforeEach 中通过 mock 填充
    wrapper.vm.onRuleChange(1)
    await Vue.nextTick()
    expect(wrapper.vm.selectedRule).toBeDefined()
    expect(wrapper.vm.selectedRule.ruleCode).toBe('age_rule')
    expect(wrapper.vm.selectedRuleId).toBe(1)
  })

  test('onRuleChange 清空选择后 selectedRule 为 null', async () => {
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.onRuleChange(null)
    await Vue.nextTick()
    expect(wrapper.vm.selectedRule).toBeNull()
  })
})

describe('RuleTest — 参数管理', () => {
  let wrapper

  beforeEach(async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: mockProjects() } })
    definitionApi.listDefinitions.mockResolvedValue({ data: { records: mockRules() } })
    definitionApi.getContent.mockResolvedValue({ data: { modelJson: mockModelJson() } })
    variableApi.listVariablesByProject.mockResolvedValue({ data: { records: mockVariables() } })

    wrapper = mount(RuleTest, {
      localVue: createTestVue(),
      stubs: {
        'el-descriptions': true, 'el-descriptions-item': true,
        'el-radio-group': true, 'el-radio-button': true,
        'el-form': true, 'el-form-item': true,
        'el-select': true, 'el-option': true,
        'el-input': true, 'el-button': true, 'el-tag': true,
        'el-alert': true, 'el-tabs': true, 'el-tab-pane': true,
        'el-collapse': true, 'el-collapse-item': true,
        'monaco-editor': true, 'trace-tree': true
      }
    })

    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('addParam 添加一个空参数行', () => {
    wrapper.vm.addParam()
    expect(wrapper.vm.params.length).toBe(1)
    expect(wrapper.vm.params[0].key).toBe('')
    expect(wrapper.vm.params[0].value).toBe('')
  })

  test('loadVariables 为选中规则加载变量到 params', async () => {
    wrapper.vm.selectedRule = mockRules()[0]
    wrapper.vm.selectedRuleId = 1
    await wrapper.vm.loadVariables()
    await Vue.nextTick()
    expect(definitionApi.getContent).toHaveBeenCalled()
    expect(wrapper.vm.params.length).toBeGreaterThan(0)
  })

  test('handleClear 清空 params 和 result', async () => {
    wrapper.vm.params = [{ key: 'age', value: '20' }]
    wrapper.vm.result = { success: true }
    wrapper.vm.handleClear()
    expect(wrapper.vm.params.length).toBe(0)
    expect(wrapper.vm.result).toBeNull()
  })

  test('loadVarMap 加载变量映射到 varMap', async () => {
    variableApi.listVariables.mockResolvedValue({ data: { records: mockVariables() } })
    await wrapper.vm.loadVarMap()
    await Vue.nextTick()
    expect(Object.keys(wrapper.vm.varMap).length).toBeGreaterThan(0)
  })
})

describe('RuleTest — 规则执行', () => {
  let wrapper

  beforeEach(async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: mockProjects() } })
    definitionApi.listDefinitions.mockResolvedValue({ data: { records: mockRules() } })
    variableApi.listVariablesByProject.mockResolvedValue({ data: { records: mockVariables() } })
    definitionApi.executeRule.mockResolvedValue({ data: mockExecutionResult() })

    wrapper = mount(RuleTest, {
      localVue: createTestVue(),
      stubs: {
        'el-descriptions': true, 'el-descriptions-item': true,
        'el-radio-group': true, 'el-radio-button': true,
        'el-form': true, 'el-form-item': true,
        'el-select': true, 'el-option': true,
        'el-input': true, 'el-button': true, 'el-tag': true,
        'el-alert': true, 'el-tabs': true, 'el-tab-pane': true,
        'el-collapse': true, 'el-collapse-item': true,
        'monaco-editor': true, 'trace-tree': true
      }
    })

    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('handleExecute 执行选中规则', async () => {
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.params = [
      { key: 'age', value: '20', type: 'INTEGER' },
      { key: 'score', value: '85.5', type: 'DOUBLE' }
    ]
    await wrapper.vm.handleExecute()
    expect(definitionApi.executeRule).toHaveBeenCalled()
  })

  test('handleExecute 参数构造正确（params 数组转 key-value 对象）', async () => {
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.params = [{ key: 'age', value: '20' }]
    await wrapper.vm.handleExecute()
    expect(definitionApi.executeRule).toHaveBeenCalledWith(
      expect.objectContaining({ id: 1, params: expect.objectContaining({ age: '20' }) })
    )
  })

  test('执行成功时 result 正确赋值', async () => {
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.params = []
    await wrapper.vm.handleExecute()
    await Vue.nextTick()
    expect(wrapper.vm.result).toBeDefined()
    expect(wrapper.vm.result.success).toBe(true)
  })

  test('执行失败时 errorMessage 正确显示', async () => {
    definitionApi.executeRule.mockResolvedValueOnce({ data: { success: false, errorMessage: '规则编译失败' } })
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.params = []
    await wrapper.vm.handleExecute()
    await Vue.nextTick()
    expect(wrapper.vm.result).toBeDefined()
    expect(wrapper.vm.result.success).toBe(false)
  })

  test('执行中 executing 状态正确', async () => {
    let resolveFn
    definitionApi.executeRule.mockImplementation(() => new Promise(r => { resolveFn = r }))
    wrapper.vm.selectedRuleId = 1
    wrapper.vm.params = []
    const executePromise = wrapper.vm.handleExecute()
    expect(wrapper.vm.executing).toBe(true)
    resolveFn({ data: mockExecutionResult() })
    await executePromise
    await Vue.nextTick()
    expect(wrapper.vm.executing).toBe(false)
  })
})