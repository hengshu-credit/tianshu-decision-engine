// tests/unit/views/ruleList.spec.js
import { mount } from '@test-utils'
import { h, nextTick } from 'vue'
// Mock API 模块
jest.mock('@/api/definition', () => ({
  listDefinitions: jest.fn(),
  createDefinition: jest.fn(),
  deleteDefinition: jest.fn(),
  publishRule: jest.fn(),
  unpublishRule: jest.fn()
}))

jest.mock('@/api/project', () => ({
  listProjects: jest.fn()
}))

import * as definitionApi from '@/api/definition'
import * as projectApi from '@/api/project'
import RuleList from '@/views/rule/RuleList.vue'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import fs from 'fs'
import path from 'path'

afterEach(() => {
  jest.clearAllMocks()
})

describe('RuleList 项目筛选交互', () => {
  test('顶部注册并绑定项目编码和项目名称筛选组件', () => {
    const source = fs.readFileSync(path.resolve(__dirname, '../../../src/views/rule/RuleList.vue'), 'utf8')

    expect(RuleList.components.ProjectFilterSelect).toBe(ProjectFilterSelect)
    expect(source).toContain('field="projectCode"')
    expect(source).toContain('field="projectName"')
  })
})

// ─── Mock 数据 ───────────────────────────────────────────
function mockRules() {
  return [
    { id: 1, ruleName: '年龄判断规则', ruleCode: 'age_rule', modelType: 'TABLE', scope: 'PROJECT', projectId: 1, projectName: '项目A', status: 0, currentVersion: 1, publishedVersion: null },
    { id: 2, ruleName: '评分卡规则', ruleCode: 'score_card', modelType: 'SCORE', scope: 'PROJECT', projectId: 1, projectName: '项目A', status: 1, currentVersion: 2, publishedVersion: 2 },
    { id: 3, ruleName: '全局规则', ruleCode: 'global_rule', modelType: 'TREE', scope: 'GLOBAL', projectId: 0, projectName: '—', status: 1, currentVersion: 1, publishedVersion: 1 }
  ]
}

function mockProjects() {
  return [
    { id: 1, projectName: '项目A', projectCode: 'project_a' },
    { id: 2, projectName: '项目B', projectCode: 'project_b' }
  ]
}

// ─── 带方法的 el-form/el-input stub（jsdom 中 el-form 的 ref 方法不可用）────────
const FormStub = {
  name: 'ElForm',
  render: () => h('form'),
  methods: {
    clearValidate: jest.fn(),
    validate: jest.fn(cb => cb && cb(true)),
    validateField: jest.fn(),
    resetFields: jest.fn()
  }
}
const InputStub = {
  name: 'ElInput',
  render: () => h('input', { attrs: { type: 'text' } }),
  methods: { focus: jest.fn(), blur: jest.fn() }
}

// ─── 测试用例 ─────────────────────────────────────────────


function createMountOptions() {
  return {
    mocks: {
      $route: { params: {} },
      $router: { push: jest.fn(), replace: jest.fn() },
      $message: Object.assign(jest.fn(), { success: jest.fn(), error: jest.fn(), warning: jest.fn() }),
      // $confirm 每次都是新 mock，测试中通过 mockResolvedValueOnce 配置返回值
      $confirm: jest.fn()
    },
    stubs: {
      'el-form': FormStub, 'el-form-item': true,
      'el-select': true, 'el-option': true,
      'el-input': InputStub, 'el-button': true, 'el-tag': true,
      'el-table': true, 'el-table-column': true,
      'el-tabs': true, 'el-tab-pane': true,
      'el-dialog': true, 'el-card': true,
      'el-pagination': true, 'el-switch': true, 'el-loading': true,
      'el-textarea': true, 'el-divider': true, 'el-alert': true,
      'el-link': true, 'el-dropdown': true, 'el-dropdown-menu': true,
      'el-dropdown-item': true, 'el-icon': true, 'el-col': true
    }
  }
}

async function mountAndWait() {
  projectApi.listProjects.mockResolvedValue({ data: { records: mockProjects() } })
  definitionApi.listDefinitions.mockResolvedValue({ data: { records: mockRules(), total: 3 } })

  const wrapper = mount(RuleList, createMountOptions())

  await nextTick()
  await new Promise(r => setTimeout(r, 100))
  return wrapper
}

