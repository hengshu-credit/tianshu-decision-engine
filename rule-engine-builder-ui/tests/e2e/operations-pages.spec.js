const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createOperationsApiData } = require('./support/operationsFixtures.cjs')

async function expectNoRootOverflow(page) {
  const metrics = await page.evaluate(() => ({
    viewport: window.innerWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth
  }))
  expect(metrics.document).toBeLessThanOrEqual(metrics.viewport + 1)
  expect(metrics.body).toBeLessThanOrEqual(metrics.viewport + 1)
}

async function expectTextSelectable(locator, expectedText) {
  const selection = await locator.evaluate(element => {
    const range = document.createRange()
    range.selectNodeContents(element)
    const current = window.getSelection()
    current.removeAllRanges()
    current.addRange(range)
    return {
      text: current.toString(),
      userSelect: getComputedStyle(element).userSelect,
      inert: Boolean(element.closest('[inert]'))
    }
  })
  expect(selection).toEqual({
    text: expectedText,
    userSelect: selection.userSelect,
    inert: false
  })
  expect(selection.userSelect).not.toBe('none')
}

async function expectDialogInsideViewport(page, dialog) {
  const viewport = page.viewportSize()
  await expect(dialog).toBeVisible()
  await expect.poll(async () => {
    const box = await dialog.boundingBox()
    return box &&
      box.x >= 0 &&
      box.y >= 0 &&
      box.x + box.width <= viewport.width + 1 &&
      box.y + box.height <= viewport.height + 1
  }).toBe(true)
}

async function activateTab(page, name) {
  const tab = page.getByRole('tab', { name, exact: true })
  await tab.click()
  await expect(tab).toHaveAttribute('aria-selected', 'true')
  const pane = page.locator(`#${await tab.getAttribute('aria-controls')}`)
  await expect(pane).toBeVisible()
  await expect(pane).toHaveAttribute('aria-hidden', 'false')
  await expect(pane).not.toHaveAttribute('inert', '')
  return pane
}

test('函数管理的筛选、函数测试和新建弹窗可用', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1440, height: 900 })
  const { requests, assertClean } = await installDistRoutes(page, {
    apiData: createOperationsApiData()
  })
  await page.goto('http://tianshu.local/index.html#/function')

  const code = page.getByText('calcRisk', { exact: true }).first()
  await expect(code).toBeVisible()
  await expectTextSelectable(code, 'calcRisk')

  const codeInput = page.locator('.el-form-item')
    .filter({ hasText: '函数编码' })
    .locator('input:visible')
    .first()
  await codeInput.fill('calcRisk')
  const before = requests.filter(request =>
    new URL(request.url).pathname === '/api/rule/function/list'
  ).length
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => requests.filter(request =>
    new URL(request.url).pathname === '/api/rule/function/list'
  ).length).toBeGreaterThan(before)
  expect(new URL(requests.filter(request =>
    new URL(request.url).pathname === '/api/rule/function/list'
  ).at(-1).url).searchParams.get('funcCode')).toBe('calcRisk')

  const row = page.getByRole('row').filter({ hasText: 'calcRisk' })
  await row.getByRole('button', { name: '测试', exact: true }).click()
  const testDialog = page.getByRole('dialog', { name: '函数测试' })
  await expectDialogInsideViewport(page, testDialog)
  await testDialog.getByRole('button', { name: '执行测试' }).click()
  await expect(testDialog.getByText('20', { exact: true })).toBeVisible()
  await testDialog.getByRole('button', { name: '关闭', exact: true }).click()

  await page.getByRole('button', { name: '新建函数' }).click()
  const createDialog = page.getByRole('dialog', { name: '新建函数' })
  await expectDialogInsideViewport(page, createDialog)
  await page.keyboard.press('Escape')
  await expect(createDialog).toBeHidden()
  await expectNoRootOverflow(page)
  expect(pageErrors).toEqual([])
  assertClean()
})

test('规则测试可选择规则、加载字段并展示执行结果', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1440, height: 900 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createOperationsApiData()
  })
  await page.goto('http://tianshu.local/index.html#/test')

  const ruleSelect = page.locator('.test-left .el-form-item')
    .filter({ hasText: '规则' })
    .locator('.el-select')
  await ruleSelect.click()
  await page.getByRole('option', { name: /age_rule/ }).click()

  await expect(page.locator('.param-key input')).toHaveValue('age')
  await expect(page.getByText('(年龄)', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '执行测试' }).click()
  await expect(page.getByText('执行成功')).toBeVisible()
  const result = page.locator('.result-pre').filter({ hasText: 'approved' }).first()
  await expect(result).toBeVisible()
  await expectTextSelectable(result, await result.innerText())
  await expectNoRootOverflow(page)
  expect(pageErrors).toEqual([])
  assertClean()
})

