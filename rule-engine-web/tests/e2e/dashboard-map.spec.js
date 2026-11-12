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
  await map.scrollIntoViewIfNeeded()
  const reset = toolbar.getByRole('button', { name: '恢复视角' })
  await reset.hover()
  const worldBox = await map.boundingBox()
  const clip = { x: Math.floor(worldBox.x + worldBox.width / 2 + 2), y: Math.floor(worldBox.y + worldBox.height / 2), width: 2, height: 2 }
  const sample = async () => (await page.screenshot({ clip })).toString('base64')
  const normal = await sample()
  await hoverCenter('测试国')
  await expect.poll(sample).not.toBe(normal)
  await reset.hover()
  await expect(tooltip).toHaveCount(0)
  await expect.poll(sample).toBe(normal)
  await hoverCenter('测试国')
  await map.click()
  await hoverCenter('东省')
  await expect(tooltip.locator('.hover-location')).toHaveText('测试国 / 东省')
  await expect(tooltip.locator('.hover-count')).toContainText('8 件')
  await expect(tooltip.locator('.hover-decisions')).toContainText('5 件 · 62.50%')
  await expect(tooltip.locator('.hover-decisions')).toContainText('1 件 · 12.50%')
  await map.click()
  await expect(toolbar.getByRole('button', { name: '返回上一级' })).toBeVisible()
  await hoverCenter('东城')
  // 下钻后的左侧边缘仅有当前国家的背景轮廓，不应整国着色或冒出国家面板。
  const cityBox = await map.boundingBox()
  await map.hover({ position: { x: cityBox.width * 0.02, y: cityBox.height / 2 } })
  await expect(tooltip).toHaveCount(0)
  const selectorBox = await toolbar.getByRole('combobox').first().boundingBox()
  const resetBox = await reset.boundingBox()
  const toolbarBox = await toolbar.boundingBox()
  expect(Math.abs((selectorBox.y + selectorBox.height / 2) - (resetBox.y + resetBox.height / 2))).toBeLessThan(3)
  expect(Math.abs(toolbarBox.x + toolbarBox.width - resetBox.x - resetBox.width)).toBeLessThan(2)
  await hoverCenter('东城')
  await map.click()
  await expect(toolbar.getByRole('combobox', { name: '选择行政区' })).toHaveCount(0)
  await hoverCenter('东城')
  await page.getByRole('button', { name: '返回上一级' }).click()
  await expect(toolbar.getByRole('combobox', { name: '选择行政区' })).toBeEnabled()
  // 周边国家没有行政区目录，仍能从当前画布直接点击、居中，并更新侧栏范围。
  const box = await map.boundingBox()
  await map.click({ position: { x: box.width * 0.97, y: box.height / 2 } })
  await expect(toolbar.locator('.el-select').first()).toContainText('邻国')
  await hoverCenter('邻国')
  await expect(tooltip.locator('.hover-count')).toContainText('0 件')
  await expect(page.locator('.dashboard-map-side--left')).toContainText('暂无有效行政区进件坐标')
  await reset.click()
  await expect(toolbar.getByRole('combobox')).toHaveCount(1)
  await hoverCenter('测试国')
  await page.setViewportSize({ width: 700, height: 900 })
  const left = page.locator('.dashboard-map-side--left')
  const right = page.locator('.dashboard-map-side--right')
  await expect(left).toBeVisible()
  expect((await map.boundingBox()).y).toBeLessThan((await left.boundingBox()).y)
  expect((await left.boundingBox()).y).toBeLessThan((await right.boundingBox()).y)
  assertClean()
})

