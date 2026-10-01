const chart = {
  on: vi.fn(),
  setOption: vi.fn(),
  resize: vi.fn(),
  dispose: vi.fn()
}

vi.mock('@/charts/dashboardCharts', () => ({
  createChartInstance: vi.fn(() => chart)
}))

import { mount } from '@test-utils'
import DashboardChart from '@/components/dashboard/DashboardChart.vue'

describe('DashboardChart', () => {
  beforeEach(() => vi.clearAllMocks())

  test('渲染、主题刷新并在卸载时销毁实例', async () => {
    const wrapper = mount(DashboardChart, {
      props: {
        ariaLabel: '期数占比',
        option: { series: [] }
      }
    })
    expect(chart.setOption).toHaveBeenCalledWith({ series: [] }, true)

    window.dispatchEvent(new Event('tianshu-theme-change'))
    expect(chart.setOption).toHaveBeenCalledTimes(2)

    wrapper.unmount()
    expect(chart.dispose).toHaveBeenCalledOnce()
  })

  test('空数据展示统一空状态且不初始化画布', () => {
    const wrapper = mount(DashboardChart, {
      props: { ariaLabel: '空图表', empty: true, option: { series: [] } }
    })
    expect(wrapper.find('el-empty-stub').attributes('description'))
      .toBe('暂无统计数据')
    expect(chart.setOption).not.toHaveBeenCalled()
  })

  test('只转发行政区域点击，更新图表时不重复绑定事件', async () => {
    const wrapper = mount(DashboardChart, {
      props: { ariaLabel: '地图', option: { series: [] } }
    })
    const handler = chart.on.mock.calls.find(([event]) => event === 'click')[1]
    handler({ componentType: 'geo', name: '中国' })
    handler({ componentType: 'series', name: '热力点' })
    expect(wrapper.emitted('region-click')).toEqual([['中国']])

    await wrapper.setProps({ option: { series: [{ type: 'heatmap' }] } })
    expect(chart.on).toHaveBeenCalledOnce()
    wrapper.unmount()
  })
})
