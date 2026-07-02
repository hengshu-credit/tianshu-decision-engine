// tests/unit/views/functionList.spec.js
import { shallowMount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'

// 直接 import API 模块（依赖 setup.js 的预置 mock）
import * as functionApi from '@/api/function'
import * as projectApi from '@/api/project'
import FunctionList from '@/views/function/FunctionList.vue'

afterEach(() => { jest.clearAllMocks() })

// ─── Mock 数据 ───────────────────────────────────────────
function mockFunctions() {
  return [
    { id: 1, funcCode: 'calculateTax', funcName: '计算税额', funcType: 'QL_EXPRESS', projectId: 1, projectName: '项目A', scope: 'PROJECT', status: 1 },
    { id: 2, funcCode: 'getRiskLevel', funcName: '获取风险等级', funcType: 'JAVA_CLASS', projectId: 1, projectName: '项目A', scope: 'PROJECT', status: 1 },
    { id: 3, funcCode: 'getCurrentTime', funcName: '获取当前时间', funcType: 'SPRING_BEAN', projectId: 0, projectName: '—', scope: 'GLOBAL', status: 0 }
  ]
}

function mockProjects() {
  return [
    { id: 1, projectName: '项目A', projectCode: 'project_a' },
    { id: 2, projectName: '项目B', projectCode: 'project_b' }
  ]
}

// ─── 工具函数 ─────────────────────────────────────────────
function createTestVue() {
  const localVue = createLocalVue()
  localVue.use(jest.genMockFromModule('element-ui'))
  return localVue
}

function makeStub(tag) {
  return { render: h => h(tag) }
}

async function mountAndWait() {
  projectApi.listProjects.mockResolvedValueOnce({ data: { records: mockProjects() } })
  functionApi.listFunctions.mockResolvedValueOnce({ data: { records: mockFunctions(), total: 3 } })

  const wrapper = shallowMount(FunctionList, {
    localVue: createTestVue(),
    mocks: {
      $route: { params: {} },
      $router: { push: jest.fn(), replace: jest.fn() },
      $confirm: jest.fn().mockResolvedValue(), // 必须 resolve，handleDelete 是 async 并 await
      $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() }
    },
    stubs: {
      'el-form': makeStub('form'), 'el-form-item': makeStub('div'),
      'el-select': makeStub('select'), 'el-option': makeStub('option'),
      'el-input': makeStub('input'), 'el-button': makeStub('button'),
      'el-tag': makeStub('span'), 'el-table': makeStub('table'),
      'el-table-column': makeStub('td'), 'el-tabs': makeStub('div'),
      'el-tab-pane': makeStub('div'), 'el-dialog': makeStub('div'),
      'el-card': makeStub('div'), 'el-pagination': makeStub('div'),
      'el-switch': makeStub('span'), 'el-loading': makeStub('div'),
      'el-textarea': makeStub('textarea'), 'el-divider': makeStub('hr'),
      'el-alert': makeStub('div'), 'el-radio': makeStub('span'),
      'el-radio-group': makeStub('div'), 'el-upload': makeStub('div'),
      'el-input-number': makeStub('input'), 'el-tooltip': makeStub('span'),
      'el-option-group': makeStub('optgroup')
    }
  })

  await Vue.nextTick()
  await new Promise(r => setTimeout(r, 100))
  return wrapper
}

// ─── 测试用例 ─────────────────────────────────────────────
describe('FunctionList — 初始化与数据加载', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('mounted 后调用 listProjects', () => {
    expect(projectApi.listProjects).toHaveBeenCalled()
  })

  test('mounted 后调用 listFunctions', () => {
    expect(functionApi.listFunctions).toHaveBeenCalled()
  })

  test('funcList 数据正确赋值', () => {
    expect(wrapper.vm.funcList).toBeInstanceOf(Array)
    expect(wrapper.vm.funcList.length).toBe(3)
  })

  test('total 正确赋值', () => {
    expect(wrapper.vm.total).toBe(3)
  })

  test('loading 初始值为 false', () => {
    expect(wrapper.vm.loading).toBe(false)
  })
})

