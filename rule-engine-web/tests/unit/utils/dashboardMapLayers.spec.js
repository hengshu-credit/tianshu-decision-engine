import {
  dashboardCountryBounds,
  dashboardCountryOptions,
  dashboardMapView,
  dashboardRegionActivity,
  dashboardRegionNames,
  dashboardWorldCountries,
  filterDashboardRegionFeatures,
  prepareDashboardWorld,
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
      { code: 'USA', name: '美国', layers: [{ level: 'ADM0' }, { level: 'ADM2' }] },
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

  test('缺少目录或共用 ISO 编码的地区独立定位和统计', () => {
    const area = (name, code, x, size) => ({
      properties: { NAME_ZH: name, ADM0_A3: code },
      geometry: { type: 'Polygon', coordinates: [[[x, 0], [x + size, 0], [x, size], [x, 0]]] }
    })
    const world = prepareDashboardWorld({ type: 'FeatureCollection', features: [
      area('离岛', 'AAA', 50, 1), area('国家主体', 'AAA', 10, 10),
      area('地区甲', '-99', 30, 1), area('地区乙', '-99', 45, 1)
    ] })
    const countries = dashboardWorldCountries(world)
    expect(new Set(Object.values(countries)).size).toBe(4)
    expect(countries['国家主体']).toBe('AAA')
    const options = dashboardCountryOptions(null, countries)
    expect(options.find(country => country.name === '地区甲').layers).toEqual([{ level: 'ADM0' }])
    const { regions } = dashboardRegionActivity(world, [
      { longitude: 30.2, latitude: 0.2, count: 3 }, { longitude: 45.2, latitude: 0.2, count: 1 }
    ])
    expect(regions.map(({ id, count }) => [id, count])).toEqual([[countries['地区甲'], 3], [countries['地区乙'], 1]])
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
    expect(dashboardRegionActivity(geoJson, [
      { longitude: 0.2, latitude: 0.2, count: 2 },
      { longitude: 2, latitude: 2, count: 3 },
      { longitude: 'bad', latitude: 0, count: 4 }
    ]).points).toEqual([{ longitude: 0.2, latitude: 0.2, count: 2 }])
  })

  test('按当前行政区边界聚合进件数和引擎决策结果', () => {
    const geoJson = { type: 'FeatureCollection', features: [
      { ...region('region-1', '区域一'), geometry: {
        type: 'Polygon', coordinates: [[[0, 0], [1, 0], [0, 1], [0, 0]]]
      } },
      { ...region('region-2', '区域二'), geometry: {
        type: 'Polygon', coordinates: [[[2, 0], [3, 0], [2, 1], [2, 0]]]
      } }
    ] }
    expect(dashboardRegionActivity(geoJson, [
      { longitude: 0.2, latitude: 0.2, count: 4, passCount: 2, reviewCount: 1, rejectCount: 1 },
      { longitude: 2.2, latitude: 0.2, count: 2, passCount: 0, reviewCount: 1, rejectCount: 1 },
      { longitude: 9, latitude: 9, count: 8, passCount: 8 }
    ]).regions).toEqual([
      {
        id: 'region-1', name: '区域一', count: 4, rate: 2 / 3,
        passCount: 2, reviewCount: 1, rejectCount: 1, passRate: 1 / 2, rejectRate: 1 / 4
      },
      {
        id: 'region-2', name: '区域二', count: 2, rate: 1 / 3,
        passCount: 0, reviewCount: 1, rejectCount: 1, passRate: 0, rejectRate: 1 / 2
      }
    ])
  })

  test('当前层级边界生成居中且带适量留白的地图视角', () => {
    const view = dashboardMapView({ features: [
      { ...region('region-1', '区域一'), geometry: {
        type: 'Polygon', coordinates: [[[10, 20], [20, 20], [20, 30], [10, 30], [10, 20]]]
      } },
      { ...region('outline', '全国'), properties: { shapeID: 'outline', shapeName: '全国', mapRole: 'outline' }, geometry: {
        type: 'Polygon', coordinates: [[[-180, -90], [180, -90], [180, 90], [-180, 90], [-180, -90]]]
      } }
    ] }, [[0, 10], [30, 0]])

    expect(view.bounds).toEqual([[9.2, 30.8], [20.8, 19.2]])
    expect(view.center).toEqual([15, 25])
    expect(view.zoom).toBe(1)
  })
})
