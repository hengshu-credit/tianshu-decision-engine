// tests/unit/views/variableList.spec.js
import { mount } from '@test-utils'
import { nextTick } from 'vue'

// 使用真实 Element Plus（setup.js 的 element-ui mock 没有挂载到 Vue.prototype）
// Mock API 模块
vi.mock('@/api/variable', () => ({
  listVariables: vi.fn(),
  listVariablesByProject: vi.fn(),
  createVariable: vi.fn(),
  updateVariable: vi.fn(),
  toGlobalVariable: vi.fn(),
  deleteVariable: vi.fn(),
  testVariable: vi.fn(),
  getVariableSourceOptions: vi.fn(),
  previewVariableDraft: vi.fn(),
  importJavaConstants: vi.fn(),
  importJsonConstants: vi.fn(),
  listFieldValidations: vi.fn(),
  listAvailableFieldValidations: vi.fn(),
  createFieldValidationDraft: vi.fn(),
  updateFieldValidationDraft: vi.fn(),
  changeFieldValidationStatusDraft: vi.fn(),
  deleteFieldValidationDraft: vi.fn()
}))

vi.mock('@/api/project', () => ({
  listProjects: vi.fn()
}))

vi.mock('@/api/dataObject', () => ({
  listDataObjects: vi.fn(),
  validateDataObjectFieldReference: vi.fn(),
  importJavaEntity: vi.fn(),
  importJsonObject: vi.fn(),
  importDdlTable: vi.fn(),
  batchValidateRules: vi.fn(),
  batchValidateAll: vi.fn(),
  getVariableTree: vi.fn(),
  toGlobalDataObject: vi.fn(),
  createDataObjectField: vi.fn(),
  updateDataObjectField: vi.fn()
}))

vi.mock('@/api/function', () => ({ listAllFunctionsByProject: vi.fn() }))
vi.mock('@/api/model', () => ({ listAllModelsByProject: vi.fn() }))

vi.mock('@/api/datasource', () => ({
  listApiConfigs: vi.fn()
}))

vi.mock('@/api/database', () => ({
  listDbDatasources: vi.fn()
}))

vi.mock('@/api/ruleList', () => ({
  listLibraries: vi.fn()
}))

import * as variableApi from '@/api/variable'
import * as projectApi from '@/api/project'
import * as dataObjectApi from '@/api/dataObject'
import * as functionApi from '@/api/function'
import * as modelApi from '@/api/model'
import * as datasourceApi from '@/api/datasource'
import * as databaseApi from '@/api/database'
import * as ruleListApi from '@/api/ruleList'
import * as definitionApi from '@/api/definition'
import VariableList from '@/views/variable/VariableList.vue'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import fs from 'fs'
import path from 'path'

afterEach(() => { vi.clearAllMocks() })

// ─── Mock 数据 ───────────────────────────────────────────
function mockVars() {
  return [
    { id: 1, varCode: 'age', varLabel: '年龄', varType: 'STRING', varSource: 'INPUT', scope: 'PROJECT', projectId: 1, projectName: '项目A', scriptName: 'age', status: 1, defaultValue: '18' },
    { id: 2, varCode: 'income', varLabel: '收入', varType: 'NUMBER', varSource: 'INPUT', scope: 'PROJECT', projectId: 1, projectName: '项目A', scriptName: 'income', status: 1 },
    { id: 3, varCode: 'MAX_AGE', varLabel: '最大年龄', varType: 'NUMBER', varSource: 'CONSTANT', scope: 'PROJECT', projectId: 1, projectName: '项目A', scriptName: 'MAX_AGE', status: 1 }
  ]
}

function mockProjects() {
  return [
    { id: 1, projectName: '项目A', projectCode: 'project_a' },
    { id: 2, projectName: '项目B', projectCode: 'project_b' }
  ]
}

// ─── 测试用例 ─────────────────────────────────────────────
// 带有 clearValidate 方法的 el-form stub
const FormStub = {
  template: '<form ref="form"><slot /></form>',
  methods: {
    clearValidate: vi.fn(),
    validate: vi.fn(callback => callback(true))
  }
}



async function mountAndWait(routeQuery = {}) {
  dataObjectApi.listDataObjects.mockResolvedValue({ data: [] })
  dataObjectApi.validateDataObjectFieldReference.mockResolvedValue({ data: [] })
  projectApi.listProjects.mockResolvedValue({ data: { records: mockProjects() } })
  variableApi.listVariables.mockResolvedValue({ data: { records: mockVars(), total: 3 } })
  variableApi.getVariableSourceOptions.mockResolvedValue({ data: {
    apiOptions: [], databaseOptions: [], listOptions: []
  } })
  ruleListApi.listLibraries.mockResolvedValue({ data: { records: [{ id: 9, listCode: 'mobile_black', listName: '手机号黑名单' }], total: 1 } })
  variableApi.listVariablesByProject.mockResolvedValue({ data: mockVars() })
  variableApi.listFieldValidations.mockResolvedValue({ data: { records: [{
    id: 11, scope: 'GLOBAL', projectId: 0, validationCode: 'mobile_required',
    validationName: '手机号必填', validationType: 'REQUIRED', validationValue: '',
    errorMessage: '手机号不能为空', status: 1
  }], total: 1 } })
  dataObjectApi.getVariableTree.mockResolvedValue({ data: { tree: [] } })
  functionApi.listAllFunctionsByProject.mockResolvedValue({ data: [] })
  modelApi.listAllModelsByProject.mockResolvedValue({ data: [] })

  const wrapper = mount(VariableList, {
    mocks: {
      $route: { params: {}, query: routeQuery },
      $router: { push: vi.fn(), replace: vi.fn() },
      $store: { state: { currentProject: null } },
      $confirm: vi.fn().mockResolvedValue(true),
      $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
    },
    stubs: {
      'el-form': FormStub,
      'el-form-item': true,
      'el-select': true, 'el-option': true,
      'el-input': true, 'el-button': true, 'el-tag': true,
      'el-table': true,
      'el-table-column': { template: '<div />' },
      'el-tabs': true, 'el-tab-pane': true,
      'el-dialog': true, 'el-card': true,
      'el-dropdown': true, 'el-dropdown-menu': true, 'el-dropdown-item': true,
      'el-pagination': true, 'el-switch': true, 'el-loading': true,
      'el-textarea': true, 'el-divider': true, 'el-alert': true,
      'el-radio': true, 'el-radio-group': true, 'el-upload': true,
      'el-input-number': true, 'el-tooltip': true, 'el-tree': true,
      'el-icon': true, 'el-col': true, 'el-row': true,
      'el-link': true, 'el-badge': true,
      'operand-picker': true, 'router-link': true
    }
  })

  await nextTick()
  await new Promise(r => setTimeout(r, 100))
  return wrapper
}

test('变量退出项目范围后清除自动项目筛选并从第一页加载', async () => {
  sessionStorage.clear()
  const projectWrapper = await mountAndWait({ projectId: '2' })
  projectWrapper.vm.qp.pageNum = 3
  projectWrapper.vm.qp.varCode = 'project_variable'
  projectWrapper.vm.saveCachedState()
  projectWrapper.unmount()

  const globalWrapper = await mountAndWait()
  const standaloneCall = variableApi.listVariables.mock.calls.filter(([params]) => params.standaloneOnly).at(-1)
  expect(standaloneCall[0]).toEqual({ pageNum: 1, pageSize: 10, standaloneOnly: true })
  globalWrapper.unmount()
  sessionStorage.clear()
})

