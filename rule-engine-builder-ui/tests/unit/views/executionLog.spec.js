// tests/unit/views/executionLog.spec.js
import { shallowMount } from '@test-utils'
import { h, nextTick } from 'vue'

import * as projectApi from '@/api/project'
import * as definitionApi from '@/api/definition'
import requestApi from '@/api/request'
import * as runtimeLogApi from '@/api/runtimeLog'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import ExecutionLog from '@/views/log/ExecutionLog.vue'

afterEach(() => { vi.clearAllMocks() })

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
  return { render: () => h(tag) }
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
  'el-empty': makeStub('div'),
  'trace-tree': makeStub('div')
}

const defaultMocks = {
  $route: { params: {} },
  $router: { push: vi.fn(), replace: vi.fn() }
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
    mocks: defaultMocks,
    stubs: defaultStubs
  })

  await nextTick()
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
    mocks: defaultMocks,
    stubs: defaultStubs
  })

  await nextTick()
  await new Promise(r => setTimeout(r, 100))
  return wrapper
}

/**
 * 边界情况 mount：自定义 mock，模拟 API 失败。
 * requestApi 是 axios 实例，无法 mockRejectedValue，改用 vi.spyOn 拦截 load() 模拟失败。
 */
async function mountFailing() {
  requestApi.mockResolvedValue({ data: { records: [], total: 0 } })
  projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
  definitionApi.listDefinitions.mockResolvedValue({ data: { records: [] } })

  const wrapper = shallowMount(ExecutionLog, {
    mocks: defaultMocks,
    stubs: defaultStubs
  })

  vi.spyOn(wrapper.vm, 'load').mockRejectedValue(new Error('网络异常'))

  await nextTick()
  await new Promise(r => setTimeout(r, 100))
  return wrapper
}

test('uses project code and name fuzzy filters', async () => {
  const wrapper = await mountEmpty()

  expect(ExecutionLog.components.ProjectFilterSelect).toBe(ProjectFilterSelect)
  expect(wrapper.vm.qp).toEqual(expect.objectContaining({ projectCode: '', projectName: '' }))

  wrapper.unmount()
})

// ─── 测试用例 ─────────────────────────────────────────────

describe('ExecutionLog — 初始化与数据加载', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('mounted 时不再预拉取 1000 条项目和规则元数据', () => {
    expect(projectApi.listProjects).not.toHaveBeenCalled()
    expect(definitionApi.listDefinitions).not.toHaveBeenCalled()
  })

  test('mounted 后加载日志列表', () => {
    expect(requestApi).toHaveBeenCalled()
  })

  test('初始日志请求严格使用当前页码和分页大小', () => {
    const call = requestApi.mock.calls.find(args => args[0] && args[0].url === '/rule/log/list')
    expect(call[0].params).toMatchObject({ pageNum: 1, pageSize: 10 })
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

  test('鉴权归因筛选初始化为空', () => {
    expect(wrapper.vm.qp.authType).toBe('')
    expect(wrapper.vm.qp.authCode).toBe('')
    expect(wrapper.vm.qp.tokenCode).toBe('')
  })
})

describe('ExecutionLog — 工具方法', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.unmount() })

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
  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('handleQuery 重置页码并重新加载', async () => {
    wrapper.vm.qp.pageNum = 5
    requestApi.mockResolvedValueOnce({ data: { records: [], total: 0 } })
    wrapper.vm.handleQuery()
    await nextTick()
    expect(wrapper.vm.qp.pageNum).toBe(1)
  })

  test('resetQuery 重置查询条件并重新加载', async () => {
    wrapper.vm.qp.source = 'SERVER'
    wrapper.vm.qp.ruleCode = 'test'
    wrapper.vm.qp.authType = 'BASIC'
    wrapper.vm.qp.authCode = 'BASIC_MAIN'
    wrapper.vm.qp.tokenCode = 'TOKEN_A'
    wrapper.vm.qp.traceId = 'QLP0001'
    requestApi.mockResolvedValueOnce({ data: { records: [], total: 0 } })
    wrapper.vm.resetQuery()
    expect(wrapper.vm.qp.source).toBe('')
    expect(wrapper.vm.qp.ruleCode).toBe('')
    expect(wrapper.vm.qp.authType).toBe('')
    expect(wrapper.vm.qp.authCode).toBe('')
    expect(wrapper.vm.qp.tokenCode).toBe('')
    expect(wrapper.vm.qp.traceId).toBe('')
    expect(wrapper.vm.qp.pageNum).toBe(1)
  })

  test('分页大小变化时 pageNum 重置为1', () => {
    wrapper.vm.qp.pageNum = 5
    wrapper.vm.qp.pageSize = 30
    requestApi.mockResolvedValueOnce({ data: { records: [], total: 0 } })
    wrapper.vm.qp.pageNum = 1
    expect(wrapper.vm.qp.pageNum).toBe(1)
  })

  test('缓存中的非法 1000 页大小回落为默认 10', () => {
    window.sessionStorage.setItem('qlexpress.pageState.ExecutionLog', JSON.stringify({
      qp: { pageNum: 3, pageSize: 1000 }
    }))

    wrapper.vm.restoreCachedState()

    expect(wrapper.vm.qp.pageNum).toBe(3)
    expect(wrapper.vm.qp.pageSize).toBe(10)
    window.sessionStorage.removeItem('qlexpress.pageState.ExecutionLog')
  })

  test('项目和规则筛选器仅在展开时按小页加载', async () => {
    projectApi.listProjects.mockReset()
    definitionApi.listDefinitions.mockReset()
    projectApi.listProjects.mockResolvedValue({ data: { records: mockProjects() } })
    definitionApi.listDefinitions.mockResolvedValue({ data: { records: [] } })

    await wrapper.vm.onProjectFilterVisible(true)
    await wrapper.vm.onRuleFilterVisible(true)

    expect(projectApi.listProjects).toHaveBeenCalledWith({ pageNum: 1, pageSize: 50, keyword: '' })
    expect(definitionApi.listDefinitions).toHaveBeenCalledWith({ pageNum: 1, pageSize: 50, keyword: '', projectCode: '' })
  })

  test('日志详情按项目编码和规则编码精确补取缺失元数据', async () => {
    projectApi.listProjects.mockReset()
    definitionApi.listDefinitions.mockReset()
    projectApi.listProjects.mockResolvedValue({ data: { records: mockProjects().slice(0, 1) } })
    definitionApi.listDefinitions.mockResolvedValue({ data: { records: [{ id: 9, ruleCode: 'age_rule', ruleName: '年龄规则' }] } })
    wrapper.vm.projectList = []
    wrapper.vm.ruleList = []

    await wrapper.vm.ensureDetailMetadata({ projectCode: 'project_a', ruleCode: 'age_rule' })

    expect(projectApi.listProjects).toHaveBeenCalledWith({ pageNum: 1, pageSize: 1, projectCode: 'project_a' })
    expect(definitionApi.listDefinitions).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10, projectCode: 'project_a', ruleCode: 'age_rule' })
    expect(wrapper.vm.projectMap.project_a).toBe('项目A')
    expect(wrapper.vm.ruleMap.age_rule).toBe('年龄规则')
  })
})

