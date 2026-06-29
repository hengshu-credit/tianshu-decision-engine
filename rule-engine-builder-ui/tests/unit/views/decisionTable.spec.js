// tests/unit/views/decisionTable.spec.js
import { shallowMount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'
import ElementUI from 'element-ui'

// 直接 import API 模块（不写 jest.mock，依赖 setup.js 的预置 mock）
import * as definitionApi from '@/api/definition'
import * as variableApi from '@/api/variable'
import * as modelApi from '@/api/model'
import * as dataObjectApi from '@/api/dataObject'
import * as functionApi from '@/api/function'
import DecisionTable from '@/views/designer/DecisionTable.vue'

afterEach(() => { jest.clearAllMocks() })

// ─── Mock 数据 ───────────────────────────────────────────
const mockDefs = { id: 1, projectId: 1, scope: 'PROJECT' }

/**
 * 决策表规则内容。
 * 叶节点 type='leaf'（非 group），conditionRoot.children 直接包含叶节点。
 */
function mockRuleContent(id) {
  return {
    id,
    name: '测试决策表',
    modelJson: JSON.stringify({
      name: '测试决策表',
      hitPolicy: 'FIRST',
      rules: [
        {
          id: 'rule-1',
          conditionRoot: {
            id: 'cond-root-1',
            type: 'group',
            op: 'AND',
            children: [
              { id: 'leaf-1', _varId: 1, varCode: 'age', varLabel: '年龄 age', varType: 'STRING', operator: '==', value: '1', type: 'leaf' }
            ]
          },
          actions: [
            { id: 'act-1', _varId: 5, varCode: 'result', varLabel: '结果 result', varType: 'STRING', actionDataType: 'ASSIGN', value: '100' }
          ]
        }
      ]
    })
  }
}

function mockProjectVars() {
  return [
    { id: 1, varCode: 'age', varLabel: '年龄', varType: 'STRING', varSource: 'INPUT', scriptName: 'age' },
    { id: 2, varCode: 'income', varLabel: '收入', varType: 'NUMBER', varSource: 'INPUT', scriptName: 'income' },
    { id: 3, varCode: 'cityCode', varLabel: '城市编码', varType: 'STRING', varSource: 'INPUT', scriptName: 'cityCode' },
    { id: 5, varCode: 'result', varLabel: '结果', varType: 'STRING', varSource: 'COMPUTED', scriptName: 'result' },
    {
      id: 4, varCode: 'MAX_AGE', varLabel: '最大年龄', varType: 'NUMBER', varSource: 'CONSTANT', scriptName: 'MAX_AGE'
    },
    {
      id: 10, varCode: 'TaxRequest', varLabel: '税务请求', varType: 'OBJECT', varSource: 'OBJECT',
      scriptName: 'TaxRequest', objectCode: 'TaxRequest', objectLabel: '税务请求',
      children: [
        { id: 11, varCode: 'amount', varLabel: '金额', varType: 'NUMBER', varSource: 'OBJECT', scriptName: 'amount' }
      ]
    },
    { id: 20, varCode: 'level', varLabel: '等级', varType: 'ENUM', varSource: 'ENUM', scriptName: 'level',
      options: [{ label: '高', value: 'HIGH' }, { label: '中', value: 'MID' }, { label: '低', value: 'LOW' }] }
  ]
}

function mockObjectTree() {
  return [
    {
      object: { objectCode: 'TaxRequest', objectLabel: '税务请求', scriptName: 'TaxRequest' },
      variables: [
        { varCode: 'amount', varLabel: '金额', varType: 'NUMBER', varSource: 'OBJECT', scriptName: 'amount' }
      ]
    }
  ]
}

function mockModels() {
  return [
    { id: 30, modelCode: 'CreditModel', modelName: '信用模型' }
  ]
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

// 挂载 DecisionTable 并等待 loadProjectVars 完成
async function mountAndWaitForRefs(propsData = { id: '1' }) {
  definitionApi.getDefinition.mockResolvedValue(mockDefs)
  definitionApi.getContent.mockResolvedValue(mockRuleContent(1))
  variableApi.listVariablesByProject.mockResolvedValue(mockProjectVars())
  modelApi.listModelInputs.mockResolvedValue([])
  modelApi.listModelOutputs.mockResolvedValue([])
  modelApi.listAllModelsByProject.mockResolvedValue(mockModels())
  dataObjectApi.getVariableTree.mockResolvedValue(mockObjectTree())
  functionApi.listAllFunctionsByProject.mockResolvedValue([])
  definitionApi.refreshFields.mockResolvedValue({})

  const wrapper = shallowMount(DecisionTable, {
    localVue: createTestVue(),
    propsData,
    mocks: {
      $route: { params: { id: 1 }, query: {}, name: 'DecisionTable' },
      $router: { push: jest.fn(), replace: jest.fn() }
    },
    stubs: {
      'el-dialog': makeStub('div'),
      'el-descriptions': makeStub('div'),
      'el-descriptions-item': makeStub('div'),
      'el-button': makeStub('button'),
      'el-button-group': makeStub('div'),
      'el-select': makeStub('select'),
      'el-option': makeStub('option'),
      'el-input': makeStub('input'),
      'el-input-number': makeStub('input'),
      'el-form': makeStub('form'),
      'el-form-item': makeStub('div'),
      'el-tag': makeStub('span'),
      'el-tooltip': makeStub('span'),
      'el-divider': makeStub('hr'),
      'el-alert': makeStub('div'),
      'el-table': makeStub('table'),
      'el-table-column': makeStub('td'),
      'el-pagination': makeStub('div'),
      'el-loading': makeStub('div'),
      'el-radio-group': makeStub('div'),
      'el-radio-button': makeStub('span'),
      'el-switch': makeStub('span'),
      'el-card': makeStub('div'),
      'script-panel': makeStub('div'),
      'var-picker': makeStub('div'),
      'trace-tree': makeStub('div'),
      'condition-group-editor': makeStub('div'),
      'action-block-editor': makeStub('div')
    }
  })

  await Vue.nextTick()
  // 手动触发 loadProjectVars（绕过 created 中的路由判断）
  await wrapper.vm.loadProjectVars(1)
  await Vue.nextTick()
  return wrapper
}

// ─── 辅助 ─────────────────────────────────────────────────
function getRefsByCategory(wrapper, category) {
  return wrapper.vm.projectRefs.filter(r => r.category === category)
}

// ═══════════════════════════════════════════════════════════════
// 变量选择器加载
// ═══════════════════════════════════════════════════════════════
describe('DecisionTable — 变量选择器加载', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWaitForRefs() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('loadProjectVars 完成后 projectRefs 非空', () => {
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

  test('projectRefs 包含 model 类别的模型', () => {
    const model = getRefsByCategory(wrapper, 'model')
    expect(model.length).toBeGreaterThan(0)
    expect(model[0].refType).toBe('MODEL')
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

  test('object 中包含数据对象字段（varType=OBJECT）', () => {
    const object = getRefsByCategory(wrapper, 'object')
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
    expect(ageOpt.varLabel).toMatch(/年龄.*age/)
  })

  test('varPickerOptions 中对象字段的 varLabel 包含对象路径', () => {
    const options = wrapper.vm.varPickerOptions
    const amountOpt = options.find(o => o.varCode === 'TaxRequest.amount')
    expect(amountOpt).toBeDefined()
    expect(amountOpt.varLabel).toMatch(/税务请求/)
  })

  test('selectedVarPickerOptions 汇总当前规则已选择的条件和动作字段', () => {
    const selected = wrapper.vm.selectedVarPickerOptions.map(o => o.varCode)
    expect(selected).toContain('age')
    expect(selected).toContain('result')
    expect(new Set(selected).size).toBe(selected.length)
  })
})

// ═══════════════════════════════════════════════════════════════
// 模型加载与同步
// ═══════════════════════════════════════════════════════════════
describe('DecisionTable — 模型加载与同步', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWaitForRefs() })
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

  test('conditionRoot.children 是非空数组', () => {
    const rule = wrapper.vm.model.rules[0]
    expect(rule.conditionRoot.children).toBeInstanceOf(Array)
    expect(rule.conditionRoot.children.length).toBeGreaterThan(0)
  })

  test('syncVarItem 通过 varCode 精确匹配更新 varLabel', () => {
    // mockRuleContent 中叶节点已含 varCode='age'，syncVarItem 补充 varLabel
    const leaf = { _varId: 1, varCode: 'age' }
    const result = wrapper.vm.syncVarItem(leaf)
    expect(result).toBe(true)
    expect(leaf.varLabel).toBe('年龄 age')
  })
})

// ═══════════════════════════════════════════════════════════════
// 变量选择器使用流程
// ═══════════════════════════════════════════════════════════════
describe('DecisionTable — 变量选择器使用流程', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWaitForRefs() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('getVarByCode 通过 refCode 查找变量', () => {
    const varItem = wrapper.vm.getVarByCode('age')
    expect(varItem).toBeDefined()
    expect(varItem.varCode).toBe('age')
  })

  test('getVarOptions 对无枚举变量的编码返回空数组', () => {
    const options = wrapper.vm.getVarOptions('nonExistent')
    expect(options).toBeInstanceOf(Array)
  })

  test('findRefByVarId 通过 varId 精确匹配', () => {
    const ref = wrapper.vm.findRefByVarId(1)
    expect(ref).toBeDefined()
    expect(ref.refCode).toBe('age')
  })

  test('syncVarItem 对仅有 varCode 的叶节点补充 varLabel', () => {
    // 模拟旧数据场景：叶节点只有 varCode，通过 syncVarItem 补充 varLabel
    const leaf = { _varId: 2, varCode: 'income' }
    const result = wrapper.vm.syncVarItem(leaf)
    expect(result).toBe(true)
    expect(leaf.varLabel).toBe('收入 income')
  })
})

// ═══════════════════════════════════════════════════════════════
// 操作方法
// ═══════════════════════════════════════════════════════════════
describe('DecisionTable — 操作方法', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWaitForRefs() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('addRule 增加一条规则', () => {
    const initialCount = wrapper.vm.model.rules.length
    wrapper.vm.addRule()
    expect(wrapper.vm.model.rules.length).toBe(initialCount + 1)
    const newRule = wrapper.vm.model.rules[wrapper.vm.model.rules.length - 1]
    expect(newRule).toHaveProperty('conditionRoot')
    expect(newRule).toHaveProperty('actions')
  })

  test('removeRule 删除指定索引的规则', () => {
    const initialCount = wrapper.vm.model.rules.length
    wrapper.vm.removeRule(0)
    expect(wrapper.vm.model.rules.length).toBe(Math.max(0, initialCount - 1))
  })

  test('copyRule 复制指定索引的规则', () => {
    const initialCount = wrapper.vm.model.rules.length
    wrapper.vm.copyRule(0)
    expect(wrapper.vm.model.rules.length).toBe(initialCount + 1)
    expect(() => JSON.stringify(wrapper.vm.model)).not.toThrow()
  })

  test('addRuleAction 为指定规则添加动作', () => {
    wrapper.vm.addRuleAction(0)
    const rule = wrapper.vm.model.rules[0]
    expect(rule.actions.length).toBe(2)
  })

  test('removeRuleAction 删除指定规则的动作（至少保留一条）', async () => {
    wrapper.vm.$confirm = jest.fn().mockResolvedValue()
    wrapper.vm.addRuleAction(0)
    wrapper.vm.addRuleAction(0)
    wrapper.vm.addRuleAction(0)
    // 初始1条 + 新增3条 = 4条，删除1条后剩3条
    await wrapper.vm.removeRuleAction(0, 0)
    expect(wrapper.vm.model.rules[0].actions.length).toBe(3)
  })

  test('removeRuleAction 动作只剩一条时提示至少保留一条', () => {
    wrapper.vm.$message = { warning: jest.fn() }
    wrapper.vm.removeRuleAction(0, 0)
    expect(wrapper.vm.$message.warning).toHaveBeenCalled()
  })
})

