import { flushPromises, mount } from '@test-utils'
import * as definitionApi from '@/api/definition'
import * as variableApi from '@/api/variable'
import * as dataObjectApi from '@/api/dataObject'
import * as functionApi from '@/api/function'
import * as modelApi from '@/api/model'
import * as ruleListApi from '@/api/ruleList'

vi.mock('@logicflow/core', () => {
  class NodeModel {
    getNodeStyle() {
      return {}
    }
    getConnectedSourceRules() {
      return []
    }
  }
  class LogicFlow {
    constructor() {
      this.extension = {
        miniMap: { show: vi.fn(), hide: vi.fn() },
        selectionSelect: { open: vi.fn(), close: vi.fn() },
      }
      this.graphModel = { edges: [] }
      this.graph = { nodes: [], edges: [] }
    }
    register() {}
    addMenuConfig() {}
    on() {}
    render(graph) {
      this.graph = graph || { nodes: [], edges: [] }
    }
    addNode(node) {
      this.graph.nodes.push(node)
    }
    setDefaultEdgeType() {}
    getGraphData() {
      return this.graph
    }
    getTransform() {
      return { SCALE_X: 1, TRANSLATE_X: 0, TRANSLATE_Y: 0 }
    }
    destroy() {}
  }
  return {
    default: LogicFlow,
    h: vi.fn(),
    CircleNode: class {},
    CircleNodeModel: NodeModel,
    RectNode: class {},
    RectNodeModel: NodeModel,
    DiamondNode: class {},
    DiamondNodeModel: NodeModel,
    SelectionSelect: class {},
    Menu: class {},
    Snapshot: class {},
    DynamicGroup: class {},
    MiniMap: class {},
    Dagre: class {},
  }
})

vi.unmock('@/components/rule/RuleDraftReadOnly.vue')

const [
  { default: ScriptEditor },
  { default: DecisionTable },
  { default: DecisionTree },
  { default: DecisionFlow },
  { default: RuleSet },
  { default: CrossTable },
  { default: Scorecard },
  { default: AdvancedCrossTable },
  { default: AdvancedScorecard },
] = await Promise.all([
  import('@/views/designer/ScriptEditor.vue'),
  import('@/views/designer/DecisionTable.vue'),
  import('@/views/designer/DecisionTree.vue'),
  import('@/views/designer/DecisionFlow.vue'),
  import('@/views/designer/RuleSet.vue'),
  import('@/views/designer/CrossTable.vue'),
  import('@/views/designer/Scorecard.vue'),
  import('@/views/designer/AdvancedCrossTable.vue'),
  import('@/views/designer/AdvancedScorecard.vue'),
])

const { default: ScriptPanel } = await vi.importActual(
  '@/components/common/ScriptPanel.vue'
)

const designers = [
  ['ScriptEditor', ScriptEditor],
  ['DecisionTable', DecisionTable],
  ['DecisionTree', DecisionTree],
  ['DecisionFlow', DecisionFlow],
  ['RuleSet', RuleSet],
  ['CrossTable', CrossTable],
  ['Scorecard', Scorecard],
  ['AdvancedCrossTable', AdvancedCrossTable],
  ['AdvancedScorecard', AdvancedScorecard],
]

const designerToolbars = [
  ['ScriptEditor', ScriptEditor, '.se-header'],
  ['DecisionTable', DecisionTable, '.dt-header'],
  ['DecisionTree', DecisionTree, '.tree-toolbar'],
  ['DecisionFlow', DecisionFlow, '.flow-toolbar'],
  ['RuleSet', RuleSet, '.rs-header'],
  ['CrossTable', CrossTable, '.ct-header'],
  ['Scorecard', Scorecard, '.sc-header'],
  ['AdvancedCrossTable', AdvancedCrossTable, '.act-header'],
  ['AdvancedScorecard', AdvancedScorecard, '.asc-header'],
]

