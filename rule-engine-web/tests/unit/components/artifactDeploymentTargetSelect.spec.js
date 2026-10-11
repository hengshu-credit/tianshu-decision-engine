import { mount, flushPromises } from '@test-utils'
import ArtifactDeploymentDialog from '@/components/artifact/ArtifactDeploymentDialog.vue'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import { getArtifactDeploymentOptions } from '@/api/artifact'
import { listProjects } from '@/api/project'
import { listDefinitions } from '@/api/definition'
import { listTransferResources } from '@/api/transfer'

vi.mock('@/api/artifact', () => ({ getArtifactDeploymentOptions: vi.fn() }))
vi.mock('@/api/project', () => ({ listProjects: vi.fn() }))
vi.mock('@/api/definition', () => ({ listDefinitions: vi.fn() }))
vi.mock('@/api/transfer', () => ({ listTransferResources: vi.fn() }))

const options = requirements => ({ data: { modelType: 'SCORE', requirements: requirements || [] } })
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
const mountDialog = () => mount(ArtifactDeploymentDialog, { props: { modelValue: true, artifactId: 7 } })
const picker = (wrapper, name) => wrapper.findAllComponents(RemoteFilterSelect).find(item => item.attributes('data-testid') === 'artifact-target-' + name)

beforeEach(() => {
  vi.clearAllMocks()
  getArtifactDeploymentOptions.mockReset().mockImplementation(() => Promise.resolve(options()))
  listProjects.mockReset().mockResolvedValue({ data: { records: [], total: 0 } })
  listDefinitions.mockReset().mockResolvedValue({ data: { records: [], total: 0 } })
  listTransferResources.mockReset().mockResolvedValue({ data: { records: [], total: 0 } })
})

test('项目按名称编码远程搜索并保留所选真实字符串ID，不允许手输名称当ID', async () => {
  const id = '9007199254740993'
  listProjects.mockResolvedValue({ data: { records: [{ id, projectName: '信贷项目', projectCode: 'KEEP_Project_Code', status: 0 }], total: 41 } })
  const wrapper = mountDialog()
  try {
    await flushPromises()
    const select = picker(wrapper, 'project')
    expect(select).toBeDefined()
    expect(select.props('allowFreeInput')).toBe(false)
    expect(select.props('optionValueKey')).toBe('id')
    const result = await select.props('fetchOptions')({ query: '信贷', pageNum: 2, pageSize: 20 })
    expect(listProjects).toHaveBeenCalledWith({ keyword: '信贷', pageNum: 2, pageSize: 20 })
    expect(result.records[0]).toMatchObject({ id })
    expect(result.records[0].label).toContain('信贷项目')
    expect(result.records[0].label).toContain('KEEP_Project_Code')
    expect(result.total).toBe(41)
    select.vm.$emit('update:value', id)
    wrapper.vm.targetMode = 'create'
    Object.assign(wrapper.vm.form, { environmentCode: 'uat', targetRuleCode: 'My_CODE', targetRuleName: '目标评分' })
    await flushPromises()
    wrapper.vm.submit()
    expect(wrapper.emitted('deploy')[0][0]).toMatchObject({ createRule: true, targetProjectId: id, targetRuleCode: 'My_CODE', targetModelType: 'SCORE' })
  } finally { wrapper.unmount() }
})

test('目标规则按项目和制品类型分页搜索，保留草稿但不展示删除、跨项目或异类规则', async () => {
  const wrapper = mountDialog()
  try {
    wrapper.vm.form.targetProjectId = 9
    wrapper.vm.form.environmentCode = 'uat'
    await flushPromises()
    const select = picker(wrapper, 'rule')
    expect(select).toBeDefined()
    listDefinitions.mockResolvedValue({ data: { records: [
      { id: 31, projectId: 9, scope: 'PROJECT', status: 0, modelType: 'SCORE', ruleName: '相同名称', ruleCode: 'draft' },
      { id: '9007199254740993', projectId: 9, scope: 'PROJECT', status: 1, modelType: 'SCORE', ruleName: '相同名称', ruleCode: 'published' },
      { id: 33, projectId: 9, scope: 'PROJECT', status: -1, modelType: 'SCORE' },
      { id: 34, projectId: 9, scope: 'PROJECT', status: 1, modelType: 'FLOW' },
      { id: 35, projectId: 10, scope: 'PROJECT', status: 1, modelType: 'SCORE' },
      { id: 36, projectId: 0, scope: 'GLOBAL', status: 1, modelType: 'SCORE' },
    ], total: 42 } })
    const result = await select.props('fetchOptions')({ query: '评分', pageNum: 2, pageSize: 20 })
    expect(listDefinitions).toHaveBeenCalledWith({ keyword: '评分', pageNum: 2, pageSize: 20, scope: 'PROJECT', projectId: 9, modelType: 'SCORE' })
    expect(result.records.map(item => item.id)).toEqual([31, '9007199254740993'])
    expect(result.records[0].label).toContain('相同名称')
    expect(result.records[0].label).toContain('draft')
    select.vm.$emit('update:value', result.records[1].id)
    await flushPromises()
    wrapper.vm.submit()
    expect(wrapper.emitted('deploy')[0][0]).toMatchObject({ createRule: false, targetDefinitionId: '9007199254740993' })
    expect(wrapper.emitted('deploy')[0][0].targetProjectId).toBe(9)
    expect(wrapper.emitted('deploy')[0][0].targetRuleCode).toBeUndefined()
  } finally { wrapper.unmount() }
})

