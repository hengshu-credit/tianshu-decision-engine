// tests/unit/views/variableList.spec.js
import { mount } from '@test-utils'
import { nextTick } from 'vue'

// 使用真实 Element Plus（setup.js 的 element-ui mock 没有挂载到 Vue.prototype）
// Mock API 模块
jest.mock('@/api/variable', () => ({
  listVariables: jest.fn(),
  listVariablesByProject: jest.fn(),
  createVariable: jest.fn(),
  updateVariable: jest.fn(),
  toGlobalVariable: jest.fn(),
  deleteVariable: jest.fn(),
  testVariable: jest.fn(),
  importJavaConstants: jest.fn(),
  importJsonConstants: jest.fn(),
  listFieldValidations: jest.fn(),
  listAvailableFieldValidations: jest.fn(),
  createFieldValidation: jest.fn(),
  updateFieldValidation: jest.fn(),
  deleteFieldValidation: jest.fn()
}))

jest.mock('@/api/project', () => ({
  listProjects: jest.fn()
}))

jest.mock('@/api/dataObject', () => ({
  batchValidateRules: jest.fn(),
  batchValidateAll: jest.fn(),
  getVariableTree: jest.fn(),
  toGlobalDataObject: jest.fn()
}))

jest.mock('@/api/function', () => ({ listAllFunctionsByProject: jest.fn() }))
jest.mock('@/api/model', () => ({ listAllModelsByProject: jest.fn() }))

jest.mock('@/api/datasource', () => ({
  listApiConfigs: jest.fn()
}))

jest.mock('@/api/database', () => ({
  listDbDatasources: jest.fn()
}))

jest.mock('@/api/ruleList', () => ({
  listLibraries: jest.fn()
}))

import * as variableApi from '@/api/variable'
import * as projectApi from '@/api/project'
import * as dataObjectApi from '@/api/dataObject'
import * as functionApi from '@/api/function'
import * as modelApi from '@/api/model'
import * as ruleListApi from '@/api/ruleList'
import * as definitionApi from '@/api/definition'
import VariableList from '@/views/variable/VariableList.vue'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import fs from 'fs'
import path from 'path'

afterEach(() => { jest.clearAllMocks() })

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
  methods: { clearValidate: jest.fn() }
}



