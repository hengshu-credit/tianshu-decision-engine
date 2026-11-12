import { readFileSync } from 'node:fs'
import path from 'node:path'

// 用源数据中的 9 位国标码关联。直管县可与市级记录同码，直辖市的区直接归属省级同码的市。
export function chinaChildAssets(publicDir) {
  const readRegions = level => JSON.parse(readFileSync(
    path.join(publicDir, 'maps', `dashboard-china-ADM${level}.geojson`), 'utf8'
  )).features.filter(feature => !feature.properties.mapRole)
  const assets = []
  let parents = readRegions(1)
  for (const level of [2, 3]) {
    const groups = new Map(parents.map(parent => [parent.properties.shapeID, []]))
    const children = readRegions(level)
    for (const child of children) {
      const id = child.properties.shapeID
      const province = id.slice(0, 5) + '0000'
      const candidates = level === 2 ? [province] : [id, id.slice(0, 7) + '00', province]
      const parent = candidates.find(candidate => groups.has(candidate))
      if (!parent) throw new Error(`中国 ADM${level} 边界 ${id} 找不到上级行政区`)
      groups.get(parent).push(child)
    }
    for (const [parent, features] of groups) {
      assets.push({
        url: `/maps/dashboard-china-ADM${level}-${parent}.json`,
        source: JSON.stringify({ type: 'FeatureCollection', features })
      })
    }
    parents = children
  }
  return assets
}
