const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDocsApiData } = require('./support/docsFixtures.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')
const { SIDEBAR_MENUS } = require('../../src/layout/layoutState.js')
const catalog = require('../../src/constants/platformResponseCodes.json')

const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
test.use({ actionTimeout: 5000 })

test('规则详情只在打开对应页签时初始化编辑器和请求 API 场景', async ({ page }) => {
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: createDetailApiData() })
  await page.goto(`${origin}/#/rule`)
  await page.getByRole('button', { name: '详情', exact: true }).first().click()
  await expect(page.getByRole('tab', { name: '生命周期', exact: true })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByTestId('rule-detail-content')).toHaveAttribute('aria-busy', 'false')
  await expect(page.locator('.monaco-editor-container')).toHaveCount(0)
  expect(harness.requests.filter(request => new URL(request.url).pathname.endsWith('/api-scenarios'))).toHaveLength(0)
  await page.getByRole('tab', { name: '开放接口', exact: true }).click()
  const contract = page.locator('#pane-open-api')
  await expect(contract).toBeVisible()
  await expect(contract.locator('.monaco-editor').first()).toBeAttached()
  await page.getByRole('tab', { name: 'API 测试用例', exact: true }).click()
  await expect(page.locator('.api-scenario-panel')).toBeVisible()
  await expect.poll(() => harness.requests.filter(request => new URL(request.url).pathname.endsWith('/api-scenarios')).length).toBe(1)
  await page.getByRole('tab', { name: '生命周期', exact: true }).click()
  await page.getByRole('tab', { name: '开放接口', exact: true }).click()
  await expect(contract).toBeVisible()
  harness.assertClean()
})

test('外数接口详情的测试编辑器按页签加载，切换后保留测试输入', async ({ page }) => {
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: createDetailApiData() })
  await page.goto(`${origin}/#/datasource`)
  await page.getByRole('tab', { name: 'API 接口', exact: true }).click()
  await page.getByRole('button', { name: '编辑', exact: true }).first().click()
  await expect(page).toHaveURL(/#\/datasource\/api\/22/)
  await expect(page.locator('.detail-form')).toHaveAttribute('aria-busy', 'false')
  await expect(page.locator('.monaco-editor-container')).toHaveCount(0)
  await page.getByRole('tab', { name: '接口测试', exact: true }).click()
  await expect(page.locator('.monaco-editor:visible').first()).toBeVisible()
  const input = page.locator('.monaco-editor:visible').first().locator('textarea')
  await input.fill('{"audit":"retained"}')
  await page.getByRole('tab', { name: '接口鉴权', exact: true }).click()
  await page.getByRole('tab', { name: '接口测试', exact: true }).click()
  await expect(page.locator('.monaco-editor:visible').first()).toContainText('retained')
  harness.assertClean()
})

test('逐一点击全部菜单与子页签，往返切换后仍可交互并记录响应耗时', async ({ page }, testInfo) => {
  test.setTimeout(180000)
  const apiData = createDocsApiData()
  const projectCode = apiData.get('/api/rule/project/list').records[0].projectCode
  const permissions = [...new Set(SIDEBAR_MENUS.flatMap(menu => menu.permission).filter(Boolean)), 'system:audit', 'account:audit']
  apiData.set('/api/auth/console/config', { loginEnabled: true })
  apiData.set('/api/auth/console/me', { userId: 1, username: 'navigation-audit', permissions, defaultPermissions: permissions, permissionVersion: 1 })
  apiData.set('/api/auth/console/preferences/theme', null)
  apiData.set('POST /api/rule/console/activity', null)
  apiData.set('/api/rule/sftp/list', { records: [], total: 0 })
  apiData.set('/api/rule/console/operation-logs', { records: [], total: 0 })
  apiData.set('/api/rule/system/settings', {
    brand: { systemName: '天枢决策引擎' }, mail: {}, access: {}, codes: { mappings: {} },
    codeCatalog: catalog, states: [], deploymentManaged: {},
  })
  apiData.set('/api/rule/system/settings/history', { records: [], total: 0 })
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData })
  await page.addInitScript(() => {
    window.switchAudit = { clicks: [], tasks: [] }
    document.addEventListener('click', event => {
      const target = event.target.closest('[data-menu-path], .workspace-tab__main, [role="tab"]')
      if (target) window.switchAudit.clicks.push({ name: target.textContent.trim(), start: performance.now() })
    }, true)
    new PerformanceObserver(list => {
      window.switchAudit.tasks.push(...list.getEntries().map(entry => ({ start: entry.startTime, duration: entry.duration })))
    }).observe({ type: 'longtask', buffered: true })
  })
  const measurements = []
  async function record(kind, name) {
    const metric = await page.evaluate(async () => {
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
      const click = window.switchAudit.clicks.at(-1)
      return { duration: Math.round(performance.now() - click.start), longTasks: window.switchAudit.tasks.filter(task => task.start >= click.start) }
    })
    measurements.push({ kind, name, ...metric })
  }
  await page.goto(`${origin}/#/project`)
  await expect(page.getByRole('cell', { name: projectCode, exact: true })).toBeVisible()
  for (const menu of SIDEBAR_MENUS) {
    // 避免把重复点击当前页记成一次页面切换。
    if (menu.index === '/project') {
      await page.locator('[data-menu-path="/rule"]').click()
      await expect(page).toHaveURL(`${origin}/#/rule`)
    }
    await page.locator(`[data-menu-path="${menu.index}"]`).click()
    await expect(page).toHaveURL(`${origin}/#${menu.index}`)
    await expect(page.locator('.layout-main > div').first()).toBeVisible()
    await record('menu', menu.label)
    // 从真实可见标签取得目标，不直接调用组件方法或绕过点击。
    const tabs = await page.locator('.layout-main [role="tab"]:visible:not([aria-disabled="true"])').allTextContents()
    // 默认页签最后再点击，测量真实切换，并验证返回默认页签的交互。
    for (const name of [...tabs.slice(1), ...tabs.slice(0, 1)]) {
      const tab = page.locator('.layout-main').getByRole('tab', { name: name.trim(), exact: true })
      await tab.click()
      await expect(tab).toHaveAttribute('aria-selected', 'true')
      const paneId = await tab.getAttribute('aria-controls')
      const pane = page.locator(`[id="${paneId}"]`)
      // SFTP / 执行日志使用空 pane 作为导航，正文位于 tabs 外。
      await expect(pane).toHaveAttribute('aria-hidden', 'false')
      await expect(pane).not.toHaveAttribute('inert', '')
      await record('tab', `${menu.label}/${name.trim()}`)
    }
    if (menu.index === '/project') continue
    await page.locator('.workspace-tab__main[data-path="/project"]').click()
    await expect(page).toHaveURL(`${origin}/#/project`)
    await expect(page.getByRole('cell', { name: projectCode, exact: true })).toBeVisible()
    await record('return', menu.label)
  }
  await testInfo.attach('navigation-timings', { body: JSON.stringify(measurements, null, 2), contentType: 'application/json' })
  console.log('SWITCH_AUDIT', JSON.stringify(measurements))
  harness.assertClean()
})
