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

function mountPage() {
  lineageApi.listLineageOptions.mockResolvedValue({ data: [{ type: 'VARIABLE', id: 1, displayName: '风险分 (riskScore)' }] })
  return shallowMount(LineageGraph, {
    localVue: createLocal(),
    mocks: {
      $message: { warning: jest.fn() }
    },
    stubs: {
      'el-form': true,
      'el-form-item': true,
      'el-select': true,
      'el-option': true,
      'el-radio-group': true,
      'el-radio-button': true,
      'el-button': true,
      'el-tag': true
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

  test('loadGraph 写入血缘图节点和边', async () => {
    const wrapper = mountPage()
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({
      data: {
        startNode: { id: 'VARIABLE:1', type: 'VARIABLE', code: 'riskScore', label: '风险分' },
        nodes: [
          { id: 'API:7', type: 'API', code: 'score_api', label: '评分API' },
          { id: 'VARIABLE:1', type: 'VARIABLE', code: 'riskScore', label: '风险分' }
        ],
        edges: [{ from: 'API:7', to: 'VARIABLE:1', label: '接口取数' }]
      }
    })

    await wrapper.vm.loadGraph()

    expect(lineageApi.getLineageGraph).toHaveBeenCalledWith({ nodeType: 'VARIABLE', nodeId: 1, direction: 'ALL' })
    expect(wrapper.vm.nodes).toHaveLength(2)
    expect(wrapper.vm.edgeLines).toHaveLength(1)
    expect(wrapper.vm.nodeColor('API')).toBe('#EA580C')
    wrapper.destroy()
  })

  test('拖拽节点后更新节点位置并带动连线', async () => {
    const wrapper = mountPage()
    wrapper.vm.nodes = [
      { id: 'API:7', type: 'API', code: 'score_api', label: '评分API' },
      { id: 'VARIABLE:1', type: 'VARIABLE', code: 'riskScore', label: '风险分' }
    ]
    wrapper.vm.edges = [{ from: 'API:7', to: 'VARIABLE:1', label: '接口取数' }]
    wrapper.vm.nodePositions = wrapper.vm.layoutNodes(wrapper.vm.nodes)
    const before = wrapper.vm.edgeLines[0].x1

    wrapper.vm.startDrag(wrapper.vm.nodes[0], { clientX: 10, clientY: 10 })
    wrapper.vm.onDragMove({ clientX: 60, clientY: 30 })
    wrapper.vm.stopDrag()

    expect(wrapper.vm.nodePositions['API:7'].left).toBe(82)
    expect(wrapper.vm.nodePositions['API:7'].top).toBe(52)
    expect(wrapper.vm.edgeLines[0].x1).toBe(before + 50)
    wrapper.destroy()
  })

  test('提供血缘方向和静态分析边界说明', () => {
    const wrapper = mountPage()

    expect(wrapper.vm.lineageGuideCards.map(item => item.title)).toEqual([
      '选择起点',
      '上游/下游',
      '静态分析边界'
    ])
    expect(wrapper.vm.lineageGuideCards[1].text).toContain('上游')
    expect(wrapper.vm.lineageGuideCards[2].text).toContain('静态分析')
    wrapper.destroy()
  })
})
