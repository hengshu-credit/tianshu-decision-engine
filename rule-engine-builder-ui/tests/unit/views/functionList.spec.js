// tests/unit/views/functionList.spec.js
import { shallowMount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'
import ElementUI from 'element-ui'

// 直接 import API 模块（不写 jest.mock，依赖 setup.js 的预置 mock）
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

// ─── 测试用例 ─────────────────────────────────────────────
function createTestVue() {
  const localVue = createLocalVue()
  localVue.use(ElementUI)
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
      $router: { push: jest.fn(), replace: jest.fn() }
    },
    stubs: {
      'el-form': makeStub('form'),
      'el-form-item': makeStub('div'),
      'el-select': makeStub('select'),
      'el-option': makeStub('option'),
      'el-input': makeStub('input'),
      'el-button': makeStub('button'),
      'el-tag': makeStub('span'),
      'el-table': makeStub('table'),
      'el-table-column': makeStub('td'),
      'el-tabs': makeStub('div'),
      'el-tab-pane': makeStub('div'),
      'el-dialog': makeStub('div'),
      'el-card': makeStub('div'),
      'el-pagination': makeStub('div'),
      'el-switch': makeStub('span'),
      'el-loading': makeStub('div'),
      'el-textarea': makeStub('textarea'),
      'el-divider': makeStub('hr'),
      'el-alert': makeStub('div'),
      'el-radio': makeStub('span'),
      'el-radio-group': makeStub('div'),
      'el-upload': makeStub('div'),
      'el-input-number': makeStub('input'),
      'el-tooltip': makeStub('span')
    }
  })

  await Vue.nextTick()
  await new Promise(r => setTimeout(r, 100))
  return wrapper
}

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

  test('functions 数据正确赋值', () => {
    expect(wrapper.vm.functions).toBeInstanceOf(Array)
    expect(wrapper.vm.functions.length).toBe(3)
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

  test('funcTypeLabel 返回正确的标签', () => {
    expect(wrapper.vm.funcTypeLabel('QL_EXPRESS')).toBe('QLExpress 脚本')
    expect(wrapper.vm.funcTypeLabel('JAVA_CLASS')).toBe('Java 类')
    expect(wrapper.vm.funcTypeLabel('SPRING_BEAN')).toBe('Spring Bean')
    expect(wrapper.vm.funcTypeLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(wrapper.vm.funcTypeLabel(null)).toBe('')
  })

  test('funcTypeTagType 返回正确的 tag 类型', () => {
    expect(wrapper.vm.funcTypeTagType('QL_EXPRESS')).toBe('primary')
    expect(wrapper.vm.funcTypeTagType('JAVA_CLASS')).toBe('warning')
    expect(wrapper.vm.funcTypeTagType('SPRING_BEAN')).toBe('success')
    expect(wrapper.vm.funcTypeTagType('UNKNOWN')).toBe('info')
  })

  test('scopeLabel 返回正确的范围标签', () => {
    expect(wrapper.vm.scopeLabel('GLOBAL')).toBe('全局')
    expect(wrapper.vm.scopeLabel('PROJECT')).toBe('项目级')
    expect(wrapper.vm.scopeLabel('UNKNOWN')).toBe('项目级')
  })

  test('statusLabel 返回正确的状态标签', () => {
    expect(wrapper.vm.statusLabel(1)).toBe('启用')
    expect(wrapper.vm.statusLabel(0)).toBe('停用')
  })

  test('statusTagType 返回正确的 tag 类型', () => {
    expect(wrapper.vm.statusTagType(1)).toBe('success')
    expect(wrapper.vm.statusTagType(0)).toBe('info')
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
    wrapper.vm.qp.funcType = 'QL_EXPRESS'
    functionApi.listFunctions.mockResolvedValueOnce({ data: { records: [], total: 0 } })
    wrapper.vm.resetQuery()
    expect(wrapper.vm.qp.scope).toBe('')
    expect(wrapper.vm.qp.funcType).toBe('')
    expect(wrapper.vm.qp.funcCode).toBe('')
  })
})

describe('FunctionList — 函数操作', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('handleCreate 打开创建弹窗', () => {
    wrapper.vm.handleCreate()
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.dialogMode).toBe('create')
    expect(wrapper.vm.form.funcType).toBe('QL_EXPRESS')
  })

  test('handleEdit 填充编辑表单', () => {
    const row = { id: 1, funcCode: 'calculateTax', funcName: '计算税额', funcType: 'QL_EXPRESS' }
    wrapper.vm.handleEdit(row)
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.dialogMode).toBe('edit')
    expect(wrapper.vm.form.id).toBe(1)
    expect(wrapper.vm.form.funcName).toBe('计算税额')
  })

  test('handleDelete 调用删除 API', async () => {
    functionApi.deleteFunction.mockResolvedValueOnce({ data: true })
    const row = { id: 1, funcName: '测试函数' }
    wrapper.vm.handleDelete(row)
    await Vue.nextTick()
    expect(functionApi.deleteFunction).toHaveBeenCalledWith(1)
  })

  test('handleToggleStatus 切换启用/停用状态', async () => {
    functionApi.updateFunction.mockResolvedValueOnce({ data: true })
    const row = { id: 1, status: 1 }
    wrapper.vm.handleToggleStatus(row)
    await Vue.nextTick()
    expect(functionApi.updateFunction).toHaveBeenCalled()
  })
})

