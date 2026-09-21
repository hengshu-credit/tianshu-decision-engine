import { mount } from '@test-utils'
import graphDesignerToolsMixin from '@/mixins/graphDesignerToolsMixin'

describe('graphDesignerToolsMixin', () => {
  test('打开属性面板并同步画布尺寸后再定位，避免按旧宽度居中', async () => {
    const calls = []
    const node = { id: 'node-1' }
    const context = {
      lf: {
        selectElementById: vi.fn(),
        resize: (width, height) => calls.push(['resize', width, height]),
        focusOn: id => calls.push(['focus', id]),
      },
      $refs: { canvasContainer: { clientWidth: 600, clientHeight: 400 } },
      $nextTick: async () => {},
      graphDataForTools: () => ({ nodes: [node] }),
      selectNodeData: () => calls.push(['panel']),
    }
    await graphDesignerToolsMixin.methods.locateGraphElement.call(context, { elementId: 'node-1', baseType: 'node' })
    expect(calls).toEqual([['panel'], ['resize', 600, 400], ['focus', 'node-1']])
  })

  test('定位完成后清空临时选择，允许业务人员再次定位同一节点', async () => {
    const graph = {
      nodes: [{ id: 'node-1', type: 'script-task', properties: { nodeName: '准入规则' } }],
      edges: [],
    }
    const wrapper = mount({
      mixins: [graphDesignerToolsMixin],
      data() {
        return {
          locatedNodeId: '',
          lf: {
            getGraphData: () => graph,
            selectElementById: () => {},
            focusOn: () => {},
          },
        }
      },
      methods: {
        selectNodeData(node) {
          this.locatedNodeId = node.id
        },
      },
      template: '<div />',
    })

    wrapper.vm.graphNavigationOptions = [
      {
        key: 'NODE:node-1:0',
        kind: 'NODE',
        baseType: 'node',
        elementId: 'node-1',
      },
    ]
    wrapper.vm.graphNavigationTarget = 'NODE:node-1:0'

    wrapper.vm.locateGraphNavigationItem('NODE:node-1:0')
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.locatedNodeId).toBe('node-1')
    expect(wrapper.vm.graphNavigationTarget).toBe('')
    wrapper.unmount()
  })
})
