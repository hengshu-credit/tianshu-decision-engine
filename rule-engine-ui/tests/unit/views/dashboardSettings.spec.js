import { flushPromises } from '@vue/test-utils'
import { shallowMount } from '@test-utils'
import * as dashboardApi from '@/api/dashboard'
import * as projectApi from '@/api/project'
import DashboardSettings from '@/views/dashboard/DashboardSettings.vue'
import { DASHBOARD_MAP_VIEW_KEY } from '@/utils/dashboardFilters'

const settings = {
  editable: true,
  projectId: null,
  projectOverride: false,
  options: [{
    refType: 'VARIABLE', refId: 3, label: '请求编号',
    valueType: 'STRING', scope: 'GLOBAL', projectId: 0
  }],
  fields: [{
    metricField: 'REQUEST_ID',
    label: '请求编号',
    mapping: {
      refType: 'VARIABLE', refId: 3, jsonSource: 'INPUT',
      mappingSource: 'SYSTEM_DEFAULT', valid: true
    }
  }]
}

describe('DashboardSettings', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    dashboardApi.getDashboardSettings.mockResolvedValue({ data: settings })
    dashboardApi.saveDashboardSettings.mockResolvedValue({ data: settings })
  })

  afterEach(() => vi.clearAllMocks())

  test('加载解析后的继承来源并按 ID 保存映射', async () => {
    const wrapper = shallowMount(DashboardSettings)
    await flushPromises()

    expect(wrapper.vm.rows[0].referenceKey).toBe('VARIABLE:3')
    expect(wrapper.vm.rows[0].source).toBe('SYSTEM_DEFAULT')
    expect(wrapper.vm.sourceLabel(wrapper.vm.rows[0].source)).toBe('系统默认')

    await wrapper.vm.save()
    expect(dashboardApi.saveDashboardSettings).toHaveBeenCalledWith(
      { projectId: null },
      { mappings: {
        REQUEST_ID: {
          refType: 'VARIABLE', refId: 3, jsonSource: 'INPUT',
          decisionValues: null
        }
      } }
    )
    wrapper.unmount()
  })

  test('只读模式禁用保存', async () => {
    dashboardApi.getDashboardSettings.mockResolvedValueOnce({
      data: { ...settings, editable: false }
    })
    const wrapper = shallowMount(DashboardSettings)
    await flushPromises()

    await wrapper.vm.save()
    expect(dashboardApi.saveDashboardSettings).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('地图视角在当前会话保存并可一键恢复中国默认值', async () => {
    const wrapper = shallowMount(DashboardSettings)
    await flushPromises()

    expect(wrapper.find('.dashboard-map-view-settings').exists()).toBe(true)
    expect(wrapper.text()).toContain('保存地图视角')
    expect(wrapper.vm.mapView).toEqual({ longitude: 104, latitude: 35, zoom: 1.5 })

    wrapper.vm.mapView = { longitude: 112, latitude: 31, zoom: 2.4 }
    await wrapper.vm.saveMapView()
    expect(JSON.parse(window.sessionStorage.getItem(DASHBOARD_MAP_VIEW_KEY)))
      .toEqual({ longitude: 112, latitude: 31, zoom: 2.4 })

    await wrapper.vm.restoreChinaMapView()
    expect(wrapper.vm.mapView).toEqual({ longitude: 104, latitude: 35, zoom: 1.5 })
    expect(JSON.parse(window.sessionStorage.getItem(DASHBOARD_MAP_VIEW_KEY)))
      .toEqual({ longitude: 104, latitude: 35, zoom: 1.5 })
    wrapper.unmount()
  })
})