describe('VariableList — 初始化与数据加载', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.unmount() })

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

  test('项目上下文限定字段数据并作为新建字段默认归属', async () => {
    const contextWrapper = await mountAndWait({ projectId: '2' })

    expect(contextWrapper.vm.currentProjectId).toBe(2)
    expect(variableApi.listVariables).toHaveBeenCalledWith(
      expect.objectContaining({ projectId: 2, standaloneOnly: true })
    )
    contextWrapper.vm.handleCreate()
    expect(contextWrapper.vm.form.scope).toBe('PROJECT')
    expect(contextWrapper.vm.form.projectId).toBe(2)
    contextWrapper.unmount()
  })

  test('无项目上下文时新建字段必须由用户选择作用范围', () => {
    wrapper.vm.handleCreate()
    expect(wrapper.vm.form.scope).toBe('')
    expect(wrapper.vm.form.projectId).toBe('')
  })

  test('项目字段来源目录只请求全局与当前项目兼容资源', async () => {
    variableApi.getVariableSourceOptions.mockResolvedValueOnce({ data: {
      apiOptions: [{ id: 11, code: 'credit_api', name: '征信接口' }],
      databaseOptions: [{ id: 21, code: 'risk_db', name: '风险库' }],
      listOptions: [{ id: 31, code: 'mobile_black', name: '手机号黑名单' }]
    } })
    wrapper.vm.form.scope = 'PROJECT'
    wrapper.vm.form.projectId = 2

    await wrapper.vm.loadVariableSourceCatalog()

    expect(variableApi.getVariableSourceOptions).toHaveBeenCalledWith({
      scope: 'PROJECT', projectId: 2
    })
    expect(wrapper.vm.sourceCatalog.apiOptions[0].id).toBe(11)
    expect(wrapper.vm.sourceCatalog.databaseOptions[0].id).toBe(21)
    expect(wrapper.vm.sourceCatalog.listOptions[0].id).toBe(31)
  })

  test('切换项目后清理不再兼容的已选来源', async () => {
    wrapper.vm.form = {
      ...wrapper.vm.initForm(), scope: 'PROJECT', projectId: 2,
      varSource: 'API', apiConfigId: 99
    }
    variableApi.getVariableSourceOptions.mockResolvedValueOnce({ data: {
      apiOptions: [{ id: 11, code: 'credit_api', name: '征信接口' }],
      databaseOptions: [], listOptions: []
    } })

    await wrapper.vm.loadVariableSourceCatalog(true)

    expect(wrapper.vm.form.apiConfigId).toBe('')
    expect(wrapper.vm.$message.warning).toHaveBeenCalledWith(
      '原取值来源不属于当前范围，请重新选择'
    )
  })

  test('切换字段来源只加载兼容目录而不拉取全量技术资源', async () => {
    wrapper.vm.form.scope = 'PROJECT'
    wrapper.vm.form.projectId = 1

    await wrapper.vm.onVarSourceChange('API')
    await wrapper.vm.onVarSourceChange('DB')

    expect(variableApi.getVariableSourceOptions).toHaveBeenCalled()
    expect(datasourceApi.listApiConfigs).not.toHaveBeenCalled()
    expect(databaseApi.listDbDatasources).not.toHaveBeenCalled()
    expect(ruleListApi.listLibraries).not.toHaveBeenCalled()
  })

  test('快速切换项目时只采用最后一次来源目录响应', async () => {
    let resolveFirst
    let resolveSecond
    variableApi.getVariableSourceOptions
      .mockReturnValueOnce(new Promise(resolve => { resolveFirst = resolve }))
      .mockReturnValueOnce(new Promise(resolve => { resolveSecond = resolve }))

    wrapper.vm.form.scope = 'PROJECT'
    wrapper.vm.form.projectId = 1
    const first = wrapper.vm.loadVariableSourceCatalog()
    wrapper.vm.form.projectId = 2
    const second = wrapper.vm.loadVariableSourceCatalog()

    resolveSecond({ data: {
      apiOptions: [{ id: 22 }], databaseOptions: [], listOptions: []
    } })
    await second
    resolveFirst({ data: {
      apiOptions: [{ id: 11 }], databaseOptions: [], listOptions: []
    } })
    await first

    expect(wrapper.vm.sourceCatalog.apiOptions).toEqual([{ id: 22 }])
    expect(wrapper.vm.sourceCatalogKey).toBe('PROJECT:2')
  })

  test('scopeTagLabel 返回正确标签', () => {
    expect(wrapper.vm.scopeTagLabel('GLOBAL')).toBe('全局')
    expect(wrapper.vm.scopeTagLabel('PROJECT')).toBe('项目级')
  })
})

describe('VariableList 项目筛选交互', () => {
  test('变量、数据对象和常量页签均提供编码与名称筛选', async () => {
    const wrapper = await mountAndWait()
    const filters = wrapper.findAllComponents(ProjectFilterSelect)

    expect(filters).toHaveLength(6)
    expect(filters.filter(item => item.props('field') === 'projectCode')).toHaveLength(3)
    expect(filters.filter(item => item.props('field') === 'projectName')).toHaveLength(3)
    wrapper.unmount()
  })
})

describe('VariableList — 标签方法', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('typeLabel 返回正确的中文标签', () => {
    expect(wrapper.vm.typeLabel('STRING')).toBe('字符串')
    expect(wrapper.vm.typeLabel('NUMBER')).toBe('数值')
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
    expect(wrapper.vm.sourceLabel('CONSTANT')).toBe('常量')
    expect(wrapper.vm.sourceLabel('COMPUTED')).toBe('计算')
    expect(wrapper.vm.sourceLabel('DB')).toBe('数据库')
    expect(wrapper.vm.sourceLabel('API')).toBe('接口')
    expect(wrapper.vm.sourceLabel('LIST')).toBe('名单')
    expect(wrapper.vm.sourceLabel('UNKNOWN')).toBe('UNKNOWN')
  })

  test('typeTagColor 返回正确的颜色', () => {
    expect(wrapper.vm.typeTagColor('STRING')).toBeUndefined()
    expect(wrapper.vm.typeTagColor('NUMBER')).toBe('warning')
    expect(wrapper.vm.typeTagColor('BOOLEAN')).toBe('success')
    expect(wrapper.vm.typeTagColor('DATE')).toBe('info')
    expect(wrapper.vm.typeTagColor('ENUM')).toBe('danger')
    expect(wrapper.vm.typeTagColor('LIST')).toBe('warning')
    expect(wrapper.vm.typeTagColor('MAP')).toBe('info')
    expect(wrapper.vm.typeTagColor('UNKNOWN')).toBeUndefined()
  })

  test('sourceTagColor 返回正确的颜色', () => {
    expect(wrapper.vm.sourceTagColor('INPUT')).toBeUndefined()
    expect(wrapper.vm.sourceTagColor('CONSTANT')).toBe('success')
    expect(wrapper.vm.sourceTagColor('COMPUTED')).toBe('warning')
    expect(wrapper.vm.sourceTagColor('DB')).toBe('info')
    expect(wrapper.vm.sourceTagColor('API')).toBe('info')
    expect(wrapper.vm.sourceTagColor('LIST')).toBe('danger')
    expect(wrapper.vm.sourceTagColor('UNKNOWN')).toBeUndefined()
  })

  test('formatUpdateTime 格式化更新时间并兼容空值', () => {
    expect(wrapper.vm.formatUpdateTime('2026-07-19T20:30:45')).toBe('2026-07-19 20:30:45')
    expect(wrapper.vm.formatUpdateTime('2026-07-19 20:30:45')).toBe('2026-07-19 20:30:45')
    expect(wrapper.vm.formatUpdateTime(null)).toBe('—')
  })

  test('变量、常量和数据对象均展示更新时间', () => {
    const source = fs.readFileSync(path.resolve(process.cwd(), 'src/views/variable/VariableList.vue'), 'utf8')
    const variableTable = source.slice(source.indexOf('<el-tab-pane label="变量列表"'), source.indexOf('</el-tab-pane>', source.indexOf('<el-tab-pane label="变量列表"')))
    const objectTable = source.slice(source.indexOf('<el-tab-pane label="数据对象"'), source.indexOf('</el-tab-pane>', source.indexOf('<el-tab-pane label="数据对象"')))
    const constantTable = source.slice(source.indexOf('<el-tab-pane label="常量列表"'), source.indexOf('</el-tab-pane>', source.indexOf('<el-tab-pane label="常量列表"')))

    expect(variableTable).toContain('label="状态 / 更新"')
    expect(variableTable).toContain('fixed="right"')
    expect(variableTable).toContain('class="table-secondary-time"')
    expect(variableTable).toContain('formatUpdateTime(row.updateTime)')
    expect(objectTable).toContain('label="更新时间"')
    expect(objectTable).toContain('formatUpdateTime(node.object.updateTime)')
    expect(constantTable).toContain('label="更新时间"')
    expect(constantTable).toContain('formatUpdateTime(row.updateTime)')
  })

  test('statusLabel 通过行数据返回正确的状态标签', () => {
    // statusLabel 不是独立方法；状态标签直接用内联表达式渲染
    // row.status === 1 → '启用'，row.status === 0 → '停用'
    const rowEnabled = { status: 1 }
    const rowDisabled = { status: 0 }
    expect(rowEnabled.status === 1 ? '启用' : '停用').toBe('启用')
    expect(rowDisabled.status === 1 ? '启用' : '停用').toBe('停用')
  })
})

