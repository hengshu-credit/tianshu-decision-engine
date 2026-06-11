// tests/setup.js
// 在模块加载前注入依赖 mock（setupFiles 在测试文件之前执行）

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
  Loading: { service: jest.fn(() => ({ close: jest.fn() })) }
}))

// 3. mock @/layout/index.vue（layout 组件导入了 router/index.js）
jest.mock('@/layout/index.vue', () => ({
  default: { name: 'Layout', template: '<div><slot /></div>' }
}))

// 4. mock SCSS 导入
jest.mock('@/styles/variables.scss', () => ({}))
jest.mock('@/styles/element-override.scss', () => ({}))

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