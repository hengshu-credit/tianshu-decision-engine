import { mount, flushPromises } from '@test-utils'
import varPickerMixin from '@/mixins/varPickerMixin'
import * as definition from '@/api/definition'
import * as variable from '@/api/variable'
import * as object from '@/api/dataObject'
import * as model from '@/api/model'
import * as fn from '@/api/function'

function catalog(id, code, extra = {}) {
  return { status: 'AVAILABLE', referenceNamespace: 'ARTIFACT_SOURCE', imported: false, localEditingAllowed: true,
    executionIdentity: { definitionId: '30', sourceType: 'VERSION', sourceId: String(id) },
    variables: [{ id: '7', varCode: code, scriptName: code, varLabel: '冻结枚举', varType: 'ENUM', varSource: 'INPUT', valueRange: '["a,b",""]' }],
    objectTree: [], models: [], functions: [], rules: [], apis: [], lists: [], diagnostics: [], ...extra }
}
function mountCatalog() {
  return mount({ mixins: [varPickerMixin], template: '<div />',
    computed: { usesDesignerReferenceCatalog: () => true },
    data: () => ({ definitionId: '30', viewRefreshToken: 1, draftGuardActive: true, contentLoaded: false })
  }, { mocks: { $route: { params: { id: '30' } } } })
}
beforeEach(() => {
  vi.clearAllMocks()
  definition.getDefinition.mockReset().mockResolvedValue({ data: { id: '30', projectId: '1', ruleName: '年龄判断规则' } })
  variable.listVariablesByProject.mockResolvedValue({ data: [] })
  object.getVariableTree.mockResolvedValue({ data: [] })
  model.listAllModelsByProject.mockResolvedValue({ data: [] })
  fn.listAllFunctionsByProject.mockResolvedValue({ data: [] })
})

function titleRoute(id) {
  return { path: `/designer/script/${id}`, fullPath: `/designer/script/${id}?sourceType=VERSION&sourceId=81`, params: { id: String(id) } }
}

test('冻结目录加载独立补齐规则页签名，迟到的当前定义不补写冻结引用', async () => {
  let release
  definition.getDefinition.mockReturnValueOnce(new Promise(resolve => { release = resolve }))
  definition.getRuleReferenceCatalog.mockResolvedValueOnce({ data: catalog(81, 'Frozen_A') })
  const wrapper = mountCatalog()
  wrapper.vm.$route = titleRoute(30)
  wrapper.vm.$store = { getters: { 'workspaceTabs/tabs': [] }, dispatch: vi.fn() }
  try {
    await wrapper.vm.loadDesignerReferenceCatalog({ state: 'VERSION', id: '81' })
    expect(wrapper.vm.referenceCatalogMode).toBe('FROZEN')
    expect(wrapper.vm.projectRefs[0].refCode).toBe('Frozen_A')
    release({ data: { id: '30', projectId: '999', ruleName: '年龄判断规则', inputFields: [{ varId: '7', scriptName: 'Live_B' }] } })
    await flushPromises()
    expect(wrapper.vm.$store.dispatch).toHaveBeenCalledWith('workspaceTabs/updateDetailTitle', {
      fullPath: titleRoute(30).fullPath, path: titleRoute(30).path, detailTitle: '年龄判断规则',
    })
    expect(definition.getDefinition).toHaveBeenCalledWith('30')
    expect(wrapper.vm.projectIdForRefs).toBeNull()
    expect(wrapper.vm.projectRefs[0].refCode).toBe('Frozen_A')
    expect(wrapper.vm.projectRefs[0].refLabel.label).toBe('冻结枚举')
    for (const api of [variable.listVariablesByProject, object.getVariableTree, model.listAllModelsByProject, fn.listAllFunctionsByProject]) expect(api).not.toHaveBeenCalled()
  } finally { release?.({ data: null }); wrapper.unmount() }
})

test('上一规则迟到的标题响应不能改写新规则页签', async () => {
  let release
  definition.getDefinition.mockReturnValueOnce(new Promise(resolve => { release = resolve }))
    .mockResolvedValueOnce({ data: { id: '31', projectId: '1', ruleName: '新规则名称' } })
  definition.getRuleReferenceCatalog.mockResolvedValueOnce({ data: catalog(81, 'OldFrozen') })
    .mockResolvedValueOnce({ data: catalog(82, 'NewFrozen', { executionIdentity: { definitionId: '31', sourceType: 'VERSION', sourceId: '82' } }) })
  const wrapper = mountCatalog()
  wrapper.vm.$route = titleRoute(30)
  wrapper.vm.$store = { getters: { 'workspaceTabs/tabs': [] }, dispatch: vi.fn() }
  try {
    await wrapper.vm.loadDesignerReferenceCatalog({ state: 'VERSION', id: '81' })
    wrapper.vm.definitionId = '31'; wrapper.vm.$route = titleRoute(31); wrapper.vm.viewRefreshToken++
    await wrapper.vm.loadDesignerReferenceCatalog({ state: 'VERSION', id: '82' })
    release({ data: { id: '30', projectId: '999', ruleName: '旧规则迟到名称' } }); await flushPromises()
    expect(wrapper.vm.$store.dispatch.mock.calls).toEqual([['workspaceTabs/updateDetailTitle', {
      fullPath: titleRoute(31).fullPath, path: titleRoute(31).path, detailTitle: '新规则名称',
    }]])
    expect(wrapper.vm.projectRefs[0].refCode).toBe('NewFrozen')
  } finally { release?.({ data: null }); wrapper.unmount() }
})

