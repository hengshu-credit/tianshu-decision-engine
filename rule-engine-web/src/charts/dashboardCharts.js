import { init, use } from 'echarts/core'
import { BarChart, PieChart, LineChart } from 'echarts/charts'
import {
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { mixThemeColors } from '@/theme/themeConfig'

use([
  BarChart,
  PieChart,
  LineChart,
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer
])

function css(root, name, fallback) {
  if (!root || typeof getComputedStyle !== 'function') return fallback
  return getComputedStyle(root).getPropertyValue(name).trim() || fallback
}

export function createDashboardPalette(root = document.documentElement) {
  const palette = {
    primary: css(root, '--el-color-primary', '#5b6cff'),
    primaryLight: css(root, '--el-color-primary-light-5', '#a6afff'),
    primaryLight3: css(root, '--el-color-primary-light-3', '#8792ff'),
    primaryLight6: css(root, '--el-color-primary-light-6', '#c2c8ff'),
    primaryDark1: css(root, '--el-color-primary-dark-1', '#5261e6'),
    secondary: css(root, '--tianshu-color-secondary', '#f76e6c'),
    success: css(root, '--el-color-success', '#22a06b'),
    warning: css(root, '--el-color-warning', '#d99000'),
    danger: css(root, '--el-color-danger', '#d94a4a'),
    info: css(root, '--el-color-info', '#718096'),
    text: css(root, '--el-text-color-primary', '#1f2937'),
    muted: css(root, '--el-text-color-secondary', '#667085'),
    border: css(root, '--el-border-color-light', '#e4e7ed'),
    surface: css(root, '--el-bg-color', '#ffffff')
  }
  // 分类数据不代表业务状态，使用主色派生的蓝紫／莓红色板，避免把 success、warning
  // 的语义色误用到任意分类上，同时让主题切换后的图表保持同一套视觉语言。
  const dark = root?.dataset?.theme === 'dark'
  const chartAccent = mixThemeColors(
    palette.primary,
    palette.danger,
    dark ? 0.58 : 0.52
  )
  const chartAccentSoft = mixThemeColors(
    chartAccent,
    palette.surface,
    dark ? 0.18 : 0.42
  )
  return {
    ...palette,
    chartAccent,
    chartColors: [
      palette.primary,
      chartAccent,
      palette.primaryLight3,
      palette.primaryDark1,
      chartAccentSoft,
      palette.primaryLight6
    ]
  }
}

export function createChartInstance(element) {
  return init(element, null, { renderer: 'canvas' })
}

function tooltip(palette) {
  return {
    trigger: 'item',
    backgroundColor: palette.surface,
    borderColor: palette.border,
    textStyle: { color: palette.text }
  }
}

export function distributionBarOption(items = [], options = {}) {
  const palette = createDashboardPalette(options.root)
  const horizontal = options.horizontal === true
  const labels = items.map(item => item.label || item.key)
  const values = items.map(item => item.count || 0)
  return {
    color: [options.color || palette.primary],
    tooltip: { ...tooltip(palette), trigger: 'axis' },
    grid: { left: horizontal ? 88 : 44, right: 20, top: 24, bottom: 44 },
    xAxis: horizontal
      ? { type: 'value', axisLabel: { color: palette.muted }, splitLine: { lineStyle: { color: palette.border } } }
      : { type: 'category', data: labels, axisLabel: { color: palette.muted, interval: 0, rotate: labels.length > 6 ? 24 : 0 } },
    yAxis: horizontal
      ? { type: 'category', data: labels, inverse: true, axisLabel: { color: palette.muted } }
      : { type: 'value', axisLabel: { color: palette.muted }, splitLine: { lineStyle: { color: palette.border } } },
    series: [{
      type: 'bar',
      data: values,
      barMaxWidth: 28,
      itemStyle: { borderRadius: horizontal ? [0, 6, 6, 0] : [6, 6, 0, 0] }
    }]
  }
}

export function ruleSetHitOption(items = [], options = {}) {
  const option = distributionBarOption(items.map(item => ({
    key: item.ruleCode,
    label: item.ruleName || item.ruleCode,
    count: item.hitCount
  })), { ...options, horizontal: true })
  option.tooltip = {
    ...option.tooltip,
    renderMode: 'richText',
    formatter: params => {
      const point = Array.isArray(params) ? params[0] : params
      const item = items[point && point.dataIndex] || {}
      const label = item.ruleName || item.ruleCode || ''
      const applications = Number(item.applicationCount || 0)
      const hits = Number(item.hitCount || 0)
      const rate = (Number(item.hitRate || 0) * 100).toFixed(2)
      return `${label}\n执行次数：${applications}\n命中次数：${hits}\n命中率：${rate}%`
    }
  }
  return option
}

export function distributionPieOption(items = [], options = {}) {
  const palette = createDashboardPalette(options.root)
  return {
    color: palette.chartColors,
    tooltip: tooltip(palette),
    legend: { bottom: 0, textStyle: { color: palette.muted } },
    series: [{
      type: 'pie',
      radius: ['48%', '72%'],
      center: ['50%', '43%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: palette.surface, borderWidth: 2 },
      label: { color: palette.text, formatter: '{b}\n{d}%' },
      data: items.map(item => ({
        name: item.label || item.key,
        value: item.count || 0
      }))
    }]
  }
}

export function regionalApplicationOption(regions = [], options = {}) {
  const palette = createDashboardPalette(options.root)
  const labels = regions.map(region => region.name)
  const counts = regions.map(region => region.count || 0)
  const rates = regions.map(region => Number(region.rate || 0) * 100)
  let cumulative = 0
  const cumulativeRates = regions.map(region => {
    cumulative += Number(region.rate || 0) * 100
    return cumulative
  })
  return {
    color: palette.chartColors.slice(0, 3),
    tooltip: {
      ...tooltip(palette),
      renderMode: 'richText',
      trigger: 'axis',
      formatter: params => {
        const index = Array.isArray(params) ? params[0]?.dataIndex : params?.dataIndex
        const region = regions[index] || {}
        return `${region.name || ''}\n进件数：${Number(region.count || 0).toLocaleString('zh-CN')}\n占比：${(Number(region.rate || 0) * 100).toFixed(2)}%\n累计占比：${(Number(cumulativeRates[index] || 0)).toFixed(2)}%`
      }
    },
    legend: {
      top: 0,
      textStyle: { color: palette.muted },
      data: ['进件数', '占比', '累计占比']
    },
    grid: { left: 4, right: regions.length > 12 ? 24 : 10, top: 54, bottom: 20, outerBoundsMode: 'same' },
    dataZoom: regions.length > 12 ? [{
      type: 'slider', yAxisIndex: 0, startValue: 0, endValue: 11,
      width: 10, right: 0, showDetail: false, brushSelect: false
    }] : [],
    xAxis: [
      {
        type: 'value',
        name: '件数',
        nameTextStyle: { color: palette.muted },
        axisLabel: { color: palette.muted },
        splitLine: { lineStyle: { color: palette.border } }
      },
      {
        type: 'value',
        name: '占比',
        min: 0,
        max: 100,
        axisLabel: { color: palette.muted, formatter: '{value}%' },
        splitLine: { show: false }
      }
    ],
    yAxis: {
      type: 'category',
      data: labels,
      inverse: true,
      axisLabel: { color: palette.muted, width: 68, overflow: 'truncate' },
      axisLine: { lineStyle: { color: palette.border } }
    },
    series: [
      { name: '进件数', type: 'bar', xAxisIndex: 0, data: counts, barMaxWidth: 24, itemStyle: { borderRadius: [0, 5, 5, 0] } },
      { name: '占比', type: 'line', xAxisIndex: 1, data: rates, smooth: true, symbol: 'circle', symbolSize: 6 },
      { name: '累计占比', type: 'line', xAxisIndex: 1, data: cumulativeRates, smooth: true, symbol: 'none', areaStyle: { opacity: 0.16 } }
    ]
  }
}

export function regionalDecisionOption(regions = [], options = {}) {
  const palette = createDashboardPalette(options.root)
  const labels = regions.map(region => region.name)
  const passRates = regions.map(region => Number(region.passRate || 0) * 100)
  const rejectRates = regions.map(region => Number(region.rejectRate || 0) * 100)
  return {
    color: [palette.success, palette.danger],
    tooltip: {
      ...tooltip(palette),
      renderMode: 'richText',
      trigger: 'axis',
      formatter: params => {
        const index = Array.isArray(params) ? params[0]?.dataIndex : params?.dataIndex
        const region = regions[index] || {}
        return `${region.name || ''}\n通过：${Number(region.passCount || 0).toLocaleString('zh-CN')}（${Number(passRates[index] || 0).toFixed(2)}%）\n拒绝：${Number(region.rejectCount || 0).toLocaleString('zh-CN')}（${Number(rejectRates[index] || 0).toFixed(2)}%）`
      }
    },
    legend: {
      top: 0,
      textStyle: { color: palette.muted },
      data: ['通过率', '拒绝率']
    },
    grid: { left: 4, right: regions.length > 12 ? 24 : 10, top: 54, bottom: 20, outerBoundsMode: 'same' },
    dataZoom: regions.length > 12 ? [{
      type: 'slider', yAxisIndex: 0, startValue: 0, endValue: 11,
      width: 10, right: 0, showDetail: false, brushSelect: false
    }] : [],
    xAxis: {
      type: 'value', min: 0, max: 100,
      axisLabel: { color: palette.muted, formatter: '{value}%' },
      splitLine: { lineStyle: { color: palette.border } }
    },
    yAxis: {
      type: 'category',
      data: labels,
      inverse: true,
      axisLabel: { color: palette.muted, width: 68, overflow: 'truncate' },
      axisLine: { lineStyle: { color: palette.border } }
    },
    series: [
      { name: '通过率', type: 'bar', data: passRates, barMaxWidth: 16 },
      { name: '拒绝率', type: 'bar', data: rejectRates, barMaxWidth: 16 }
    ]
  }
}
