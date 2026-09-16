import { init, registerMap, use } from 'echarts/core'
import { BarChart, HeatmapChart, PieChart } from 'echarts/charts'
import {
  GeoComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  VisualMapComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { mixThemeColors } from '@/theme/themeConfig'

use([
  BarChart,
  HeatmapChart,
  PieChart,
  GeoComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  VisualMapComponent,
  CanvasRenderer
])

export const DASHBOARD_MAP_NAME = 'dashboard-world'

function css(root, name, fallback) {
  if (!root || typeof getComputedStyle !== 'function') return fallback
  return getComputedStyle(root).getPropertyValue(name).trim() || fallback
}

export function createDashboardPalette(root = document.documentElement) {
  return {
    primary: css(root, '--el-color-primary', '#5b6cff'),
    primaryLight: css(root, '--el-color-primary-light-5', '#a6afff'),
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
}

export function createChartInstance(element) {
  return init(element, null, { renderer: 'canvas' })
}

export function registerDashboardMap(geoJson, mapName = DASHBOARD_MAP_NAME) {
  registerMap(mapName, geoJson)
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
      return `${label}\n进件数：${applications}\n命中数：${hits}\n命中率：${rate}%`
    }
  }
  return option
}

export function distributionPieOption(items = [], options = {}) {
  const palette = createDashboardPalette(options.root)
  return {
    color: [palette.primary, palette.success, palette.warning,
      palette.danger, palette.secondary, palette.info],
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

export function geoHeatmapOption(points = [], options = {}) {
  const palette = createDashboardPalette(options.root)
  const max = Math.max(1, ...points.map(point => point.count || 0))
  const center = options.fitRegion ? undefined : (Array.isArray(options.center) ? options.center : [104, 35])
  const zoom = options.fitRegion ? 1 : (Number.isFinite(options.zoom) ? options.zoom : 1.5)
  const regionLabel = params => options.regionNames?.[params.name] || params.name || ''
  return {
    tooltip: {
      ...tooltip(palette),
      renderMode: 'richText',
      formatter: params => Array.isArray(params.value)
        ? `${params.value[0]}, ${params.value[1]}：${params.value[2]}`
        : regionLabel(params)
    },
    visualMap: {
      show: points.length > 0,
      min: 0,
      max,
      calculable: true,
      left: 16,
      bottom: 12,
      textStyle: { color: palette.muted },
      inRange: {
        color: [mixThemeColors(palette.surface, palette.primary, 0.2), palette.primary, palette.secondary]
      }
    },
    geo: {
      map: options.mapName || DASHBOARD_MAP_NAME,
      nameProperty: options.nameProperty || 'NAME_ZH',
      tooltip: { show: true, formatter: regionLabel },
      roam: true,
      center,
      zoom,
      boundingCoords: options.fitRegion ? options.bounds : undefined,
      regions: (options.decorations || []).map(decoration => ({
        name: decoration.id,
        silent: true,
        tooltip: { show: false },
        label: { show: false },
        emphasis: { disabled: true, label: { show: false } },
        ...(decoration.role === 'boundary' ? { itemStyle: { areaColor: palette.primary, borderColor: palette.primary } } : {})
      })),
      itemStyle: {
        areaColor: mixThemeColors(palette.surface, palette.primary, 0.1),
        borderColor: mixThemeColors(palette.border, palette.primary, 0.25)
      },
      emphasis: {
        label: { show: true, color: palette.text, formatter: regionLabel },
        itemStyle: {
          areaColor: mixThemeColors(palette.surface, palette.primary, 0.24),
          borderColor: palette.primary,
          borderWidth: 2
        }
      }
    },
    series: [{
      type: 'heatmap',
      coordinateSystem: 'geo',
      pointSize: 10,
      blurSize: 16,
      data: points.map(point => [point.longitude, point.latitude, point.count])
    }]
  }
}
