/**
 * varPickerMixin 单元测试
 *
 * 关键点：
 * - 测试文件直接 import setup.js 预置的 jest.fn()，通过 .mockResolvedValueOnce() 配置返回值
 * - 不在测试文件中写 jest.mock()（避免覆盖 setup.js 的基础 mock）
 * - API 返回值应传原始数据（数组），axios 拦截器会包成 { data: ... }，mixin 解包后得到正确数组
 * - 使用 jest.resetAllMocks() 而非 jest.clearAllMocks()，确保返回值的重置
 */

import Vue from 'vue'
import varPickerMixin from '@/mixins/varPickerMixin'

// 直接 import setup.js 预置的 mock
import * as definitionApi from '@/api/definition'
import * as variableApi from '@/api/variable'
import * as dataObjectApi from '@/api/dataObject'
import * as functionApi from '@/api/function'
import * as modelApi from '@/api/model'

// ─── Mock 数据（传原始数组，由 axios 拦截器包成 { data: ... }） ─────
const mockDefs = { id: 1, projectId: 1, scope: 'PROJECT' }

const mockVars = [
  { id: 1, varCode: 'age', varLabel: '年龄', varType: 'Integer', varSource: 'INPUT', scriptName: 'age' },
  { id: 2, varCode: 'income', varLabel: '收入', varType: 'NUMBER', varSource: 'INPUT', scriptName: 'income' },
  { id: 3, varCode: 'MAX_AGE', varLabel: '最大年龄', varType: 'Integer', varSource: 'CONSTANT', scriptName: 'MAX_AGE' },
  { id: 20, varCode: 'level', varLabel: '等级', varType: 'ENUM', varSource: 'ENUM', scriptName: 'level',
    options: [{ label: '高', value: 'HIGH' }, { label: '中', value: 'MID' }, { label: '低', value: 'LOW' }] }
]

// getVariableTree 返回数组（axios 拦截器会包成 { data: [...] }）
const mockObjectTree = [
  {
    object: { objectCode: 'TaxRequest', objectLabel: '税务请求', scriptName: 'TaxRequest' },
    variables: [
      { varCode: 'amount', varLabel: '金额', varType: 'NUMBER', varSource: 'OBJECT', scriptName: 'amount' }
    ]
  }
]

const mockModels = [
  {
    id: 101,
    modelCode: 'creditModel',
    modelName: '信用模型',
    modelType: 'LR',
    status: 1,
    outputFields: [
      { id: 201, modelId: 101, fieldName: 'score', scriptName: 'score', fieldLabel: '评分', fieldType: 'NUMBER' }
    ]
  }
]

// getVariableTree 返回数组（axios 拦截器会包成 { data: [...] }）

