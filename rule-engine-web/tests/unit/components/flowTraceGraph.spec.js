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
})
