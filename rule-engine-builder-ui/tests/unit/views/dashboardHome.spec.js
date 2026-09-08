import { flushPromises } from '@vue/test-utils'
import { mount, shallowMount } from '@test-utils'
import * as dashboardApi from '@/api/dashboard'
import * as definitionApi from '@/api/definition'
import * as projectApi from '@/api/project'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import DashboardHome from '@/views/dashboard/DashboardHome.vue'
import { DASHBOARD_MAP_VIEW_KEY } from '@/utils/dashboardFilters'

function applications() {
  return {
    visible: true,
    summary: {
      applicationCount: 12,
      passCount: 8,
      reviewCount: 2,
      rejectCount: 2,
      deviceCount: 10,
      passRate: 2 / 3,
      reviewRate: 1 / 6
    },
    periods: { items: [], validCount: 0, excludedCount: 0 },
    amounts: { items: [], validCount: 0, excludedCount: 0 },
    geo: { points: [], validCount: 0, excludedCount: 0 },
    mappingIssues: []
  }
}

describe('DashboardHome', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    definitionApi.listDefinitions.mockResolvedValue({ data: { records: [], total: 0 } })
    definitionApi.listProjectDefinitions.mockResolvedValue({ data: { records: [], total: 0 } })
    dashboardApi.getDashboardApplications.mockResolvedValue({ data: applications() })
    dashboardApi.getDashboardOperations.mockResolvedValue({ data: {} })
    dashboardApi.getDashboardGovernance.mockResolvedValue({ data: {} })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ type: 'FeatureCollection', features: [] })
    }))
  })

  afterEach(() => {
    vi.clearAllMocks()
    vi.unstubAllGlobals()
  })

  test('首次进入并行加载三个独立区域', async () => {
    const wrapper = shallowMount(DashboardHome)
    await flushPromises()

    expect(dashboardApi.getDashboardApplications).toHaveBeenCalledOnce()
    expect(dashboardApi.getDashboardOperations).toHaveBeenCalledOnce()
    expect(dashboardApi.getDashboardGovernance).toHaveBeenCalledOnce()
    expect(wrapper.vm.summary.applicationCount).toBe(12)
    expect(wrapper.findAllComponents({ name: 'MetricCard' })
      .some(card => card.props('label') === '进件数')).toBe(true)
    wrapper.unmount()
  })

  test('生产构建使用的运行时可以实际渲染指标组件', async () => {
    const runtimeSafeComponents = ['MetricCard', 'ChartCard', 'ResourceCard']
    runtimeSafeComponents.forEach(name => {
      expect(DashboardHome.components[name].template).toBeUndefined()
    })
    const wrapper = mount(DashboardHome, {
      global: {
        stubs: {
          DashboardChart: true
        }
      }
    })
    await flushPromises()

    expect(wrapper.find('.dashboard-metrics').text()).toContain('进件数')
    expect(wrapper.find('.dashboard-metrics').text()).toContain('12')
    wrapper.unmount()
  })

  test('提示保持单行且设置按钮位于筛选栏最右侧', async () => {
    const wrapper = mount(DashboardHome, {
      global: {
        stubs: {
          DashboardChart: true
        }
      }
    })
    await flushPromises()

    expect(wrapper.find('.dashboard-heading-line').exists()).toBe(true)
    expect(wrapper.find('.dashboard-heading .dashboard-settings-button').exists()).toBe(false)
    expect(wrapper.find('.dashboard-filters .dashboard-settings-button').exists()).toBe(true)
    expect(wrapper.find('.dashboard-filter-form').exists()).toBe(true)
    wrapper.unmount()
  })

  test('再次查询期间保留指标和图表实例，完成后更新原有内容', async () => {
    const wrapper = mount(DashboardHome, {
      global: { stubs: { DashboardChart: true } }
    })
    await flushPromises()
    const chart = wrapper.findComponent({ name: 'DashboardChart' }).vm
    let finish
    dashboardApi.getDashboardApplications.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    try {
      const pending = wrapper.vm.refreshAll()
      await wrapper.vm.$nextTick()
      expect(wrapper.find('.dashboard-metrics').exists()).toBe(true)
      expect(wrapper.find('.dashboard-metrics').text()).toContain('12')
      expect(wrapper.findComponent({ name: 'DashboardChart' }).vm).toBe(chart)
      expect(wrapper.find('.dashboard-section').attributes('aria-busy')).toBe('true')
      finish({ data: { ...applications(), summary: { applicationCount: 24 } } })
      await pending
      await wrapper.vm.$nextTick()
      expect(wrapper.find('.dashboard-metrics').text()).toContain('24')
      expect(wrapper.findComponent({ name: 'DashboardChart' }).vm).toBe(chart)
      expect(wrapper.find('.dashboard-section').attributes('aria-busy')).toBe('false')
    } finally {
      finish({ data: applications() })
      wrapper.unmount()
    }
  })

  test('规则筛选和时间快捷项统一进入筛选表单', async () => {
    const wrapper = mount(DashboardHome, {
      global: {
        stubs: {
          DashboardChart: true
        }
      }
    })
    await flushPromises()

    const ruleFilters = wrapper.findAllComponents(RemoteFilterSelect)
    expect(ruleFilters).toHaveLength(2)
    expect(ruleFilters.map(item => item.props('optionValueKey')))
      .toEqual(['ruleCode', 'ruleName'])
    expect(wrapper.vm.dateShortcuts.map(item => item.text))
      .toEqual(['今天', '近7天', '近30天'])

    wrapper.vm.filters.ruleCode = 'RC_RISK'
    wrapper.vm.filters.ruleName = '风险审批'
    expect(wrapper.vm.queryParams()).toMatchObject({
      ruleCode: 'RC_RISK',
      ruleName: '风险审批'
    })
    wrapper.unmount()
  })

  test('运行区域失败仍保留进件结果并提供局部重试', async () => {
    dashboardApi.getDashboardOperations.mockRejectedValueOnce(
      new Error('运行统计失败')
    )
    const wrapper = shallowMount(DashboardHome)
    await flushPromises()

    expect(wrapper.vm.summary.applicationCount).toBe(12)
    expect(wrapper.vm.sections.operations.error).toBe('运行统计失败')
    expect(wrapper.vm.sections.applications.data.summary.applicationCount).toBe(12)
    wrapper.unmount()
  })

  test('地图使用会话配置并可一键恢复设置视角', async () => {
    window.sessionStorage.setItem(DASHBOARD_MAP_VIEW_KEY, JSON.stringify({
      longitude: 112,
      latitude: 31,
      zoom: 2.4
    }))
    const wrapper = mount(DashboardHome, {
      global: {
        stubs: {
          DashboardChart: true
        }
      }
    })
    await flushPromises()

    expect(wrapper.vm.geoOption.geo.center).toEqual([112, 31])
    expect(wrapper.vm.geoOption.geo.zoom).toBe(2.4)
    expect(wrapper.find('[data-testid="dashboard-map-reset"]').exists()).toBe(true)

    const version = wrapper.vm.mapViewVersion
    await wrapper.find('[data-testid="dashboard-map-reset"]').trigger('click')
    expect(wrapper.vm.mapViewVersion).toBe(version + 1)
    expect(wrapper.vm.geoOption.geo.center).toEqual([112, 31])
    expect(wrapper.vm.geoOption.geo.zoom).toBe(2.4)
    wrapper.unmount()
  })
})