// ═══════════════════════════════════════════════════════════════
// 测试弹窗与参数构建
// ═══════════════════════════════════════════════════════════════
describe('DecisionTable — 测试弹窗与参数构建', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWaitForRefs() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('testVarCodeList 从条件树收集变量编码（去重）', () => {
    const varCodes = wrapper.vm.testVarCodeList
    expect(varCodes).toBeInstanceOf(Array)
    expect(varCodes.includes('age')).toBe(true)
  })

  test('testVarMeta(code) 返回变量类型信息', () => {
    const meta = wrapper.vm.testVarMeta('age')
    expect(meta).toBeDefined()
    expect(meta).toHaveProperty('varType')
  })

  test('buildTestParamsTemplate 构建测试参数模板', () => {
    const template = wrapper.vm.buildTestParamsTemplate()
    expect(template).toBeDefined()
    expect(template).toHaveProperty('age')
  })
})

// ═══════════════════════════════════════════════════════════════
// 保存功能
// ═══════════════════════════════════════════════════════════════
describe('DecisionTable — 保存功能', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWaitForRefs() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('handleSave 保存内容后显示成功提示', async () => {
    definitionApi.saveContent.mockResolvedValueOnce({ success: true })
    definitionApi.refreshFields.mockResolvedValueOnce({})
    definitionApi.compileRule.mockResolvedValueOnce({ success: true })
    wrapper.vm.$message = { success: jest.fn(), error: jest.fn() }
    await wrapper.vm.handleSave()
    expect(definitionApi.saveContent).toHaveBeenCalled()
    expect(definitionApi.refreshFields).toHaveBeenCalled()
    expect(wrapper.vm.$message.success).toHaveBeenCalledWith('保存成功')
  })

  test('handleSave 失败时显示错误提示并抛出异常', async () => {
    definitionApi.saveContent.mockRejectedValueOnce(new Error('保存失败'))
    definitionApi.refreshFields.mockRejectedValueOnce(new Error('refresh失败'))
    const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {})
    wrapper.vm.$message = { success: jest.fn(), error: jest.fn() }
    await expect(wrapper.vm.handleSave()).rejects.toThrow('保存失败')
    expect(definitionApi.saveContent).toHaveBeenCalled()
    expect(wrapper.vm.$message.error).toHaveBeenCalled()
    consoleSpy.mockRestore()
  })
})

// ═══════════════════════════════════════════════════════════════
// 规则复制
// ═══════════════════════════════════════════════════════════════
describe('DecisionTable — 规则复制', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWaitForRefs() })
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
