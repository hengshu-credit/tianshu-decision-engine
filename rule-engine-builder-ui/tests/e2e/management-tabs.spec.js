const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createManagementApiData } = require('./support/managementFixtures.cjs')

async function activateTab(page, name) {
  const tab = page.getByRole('tab', { name, exact: true })
  await tab.click()
  await expect(tab).toHaveAttribute('aria-selected', 'true')
  const paneId = await tab.getAttribute('aria-controls')
  const pane = page.locator(`#${paneId}`)
  await expect(pane).toBeVisible()
  await expect(pane).toHaveAttribute('aria-hidden', 'false')
  await expect(pane).not.toHaveAttribute('inert', '')
  return pane
}

async function expectTextSelectable(locator, expectedText) {
  const selection = await locator.evaluate(element => {
    const range = document.createRange()
    range.selectNodeContents(element)
    const currentSelection = window.getSelection()
    currentSelection.removeAllRanges()
    currentSelection.addRange(range)
    return {
      text: currentSelection.toString(),
      inert: !!element.closest('[inert]')
    }
  })
  expect(selection).toEqual({ text: expectedText, inert: false })
}

async function expectNoRootOverflow(page) {
  const metrics = await page.evaluate(() => ({
    viewport: window.innerWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth
  }))
  expect(metrics.document).toBeLessThanOrEqual(metrics.viewport + 1)
  expect(metrics.body).toBeLessThanOrEqual(metrics.viewport + 1)
}

async function expectDialogUsable(page, buttonName, title) {
  await page.getByRole('button', { name: buttonName, exact: true }).click()
  const dialog = page.locator('.el-dialog:visible').last()
  await expect(dialog).toBeVisible()
  await expect(dialog.getByText(title, { exact: true }).first()).toBeVisible()
  const viewport = page.viewportSize()
  await expect.poll(async () => {
    const box = await dialog.boundingBox()
    return Boolean(
      box &&
        box.x >= 0 &&
        box.y >= 0 &&
        box.x + box.width <= viewport.width + 1 &&
        box.y + box.height <= viewport.height + 1
    )
  }).toBe(true)
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
}

test('变量管理四类业务页签的数据、复制、按钮和弹框均可用', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1280, height: 720 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createManagementApiData()
  })
  await page.goto('http://tianshu.local/index.html#/variable')

  const cases = [
    {
      tab: '变量列表',
      text: 'age',
      button: '新建变量',
      dialogTitle: '新建变量'
    },
    {
      tab: '数据对象',
      text: 'TaxRequest',
      button: '新建对象',
      dialogTitle: '新建数据对象'
    },
    {
      tab: '常量列表',
      text: 'MAX_AGE',
      button: '新建常量',
      dialogTitle: '新建常量'
    },
    {
      tab: '字段校验',
      text: 'mobile_required',
      button: '新建校验规则',
      dialogTitle: '新建字段校验'
    }
  ]

  for (const item of cases) {
    const pane = await activateTab(page, item.tab)
    const text = pane.getByText(item.text, { exact: true }).first()
    await expect(text).toBeVisible()
    await expectTextSelectable(text, item.text)
    await expectDialogUsable(page, item.button, item.dialogTitle)
    await expectNoRootOverflow(page)
  }

  expect(pageErrors).toEqual([])
  assertClean()
})

test('外数的数据源、API、调用日志和质量看板均正常', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1280, height: 720 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createManagementApiData()
  })
  await page.goto('http://tianshu.local/index.html#/datasource')

  const apiPane = await activateTab(page, 'API 接口')
  const apiCode = apiPane.getByText('credit_query', { exact: true }).first()
  await expect(apiCode).toBeVisible()
  await expectTextSelectable(apiCode, 'credit_query')
  await expect(page.getByRole('button', { name: '新建接口' })).toBeVisible()

  const logPane = await activateTab(page, '调用日志')
  await expect(logPane.getByText('外数调用日志', { exact: true })).toBeVisible()
  await expect(logPane.getByText('credit_query', { exact: true }).first()).toBeVisible()
  await expect(logPane.locator('.datasource-stat-cell')).toHaveCount(8)
  await expectNoRootOverflow(page)
  expect(pageErrors).toEqual([])
  assertClean()
})

for (const moduleCase of [
  {
    name: '数据库',
    path: '/database',
    tab: '调用日志',
    title: '数据库调用日志',
    rowText: 'risk_mysql'
  },
  {
    name: '模型',
    path: '/model',
    tab: '模型执行日志',
    title: '模型执行日志',
    rowText: 'credit_score'
  }
]) {
  test(`${moduleCase.name}调用日志页签可进入、可复制且不会失去交互`, async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    const { assertClean } = await installDistRoutes(page, {
      apiData: createManagementApiData()
    })
    await page.goto(`http://tianshu.local/index.html#${moduleCase.path}`)

    const pane = await activateTab(page, moduleCase.tab)
    await expect(pane.getByText(moduleCase.title, { exact: true }).first()).toBeVisible()
    const rowText = pane.getByText(moduleCase.rowText, { exact: true }).first()
    await expect(rowText).toBeVisible()
    await expectTextSelectable(rowText, moduleCase.rowText)
    await expectNoRootOverflow(page)
    assertClean()
  })
}