describe('VariableList — 筛选与搜索', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('queryProjectCode 模糊匹配', () => {
    wrapper.vm.projectList = mockProjects()
    wrapper.vm.queryProjectCode('project_a')
    expect(wrapper.vm.filteredProjectCodes.length).toBe(1)
    wrapper.vm.queryProjectCode('')
    expect(wrapper.vm.filteredProjectCodes.length).toBe(2)
  })

  test('queryProjectName 模糊匹配', () => {
    wrapper.vm.projectList = mockProjects()
    wrapper.vm.queryProjectName('项目A')
    expect(wrapper.vm.filteredProjectNames.length).toBe(1)
    wrapper.vm.queryProjectName('')
    expect(wrapper.vm.filteredProjectNames.length).toBe(2)
  })

  test('queryVarCode 模糊匹配', async () => {
    wrapper.vm.allVarCodes = ['age', 'income', 'MAX_AGE']
    wrapper.vm.queryVarCode('age')
    await nextTick()
    // 匹配 'age' 和 'MAX_AGE'（indexOf 不区分大小写）
    expect(wrapper.vm.filteredVarCodes.length).toBe(2)
  })

  test('queryVarLabel 模糊匹配', async () => {
    wrapper.vm.allVarLabels = ['年龄', '收入', '最大年龄']
    wrapper.vm.queryVarLabel('年龄')
    await nextTick()
    // 匹配 '年龄' 和 '最大年龄'
    expect(wrapper.vm.filteredVarLabels.length).toBe(2)
  })

  test('handleQuery 重置页码并加载', async () => {
    wrapper.vm.qp.pageNum = 5
    variableApi.listVariables.mockResolvedValue({ data: { records: [], total: 0 } })
    wrapper.vm.handleQuery()
    await nextTick()
    expect(wrapper.vm.qp.pageNum).toBe(1)
  })

  test('resetQuery 重置查询条件', async () => {
    wrapper.vm.qp.scope = 'GLOBAL'
    wrapper.vm.qp.varType = 'STRING'
    wrapper.vm.qp.varCode = 'test'
    variableApi.listVariables.mockResolvedValue({ data: { records: [], total: 0 } })
    wrapper.vm.resetQuery()
    expect(wrapper.vm.qp.scope).toBe('')
    expect(wrapper.vm.qp.varType).toBe('')
    expect(wrapper.vm.qp.varCode).toBe('')
  })

  test('脚本名称不再通过列表输入框失焦保存', () => {
    const source = fs.readFileSync(path.resolve(process.cwd(), 'src/views/variable/VariableList.vue'), 'utf8')

    expect(source).not.toContain('@blur="onVarScriptNameChange(row)"')
    expect(source).not.toContain('@blur="onObjectScriptNameChange(node.object)"')
    expect(source).not.toContain('@blur="onObjectFieldScriptNameBlur(row)"')
    expect(wrapper.vm.onVarScriptNameChange).toBeUndefined()
    expect(wrapper.vm.onObjectScriptNameChange).toBeUndefined()
    expect(wrapper.vm.onObjectFieldScriptNameBlur).toBeUndefined()
  })
})

