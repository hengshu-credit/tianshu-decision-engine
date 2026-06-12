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

// 2. mock element-ui（Message / Notification / Loading / MessageBox）
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
  directive: (name, definition) => {}
}))

// 3. mock axios（支持 axios.create() 实例）
jest.mock('axios', () => {
  const mockInstance = {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
    request: jest.fn()
  }
  return {
    create: jest.fn(() => mockInstance),
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
    interceptors: {
      request: { use: jest.fn(), handlers: [] },
      response: { use: jest.fn(), handlers: [] }
    },
    __mockInstance: mockInstance
  }
})

// 4. mock @/api/request（所有 API 的基础模块）
//    提供 .get / .post / .put / .delete 方法，供组件直接调用 request.get(...) 时使用
//    测试文件通过 request.get.mockResolvedValueOnce(...) 配置返回值
jest.mock('@/api/request', () => ({
  default: jest.fn(),
  get: jest.fn(),
  post: jest.fn(),
  put: jest.fn(),
  delete: jest.fn(),
  __esModule: true
}))

// 5. mock 各 API 模块（基础 jest.fn()，无默认值，测试文件配置返回值）
jest.mock('@/api/definition', () => ({
  getDefinition: jest.fn(),
  getContent: jest.fn(),
  listDefinitions: jest.fn(),
  createDefinition: jest.fn(),
  updateDefinition: jest.fn(),
  deleteDefinition: jest.fn(),
  saveContent: jest.fn(),
  compileRule: jest.fn(),
  publishRule: jest.fn(),
  unpublishRule: jest.fn(),
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
  refreshFields: jest.fn(),
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
  __esModule: true
}))
jest.mock('@/api/dataObject', () => ({
  getVariableTree: jest.fn(),
  getDataObjectFieldOptions: jest.fn(),
  __esModule: true
}))
jest.mock('@/api/function', () => ({
  listAllFunctionsByProject: jest.fn(),
  listFunctions: jest.fn(),
  createFunction: jest.fn(),
  updateFunction: jest.fn(),
  deleteFunction: jest.fn(),
  __esModule: true
}))
jest.mock('@/api/model', () => ({
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

// 6. mock @/layout/index.vue（layout 组件导入了 router/index.js）
jest.mock('@/layout/index.vue', () => ({
  default: { name: 'Layout', template: '<div><slot /></div>' }
}))

// 7. mock SCSS 导入
jest.mock('@/styles/variables.scss', () => ({}))
jest.mock('@/styles/element-override.scss', () => ({}))

// 8. mock trace-tree 等组件（路径已与 moduleNameMapper 对齐）
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
    mounted() {},
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