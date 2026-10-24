const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createOperationsApiData } = require('./support/operationsFixtures.cjs')

for (const colorScheme of ['LIGHT', 'DARK']) {
  test(`${colorScheme} 标题栏两个菜单使用统一字体、行高和交互样式`, async ({ page }, testInfo) => {
    const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
    let queries = 0
    const { assertClean } = await installDistRoutes(page, {
      origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL),
      apiData: new Map([
        ['/api/auth/console/config', { loginEnabled: true }],
        ['/api/auth/console/me', { username: 'admin', permissions: ['project:view'] }],
        ['/api/auth/console/preferences/theme', { schemaVersion: 1, colorScheme, accentPreset: 'THEME_BLUE', sidebarTheme: 'DARK', contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false }],
        ['POST /api/rule/console/activity', true],
        ['/api/rule/project/list', () => { queries++; return { records: [], total: 0 } }],
      ]),
    })
    await page.setViewportSize({ width: 1280, height: 900 })
    await page.goto(`${origin}/#/project`)
    await expect(page.locator('html')).toHaveAttribute('data-theme', colorScheme.toLowerCase())
    await page.locator('.workspace-tab__main').click({ button: 'right' })
    const context = page.locator('.workspace-tabs__context-menu')
    const refresh = context.getByRole('menuitem', { name: '刷新', exact: false })
    const itemStyle = locator => locator.evaluate(el => {
      const style = getComputedStyle(el)
      const icon = getComputedStyle(el.querySelector('.el-icon'))
      return { family: style.fontFamily, size: style.fontSize, height: el.getBoundingClientRect().height,
        lineHeight: style.lineHeight, padding: style.padding, gap: style.gap, iconSize: icon.fontSize }
    })
    const panelStyle = locator => locator.evaluate(el => {
      const style = getComputedStyle(el)
      return [style.padding, style.backgroundColor, style.borderColor, style.borderRadius, style.boxShadow]
    })
    await expect(refresh).toHaveCSS('font-size', '14px')
    const expectedItem = await itemStyle(refresh)
    expect(expectedItem.height).toBe(32)
    expect(expectedItem.family).toContain('AlimamaFangYuanTiVF')
    const expectedPanel = await panelStyle(context)
    await context.screenshot({ path: testInfo.outputPath(`context-menu-${colorScheme}.png`) })
    const shortcut = await refresh.locator('.workspace-tab-operation__shortcut').boundingBox()
    const contextBox = await context.boundingBox()
    expect(shortcut.x + shortcut.width).toBeLessThan(contextBox.x + contextBox.width)
    const disabled = context.getByRole('menuitem', { name: '关闭左侧', exact: true })
    await expect(disabled).toBeDisabled()
    await disabled.hover()
    await expect(disabled).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)')
    await refresh.hover()
    await expect.poll(() => refresh.evaluate(el => el.getAnimations().filter(animation => animation.playState === 'running').length)).toBe(0)
    const hoverStyle = locator => locator.evaluate(el => [getComputedStyle(el).color, getComputedStyle(el).backgroundColor])
    const expectedHover = await hoverStyle(refresh)
    await refresh.focus()
    await page.keyboard.press('Tab')
    await page.keyboard.press('Shift+Tab')
    await expect(refresh).toBeFocused()
    await expect(refresh).toHaveCSS('outline-style', 'solid')
    const beforeRefresh = queries
    await refresh.press('Enter')
    await expect(context).toBeHidden()
    await expect.poll(() => queries).toBeGreaterThan(beforeRefresh)

    await page.getByRole('button', { name: 'admin的账户菜单' }).click()
    const account = page.locator('.layout-account-dropdown')
    const theme = account.getByRole('menuitem', { name: '主题设置', exact: true })
    await expect(theme).toBeVisible()
    await expect.poll(() => itemStyle(theme)).toEqual(expectedItem)
    expect(await panelStyle(account)).toEqual(expectedPanel)
    await account.screenshot({ path: testInfo.outputPath(`account-menu-${colorScheme}.png`) })
    await theme.hover()
    await expect.poll(() => hoverStyle(theme)).toEqual(expectedHover)
    await theme.focus()
    await page.keyboard.press('ArrowDown')
    await page.keyboard.press('ArrowUp')
    await expect(theme).toBeFocused()
    await expect(theme).toHaveCSS('outline-style', 'solid')
    await theme.press('Enter')
    const drawer = page.getByRole('dialog', { name: '主题设置', exact: true })
    await expect(drawer).toBeVisible()
    await drawer.getByRole('button', { name: '取消', exact: true }).click()
    assertClean()
  })
}

