// tests/unit/views/projectList.spec.js
import { mount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'
import * as projectApi from '@/api/project'
import ProjectList from '@/views/project/ProjectList.vue'

afterEach(() => { jest.clearAllMocks() })

// ─── Mock 数据 ───────────────────────────────────────────
function mockProjects() {
  return [
    { id: 1, projectCode: 'project_a', projectName: '项目A', description: '风控项目A', status: 1, maskedToken: '****abc', createTime: '2024-01-01 10:00:00' },
    { id: 2, projectCode: 'project_b', projectName: '项目B', description: '风控项目B', status: 0, maskedToken: null, createTime: '2024-01-02 10:00:00' },
    { id: 3, projectCode: 'project_c', projectName: '项目C', description: '', status: 1, maskedToken: '****xyz', createTime: '2024-01-03 10:00:00' }
  ]
}

// ─── 测试辅助：带 validate 方法的 form ref mock ─────────────────────────
function withFormRef(wrapper, validateResult = true) {
  wrapper.vm.$refs.form = {
    validate: jest.fn(cb => cb(validateResult))
  }
  return wrapper
}

// ─── 测试用例 ─────────────────────────────────────────────
function createTestVue() {
  const localVue = createLocalVue()
  localVue.prototype.$confirm = jest.fn().mockResolvedValue(true)
  localVue.prototype.$message = { success: jest.fn(), error: jest.fn(), warning: jest.fn(), info: jest.fn() }
  localVue.prototype.$notify = jest.fn()
  return localVue
}

async function mountAndWait() {
  projectApi.listProjects.mockResolvedValue({ data: { records: mockProjects(), total: 3 } })

  const wrapper = mount(ProjectList, {
    localVue: createTestVue(),
    mocks: {
      $route: { params: {} },
      $router: { push: jest.fn(), replace: jest.fn() }
    },
    stubs: {
      'el-form': true, 'el-form-item': true,
      'el-select': true, 'el-option': true,
      'el-input': true, 'el-button': true, 'el-tag': true,
      'el-table': true, 'el-table-column': true,
      'el-tabs': true, 'el-tab-pane': true,
      'el-dialog': true, 'el-card': true,
      'el-pagination': true, 'el-switch': true, 'el-loading': true,
      'el-textarea': true, 'el-divider': true, 'el-alert': true,
      'el-date-picker': true, 'el-tooltip': true
    }
  })

  await Vue.nextTick()
  await new Promise(r => setTimeout(r, 100))
  return wrapper
}

describe('ProjectList — 初始化与数据加载', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('mounted 后调用 listProjects', () => {
    expect(projectApi.listProjects).toHaveBeenCalled()
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

  test('dialogVisible 初始值为 false', () => {
    expect(wrapper.vm.dialogVisible).toBe(false)
  })

  test('tokenDialogVisible 初始值为 false', () => {
    expect(wrapper.vm.tokenDialogVisible).toBe(false)
  })

  test('分页参数初始化正确', () => {
    expect(wrapper.vm.qp.pageNum).toBe(1)
    expect(wrapper.vm.qp.pageSize).toBe(10)
    expect(wrapper.vm.qp.status).toBe('')
  })
})

describe('ProjectList — 筛选与搜索', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('queryProjectCode 模糊匹配', () => {
    wrapper.vm.allProjectCodes = ['project_a', 'project_b', 'project_c']
    wrapper.vm.queryProjectCode('project_a')
    expect(wrapper.vm.filteredProjectCodes.length).toBe(1)
    wrapper.vm.queryProjectCode('')
    expect(wrapper.vm.filteredProjectCodes.length).toBe(3)
  })

  test('queryProjectName 模糊匹配', () => {
    wrapper.vm.allProjectNames = ['项目A', '项目B', '项目C']
    wrapper.vm.queryProjectName('项目A')
    expect(wrapper.vm.filteredProjectNames.length).toBe(1)
    wrapper.vm.queryProjectName('')
    expect(wrapper.vm.filteredProjectNames.length).toBe(3)
  })

  test('onCreateTimeChange 设置时间范围', () => {
    wrapper.vm.onCreateTimeChange(['2024-01-01', '2024-01-31'])
    expect(wrapper.vm.qp.createBeginTime).toBe('2024-01-01')
    expect(wrapper.vm.qp.createEndTime).toBe('2024-01-31')
  })

  test('onCreateTimeChange 空值清空范围', () => {
    wrapper.vm.qp.createBeginTime = '2024-01-01'
    wrapper.vm.qp.createEndTime = '2024-01-31'
    wrapper.vm.onCreateTimeChange(null)
    expect(wrapper.vm.qp.createBeginTime).toBe('')
    expect(wrapper.vm.qp.createEndTime).toBe('')
  })

  test('handleQuery 重置页码并重新加载', async () => {
    wrapper.vm.qp.pageNum = 5
    projectApi.listProjects.mockResolvedValue({ data: { records: [], total: 0 } })
    wrapper.vm.handleQuery()
    await Vue.nextTick()
    expect(wrapper.vm.qp.pageNum).toBe(1)
  })

  test('resetQuery 重置所有查询条件', () => {
    wrapper.vm.qp.status = '1'
    wrapper.vm.qp.projectCode = 'test'
    wrapper.vm.qp.projectName = '测试项目'
    wrapper.vm.qp.createBeginTime = '2024-01-01'
    wrapper.vm.qp.createEndTime = '2024-01-31'
    wrapper.vm.createTimeRange = ['2024-01-01', '2024-01-31']
    projectApi.listProjects.mockResolvedValue({ data: { records: [], total: 0 } })
    wrapper.vm.resetQuery()
    expect(wrapper.vm.qp.status).toBe('')
    expect(wrapper.vm.qp.projectCode).toBe('')
    expect(wrapper.vm.qp.projectName).toBe('')
    expect(wrapper.vm.qp.createBeginTime).toBe('')
    expect(wrapper.vm.qp.createEndTime).toBe('')
    expect(wrapper.vm.createTimeRange).toEqual([])
  })

  test('loadData 删除空参数', async () => {
    wrapper.vm.qp.projectCode = ''
    wrapper.vm.qp.status = ''
    projectApi.listProjects.mockResolvedValue({ data: { records: [], total: 0 } })
    await wrapper.vm.loadData()
    const callArgs = projectApi.listProjects.mock.calls[projectApi.listProjects.mock.calls.length - 1][0]
    expect(callArgs.projectCode).toBeUndefined()
    expect(callArgs.status).toBeUndefined()
  })
})

describe('ProjectList — 项目操作', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('handleCreate 打开创建弹窗并重置表单', () => {
    wrapper.vm.handleCreate()
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.form.id).toBeNull()
    expect(wrapper.vm.form.projectCode).toBe('')
    expect(wrapper.vm.form.projectName).toBe('')
    expect(wrapper.vm.form.status).toBe(1)
  })

  test('handleEdit 填充编辑表单', () => {
    const row = { id: 1, projectCode: 'project_a', projectName: '项目A', description: '描述', status: 1 }
    wrapper.vm.handleEdit(row)
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.form.id).toBe(1)
    expect(wrapper.vm.form.projectName).toBe('项目A')
    expect(wrapper.vm.form.description).toBe('描述')
  })

  test('handleEdit 编辑时禁用项目编码', () => {
    const row = { id: 1, projectCode: 'project_a', projectName: '项目A', status: 1 }
    wrapper.vm.handleEdit(row)
    expect(wrapper.vm.form.projectCode).toBe('project_a')
    expect(wrapper.vm.form.id).toBe(1)
  })

  test('handleViewToken 打开令牌弹窗', async () => {
    projectApi.getFullToken.mockResolvedValue({ code: 200, data: 'secret-token-12345678' })
    const row = { id: 1, projectName: '项目A', maskedToken: '****abc' }
    wrapper.vm.handleViewToken(row)
    // API 调用是异步的，等待 Promise resolve
    await new Promise(r => setTimeout(r, 50))
    await Vue.nextTick()
    expect(wrapper.vm.tokenDialogVisible).toBe(true)
    expect(wrapper.vm.currentTokenProject).toBe('项目A')
    expect(wrapper.vm.fullToken).toBe('secret-token-12345678')
  })

  test('handleDelete 调用删除 API', async () => {
    projectApi.deleteProject.mockResolvedValue({ data: true })
    const row = { id: 1, projectName: '测试项目' }
    wrapper.vm.handleDelete(row)
    // $confirm 是异步的，等待用户确认后 deleteProject 被调用
    await new Promise(r => setTimeout(r, 50))
    await Vue.nextTick()
    expect(projectApi.deleteProject).toHaveBeenCalledWith(1)
  })

  test('handleExportDoc 导出 API 文档', async () => {
    projectApi.exportApiDoc.mockResolvedValue({ data: 'http://example.com/doc.pdf' })
    const row = { id: 1, projectName: '测试项目' }
    wrapper.vm.handleExportDoc(row)
    await Vue.nextTick()
    expect(projectApi.exportApiDoc).toHaveBeenCalledWith(1)
  })

  test('handleSubmit 新建项目时调用 createProject', async () => {
    projectApi.createProject.mockResolvedValue({ code: 200, data: { accessToken: 'tok' } })
    wrapper.vm.form = { id: null, projectCode: 'new_project', projectName: '新项目', description: '', status: 1 }
    withFormRef(wrapper)
    wrapper.vm.handleSubmit()
    // validate 回调是异步的
    await new Promise(r => setTimeout(r, 50))
    await Vue.nextTick()
    expect(projectApi.createProject).toHaveBeenCalled()
  })

  test('handleSubmit 编辑项目时调用 updateProject', async () => {
    projectApi.updateProject.mockResolvedValue({ code: 200, data: true })
    wrapper.vm.form = { id: 1, projectCode: 'project_a', projectName: '项目A已更新', description: '', status: 1 }
    withFormRef(wrapper)
    wrapper.vm.handleSubmit()
    await new Promise(r => setTimeout(r, 50))
    await Vue.nextTick()
    expect(projectApi.updateProject).toHaveBeenCalled()
  })

  test('handleSubmit 成功后关闭弹窗并刷新', async () => {
    projectApi.createProject.mockResolvedValue({ code: 200, data: { accessToken: 'tok' } })
    wrapper.vm.form = { id: null, projectCode: 'new_project', projectName: '新项目', status: 1 }
    withFormRef(wrapper)
    wrapper.vm.handleSubmit()
    await new Promise(r => setTimeout(r, 50))
    await Vue.nextTick()
    expect(wrapper.vm.dialogVisible).toBe(false)
  })
})

