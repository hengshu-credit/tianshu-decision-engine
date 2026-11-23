import { readFileSync } from 'node:fs'
import {
  dashboardCountryBounds, dashboardCountryOptions, dashboardFeatureAt, dashboardRegionActivity,
  dashboardWorldCountries, normalizeChileRegionNames, prepareDashboardWorld,
} from '@/utils/dashboardMapLayers'

const read = name => JSON.parse(readFileSync(`public/maps/${name}`, 'utf8'))
const world = prepareDashboardWorld(read('dashboard-world.geojson'))
const countries = dashboardCountryOptions(read('dashboard-boundaries.json'), dashboardWorldCountries(world))
const bounds = dashboardCountryBounds(world)

// GeoNames cities15000 城市中心坐标（经度、纬度），含多岛国家和粗底图曾遗漏的沿海城市。
const cases = [
  ['KAZ', '哈萨克斯坦', [[71.44598, 51.1801], [76.91150, 43.25249]]],
  ['MYS', '马来西亚', [[101.68653, 3.14120], [110.33333, 1.55]]],
  ['IDN', '印度尼西亚', [[106.84513, -6.21462], [98.66667, 3.58333], [140.71813, -2.53371]]],
  ['VNM', '越南', [[105.84117, 21.02450], [106.62965, 10.82302]]],
  ['THA', '泰国', [[100.50144, 13.75398], [98.98468, 18.79038]]],
  ['PHL', '菲律宾', [[120.98220, 14.60420], [125.61278, 7.07306]]],
  ['MEX', '墨西哥', [[-99.12766, 19.42847], [-103.34749, 20.67738]]],
  ['CHL', '智利', [[-70.64827, -33.45694], [-73.04977, -36.82699]]],
  ['UZB', '乌兹别克斯坦', [[69.21627, 41.26465], [66.96445, 39.65456]]],
  ['PAK', '巴基斯坦', [[73.04329, 33.72148], [67.01040, 24.86080]]],
  ['BRA', '巴西', [[-47.92972, -15.77972], [-46.63611, -23.54750]]],
  ['COL', '哥伦比亚', [[-74.08175, 4.60971], [-75.57151, 6.24500]]],
  ['LAO', '老挝', [[102.60000, 17.96667], [102.15250, 19.89330]]],
  ['NGA', '尼日利亚', [[7.49508, 9.05785], [3.39467, 6.45407]]],
  ['IND', '印度', [[77.23149, 28.65195], [72.88261, 19.07283]]],
]

test.each(cases)('%s 的国家、统计归属、取景和层级目录与城市样本一致', (code, name, coordinates) => {
  const country = countries.find(item => item.code === code)
  expect(country.name).toBe(name)
  const levels = country.layers.map(layer => layer.level)
  expect(levels.slice(0, 3)).toEqual(['ADM0', 'ADM1', 'ADM2'])
  expect(new Set(levels).size).toBe(levels.length)
  const [[west, north], [east, south]] = bounds[code]
  for (const [x, y] of coordinates) {
    expect(dashboardFeatureAt(world.features, x, y)?.properties.worldKey).toBe(code)
    expect(x).toBeGreaterThanOrEqual(west)
    expect(x).toBeLessThanOrEqual(east)
    expect(y).toBeGreaterThanOrEqual(south)
    expect(y).toBeLessThanOrEqual(north)
  }
  const points = coordinates.map(([longitude, latitude]) => ({ longitude, latitude, count: 1 }))
  expect(dashboardRegionActivity(world, points).regions)
    .toEqual([expect.objectContaining({ id: code, count: points.length, rate: 1 })])
})

test('智利一级区标签恢复重音，已正确的名称及标识保留', () => {
  const names = ['RegiÃ³n de Ã\u0091uble', 'Región de Ñuble']
  const input = { features: names.map((shapeName, shapeID) => ({ properties: { shapeID, shapeName } })) }
  expect(normalizeChileRegionNames(input).features.map(f => f.properties)).toEqual([
    { shapeID: 0, shapeName: 'Región de Ñuble' }, { shapeID: 1, shapeName: 'Región de Ñuble' },
  ])
})
