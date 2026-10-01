import { readFile, writeFile, copyFile } from 'node:fs/promises'
import path from 'node:path'
import polygonClipping from 'polygon-clipping'

// 输入为已下载的原始 JSON；保留来源坐标，不手工绘制或简化中国界线。
const sourceDir = process.argv[2]
if (!sourceDir) throw new Error('Usage: node scripts/prepare-dashboard-china-maps.mjs <source-directory>')
const read = async name => JSON.parse((await readFile(path.join(sourceDir, name), 'utf8')).replace(/^\uFEFF/, ''))
const write = (name, data) => writeFile(new URL(`../public/maps/${name}`, import.meta.url), `${JSON.stringify(data)}\n`)
const outline = await read('tianshu-datav-china-outline.json')
const provinces = await read('tianshu-datav-china-provinces.json')
const sourceWorld = await read('tianshu-cn-world-source.json')
const maritime = provinces.features.find(feature => feature.properties.adcode === '100000_JD')
if (!maritime || maritime.geometry.coordinates.length !== 10) throw new Error('中国断续线源数据发生变化，须重新核对')
const linePolygons = new Set(maritime.geometry.coordinates.map(polygon => JSON.stringify(polygon)))
const china = outline.features[0]
const chinaLand = { ...china, geometry: {
  type: 'MultiPolygon',
  coordinates: china.geometry.coordinates.filter(polygon => !linePolygons.has(JSON.stringify(polygon)))
} }
const chinaProperties = { NAME_ZH: '中国', ADM0_A3: 'CHN', full_name: '中华人民共和国' }
const world = {
  ...sourceWorld,
  features: sourceWorld.features.filter(feature => !['CHN', 'TWN', 'HKG', 'MAC'].includes(feature.properties.iso_a3)).map(feature => {
    const coordinates = polygonClipping.difference(feature.geometry.coordinates, chinaLand.geometry.coordinates)
    return {
      ...feature,
      properties: { ...feature.properties, NAME_ZH: feature.properties.name, ADM0_A3: feature.properties.iso_a3 },
      geometry: { type: 'MultiPolygon', coordinates }
    }
  }).filter(feature => feature.geometry.coordinates.length)
}
world.features.push({ ...china, properties: chinaProperties })
await write('dashboard-world.geojson', world)
await write('dashboard-china-land.geojson', { type: 'FeatureCollection', features: [chinaLand] })
const context = { ...china, properties: { shapeID: 'CHN-outline', shapeName: '中国', mapRole: 'outline' } }
const boundary = { ...maritime, properties: { ...maritime.properties, shapeID: 'CHN-maritime', shapeName: '南海断续线及东海有关线段', mapRole: 'boundary' } }
for (const level of [0, 1, 2, 3]) {
  let regions
  if (level === 0) regions = [{ ...china, properties: { ...china.properties, shapeID: 'CHN', shapeName: '中国' } }]
  else if (level === 1) regions = provinces.features.filter(feature => Number.isInteger(feature.properties.adcode)).map(feature => ({
    ...feature, properties: { ...feature.properties, shapeID: `156${feature.properties.adcode}`, shapeName: feature.properties.name }
  }))
  else {
    const source = await read(`tianshu-chn-level-${level}.json`)
    regions = source.features.filter(feature => ['Polygon', 'MultiPolygon'].includes(feature.geometry.type)).map(feature => ({
      ...feature, properties: { ...feature.properties, shapeID: feature.properties.gb, shapeName: feature.properties.full_name || feature.properties.name }
    }))
  }
  await write(`dashboard-china-ADM${level}.geojson`, {
    type: 'FeatureCollection',
    features: level === 0 ? regions : [context, ...regions, boundary]
  })
  console.log(`ADM${level}: ${regions.length} regions`)
}
await copyFile(path.join(sourceDir, 'tianshu-chinese-geodata-LICENSE.txt'), new URL('../public/maps/chinese-geodata-LICENSE.txt', import.meta.url))
