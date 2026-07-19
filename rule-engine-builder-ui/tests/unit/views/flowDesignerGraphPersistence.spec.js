import DecisionFlow from '@/views/designer/DecisionFlow.vue'
import DecisionTree from '@/views/designer/DecisionTree.vue'

const DESIGNERS = [
  ['决策流', DecisionFlow],
  ['决策树', DecisionTree]
]

describe.each(DESIGNERS)('%s画布持久化', (name, designer) => {
  test('图首次渲染完成后再创建默认开启的小地图', () => {
    const miniMap = { isShow: false, show: jest.fn(), hide: jest.fn() }
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
})
