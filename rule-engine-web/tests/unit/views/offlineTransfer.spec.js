import { shallowMount } from '@test-utils'
import { nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

vi.mock('@/api/transfer', () => ({
  exportResourceTransfer: vi.fn(),
  listTransferResources: vi.fn(),
  previewResourceTransfer: vi.fn(),
  importResourceTransfer: vi.fn(),
  listTransferLogs: vi.fn(),
  getTransferLog: vi.fn(),
  getTransferLogLineage: vi.fn(),
}))

vi.mock('@/api/lineage', () => ({
  getLineageGraph: vi.fn(),
}))

import * as transferApi from '@/api/transfer'
import * as lineageApi from '@/api/lineage'
import { listProjects } from '@/api/project'
import OfflineTransfer from '@/views/transfer/OfflineTransfer.vue'

describe('OfflineTransfer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    transferApi.listTransferResources.mockReset().mockResolvedValue({ data: { records: [], total: 0 } })
    transferApi.previewResourceTransfer.mockReset().mockResolvedValue({
      data: { packageDigest: 'digest-1', conflictCount: 0, resources: [], roots: [] },
    })
    transferApi.importResourceTransfer.mockReset().mockResolvedValue({ data: { status: 'APPLIED' } })
    transferApi.listTransferLogs.mockReset().mockResolvedValue({ data: { records: [], total: 0 } })
    transferApi.getTransferLog.mockReset().mockResolvedValue({ data: null })
    transferApi.getTransferLogLineage.mockReset().mockResolvedValue({ data: { nodes: [], edges: [] } })
    lineageApi.getLineageGraph.mockReset().mockResolvedValue({ data: { nodes: [], edges: [] } })
    ElMessageBox.confirm.mockReset().mockResolvedValue('confirm')
  })

  test('导出根资源使用名称编码候选并保留稳定 ID', async () => {
    const wrapper = shallowMount(OfflineTransfer)
    const root = wrapper.vm.roots[0]
    transferApi.listTransferResources.mockResolvedValueOnce({ data: { records: [
      { id: 101, code: 'Risk_Mixed', label: '授信判断', displayName: '授信判断 (Risk_Mixed)', scope: 'GLOBAL' },
    ], total: 1 } })
    const result = await wrapper.vm.fetchRootOptions(root, { query: '授信', pageNum: 1, pageSize: 20 })
    expect(transferApi.listTransferResources).toHaveBeenCalledWith({ nodeType: 'RULE', keyword: '授信', pageNum: 1, pageSize: 20 })
    expect(result.records).toEqual([{ id: 101, label: '授信判断 (Risk_Mixed) · 全局' }])
    expect(wrapper.find('input[placeholder="资源 ID"]').exists()).toBe(false)
    const selector = wrapper.findAllComponents({ name: 'RemoteFilterSelect' })[0]
    selector.vm.$emit('update:value', result.records[0].id)
    await nextTick()
    expect(root.resourceId).toBe(101)
    wrapper.unmount()
  })

  test.each([
    ['PROJECT', 'PROJECT'], ['RULE', 'RULE'], ['VARIABLE', 'VARIABLE'], ['DATA_OBJECT', 'DATA_OBJECT'],
    ['FUNCTION', 'FUNCTION'], ['MODEL', 'MODEL'], ['EXPERIMENT', 'EXPERIMENT'],
    ['DATABASE', 'DB'], ['EXTERNAL_DATASOURCE', 'DATASOURCE'], ['EXTERNAL_API', 'API'], ['LIST_LIBRARY', 'LIST'],
  ])('%s 检索转换类型但不转换名称编码，支持翻页并区分项目归属', async (resourceType, nodeType) => {
    const wrapper = shallowMount(OfflineTransfer)
    const root = wrapper.vm.roots[0]
    root.resourceType = resourceType
    transferApi.listTransferResources.mockResolvedValueOnce({ data: { records: [
      { id: 31, displayName: '同名配置 (Mixed_Case)', scope: 'PROJECT', projectId: 3, projectName: '授信项目', projectCode: 'credit' },
    ], total: 41 } })
    const result = await wrapper.vm.fetchRootOptions(root, { query: 'Mixed_Case', pageNum: 2, pageSize: 20 })
    expect(transferApi.listTransferResources).toHaveBeenCalledWith({ nodeType, keyword: 'Mixed_Case', pageNum: 2, pageSize: 20 })
    expect(result.total).toBe(41)
    expect(result.records[0]).toEqual({ id: 31, label: resourceType === 'PROJECT'
      ? '同名配置 (Mixed_Case)' : '同名配置 (Mixed_Case) · 授信项目 / credit' })
    wrapper.unmount()
  })

  test('资源类型切换清除旧 ID 并重建候选选择器', async () => {
    const wrapper = shallowMount(OfflineTransfer)
    const root = wrapper.vm.roots[0]
    root.resourceId = 101
    const oldSelector = wrapper.findAllComponents({ name: 'RemoteFilterSelect' })[0].vm
    wrapper.findComponent('.root-type').vm.$emit('update:modelValue', 'MODEL')
    wrapper.findComponent('.root-type').vm.$emit('change', 'MODEL')
    await nextTick()
    expect(root.resourceType).toBe('MODEL')
    expect(root.resourceId).toBe('')
    expect(wrapper.findAllComponents({ name: 'RemoteFilterSelect' })[0].vm).not.toBe(oldSelector)
    wrapper.unmount()
  })

  test('删除中间根资源后其他行保持身份，新行不复用旧候选', () => {
    const wrapper = shallowMount(OfflineTransfer)
    wrapper.vm.addRoot()
    wrapper.vm.addRoot()
    const keys = wrapper.vm.roots.map(root => root.key)
    expect(new Set(keys).size).toBe(3)
    wrapper.vm.roots[2].resourceId = 23
    wrapper.vm.removeRoot(1)
    wrapper.vm.addRoot()
    expect(wrapper.vm.roots[1]).toMatchObject({ key: keys[2], resourceId: 23 })
    expect(keys).not.toContain(wrapper.vm.roots[2].key)
    wrapper.unmount()
  })

  test('同类型重复选择会清除当前选择，其他类型同 ID 可以保留', () => {
    const wrapper = shallowMount(OfflineTransfer)
    wrapper.vm.roots[0].resourceId = 12
    wrapper.vm.addRoot()
    const second = wrapper.vm.roots[1]
    second.resourceId = 12
    wrapper.vm.onRootSelectionChange(second)
    expect(second.resourceId).toBe('')
    expect(ElMessage.warning).toHaveBeenCalledWith('该资源已添加，请选择其他资源')
    second.resourceType = 'MODEL'
    second.resourceId = 12
    wrapper.vm.onRootSelectionChange(second)
    expect(second.resourceId).toBe(12)
    wrapper.unmount()
  })

  test('候选加载失败可重试，旧类型的迟到错误不污染新类型', async () => {
    const wrapper = shallowMount(OfflineTransfer)
    const root = wrapper.vm.roots[0]
    transferApi.listTransferResources.mockRejectedValueOnce(new Error('服务不可用'))
    await expect(wrapper.vm.fetchRootOptions(root, { query: '', pageNum: 1 })).rejects.toThrow('服务不可用')
    expect(root.loadError).toContain('重新展开')
    await wrapper.vm.fetchRootOptions(root, { query: 'risk', pageNum: 1 })
    expect(root.loadError).toBe('')
    let rejectOld
    transferApi.listTransferResources.mockReturnValueOnce(new Promise((resolve, reject) => { rejectOld = reject }))
    const pending = wrapper.vm.fetchRootOptions(root, { query: 'old', pageNum: 1 })
    root.resourceType = 'MODEL'
    wrapper.vm.onRootTypeChange(root)
    rejectOld(new Error('旧请求失败'))
    await expect(pending).rejects.toThrow('旧请求失败')
    expect(root.loadError).toBe('')
    wrapper.unmount()
  })

  test('未选择候选不能导出，导出失败展示可重试错误且释放忙状态', async () => {
    const wrapper = shallowMount(OfflineTransfer)
    await wrapper.vm.exportPackage()
    expect(transferApi.exportResourceTransfer).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('请为每一行选择要导出的资源')
    wrapper.vm.roots[0].resourceId = 101
    transferApi.exportResourceTransfer.mockRejectedValueOnce(new Error('配置包生成失败'))
    await wrapper.vm.exportPackage()
    expect(wrapper.vm.exportError).toBe('配置包生成失败')
    expect(wrapper.vm.exporting).toBe(false)
    wrapper.unmount()
  })

  test('关闭上游依赖选项时只导出当前选择的根资源', async () => {
    const wrapper = shallowMount(OfflineTransfer)
    wrapper.vm.roots[0].resourceId = 101
    wrapper.vm.exportOnlySelected = true
    transferApi.exportResourceTransfer.mockResolvedValueOnce({ data: new Blob(['zip']) })
    await wrapper.vm.exportPackage()
    expect(transferApi.exportResourceTransfer).toHaveBeenCalledWith(
      [{ resourceType: 'RULE', resourceId: 101 }],
      { includeDependencies: false },
    )
    wrapper.unmount()
  })

  test('选择导出根资源后按稳定 ID 请求血缘图', async () => {
    const wrapper = shallowMount(OfflineTransfer)
    wrapper.vm.roots[0].resourceType = 'RULE'
    wrapper.vm.roots[0].resourceId = 101
    await wrapper.vm.onRootSelectionChange(wrapper.vm.roots[0])
    expect(lineageApi.getLineageGraph).toHaveBeenCalledWith({
      nodeType: 'RULE', nodeId: 101, direction: 'ALL', maxDepth: 2,
    })
    wrapper.unmount()
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

  test('预检报告展示外部关联并把手工资源和字段映射放进导入选项', async () => {
    const wrapper = shallowMount(OfflineTransfer)
    wrapper.vm.selectFile({ raw: new File(['zip'], 'transfer.zip') })
    wrapper.vm.options.targetScope = 'GLOBAL'
    transferApi.previewResourceTransfer.mockResolvedValueOnce({ data: {
      packageDigest: 'digest-association',
      resources: [{ key: 'RULE:1', resourceCode: 'rule', resourceType: 'RULE' }],
      selectedResourceKeys: ['RULE:1'],
      roots: ['RULE:1'],
      associations: [{
        referenceKey: 'RULE:1|/content/@json/field|DATA_OBJECT:7|/fields/0/id',
        targetKey: 'DATA_OBJECT:7',
        targetResourceType: 'DATA_OBJECT',
        childPath: '/fields/0/id',
        candidates: [{ id: 101, fields: [{ id: 501 }] }],
      }],
    } })
    await wrapper.vm.previewPackage()
    wrapper.vm.resourceBindingsDraft = { 'DATA_OBJECT:7': 101 }
    wrapper.vm.fieldBindingsDraft = { 'RULE:1|/content/@json/field|DATA_OBJECT:7|/fields/0/id': 501 }
    const options = wrapper.vm.normalizedOptions()
    expect(options.resourceBindings).toEqual({ 'DATA_OBJECT:7': 101 })
    expect(options.fieldBindings).toEqual({ 'RULE:1|/content/@json/field|DATA_OBJECT:7|/fields/0/id': 501 })
    wrapper.unmount()
  })

  test('同码复用建议会进入逐项可编辑的资源动作', async () => {
    const wrapper = shallowMount(OfflineTransfer)
    wrapper.vm.selectFile({ raw: new File(['zip'], 'transfer.zip') })
    wrapper.vm.options.targetScope = 'GLOBAL'
    transferApi.previewResourceTransfer.mockResolvedValueOnce({ data: {
      packageDigest: 'digest-reuse',
      resources: [{ key: 'VARIABLE:1', resourceCode: 'score', resourceType: 'VARIABLE',
        recommendedAction: 'REUSE', selectedAction: 'REUSE', reviewable: true }],
      selectedResourceKeys: ['VARIABLE:1'], roots: ['VARIABLE:1'], associations: [],
    } })
    await wrapper.vm.previewPackage()
    expect(wrapper.vm.resourceActionsDraft).toEqual({ 'VARIABLE:1': 'REUSE' })
    wrapper.vm.resourceActionsDraft['VARIABLE:1'] = 'SUFFIX'
    expect(wrapper.vm.normalizedOptions().resourceActions).toEqual({ 'VARIABLE:1': 'SUFFIX' })
    wrapper.unmount()
  })

  test('查看迁移日志会同时请求实时血缘图', async () => {
    const wrapper = shallowMount(OfflineTransfer)
    transferApi.getTransferLog.mockResolvedValueOnce({ data: { id: 8, status: 'SUCCESS', contentJson: '{}' } })
    transferApi.getTransferLogLineage.mockResolvedValueOnce({ data: { nodes: [{ id: 'RULE:1' }], edges: [] } })
    await wrapper.vm.openLogDetail({ id: 8 })
    expect(transferApi.getTransferLogLineage).toHaveBeenCalledWith(8)
    expect(wrapper.vm.logLineage.nodes).toHaveLength(1)
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

  test('新建项目区域保留固定占位，不因切换目标模式重排整个表单', async () => {
    const wrapper = shallowMount(OfflineTransfer)
    const fields = wrapper.find('.project-create-fields')
    expect(fields.exists()).toBe(true)
    expect(fields.classes()).not.toContain('is-enabled')
    expect(fields.find('.project-create-fields__placeholder').exists()).toBe(true)

    wrapper.vm.options.targetScope = 'PROJECT'
    wrapper.vm.options.createProject = true
    await nextTick()
    expect(wrapper.find('.project-create-fields').classes()).toContain('is-enabled')
    expect(wrapper.find('.project-create-fields__inputs').exists()).toBe(true)
    expect(wrapper.find('.project-create-fields__placeholder').isVisible()).toBe(false)
    expect(wrapper.find('.import-options__policies').exists()).toBe(true)
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
