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
      button: '新建字段',
      dialogTitle: '新建字段'
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
      dialogTitle: '新建字段校验审批'
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

test('变量新建和编辑弹窗的高级设置居中且数字输入框无加减控件', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  const apiData = createManagementApiData()
  apiData.set('/api/rule/variable/source-options', {
    apiOptions: [{ id: 22, name: '征信查询', code: 'credit_query' }],
    dbOptions: [],
    listOptions: []
  })
  apiData.set('/api/rule/datasource/api-config/22/binding-contract', {
    requestFields: [
      {
        id: 'body.userId',
        location: 'body',
        path: 'userId',
        value: { kind: 'LITERAL', value: 'u-1', label: 'u-1' },
        overridable: true
      }
    ],
    resultFields: [{ value: 'body.risk', label: '风险结果' }]
  })
  apiData.set('/api/rule/project/1', {
    id: 1,
    projectCode: 'e2e_project',
    projectName: 'E2E 项目',
    status: 1
  })
  apiData.set('/api/rule/dataobject/tree/1', { tree: [] })
  apiData.set('/api/rule/function/project/1/all', [])
  apiData.set('/api/rule/model/project/1/all', [])
  const { assertClean } = await installDistRoutes(page, {
    apiData
  })
  await page.goto('http://tianshu.local/index.html#/variable?projectId=1')

  for (const action of ['新建字段', '编辑']) {
    await page.getByRole('button', { name: action, exact: true }).first().click()
    const dialog = page.locator('.el-dialog:visible').last()
    const form = dialog.locator('form').first()
    const collapse = dialog.locator('.variable-advanced-collapse')
    await expect(collapse).toBeVisible()

    const gaps = await Promise.all([
      form.boundingBox(),
      collapse.boundingBox(),
    ]).then(([formBox, collapseBox]) => ({
      left: collapseBox.x - formBox.x,
      right: formBox.x + formBox.width - collapseBox.x - collapseBox.width,
    }))
    expect(Math.abs(gaps.left - gaps.right)).toBeLessThanOrEqual(1)

    await collapse.locator('.el-collapse-item__header').click()
    await expect(collapse.locator('.el-input-number').first()).toBeVisible()
    await expect(collapse.locator('.el-input-number__decrease:visible')).toHaveCount(0)
    await expect(collapse.locator('.el-input-number__increase:visible')).toHaveCount(0)

    await dialog.getByRole('button', { name: /外数接口/ }).click()
    await dialog.locator('.el-form-item').filter({ hasText: '接口配置' }).first().getByRole('combobox').click()
    await page.locator('.el-select-dropdown:visible').getByText('征信查询 / credit_query', { exact: true }).click()
    const binding = dialog.locator('.api-source-binding')
    await expect(binding).toBeVisible()
    const [bindingBox, tableBox, formBox, previewBox, advancedBox] = await Promise.all([
      binding.boundingBox(),
      binding.locator('.api-binding-table-wrap').boundingBox(),
      form.boundingBox(),
      dialog.locator('.draft-preview-panel').boundingBox(),
      collapse.boundingBox()
    ])
    expect(bindingBox.width).toBeGreaterThan(400)
    expect(tableBox.width).toBeGreaterThan(400)
    expect(Math.abs(formBox.width - previewBox.width)).toBeLessThanOrEqual(2)
    expect(previewBox.y).toBeGreaterThan(advancedBox.y + advancedBox.height)

    const positions = await form.locator('.el-form-item').evaluateAll(items => items.map(item => ({
      label: item.querySelector('.el-form-item__label')?.textContent?.trim(),
      top: item.getBoundingClientRect().top
    })))
    const defaultTop = positions.find(item => item.label === '默认值')?.top
    const sourceTop = positions.find(item => item.label === '取值方式')?.top
    expect(defaultTop).toBeLessThan(sourceTop)

    await dialog.locator('.el-dialog__headerbtn').click()
    await expect(dialog).toBeHidden()
  }

  assertClean()
})

test('外数的数据源、API、调用日志和质量看板均正常', async ({ page }) => {
  const pageErrors = []
  const statsRequests = []
  const logRequests = []
  page.on('request', request => {
    const url = new URL(request.url())
    if (url.pathname === '/api/rule/runtime-log/external-api-stats') statsRequests.push(url)
    if (url.pathname === '/api/rule/runtime-log/list') logRequests.push(url)
  })
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
  expect(statsRequests).toHaveLength(0)
  expect(logRequests).toHaveLength(0)

  const monitorPane = await activateTab(page, '监控看板')
  await expect(monitorPane.getByText('外数供应商质量看板', { exact: true })).toBeVisible()
  await expect(monitorPane.locator('.datasource-stat-cell')).toHaveCount(8)
  await expect(monitorPane.getByText('credit_query', { exact: true })).toBeVisible()
  await expect(monitorPane.locator('.module-call-log')).toHaveCount(0)
  expect(statsRequests).toHaveLength(1)
  expect(logRequests).toHaveLength(0)
  await monitorPane.getByRole('button', { name: '刷新指标', exact: true }).click()
  await expect.poll(() => statsRequests.length).toBe(2)

  const logPane = await activateTab(page, '调用日志')
  await expect(logPane.getByText('外数调用日志', { exact: true })).toBeVisible()
  await expect(logPane.getByText('credit_query', { exact: true }).first()).toBeVisible()
  await expect(logPane.locator('.datasource-stat-cell')).toHaveCount(0)
  expect(logRequests).toHaveLength(1)
  await logPane.getByRole('button', { name: '刷新', exact: true }).click()
  await expect.poll(() => logRequests.length).toBe(2)
  expect(statsRequests).toHaveLength(2)
  await logPane.getByRole('textbox', { name: 'Trace ID', exact: true }).fill('trace_datasource')
  await logPane.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => logRequests.at(-1)?.searchParams.get('traceId')).toBe('trace_datasource')
  await activateTab(page, '监控看板')
  await activateTab(page, '调用日志')
  await expect(logPane.getByRole('textbox', { name: 'Trace ID', exact: true })).toHaveValue('trace_datasource')
  await logPane.getByRole('button', { name: '详情', exact: true }).click()
  const apiLogDialog = page.getByRole('dialog', { name: 'API外数调用详情' })
  await expect(apiLogDialog).toBeVisible()
  await expect(apiLogDialog.getByText('外数调用链', { exact: true })).toBeVisible()
  await expect(apiLogDialog.getByText('规则入参', { exact: true })).toBeVisible()
  await expect(apiLogDialog.getByText('鉴权（已脱敏）', { exact: true })).toBeVisible()
  await expect(apiLogDialog.getByText('外数结果赋值到引擎变量/对象', { exact: true })).toBeVisible()
  await expect(apiLogDialog.getByText('分析请求报文（按配置留存）', { exact: true })).toBeVisible()
  await page.keyboard.press('Escape')
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
