const { test, expect } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')

test('L7 热力图、地区决策占比和行政区下钻使用相同范围', async ({ page }) => {
  const geo = {
    points: [{ longitude: 112, latitude: 31, count: 8, passCount: 5, reviewCount: 2, rejectCount: 1 }],
    validCount: 8, excludedCount: 0
  }
  const { assertClean } = await installDistRoutes(page, {
    apiData: new Map([['/api/rule/dashboard/applications', { visible: true, summary: { applicationCount: 8 }, geo }]])
  })
  // 单一矩形让画布中心始终是同一地区，不依赖浏览器像素比或底图更新。
  await page.route(/dashboard-china-ADM[12].*\.(?:geojson|json)$/, route => {
    const province = route.request().url().includes('ADM1')
    return route.fulfill({ json: {
      type: 'FeatureCollection', features: [{
        type: 'Feature',
        properties: { shapeID: province ? 'p1' : 'c1', shapeName: province ? '测试省' : '测试市', province: '测试省' },
        geometry: { type: 'Polygon', coordinates: [[[110, 29], [114, 29], [114, 33], [110, 33], [110, 29]]] }
      }]
    } })
  })
  await page.setViewportSize({ width: 1600, height: 980 })
  await page.goto('http://tianshu.local/index.html#/dashboard')
  const map = page.getByRole('img', { name: '进件地图热力图' })
  await expect(map.locator('.l7-scene canvas')).toBeVisible()
  await expect(page.locator('.heatmap-legend')).toContainText('进件密度')
  await expect(async () => {
    await map.hover()
    await expect(page.locator('.heatmap-tooltip')).toHaveText(/测试省 · 8 件\s*通过率 62.50% · 拒绝率 12.50%/, { timeout: 800 })
  }).toPass({ timeout: 8000 })
  await map.click()
  await expect(page.locator('.dashboard-map-toolbar')).toContainText('当前：测试省')
  await expect(map.locator('.l7-scene canvas')).toBeVisible()
  await page.getByRole('button', { name: '返回上一级' }).click()
  await expect(page.locator('.dashboard-map-toolbar')).toContainText('省级行政区')
  await page.setViewportSize({ width: 700, height: 900 })
  const left = page.locator('.dashboard-map-side--left')
  const right = page.locator('.dashboard-map-side--right')
  await expect(left).toBeVisible()
  expect((await map.boundingBox()).y).toBeLessThan((await left.boundingBox()).y)
  expect((await left.boundingBox()).y).toBeLessThan((await right.boundingBox()).y)
  assertClean()
})
