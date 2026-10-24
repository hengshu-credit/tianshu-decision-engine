const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')
const { createDocsApiData } = require('./support/docsFixtures.cjs')

const routes = [
  '/dashboard', '/dashboard/settings', '/account', '/account/profile', '/approval', '/approval/1', '/transfer', '/system/settings',
  '/project', '/rule', '/variable', '/list', '/datasource', '/database', '/sftp', '/sftp/logs',
  '/model', '/function', '/test', '/lineage', '/experiment', '/log', '/billing',
  '/project/1', '/rule/101', '/list/9', '/datasource/source/21', '/datasource/api/22',
  '/database/31', '/model/41', '/experiment/detail/61', '/datasource/source/new',
  '/datasource/api/new', '/database/new', '/experiment/new',
  '/designer/table/101', '/designer/tree/102', '/designer/flow/103', '/designer/ruleset/104',
  '/designer/cross/105', '/designer/score/106', '/designer/cross-adv/107',
  '/designer/score-adv/108', '/designer/script/109',
]

function apiData() {
  const data = createDetailApiData()
  for (const [key, value] of createDocsApiData()) if (!data.has(key)) data.set(key, value)
  for (const [key, value] of createDesignerApiData()) data.set(key, value)
  data.set('/api/auth/console/profile', { username: 'e2e', displayName: '布局验收', registrationStatus: 'ACTIVE' })
  data.set('/api/rule/dashboard/settings', { editable: true, fields: [], options: [] })
  data.set('/api/rule/governance/requests/1', { request: data.get('/api/rule/governance/requests').records[0], versions: [], events: [] })
  data.set('/api/rule/system/settings', {
    brand: { systemName: '天枢决策引擎', subtitle: '天工开物，枢衡定策', logoId: '', faviconId: '' },
    mail: { email: '', host: '', port: 587, encryption: 'STARTTLS' },
    access: { registrationEnabled: false, passwordRecoveryEnabled: false },
    codes: { enabled: false, mappings: {} }, codeCatalog: [], deploymentManaged: {},
  })
  data.set('/api/rule/sftp/list', { records: [], total: 0 })
  return data
}

async function measure(page) {
  return page.evaluate(() => {
    const main = document.querySelector('.layout-main')
    const footer = document.querySelector('.layout-footer')
    const root = main.firstElementChild
    return {
      route: location.hash,
      outerOverflow: main.scrollHeight - main.clientHeight,
      pageClass: root.className,
      pageHeight: root.getBoundingClientRect().height,
      mainBottom: main.getBoundingClientRect().bottom,
      footerTop: footer.getBoundingClientRect().top,
      footerBottom: footer.getBoundingClientRect().bottom,
      footerHeight: footer.getBoundingClientRect().height,
      viewportHeight: innerHeight,
      rootOverflow: document.documentElement.scrollHeight - document.documentElement.clientHeight,
    }
  })
}

test('业务页面均为页脚预留空间，固定工作区不出现额外外层滚动', async ({ page }, testInfo) => {
  test.setTimeout(90000)
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: apiData() })
  await page.setViewportSize({ width: 1440, height: 900 })
  const metrics = []
  for (const route of routes) {
    await page.goto(`${origin}/#${route}`)
    await expect(page.locator(`.workspace-tab[data-tab="${route}"]`)).toHaveClass(/is-active/)
    await expect(page.locator('.layout-main > *').first()).toBeVisible()
    await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
    const result = await measure(page)
    metrics.push(result)
    expect.soft(result.mainBottom, route).toBeLessThanOrEqual(result.footerTop + 1)
    expect.soft(result.footerBottom, route).toBeLessThanOrEqual(result.viewportHeight + 1)
    expect.soft(result.rootOverflow, route).toBeLessThanOrEqual(1)
    if (/uiue-compact-designer|rule-test-page|management-list-page/.test(result.pageClass)) {
      expect.soft(result.outerOverflow, route).toBeLessThanOrEqual(1)
    }
  }
  await require('node:fs/promises').writeFile(testInfo.outputPath('page-height-audit.json'), JSON.stringify(metrics, null, 2))
  await testInfo.attach('page-height-audit', { body: JSON.stringify(metrics, null, 2), contentType: 'application/json' })
  harness.assertClean()
})

