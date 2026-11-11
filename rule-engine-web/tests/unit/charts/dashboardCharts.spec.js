import {
  createDashboardPalette,
  distributionBarOption,
  distributionPieOption,
  regionalApplicationOption,
  regionalDecisionOption,
  ruleSetHitOption
} from '@/charts/dashboardCharts'
import { DEFAULT_THEME_CONFIG } from '@/theme/themeConfig'
import { applyTheme } from '@/theme/themeRuntime'

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

  test('地区通过率和拒绝率使用同一百分比轴，人审及未分类进件保留在分母', () => {
    const option = regionalDecisionOption([
      { name: '区域一', count: 8, passCount: 5, rejectCount: 1, passRate: 5 / 8, rejectRate: 1 / 8 }
    ])
    expect(option.series.map(series => series.name)).toEqual(['通过率', '拒绝率'])
    expect(option.series.map(series => series.data)).toEqual([[62.5], [12.5]])
    expect(option.xAxis.max).toBe(100)
    expect(option.tooltip.formatter([{ dataIndex: 0 }])).toContain('通过：5（62.50%）')
  })

  test('地图高亮与饼图分类色随主题变化', () => {
    const root = document.documentElement
    const before = createDashboardPalette(root)
    applyTheme({ ...DEFAULT_THEME_CONFIG, accentMode: 'CUSTOM_SOLID', customSolidColor: '#873FF2' })
    const palette = createDashboardPalette(root)
    expect(palette.primary).toBe('#873FF2')
    expect(palette.chartAccent).not.toBe(before.chartAccent)
    expect(distributionPieOption().color).toEqual(palette.chartColors)
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
