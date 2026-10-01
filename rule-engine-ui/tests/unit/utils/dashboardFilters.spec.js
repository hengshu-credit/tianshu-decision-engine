import {
  DASHBOARD_FILTERS_KEY,
  dashboardQuickRange,
  defaultDashboardFilters,
  readDashboardFilters,
  validateDashboardRange,
  writeDashboardFilters
} from '@/utils/dashboardFilters'
import * as dashboardSession from '@/utils/dashboardFilters'

describe('dashboard filters', () => {
  test('默认范围从当天前六天零点到当前时刻', () => {
    const now = new Date(2026, 7, 27, 15, 12, 30)
    expect(defaultDashboardFilters(now)).toEqual({
      projectId: null,
      ruleCode: '',
      ruleName: '',
      startTime: '2026-08-21 00:00:00',
      endTime: '2026-08-27 15:12:30'
    })
  })

  test('统计时间快捷范围均从当天零点计至当前时刻', () => {
    const now = new Date(2026, 7, 27, 15, 12, 30)

    expect(dashboardQuickRange(1, now)).toEqual([
      new Date(2026, 7, 27, 0, 0, 0),
      new Date(2026, 7, 27, 15, 12, 30)
    ])
    expect(dashboardQuickRange(7, now)).toEqual([
      new Date(2026, 7, 21, 0, 0, 0),
      new Date(2026, 7, 27, 15, 12, 30)
    ])
    expect(dashboardQuickRange(30, now)).toEqual([
      new Date(2026, 6, 29, 0, 0, 0),
      new Date(2026, 7, 27, 15, 12, 30)
    ])
  })

  test('只写入传入的会话存储', () => {
    const storage = { setItem: vi.fn() }
    const filters = {
      projectId: 9,
      ruleCode: 'RC_RISK',
      ruleName: '风险审批',
      startTime: '2026-08-01 00:00:00',
      endTime: '2026-08-02 00:00:00'
    }

    writeDashboardFilters(storage, filters)

    expect(storage.setItem).toHaveBeenCalledWith(
      DASHBOARD_FILTERS_KEY,
      JSON.stringify(filters)
    )
  })

  test('会话缓存恢复规则编码和名称并清理首尾空格', () => {
    const value = readDashboardFilters({
      getItem: () => JSON.stringify({
        projectId: 9,
        ruleCode: ' RC_RISK ',
        ruleName: ' 风险审批 ',
        startTime: '2026-08-01 00:00:00',
        endTime: '2026-08-02 00:00:00'
      })
    })

    expect(value.ruleCode).toBe('RC_RISK')
    expect(value.ruleName).toBe('风险审批')
  })

  test('损坏或超过九十天的缓存回退默认值', () => {
    const now = new Date(2026, 7, 27, 15, 12, 30)
    expect(readDashboardFilters({ getItem: () => '{bad' }, now))
      .toEqual(defaultDashboardFilters(now))
    expect(readDashboardFilters({
      getItem: () => JSON.stringify({
        projectId: 1,
        startTime: '2026-01-01 00:00:00',
        endTime: '2026-08-27 00:00:00'
      })
    }, now)).toEqual(defaultDashboardFilters(now))
  })

  test('九十天边界有效，多一秒无效', () => {
    expect(validateDashboardRange(
      '2026-01-01 00:00:00', '2026-04-01 00:00:00'
    ).valid).toBe(true)
    expect(validateDashboardRange(
      '2026-01-01 00:00:00', '2026-04-01 00:00:01'
    ).message).toContain('90天')
  })

  test('地图视角默认展示中国并只在当前会话保存', () => {
    expect(dashboardSession.defaultDashboardMapView).toBeTypeOf('function')
    expect(dashboardSession.defaultDashboardGlobalMapView()).toEqual({
      longitude: 0,
      latitude: 0,
      zoom: 1
    })
    const storage = {
      getItem: vi.fn(() => null),
      setItem: vi.fn()
    }

    expect(dashboardSession.readDashboardMapView(storage)).toEqual({
      longitude: 104,
      latitude: 35,
      zoom: 1.5
    })

    const result = dashboardSession.writeDashboardMapView(storage, {
      longitude: 112,
      latitude: 31,
      zoom: 2.4
    })
    expect(result.valid).toBe(true)
    expect(storage.setItem).toHaveBeenCalledWith(
      dashboardSession.DASHBOARD_MAP_VIEW_KEY,
      JSON.stringify({ longitude: 112, latitude: 31, zoom: 2.4 })
    )
  })

  test('地图视角拒绝非法数值并在缓存损坏时回退中国', () => {
    expect(dashboardSession.validateDashboardMapView({
      longitude: 104,
      latitude: null,
      zoom: 1.5
    })).toEqual({ valid: false, message: '中心纬度须在 -90 至 90 之间' })
    expect(dashboardSession.validateDashboardMapView({
      longitude: 181,
      latitude: 35,
      zoom: 1.5
    })).toEqual({ valid: false, message: '中心经度须在 -180 至 180 之间' })
    expect(dashboardSession.validateDashboardMapView({
      longitude: 104,
      latitude: 91,
      zoom: 1.5
    })).toEqual({ valid: false, message: '中心纬度须在 -90 至 90 之间' })
    expect(dashboardSession.validateDashboardMapView({
      longitude: 104,
      latitude: 35,
      zoom: 10.1
    })).toEqual({ valid: false, message: '缩放比例须在 0.5 至 10 之间' })
    expect(dashboardSession.readDashboardMapView({
      getItem: () => '{bad'
    })).toEqual({ longitude: 104, latitude: 35, zoom: 1.5 })
  })
})