for (const [name, route, area] of [
  ['决策树', '/designer/tree/102', '.tree-canvas'],
  ['决策流', '/designer/flow/103', '.flow-canvas'],
  ['脚本', '/designer/script/109', '.se-body'],
  ['规则测试', '/test?projectId=1', '.test-layout'],
  ['血缘', '/lineage', '.graph-wrap'],
]) {
  test(`${name}在顶部导航、项目范围和页脚换行后仍按剩余高度展示`, async ({ page }, testInfo) => {
    const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
    const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: apiData() })
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto(`${origin}/#${route}`)
    await expect(page.locator(area).first()).toBeVisible()
    await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
    await page.setViewportSize({ width: 1100, height: 800 })
    await expect.poll(async () => (await measure(page)).footerHeight).toBeGreaterThan(32)
    await expect.poll(async () => (await measure(page)).outerOverflow).toBeLessThanOrEqual(1)
    await page.locator('.layout-account-trigger').click()
    await page.locator('[data-account-command="theme"]').click()
    const drawer = page.getByRole('dialog', { name: '主题设置' })
    await drawer.locator('[data-navigation-layout="TOP"]').click()
    await drawer.locator('[data-action="save"]').click()
    for (const size of [{ width: 1280, height: 720 }, { width: 1100, height: 800 }, { width: 1440, height: 900 }]) {
      await page.setViewportSize(size)
      await expect.poll(async () => (await measure(page)).outerOverflow).toBeLessThanOrEqual(1)
      const metrics = await measure(page)
      expect(metrics.mainBottom).toBeLessThanOrEqual(metrics.footerTop + 1)
      expect(metrics.footerBottom).toBeLessThanOrEqual(size.height + 1)
      expect((await page.locator(area).first().boundingBox()).height).toBeGreaterThan(120)
      if (name === '决策流' && size.width === 1280) await page.screenshot({ path: testInfo.outputPath('flow-footer-height.png') })
    }
    harness.assertClean()
  })
}

test('表达式展开页面使用可用高度，页脚多行和顶部导航下仍可编辑', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: apiData() })
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto(`${origin}/#/designer/table/101`)
  await page.getByRole('button', { name: '添加行', exact: true }).click()
  await page.getByPlaceholder('选择左操作数...').first().click()
  await page.locator('.fp-panel:visible').getByRole('button', { name: 'ƒx 组合表达式', exact: true }).click()
  await page.locator('.fp-panel:visible').getByRole('button', { name: '展开页面', exact: true }).click()
  const editor = page.locator('.expression-editor-page')
  await expect(editor).toBeVisible()
  await page.locator('.layout-account-trigger').click()
  await page.locator('[data-account-command="theme"]').click()
  const drawer = page.getByRole('dialog', { name: '主题设置' })
  await drawer.locator('[data-navigation-layout="TOP"]').click()
  await drawer.locator('[data-action="save"]').click()
  await page.setViewportSize({ width: 900, height: 720 })
  await expect.poll(async () => (await measure(page)).outerOverflow).toBeLessThanOrEqual(1)
  await expect(editor.getByRole('button', { name: '应用到规则', exact: true })).toBeInViewport()
  await expect(editor.locator('.canvas-scroll')).toBeVisible()
  expect((await measure(page)).footerHeight).toBeGreaterThan(32)
  harness.assertClean()
})

test('长评分卡在设计器内部滚动，底部内容仍可访问且页脚不受挤压', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: apiData() })
  await page.setViewportSize({ width: 1280, height: 720 })
  await page.goto(`${origin}/#/designer/score/106`)
  const add = page.getByRole('button', { name: '添加评分项', exact: true })
  await expect(add).toBeEnabled()
  for (let index = 0; index < 8; index++) await add.click()
  const designer = page.locator('.sc-designer')
  const last = page.locator('.score-item-card').last()
  await last.scrollIntoViewIfNeeded()
  await expect(last).toBeInViewport()
  expect(await designer.evaluate(el => el.scrollTop)).toBeGreaterThan(0)
  expect((await measure(page)).outerOverflow).toBeLessThanOrEqual(1)
  await expect(page.locator('.layout-footer')).toBeInViewport()
  harness.assertClean()
})
