// tests/unit/views/variableList.spec.js
import { mount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'
import ElementUI from 'element-ui'

// Mock API 模块
jest.mock('@/api/variable', () => ({
  listVariables: jest.fn(),
  listVariablesByProject: jest.fn(),
  createVariable: jest.fn(),
  updateVariable: jest.fn(),
  deleteVariable: jest.fn(),
  batchImportVariables: jest.fn(),
  batchValidateVariables: jest.fn()
}))

jest.mock('@/api/project', () => ({
  listProjects: jest.fn()
}))

import * as variableApi from '@/api/variable'
import * as projectApi from '@/api/project'
import VariableList from '@/views/variable/VariableList.vue'

afterEach(() => { jest.clearAllMocks() })

// ─── Mock 数据 ───────────────────────────────────────────
function mockVars() {
  return [
    { id: 1, varCode: 'age', varLabel: '年龄', varType: 'INTEGER', varSource: 'INPUT', scope: 'PROJECT', projectId: 1, projectName: '项目A', scriptName: 'age', status: 1, defaultValue: '18' },
    { id: 2, varCode: 'income', varLabel: '收入', varType: 'DOUBLE', varSource: 'INPUT', scope: 'PROJECT', projectId: 1, projectName: '项目A', scriptName: 'income', status: 1 },
    { id: 3, varCode: 'MAX_AGE', varLabel: '最大年龄', varType: 'INTEGER', varSource: 'CONSTANT', scope: 'PROJECT', projectId: 1, projectName: '项目A', scriptName: 'MAX_AGE', status: 1 }
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

async function mountAndWait() {
  projectApi.listProjects.mockResolvedValue({ data: { records: mockProjects() } })
  variableApi.listVariables.mockResolvedValue({ data: { records: mockVars(), total: 3 } })

  const wrapper = mount(VariableList, {
    localVue: createTestVue(),
    mocks: {
      $route: { params: {} },
      $router: { push: jest.fn(), replace: jest.fn() }
    },
    stubs: {
      'el-form': true, 'el-form-item': true,
      'el-select': true, 'el-option': true,
      'el-input': true, 'el-button': true, 'el-tag': true,
      'el-table': true, 'el-table-column': true,
      'el-tabs': true, 'el-tab-pane': true,
      'el-dialog': true, 'el-card': true,
      'el-dropdown': true, 'el-dropdown-menu': true, 'el-dropdown-item': true,
      'el-pagination': true, 'el-switch': true, 'el-loading': true,
      'el-textarea': true, 'el-divider': true, 'el-alert': true,
      'el-radio': true, 'el-radio-group': true, 'el-upload': true,
      'el-input-number': true, 'el-tooltip': true
    }
  })

  await Vue.nextTick()
  await new Promise(r => setTimeout(r, 100))
  return wrapper
}

describe('VariableList — 初始化与数据加载', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('mounted 后调用 listProjects', () => {
    expect(projectApi.listProjects).toHaveBeenCalled()
  })

  test('mounted 后调用 listVariables', () => {
    expect(variableApi.listVariables).toHaveBeenCalled()
  })

  test('standaloneVars 数据正确赋值', () => {
    expect(wrapper.vm.standaloneVars).toBeInstanceOf(Array)
    expect(wrapper.vm.standaloneVars.length).toBeGreaterThan(0)
  })

  test('activeTab 默认为 list', () => {
    expect(wrapper.vm.activeTab).toBe('list')
  })

  test('scopeTagLabel 返回正确标签', () => {
    expect(wrapper.vm.scopeTagLabel('GLOBAL')).toBe('全局')
    expect(wrapper.vm.scopeTagLabel('PROJECT')).toBe('项目级')
    expect(wrapper.vm.scopeTagLabel('UNKNOWN')).toBe('项目级')
  })
})

describe('VariableList — 标签方法', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('typeLabel 返回正确的中文标签', () => {
    expect(wrapper.vm.typeLabel('INTEGER')).toBe('整数')
    expect(wrapper.vm.typeLabel('DOUBLE')).toBe('浮点')
    expect(wrapper.vm.typeLabel('STRING')).toBe('字符串')
    expect(wrapper.vm.typeLabel('BOOLEAN')).toBe('布尔')
    expect(wrapper.vm.typeLabel('DATE')).toBe('日期')
    expect(wrapper.vm.typeLabel('ENUM')).toBe('枚举')
    expect(wrapper.vm.typeLabel('OBJECT')).toBe('对象')
    expect(wrapper.vm.typeLabel('LIST')).toBe('列表')
    expect(wrapper.vm.typeLabel('MAP')).toBe('映射')
    expect(wrapper.vm.typeLabel('UNKNOWN')).toBe('UNKNOWN')
  })

  test('sourceLabel 返回正确的中文标签', () => {
    expect(wrapper.vm.sourceLabel('INPUT')).toBe('输入')
    expect(wrapper.vm.sourceLabel('OUTPUT')).toBe('输出')
    expect(wrapper.vm.sourceLabel('CONSTANT')).toBe('常量')
    expect(wrapper.vm.sourceLabel('COMPUTED')).toBe('计算')
    expect(wrapper.vm.sourceLabel('DB')).toBe('数据库')
    expect(wrapper.vm.sourceLabel('API')).toBe('接口')
    expect(wrapper.vm.sourceLabel('UNKNOWN')).toBe('UNKNOWN')
  })

  test('typeTagColor 返回正确的颜色', () => {
    expect(wrapper.vm.typeTagColor('INTEGER')).toBe('primary')
    expect(wrapper.vm.typeTagColor('DOUBLE')).toBe('warning')
    expect(wrapper.vm.typeTagColor('BOOLEAN')).toBe('success')
    expect(wrapper.vm.typeTagColor('DATE')).toBe('info')
    expect(wrapper.vm.typeTagColor('ENUM')).toBe('danger')
    expect(wrapper.vm.typeTagColor('STRING')).toBe('')
    expect(wrapper.vm.typeTagColor('UNKNOWN')).toBe('')
  })

  test('sourceTagColor 返回正确的颜色', () => {
    expect(wrapper.vm.sourceTagColor('INPUT')).toBe('primary')
    expect(wrapper.vm.sourceTagColor('OUTPUT')).toBe('success')
    expect(wrapper.vm.sourceTagColor('CONSTANT')).toBe('warning')
    expect(wrapper.vm.sourceTagColor('COMPUTED')).toBe('info')
    expect(wrapper.vm.sourceTagColor('DB')).toBe('danger')
    expect(wrapper.vm.sourceTagColor('API')).toBe('')
  })

  test('statusLabel 返回正确的状态标签', () => {
    expect(wrapper.vm.statusLabel(1)).toBe('启用')
    expect(wrapper.vm.statusLabel(0)).toBe('停用')
  })
})

describe('VariableList — 筛选与搜索', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('queryProjectCode 模糊匹配', () => {
    wrapper.vm.projectList = mockProjects()
    wrapper.vm.queryProjectCode('project_a')
    expect(wrapper.vm.filteredProjectCodes.length).toBe(1)
    wrapper.vm.queryProjectCode('全部')
    expect(wrapper.vm.filteredProjectCodes.length).toBe(2)
  })

  test('queryProjectName 模糊匹配', () => {
    wrapper.vm.projectList = mockProjects()
    wrapper.vm.queryProjectName('项目A')
    expect(wrapper.vm.filteredProjectNames.length).toBe(1)
    wrapper.vm.queryProjectName('')
    expect(wrapper.vm.filteredProjectNames.length).toBe(2)
  })

  test('queryVarCode 模糊匹配', () => {
    wrapper.vm.allVarCodes = ['age', 'income', 'MAX_AGE']
    wrapper.vm.queryVarCode('age')
    expect(wrapper.vm.filteredVarCodes.length).toBe(1)
  })

  test('queryVarLabel 模糊匹配', () => {
    wrapper.vm.allVarLabels = ['年龄', '收入', '最大年龄']
    wrapper.vm.queryVarLabel('年龄')
    expect(wrapper.vm.filteredVarLabels.length).toBe(1)
  })

  test('handleQuery 重置页码并加载', async () => {
    wrapper.vm.qp.pageNum = 5
    variableApi.listVariables.mockResolvedValue({ data: { records: [], total: 0 } })
    wrapper.vm.handleQuery()
    await Vue.nextTick()
    expect(wrapper.vm.qp.pageNum).toBe(1)
  })

  test('resetQuery 重置查询条件', () => {
    wrapper.vm.qp.scope = 'GLOBAL'
    wrapper.vm.qp.varType = 'INTEGER'
    wrapper.vm.qp.varCode = 'test'
    variableApi.listVariables.mockResolvedValue({ data: { records: [], total: 0 } })
    wrapper.vm.resetQuery()
    expect(wrapper.vm.qp.scope).toBe('')
    expect(wrapper.vm.qp.varType).toBe('')
    expect(wrapper.vm.qp.varCode).toBe('')
  })

  test('onVarScriptNameChange 触发保存', async () => {
    variableApi.updateVariable.mockResolvedValue({ data: true })
    const row = { id: 1, varCode: 'age', scriptName: 'age_new' }
    await wrapper.vm.onVarScriptNameChange(row)
    expect(variableApi.updateVariable).toHaveBeenCalled()
  })
})

