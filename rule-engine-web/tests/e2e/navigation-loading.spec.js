const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')
const { DEFAULT_THEME_CONFIG } = require('../../src/theme/themeConfig.js')

function deferred() {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}

test('首次下载页面代码时先显示骨架，下载完成后正常切换并支持浏览器返回', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const download = deferred()
  const { assertClean } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL) })
  await page.route(/\/(?:src\/views\/rule\/RuleList\.vue|assets\/RuleList-[^/]+\.js)(?:\?.*)?$/, async route => {
    await download.promise
    await route.fallback()
  })
  await page.goto(`${origin}/#/project`)
  await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeVisible()
  try {
    await page.locator('[data-menu-path="/rule"]').click()
    await expect(page.getByTestId('route-page-skeleton')).toContainText('规则管理', { timeout: 1000 })
    await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeHidden()
    download.resolve()
    await expect(page).toHaveURL(/#\/rule$/)
    await expect(page.getByRole('textbox', { name: '规则编码', exact: true })).toBeVisible()
    await page.goBack()
    await expect(page).toHaveURL(/#\/project$/)
    await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeVisible()
    await expect(page.getByTestId('route-page-skeleton')).toBeHidden()
    assertClean()
  } finally {
    download.resolve()
  }
})

test('会话失效转到登录页时继续显示骨架，不闪回旧页面内容', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const download = deferred()
  let expired = false
  const { assertClean } = await installDistRoutes(page, {
    origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL),
    apiData: new Map([
      ['/api/auth/console/config', { loginEnabled: true }],
      ['/api/auth/console/me', () => expired ? null : { username: 'admin', permissions: ['project:view', 'rule:view'] }],
      ['/api/auth/console/preferences/theme', {}],
      ['POST /api/rule/console/activity', true],
    ]),
  })
  await page.route(/\/(?:src\/views\/Login\.vue|assets\/Login-[^/]+\.js)(?:\?.*)?$/, async route => {
    await download.promise
    await route.fallback()
  })
  await page.goto(`${origin}/#/project`)
  await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'admin的账户菜单' })).toBeVisible()
  expired = true
  try {
    await page.locator('[data-menu-path="/rule"]').click()
    await expect(page.getByTestId('route-page-skeleton')).toContainText('登录')
    await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeHidden()
    download.resolve()
    await expect(page).toHaveURL(/#\/login\?redirect=\/rule$/)
    await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
    assertClean()
  } finally {
    download.resolve()
  }
})

test('菜单点击后立即显示目标骨架，校验和列表响应分阶段完成', async ({ page }, testInfo) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const auth = deferred()
  const data = deferred()
  let holdAuth = false
  let ruleRequests = 0
  const { assertClean } = await installDistRoutes(page, {
    origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL),
    apiData: new Map([
      ['/api/auth/console/config', { loginEnabled: true }],
      ['/api/auth/console/me', async () => {
        if (holdAuth) await auth.promise
        return { username: 'admin', permissions: ['project:view', 'rule:view', 'model:view'] }
      }],
      ['/api/auth/console/preferences/theme', {}],
      ['POST /api/rule/console/activity', true],
      ['/api/rule/definition/list', async () => {
        ruleRequests++
        await data.promise
        return { records: [{ id: 1, ruleCode: 'async_rule', ruleName: '异步加载规则', modelType: 'TABLE', scope: 'GLOBAL', status: 1 }], total: 1 }
      }],
    ]),
  })
  await page.goto(`${origin}/#/project`)
  await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'admin的账户菜单' })).toBeVisible()
  holdAuth = true
  try {
    await page.locator('[data-menu-path="/rule"]').click()
    const skeleton = page.getByTestId('route-page-skeleton')
    await expect(skeleton).toBeVisible({ timeout: 1000 })
    await expect(skeleton).toContainText('规则管理')
    await expect(skeleton.locator('.tianshu-loading')).toBeVisible()
    expect(await skeleton.locator('.tianshu-loading').evaluate(element => getComputedStyle(element, '::before').animationName)).toBe('tianshu-loader2')
    await expect(skeleton.locator('.route-page-skeleton__block').first()).toHaveCSS('animation-name', 'none')
    await expect(page.locator('[data-menu-path="/rule"]')).toHaveClass(/is-active/)
    await expect(page.locator('.workspace-tab.is-active')).toContainText('规则管理')
    await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeHidden()
    expect(ruleRequests).toBe(0)
    await page.screenshot({ path: testInfo.outputPath('navigation-skeleton.png') })
    auth.resolve()
    await expect(page).toHaveURL(/#\/rule$/)
    await expect(skeleton).toBeHidden()
    await expect(page.getByRole('textbox', { name: '规则编码', exact: true })).toBeVisible()
    await expect(page.locator('.layout-main .el-loading-mask').first()).toBeVisible()
    data.resolve()
    await expect(page.getByRole('cell', { name: 'async_rule', exact: true })).toBeVisible()
    assertClean()
  } finally {
    auth.resolve()
    data.resolve()
  }
})

