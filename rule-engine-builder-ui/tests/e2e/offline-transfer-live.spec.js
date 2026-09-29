const { expect, test } = require('@playwright/test')

test.afterEach(async ({ page }) => {
  for (const input of await page.locator('input[type="password"]').all()) await input.fill('')
})

// 显式指定独立验证环境后运行；所有创建与导入操作均通过页面完成。
test('真实离线迁移：全局、已有项目覆盖策略、新建项目均保持明确归属', async ({ page }, testInfo) => {
  const base = process.env.E2E_TRANSFER_BASE_URL
  test.skip(!base, '设置 E2E_TRANSFER_BASE_URL 后执行真实服务验收；会创建一个独立验证项目')
  test.setTimeout(120000)
  page.setDefaultTimeout(15000)
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto(`${base}/#/project`)
  await expect(page.locator('.layout-sidebar, input[autocomplete="username"]').first()).toBeVisible()
  if (page.url().includes('#/login')) {
    if (!process.env.E2E_USERNAME || !process.env.E2E_PASSWORD) throw new Error('需要提供本地验证账号')
    await page.locator('input[autocomplete="username"]').fill(process.env.E2E_USERNAME)
    await page.locator('input[autocomplete="current-password"]').fill(process.env.E2E_PASSWORD)
    const loginResponse = page.waitForResponse('**/api/auth/console/login')
    await page.locator('button[type="submit"]').click()
    const login = await (await loginResponse).json()
    if (await page.locator('input[autocomplete="current-password"]').count()) {
      await page.locator('input[autocomplete="current-password"]').fill('').catch(() => {})
    }
    expect(login.code, login.message).toBe(200)
  }
  await expect(page.locator('.layout-sidebar')).toBeVisible()
  const row = page.locator('.el-table__body-wrapper tbody tr').first()
  await expect(row).toBeVisible()
  const projectCode = (await row.locator('td').nth(0).innerText()).trim()
  await row.getByRole('button', { name: '进入', exact: true }).click()
  await expect(page).toHaveURL(/#\/project\/\d+/)
  const sourceId = Number(/#\/project\/(\d+)/.exec(page.url())[1])
  await page.getByRole('button', { name: '离线迁移', exact: true }).first().click()
  await expect(page.getByRole('heading', { name: '离线配置迁移' })).toBeVisible()
  await page.locator('.root-type').click()
  await page.getByRole('option', { name: '项目', exact: true }).click()
  await page.getByPlaceholder('资源 ID').fill(String(sourceId))
  const downloaded = page.waitForEvent('download')
  await page.getByRole('button', { name: '生成并下载配置包' }).click()
  const filename = testInfo.outputPath('real-project-transfer.zip')
  await (await downloaded).saveAs(filename)
  await page.locator('input[type="file"]').setInputFiles(filename)

  const choose = async (label, value) => {
    await page.getByRole('combobox', { name: label, exact: true }).press('ArrowDown')
    await page.getByRole('option', { name: value, exact: true }).click()
  }
  const importFromUi = async () => {
    const previewResponse = page.waitForResponse('**/api/rule/transfer/preview/options')
    await page.getByRole('button', { name: '预览冲突', exact: true }).click()
    const preview = await (await previewResponse).json()
    expect(preview.code, preview.message).toBe(200)
    expect(preview.data.conflictCount).toBe(0)
    await page.getByRole('button', { name: '确认导入', exact: true }).click()
    const appliedResponse = page.waitForResponse('**/api/rule/transfer/import')
    await page.getByRole('dialog', { name: '确认导入' }).getByRole('button', { name: '确定', exact: true }).click()
    const applied = await (await appliedResponse).json()
    expect(applied.code, applied.message).toBe(200)
    await expect(page.getByRole('dialog', { name: '导入结果' })).toBeVisible()
    await page.getByRole('dialog', { name: '导入结果' }).getByRole('button', { name: '关闭此对话框' }).click()
    return applied.data
  }

  await choose('目标范围', '全局')
  const global = await importFromUi()
  expect(global.projectCreated).toBe(false)
  expect(global.resourceIdMapping[`PROJECT:${sourceId}`]).toBe(0)
  expect(global.resources[0].status).toBe('SKIPPED')

  await choose('目标范围', '项目级')
  await page.getByRole('combobox', { name: '目标项目', exact: true }).fill(projectCode)
  await page.getByRole('option', { name: new RegExp(` / ${projectCode}$`) }).click()
  await choose('资源冲突策略', '覆盖已有配置')
  const bound = await importFromUi()
  expect(bound.projectCreated).toBe(false)
  expect(bound.resourceIdMapping[`PROJECT:${sourceId}`]).toBe(sourceId)
  expect(bound.resources[0].status).toBe('BOUND')

  await page.getByText('导入时新建项目', { exact: true }).click()
  const newCode = `transfer_verify_${Date.now()}`
  await page.getByPlaceholder('新项目编码（可覆盖源编码）').fill(newCode)
  await page.getByPlaceholder('新项目名称（可覆盖源名称）').fill('离线迁移独立验收项目')
  await choose('资源冲突策略', '复用相同配置')
  const created = await importFromUi()
  expect(created.projectCreated).toBe(true)
  expect(created.targetProjectId).toBeGreaterThan(0)
  expect(created.targetProjectId).not.toBe(sourceId)
  expect(created.resourceIdMapping[`PROJECT:${sourceId}`]).toBe(created.targetProjectId)

  await page.getByRole('button', { name: '项目管理', exact: true }).first().click()
  await page.getByRole('textbox', { name: '项目编码', exact: true }).fill(newCode)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('cell', { name: newCode, exact: true })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('real-new-project.png'), fullPage: true })
  expect(pageErrors).toEqual([])
  console.log(JSON.stringify({ sourceId, createdProjectId: created.targetProjectId, createdProjectCode: newCode }))
})