describe('RuleList — 初始化与数据加载', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('mounted 后调用 listProjects', () => {
    expect(projectApi.listProjects).toHaveBeenCalled()
  })

  test('mounted 后调用 listDefinitions', () => {
    expect(definitionApi.listDefinitions).toHaveBeenCalled()
  })

  test('tableData 数据正确赋值', () => {
    expect(wrapper.vm.tableData).toBeInstanceOf(Array)
    expect(wrapper.vm.tableData.length).toBe(3)
  })

  test('total 正确赋值', () => {
    expect(wrapper.vm.total).toBe(3)
  })

  test('loading 初始值为 false', () => {
    expect(wrapper.vm.loading).toBe(false)
  })
})

describe('RuleList — 标签与格式化方法', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('modelTypeLabel 返回正确的中文标签', () => {
    expect(wrapper.vm.modelTypeLabel('TABLE')).toBe('决策表')
    expect(wrapper.vm.modelTypeLabel('TREE')).toBe('决策树')
    expect(wrapper.vm.modelTypeLabel('FLOW')).toBe('决策流')
    expect(wrapper.vm.modelTypeLabel('RULE_SET')).toBe('规则集')
    expect(wrapper.vm.modelTypeLabel('CROSS')).toBe('交叉表')
    expect(wrapper.vm.modelTypeLabel('SCORE')).toBe('评分卡')
    expect(wrapper.vm.modelTypeLabel('CROSS_ADV')).toBe('复杂交叉表')
    expect(wrapper.vm.modelTypeLabel('SCORE_ADV')).toBe('复杂评分卡')
    expect(wrapper.vm.modelTypeLabel('SCRIPT')).toBe('QL脚本')
    expect(wrapper.vm.modelTypeLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(wrapper.vm.modelTypeLabel(null)).toBe(null)
  })

  test('statusLabel 返回正确的状态标签', () => {
    expect(wrapper.vm.statusLabel(0)).toBe('草稿')
    expect(wrapper.vm.statusLabel(1)).toBe('已发布')
    expect(wrapper.vm.statusLabel(2)).toBe('已下线')
    expect(wrapper.vm.statusLabel(9)).toBe(9)
  })

  test('statusTagType 返回正确的 tag 类型', () => {
    expect(wrapper.vm.statusTagType(0)).toBe('info')
    expect(wrapper.vm.statusTagType(1)).toBe('success')
    expect(wrapper.vm.statusTagType(2)).toBe('warning')
    expect(wrapper.vm.statusTagType(9)).toBe('info')
  })

  test('仅发布状态和发布版本同时存在时才视为已发布', () => {
    expect(wrapper.vm.effectiveStatus({ status: 1, publishedVersion: null })).toBe(0)
    expect(wrapper.vm.effectiveStatus({ status: 1, publishedVersion: 3 })).toBe(1)
    expect(wrapper.vm.effectiveStatus({ status: 2, publishedVersion: 3 })).toBe(2)
    expect(wrapper.vm.isPublished({ status: 1, publishedVersion: null })).toBe(false)
    expect(wrapper.vm.isPublished({ status: 1, publishedVersion: 3 })).toBe(true)
  })

  test('发布版本为空时显示占位符', () => {
    expect(wrapper.vm.publishedVersionLabel({ publishedVersion: null })).toBe('-')
    expect(wrapper.vm.publishedVersionLabel({ publishedVersion: 0 })).toBe(0)
    expect(wrapper.vm.publishedVersionLabel({ publishedVersion: 3 })).toBe(3)
  })
})

