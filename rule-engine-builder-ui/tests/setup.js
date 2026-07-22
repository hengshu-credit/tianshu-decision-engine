// tests/setup.js
// 在模块加载前注入依赖 mock（setupFiles 在测试文件之前执行）
// 注意：vi.mock() 在 Jest 中会被提升（hoisting）且按模块路径去重，
//       setup.js 的 mock 会覆盖测试文件中的同名 vi.mock()。
//       因此 setup.js 只提供基础 mock（vi.fn()），测试文件通过
//       .mockResolvedValueOnce() / .mockResolvedValue() 配置返回值。

// 1. mock Vue Router
vi.mock('@/router', () => ({
  default: {
    push: vi.fn(),
    replace: vi.fn(),
    go: vi.fn(),
    back: vi.fn(),
    currentRoute: { path: '/', fullPath: '/' }
  }
}))

// 2. mock Element Plus 的全局服务；组件在测试中使用 VTU stub。
vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn()
  }),
  ElNotification: Object.assign(vi.fn(), {
    success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn()
  }),
  ElMessageBox: {
    confirm: vi.fn().mockResolvedValue('confirm'),
    alert: vi.fn().mockResolvedValue('alert')
  },
  ElLoading: {
    service: vi.fn(() => ({ close: vi.fn() }))
  },
  default: { install: vi.fn() }
}))

// 3. mock axios（支持 axios.create() 实例）
vi.mock('axios', () => {
  const mockRequest = vi.fn(() => {
    return Promise.resolve({ data: { code: 200, data: [] } })
  })
  const mockInstance = {
    get: mockRequest,
    post: mockRequest,
    put: mockRequest,
    delete: mockRequest,
    request: mockRequest
  }
  const mockAxios = {
    create: vi.fn(() => mockInstance),
    get: mockRequest,
    post: mockRequest,
    put: mockRequest,
    delete: mockRequest,
    interceptors: {
      request: { use: vi.fn(), handlers: [] },
      response: { use: vi.fn(), handlers: [] }
    },
    __mockInstance: mockInstance,
    __mockRequest: mockRequest
  }
  return { ...mockAxios, default: mockAxios }
})

// 4. mock @/api/request
// request.js 实际导出的是一个 axios 实例（可调用函数 + get/post/put/delete 方法）。
// mock 需要模拟两种用法：request(config) 和 request.get(url, config) 等。
const mockRequestFn = vi.fn(() => {
  return Promise.resolve({ data: { code: 200, data: [] } })
})
mockRequestFn.get = mockRequestFn
mockRequestFn.post = mockRequestFn
mockRequestFn.put = mockRequestFn
mockRequestFn.delete = mockRequestFn
mockRequestFn.interceptors = {
  request: { use: vi.fn(), handlers: [] },
  response: { use: vi.fn(), handlers: [] }
}
vi.mock('@/api/request', () => ({ default: mockRequestFn }))