test('LIVE目录与页签名复用一次定义查询，标题失败不会破坏冻结目录', async () => {
  definition.getRuleReferenceCatalog.mockResolvedValueOnce({ data: catalog(81, 'Frozen_A', { referenceNamespace: 'LOCAL' }) })
  const wrapper = mountCatalog()
  wrapper.vm.$route = titleRoute(30)
  wrapper.vm.$store = { getters: { 'workspaceTabs/tabs': [] }, dispatch: vi.fn() }
  try {
    await wrapper.vm.loadDesignerReferenceCatalog({ state: 'VERSION', id: '81' })
    expect(wrapper.vm.referenceCatalogMode).toBe('LIVE')
    expect(definition.getDefinition).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.$store.dispatch).toHaveBeenCalledTimes(1)
    definition.getDefinition.mockRejectedValueOnce(new Error('标题暂不可用'))
    definition.getRuleReferenceCatalog.mockResolvedValueOnce({ data: catalog(82, 'Frozen_B') })
    await wrapper.vm.loadDesignerReferenceCatalog({ state: 'VERSION', id: '82' }); await flushPromises()
    expect(wrapper.vm.referenceCatalogMode).toBe('FROZEN')
    expect(wrapper.vm.projectRefs[0].refCode).toBe('Frozen_B')
    expect(wrapper.vm.referenceCatalogError).toBe('')
  } finally { wrapper.unmount() }
})

test.each([0, '0', null, undefined])('全局规则 projectId=%s 从空历史目录切换后通过0加载当前字段', async projectId => {
  definition.getDefinition.mockResolvedValue({ data: { id: '30', scope: 'GLOBAL', projectId } })
  definition.getRuleReferenceCatalog.mockResolvedValue({ data: catalog(81, '', { variables: [] }) })
  variable.listVariablesByProject.mockResolvedValue({ data: [{ id: '9', varCode: 'Global_Age', varLabel: '全局年龄', varType: 'NUMBER', varSource: 'INPUT' }] })
  const wrapper = mountCatalog()
  try {
    const view = { state: 'VERSION', id: '81' }
    await wrapper.vm.loadDesignerReferenceCatalog(view)
    expect(wrapper.vm.referenceCatalogMode).toBe('FROZEN')
    expect(wrapper.vm.projectRefs).toHaveLength(0)
    expect(variable.listVariablesByProject).not.toHaveBeenCalled()
    await wrapper.vm.loadDesignerReferenceCatalog(view, { livePreview: true })
    expect(wrapper.vm.referenceCatalogMode).toBe('LIVE')
    expect(wrapper.vm.projectIdForRefs).toBe(0)
    expect(variable.listVariablesByProject).toHaveBeenCalledWith(0)
    expect(object.getVariableTree).toHaveBeenCalledWith(0)
    expect(wrapper.vm.varPickerOptions[0]).toMatchObject({ _varId: '9', _refType: 'VARIABLE', varCode: 'Global_Age' })
  } finally { wrapper.unmount() }
})

test('项目规则缺失项目ID时提示加载失败，不能当成空的当前目录', async () => {
  definition.getDefinition.mockResolvedValue({ data: { id: '30', scope: 'PROJECT', projectId: null } })
  definition.getRuleReferenceCatalog.mockResolvedValue({ data: catalog(81, '', { variables: [], referenceNamespace: 'LOCAL' }) })
  const wrapper = mountCatalog()
  try {
    await wrapper.vm.loadDesignerReferenceCatalog({ state: 'VERSION', id: '81' })
    expect(wrapper.vm.referenceCatalogMode).toBe('UNAVAILABLE')
    expect(wrapper.vm.referenceCatalogError).toContain('缺少所属项目')
    expect(variable.listVariablesByProject).not.toHaveBeenCalled()
  } finally { wrapper.unmount() }
})

