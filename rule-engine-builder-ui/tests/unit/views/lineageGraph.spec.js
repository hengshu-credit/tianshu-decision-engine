import { shallowMount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'
jest.unmock('element-ui')
import ElementUI from 'element-ui'
import LineageGraph from '@/views/lineage/LineageGraph.vue'
import * as lineageApi from '@/api/lineage'

function createLocal() {
  const localVue = createLocalVue()
  localVue.use(ElementUI)
  return localVue
}

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
    localVue: createLocal(),
    mocks: {
      $message: { warning: jest.fn(), error: jest.fn() }
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

afterEach(() => jest.clearAllMocks())

describe('LineageGraph', () => {
  test('created 后按默认变量类型加载起点选项', async () => {
    const wrapper = mountPage()
    await Vue.nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(lineageApi.listLineageOptions).toHaveBeenCalledWith({ nodeType: 'VARIABLE', keyword: '' })
    expect(wrapper.vm.options[0].displayName).toBe('风险分 (riskScore)')
    wrapper.destroy()
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
    expect(wrapper.vm.edgeLines[0].toId).toBe('CURRENT')
    expect(wrapper.vm.edgeLines[2].fromId).toBe('CURRENT')
    wrapper.destroy()
  })

  test('首次生成后将滚动区域对准当前节点', async () => {
    const wrapper = mountPage()
    const graphWrap = wrapper.find('.graph-wrap').element
    Object.defineProperty(graphWrap, 'clientWidth', { value: 800 })
    Object.defineProperty(graphWrap, 'scrollWidth', { value: 1352 })
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: graphResponse() })

    await wrapper.vm.loadGraph()
    await Vue.nextTick()

    expect(graphWrap.scrollLeft).toBe(276)
    wrapper.destroy()
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
    wrapper.destroy()
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
    wrapper.destroy()
  })

  test('共享业务节点在不同路径生成独立展示实例', () => {
    const wrapper = mountPage()
    const data = graphResponse()
    data.nodes.splice(2, 0,
      { id: 'API:6', refId: 6, type: 'API', code: 'backup_api', label: '备用API', hasUpstream: true, hasDownstream: true })
    data.edges.splice(1, 0,
      { from: 'DATASOURCE:8', to: 'API:6', label: '包含API' },
      { from: 'API:6', to: 'VARIABLE:1', label: '接口取数' })

    const roots = wrapper.vm.buildBranches(data, 'UPSTREAM', 2)

    expect(roots).toHaveLength(2)
    expect(roots[0].children[0].node.id).toBe('DATASOURCE:8')
    expect(roots[1].children[0].node.id).toBe('DATASOURCE:8')
    expect(roots[0].children[0].instanceId).not.toBe(roots[1].children[0].instanceId)
    wrapper.destroy()
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
    wrapper.destroy()
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
    wrapper.destroy()
  })
})
