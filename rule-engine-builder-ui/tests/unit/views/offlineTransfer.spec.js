import { shallowMount } from '@test-utils'
import { nextTick } from 'vue'
import { ElMessageBox } from 'element-plus'

vi.mock('@/api/transfer', () => ({
  exportResourceTransfer: vi.fn(),
  previewResourceTransfer: vi.fn(),
  importResourceTransfer: vi.fn(),
}))

import * as transferApi from '@/api/transfer'
import { listProjects } from '@/api/project'
import OfflineTransfer from '@/views/transfer/OfflineTransfer.vue'

describe('OfflineTransfer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    transferApi.previewResourceTransfer.mockReset().mockResolvedValue({
      data: { packageDigest: 'digest-1', conflictCount: 0, resources: [], roots: [] },
    })
    transferApi.importResourceTransfer.mockReset().mockResolvedValue({ data: { status: 'APPLIED' } })
    ElMessageBox.confirm.mockReset().mockResolvedValue('confirm')
  })

  async function previewGlobal() {
    const wrapper = shallowMount(OfflineTransfer)
    wrapper.vm.selectFile({ raw: new File(['zip'], 'transfer.zip') })
    wrapper.vm.options.targetScope = 'GLOBAL'
    await nextTick()
    await wrapper.vm.previewPackage()
    return wrapper
  }

  test.each([
    ['targetScope', 'PROJECT'], ['targetProjectId', '9'], ['createProject', true],
    ['projectCode', 'changed'], ['projectName', '变更项目'], ['resourcePolicy', 'OVERWRITE'],
    ['variablePolicy', 'SUFFIX'], ['suffix', '_other'], ['publishRules', true],
  ])('预检后修改 %s 必须重新预检', async (key, value) => {
    const wrapper = await previewGlobal()
    expect(wrapper.vm.preview).not.toBeNull()
    wrapper.vm.options[key] = value
    await nextTick()
    expect(wrapper.vm.preview).toBeNull()
    wrapper.unmount()
  })

  test('没有预检时即使直接调用提交方法也不能导入', async () => {
    const wrapper = shallowMount(OfflineTransfer)
    wrapper.vm.selectFile({ raw: new File(['zip'], 'transfer.zip') })
    wrapper.vm.options.targetScope = 'GLOBAL'
    await wrapper.vm.applyPackage()
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
    expect(transferApi.importResourceTransfer).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('更换文件后丢弃旧预检的迟到响应', async () => {
    let resolvePreview
    const wrapper = await previewGlobal()
    transferApi.previewResourceTransfer.mockReturnValueOnce(new Promise(resolve => { resolvePreview = resolve }))
    const pending = wrapper.vm.previewPackage()
    wrapper.vm.selectFile({ raw: new File(['different'], 'transfer.zip') })
    resolvePreview({ data: { packageDigest: 'old-file' } })
    await pending
    expect(wrapper.vm.preview).toBeNull()
    expect(wrapper.vm.previewing).toBe(false)
    wrapper.unmount()
  })

  test('重新预检失败后不能继续使用上次通过的报告', async () => {
    const wrapper = await previewGlobal()
    transferApi.previewResourceTransfer.mockRejectedValueOnce(new Error('预检服务不可用'))
    await wrapper.vm.previewPackage().catch(() => {})
    expect(wrapper.vm.preview).toBeNull()
    await wrapper.vm.applyPackage()
    expect(transferApi.importResourceTransfer).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('确认弹窗等待期间修改配置不会发送未预检的新选项', async () => {
    const wrapper = await previewGlobal()
    let confirm
    ElMessageBox.confirm.mockReturnValueOnce(new Promise(resolve => { confirm = resolve }))
    const pending = wrapper.vm.applyPackage()
    wrapper.vm.options.resourcePolicy = 'OVERWRITE'
    await nextTick()
    confirm('confirm')
    await pending
    expect(transferApi.importResourceTransfer).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('连续点击只确认和提交一次，成功后预检失效', async () => {
    const wrapper = await previewGlobal()
    const originalFile = wrapper.vm.importFile
    let confirm
    ElMessageBox.confirm.mockReturnValueOnce(new Promise(resolve => { confirm = resolve }))
    const first = wrapper.vm.applyPackage()
    const second = wrapper.vm.applyPackage()
    confirm('confirm')
    await Promise.all([first, second])
    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1)
    expect(transferApi.importResourceTransfer).toHaveBeenCalledTimes(1)
    expect(transferApi.importResourceTransfer).toHaveBeenCalledWith(originalFile,
      expect.objectContaining({ targetScope: 'GLOBAL', createProject: false, targetProjectId: null }))
    expect(ElMessageBox.confirm.mock.calls[0][0]).toContain('全局')
    expect(wrapper.vm.resultVisible).toBe(true)
    expect(wrapper.vm.preview).toBeNull()
    await wrapper.vm.applyPackage()
    expect(transferApi.importResourceTransfer).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  test('取消确认保留预检且不抛出未处理异常', async () => {
    const wrapper = await previewGlobal()
    ElMessageBox.confirm.mockRejectedValueOnce('cancel')
    await expect(wrapper.vm.applyPackage()).resolves.toBeUndefined()
    expect(wrapper.vm.preview).not.toBeNull()
    expect(wrapper.vm.applying).toBe(false)
    expect(transferApi.importResourceTransfer).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('两个预检乱序返回时仅展示当前配置报告', async () => {
    const wrapper = await previewGlobal()
    let resolveOld
    let resolveNew
    transferApi.previewResourceTransfer
      .mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve }))
      .mockReturnValueOnce(new Promise(resolve => { resolveNew = resolve }))
    const old = wrapper.vm.previewPackage()
    wrapper.vm.options.suffix = '_new'
    const current = wrapper.vm.previewPackage()
    resolveOld({ data: { packageDigest: 'old' } })
    await old
    expect(wrapper.vm.previewing).toBe(true)
    expect(wrapper.vm.preview).toBeNull()
    resolveNew({ data: { packageDigest: 'new' } })
    await current
    expect(wrapper.vm.preview.packageDigest).toBe('new')
    expect(wrapper.vm.hasCurrentPreview).toBe(true)
    wrapper.unmount()
  })

  test('导入失败后重新预检，避免直接重复写入', async () => {
    const wrapper = await previewGlobal()
    transferApi.importResourceTransfer.mockRejectedValueOnce(new Error('连接中断'))
    await wrapper.vm.applyPackage()
    expect(wrapper.vm.preview).toBeNull()
    expect(wrapper.vm.applying).toBe(false)
    expect(wrapper.vm.resultVisible).toBe(false)
    await wrapper.vm.applyPackage()
    expect(transferApi.importResourceTransfer).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  test('无摘要预检不能开启确认导入', async () => {
    const wrapper = await previewGlobal()
    transferApi.previewResourceTransfer.mockResolvedValueOnce({ data: {} })
    await wrapper.vm.previewPackage()
    expect(wrapper.vm.hasCurrentPreview).toBe(false)
    expect(wrapper.vm.previewError).toContain('摘要')
    wrapper.unmount()
  })

  test('项目搜索展示名称与编码而提交稳定 ID', async () => {
    listProjects.mockResolvedValueOnce({ data: {
      records: [{ id: 7, projectCode: 'credit', projectName: '授信项目' }], total: 21,
    } })
    const wrapper = shallowMount(OfflineTransfer)
    const result = await wrapper.vm.fetchTargetProjects({ query: '授信', pageNum: 2, pageSize: 20 })
    expect(listProjects).toHaveBeenCalledWith({ keyword: '授信', pageNum: 2, pageSize: 20 })
    expect(result).toEqual({ records: [{ id: 7, label: '授信项目 / credit' }], total: 21 })
    wrapper.vm.options.targetProjectId = result.records[0].id
    expect(wrapper.vm.normalizedOptions().targetProjectId).toBe(7)
    wrapper.unmount()
  })

  test('全局请求规范化不会夹带残留新建项目参数', () => {
    const wrapper = shallowMount(OfflineTransfer)
    Object.assign(wrapper.vm.options, { targetScope: 'GLOBAL', createProject: true, projectCode: 'old', projectName: 'old', targetProjectId: '3' })
    expect(wrapper.vm.normalizedOptions()).toMatchObject({
      createProject: false, projectCode: null, projectName: null, targetProjectId: null,
    })
    wrapper.unmount()
  })

  test('项目级导入可选择新建项目并保留冲突与规则发布策略', () => {
    const wrapper = shallowMount(OfflineTransfer)
    wrapper.vm.options = {
      ...wrapper.vm.options,
      targetScope: 'PROJECT',
      targetProjectId: '',
      createProject: true,
      projectCode: 'new_credit',
      projectName: '新授信项目',
      publishRules: true,
      variablePolicy: 'SUFFIX',
      resourcePolicy: 'OVERWRITE',
      suffix: '_staging',
    }

    expect(wrapper.vm.normalizedOptions()).toEqual(expect.objectContaining({
      targetScope: 'PROJECT',
      targetProjectId: null,
      projectCode: 'new_credit',
      projectName: '新授信项目',
      publishRules: true,
      variablePolicy: 'SUFFIX',
      resourcePolicy: 'OVERWRITE',
      suffix: '_staging',
    }))
    expect(wrapper.vm.validateTargetOptions()).toBe(true)
    wrapper.unmount()
  })

  test('项目级导入未绑定项目且未选择新建时阻止预检', () => {
    const wrapper = shallowMount(OfflineTransfer)
    wrapper.vm.options.targetScope = 'PROJECT'
    wrapper.vm.options.targetProjectId = ''
    wrapper.vm.options.createProject = false

    expect(wrapper.vm.validateTargetOptions()).toBe(false)
    wrapper.unmount()
  })

  test('切换为全局导入时清除隐藏的项目绑定参数', () => {
    const wrapper = shallowMount(OfflineTransfer)
    wrapper.vm.options = {
      ...wrapper.vm.options,
      targetScope: 'PROJECT',
      targetProjectId: '9',
      createProject: true,
      projectCode: 'new_credit',
      projectName: '新授信项目',
    }

    wrapper.vm.options.targetScope = 'GLOBAL'
    wrapper.vm.onTargetScopeChange('GLOBAL')

    expect(wrapper.vm.options).toMatchObject({
      targetScope: 'GLOBAL',
      targetProjectId: '',
      createProject: false,
      projectCode: '',
      projectName: '',
    })
    wrapper.unmount()
  })

  test('预检使用目标作用域和冲突选项发送配置包', async () => {
    transferApi.previewResourceTransfer.mockResolvedValue({
      data: { packageDigest: 'digest-1', conflictCount: 0, resources: [], roots: [] },
    })
    const wrapper = shallowMount(OfflineTransfer)
    const file = new File(['zip'], 'transfer.zip', { type: 'application/zip' })
    wrapper.vm.selectFile({ raw: file })
    wrapper.vm.options.targetScope = 'GLOBAL'

    await wrapper.vm.previewPackage()

    expect(transferApi.previewResourceTransfer).toHaveBeenCalledWith(
      file,
      expect.objectContaining({ targetScope: 'GLOBAL', targetProjectId: null })
    )
    expect(wrapper.vm.preview.packageDigest).toBe('digest-1')
    wrapper.unmount()
  })
})
