import { mount } from '@test-utils'
import { nextTick } from 'vue'
import FlowTraceGraph from '@/components/common/FlowTraceGraph.vue'

function mountGraph(cards = []) {
  return mount(FlowTraceGraph, {
    props: {
      modelData: {
        nodes: [
          { id: 'start', type: 'start', name: '开始', x: 100, y: 180 },
          { id: 'check', type: 'decision', name: '额度检查', x: 300, y: 180 },
          { id: 'pass', type: 'task', name: '通过处理', x: 520, y: 120, actionData: [{ target: 'decision' }] },
          { id: 'end', type: 'end', name: '结束', x: 740, y: 180 },
        ],
        edges: [
          { id: 'start-check', source: 'start', target: 'check' },
          { id: 'check-pass', source: 'check', target: 'pass', conditionExpression: 'amount >= 1000' },
          { id: 'pass-end', source: 'pass', target: 'end' },
        ],
      },
      cards,
    },
  })
}

describe('FlowTraceGraph', () => {
  it('按执行卡片回填节点和边状态，并提供节点详情', async () => {
    const wrapper = mountGraph([
      {
        stepType: 'decision',
        title: '额度检查',
        status: 'hit',
        conditions: [{ varLabel: '申请金额', operator: '大于等于', compareDisplay: '1000', result: true }],
      },
      {
        stepType: 'assign',
        title: '通过处理',
        targetVar: 'decision',
        resultDisplay: 'PASS',
      },
      { stepType: 'end', title: '最终结果汇总' },
    ])

    const graph = wrapper.vm.graphSnapshot
    expect(graph.nodes.map(node => node.properties.traceStatus)).toEqual(['done', 'hit', 'done', 'done'])
    expect(graph.edges.map(edge => edge.properties.traceStatus)).toEqual(['hit', 'hit', 'hit'])

    wrapper.vm.selectNode(graph.nodes[1])
    await nextTick()
    expect(wrapper.vm.selected).toMatchObject({ title: '额度检查', status: 'hit' })
    expect(wrapper.find('[data-testid="flow-trace-detail"]').text()).toContain('额度检查')
    wrapper.unmount()
  })

  it('LogicFlow 不可用时保留可点击的节点降级视图', () => {
    const wrapper = mountGraph()
    expect(wrapper.find('[data-testid="flow-trace-fallback"]').exists()).toBe(true)
    expect(wrapper.findAll('.flow-trace-fallback__node')).toHaveLength(4)
    wrapper.unmount()
  })

  it('同时展示分支名和快照中的真实条件', async () => {
    const wrapper = mountGraph()
    const { nodes } = wrapper.props('modelData')
    const expression = 'applicant.' + 'longField'.repeat(60) + ' == false'
    await wrapper.setProps({ modelData: { logicflow: { nodes, edges: [{
      id: 'branch', sourceNodeId: 'check', targetNodeId: 'pass',
      properties: { conditionName: '通过分支', conditionExpr: expression },
    }] } } })
    const edge = wrapper.vm.graphSnapshot.edges[0]
    await wrapper.setData({ selected: edge.trace })
    const detail = wrapper.get('[data-testid="flow-trace-detail"]')
    expect(detail.text()).toContain('通过分支')
    expect(detail.get('pre').text()).toBe(expression)
    wrapper.unmount()
  })

  it('在判断详情中保留长变量编码和条件实际值', async () => {
    const code = 'creditAssessment'.repeat(40)
    const wrapper = mountGraph([{ stepType: 'decision', title: '额度检查', status: 'miss',
      conditions: [{ varCode: code, varLabel: '额度', operator: '等于', compareDisplay: '1', actualValue: false, result: false }],
    }])
    wrapper.vm.selectNode(wrapper.vm.graphSnapshot.nodes[1])
    await nextTick()
    const detail = wrapper.get('[data-testid="flow-trace-detail"]')
    expect(detail.text()).toContain(code)
    expect(detail.text()).toContain('实际值')
    expect(detail.text()).toContain('false')
    wrapper.unmount()
  })

  it('同时有引擎快照和画布快照时按连线 ID 还原引擎条件', async () => {
    const wrapper = mountGraph()
    const model = wrapper.props('modelData')
    await wrapper.setProps({ modelData: { ...model, logicflow: { nodes: model.nodes,
      edges: [{ id: 'check-pass', sourceNodeId: 'check', targetNodeId: 'pass', properties: { conditionName: '额度校验' } }],
    } } })
    expect(wrapper.vm.graphSnapshot.edges[0].trace).toMatchObject({ title: '额度校验', condition: 'amount >= 1000' })
    wrapper.unmount()
  })

  it('结束节点保留全部输出字段及空值', async () => {
    const result = { first: 0, second: false, third: null, fourth: '' }
    const wrapper = mountGraph([{ stepType: 'assign', targetVar: '_result', result, resultDisplay: 'first:0' }])
    wrapper.vm.selectNode(wrapper.vm.graphSnapshot.nodes[3])
    await nextTick()
    expect(JSON.parse(wrapper.get('.flow-trace-detail__section pre').text())).toEqual(result)
    wrapper.unmount()
  })
})
