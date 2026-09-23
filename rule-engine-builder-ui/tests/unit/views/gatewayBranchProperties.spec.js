import { markRaw } from 'vue'
import { flushPromises, mount } from '@test-utils'
import DecisionTree from '@/views/designer/DecisionTree.vue'
import DecisionFlow from '@/views/designer/DecisionFlow.vue'
import EdgePropertiesEditor from '@/components/flow/EdgePropertiesEditor.vue'
import * as definitionApi from '@/api/definition'

const designers = [['决策树', DecisionTree], ['决策流', DecisionFlow]]

function createGraph() {
  return {
    nodes: [
      { id: 'gateway', type: 'exclusive-gateway', x: 200, y: 100, properties: { nodeName: '额度判断' } },
      { id: 'pass', type: 'end-event', x: 400, y: 50, properties: { nodeName: '通过' } },
      { id: 'reject', type: 'end-event', x: 400, y: 200, properties: { nodeName: '拒绝' } },
    ],
    edges: [
      { id: 'yes', type: 'polyline', sourceNodeId: 'gateway', targetNodeId: 'pass', properties: { conditionName: '达标', conditionExpr: 'score > 600', priority: 10 } },
      { id: 'no', type: 'polyline', sourceNodeId: 'gateway', targetNodeId: 'reject', properties: { conditionName: '未达标', conditionExpr: '', priority: 20 } },
    ],
  }
}

async function mountDesigner(component, graph = createGraph()) {
  definitionApi.getDefinition.mockResolvedValue({ data: { id: 30, projectId: null } })
  definitionApi.listRuleRevisions.mockResolvedValue({ data: [] })
  definitionApi.listPublishedVersions.mockResolvedValue({ data: [] })
  const lf = markRaw({
    getGraphData: () => graph,
    graphModel: { edges: graph.edges },
    getNodeEdges: id => graph.edges.filter(edge => edge.sourceNodeId === id || edge.targetNodeId === id),
    getNodeModelById: id => graph.nodes.find(node => node.id === id),
    getEdgeModelById: id => graph.edges.find(edge => edge.id === id),
    getProperties: id => [...graph.edges, ...graph.nodes].find(item => item.id === id)?.properties || {},
    setProperties: vi.fn((id, props) => { Object.assign([...graph.edges, ...graph.nodes].find(item => item.id === id).properties, props) }),
    deleteProperty: vi.fn((id, key) => { delete graph.edges.find(edge => edge.id === id).properties[key] }),
    changeEdgeType: vi.fn((id, type) => { graph.edges.find(edge => edge.id === id).type = type }),
    updateText: vi.fn((id, text) => { graph.edges.find(edge => edge.id === id).text = text }),
    destroy: vi.fn(),
  })
  const wrapper = mount({ ...component, mounted() {} }, {
    attachTo: document.body,
    mocks: { $route: { params: { id: 30 }, query: {} }, $router: { push: vi.fn(), back: vi.fn() } },
    stubs: {
      RuleDraftReadOnly: true, RuleDesignerActionBar: true, RuleDesignerStatus: true,
      RuleDesignerDialogs: true, RuleDesignerVersionSelect: true, DesignerTestDialog: true,
      GraphDesignerNavigator: true, EndNodeScopeDialog: true, FlowNodeAddMenu: true,
      ScriptPanel: true, ActionBlockEditor: true, ConditionGroupEditor: true,
    },
  })
  await flushPromises()
  wrapper.vm.lf = lf
  wrapper.vm.selectNodeData({ id: 'gateway' })
  await wrapper.vm.$nextTick()
  return { wrapper, graph, lf }
}

