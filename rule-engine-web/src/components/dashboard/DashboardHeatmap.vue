<template>
  <div class="heatmap-shell" @pointerleave="clearHover" @pointercancel="clearHover" @keydown.esc="clearHover">
    <div v-show="!empty && !error" ref="host" class="heatmap-canvas" role="img" :aria-label="ariaLabel" />
    <el-empty v-if="empty || error" :description="error || emptyDescription" />
    <aside v-if="hovered && !empty && !error" class="heatmap-tooltip" :class="{ 'heatmap-tooltip--right': panelOnRight }" aria-label="区域进件详情">
      <strong class="hover-location">{{ hoverPath }}</strong>
      <p v-if="dataStatus">{{ dataStatus }}</p>
      <template v-else>
        <p class="hover-count">进件数 <b>{{ (hoverStats?.count || 0).toLocaleString('zh-CN') }}</b> 件</p>
        <dl v-if="hoverStats" class="hover-decisions">
          <dt>通过</dt><dd>{{ hoverStats.passCount }} 件 · {{ (100 * hoverStats.passRate).toFixed(2) }}%</dd>
          <dt>人审</dt><dd>{{ hoverStats.reviewCount }} 件</dd>
          <dt>拒绝</dt><dd>{{ hoverStats.rejectCount }} 件 · {{ (100 * hoverStats.rejectRate).toFixed(2) }}%</dd>
        </dl>
        <small>{{ queryNote }}</small>
      </template>
      <small>边界来源 · <a v-if="hoverSource.url" :href="hoverSource.url" target="_blank" rel="noopener noreferrer">{{ hoverSource.label }}</a><template v-else>{{ hoverSource.label }}</template><template v-if="hoverSource.note"> · {{ hoverSource.note }}</template></small>
    </aside>
    <div v-if="points.length && ready && !empty && !error" class="heatmap-legend">
      <span class="legend-title">进件密度</span>
      <span>低</span><i class="legend-ramp" aria-hidden="true" :style="{ background: gradient }" /><span>高</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { dashboardFeatureAt, dashboardRegionActivity } from '@/utils/dashboardMapLayers'
import { mixThemeColors } from '@/theme/themeConfig'

const props = defineProps({
  ariaLabel: { type: String, required: true },
  empty: Boolean,
  emptyDescription: { type: String, required: true },
  boundaries: { type: Object, default: null },
  world: { type: Object, default: null },
  view: { type: Object, required: true },
  points: { type: Array, required: true },
  country: { type: String, default: '' },
  locationPath: { type: Array, default: () => [] },
  geo: { type: Object, default: null },
  queryNote: { type: String, default: '' },
  dataStatus: { type: String, default: '' },
  source: { type: Object, default: null },
  regions: { type: Array, required: true },
  palette: { type: Object, required: true }
})
const emit = defineEmits(['region-click', 'country-click'])
const host = ref(null)
const ready = ref(false)
const error = ref('')
const hovered = shallowRef(null)
const panelOnRight = ref(false)
const worldSource = { label: '本地全球边界', url: 'https://github.com/JayMuShui/chinese-global-compliant-geodata' }
const hoverSource = computed(() => hovered.value?.properties.contextCountry ? worldSource : props.source || worldSource)
const hoverPath = computed(() => {
  const p = hovered.value?.properties
  if (!p) return ''
  if (p.contextCountry) return p.NAME_ZH
  return [...new Set([...props.locationPath, p.province?.trim(), p.city?.trim(), p.shapeName || p.NAME_ZH].filter(Boolean))].join(' / ')
})
const hoverStats = computed(() => {
  const feature = hovered.value
  if (!feature) return null
  if (feature.properties.contextCountry) {
    return dashboardRegionActivity({ features: [feature] }, props.geo?.points).regions[0]
  }
  return regionIndex.value.get(feature.properties.regionId)
})
const heatRamp = computed(() => {
  const colors = props.palette.heatColors
  return { colors, positions: colors.map((_, index) => index / (colors.length - 1)) }
})
const gradient = computed(() => `linear-gradient(to right, ${heatRamp.value.colors.join(',')})`)
const regionIndex = computed(() => new Map(props.regions.map(region => [region.id, region])))
let scene, fill, outline, highlight, heat, HeatmapLayer
let disposed = false
let starting = false