test('页签背景仅随明暗模式变化，选中下划线跟随主题色', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const { assertClean } = await installDistRoutes(page, {
    origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL),
  })
  await page.goto(`${origin}/#/project`)
  const activeTab = page.locator('.workspace-tab.is-active')
  await expect(activeTab).toBeVisible()
  await expect(activeTab.locator('.workspace-tab__main .app-icon')).toHaveCount(0)
  const appearance = () => activeTab.evaluate(el => {
    const indicator = getComputedStyle(el, '::after')
    return {
      background: getComputedStyle(document.querySelector('.layout-topbar')).backgroundColor,
      closeBackground: getComputedStyle(el.querySelector('.workspace-tab__close')).backgroundColor,
      lineColor: indicator.backgroundColor,
      lineImage: indicator.backgroundImage,
      lineHeight: indicator.height,
      text: getComputedStyle(el).color,
    }
  })
  const original = await appearance()
  await page.locator('.layout-account-trigger').click()
  await page.locator('[data-account-command="theme"]').click()
  const drawer = page.getByRole('dialog', { name: '主题设置' })
  await drawer.locator('[data-accent="THEME_BLUE"]').click()
  await expect.poll(async () => (await appearance()).background).toBe(original.background)
  await expect.poll(async () => (await appearance()).closeBackground).toBe(original.closeBackground)
  await expect.poll(async () => (await appearance()).lineImage).toBe('none')
  await expect.poll(async () => (await appearance()).lineColor).toBe('rgb(38, 57, 233)')
  const light = await appearance()
  expect(light.lineHeight).toBe('2px')
  await drawer.locator('[data-theme-scheme="DARK"]').click()
  await expect.poll(async () => (await appearance()).background).not.toBe(light.background)
  await expect.poll(async () => (await appearance()).text).not.toBe(light.text)
  await expect.poll(() => activeTab.locator('.workspace-tab__close').evaluate(el =>
    el.getAnimations().filter(animation => animation.playState === 'running').length
  )).toBe(0)
  const dark = await appearance()
  await drawer.locator('[data-accent="PEACH_PINK_GRADIENT"]').click()
  await expect.poll(async () => (await appearance()).lineImage).toContain('linear-gradient')
  await expect.poll(async () => (await appearance()).background).toBe(dark.background)
  await expect.poll(async () => (await appearance()).closeBackground).toBe(dark.closeBackground)
  await drawer.locator('[data-action="cancel"]').click()
  await expect.poll(appearance).toEqual(original)
  assertClean()
})

for (const width of [1920, 560]) {
  test(`页脚 ${width}px 自适应换行并悬停展示对应联系二维码`, async ({ page }) => {
    const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
    const { assertClean } = await installDistRoutes(page, {
      origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL),
    })
    await page.route('https://hengshucredit.com/images/*.svg', route => route.fulfill({
      contentType: 'image/svg+xml',
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="220" height="220"><rect width="220" height="220" fill="white" /></svg>',
    }))
    await page.setViewportSize({ width, height: 1000 })
    await page.goto(`${origin}/#/project`)
    const footer = page.locator('.layout-footer')
    await expect(footer).toBeInViewport()
    const copyright = await footer.locator('.layout-footer-copyright').boundingBox()
    const wechat = page.getByRole('button', { name: '微信：itlubber', exact: true })
    const official = page.getByRole('button', { name: '公众号：itlubber_art', exact: true })
    if (width === 1920) {
      for (const item of [wechat, official, footer.getByRole('link', { name: 'github.com/itlubber', exact: true })]) {
        expect(Math.abs((await item.boundingBox()).y - copyright.y)).toBeLessThanOrEqual(1)
      }
    } else {
      expect((await official.boundingBox()).y).toBeGreaterThan(copyright.y)
    }
    expect(await footer.evaluate(el => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(1)
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1)
    expect((await page.locator('.layout-main').boundingBox()).y + (await page.locator('.layout-main').boundingBox()).height)
      .toBeLessThanOrEqual((await footer.boundingBox()).y + 1)

    for (const [button, alt, filename] of [
      [wechat, '微信 itlubber 二维码', 'itlubber.svg'],
      [official, '公众号 itlubber_art 二维码', 'itlubber_art.svg'],
    ]) {
      const image = page.getByRole('img', { name: alt, exact: true })
      await expect(image).toBeHidden()
      await button.hover()
      await expect(image).toBeVisible()
      await expect(image).toHaveAttribute('src', `https://hengshucredit.com/images/${filename}`)
      await expect.poll(() => image.evaluate(el => el.naturalWidth)).toBeGreaterThan(0)
      const imageBox = await image.boundingBox()
      expect(imageBox.y).toBeGreaterThanOrEqual(0)
      expect(imageBox.x).toBeGreaterThanOrEqual(0)
      expect(imageBox.x + imageBox.width).toBeLessThanOrEqual(width)
      expect(imageBox.y + imageBox.height).toBeLessThanOrEqual((await button.boundingBox()).y)
      await page.locator('.layout-topbar').hover()
      await expect(image).toBeHidden()
    }
    assertClean()
  })
}