describe('FunctionList — 边界情况', () => {
  test('functions 为空数组不报错', async () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: [] } })
    functionApi.listFunctions.mockResolvedValueOnce({ data: { records: [], total: 0 } })
    const wrapper = shallowMount(FunctionList, {
      localVue: createTestVue(),
      mocks: {
        $route: { params: {} },
        $router: { push: jest.fn(), replace: jest.fn() }
      },
      stubs: {
        'el-form': makeStub('form'), 'el-form-item': makeStub('div'),
        'el-select': makeStub('select'), 'el-option': makeStub('option'),
        'el-input': makeStub('input'), 'el-button': makeStub('button'),
        'el-tag': makeStub('span'),
        'el-table': makeStub('table'), 'el-table-column': makeStub('td'),
        'el-dialog': makeStub('div'), 'el-pagination': makeStub('div'),
        'el-loading': makeStub('div'), 'el-textarea': makeStub('textarea')
      }
    })
    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
    expect(wrapper.vm.functions).toEqual([])
    wrapper.destroy()
  })

  test('handleCreate 设置正确的默认值', () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: mockProjects() } })
    functionApi.listFunctions.mockResolvedValueOnce({ data: { records: mockFunctions(), total: 3 } })
    const wrapper = shallowMount(FunctionList, {
      localVue: createTestVue(),
      mocks: {
        $route: { params: {} },
        $router: { push: jest.fn(), replace: jest.fn() }
      },
      stubs: {
        'el-form': makeStub('form'), 'el-form-item': makeStub('div'),
        'el-select': makeStub('select'), 'el-option': makeStub('option'),
        'el-input': makeStub('input'), 'el-button': makeStub('button'),
        'el-tag': makeStub('span'),
        'el-table': makeStub('table'), 'el-table-column': makeStub('td'),
        'el-dialog': makeStub('div'), 'el-pagination': makeStub('div'),
        'el-loading': makeStub('div'), 'el-textarea': makeStub('textarea'),
        'el-radio': makeStub('span'), 'el-radio-group': makeStub('div'),
        'el-switch': makeStub('span'), 'el-divider': makeStub('hr')
      }
    })
    wrapper.vm.handleCreate()
    expect(wrapper.vm.form.funcType).toBe('QL_EXPRESS')
    expect(wrapper.vm.form.scope).toBe('PROJECT')
    wrapper.destroy()
  })
})