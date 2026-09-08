import { shallowMount } from '@test-utils'
import { nextTick } from 'vue'
import LineageGraph from '@/views/lineage/LineageGraph.vue'
import * as lineageApi from '@/api/lineage'



function graphResponse() {
  return {
    startNode: {
      id: 'VARIABLE:1', refId: 1, type: 'VARIABLE', code: 'score', label: '评分',
      hasUpstream: true, hasDownstream: true
    },
    nodes: [
      { id: 'DATASOURCE:8', refId: 8, type: 'DATASOURCE', code: 'credit', label: '征信源', hasUpstream: true, hasDownstream: true },
      { id: 'API:7', refId: 7, type: 'API', code: 'score_api', label: '评分API', hasUpstream: true, hasDownstream: true },
      { id: 'VARIABLE:1', refId: 1, type: 'VARIABLE', code: 'score', label: '评分', hasUpstream: true, hasDownstream: true },
      { id: 'RULE:9', refId: 9, type: 'RULE', code: 'approve', label: '审批规则', hasUpstream: true, hasDownstream: true },
      { id: 'VARIABLE:2', refId: 2, type: 'VARIABLE', code: 'result', label: '结果', hasUpstream: true, hasDownstream: true }
    ],
    edges: [
      { from: 'DATASOURCE:8', to: 'API:7', label: '包含API' },
      { from: 'API:7', to: 'VARIABLE:1', label: '接口取数' },
      { from: 'VARIABLE:1', to: 'RULE:9', label: '规则输入' },
      { from: 'RULE:9', to: 'VARIABLE:2', label: '规则输出' }
    ]
  }
}

function mountPage() {
  lineageApi.listLineageOptions.mockResolvedValue({
    data: [{ type: 'VARIABLE', id: 1, displayName: '风险分 (riskScore)' }]
  })
  return shallowMount(LineageGraph, {
    mocks: {
      $message: { warning: vi.fn(), error: vi.fn() }
    },
    stubs: {
      'el-form': true,
      'el-form-item': true,
      'el-select': true,
      'el-option': true,
      'el-radio-group': true,
      'el-radio-button': true,
      'el-button': true
    }
  })
}

// 复现 credit_apply_count_1m：项目直连结果，多个规则共享项目和对象字段。
function sharedOutputGraph() {
  const startNode = { id: 'VARIABLE:311', refId: 311, type: 'VARIABLE', code: 'credit_apply_count_1m', label: '近1个月授信申请次数' }
  const object = { id: 'DATA_OBJECT:12', refId: 12, type: 'DATA_OBJECT', code: 'request' }
  return {
    startNode,
    nodes: [startNode,
      { id: 'PROJECT:4', refId: 4, type: 'PROJECT', code: 'project' },
      ...[32, 34, 30].map(id => ({ id: `RULE:${id}`, refId: id, type: 'RULE', code: `rule${id}`, hasUpstream: true })),
      { id: 'DATA_FIELD:125', refId: 125, type: 'DATA_FIELD', code: 'gaid', dataObject: object }
    ],
    edges: [
      { from: 'PROJECT:4', to: startNode.id, label: '项目包含' },
      ...[32, 34, 30].flatMap(id => [
        { from: 'PROJECT:4', to: `RULE:${id}`, label: '项目包含' },
        { from: 'DATA_FIELD:125', to: `RULE:${id}`, label: '规则输入' },
        { from: `RULE:${id}`, to: startNode.id, label: '规则输出' }
      ])
    ]
  }
}

afterEach(() => vi.clearAllMocks())

