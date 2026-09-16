import DecisionFlow from '@/views/designer/DecisionFlow.vue'
import DecisionTree from '@/views/designer/DecisionTree.vue'

const DESIGNERS = [
  ['决策流', DecisionFlow],
  ['决策树', DecisionTree]
]

function createHorizontalGraph() {
  return {
    nodes: [
      { id: 'start', type: 'start-event', x: 100, y: 100, properties: {} },
      { id: 'task', type: 'script-task', x: 100, y: 300, properties: { actionData: [] } }
    ],
    edges: [{
      id: 'edge-1',
      type: 'polyline',
      sourceNodeId: 'start',
      targetNodeId: 'task',
      sourceAnchorId: 'start_1',
      targetAnchorId: 'task_3',
      startPoint: { x: 125, y: 100 },
      endPoint: { x: 20, y: 300 },
      properties: {}
    }]
  }
}

describe.each(DESIGNERS)('%s画布持久化', (name, designer) => {
  test.each(['legacy', 'logicflow'])('%s 模型加载、编译序列化与再次加载保留脚本引用 ID', async format => {
    let graph = { nodes: [], edges: [] }
    const conditionExpression = 'taxpayerType == "一般纳税人"'
    const scriptVarRefs = [{ refCode: 'taxpayerType', varId: 255, refType: 'VARIABLE' }]
    const source = {
      nodes: [
        { id: 'start', type: 'start', x: 100, y: 100 },
        { id: 'end', type: 'end', x: 300, y: 100 }
      ],
      edges: [{ id: 'condition', source: 'start', target: 'end', conditionExpression }],
      scriptVarRefs
    }
    if (format === 'logicflow') source.logicflow = designer.methods.convertLegacyModel(source)
    const context = {
      viewRevision: { modelJson: JSON.stringify(source) },
      lf: {
        render: vi.fn(data => { graph = data }),
        getGraphData: () => graph,
        setDefaultEdgeType: vi.fn(),
        graphModel: { edges: [] }
      },
      convertLegacyModel: designer.methods.convertLegacyModel,
      lfTypeToBackend: designer.methods.lfTypeToBackend,
      refreshStartNodeState: vi.fn(),
      updateZoom: vi.fn(),
      _syncModelVarRefs: vi.fn(),
      initializeDesignerDraftTracking: vi.fn(),
      serializeDesignerDraft() {
        return JSON.stringify(designer.methods.buildBackendModel.call(this, false))
      },
      $nextTick: callback => callback(),
      $message: { error: vi.fn() }
    }

    await designer.methods.loadContent.call(context)
    const serialized = context.serializeDesignerDraft()
    expect(context.$message.error).not.toHaveBeenCalled()
    expect(JSON.parse(serialized)).toMatchObject({
      scriptVarRefs,
      edges: [{ conditionExpression }]
    })

    context.viewRevision = { modelJson: serialized }
    await designer.methods.loadContent.call(context)
    expect(JSON.parse(context.serializeDesignerDraft()).scriptVarRefs).toEqual(scriptVarRefs)

    context.viewRevision = { modelJson: JSON.stringify({ ...source, scriptVarRefs: undefined }) }
    await designer.methods.loadContent.call(context)
    expect(JSON.parse(context.serializeDesignerDraft()).scriptVarRefs).toEqual([])
  })

  test('锚点轻微拖动且未创建连线时仍打开快捷新增菜单', () => {
    const payload = { data: { id: 'task_1' }, nodeModel: { id: 'task' }, e: { clientX: 106, clientY: 100 } }
    const context = { anchorGesture: null, openAnchorMenu: vi.fn() }

    designer.methods.onAnchorMouseDown.call(context, {
      ...payload,
      e: { clientX: 100, clientY: 100 }
    })
    designer.methods.onAnchorDrag.call(context, payload)
    designer.methods.onAnchorDragEnd.call(context, payload)

    expect(context.openAnchorMenu).toHaveBeenCalledWith(payload)
    expect(context.anchorGesture).toBeNull()
  })

  test('锚点明显拖动时不打开快捷新增菜单', () => {
    const payload = { data: { id: 'task_1' }, nodeModel: { id: 'task' }, e: { clientX: 120, clientY: 100 } }
    const context = { anchorGesture: null, openAnchorMenu: vi.fn() }

    designer.methods.onAnchorMouseDown.call(context, {
      ...payload,
      e: { clientX: 100, clientY: 100 }
    })
    designer.methods.onAnchorDrag.call(context, payload)
    designer.methods.onAnchorDragEnd.call(context, payload)

    expect(context.openAnchorMenu).not.toHaveBeenCalled()
    expect(context.anchorGesture).toBeNull()
  })

  test('图首次渲染完成后再创建默认开启的小地图', () => {
    const miniMap = { isShow: false, show: vi.fn(), hide: vi.fn() }
    const context = { miniMapVisible: true, lf: { extension: { miniMap } } }

    designer.methods.onGraphRendered.call(context)

    expect(miniMap.show).toHaveBeenCalledTimes(1)
    expect(miniMap.hide).not.toHaveBeenCalled()
  })

  test('分组仅写入 logicflow 画布数据，不进入业务节点和连线', () => {
    const graph = {
      nodes: [
        { id: 'group-1', type: 'dynamic-group', x: 200, y: 150, properties: { children: ['start-1', 'task-1'] } },
        { id: 'start-1', type: 'start-event', x: 120, y: 150, properties: { nodeName: '开始' } },
        { id: 'task-1', type: 'script-task', x: 280, y: 150, properties: { nodeName: '执行动作', actionData: [] } }
      ],
      edges: [
        { id: 'edge-1', type: 'polyline', sourceNodeId: 'start-1', targetNodeId: 'task-1', properties: {} }
      ]
    }
    const context = {
      lf: {
        getGraphData: () => graph,
        graphModel: { edges: [{ id: 'edge-1', virtual: false }] }
      },
      activeElement: null,
      edgeCondMode: 'script',
      edgeConditionRoot: null,
      globalEdgeLineType: 'polyline',
      lfTypeToBackend: designer.methods.lfTypeToBackend
    }

    const model = designer.methods.buildBackendModel.call(context)

    expect(model.nodes.map(node => node.id)).toEqual(['start-1', 'task-1'])
    expect(model.edges.map(edge => edge.id)).toEqual(['edge-1'])
    expect(model.logicflow.nodes.map(node => node.id)).toEqual(['group-1', 'start-1', 'task-1'])
    expect(model.logicflow.nodes[0].properties.children).toEqual(['start-1', 'task-1'])
  })

  test('一键美化只渲染布局副本且不触发保存', () => {
    const graph = createHorizontalGraph()
    const snapshot = JSON.parse(JSON.stringify(graph))
    const context = {
      lf: {
        getGraphData: vi.fn(() => graph),
        graphModel: { edges: graph.edges.map(edge => ({ id: edge.id, virtual: false })) },
        render: vi.fn(),
        fitView: vi.fn()
      },
      handleSave: vi.fn(),
      handleCompile: vi.fn(),
      updateZoom: vi.fn(),
      $nextTick: callback => callback(),
      $message: { info: vi.fn(), success: vi.fn(), error: vi.fn() }
    }

    designer.methods.beautifyGraph.call(context)

    expect(context.lf.render).toHaveBeenCalledTimes(1)
    const rendered = context.lf.render.mock.calls[0][0]
    expect(rendered).not.toBe(graph)
    expect(rendered.nodes[1].x).toBeGreaterThan(rendered.nodes[0].x)
    expect(rendered.nodes[1].y).toBe(rendered.nodes[0].y)
    expect(context.handleSave).not.toHaveBeenCalled()
    expect(context.handleCompile).not.toHaveBeenCalled()
    expect(graph).toEqual(snapshot)
  })
})

