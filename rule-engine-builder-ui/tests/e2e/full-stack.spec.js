const { expect, test } = require('@playwright/test')

const baseUrl = process.env.E2E_BASE_URL

test('真实部署核心页面可访问', async ({ page }) => {
  test.skip(!baseUrl, '设置 E2E_BASE_URL 后执行真实前后端联调')

  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))

  await page.goto(`${baseUrl.replace(/\/$/, '')}/#/project`)
  if (page.url().includes('#/login')) {
    const username = process.env.E2E_USERNAME
    const password = process.env.E2E_PASSWORD
    if (!username || !password) {
      throw new Error('控制台启用登录时必须设置 E2E_USERNAME 和 E2E_PASSWORD')
    }
    await page.locator('input[autocomplete="username"]').fill(username)
    await page.locator('input[autocomplete="current-password"]').fill(password)
    await page.locator('button[type="submit"]').click()
  }

  await expect(page.locator('.layout-sidebar')).toBeVisible()
  await expect(page.locator('[data-menu-path="/project"]')).toHaveClass(/is-active/)
  expect(pageErrors).toEqual([])
})

test('真实数据库可通过可视化参数完成只读查询', async ({ page }) => {
  test.skip(!baseUrl, '设置 E2E_BASE_URL 后执行真实前后端联调')

  const pageErrors = []
  const consoleErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })

  await page.setViewportSize({ width: 1280, height: 720 })
  await page.goto(`${baseUrl.replace(/\/$/, '')}/#/database`)
  if (page.url().includes('#/login')) {
    const username = process.env.E2E_USERNAME
    const password = process.env.E2E_PASSWORD
    if (!username || !password) {
      throw new Error('控制台启用登录时必须设置 E2E_USERNAME 和 E2E_PASSWORD')
    }
    await page.locator('input[autocomplete="username"]').fill(username)
    await page.locator('input[autocomplete="current-password"]').fill(password)
    await page.locator('button[type="submit"]').click()
  }

  const datasourceRow = page.getByRole('row').filter({
    hasText: 'rule_engine_mysql'
  })
  await expect(datasourceRow).toBeVisible()
  await datasourceRow.getByRole('button', { name: '查询', exact: true }).click()
  const dialog = page.locator('.el-dialog:visible').last()
  await expect(dialog.getByText('只读查询', { exact: true })).toBeVisible()

  const editor = dialog.locator('.monaco-editor-container')
  await editor.click()
  await page.keyboard.insertText('SELECT ? AS score')
  await expect(dialog.getByText('已识别 1 个有效 ? 占位符', { exact: true }))
    .toBeVisible()
  await dialog.locator('.query-param-type').click()
  await page.getByRole('option', { name: '数值', exact: true }).last().click()
  await dialog.getByLabel('参数 1 值').fill('88')
  await dialog.getByRole('button', { name: '执行查询', exact: true }).click()

  await expect(dialog.getByText('查询成功', { exact: true })).toBeVisible()
  await expect(dialog.getByRole('cell', { name: '88', exact: true })).toBeVisible()
  const dialogBox = await dialog.boundingBox()
  const viewport = page.viewportSize()
  expect(dialogBox.x).toBeGreaterThanOrEqual(0)
  expect(dialogBox.y).toBeGreaterThanOrEqual(0)
  expect(dialogBox.x + dialogBox.width).toBeLessThanOrEqual(viewport.width)
  expect(dialogBox.y + dialogBox.height).toBeLessThanOrEqual(viewport.height)
  expect(pageErrors).toEqual([])
  expect(consoleErrors).toEqual([])
})

test('真实模型可确认参数来源并通过表单执行在线测试', async ({ page }) => {
  test.skip(!baseUrl, '设置 E2E_BASE_URL 后执行真实前后端联调')

  const pageErrors = []
  const consoleErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })

  await page.setViewportSize({ width: 1280, height: 720 })
  await page.goto(`${baseUrl.replace(/\/$/, '')}/#/model`)
  if (page.url().includes('#/login')) {
    const username = process.env.E2E_USERNAME
    const password = process.env.E2E_PASSWORD
    if (!username || !password) {
      throw new Error('控制台启用登录时必须设置 E2E_USERNAME 和 E2E_PASSWORD')
    }
    await page.locator('input[autocomplete="username"]').fill(username)
    await page.locator('input[autocomplete="current-password"]').fill(password)
    await page.locator('button[type="submit"]').click()
  }

  const codeFilter = page.getByRole('textbox', { name: '模型编码', exact: true }).first()
  await codeFilter.fill('score_f1')
  await codeFilter.press('Enter')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  const modelRow = page.getByRole('row').filter({ hasText: 'score_f1' })
  await expect(modelRow).toBeVisible()
  await modelRow.getByRole('button', { name: '详情', exact: true }).click()
  await page.getByRole('button', { name: '模型测试', exact: true }).click()

  const dialog = page.getByRole('dialog', { name: '模型测试' })
  await expect(dialog.getByText(/测试配置已(就绪|降级加载)/)).toBeVisible()
  await expect(dialog.getByText(
    /参数来源：(已保存测试参数|模型上传样例|测试 Schema 样例|字段默认值)/
  )).toBeVisible()
  await dialog.getByRole('button', { name: '执行测试', exact: true }).click()
  await expect(dialog.getByText('执行成功', { exact: true })).toBeVisible()
  await expect(dialog.locator('pre')).not.toBeEmpty()

  const dialogBox = await dialog.boundingBox()
  const viewport = page.viewportSize()
  expect(dialogBox.x).toBeGreaterThanOrEqual(0)
  expect(dialogBox.y).toBeGreaterThanOrEqual(0)
  expect(dialogBox.x + dialogBox.width).toBeLessThanOrEqual(viewport.width)
  expect(dialogBox.y + dialogBox.height).toBeLessThanOrEqual(viewport.height)
  expect(pageErrors).toEqual([])
  expect(consoleErrors).toEqual([])
})