test('关闭未选择目标的错误提示不会污染重开，切换全局后返回项目必须重新选择', async () => {
  const wrapper = mountDialog()
  try {
    await flushPromises()
    wrapper.vm.projectOptionsError = '上一次失败'
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true }); await flushPromises()
    expect(wrapper.vm.projectOptionsError).toBe('')
    wrapper.vm.form.targetProjectId = 9
    wrapper.vm.targetScope = 'GLOBAL'
    await flushPromises()
    expect(wrapper.vm.form.targetProjectId).toBeNull()
    wrapper.vm.targetScope = 'PROJECT'
    await flushPromises()
    expect(wrapper.vm.targetContextReady).toBe(false)
    expect(wrapper.vm.form.targetDefinitionId).toBeNull()
  } finally { wrapper.unmount() }
})

test('创建目标中项目→全局→项目只保留一组编码名称，切回现有目标不残留创建表单', async () => {
  const wrapper = mountDialog()
  try {
    wrapper.vm.targetMode = 'create'
    wrapper.vm.form.targetProjectId = 9
    wrapper.vm.form.targetRuleCode = 'Keep_User_Code'
    wrapper.vm.form.targetRuleName = '客户目标名称'
    await flushPromises()
    const count = label => wrapper.findAll(`[label="${label}"]`).length
    expect(count('目标规则编码')).toBe(1)
    expect(count('目标规则名称')).toBe(1)
    wrapper.vm.targetScope = 'GLOBAL'; await flushPromises()
    expect(count('目标项目')).toBe(0)
    expect(count('目标规则编码')).toBe(1)
    wrapper.vm.targetScope = 'PROJECT'; await flushPromises()
    wrapper.vm.form.targetProjectId = 9; await flushPromises()
    expect.soft(count('目标规则编码')).toBe(1)
    expect.soft(count('目标规则名称')).toBe(1)
    expect(count('目标项目')).toBe(1)
    expect(wrapper.vm.form.targetRuleCode).toBe('Keep_User_Code')
    expect(wrapper.vm.form.targetRuleName).toBe('客户目标名称')
    wrapper.vm.targetMode = 'existing'; await flushPromises()
    expect(count('目标规则编码')).toBe(0)
    expect(count('目标规则名称')).toBe(0)
    expect(count('目标规则')).toBe(1)
    wrapper.vm.form.targetProjectId = 10; await flushPromises()
    wrapper.vm.targetScope = 'GLOBAL'; await flushPromises()
    wrapper.vm.targetScope = 'PROJECT'; await flushPromises()
    expect(count('目标规则')).toBe(1)
    expect(count('目标规则编码')).toBe(0)
  } finally { wrapper.unmount() }
})

