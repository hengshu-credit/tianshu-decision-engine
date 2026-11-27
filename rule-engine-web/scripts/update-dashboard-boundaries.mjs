import { readFile, writeFile } from 'node:fs/promises'

const api = 'https://www.geoboundaries.org/api/current/gbOpen/'
const source = api + 'ALL/ALL/'
let records
if (process.argv[2]) {
  records = JSON.parse((await readFile(process.argv[2], 'utf8')).replace(/^\uFEFF/, ''))
} else {
  const response = await fetch(source, { signal: AbortSignal.timeout(60000) })
  if (!response.ok) throw new Error(`geoBoundaries catalog: HTTP ${response.status}`)
  records = await response.json()
}
const countries = new Map()
for (let record of records) {
  if (!/^[A-Z]{3}$/.test(record.boundaryISO) || !/^ADM[0-5]$/.test(record.boundaryType)) continue
  if (['CHN', 'TWN', 'HKG', 'MAC'].includes(record.boundaryISO)) continue
  // ALL/ALL 中印度 ADM0 的 boundaryType 和下载地址曾误指向 ADM1。
  const canonicalLevel = /^([A-Z]{3})-(ADM[0-5])-\d+$/.exec(record.boundaryID)
  if (canonicalLevel && canonicalLevel[1] === record.boundaryISO && canonicalLevel[2] !== record.boundaryType) {
    const [, code, level] = canonicalLevel
    const response = await fetch(`${api}${code}/${level}/`, { signal: AbortSignal.timeout(60000) })
    if (!response.ok) throw new Error(`Cannot verify ${code} ${level}: HTTP ${response.status}`)
    record = await response.json()
    if (record.boundaryISO !== code || record.boundaryType !== level) throw new Error(`Invalid boundary metadata for ${code} ${level}`)
  }
  const url = new URL(record.simplifiedGeometryGeoJSON)
  if (url.origin !== 'https://github.com' || !url.pathname.startsWith('/wmgeolab/geoBoundaries/raw/')) {
    throw new Error(`Unexpected boundary source: ${url}`)
  }
  const country = countries.get(record.boundaryISO) || {
    code: record.boundaryISO,
    name: record.boundaryName,
    layers: []
  }
  if (country.layers.some(layer => layer.level === record.boundaryType)) {
    throw new Error(`Duplicate boundary level: ${country.code} ${record.boundaryType}`)
  }
  country.layers.push({
    level: record.boundaryType,
    year: record.boundaryYearRepresented,
    count: Number(record.admUnitCount),
    url: `https://media.githubusercontent.com/media/wmgeolab/geoBoundaries/${url.pathname.split('/raw/')[1]}`
  })
  countries.set(country.code, country)
}

const catalog = {
  source,
  updatedAt: new Date().toISOString().slice(0, 10),
  license: 'CC BY 4.0',
  countries: [...countries.values()].sort((a, b) => a.code.localeCompare(b.code))
}
catalog.countries.forEach(country => country.layers.sort((a, b) => a.level.localeCompare(b.level)))
await writeFile(new URL('../public/maps/dashboard-boundaries.json', import.meta.url), `${JSON.stringify(catalog, null, 2)}\n`)
console.log(`Updated ${catalog.countries.length} countries and ${catalog.countries.reduce((count, country) => count + country.layers.length, 0)} boundary layers`)
