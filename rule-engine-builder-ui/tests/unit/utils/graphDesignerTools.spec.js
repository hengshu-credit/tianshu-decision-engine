import { buildGraphNavigationItems, collectGraphConfigurationIssues } from '@/utils/graphDesignerTools'

describe('graphDesignerTools', () => {
  const graph = {
    nodes: [
      { id: 'start', type: 'start-event', properties: { nodeName: '开始' } },
      { id: 'gateway', type: 'exclusive-gateway', properties: { nodeName: '客户准入' } },
      { id: 'action', type: 'script-task', properties: { nodeName: '写入结果', actionData: [], leftVarId: 11, leftRefType: 'VARIABLE', leftVar: '客户.age' } }
    ],
    edges: [
      { id: 'edge-1', sourceNodeId: 'start', targetNodeId: 'gateway', properties: {} },
      { id: 'edge-2', sourceNodeId: 'gateway', targetNodeId: 'action', properties: { conditionName: '成年', conditionExpr: 'customer.age >= 18' } }
    ]
  }

  test('节点搜索同时返回引用定位入口', () => {
    expect(buildGraphNavigationItems(graph, '客户')).toEqual([
      expect.objectContaining({ kind: 'NODE', elementId: 'gateway', label: '客户准入' }),
      expect.objectContaining({ kind: 'REFERENCE', elementId: 'action', refId: 11, refType: 'VARIABLE' })
    ])
  })

  test('未配置检查定位空动作和出口不足的条件节点', () => {
    const issues = collectGraphConfigurationIssues(graph, 'FLOW')
    expect(issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ elementId: 'gateway', message: expect.stringContaining('至少需要两个出口') }),
      expect.objectContaining({ elementId: 'action', message: expect.stringContaining('未配置动作') })
    ]))
  })

  test('引用 ID 与 ref_type 缺一时报告悬空引用', () => {
    const broken = JSON.parse(JSON.stringify(graph))
    broken.nodes[2].properties.leftRefType = ''
    const issues = collectGraphConfigurationIssues(broken, 'FLOW')
    expect(issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ elementId: 'action', message: expect.stringContaining('ID + ref_type') })
    ]))
  })
})
