import {
  dashboardCountryBounds,
  dashboardCountryOptions,
  dashboardPointsInGeoJson,
  dashboardRegionNames,
  dashboardWorldCountries,
  filterDashboardRegionFeatures,
  prioritizeChinaBoundary
} from '@/utils/dashboardMapLayers'

function region(id, name) {
  return {
    type: 'Feature',
    properties: { shapeID: id, shapeName: name },
    geometry: { type: 'Polygon', coordinates: [[[0, 0], [1, 0], [0, 1], [0, 0]]] }
  }
}

describe('dashboard administrative map layers', () => {
  test('国家使用 ISO 标识与边界目录关联，本地底图缺少的小国仍可选择', () => {
    const world = dashboardWorldCountries({ features: [
      { properties: { NAME_ZH: '美国', ADM0_A3: 'USA' } }
    ] })
    const catalog = { countries: [
      { code: 'USA', name: 'United States', layers: [{ level: 'ADM2' }] },
      { code: 'MCO', name: 'Monaco', layers: [{ level: 'ADM0' }] }
    ] }
    expect(world['美国']).toBe('USA')
    expect(dashboardCountryOptions(catalog, world)).toEqual(expect.arrayContaining([
      { code: 'USA', name: '美国', layers: [{ level: 'ADM2' }] },
      { code: 'MCO', name: 'Monaco', layers: [{ level: 'ADM0' }] }
    ]))
  })

  test('同名市县使用独立区域 ID，不会联动高亮', () => {
    const names = dashboardRegionNames({ features: [
      region('county-1', 'Washington'), region('county-2', 'Washington')
    ] })
    expect(names).toEqual({ 'county-1': 'Washington', 'county-2': 'Washington' })
  })

  test('主体陆地与远离的海岛共存时，初始视角不会横跨整个世界', () => {
    const bounds = dashboardCountryBounds({ features: [{
      properties: { ADM0_A3: 'USA' },
      geometry: { type: 'MultiPolygon', coordinates: [
        [[[-125, 25], [-65, 25], [-65, 50], [-125, 50], [-125, 25]]],
        [[[145, 13], [146, 13], [146, 14], [145, 13]]]
      ] }
    }] })
    expect(bounds.USA).toEqual([[-131, 52.5], [-59, 22.5]])
  })

  test('拒绝空边界、重复标识和缺失必要字段的数据', () => {
    expect(() => dashboardRegionNames({ features: [] })).toThrow('未找到')
    expect(() => dashboardRegionNames({ features: [region('same', 'A'), region('same', 'B')] })).toThrow('标识重复')
    expect(() => dashboardRegionNames({ features: [region('', 'A')] })).toThrow('不完整')
  })

  test('中国优先使用本地层级，台湾港澳不作为独立国家供海外目录覆盖', () => {
    const options = dashboardCountryOptions({ countries: ['TWN', 'HKG', 'MAC', 'CHN', 'USA'].map(code => ({
      code, name: code, layers: [{ level: 'ADM1', url: 'https://foreign.example/map' }]
    })) }, {})
    expect(options[0].code).toBe('CHN')
    expect(options.map(country => country.code)).toEqual(['CHN', 'USA'])
    expect(options[0].layers).toHaveLength(4)
    expect(options[0].layers.every(layer => layer.url.startsWith('/maps/dashboard-china-'))).toBe(true)
  })

  test('境外区域与中国边界重叠时扣除重叠部分，并保留原始数据', () => {
    const foreign = { features: [{ ...region('foreign', '邻区'), geometry: {
      type: 'Polygon', coordinates: [[[2, 0], [6, 0], [6, 4], [2, 4], [2, 0]]]
    } }] }
    const china = { type: 'MultiPolygon', coordinates: [[[[0, 0], [4, 0], [4, 4], [0, 4], [0, 0]]]] }
    const result = prioritizeChinaBoundary(foreign, china)
    expect(result.features[0].geometry.type).toBe('MultiPolygon')
    expect(result.features[0].geometry.coordinates.flat(2).every(point => point[0] >= 4)).toBe(true)
    expect(foreign.features[0].geometry.coordinates[0][0]).toEqual([2, 0])
    expect(result.features[0].properties.shapeID).toBe('foreign')
  })

  test('下钻时只保留父级边界内的子级区域，去掉装饰边界和同级区域', () => {
    const parent = region('province-1', '省一')
    const children = {
      type: 'FeatureCollection',
      features: [
        { ...region('city-1', '市一'), geometry: { type: 'Polygon', coordinates: [[[0.1, 0.1], [0.4, 0.1], [0.1, 0.4], [0.1, 0.1]]] } },
        { ...region('city-2', '市二'), geometry: { type: 'Polygon', coordinates: [[[2, 2], [3, 2], [2, 3], [2, 2]]] } },
        { ...region('outline', '全国'), properties: { shapeID: 'outline', shapeName: '全国', mapRole: 'outline' } }
      ]
    }
    expect(filterDashboardRegionFeatures(children, parent).features.map(feature => feature.properties.shapeID))
      .toEqual(['city-1'])
  })

  test('行政区属性可优先排除相邻边界的几何误差', () => {
    const parent = {
      ...region('province-1', '省一'),
      properties: { shapeID: 'province-1', shapeName: '省一' }
    }
    const children = {
      type: 'FeatureCollection',
      features: [
        { ...region('city-1', '市一'), properties: { shapeID: 'city-1', shapeName: '市一', province: '省一' } },
        { ...region('city-2', '市二'), properties: { shapeID: 'city-2', shapeName: '市二', province: '省二' } }
      ]
    }
    expect(filterDashboardRegionFeatures(children, parent).features.map(feature => feature.properties.shapeID))
      .toEqual(['city-1'])
  })

  test('热力点只保留当前地图边界内的坐标', () => {
    const geoJson = { type: 'FeatureCollection', features: [region('region-1', '区域一')] }
    expect(dashboardPointsInGeoJson([
      { longitude: 0.2, latitude: 0.2, count: 2 },
      { longitude: 2, latitude: 2, count: 3 },
      { longitude: 'bad', latitude: 0, count: 4 }
    ], geoJson)).toEqual([{ longitude: 0.2, latitude: 0.2, count: 2 }])
  })
})
