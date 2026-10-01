const { expect, test } = require('@playwright/test')

test.afterEach(async ({ page }) => {
  for (const input of await page.locator('input[type="password"]').all()) await input.fill('')
})

test('真实管理列表：登录、选择50行、保存滚动设置、刷新和恢复设置', async ({ page }, testInfo) => {
  test.skip(!process.env.E2E_TABLE_SCROLL_LIVE, '显式开启真实环境验证')
  test.setTimeout(90000)
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.setViewportSize({ width: 1600, height: 1000 })
  await page.goto(`${process.env.E2E_BASE_URL}/#/variable`)
  await expect(page.locator('.layout-account-trigger, input[autocomplete="username"]').first()).toBeVisible()
  if (page.url().includes('#/login')) {
    await page.locator('input[autocomplete="username"]').fill(process.env.E2E_USERNAME)
    await page.locator('input[autocomplete="current-password"]').fill(process.env.E2E_PASSWORD)
    const response = page.waitForResponse(response => response.url().includes('/auth/console/login'))
    await page.locator('button[type="submit"]').click()
    const result = await (await response).json()
    expect(result.code, result.message).toBe(200)
  }
  await expect(page.locator('.layout-account-trigger')).toBeVisible()
  const openTheme = async () => {
    await page.locator('.layout-account-trigger').click()
    await page.locator('[data-account-command="theme"]').click()
    return page.getByRole('dialog', { name: '主题设置' })
  }
  const drawer = await openTheme()
  const originalMode = await drawer.locator('[data-table-scroll-mode][aria-pressed="true"]').getAttribute('data-table-scroll-mode')
  await drawer.locator('[data-action="cancel"]').click()
  const saveMode = async mode => {
    const theme = await openTheme()
    await theme.locator(`[data-table-scroll-mode="${mode}"]`).click()
    const saved = page.waitForResponse(response => response.url().endsWith('/preferences/theme') && response.request().method() === 'PUT')
    await theme.locator('[data-action="save"]').click()
    expect((await (await saved).json()).code).toBe(200)
    await expect(theme).toBeHidden()
  }
  try {
    const pager = page.locator('.management-list-page .el-pagination:visible').last()
    await pager.locator('.el-select').click()
    await page.getByRole('option', { name: '50条/页', exact: true }).click()
    await pager.getByRole('listitem', { name: '第 1 页', exact: true }).click()
    const table = page.locator('.management-table:visible').first()
    await expect(table.locator('.el-table__body > tbody > tr')).toHaveCount(50)
    await saveMode('FIXED')
    await expect(pager).toBeInViewport()
    await expect.poll(() => page.locator('.layout-main').evaluate(el => el.scrollHeight - el.clientHeight)).toBeLessThanOrEqual(1)
    const header = table.locator('.el-table__header-wrapper')
    const before = { header: await header.boundingBox(), pager: await pager.boundingBox() }
    await table.locator('.el-scrollbar__wrap').first().hover()
    await page.mouse.wheel(0, 1400)
    await expect.poll(() => table.locator('.el-scrollbar__wrap').first().evaluate(el => el.scrollTop)).toBeGreaterThan(0)
    expect(Math.abs((await header.boundingBox()).y - before.header.y)).toBeLessThan(1)
    expect(Math.abs((await pager.boundingBox()).y - before.pager.y)).toBeLessThan(1)
    await page.screenshot({ path: testInfo.outputPath('fixed-table-scroll.png') })
    await page.reload()
    await expect(page.locator('html')).toHaveAttribute('data-table-scroll-mode', 'fixed')
    await saveMode('AUTO')
    await pager.locator('.el-select').click()
    await page.getByRole('option', { name: '50条/页', exact: true }).click()
    await expect.poll(() => page.locator('.layout-main').evaluate(el => el.scrollHeight - el.clientHeight)).toBeGreaterThan(100)
    expect(errors).toEqual([])
  } finally {
    await saveMode(originalMode)
  }
})