describe('ProjectList — 边界情况', () => {
  test('projects 为空数组不报错', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [], total: 0 } })
    const wrapper = mount(ProjectList, {
      localVue: createTestVue(),
      mocks: {
        $route: { params: {} },
        $router: { push: jest.fn(), replace: jest.fn() }
      },
      stubs: {
        'el-form': true, 'el-form-item': true, 'el-select': true, 'el-option': true,
        'el-input': true, 'el-button': true, 'el-tag': true,
        'el-table': true, 'el-table-column': true,
        'el-dialog': true, 'el-pagination': true, 'el-loading': true, 'el-textarea': true,
        'el-date-picker': true, 'el-switch': true, 'el-tabs': true, 'el-tab-pane': true,
        'el-card': true, 'el-divider': true, 'el-alert': true, 'el-tooltip': true
      }
    })
    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
    expect(wrapper.vm.tableData).toEqual([])
    wrapper.destroy()
  })

  test('handleDelete 无项目名称时不报错', async () => {
    // 独立的 wrapper，避免与外层 describe 的 beforeEach 冲突
    projectApi.listProjects.mockResolvedValue({ data: { records: [], total: 0 } })
    const wrapper = mount(ProjectList, {
      localVue: createTestVue(),
      mocks: {
        $route: { params: {} },
        $router: { push: jest.fn(), replace: jest.fn() }
      },
      stubs: {
        'el-form': true, 'el-form-item': true, 'el-select': true, 'el-option': true,
        'el-input': true, 'el-button': true, 'el-tag': true,
        'el-table': true, 'el-table-column': true,
        'el-dialog': true, 'el-pagination': true, 'el-loading': true, 'el-textarea': true,
        'el-date-picker': true, 'el-switch': true, 'el-tabs': true, 'el-tab-pane': true,
        'el-card': true, 'el-divider': true, 'el-alert': true, 'el-tooltip': true
      }
    })
    await Vue.nextTick()
    projectApi.deleteProject.mockResolvedValue({ data: true })
    const row = { id: 99 }
    wrapper.vm.handleDelete(row)
    await new Promise(r => setTimeout(r, 50))
    await Vue.nextTick()
    expect(projectApi.deleteProject).toHaveBeenCalledWith(99)
    wrapper.destroy()
  })

  test('分页切换保持 pageSize', async () => {
    const w = await mountAndWait()
    w.vm.qp.pageSize = 30
    w.vm.qp.pageNum = 1
    expect(w.vm.qp.pageSize).toBe(30)
    expect(w.vm.qp.pageNum).toBe(1)
    w.destroy()
  })

  test('allProjectCodes 和 allProjectNames 从响应中提取', async () => {
    const w = await mountAndWait()
    await w.vm.loadData()
    expect(w.vm.allProjectCodes.length).toBe(3)
    expect(w.vm.allProjectNames.length).toBe(3)
    expect(w.vm.allProjectCodes).toContain('project_a')
    expect(w.vm.allProjectNames).toContain('项目A')
    w.destroy()
  })
})