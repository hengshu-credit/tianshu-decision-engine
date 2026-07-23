const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createManagementApiData } = require('./support/managementFixtures.cjs')

const pages = [
  {
    name: '项目管理',
    path: '/project',
    rowText: 'e2e_project',
    createButton: '新建项目',
    dialogTitle: '新建项目',
    listApi: '/api/rule/project/list'
  },
  {
    name: '规则管理',
    path: '/rule',
    rowText: 'age_rule',
    createButton: '新建规则',
    dialogTitle: '新建规则',
    listApi: '/api/rule/definition/list'
  },
  {
    name: '变量管理',
    path: '/variable',
    rowText: 'age',
    createButton: '新建变量',
    dialogTitle: '新建变量',
    listApi: '/api/rule/variable/list',
    projectSelectIndex: 1
  },
  {
    name: '名单管理',
    path: '/list',
    rowText: 'mobile_black',
    createButton: '新建名单库',
    dialogTitle: '新建名单库',
    listApi: '/api/rule/list/library'
  },
  {
    name: '外数管理',
    path: '/datasource',
    rowText: 'credit_vendor',
    createButton: '新建数据源',
    dialogTitle: '新建外数数据源',
    createRoute: '/datasource/source/new',
    listApi: '/api/rule/datasource/list'
  },
  {
    name: '数据库管理',
    path: '/database',
    rowText: 'risk_mysql',
    createButton: '新建数据库',
    dialogTitle: '新建数据库数据源',
    createRoute: '/database/new',
    listApi: '/api/rule/database/list'
  },
  {
    name: '模型管理',
    path: '/model',
    rowText: 'credit_score',
    createButton: '上传模型',
    dialogTitle: '上传模型',
    listApi: '/api/rule/model/list'
  }
]

function visibleFilterArea(page) {
  return page.locator('.uiue-search-container:visible, .tab-filter-row:visible').first()
}

async function projectCodeInput(page, pageCase) {
  if (pageCase.projectSelectIndex != null) {
    return visibleFilterArea(page)
      .locator('.el-select input:visible')
      .nth(pageCase.projectSelectIndex)
  }
  return page
    .locator('.el-form-item')
    .filter({ hasText: '项目编码' })
    .locator('input:visible')
    .first()
}

async function expectTextSelectable(locator, expectedText) {
  const selection = await locator.evaluate(element => {
    const range = document.createRange()
    range.selectNodeContents(element)
    const rangeText = range.toString()
    const currentSelection = window.getSelection()
    currentSelection.removeAllRanges()
    currentSelection.addRange(range)
    return {
      text: currentSelection.toString(),
      rangeText,
      userSelect: window.getComputedStyle(element).userSelect,
      inertAncestor: element.closest('[inert]')?.outerHTML.slice(0, 300)
    }
  })
  expect(selection.text, JSON.stringify(selection)).toBe(expectedText)
  expect(selection.userSelect).not.toBe('none')
}

for (const pageCase of pages) {
  test(`${pageCase.name}的数据、筛选、复制和弹框均可用`, async ({ page }) => {
    const pageErrors = []
    const consoleErrors = []
    page.on('pageerror', error => pageErrors.push(error.message))
    page.on('console', message => {
      if (message.type() === 'error') consoleErrors.push(message.text())
    })

    await page.setViewportSize({ width: 1440, height: 900 })
    const { requests, assertClean } = await installDistRoutes(page, {
      apiData: createManagementApiData()
    })
    await page.goto(`http://tianshu.local/index.html#${pageCase.path}`)

    const rowText = page.getByText(pageCase.rowText, { exact: true }).first()
    await expect(rowText).toBeVisible()
    await expect(visibleFilterArea(page)).toBeVisible()
    await expect(page.getByRole('button', { name: pageCase.createButton })).toBeVisible()

    const viewportMetrics = await page.evaluate(() => ({
      viewportWidth: window.innerWidth,
      documentWidth: document.documentElement.scrollWidth,
      bodyWidth: document.body.scrollWidth
    }))
    expect(viewportMetrics.documentWidth).toBeLessThanOrEqual(viewportMetrics.viewportWidth + 1)
    expect(viewportMetrics.bodyWidth).toBeLessThanOrEqual(viewportMetrics.viewportWidth + 1)

    await expectTextSelectable(rowText, pageCase.rowText)

    const projectInput = await projectCodeInput(page, pageCase)
    await expect(projectInput).toBeVisible()
    await projectInput.fill('e2e_project')
    const requestCount = requests.filter(request =>
      new URL(request.url).pathname === pageCase.listApi
    ).length
    await visibleFilterArea(page).getByRole('button', { name: '查询' }).first().click()
    await expect.poll(() => requests.filter(request =>
      new URL(request.url).pathname === pageCase.listApi
    ).length).toBeGreaterThan(requestCount)
    const lastListRequest = requests
      .filter(request => new URL(request.url).pathname === pageCase.listApi)
      .at(-1)
    expect(new URL(lastListRequest.url).searchParams.get('projectCode')).toBe('e2e_project')

    await visibleFilterArea(page).getByRole('button', { name: '重置' }).first().click()
    await expect(projectInput).toHaveValue('')

    await page.getByRole('button', { name: pageCase.createButton }).click()
    if (pageCase.createRoute) {
      await expect(page).toHaveURL(new RegExp(`#${pageCase.createRoute}$`))
      await expect(
        page.getByText(pageCase.dialogTitle, { exact: true }).first()
      ).toBeVisible()
      await expect(page.getByRole('button', { name: '保存' })).toBeVisible()
      await expect(page.getByRole('button', { name: '返回' })).toBeVisible()
      const createPageMetrics = await page.evaluate(() => ({
        viewportWidth: window.innerWidth,
        documentWidth: document.documentElement.scrollWidth,
        bodyWidth: document.body.scrollWidth
      }))
      expect(createPageMetrics.documentWidth).toBeLessThanOrEqual(
        createPageMetrics.viewportWidth + 1
      )
      expect(createPageMetrics.bodyWidth).toBeLessThanOrEqual(
        createPageMetrics.viewportWidth + 1
      )
      await page.getByRole('button', { name: '返回' }).click()
      await expect(rowText).toBeVisible()
      expect(pageErrors).toEqual([])
      expect(consoleErrors).toEqual([])
      assertClean()
      return
    }
    const dialog = page.locator('.el-dialog:visible').last()
    await expect(dialog).toBeVisible()
    await expect(dialog.getByText(pageCase.dialogTitle, { exact: true }).first()).toBeVisible()
    const box = await dialog.boundingBox()
    const viewport = page.viewportSize()
    expect(box).not.toBeNull()
    expect(box.x).toBeGreaterThanOrEqual(0)
    expect(box.y).toBeGreaterThanOrEqual(0)
    expect(box.x + box.width).toBeLessThanOrEqual(viewport.width + 1)
    expect(box.y + box.height).toBeLessThanOrEqual(viewport.height + 1)

    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
    expect(pageErrors).toEqual([])
    expect(consoleErrors).toEqual([])
    assertClean()
  })
}
