const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')

async function openDetailPage(page, path) {
  const pageErrors = []
  const consoleErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  await page.setViewportSize({ width: 1440, height: 900 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createDetailApiData()
  })
  await page.goto(`http://tianshu.local/index.html#${path}`)
  await expect(page.getByRole('main')).toBeVisible()
  return { pageErrors, consoleErrors, assertClean }
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
  expect(selection.text).toBe(expectedText)
  expect(selection.userSelect).not.toBe('none')
  expect(selection.inert).toBe(false)
}

async function expectPrimaryActions(page, names) {
  for (const name of names) {
    const button = page.getByRole('main').getByRole('button', { name, exact: true }).first()
    await expect(button).toBeVisible()
    await expect(button).toBeInViewport()
  }
}

test('项目详情加载项目规则并保持内容可复制', async ({ page }) => {
  const errors = await openDetailPage(page, '/project/1')
  const title = page.getByText('E2E 项目', { exact: true }).first()
  const ruleCode = page.getByText('age_rule', { exact: true }).first()
  await expect(title).toBeVisible()
  await expect(ruleCode).toBeVisible()
  await expectTextSelectable(ruleCode, 'age_rule')
  await expectPrimaryActions(page, ['返回', '添加规则', '新建规则'])
  await expectNoRootOverflow(page)
  expect(errors.pageErrors).toEqual([])
  expect(errors.consoleErrors).toEqual([])
  errors.assertClean()
})

test('规则详情加载输入输出字段且页签可操作', async ({ page }) => {
  const errors = await openDetailPage(page, '/rule/101')
  await expect(page.getByText('age_rule', { exact: true }).first()).toBeVisible()
  const inputCode = page.getByText('age', { exact: true }).first()
  await expect(inputCode).toBeVisible()
  await expectTextSelectable(inputCode, 'age')

  const outputTab = page.getByRole('tab', { name: /输出字段/ })
  await outputTab.click()
  await expect(outputTab).toHaveAttribute('aria-selected', 'true')
  const outputCode = page.getByText('approved', { exact: true }).first()
  await expect(outputCode).toBeVisible()
  await expectTextSelectable(outputCode, 'approved')
  await expectPrimaryActions(page, ['返回'])
  await expectNoRootOverflow(page)
  expect(errors.pageErrors).toEqual([])
  expect(errors.consoleErrors).toEqual([])
  errors.assertClean()
})

test('名单详情加载记录和变更日志且内容可复制', async ({ page }) => {
  const errors = await openDetailPage(page, '/list/9')
  await expect(page.locator('.detail-meta')).toContainText('mobile_black')
  const mobile = page.getByText('13800138000', { exact: true }).first()
  await expect(mobile).toBeVisible()
  await expectTextSelectable(mobile, '13800138000')

  const logsTab = page.getByRole('tab', { name: '变更日志', exact: true })
  await logsTab.click()
  await expect(logsTab).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByText('INSERT', { exact: true }).first()).toBeVisible()
  await expectPrimaryActions(page, ['返回', '导入', '导出'])
  await expectNoRootOverflow(page)
  expect(errors.pageErrors).toEqual([])
  expect(errors.consoleErrors).toEqual([])
  errors.assertClean()
})

const editableDetails = [
  {
    name: '外数数据源详情',
    path: '/datasource/source/21',
    label: '数据源编码',
    value: 'credit_vendor'
  },
  {
    name: '外数接口详情',
    path: '/datasource/api/22',
    label: '接口编码',
    value: 'credit_query'
  },
  {
    name: '数据库详情',
    path: '/database/31',
    label: '数据源编码',
    value: 'risk_mysql'
  },
  {
    name: '分流实验详情',
    path: '/experiment/detail/61',
    label: '实验编码',
    value: 'risk_ab'
  }
]

for (const detail of editableDetails) {
  test(`${detail.name}加载表单且主操作可用`, async ({ page }) => {
    const errors = await openDetailPage(page, detail.path)
    const input = page.locator('.el-form-item:visible')
      .filter({ hasText: detail.label })
      .locator('input:visible')
      .first()
    await expect(input).toBeVisible()
    await expect(input).toHaveValue(detail.value)
    await expectPrimaryActions(page, ['返回', '保存'])
    await expectNoRootOverflow(page)
    expect(errors.pageErrors).toEqual([])
    expect(errors.consoleErrors).toEqual([])
    errors.assertClean()
  })
}

test('模型详情加载输入输出字段且字段内容可复制', async ({ page }) => {
  const errors = await openDetailPage(page, '/model/41')
  await expect(page.getByText('credit_score', { exact: true }).first()).toBeVisible()
  const inputCode = page.getByText('age', { exact: true }).first()
  await expect(inputCode).toBeVisible()
  await expectTextSelectable(inputCode, 'age')

  const outputTab = page.getByRole('tab', { name: /输出字段/ })
  await outputTab.click()
  await expect(outputTab).toHaveAttribute('aria-selected', 'true')
  const outputCode = page.getByText('riskScore', { exact: true }).first()
  await expect(outputCode).toBeVisible()
  await expectTextSelectable(outputCode, 'riskScore')
  await expectPrimaryActions(page, ['返回'])
  await expectNoRootOverflow(page)
  expect(errors.pageErrors).toEqual([])
  expect(errors.consoleErrors).toEqual([])
  errors.assertClean()
})

test('新建外数接口页面字段与主操作正常显示', async ({ page }) => {
  const errors = await openDetailPage(page, '/datasource/api/new')
  await expect(page.getByText('新建外数 API 接口', { exact: true }).first()).toBeVisible()
  await expectPrimaryActions(page, ['返回', '保存'])
  await expect(page.locator('.el-form-item:visible').first()).toBeVisible()
  await expectNoRootOverflow(page)
  expect(errors.pageErrors).toEqual([])
  expect(errors.consoleErrors).toEqual([])
  errors.assertClean()
})