describe.each(DESIGNERS)('%s锚点连线提示', (name, designer) => {
  test('轻微拖动命中原节点时不提示不允许添加连线', () => {
    const context = {
      anchorGesture: null,
      openAnchorMenu: vi.fn(),
      $message: { warning: vi.fn() }
    }
    const payload = { data: { id: 'task_1' }, nodeModel: { id: 'task' }, e: { clientX: 106, clientY: 100 } }

    designer.methods.onAnchorMouseDown.call(context, {
      ...payload,
      e: { clientX: 100, clientY: 100 }
    })
    designer.methods.onAnchorDrag.call(context, payload)
    designer.methods.handleConnectionNotAllowed?.call(context, { msg: '不允许添加连线' })

    expect(context.$message.warning).not.toHaveBeenCalled()
  })

  test('明显拖动产生非法连线时保留提示', () => {
    const context = {
      anchorGesture: null,
      openAnchorMenu: vi.fn(),
      $message: { warning: vi.fn() }
    }
    const payload = { data: { id: 'task_1' }, nodeModel: { id: 'task' }, e: { clientX: 120, clientY: 100 } }

    designer.methods.onAnchorMouseDown.call(context, {
      ...payload,
      e: { clientX: 100, clientY: 100 }
    })
    designer.methods.onAnchorDrag.call(context, payload)
    designer.methods.handleConnectionNotAllowed?.call(context, { msg: '不允许添加连线' })

    expect(context.$message.warning).toHaveBeenCalledWith('不允许添加连线')
  })
})
