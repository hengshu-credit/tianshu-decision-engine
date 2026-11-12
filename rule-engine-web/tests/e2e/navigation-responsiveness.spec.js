const { expect, test: base } = require('@playwright/test')
const { observeNavigationErrors } = require('./support/navigationErrorLog.cjs')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDocsApiData } = require('./support/docsFixtures.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')
const { SIDEBAR_MENUS } = require('../../src/layout/layoutState.js')
const catalog = require('../../src/constants/platformResponseCodes.json')

const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
const test = base.extend({
  navigationAudit: async ({ page }, use, testInfo) => {
    const audit = await observeNavigationErrors(page, origin)
    try {
      await use(audit)
    } finally {
      const report = await audit.finish(testInfo)
      if (testInfo.status === 'passed') {
        const failures = report.issues.filter(issue => !['cancelled', 'console.warning', 'ui-warning', 'validation'].includes(issue.kind))
        expect(failures, '逐页报错见 navigation-audit.json').toEqual([])
      }
    }
  },
})
test.use({ actionTimeout: 5000 })

test('变量列表不预取各项目引用，打开对象字段仍显示其引用变量', async ({ page }) => {
  const apiData = createDocsApiData()
  const variable = apiData.get('/api/rule/variable/project/1')[0]
  const object = apiData.get('/api/rule/dataobject/tree').tree[0]
  object.variables[0].refVariableId = variable.id
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData })
  const referenceCalls = () => harness.requests.filter(request => new URL(request.url).pathname === '/api/rule/variable/project/1')
  await page.goto(origin + '/#/variable')
  await expect(page.getByRole('cell', { name: variable.varCode, exact: true }).first()).toBeVisible()
  expect(referenceCalls()).toHaveLength(0)
  await page.getByRole('tab', { name: '数据对象', exact: true }).click()
  await page.getByTestId('data-object-table').getByText(object.object.objectCode, { exact: true }).first().click()
  const reference = page.locator('.reference-variable-cell').first()
  await expect(reference).toContainText(variable.varLabel)
  await expect(reference).toContainText(variable.scriptName)
  expect(referenceCalls()).toHaveLength(1)
  harness.assertClean()
})

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

