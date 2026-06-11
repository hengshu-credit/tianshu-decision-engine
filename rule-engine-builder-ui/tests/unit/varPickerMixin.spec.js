/**
 * varPickerMixin 单元测试
 * 通过 mock axios 拦截所有 HTTP 请求，模拟后端返回数据
 */

import Vue from 'vue'
import axios from 'axios'
import varPickerMixin from '@/mixins/varPickerMixin'

// 模拟 API 返回数据
const mockVars = [
  { id: 1, varCode: 'age', varLabel: '年龄', varType: 'Integer', varSource: 'INPUT', scriptName: 'age' },
  { id: 2, varCode: 'income', varLabel: '收入', varType: 'NUMBER', varSource: 'INPUT', scriptName: 'income' },
  { id: 3, varCode: 'MAX_AGE', varLabel: '最大年龄', varType: 'Integer', varSource: 'CONSTANT', scriptName: 'MAX_AGE' }
]

const mockObjectTree = [
  {
    object: { objectCode: 'TaxRequest', objectLabel: '税务请求', scriptName: 'TaxRequest' },
    variables: [
      { varCode: 'amount', varLabel: '金额', varType: 'NUMBER', varSource: 'OBJECT', scriptName: 'amount' }
    ]
  }
]

// mock axios（request.js 底层依赖 axios）
jest.mock('axios', () => ({
  get: jest.fn(),
  post: jest.fn()
}))