// 5. mock 各 API 模块
vi.mock('@/api/definition', () => ({
  getDefinition: vi.fn(),
  getContent: vi.fn(),
  listDefinitions: vi.fn(),
  listProjectDefinitions: vi.fn(),
  createDefinition: vi.fn(),
  updateDefinition: vi.fn(),
  deleteDefinition: vi.fn(),
  saveContent: vi.fn(),
  refreshFields: vi.fn(),
  getDetail: vi.fn(),
  inputFields: vi.fn(),
  outputFields: vi.fn(),
  publish: vi.fn(),
  unpublish: vi.fn(),
  copyRule: vi.fn(),
  compileRule: vi.fn(),
  validateCallCycle: vi.fn(),
  publishRule: vi.fn(),
  unpublishRule: vi.fn(),
  listVersions: vi.fn(),
  getVersion: vi.fn(),
  compareVersions: vi.fn(),
  rollbackVersion: vi.fn(),
  executeRule: vi.fn(),
  getRuleTestSchema: vi.fn(),
  saveScript: vi.fn(),
  updateScriptMode: vi.fn(),
  validateScript: vi.fn(),
  getDefinitionDetail: vi.fn(),
  listInputFields: vi.fn(),
  listOutputFields: vi.fn(),
  updateInputField: vi.fn(),
  updateOutputField: vi.fn(),
  listApiScenarios: vi.fn(),
  createApiScenario: vi.fn(),
  updateApiScenario: vi.fn(),
  deleteApiScenario: vi.fn(),
  copyApiScenario: vi.fn(),
  sortApiScenarios: vi.fn(),
  executeApiScenario: vi.fn(),
  DEFAULT_RULE_REQUEST_TIMEOUT_MS: 180000,
  migrateFields: vi.fn(),
  ensureDraftRevision: vi.fn(),
  listRuleRevisions: vi.fn(),
  getRuleRevision: vi.fn(),
  getCurrentDraftRevision: vi.fn(),
  preflightRuleRevision: vi.fn(),
  submitRuleRevision: vi.fn(),
  returnRuleRevision: vi.fn(),
  approveRuleRevision: vi.fn(),
  publishRuleRevision: vi.fn(),
  offlineRuleRevision: vi.fn(),
  getRuleLifecycleTimeline: vi.fn(),
  __esModule: true
}))
vi.mock('@/api/expression', () => ({
  compileExpression: vi.fn(),
  getExpressionTestSchema: vi.fn(),
  executeExpression: vi.fn(),
  __esModule: true
}))
vi.mock('@/api/variable', () => ({
  listVariablesByProject: vi.fn(),
  getVariableOptions: vi.fn(),
  listVariables: vi.fn(),
  createVariable: vi.fn(),
  updateVariable: vi.fn(),
  deleteVariable: vi.fn(),
  testVariable: vi.fn(),
  batchValidateVariables: vi.fn(),
  importJavaConstants: vi.fn(),
  importJsonConstants: vi.fn(),
  listFieldValidations: vi.fn(),
  listAvailableFieldValidations: vi.fn(),
  createFieldValidation: vi.fn(),
  updateFieldValidation: vi.fn(),
  deleteFieldValidation: vi.fn(),
  __esModule: true
}))
vi.mock('@/api/dataObject', () => ({
  listDataObjects: vi.fn(),
  getVariableTree: vi.fn(),
  getDataObjectFieldOptions: vi.fn(),
  __esModule: true
}))
vi.mock('@/api/function', () => ({
  listAllFunctionsByProject: vi.fn(),
  listFunctionsByProject: vi.fn(),
  listFunctions: vi.fn(),
  getFunctionById: vi.fn(),
  createFunction: vi.fn(),
  updateFunction: vi.fn(),
  deleteFunction: vi.fn(),
  testFunction: vi.fn(),
  listVersions: vi.fn(),
  getVersion: vi.fn(),
  compareVersions: vi.fn(),
  rollbackVersion: vi.fn(),
  __esModule: true
}))
vi.mock('@/api/model', () => ({
  listAllModelsByProject: vi.fn(),
  listModelInputs: vi.fn(),
  listModelOutputs: vi.fn(),
  listModels: vi.fn(),
  getModel: vi.fn(),
  createModel: vi.fn(),
  updateModel: vi.fn(),
  deleteModel: vi.fn(),
  getTestParams: vi.fn(),
  saveTestParams: vi.fn(),
  executeModel: vi.fn(),
  updateModelInputField: vi.fn(),
  updateModelOutputField: vi.fn(),
  analyzeModelImpact: vi.fn(),
  replaceModel: vi.fn(),
  unpublishModel: vi.fn(),
  __esModule: true
}))
vi.mock('@/api/artifact', () => ({
  downloadArtifact: vi.fn(),
  importArtifact: vi.fn(),
  deployArtifact: vi.fn(),
  __esModule: true
}))
vi.mock('@/api/project', () => ({
  listProjects: vi.fn(),
  getProject: vi.fn(),
  createProject: vi.fn(),
  updateProject: vi.fn(),
  deleteProject: vi.fn(),
  getMaskedToken: vi.fn(),
  getFullToken: vi.fn(),
  regenerateToken: vi.fn(),
  exportApiDoc: vi.fn(),
  listProjectAuths: vi.fn(),
  createProjectAuth: vi.fn(),
  updateProjectAuth: vi.fn(),
  updateProjectAuthStatus: vi.fn(),
  getProjectAuthFull: vi.fn(),
  regenerateProjectAuthSecret: vi.fn(),
  listProjectAuthTokens: vi.fn(),
  getProjectAuthTokenFull: vi.fn(),
  revokeProjectAuthToken: vi.fn(),
  listProjectAuthAccessLogs: vi.fn(),
  __esModule: true
}))
vi.mock('@/api/datasource', () => ({
  listDatasources: vi.fn(),
  getDatasource: vi.fn(),
  createDatasource: vi.fn(),
  updateDatasource: vi.fn(),
  deleteDatasource: vi.fn(),
  listApiConfigs: vi.fn(),
  createApiConfig: vi.fn(),
  updateApiConfig: vi.fn(),
  deleteApiConfig: vi.fn(),
  invokeApiConfig: vi.fn(),
  invokeApiConfigPreview: vi.fn(),
  previewApiConfigRequest: vi.fn(),
  testDatasourceAuth: vi.fn(),
  __esModule: true
}))
vi.mock('@/api/runtimeLog', () => ({
  listRuntimeLogs: vi.fn(),
  getExternalApiStats: vi.fn(),
  getRuleSetStats: vi.fn(),
  __esModule: true
}))
vi.mock('@/api/lineage', () => ({
  listLineageOptions: vi.fn(),
  getLineageGraph: vi.fn(),
  __esModule: true
}))
vi.mock('@/api/database', () => ({
  listDbDatasources: vi.fn(),
  getDbDatasource: vi.fn(),
  createDbDatasource: vi.fn(),
  updateDbDatasource: vi.fn(),
  deleteDbDatasource: vi.fn(),
  testDbDatasource: vi.fn(),
  testDbDatasourceDraft: vi.fn(),
  queryDbDatasource: vi.fn(),
  __esModule: true
}))
vi.mock('@/api/billing', () => ({
  listBillingConfigs: vi.fn(),
  createBillingConfig: vi.fn(),
  updateBillingConfig: vi.fn(),
  deleteBillingConfig: vi.fn(),
  listBillingRecords: vi.fn(),
  listBillingSummaries: vi.fn(),
  refreshBillingSummary: vi.fn(),
  __esModule: true
}))
vi.mock('@/api/experiment', () => ({
  listExperiments: vi.fn(),
  getExperiment: vi.fn(),
  listExperimentLogs: vi.fn(),
  saveExperiment: vi.fn(),
  deleteExperiment: vi.fn(),
  executeExperiment: vi.fn(),
  listVersions: vi.fn(),
  getVersion: vi.fn(),
  compareVersions: vi.fn(),
  rollbackVersion: vi.fn(),
  __esModule: true
}))
vi.mock('@/api/ruleList', () => ({
  listLibraries: vi.fn(),
  getLibrary: vi.fn(),
  createLibrary: vi.fn(),
  updateLibrary: vi.fn(),
  deleteLibrary: vi.fn(),
  listRecords: vi.fn(),
  createRecord: vi.fn(),
  updateRecord: vi.fn(),
  deleteRecord: vi.fn(),
  listRecordLogs: vi.fn(),
  importRecords: vi.fn(),
  listTemplateUrl: '/api/rule/list/template',
  listExportUrl: vi.fn(id => `/api/rule/list/${id}/export`),
  __esModule: true
}))

