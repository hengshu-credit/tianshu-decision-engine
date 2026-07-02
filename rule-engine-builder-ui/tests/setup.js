// tests/setup.js
// 在模块加载前注入依赖 mock（setupFiles 在测试文件之前执行）
// 注意：jest.mock() 在 Jest 中会被提升（hoisting）且按模块路径去重，
//       setup.js 的 mock 会覆盖测试文件中的同名 jest.mock()。
//       因此 setup.js 只提供基础 mock（jest.fn()），测试文件通过
//       .mockResolvedValueOnce() / .mockResolvedValue() 配置返回值。

// 1. mock Vue Router
jest.mock('@/router', () => ({
  default: {
    push: jest.fn(),
    replace: jest.fn(),
    go: jest.fn(),
    back: jest.fn(),
    currentRoute: { path: '/', fullPath: '/' }
  }
}))

// 2. mock element-ui（Message / Notification / Loading / MessageBox / v-loading directive）
jest.mock('element-ui', () => ({
  Message: {
    success: jest.fn(), error: jest.fn(), warning: jest.fn(), info: jest.fn()
  },
  Notification: {
    success: jest.fn(), error: jest.fn(), warning: jest.fn(), info: jest.fn()
  },
  MessageBox: {
    confirm: jest.fn().mockResolvedValue('confirm'),
    alert: jest.fn().mockResolvedValue('alert')
  },
  Loading: {
    service: jest.fn(() => ({ close: jest.fn() }))
  },
  directive: (name, definition) => {
    if (name === 'loading') {
      return { bind: jest.fn(), update: jest.fn(), unbind: jest.fn() }
    }
  }
}))

// 3. mock axios（支持 axios.create() 实例）
jest.mock('axios', () => {
  const mockRequest = jest.fn((config) => {
    return Promise.resolve({ data: { code: 200, data: [] } })
  })
  const mockInstance = {
    get: mockRequest,
    post: mockRequest,
    put: mockRequest,
    delete: mockRequest,
    request: mockRequest
  }
  return {
    create: jest.fn(() => mockInstance),
    get: mockRequest,
    post: mockRequest,
    put: mockRequest,
    delete: mockRequest,
    interceptors: {
      request: { use: jest.fn(), handlers: [] },
      response: { use: jest.fn(), handlers: [] }
    },
    __mockInstance: mockInstance,
    __mockRequest: mockRequest
  }
})

// 4. mock @/api/request
// request.js 实际导出的是一个 axios 实例（可调用函数 + get/post/put/delete 方法）。
// mock 需要模拟两种用法：request(config) 和 request.get(url, config) 等。
const mockRequestFn = jest.fn(config => {
  return Promise.resolve({ data: { code: 200, data: [] } })
})
mockRequestFn.get = mockRequestFn
mockRequestFn.post = mockRequestFn
mockRequestFn.put = mockRequestFn
mockRequestFn.delete = mockRequestFn
mockRequestFn.interceptors = {
  request: { use: jest.fn(), handlers: [] },
  response: { use: jest.fn(), handlers: [] }
}
jest.mock('@/api/request', () => mockRequestFn)