for (const sidebarTheme of ['DARK', 'LIGHT']) {
  test(`${sidebarTheme} 侧栏折叠按钮悬停与菜单一致，空间充足贴底、溢出跟随菜单滚动`, async ({ page }, testInfo) => {
    const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
    const { assertClean } = await installDistRoutes(page, {
      origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL),
    })
    await page.setViewportSize({ width: 1440, height: 1100 })
    await page.goto(`${origin}/#/project`)
    await page.locator('.layout-account-trigger').click()
    await page.locator('[data-account-command="theme"]').click()
    await page.locator(`[data-sidebar-theme="${sidebarTheme}"]`).click()
    await page.locator('[data-action="save"]').click()
    const menu = page.locator('.sidebar-menu')
    const lastItem = menu.locator('.sidebar-menu__item').last()
    const collapse = menu.locator('.sidebar-collapse')
    const appearance = locator => locator.evaluate(element => {
      const style = getComputedStyle(element)
      return [style.color, style.backgroundColor, style.borderTopWidth, style.borderRadius, style.height]
    })
    for (const compact of [false, true]) {
      if (compact) await collapse.click()
      await expect(page.locator('.layout-sidebar')).toHaveCSS('width', compact ? '64px' : '220px')
      await page.setViewportSize({ width: 1440, height: 1100 })
      await expect.poll(async () => {
        const box = await collapse.boundingBox()
        return 1100 - box.y - box.height
      }).toBe(10)
      await lastItem.hover()
      await expect(lastItem).toHaveCSS('background-color', sidebarTheme === 'DARK' ? 'rgb(38, 45, 79)' : 'rgb(241, 245, 249)')
      const expectedAppearance = await appearance(lastItem)
      await collapse.hover()
      await expect.poll(() => appearance(collapse)).toEqual(expectedAppearance)
      await page.setViewportSize({ width: 1440, height: 600 })
      await expect.poll(() => menu.evaluate(element => element.scrollHeight - element.clientHeight)).toBeGreaterThan(0)
      await menu.hover()
      await page.mouse.wheel(0, -2000)
      await expect.poll(() => menu.evaluate(element => element.scrollTop)).toBe(0)
      expect((await collapse.boundingBox()).y).toBeGreaterThan(600)
      await page.mouse.wheel(0, 2000)
      await expect(collapse).toBeInViewport()
      const lastBox = await lastItem.boundingBox()
      expect((await collapse.boundingBox()).y - lastBox.y - lastBox.height).toBe(4)
      await collapse.hover()
      await page.locator('.layout-sidebar').screenshot({ path: testInfo.outputPath(`sidebar-${sidebarTheme}-${compact}.png`) })
      await page.setViewportSize({ width: 1440, height: 1100 })
    }
    assertClean()
  })
}

