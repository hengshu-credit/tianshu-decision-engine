const { existsSync } = require('node:fs')
const path = require('node:path')
const { expect, test } = require('@playwright/test')

const envPath = path.resolve(__dirname, '../../../.env')
if (existsSync(envPath)) process.loadEnvFile(envPath)
const baseUrl = process.env.E2E_BASE_URL

test('真实规则测试收藏保留执行快照并完整恢复输入', async ({ page }) => {
  test.skip(!baseUrl, '设置 E2E_BASE_URL 后执行真实前后端联调')
  test.setTimeout(180000)
  const pageErrors = []
  let rules = []
  let created = false
  const caseName = `UI收藏回归_${Date.now()}`
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('response', async response => {
    if (/\/api\/rule\/definition\/(list|project-list\/\d+)$/.test(new URL(response.url()).pathname)) {
      const body = await response.json()
      rules = body.data?.records || []
    }
  })
  await page.setViewportSize({ width: 1600, height: 1000 })
  await page.goto(`${baseUrl.replace(/\/$/, '')}/#/test`)
  await expect(page.locator('.layout-sidebar, input[autocomplete="username"]').first()).toBeVisible()
  if (page.url().includes('#/login')) {
    if (!process.env.E2E_USERNAME || !process.env.E2E_PASSWORD) {
      throw new Error('控制台启用登录时必须设置 E2E_USERNAME 和 E2E_PASSWORD')
    }
    await page.locator('input[autocomplete="username"]').fill(process.env.E2E_USERNAME)
    await page.locator('input[autocomplete="current-password"]').fill(process.env.E2E_PASSWORD)
    await page.locator('button[type="submit"]').click()
  }
  await expect(page.locator('.rule-test-page')).toBeVisible()
  await expect.poll(() => rules.length).toBeGreaterThan(0)
  const rule = process.env.E2E_RULE_TEST_CODE
    ? rules.find(item => item.ruleCode === process.env.E2E_RULE_TEST_CODE)
    : rules.find(item => ['CROSS', 'SCORE'].includes(item.modelType) && item.status === 1)
  expect(rule, '需要一个已有的已发布简单交叉表/评分卡，或通过 E2E_RULE_TEST_CODE 指定测试规则').toBeTruthy()
  const selector = page.locator('.test-left .el-form-item').filter({
    has: page.locator('.el-form-item__label').filter({ hasText: /^规则$/ })
  }).locator('.el-select')
  await selector.click()
  await page.getByRole('option').filter({ hasText: `(${rule.ruleCode})` }).click()
  await expect(page.locator('.input-load-state strong')).toHaveText(/测试输入已就绪|未识别到结构化输入/)

  await page.getByRole('button', { name: '手动添加', exact: true }).click()
  const snapshotRow = page.locator('.param-row').last()
  await snapshotRow.locator('.param-key input').fill('uiSnapshotMarker')
  await snapshotRow.locator('.param-value input').fill('original')
  const savedParamCount = await page.locator('.param-row').count()
  const executionResponse = page.waitForResponse(response =>
    new URL(response.url()).pathname === `/api/rule/definition/${rule.id}/api-scenarios/execute-validated` && response.request().method() === 'POST')
  await page.getByRole('button', { name: '执行测试', exact: true }).click()
  const execution = await executionResponse
  const executedParams = JSON.parse(execution.request().postDataJSON().requestJson).params
  await expect(page.locator('.test-result-content')).toBeVisible()
  expect((await execution.json()).data?.execution?.success).toBe(true)
  await expect(page.getByTestId('test-validation-result')).toContainText('未设置预期')

  try {
    await page.getByRole('button', { name: '保存为新用例', exact: true }).click()
    const prompt = page.locator('.el-message-box')
    await expect(prompt.getByText('收藏测试用例', { exact: true })).toBeVisible()
    await prompt.locator('input').fill(caseName)
    const saveResponse = page.waitForResponse(response =>
      new URL(response.url()).pathname === `/api/rule/definition/${rule.id}/api-scenarios` && response.request().method() === 'POST')
    await prompt.getByRole('button', { name: '确定', exact: true }).click()
    const saved = await saveResponse
    expect((await saved.json()).code).toBe(200)
    created = true
    expect(JSON.parse(saved.request().postDataJSON().requestJson).params).toEqual(executedParams)
    const fixedCase = page.locator('.fixed-case-row').filter({ hasText: caseName })
    await expect(fixedCase).toBeVisible()

    await snapshotRow.locator('.param-value input').fill('changed')
    await page.getByRole('button', { name: '保存为新用例', exact: true }).click()
    await expect(page.getByText('输入参数已修改，请重新执行后再收藏输入与结果', { exact: true })).toBeVisible()
    await expect(page.locator('.el-message-box')).not.toBeVisible()
    await page.getByRole('button', { name: '手动添加', exact: true }).click()
    const temporaryRow = page.locator('.param-row').last()
    await temporaryRow.locator('.param-key input').fill('uiTemporaryField')
    await temporaryRow.locator('.param-value input').fill('remove-me')
    await fixedCase.getByRole('button', { name: '载入', exact: true }).click()
    await expect(page.locator('.param-row')).toHaveCount(savedParamCount)
    const loadedKeys = await page.locator('.param-key input').evaluateAll(inputs => inputs.map(input => input.value))
    expect(loadedKeys).not.toContain('uiTemporaryField')
    const markerIndex = loadedKeys.indexOf('uiSnapshotMarker')
    expect(markerIndex).toBeGreaterThanOrEqual(0)
    await expect(page.locator('.param-row').nth(markerIndex).locator('.param-value input')).toHaveValue('original')
    expect(pageErrors).toEqual([])
  } finally {
    if (created) {
      await page.goto(`${baseUrl.replace(/\/$/, '')}/#/rule/${rule.id}`)
      await page.getByRole('tab', { name: 'API 测试用例', exact: true }).click()
      const row = page.locator('.api-scenario-panel').getByRole('row').filter({ hasText: caseName })
      await expect(row).toBeVisible()
      await row.getByRole('button', { name: '删除', exact: true }).click()
      const confirmation = page.locator('.el-message-box')
      await expect(confirmation.getByText('删除确认', { exact: true })).toBeVisible()
      await confirmation.getByRole('button', { name: '确定', exact: true }).click()
      await expect(row).toHaveCount(0)
    }
  }
})