describe('FunctionList — 标签方法', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('implTypeLabel 返回正确的标签', () => {
    expect(wrapper.vm.implTypeLabel('SCRIPT')).toBe('脚本')
    expect(wrapper.vm.implTypeLabel('JAVA')).toBe('Java类')
    expect(wrapper.vm.implTypeLabel('BEAN')).toBe('Bean')
    expect(wrapper.vm.implTypeLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(wrapper.vm.implTypeLabel(null)).toBe(null)
  })

  test('scopeLabel 返回正确的范围标签', () => {
    expect(wrapper.vm.scopeLabel('GLOBAL')).toBe('全局')
    expect(wrapper.vm.scopeLabel('PROJECT')).toBe('项目级')
    expect(wrapper.vm.scopeLabel('UNKNOWN')).toBe('项目级')
  })

  test('parseParams 正确解析 JSON', () => {
    expect(wrapper.vm.parseParams('[{"a":1}]')).toEqual([{ a: 1 }])
    expect(wrapper.vm.parseParams('not json')).toEqual([])
  })
})

describe('FunctionList — 筛选与搜索', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('queryProjectCode 模糊匹配', () => {
    wrapper.vm.projectList = mockProjects()
    wrapper.vm.queryProjectCode('project_a')
    expect(wrapper.vm.filteredProjectCodes.length).toBe(1)
  })

  test('queryProjectCode 空查询返回全部', () => {
    wrapper.vm.projectList = mockProjects()
    wrapper.vm.queryProjectCode('')
    expect(wrapper.vm.filteredProjectCodes.length).toBe(2)
  })

  test('queryProjectName 模糊匹配', () => {
    wrapper.vm.projectList = mockProjects()
    wrapper.vm.queryProjectName('项目A')
    expect(wrapper.vm.filteredProjectNames.length).toBe(1)
  })

  test('handleQuery 重置页码并重新加载', async () => {
    wrapper.vm.qp.pageNum = 5
    functionApi.listFunctions.mockResolvedValueOnce({ data: { records: [], total: 0 } })
    wrapper.vm.handleQuery()
    await Vue.nextTick()
    expect(wrapper.vm.qp.pageNum).toBe(1)
  })

  test('resetQuery 重置所有查询条件', () => {
    wrapper.vm.qp.scope = 'GLOBAL'
    wrapper.vm.qp.implType = 'SCRIPT'
    functionApi.listFunctions.mockResolvedValueOnce({ data: { records: [], total: 0 } })
    wrapper.vm.resetQuery()
    expect(wrapper.vm.qp.scope).toBe('')
    expect(wrapper.vm.qp.implType).toBe('')
    expect(wrapper.vm.qp.funcCode).toBe('')
  })
})