test('默认主题与截图一致，预览取消、恢复默认、保存刷新和每页条数均可用', async ({ page }, testInfo) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const apiData = createOperationsApiData()
  const source = apiData.get('/api/rule/project/list').records[0]
  const rows = Array.from({ length: 45 }, (_, index) => ({
    ...source, id: index + 1, projectCode: `DEFAULT_${index + 1}`,
  }))
  apiData.set('/api/rule/project/list', ({ url }) => {
    const size = Number(url.searchParams.get('pageSize'))
    const page = Number(url.searchParams.get('pageNum'))
    return { records: rows.slice((page - 1) * size, page * size), total: rows.length }
  })
  const { assertClean } = await installDistRoutes(page, {
    origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData,
  })
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto(`${origin}/#/project`)
  const root = page.locator('html')
  const tableRows = page.locator('.management-table .el-table__body > tbody > tr')
  const pager = page.locator('.management-list-page .el-pagination:visible')
  await expect(tableRows).toHaveCount(20)
  await pager.getByRole('button', { name: '下一页' }).click()
  await expect(tableRows.first()).toContainText('DEFAULT_21')
  await pager.locator('.el-select').click()
  await page.getByRole('option', { name: '10条/页', exact: true }).click()
  await expect(tableRows).toHaveCount(10)
  await pager.locator('.el-select').click()
  await page.getByRole('option', { name: '20条/页', exact: true }).click()
  await expect(tableRows).toHaveCount(20)
  await expect(tableRows.first()).toContainText('DEFAULT_1')

  await page.locator('.layout-account-trigger').click()
  await page.locator('[data-account-command="theme"]').click()
  const drawer = page.getByRole('dialog', { name: '主题设置' })
  for (const selector of ['[data-theme-scheme="LIGHT"]', '[data-accent="PEACH_PINK_GRADIENT"]', '[data-navigation-layout="LEFT"]', '[data-sidebar-theme="DARK"]', '[data-content-width="FLUID"]', '[data-table-scroll-mode="FIXED"]']) {
    await expect(drawer.locator(selector)).toHaveAttribute('aria-pressed', 'true')
  }
  await drawer.locator('[data-theme-scheme="DARK"]').click()
  await drawer.locator('[data-action="cancel"]').click()
  await expect(root).toHaveAttribute('data-theme', 'light')
  await page.locator('.layout-account-trigger').click()
  await page.locator('[data-account-command="theme"]').click()
  await drawer.locator('[data-accent="THEME_BLUE"]').click()
  await drawer.locator('[data-table-scroll-mode="AUTO"]').click()
  await drawer.locator('[data-action="restore-default"]').click()
  await expect(drawer.locator('[data-accent="PEACH_PINK_GRADIENT"]')).toHaveAttribute('aria-pressed', 'true')
  await expect(root).toHaveAttribute('data-table-scroll-mode', 'fixed')
  await page.screenshot({ path: testInfo.outputPath('default-theme-and-pagination.png') })
  await drawer.locator('[data-action="save"]').click()
  await page.reload()
  await expect(tableRows).toHaveCount(20)
  await expect(root).toHaveAttribute('data-theme', 'light')
  await expect(root).toHaveAttribute('data-table-scroll-mode', 'fixed')
  await expect(root).toHaveCSS('--tianshu-brand-background', 'linear-gradient(135deg, #873FF2 0%, #E14ECA 100%)')
  assertClean()
})

for (const [route, tab, endpoint] of [
  ['variable', '常量列表', '/api/rule/variable/list'],
  ['variable', '字段校验', '/api/rule/field-validation/list'],
  ['billing', '计费明细', '/api/rule/billing/record/list'],
  ['billing', '计费汇总', '/api/rule/billing/summary/list'],
  ['database', '调用日志', '/api/rule/runtime-log/list'],
  ['datasource', '调用日志', '/api/rule/runtime-log/list'],
  ['model', '模型执行日志', '/api/rule/runtime-log/list'],
  ['list', '名单日志', '/api/rule/runtime-log/list'],
  ['sftp', null, '/api/rule/sftp/list'],
  ['sftp/logs', null, '/api/rule/runtime-log/list'],
]) {
  test(`${route} ${tab || '列表'} 默认请求和分页选项均为 20 条`, async ({ page }) => {
    const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
    const apiData = createOperationsApiData()
    apiData.set('/api/rule/sftp/list', { records: [], total: 0 })
    const { requests, assertClean } = await installDistRoutes(page, {
      origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData,
    })
    await page.goto(`${origin}/#/${route}`)
    if (tab) await page.getByRole('tab', { name: tab, exact: true }).click()
    const pager = page.locator('.management-list-page .el-pagination:visible').last()
    await expect(pager.locator('.el-select')).toHaveText('20条/页')
    await expect.poll(() => requests.some(request => {
      const url = new URL(request.url)
      return url.pathname === endpoint && url.searchParams.get('pageSize') === '20'
    })).toBe(true)
    assertClean()
  })
}
