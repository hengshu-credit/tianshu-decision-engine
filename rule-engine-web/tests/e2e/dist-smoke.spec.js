const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')

test('登录页账密初始为空，填入后只有主动提交才发送登录请求', async ({ page }) => {
  let loginRequests = 0
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const { assertClean } = await installDistRoutes(page, {
    origin,
    liveStatic: Boolean(process.env.E2E_DEV_BASE_URL),
    apiData: new Map([
      ['/api/auth/console/config', { loginEnabled: true, initializationRequired: false }],
      ['/api/auth/console/me', { userId: 1, username: 'e2e-user', permissions: ['project:view'], defaultPermissions: ['project:view'], projectAccessMode: 'ALL', globalEditable: true }],
      ['/api/auth/console/preferences/theme', null],
      ['POST /api/rule/console/activity', null],
      ['POST /api/auth/console/login', ({ request }) => {
        loginRequests++
        expect(request.postDataJSON()).toEqual({ username: 'e2e-user', password: 'E2E-only-password-123' })
        return null
      }],
    ]),
  })
  await page.goto(`${origin}/#/login`)
  const username = page.getByRole('textbox', { name: /用户名$/ })
  const password = page.getByLabel(/密码$/)
  await expect(username).toHaveValue('')
  await expect(password).toHaveValue('')
  expect(loginRequests).toBe(0)
  await username.fill('e2e-user')
  await password.fill('E2E-only-password-123')
  await password.blur()
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeEnabled()
  expect(loginRequests).toBe(0)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/#\/project$/)
  expect(loginRequests).toBe(1)
  assertClean()
})

test('根路由默认进入数据看板并完成三分区加载', async ({ page }) => {
  const { assertClean } = await installDistRoutes(page)
  await page.goto('http://tianshu.local/index.html#/')

  await expect(page).toHaveURL(/#\/dashboard$/)
  await expect(page.locator('[data-menu-path="/dashboard"]')).toHaveClass(/is-active/)
  await expect(page.getByText('进件数', { exact: true })).toBeVisible()
  await expect(page.getByText('规则执行耗时分布', { exact: true })).toBeVisible()
  await expect(page.getByText('规则集命中规则 TOP', { exact: true })).toBeVisible()
  assertClean()
})

test('生产构建可加载并完成核心管理页面导航', async ({ page }) => {
  const pageErrors = []
  const consoleErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })

  const { assertClean } = await installDistRoutes(page)
  await page.goto('http://tianshu.local/index.html#/project')

  await expect(page.locator('.sidebar-brand')).toContainText('天枢决策引擎')
  await expect(page.locator('[data-menu-path="/project"]')).toHaveClass(/is-active/)
  await expect(page.getByText('E2E 项目', { exact: true })).toBeVisible()

  await page.locator('[data-menu-path="/rule"]').click()
  await expect(page).toHaveURL(/#\/rule$/)
  await expect(page.locator('[data-menu-path="/rule"]')).toHaveClass(/is-active/)

  await page.locator('[data-menu-path="/model"]').click()
  await expect(page).toHaveURL(/#\/model$/)
  await expect(page.locator('[data-menu-path="/model"]')).toHaveClass(/is-active/)
  await expect(page.getByText('模型管理', { exact: true }).last()).toBeVisible()

  expect(pageErrors).toEqual([])
  expect(consoleErrors).toEqual([])
  assertClean()
})

test('未配置的模拟接口会被严格验收检出', async ({ page }) => {
  const { assertClean } = await installDistRoutes(page)
  await page.goto('http://tianshu.local/index.html#/project')
  await page.evaluate(() => fetch('/api/e2e-unexpected'))

  expect(() => assertClean()).toThrow('/api/e2e-unexpected')
})

test('模拟接口会校验 HTTP 方法', async ({ page }) => {
  const { assertClean } = await installDistRoutes(page, {
    apiData: new Map([['/api/e2e-method', { ok: true }]])
  })
  await page.goto('http://tianshu.local/index.html#/project')
  await page.evaluate(() => fetch('/api/e2e-method', { method: 'POST' }))

  expect(() => assertClean()).toThrow('POST /api/e2e-method (expected GET)')
})
