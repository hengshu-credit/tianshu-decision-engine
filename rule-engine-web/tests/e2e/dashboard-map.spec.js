const { test, expect } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')

test('L7 热力图、地区决策占比和行政区下钻使用相同范围', async ({ page }) => {
  const geo = {
    points: [{ longitude: 34, latitude: 0, count: 8, passCount: 5, reviewCount: 2, rejectCount: 1 }],
    validCount: 8, excludedCount: 0
  }
  const { assertClean } = await installDistRoutes(page, {
    apiData: new Map([['/api/rule/dashboard/applications', { visible: true, summary: { applicationCount: 8 }, geo }]])
  })
  const rectangle = (properties, west, east) => ({
    type: 'Feature', properties,
    geometry: { type: 'Polygon', coordinates: [[[west, -8], [east, -8], [east, 8], [west, 8], [west, -8]]] }
  })
  const collection = features => ({ type: 'FeatureCollection', features })
  await page.route(/dashboard-world.*\.(?:geojson|json)$/, route => route.fulfill({ json: collection([
    rectangle({ NAME_ZH: '测试国', ADM0_A3: 'AAA' }, -5, 45),
    rectangle({ NAME_ZH: '邻国', ADM0_A3: 'BBB' }, 45, 51)
  ]) }))
  await page.route(/dashboard-boundaries.*\.json$/, route => route.fulfill({ json: {
    countries: [{ code: 'AAA', name: '测试国', layers: [
      { level: 'ADM1', url: '/province.json' }, { level: 'ADM2', url: '/city.json' }
    ] }]
  } }))
  await page.route('**/province.json', route => route.fulfill({ json: collection([
    rectangle({ shapeID: 'west', shapeName: '西省' }, -5, 15),
    rectangle({ shapeID: 'east', shapeName: '东省' }, 15, 45)
  ]) }))
  await page.route('**/city.json', route => route.fulfill({ json: collection([
    rectangle({ shapeID: 'city1', shapeName: '西城', province: '东省' }, 15, 29),
    rectangle({ shapeID: 'city2', shapeName: '东城', province: '东省' }, 29, 45)
  ]) }))
  await page.setViewportSize({ width: 1600, height: 980 })
  await page.goto('http://tianshu.local/index.html#/dashboard')
  const map = page.getByRole('img', { name: '进件地图热力图' })
  await expect(map.locator('.l7-scene canvas')).toBeVisible()
  await expect(page.locator('.heatmap-legend')).toContainText('进件密度')
  const toolbar = page.locator('.dashboard-map-toolbar')
  const tooltip = page.locator('.heatmap-tooltip')
  const hoverCenter = async name => {
    await expect(async () => {
      await map.hover()
      await expect(tooltip).toContainText(name, { timeout: 800 })
    }).toPass({ timeout: 8000 })
  }
  await hoverCenter('测试国')
  await map.click()
  await hoverCenter('东省')
  await expect(tooltip).toHaveText(/东省 · 8 件\s*通过率 62.50% · 拒绝率 12.50%/)
  await map.click()
  await expect(toolbar).toContainText('当前：东省')
  await hoverCenter('东城')
  await map.click()
  await expect(toolbar).toContainText('东省 / 东城')
  await hoverCenter('东城')
  await page.getByRole('button', { name: '返回上一级' }).click()
  await expect(toolbar).not.toContainText('东省 / 东城')
  // 周边国家没有行政区目录，仍能从当前画布直接点击、居中，并更新侧栏范围。
  const box = await map.boundingBox()
  await map.click({ position: { x: box.width * 0.97, y: box.height / 2 } })
  await expect(toolbar.locator('.el-select').first()).toContainText('邻国')
  await hoverCenter('邻国')
  await expect(page.locator('.dashboard-map-side--left')).toContainText('暂无有效行政区进件坐标')
  await page.getByRole('button', { name: '返回全球' }).click()
  await hoverCenter('测试国')
  await page.setViewportSize({ width: 700, height: 900 })
  const left = page.locator('.dashboard-map-side--left')
  const right = page.locator('.dashboard-map-side--right')
  await expect(left).toBeVisible()
  expect((await map.boundingBox()).y).toBeLessThan((await left.boundingBox()).y)
  expect((await left.boundingBox()).y).toBeLessThan((await right.boundingBox()).y)
  assertClean()
})
