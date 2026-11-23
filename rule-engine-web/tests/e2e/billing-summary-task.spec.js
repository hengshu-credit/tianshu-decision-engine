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
    const toast = page.locator('.el-message').filter({ hasText: '汇总已完成' })
    await expect(toast).toContainText('1000000 条明细')
    await expect(status).toHaveCount(0)
    await expect(refresh).toBeEnabled()
    expect(submissions).toBe(1)
    expect(statusReads).toBeGreaterThanOrEqual(2)
    await page.setViewportSize({ width: 560, height: 820 })
    await expect(toast).toBeHidden({ timeout: 10000 })
    await expect(refresh).toBeInViewport()
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
  const summaryDatePicker = page.locator('.el-form-item').filter({ hasText: /^汇总日期/ })
  const summaryDateInputs = summaryDatePicker.locator('input.el-range-input')
  await summaryDateInputs.nth(0).fill('2026-10-12')
  await summaryDateInputs.nth(1).fill('2026-10-12')
  await summaryDateInputs.nth(1).press('Enter')
  await expect(page.getByTestId('summary-refresh-status')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '刷新汇总', exact: true })).toBeEnabled()
  assertClean()
})

test('从计费编辑选中项目规则并保存邮箱，汇总行只提交自己的 ID', async ({ page }) => {
  const data = createDocsApiData()
  const config = data.get('/api/rule/billing/config/list').records[0]
  const rules = data.get('/api/rule/definition/project-list/1')
  const nextRule = { ...rules.records[0], id: 108, ruleCode: 'kyc_check', ruleName: '活体核验' }
  data.set('/api/rule/definition/project-list/1', { records: [...rules.records, nextRule], total: 2 })
  let saved, sends = 0
  data.set('POST /api/rule/governance/drafts', ({ request }) => {
    saved = JSON.parse(request.postDataJSON().snapshotJson)
    return { resourceId: config.id, resourceType: 'BILLING_CONFIG', state: 'DRAFT' }
  })
  const summary = data.get('/api/rule/billing/summary/list').records[0]
  Object.assign(summary, { projectId: 1, mailAvailable: true })
  data.set('POST /api/rule/billing/summary/83/send', () => { sends++; return null })
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/index.html#/billing')
  await page.getByRole('button', { name: '编辑', exact: true }).first().click()
  const dialog = page.getByRole('dialog', { name: '编辑计费配置' })
  await expect(dialog.locator('.el-form-item').filter({ hasText: '所属项目' })).toContainText(config.projectName)
  await dialog.locator('.el-form-item').filter({ hasText: '指定对象' }).getByRole('combobox').click()
  await page.getByRole('option', { name: /活体核验/ }).click()
  await dialog.getByPlaceholder('选填，审批生效后可在计费汇总发送月度账单').fill('billing@example.test')
  await dialog.getByRole('button', { name: '保存草稿' }).click()
  await expect(dialog).toBeHidden()
  expect(saved).toMatchObject({ projectId: 1, targetRefId: 108, billEmail: 'billing@example.test' })
  await page.getByRole('tab', { name: '计费汇总', exact: true }).click()
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByText('账单邮件已发送')).toBeVisible()
  expect(sends).toBe(1)
  assertClean()
})

test('新建计费项先选项目再选规则，邮箱可留空', async ({ page }) => {
  const data = createDocsApiData()
  const project = data.get('/api/rule/project/1')
  const rule = data.get('/api/rule/definition/project-list/1').records[0]
  let saved
  data.set('POST /api/rule/governance/drafts', ({ request }) => {
    saved = JSON.parse(request.postDataJSON().snapshotJson)
    return { resourceId: 92 }
  })
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/index.html#/billing')
  await page.getByRole('button', { name: /新建/ }).first().click()
  const dialog = page.getByRole('dialog', { name: '新建计费配置' })
  const selector = label => dialog.locator('.el-form-item').filter({ hasText: label }).getByRole('combobox')
  await expect(selector('指定对象')).toBeDisabled()
  await selector('所属项目').click()
  await page.getByRole('option', { name: new RegExp(project.projectName) }).click()
  await dialog.getByPlaceholder('如 ENGINE_COUNT').fill('KycMonth')
  await dialog.getByPlaceholder('如 规则执行计费').fill('活体计费')
  await selector('指定对象').click()
  await page.getByRole('option', { name: new RegExp(rule.ruleName) }).click()
  await dialog.getByRole('button', { name: '保存草稿' }).click()
  await expect(dialog).toBeHidden()
  expect(saved).toMatchObject({ projectId: project.id, targetRefId: rule.id, billEmail: '' })
  assertClean()
})
