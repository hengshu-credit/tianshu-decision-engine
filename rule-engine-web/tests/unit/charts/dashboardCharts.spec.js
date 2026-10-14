import {
  createDashboardPalette,
  distributionBarOption,
  distributionPieOption,
  geoHeatmapOption,
  regionalApplicationOption,
  regionalDecisionOption,
  ruleSetHitOption
} from '@/charts/dashboardCharts'
import { DEFAULT_THEME_CONFIG, mixThemeColors } from '@/theme/themeConfig'
import { applyTheme } from '@/theme/themeRuntime'
import { readFileSync } from 'node:fs'

describe('dashboard charts', () => {
  afterEach(() => {
    document.documentElement.removeAttribute('style')
    document.documentElement.removeAttribute('data-theme')
  })

  test('调色板读取当前主题 CSS 变量', () => {
    document.documentElement.style.setProperty('--el-color-primary', '#123456')
    document.documentElement.style.setProperty('--tianshu-color-secondary', '#654321')
    const palette = createDashboardPalette(document.documentElement)
    expect(palette.primary).toBe('#123456')
    expect(palette.secondary).toBe('#654321')
  })

  test('分布图保留标签、数量和主题色', () => {
    const items = [{ key: '12', label: '12期', count: 8 }]
    const pie = distributionPieOption(items)
    const palette = createDashboardPalette(document.documentElement)

    expect(distributionBarOption(items).series[0].data).toEqual([8])
    expect(pie.series[0].data)
      .toEqual([{ name: '12期', value: 8 }])
    expect(pie.color).toEqual(palette.chartColors)
    expect(pie.color).not.toContain(palette.success)
    expect(pie.color).not.toContain(palette.warning)
  })

  test('地区进件图同时展示数量、占比和累计占比', () => {
    const option = regionalApplicationOption([
      { name: '区域一', count: 6, rate: 0.6 },
      { name: '区域二', count: 4, rate: 0.4 }
    ])
    expect(option.series.map(series => series.name)).toEqual(['进件数', '占比', '累计占比'])
    expect(option.series[0].data).toEqual([6, 4])
    expect(option.series[1].data).toEqual([60, 40])
    expect(option.series[2].data).toEqual([60, 100])
    expect(option.xAxis[0].type).toBe('value')
    expect(option.yAxis.type).toBe('category')
  })

  test('地区决策图同时展示通过、拒绝和拒绝率', () => {
    const option = regionalDecisionOption([
      { name: '区域一', passCount: 5, rejectCount: 1, rejectRate: 1 / 6 }
    ])
    expect(option.series.map(series => series.name)).toEqual(['通过', '拒绝', '拒绝率'])
    expect(option.series[0].data).toEqual([5])
    expect(option.series[1].data).toEqual([1])
    expect(option.series[2].data[0]).toBeCloseTo(100 / 6, 10)
    expect(option.xAxis[0].type).toBe('value')
    expect(option.yAxis.type).toBe('category')
  })

  test('地图热力图只接收服务端合法坐标点', () => {
    const option = geoHeatmapOption([
      { longitude: 120.12, latitude: 30.28, count: 4 }
    ], { center: [112, 31], zoom: 2.4 })
    expect(option.series[0].coordinateSystem).toBe('geo')
    expect(option.series[0].data).toEqual([[120.12, 30.28, 4]])
    expect(option.geo.center).toEqual([112, 31])
    expect(option.geo.zoom).toBe(2.4)
    expect(option.visualMap.show).toBe(true)
  })

  test('无经纬度数据时仍生成默认底图但不展示虚构热力点或数值图例', () => {
    const option = geoHeatmapOption()

    expect(option.geo.center).toEqual([104, 35])
    expect(option.geo.zoom).toBe(1.5)
    expect(option.series[0].data).toEqual([])
    expect(option.visualMap.show).toBe(false)
  })

  test.each([
    ['LIGHT', '#FFFFFF', '#172033', '#E2E8F0'],
    ['DARK', '#151D31', '#EDF2FF', '#28344D']
  ])('%s 模式的地图与自定义主题配色保持一致', (colorScheme, surface, text, border) => {
    const root = document.documentElement
    applyTheme({
      ...DEFAULT_THEME_CONFIG,
      colorScheme,
      accentMode: 'CUSTOM_GRADIENT',
      customGradientColors: ['#873FF2', '#2CC7B8']
    })
    // jsdom 不加载主题样式表，在此提供对应模式的基础表面色。
    root.style.setProperty('--el-bg-color', surface)
    root.style.setProperty('--el-text-color-primary', text)
    root.style.setProperty('--el-border-color-light', border)
    const option = geoHeatmapOption([{ longitude: 120, latitude: 30, count: 4 }])

    expect(option.geo.itemStyle.areaColor).toBe(mixThemeColors(surface, '#873FF2', 0.16))
    expect(option.geo.itemStyle.borderColor).toBe(mixThemeColors(border, '#873FF2', 0.25))
    const palette = createDashboardPalette(root)
    expect(option.geo.emphasis.itemStyle.areaColor).toBe(palette.chartAccent)
    expect(option.geo.emphasis.label.color).toBe(text)
    expect(option.visualMap.inRange.color).toEqual([
      mixThemeColors(surface, '#873FF2', 0.2), '#873FF2',
      palette.chartAccent
    ])
    expect(option.tooltip.backgroundColor).toBe(surface)
    expect(option.tooltip.textStyle.color).toBe(text)
  })

  test('地图高亮与饼图分类色随主题主色变化且避开状态绿黄', () => {
    const root = document.documentElement
    applyTheme({
      ...DEFAULT_THEME_CONFIG,
      colorScheme: 'DARK',
      accentMode: 'CUSTOM_SOLID',
      customSolidColor: '#873FF2'
    })
    root.style.setProperty('--el-bg-color', '#151D31')
    root.style.setProperty('--el-text-color-primary', '#EDF2FF')
    root.style.setProperty('--el-border-color-light', '#28344D')

    const palette = createDashboardPalette(root)
    const pie = distributionPieOption([
      { label: '分类一', count: 3 },
      { label: '分类二', count: 2 }
    ])
    const map = geoHeatmapOption([{ longitude: 120, latitude: 30, count: 3 }])

    expect(pie.color).toEqual(palette.chartColors)
    expect(pie.color).not.toContain(palette.success)
    expect(pie.color).not.toContain(palette.warning)
    expect(map.geo.emphasis.itemStyle.areaColor).toBe(palette.chartAccent)
    expect(map.visualMap.inRange.color.at(-1)).toBe(palette.chartAccent)
  })

  test('底图悬停显示区域名称，热力点仍显示坐标和数量', () => {
    const { tooltip, geo } = geoHeatmapOption()

    expect(geo.tooltip.show).toBe(true)
    expect(tooltip.formatter({ componentType: 'geo', name: '中华人民共和国' }))
      .toBe('中华人民共和国')
    expect(tooltip.formatter({ componentType: 'series', value: [120, 30, 4] }))
      .toBe('120, 30：4')
  })

  test('真实世界底图的每个区域都有独立名称，悬停仅高亮当前区域', () => {
    const world = JSON.parse(readFileSync('public/maps/dashboard-world.geojson', 'utf8'))
    const { geo } = geoHeatmapOption()
    const names = world.features.map(feature => feature.properties[geo.nameProperty])

    expect(names.every(name => typeof name === 'string' && name.length > 0)).toBe(true)
    expect(new Set(names).size).toBe(world.features.length)
    expect(geo.emphasis.label.show).toBe(true)
    expect(geo.emphasis.itemStyle.borderWidth).toBeGreaterThan(1)
  })

  test('行政区地图按唯一 ID 识别同名区域，并自动适配当前国家范围', () => {
    const option = geoHeatmapOption([], {
      mapName: 'dashboard-administrative-region',
      nameProperty: 'shapeID',
      regionNames: { 'county-1': 'Washington', 'county-2': 'Washington' },
      fitRegion: true,
      center: [104, 35],
      zoom: 2.4
    })
    expect(option.geo.nameProperty).toBe('shapeID')
    expect(option.geo.center).toEqual([104, 35])
    expect(option.geo.zoom).toBe(2.4)
    expect(option.geo.map).toBe('dashboard-administrative-region')
    expect(option.tooltip.formatter({ componentType: 'geo', name: 'county-2' })).toBe('Washington')
    expect(option.geo.tooltip.formatter({ componentType: 'geo', name: 'county-2' })).toBe('Washington')
    expect(option.geo.emphasis.label.formatter({ name: 'county-1' })).toBe('Washington')
  })

  test('行政区地图按进件占比着色并保留热力点图层', () => {
    const option = geoHeatmapOption([{ longitude: 120, latitude: 30, count: 4 }], {
      mapName: 'dashboard-administrative-region',
      nameProperty: 'shapeID',
      regionData: [{ id: 'province-1', name: '区域一', count: 4, rate: 1 }]
    })
    expect(option.series[0].type).toBe('heatmap')
    expect(option.series[1].type).toBe('map')
    expect(option.series[1].data).toEqual([{ name: 'province-1', value: 4 }])
    expect(option.visualMap.show).toBe(true)
  })

  test('规则集 TOP 悬浮信息包含进件、命中和命中率', () => {
    const option = ruleSetHitOption([{
      ruleCode: 'R1', ruleName: '规则一', applicationCount: 20,
      hitCount: 5, hitRate: 0.25
    }])

    expect(option.series[0].data).toEqual([5])
    expect(option.tooltip.renderMode).toBe('richText')
    expect(option.tooltip.formatter([{ dataIndex: 0 }]))
      .toBe('规则一\n执行次数：20\n命中次数：5\n命中率：25.00%')
  })
})
