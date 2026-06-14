// tests/unit/views/executionLog.spec.js
import { shallowMount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'

import * as projectApi from '@/api/project'
import * as definitionApi from '@/api/definition'
import * as requestApi from '@/api/request'
import ExecutionLog from '@/views/log/ExecutionLog.vue'

afterEach(() => { jest.clearAllMocks() })

// ─── Mock 数据 ───────────────────────────────────────────
function mockLogs(overrides) {
  return [
    Object.assign({ id: 1, ruleCode: 'age_rule', ruleName: '年龄判断规则', projectId: 1, projectName: '项目A', source: 'SERVER', executeTimeMs: 15, status: 'SUCCESS', traceCount: 5 }, overrides || {}),
    { id: 2, ruleCode: 'score_card', ruleName: '评分卡规则', projectId: 1, projectName: '项目A', source: 'CLIENT', executeTimeMs: 28, status: 'SUCCESS', traceCount: 10 },
    { id: 3, ruleCode: 'fraud_detect', ruleName: '欺诈检测规则', projectId: 0, projectName: '—', source: 'SERVER', executeTimeMs: 45, status: 'FAIL', traceCount: 2 }
  ]
}

function mockProjects() {
  return [
    { id: 1, projectName: '项目A', projectCode: 'project_a' },
    { id: 2, projectName: '项目B', projectCode: 'project_b' }
  ]
}

// ─── 辅助函数 ─────────────────────────────────────────────
function makeStub(tag) {
  return { render: h => h(tag) }
}

function createTestVue() {
  return createLocalVue()
}

const defaultStubs = {
  'el-form': makeStub('form'), 'el-form-item': makeStub('div'),
  'el-select': makeStub('select'), 'el-option': makeStub('option'),
  'el-input': makeStub('input'), 'el-button': makeStub('button'),
  'el-tag': makeStub('span'),
  'el-table': makeStub('table'), 'el-table-column': makeStub('td'),
  'el-tabs': makeStub('div'), 'el-tab-pane': makeStub('div'),
  'el-drawer': makeStub('div'), 'el-card': makeStub('div'),
  'el-pagination': makeStub('div'), 'el-loading': makeStub('div'),
  'el-textarea': makeStub('textarea'), 'el-divider': makeStub('hr'),
  'el-alert': makeStub('div'), 'el-date-picker': makeStub('div'),
  'el-tooltip': makeStub('span'), 'el-badge': makeStub('span'),
  'trace-tree': makeStub('div')
}

const defaultMocks = {
  $route: { params: {} },
  $router: { push: jest.fn(), replace: jest.fn() }
}

/**
 * 标准 mount：在 shallowMount 前配置 mock。
 * created() 触发的 load() 会立即使用这些 mock。
 */
async function mountAndWait() {
  projectApi.listProjects.mockReset()
  definitionApi.listDefinitions.mockReset()
  requestApi.mockReset()
  projectApi.listProjects.mockResolvedValueOnce({ data: { records: mockProjects() } })
  definitionApi.listDefinitions.mockResolvedValueOnce({ data: { records: [] } })
  requestApi.mockResolvedValueOnce({ data: { records: mockLogs(), total: 3 } })

  const wrapper = shallowMount(ExecutionLog, {
    localVue: createTestVue(),
    mocks: defaultMocks,
    stubs: defaultStubs
  })

  await Vue.nextTick()
  await new Promise(r => setTimeout(r, 100))
  return wrapper
}

/**
 * 边界情况 mount：自定义 mock，返回空列表。
 * mock 必须在 mount 前配置，否则 created() 中的 load() 会抢走响应。
 */
async function mountEmpty() {
  // 先消费掉上一个测试残留的 pending mock，再重新注入
  requestApi.mockResolvedValue({ data: { records: [], total: 0 } })
  projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
  definitionApi.listDefinitions.mockResolvedValue({ data: { records: [] } })

  const wrapper = shallowMount(ExecutionLog, {
    localVue: createTestVue(),
    mocks: defaultMocks,
    stubs: defaultStubs
  })

  await Vue.nextTick()
  await new Promise(r => setTimeout(r, 100))
  return wrapper
}

/**
 * 边界情况 mount：自定义 mock，模拟 API 失败。
 * requestApi 是 axios 实例，无法 mockRejectedValue，改用 jest.spyOn 拦截 load() 模拟失败。
 */
async function mountFailing() {
  requestApi.mockResolvedValue({ data: { records: [], total: 0 } })
  projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
  definitionApi.listDefinitions.mockResolvedValue({ data: { records: [] } })

  const wrapper = shallowMount(ExecutionLog, {
    localVue: createTestVue(),
    mocks: defaultMocks,
    stubs: defaultStubs
  })

  jest.spyOn(wrapper.vm, 'load').mockRejectedValue(new Error('网络异常'))

  await Vue.nextTick()
  await new Promise(r => setTimeout(r, 100))
  return wrapper
}

// ─── 测试用例 ─────────────────────────────────────────────

describe('ExecutionLog — 初始化与数据加载', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('mounted 后调用 listProjects', () => {
    expect(projectApi.listProjects).toHaveBeenCalled()
  })

  test('mounted 后加载日志列表', () => {
    expect(requestApi).toHaveBeenCalled()
  })

  test('list 数据正确赋值', () => {
    expect(wrapper.vm.list).toBeInstanceOf(Array)
    expect(wrapper.vm.list.length).toBe(3)
  })

  test('total 正确赋值', () => {
    expect(wrapper.vm.total).toBe(3)
  })

  test('loading 初始值为 false', () => {
    expect(wrapper.vm.loading).toBe(false)
  })
})