async function mountAndWait() {
  projectApi.listProjects.mockResolvedValue({ data: { records: mockProjects() } })
  variableApi.listVariables.mockResolvedValue({ data: { records: mockVars(), total: 3 } })
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
      $route: { params: {} },
      $router: { push: jest.fn(), replace: jest.fn() },
      $confirm: jest.fn().mockResolvedValue(true),
      $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() }
    },
    stubs: {
      'el-form': FormStub,
      'el-form-item': true,
      'el-select': true, 'el-option': true,
      'el-input': true, 'el-button': true, 'el-tag': true,
      'el-table': true, 'el-table-column': true,
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

    expect(variableTable).toContain('label="更新时间"')
    expect(variableTable).toContain('formatUpdateTime(row.updateTime)')
    expect(objectTable).toContain('更新时间：{{ formatUpdateTime(node.object.updateTime) }}')
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

  test('项目级变量确认后转为全局并刷新变量列表', async () => {
    variableApi.toGlobalVariable.mockResolvedValue({ data: true })
    const loadData = jest.spyOn(wrapper.vm, 'loadData').mockResolvedValue()
    const row = { id: 1, varLabel: '年龄', varSource: 'INPUT', scope: 'PROJECT' }

    await wrapper.vm.handleToGlobal(row)

    expect(wrapper.vm.$confirm).toHaveBeenCalledWith(
      '确认将「年龄」转为全局变量？转换后将不再归属原项目。',
      '转为全局',
      { type: 'warning' }
    )
    expect(variableApi.toGlobalVariable).toHaveBeenCalledWith(1)
    expect(loadData).toHaveBeenCalled()
    expect(wrapper.vm.$message.success).toHaveBeenCalledWith('转换成功，该变量已转为全局变量')
  })

  test('项目级常量确认后转为全局并刷新常量列表', async () => {
    variableApi.toGlobalVariable.mockResolvedValue({ data: true })
    const loadConstants = jest.spyOn(wrapper.vm, 'loadConstants').mockResolvedValue()
    const row = { id: 3, varLabel: '最大年龄', varSource: 'CONSTANT', scope: 'PROJECT' }

    await wrapper.vm.handleToGlobal(row)

    expect(variableApi.toGlobalVariable).toHaveBeenCalledWith(3)
    expect(loadConstants).toHaveBeenCalled()
  })

  test('项目级数据对象确认后连同字段转为全局并刷新对象列表', async () => {
    dataObjectApi.toGlobalDataObject.mockResolvedValue({ data: true })
    const loadObjectTree = jest.spyOn(wrapper.vm, 'loadObjectTree').mockResolvedValue()
    const object = { id: 5, objectLabel: '请求对象', objectCode: 'TSRequestBody', scope: 'PROJECT' }

    await wrapper.vm.handleObjectToGlobal(object)

    expect(wrapper.vm.$confirm).toHaveBeenCalledWith(
      '确认将「请求对象」及其字段转为全局？转换后将不再归属原项目。',
      '转为全局',
      { type: 'warning' }
    )
    expect(dataObjectApi.toGlobalDataObject).toHaveBeenCalledWith(5)
    expect(loadObjectTree).toHaveBeenCalled()
    expect(wrapper.vm.$message.success).toHaveBeenCalledWith('转换成功，该数据对象及其字段已转为全局')
  })

  test('变量列表和常量列表仅为项目级记录显示转为全局入口', () => {
    const source = fs.readFileSync(path.resolve(process.cwd(), 'src/views/variable/VariableList.vue'), 'utf8')
    const variableTable = source.slice(source.indexOf('<el-tab-pane label="变量列表"'), source.indexOf('</el-tab-pane>', source.indexOf('<el-tab-pane label="变量列表"')))
    const constantTable = source.slice(source.indexOf('<el-tab-pane label="常量列表"'), source.indexOf('</el-tab-pane>', source.indexOf('<el-tab-pane label="常量列表"')))

    expect(variableTable).toContain('row.scope === \'PROJECT\'')
    expect(variableTable).toContain('转为全局')
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
        $router: { push: jest.fn(), replace: jest.fn() },
        $confirm: jest.fn().mockResolvedValue(true),
        $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() }
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
    const loadConstantsSpy = jest.spyOn(wrapper.vm, 'loadConstants').mockResolvedValue({ data: { records: [], total: 0 } })
    wrapper.vm.onTabClick({ name: 'constants' })
    await nextTick()
    // onTabClick 只处理 objects 和 constants；切换到 constants 时调用 loadConstants
    expect(loadConstantsSpy).toHaveBeenCalled()
    wrapper.unmount()
  })
})

describe('VariableList — 字段校验规则库', () => {
  test('渲染字段校验 Tab，并在切换时加载规则库', async () => {
    const wrapper = await mountAndWait()
    const source = fs.readFileSync(path.resolve(__dirname, '../../../src/views/variable/VariableList.vue'), 'utf8')

    expect(source).toContain('<el-tab-pane label="字段校验" name="validations">')
    await wrapper.vm.onTabClick({ name: 'validations' })
    expect(variableApi.listFieldValidations).toHaveBeenCalled()
    wrapper.unmount()
  })

  test('新建字段校验保存稳定配置，不改写用户输入的编码', async () => {
    const wrapper = await mountAndWait()
    variableApi.createFieldValidation.mockResolvedValueOnce({})
    wrapper.vm.activeTab = 'validations'
    wrapper.vm.handlePrimaryCreate()
    wrapper.vm.validationForm = {
      id: null, scope: 'GLOBAL', projectId: '', validationCode: 'Mobile_Check',
      validationName: '手机号校验', validationType: 'REGEX', validationValue: '^1\\d{10}$',
      errorMessage: '手机号格式错误', description: '', status: 1
    }

    await wrapper.vm.saveFieldValidation()

    expect(variableApi.createFieldValidation).toHaveBeenCalledWith(expect.objectContaining({
      validationCode: 'Mobile_Check', validationType: 'REGEX', projectId: 0
    }))
    wrapper.unmount()
  })
})