describe('LineageGraph', () => {
  test('项目直连结果时仍排在规则上游，展开对象不会改变规则依赖层级', async () => {
    const wrapper = mountPage()
    wrapper.vm.query.nodeId = 311
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: sharedOutputGraph() })
    await wrapper.vm.loadGraph()
    const assertForwardEdges = () => {
      for (const edge of wrapper.vm.edgeLines) {
        expect(wrapper.vm.nodePosition(edge.fromId).left, edge.key).toBeLessThan(wrapper.vm.nodePosition(edge.toId).left)
      }
      const rules = wrapper.vm.visibleBranches.filter(item => item.branch.node.type === 'RULE')
      expect(new Set(rules.map(item => wrapper.vm.nodePosition(item.branch.instanceId).left)).size).toBe(1)
    }
    assertForwardEdges()
    await wrapper.vm.toggleBranch(wrapper.vm.visibleBranches.find(item => item.branch.objectGroup).branch)
    assertForwardEdges()
    await wrapper.vm.toggleBranch(wrapper.vm.upstreamRoots.find(branch => branch.node.id === 'RULE:32'))
    assertForwardEdges()
    wrapper.unmount()
  })

  test('收起部分规则保留共享依赖连线，全部收起才隐藏独有对象', async () => {
    const wrapper = mountPage()
    wrapper.vm.query.nodeId = 311
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: sharedOutputGraph() })
    await wrapper.vm.loadGraph()
    const originalEdges = wrapper.vm.edgeLines.map(edge => edge.key).sort()
    const rules = wrapper.vm.upstreamRoots.filter(branch => branch.node.type === 'RULE')
    await wrapper.vm.toggleBranch(rules[0])
    expect(wrapper.vm.edgeLines.map(edge => edge.key).sort()).toEqual(originalEdges)
    await wrapper.vm.toggleBranch(rules[1])
    expect(wrapper.vm.edgeLines.map(edge => edge.key).sort()).toEqual(originalEdges)
    await wrapper.vm.toggleBranch(rules[2])
    expect(wrapper.vm.visibleBranches.some(item => item.branch.node.type === 'DATA_OBJECT')).toBe(false)
    expect(wrapper.vm.edgeLines.filter(edge => edge.label === '项目包含')).toHaveLength(4)
    await wrapper.vm.toggleBranch(rules[0])
    expect(wrapper.vm.edgeLines.map(edge => edge.key).sort()).toEqual(originalEdges)
    wrapper.unmount()
  })

  test('对象在下游时先展示对象再展示字段和依赖规则，不额外跨层', async () => {
    const wrapper = mountPage()
    const data = sharedOutputGraph()
    const object = data.nodes.find(node => node.type === 'DATA_FIELD').dataObject
    data.nodes.push(object)
    data.startNode = data.nodes.find(node => node.type === 'PROJECT')
    data.edges.push({ from: 'PROJECT:4', to: object.id, label: '项目包含' },
      { from: object.id, to: 'DATA_FIELD:125', label: '包含字段' })
    wrapper.vm.query.nodeId = 4
    wrapper.vm.query.nodeType = 'PROJECT'
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data })
    await wrapper.vm.loadGraph()
    const objectBranch = wrapper.vm.visibleBranches.find(item => item.branch.node.type === 'DATA_OBJECT').branch
    const sourceX = wrapper.vm.nodePosition('CURRENT').left
    const objectX = wrapper.vm.nodePosition(objectBranch.instanceId).left
    const ruleX = wrapper.vm.nodePosition('RULE:32').left
    expect(objectX).toBeGreaterThan(sourceX)
    expect(objectX).toBeLessThan(ruleX)
    await wrapper.vm.toggleBranch(objectBranch)
    expect(wrapper.vm.nodePosition('DATA_OBJECT:12').left).toBeLessThan(wrapper.vm.nodePosition('DATA_FIELD:125').left)
    expect(wrapper.vm.nodePosition('DATA_FIELD:125').left).toBeLessThan(wrapper.vm.nodePosition('RULE:32').left)
    wrapper.unmount()
  })
  test('空画布不注册滚轮监听，生成后缩放并阻止页面滚动，重置后移除监听', async () => {
    const addListener = vi.spyOn(Element.prototype, 'addEventListener')
    const removeListener = vi.spyOn(Element.prototype, 'removeEventListener')
    const wrapper = mountPage()
    try {
      const graphWrap = wrapper.find('.graph-wrap').element
      const wheelCalls = () => addListener.mock.calls.filter((args, index) =>
        addListener.mock.contexts[index] === graphWrap && args[0] === 'wheel'
      )
      expect(wheelCalls()).toHaveLength(0)

      wrapper.vm.query.nodeId = 1
      lineageApi.getLineageGraph.mockResolvedValueOnce({ data: graphResponse() })
      await wrapper.vm.loadGraph()
      await nextTick()
      expect(wheelCalls()).toHaveLength(1)
      expect(wheelCalls()[0][2]).toEqual({ passive: false })
      wrapper.vm.viewport = { x: 0, y: 0, scale: 1 }
      const wheel = new WheelEvent('wheel', { deltaY: -100, cancelable: true })
      graphWrap.dispatchEvent(wheel)
      expect(wheel.defaultPrevented).toBe(true)
      expect(wrapper.vm.viewport.scale).toBeCloseTo(1.1)

      wrapper.vm.resetGraph()
      await nextTick()
      expect(removeListener.mock.calls.some((args, index) =>
        removeListener.mock.contexts[index] === graphWrap && args[0] === 'wheel'
      )).toBe(true)
      const emptyWheel = new WheelEvent('wheel', { deltaY: -100, cancelable: true })
      graphWrap.dispatchEvent(emptyWheel)
      expect(emptyWheel.defaultPrevented).toBe(false)
    } finally {
      wrapper.unmount()
      addListener.mockRestore()
      removeListener.mockRestore()
    }
  })

  test('created 后按默认变量类型加载起点选项', async () => {
    const wrapper = mountPage()
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(lineageApi.listLineageOptions).toHaveBeenCalledWith({ nodeType: 'VARIABLE', keyword: '' })
    expect(wrapper.vm.options[0].displayName).toBe('风险分 (riskScore)')
    wrapper.unmount()
  })

  test('首次生成在中间节点两侧展示上下游各两跳', async () => {
    const wrapper = mountPage()
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: graphResponse() })

    await wrapper.vm.loadGraph()

    expect(lineageApi.getLineageGraph).toHaveBeenCalledWith({
      nodeType: 'VARIABLE', nodeId: 1, direction: 'ALL', maxDepth: 2
    })
    expect(wrapper.vm.upstreamRoots[0].node.id).toBe('API:7')
    expect(wrapper.vm.upstreamRoots[0].children[0].node.id).toBe('DATASOURCE:8')
    expect(wrapper.vm.downstreamRoots[0].node.id).toBe('RULE:9')
    expect(wrapper.vm.downstreamRoots[0].children[0].node.id).toBe('VARIABLE:2')
    expect(wrapper.vm.visibleBranches).toHaveLength(4)

    const current = wrapper.vm.mindMapLayout.positions.CURRENT
    const upstream = wrapper.vm.mindMapLayout.positions[wrapper.vm.upstreamRoots[0].instanceId]
    const downstream = wrapper.vm.mindMapLayout.positions[wrapper.vm.downstreamRoots[0].instanceId]
    expect(upstream.left).toBeLessThan(current.left)
    expect(downstream.left).toBeGreaterThan(current.left)
    expect(wrapper.vm.edgeLines.find(edge => edge.fromId === 'API:7').toId).toBe('CURRENT')
    expect(wrapper.vm.edgeLines.find(edge => edge.toId === 'RULE:9').fromId).toBe('CURRENT')
    wrapper.unmount()
  })

  test('首次生成后将血缘图适配到可视区域', async () => {
    const wrapper = mountPage()
    const graphWrap = wrapper.find('.graph-wrap').element
    Object.defineProperty(graphWrap, 'clientWidth', { value: 800 })
    Object.defineProperty(graphWrap, 'clientHeight', { value: 440 })
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: graphResponse() })

    await wrapper.vm.loadGraph()
    await nextTick()

    expect(wrapper.vm.viewport.scale).toBeLessThanOrEqual(1)
    expect(wrapper.vm.viewport.x).toBeGreaterThanOrEqual(0)
    expect(wrapper.vm.viewport.y).toBeGreaterThanOrEqual(0)
    wrapper.unmount()
  })

  test('支持以画布中心缩放并限制缩放范围', () => {
    const wrapper = mountPage()
    const graphWrap = wrapper.find('.graph-wrap').element
    Object.defineProperty(graphWrap, 'clientWidth', { value: 1000 })
    Object.defineProperty(graphWrap, 'clientHeight', { value: 500 })
    graphWrap.getBoundingClientRect = () => ({ left: 0, top: 0, width: 1000, height: 500 })

    wrapper.vm.viewport = { x: 100, y: 50, scale: 1 }
    wrapper.vm.setZoom(1.25)

    expect(wrapper.vm.viewport.scale).toBe(1.25)
    expect(wrapper.vm.viewport.x).toBe(0)
    expect(wrapper.vm.viewport.y).toBe(0)

    wrapper.vm.setZoom(99)
    expect(wrapper.vm.viewport.scale).toBe(2)
    wrapper.vm.setZoom(0)
    expect(wrapper.vm.viewport.scale).toBe(0.4)
    wrapper.unmount()
  })

  test('节点拖动只覆盖当前组件实例坐标并同步更新连线', async () => {
    const wrapper = mountPage()
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: graphResponse() })
    await wrapper.vm.loadGraph()
    const branch = wrapper.vm.downstreamRoots[0]
    const instanceId = branch.instanceId
    const originalPosition = wrapper.vm.nodePosition(instanceId)
    const originalPath = wrapper.vm.edgeLines.find(edge => edge.toId === instanceId).path

    wrapper.vm.beginNodeDrag({ clientX: 200, clientY: 160, currentTarget: { setPointerCapture: vi.fn() } }, instanceId)
    wrapper.vm.onPointerMove({ clientX: 264, clientY: 200 })
    wrapper.vm.endPointerInteraction()

    expect(wrapper.vm.positionOverrides[instanceId]).toEqual({
      left: originalPosition.left + 64 / wrapper.vm.viewport.scale,
      top: originalPosition.top + 40 / wrapper.vm.viewport.scale
    })
    expect(wrapper.vm.edgeLines.find(edge => edge.toId === instanceId).path).not.toBe(originalPath)

    const anotherWrapper = mountPage()
    expect(anotherWrapper.vm.positionOverrides).toEqual({})
    anotherWrapper.unmount()
    wrapper.unmount()
  })

  test('可拖动画布且一键最佳分布会清除手工坐标并重新适配', async () => {
    const wrapper = mountPage()
    const graphWrap = wrapper.find('.graph-wrap').element
    Object.defineProperty(graphWrap, 'clientWidth', { value: 1000 })
    Object.defineProperty(graphWrap, 'clientHeight', { value: 500 })
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: graphResponse() })
    await wrapper.vm.loadGraph()

    const originalViewport = { ...wrapper.vm.viewport }
    wrapper.vm.beginCanvasPan({ clientX: 100, clientY: 100, currentTarget: { setPointerCapture: vi.fn() } })
    wrapper.vm.onPointerMove({ clientX: 145, clientY: 125 })
    wrapper.vm.endPointerInteraction()
    expect(wrapper.vm.viewport.x).toBe(originalViewport.x + 45)
    expect(wrapper.vm.viewport.y).toBe(originalViewport.y + 25)

    wrapper.vm.positionOverrides = { CURRENT: { left: 16, top: 24 } }
    wrapper.vm.resetToBestLayout()
    expect(wrapper.vm.positionOverrides).toEqual({})
    expect(wrapper.vm.viewport.scale).toBeLessThanOrEqual(1)
    expect(wrapper.find('[aria-label="一键回到最佳分布"]').exists()).toBe(true)
    wrapper.unmount()
  })

  test('第二跳节点按需展开并在收起后复用缓存', async () => {
    const wrapper = mountPage()
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: graphResponse() })
    await wrapper.vm.loadGraph()
    const branch = wrapper.vm.upstreamRoots[0].children[0]
    lineageApi.getLineageGraph.mockResolvedValueOnce({
      data: {
        startNode: graphResponse().nodes[0],
        nodes: [
          graphResponse().nodes[0],
          { id: 'PROJECT:5', refId: 5, type: 'PROJECT', code: 'global', label: '全局项目', hasUpstream: false, hasDownstream: true }
        ],
        edges: [{ from: 'PROJECT:5', to: 'DATASOURCE:8', label: '项目包含' }]
      }
    })

    await wrapper.vm.toggleBranch(branch)

    expect(lineageApi.getLineageGraph).toHaveBeenLastCalledWith({
      nodeType: 'DATASOURCE', nodeId: 8, direction: 'UPSTREAM', maxDepth: 1
    })
    expect(branch.children[0].node.id).toBe('PROJECT:5')
    expect(branch.expanded).toBe(true)

    await wrapper.vm.toggleBranch(branch)
    expect(branch.expanded).toBe(false)
    await wrapper.vm.toggleBranch(branch)
    expect(branch.expanded).toBe(true)
    expect(lineageApi.getLineageGraph).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  test('收起父节点会隐藏整条后代分支', async () => {
    const wrapper = mountPage()
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: graphResponse() })
    await wrapper.vm.loadGraph()

    await wrapper.vm.toggleBranch(wrapper.vm.upstreamRoots[0])

    expect(wrapper.vm.visibleBranches.map(item => item.branch.node.id)).toEqual([
      'API:7', 'RULE:9', 'VARIABLE:2'
    ])
    wrapper.unmount()
  })

  test('共享业务节点只展示一次且保留不同路径的连线', async () => {
    const wrapper = mountPage()
    const data = graphResponse()
    data.nodes.splice(2, 0,
      { id: 'API:6', refId: 6, type: 'API', code: 'backup_api', label: '备用API', hasUpstream: true, hasDownstream: true })
    data.edges.splice(1, 0,
      { from: 'DATASOURCE:8', to: 'API:6', label: '包含API' },
      { from: 'API:6', to: 'VARIABLE:1', label: '接口取数' })

    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data })
    await wrapper.vm.loadGraph()
    expect(wrapper.vm.visibleBranches.filter(item => item.branch.node.id === 'DATASOURCE:8')).toHaveLength(1)
    expect(wrapper.vm.edgeLines.filter(edge => edge.label === '包含API')).toHaveLength(2)
    await wrapper.vm.toggleBranch(wrapper.vm.upstreamRoots[0])
    expect(wrapper.vm.visibleBranches.filter(item => item.branch.node.id === 'DATASOURCE:8')).toHaveLength(1)
    wrapper.unmount()
  })

  test('当前路径出现循环引用时生成不可继续展开的终止节点', async () => {
    const wrapper = mountPage()
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: graphResponse() })
    await wrapper.vm.loadGraph()
    const branch = wrapper.vm.downstreamRoots[0].children[0]
    lineageApi.getLineageGraph.mockResolvedValueOnce({
      data: {
        startNode: graphResponse().nodes[4],
        nodes: [graphResponse().nodes[3], graphResponse().nodes[4]],
        edges: [{ from: 'VARIABLE:2', to: 'RULE:9', label: '规则输入' }]
      }
    })

    await wrapper.vm.toggleBranch(branch)

    expect(branch.children[0].node.id).toBe('RULE:9')
    expect(branch.children[0].cycle).toBe(true)
    expect(wrapper.vm.canToggle(branch.children[0])).toBe(false)
    expect(wrapper.vm.visibleBranches.filter(item => item.branch.node.id === 'RULE:9')).toHaveLength(1)
    expect(wrapper.vm.edgeLines.some(edge => edge.label === '规则输入' && edge.fromId === branch.instanceId)).toBe(true)
    wrapper.unmount()
  })

  test('同一数据对象默认合并字段，展开后保留对象和独立字段节点', async () => {
    const wrapper = mountPage()
    const data = graphResponse()
    const object = { id: 'DATA_OBJECT:30', refId: 30, type: 'DATA_OBJECT', code: 'Request_ABC', label: '申请数据' }
    data.nodes.push(...[31, 32].map(id => ({
      id: `DATA_FIELD:${id}`, refId: id, type: 'DATA_FIELD', code: `field${id}`, label: `字段${id}`, dataObject: object
    })))
    data.edges.push(...[31, 32].map(id => ({ from: `DATA_FIELD:${id}`, to: 'VARIABLE:1', label: '输入' })))
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data })
    await wrapper.vm.loadGraph()
    const objects = () => wrapper.vm.visibleBranches.filter(item => item.branch.node.type === 'DATA_OBJECT')
    const fields = () => wrapper.vm.visibleBranches.filter(item => item.branch.node.type === 'DATA_FIELD')
    expect(objects()).toHaveLength(1)
    expect(fields()).toHaveLength(0)
    expect(objects()[0].branch.node.code).toBe('Request_ABC')
    await wrapper.vm.toggleBranch(objects()[0].branch)
    expect(objects()).toHaveLength(1)
    expect(fields()).toHaveLength(2)
    expect(wrapper.vm.edgeLines.filter(edge => edge.label === '包含字段')).toHaveLength(2)
    const objectPosition = wrapper.vm.nodePosition(objects()[0].branch.instanceId)
    expect(fields().every(item => wrapper.vm.nodePosition(item.branch.instanceId).left > objectPosition.left)).toBe(true)
    await wrapper.vm.toggleBranch(objects()[0].branch)
    expect(fields()).toHaveLength(0)
    expect(lineageApi.getLineageGraph).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  test('共享节点展开后点击一次即可收起所有路径上的后代', async () => {
    const wrapper = mountPage()
    const data = graphResponse()
    data.nodes.push({ id: 'API:6', refId: 6, type: 'API', code: 'backup', hasUpstream: true })
    data.edges.push({ from: 'API:6', to: 'VARIABLE:1' }, { from: 'DATASOURCE:8', to: 'API:6' })
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data })
    await wrapper.vm.loadGraph()
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: {
      startNode: data.nodes[0],
      nodes: [{ id: 'PROJECT:5', refId: 5, type: 'PROJECT', code: 'project' }],
      edges: [{ from: 'PROJECT:5', to: 'DATASOURCE:8', label: '项目包含' }]
    } })
    const shared = () => wrapper.vm.visibleBranches.find(item => item.branch.node.id === 'DATASOURCE:8').branch
    await wrapper.vm.toggleBranch(shared())
    expect(wrapper.vm.visibleBranches.filter(item => item.branch.node.id === 'PROJECT:5')).toHaveLength(1)
    await wrapper.vm.toggleBranch(wrapper.vm.upstreamRoots[0])
    expect(wrapper.vm.visibleBranches.filter(item => item.branch.node.id === 'PROJECT:5')).toHaveLength(1)
    await wrapper.vm.toggleBranch(shared())
    expect(wrapper.vm.visibleBranches.filter(item => item.branch.node.id === 'PROJECT:5')).toHaveLength(0)
    wrapper.unmount()
  })

  test('提供两跳展开和静态分析边界说明', () => {
    const wrapper = mountPage()

    expect(wrapper.vm.lineageGuideCards.map(item => item.title)).toEqual([
      '选择起点',
      '两跳展开',
      '静态分析边界'
    ])
    expect(wrapper.vm.lineageGuideCards[1].text).toContain('两层')
    expect(wrapper.vm.lineageGuideCards[2].text).toContain('静态分析')
    wrapper.unmount()
  })
})
