// tests/unit/views/decisionTable.spec.js
import { mount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'
import ElementUI from 'element-ui'

// 直接 mock API 模块（避免真实 axios 依赖链）
jest.mock('@/api/definition', () => ({
  getDefinition: jest.fn(),
  getContent: jest.fn(),
  listDefinitions: jest.fn(),
  createDefinition: jest.fn(),
  updateDefinition: jest.fn(),
  deleteDefinition: jest.fn(),
  saveContent: jest.fn()
}))

jest.mock('@/api/variable', () => ({
  listVariablesByProject: jest.fn(),
  getVariableOptions: jest.fn()
}))

jest.mock('@/api/model', () => ({
  listModelInputs: jest.fn(),
  listModelOutputs: jest.fn()
}))

jest.mock('@/api/dataObject', () => ({
  getVariableTree: jest.fn(),
  getDataObjectFieldOptions: jest.fn()
}))

jest.mock('@/api/function', () => ({
  listAllFunctionsByProject: jest.fn()
}))

import * as definitionApi from '@/api/definition'
import * as variableApi from '@/api/variable'
import * as modelApi from '@/api/model'
import * as dataObjectApi from '@/api/dataObject'
import * as functionApi from '@/api/function'
import DecisionTable from '@/views/designer/DecisionTable.vue'

afterEach(() => { jest.clearAllMocks() })

// ─── Mock 数据 ───────────────────────────────────────────
function mockRuleContent(id) {
  return {
    id,
    name: '测试决策表',
    content: JSON.stringify({
      name: '测试决策表',
      rules: [
        {
          id: 'rule-1',
          conditionRoot: {
            id: 'cond-root-1',
            type: 'AND',
            children: [
              { id: 'leaf-1', varCode: 'age', varLabel: '年龄', varType: 'Integer', operator: '==', value: '1' }
            ]
          },
          actions: [
            { id: 'act-1', varCode: 'result', varLabel: '结果', varType: 'STRING', actionDataType: 'ASSIGN', value: '100' }
          ]
        }
      ]
    })
  }
}

function mockProjectVars() {
  return [
    { id: 1, varCode: 'age', varLabel: '年龄', varType: 'Integer', varSource: 'INPUT', scriptName: 'age' },
    { id: 2, varCode: 'income', varLabel: '收入', varType: 'NUMBER', varSource: 'INPUT', scriptName: 'income' },
    { id: 3, varCode: 'cityCode', varLabel: '城市编码', varType: 'STRING', varSource: 'INPUT', scriptName: 'cityCode' },
    { id: 4, varCode: 'MAX_AGE', varLabel: '最大年龄', varType: 'Integer', varSource: 'CONSTANT', scriptName: 'MAX_AGE' },
    {
      id: 10, varCode: 'TaxRequest', varLabel: '税务请求', varType: 'OBJECT', varSource: 'OBJECT',
      scriptName: 'TaxRequest', objectCode: 'TaxRequest', objectLabel: '税务请求',
      children: [
        { id: 11, varCode: 'amount', varLabel: '金额', varType: 'NUMBER', varSource: 'OBJECT', scriptName: 'amount', objectCode: 'TaxRequest', objectLabel: '税务请求' },
        { id: 12, varCode: 'taxRate', varLabel: '税率', varType: 'NUMBER', varSource: 'OBJECT', scriptName: 'taxRate', objectCode: 'TaxRequest', objectLabel: '税务请求' }
      ]
    },
    { id: 20, varCode: 'level', varLabel: '等级', varType: 'ENUM', varSource: 'ENUM', scriptName: 'level',
      options: [{ label: '高', value: 'HIGH' }, { label: '中', value: 'MID' }, { label: '低', value: 'LOW' }] }
  ]
}

// mock getVariableTree 返回的数据格式（用于 object category）
function mockObjectTree() {
  return [
    {
      object: { objectCode: 'TaxRequest', objectLabel: '税务请求', scriptName: 'TaxRequest' },
      variables: [
        { varCode: 'amount', varLabel: '金额', varType: 'NUMBER', varSource: 'OBJECT', scriptName: 'amount', objectCode: 'TaxRequest', objectLabel: '税务请求' },
        { varCode: 'taxRate', varLabel: '税率', varType: 'NUMBER', varSource: 'OBJECT', scriptName: 'taxRate', objectCode: 'TaxRequest', objectLabel: '税务请求' }
      ]
    }
  ]
}

// ─── 测试用例 ─────────────────────────────────────────────
function createTestVue() {
  const localVue = createLocalVue()
  localVue.use(ElementUI)
  return localVue
}

// 挂载 DecisionTable 并等待 loadProjectVars 完成（异步加载变量数据）
// 注意：loadProjectVars(definitionId) 接受 definitionId 参数，从路由 params.id 获取
async function mountAndWaitForRefs(propsData = { id: '1' }) {
  definitionApi.getContent.mockResolvedValue(mockRuleContent(1))
  definitionApi.getDefinition.mockResolvedValue({ data: { id: 1, projectId: 1, scope: 'PROJECT' } })
  variableApi.listVariablesByProject.mockResolvedValue({ data: mockProjectVars() })
  modelApi.listModelInputs.mockResolvedValue({ data: [] })
  modelApi.listModelOutputs.mockResolvedValue({ data: [] })
  // dataObject.getVariableTree 返回对象树（用于 object category）
  dataObjectApi.getVariableTree.mockResolvedValue({ data: mockObjectTree() })
  functionApi.listAllFunctionsByProject.mockResolvedValue({ data: [] })

  const wrapper = mount(DecisionTable, {
    localVue: createTestVue(),
    propsData,
    mocks: {
      $route: { params: { id: 1 }, query: {}, name: 'DecisionTable' },
      $router: { push: jest.fn(), replace: jest.fn() }
    },
    stubs: {
      'el-dialog': true, 'el-descriptions': true, 'el-descriptions-item': true,
      'el-button': true, 'el-button-group': true, 'el-select': true, 'el-option': true,
      'el-input': true, 'el-input-number': true,
      'el-form': true, 'el-form-item': true,
      'el-tag': true, 'el-tooltip': true,
      'el-divider': true, 'el-alert': true,
      'el-table': true, 'el-table-column': true,
      'el-pagination': true, 'el-loading': true,
      'el-radio-group': true, 'el-radio-button': true,
      'el-switch': true, 'el-card': true
    }
  })

  await Vue.nextTick()
  // 手动调用 loadProjectVars(definitionId) 等待变量数据加载完成
  await wrapper.vm.loadProjectVars(1)
  await Vue.nextTick()
  return wrapper
}

// ─── 辅助：按 category 从 projectRefs 数组中提取子集 ───────
// projectRefs 是扁平数组，每个元素有 category: 'standalone'|'constant'|'object'
function getRefsByCategory(wrapper, category) {
  return wrapper.vm.projectRefs.filter(r => r.category === category)
}

describe('DecisionTable — 变量选择器加载', () => {
  let wrapper

  beforeEach(async () => {
    wrapper = await mountAndWaitForRefs()
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('loadProjectVars 完成后 projectRefs 非空（扁平数组）', () => {
    expect(wrapper.vm.projectRefs).toBeInstanceOf(Array)
    expect(wrapper.vm.projectRefs.length).toBeGreaterThan(0)
  })

  test('projectRefs 包含 standalone 类别的变量', () => {
    const standalone = getRefsByCategory(wrapper, 'standalone')
    expect(standalone.length).toBeGreaterThan(0)
  })

  test('projectRefs 包含 constant 类别的常量', () => {
    const constant = getRefsByCategory(wrapper, 'constant')
    expect(constant.length).toBeGreaterThan(0)
  })

  test('projectRefs 包含 object 类别的对象字段', () => {
    const object = getRefsByCategory(wrapper, 'object')
    expect(object.length).toBeGreaterThan(0)
  })

  test('standalone 变量的 refCode 使用 scriptName', () => {
    const standalone = getRefsByCategory(wrapper, 'standalone')
    const ageVar = standalone.find(v => v.varObj && v.varObj.varCode === 'age')
    expect(ageVar).toBeDefined()
    expect(ageVar.refCode).toBe('age')
  })

  test('constant 的 refCode 使用 scriptName', () => {
    const constant = getRefsByCategory(wrapper, 'constant')
    const maxAgeVar = constant.find(v => v.varObj && v.varObj.varCode === 'MAX_AGE')
    expect(maxAgeVar).toBeDefined()
    expect(maxAgeVar.refCode).toBe('MAX_AGE')
  })

  test('object 中包含数据对象条目（varType=OBJECT）', () => {
    const object = getRefsByCategory(wrapper, 'object')
    // 查找 TaxRequest.amount 字段
    const amountField = object.find(v => v.refCode === 'TaxRequest.amount')
    expect(amountField).toBeDefined()
    expect(amountField.objectCode).toBe('TaxRequest')
  })

  test('varPickerOptions 包含变量选项（每项含 varCode 和 varLabel）', () => {
    const options = wrapper.vm.varPickerOptions
    expect(options).toBeInstanceOf(Array)
    expect(options.length).toBeGreaterThan(0)
    const ageOpt = options.find(o => o.varCode === 'age')
    expect(ageOpt).toBeDefined()
    // varLabel 格式为 "标签 code"，如 "年龄 age"
    expect(ageOpt.varLabel).toMatch(/年龄.*age/)
  })

  test('varPickerOptions 每项的 varLabel 包含 varCode（格式为 "varLabel varCode"）', () => {
    const options = wrapper.vm.varPickerOptions
    const ageOpt = options.find(o => o.varCode === 'age')
    expect(ageOpt).toBeDefined()
    expect(ageOpt.varLabel).toMatch(/年龄.*age/)
  })

  test('varPickerOptions 中对象字段的 varLabel 包含对象路径', () => {
    const options = wrapper.vm.varPickerOptions
    const amountOpt = options.find(o => o.varCode === 'TaxRequest.amount')
    expect(amountOpt).toBeDefined()
    expect(amountOpt.varLabel).toMatch(/税务请求/)
  })
})

describe('DecisionTable — 模型加载与同步', () => {
  let wrapper

  beforeEach(async () => {
    wrapper = await mountAndWaitForRefs()
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('model.rules 正确解析', () => {
    expect(wrapper.vm.model).toBeDefined()
    expect(wrapper.vm.model.rules).toBeInstanceOf(Array)
    expect(wrapper.vm.model.rules.length).toBe(1)
  })

  test('每条规则包含 conditionRoot 和 actions', () => {
    const rule = wrapper.vm.model.rules[0]
    expect(rule).toHaveProperty('conditionRoot')
    expect(rule).toHaveProperty('actions')
    expect(rule.actions).toBeInstanceOf(Array)
  })

  test('conditionRoot.children 包含叶节点', () => {
    const rule = wrapper.vm.model.rules[0]
    expect(rule.conditionRoot.children).toBeInstanceOf(Array)
    expect(rule.conditionRoot.children.length).toBeGreaterThan(0)
  })

  test('syncVarItem 通过 _varId 同步 varCode', () => {
    const leaf = { _varId: 2 }
    wrapper.vm.syncVarItem(leaf)
    expect(leaf.varCode).toBe('income')
  })
})

describe('DecisionTable — 变量选择器使用流程', () => {
  let wrapper

  beforeEach(async () => {
    wrapper = await mountAndWaitForRefs()
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('getVarByCode 通过 refCode 查找变量', () => {
    const varItem = wrapper.vm.getVarByCode('age')
    expect(varItem).toBeDefined()
    expect(varItem.varCode).toBe('age')
    expect(varItem.varType).toBe('Integer')
  })

  test('getVarOptions 对枚举类型返回选项列表', () => {
    const options = wrapper.vm.getVarOptions('level')
    expect(options).toBeInstanceOf(Array)
    expect(options.length).toBe(3)
    expect(options.find(o => o.value === 'HIGH')).toBeDefined()
  })

  test('findRefByVarId 通过 varId 精确匹配', () => {
    // 变量 age 的 id 为 1
    const ref = wrapper.vm.findRefByVarId(1)
    expect(ref).toBeDefined()
    expect(ref.refCode).toBe('age')
  })

  test('syncVarItem 通过 _varId 精确匹配更新 varCode', () => {
    const leaf = { _varId: 2 } // 变量 income 的 id 为 2
    wrapper.vm.syncVarItem(leaf)
    expect(leaf.varCode).toBe('income')
  })
})

describe('DecisionTable — 操作方法', () => {
  let wrapper

  beforeEach(async () => {
    wrapper = await mountAndWaitForRefs()
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('addRule 增加一条规则', () => {
    const initialCount = wrapper.vm.model.rules.length
    wrapper.vm.addRule()
    expect(wrapper.vm.model.rules.length).toBe(initialCount + 1)
  })

  test('removeRule 删除指定索引的规则', () => {
    const initialCount = wrapper.vm.model.rules.length
    wrapper.vm.removeRule(0)
    expect(wrapper.vm.model.rules.length).toBe(initialCount - 1)
  })

  test('copyRule 复制指定索引的规则', () => {
    const initialCount = wrapper.vm.model.rules.length
    wrapper.vm.copyRule(0)
    expect(wrapper.vm.model.rules.length).toBe(initialCount + 1)
  })

  test('addRuleAction 为指定规则添加动作', () => {
    wrapper.vm.addRuleAction(0)
    const rule = wrapper.vm.model.rules[0]
    expect(rule.actions.length).toBe(2)
  })

  test('removeRuleAction 删除指定规则的动作', () => {
    const initialCount = wrapper.vm.model.rules[0].actions.length
    wrapper.vm.removeRuleAction(0, 0)
    expect(wrapper.vm.model.rules[0].actions.length).toBe(initialCount - 1)
  })
})

describe('DecisionTable — testVarCodeList 和测试弹窗', () => {
  let wrapper

  beforeEach(async () => {
    wrapper = await mountAndWaitForRefs()
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('testVarCodeList 从条件树收集变量编码（去重）', () => {
    const varCodes = wrapper.vm.testVarCodeList
    expect(varCodes).toBeInstanceOf(Array)
    expect(varCodes.includes('age')).toBe(true)
  })

  test('testVarMeta 返回变量元信息', () => {
    const meta = wrapper.vm.testVarMeta
    expect(meta).toBeInstanceOf(Object)
    expect(meta).toHaveProperty('age')
  })
})

describe('DecisionTable — 保存功能', () => {
  let wrapper

  beforeEach(async () => {
    wrapper = await mountAndWaitForRefs()
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('handleSave 保存内容后触发回调', async () => {
    definitionApi.saveContent.mockResolvedValue({ data: true })
    const callback = jest.fn()
    await wrapper.vm.handleSave(callback)
    expect(definitionApi.saveContent).toHaveBeenCalled()
    expect(callback).toHaveBeenCalled()
  })

  test('handleSave 失败时不触发回调', async () => {
    definitionApi.saveContent.mockRejectedValue(new Error('保存失败'))
    const callback = jest.fn()
    const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {})
    await wrapper.vm.handleSave(callback)
    expect(callback).not.toHaveBeenCalled()
    consoleSpy.mockRestore()
  })
})

describe('DecisionTable — 规则复制后 _varId 保持一致', () => {
  let wrapper

  beforeEach(async () => {
    wrapper = await mountAndWaitForRefs()
  })

  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('复制规则后源规则和复制规则的 _varId 一致', () => {
    const originalRule = wrapper.vm.model.rules[0]
    const originalLeaf = originalRule.conditionRoot.children[0]
    const originalVarId = originalLeaf._varId

    wrapper.vm.copyRule(0)
    const copiedRule = wrapper.vm.model.rules[1]
    const copiedLeaf = copiedRule.conditionRoot.children[0]
    expect(copiedLeaf._varId).toBe(originalVarId)
  })
})