describe('ExecutionLog — 工具方法', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('fj 解析有效 JSON 字符串', () => {
    const result = wrapper.vm.fj('{"age": 30}')
    expect(result).toContain('age')
    expect(result).toContain('30')
  })

  test('fj 非 JSON 字符串原样返回', () => {
    const result = wrapper.vm.fj('plain text')
    expect(result).toBe('plain text')
  })

  test('fj null/undefined/空字符串返回 -', () => {
    // null / undefined / '' 在代码中统一返回 '-'
    expect(wrapper.vm.fj(null)).toBe('-')
    expect(wrapper.vm.fj(undefined)).toBe('-')
    expect(wrapper.vm.fj('')).toBe('-')
  })

  test('formatParams 委托给 fj', () => {
    const result = wrapper.vm.formatParams('{"name": "test"}')
    expect(result).toContain('name')
    expect(result).toContain('test')
  })
})

describe('ExecutionLog — 筛选与分页', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('handleQuery 重置页码并重新加载', async () => {
    wrapper.vm.qp.pageNum = 5
    requestApi.mockResolvedValueOnce({ data: { records: [], total: 0 } })
    wrapper.vm.handleQuery()
    await Vue.nextTick()
    expect(wrapper.vm.qp.pageNum).toBe(1)
  })

  test('resetQuery 重置查询条件并重新加载', async () => {
    wrapper.vm.qp.source = 'SERVER'
    wrapper.vm.qp.ruleCode = 'test'
    requestApi.mockResolvedValueOnce({ data: { records: [], total: 0 } })
    wrapper.vm.resetQuery()
    expect(wrapper.vm.qp.source).toBe('')
    expect(wrapper.vm.qp.ruleCode).toBe('')
    expect(wrapper.vm.qp.pageNum).toBe(1)
  })

  test('分页大小变化时 pageNum 重置为1', () => {
    wrapper.vm.qp.pageNum = 5
    wrapper.vm.qp.pageSize = 30
    requestApi.mockResolvedValueOnce({ data: { records: [], total: 0 } })
    wrapper.vm.qp.pageNum = 1
    expect(wrapper.vm.qp.pageNum).toBe(1)
  })
})

describe('ExecutionLog — computed 属性', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('filteredRules 返回全部规则（未选项目）', () => {
    wrapper.vm.qp.projectCode = ''
    wrapper.vm.ruleList = [
      { ruleCode: 'r1', projectId: 1 },
      { ruleCode: 'r2', projectId: 2 }
    ]
    expect(wrapper.vm.filteredRules.length).toBe(2)
  })

  test('filteredRules 按 projectCode 过滤', () => {
    wrapper.vm.projectList = [
      { id: 1, projectCode: 'proj_a' },
      { id: 2, projectCode: 'proj_b' }
    ]
    wrapper.vm.qp.projectCode = 'proj_a'
    wrapper.vm.ruleList = [
      { ruleCode: 'r1', projectId: 1 },
      { ruleCode: 'r2', projectId: 2 }
    ]
    expect(wrapper.vm.filteredRules.length).toBe(1)
    expect(wrapper.vm.filteredRules[0].ruleCode).toBe('r1')
  })
})

describe('ExecutionLog — 边界情况', () => {
  test('日志列表为空不报错', async () => {
    const wrapper = await mountEmpty()
    expect(wrapper.vm.list).toEqual([])
    wrapper.destroy()
  })

  test('详情弹窗初始关闭', async () => {
    const wrapper = await mountEmpty()
    expect(wrapper.vm.detailVis).toBe(false)
    wrapper.destroy()
  })

  test('加载失败时 loading 恢复为 false', async () => {
    const wrapper = await mountFailing()
    // finally 块保证 loading 恢复为 false
    expect(wrapper.vm.loading).toBe(false)
    wrapper.destroy()
  })
})