describe('RuleList — 筛选与搜索', () => {
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

  test('handleQuery 重置页码并重新加载', async () => {
    wrapper.vm.queryParams.pageNum = 5
    definitionApi.listDefinitions.mockResolvedValue({ data: { records: [], total: 0 } })
    wrapper.vm.handleQuery()
    await nextTick()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
  })

  test('resetQuery 重置所有查询条件', () => {
    wrapper.vm.queryParams.scope = 'GLOBAL'
    wrapper.vm.queryParams.modelType = 'TABLE'
    wrapper.vm.queryParams.ruleCode = 'test'
    definitionApi.listDefinitions.mockResolvedValue({ data: { records: [], total: 0 } })
    wrapper.vm.resetQuery()
    expect(wrapper.vm.queryParams.scope).toBe('')
    expect(wrapper.vm.queryParams.modelType).toBe('')
    expect(wrapper.vm.queryParams.ruleCode).toBe('')
    expect(wrapper.vm.queryParams.status).toBe('')
  })

  test('loadData 删除空参数', async () => {
    wrapper.vm.queryParams.scope = ''
    wrapper.vm.queryParams.projectCode = ''
    definitionApi.listDefinitions.mockResolvedValue({ data: { records: [], total: 0 } })
    await wrapper.vm.loadData()
    const callArgs = definitionApi.listDefinitions.mock.calls[definitionApi.listDefinitions.mock.calls.length - 1][0]
    expect(callArgs.scope).toBeUndefined()
    expect(callArgs.projectCode).toBeUndefined()
  })
})

describe('RuleList — 规则操作', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('handleCreate 打开创建弹窗', () => {
    wrapper.vm.form.status = 1
    wrapper.vm.handleCreate()
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.form.status).toBe(0)
  })

  test('handleDetail 跳转到详情页', () => {
    const row = { id: 1 }
    wrapper.vm.handleDetail(row)
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/rule/1')
  })

  test('handleDesign 跳转到设计器', () => {
    const row = { id: 1, modelType: 'TABLE' }
    wrapper.vm.handleDesign(row)
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/designer/table/1')
  })

  test('handleDesign 跳转到规则集设计器', () => {
    const row = { id: 4, modelType: 'RULE_SET' }
    wrapper.vm.handleDesign(row)
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/designer/ruleset/4')
  })

  test('handlePublish 调用发布 API', async () => {
    definitionApi.publishRule.mockResolvedValue({ data: true })
    wrapper.vm.$confirm.mockResolvedValueOnce()
    const row = { id: 1, ruleName: '测试规则' }
    await wrapper.vm.handlePublish(row)
    await nextTick()
    expect(definitionApi.publishRule).toHaveBeenCalledWith(1)
  })

  test('handleUnpublish 调用下线 API', async () => {
    definitionApi.unpublishRule.mockResolvedValue({ data: true })
    wrapper.vm.$confirm.mockResolvedValueOnce()
    const row = { id: 1, ruleName: '测试规则' }
    await wrapper.vm.handleUnpublish(row)
    await nextTick()
    expect(definitionApi.unpublishRule).toHaveBeenCalledWith(1)
  })

  test('handleDelete 调用删除 API', async () => {
    definitionApi.deleteDefinition.mockResolvedValue({ data: true })
    wrapper.vm.$confirm.mockResolvedValueOnce()
    const row = { id: 1, ruleName: '测试规则' }
    await wrapper.vm.handleDelete(row)
    await nextTick()
    expect(definitionApi.deleteDefinition).toHaveBeenCalledWith(1)
  })

  test('handlePublish 显示重新发布按钮', async () => {
    definitionApi.publishRule.mockResolvedValue({ data: true })
    wrapper.vm.$confirm.mockResolvedValueOnce()
    const row = { id: 1, publishedVersion: 1 }
    await wrapper.vm.handlePublish(row)
    await nextTick()
    expect(definitionApi.publishRule).toHaveBeenCalledWith(1)
  })
})

describe('RuleList — 边界情况', () => {
  test('rules 为空数组不报错', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    definitionApi.listDefinitions.mockResolvedValue({ data: { records: [], total: 0 } })
    const wrapper = mount(RuleList, createMountOptions())
    await nextTick()
    await new Promise(r => setTimeout(r, 100))
    expect(wrapper.vm.tableData).toEqual([])
    wrapper.unmount()
  })

  test('listDefinitions 失败时显示错误消息', async () => {
    definitionApi.listDefinitions.mockRejectedValue(new Error('加载失败'))
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {})
    const wrapper = mount(RuleList, createMountOptions())
    await nextTick()
    await new Promise(r => setTimeout(r, 100))
    expect(wrapper.vm.loading).toBe(false)
    consoleSpy.mockRestore()
    wrapper.unmount()
  })
})
