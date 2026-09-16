import { flushPromises } from '@vue/test-utils'
import { mount, shallowMount } from '@test-utils'
import * as dashboardApi from '@/api/dashboard'
import * as definitionApi from '@/api/definition'
import * as projectApi from '@/api/project'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import DashboardHome from '@/views/dashboard/DashboardHome.vue'
import { DASHBOARD_MAP_VIEW_KEY } from '@/utils/dashboardFilters'
import { CHINA_MAP_BOUNDS } from '@/utils/dashboardMapLayers'

const chinaMap = { type: 'FeatureCollection', features: [{
  type: 'Feature', properties: { shapeID: '156710000', shapeName: '台湾省' },
  geometry: { type: 'Polygon', coordinates: [[[120, 22], [122, 22], [122, 25], [120, 22]]] }
}] }

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
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async url => ({
      ok: true,
      json: () => Promise.resolve(url.includes('dashboard-china') ? chinaMap : { type: 'FeatureCollection', features: [] })
    })))
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
    await wrapper.vm.changeMapCountry('')

    expect(wrapper.vm.geoOption.geo.center).toEqual([112, 31])
    expect(wrapper.vm.geoOption.geo.zoom).toBe(2.4)
    expect(wrapper.findAllComponents({ name: 'DashboardChart' })
      .find(chart => chart.props('ariaLabel') === '进件地图热力图').props('empty')).toBe(false)
    expect(wrapper.find('[data-testid="dashboard-map-reset"]').exists()).toBe(true)

    const version = wrapper.vm.mapViewVersion
    await wrapper.find('[data-testid="dashboard-map-reset"]').trigger('click')
    expect(wrapper.vm.mapViewVersion).toBe(version + 1)
    expect(wrapper.vm.geoOption.geo.center).toEqual([112, 31])
    expect(wrapper.vm.geoOption.geo.zoom).toBe(2.4)
    wrapper.unmount()
  })

  test.each([undefined, { points: [], validCount: 0, excludedCount: 3 }])(
    '缺少地理统计或全部坐标无效时显示默认地图和无数据提示', async geo => {
      dashboardApi.getDashboardApplications.mockResolvedValueOnce({
        data: { ...applications(), geo }
      })
      const wrapper = mount(DashboardHome, {
        global: { stubs: { DashboardChart: true } }
      })
      await flushPromises()

      const map = wrapper.findAllComponents({ name: 'DashboardChart' })
        .find(chart => chart.props('ariaLabel') === '进件地图热力图')
      expect(map.props('empty')).toBe(false)
      expect(wrapper.vm.mapCountry).toBe('CHN')
      expect(map.props('option').geo.center).toBeUndefined()
      expect(map.props('option').geo.boundingCoords).toEqual(CHINA_MAP_BOUNDS)
      expect(map.props('option').series[0].data).toEqual([])
      expect(wrapper.text()).toContain('暂无合法经纬度记录，展示初始视角')
      wrapper.unmount()
    }
  )

  test('地图资源就绪前保留加载状态，就绪后显示无数据底图', async () => {
    let finish
    fetch.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const wrapper = mount(DashboardHome, {
      global: { stubs: { DashboardChart: true } }
    })
    await flushPromises()
    const map = wrapper.findAllComponents({ name: 'DashboardChart' })
      .find(chart => chart.props('ariaLabel') === '进件地图热力图')
    expect(map.props('empty')).toBe(true)
    expect(map.props('emptyDescription')).toBe('本地地图正在加载')

    finish({ ok: true, json: async () => chinaMap })
    await flushPromises()
    expect(wrapper.findAllComponents({ name: 'DashboardChart' })
      .find(chart => chart.props('ariaLabel') === '进件地图热力图').props('empty')).toBe(false)
    wrapper.unmount()
  })

  test('主题切换与查询结果变化后同步更新地图', async () => {
    const wrapper = mount(DashboardHome, {
      global: { stubs: { DashboardChart: true } }
    })
    await flushPromises()
    const map = wrapper.findAllComponents({ name: 'DashboardChart' })
      .find(chart => chart.props('ariaLabel') === '进件地图热力图')
    const before = map.props('option').geo.itemStyle.areaColor
    try {
      document.documentElement.style.setProperty('--el-color-primary', '#873FF2')
      window.dispatchEvent(new Event('tianshu-theme-change'))
      await wrapper.vm.$nextTick()
      expect(map.props('option').geo.itemStyle.areaColor).not.toBe(before)

      dashboardApi.getDashboardApplications.mockResolvedValueOnce({
        data: { ...applications(), geo: {
          points: [{ longitude: 120, latitude: 30, count: 4 }], validCount: 4, excludedCount: 0
        } }
      })
      await wrapper.vm.refreshAll()
      expect(map.props('option').series[0].data).toEqual([[120, 30, 4]])
      expect(map.props('option').visualMap.show).toBe(true)
      expect(wrapper.text()).not.toContain('暂无合法经纬度记录，展示初始视角')

      await wrapper.vm.refreshAll()
      expect(map.props('empty')).toBe(false)
      expect(map.props('option').series[0].data).toEqual([])
      expect(map.props('option').visualMap.show).toBe(false)
    } finally {
      document.documentElement.style.removeProperty('--el-color-primary')
      wrapper.unmount()
    }
  })

  test('点击国家后加载可用行政区层级，并能返回全球与恢复默认视角', async () => {
    const catalog = { countries: [{ code: 'USA', name: '美国', layers: [
      { level: 'ADM0', year: '2018', url: '/usa-country.json' },
      { level: 'ADM1', year: '2018', url: '/usa-states.json' },
      { level: 'ADM2', year: '2018', url: '/usa-counties.json' }
    ] }] }
    const world = { type: 'FeatureCollection', features: [{
      type: 'Feature', properties: { NAME_ZH: '美国', ADM0_A3: 'USA' },
      geometry: { type: 'Polygon', coordinates: [[[-100, 30], [-90, 30], [-90, 40], [-100, 30]]] }
    }] }
    const administrative = { ...world, features: [{
      ...world.features[0], properties: { shapeID: 'region-1', shapeName: 'Washington' }
    }] }
    fetch.mockImplementation(async url => ({ ok: true, json: async () =>
      url.includes('dashboard-boundaries') ? catalog : url.includes('dashboard-world') ? world : url.includes('china-land') ? chinaMap : administrative
    }))
    const wrapper = mount(DashboardHome, { global: { stubs: { DashboardChart: true } } })
    await flushPromises()
    await wrapper.vm.changeMapCountry('')
    await wrapper.vm.$nextTick()
    const map = wrapper.findAllComponents({ name: 'DashboardChart' })
      .find(chart => chart.props('ariaLabel') === '进件地图热力图')
    map.vm.$emit('region-click', '美国')
    await flushPromises()
    expect(wrapper.vm.mapCountry).toBe('USA')
    expect(wrapper.vm.mapLevel).toBe('ADM1')
    expect(wrapper.vm.geoOption.geo.nameProperty).toBe('shapeID')
    expect(wrapper.vm.geoOption.geo.center).toBeUndefined()
    expect(wrapper.vm.mapLayerOptions.map(layer => layer.level)).toEqual(['ADM0', 'ADM1', 'ADM2'])
    expect(wrapper.text()).toContain('geoBoundaries')
    await wrapper.vm.changeMapLevel('ADM2')
    expect(fetch).toHaveBeenCalledWith('/usa-counties.json', expect.objectContaining({ signal: expect.any(AbortSignal) }))
    expect(wrapper.vm.mapReady).toBe(true)

    await wrapper.vm.changeMapCountry('')
    expect(wrapper.vm.geoOption.geo.nameProperty).toBe('NAME_ZH')
    expect(wrapper.vm.geoOption.geo.center).toEqual([104, 35])
    expect(wrapper.vm.geoOption.geo.zoom).toBe(1.5)
    expect(wrapper.vm.mapReady).toBe(true)
    wrapper.unmount()
  })

  test('行政区加载失败可重试，快速切换后旧请求不覆盖新视图', async () => {
    const wrapper = mount(DashboardHome, { global: { stubs: { DashboardChart: true } } })
    await flushPromises()
    wrapper.vm.mapCatalog = { countries: [{ code: 'USA', name: '美国', layers: [
      { level: 'ADM1', url: '/states.json' }
    ] }] }
    fetch.mockRejectedValueOnce(new Error('Network error'))
    await wrapper.vm.changeMapCountry('USA')
    expect(wrapper.vm.mapReady).toBe(false)
    expect(wrapper.vm.mapError).toContain('行政区边界加载失败')
    expect(wrapper.text()).toContain('重试地图')

    let completeOld
    fetch.mockReturnValueOnce(new Promise(resolve => { completeOld = resolve }))
    const oldRequest = wrapper.vm.loadMap()
    const oldController = wrapper.vm.mapLoadController
    await wrapper.vm.changeMapCountry('')
    expect(oldController.signal.aborted).toBe(true)
    completeOld({ ok: true, json: async () => ({ features: [] }) })
    await oldRequest
    expect(wrapper.vm.mapCountry).toBe('')
    expect(wrapper.vm.mapError).toBe('')
    expect(wrapper.vm.mapReady).toBe(true)
    expect(wrapper.vm.mapLoading).toBe(false)
    wrapper.unmount()
  })
})
