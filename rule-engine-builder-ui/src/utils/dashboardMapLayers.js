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

/**
 * 只保留父级行政区范围内的子级边界。
 *
 * 行政区目录并不保证各国都提供统一的父级编码，因此这里以几何相交
 * 作为最终判定，兼容中国 adcode 和 geoBoundaries 的不同编码方式。
 */
export function filterDashboardRegionFeatures(geoJson, parentFeature) {
  if (!parentFeature?.geometry) return geoJson
  return {
    ...geoJson,
    features: (geoJson.features || []).filter(feature => {
      if (feature.properties?.mapRole) return false
      const propertyMatch = parentPropertyMatch(feature.properties, parentFeature.properties)
      return propertyMatch === null
        ? geometryIntersects(feature.geometry, parentFeature.geometry)
        : propertyMatch
    })
  }
}

/**
 * 过滤掉当前地图边界外的热力点。
 * 地图装饰线（outline/boundary）不参与点位判定，避免全中国外框把海上点
 * 或当前下钻区域外的点重新带回来。
 */
export function dashboardPointsInGeoJson(points = [], geoJson) {
  const boundaries = (geoJson?.features || [])
    .filter(feature => !feature.properties?.mapRole && feature.geometry)
  if (!boundaries.length) return []
  return points.filter(point => {
    const longitude = Number(point?.longitude)
    const latitude = Number(point?.latitude)
    if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return false
    return boundaries.some(feature => pointInGeometry([longitude, latitude], feature.geometry))
  })
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

function geometryIntersects(left, right) {
  if (!left || !right || !['Polygon', 'MultiPolygon'].includes(left.type) ||
    !['Polygon', 'MultiPolygon'].includes(right.type)) return false
  const leftBounds = geometryBounds(left.coordinates)
  const rightBounds = geometryBounds(right.coordinates)
  if (leftBounds[0] > rightBounds[2] || leftBounds[2] < rightBounds[0] ||
    leftBounds[1] > rightBounds[3] || leftBounds[3] < rightBounds[1]) return false

  const leftPolygons = left.type === 'Polygon' ? [left.coordinates] : left.coordinates
  const rightPolygons = right.type === 'Polygon' ? [right.coordinates] : right.coordinates
  if (leftPolygons.some(polygon => polygon.some(ring => ring.some(point => pointInGeometry(point, right, false)))) ||
    rightPolygons.some(polygon => polygon.some(ring => ring.some(point => pointInGeometry(point, left, false))))) {
    return true
  }
  try {
    const intersection = polygonClipping.intersection(leftPolygons, rightPolygons)
    return intersection.some(polygon => polygon.some(ring => ringArea(ring) > 1e-10))
  } catch (error) {
    return false
  }
}

function parentPropertyMatch(child = {}, parent = {}) {
  const parentName = normalizeRegionName(parent.shapeName || parent.full_name || parent.name)
  if (parentName) {
    const childNames = ['province', 'city'].map(key => normalizeRegionName(child[key])).filter(Boolean)
    if (childNames.length) return childNames.includes(parentName)
  }
  const parentCode = String(parent.adcode || parent.shapeID || '').replace(/\D/g, '')
  const childCode = String(child.gb || child.adcode || child.shapeID || '').replace(/\D/g, '')
  if (parentCode.length >= 6 && childCode.length >= 6 &&
    parentCode.slice(0, 6) === childCode.slice(0, 6)) return true
  return null
}

function normalizeRegionName(value) {
  return typeof value === 'string' ? value.replace(/\s+/g, '') : ''
}

function pointInGeometry(point, geometry, includeBoundary = true) {
  if (!geometry || !Array.isArray(geometry.coordinates)) return false
  const polygons = geometry.type === 'Polygon' ? [geometry.coordinates] : geometry.coordinates
  return geometry.type === 'Polygon' || geometry.type === 'MultiPolygon'
    ? polygons.some(polygon => pointInPolygon(point, polygon, includeBoundary))
    : false
}

function pointInPolygon(point, polygon, includeBoundary) {
  if (!polygon?.[0]?.length) return false
  if (!pointInRing(point, polygon[0], includeBoundary)) return false
  return !polygon.slice(1).some(ring => pointInRing(point, ring, includeBoundary))
}

function pointInRing([x, y], ring, includeBoundary) {
  let inside = false
  for (let index = 0, previous = ring.length - 1; index < ring.length; previous = index++) {
    const [xi, yi] = ring[index]
    const [xj, yj] = ring[previous]
    const cross = (x - xi) * (yj - yi) - (y - yi) * (xj - xi)
    const onEdge = Math.abs(cross) < 1e-10 &&
      x >= Math.min(xi, xj) - 1e-10 && x <= Math.max(xi, xj) + 1e-10 &&
      y >= Math.min(yi, yj) - 1e-10 && y <= Math.max(yi, yj) + 1e-10
    if (onEdge) return includeBoundary
    if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
  }
  return inside
}

function ringArea(ring) {
  return Math.abs(ring.reduce((area, point, index) => {
    const next = ring[(index + 1) % ring.length]
    return area + point[0] * next[1] - next[0] * point[1]
  }, 0)) / 2
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
