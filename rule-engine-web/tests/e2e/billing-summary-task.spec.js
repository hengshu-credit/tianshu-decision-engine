const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDocsApiData } = require('./support/docsFixtures.cjs')

for (const colorScheme of ['LIGHT', 'DARK']) {
  test(`${colorScheme} 汇总后台运行、网络恢复、刷新页面及完成反馈`, async ({ page }, testInfo) => {
    await page.addInitScript(scheme => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({
      schemaVersion: 1, colorScheme: scheme, accentPreset: 'THEME_BLUE', sidebarTheme: 'DARK', contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false,
    })), colorScheme)
    let current = null, submissions = 0, statusReads = 0
    const data = createDocsApiData()
    data.set('/api/rule/billing/summary/refresh', () => current)
    data.set('POST /api/rule/billing/summary/refresh', () => {
      submissions++
      current = { taskId: 'billing-task', summaryDate: '2026-10-11', status: 'PENDING' }
      return current
    })
    data.set('/api/rule/billing/summary/refresh/billing-task', () => { statusReads++; return current })
    const { assertClean } = await installDistRoutes(page, { apiData: data })
    await page.goto('http://tianshu.local/index.html#/billing')
    await expect(page.locator('html')).toHaveAttribute('data-theme', colorScheme.toLowerCase())
    await page.getByRole('tab', { name: '计费汇总', exact: true }).click()
    const refresh = page.getByRole('button', { name: '刷新汇总', exact: true })
    await refresh.click()
    const status = page.getByTestId('summary-refresh-status')
    await expect(status).toContainText('等待汇总')
    await expect(refresh).toBeDisabled()
    await expect(status).toContainText('上次成功的汇总')
    await expect(page.locator('.el-table:visible .el-table__body')).toContainText('face_verify_call')
    current.status = 'RUNNING'
    await expect(status).toContainText('汇总中')
    expect(submissions).toBe(1)

    let failRead = true
    await page.route('**/api/rule/billing/summary/refresh/billing-task', async route => {
      if (!failRead) return route.fallback()
      failRead = false
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 500, message: '测试进度查询失败' }) })
    })
    await expect(page.getByText(/刷新进度读取失败/)).toBeVisible()
    await page.getByRole('button', { name: '重试查询', exact: true }).click()
    await expect(status).toContainText('汇总中')
    expect(submissions).toBe(1)
    await page.reload()
    await page.getByRole('tab', { name: '计费汇总', exact: true }).click()
    await expect(status).toContainText('汇总中')
    await expect(refresh).toBeDisabled()
    current = { ...current, status: 'SUCCEEDED', recordCount: 1000000, summaryCount: 20000 }
    await expect(status).toContainText('汇总已完成')
    await expect(status).toContainText('1000000 条明细')
    await expect(refresh).toBeEnabled()
    expect(submissions).toBe(1)
    expect(statusReads).toBeGreaterThanOrEqual(2)
    await page.setViewportSize({ width: 560, height: 820 })
    await status.scrollIntoViewIfNeeded()
    await status.hover()
    await page.mouse.wheel(0, 200)
    await expect(status).toBeInViewport()
    await expect.poll(async () => (await status.boundingBox()).y).toBeGreaterThanOrEqual(0)
    await expect.poll(async () => { const box = await status.boundingBox(); return box.y + box.height }).toBeLessThanOrEqual(820)
    await page.screenshot({ path: testInfo.outputPath(`billing-summary-${colorScheme}.png`), fullPage: true, animations: 'disabled' })
    assertClean()
  })
}

test('汇总失败保留旧表，重试创建新任务且切换日期展示对应任务', async ({ page }) => {
  const data = createDocsApiData()
  let latest = { taskId: 'failed-task', summaryDate: '2026-10-11', status: 'FAILED', errorMessage: '汇总处理失败，原汇总保留' }
  let submissions = 0
  data.set('/api/rule/billing/summary/refresh', ({ url }) => url.searchParams.get('summaryDate') === '2026-10-12' ? null : latest)
  data.set('POST /api/rule/billing/summary/refresh', () => {
    submissions++
    latest = { taskId: 'retry-task', summaryDate: '2026-10-11', status: 'PENDING' }
    return latest
  })
  data.set('/api/rule/billing/summary/refresh/retry-task', () => latest)
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/index.html#/billing')
  await page.getByRole('tab', { name: '计费汇总', exact: true }).click()
  await expect(page.getByTestId('summary-refresh-status')).toContainText('汇总失败')
  await expect(page.locator('.el-table:visible .el-table__body')).toContainText('face_verify_call')
  await page.getByRole('button', { name: '刷新汇总', exact: true }).click()
  await expect(page.getByTestId('summary-refresh-status')).toContainText('等待汇总')
  expect(submissions).toBe(1)
  await page.getByPlaceholder('选择日期', { exact: true }).fill('2026-10-12')
  await page.getByPlaceholder('选择日期', { exact: true }).press('Enter')
  await expect(page.getByTestId('summary-refresh-status')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '刷新汇总', exact: true })).toBeEnabled()
  assertClean()
})