// 外围底图也参与拾取，拖到相邻国家后可直接点击切换，统计仍只取当前行政区。
const mapFeatures = computed(() => {
  const { surface, primary } = props.palette
  const land = mixThemeColors(surface, primary, 0.16)
  const contextColor = mixThemeColors(surface, primary, 0.11)
  const max = props.regions[0]?.count || 1
  const context = (props.world?.features || []).map(feature => ({
    ...feature, properties: { ...feature.properties, contextCountry: feature.properties.worldKey, regionId: '', fillColor: contextColor }
  }))
  const active = (props.boundaries?.features || []).map(feature => {
    const p = feature.properties
    const regionId = p.shapeID || p.worldKey || p.NAME_ZH
    const count = regionIndex.value.get(regionId)?.count || 0
    return { ...feature, properties: { ...p, regionId, fillColor: mixThemeColors(land, primary, 0.16 * count / max) } }
  })
  const features = [...context, ...active]
  return {
    areas: { type: 'FeatureCollection', features: features.filter(feature => !feature.properties.mapRole) },
    lines: { type: 'FeatureCollection', features }
  }
})

function clearHover() {
  if (!hovered.value) return
  hovered.value = null
  highlight?.hide()
}

function hoverRegion({ lngLat, point, originalEvent }) {
  if (!ready.value || props.empty || originalEvent?.buttons || scene.map.isMoving()) return clearHover()
  const feature = dashboardFeatureAt(mapFeatures.value.areas.features, lngLat.lng, lngLat.lat)
  if (!feature || (feature.properties.contextCountry && feature.properties.contextCountry === props.country)) return clearHover()
  if (feature === hovered.value) return
  hovered.value = feature
  panelOnRight.value = point.x < host.value.clientWidth / 2
  highlight.setData({ type: 'FeatureCollection', features: [feature] })
  highlight.show()
}

function applyCamera() {
  clearHover()
  if (!scene?.map || !host.value) return
  const { bounds, center, zoom } = props.view
  if (bounds) {
    const [[west, north], [east, south]] = bounds
    // 墨卡托投影不能表示极点，南极洲的几何范围可达到南纬 90 度。
    scene.fitBounds([[west, Math.max(south, -85.051129)], [east, Math.min(north, 85.051129)]], { duration: 0 })
  } else {
    const worldSize = Math.max(1, Math.min(host.value.clientWidth, host.value.clientHeight))
    scene.map.jumpTo({ zoom: Math.log2(worldSize * zoom / 512), center, pitch: 0, bearing: 0 })
  }
}

function shadeRegions() {
  const { surface, primary, border, chartAccent } = props.palette
  const land = mixThemeColors(surface, primary, 0.16)
  highlight.color(mixThemeColors(land, chartAccent, 0.32))
  outline.color(mixThemeColors(border, primary, 0.22))
}

function styleHeat() {
  if (!heat) return
  const radius = Math.max(14, Math.min(24, 14 + scene.getZoom() * 1.5))
  heat.style({ radius, intensity: 1.9, opacity: 0.86, rampColors: heatRamp.value })
}

function updateHeat() {
  clearHover()
  if (heat && !heat.inited) return
  if (heat) scene.removeLayer(heat)
  heat = null
  const points = props.points
  if (!points.length) return
  // L7 的二维热力图持有独立帧缓冲；替换数据时一并替换图层，避免重绘半初始化的模型。
  const max = points.reduce((value, point) => Math.max(value, point.count), 1)
  heat = new HeatmapLayer({ zIndex: 3, enablePicking: false })
    .source(points, { parser: { type: 'json', x: 'longitude', y: 'latitude' } })
    .shape('heatmap').size('count', count => Math.max(0.14, Math.sqrt(count / max)))
  styleHeat()
  heat.once('inited', () => {
    if (!disposed && points !== props.points) queueMicrotask(() => { if (!disposed) updateHeat() })
  })
  scene.addLayer(heat)
}

