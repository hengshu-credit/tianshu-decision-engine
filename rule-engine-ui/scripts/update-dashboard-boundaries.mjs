import { readFile, writeFile } from 'node:fs/promises'

const source = 'https://www.geoboundaries.org/api/current/gbOpen/ALL/ALL/'
let records
if (process.argv[2]) {
  records = JSON.parse((await readFile(process.argv[2], 'utf8')).replace(/^\uFEFF/, ''))
} else {
  const response = await fetch(source, { signal: AbortSignal.timeout(60000) })
  if (!response.ok) throw new Error(`geoBoundaries catalog: HTTP ${response.status}`)
  records = await response.json()
}
const countries = new Map()
for (const record of records) {
  if (!/^[A-Z]{3}$/.test(record.boundaryISO) || !/^ADM[0-5]$/.test(record.boundaryType)) continue
  if (['CHN', 'TWN', 'HKG', 'MAC'].includes(record.boundaryISO)) continue
  const url = new URL(record.simplifiedGeometryGeoJSON)
  if (url.origin !== 'https://github.com' || !url.pathname.startsWith('/wmgeolab/geoBoundaries/raw/')) {
    throw new Error(`Unexpected boundary source: ${url}`)
  }
  const country = countries.get(record.boundaryISO) || {
    code: record.boundaryISO,
    name: record.boundaryName,
    layers: []
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
