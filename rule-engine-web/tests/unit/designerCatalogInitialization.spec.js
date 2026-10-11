import { shallowMount, flushPromises } from '@test-utils'
import { nextTick } from 'vue'
import DecisionTable from '@/views/designer/DecisionTable.vue'
import RuleSet from '@/views/designer/RuleSet.vue'
import * as definition from '@/api/definition'
import * as variable from '@/api/variable'
import * as object from '@/api/dataObject'
import * as model from '@/api/model'
import * as fn from '@/api/function'
import * as list from '@/api/ruleList'
import * as datasource from '@/api/datasource'

function catalog(extra = {}) {
  return { status: 'AVAILABLE', referenceNamespace: 'ARTIFACT_SOURCE', localEditingAllowed: true,
    executionIdentity: { definitionId: '30', sourceType: 'VERSION', sourceId: '81' },
    variables: [], objectTree: [], models: [], functions: [], rules: [], apis: [], lists: [], diagnostics: [], ...extra }
}

beforeEach(() => {
  vi.clearAllMocks()
  definition.listRuleRevisions.mockResolvedValue({ data: [] })
  definition.listPublishedVersions.mockResolvedValue({ data: [{ id: '81', version: 1 }] })
  definition.getVersionById.mockResolvedValue({ data: { id: '81', definitionId: '30', version: 1, modelJson: '{}' } })
  definition.getDefinition.mockResolvedValue({ data: { id: '30', projectId: '1' } })
  definition.listProjectDefinitions.mockResolvedValue({ data: { records: [] } })
  variable.listVariablesByProject.mockResolvedValue({ data: [] })
  object.getVariableTree.mockResolvedValue({ data: [] })
  model.listAllModelsByProject.mockResolvedValue({ data: [] })
  fn.listAllFunctionsByProject.mockResolvedValue({ data: [] })
  list.listLibraries.mockResolvedValue({ data: { records: [] } })
  datasource.listApiConfigs.mockResolvedValue({ data: { records: [] } })
})

function mountDesigner(component, route) {
  return shallowMount(component, { mocks: {
    $route: { path: `/designer/${route}/30`, params: { id: '30' }, query: {} },
    $router: { replace: vi.fn(), push: vi.fn() },
  } })
}

test.each([['table', DecisionTable], ['ruleset', RuleSet]])('%s创建时重复请求目录不能丢失首次修改跟踪', async (route, component) => {
  let releaseDuplicate
  const duplicate = new Promise(resolve => { releaseDuplicate = resolve })
  definition.getRuleReferenceCatalog.mockReset().mockResolvedValueOnce({ data: catalog() }).mockReturnValueOnce(duplicate)
  const wrapper = mountDesigner(component, route)
  try {
    // Actual created hooks start loadProjectVars and loadContent together. A redundant second
    // catalog request stays pending until the real page's nextTick tracking initialization ran.
    await flushPromises(); await nextTick()
    expect(wrapper.vm.contentLoaded).toBe(true)
    releaseDuplicate({ data: catalog() })
    await flushPromises(); await nextTick()
    expect(wrapper.vm.referenceCatalogMode).toBe('FROZEN')
    expect(wrapper.vm.model.rules).toHaveLength(0)
    const baseline = wrapper.vm.designerBaselineFingerprint
    wrapper.vm.addRule()
    await nextTick(); await flushPromises()
    expect(wrapper.vm.model.rules).toHaveLength(1)
    expect(wrapper.vm.designerActionState).toBe('DIRTY')
    expect(wrapper.vm.designerDraftTrackingReady).toBe(true)
    expect(wrapper.vm.designerHasUnsavedChanges).toBe(true)
    expect(wrapper.vm.designerBaselineFingerprint).toBe(baseline)
    expect(wrapper.vm.referenceCatalogMode).toBe('LIVE')
    expect(wrapper.vm.designerTestExecutionSource).toMatchObject({ sourceType: 'PREVIEW', baseSourceType: 'VERSION', baseSourceId: '81' })
  } finally { releaseDuplicate({ data: catalog() }); wrapper.unmount() }
})

test('显式刷新仍重新验证目录并阻止使用已失效的来源', async () => {
  definition.getRuleReferenceCatalog.mockReset().mockResolvedValue({ data: catalog() })
  const wrapper = mountDesigner(DecisionTable, 'table')
  try {
    await flushPromises(); await nextTick()
    const original = wrapper.vm.serializeDesignerDraft()
    definition.getRuleReferenceCatalog.mockResolvedValueOnce({ data: catalog({ status: 'UNAVAILABLE', localEditingAllowed: false, diagnostics: ['制品已不可验证'] }) })
    await wrapper.vm.refreshProjectRefs()
    expect(wrapper.vm.referenceCatalogMode).toBe('UNAVAILABLE')
    expect(wrapper.vm.canEditDraft).toBe(false)
    expect(wrapper.vm.designerCanTest).toBe(false)
    expect(wrapper.vm.referenceCatalogError).toContain('不可验证')
    expect(wrapper.vm.serializeDesignerDraft()).toBe(original)
  } finally { wrapper.unmount() }
})
