const { expect, test } = require('@playwright/test')

test.afterEach(async ({ page }) => {
  for (const input of await page.locator('input[type="password"]').all()) await input.fill('')
})

test('真实评分卡加载字段名称后保持未修改，真实编辑仍受离开保护', async ({ page }) => {
  const base = process.env.E2E_HYDRATION_BASE_URL
  test.skip(!base, '显式设置 E2E_HYDRATION_BASE_URL；只修改页面内存并放弃，不保存规则')
  test.setTimeout(60000)
  const id = process.env.E2E_HYDRATION_RULE_ID || '17'
  const errors = []
  const writes = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('request', request => {
    if (request.method() === 'POST' && request.url().includes('/api/rule/definition')) writes.push(request.url())
  })
  await page.goto(`${base}/#/rule/${id}`)
  await expect(page.locator('.layout-sidebar, input[autocomplete="username"]').first()).toBeVisible()
  if (page.url().includes('#/login')) {
    await page.locator('input[autocomplete="username"]').fill(process.env.E2E_USERNAME)
    await page.locator('input[autocomplete="current-password"]').fill(process.env.E2E_PASSWORD)
    await page.locator('button[type="submit"]').click()
  }
  await page.getByRole('button', { name: '进入设计', exact: true }).click()
  await expect(page).toHaveURL(/\/designer\/score-adv\//)
  await expect(page.getByTestId('designer-info')).toContainText(/已加载 \d+ 个变量/)
  await expect(page.locator('.result-var-picker input')).not.toHaveValue('')
  await expect(page.getByTestId('designer-info')).toContainText('已加载，尚未修改')
  await page.getByTestId('designer-version-select').click()
  const published = page.getByRole('option', { name: /^发布版本 v/ }).first()
  const target = await published.innerText()
  await published.click()
  await expect(page.getByRole('dialog', { name: '切换版本', exact: true })).toBeHidden()
  await expect(page.getByTestId('designer-version-select')).toContainText(target)
  await expect(page.getByTestId('designer-info')).toContainText('已加载，尚未修改')

  const score = page.locator('.asc-base-config').getByRole('spinbutton').first()
  const original = await score.inputValue()
  await score.fill(String(Number(original) + 1))
  await score.press('Tab')
  await expect(page.getByTestId('designer-info')).toContainText('有未保存修改')
  await page.getByTestId('designer-version-select').click()
  await page.getByRole('option', { name: /^草稿/ }).first().click()
  const confirm = page.getByRole('dialog', { name: '切换版本', exact: true })
  await expect(confirm).toBeVisible()
  await confirm.getByRole('button', { name: '取消', exact: true }).click()
  await expect(score).toHaveValue(String(Number(original) + 1))
  await score.fill(original)
  await score.press('Tab')
  await expect(page.getByTestId('designer-info')).toContainText('已加载，尚未修改')
  expect(writes).toEqual([])
  expect(errors).toEqual([])
})