describe.each(designers)('%s 条件节点分支属性', (name, component) => {
  test('点击条件节点后直接展开所有出边属性与条件，并可独立收起', async () => {
    const { wrapper } = await mountDesigner(component)
    try {
      const cards = wrapper.findAll('.gateway-branch')
      expect(cards).toHaveLength(2)
      expect(cards[0].text()).toContain('通过')
      expect(cards[0].text()).toContain('score > 600')
      expect(cards[1].text()).toContain('默认分支（else）')
      expect(cards[0].get('.branch-editor').isVisible()).toBe(true)
      await cards[0].get('.branch-toggle').trigger('click')
      expect(cards[0].get('.branch-toggle').attributes('aria-expanded')).toBe('false')
      expect(cards[0].get('.branch-editor').isVisible()).toBe(false)
      expect(cards[1].get('.branch-editor').isVisible()).toBe(true)
      expect(wrapper.vm.activeElement.id).toBe('gateway')
    } finally { wrapper.unmount() }
  })

  test('节点内修改标签、线型和优先级同步到画布，单独选边再修改仍保持一致', async () => {
    const { wrapper, graph, lf } = await mountDesigner(component)
    try {
      const editor = wrapper.findAllComponents(EdgePropertiesEditor)[0]
      editor.vm.updateProperty('conditionName', '优质客户')
      await wrapper.vm.$nextTick()
      editor.vm.updateProperty('edgeLineType', 'bezier')
      await wrapper.vm.$nextTick()
      editor.vm.updateProperty('priority', 30)
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.activeElement.id).toBe('gateway')
      expect(graph.edges[0]).toMatchObject({ type: 'bezier', text: '优质客户', properties: { conditionName: '优质客户', conditionExpr: 'score > 600', priority: 30 } })
      expect(lf.changeEdgeType).toHaveBeenCalledWith('yes', 'bezier')
      expect(wrapper.findAll('.gateway-branch').map(card => card.attributes('data-edge-id'))).toEqual(['no', 'yes'])

      wrapper.vm.selectEdgeById('yes')
      await wrapper.vm.$nextTick()
      const selectedEditor = wrapper.getComponent(EdgePropertiesEditor)
      expect(selectedEditor.props('modelValue')).toMatchObject({ conditionName: '优质客户', edgeLineType: 'bezier', priority: 30 })
      selectedEditor.vm.updateProperty('conditionName', '已复核')
      await wrapper.vm.$nextTick()
      wrapper.vm.selectNodeData({ id: 'gateway' })
      await wrapper.vm.$nextTick()
      expect(wrapper.get('[data-edge-id="yes"] .branch-title').text()).toContain('已复核')
      expect(graph.edges[1].properties.conditionName).toBe('未达标')
      wrapper.get('[data-edge-id="yes"]').getComponent(EdgePropertiesEditor).vm.updateProperty('edgeLineType', '')
      await wrapper.vm.$nextTick()
      expect(graph.edges[0].properties).not.toHaveProperty('edgeLineType')
      expect(graph.edges[0].type).toBe('polyline')
    } finally { wrapper.unmount() }
  })

  test('可视化条件保留变量ID，脚本覆盖旧配置且收起、保存、重载后不丢失', async () => {
    const { wrapper, graph } = await mountDesigner(component)
    let reloaded
    try {
      const editor = wrapper.findAllComponents(EdgePropertiesEditor)[0]
      const root = editor.props('conditionRoot')
      root.children[0].leftOperand = { kind: 'REFERENCE', code: 'score', value: 'score', refId: 91, refType: 'VARIABLE', valueType: 'NUMBER' }
      root.children[0].rightOperand = { kind: 'LITERAL', value: 720, valueType: 'NUMBER' }
      editor.vm.$emit('condition-change')
      await wrapper.vm.$nextTick()
      expect(graph.edges[0].properties.conditionConfig.children[0].leftOperand.refId).toBe(91)
      expect(graph.edges[0].properties.conditionExpr).toContain('720')

      editor.vm.$emit('update:mode', 'script')
      await wrapper.vm.$nextTick()
      editor.vm.updateProperty('conditionExpr', 'riskAllowed(score)')
      await wrapper.vm.$nextTick()
      await wrapper.get('[data-edge-id="yes"] .branch-toggle').trigger('click')
      expect(wrapper.get('[data-edge-id="yes"] .branch-condition-summary').text()).toContain('riskAllowed(score)')
      const saved = wrapper.vm.buildBackendModel()
      expect(saved.edges[0]).toMatchObject({ conditionExpression: 'riskAllowed(score)', conditionConfig: null, priority: 10 })
      expect(saved.edges[1]).toMatchObject({ conditionExpression: '', conditionConfig: null })
      reloaded = await mountDesigner(component, saved.logicflow)
      expect(reloaded.wrapper.findAllComponents(EdgePropertiesEditor)[0].props()).toMatchObject({ mode: 'script', modelValue: { conditionExpr: 'riskAllowed(score)', conditionConfig: null } })

      wrapper.vm.selectEdgeById('no')
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.buildBackendModel().edges[1]).toMatchObject({ conditionExpression: '', conditionConfig: null })
    } finally {
      reloaded?.wrapper.unmount()
      wrapper.unmount()
    }
  })

  test('分支上移不触发折叠，清空可视化条件后正确恢复默认分支', async () => {
    const { wrapper, graph } = await mountDesigner(component)
    try {
      await wrapper.get('[data-edge-id="no"] .edge-priority-up').trigger('click')
      expect(graph.edges.map(edge => edge.properties.priority)).toEqual([20, 10])
      expect(wrapper.get('[data-edge-id="no"] .branch-toggle').attributes('aria-expanded')).toBe('true')
      const editor = wrapper.get('[data-edge-id="yes"]').getComponent(EdgePropertiesEditor)
      editor.props('conditionRoot').children.splice(0)
      editor.vm.$emit('condition-change')
      await wrapper.vm.$nextTick()
      expect(graph.edges[0].properties).toMatchObject({ conditionExpr: '', conditionConfig: null })
      expect(wrapper.get('[data-edge-id="yes"] .branch-condition-summary').text()).toContain('默认分支（else）')
    } finally { wrapper.unmount() }
  })

  test('旧连线变量ID在节点内编辑后仍保留，默认优先级按该节点的出边顺序计算', async () => {
    const graph = createGraph()
    graph.edges.forEach(edge => { delete edge.properties.priority })
    graph.edges[0].properties.leftVarId = 91
    graph.edges[0].properties.leftRefType = 'VARIABLE'
    const { wrapper } = await mountDesigner(component, graph)
    try {
      const editor = wrapper.findAllComponents(EdgePropertiesEditor)[0]
      editor.vm.$emit('condition-change')
      await wrapper.vm.$nextTick()
      expect(graph.edges[0].properties.conditionConfig.children[0].leftOperand.refId).toBe(91)
      wrapper.vm.selectEdgeById('no')
      await wrapper.vm.$nextTick()
      expect(wrapper.getComponent(EdgePropertiesEditor).props('modelValue').priority).toBe(20)
      expect(wrapper.vm.buildBackendModel().edges.map(edge => edge.priority)).toEqual([10, 20])
    } finally { wrapper.unmount() }
  })
})