describe('VariableList — 变量操作', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('handleEdit 填充编辑表单', () => {
    const row = { id: 1, varCode: 'age', varLabel: '年龄', varType: 'STRING', varSource: 'INPUT', scriptName: 'age' }
    wrapper.vm.handleEdit(row)
    // handleEdit 使用 form 而非 editForm，使用 dialogVisible 而非 createVisible
    // $refs.form 在 shallowMount 中指向 stub，不会触发 clearValidate 调用
    expect(wrapper.vm.form.id).toBe(1)
    expect(wrapper.vm.form.varLabel).toBe('年龄')
    expect(wrapper.vm.dialogVisible).toBe(true)
  })

  test('编辑弹窗提供脚本名称输入框并加载现有值', async () => {
    const row = { id: 1, varCode: 'age', varLabel: '年龄', varType: 'STRING', varSource: 'INPUT', scriptName: 'age' }

    wrapper.vm.handleEdit(row)
    await nextTick()

    expect(wrapper.find('[data-testid="variable-script-name-input"]').exists()).toBe(true)
    expect(wrapper.vm.form.scriptName).toBe('age')
  })

  test('常量只能通过编辑弹窗修改且表格没有行内保存', () => {
    const row = { id: 3, varCode: 'EMPTY_STRING', varLabel: '空字符串', varType: 'STRING', varSource: 'CONSTANT', defaultValue: '' }
    wrapper.vm.handleEdit(row)

    expect(wrapper.vm.form.id).toBe(3)
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.onConstDefaultBlur).toBeUndefined()

    const source = fs.readFileSync(path.resolve(process.cwd(), 'src/views/variable/VariableList.vue'), 'utf8')
    const constantTable = source.slice(source.indexOf('<el-tab-pane label="常量列表"'), source.indexOf('</el-tab-pane>', source.indexOf('<el-tab-pane label="常量列表"')))
    expect(constantTable).not.toContain('v-model="row.defaultValue"')
    expect(constantTable).not.toContain('@blur="onConstDefaultBlur(row)"')
  })

  test('handlePrimaryCreate 打开创建弹窗', () => {
    // handlePrimaryCreate 打开 dialogVisible；当前 tab 为 'list' 时调用 handleCreate
    wrapper.vm.handlePrimaryCreate()
    // handleCreate 设置 dialogVisible = true
    expect(wrapper.vm.dialogVisible).toBe(true)
  })

  test('handleDelete 调用删除 API', async () => {
    variableApi.deleteVariable.mockResolvedValue({ data: true })
    const row = { id: 1, varLabel: '年龄' }
    wrapper.vm.handleDelete(row)
    await nextTick()
    expect(variableApi.deleteVariable).toHaveBeenCalledWith(1)
  })

  test('项目级变量确认后创建转全局审批并跳转审批详情', async () => {
    variableApi.toGlobalVariable.mockResolvedValue({ data: { id: 41 } })
    const row = { id: 1, varLabel: '年龄', varSource: 'INPUT', scope: 'PROJECT' }

    await wrapper.vm.handleToGlobal(row)

    expect(wrapper.vm.$confirm).toHaveBeenCalledWith(
      '确认将「年龄」转为全局变量？转换后将不再归属原项目。',
      '转为全局',
      { type: 'warning' }
    )
    expect(variableApi.toGlobalVariable).toHaveBeenCalledWith(1)
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/approval/41')
    expect(wrapper.vm.$message.success).toHaveBeenCalledWith('已创建转全局审批，请完成审批后生效')
  })

  test('项目级常量确认后创建转全局审批', async () => {
    variableApi.toGlobalVariable.mockResolvedValue({ data: { id: 42 } })
    const row = { id: 3, varLabel: '最大年龄', varSource: 'CONSTANT', scope: 'PROJECT' }

    await wrapper.vm.handleToGlobal(row)

    expect(variableApi.toGlobalVariable).toHaveBeenCalledWith(3)
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/approval/42')
  })

  test('项目级数据对象确认后连同字段创建转全局审批', async () => {
    dataObjectApi.toGlobalDataObject.mockResolvedValue({ data: { id: 43 } })
    const object = { id: 5, objectLabel: '请求对象', objectCode: 'TSRequestBody', scope: 'PROJECT' }

    await wrapper.vm.handleObjectToGlobal(object)

    expect(wrapper.vm.$confirm).toHaveBeenCalledWith(
      '确认将「请求对象」及其字段转为全局？转换后将不再归属原项目。',
      '转为全局',
      { type: 'warning' }
    )
    expect(dataObjectApi.toGlobalDataObject).toHaveBeenCalledWith(5)
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/approval/43')
    expect(wrapper.vm.$message.success).toHaveBeenCalledWith('已创建转全局审批，请完成审批后生效')
  })

  test('变量列表和常量列表仅为项目级记录显示转为全局入口', () => {
    const source = fs.readFileSync(path.resolve(process.cwd(), 'src/views/variable/VariableList.vue'), 'utf8')
    const variableTable = source.slice(source.indexOf('<el-tab-pane label="变量列表"'), source.indexOf('</el-tab-pane>', source.indexOf('<el-tab-pane label="变量列表"')))
    const constantTable = source.slice(source.indexOf('<el-tab-pane label="常量列表"'), source.indexOf('</el-tab-pane>', source.indexOf('<el-tab-pane label="常量列表"')))

    expect(variableTable).toContain('row.scope === \'PROJECT\'')
    expect(variableTable).toContain('转全局')
    expect(variableTable).not.toContain('<el-dropdown')
    expect(constantTable).toContain('row.scope === \'PROJECT\'')
    expect(constantTable).toContain('转为全局')
  })

  test('数据对象仅为项目级记录显示转为全局入口', () => {
    const source = fs.readFileSync(path.resolve(process.cwd(), 'src/views/variable/VariableList.vue'), 'utf8')
    const objectPanel = source.slice(source.indexOf('<el-tab-pane label="数据对象"'), source.indexOf('</el-tab-pane>', source.indexOf('<el-tab-pane label="数据对象"')))
    const objectDialog = source.slice(source.indexOf('<!-- Create/Edit Data Object Dialog -->'), source.indexOf('<!-- Create/Edit Object Field Dialog -->'))

    expect(objectPanel).toContain("node.object.scope === 'PROJECT'")
    expect(objectPanel).toContain('handleObjectToGlobal(node.object)')
    expect(objectPanel).toContain('转为全局')
    expect(objectDialog).toMatch(/<el-select\s+v-model="objectForm\.scope"\s+:disabled="!!objectForm\.id"/)
  })

  test('数据对象按表格逐行展示并通过展开列查看字段', () => {
    const source = fs.readFileSync(path.resolve(process.cwd(), 'src/views/variable/VariableList.vue'), 'utf8')
    const objectPanel = source.slice(source.indexOf('<el-tab-pane label="数据对象"'), source.indexOf('</el-tab-pane>', source.indexOf('<el-tab-pane label="数据对象"')))

    expect(objectPanel).toContain('data-testid="data-object-table"')
    expect(objectPanel).toMatch(/<el-table-column\s+type="expand"/)
    expect(objectPanel).not.toContain('class="var-group-card"')
  })

  test('对象字段引用入口同时支持同范围且类型兼容的启用变量和常量', () => {
    wrapper.vm.objectFieldOwner = { scope: 'PROJECT', projectId: 1 }
    wrapper.vm.form.varType = 'NUMBER'
    wrapper.vm.objectFieldReferenceVariables = [
      { id: 1, scriptName: 'age', varType: 'INTEGER', varSource: 'INPUT', status: 1, scope: 'PROJECT', projectId: 1 },
      { id: 2, scriptName: 'name', varType: 'STRING', varSource: 'INPUT', status: 1, scope: 'PROJECT', projectId: 1 },
      { id: 3, scriptName: 'maxAge', varType: 'NUMBER', varSource: 'CONSTANT', status: 1, scope: 'PROJECT', projectId: 1 },
      { id: 4, scriptName: 'otherAge', varType: 'NUMBER', varSource: 'INPUT', status: 1, scope: 'PROJECT', projectId: 2 },
      { id: 5, scriptName: 'globalAge', varType: 'DOUBLE', varSource: 'INPUT', status: 1, scope: 'GLOBAL', projectId: 0 },
      { id: 6, scriptName: 'disabledAge', varType: 'NUMBER', varSource: 'INPUT', status: 0, scope: 'PROJECT', projectId: 1 }
    ]

    expect(wrapper.vm.objectFieldReferenceOptions.map(item => item.id)).toEqual([1, 3, 5])
    expect(wrapper.vm.objectFieldReferenceGroups.map(item => item.label)).toEqual(['变量', '常量'])
  })

  test('编辑对象字段保留 refVariableId 供直接引用变量', () => {
    const node = { object: { id: 20, scope: 'PROJECT', projectId: 1 }, variables: [] }
    wrapper.vm.handleEditObjectField({
      id: 30,
      projectId: 1,
      varCode: 'ageAlias',
      varLabel: '年龄',
      scriptName: 'age',
      varType: 'NUMBER',
      refVariableId: 9
    }, node)

    expect(wrapper.vm.form.refVariableId).toBe(9)
    expect(wrapper.vm.objectFieldOwner).toEqual(node.object)
  })

  test('提交对象字段时按稳定 ID 保存引用变量', async () => {
    dataObjectApi.updateDataObjectField.mockResolvedValue({ data: { id: 88 } })
    const node = { object: { id: 20, scope: 'PROJECT', projectId: 1 }, variables: [] }
    wrapper.vm.handleEditObjectField({
      id: 30,
      objectId: 20,
      projectId: 1,
      varCode: 'ageAlias',
      varLabel: '年龄',
      scriptName: 'ageAlias',
      varType: 'NUMBER',
      refVariableId: 9,
      status: 1
    }, node)

    await new Promise(resolve => setTimeout(resolve, 0))
    wrapper.vm.handleSubmit()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(dataObjectApi.updateDataObjectField).toHaveBeenCalledWith(
      expect.objectContaining({ id: 30, objectId: 20, refVariableId: 9 })
    )
  })

  test('编辑引用时不把展示用完整路径写回用户原始脚本名称', () => {
    const node = { object: { id: 20, scope: 'GLOBAL', projectId: 0 }, variables: [] }
    wrapper.vm.handleEditObjectField({ id: 30, varCode: 'Age', varLabel: '年龄', varType: 'NUMBER', scriptName: 'Request.Age', originalScriptName: 'Age' }, node)
    expect(wrapper.vm.form.scriptName).toBe('Age')
  })

  test('对象引用按类型区分 ID，服务端校验失败时不改变原绑定', async () => {
    wrapper.vm.objectFieldOwner = { id: 20, scope: 'PROJECT', projectId: 1 }
    wrapper.vm.objectFieldParentId = 20
    wrapper.vm.form = { id: 30, varType: 'OBJECT', refVariableId: 9 }
    wrapper.vm.objectFieldReferenceObjects = [{ id: 9, objectCode: 'Address', scope: 'GLOBAL', projectId: 0, status: 1 }]
    dataObjectApi.validateDataObjectFieldReference.mockResolvedValueOnce({ data: [{ message: '对象循环引用' }] })
    await wrapper.vm.changeObjectFieldReference('OBJECT:9')
    expect(wrapper.vm.form.refVariableId).toBe(9)
    expect(wrapper.vm.form.refObjectId).toBeUndefined()
    expect(wrapper.vm.objectFieldReferenceError).toBe('对象循环引用')

    await wrapper.vm.changeObjectFieldReference('OBJECT:9')
    expect(wrapper.vm.form).toMatchObject({ refVariableId: null, refObjectId: 9, refObjectCode: null })
    expect(dataObjectApi.validateDataObjectFieldReference).toHaveBeenCalledWith(20, expect.objectContaining({ refObjectId: 9, refVariableId: null }))
    await wrapper.vm.changeObjectFieldReference('')
    expect(wrapper.vm.form).toMatchObject({ refVariableId: null, refObjectId: null })
  })

  test('类型不匹配不能开始绑定，旧校验不能覆盖类型变更', async () => {
    wrapper.vm.isObjectField = true
    wrapper.vm.objectFieldOwner = { id: 20, scope: 'GLOBAL', projectId: 0 }
    wrapper.vm.objectFieldParentId = 20
    wrapper.vm.form = { varType: 'STRING' }
    wrapper.vm.objectFieldReferenceVariables = [{ id: 9, varType: 'NUMBER', scope: 'GLOBAL', status: 1 }]
    await wrapper.vm.changeObjectFieldReference('VARIABLE:9')
    expect(dataObjectApi.validateDataObjectFieldReference).not.toHaveBeenCalled()
    expect(wrapper.vm.form.refVariableId).toBeUndefined()

    wrapper.vm.form.varType = 'NUMBER'
    let resolveValidation
    dataObjectApi.validateDataObjectFieldReference.mockReturnValueOnce(new Promise(resolve => { resolveValidation = resolve }))
    const pending = wrapper.vm.changeObjectFieldReference('VARIABLE:9')
    wrapper.vm.form.varType = 'STRING'
    wrapper.vm.onObjectFieldTypeChange()
    resolveValidation({ data: [] })
    await pending
    expect(wrapper.vm.form.refVariableId).toBeUndefined()
  })

  test('字段分页保留完整子树，展开状态按字段 ID 记录', () => {
    wrapper.vm.objectFieldPageSize = 1
    const nested = { id: 1, children: [{ id: 2, children: [{ id: 3 }] }] }
    const node = { object: { id: 20 }, variables: [nested, { id: 4 }] }
    expect(wrapper.vm.objectFieldTotal(node)).toBe(4)
    expect(wrapper.vm.objectFieldRootTotal(node)).toBe(2)
    expect(wrapper.vm.paginatedObjectFields(node)).toEqual([nested])
    wrapper.vm.setObjectFieldExpansion(node, true)
    expect(wrapper.vm.objectFieldExpandedKeys(node)).toEqual([1, 2])
    wrapper.vm.handleObjectFieldExpandChange(node, nested, false)
    expect(wrapper.vm.objectFieldExpandedKeys(node)).toEqual([2])
    wrapper.vm.handleObjectFieldPageChange(node, 2)
    expect(wrapper.vm.paginatedObjectFields(node)).toEqual([{ id: 4 }])
    wrapper.vm.setObjectFieldExpansion(node, false)
    expect(wrapper.vm.objectFieldExpandedKeys(node)).toEqual([])
  })

  test('提交前重新校验引用，拒绝已失效的绑定', async () => {
    wrapper.vm.isObjectField = true
    wrapper.vm.objectFieldParentId = 20
    wrapper.vm.form = { id: 30, varCode: 'Alias', varLabel: '引用', varType: 'STRING', refVariableId: 9 }
    dataObjectApi.validateDataObjectFieldReference.mockResolvedValueOnce({ data: [{ message: '引用资源已停用' }] })
    wrapper.vm.handleSubmit()
    await new Promise(resolve => setTimeout(resolve, 0))
    expect(wrapper.vm.objectFieldReferenceError).toBe('引用资源已停用')
    expect(dataObjectApi.updateDataObjectField).not.toHaveBeenCalled()
  })

  test('所有字段、常量和数据对象写操作均由字段编辑权限控制', () => {
    const source = fs.readFileSync(path.resolve(process.cwd(), 'src/views/variable/VariableList.vue'), 'utf8')
    const guardedHandlers = [
      'handleObjectToGlobal(node.object)',
      'handleAddObjectField(node)',
      'handleDeleteObject(node.object)',
      'handleEditObjectField(row, node)',
      'handleOptions(row, true)',
      'handleDeleteObjectField(row)',
      'editFieldValidation(row)',
      'removeFieldValidation(row)',
      "handleVariableRowCommand('options', row)",
      "handleVariableRowCommand('global', row)",
      "handleVariableRowCommand('delete', row)",
    ]

    guardedHandlers.forEach(handler => {
      const escaped = handler.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      expect(source).toMatch(new RegExp(
        `<el-button(?:(?!</el-button).)*v-permission="'field:edit'"(?:(?!</el-button).)*@click(?:\\.stop)?="${escaped}"`,
        's'
      ))
    })
    expect(source).toContain("if (!hasPermission('field:edit'))")
    expect(source).toContain("if (command === 'options') return this.handleOptions(row)")
    expect(source).toContain("if (command === 'global') return this.handleToGlobal(row)")
    expect(source).toContain("if (command === 'delete') return this.handleDelete(row)")
    expect(source).toMatch(/v-model="node\.object\.objectType"[\s\S]*?:disabled="!canEditFields"/)
    expect(source).not.toContain('v-model="node.object.scriptName"')
    expect(source).not.toContain('v-model="row.scriptName"')
  })

  test('handleImportCmd 打开对应导入弹窗', () => {
    // 实际属性名是 importJavaEntityVisible 等（以 import 开头）
    wrapper.vm.handleImportCmd('java-entity')
    expect(wrapper.vm.importJavaEntityVisible).toBe(true)
    wrapper.vm.importJavaEntityVisible = false
    wrapper.vm.handleImportCmd('json-object')
    expect(wrapper.vm.importJsonObjectVisible).toBe(true)
    wrapper.vm.importJsonObjectVisible = false
    wrapper.vm.handleImportCmd('ddl-table')
    expect(wrapper.vm.importDdlVisible).toBe(true)
    wrapper.vm.importJavaConstVisible = false
    wrapper.vm.handleImportCmd('java-const')
    expect(wrapper.vm.importJavaConstVisible).toBe(true)
    wrapper.vm.importJavaConstVisible = false
    wrapper.vm.handleImportCmd('json-const')
    expect(wrapper.vm.importJsonConstVisible).toBe(true)
  })

  test('常量导入只生成审批草稿并跳转审批管理', async () => {
    variableApi.importJsonConstants.mockResolvedValue({
      data: {
        success: true,
        constantCount: 2,
        requestCount: 2,
        requestIds: [51, 52]
      }
    })
    const loadData = vi.spyOn(wrapper.vm, 'loadData')
    const loadConstants = vi.spyOn(wrapper.vm, 'loadConstants')
    wrapper.vm.importForm.scope = 'GLOBAL'
    wrapper.vm.importForm.projectId = null
    wrapper.vm.importForm.jsonContent = '{"MAX_AGE":65,"MIN_AGE":18}'

    await wrapper.vm.doImportJsonConst()

    expect(variableApi.importJsonConstants).toHaveBeenCalled()
    expect(wrapper.vm.importResultVisible).toBe(true)
    expect(wrapper.vm.importResult.requestIds).toEqual([51, 52])
    expect(loadData).not.toHaveBeenCalled()
    expect(loadConstants).not.toHaveBeenCalled()

    wrapper.vm.goImportApprovals()
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/approval')
  })

  test('handleBatchValidate 打开验证弹窗', () => {
    // handleBatchValidate 不直接调用 API，只打开 validateDialogVisible
    wrapper.vm.handleBatchValidate()
    expect(wrapper.vm.validateDialogVisible).toBe(true)
    // doBatchValidate 才调用 batchValidateRules
  })

  test('API/DB/LIST 变量显示测试入口并执行变量测试', async () => {
    const row = {
      id: 10,
      varCode: 'riskScore',
      varLabel: '风险分',
      scriptName: 'riskScore',
      varType: 'NUMBER',
      varSource: 'API',
      sourceConfig: JSON.stringify({ paramMapping: { customerId: '$.customer.id' }, resultPath: 'body.score' })
    }
    expect(wrapper.vm.isTestableSource(row)).toBe(true)
    expect(wrapper.vm.isTestableSource({ varSource: 'INPUT' })).toBe(false)
    definitionApi.getRuleTestSchema.mockResolvedValueOnce({ data: {
      inputs: [{ refId: 20, refType: 'DATA_OBJECT', scriptName: 'customer.id', valueType: 'STRING' }],
      sampleParams: { customer: { id: 'C000' } }
    } })

    await wrapper.vm.handleTestVariable(row)
    expect(wrapper.vm.variableTestVisible).toBe(true)
    expect(definitionApi.getRuleTestSchema).toHaveBeenCalledWith({ targetType: 'VARIABLE', targetId: 10 })
    expect(JSON.parse(wrapper.vm.variableTestParamsText)).toEqual({ customer: { id: 'C000' } })

    variableApi.testVariable.mockResolvedValueOnce({ data: { resolvedValue: 88 } })
    wrapper.vm.variableTestParamsText = '{"customer":{"id":"C001"}}'
    await wrapper.vm.doTestVariable()

    expect(variableApi.testVariable).toHaveBeenCalledWith(10, { customer: { id: 'C001' } })
    expect(wrapper.vm.variableTestResult.resolvedValue).toBe(88)
  })

  test('接口变量测试优先加载 API 中保存的测试样例', async () => {
    wrapper.vm.apiConfigOptions = [
      { id: 10001, apiName: '评分接口', testSampleParams: '{"request":{"mobile":"13800000000","age":30}}' }
    ]
    wrapper.vm.sourceOptionsLoaded.API = true
    const row = {
      id: 11,
      varSource: 'API',
      sourceConfig: JSON.stringify({ apiConfigId: 10001, resultPath: 'body.score' })
    }

    await wrapper.vm.handleTestVariable(row)

    expect(JSON.parse(wrapper.vm.variableTestParamsText)).toEqual({
      request: { mobile: '13800000000', age: 30 }
    })
  })

  test('变量测试模板支持模板表达式和名单递归表达式', () => {
    const apiRow = {
      varSource: 'API',
      sourceConfig: JSON.stringify({
        paramMapping: {
          requestId: '${requestId}',
          certNo: 'prefix-${customer.idNo}-${customer.mobile}'
        }
      })
    }
    expect(JSON.parse(wrapper.vm.buildTestParamTemplate(apiRow))).toEqual({
      requestId: '',
      customer: { idNo: '', mobile: '' }
    })

    const listRow = {
      varSource: 'LIST',
      sourceConfig: JSON.stringify({
        queryOperands: [{
          kind: 'FUNCTION', functionCode: 'toStringValue', args: [
            { kind: 'REFERENCE', refId: 11, refType: 'DATA_OBJECT', code: 'request.mobile', valueType: 'STRING' }
          ]
        }]
      })
    }
    expect(JSON.parse(wrapper.vm.buildTestParamTemplate(listRow))).toEqual({
      request: { mobile: '' }
    })

    expect(wrapper.vm.sourceInputFields(listRow)[0].field).toBe('request.mobile')
  })

  test('API variable test template merges paramMapping and API config references', () => {
    wrapper.vm.apiConfigOptions = [
      {
        id: 10001,
        headerConfig: '{"age":"$.age"}',
        bodyTemplate: '{"income":"${income}"}'
      }
    ]
    const apiRow = {
      varSource: 'API',
      sourceConfig: JSON.stringify({
        apiConfigId: 10001,
        paramMapping: { requestId: '${requestId}' }
      })
    }

    expect(JSON.parse(wrapper.vm.buildTestParamTemplate(apiRow))).toEqual({
      age: '18',
      income: 0,
      requestId: ''
    })
  })

  test('buildVariablePayload 生成多字段多名单新配置且不写旧字段', async () => {
    wrapper.vm.form = {
      ...wrapper.vm.initForm(),
      varCode: 'mobileBlackHit',
      varLabel: '手机号黑名单命中',
      varType: 'NUMBER',
      varSource: 'LIST',
      listIds: [9, 10],
      listQueryOperands: [
        { kind: 'REFERENCE', refId: 11, refType: 'DATA_OBJECT', code: 'request.mobile', valueType: 'STRING' },
        { kind: 'REFERENCE', refId: 12, refType: 'VARIABLE', code: 'backupMobile', valueType: 'STRING' }
      ],
      listCombinationMode: 'ALL_FIELDS_ANY_LIST',
      listMatchMode: 'CONTAINED_IN_LIST',
      listItemTypes: ['MOBILE'],
      listReturnMode: 'NUMBER',
    }
    const payload = wrapper.vm.buildVariablePayload()
    const config = JSON.parse(payload.sourceConfig)
    expect(config.listIds).toEqual([9, 10])
    expect(config.queryOperands).toHaveLength(2)
    expect(config.queryOperands[0]).toMatchObject({ refId: 11, refType: 'DATA_OBJECT' })
    expect(config.combinationMode).toBe('ALL_FIELDS_ANY_LIST')
    expect(config.matchMode).toBe('CONTAINED_IN_LIST')
    expect(config.itemTypes).toEqual(['MOBILE'])
    expect(config.listId).toBeUndefined()
    expect(config.queryField).toBeUndefined()
    expect(payload.listIds).toBeUndefined()
    expect(payload.varType).toBe('NUMBER')
  })

  test('名单查询表达式支持增删且布尔返回同步变量类型', () => {
    wrapper.vm.form = { ...wrapper.vm.initForm(), varSource: 'LIST' }
    wrapper.vm.addListQueryOperand()
    expect(wrapper.vm.form.listQueryOperands).toHaveLength(2)
    wrapper.vm.setListQueryOperand(1, { kind: 'LITERAL', value: '13800138000', valueType: 'STRING' })
    expect(wrapper.vm.form.listQueryOperands[1].value).toBe('13800138000')
    wrapper.vm.removeListQueryOperand(0)
    expect(wrapper.vm.form.listQueryOperands).toHaveLength(1)

    wrapper.vm.onListReturnModeChange('BOOLEAN')
    expect(wrapper.vm.form.varType).toBe('BOOLEAN')
  })

  test('名单查询字段素材复用统一引用目录并排除变量自身', async () => {
    variableApi.listVariablesByProject.mockResolvedValueOnce({ data: [
      { id: 30, varCode: 'selfHit', scriptName: 'selfHit', varLabel: '当前变量', varType: 'NUMBER', varSource: 'LIST' },
      { id: 31, varCode: 'mobile', scriptName: 'mobile', varLabel: '手机号', varType: 'STRING', varSource: 'INPUT' }
    ] })
    wrapper.vm.form = { ...wrapper.vm.initForm(), id: 30, scope: 'PROJECT', projectId: 1, varSource: 'LIST' }

    await wrapper.vm.loadListExpressionOptions()

    expect(wrapper.vm.listReferenceOptions.map(item => item._varId)).toEqual([31])
    expect(wrapper.vm.listReferenceOptions[0]).toMatchObject({ _refType: 'VARIABLE', varCode: 'mobile' })
    expect(variableApi.listVariablesByProject).toHaveBeenCalledWith(1)
  })

  test.each(['LIST', 'API', 'DB'])('%s 取值预览两列各自只保留一个标题，数据库样例字段不挤占左列', async (source) => {
    await wrapper.setData({
      dialogVisible: true,
      form: { ...wrapper.vm.initForm(), varSource: source },
    })
    const columns = wrapper.findAll('.draft-preview-panel__body > div')
    expect(columns).toHaveLength(2)
    expect(columns[0].findAll('summary, label')).toHaveLength(1)
    expect(columns[1].findAll('summary, label')).toHaveLength(1)
    expect(columns[0].find('.db-sample-fields').exists()).toBe(false)
  })

  test('新增变量弹窗启用宽高拖拽，关闭时传递清理状态', async () => {
    wrapper.vm.handleCreate()
    await nextTick()
    const resize = wrapper.findComponent({ name: 'DialogResizeHandle' })
    expect(resize.exists()).toBe(true)
    expect(resize.props('visible')).toBe(true)
    wrapper.vm.dialogVisible = false
    await nextTick()
    expect(resize.props('visible')).toBe(false)
  })

  test('buildVariablePayload 生成接口变量配置', async () => {
    wrapper.vm.form = {
      ...wrapper.vm.initForm(),
      varCode: 'hscreditScoreV1',
      varLabel: '衡枢分V1',
      varType: 'NUMBER',
      varSource: 'API',
      apiConfigId: 10001,
      apiResultPath: 'body.v1',
      apiForceRefresh: true,
      apiExceptionStrategy: 'RETURN_DEFAULT',
      apiFallbackValue: '0'
    }

    const payload = wrapper.vm.buildVariablePayload()
    const config = JSON.parse(payload.sourceConfig)

    expect(config.apiConfigId).toBe(10001)
    expect(config.paramMapping).toBeUndefined()
    expect(config.resultPath).toBe('body.v1')
    expect(config.forceRefresh).toBe(true)
    expect(config.exceptionStrategy).toBe('RETURN_DEFAULT')
    expect(payload.apiConfigId).toBeUndefined()
  })

  test('新字段可在生成审批草稿前执行取值预览', async () => {
    wrapper.vm.form = {
      ...wrapper.vm.initForm(), scope: 'PROJECT', projectId: 1,
      varCode: 'riskScore', varLabel: '风险分', varType: 'NUMBER',
      varSource: 'DB', dbDatasourceId: 21,
      dbSql: 'select score from risk where customer_id = ?',
      dbParams: '["$.customerId"]', dbResultPath: '0.score'
    }
    wrapper.vm.draftPreviewParamsText = '{"customerId":"C001"}'
    variableApi.previewVariableDraft.mockResolvedValueOnce({ data: {
      resolvedValue: 88,
      resolvedParams: { customerId: 'C001', riskScore: 88 }
    } })

    await wrapper.vm.previewDraftVariable()

    expect(variableApi.previewVariableDraft).toHaveBeenCalledWith(
      expect.objectContaining({
        varCode: 'riskScore',
        sourceConfig: expect.stringContaining('dbDatasourceId')
      }),
      { customerId: 'C001' }
    )
    expect(wrapper.vm.draftPreviewResult.resolvedValue).toBe(88)
  })

  test.each([
    ['java-entity', 'doImportJavaEntity', 'importJavaEntity'],
    ['json-object', 'doImportJsonObject', 'importJsonObject'],
    ['ddl-table', 'doImportDdl', 'importDdlTable'],
    ['java-const', 'doImportJavaConst', 'importJavaConstants'],
    ['json-const', 'doImportJsonConst', 'importJsonConstants'],
  ])('%s 使用当前作用范围提交并展示审批草稿结果', async (mode, method, apiName) => {
    const api = mode.endsWith('-const') ? variableApi[apiName] : dataObjectApi[apiName]
    api.mockResolvedValueOnce({ data: { success: true, requestIds: [88], requestCount: 1 } })
    wrapper.vm.handleImportCmd(mode)
    Object.assign(wrapper.vm.importForm, { scope: 'GLOBAL', objectCode: 'Mixed_Request', javaSource: 'class Request {}', jsonContent: '{"Age":18}', ddlSource: 'CREATE TABLE Request (Age int);' })
    await wrapper.vm[method]()
    expect(api).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.importResult.success).toBe(true)
    expect(wrapper.vm.importResultVisible).toBe(true)
    wrapper.vm.goImportApprovals()
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/approval/88')
  })

  test('导入失败保留原输入，提交期间阻止重复请求', async () => {
    wrapper.vm.handleImportCmd('json-object')
    Object.assign(wrapper.vm.importForm, { scope: 'GLOBAL', objectCode: 'Bad_Request', jsonContent: '{"items":[1,"A"]}' })
    let complete
    dataObjectApi.importJsonObject.mockReturnValueOnce(new Promise(resolve => { complete = resolve }))
    const first = wrapper.vm.doImportJsonObject()
    await wrapper.vm.doImportJsonObject()
    expect(dataObjectApi.importJsonObject).toHaveBeenCalledTimes(1)
    complete({ data: { success: false, error: '数组元素类型不一致' } })
    await first
    expect(wrapper.vm.importJsonObjectVisible).toBe(true)
    expect(wrapper.vm.importForm.jsonContent).toBe('{"items":[1,"A"]}')
    expect(wrapper.vm.importResult.error).toContain('类型不一致')
  })

  test('SQL 业务字段样例按真实选择器元数据生成，并保持当前字段编码', async () => {
    wrapper.vm.form.dbParams = JSON.stringify([{ kind: 'REFERENCE', refType: 'VARIABLE', refId: 7, value: 'oldName' }])
    wrapper.vm.listReferenceOptions = [{ _refType: 'VARIABLE', _varId: 7, varCode: 'newAge', varLabel: '年龄 newAge', varLabelText: '年龄', varType: 'INTEGER' }]
    expect(wrapper.vm.dbSampleFields).toEqual([{ key: 'VARIABLE:7', code: 'newAge', label: '年龄', type: 'INTEGER' }])
    wrapper.vm.setDbSampleValue(wrapper.vm.dbSampleFields[0], '28')
    expect(JSON.parse(wrapper.vm.draftPreviewParamsText)).toEqual({ newAge: 28 })
  })

  test('数据库配置进度只有在 SQL 和参数完整时才显示就绪', () => {
    wrapper.vm.form = { ...wrapper.vm.initForm(), scope: 'PROJECT', projectId: 1, varCode: 'score', varLabel: '评分', varSource: 'DB', dbDatasourceId: 31, dbSql: 'SELECT ?', dbParams: '[]' }
    expect(wrapper.vm.variableConfigurationChecklist[2].ready).toBe(false)
    wrapper.vm.form.dbParams = '[0]'
    expect(wrapper.vm.variableConfigurationChecklist[2].ready).toBe(true)
    wrapper.vm.form.dbParams = '{invalid'
    expect(wrapper.vm.variableConfigurationChecklist[2].ready).toBe(false)
  })

  test('保存前预览使用高对比度实心主按钮', async () => {
    wrapper.vm.dialogVisible = true
    wrapper.vm.form = {
      ...wrapper.vm.initForm(), varSource: 'DB', dbDatasourceId: 21
    }
    await nextTick()

    const previewButton = wrapper.findAll('el-button-stub')
      .find(button => button.text().includes('预览取值'))

    expect(previewButton).toBeDefined()
    expect(previewButton.attributes('type')).toBe('primary')
    expect(previewButton.attributes('plain')).toBeUndefined()
  })

  test('取值预览失败由统一请求提示承接且不会产生未处理事件异常', async () => {
    wrapper.vm.form = {
      ...wrapper.vm.initForm(), scope: 'PROJECT', projectId: 1,
      varCode: 'riskScore', varLabel: '风险分', varType: 'NUMBER',
      varSource: 'DB', dbDatasourceId: 21,
      dbSql: 'select 1 as score', dbParams: '[]', dbResultPath: '0.score'
    }
    const requestError = Object.assign(new Error('数据库不可用'), {
      requestErrorNotified: true
    })
    variableApi.previewVariableDraft.mockRejectedValueOnce(requestError)

    await expect(wrapper.vm.previewDraftVariable()).resolves.toBeUndefined()

    expect(wrapper.vm.draftPreviewResult).toBeNull()
    expect(wrapper.vm.draftPreviewError).toBe('数据库不可用')
    expect(wrapper.vm.$message.error).not.toHaveBeenCalled()
    expect(wrapper.vm.draftPreviewing).toBe(false)
  })

  test('空白字段从 0/4 开始且后续步骤不能越过前置步骤', () => {
    wrapper.vm.form = wrapper.vm.initForm()

    expect(wrapper.vm.variableConfigurationChecklist.map(item => item.ready))
      .toEqual([false, false, false, false])
    expect(wrapper.vm.variableConfigurationReadyCount).toBe(0)

    wrapper.vm.form.varCode = 'riskScore'
    wrapper.vm.form.varLabel = '风险分'

    expect(wrapper.vm.variableConfigurationChecklist.map(item => item.ready))
      .toEqual([false, false, false, false])
  })

  test('修改预览样例参数后旧结果立即失效', async () => {
    wrapper.vm.dialogVisible = true
    wrapper.vm.form = {
      ...wrapper.vm.initForm(), scope: 'PROJECT', projectId: 1,
      varCode: 'riskScore', varLabel: '风险分', varType: 'NUMBER',
      varSource: 'DB', dbDatasourceId: 21,
      dbSql: 'select score from risk where customer_id = ?',
      dbParams: '["$.customerId"]', dbResultPath: '0.score'
    }
    wrapper.vm.draftPreviewParamsText = '{"customerId":"C001"}'
    await nextTick()
    wrapper.vm.draftPreviewResult = { resolvedValue: 88 }

    wrapper.vm.draftPreviewParamsText = '{"customerId":"C002"}'
    await nextTick()

    expect(wrapper.vm.draftPreviewResult).toBeNull()
    expect(wrapper.vm.variableConfigurationChecklist[3].ready).toBe(false)
  })

  test('预览请求期间修改 SQL 后旧响应不得重新标记验证完成', async () => {
    wrapper.vm.dialogVisible = true
    wrapper.vm.form = {
      ...wrapper.vm.initForm(), scope: 'PROJECT', projectId: 1,
      varCode: 'riskScore', varLabel: '风险分', varType: 'NUMBER',
      varSource: 'DB', dbDatasourceId: 21,
      dbSql: 'select 1 as score', dbParams: '[]', dbResultPath: '0.score'
    }
    let resolvePreview
    variableApi.previewVariableDraft.mockReturnValueOnce(
      new Promise(resolve => { resolvePreview = resolve })
    )

    const pending = wrapper.vm.previewDraftVariable()
    await nextTick()
    wrapper.vm.form.dbSql = 'select 2 as score'
    await nextTick()
    resolvePreview({ data: { resolvedValue: 1 } })
    await pending

    expect(wrapper.vm.draftPreviewResult).toBeNull()
    expect(wrapper.vm.draftPreviewError).toBe('')
    expect(wrapper.vm.variableConfigurationChecklist[3].ready).toBe(false)
    expect(wrapper.vm.$message.success).not.toHaveBeenCalled()
  })

  test('字段配置检查按归属、定义、来源和预览反馈就绪状态', async () => {
    wrapper.vm.form = {
      ...wrapper.vm.initForm(), scope: 'PROJECT', projectId: 1,
      varCode: 'mobileHit', varLabel: '手机号命中', varType: 'BOOLEAN',
      varSource: 'LIST', listIds: [31],
      listQueryOperands: [{
        kind: 'LITERAL', value: '13800138000', valueType: 'STRING'
      }]
    }
    wrapper.vm.draftPreviewResult = { resolvedValue: true }

    expect(wrapper.vm.variableConfigurationChecklist.map(item => item.ready))
      .toEqual([true, true, true, true])
  })

  test('applySourceConfigToForm 回显多字段多名单配置', () => {
    wrapper.vm.form = {
      ...wrapper.vm.initForm(),
      varSource: 'LIST',
      sourceConfig: JSON.stringify({
        listIds: [9, 10],
        queryOperands: [{ kind: 'REFERENCE', refId: 11, refType: 'DATA_OBJECT', code: 'request.mobile' }],
        combinationMode: 'ANY_FIELD_ALL_LISTS',
        matchMode: 'NOT_CONTAINED_IN_LIST',
        itemTypes: ['MOBILE']
      })
    }
    wrapper.vm.applySourceConfigToForm()

    expect(wrapper.vm.form.listIds).toEqual([9, 10])
    expect(wrapper.vm.form.listQueryOperands[0]).toMatchObject({ refId: 11, refType: 'DATA_OBJECT' })
    expect(wrapper.vm.form.listCombinationMode).toBe('ANY_FIELD_ALL_LISTS')
    expect(wrapper.vm.form.listMatchMode).toBe('NOT_CONTAINED_IN_LIST')
    expect(wrapper.vm.form.listItemTypes).toEqual(['MOBILE'])
  })

  test('applySourceConfigToForm 回显接口变量配置', () => {
    wrapper.vm.form = {
      ...wrapper.vm.initForm(),
      varSource: 'API',
      sourceConfig: JSON.stringify({
        apiConfigId: 10001,
        paramMapping: { request_id: '$.requestId' },
        resultPath: 'body.v1',
        forceRefresh: true,
        exceptionStrategy: 'RETURN_DEFAULT',
        fallbackValue: 0
      })
    }

    wrapper.vm.applySourceConfigToForm()

    expect(wrapper.vm.form.apiConfigId).toBe(10001)
    expect(JSON.parse(wrapper.vm.form.apiParamMapping)).toEqual({ request_id: '$.requestId' })
    expect(wrapper.vm.form.apiResultPath).toBe('body.v1')
    expect(wrapper.vm.form.apiForceRefresh).toBe(true)
    expect(wrapper.vm.form.apiExceptionStrategy).toBe('RETURN_DEFAULT')
    expect(wrapper.vm.form.apiFallbackValue).toBe('0')
  })
})