test('真实国内边界可从浙江省下钻至杭州市和西湖区，按上级下载且能逐级返回', async ({ page }) => {
  const { assertClean } = await installDistRoutes(page)
  const boundaries = []
  page.on('request', request => {
    const file = new URL(request.url()).pathname.split('/').at(-1)
    if (file.startsWith('dashboard-china-ADM')) boundaries.push(file)
  })
  await page.goto('http://tianshu.local/index.html#/dashboard')
  const toolbar = page.locator('.dashboard-map-toolbar')
  await toolbar.locator('.el-select').first().click()
  await page.getByRole('option', { name: '中国', exact: true }).click()

  async function choose(name) {
    const picker = toolbar.getByRole('combobox', { name: '选择行政区' })
    await picker.fill(name)
    await page.getByRole('option', { name, exact: true }).click()
  }
  await choose('浙江省')
  await expect(toolbar).toContainText('地市级行政区')
  await choose('杭州市')
  await expect(toolbar).toContainText('区县级行政区')
  await choose('西湖区')
  const map = page.getByRole('img', { name: '进件地图热力图' })
  await map.hover()
  await expect(page.locator('.hover-location')).toHaveText('中国 / 浙江省 / 杭州市 / 西湖区')
  expect(boundaries.some(file => file.startsWith('dashboard-china-ADM2-156330000-'))).toBe(true)
  expect(boundaries.some(file => file.startsWith('dashboard-china-ADM3-156330100-'))).toBe(true)
  expect(boundaries.filter(file => /^dashboard-china-ADM[23]-[a-f0-9]{16}\.json$/.test(file))).toEqual([])
  await toolbar.getByRole('button', { name: '返回上一级' }).click()
  await expect(page.locator('.heatmap-tooltip')).toHaveCount(0)
  await expect(toolbar.getByRole('combobox', { name: '选择行政区' })).toBeEnabled()
  await expect(async () => {
    await map.hover()
    await expect(page.locator('.heatmap-tooltip')).toContainText(/区|县|市/, { timeout: 800 })
  }).toPass({ timeout: 8000 })
  const countyPath = await page.locator('.hover-location').innerText()
  await map.click()
  await expect(toolbar.getByRole('combobox', { name: '选择行政区' })).toHaveCount(0)
  expect(countyPath).toContain('中国 / 浙江省 / 杭州市 / ')
  await toolbar.getByRole('button', { name: '恢复视角' }).click()
  await expect(page.locator('.heatmap-tooltip')).toHaveCount(0)
  await expect(toolbar.locator('.el-select')).toHaveCount(1)
  assertClean()
})

for (const destination of ['/dashboard', '/project']) {
  test(`登录目标 ${destination} 时，看板内容懒加载且工作台可操作`, async ({ page }) => {
    let loggedIn = false
    const account = { username: 'dashboard-qa', permissions: ['project:view'] }
    const apiData = new Map([
      ['/api/auth/console/config', { loginEnabled: true }],
      ['/api/auth/console/preferences/theme', null],
      ['/api/auth/console/me', () => loggedIn ? account : null],
      ['POST /api/auth/console/login', () => { loggedIn = true; return account }],
      ['POST /api/rule/console/activity', null]
    ])
    const { assertClean } = await installDistRoutes(page, { apiData })
    let resume
    const hold = new Promise(resolve => { resume = resolve })
    let downloading = false
    const dataRequests = []
    page.on('request', request => {
      if (request.url().includes('/api/rule/dashboard/')) dataRequests.push(request.url())
    })
    await page.route(/\/assets\/DashboardHome-[^/]+\.js$/, async route => {
      downloading = true
      await hold
      await route.fallback()
    })
    try {
      await page.goto('http://tianshu.local/index.html#' + destination)
      await page.getByPlaceholder('请输入用户名').fill('dashboard-qa')
      await page.getByPlaceholder('请输入密码').fill('test-login')
      expect(downloading).toBe(false)
      await page.getByRole('button', { name: '登录', exact: true }).click()
      await expect(page).toHaveURL('http://tianshu.local/index.html#' + destination)
      await expect(page.locator('.layout-account-name')).toHaveText('dashboard-qa')
      if (destination === '/project') {
        await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeVisible()
        expect(downloading).toBe(false)
        expect(dataRequests).toEqual([])
        await page.locator('[data-menu-path="/dashboard"]').click()
      }
      await expect.poll(() => downloading).toBe(true)
      await expect(page.locator('.dashboard-entry')).toBeVisible()
      expect(dataRequests).toEqual([])
      await page.locator('[data-menu-path="/project"]').click()
      await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeVisible()
    } finally { resume() }
    await page.locator('[data-menu-path="/dashboard"]').click()
    await expect(page.locator('.dashboard-metrics')).toBeVisible()
    await expect.poll(() => dataRequests.length).toBeGreaterThan(0)
    assertClean()
  })
}