test.each(['definition', 'variables'])('转当前资源时%s读取失败不能宣称LIVE或保留不完整目录', async failure => {
  definition.getRuleReferenceCatalog.mockResolvedValueOnce({ data: catalog(81, 'FrozenOnly') })
  const wrapper = mountCatalog()
  try {
    const view = { state: 'VERSION', id: '81' }
    await wrapper.vm.loadDesignerReferenceCatalog(view)
    expect(wrapper.vm.referenceCatalogMode).toBe('FROZEN')
    definition.getDefinition.mockResolvedValueOnce({ data: { id: '30', projectId: '1' } })
    if (failure === 'definition') definition.getDefinition.mockReset().mockRejectedValueOnce(new Error('当前规则读取失败'))
    else variable.listVariablesByProject.mockRejectedValueOnce(new Error('当前变量目录读取失败'))
    await wrapper.vm.loadDesignerReferenceCatalog(view, { livePreview: true })
    expect(wrapper.vm.referenceCatalogMode).toBe('UNAVAILABLE')
    expect(wrapper.vm.projectRefs).toEqual([])
    expect(wrapper.vm.varsLoadError).toBe(true)
    expect(wrapper.vm.referenceCatalogError).toContain('读取失败')
  } finally { wrapper.unmount() }
})

test('FROZEN枚举和名称完全来自目录，连同枚举选项也不读当前同号ID', async () => {
  definition.getRuleReferenceCatalog.mockResolvedValueOnce({ data: catalog(81, 'OriginalEnum', { displayLabels: { 'VARIABLE:7': '版本保存的业务标签' } }) })
  const wrapper = mountCatalog()
  await wrapper.vm.loadDesignerReferenceCatalog({ state: 'VERSION', id: '81' })
  expect(wrapper.vm.referenceCatalogMode).toBe('FROZEN')
  expect(wrapper.vm.getVarOptions('OriginalEnum')).toEqual([{ value: 'a,b', label: 'a,b' }, { value: '', label: '' }])
  expect(wrapper.vm.projectRefs[0].refLabel.label).toBe('版本保存的业务标签')
  // Current rule metadata is permitted only for the workspace title, never for frozen resource names.
  expect(definition.getDefinition).toHaveBeenCalledTimes(1)
  expect(wrapper.vm.projectIdForRefs).toBeNull()
  for (const api of [variable.listVariablesByProject, variable.getVariableOptions, object.getVariableTree,
    object.getDataObjectFieldOptions, model.listAllModelsByProject, fn.listAllFunctionsByProject]) expect(api).not.toHaveBeenCalled()
  wrapper.unmount()
})

test('切来源后旧目录迟到不能覆盖新目录、错误或项目上下文', async () => {
  let release
  definition.getRuleReferenceCatalog.mockReturnValueOnce(new Promise(resolve => { release = resolve }))
    .mockResolvedValueOnce({ data: catalog(82, 'SecondSource') })
  const wrapper = mountCatalog()
  wrapper.vm.projectIdForRefs = 999
  const old = wrapper.vm.loadDesignerReferenceCatalog({ state: 'VERSION', id: '81' })
  await wrapper.vm.loadDesignerReferenceCatalog({ state: 'VERSION', id: '82' })
  release({ data: catalog(81, 'WrongOldSource') }); await old
  expect(wrapper.vm.projectRefs.map(ref => ref.refCode)).toEqual(['SecondSource'])
  expect(wrapper.vm.referenceCatalogKey).toContain('VERSION:82')
  expect(wrapper.vm.projectIdForRefs).toBeNull()
  expect(wrapper.vm.varsLoadError).toBe(false)
  wrapper.unmount()
})

test.each(['UNAVAILABLE', 'INVALID'])('%s目录保留来源诊断且清空引用，不用live补齐', async status => {
  definition.getRuleReferenceCatalog.mockResolvedValueOnce({ data: catalog(81, 'NotTrusted', { status, localEditingAllowed: false, diagnostics: ['原制品无法验证'] }) })
  const wrapper = mountCatalog()
  await wrapper.vm.loadDesignerReferenceCatalog({ state: 'VERSION', id: '81' })
  expect(wrapper.vm.referenceCatalogMode).toBe('UNAVAILABLE')
  expect(wrapper.vm.projectRefs).toEqual([])
  expect(wrapper.vm.referenceCatalogError).toContain('原制品无法验证')
  expect(variable.listVariablesByProject).not.toHaveBeenCalled()
  wrapper.unmount()
})

test('目录身份与所选来源不符时拒绝，离开页面后响应也不能写回', async () => {
  definition.getRuleReferenceCatalog.mockResolvedValueOnce({ data: catalog(82, 'WrongSource') })
  const wrapper = mountCatalog()
  await wrapper.vm.loadDesignerReferenceCatalog({ state: 'VERSION', id: '81' })
  expect(wrapper.vm.projectRefs).toEqual([])
  expect(wrapper.vm.referenceCatalogMode).toBe('UNAVAILABLE')
  let release
  definition.getRuleReferenceCatalog.mockReturnValueOnce(new Promise(resolve => { release = resolve }))
  const pending = wrapper.vm.loadDesignerReferenceCatalog({ state: 'VERSION', id: '81' })
  wrapper.vm.draftGuardActive = false
  release({ data: catalog(81, 'Late') }); await pending; await flushPromises()
  expect(wrapper.vm.projectRefs).toEqual([])
  wrapper.unmount()
})