describe('VariableList — 变量操作', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('handleEdit 填充编辑表单', () => {
    const row = { id: 1, varCode: 'age', varLabel: '年龄', varType: 'INTEGER', varSource: 'INPUT', scriptName: 'age' }
    wrapper.vm.handleEdit(row)
    expect(wrapper.vm.editForm.id).toBe(1)
    expect(wrapper.vm.editForm.varLabel).toBe('年龄')
    expect(wrapper.vm.editVisible).toBe(true)
  })

  test('handlePrimaryCreate 打开创建弹窗', () => {
    wrapper.vm.handlePrimaryCreate()
    expect(wrapper.vm.createVisible).toBe(true)
  })

  test('handleDelete 调用删除 API', async () => {
    variableApi.deleteVariable.mockResolvedValue({ data: true })
    const row = { id: 1, varLabel: '年龄' }
    wrapper.vm.handleDelete(row)
    await Vue.nextTick()
    expect(variableApi.deleteVariable).toHaveBeenCalledWith(1)
  })

  test('handleImportCmd 打开对应导入弹窗', () => {
    wrapper.vm.handleImportCmd('java-entity')
    expect(wrapper.vm.javaImportVisible).toBe(true)
    wrapper.vm.javaImportVisible = false
    wrapper.vm.handleImportCmd('json-object')
    expect(wrapper.vm.jsonImportVisible).toBe(true)
    wrapper.vm.jsonImportVisible = false
    wrapper.vm.handleImportCmd('ddl-table')
    expect(wrapper.vm.ddlImportVisible).toBe(true)
    wrapper.vm.ddlImportVisible = false
    wrapper.vm.handleImportCmd('java-const')
    expect(wrapper.vm.javaConstImportVisible).toBe(true)
    wrapper.vm.javaConstImportVisible = false
    wrapper.vm.handleImportCmd('json-const')
    expect(wrapper.vm.jsonConstImportVisible).toBe(true)
  })

  test('handleBatchValidate 调用批量验证 API', async () => {
    variableApi.batchValidateVariables.mockResolvedValue({ data: [] })
    wrapper.vm.handleBatchValidate()
    await Vue.nextTick()
    expect(variableApi.batchValidateVariables).toHaveBeenCalled()
    expect(wrapper.vm.validating).toBe(false)
  })
})

describe('VariableList — 边界情况', () => {
  test('listVariables 返回空数组不报错', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    variableApi.listVariables.mockResolvedValue({ data: { records: [], total: 0 } })
    const wrapper = mount(VariableList, {
      localVue: createTestVue(),
      mocks: {
        $route: { params: {} },
        $router: { push: jest.fn(), replace: jest.fn() }
      },
      stubs: {
        'el-form': true, 'el-form-item': true, 'el-select': true, 'el-option': true,
        'el-input': true, 'el-button': true, 'el-tag': true,
        'el-table': true, 'el-table-column': true,
        'el-tabs': true, 'el-tab-pane': true,
        'el-dialog': true, 'el-dropdown': true, 'el-dropdown-menu': true, 'el-dropdown-item': true,
        'el-pagination': true, 'el-switch': true, 'el-loading': true, 'el-textarea': true
      }
    })
    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
    expect(wrapper.vm.standaloneVars).toEqual([])
    wrapper.destroy()
  })

  test('onTabClick 切换 Tab 不报错', async () => {
    const wrapper = await mountAndWait()
    wrapper.vm.onTabClick({ name: 'object' })
    expect(wrapper.vm.activeTab).toBe('object')
    wrapper.destroy()
  })
})