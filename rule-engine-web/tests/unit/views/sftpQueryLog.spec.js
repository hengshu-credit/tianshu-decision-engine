import { mount, flushPromises } from '@vue/test-utils'
import SftpQueryLog from '@/views/sftp/SftpQueryLog.vue'
import { listRuntimeLogs } from '@/api/runtimeLog'
import { previewSftpLog } from '@/api/sftp'

vi.mock('@/api/sftp', () => ({ previewSftpLog: vi.fn() }))

describe('SFTP 日志筛选', () => {
  let wrapper
  beforeEach(async () => {
    vi.clearAllMocks()
    URL.createObjectURL = vi.fn(() => 'blob:current')
    URL.revokeObjectURL = vi.fn()
    listRuntimeLogs.mockResolvedValue({ data: { records: [], total: 0 } })
    wrapper = mount(SftpQueryLog, { shallow: true, global: { stubs: { 'project-filter-select': true } } })
    await flushPromises()
  })
  afterEach(() => wrapper.unmount())
  it('新查询返回第一页并保留 SFTP 模块范围', async () => {
    wrapper.vm.query.pageNum = 3
    wrapper.vm.query.traceId = 'trace-new'
    await wrapper.vm.handleQuery()
    expect(listRuntimeLogs).toHaveBeenLastCalledWith(expect.objectContaining({ pageNum: 1, moduleType: 'SFTP', traceId: 'trace-new' }))
  })
  it('慢响应不覆盖较新的查询结果', async () => {
    let resolveOld
    listRuntimeLogs.mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve }))
    const oldLoad = wrapper.vm.load()
    listRuntimeLogs.mockResolvedValueOnce({ data: { records: [{ id: 2 }], total: 1 } })
    await wrapper.vm.load()
    resolveOld({ data: { records: [{ id: 1 }], total: 9 } })
    await oldLoad
    expect(wrapper.vm.rows).toEqual([{ id: 2 }])
    expect(wrapper.vm.total).toBe(1)
  })
  it('读取失败可以重试，且不展示上一次查询结果', async () => {
    wrapper.vm.rows = [{ id: 1 }]
    listRuntimeLogs.mockRejectedValueOnce(new Error('日志不可用'))
    await wrapper.vm.load()
    expect(wrapper.vm.rows).toEqual([])
    expect(wrapper.vm.loadError).toBe('日志不可用')
    await wrapper.vm.load()
    expect(wrapper.vm.loadError).toBe('')
  })
  it('切换文件后旧响应不生成 URL，也不覆盖当前内容', async () => {
    let resolveOld
    previewSftpLog.mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve }))
    const oldPreview = wrapper.vm.preview({ id: 1 })
    previewSftpLog.mockResolvedValueOnce({ data: { type: 'text/plain', text: async () => '新文件' } })
    await wrapper.vm.preview({ id: 2 })
    resolveOld({ data: { type: 'text/plain', text: async () => '旧文件' } })
    await oldPreview
    expect(wrapper.vm.previewText).toBe('新文件')
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
  })
  it('关闭后中止读取，晚到响应不能恢复文件内容', async () => {
    let resolveRequest
    previewSftpLog.mockReturnValueOnce(new Promise(resolve => { resolveRequest = resolve }))
    const pending = wrapper.vm.preview({ id: 1 })
    const signal = previewSftpLog.mock.calls[0][1]?.signal
    wrapper.vm.previewVisible = false
    await flushPromises()
    expect(signal?.aborted).toBe(true)
    resolveRequest({ data: { type: 'image/png' } })
    await pending
    expect(wrapper.vm.previewUrl).toBe('')
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })
  it('关闭已打开的预览立即回收 URL', async () => {
    previewSftpLog.mockResolvedValueOnce({ data: { type: 'image/png' } })
    await wrapper.vm.preview({ id: 1 })
    wrapper.vm.previewVisible = false
    await flushPromises()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:current')
    expect(wrapper.vm.previewUrl).toBe('')
  })
  it('失败显示错误而非无内容，重新打开可以恢复', async () => {
    previewSftpLog.mockRejectedValueOnce(new Error('读取超时'))
    await wrapper.vm.preview({ id: 1 })
    expect(wrapper.vm.previewError).toBe('读取超时')
    previewSftpLog.mockResolvedValueOnce({ data: { type: 'image/png' } })
    await wrapper.vm.preview({ id: 1 })
    expect(wrapper.vm.previewError).toBe('')
    expect(wrapper.vm.previewUrl).toBe('blob:current')
  })
})
