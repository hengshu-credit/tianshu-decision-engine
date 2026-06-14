// tests/unit/views/modelDetail.spec.js
import { mount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'

// 使用真实 Element UI（setup.js 的 element-ui mock 没有挂载到 Vue.prototype）
jest.unmock('element-ui')
import ElementUI from 'element-ui'

// Mock API 模块（覆盖 setup.js 的基础 jest.fn()，测试文件负责配置返回值）
jest.mock('@/api/model', () => ({
  getModel: jest.fn(),
  updateModelInputField: jest.fn(),
  updateModelOutputField: jest.fn(),
  executeModel: jest.fn(),
  getTestParams: jest.fn(),
  saveTestParams: jest.fn()
}))

jest.mock('@/api/variable', () => ({
  listVariablesByProject: jest.fn(),
  listVariables: jest.fn(),
  getVariableOptions: jest.fn()
}))

jest.mock('@/api/dataObject', () => ({
  getVariableTree: jest.fn(),
  getDataObjectFieldOptions: jest.fn()
}))

import * as modelApi from '@/api/model'
import * as variableApi from '@/api/variable'
import * as dataObjectApi from '@/api/dataObject'
import ModelDetail from '@/views/model/ModelDetail.vue'

afterEach(() => { jest.clearAllMocks() })

// ─── Mock 数据 ───────────────────────────────────────────
function mockModel(id = 1) {
  return {
    id,
    modelName: '风险评分模型',
    modelCode: 'risk_score_model',
    modelType: 'XGBOOST',
    modelFormat: 'PMML',
    scope: 'PROJECT',
    projectId: 1,
    projectName: '项目A',
    description: '用于评估客户风险的评分模型',
    modelFileName: 'risk_score.pmml',
    modelFileSize: 102400,
    currentVersion: 1,
    publishedVersion: 1,
    inputFields: [
      { id: 1, fieldName: 'age', fieldLabel: '年龄', fieldType: 'INTEGER', varId: 10, scriptName: 'age', missingValue: '18' },
      { id: 2, fieldName: 'income', fieldLabel: '收入', fieldType: 'DOUBLE', varId: null, scriptName: '', missingValue: '' }
    ],
    outputFields: [
      { id: 100, fieldName: 'score', fieldLabel: '评分', fieldType: 'DOUBLE', varId: 20, scriptName: 'score', transformType: 'SCALE' },
      { id: 101, fieldName: 'level', fieldLabel: '等级', fieldType: 'STRING', varId: null, scriptName: '', transformType: '' }
    ]
  }
}

function mockVars() {
  return [
    { id: 10, varCode: 'age', varLabel: '年龄', varType: 'STRING', varSource: 'INPUT', scriptName: 'age' },
    { id: 20, varCode: 'score', varLabel: '评分', varType: 'NUMBER', varSource: 'OUTPUT', scriptName: 'score' },
    { id: 30, varCode: 'balance', varLabel: '余额', varType: 'NUMBER', varSource: 'INPUT', scriptName: 'balance' }
  ]
}

function mockConsts() {
  return [
    { id: 100, varCode: 'MAX_AGE', varLabel: '最大年龄', varType: 'NUMBER', varSource: 'CONSTANT', scriptName: 'MAX_AGE' }
  ]
}

function mockObjectTree() {
  return [
    {
      object: { objectCode: 'TaxRequest', objectLabel: '税务请求', scriptName: 'TaxRequest' },
      variables: [
        { id: 200, varCode: 'amount', varLabel: '金额', varType: 'NUMBER', varSource: 'OBJECT', scriptName: 'amount' }
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

async function mountAndWait(modelData = mockModel()) {
  modelApi.getModel.mockResolvedValue({ data: modelData })
  variableApi.listVariablesByProject.mockResolvedValue({ data: mockVars() })
  variableApi.listVariables.mockResolvedValue({ data: { records: mockConsts() } })
  dataObjectApi.getVariableTree.mockResolvedValue(mockObjectTree())

  const wrapper = mount(ModelDetail, {
    localVue: createTestVue(),
    propsData: { id: '1' },
    mocks: {
      $route: { params: { id: 1 } },
      $router: { push: jest.fn(), replace: jest.fn() }
    },
    stubs: {
      'el-descriptions': true, 'el-descriptions-item': true,
      'el-button': true, 'el-button-group': true,
      'el-select': true, 'el-option': true,
      'el-input': true, 'el-input-number': true,
      'el-form': true, 'el-form-item': true,
      'el-tag': true, 'el-tooltip': true,
      'el-divider': true, 'el-alert': true,
      'el-table': true, 'el-table-column': true,
      'el-tabs': true, 'el-tab-pane': true,
      'el-dialog': true, 'el-card': true,
      'el-date-picker': true, 'el-loading': true,
      'monaco-editor': true
    }
  })

  await Vue.nextTick()
  await new Promise(r => setTimeout(r, 100))
  return wrapper
}

describe('ModelDetail — 初始化与数据加载', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('mounted 后调用 getModel', () => {
    expect(modelApi.getModel).toHaveBeenCalledWith(1)
  })

  test('model 数据正确赋值', () => {
    expect(wrapper.vm.model).toBeDefined()
    expect(wrapper.vm.model.modelName).toBe('风险评分模型')
    expect(wrapper.vm.model.modelCode).toBe('risk_score_model')
  })

  test('modelTypeLabel 返回正确的标签', () => {
    expect(wrapper.vm.modelTypeLabel('XGBOOST')).toBe('XGBoost')
    expect(wrapper.vm.modelTypeLabel('LR')).toBe('LR（逻辑回归）')
    expect(wrapper.vm.modelTypeLabel('RANDOM_FOREST')).toBe('RandomForest')
    expect(wrapper.vm.modelTypeLabel('NEURAL_NET')).toBe('NeuralNet（神经网络）')
    expect(wrapper.vm.modelTypeLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(wrapper.vm.modelTypeLabel(null)).toBe('—')
  })

  test('formatFileSize 正确格式化文件大小', () => {
    expect(wrapper.vm.formatFileSize(0)).toBe('-')
    expect(wrapper.vm.formatFileSize(500)).toBe('500 B')
    expect(wrapper.vm.formatFileSize(1024)).toBe('1.0 KB')
    expect(wrapper.vm.formatFileSize(1024 * 1024)).toBe('1.00 MB')
    expect(wrapper.vm.formatFileSize(null)).toBe('-')
    expect(wrapper.vm.formatFileSize(undefined)).toBe('-')
  })

  test('loading 初始值为 false（加载完成后）', () => {
    expect(wrapper.vm.loading).toBe(false)
  })

  test('testVisible 初始值为 false', () => {
    expect(wrapper.vm.testVisible).toBe(false)
  })
})

describe('ModelDetail — 变量关联加载', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('loadVarsByProject 调用正确的 API', async () => {
    variableApi.listVariablesByProject.mockResolvedValueOnce({ data: mockVars() })
    variableApi.listVariables.mockResolvedValueOnce({ data: { records: mockConsts() } })
    dataObjectApi.getVariableTree.mockResolvedValueOnce(mockObjectTree())

    await wrapper.vm.loadVarsByProject(1)
    await Vue.nextTick()

    expect(variableApi.listVariablesByProject).toHaveBeenCalledWith(1)
    expect(variableApi.listVariables).toHaveBeenCalled()
    expect(dataObjectApi.getVariableTree).toHaveBeenCalledWith(1)
  })

  test('buildVarOptions 正确构建 varMap 和 varPickerGroups', async () => {
    const vars = mockVars()
    const consts = mockConsts()
    const tree = mockObjectTree()

    wrapper.vm.buildVarOptions([...vars, ...consts], tree)

    expect(wrapper.vm.varMap).toBeDefined()
    expect(Object.keys(wrapper.vm.varMap).length).toBeGreaterThan(0)
    expect(wrapper.vm.varMap[10]).toBeDefined()
    expect(wrapper.vm.varMap[10].varCode).toBe('age')

    // varPickerGroups 三层结构
    expect(wrapper.vm.varPickerGroups.length).toBe(3)
    expect(wrapper.vm.varPickerGroups[0].label).toBe('普通变量')
    expect(wrapper.vm.varPickerGroups[1].label).toBe('常量')
    expect(wrapper.vm.varPickerGroups[2].label).toBe('数据对象字段')
  })

  test('buildVarOptions 中常量正确分组', async () => {
    const consts = mockConsts()
    wrapper.vm.buildVarOptions(consts, [])

    const constGroup = wrapper.vm.varPickerGroups.find(g => g.label === '常量')
    expect(constGroup.options.length).toBe(1)
    expect(constGroup.options[0].sourceType).toBe('constant')
    expect(constGroup.options[0].varCode).toBe('MAX_AGE')
  })

  test('buildVarOptions 中数据对象字段正确分组', async () => {
    const tree = mockObjectTree()
    wrapper.vm.buildVarOptions([], tree)

    const objGroup = wrapper.vm.varPickerGroups.find(g => g.label === '数据对象字段')
    expect(objGroup.options.length).toBe(1)
    expect(objGroup.options[0].sourceType).toBe('dataObject')
    expect(objGroup.options[0].varCode).toBe('amount')
  })

  test('buildVarOptions 中 id 去重', () => {
    const duplicateVars = [
      { id: 10, varCode: 'age1', varLabel: '年龄', varType: 'STRING', varSource: 'INPUT', scriptName: 'age1' },
      { id: 10, varCode: 'age2', varLabel: '年龄2', varType: 'STRING', varSource: 'INPUT', scriptName: 'age2' }
    ]
    wrapper.vm.buildVarOptions(duplicateVars, [])
    const varGroup = wrapper.vm.varPickerGroups.find(g => g.label === '普通变量')
    expect(varGroup.options.filter(o => o.id === 10).length).toBe(1)
  })

  test('loadGlobalVars 加载全局变量', async () => {
    variableApi.listVariables.mockResolvedValueOnce({ data: { records: mockVars() } })
    variableApi.listVariables.mockResolvedValueOnce({ data: { records: mockConsts() } })
    dataObjectApi.getVariableTree.mockResolvedValueOnce(mockObjectTree())

    await wrapper.vm.loadGlobalVars()
    await Vue.nextTick()

    expect(variableApi.listVariables).toHaveBeenCalled()
    expect(dataObjectApi.getVariableTree).toHaveBeenCalledWith(0)
  })

  test('onVarClear 清除关联变量', () => {
    const row = { varId: 10, fieldLabel: '年龄', scriptName: 'age' }
    wrapper.vm.onVarClear(row)
    expect(row.varId).toBeNull()
    expect(row.fieldLabel).toBe('')
    expect(row.scriptName).toBe('')
  })

  test('onVarChange 同步普通变量信息到行（依赖 v-model 已更新 varId）', () => {
    // buildVarOptions 将变量 id 映射为选项的 id 字段
    wrapper.vm.buildVarOptions(mockVars(), [])
    const row = { varId: null, fieldLabel: '', scriptName: '', varSource: '' }
    // v-model 已在 el-select @input 中更新了 row.varId，此处模拟该行为
    row.varId = 10
    wrapper.vm.onVarChange(row, 10)
    // onVarChange 不修改 varId（由 v-model 处理），只同步 fieldLabel / scriptName / varSource
    expect(row.fieldLabel).toBe('年龄')
    expect(row.scriptName).toBe('age')
    expect(row.varSource).toBe('variable')
  })

  test('onVarChange 选择常量时 sourceType 为 constant', () => {
    wrapper.vm.buildVarOptions(mockConsts(), [])
    const row = { varId: null, fieldLabel: '', scriptName: '' }
    wrapper.vm.onVarChange(row, 100)
    expect(row.varSource).toBe('constant')
  })

  test('onVarChange 选择数据对象字段时 sourceType 为 dataObject', () => {
    wrapper.vm.buildVarOptions([], mockObjectTree())
    const row = { varId: null, fieldLabel: '', scriptName: '' }
    wrapper.vm.onVarChange(row, 200)
    expect(row.varSource).toBe('dataObject')
  })
})

describe('ModelDetail — 输入字段编辑', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('editInputField 设置行的 _editing 标志', () => {
    const field = wrapper.vm.model.inputFields[0]
    wrapper.vm.editInputField(field)
    expect(field._editing).toBe(true)
  })

  test('editInputField 同时设置 _origin 原始数据', () => {
    const field = wrapper.vm.model.inputFields[0]
    wrapper.vm.editInputField(field)
    expect(field._origin).toBeDefined()
    expect(field._origin.varId).toBe(10)
    expect(field._origin.scriptName).toBe('age')
  })

  test('editInputField 取消其他行的编辑状态', () => {
    const field0 = wrapper.vm.model.inputFields[0]
    const field1 = wrapper.vm.model.inputFields[1]
    field1._editing = true
    wrapper.vm.editInputField(field0)
    expect(field1._editing).toBe(false)
  })

  test('cancelEditInput 恢复原始数据并取消编辑', () => {
    const field = wrapper.vm.model.inputFields[0]
    field._editing = true
    field._origin = { varId: 10, fieldLabel: '年龄', scriptName: 'age', missingValue: '18' }
    field.varId = 20
    field.scriptName = 'changed'
    wrapper.vm.cancelEditInput(field)
    expect(field.varId).toBe(10)
    expect(field.scriptName).toBe('age')
    expect(field._editing).toBe(false)
  })

  test('inputRowClassName 返回 editing-row 当 _editing 为 true', () => {
    const row = { _editing: true }
    expect(wrapper.vm.inputRowClassName({ row })).toBe('editing-row')
  })

  test('inputRowClassName 返回空字符串 当 _editing 为 false', () => {
    const row = { _editing: false }
    expect(wrapper.vm.inputRowClassName({ row })).toBe('')
  })
})

describe('ModelDetail — 输出字段编辑', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('editOutputField 设置行的 _editing 标志', () => {
    const field = wrapper.vm.model.outputFields[0]
    wrapper.vm.editOutputField(field)
    expect(field._editing).toBe(true)
  })

  test('editOutputField 同时设置 _origin 原始数据', () => {
    const field = wrapper.vm.model.outputFields[0]
    wrapper.vm.editOutputField(field)
    expect(field._origin).toBeDefined()
    expect(field._origin.varId).toBe(20)
    expect(field._origin.transformType).toBe('SCALE')
  })

  test('cancelEditOutput 恢复原始数据并取消编辑', () => {
    const field = wrapper.vm.model.outputFields[0]
    field._editing = true
    field._origin = { varId: 20, fieldLabel: '评分', scriptName: 'score', transformType: 'SCALE' }
    field.transformType = 'NONE'
    wrapper.vm.cancelEditOutput(field)
    expect(field.transformType).toBe('SCALE')
    expect(field._editing).toBe(false)
  })

  test('outputRowClassName 返回 editing-row 当 _editing 为 true', () => {
    const row = { _editing: true }
    expect(wrapper.vm.outputRowClassName({ row })).toBe('editing-row')
  })
})

describe('ModelDetail — 字段保存功能', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('saveInputField 调用 updateModelInputField 并更新状态', async () => {
    const field = wrapper.vm.model.inputFields[0]
    field._editing = true
    modelApi.updateModelInputField.mockResolvedValue({ data: true })

    await wrapper.vm.saveInputField(field)
    await Vue.nextTick()

    expect(modelApi.updateModelInputField).toHaveBeenCalled()
    expect(field._editing).toBe(false)
    expect(field._saving).toBe(false)
  })

  test('saveInputField 失败时显示错误消息', async () => {
    const field = wrapper.vm.model.inputFields[0]
    field._editing = true
    modelApi.updateModelInputField.mockRejectedValue(new Error('保存失败'))

    await wrapper.vm.saveInputField(field)
    await Vue.nextTick()

    expect(field._saving).toBe(false)
    expect(field._editing).toBe(true)
  })

  test('saveOutputField 调用 updateModelOutputField 并更新状态', async () => {
    const field = wrapper.vm.model.outputFields[0]
    field._editing = true
    modelApi.updateModelOutputField.mockResolvedValue({ data: true })

    await wrapper.vm.saveOutputField(field)
    await Vue.nextTick()

    expect(modelApi.updateModelOutputField).toHaveBeenCalled()
    expect(field._editing).toBe(false)
  })
})

describe('ModelDetail — 模型测试弹窗', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('openTestDialog 打开弹窗并重置状态', async () => {
    modelApi.getModel.mockResolvedValue({ data: mockModel() })
    modelApi.getTestParams.mockResolvedValue({ data: null })
    wrapper.vm.model = mockModel()

    wrapper.vm.openTestDialog()
    await Vue.nextTick()

    expect(wrapper.vm.testVisible).toBe(true)
    expect(wrapper.vm.testReady).toBe(false)
    expect(wrapper.vm.testResult).toBeNull()
    expect(wrapper.vm.testMode).toBe('manual')
  })

  test('switchToJsonMode 切换到 JSON 模式', () => {
    wrapper.vm.testMode = 'manual'
    wrapper.vm.syncParamsToJson = jest.fn()
    wrapper.vm.switchToJsonMode()
    expect(wrapper.vm.testMode).toBe('json')
  })

  test('switchToManualMode 切换到表单模式', () => {
    wrapper.vm.testMode = 'json'
    wrapper.vm.syncJsonToParams = jest.fn()
    wrapper.vm.switchToManualMode()
    expect(wrapper.vm.testMode).toBe('manual')
  })

  test('switchToJsonMode 相同模式不重复切换', () => {
    wrapper.vm.testMode = 'json'
    const syncFn = jest.fn()
    wrapper.vm.syncParamsToJson = syncFn
    wrapper.vm.switchToJsonMode()
    expect(syncFn).not.toHaveBeenCalled()
  })

  test('switchToManualMode 相同模式不重复切换', () => {
    wrapper.vm.testMode = 'manual'
    const syncFn = jest.fn()
    wrapper.vm.syncJsonToParams = syncFn
    wrapper.vm.switchToManualMode()
    expect(syncFn).not.toHaveBeenCalled()
  })

  test('syncParamsToJson 将 testParams 同步到 JSON', () => {
    wrapper.vm.testFields = [
      { fieldName: 'age', fieldType: 'INTEGER' },
      { fieldName: 'income', fieldType: 'DOUBLE' }
    ]
    wrapper.vm.testParams = { age: 30, income: 5000.5 }
    wrapper.vm.syncParamsToJson()
    const parsed = JSON.parse(wrapper.vm.testJsonStr)
    expect(parsed.age).toBe(30)
    expect(parsed.income).toBe(5000.5)
  })

  test('syncParamsToJson 空值转为 null', () => {
    wrapper.vm.testFields = [{ fieldName: 'name', fieldType: 'STRING' }]
    wrapper.vm.testParams = { name: '' }
    wrapper.vm.syncParamsToJson()
    const parsed = JSON.parse(wrapper.vm.testJsonStr)
    expect(parsed.name).toBeNull()
  })

  test('onJsonInput 检测 JSON 格式错误', () => {
    wrapper.vm.testJsonStr = 'not a json'
    wrapper.vm.onJsonInput()
    expect(wrapper.vm.jsonError).toContain('JSON 格式错误')
  })

  test('onJsonInput 有效 JSON 清除错误', () => {
    wrapper.vm.testJsonStr = '{"age": 30}'
    wrapper.vm.jsonError = 'previous error'
    wrapper.vm.onJsonInput()
    expect(wrapper.vm.jsonError).toBe('')
  })

  test('handleClearParams 重置所有参数', () => {
    wrapper.vm.testFields = [
      { fieldName: 'age', fieldType: 'INTEGER' },
      { fieldName: 'active', fieldType: 'BOOLEAN' },
      { fieldName: 'name', fieldType: 'STRING' }
    ]
    wrapper.vm.testParams = { age: 30, active: true, name: 'test' }
    wrapper.vm.testResult = { success: true }
    wrapper.vm.handleClearParams()
    expect(wrapper.vm.testParams.age).toBe(0)
    expect(wrapper.vm.testParams.active).toBe(false)
    expect(wrapper.vm.testParams.name).toBe('')
    expect(wrapper.vm.testResult).toBeNull()
  })

  test('formatResult 格式化对象输出', () => {
    const outputs = { score: 85.5, level: 'A' }
    const result = wrapper.vm.formatResult(outputs)
    expect(result).toContain('score')
    expect(result).toContain('85.5')
  })

  test('formatResult 非对象直接返回', () => {
    expect(wrapper.vm.formatResult('plain string')).toBe('plain string')
  })
})

