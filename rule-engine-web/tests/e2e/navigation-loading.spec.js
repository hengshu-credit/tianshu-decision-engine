const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')
const { DEFAULT_THEME_CONFIG } = require('../../src/theme/themeConfig.js')

const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
const liveStatic = Boolean(process.env.E2E_DEV_BASE_URL)
function deferred() {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}

for (const navigationLayout of ['LEFT', 'TOP']) {
  test(`${navigationLayout} 慢会话复核和数据请求不挡住分流实验实际页面，动画只在表格中央`, async ({ page }, testInfo) => {
    const auth = deferred(), data = deferred()
    let holdAuth = false, dataReturned = false
    const apiData = createDetailApiData()
    const experiments = apiData.get('/api/rule/experiment/list')
    apiData.set('/api/auth/console/config', { loginEnabled: true })
    apiData.set('/api/auth/console/me', async () => {
      if (holdAuth) await auth.promise
      return { username: 'admin', permissions: ['project:view', 'experiment:view', 'experiment:edit'] }
    })
    apiData.set('/api/auth/console/preferences/theme', { ...DEFAULT_THEME_CONFIG, navigationLayout, colorScheme: navigationLayout === 'TOP' ? 'DARK' : 'LIGHT' })
    apiData.set('POST /api/rule/console/activity', true)
    apiData.set('/api/rule/experiment/list', async () => { await data.promise; dataReturned = true; return experiments })
    const { assertClean } = await installDistRoutes(page, { origin, liveStatic, apiData })
    await page.goto(`${origin}/#/project`)
    await expect(page.getByRole('button', { name: 'admin的账户菜单' })).toBeVisible()
    await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeVisible()
    holdAuth = true
    try {
      await page.locator('[data-menu-path="/experiment"]').click()
      await expect(page).toHaveURL(/#\/experiment$/, { timeout: 1500 })
      await expect(page.getByRole('heading', { name: '分流实验', exact: true })).toBeVisible()
      await expect(page.getByRole('button', { name: '新建实验', exact: true })).toBeVisible()
      await expect(page.getByPlaceholder('编码或名称')).toBeVisible()
      await expect(page.getByRole('columnheader', { name: '实验编码', exact: true })).toBeVisible()
      await expect(page.getByTestId('route-page-skeleton')).toHaveCount(0)
      const table = page.locator('.experiment-page .el-table')
      const spinner = table.locator('.el-loading-spinner')
      await expect(spinner).toBeVisible()
      await expect(table.locator('.el-table__empty-text')).toBeHidden()
      const box = await table.boundingBox(), icon = await spinner.boundingBox()
      expect(Math.abs(icon.x + icon.width / 2 - box.x - box.width / 2)).toBeLessThan(2)
      expect(Math.abs(icon.y + icon.height / 2 - box.y - box.height / 2)).toBeLessThan(2)
      expect(dataReturned).toBe(false)
      await page.screenshot({ path: testInfo.outputPath(`real-page-loading-${navigationLayout}.png`) })
      await page.locator('.workspace-tab__main[data-path="/project"]').click()
      await expect(page).toHaveURL(/#\/project$/, { timeout: 1500 })
      expect(dataReturned).toBe(false)
      auth.resolve()
      data.resolve()
      await expect.poll(() => dataReturned).toBe(true)
      await page.locator('[data-menu-path="/experiment"]').click()
      await expect(page.getByRole('cell', { name: 'risk_ab', exact: true })).toBeVisible()
      await expect(page.locator('.experiment-page .el-loading-mask')).toHaveCount(0)
      assertClean()
    } finally { auth.resolve(); data.resolve() }
  })
}

const pages = [
  ['/rule', '/api/rule/definition/list'], ['/variable', '/api/rule/variable/list'],
  ['/model', '/api/rule/model/list'], ['/function', '/api/rule/function/list'],
  ['/database', '/api/rule/database/list'], ['/datasource', '/api/rule/datasource/list'],
  ['/list', '/api/rule/list/library'], ['/billing', '/api/rule/billing/config/list'],
  ['/log', '/api/rule/log/list'],
]
for (const [path, endpoint] of pages) {
  test(`${path} 首次进入直接展示筛选栏和表头，加载限定在表格内`, async ({ page }) => {
    const data = deferred()
    let requested = false
    const apiData = createDetailApiData()
    const result = apiData.get(endpoint)
    apiData.set(endpoint, async args => { requested = true; await data.promise; return typeof result === 'function' ? result(args) : result })
    const { assertClean } = await installDistRoutes(page, { origin, liveStatic, apiData })
    try {
      await page.goto(`${origin}/#/project`)
      await page.locator(`[data-menu-path="${path}"]`).click()
      await expect(page).toHaveURL(`${origin}/#${path}`)
      await expect.poll(() => requested).toBe(true)
      await expect(page.locator('.layout-main .uiue-filter-toolbar:visible, .layout-main .tab-filter-row:visible').first()).toBeVisible()
      const table = page.locator('.layout-main .el-table:visible:has(> .el-loading-mask)').first()
      await expect(table.locator('thead')).toBeVisible()
      await expect(table.locator('.el-loading-spinner')).toBeVisible()
      await expect(page.getByTestId('route-page-skeleton')).toHaveCount(0)
      await page.locator('.workspace-tab__main[data-path="/project"]').click({ timeout: 1500 })
      await expect(page).toHaveURL(/#\/project$/)
      data.resolve()
      assertClean()
    } finally { data.resolve() }
  })
}

test('首次未验证会话不请求业务数据，已登录会话失效仍返回登录页', async ({ page }) => {
  const auth = deferred()
  let expire = false
  const { requests } = await installDistRoutes(page, { origin, liveStatic, apiData: new Map([
    ['/api/auth/console/config', { loginEnabled: true }],
    ['/api/auth/console/me', async () => {
      await auth.promise
      return expire ? null : { username: 'admin', permissions: ['project:view', 'rule:view'] }
    }],
    ['/api/auth/console/preferences/theme', null], ['POST /api/rule/console/activity', true],
  ]) })
  try {
    await page.goto(`${origin}/#/project`)
    await expect(page.locator('.app-bootstrap')).toBeVisible()
    expect(requests.some(request => new URL(request.url).pathname === '/api/rule/project/list')).toBe(false)
    await expect(page.getByTestId('route-page-skeleton')).toHaveCount(0)
    auth.resolve()
    await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'admin的账户菜单' })).toBeVisible()
    expire = true
    await page.locator('[data-menu-path="/rule"]').click()
    await expect(page).toHaveURL(/#\/login\?redirect=\/rule$/)
    await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
  } finally { auth.resolve() }
})

test('页面代码冷下载保留当前内容，完成后显示真实目标页，不插入骨架过渡页', async ({ page }) => {
  const download = deferred()
  await installDistRoutes(page, { origin, liveStatic })
  await page.route(/\/(?:src\/views\/rule\/RuleList\.vue|assets\/RuleList-[^/]+\.js)(?:\?.*)?$/, async route => { await download.promise; await route.fallback() })
  try {
    await page.goto(`${origin}/#/project`)
    await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeVisible()
    await page.locator('[data-menu-path="/rule"]').click()
    await expect(page.locator('.workspace-tab.is-active')).toContainText('规则管理')
    await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeVisible()
    await expect(page.getByTestId('route-page-skeleton')).toHaveCount(0)
    download.resolve()
    await expect(page).toHaveURL(/#\/rule$/)
    await expect(page.getByRole('textbox', { name: '规则编码', exact: true })).toBeVisible()
    await page.goBack()
    await expect(page).toHaveURL(/#\/project$/)
  } finally { download.resolve() }
})

test('未保存配置仍需确认，缓存页返回保留筛选输入', async ({ page }) => {
  const { assertClean } = await installDistRoutes(page, { origin, liveStatic, apiData: createDetailApiData() })
  await page.goto(`${origin}/#/database/new`)
  const name = page.getByPlaceholder('如 客户核心库', { exact: true })
  await name.fill('尚未保存的配置')
  await page.locator('[data-menu-path="/project"]').click()
  await expect(page.getByRole('dialog', { name: '未保存提醒' })).toBeVisible()
  await page.getByRole('button', { name: '继续编辑', exact: true }).click()
  await expect(name).toHaveValue('尚未保存的配置')
  await page.locator('[data-menu-path="/project"]').click()
  await page.getByRole('button', { name: '继续离开', exact: true }).click()
  await page.locator('[data-menu-path="/variable"]').click()
  const code = page.getByPlaceholder('变量编码', { exact: true })
  await code.fill('保留筛选')
  await page.locator('[data-menu-path="/project"]').click()
  await page.locator('.workspace-tab__main[data-path="/variable"]').click()
  await expect(code).toHaveValue('保留筛选')
  assertClean()
})