test('血缘分析可搜索起点、生成关系图并复制节点内容', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1440, height: 900 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createOperationsApiData()
  })
  await page.goto('http://tianshu.local/index.html#/lineage')

  const startSelect = page.locator('.query-panel .el-form-item')
    .filter({ hasText: '起点' })
    .locator('.el-select')
  await startSelect.click()
  await page.getByRole('option', { name: '年龄 (age)' }).click()
  await page.getByRole('button', { name: '生成血缘图' }).click()

  const current = page.locator('.current-node .node-code')
  const downstream = page.locator('.branch-node .node-code')
  await expect(current).toHaveText('age')
  await expect(downstream).toHaveText('age_rule')
  await expectTextSelectable(downstream, 'age_rule')
  await expectNoRootOverflow(page)
  expect(pageErrors).toEqual([])
  assertClean()
})

test('分流实验列表、筛选与新建详情页可用', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1440, height: 900 })
  const { requests, assertClean } = await installDistRoutes(page, {
    apiData: createOperationsApiData()
  })
  await page.goto('http://tianshu.local/index.html#/experiment')

  const code = page.getByText('risk_ab', { exact: true })
  await expect(code).toBeVisible()
  await expectTextSelectable(code, 'risk_ab')
  const keyword = page.locator('.el-form-item')
    .filter({ hasText: '关键字' })
    .locator('input')
  await keyword.fill('risk')
  await page.getByRole('button', { name: '查询' }).click()
  expect(new URL(requests.filter(request =>
    new URL(request.url).pathname === '/api/rule/experiment/list'
  ).at(-1).url).searchParams.get('keyword')).toBe('risk')

  await page.getByRole('button', { name: '新建实验' }).click()
  await expect(page).toHaveURL(/#\/experiment\/new$/)
  const main = page.getByRole('main')
  await expect(main.getByText('新建分流实验', { exact: true }).first()).toBeVisible()
  await expect(main.getByRole('button', { name: '保存' })).toBeVisible()
  await expect(main.getByRole('button', { name: '返回' })).toBeVisible()
  await expectNoRootOverflow(page)
  await main.getByRole('button', { name: '返回' }).click()
  await expect(code).toBeVisible()
  expect(pageErrors).toEqual([])
  assertClean()
})

test('执行日志详情和规则集命中统计可用', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1440, height: 900 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createOperationsApiData()
  })
  await page.goto('http://tianshu.local/index.html#/log')

  const trace = page.getByText('trace_rule_001', { exact: true })
  await expect(trace).toBeVisible()
  await expectTextSelectable(trace, 'trace_rule_001')
  const row = page.getByRole('row').filter({ hasText: 'trace_rule_001' })
  await row.getByRole('button', { name: '详情' }).click()
  const drawer = page.getByRole('dialog', { name: '日志详情' })
  await expect(drawer).toBeVisible()
  await expect(drawer.getByText('trace_rule_001', { exact: true })).toBeVisible()
  await drawer.getByRole('button', { name: '关闭' }).click()
  await expect(drawer).toBeHidden()

  const statsTab = page.getByRole('tab', { name: '规则集命中统计' })
  await statsTab.click()
  await expect(statsTab).toHaveAttribute('aria-selected', 'true')
  const statsPane = page.locator('.rule-set-stats')
  await expect(statsPane.getByText('风险规则集', { exact: true })).toBeVisible()
  await expect(statsPane.getByText('80.00%', { exact: true }).first()).toBeVisible()
  await expectNoRootOverflow(page)
  expect(pageErrors).toEqual([])
  assertClean()
})

test('账单配置、明细、汇总及新建弹窗可用', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1280, height: 720 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createOperationsApiData()
  })
  await page.goto('http://tianshu.local/index.html#/billing')

  const configCode = page.getByText('engine_call', { exact: true }).first()
  await expect(configCode).toBeVisible()
  await expectTextSelectable(configCode, 'engine_call')
  await page.getByRole('button', { name: '新建计费项' }).click()
  const dialog = page.getByRole('dialog', { name: '新建计费项' })
  await expectDialogInsideViewport(page, dialog)
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()

  const recordPane = await activateTab(page, '计费明细')
  await expect(recordPane.getByText('age_rule', { exact: true })).toBeVisible()
  await expectTextSelectable(recordPane.getByText('TOKEN_E2E', { exact: true }), 'TOKEN_E2E')

  const summaryPane = await activateTab(page, '计费汇总')
  await expect(summaryPane.getByText('2026-07-23', { exact: true })).toBeVisible()
  await expect(summaryPane.getByText('CNY 0.1', { exact: true })).toBeVisible()
  await expectNoRootOverflow(page)
  expect(pageErrors).toEqual([])
  assertClean()
})
