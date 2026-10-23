const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createOperationsApiData } = require('./support/operationsFixtures.cjs')

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