describe('ModelDetail — 模型测试执行', () => {
  let wrapper

  beforeEach(async () => {
    wrapper = await mountAndWait()
    wrapper.vm.testFields = [
      { fieldName: 'age', fieldType: 'INTEGER' },
      { fieldName: 'score', fieldType: 'DOUBLE' }
    ]
    wrapper.vm.testParams = { age: 30, score: 85.5 }
    wrapper.vm.testJsonStr = '{"age": 30, "score": 85.5}'
  })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('doTest 成功执行后设置 testResult', async () => {
    modelApi.executeModel.mockResolvedValue({ data: { success: true, outputs: { result: 100 } } })
    wrapper.vm.testMode = 'manual'
    wrapper.vm.testVisible = true
    await wrapper.vm.doTest()
    expect(wrapper.vm.testResult).toBeDefined()
    expect(wrapper.vm.testResult.success).toBe(true)
    expect(wrapper.vm.testExecuting).toBe(false)
  })

  test('doTest JSON 模式解析参数', async () => {
    modelApi.executeModel.mockResolvedValue({ data: { success: true, outputs: {} } })
    wrapper.vm.testMode = 'json'
    wrapper.vm.testJsonStr = '{"age": 25}'
    await wrapper.vm.doTest()
    expect(modelApi.executeModel).toHaveBeenCalled()
  })

  test('doTest JSON 格式错误时显示错误', async () => {
    wrapper.vm.testMode = 'json'
    wrapper.vm.testJsonStr = 'invalid json'
    const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {})
    await wrapper.vm.doTest()
    expect(wrapper.vm.testExecuting).toBe(false)
    consoleSpy.mockRestore()
  })

  test('doTest 失败时设置错误信息', async () => {
    modelApi.executeModel.mockRejectedValue(new Error('模型执行失败'))
    wrapper.vm.testMode = 'manual'
    await wrapper.vm.doTest()
    expect(wrapper.vm.testResult).toBeDefined()
    expect(wrapper.vm.testResult.error).toContain('模型执行失败')
  })

  test('handleSaveParams 保存测试参数', async () => {
    modelApi.saveTestParams.mockResolvedValue({ data: true })
    wrapper.vm.testMode = 'manual'
    wrapper.vm.testParams = { age: 30 }
    await wrapper.vm.handleSaveParams()
    expect(modelApi.saveTestParams).toHaveBeenCalled()
  })

  test('handleSaveParams JSON 模式保存 JSON 字符串', async () => {
    modelApi.saveTestParams.mockResolvedValue({ data: true })
    wrapper.vm.testMode = 'json'
    wrapper.vm.testJsonStr = '{"age": 30}'
    await wrapper.vm.handleSaveParams()
    expect(modelApi.saveTestParams).toHaveBeenCalled()
  })
})