// 6. mock @/layout/index.vue
vi.mock('@/layout/index.vue', () => ({
  default: { name: 'Layout', template: '<div><slot /></div>' }
}))

// 7. mock SCSS 导入
vi.mock('@/styles/variables.scss', () => ({}))
vi.mock('@/styles/element-override.scss', () => ({}))

// 8. mock trace-tree 等组件
vi.mock('@/components/common/TraceTree.vue', () => ({
  default: { name: 'TraceTree', template: '<div />' }
}))
vi.mock('@/components/flow/ActionBlockEditor.vue', () => ({
  default: { name: 'ActionBlockEditor', template: '<div />' }
}))
vi.mock('@/components/common/VarPicker.vue', () => ({
  default: { name: 'VarPicker', template: '<div />', props: ['vars', 'value'] }
}))
vi.mock('@/components/common/ScriptPanel.vue', () => ({
  default: { name: 'ScriptPanel', template: '<div />' }
}))

// 注册 Vue Test Utils 2 的全局指令与实例属性。
const { config } = require('@vue/test-utils')
const loadingDirectiveStub = {
  beforeMount: vi.fn(),
  mounted: vi.fn(),
  updated: vi.fn(),
  beforeUnmount: vi.fn(),
  unmounted: vi.fn()
}
config.global.directives.loading = loadingDirectiveStub
config.global.components.AppIcon = {
  name: 'AppIcon',
  props: ['name'],
  template: '<i class="app-icon-stub" />'
}
config.global.renderStubDefaultSlot = true
const globalStubNames = [
  'el-alert', 'el-badge', 'el-button', 'el-button-group', 'el-card',
  'el-checkbox', 'el-checkbox-group', 'el-col', 'el-collapse',
  'el-collapse-item', 'el-date-picker', 'el-descriptions',
  'el-descriptions-item', 'el-dialog', 'el-divider', 'el-drawer',
  'el-dropdown', 'el-dropdown-item', 'el-dropdown-menu', 'el-empty',
  'el-form', 'el-form-item', 'el-icon', 'el-input', 'el-input-number',
  'el-link', 'el-option', 'el-option-group', 'el-pagination', 'el-popover',
  'el-progress', 'el-radio', 'el-radio-button', 'el-radio-group', 'el-row',
  'el-select', 'el-slider', 'el-switch', 'el-table', 'el-table-column',
  'el-tab-pane', 'el-tabs', 'el-tag', 'el-tooltip', 'el-upload'
]
config.global.stubs = globalStubNames.reduce((stubs, name) => {
  stubs[name] = true
  return stubs
}, {})
config.global.stubs['el-table-column'] = {
  name: 'ElTableColumn',
  props: ['prop', 'label'],
  template: '<div><slot name="header" :column="{}" /><slot :row="{}" :$index="0" /></div>'
}
config.global.mocks = {
  $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  $confirm: vi.fn().mockResolvedValue(true),
  $notify: vi.fn(),
  $loading: vi.fn(() => ({ close: vi.fn() })),
  $t: key => key
}

// 抑制日常 log，error/warn 保留以便调试
global.console = {
  ...console,
  log: vi.fn(),
  info: vi.fn(),
  debug: vi.fn(),
  warn: vi.fn()
}

// 全局 Vue mock 工厂（供测试文件使用）
function createVueMock(mixins = []) {
  return {
    data() { return {} },
    computed: {},
    watch: {},
    methods: {},
    created() {},
    mounted: {},
    $watch: vi.fn(),
    $set: vi.fn((obj, key, val) => { obj[key] = val }),
    $delete: vi.fn(),
    $refs: {},
    $options: { name: 'TestComponent' },
    $route: { path: '/', params: { id: 1 }, query: {}, name: 'test' },
    $router: { push: vi.fn(), replace: vi.fn(), go: vi.fn(), back: vi.fn() },
    $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    $confirm: vi.fn().mockResolvedValue(true),
    $notify: vi.fn(),
    $loading: vi.fn(() => ({ close: vi.fn() })),
    $axios: vi.fn(),
    $t: (key) => key,
    mixins
  }
}

global.createVueMock = createVueMock