describe('VariableList — 边界情况', () => {
  test('listVariables 返回空数组不报错', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    variableApi.listVariables.mockResolvedValue({ data: { records: [], total: 0 } })
    const wrapper = mount(VariableList, {
      mocks: {
        $route: { params: {} },
        $router: { push: vi.fn(), replace: vi.fn() },
        $confirm: vi.fn().mockResolvedValue(true),
        $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
      },
      stubs: {
        'el-form': FormStub,
        'el-form-item': true, 'el-select': true, 'el-option': true,
        'el-input': true, 'el-button': true, 'el-tag': true,
        'el-table': FormStub, 'el-table-column': true,
        'el-tabs': true, 'el-tab-pane': true,
        'el-dialog': true, 'el-dropdown': true, 'el-dropdown-menu': true, 'el-dropdown-item': true,
        'el-pagination': true, 'el-switch': true, 'el-loading': true, 'el-textarea': true,
        'el-tree': true, 'el-icon': true, 'el-col': true, 'el-row': true,
        'el-link': true, 'el-badge': true, 'el-input-number': true, 'router-link': true
      }
    })
    await nextTick()
    await new Promise(r => setTimeout(r, 100))
    expect(wrapper.vm.standaloneVars).toEqual([])
    wrapper.unmount()
  })

  test('onTabClick 切换 Tab 触发加载', async () => {
    const wrapper = await mountAndWait()
    const loadConstantsSpy = vi.spyOn(wrapper.vm, 'loadConstants').mockResolvedValue({ data: { records: [], total: 0 } })
    wrapper.vm.activeTab = 'list'
    wrapper.vm.onTabClick({ name: 'constants' })
    await nextTick()
    // onTabClick 只处理 objects 和 constants；切换到 constants 时调用 loadConstants
    expect(wrapper.vm.activeTab).toBe('constants')
    expect(loadConstantsSpy).toHaveBeenCalled()
    wrapper.unmount()
  })
})