// 5. mock 各 API 模块
jest.mock('@/api/definition', () => ({
  getDefinition: jest.fn(),
  getContent: jest.fn(),
  listDefinitions: jest.fn(),
  listProjectDefinitions: jest.fn(),
  createDefinition: jest.fn(),
  updateDefinition: jest.fn(),
  deleteDefinition: jest.fn(),
  saveContent: jest.fn(),
  refreshFields: jest.fn(),
  getDetail: jest.fn(),
  inputFields: jest.fn(),
  outputFields: jest.fn(),
  publish: jest.fn(),
  unpublish: jest.fn(),
  copyRule: jest.fn(),
  compileRule: jest.fn(),
  validateCallCycle: jest.fn(),
  publishRule: jest.fn(),
  unpublishRule: jest.fn(),
  listVersions: jest.fn(),
  getVersion: jest.fn(),
  compareVersions: jest.fn(),
  rollbackVersion: jest.fn(),
  executeRule: jest.fn(),
  saveScript: jest.fn(),
  updateScriptMode: jest.fn(),
  validateScript: jest.fn(),
  getDefinitionDetail: jest.fn(),
  listInputFields: jest.fn(),
  listOutputFields: jest.fn(),
  updateInputField: jest.fn(),
  updateOutputField: jest.fn(),
  migrateFields: jest.fn(),
  __esModule: true
}))
jest.mock('@/api/variable', () => ({
  listVariablesByProject: jest.fn(),
  getVariableOptions: jest.fn(),
  listVariables: jest.fn(),
  createVariable: jest.fn(),
  updateVariable: jest.fn(),
  deleteVariable: jest.fn(),
  batchValidateVariables: jest.fn(),
  importJavaConstants: jest.fn(),
  importJsonConstants: jest.fn(),
  __esModule: true
}))
jest.mock('@/api/dataObject', () => ({
  listDataObjects: jest.fn(),
  getVariableTree: jest.fn(),
  getDataObjectFieldOptions: jest.fn(),
  __esModule: true
}))
jest.mock('@/api/function', () => ({
  listAllFunctionsByProject: jest.fn(),
  listFunctionsByProject: jest.fn(),
  listFunctions: jest.fn(),
  getFunctionById: jest.fn(),
  createFunction: jest.fn(),
  updateFunction: jest.fn(),
  deleteFunction: jest.fn(),
  listVersions: jest.fn(),
  getVersion: jest.fn(),
  compareVersions: jest.fn(),
  rollbackVersion: jest.fn(),
  __esModule: true
}))
jest.mock('@/api/model', () => ({
  listAllModelsByProject: jest.fn(),
  listModelInputs: jest.fn(),
  listModelOutputs: jest.fn(),
  listModels: jest.fn(),
  getModel: jest.fn(),
  createModel: jest.fn(),
  updateModel: jest.fn(),
  deleteModel: jest.fn(),
  getTestParams: jest.fn(),
  saveTestParams: jest.fn(),
  executeModel: jest.fn(),
  updateModelInputField: jest.fn(),
  updateModelOutputField: jest.fn(),
  __esModule: true
}))
jest.mock('@/api/project', () => ({
  listProjects: jest.fn(),
  getProject: jest.fn(),
  createProject: jest.fn(),
  updateProject: jest.fn(),
  deleteProject: jest.fn(),
  getMaskedToken: jest.fn(),
  getFullToken: jest.fn(),
  regenerateToken: jest.fn(),
  exportApiDoc: jest.fn(),
  __esModule: true
}))
jest.mock('@/api/datasource', () => ({
  listDatasources: jest.fn(),
  getDatasource: jest.fn(),
  createDatasource: jest.fn(),
  updateDatasource: jest.fn(),
  deleteDatasource: jest.fn(),
  listApiConfigs: jest.fn(),
  createApiConfig: jest.fn(),
  updateApiConfig: jest.fn(),
  deleteApiConfig: jest.fn(),
  invokeApiConfig: jest.fn(),
  testDatasourceAuth: jest.fn(),
  __esModule: true
}))
jest.mock('@/api/runtimeLog', () => ({
  listRuntimeLogs: jest.fn(),
  __esModule: true
}))
jest.mock('@/api/database', () => ({
  listDbDatasources: jest.fn(),
  createDbDatasource: jest.fn(),
  updateDbDatasource: jest.fn(),
  deleteDbDatasource: jest.fn(),
  testDbDatasource: jest.fn(),
  testDbDatasourceDraft: jest.fn(),
  queryDbDatasource: jest.fn(),
  __esModule: true
}))
jest.mock('@/api/billing', () => ({
  listBillingConfigs: jest.fn(),
  createBillingConfig: jest.fn(),
  updateBillingConfig: jest.fn(),
  deleteBillingConfig: jest.fn(),
  listBillingRecords: jest.fn(),
  listBillingSummaries: jest.fn(),
  refreshBillingSummary: jest.fn(),
  __esModule: true
}))
jest.mock('@/api/experiment', () => ({
  listExperiments: jest.fn(),
  getExperiment: jest.fn(),
  listExperimentLogs: jest.fn(),
  saveExperiment: jest.fn(),
  deleteExperiment: jest.fn(),
  executeExperiment: jest.fn(),
  listVersions: jest.fn(),
  getVersion: jest.fn(),
  compareVersions: jest.fn(),
  rollbackVersion: jest.fn(),
  __esModule: true
}))
jest.mock('@/api/ruleList', () => ({
  listLibraries: jest.fn(),
  getLibrary: jest.fn(),
  createLibrary: jest.fn(),
  updateLibrary: jest.fn(),
  deleteLibrary: jest.fn(),
  listRecords: jest.fn(),
  createRecord: jest.fn(),
  updateRecord: jest.fn(),
  deleteRecord: jest.fn(),
  listRecordLogs: jest.fn(),
  importRecords: jest.fn(),
  listTemplateUrl: '/api/rule/list/template',
  listExportUrl: jest.fn(id => `/api/rule/list/${id}/export`),
  __esModule: true
}))

// 6. mock @/layout/index.vue
jest.mock('@/layout/index.vue', () => ({
  default: { name: 'Layout', template: '<div><slot /></div>' }
}))

// 7. mock SCSS 导入
jest.mock('@/styles/variables.scss', () => ({}))
jest.mock('@/styles/element-override.scss', () => ({}))

// 8. mock trace-tree 等组件
jest.mock('@/components/common/TraceTree.vue', () => ({ name: 'TraceTree', template: '<div />' }))
jest.mock('@/components/flow/ActionBlockEditor.vue', () => ({ name: 'ActionBlockEditor', template: '<div />' }))
jest.mock('@/components/common/VarPicker.vue', () => ({ name: 'VarPicker', template: '<div />', props: ['vars', 'value'] }))
jest.mock('@/components/common/ScriptPanel.vue', () => ({ name: 'ScriptPanel', template: '<div />' }))

// 抑制日常 log，error/warn 保留以便调试
global.console = {
  ...console,
  log: jest.fn(),
  info: jest.fn(),
  debug: jest.fn(),
  warn: jest.fn()
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
    $watch: jest.fn(),
    $set: jest.fn((obj, key, val) => { obj[key] = val }),
    $delete: jest.fn(),
    $refs: {},
    $options: { name: 'TestComponent' },
    $route: { path: '/', params: { id: 1 }, query: {}, name: 'test' },
    $router: { push: jest.fn(), replace: jest.fn(), go: jest.fn(), back: jest.fn() },
    $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn(), info: jest.fn() },
    $confirm: jest.fn().mockResolvedValue(true),
    $notify: jest.fn(),
    $loading: jest.fn(() => ({ close: jest.fn() })),
    $axios: jest.fn(),
    $t: (key) => key,
    mixins
  }
}

global.createVueMock = createVueMock
