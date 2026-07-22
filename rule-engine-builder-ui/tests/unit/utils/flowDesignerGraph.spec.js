import {
  FLOW_MENU_OPTIONS,
  FLOW_THEME_COLOR,
  addConnectedNode,
  calculateGroupBounds,
  createAnchorGesture,
  createFlowNodeData,
  findAvailableNodePosition,
  getBusinessGraphData,
  getPersistableGraphData,
  createDynamicGroup,
  isAnchorClickGesture,
  layoutGraphByAnchors,
  resolveAnchorDirection,
  resolveEdgeDirections,
  updateAnchorGesture
} from '@/components/flow/flowDesignerGraph'

describe('flowDesignerGraph', () => {
  test('锚点移动不超过可视拖线阈值时仍按单击处理', () => {
    const gesture = updateAnchorGesture(
      createAnchorGesture({ e: { clientX: 100, clientY: 100 } }),
      { e: { clientX: 108, clientY: 100 } }
    )

    expect(isAnchorClickGesture(gesture)).toBe(true)
  })

  test('锚点移动超过可视拖线阈值时保留手工拖拽语义', () => {
    const gesture = updateAnchorGesture(
      createAnchorGesture({ e: { clientX: 100, clientY: 100 } }),
      { e: { clientX: 112, clientY: 100 } }
    )

    expect(isAnchorClickGesture(gesture)).toBe(false)
  })

  test('四向锚点按相对节点中心识别方向', () => {
    const node = { x: 200, y: 160 }

    expect(resolveAnchorDirection({ x: 200, y: 100 }, node)).toBe('top')
    expect(resolveAnchorDirection({ x: 280, y: 160 }, node)).toBe('right')
    expect(resolveAnchorDirection({ x: 200, y: 220 }, node)).toBe('bottom')
    expect(resolveAnchorDirection({ x: 120, y: 160 }, node)).toBe('left')
  })

  test('新增节点位置沿锚点方向推进并避开已有节点', () => {
    const source = { id: 'source', x: 100, y: 100 }
    const nodes = [source, { id: 'occupied', x: 280, y: 100 }]

    expect(findAvailableNodePosition(nodes, source, 'right')).toEqual({ x: 460, y: 100 })
    expect(findAvailableNodePosition(nodes, source, 'bottom')).toEqual({ x: 100, y: 280 })
  })

  test('快捷新增节点复用设计器节点默认业务属性', () => {
    const node = createFlowNodeData('end-event', { x: 320, y: 260, terminationScope: 'ALL_RULES' })

    expect(node).toMatchObject({
      type: 'end-event',
      x: 320,
      y: 260,
      properties: {
        nodeName: '跳出整体规则',
        terminationScope: 'ALL_RULES',
        actionData: []
      }
    })
    expect(node.properties.nodeCode).toMatch(/^END_EVENT_/)
  })

  test('新增下一个节点后使用相对锚点自动连线并选中新节点', () => {
    const sourceNode = {
      id: 'source',
      x: 100,
      y: 100,
      isAllowConnectedAsSource: vi.fn(() => ({ isAllPass: true, msg: '' }))
    }
    const targetNode = {
      id: 'target',
      anchors: [
        { id: 'target_top', x: 280, y: 80 },
        { id: 'target_right', x: 360, y: 100 },
        { id: 'target_bottom', x: 280, y: 120 },
        { id: 'target_left', x: 200, y: 100 }
      ],
      isAllowConnectedAsTarget: vi.fn(() => ({ isAllPass: true, msg: '' }))
    }
    const lf = {
      getGraphData: vi.fn(() => ({ nodes: [{ id: 'source', x: 100, y: 100 }], edges: [] })),
      addNode: vi.fn(() => targetNode),
      addEdge: vi.fn(() => ({ id: 'edge-1' })),
      deleteNode: vi.fn(),
      selectElementById: vi.fn()
    }

    const result = addConnectedNode(lf, {
      sourceNode,
      sourceAnchor: { id: 'source_right', x: 180, y: 100 },
      type: 'script-task',
      direction: 'right'
    })

    expect(lf.addNode).toHaveBeenCalledWith(expect.objectContaining({ type: 'script-task', x: 280, y: 100 }))
    expect(lf.addEdge).toHaveBeenCalledWith(expect.objectContaining({
      sourceNodeId: 'source',
      targetNodeId: 'target',
      sourceAnchorId: 'source_right',
      targetAnchorId: 'target_left'
    }))
    expect(lf.selectElementById).toHaveBeenCalledWith('target')
    expect(result).toEqual({ node: targetNode, edge: { id: 'edge-1' } })
  })

  test('自动连线规则拒绝时回滚已新增节点', () => {
    const sourceNode = {
      id: 'source',
      x: 100,
      y: 100,
      isAllowConnectedAsSource: vi.fn(() => ({ isAllPass: false, msg: '禁止连接' }))
    }
    const targetNode = {
      id: 'target',
      anchors: [{ id: 'target_left', x: 200, y: 100 }],
      isAllowConnectedAsTarget: vi.fn(() => ({ isAllPass: true, msg: '' }))
    }
    const lf = {
      getGraphData: vi.fn(() => ({ nodes: [{ id: 'source', x: 100, y: 100 }] })),
      addNode: vi.fn(() => targetNode),
      addEdge: vi.fn(),
      deleteNode: vi.fn()
    }

    expect(() => addConnectedNode(lf, {
      sourceNode,
      sourceAnchor: { id: 'source_right', x: 180, y: 100 },
      type: 'script-task',
      direction: 'right'
    })).toThrow('禁止连接')
    expect(lf.deleteNode).toHaveBeenCalledWith('target')
    expect(lf.addEdge).not.toHaveBeenCalled()
  })

  test('分组边界包含所有成员并预留内边距', () => {
    const models = [
      { getBounds: () => ({ minX: 20, maxX: 180, minY: 60, maxY: 140 }) },
      { getBounds: () => ({ minX: 190, maxX: 350, minY: 150, maxY: 210 }) }
    ]

    expect(calculateGroupBounds(models, 25)).toEqual({ x: 185, y: 135, width: 380, height: 200 })
  })

  test('将至少两个选中业务节点创建为可折叠动态分组', () => {
    const models = {
      a: { id: 'a', isGroup: false, getBounds: () => ({ minX: 20, maxX: 180, minY: 60, maxY: 140 }) },
      b: { id: 'b', isGroup: false, getBounds: () => ({ minX: 190, maxX: 350, minY: 150, maxY: 210 }) }
    }
    const group = { id: 'group-1' }
    const lf = {
      extension: { dynamicGroup: { nodeGroupMap: new Map() } },
      getSelectElements: vi.fn(() => ({ nodes: [{ id: 'a' }, { id: 'b' }] })),
      getNodeModelById: vi.fn(id => models[id]),
      addNode: vi.fn(() => group),
      clearSelectElements: vi.fn(),
      selectElementById: vi.fn()
    }

    expect(createDynamicGroup(lf)).toBe(group)
    expect(lf.addNode).toHaveBeenCalledWith(expect.objectContaining({
      type: 'dynamic-group',
      properties: expect.objectContaining({ children: ['a', 'b'], collapsible: true, autoResize: true })
    }))
    expect(lf.clearSelectElements).toHaveBeenCalled()
    expect(lf.selectElementById).toHaveBeenCalledWith('group-1')
  })

  test('画布持久化保留分组但过滤折叠虚拟边，业务图过滤分组', () => {
    const graph = {
      nodes: [
        { id: 'group', type: 'dynamic-group', properties: { children: ['a', 'b'] } },
        { id: 'a', type: 'start-event' },
        { id: 'b', type: 'script-task' }
      ],
      edges: [
        { id: 'real', sourceNodeId: 'a', targetNodeId: 'b' },
        { id: 'virtual', sourceNodeId: 'group', targetNodeId: 'b' }
      ]
    }
    const lf = {
      getGraphData: () => graph,
      graphModel: { edges: [{ id: 'real', virtual: false }, { id: 'virtual', virtual: true }] }
    }

    const canvasGraph = getPersistableGraphData(lf)
    expect(canvasGraph.nodes.map(node => node.id)).toEqual(['group', 'a', 'b'])
    expect(canvasGraph.edges.map(edge => edge.id)).toEqual(['real'])
    expect(getBusinessGraphData(canvasGraph)).toEqual({
      nodes: [graph.nodes[1], graph.nodes[2]],
      edges: [graph.edges[0]]
    })
  })

  test('快捷菜单不提供开始节点且决策树不提供聚合节点', () => {
    expect(FLOW_THEME_COLOR).toBe('#2639E9')
    expect(FLOW_MENU_OPTIONS.flow.map(item => item.type)).toEqual([
      'exclusive-gateway', 'script-task', 'join-gateway', 'end-event'
    ])
    expect(FLOW_MENU_OPTIONS.tree.map(item => item.type)).toEqual([
      'exclusive-gateway', 'script-task', 'end-event'
    ])
  })

  test('连线方向优先按端点坐标识别并兼容默认锚点 ID', () => {
    const source = { id: 'source', x: 100, y: 100 }
    const target = { id: 'target', x: 300, y: 100 }

    expect(resolveEdgeDirections({
      sourceAnchorId: 'source_1',
      targetAnchorId: 'target_3'
    }, source, target)).toMatchObject({ x: 1, y: 0, sourceDirection: 'right', targetDirection: 'left' })

    expect(resolveEdgeDirections({
      sourceAnchorId: 'source_1',
      targetAnchorId: 'target_3',
      startPoint: { x: 100, y: 50 },
      endPoint: { x: 300, y: 150 }
    }, source, target)).toMatchObject({ x: 0, y: -1, sourceDirection: 'top', targetDirection: 'bottom' })
  })

  test.each([
    ['右到左锚点保持向右', 'source_1', 'target_3', 'x', 1],
    ['左到右锚点保持向左', 'source_3', 'target_1', 'x', -1],
    ['下到上锚点保持向下', 'source_2', 'target_0', 'y', 1],
    ['上到下锚点保持向上', 'source_0', 'target_2', 'y', -1]
  ])('%s', (name, sourceAnchorId, targetAnchorId, axis, sign) => {
    const graph = {
      nodes: [
        { id: 'source', type: 'start-event', x: 400, y: 300, properties: {} },
        { id: 'target', type: 'script-task', x: 420, y: 320, properties: {} }
      ],
      edges: [{ id: 'edge', sourceNodeId: 'source', targetNodeId: 'target', sourceAnchorId, targetAnchorId, properties: {} }]
    }

    const result = layoutGraphByAnchors(graph)
    const source = result.nodes.find(node => node.id === 'source')
    const target = result.nodes.find(node => node.id === 'target')

    expect(Math.sign(target[axis] - source[axis])).toBe(sign)
  })

  test('混合锚点按每条边形成二维分支且不修改输入图', () => {
    const graph = {
      nodes: [
        { id: 'root', type: 'start-event', x: 100, y: 100, properties: { nodeName: '开始' } },
        { id: 'right', type: 'script-task', x: 120, y: 120, properties: { actionData: [{ type: 'assign' }] } },
        { id: 'bottom', type: 'script-task', x: 140, y: 140, properties: {} },
        { id: 'corner', type: 'script-task', x: 160, y: 160, properties: {} }
      ],
      edges: [
        { id: 'right-edge', sourceNodeId: 'root', targetNodeId: 'right', sourceAnchorId: 'root_1', targetAnchorId: 'right_3', properties: { conditionName: '右' } },
        { id: 'bottom-edge', sourceNodeId: 'root', targetNodeId: 'bottom', sourceAnchorId: 'root_2', targetAnchorId: 'bottom_0', properties: { conditionName: '下' } },
        { id: 'corner-edge', sourceNodeId: 'right', targetNodeId: 'corner', sourceAnchorId: 'right_1', targetAnchorId: 'corner_0', properties: { conditionName: '右下' } }
      ]
    }
    const snapshot = JSON.parse(JSON.stringify(graph))

    const result = layoutGraphByAnchors(graph)
    const root = result.nodes.find(node => node.id === 'root')
    const right = result.nodes.find(node => node.id === 'right')
    const bottom = result.nodes.find(node => node.id === 'bottom')
    const corner = result.nodes.find(node => node.id === 'corner')

    expect(right.x).toBeGreaterThan(root.x)
    expect(bottom.y).toBeGreaterThan(root.y)
    expect(corner.x).toBeGreaterThan(right.x)
    expect(corner.y).toBeGreaterThan(right.y)
    expect(graph).toEqual(snapshot)
    expect(result).not.toBe(graph)
    expect(result.nodes[1].properties).not.toBe(graph.nodes[1].properties)
  })

  test('多段链路在节点移动前冻结锚点方向，避免旧端点坐标误导后续连线', () => {
    const graph = {
      nodes: [
        { id: 'root', type: 'start-event', x: 100, y: 100, properties: {} },
        { id: 'middle', type: 'script-task', x: 100, y: 300, properties: {} },
        { id: 'end', type: 'script-task', x: 100, y: 500, properties: {} }
      ],
      edges: [
        {
          id: 'first', sourceNodeId: 'root', targetNodeId: 'middle',
          sourceAnchorId: 'root_1', targetAnchorId: 'middle_3',
          startPoint: { x: 125, y: 100 }, endPoint: { x: 20, y: 300 }
        },
        {
          id: 'second', sourceNodeId: 'middle', targetNodeId: 'end',
          sourceAnchorId: 'middle_1', targetAnchorId: 'end_3',
          startPoint: { x: 180, y: 300 }, endPoint: { x: 20, y: 500 }
        }
      ]
    }

    const result = layoutGraphByAnchors(graph)
    const root = result.nodes.find(node => node.id === 'root')
    const middle = result.nodes.find(node => node.id === 'middle')
    const end = result.nodes.find(node => node.id === 'end')

    expect(middle.x).toBeGreaterThan(root.x)
    expect(end.x).toBeGreaterThan(middle.x)
    expect(end.y).toBe(middle.y)
  })

  test('多入边节点与美化前距离最近的直接上游平齐', () => {
    const graph = {
      nodes: [
        { id: 'far', type: 'script-task', x: 100, y: 100, properties: {} },
        { id: 'near', type: 'script-task', x: 300, y: 300, properties: {} },
        { id: 'target', type: 'script-task', x: 360, y: 320, properties: {} }
      ],
      edges: [
        { id: 'far-edge', sourceNodeId: 'far', targetNodeId: 'target', sourceAnchorId: 'far_1', targetAnchorId: 'target_3' },
        { id: 'near-edge', sourceNodeId: 'near', targetNodeId: 'target', sourceAnchorId: 'near_1', targetAnchorId: 'target_3' }
      ]
    }

    const result = layoutGraphByAnchors(graph)
    const near = result.nodes.find(node => node.id === 'near')
    const target = result.nodes.find(node => node.id === 'target')

    expect(target.y).toBe(near.y)
    expect(target.x).toBeGreaterThan(near.x)
  })

  test('同一水平方向的多个分支沿前进方向错开且保持平齐', () => {
    const graph = {
      nodes: [
        { id: 'root', type: 'start-event', x: 100, y: 100, properties: {} },
        { id: 'branch-a', type: 'script-task', x: 120, y: 100, properties: {} },
        { id: 'branch-b', type: 'script-task', x: 140, y: 100, properties: {} }
      ],
      edges: [
        { id: 'edge-a', sourceNodeId: 'root', targetNodeId: 'branch-a', sourceAnchorId: 'root_1', targetAnchorId: 'branch-a_3' },
        { id: 'edge-b', sourceNodeId: 'root', targetNodeId: 'branch-b', sourceAnchorId: 'root_1', targetAnchorId: 'branch-b_3' }
      ]
    }

    const result = layoutGraphByAnchors(graph)
    const branchA = result.nodes.find(node => node.id === 'branch-a')
    const branchB = result.nodes.find(node => node.id === 'branch-b')

    expect(branchA.x).toBeGreaterThan(result.nodes[0].x)
    expect(branchA.y).toBe(result.nodes[0].y)
    expect(branchB.y).toBe(result.nodes[0].y)
    expect(branchB.x).toBeGreaterThan(branchA.x)
  })

  test('同一垂直方向的多个分支沿前进方向错开且保持平齐', () => {
    const graph = {
      nodes: [
        { id: 'root', type: 'start-event', x: 100, y: 100, properties: {} },
        { id: 'branch-a', type: 'script-task', x: 100, y: 120, properties: {} },
        { id: 'branch-b', type: 'script-task', x: 100, y: 140, properties: {} }
      ],
      edges: [
        { id: 'edge-a', sourceNodeId: 'root', targetNodeId: 'branch-a', sourceAnchorId: 'root_2', targetAnchorId: 'branch-a_0' },
        { id: 'edge-b', sourceNodeId: 'root', targetNodeId: 'branch-b', sourceAnchorId: 'root_2', targetAnchorId: 'branch-b_0' }
      ]
    }

    const result = layoutGraphByAnchors(graph)
    const branchA = result.nodes.find(node => node.id === 'branch-a')
    const branchB = result.nodes.find(node => node.id === 'branch-b')

    expect(branchA.x).toBe(result.nodes[0].x)
    expect(branchB.x).toBe(result.nodes[0].x)
    expect(branchB.y).toBeGreaterThan(branchA.y)
  })

  test('布局后动态分组覆盖成员并保留 children 和折叠状态', () => {
    const graph = {
      nodes: [
        { id: 'group', type: 'dynamic-group', x: 100, y: 100, properties: { children: ['source', 'target'], isCollapsed: true, width: 100, height: 100 } },
        { id: 'source', type: 'start-event', x: 100, y: 100, properties: {} },
        { id: 'target', type: 'script-task', x: 100, y: 200, properties: {} }
      ],
      edges: [{
        id: 'edge', sourceNodeId: 'source', targetNodeId: 'target', sourceAnchorId: 'source_1', targetAnchorId: 'target_3',
        startPoint: { x: 125, y: 100 }, endPoint: { x: 20, y: 200 }, pointsList: [{ x: 125, y: 100 }]
      }]
    }

    const result = layoutGraphByAnchors(graph)
    const group = result.nodes.find(node => node.id === 'group')
    const source = result.nodes.find(node => node.id === 'source')
    const target = result.nodes.find(node => node.id === 'target')
    const minX = Math.min(source.x - 25, target.x - 80)
    const maxX = Math.max(source.x + 25, target.x + 80)

    expect(group.properties.children).toEqual(['source', 'target'])
    expect(group.properties.isCollapsed).toBe(true)
    expect(group.x - group.properties.width / 2).toBeLessThanOrEqual(minX)
    expect(group.x + group.properties.width / 2).toBeGreaterThanOrEqual(maxX)
    expect(result.edges[0]).not.toHaveProperty('startPoint')
    expect(result.edges[0]).not.toHaveProperty('endPoint')
    expect(result.edges[0]).not.toHaveProperty('pointsList')
  })

  test('缺少锚点信息时沿原节点主要相对方向稳定回退', () => {
    const graph = {
      nodes: [
        { id: 'source', type: 'start-event', x: 300, y: 300, properties: {} },
        { id: 'target', type: 'script-task', x: 80, y: 320, properties: {} }
      ],
      edges: [{ id: 'edge', sourceNodeId: 'source', targetNodeId: 'target' }]
    }

    const result = layoutGraphByAnchors(graph)

    expect(result.nodes[1].x).toBeLessThan(result.nodes[0].x)
  })
})
