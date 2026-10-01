import { readFileSync } from 'node:fs'
import { CHINA_MAP_BOUNDS, dashboardCountryOptions } from '@/utils/dashboardMapLayers'

const read = name => JSON.parse(readFileSync(`public/maps/${name}`, 'utf8'))

function containsRing(ring, point) {
  let inside = false
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i]
    const [xj, yj] = ring[j]
    if ((yi > point[1]) !== (yj > point[1]) && point[0] < (xj - xi) * (point[1] - yi) / (yj - yi) + xi) inside = !inside
  }
  return inside
}

function contains(geometry, point) {
  const polygons = geometry.type === 'Polygon' ? [geometry.coordinates] : geometry.coordinates
  return polygons.some(polygon => containsRing(polygon[0], point) && !polygon.slice(1).some(ring => containsRing(ring, point)))
}

describe('中国边界资源', () => {
  test('中国全图包含台湾、香港、澳门及国内边界源中的藏南区域，全球图保持同一边界', () => {
    const national = read('dashboard-china-ADM0.geojson').features[0]
    const world = read('dashboard-world.geojson')
    const china = world.features.find(feature => feature.properties.ADM0_A3 === 'CHN')
    expect(china.geometry).toEqual(national.geometry)
    for (const point of [[121.56, 25.03], [114.17, 22.32], [113.55, 22.19], [91.87, 27.49]]) {
      expect(contains(china.geometry, point)).toBe(true)
      expect(world.features.filter(feature => feature.properties.ADM0_A3 !== 'CHN').some(feature => contains(feature.geometry, point))).toBe(false)
    }
    expect(world.features.some(feature => ['TWN', 'HKG', 'MAC'].includes(feature.properties.ADM0_A3))).toBe(false)
  })

  test('34 个省级行政区包含台湾港澳，完整保留 10 个源数据断续线面片', () => {
    const provinces = read('dashboard-china-ADM1.geojson')
    const regions = provinces.features.filter(feature => !feature.properties.mapRole)
    expect(regions).toHaveLength(34)
    expect(regions.map(feature => feature.properties.shapeName)).toEqual(expect.arrayContaining(['台湾省', '香港特别行政区', '澳门特别行政区']))
    const maritime = provinces.features.find(feature => feature.properties.mapRole === 'boundary')
    expect(maritime.geometry.coordinates).toHaveLength(10)
    const points = maritime.geometry.coordinates.flat(2)
    expect(Math.min(...points.map(point => point[1]))).toBeLessThan(4)
    expect(points.every(([longitude, latitude]) => longitude >= CHINA_MAP_BOUNDS[0][0] && longitude <= CHINA_MAP_BOUNDS[1][0] &&
      latitude <= CHINA_MAP_BOUNDS[0][1] && latitude >= CHINA_MAP_BOUNDS[1][1])).toBe(true)
  })

  test('陆地边界包含钓鱼岛、赤尾屿和南海岛礁，不靠断续线充当岛屿', () => {
    const points = read('dashboard-china-land.geojson').features[0].geometry.coordinates.flat(2)
    expect(points.some(([x, y]) => x >= 123.3 && x <= 123.7 && y >= 25.6 && y <= 25.9)).toBe(true)
    expect(points.some(([x, y]) => x >= 124.4 && x <= 124.7 && y >= 25.8 && y <= 26.1)).toBe(true)
    expect(Math.min(...points.map(point => point[1]))).toBeLessThan(4)
  })

  test.each([2, 3])('ADM%s 保留完整中国背景及断续线，按国标码独立识别市县', level => {
    const map = read(`dashboard-china-ADM${level}.geojson`)
    const outline = map.features.find(feature => feature.properties.mapRole === 'outline')
    const maritime = map.features.find(feature => feature.properties.mapRole === 'boundary')
    expect(outline.geometry).toEqual(read('dashboard-china-ADM0.geojson').features[0].geometry)
    expect(maritime.geometry.coordinates).toHaveLength(10)
    const regions = map.features.filter(feature => !feature.properties.mapRole)
    expect(regions.some(feature => feature.properties.province === '台湾省')).toBe(true)
    expect(new Set(regions.map(feature => feature.properties.shapeID)).size).toBe(regions.length)
    expect(regions.every(feature => feature.properties.shapeName)).toBe(true)
  })

  test('海外目录不能再覆盖中国层级或把台湾港澳单列', () => {
    const catalog = read('dashboard-boundaries.json')
    expect(catalog.countries.some(country => ['CHN', 'TWN', 'HKG', 'MAC'].includes(country.code))).toBe(false)
    const countries = dashboardCountryOptions(catalog, {})
    expect(countries[0].code).toBe('CHN')
    expect(countries[0].layers.every(layer => layer.url.startsWith('/maps/dashboard-china-'))).toBe(true)
  })
})