for (const navigationLayout of ['LEFT', 'TOP']) {
  test(`${navigationLayout} 全部菜单等待期间立即反馈，点击当前页可取消且不遗留页签`, async ({ page }, testInfo) => {
    const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
    const auth = deferred()
    let waiting = false
    const { assertClean } = await installDistRoutes(page, {
      origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL),
      apiData: new Map([
        ['/api/auth/console/config', { loginEnabled: true }],
        ['/api/auth/console/me', async () => {
          if (waiting) await auth.promise
          return { username: 'admin', permissions: ['project', 'rule', 'field', 'list', 'datasource', 'database', 'model', 'function', 'lineage', 'experiment', 'log', 'billing', 'approval', 'transfer', 'account', 'system'].map(module => `${module}:view`) }
        }],
        ['/api/auth/console/preferences/theme', { ...DEFAULT_THEME_CONFIG, navigationLayout, colorScheme: navigationLayout === 'TOP' ? 'DARK' : 'LIGHT' }],
        ['POST /api/rule/console/activity', true],
      ]),
    })
    await page.goto(`${origin}/#/project`)
    await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'admin的账户菜单' })).toBeVisible()
    if (navigationLayout === 'TOP') await expect(page.locator('.top-navigation__menu')).toBeVisible()
    await expect(page.locator('[data-menu-path]')).toHaveCount(19)
    const targets = await page.locator('[data-menu-path]').evaluateAll(items => items.map(item => ({ path: item.dataset.menuPath, title: item.getAttribute('aria-label') })))
    waiting = true
    try {
      for (const target of targets.filter(item => item.path !== '/project')) {
        const title = target.path === '/sftp' ? 'SFTP数据源配置' : target.title
        await page.locator(`[data-menu-path="${target.path}"]`).click()
        await expect(page.getByTestId('route-page-skeleton')).toContainText(title, { timeout: 1000 })
        await expect(page.locator('.workspace-tab.is-active')).toContainText(title)
        await expect(page.locator(`[data-menu-path="${target.path}"]`)).toHaveClass(/is-active/)
        if (target.path === '/rule') {
          await page.locator('.workspace-tab[data-tab="/rule"]').hover()
          await page.getByRole('button', { name: '关闭规则管理', exact: true }).click()
        } else await page.locator('[data-menu-path="/project"]').click()
        await expect(page.getByTestId('route-page-skeleton')).toBeHidden()
        await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeVisible()
        await expect(page.locator('.workspace-tab')).toHaveCount(1)
      }
      await page.setViewportSize({ width: 760, height: 700 })
      await page.emulateMedia({ reducedMotion: 'reduce' })
      await page.locator('[data-menu-path="/rule"]').click()
      await expect(page.getByTestId('route-page-skeleton')).toBeVisible()
      await expect(page.locator('.route-page-skeleton__block').first()).toHaveCSS('animation-name', 'none')
      expect(await page.getByTestId('route-page-skeleton').locator('.tianshu-loading').evaluate(element => getComputedStyle(element, '::before').animationName)).toBe('none')
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
      await page.screenshot({ path: testInfo.outputPath(`skeleton-${navigationLayout}.png`) })
      await page.locator('[data-menu-path="/project"]').click()
    } finally {
      auth.resolve()
    }
    await expect(page.getByTestId('route-page-skeleton')).toBeHidden()
    await expect(page).toHaveURL(/#\/project$/)
    assertClean()
  })
}

test('新目标加载完成后旧校验才返回，页面和活动页签仍属于最后一次点击', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const oldAuth = deferred()
  let deferNext = false
  const { assertClean } = await installDistRoutes(page, {
    origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL),
    apiData: new Map([
      ['/api/auth/console/config', { loginEnabled: true }],
      ['/api/auth/console/me', async () => {
        if (deferNext) { deferNext = false; await oldAuth.promise }
        return { username: 'admin', permissions: ['project:view', 'rule:view', 'model:view'] }
      }],
      ['/api/auth/console/preferences/theme', {}],
      ['POST /api/rule/console/activity', true],
    ]),
  })
  await page.goto(`${origin}/#/project`)
  await expect(page.getByRole('button', { name: 'admin的账户菜单' })).toBeVisible()
  deferNext = true
  try {
    await page.locator('[data-menu-path="/rule"]').click()
    await expect(page.getByTestId('route-page-skeleton')).toContainText('规则管理')
    await expect.poll(() => deferNext).toBe(false)
    await page.locator('[data-menu-path="/model"]').click()
    await expect(page).toHaveURL(/#\/model$/)
    await expect(page.getByRole('textbox', { name: '模型编码', exact: true })).toBeVisible()
    oldAuth.resolve()
    await expect(page.locator('.workspace-tab.is-active')).toContainText('模型管理')
    await expect(page.getByTestId('route-page-skeleton')).toBeHidden()
    await expect(page.locator('[data-menu-path="/model"]')).toHaveClass(/is-active/)
    await expect(page.locator('.workspace-tab')).toHaveCount(2)
    assertClean()
  } finally {
    oldAuth.resolve()
  }
})

test('未保存配置取消离开时保留输入，确认离开后可以通过页签返回缓存页面', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const { assertClean } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: createDetailApiData() })
  await page.goto(`${origin}/#/database/new`)
  const name = page.getByPlaceholder('如 客户核心库', { exact: true })
  await name.fill('尚未保存的配置')
  await page.locator('[data-menu-path="/project"]').click()
  await expect(page.getByRole('dialog', { name: '未保存提醒' })).toBeVisible()
  await expect(page.getByTestId('route-page-skeleton')).toBeHidden()
  await page.getByRole('button', { name: '继续编辑', exact: true }).click()
  await expect(name).toHaveValue('尚未保存的配置')
  await page.locator('[data-menu-path="/project"]').click()
  await page.getByRole('button', { name: '继续离开', exact: true }).click()
  await expect(page).toHaveURL(/#\/project$/)
  await page.locator('[data-menu-path="/variable"]').click()
  await expect(page).toHaveURL(/#\/variable$/)
  const code = page.getByPlaceholder('变量编码', { exact: true })
  await code.fill('保留筛选')
  await page.locator('[data-menu-path="/project"]').click()
  await expect(page).toHaveURL(/#\/project$/)
  await page.locator('.workspace-tab__main').filter({ hasText: '变量管理' }).click()
  await expect(page).toHaveURL(/#\/variable$/)
  await expect(code).toHaveValue('保留筛选')
  assertClean()
})