describe('ExecutionLog — computed 属性', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.unmount() })

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
    wrapper.unmount()
  })

  test('详情弹窗初始关闭', async () => {
    const wrapper = await mountEmpty()
    expect(wrapper.vm.detailVis).toBe(false)
    wrapper.unmount()
  })

  test('加载失败时 loading 恢复为 false', async () => {
    const wrapper = await mountFailing()
    // finally 块保证 loading 恢复为 false
    expect(wrapper.vm.loading).toBe(false)
    wrapper.unmount()
  })
})

describe('ExecutionLog — 规则集命中统计', () => {
  let wrapper

  beforeEach(async () => {
    runtimeLogApi.getRuleSetStats.mockResolvedValue({
      data: {
        overview: {
          evaluationCount: 3,
          hitCount: 1,
          hitRate: 1 / 3,
          failureRate: 1 / 3,
          p95CostTimeMs: 200,
          p99CostTimeMs: 200
        },
        ruleSets: [{
          ruleCode: 'RS-A',
          ruleName: '准入规则集',
          evaluationCount: 2,
          hitCount: 1,
          hitRate: 0.5,
          items: [{ ruleCode: 'A-1', ruleName: '年龄准入', evaluationCount: 2, hitCount: 1, hitRate: 0.5 }]
        }]
      }
    })
    wrapper = await mountAndWait()
  })

  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('切换到规则集统计时按项目、规则集和时间范围加载数据', async () => {
    wrapper.vm.qp.projectCode = 'project_a'
    wrapper.vm.qp.ruleCode = 'RS-A'

    await wrapper.vm.handleViewChange({ name: 'ruleSetStats' })

    expect(runtimeLogApi.getRuleSetStats).toHaveBeenCalledWith({
      projectCode: 'project_a',
      projectName: '',
      ruleCode: 'RS-A',
      startTime: wrapper.vm.timeRange[0],
      endTime: wrapper.vm.timeRange[1]
    })
    expect(wrapper.vm.ruleSetStats.ruleSets[0].items[0].ruleCode).toBe('A-1')
  })

  test('统计加载失败时保留可重试错误状态', async () => {
    runtimeLogApi.getRuleSetStats.mockRejectedValueOnce(new Error('network'))

    await wrapper.vm.loadRuleSetStats()

    expect(wrapper.vm.ruleSetStatsLoading).toBe(false)
    expect(wrapper.vm.ruleSetStatsError).toBe('规则集命中统计加载失败')
  })
})
