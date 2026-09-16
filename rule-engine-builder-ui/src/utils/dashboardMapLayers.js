import polygonClipping from 'polygon-clipping'

export const DASHBOARD_REGION_MAP_NAME = 'dashboard-administrative-region'
export const CHINA_MAP_BOUNDS = [[72, 55], [137, 2]]
const CHINA_CODES = new Set(['CHN', 'TWN', 'HKG', 'MAC'])
const CHINA_MAP_LAYERS = {
  code: 'CHN',
  name: '中国',
  layers: [0, 1, 2, 3].map(level => ({
    level: `ADM${level}`,
    url: `/maps/dashboard-china-ADM${level}.geojson`,
    year: level < 2 ? '国内边界源快照' : '2024',
    label: ['中国全图', '省级行政区', '地市级行政区', '区县级行政区'][level]
  }))
}

export const ADMIN_LEVEL_LABELS = {
  ADM0: '国家／地区边界',
  ADM1: '一级行政区（省／州等）',
  ADM2: '二级行政区（市／县等）',
  ADM3: '三级行政区（区／县等）',
  ADM4: '四级行政区',
  ADM5: '五级行政区'
}

export function dashboardWorldCountries(geoJson) {
  return Object.fromEntries((geoJson.features || []).map(feature => {
    const properties = feature.properties
    return [properties.NAME_ZH, properties.ADM0_A3]
  }))
}

// 离岛跨越日期变更线时，全域包围盒会把省市县压缩到很小；初始视角聚焦主体陆地，仍可漫游。
export function dashboardCountryBounds(geoJson) {
  const bounds = {}
  for (const feature of geoJson.features || []) {
    const polygons = feature.geometry.type === 'MultiPolygon'
      ? feature.geometry.coordinates : [feature.geometry.coordinates]
    const ringArea = ring => Math.abs(ring.reduce((area, point, index) => {
      const next = ring[(index + 1) % ring.length]
      return area + point[0] * next[1] - next[0] * point[1]
    }, 0))
    const ring = polygons.map(polygon => polygon[0]).filter(Boolean)
      .sort((a, b) => ringArea(b) - ringArea(a))[0]
    if (!ring?.length) continue
    const xs = ring.map(point => point[0])
    const ys = ring.map(point => point[1])
    const west = Math.min(...xs)
    const east = Math.max(...xs)
    const south = Math.min(...ys)
    const north = Math.max(...ys)
    const paddingX = (east - west) * 0.1
    const paddingY = (north - south) * 0.1
    bounds[feature.properties.ADM0_A3] = [
      [Math.max(-180, west - paddingX), Math.min(90, north + paddingY)],
      [Math.min(180, east + paddingX), Math.max(-90, south - paddingY)]
    ]
  }
  return bounds
}

export function dashboardCountryOptions(catalog, worldCountries) {
  const names = Object.fromEntries(Object.entries(worldCountries).map(([name, code]) => [code, name]))
  const countries = (catalog?.countries || []).filter(country => !CHINA_CODES.has(country.code)).map(country => ({
    ...country,
    name: names[country.code] || country.name
  })).sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
  return [CHINA_MAP_LAYERS, ...countries]
}

export function prioritizeChinaBoundary(geoJson, chinaGeometry) {
  const polygons = chinaGeometry.type === 'Polygon' ? [chinaGeometry.coordinates] : chinaGeometry.coordinates
  const clips = polygons.map(coordinates => ({ coordinates, bounds: geometryBounds(coordinates) }))
  return {
    ...geoJson,
    features: geoJson.features.flatMap(feature => {
      const bounds = geometryBounds(feature.geometry.coordinates)
      const overlapping = clips.filter(clip => bounds[0] <= clip.bounds[2] && bounds[2] >= clip.bounds[0] &&
        bounds[1] <= clip.bounds[3] && bounds[3] >= clip.bounds[1])
      if (!overlapping.length) return [feature]
      const coordinates = polygonClipping.difference(feature.geometry.coordinates, ...overlapping.map(clip => clip.coordinates))
      return coordinates.length ? [{ ...feature, geometry: { type: 'MultiPolygon', coordinates } }] : []
    })
  }
}

function geometryBounds(coordinates, bounds = [Infinity, Infinity, -Infinity, -Infinity]) {
  if (typeof coordinates[0] === 'number') {
    bounds[0] = Math.min(bounds[0], coordinates[0])
    bounds[1] = Math.min(bounds[1], coordinates[1])
    bounds[2] = Math.max(bounds[2], coordinates[0])
    bounds[3] = Math.max(bounds[3], coordinates[1])
  } else coordinates.forEach(child => geometryBounds(child, bounds))
  return bounds
}

export function dashboardRegionNames(geoJson) {
  if (!Array.isArray(geoJson.features) || !geoJson.features.length) {
    throw new Error('未找到可用的行政区边界')
  }
  const names = {}
  for (const feature of geoJson.features) {
    const { shapeID, shapeName } = feature.properties || {}
    if (!shapeID || !shapeName || !['Polygon', 'MultiPolygon'].includes(feature.geometry?.type)) {
      throw new Error('行政区边界数据不完整，请稍后重试')
    }
    if (Object.hasOwn(names, shapeID)) throw new Error('行政区边界标识重复，请稍后重试')
    names[shapeID] = shapeName
  }
  return names
}
