const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')

test('表达式编辑器从规则设计器打开后可配置、测试、暂存和编译', async ({ page }) => {
  const pageErrors = []
  const consoleErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  await page.setViewportSize({ width: 1440, height: 900 })
  const { requests, assertClean } = await installDistRoutes(page, {
    apiData: createDesignerApiData()
  })

  await page.goto('http://tianshu.local/index.html#/designer/table/101')
  await page.getByRole('button', { name: '添加行', exact: true }).click()
  await page.getByRole('button', { name: '配置组合表达式' }).first().click()

  await expect(page).toHaveURL(/#\/designer\/expression\/101\/expression-101-operand-picker-\d+$/)
  const main = page.getByRole('main')
  await expect(main.getByText('决策表 · 左操作数', { exact: true }).first()).toBeVisible()
  await expect(main.getByText('age', { exact: true })).toBeVisible()

  const metrics = await page.evaluate(() => ({
    viewport: window.innerWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
    userSelect: getComputedStyle(document.querySelector('main')).userSelect
  }))
  expect(metrics.document).toBeLessThanOrEqual(metrics.viewport + 1)
  expect(metrics.body).toBeLessThanOrEqual(metrics.viewport + 1)
  expect(metrics.userSelect).not.toBe('none')

  await main.getByText('age', { exact: true }).click()
  await expect(main.getByText('年龄 age', { exact: false }).first()).toBeVisible()

  await main.getByRole('button', { name: '测试', exact: true }).click()
  const testDialog = page.getByRole('dialog', { name: '测试当前表达式' })
  await expect(testDialog).toBeVisible()
  await expect(testDialog.getByText('年龄')).toBeVisible()
  await expect.poll(async () => {
    const dialogBox = await testDialog.boundingBox()
    return dialogBox &&
      dialogBox.x >= 0 &&
      dialogBox.y >= 0 &&
      dialogBox.x + dialogBox.width <= 1440 &&
      dialogBox.y + dialogBox.height <= 900
  }).toBe(true)
  await testDialog.getByRole('button', { name: '开始测试' }).click()
  await expect(testDialog.getByText('测试通过')).toBeVisible()
  await testDialog.getByRole('button', { name: '关闭', exact: true }).click()

  await main.getByRole('button', { name: '临时保存' }).click()
  await expect(main.getByText('草稿已临时保存')).toBeVisible()
  await main.getByRole('button', { name: '保存并编译' }).click()
  await expect(page).toHaveURL(/#\/designer\/table\/101$/)

  expect(requests.some(request => new URL(request.url).pathname === '/api/rule/expression/schema')).toBe(true)
  expect(requests.some(request => new URL(request.url).pathname === '/api/rule/expression/test')).toBe(true)
  expect(requests.some(request => new URL(request.url).pathname === '/api/rule/expression/compile')).toBe(true)
  expect(pageErrors).toEqual([])
  expect(consoleErrors).toEqual([])
  assertClean()
})