describe('ModelDetail — 边界情况', () => {
  test('inputFields 为空时不报错', async () => {
    const modelData = { ...mockModel(), inputFields: [], outputFields: [] }
    const wrapper = await mountAndWait(modelData)
    expect(wrapper.vm.model.inputFields).toEqual([])
    wrapper.destroy()
  })

  test('outputFields 为空时不报错', async () => {
    const modelData = { ...mockModel(), inputFields: [], outputFields: [] }
    const wrapper = await mountAndWait(modelData)
    expect(wrapper.vm.model.outputFields).toEqual([])
    wrapper.destroy()
  })

  test('projectId 为 null 时加载全局变量', async () => {
    variableApi.listVariables.mockResolvedValueOnce({ data: { records: mockVars() } })
    variableApi.listVariables.mockResolvedValueOnce({ data: { records: mockConsts() } })
    dataObjectApi.getVariableTree.mockResolvedValueOnce(mockObjectTree())

    const modelData = { ...mockModel(), projectId: null }
    const wrapper = await mountAndWait(modelData)
    await wrapper.vm.loadGlobalVars()
    await Vue.nextTick()
    expect(variableApi.listVariables).toHaveBeenCalled()
    wrapper.destroy()
  })

  test('buildVarOptions 处理空数组', () => {
    const localWrapper = mount(ModelDetail, {
      localVue: createTestVue(),
      mocks: {
        $route: { params: { id: 1 } },
        $router: { push: jest.fn(), replace: jest.fn() }
      },
      stubs: {
        'el-descriptions': true, 'el-descriptions-item': true, 'el-button': true,
        'el-tabs': true, 'el-tab-pane': true, 'el-table': true, 'el-table-column': true,
        'el-loading': true, 'el-card': true, 'el-dialog': true, 'monaco-editor': true,
        'el-input': true, 'el-select': true, 'el-option': true, 'el-input-number': true,
        'el-form': true, 'el-form-item': true, 'el-tag': true, 'el-tooltip': true,
        'el-divider': true, 'el-alert': true, 'el-date-picker': true
      }
    })
    localWrapper.vm.buildVarOptions([], [])
    expect(localWrapper.vm.varMap).toEqual({})
    expect(localWrapper.vm.varPickerGroups.length).toBe(3)
    localWrapper.destroy()
  })
})