import { shallowMount, flushPromises } from '@test-utils'
import LineageGraph from '@/views/lineage/LineageGraph.vue'
import { createGraphCanvas } from '@/components/flow/graphCanvas'
import { listLineageOptions } from '@/api/lineage'

vi.mock('@/components/flow/graphCanvas', () => ({ createGraphCanvas: vi.fn() }))

const startNode = { id: 'VARIABLE:1', refId: 1, type: 'VARIABLE', code: 'age', label: '年龄' }
const graph = { startNode, nodes: [startNode], edges: [] }
function canvas() {
  return {
    render: vi.fn(), destroy: vi.fn(), on: vi.fn(),
    setZoomMiniSize: vi.fn(), setZoomMaxSize: vi.fn(),
    fitView: vi.fn(), translateCenter: vi.fn(),
    getTransform: () => ({ SCALE_X: 1 }),
  }
}
async function settle() {
  await vi.dynamicImportSettled()
  await flushPromises()
}
beforeEach(() => {
  vi.clearAllMocks()
  listLineageOptions.mockResolvedValue({ data: [] })
  createGraphCanvas.mockImplementation(canvas)
})

test('空白血缘页只加载候选项，不初始化画布', async () => {
  const wrapper = shallowMount(LineageGraph)
  try {
    await settle()
    expect(listLineageOptions).toHaveBeenCalledOnce()
    expect(createGraphCanvas).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请选择起点后生成血缘图')
  } finally { wrapper.unmount() }
})

test('嵌入图首次挂载按需创建画布，重复渲染复用实例和最新图数据', async () => {
  const wrapper = shallowMount(LineageGraph, { props: { embedded: true, initialGraph: graph } })
  try {
    wrapper.vm.renderLineageGraph()
    await settle()
    expect(createGraphCanvas).toHaveBeenCalledOnce()
    const lf = wrapper.vm.lf
    expect(lf.render.mock.lastCall[0].nodes[0].properties.nodeCode).toBe('age')
    await wrapper.setProps({ initialGraph: { ...graph, startNode: { ...startNode, code: 'AgeOriginal' } } })
    expect(lf.render.mock.lastCall[0].nodes[0].properties.nodeCode).toBe('AgeOriginal')
    expect(createGraphCanvas).toHaveBeenCalledOnce()
  } finally { wrapper.unmount() }
})

test('加载中离开页面，不在卸载后创建画布', async () => {
  const wrapper = shallowMount(LineageGraph, { props: { embedded: true, initialGraph: graph } })
  expect(wrapper.vm.canvasLoading).toBe(true)
  wrapper.unmount()
  await settle()
  expect(createGraphCanvas).not.toHaveBeenCalled()
})

test('初始化失败可以重试，且失败不会因重渲染自动循环请求', async () => {
  createGraphCanvas.mockImplementationOnce(() => { throw new Error('resource unavailable') })
  const wrapper = shallowMount(LineageGraph, { props: { initialGraph: graph } })
  try {
    await settle()
    expect(wrapper.vm.canvasError).toBe('血缘图加载失败，请重新加载')
    expect(wrapper.vm.canvasLoading).toBe(false)
    wrapper.vm.renderLineageGraph()
    await settle()
    expect(createGraphCanvas).toHaveBeenCalledOnce()
    await wrapper.findComponent({ name: 'AsyncState' }).vm.$emit('retry')
    await settle()
    expect(createGraphCanvas).toHaveBeenCalledTimes(2)
    expect(wrapper.vm.canvasError).toBe('')
    expect(wrapper.vm.lf.render).toHaveBeenCalled()
  } finally { wrapper.unmount() }
})