async function startScene() {
  if (props.empty || !props.boundaries || scene || starting) return
  starting = true
  try {
    const l7 = await import('./dashboardL7')
    const { Scene, BaseMap, LineLayer, PolygonLayer, supportsWebGL } = l7
    HeatmapLayer = l7.HeatmapLayer
    if (disposed) return
    if (!supportsWebGL()) {
      error.value = '当前浏览器无法使用 WebGL，请开启硬件加速后重试。'
      return
    }
    scene = new Scene({
      id: host.value, logoVisible: false,
      map: new BaseMap({ center: props.view.center || [0, 0], zoom: 0, minZoom: -3, maxZoom: 19, renderWorldCopies: false })
    })
    scene.on('webglcontextlost', () => { error.value = '地图绘制已中断，请点击恢复视角重新加载。' })
    scene.on('loaded', () => {
      if (disposed) return
      applyCamera()
      fill = new PolygonLayer({ zIndex: 0, enablePicking: false }).source(mapFeatures.value.areas).shape('fill').color('fillColor', color => color)
      highlight = new PolygonLayer({ zIndex: 1, enablePicking: false, visible: false })
        .source({ type: 'FeatureCollection', features: [] }).shape('fill')
      outline = new LineLayer({ zIndex: 2, enablePicking: false })
        .source(mapFeatures.value.lines).shape('line').size(0.5)
      shadeRegions()
      scene.map.on('zoomend', () => {
        if (heat && !disposed) { styleHeat(); scene.render() }
      })
      scene.map.on('mousemove', hoverRegion)
      scene.map.on('movestart', clearHover)
      scene.map.on('click', ({ lngLat }) => {
        const p = dashboardFeatureAt(mapFeatures.value.areas.features, lngLat.lng, lngLat.lat)?.properties
        clearHover()
        if (!p || (p.contextCountry && p.contextCountry === props.country)) return
        if (p.contextCountry) emit('country-click', p.contextCountry)
        else emit('region-click', p.shapeID || p.NAME_ZH)
      })
      scene.addLayer(fill)
      scene.addLayer(highlight)
      scene.addLayer(outline)
      updateHeat()
      ready.value = true
    })
  } catch {
    if (!disposed) error.value = '地图组件加载失败，请点击恢复视角重试。'
  } finally {
    starting = false
  }
}

defineExpose({ error, recenter: applyCamera })
onMounted(() => {
  window.addEventListener('blur', clearHover)
  startScene()
})
watch(() => props.empty, empty => {
  if (empty) clearHover()
  else startScene()
}, { flush: 'post' })
watch(mapFeatures, ({ areas, lines }) => {
  if (!ready.value || props.empty) return
  clearHover()
  fill.setData(areas)
  outline.setData(lines)
})
watch(() => props.view, () => { if (ready.value && !props.empty) applyCamera() }, { flush: 'post' })
watch(() => props.palette, () => {
  if (!ready.value) return
  shadeRegions()
  styleHeat()
  scene.render()
})
watch(() => props.points, () => { if (ready.value) updateHeat() })
onBeforeUnmount(() => {
  disposed = true
  window.removeEventListener('blur', clearHover)
  scene?.destroy()
})
</script>

<style scoped>
.heatmap-shell { position: relative; isolation: isolate; height: clamp(360px, 54vh, 660px); overflow: hidden; background: var(--el-bg-color); border-radius: 8px; }
.heatmap-canvas { position: absolute; inset: 0; }
.heatmap-canvas :deep(canvas) { position: absolute; inset: 0; pointer-events: none; }
.heatmap-legend, .heatmap-tooltip {
  position: absolute;
  z-index: 4;
  left: 12px;
  box-sizing: border-box;
  max-width: calc(100% - 24px);
  padding: 6px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  box-shadow: 0 2px 8px rgb(21 36 59 / 8%);
  font-size: 12px;
  line-height: 1.5;
  pointer-events: none;
}
.heatmap-tooltip { top: 12px; width: max-content; max-width: min(280px, calc(100% - 24px)); max-height: calc(100% - 24px); overflow: auto; padding: 10px 12px; pointer-events: auto; font-variant-numeric: tabular-nums; }
.heatmap-tooltip--right { left: auto; right: 12px; }
.hover-location { display: block; font-size: 13px; }
.heatmap-tooltip p { margin: 5px 0; }
.hover-decisions { display: grid; grid-template-columns: auto auto; gap: 2px 16px; margin: 4px 0 8px; }
.hover-decisions dt, .heatmap-tooltip small { color: var(--el-text-color-secondary); }
.hover-decisions dd { margin: 0; text-align: right; }
.heatmap-tooltip small { display: block; font-size: 11px; }
.heatmap-tooltip a { color: var(--el-color-primary); }
.heatmap-legend { bottom: 12px; display: flex; flex-wrap: wrap; align-items: center; gap: 6px; color: var(--el-text-color-secondary); }
.legend-title { margin-right: 2px; color: var(--el-text-color-primary); font-weight: 600; }
.legend-ramp { width: 64px; height: 6px; border-radius: 3px; }
</style>
