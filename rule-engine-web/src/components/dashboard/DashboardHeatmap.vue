<template>
  <div class="heatmap-shell">
    <div v-show="!empty && !error" ref="host" class="heatmap-canvas" role="img" :aria-label="ariaLabel" />
    <el-empty v-if="empty || error" :description="error || emptyDescription" />
    <div v-if="hover && !empty" class="heatmap-tooltip">{{ hover }}</div>
    <div v-if="points.length && ready && !empty && !error" class="heatmap-legend">
      进件密度 <span>低</span><i :style="{ background: gradient }" /><span>高</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { mixThemeColors } from '@/theme/themeConfig'

const props = defineProps({
  ariaLabel: { type: String, required: true },
  empty: Boolean,
  emptyDescription: { type: String, required: true },
  boundaries: { type: Object, default: null },
  view: { type: Object, required: true },
  points: { type: Array, required: true },
  regions: { type: Array, required: true },
  palette: { type: Object, required: true }
})
const emit = defineEmits(['region-click'])
const host = ref(null)
const ready = ref(false)
const error = ref('')
const hover = ref('')
const rampColors = ['#421eb2', '#8c1eb2', '#f27deb', '#fff598', '#f7b74a', '#ff4818']
const gradient = `linear-gradient(to right, ${rampColors.join(',')})`
const regionIndex = computed(() => new Map(props.regions.map(region => [region.id, region])))
let scene, fill, outline, heat
let disposed = false

function shadeRegions() {
  const { surface, primary, border, chartAccent } = props.palette
  const base = mixThemeColors(surface, primary, 0.16)
  const max = props.regions[0]?.count || 1
  fill.color('regionId', id => {
    const count = regionIndex.value.get(id)?.count || 0
    return mixThemeColors(base, primary, 0.55 * count / max)
  }).active({ color: chartAccent })
  outline.color(mixThemeColors(border, primary, 0.35))
}

function updateHeat() {
  hover.value = ''
  const max = props.points.reduce((value, point) => Math.max(value, point.count), 1)
  heat.size('count', count => Math.max(0.08, count / max))
  heat.setData(props.points)
}

onMounted(async () => {
  if (props.empty || !props.boundaries) return
  try {
    const { Scene, BaseMap, HeatmapLayer, LineLayer, PolygonLayer, supportsWebGL } = await import('./dashboardL7')
    if (disposed) return
    if (!supportsWebGL()) {
      error.value = '当前浏览器无法使用 WebGL，请开启硬件加速后重试。'
      return
    }
    const { center, zoom, bounds } = props.view
    // 会话保存的是相对全图的缩放倍数。L7 在第 0 级用 512 像素表示一周经度。
    const worldSize = Math.max(1, Math.min(host.value.clientWidth, host.value.clientHeight))
    const initialZoom = bounds ? 1 : Math.log2(worldSize * zoom / 512)
    scene = new Scene({
      id: host.value, logoVisible: false,
      map: new BaseMap({ center, zoom: initialZoom, minZoom: -3, maxZoom: 19, renderWorldCopies: false })
    })
    scene.on('webglcontextlost', () => { error.value = '地图绘制已中断，请点击恢复视角重新加载。' })
    scene.on('loaded', () => {
      if (disposed) return
      if (bounds) {
        const [[west, north], [east, south]] = bounds
        scene.fitBounds([[west, south], [east, north]], { duration: 0 })
      }
      const features = props.boundaries.features.filter(feature => !feature.properties.mapRole)
        .map(feature => {
          const p = feature.properties
          return { ...feature, properties: { ...p, regionId: p.shapeID || p.ADM0_A3 || p.NAME_ZH } }
        })
      fill = new PolygonLayer({ zIndex: 0 }).source({ type: 'FeatureCollection', features }).shape('fill')
      outline = new LineLayer({ zIndex: 1, enablePicking: false })
        .source(props.boundaries).shape('line').size(0.8)
      shadeRegions()
      fill.on('mousemove', ({ feature }) => {
        const p = feature?.properties
        const region = regionIndex.value.get(p?.regionId)
        hover.value = region
          ? `${region.name} · ${region.count.toLocaleString('zh-CN')} 件\n通过率 ${(100 * region.passRate).toFixed(2)}% · 拒绝率 ${(100 * region.rejectRate).toFixed(2)}%`
          : p?.shapeName || p?.NAME_ZH || ''
      })
      fill.on('mouseout', () => { hover.value = '' })
      fill.on('click', ({ feature }) => {
        const key = feature?.properties?.shapeID || feature?.properties?.NAME_ZH
        if (key) emit('region-click', key)
      })
      heat = new HeatmapLayer({ zIndex: 2, enablePicking: false })
        .source(props.points, { parser: { type: 'json', x: 'longitude', y: 'latitude' } })
        .shape('heatmap')
        .style({ intensity: 2, radius: 20, rampColors: { colors: rampColors, positions: [0, 0.2, 0.4, 0.6, 0.8, 1] } })
      updateHeat()
      scene.addLayer(fill)
      scene.addLayer(outline)
      scene.addLayer(heat)
      ready.value = true
    })
  } catch {
    if (!disposed) error.value = '地图组件加载失败，请点击恢复视角重试。'
  }
})

watch([() => props.palette, regionIndex], () => {
  if (ready.value) { shadeRegions(); scene.render() }
})
watch(() => props.points, () => { if (ready.value) updateHeat() })
onBeforeUnmount(() => {
  disposed = true
  scene?.destroy()
})
</script>

<style scoped>
.heatmap-shell { position: relative; isolation: isolate; height: clamp(360px, 54vh, 660px); overflow: hidden; background: var(--el-bg-color); border-radius: 8px; }
.heatmap-canvas { position: absolute; inset: 0; }
.heatmap-canvas :deep(canvas) { position: absolute; inset: 0; pointer-events: none; }
.heatmap-legend, .heatmap-tooltip { position: absolute; z-index: 4; left: 12px; padding: 6px 10px; border-radius: 5px; background: var(--el-bg-color); color: var(--el-text-color-primary); font-size: 12px; pointer-events: none; }
.heatmap-tooltip { top: 12px; white-space: pre-line; }
.heatmap-legend { bottom: 12px; display: flex; align-items: center; gap: 6px; }
.heatmap-legend i { width: 64px; height: 6px; border-radius: 3px; }
</style>
