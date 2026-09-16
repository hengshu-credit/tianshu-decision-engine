const { chromium, expect } = require('../rule-engine-builder-ui/node_modules/@playwright/test')
const { createDetailApiData } = require('../rule-engine-builder-ui/tests/e2e/support/detailFixtures.cjs')
const path = require('node:path')

;(async () => {
  const browser = await chromium.launch({ headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 800 } })
    const errors = []
    const unmatched = []
    page.on('pageerror', error => { errors.push(error.message); console.error('Page error:', error.message) })
    page.on('console', message => { if (message.type() === 'error') { errors.push(message.text()); console.error('Console error:', message.text()) } })
    const fixtures = createDetailApiData()
    const projectName = '企业授信审批与风险准入综合决策项目（覆盖全国各分支机构）'
    const project = { ...fixtures.get('/api/rule/project/1'), projectName }
    fixtures.set('/api/auth/console/config', { loginEnabled: false })
    fixtures.set('/api/auth/console/me', { username: 'e2e' })
    fixtures.set('/api/rule/project/1', project)
    fixtures.set('/api/rule/project/list', { records: [project], total: 1 })
    fixtures.set('/api/rule/project/1/workbench', {
      project, metrics: {}, checks: [], warnings: [], recentExecution: null,
    })
    await page.route('http://127.0.0.1:19091/api/**', async route => {
      const url = new URL(route.request().url())
      const key = route.request().method() === 'GET' ? url.pathname : `${route.request().method()} ${url.pathname}`
      if (!fixtures.has(key)) unmatched.push(key)
      const configured = fixtures.get(key)
      const data = typeof configured === 'function' ? await configured({ url, request: route.request() }) : configured
      await route.fulfill({ json: { code: 200, message: 'success', data: data ?? { records: [], total: 0 } } })
    })
    await page.goto('http://127.0.0.1:19091/#/project')
    await page.screenshot({ path: path.join(__dirname, 'tab-title-dev-initial.png') })
    await page.getByRole('button', { name: '进入', exact: true }).click()
    const title = `项目 · ${projectName}`
    const tab = page.locator('.workspace-tab__main').filter({ hasText: title })
    await expect(tab).toBeVisible()
    expect(await page.locator('.workspace-tab').evaluateAll(elements => elements.map(el => el.getBoundingClientRect().width))).toEqual([160, 160])
    const alignment = await page.locator('.workspace-tab').evaluateAll(elements => elements.map(element => {
      const tabBox = element.getBoundingClientRect()
      const label = element.querySelector('.workspace-tab__title')
      const labelBox = label.getBoundingClientRect()
      const closeBox = element.querySelector('.workspace-tab__close').getBoundingClientRect()
      const range = document.createRange()
      range.selectNodeContents(label)
      const textBox = range.getBoundingClientRect()
      return {
        centered: Math.abs(labelBox.x + labelBox.width / 2 - tabBox.x - tabBox.width / 2) < 1,
        shortTextCentered: label.scrollWidth > label.clientWidth || Math.abs(textBox.x + textBox.width / 2 - tabBox.x - tabBox.width / 2) < 1,
        noCloseOverlap: labelBox.right <= closeBox.left,
      }
    }))
    expect(alignment).toEqual([
      { centered: true, shortTextCentered: true, noCloseOverlap: true },
      { centered: true, shortTextCentered: true, noCloseOverlap: true },
    ])
    await expect.poll(() => tab.locator('.workspace-tab__title').evaluate(el => el.scrollWidth > el.clientWidth)).toBe(true)
    await tab.hover()
    await expect(page.getByRole('tooltip', { name: title, exact: true })).toBeVisible()
    await expect(page.getByRole('tooltip', { name: title, exact: true })).toHaveCSS('opacity', '1')
    await page.screenshot({ path: path.join(__dirname, 'tab-title-hover.png') })
    await page.getByRole('tab', { name: '项目规则', exact: true }).click()
    await page.getByTestId('view-rule').click()
    await expect(page.locator('.workspace-tab__main').filter({ hasText: 'QL脚本 · 年龄判断规则' })).toBeVisible()
    await tab.click()
    await page.reload()
    await expect(tab).toBeVisible()
    await expect(page.locator('.workspace-tab__main').filter({ hasText: 'QL脚本 · 年龄判断规则' })).toBeVisible()
    await tab.click({ button: 'right' })
    await page.locator('[data-operation="refresh"]').click()
    await expect(page.getByRole('heading', { name: projectName, exact: true })).toBeVisible()
    await page.getByRole('button', { name: `关闭${title}`, exact: true }).click()
    await expect(tab).toHaveCount(0)
    expect(errors).toEqual([])
    expect(unmatched).toEqual([])
    console.log('PASS: Vite dev UI navigation, project and designer titles, truncation, tooltip, switch, reload, refresh, close; no browser errors')
  } finally {
    await browser.close()
  }
})().catch(error => { console.error(error); process.exitCode = 1 })
