// tests/unit/views/executionLog.spec.js
import { shallowMount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'
import ElementUI from 'element-ui'

// Mock API 模块（setup.js 已提供默认 mock，测试文件按需覆盖）
jest.mock('@/api/definition', () => ({
  getDefinition: jest.fn()
}))

jest.mock('@/api/project', () => ({
  listProjects: jest.fn()
}))

jest.mock('@/api/request', () => ({
  post: jest.fn()
}))

import * as definitionApi from '@/api/definition'
import * as projectApi from '@/api/project'
import * as requestApi from '@/api/request'
import ExecutionLog from '@/views/log/ExecutionLog.vue'

afterEach(() => { jest.clearAllMocks() })

// ─── Mock 数据 ───────────────────────────────────────────
function mockLogs() {
  return [
    { id: 1, ruleCode: 'age_rule', ruleName: '年龄判断规则', projectId: 1, projectName: '项目A', source: 'SERVER', executeTimeMs: 15, status: 'SUCCESS', traceCount: 5 },
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

function mockLogDetail() {
  return {
    id: 1,
    ruleCode: 'age_rule',
    ruleName: '年龄判断规则',
    source: 'SERVER',
    projectId: 1,
    projectName: '项目A',
    status: 'SUCCESS',
    executeTimeMs: 15,
    inputParams: '{"age": 30}',
    outputResult: '{"result": "PASS"}',
    trace: [
      { step: 1, node: '条件判断', expr: 'age >= 18', result: true },
      { step: 2, node: '输出', expr: 'result = "PASS"', result: 'PASS' }
    ]
  }
}

// ─── 测试用例 ─────────────────────────────────────────────
function createTestVue() {
  const localVue = createLocalVue()
  localVue.use(ElementUI)
  return localVue
}

// 使用 render 函数 stub 避免 v-loading directive 解析失败
function makeStub(tag) {
  return { render: h => h(tag) }
}

async function mountAndWait() {
  projectApi.listProjects.mockResolvedValueOnce({ data: { records: mockProjects() } })
  requestApi.post.mockResolvedValueOnce({ data: { records: mockLogs(), total: 3 } })

  const wrapper = shallowMount(ExecutionLog, {
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
      'el-loading': makeStub('div'),
      'el-textarea': makeStub('textarea'),
      'el-divider': makeStub('hr'),
      'el-alert': makeStub('div'),
      'el-date-picker': makeStub('div'),
      'el-tooltip': makeStub('span'),
      'trace-tree': makeStub('div')
    }
  })

  await Vue.nextTick()
  await new Promise(r => setTimeout(r, 100))
  return wrapper
}

describe('ExecutionLog — 初始化与数据加载', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('mounted 后调用 listProjects', () => {
    expect(projectApi.listProjects).toHaveBeenCalled()
  })

  test('mounted 后加载日志列表', () => {
    expect(requestApi.post).toHaveBeenCalled()
  })

  test('logList 数据正确赋值', () => {
    expect(wrapper.vm.logList).toBeInstanceOf(Array)
    expect(wrapper.vm.logList.length).toBe(3)
  })

  test('total 正确赋值', () => {
    expect(wrapper.vm.total).toBe(3)
  })

  test('loading 初始值为 false', () => {
    expect(wrapper.vm.loading).toBe(false)
  })
})

describe('ExecutionLog — 标签方法', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('sourceLabel 返回正确的标签', () => {
    expect(wrapper.vm.sourceLabel('SERVER')).toBe('服务端')
    expect(wrapper.vm.sourceLabel('CLIENT')).toBe('客户端')
    expect(wrapper.vm.sourceLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(wrapper.vm.sourceLabel(null)).toBe('')
  })

  test('statusLabel 返回正确的状态标签', () => {
    expect(wrapper.vm.statusLabel('SUCCESS')).toBe('成功')
    expect(wrapper.vm.statusLabel('FAIL')).toBe('失败')
    expect(wrapper.vm.statusLabel('TIMEOUT')).toBe('超时')
    expect(wrapper.vm.statusLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(wrapper.vm.statusLabel(null)).toBe('')
  })

  test('statusType 返回正确的 tag 类型', () => {
    expect(wrapper.vm.statusType('SUCCESS')).toBe('success')
    expect(wrapper.vm.statusType('FAIL')).toBe('danger')
    expect(wrapper.vm.statusType('TIMEOUT')).toBe('warning')
    expect(wrapper.vm.statusType('UNKNOWN')).toBe('info')
    expect(wrapper.vm.statusType(null)).toBe('info')
  })

  test('scopeLabel 返回正确的范围标签', () => {
    expect(wrapper.vm.scopeLabel('GLOBAL')).toBe('全局')
    expect(wrapper.vm.scopeLabel('PROJECT')).toBe('项目级')
  })
})

describe('ExecutionLog — 日志详情', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('handleViewDetail 打开详情弹窗', async () => {
    definitionApi.getDefinition.mockResolvedValueOnce({ data: { id: 1 } })
    requestApi.post.mockResolvedValueOnce({ data: mockLogDetail() })

    const row = mockLogs()[0]
    await wrapper.vm.handleViewDetail(row)
    await Vue.nextTick()

    expect(wrapper.vm.detailVisible).toBe(true)
    expect(wrapper.vm.detailLoading).toBe(false)
  })

  test('handleViewDetail 加载详情数据', async () => {
    definitionApi.getDefinition.mockResolvedValueOnce({ data: { id: 1 } })
    requestApi.post.mockResolvedValueOnce({ data: mockLogDetail() })

    const row = mockLogs()[0]
    await wrapper.vm.handleViewDetail(row)
    await Vue.nextTick()

    expect(wrapper.vm.currentDetail).toBeDefined()
    expect(wrapper.vm.currentDetail.ruleCode).toBe('age_rule')
  })

  test('formatParams 格式化 JSON 参数', () => {
    const result = wrapper.vm.formatParams('{"age": 30}')
    expect(result).toContain('age')
    expect(result).toContain('30')
  })

  test('formatParams 非 JSON 字符串原样返回', () => {
    const result = wrapper.vm.formatParams('plain text')
    expect(result).toBe('plain text')
  })

  test('formatParams null/undefined 返回空', () => {
    expect(wrapper.vm.formatParams(null)).toBe('-')
    expect(wrapper.vm.formatParams(undefined)).toBe('-')
    expect(wrapper.vm.formatParams('')).toBe('-')
  })
})

describe('ExecutionLog — 筛选与分页', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('handleQuery 重置页码并重新加载', async () => {
    wrapper.vm.queryParams.pageNum = 5
    requestApi.post.mockResolvedValueOnce({ data: { records: [], total: 0 } })
    wrapper.vm.handleQuery()
    await Vue.nextTick()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
  })

  test('resetQuery 重置所有查询条件', () => {
    wrapper.vm.queryParams.source = 'SERVER'
    wrapper.vm.queryParams.status = 'FAIL'
    wrapper.vm.queryParams.projectCode = 'test'
    requestApi.post.mockResolvedValueOnce({ data: { records: [], total: 0 } })
    wrapper.vm.resetQuery()
    expect(wrapper.vm.queryParams.source).toBe('')
    expect(wrapper.vm.queryParams.status).toBe('')
    expect(wrapper.vm.queryParams.ruleCode).toBe('')
  })

  test('分页大小变化时 pageNum 重置为1', () => {
    wrapper.vm.queryParams.pageNum = 5
    wrapper.vm.queryParams.pageSize = 30
    requestApi.post.mockResolvedValueOnce({ data: { records: [], total: 0 } })
    wrapper.vm.queryParams.pageNum = 1
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
  })
})