describe('varPickerMixin', () => {
  let axiosGet, axiosPost

  beforeEach(() => {
    axiosGet = axios.get
    axiosPost = axios.post
    jest.clearAllMocks()

    // 定义 axios.get 的默认行为
    axiosGet.mockImplementation((url) => {
      if (url.includes('/definition/')) {
        return Promise.resolve({ data: { id: 1, projectId: 1, scope: 'PROJECT' } })
      }
      if (url.includes('/variable/listByProject')) {
        return Promise.resolve({ data: mockVars })
      }
      if (url.includes('/dataObject/variableTree')) {
        return Promise.resolve({ data: mockObjectTree })
      }
      if (url.includes('/function/listAllByProject')) {
        return Promise.resolve({ data: [] })
      }
      if (url.includes('/model/inputs')) {
        return Promise.resolve({ data: [] })
      }
      if (url.includes('/model/outputs')) {
        return Promise.resolve({ data: [] })
      }
      return Promise.resolve({ data: null })
    })
  })

  /** 创建混入了 varPickerMixin 的 Vue 实例 */
  function createMixinVM(definitionId = 1) {
    const ComponentDef = {
      mixins: [varPickerMixin],
      data() {
        return {
          definitionId,
          projectId: 1,
          varsLoading: false,
          varsLoadError: false,
          projectVars: [],
          projectRefs: [],
          variableTree: [],
          projectFunctions: [],
          modelInputVars: [],
          modelOutputVars: []
        }
      }
    }
    return new (Vue.extend(ComponentDef))()
  }

  // ─── API Mock 验证测试 ────────────────────────────────────
  test('loadProjectVars 调用正确的 API 并传入正确的参数', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)

    // 验证各 API 按预期被调用
    expect(axiosGet).toHaveBeenCalledWith(expect.stringContaining('/definition/'), expect.any(Object))
    expect(axiosGet).toHaveBeenCalledWith(expect.stringContaining('/variable/listByProject'), expect.any(Object))
    expect(axiosGet).toHaveBeenCalledWith(expect.stringContaining('/dataObject/variableTree'), expect.any(Object))
    expect(axiosGet).toHaveBeenCalledWith(expect.stringContaining('/function/listAllByProject'), expect.any(Object))
    expect(axiosGet).toHaveBeenCalledWith(expect.stringContaining('/model/inputs'), expect.any(Object))
    expect(axiosGet).toHaveBeenCalledWith(expect.stringContaining('/model/outputs'), expect.any(Object))
  })

  // ─── 加载流程测试 ────────────────────────────────────────
  test('loadProjectVars 完成后 projectVars 非空', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    expect(vm.projectVars.length).toBeGreaterThan(0)
  })

  test('loadProjectVars 完成后 projectRefs 非空（3层结构数组）', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    expect(vm.projectRefs.length).toBeGreaterThan(0)
  })

  test('loadProjectVars 完成后 inputVars 仅包含 INPUT 类型', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    expect(vm.inputVars.length).toBe(2) // age, income
    expect(vm.inputVars.every(v => v.varSource === 'INPUT')).toBe(true)
  })

  // ─── 变量分类测试 ────────────────────────────────────────
  test('projectRefs 包含 standalone 类别的变量（非 CONSTANT）', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const standalone = vm.projectRefs.filter(r => r.category === 'standalone')
    expect(standalone.length).toBe(2) // age, income
    expect(standalone.every(r => r.varSource !== 'CONSTANT')).toBe(true)
  })

  test('projectRefs 包含 constant 类别的常量（varSource=CONSTANT）', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const constant = vm.projectRefs.filter(r => r.category === 'constant')
    expect(constant.length).toBe(1) // MAX_AGE
    expect(constant[0].refCode).toBe('MAX_AGE')
  })

  test('projectRefs 包含 object 类别的对象字段', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const object = vm.projectRefs.filter(r => r.category === 'object')
    expect(object.length).toBe(1) // TaxRequest.amount
    expect(object[0].refCode).toBe('TaxRequest.amount')
  })

  // ─── 方法测试 ────────────────────────────────────────────
  test('getVarByCode 通过 refCode 查找变量', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const varItem = vm.getVarByCode('age')
    expect(varItem).toBeDefined()
    expect(varItem.varCode).toBe('age')
  })

  test('getVarByCode 对未知编码返回无 refCode 的对象', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const varItem = vm.getVarByCode('nonExistent')
    expect(varItem.refCode).toBeUndefined()
  })

  test('findRefByVarId 通过 varId 精确匹配', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const ref = vm.findRefByVarId(1)
    expect(ref).toBeDefined()
    expect(ref.refCode).toBe('age')
  })

  test('findRefByVarId 对未知 varId 返回 undefined', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const ref = vm.findRefByVarId(999)
    expect(ref).toBeUndefined()
  })

  test('syncVarItem 通过 _varId 更新 varCode', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const leaf = { _varId: 2 }
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

  // ─── 变量选择器选项测试 ───────────────────────────────────
  test('varPickerOptions 包含所有选项', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const options = vm.varPickerOptions
    expect(options.length).toBe(4) // 2 standalone + 1 constant + 1 object
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
    expect(amountOpt.varLabel).toMatch(/税务请求/)
  })

  // ─── 错误处理测试 ────────────────────────────────────────
  test('loadProjectVars 失败时 varsLoadError 为 true', async () => {
    axiosGet.mockRejectedValueOnce(new Error('network error'))
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    expect(vm.varsLoadError).toBe(true)
  })

  test('loadProjectVars 失败时 projectVars 保持原值', async () => {
    axiosGet.mockRejectedValueOnce(new Error('network error'))
    const vm = createMixinVM()
    const originalVars = vm.projectVars.slice()
    await vm.loadProjectVars(1)
    expect(vm.projectVars).toEqual(originalVars)
  })

  // ─── refreshProjectRefs 测试 ─────────────────────────────
  test('refreshProjectRefs 重新加载变量并更新 projectRefs', async () => {
    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    const initialCount = vm.projectRefs.length

    // 追加新变量：改写 axios.get 的返回值
    axiosGet.mockImplementation((url) => {
      if (url.includes('/variable/listByProject')) {
        return Promise.resolve({
          data: [
            ...mockVars,
            { id: 4, varCode: 'balance', varLabel: '余额', varType: 'NUMBER', varSource: 'INPUT', scriptName: 'balance' }
          ]
        })
      }
      if (url.includes('/definition/')) {
        return Promise.resolve({ data: { id: 1, projectId: 1, scope: 'PROJECT' } })
      }
      if (url.includes('/dataObject/variableTree')) {
        return Promise.resolve({ data: [] })
      }
      if (url.includes('/function/listAllByProject')) {
        return Promise.resolve({ data: [] })
      }
      if (url.includes('/model/inputs')) {
        return Promise.resolve({ data: [] })
      }
      if (url.includes('/model/outputs')) {
        return Promise.resolve({ data: [] })
      }
      return Promise.resolve({ data: null })
    })

    await vm.refreshProjectRefs()
    expect(vm.projectRefs.length).toBe(initialCount + 1)
    expect(vm.getVarByCode('balance')).toBeDefined()
  })

  // ─── 空数据边界测试 ─────────────────────────────────────
  test('空变量列表时 projectRefs 为空', async () => {
    axiosGet.mockImplementation((url) => {
      if (url.includes('/definition/')) {
        return Promise.resolve({ data: { id: 1, projectId: 1, scope: 'PROJECT' } })
      }
      return Promise.resolve({ data: [] })
    })

    const vm = createMixinVM()
    await vm.loadProjectVars(1)
    expect(vm.projectRefs).toEqual([])
    expect(vm.inputVars).toEqual([])
  })
})