describe('FunctionList — 函数操作', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('handleCreate 打开创建弹窗并设置默认值', () => {
    wrapper.vm.handleCreate()
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.editForm.implType).toBe('SCRIPT')
    expect(wrapper.vm.editForm.status).toBe(1)
  })

  test('handleEdit 填充编辑表单', () => {
    const row = { id: 1, funcCode: 'calculateTax', funcName: '计算税额', implType: 'SCRIPT', scope: 'PROJECT' }
    wrapper.vm.handleEdit(row)
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.editForm.id).toBe(1)
    expect(wrapper.vm.editForm.funcName).toBe('计算税额')
  })

  test('handleEdit 解析 paramsJson', () => {
    const row = { id: 1, funcName: '测试函数', implType: 'SCRIPT', paramsJson: '[{"name":"x","type":"STRING"}]' }
    wrapper.vm.handleEdit(row)
    expect(wrapper.vm.editParams.length).toBe(1)
    expect(wrapper.vm.editParams[0].name).toBe('x')
  })

  test('handleEdit 空 paramsJson 时有默认参数行', () => {
    const row = { id: 1, funcName: '测试函数', implType: 'SCRIPT' }
    wrapper.vm.handleEdit(row)
    expect(wrapper.vm.editParams.length).toBeGreaterThan(0)
    expect(wrapper.vm.editParams[0].name).toBe('')
  })

  test('handleDelete 调用删除 API', async () => {
    functionApi.deleteFunction.mockResolvedValueOnce({ data: true })
    const row = { id: 99, funcName: '测试函数' }
    // $confirm 必须 resolve，handleDelete 是 async 并 await 它
    wrapper.vm.$confirm = jest.fn().mockResolvedValue()
    wrapper.vm.handleDelete(row)
    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 50)) // 等待 async handleDelete 完成
    expect(functionApi.deleteFunction).toHaveBeenCalledWith(99)
  })

  test('openVersionDialog loads versions and compareWithNext compares adjacent versions', async () => {
    functionApi.listVersions.mockResolvedValueOnce({ data: [{ version: 2, functionJson: '{"a":2}' }, { version: 1, functionJson: '{"a":1}' }] })
    functionApi.compareVersions.mockResolvedValueOnce({ data: { left: { version: 2 }, right: { version: 1 }, functionChanged: true } })

    await wrapper.vm.openVersionDialog({ id: 1 })
    await wrapper.vm.compareWithNext(wrapper.vm.versionList[0], 0)

    expect(wrapper.vm.versionVisible).toBe(true)
    expect(functionApi.listVersions).toHaveBeenCalledWith(1)
    expect(functionApi.compareVersions).toHaveBeenCalledWith(1, 2, 1)
    expect(wrapper.vm.versionCompare.functionChanged).toBe(true)
  })
})

describe('FunctionList — 边界情况', () => {
  test('funcList 为空数组不报错', async () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: [] } })
    functionApi.listFunctions.mockResolvedValueOnce({ data: { records: [], total: 0 } })
    const wrapper = shallowMount(FunctionList, {
      localVue: createTestVue(),
      mocks: {
        $route: { params: {} },
        $router: { push: jest.fn(), replace: jest.fn() },
        $confirm: jest.fn().mockResolvedValue(),
        $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() }
      },
      stubs: {
        'el-form': makeStub('form'), 'el-form-item': makeStub('div'),
        'el-select': makeStub('select'), 'el-option': makeStub('option'),
        'el-input': makeStub('input'), 'el-button': makeStub('button'),
        'el-tag': makeStub('span'), 'el-table': makeStub('table'),
        'el-table-column': makeStub('td'), 'el-dialog': makeStub('div'),
        'el-pagination': makeStub('div'), 'el-loading': makeStub('div'),
        'el-textarea': makeStub('textarea')
      }
    })
    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
    expect(wrapper.vm.funcList).toEqual([])
    wrapper.destroy()
  })

  test('initial loading is false', async () => {
    const wrapper = await mountAndWait()
    expect(wrapper.vm.loading).toBe(false)
    wrapper.destroy()
  })

  test('initial funcList is empty array before load', async () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: [] } })
    functionApi.listFunctions.mockResolvedValueOnce({ data: { records: [], total: 0 } })
    const wrapper = shallowMount(FunctionList, {
      localVue: createTestVue(),
      mocks: {
        $route: { params: {} },
        $router: { push: jest.fn(), replace: jest.fn() },
        $confirm: jest.fn().mockResolvedValue(),
        $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() }
      },
      stubs: {
        'el-form': makeStub('form'), 'el-form-item': makeStub('div'),
        'el-select': makeStub('select'), 'el-option': makeStub('option'),
        'el-input': makeStub('input'), 'el-button': makeStub('button'),
        'el-tag': makeStub('span'), 'el-table': makeStub('table'),
        'el-table-column': makeStub('td'), 'el-dialog': makeStub('div'),
        'el-pagination': makeStub('div'), 'el-loading': makeStub('div'),
        'el-textarea': makeStub('textarea'), 'el-radio': makeStub('span'),
        'el-radio-group': makeStub('div'), 'el-switch': makeStub('span'),
        'el-divider': makeStub('hr'), 'el-tabs': makeStub('div'),
        'el-tab-pane': makeStub('div'), 'el-card': makeStub('div')
      }
    })
    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
    expect(wrapper.vm.funcList).toEqual([])
    wrapper.destroy()
  })
})