const explicitRevisionSources = [
  {
    name: 'ScriptEditor',
    component: ScriptEditor,
    model: { script: "result = 'revision-script-41'" },
    assertLoaded: (vm) => expect(vm.script).toBe("result = 'revision-script-41'"),
  },
  {
    name: 'DecisionTable',
    component: DecisionTable,
    model: { sourceMarker: 'revision-table-41', rules: [] },
    assertLoaded: (vm) => expect(vm.model.sourceMarker).toBe('revision-table-41'),
  },
  {
    name: 'DecisionTree',
    component: DecisionTree,
    model: {
      defaultEdgeLineType: 'polyline',
      logicflow: {
        nodes: [
          {
            id: 'revision-tree-41',
            type: 'script-task',
            x: 160,
            y: 120,
            properties: { actionData: [], sourceMarker: 'revision-tree-41' },
          },
        ],
        edges: [],
      },
    },
    assertLoaded: (vm) =>
      expect(vm.lf.getGraphData().nodes[0].properties.sourceMarker).toBe(
        'revision-tree-41'
      ),
  },
  {
    name: 'DecisionFlow',
    component: DecisionFlow,
    model: {
      defaultEdgeLineType: 'polyline',
      logicflow: {
        nodes: [
          {
            id: 'revision-flow-41',
            type: 'script-task',
            x: 160,
            y: 120,
            properties: { actionData: [], sourceMarker: 'revision-flow-41' },
          },
        ],
        edges: [],
      },
    },
    assertLoaded: (vm) =>
      expect(vm.lf.getGraphData().nodes[0].properties.sourceMarker).toBe(
        'revision-flow-41'
      ),
  },
  {
    name: 'RuleSet',
    component: RuleSet,
    model: { sourceMarker: 'revision-ruleset-41', executionMode: 'SERIAL', rules: [] },
    assertLoaded: (vm) => expect(vm.model.sourceMarker).toBe('revision-ruleset-41'),
  },
  {
    name: 'CrossTable',
    component: CrossTable,
    model: {
      sourceMarker: 'revision-cross-41',
      rowDimensions: [],
      colDimensions: [],
      cells: [],
    },
    assertLoaded: (vm) => expect(vm.model.sourceMarker).toBe('revision-cross-41'),
  },
  {
    name: 'Scorecard',
    component: Scorecard,
    model: { sourceMarker: 'revision-score-41', scoreItems: [], thresholds: [] },
    assertLoaded: (vm) => expect(vm.model.sourceMarker).toBe('revision-score-41'),
  },
  {
    name: 'AdvancedCrossTable',
    component: AdvancedCrossTable,
    model: {
      sourceMarker: 'revision-advanced-cross-41',
      rowDimensions: [],
      colDimensions: [],
      cells: [],
    },
    assertLoaded: (vm) =>
      expect(vm.model.sourceMarker).toBe('revision-advanced-cross-41'),
  },
  {
    name: 'AdvancedScorecard',
    component: AdvancedScorecard,
    model: {
      sourceMarker: 'revision-advanced-score-41',
      initialScore: 0,
      dimensionGroups: [],
      thresholds: [],
    },
    assertLoaded: (vm) =>
      expect(vm.model.sourceMarker).toBe('revision-advanced-score-41'),
  },
]

function configureQueryApis() {
  definitionApi.getDefinition.mockResolvedValue({
    data: { id: 30, projectId: null, scope: 'PROJECT' },
  })
  definitionApi.listProjectDefinitions.mockResolvedValue({ data: [] })
  variableApi.listVariablesByProject.mockResolvedValue({ data: [] })
  dataObjectApi.getVariableTree.mockResolvedValue({ data: [] })
  functionApi.listAllFunctionsByProject.mockResolvedValue({ data: [] })
  modelApi.listAllModelsByProject.mockResolvedValue({ data: [] })
  ruleListApi.listLibraries.mockResolvedValue({ data: [] })
}

function mountDesigner(component, query = {}, options = {}) {
  const wrapper = mount(component, {
    mocks: {
      $route: { params: { id: 30 }, query },
      $router: {
        push: vi.fn(),
        replace: vi.fn(),
        back: vi.fn(),
      },
      $alert: vi.fn(),
    },
    stubs: {
      MonacoEditor: true,
      DesignerTestDialog: true,
      OperandPicker: true,
      ConditionGroupEditor: true,
      ActionBlockEditor: true,
      EndNodeScopeDialog: true,
      FlowNodeAddMenu: true,
      GraphDesignerNavigator: true,
      ScriptPanel: true,
      ...(options.stubs || {}),
    },
    directives: options.directives,
  })
  wrapper.vm.requestDesignerChoice = async () => ({ action: 'save', saveMode: 'OVERWRITE' })
  return wrapper
}