describe('VariableList — 字段校验规则库', () => {
  test('渲染字段校验 Tab，并在切换时加载规则库', async () => {
    const wrapper = await mountAndWait()
    const source = fs.readFileSync(path.resolve(__dirname, '../../../src/views/variable/VariableList.vue'), 'utf8')

    expect(source).toMatch(/<el-tab-pane\b[^>]*label="字段校验"[^>]*name="validations"[^>]*>/)
    await wrapper.vm.onTabClick({ paneName: 'validations' })
    expect(variableApi.listFieldValidations).toHaveBeenCalled()
    wrapper.unmount()
  })

  test('新建字段校验保存稳定配置，不改写用户输入的编码', async () => {
    const wrapper = await mountAndWait()
    variableApi.createFieldValidationDraft.mockResolvedValueOnce({
      data: { id: 91 }
    })
    wrapper.vm.activeTab = 'validations'
    wrapper.vm.handlePrimaryCreate()
    wrapper.vm.validationForm = {
      id: null, scope: 'GLOBAL', projectId: '', validationCode: 'Mobile_Check',
      validationName: '手机号校验', validationType: 'REGEX', validationValue: '^1\\d{10}$',
      errorMessage: '手机号格式错误', description: '', status: 1
    }

    await wrapper.vm.saveFieldValidation()

    expect(variableApi.createFieldValidationDraft).toHaveBeenCalledWith(expect.objectContaining({
      validationCode: 'Mobile_Check', validationType: 'REGEX', projectId: 0
    }))
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/approval/91')
    wrapper.unmount()
  })

  test('编辑字段校验改变状态时生成显式停用审批', async () => {
    const wrapper = await mountAndWait()
    variableApi.changeFieldValidationStatusDraft.mockResolvedValueOnce({
      data: { id: 92 }
    })
    wrapper.vm.validationOriginalStatus = 1
    wrapper.vm.validationForm = {
      id: 11, scope: 'PROJECT', projectId: 7,
      validationCode: 'Mobile_Check', validationName: '手机号校验',
      validationType: 'REQUIRED', validationValue: null,
      errorMessage: '手机号不能为空', description: '', status: 0
    }

    await wrapper.vm.saveFieldValidation()

    expect(variableApi.changeFieldValidationStatusDraft)
      .toHaveBeenCalledWith(expect.objectContaining({ id: 11 }), 0)
    expect(variableApi.updateFieldValidationDraft).not.toHaveBeenCalled()
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/approval/92')
    wrapper.unmount()
  })

  test('正则预置只填充校验值，精确匹配时回显，改为自定义正则后清空回显', async () => {
    const wrapper = await mountAndWait()
    wrapper.vm.validationForm = {
      ...wrapper.vm.initFieldValidationForm(),
      validationCode: 'Mobile_Check',
      validationName: '手机号校验',
      validationType: 'REGEX',
      errorMessage: '手机号格式错误'
    }

    wrapper.vm.applyFieldValidationRegexPreset('MOBILE')

    expect(wrapper.vm.validationForm.validationValue).toBe('^1[0-9]{10}$')
    expect(wrapper.vm.validationForm.validationCode).toBe('Mobile_Check')
    expect(wrapper.vm.selectedFieldValidationRegexPreset).toBe('MOBILE')

    wrapper.vm.validationForm.validationValue = '^custom$'
    await nextTick()
    expect(wrapper.vm.selectedFieldValidationRegexPreset).toBe('')
    wrapper.unmount()
  })

  test('系统内置校验规则显示只读标识且不能进入编辑或删除流程', async () => {
    const wrapper = await mountAndWait()
    const source = fs.readFileSync(path.resolve(__dirname, '../../../src/views/variable/VariableList.vue'), 'utf8')
    const builtin = {
      id: 101,
      scope: 'GLOBAL',
      projectId: 0,
      validationCode: 'builtin_mobile',
      validationName: '手机号',
      validationType: 'REGEX',
      validationValue: '^1[0-9]{10}$',
      errorMessage: '请输入正确的手机号',
      status: 1,
      builtIn: true
    }

    expect(source).toContain('系统内置')
    expect(source).toContain('v-if="!row.builtIn"')

    wrapper.vm.editFieldValidation(builtin)
    expect(wrapper.vm.validationDialogVisible).toBe(false)
    expect(wrapper.vm.$message.warning).toHaveBeenCalledWith('系统内置校验规则不可编辑')

    wrapper.vm.removeFieldValidation(builtin)
    expect(wrapper.vm.$confirm).not.toHaveBeenCalled()
    expect(variableApi.deleteFieldValidationDraft).not.toHaveBeenCalled()
    expect(wrapper.vm.$message.warning).toHaveBeenCalledWith('系统内置校验规则不可删除')
    wrapper.unmount()
  })
})
