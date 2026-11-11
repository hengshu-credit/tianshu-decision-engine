import polygonClipping from 'polygon-clipping'

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

function largestRing(feature) {
  const { type, coordinates } = feature.geometry
  const polygons = type === 'MultiPolygon' ? coordinates : [coordinates]
  let largest = []
  let maxArea = 0
  for (const [ring] of polygons) {
    const area = ringArea(ring)
    if (area > maxArea) {
      largest = ring
      maxArea = area
    }
  }
  return largest
}

// 世界底图里部分属地共用 ISO 编码，另有多个地区使用 -99。让每个可点击元素有独立标识，
// 仅把国家主体关联到该 ISO 编码的行政区目录。
export function prepareDashboardWorld(geoJson) {
  const primary = new Map()
  for (const feature of geoJson.features) {
    const code = feature.properties.ADM0_A3
    if (!/^[A-Z]{3}$/.test(code)) continue
    const other = primary.get(code)
    if (!other || ringArea(largestRing(feature)) > ringArea(largestRing(other))) primary.set(code, feature)
  }
  return { ...geoJson, features: geoJson.features.map(feature => {
    const p = feature.properties
    const worldKey = primary.get(p.ADM0_A3) === feature ? p.ADM0_A3 : `world:${p.NAME_ZH}`
    return { ...feature, properties: { ...p, worldKey } }
  }) }
}

export function dashboardWorldCountries(geoJson) {
  return Object.fromEntries(geoJson.features.map(({ properties: p }) => [p.NAME_ZH, p.worldKey || p.ADM0_A3]))
}

// 跨洲国家聚焦主体陆地，离岛仍可漫游查看。
export function dashboardCountryBounds(geoJson) {
  const bounds = {}
  for (const feature of geoJson.features) {
    const ring = largestRing(feature)
    if (!ring.length) continue
    bounds[feature.properties.worldKey || feature.properties.ADM0_A3] = paddedBounds(geometryBounds(ring), 0.1)
  }
  return bounds
}

export function dashboardCountryOptions(catalog, worldCountries) {
  const countries = new Map((catalog?.countries || [])
    .filter(country => !CHINA_CODES.has(country.code)).map(country => [country.code, country]))
  for (const [name, code] of Object.entries(worldCountries)) {
    if (CHINA_CODES.has(code)) continue
    const layers = countries.get(code)?.layers || []
    countries.set(code, { code, name, layers: layers.some(layer => layer.level === 'ADM0')
      ? layers : [{ level: 'ADM0' }, ...layers] })
  }
  return [CHINA_MAP_LAYERS, ...Array.from(countries.values()).sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))]
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

const cachedBounds = new WeakMap()
function featureBounds(geometry) {
  if (!cachedBounds.has(geometry)) cachedBounds.set(geometry, geometryBounds(geometry.coordinates))
  return cachedBounds.get(geometry)
}

function containsPoint(geometry, point) {
  const [west, south, east, north] = featureBounds(geometry)
  const [lng, lat] = point
  return lng >= west && lng <= east && lat >= south && lat <= north && pointInGeometry(point, geometry)
}

// 按绘制顺序从上往下拾取，优先命中行政区，其次是周边国家。
export function dashboardFeatureAt(features, longitude, latitude) {
  const point = [longitude, latitude]
  return features.findLast(feature => containsPoint(feature.geometry, point))
}

/** 一次归属判定同时生成热力点和侧栏统计，装饰边界不参与统计。 */
export function dashboardRegionActivity(geoJson, points = []) {
  if (!Array.isArray(points) || !points.length) return { points: [], regions: [] }
  const regions = (geoJson?.features || [])
    .filter(feature => !feature.properties?.mapRole && feature.geometry)
    .map(feature => ({
      feature,
      id: feature.properties?.shapeID || feature.properties?.worldKey || feature.properties?.ADM0_A3 || feature.properties?.NAME_ZH,
      name: feature.properties?.shapeName || feature.properties?.NAME_ZH || feature.properties?.shapeID
    }))
    .filter(region => region.id && region.name)
  const totals = regions.map(region => ({
    id: region.id,
    name: region.name,
    count: 0,
    passCount: 0,
    reviewCount: 0,
    rejectCount: 0
  }))
  const visiblePoints = []
  for (const point of points) {
    const longitude = point?.longitude
    const latitude = point?.latitude
    const count = Number(point?.count || 0)
    if (!Number.isFinite(longitude) || !Number.isFinite(latitude) || !Number.isFinite(count) || count <= 0) continue
    const pointLocation = [longitude, latitude]
    const index = regions.findIndex(({ feature }) => containsPoint(feature.geometry, pointLocation))
    if (index < 0) continue
    visiblePoints.push(point)
    const target = totals[index]
    target.count += count
    target.passCount += nonNegativeCount(point?.passCount)
    target.reviewCount += nonNegativeCount(point?.reviewCount)
    target.rejectCount += nonNegativeCount(point?.rejectCount)
  }
  const total = totals.reduce((sum, region) => sum + region.count, 0)
  const statistics = totals
    .filter(region => region.count > 0)
    .sort((first, second) => second.count - first.count || first.name.localeCompare(second.name, 'zh-CN'))
    .map(region => ({
      ...region,
      rate: total ? region.count / total : 0,
      passRate: region.passCount / region.count,
      rejectRate: region.rejectCount / region.count
    }))
  return { points: visiblePoints, regions: statistics }
}

// 优先按选中的几何范围取景；缺少几何时使用已计算的国家边界。
export function dashboardMapView(geoJson, fallbackBounds) {
  const coordinates = (geoJson?.features || [])
    .filter(feature => !feature.properties?.mapRole && feature.geometry?.coordinates)
    .map(feature => feature.geometry.coordinates)
  const rawBounds = coordinates.reduce((bounds, value) => geometryBounds(value, bounds),
    [Infinity, Infinity, -Infinity, -Infinity])
  const hasBounds = rawBounds.every(Number.isFinite) && rawBounds[2] > rawBounds[0] && rawBounds[3] > rawBounds[1]
  const bounds = hasBounds ? paddedBounds(rawBounds) : fallbackBounds
  if (!bounds) return { bounds: undefined, center: undefined, zoom: 1 }
  return {
    bounds,
    center: [
      (bounds[0][0] + bounds[1][0]) / 2,
      (bounds[0][1] + bounds[1][1]) / 2
    ],
    zoom: 1
  }
}

function paddedBounds([west, south, east, north], padding = 0.08) {
  const paddingX = (east - west) * padding
  const paddingY = (north - south) * padding
  return [
    [Math.max(-180, west - paddingX), Math.min(90, north + paddingY)],
    [Math.min(180, east + paddingX), Math.max(-90, south - paddingY)]
  ]
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

function nonNegativeCount(value) {
  const count = Number(value || 0)
  return Number.isFinite(count) && count > 0 ? count : 0
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