describe('九类设计器草稿保护', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    configureQueryApis()
  })

  test.each(explicitRevisionSources)('$name 加载失败可原地重试精确版本，成功前不能保存', async ({ component, model, assertLoaded }) => {
    definitionApi.listRuleRevisions.mockResolvedValue({ data: [] })
    definitionApi.listPublishedVersions.mockResolvedValue({ data: [] })
    let resolveRetry
    definitionApi.getVersionById
      .mockRejectedValueOnce(new Error('读取失败'))
      .mockImplementationOnce(() => new Promise(resolve => { resolveRetry = resolve }))
    const wrapper = mountDesigner(component, { sourceType: 'VERSION', sourceId: '81' })
    await flushPromises()

    expect(wrapper.get('[data-action="save"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[role="dialog"][aria-modal="true"]').exists()).toBe(false)
    await wrapper.get('[data-testid="designer-source-retry"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-action="save"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="designer-source-retry"]').exists()).toBe(false)
    resolveRetry({ data: { id: 81, definitionId: 30, version: 1, modelJson: JSON.stringify(model) } })
    await flushPromises()

    expect(definitionApi.getVersionById).toHaveBeenNthCalledWith(2, 30, '81')
    expect(wrapper.vm.viewRevision).toMatchObject({ id: 81, sourceType: 'VERSION' })
    assertLoaded(wrapper.vm)
    expect(wrapper.get('[data-action="save"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.find('[data-testid="draft-read-only"]').exists()).toBe(false)
    expect(definitionApi.saveDesignerDraft).not.toHaveBeenCalled()
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test.each(designerToolbars)(
    '%s 使用统一四动作操作栏',
    async (_name, component, toolbarSelector) => {
      definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
      definitionApi.getContent.mockResolvedValueOnce({
        data: { modelJson: '{}' },
      })
      const wrapper = mountDesigner(component)
      await flushPromises()

      const toolbar = wrapper.get(toolbarSelector)
      expect(
        toolbar.findComponent({ name: 'RuleDesignerActionBar' }).exists()
      ).toBe(true)
      expect(toolbar.findAll('.rule-designer-actions [data-action]').map(button => button.attributes('data-action'))).toEqual(['compile', 'save', 'publish', 'test'])
      expect(toolbar.text()).not.toContain('临时保存配置')
      expect(toolbar.text()).not.toContain('编译后测试')
      wrapper.unmount()
    }
  )

  test.each(designers)('%s 的保存与编译入口受规则编辑权限控制', async (
    _name,
    component
  ) => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    const permissions = []
    const wrapper = mountDesigner(component, {}, {
      directives: {
        permission: {
          mounted: (_element, binding) => permissions.push(binding.value)
        }
      }
    })
    await flushPromises()

    expect(permissions).toContain('rule:edit')
    wrapper.unmount()
  })

  test('可视化设计器从共享提示点击进入当前规则生命周期', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{
        id: 6,
        definitionId: 30,
        revisionNo: 2,
        state: 'DRAFT',
        lockVersion: 0,
        modelJson: JSON.stringify({ rules: [] }),
      }],
    })
    const wrapper = mountDesigner(DecisionTable, {}, {
      stubs: { ScriptPanel },
    })
    await flushPromises()

    wrapper.vm.goRuleLifecycle()

    expect(wrapper.vm.$router.push).toHaveBeenCalledWith({
      name: 'RuleDetail',
      params: { id: 30 },
      query: { focus: 'lifecycle' },
    })
    wrapper.unmount()
  })

  test('脚本设计器从自身提示点击进入当前规则生命周期', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{
        id: 6,
        definitionId: 30,
        revisionNo: 2,
        state: 'DRAFT',
        lockVersion: 0,
        modelJson: JSON.stringify({ script: '', scriptVarRefs: [] }),
      }],
    })
    const wrapper = mountDesigner(ScriptEditor)
    await flushPromises()

    wrapper.vm.goRuleLifecycle()

    expect(wrapper.vm.$router.push).toHaveBeenCalledWith({
      name: 'RuleDetail',
      params: { id: 30 },
      query: { focus: 'lifecycle' },
    })
    wrapper.unmount()
  })

  test.each(designers)('%s 没有草稿也可直接编辑，点击暂存才持久化', async (_name, component) => {
    const published = { id: 5, definitionId: 30, revisionNo: 1, state: 'PUBLISHED', lockVersion: 0, modelJson: '{}' }
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [published] })
    definitionApi.saveDesignerDraft.mockResolvedValueOnce({ data: { revision: { id: 6, state: 'DRAFT', lockVersion: 1, modelJson: '{}' }, compileSuccess: true, issues: [] } })
    const wrapper = mountDesigner(component)
    await flushPromises()
    expect(wrapper.find('[data-testid="draft-read-only"]').exists()).toBe(false)
    expect(wrapper.vm.canEditDraft).toBe(true)
    expect(definitionApi.saveDesignerDraft).not.toHaveBeenCalled()
    expect(definitionApi.createDraftRevision).not.toHaveBeenCalled()
    await wrapper.get('[data-action="save"]').trigger('click')
    await flushPromises()
    expect(definitionApi.saveDesignerDraft).toHaveBeenCalledWith('30', expect.objectContaining({ sourceType: 'REVISION', sourceId: '5', modelJson: expect.any(String) }))
    expect(wrapper.vm.viewRevision).toMatchObject({ id: 6, state: 'DRAFT' })
    expect(published.modelJson).toBe('{}')
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test.each(designers)('%s 在 DRAFT 就绪后解除只读遮罩', async (
    _name,
    component
  ) => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 0,
          modelJson: '{}',
        },
      ],
    })

    const wrapper = mountDesigner(component)
    await flushPromises()

    expect({
      overlay: wrapper.find('[data-testid="draft-read-only"]').exists(),
      canEditDraft: wrapper.vm.canEditDraft,
      draftRevision: wrapper.vm.draftRevision,
      draftGuardLoaded: wrapper.vm.draftGuardLoaded,
    }).toEqual({
      overlay: false,
      canEditDraft: true,
      draftRevision: expect.objectContaining({ id: 6, state: 'DRAFT' }),
      draftGuardLoaded: true,
    })
    wrapper.unmount()
  })

  test.each(explicitRevisionSources)(
    '$name 无版本时直接加载初始内容，只有点击暂存才写服务端',
    async ({ component, model, assertLoaded }) => {
      definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
      definitionApi.getContent.mockResolvedValueOnce({ data: { modelJson: JSON.stringify(model) } })
      definitionApi.saveDesignerDraft.mockResolvedValueOnce({ data: { revision: { id: 6, state: 'DRAFT', lockVersion: 1, modelJson: JSON.stringify(model) }, compileSuccess: true, issues: [] } })
      const wrapper = mountDesigner(component)
      await flushPromises()
      assertLoaded(wrapper.vm)
      expect(wrapper.vm.canEditDraft).toBe(true)
      expect(wrapper.find('[data-testid="draft-read-only"]').exists()).toBe(false)
      expect(definitionApi.saveDesignerDraft).not.toHaveBeenCalled()
      await wrapper.get('[data-action="save"]').trigger('click')
      await flushPromises()
      expect(definitionApi.saveDesignerDraft).toHaveBeenCalledWith('30', expect.objectContaining({ modelJson: expect.any(String) }))
      expect(definitionApi.createDraftRevision).not.toHaveBeenCalled()
      expect(definitionApi.saveContent).not.toHaveBeenCalled()
      wrapper.unmount()
    }
  )

  test.each(designers)('%s 查看历史节点时已有 DRAFT 则也不自动切换或覆盖暂存草稿', async (
    _name,
    component
  ) => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 0,
          modelJson: '{}',
        },
      ],
    })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: {
        id: 41,
        definitionId: 30,
        revisionNo: 7,
        state: 'PUBLISHED',
        lockVersion: 3,
        modelJson: '{"sourceMarker":"revision-41"}',
      },
    })
    const wrapper = mountDesigner(component, {
      sourceType: 'REVISION',
      sourceId: '41',
    })
    await flushPromises()

    const readOnly = wrapper.findComponent({ name: 'RuleDraftReadOnly' })
    expect(readOnly.props()).toMatchObject({
      visible: false,
      loading: false,
      loadError: false,
      revisionLabel: '修订 7',
      revisionState: 'PUBLISHED',
      canFork: true,
    })

    definitionApi.saveDesignerDraft.mockResolvedValueOnce({ data: { revision: { id: 42, state: 'DRAFT', lockVersion: 1 }, compileSuccess: true } })
    await wrapper.vm.handleSave()
    expect(definitionApi.saveDesignerDraft).toHaveBeenCalledWith('30', expect.objectContaining({ saveMode: 'NEW', sourceId: '41' }))
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    expect(wrapper.vm.$router.replace).toHaveBeenCalled()
    expect(wrapper.vm.viewRevision.id).toBe(42)
    wrapper.unmount()
  })

  test.each(explicitRevisionSources)(
    '$name 从显式 REVISION 的 modelJson 加载自身可观测状态，而非当前 DRAFT',
    async ({ component, model, assertLoaded }) => {
      definitionApi.listRuleRevisions.mockResolvedValueOnce({
        data: [
          {
            id: 6,
            definitionId: 30,
            revisionNo: 2,
            state: 'DRAFT',
            lockVersion: 0,
            modelJson: JSON.stringify({ sourceMarker: 'draft-6' }),
          },
        ],
      })
      definitionApi.getRuleRevision.mockResolvedValueOnce({
        data: {
          id: 41,
          definitionId: 30,
          revisionNo: 7,
          state: 'PUBLISHED',
          lockVersion: 3,
          modelJson: JSON.stringify(model),
        },
      })

      const wrapper = mountDesigner(component, {
        sourceType: 'REVISION',
        sourceId: '41',
      })
      await flushPromises()

      expect(wrapper.vm.viewRevision).toMatchObject({ id: 41, state: 'PUBLISHED' })
      assertLoaded(wrapper.vm)
      wrapper.unmount()
    }
  )

  test.each(designers)('%s 已删除来源时执行保存入口仍由共享守卫阻止', async (
    _name,
    component
  ) => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 0,
          modelJson: '{}',
        },
      ],
    })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: {
        id: 41,
        definitionId: 30,
        revisionNo: 7,
        state: 'DELETED',
        lockVersion: 3,
        modelJson: '{}',
      },
    })
    const wrapper = mountDesigner(component, {
      sourceType: 'REVISION',
      sourceId: '41',
    })
    await flushPromises()

    await expect(wrapper.vm.handleSave()).resolves.toBe(false)
    expect(wrapper.vm.$message.error).toHaveBeenCalled()
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('DecisionTable 结构编辑后可直接编译历史副本且不保存', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 0,
          modelJson: '{}',
        },
      ],
    })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: {
        id: 41,
        definitionId: 30,
        revisionNo: 7,
        state: 'PUBLISHED',
        lockVersion: 3,
        modelJson: JSON.stringify({ rules: [] }),
      },
    })
    const wrapper = mountDesigner(DecisionTable, {
      sourceType: 'REVISION',
      sourceId: '41',
    })
    await flushPromises()

    wrapper.vm.addRule()
    expect(wrapper.vm.model.rules).toHaveLength(1)
    definitionApi.compileDesignerModel.mockResolvedValueOnce({ data: { compileSuccess: true, compiledScript: 'result = true;', preflightReport: { valid: true } } })
    await wrapper.vm.handleCompile()
    expect(definitionApi.compileDesignerModel).toHaveBeenCalledWith('30', expect.objectContaining({ sourceId: '41', modelJson: expect.any(String) }))
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('精确来源加载失败后，脚本测试入口不能保存当前 DRAFT', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 0,
          modelJson: JSON.stringify({ script: "result = 'draft-6'" }),
        },
      ],
    })
    definitionApi.getRuleRevision.mockRejectedValueOnce(new Error('修订 41 不存在'))
    const wrapper = mountDesigner(ScriptEditor, {
      sourceType: 'REVISION',
      sourceId: '41',
    })
    await flushPromises()

    expect(wrapper.vm.draftGuardError).toBeInstanceOf(Error)
    await expect(wrapper.vm.handleTest()).resolves.toBeUndefined()
    expect(wrapper.vm.$message.warning).toHaveBeenCalledWith(
      '当前内容未加载或没有规则执行权限'
    )
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('精确来源加载中时，决策流结构编辑后的保存会等待守卫并最终被阻止', async () => {
    let resolveSource
    const sourcePromise = new Promise((resolve) => {
      resolveSource = resolve
    })
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 0,
          modelJson: '{}',
        },
      ],
    })
    definitionApi.getRuleRevision.mockReturnValueOnce(sourcePromise)
    const wrapper = mountDesigner(DecisionFlow, {
      sourceType: 'REVISION',
      sourceId: '41',
    })
    await Promise.resolve()

    expect(wrapper.vm.draftGuardLoaded).toBe(false)
    wrapper.vm.addNode('script-task')
    const pendingSave = wrapper.vm.handleSave()
    await Promise.resolve()
    expect(definitionApi.saveContent).not.toHaveBeenCalled()

    resolveSource({
      data: {
        id: 41,
        definitionId: 30,
        revisionNo: 7,
        state: 'DELETED',
        lockVersion: 3,
        modelJson: '{}',
      },
    })
    await expect(pendingSave).resolves.toBe(false)
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('ScriptEditor 从非空 DRAFT 加载稳定引用并在编译失败不推进锁且仍可打开测试', async () => {
    const scriptModel = {
      script: 'credit_score = profile.score;',
      scriptVarRefs: [
        {
          refCode: 'profile.score',
          varId: 88,
          refType: 'DATA_OBJECT',
        },
      ],
    }
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 4,
          modelJson: JSON.stringify(scriptModel),
        },
      ],
    })
    definitionApi.compileDesignerModel.mockResolvedValueOnce({
      data: {
        revision: {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 5,
          modelJson: JSON.stringify(scriptModel),
        },
        compileSuccess: false,
        compileMessage: '脚本解析失败',
        issues: [{ code: 'QL_PARSE_ERROR', severity: 'ERROR' }],
      },
    })
    const wrapper = mountDesigner(ScriptEditor)
    await flushPromises()

    expect({
      script: wrapper.vm.script,
      scriptVarRefs: wrapper.vm.scriptVarRefs,
    }).toEqual(scriptModel)
    await wrapper.vm.handleCompile()
    await wrapper.vm.handleTest()

    const payload = definitionApi.compileDesignerModel.mock.calls[0][1]
    expect(JSON.parse(payload.modelJson)).toEqual(scriptModel)
    expect(wrapper.vm.draftRevision.lockVersion).toBe(4)
    expect(wrapper.vm.testVisible).toBe(true)
    expect(definitionApi.saveDesignerDraft).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('DecisionFlow 从非空 DRAFT 加载图结构并按稳定引用原子保存', async () => {
    const flowModel = {
      defaultEdgeLineType: 'polyline',
      nodes: [
        {
          id: 'task_1',
          type: 'task',
          name: '评分任务',
          x: 240,
          y: 180,
          actionData: [],
          leftVarId: 81,
          leftRefType: 'VARIABLE',
        },
      ],
      edges: [],
      logicflow: {
        nodes: [
          {
            id: 'task_1',
            type: 'script-task',
            x: 240,
            y: 180,
            properties: {
              nodeName: '评分任务',
              actionData: [],
              leftVarId: 81,
              leftRefType: 'VARIABLE',
            },
          },
        ],
        edges: [],
      },
    }
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 7,
          definitionId: 30,
          revisionNo: 3,
          state: 'DRAFT',
          lockVersion: 2,
          modelJson: JSON.stringify(flowModel),
        },
      ],
    })
    definitionApi.saveDesignerDraft.mockResolvedValueOnce({
      data: {
        revision: {
          id: 7,
          definitionId: 30,
          revisionNo: 3,
          state: 'DRAFT',
          lockVersion: 3,
        },
        compileSuccess: true,
        issues: [],
      },
    })
    const wrapper = mountDesigner(DecisionFlow)
    await flushPromises()

    expect(wrapper.vm.lf.getGraphData().nodes[0]).toMatchObject({
      id: 'task_1',
      properties: {
        leftVarId: 81,
        leftRefType: 'VARIABLE',
      },
    })
    wrapper.vm.lf.getGraphData().nodes[0].properties.nodeName = '修改后的评分任务'
    await wrapper.vm.handleSave()

    const payload = definitionApi.saveDesignerDraft.mock.calls[0][1]
    const savedModel = JSON.parse(payload.modelJson)
    expect({
      revisionId: payload.revisionId,
      lockVersion: payload.lockVersion,
      backendNode: savedModel.nodes[0],
      logicflowNode: savedModel.logicflow.nodes[0],
    }).toEqual({
      revisionId: '7',
      lockVersion: 2,
      backendNode: expect.objectContaining({
        id: 'task_1',
        leftVarId: 81,
        leftRefType: 'VARIABLE',
      }),
      logicflowNode: expect.objectContaining({
        id: 'task_1',
        properties: expect.objectContaining({
          leftVarId: 81,
          leftRefType: 'VARIABLE',
        }),
      }),
    })
    wrapper.unmount()
  })

  test.each(designerToolbars)('%s 将规则版本选择器放在顶部工具栏内', async (
    _name,
    component,
    toolbarSelector
  ) => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 0,
          modelJson: '{}',
        },
      ],
    })

    const wrapper = mountDesigner(component)
    await flushPromises()

    expect(
      wrapper.get(toolbarSelector)
        .find('[data-testid="designer-version-control"]')
        .exists()
    ).toBe(true)
    wrapper.unmount()
  })

  test.each([
    ['DecisionTree', DecisionTree],
    ['DecisionFlow', DecisionFlow],
  ])('%s 属性面板关闭按钮会保存当前编辑并清除选中项', async (name, component) => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 7, definitionId: 30, revisionNo: 3, state: 'DRAFT', lockVersion: 2, modelJson: '{}' }],
    })
    const wrapper = mountDesigner(component)
    await flushPromises()
    wrapper.vm.activeElement = {
      id: 'task-1',
      type: 'script-task',
      baseType: 'node',
      properties: { actionData: [] },
    }
    await wrapper.vm.$nextTick()

    const propertyInput = document.createElement('input')
    const propertyInputWrapper = document.createElement('div')
    propertyInputWrapper.className = 'el-input'
    propertyInputWrapper.appendChild(propertyInput)
    document.body.appendChild(propertyInputWrapper)
    propertyInput.focus()
    expect(document.activeElement).toBe(propertyInput)

    await wrapper.get('.prop-close').trigger('click')

    expect(wrapper.vm.activeElement).toBeNull()
    expect(document.activeElement).not.toBe(propertyInput)
    propertyInputWrapper.remove()
    wrapper.unmount()
  })

  test.each([
    ['DecisionTree', DecisionTree],
    ['DecisionFlow', DecisionFlow],
  ])('%s 已有开始节点时不会再次添加开始节点', async (name, component) => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 7, definitionId: 30, revisionNo: 3, state: 'DRAFT', lockVersion: 2, modelJson: '{}' }],
    })
    const wrapper = mountDesigner(component)
    await flushPromises()
    const before = wrapper.vm.lf.getGraphData().nodes.filter(node => node.type === 'start-event').length

    wrapper.vm.addNode('start-event')

    const after = wrapper.vm.lf.getGraphData().nodes.filter(node => node.type === 'start-event').length
    expect(before).toBe(1)
    expect(after).toBe(1)
    wrapper.unmount()
  })

  test.each([
    ['DecisionTree', DecisionTree],
    ['DecisionFlow', DecisionFlow],
  ])('%s 工具栏连续新增节点使用稳定网格位置', async (name, component) => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 7, definitionId: 30, revisionNo: 3, state: 'DRAFT', lockVersion: 2, modelJson: '{}' }],
    })
    const wrapper = mountDesigner(component)
    await flushPromises()

    wrapper.vm.addNode('script-task')
    wrapper.vm.addNode('script-task')

    const tasks = wrapper.vm.lf.getGraphData().nodes.filter(node => node.type === 'script-task')
    expect(tasks.map(node => ({ x: node.x, y: node.y }))).toEqual([
      { x: 400, y: 300 },
      { x: 620, y: 300 },
    ])
    wrapper.unmount()
  })

  test.each([
    ['DecisionTree', DecisionTree],
    ['DecisionFlow', DecisionFlow],
  ])('%s 切换到节点前会自动保存当前可视化连线条件', async (name, component) => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 7, definitionId: 30, revisionNo: 3, state: 'DRAFT', lockVersion: 2, modelJson: '{}' }],
    })
    const wrapper = mountDesigner(component)
    await flushPromises()
    const setProperties = vi.fn()
    wrapper.vm.lf = {
      getProperties: vi.fn(() => ({})),
      setProperties,
      changeEdgeType: vi.fn(),
      updateText: vi.fn(),
      destroy: vi.fn(),
      getNodeModelById: vi.fn(() => ({
        type: 'script-task',
        properties: { actionData: [] },
      })),
    }
    wrapper.vm.activeElement = { id: 'edge-1', type: 'polyline', baseType: 'edge', properties: {} }
    wrapper.vm.edgeCondMode = 'visual'
    wrapper.vm.edgeProps = { conditionName: '', conditionExpr: '', conditionConfig: null, edgeLineType: '' }
    wrapper.vm.edgeConditionRoot = {
      type: 'group',
      op: 'AND',
      children: [{
        type: 'leaf',
        operator: '>',
        leftOperand: {
          kind: 'REFERENCE', value: 'score', code: 'score', valueType: 'NUMBER',
          refId: 1, refType: 'VARIABLE', resolved: true,
        },
        rightOperand: { kind: 'LITERAL', value: '600', valueType: 'NUMBER' },
      }],
    }

    wrapper.vm.selectNodeData({ id: 'task-1' })

    expect(setProperties).toHaveBeenCalledWith('edge-1', expect.objectContaining({
      conditionExpr: expect.stringContaining('score'),
      conditionConfig: expect.objectContaining({ type: 'group' }),
    }))
    expect(wrapper.vm.activeElement).toMatchObject({ id: 'task-1', baseType: 'node' })
    wrapper.unmount()
  })

  test('DecisionFlow 后端模型保留显式分支优先级', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 7, definitionId: 30, revisionNo: 3, state: 'DRAFT', lockVersion: 2, modelJson: '{}' }],
    })
    const wrapper = mountDesigner(DecisionFlow)
    await flushPromises()
    wrapper.vm.activeElement = null
    wrapper.vm.lf.graph = {
      nodes: [
        { id: 'start', type: 'start-event', x: 100, y: 100, properties: { nodeName: '开始' } },
        { id: 'decision', type: 'exclusive-gateway', x: 300, y: 100, properties: { nodeName: '判断' } },
        { id: 'end-a', type: 'end-event', x: 500, y: 50, properties: { nodeName: '返回' } },
        { id: 'end-b', type: 'end-event', x: 500, y: 150, properties: { nodeName: '返回' } },
      ],
      edges: [
        { id: 'start-edge', sourceNodeId: 'start', targetNodeId: 'decision', properties: {} },
        { id: 'edge-a', sourceNodeId: 'decision', targetNodeId: 'end-a', properties: { conditionExpr: 'score > 0', priority: 20 } },
        { id: 'edge-b', sourceNodeId: 'decision', targetNodeId: 'end-b', properties: { conditionExpr: 'score > 0', priority: 10 } },
      ],
    }

    const model = wrapper.vm.buildBackendModel()

    expect(model.edges.slice(1).map(edge => edge.priority)).toEqual([20, 10])
    wrapper.unmount()
  })

  test('DecisionFlow 判断节点可上移分支并重写连续优先级', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 7, definitionId: 30, revisionNo: 3, state: 'DRAFT', lockVersion: 2, modelJson: '{}' }],
    })
    const wrapper = mountDesigner(DecisionFlow)
    await flushPromises()
    const edges = [
      { id: 'edge-a', sourceNodeId: 'decision', properties: { conditionName: 'A', priority: 10 } },
      { id: 'edge-b', sourceNodeId: 'decision', properties: { conditionName: 'B', priority: 20 } },
    ]
    const setProperties = vi.fn((id, props) => {
      edges.find(edge => edge.id === id).properties = props
    })
    wrapper.vm.lf = {
      getNodeEdges: vi.fn(() => edges),
      getProperties: vi.fn(id => edges.find(edge => edge.id === id).properties),
      setProperties,
      destroy: vi.fn(),
    }
    wrapper.vm.activeElement = {
      id: 'decision', type: 'exclusive-gateway', baseType: 'node', properties: {},
    }
    await wrapper.vm.$nextTick()

    const upButtons = wrapper.findAll('.edge-priority-up')
    expect(upButtons).toHaveLength(2)
    await upButtons[1].trigger('click')

    expect(setProperties).toHaveBeenCalledWith('edge-b', expect.objectContaining({ priority: 10 }))
    expect(setProperties).toHaveBeenCalledWith('edge-a', expect.objectContaining({ priority: 20 }))
    wrapper.unmount()
  })

  test('DecisionFlow 前端验证阻止只有开始节点的空流', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 7, definitionId: 30, revisionNo: 3, state: 'DRAFT', lockVersion: 2, modelJson: '{}' }],
    })
    const wrapper = mountDesigner(DecisionFlow)
    await flushPromises()

    wrapper.vm.handleValidate()

    expect(wrapper.vm.$alert).toHaveBeenCalledWith(
      expect.stringMatching(/结束节点|出边/),
      '验证失败',
      expect.any(Object)
    )
    wrapper.unmount()
  })

  test('DecisionFlow 前端验证阻止判断节点存在多个默认分支', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 7, definitionId: 30, revisionNo: 3, state: 'DRAFT', lockVersion: 2, modelJson: '{}' }],
    })
    const wrapper = mountDesigner(DecisionFlow)
    await flushPromises()
    wrapper.vm.lf.graph = {
      nodes: [
        { id: 'start', type: 'start-event', x: 100, y: 100, properties: { nodeName: '开始' } },
        { id: 'decision', type: 'exclusive-gateway', x: 300, y: 100, properties: { nodeName: '判断' } },
        { id: 'end-a', type: 'end-event', x: 500, y: 50, properties: { nodeName: '返回 A' } },
        { id: 'end-b', type: 'end-event', x: 500, y: 150, properties: { nodeName: '返回 B' } },
      ],
      edges: [
        { id: 'start-edge', sourceNodeId: 'start', targetNodeId: 'decision', properties: {} },
        { id: 'edge-a', sourceNodeId: 'decision', targetNodeId: 'end-a', properties: {} },
        { id: 'edge-b', sourceNodeId: 'decision', targetNodeId: 'end-b', properties: {} },
      ],
    }

    wrapper.vm.handleValidate()

    expect(wrapper.vm.$alert).toHaveBeenCalledWith(
      expect.stringContaining('默认分支'),
      '验证失败',
      expect.any(Object)
    )
    wrapper.unmount()
  })

  test('DecisionTree 前端验证阻止动作节点多出边和不可达节点', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 7, definitionId: 30, revisionNo: 3, state: 'DRAFT', lockVersion: 2, modelJson: '{}' }],
    })
    const wrapper = mountDesigner(DecisionTree)
    await flushPromises()
    wrapper.vm.lf.graph = {
      nodes: [
        { id: 'start', type: 'start-event', x: 100, y: 100, properties: { nodeName: '开始' } },
        { id: 'task', type: 'script-task', x: 300, y: 100, properties: { nodeName: '动作' } },
        { id: 'end-a', type: 'end-event', x: 500, y: 50, properties: { nodeName: '返回 A' } },
        { id: 'end-b', type: 'end-event', x: 500, y: 150, properties: { nodeName: '返回 B' } },
        { id: 'orphan', type: 'script-task', x: 700, y: 300, properties: { nodeName: '孤立动作' } },
      ],
      edges: [
        { id: 'start-edge', sourceNodeId: 'start', targetNodeId: 'task', properties: {} },
        { id: 'edge-a', sourceNodeId: 'task', targetNodeId: 'end-a', properties: {} },
        { id: 'edge-b', sourceNodeId: 'task', targetNodeId: 'end-b', properties: {} },
      ],
    }

    wrapper.vm.handleValidate()

    expect(wrapper.vm.$alert).toHaveBeenCalledWith(
      expect.stringMatching(/只能有一条出边/),
      '验证失败',
      expect.any(Object)
    )
    expect(wrapper.vm.$alert.mock.calls[0][0]).toContain('不可达')
    wrapper.unmount()
  })

  test('AdvancedCrossTable 从非空 DRAFT 保留维度 ID 与单元格结构', async () => {
    const referenceOperand = {
      kind: 'REFERENCE',
      value: 'customer.segment',
      code: 'customer.segment',
      label: '客户分群',
      valueType: 'STRING',
      refId: 71,
      refType: 'DATA_OBJECT',
      resolved: true,
    }
    const resultOperand = {
      kind: 'REFERENCE',
      value: 'decision.result',
      code: 'decision.result',
      label: '决策结果',
      valueType: 'NUMBER',
      refId: 73,
      refType: 'DATA_OBJECT',
      resolved: true,
    }
    const literal = {
      kind: 'LITERAL',
      value: 'A',
      valueType: 'STRING',
    }
    const advancedModel = {
      rowDimensions: [
        {
          varCode: 'customer.segment',
          varLabel: '客户分群',
          varType: 'STRING',
          _varId: 71,
          _refType: 'DATA_OBJECT',
          operand: referenceOperand,
          segments: [
            {
              label: 'A',
              operator: '==',
              value: 'A',
              valueOperand: literal,
            },
          ],
        },
      ],
      colDimensions: [
        {
          varCode: 'customer.level',
          varLabel: '客户等级',
          varType: 'STRING',
          _varId: 72,
          _refType: 'DATA_OBJECT',
          operand: {
            ...referenceOperand,
            value: 'customer.level',
            code: 'customer.level',
            label: '客户等级',
            refId: 72,
          },
          segments: [
            {
              label: 'VIP',
              operator: '==',
              value: 'VIP',
              valueOperand: {
                ...literal,
                value: 'VIP',
              },
            },
          ],
        },
      ],
      resultVar: {
        varCode: 'decision.result',
        varLabel: '决策结果',
        varType: 'NUMBER',
        _varId: 73,
        _refType: 'DATA_OBJECT',
        operand: resultOperand,
      },
      cells: [[{ kind: 'LITERAL', value: 100, valueType: 'NUMBER' }]],
    }
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 8,
          definitionId: 30,
          revisionNo: 4,
          state: 'DRAFT',
          lockVersion: 1,
          modelJson: JSON.stringify(advancedModel),
        },
      ],
    })
    const wrapper = mountDesigner(AdvancedCrossTable)
    await flushPromises()

    expect({
      rowVarId: wrapper.vm.model.rowDimensions[0]._varId,
      colVarId: wrapper.vm.model.colDimensions[0]._varId,
      resultVarId: wrapper.vm.model.resultVar._varId,
      cell: wrapper.vm.cellData[0][0],
    }).toEqual({
      rowVarId: 71,
      colVarId: 72,
      resultVarId: 73,
      cell: { kind: 'LITERAL', value: 100, valueType: 'NUMBER' },
    })
    wrapper.unmount()
  })

  test('RuleSet 版本历史只按稳定 ID 载入版本，不回滚或写入草稿', async () => {
    const oldModel = {
      executionMode: 'SERIAL',
      rules: [{ code: 'OLD_RULE', enabled: true }],
    }
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({
        data: [
          {
            id: 6,
            definitionId: 30,
            revisionNo: 2,
            state: 'DRAFT',
            lockVersion: 4,
            modelJson: JSON.stringify(oldModel),
          },
        ],
      })
    const wrapper = mountDesigner(RuleSet)
    await flushPromises()

    expect(wrapper.vm.model.rules[0].code).toBe('OLD_RULE')
    await wrapper.vm.loadVersionInDesigner({ id: '9007199254740993', version: 1 })
    expect(wrapper.vm.$router.replace).toHaveBeenCalledWith({
      query: { sourceType: 'VERSION', sourceId: '9007199254740993' },
    })
    expect(definitionApi.rollbackVersion).not.toHaveBeenCalled()
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    expect(definitionApi.saveDesignerDraft).not.toHaveBeenCalled()
    expect(wrapper.vm.draftRevision.lockVersion).toBe(4)

    wrapper.vm.$router.replace.mockClear()
    await wrapper.vm.loadVersionInDesigner({ version: 1 })
    expect(wrapper.vm.$router.replace).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