test('全局必须显式选择，新建发项目0；未选项目不能被当作全局', async () => {
  const wrapper = mountDialog()
  try {
    wrapper.vm.targetMode = 'create'
    Object.assign(wrapper.vm.form, { environmentCode: 'uat', targetRuleCode: 'GLOBAL_Keep', targetRuleName: '全局评分' })
    await flushPromises()
    expect(wrapper.vm.ready).toBe(false)
    wrapper.vm.targetScope = 'GLOBAL'
    await flushPromises()
    expect(wrapper.vm.ready).toBe(true)
    expect(picker(wrapper, 'project')).toBeUndefined()
    expect(getArtifactDeploymentOptions).toHaveBeenLastCalledWith(7, 0)
    wrapper.vm.submit()
    expect(wrapper.emitted('deploy')[0][0].targetProjectId).toBe(0)
    wrapper.vm.targetMode = 'existing'
    await flushPromises()
    await picker(wrapper, 'rule').props('fetchOptions')({ query: '全局', pageNum: 1, pageSize: 20 })
    expect(listDefinitions).toHaveBeenLastCalledWith({ keyword: '全局', pageNum: 1, pageSize: 20, modelType: 'SCORE', scope: 'GLOBAL', projectId: 0 })
  } finally { wrapper.unmount() }
})

test('改变项目立即清除现有规则及绑定，旧项目搜索迟到不能填入新项目', async () => {
  getArtifactDeploymentOptions.mockImplementation(() => Promise.resolve(options([{ componentId: 'API:5', resourceType: 'EXTERNAL_API', candidates: [] }])))
  const oldSearch = deferred()
  listTransferResources.mockReturnValue(oldSearch.promise)
  const wrapper = mountDialog()
  try {
    wrapper.vm.form.targetProjectId = 9
    await flushPromises()
    wrapper.vm.form.targetDefinitionId = 31
    wrapper.vm.form.bindings = { 'API:5': 101 }
    const search = wrapper.vm.searchBindingCandidates(wrapper.vm.bindingAssociations[0])
    wrapper.vm.form.targetProjectId = 10
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.form.targetDefinitionId).toBeNull()
    expect(wrapper.vm.form.bindings).toEqual({})
    await flushPromises()
    oldSearch.resolve({ data: { records: [{ id: 101, projectId: 9, code: 'old', displayName: '旧项目资源' }] } })
    await search
    expect(wrapper.vm.options.requirements[0].candidates).toEqual([])
  } finally { wrapper.unmount() }
})

test('关闭清空目标并丢弃迟到绑定失败，重开不带上次规则', async () => {
  getArtifactDeploymentOptions.mockResolvedValue(options([{ componentId: 'API:5', resourceType: 'EXTERNAL_API', candidates: [] }]))
  const old = deferred()
  listTransferResources.mockReturnValue(old.promise)
  const wrapper = mountDialog()
  try {
    wrapper.vm.form.targetProjectId = 9
    await flushPromises()
    wrapper.vm.form.targetDefinitionId = 31
    wrapper.vm.form.bindings = { 'API:5': 101 }
    const search = wrapper.vm.searchBindingCandidates(wrapper.vm.bindingAssociations[0])
    await wrapper.setProps({ modelValue: false })
    expect(wrapper.vm.form.targetDefinitionId).toBeNull()
    expect(wrapper.vm.form.targetProjectId).toBeNull()
    expect(wrapper.vm.form.bindings).toEqual({})
    old.reject(new Error('旧请求失败'))
    await search
    expect(wrapper.vm.$message.error).not.toHaveBeenCalled()
    await wrapper.setProps({ modelValue: true }); await flushPromises()
    expect(wrapper.vm.form.targetDefinitionId).toBeNull()
  } finally { wrapper.unmount() }
})

test('切换制品作废旧规则选择和候选，模型类型以新包为准', async () => {
  const wrapper = mountDialog()
  try {
    wrapper.vm.form.targetProjectId = 9
    await flushPromises()
    wrapper.vm.form.targetDefinitionId = 31
    wrapper.vm.form.bindings = { 'API:5': 101 }
    const old = deferred()
    listDefinitions.mockReturnValueOnce(old.promise)
    const oldPicker = picker(wrapper, 'rule')
    expect(oldPicker).toBeDefined()
    const search = oldPicker.props('fetchOptions')({ query: '旧', pageNum: 1, pageSize: 20 })
    getArtifactDeploymentOptions.mockResolvedValue({ data: { modelType: 'FLOW', requirements: [] } })
    await wrapper.setProps({ artifactId: 8 }); await flushPromises()
    expect(wrapper.vm.form.targetDefinitionId).toBeNull()
    expect(wrapper.vm.form.bindings).toEqual({})
    old.resolve({ data: { records: [{ id: 31, modelType: 'SCORE', projectId: 9, scope: 'PROJECT', status: 0 }], total: 1 } })
    expect((await search).records).toEqual([])
    expect(wrapper.vm.packageModelType).toBe('FLOW')
  } finally { wrapper.unmount() }
})
