import { mount, flushPromises } from '@vue/test-utils'
import SftpDatasourceList from '@/views/sftp/SftpDatasourceList.vue'
import * as sftpApi from '@/api/sftp'
import { editableResource } from '@/api/governance'
import { clearDesignerLeaveGuards, confirmDesignerPathsCanClose } from '@/utils/designerLeaveGuard'

vi.mock('@/api/sftp', () => ({
  listSftpDatasources: vi.fn(), getSftpDatasource: vi.fn(),
  createSftpDatasource: vi.fn(), updateSftpDatasource: vi.fn(),
  deleteSftpDatasource: vi.fn(), testSftpDatasource: vi.fn(), testSftpDatasourceDraft: vi.fn(),
}))
vi.mock('@/api/governance', () => ({ editableResource: vi.fn() }))

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

describe('SFTP 数据源配置流程', () => {
  let wrapper
  beforeEach(async () => {
    vi.clearAllMocks()
    editableResource.mockReset()
    sftpApi.listSftpDatasources.mockResolvedValue({ data: { records: [], total: 0 } })
    wrapper = mount(SftpDatasourceList, {
      shallow: true,
      global: {
        mocks: { $route: { path: '/sftp' }, $router: { push: vi.fn() }, $message: { success: vi.fn(), error: vi.fn() }, $confirm: vi.fn().mockResolvedValue(true) },
        stubs: {
          'project-filter-select': true, 'governance-workspace-toolbar': true,
          'governance-resource-status': true, 'governance-publish-action': true, 'scope-project-fields': true,
          'el-form': { template: '<div><slot /></div>', methods: { validate: () => Promise.resolve(true), clearValidate() {} } },
        },
      },
    })
    await flushPromises()
  })
  afterEach(() => { wrapper.unmount(); clearDesignerLeaveGuards() })

  it('取消关闭或关闭页签时保留未保存内容，确认后清除表单与凭据', async () => {
    wrapper.vm.openCreate()
    wrapper.vm.form.datasourceName = '尚未保存的配置'
    wrapper.vm.form.password = 'only-in-memory'
    wrapper.vm.$confirm.mockRejectedValueOnce('cancel')
    expect(await confirmDesignerPathsCanClose(['/sftp'])).toBe(false)
    expect(wrapper.vm.form.datasourceName).toBe('尚未保存的配置')
    wrapper.vm.$confirm.mockRejectedValueOnce('cancel')
    await wrapper.vm.closeDialog()
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.form.password).toBe('only-in-memory')
    wrapper.vm.$confirm.mockResolvedValueOnce(true)
    await wrapper.vm.closeDialog()
    expect(wrapper.vm.dialogVisible).toBe(false)
    expect(wrapper.vm.form.password).toBe('')
    expect(wrapper.vm.configurationHasUnsavedChanges).toBe(false)
  })

  it('连续保存只提交一次，保存中不能关闭或替换表单', async () => {
    wrapper.vm.openCreate()
    wrapper.vm.form.datasourceCode = 'Keep_MixedCase'
    const pending = deferred()
    sftpApi.createSftpDatasource.mockReturnValueOnce(pending.promise)
    const first = wrapper.vm.save()
    const duplicate = wrapper.vm.save()
    await flushPromises()
    expect(sftpApi.createSftpDatasource).toHaveBeenCalledTimes(1)
    await wrapper.vm.closeDialog()
    wrapper.vm.openCreate()
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.form.datasourceCode).toBe('Keep_MixedCase')
    expect(await confirmDesignerPathsCanClose(['/sftp'])).toBe(false)
    pending.resolve({ data: { id: 81, status: 'DRAFT' } })
    await Promise.all([first, duplicate])
    expect(wrapper.vm.dialogVisible).toBe(false)
    expect(wrapper.vm.saving).toBe(false)
    expect(wrapper.vm.configurationHasUnsavedChanges).toBe(false)
  })

  it('迟到的编辑读取不能覆盖刚打开的新建表单', async () => {
    const pending = deferred()
    sftpApi.getSftpDatasource.mockReturnValueOnce(pending.promise)
    editableResource.mockResolvedValueOnce({ id: 9, datasourceName: '旧配置' })
    const editing = wrapper.vm.openEdit({ id: 9 })
    wrapper.vm.openCreate()
    wrapper.vm.form.datasourceName = '正在新建的配置'
    pending.resolve({ data: { id: 9 } })
    await editing
    expect(wrapper.vm.form.id).toBeUndefined()
    expect(wrapper.vm.form.datasourceName).toBe('正在新建的配置')
  })

  it('查询新条件时从第一页开始，刷新保留当前页', async () => {
    wrapper.vm.query.pageNum = 4
    wrapper.vm.query.datasourceCode = 'Target_Code'
    await wrapper.vm.handleQuery()
    expect(sftpApi.listSftpDatasources).toHaveBeenLastCalledWith(expect.objectContaining({ pageNum: 1, datasourceCode: 'Target_Code' }))
    wrapper.vm.query.pageNum = 2
    await wrapper.vm.load()
    expect(sftpApi.listSftpDatasources).toHaveBeenLastCalledWith(expect.objectContaining({ pageNum: 2 }))
  })

  it('后返回的旧请求不能覆盖新筛选的结果', async () => {
    const first = deferred(), second = deferred()
    sftpApi.listSftpDatasources.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    wrapper.vm.query.datasourceCode = 'old'
    const oldLoad = wrapper.vm.load()
    wrapper.vm.query.datasourceCode = 'new'
    const newLoad = wrapper.vm.load()
    expect(sftpApi.listSftpDatasources.mock.calls.at(-2)[0].datasourceCode).toBe('old')
    second.resolve({ data: { records: [{ id: 2 }], total: 1 } })
    await newLoad
    first.resolve({ data: { records: [{ id: 1 }], total: 20 } })
    await oldLoad
    expect(wrapper.vm.rows).toEqual([{ id: 2 }])
    expect(wrapper.vm.total).toBe(1)
    expect(wrapper.vm.loading).toBe(false)
  })

  it('旧请求失败不能结束新请求的加载状态', async () => {
    const first = deferred(), second = deferred()
    sftpApi.listSftpDatasources.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const oldLoad = wrapper.vm.load(), newLoad = wrapper.vm.load()
    first.reject(new Error('过期请求失败'))
    await oldLoad
    expect(wrapper.vm.loading).toBe(true)
    expect(wrapper.vm.loadError).toBe('')
    second.resolve({ data: { records: [{ id: 2 }], total: 1 } })
    await newLoad
  })

  it('列表失败清除旧记录并提供可恢复状态', async () => {
    wrapper.vm.rows = [{ id: 1 }]
    wrapper.vm.total = 1
    sftpApi.listSftpDatasources.mockRejectedValueOnce(new Error('连接中断'))
    await wrapper.vm.load()
    expect(wrapper.vm.rows).toEqual([])
    expect(wrapper.vm.total).toBe(0)
    expect(wrapper.vm.loadError).toContain('连接中断')
    await wrapper.vm.load()
    expect(wrapper.vm.loadError).toBe('')
  })

  it('草稿读取失败不允许用生效配置继续编辑', async () => {
    sftpApi.getSftpDatasource.mockResolvedValue({ data: { id: 9, datasourceName: '生效配置' } })
    editableResource.mockRejectedValueOnce(new Error('草稿服务不可用'))
    await wrapper.vm.openEdit({ id: 9 })
    expect(wrapper.vm.dialogVisible).toBe(false)
    expect(wrapper.vm.$message.error).toHaveBeenCalledWith(expect.stringContaining('草稿'))
  })

  it('确实没有草稿时仍可编辑生效配置', async () => {
    const detail = { id: 9, datasourceCode: 'Keep_MixedCase' }
    sftpApi.getSftpDatasource.mockResolvedValue({ data: detail })
    editableResource.mockResolvedValue(detail)
    await wrapper.vm.openEdit({ id: 9 })
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.form.datasourceCode).toBe('Keep_MixedCase')
  })

  it('保存草稿说明下一步提交审批，不声称已经生效', async () => {
    wrapper.vm.form.datasourceCode = 'Keep_MixedCase'
    sftpApi.createSftpDatasource.mockResolvedValue({ data: { id: 81, status: 'DRAFT' } })
    await wrapper.vm.save()
    expect(sftpApi.createSftpDatasource).toHaveBeenCalledWith(expect.objectContaining({ datasourceCode: 'Keep_MixedCase' }))
    expect(wrapper.vm.$message.success).toHaveBeenCalledWith(expect.stringContaining('草稿已保存'))
    expect(wrapper.vm.$message.success).toHaveBeenCalledWith(expect.stringContaining('提交审批'))
  })

  it('删除未发布草稿说明无需审批，避免用户寻找不存在的审批任务', async () => {
    sftpApi.deleteSftpDatasource.mockResolvedValue({ data: { deleted: true } })
    wrapper.vm.remove({ id: 81, datasourceName: '草稿' })
    await flushPromises()
    expect(sftpApi.deleteSftpDatasource).toHaveBeenCalledWith(81)
    expect(wrapper.vm.$message.success).toHaveBeenCalledWith('已删除未发布草稿，无需审批')
    expect(wrapper.vm.$router.push).not.toHaveBeenCalled()
  })
  it('修改连接参数使上次测试成功过期，迟到结果不覆盖新表单', async () => {
    sftpApi.testSftpDatasourceDraft.mockResolvedValueOnce({ data: 'ok' })
    await wrapper.vm.testDraft()
    expect(wrapper.vm.connectionTestOutdated).toBe(false)
    expect(wrapper.vm.connectionTestStatus).toBe('success')
    wrapper.vm.form.host = 'changed.invalid'
    expect(wrapper.vm.connectionTestOutdated).toBe(true)
    const pending = deferred()
    sftpApi.testSftpDatasourceDraft.mockReturnValueOnce(pending.promise)
    const testing = wrapper.vm.testDraft()
    await flushPromises()
    wrapper.vm.openCreate()
    pending.resolve({ data: 'ok' })
    await testing
    expect(wrapper.vm.connectionTestStatus).toBe('idle')
  })
})