test('逐一点击全部菜单与子页签，往返切换后仍可交互并记录响应耗时', async ({ page, navigationAudit }, testInfo) => {
  test.setTimeout(180000)
  const apiData = createDocsApiData()
  const projectCode = apiData.get('/api/rule/project/list').records[0].projectCode
  const permissions = [...new Set(SIDEBAR_MENUS.flatMap(menu => menu.permission).filter(Boolean)), 'system:audit', 'account:audit']
  apiData.set('/api/auth/console/config', { loginEnabled: true })
  apiData.set('/api/auth/console/me', { userId: 1, username: 'navigation-audit', permissions, defaultPermissions: permissions, permissionVersion: 1 })
  apiData.set('/api/auth/console/preferences/theme', null)
  apiData.set('POST /api/rule/console/activity', null)
  apiData.set('/api/rule/sftp/list', { records: [], total: 0 })
  const ledgerEvent = { id: 1, title: '策略调整验收', scope: 'GLOBAL', status: 'ACCEPTANCE', canEdit: false }
  const ledgerTitle = '策略调整已发布，等待验收'
  apiData.set('/api/rule/ledger/events', [ledgerEvent])
  apiData.set('/api/rule/ledger/events/manage', { records: [ledgerEvent], total: 1 })
  apiData.set('/api/rule/ledger/timeline', { records: [{
    id: 11, articleId: 1, versionNo: 1, title: ledgerTitle, summary: '本周完成策略调整，下周验收。',
    scope: 'GLOBAL', category: 'STRATEGY', activityTime: '2026-11-04T14:20:00', events: [ledgerEvent], actorName: '验收人员',
  }], total: 1 })
  apiData.set('/api/rule/ledger/articles', { records: [{
    id: 1, title: ledgerTitle, scope: 'GLOBAL', category: 'STRATEGY', publishedVersion: 1,
    publishedRevisionId: 11, hasDraft: false, canEdit: false, updateTime: '2026-11-04T14:20:00',
  }], total: 1 })
  apiData.set('/api/rule/ledger/assets', { records: [{
    id: 1, name: '验收截图.png', scope: 'PRIVATE', scanStatus: 'PENDING', scanMessage: '等待安全检测',
    sizeBytes: 1024, contentType: 'image/png', ownerName: '验收人员', canManage: false, contentUrl: null,
    updateTime: '2026-11-04T14:20:00',
  }], total: 1 })
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
  const measurements = navigationAudit.timings
  async function record(kind, name) {
    const metric = await page.evaluate(async () => {
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
      const click = window.switchAudit.clicks.at(-1)
      return { duration: Math.round(performance.now() - click.start), longTasks: window.switchAudit.tasks.filter(task => task.start >= click.start) }
    })
    measurements.push({ kind, name, ...metric })
  }
  navigationAudit.step('/project', '首次打开项目列表')
  await page.goto(`${origin}/#/project`)
  await expect(page.getByRole('cell', { name: projectCode, exact: true })).toBeVisible()
  for (const menu of SIDEBAR_MENUS) {
    // 避免把重复点击当前页记成一次页面切换。
    if (menu.index === '/project') {
      navigationAudit.step('/rule', '从当前项目页离开')
      await page.locator('[data-menu-path="/rule"]').click()
      await expect(page).toHaveURL(`${origin}/#/rule`)
    }
    navigationAudit.step(menu.index, `点击${menu.label}`)
    const requestStart = harness.requests.length
    await page.locator(`[data-menu-path="${menu.index}"]`).click()
    await expect(page).toHaveURL(`${origin}/#${menu.index}`)
    await expect(page.locator('.layout-main > div').first()).toBeVisible()
    await record('menu', menu.label)
    if (menu.index === '/test' || menu.index === '/lineage') {
      const label = menu.index === '/test' ? '筛选范围' : '方向'
      await expect(page.getByRole('radiogroup', { name: label, exact: true })).toBeVisible()
      expect(await page.locator('main label[for]').evaluateAll(labels => labels.filter(item => !item.control).map(item => item.textContent))).toEqual([])
    }
    if (menu.index === '/billing' || menu.index === '/system/settings') {
      await expect(page.locator('.layout-main [role="tabpanel"]')).toHaveCount(1)
    }
    if (menu.index === '/sftp') {
      const requests = harness.requests.slice(requestStart).map(request => new URL(request.url).pathname)
      expect(requests).not.toContain('/api/rule/project/list')
      expect(requests).not.toContain('/api/rule/project/options')
    }
    // 从真实可见标签取得目标，不直接调用组件方法或绕过点击。
    const tabs = await page.locator('.layout-main [role="tab"]:visible:not([aria-disabled="true"])').allTextContents()
    // 默认页签最后再点击，测量真实切换，并验证返回默认页签的交互。
    for (const name of [...tabs.slice(1), ...tabs.slice(0, 1)]) {
      const tab = page.locator('.layout-main').getByRole('tab', { name: name.trim(), exact: true })
      navigationAudit.step(menu.index, `切换${name.trim()}`)
      await tab.click()
      await expect(tab).toHaveAttribute('aria-selected', 'true')
      const paneId = await tab.getAttribute('aria-controls')
      const pane = page.locator(`[id="${paneId}"]`)
      // SFTP / 执行日志使用空 pane 作为导航，正文位于 tabs 外。
      await expect(pane).toHaveAttribute('aria-hidden', 'false')
      await expect(pane).not.toHaveAttribute('inert', '')
      if (menu.index === '/ledger') {
        await expect(pane).toContainText(name.trim() === '素材库' ? '验收截图.png' : name.trim() === '事件与类型' ? ledgerEvent.title : ledgerTitle)
      }
      await record('tab', `${menu.label}/${name.trim()}`)
      if (menu.index === '/billing' || menu.index === '/system/settings') {
        const hiddenPanes = page.locator('.layout-main [role="tabpanel"][aria-hidden="true"]')
        for (const hidden of await hiddenPanes.all()) await expect(hidden).toHaveAttribute('inert', '')
      }
    }
    if (menu.index === '/project') continue
    navigationAudit.step('/project', `从${menu.label}返回项目列表`)
    await page.locator('.workspace-tab__main[data-path="/project"]').click()
    await expect(page).toHaveURL(`${origin}/#/project`)
    await expect(page.getByRole('cell', { name: projectCode, exact: true })).toBeVisible()
    await record('return', menu.label)
  }
  await testInfo.attach('navigation-timings', { body: JSON.stringify(measurements, null, 2), contentType: 'application/json' })
  console.log('SWITCH_AUDIT', JSON.stringify(measurements))
  harness.assertClean()
})