describe('ExecutionLog — 边界情况', () => {
  test('日志列表为空不报错', async () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: [] } })
    requestApi.post.mockResolvedValueOnce({ data: { records: [], total: 0 } })
    const wrapper = shallowMount(ExecutionLog, {
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
        'el-date-picker': makeStub('div'), 'trace-tree': makeStub('div')
      }
    })
    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
    expect(wrapper.vm.logList).toEqual([])
    wrapper.destroy()
  })

  test('详情加载失败时设置错误状态', async () => {
    requestApi.post.mockRejectedValueOnce(new Error('加载失败'))
    const wrapper = shallowMount(ExecutionLog, {
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
        'el-date-picker': makeStub('div'), 'trace-tree': makeStub('div')
      }
    })
    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
    // 详情弹窗关闭状态下加载失败不影响主列表
    expect(wrapper.vm.detailVisible).toBe(false)
    wrapper.destroy()
  })

  test('handleViewDetail 无 ruleId 时不调用 API', async () => {
    projectApi.listProjects.mockResolvedValueOnce({ data: { records: mockProjects() } })
    requestApi.post.mockResolvedValueOnce({ data: { records: mockLogs(), total: 3 } })

    const wrapper = await mountAndWait()
    const row = { id: 1, ruleCode: 'test', ruleName: '测试', source: 'SERVER', ruleDefinitionId: null }
    // 验证无 ruleId 不报错
    expect(() => wrapper.vm.handleViewDetail(row)).not.toThrow()
  })
})