describe('varPickerMixin', () => {
  beforeEach(() => {
    // clearAllMocks：清除调用记录，保留 mockResolvedValueOnce 的配置
    // resetAllMocks 会清除所有实现，导致测试体内设置的 mock 无效
    jest.clearAllMocks()
    // 默认 mock（大多数测试需要）
    definitionApi.getDefinition.mockResolvedValue(mockDefs)
    variableApi.listVariablesByProject.mockResolvedValue(mockVars)
    dataObjectApi.getVariableTree.mockResolvedValue(mockObjectTree)
    functionApi.listAllFunctionsByProject.mockResolvedValue([])
    modelApi.listAllModelsByProject.mockResolvedValue(mockModels)
    dataObjectApi.getDataObjectFieldOptions.mockResolvedValue([])
  })

  /** 创建混入了 varPickerMixin 的 Vue 实例 */
  function createMixinVM() {
    const ComponentDef = {
      mixins: [varPickerMixin],
      data() {
        return {
          definitionId: 1,
          projectId: 1,
          loadingVars: false,
          varsLoadError: false,
          projectVars: [],
          projectRefs: [],
          variableTree: [],
          projectFunctions: [],
          // contentLoaded: true 触发 _trySyncModelVarRefs()
          contentLoaded: true
        }
      }
    }
    return new (Vue.extend(ComponentDef))()
  }

  // ─── API Mock 验证测试 ────────────────────────────────────
  test('loadProjectVars 调用正确的 API 并传入正确的参数', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    expect(definitionApi.getDefinition).toHaveBeenCalledWith(1)
    expect(variableApi.listVariablesByProject).toHaveBeenCalledWith(1)
    expect(dataObjectApi.getVariableTree).toHaveBeenCalledWith(1)
    expect(functionApi.listAllFunctionsByProject).toHaveBeenCalledWith(1)
    expect(modelApi.listAllModelsByProject).toHaveBeenCalledWith(1)
  })

  test('loadProjectVars 完成后 projectVars 非空', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    expect(vm.projectVars.length).toBeGreaterThan(0)
  })

  test('loadProjectVars 完成后 projectRefs 非空（扁平数组）', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    expect(vm.projectRefs.length).toBeGreaterThan(0)
  })

  test('loadProjectVars 完成后 inputVars 仅包含 INPUT 类型', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    // age, income 是 INPUT；MAX_AGE 是 CONSTANT；level 是 ENUM（非 INPUT）
    expect(vm.inputVars.length).toBe(2)
    expect(vm.inputVars.every(v => v.varSource === 'INPUT')).toBe(true)
  })

  // ─── 变量分类测试 ────────────────────────────────────────
  test('projectRefs 包含 standalone 类别的变量（非 CONSTANT）', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const standalone = vm.projectRefs.filter(r => r.category === 'standalone')
    // age, income, level（varSource = INPUT/ENUM，排除 CONSTANT）
    expect(standalone.length).toBe(3)
    expect(standalone.every(r => r.varObj.varSource !== 'CONSTANT')).toBe(true)
  })

  test('projectRefs 包含 constant 类别的常量（varSource=CONSTANT）', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const constant = vm.projectRefs.filter(r => r.category === 'constant')
    expect(constant.length).toBe(1)
    expect(constant[0].refCode).toBe('MAX_AGE')
  })

  test('projectRefs 包含 object 类别的对象字段', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const object = vm.projectRefs.filter(r => r.category === 'object')
    expect(object.length).toBe(1)
    expect(object[0].refCode).toBe('TaxRequest.amount')
  })

  test('projectRefs 包含 model 类别的模型输出字段', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const models = vm.projectRefs.filter(r => r.category === 'model')
    expect(models.length).toBe(1)
    expect(models[0].refCode).toBe('creditModel.score')
    expect(models[0].refType).toBe('MODEL_OUTPUT')
    expect(models[0].varObj.id).toBe(201)
  })

  // ─── 方法测试 ────────────────────────────────────────────
  test('getVarByCode 通过 refCode 查找变量', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const varItem = vm.getVarByCode('age')
    expect(varItem).toBeDefined()
    expect(varItem.varCode).toBe('age')
  })

  test('getVarByCode 对未知编码返回 null', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const varItem = vm.getVarByCode('nonExistent')
    expect(varItem).toBeNull()
  })

  test('findRefByVarId 通过 varId 精确匹配', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const ref = vm.findRefByVarId(1)
    expect(ref).toBeDefined()
    expect(ref.refCode).toBe('age')
  })

  test('findRefByVarId 对未知 varId 返回 null', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const ref = vm.findRefByVarId(999)
    expect(ref).toBeNull()
  })

  test('syncVarItem 通过 _varId 更新 varCode', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    // syncVarItem 要求 item.varCode 存在，否则直接返回 false
    const leaf = { _varId: 2, varCode: 'oldValue' }
    const changed = vm.syncVarItem(leaf)
    expect(changed).toBe(true)
    expect(leaf.varCode).toBe('income')
  })

  test('syncVarItem 对未知 _varId 不修改 varCode', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const leaf = { _varId: 999, varCode: 'original' }
    const changed = vm.syncVarItem(leaf)
    expect(changed).toBe(false)
    expect(leaf.varCode).toBe('original')
  })

  test('syncActionDataVarRefs 通过字段级变量 ID 更新 actionData', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const actionData = [{
      type: 'ternary',
      target: 'oldDecision',
      _targetVarId: 2,
      _targetRefType: 'VARIABLE',
      condVar: 'oldAge',
      _condVarId: 1,
      _condVarRefType: 'VARIABLE',
      condOp: '>=',
      condValue: '18',
      trueValue: '"PASS"',
      falseValue: '"REJECT"'
    }]

    const changed = vm.syncActionDataVarRefs(actionData)

    expect(changed).toBe(true)
    expect(actionData[0].target).toBe('income')
    expect(actionData[0].condVar).toBe('age')
  })

  // ─── 变量选择器选项测试 ───────────────────────────────────
  test('varPickerOptions 包含所有选项', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const options = vm.varPickerOptions
    expect(options.length).toBe(6) // 3 standalone + 1 constant + 1 object + 1 model
    const ageOpt = options.find(o => o.varCode === 'age')
    expect(ageOpt).toBeDefined()
    expect(ageOpt.varLabel).toMatch(/年龄.*age/)
  })

  test('varPickerOptions 中对象字段的 varLabel 包含对象路径', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const options = vm.varPickerOptions
    const amountOpt = options.find(o => o.varCode === 'TaxRequest.amount')
    expect(amountOpt).toBeDefined()
    // makeRefLabel 生成 label 为 "金额" 或带前缀格式
    expect(amountOpt.varLabel).toMatch(/金额|TaxRequest/)
  })

  test('selectedVarPickerOptions 从页面模型字段中按引用去重汇总已选字段', async () => {
    const ComponentDef = {
      mixins: [varPickerMixin],
      data() {
        return {
          contentLoaded: true,
          selectedModelItems: [
            { _varId: 1, _refType: 'VARIABLE', varCode: 'legacyAge' },
            { varCode: 'TaxRequest.amount' },
            { _varId: 201, _refType: 'MODEL_OUTPUT' },
            { varCode: 'age' }
          ],
          selectedActionData: [
            { type: 'assign', target: 'income', value: '1' },
            { type: 'switch-block', matchVar: 'level', cases: [{ actions: [{ target: 'TaxRequest.amount' }] }] }
          ]
        }
      },
      methods: {
        collectSelectedVarItems() {
          return [
            ...this.selectedModelItems,
            ...this.collectActionDataVarItems(this.selectedActionData)
          ]
        }
      }
    }
    const vm = new (Vue.extend(ComponentDef))()
    await vm.loadProjectVars(1)

    expect(vm.selectedVarPickerOptions.map(o => o.varCode)).toEqual([
      'age',
      'TaxRequest.amount',
      'creditModel.score',
      'income',
      'level'
    ])
  })

  // ─── 错误处理测试 ────────────────────────────────────────
  test('loadProjectVars 失败时 varsLoadError 为 true', async () => {
    // getDefinition 失败才会触发 varsLoadError（因为 listVariablesByProject 等使用了 .catch(() => [])）
    definitionApi.getDefinition.mockRejectedValue(new Error('network error'))
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    expect(vm.varsLoadError).toBe(true)
  })

  test('loadProjectVars 失败时 projectRefs 保持为空数组', async () => {
    // 同上，getDefinition 失败才会进入 catch 块
    definitionApi.getDefinition.mockRejectedValue(new Error('network error'))
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    expect(vm.projectRefs).toEqual([])
  })

  // ─── refreshProjectRefs 测试 ─────────────────────────────
  test('refreshProjectRefs 重新加载变量并更新 projectRefs', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const initialCount = vm.projectRefs.length

    // 追加新变量（模拟 refresh 返回更多数据）
    variableApi.listVariablesByProject.mockResolvedValue([
      ...mockVars,
      { id: 4, varCode: 'balance', varLabel: '余额', varType: 'NUMBER', varSource: 'INPUT', scriptName: 'balance' }
    ])
    dataObjectApi.getVariableTree.mockResolvedValue(mockObjectTree)
    functionApi.listAllFunctionsByProject.mockResolvedValue([])
    modelApi.listAllModelsByProject.mockResolvedValue(mockModels)

    await vm.refreshProjectRefs()
    expect(vm.projectRefs.length).toBe(initialCount + 1)
    expect(vm.getVarByCode('balance')).toBeDefined()
  })

  // ─── 空数据边界测试 ─────────────────────────────────────
  test('空变量列表时 projectRefs 为空', async () => {
    // beforeEach 已 mock getDefinition，此处只需覆盖返回空数组
    variableApi.listVariablesByProject.mockResolvedValue([])
    dataObjectApi.getVariableTree.mockResolvedValue([])
    modelApi.listAllModelsByProject.mockResolvedValue([])

    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    expect(vm.projectRefs).toEqual([])
    expect(vm.inputVars).toEqual([])
  })
})
