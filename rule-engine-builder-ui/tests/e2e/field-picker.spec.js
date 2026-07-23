const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')

test('决策表字段选择器可加载并选择普通变量和对象字段', async ({ page }) => {
  const pageErrors = []
  const consoleErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })

  const { requests, assertClean } = await installDistRoutes(page, {
    apiData: createDesignerApiData()
  })
  await page.goto('http://tianshu.local/index.html#/designer/table/101')

  await expect(page.locator('.dt-var-status')).toContainText('已加载 5 个变量/常量/对象字段')
  await page.getByRole('button', { name: '添加行' }).click()

  const targetField = page.getByPlaceholder('选择目标字段')
  await expect(targetField).toBeVisible()
  await targetField.click()

  const targetReference = targetField.locator('xpath=ancestor::div[contains(@class, "vp-reference")]')
  const popoverId = await targetReference.getAttribute('aria-describedby')
  expect(popoverId).toBeTruthy()
  const popover = page.locator(`[id="${popoverId}"]`)
  await expect(popover).toBeVisible()
  await popover.locator('.vp-cat-item').filter({ hasText: '普通变量' }).click()
  await popover.locator('.vp-row').filter({ hasText: 'age' }).click()
  await expect(targetField).toHaveValue(/age/)
  await expect(popover).toBeHidden()

  await targetField.click()
  await expect(popover).toBeVisible()
  await popover.locator('.vp-cat-item').filter({ hasText: '数据对象' }).click()
  await popover.locator('.vp-row').filter({ hasText: 'TaxRequest' }).click()
  await popover.locator('.vp-child-item').filter({ hasText: 'amount' }).click()
  await expect(targetField).toHaveValue(/TaxRequest\.amount/)
  await expect(popover).toBeHidden()

  await targetField.click()
  await expect(popover).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(popover).toBeHidden()

  const requestedPaths = requests.map(request => new URL(request.url).pathname)
  expect(requestedPaths).toEqual(expect.arrayContaining([
    '/api/rule/definition/101',
    '/api/rule/definition/content/101',
    '/api/rule/variable/project/1',
    '/api/rule/dataobject/tree/1',
    '/api/rule/function/project/1/all',
    '/api/rule/model/project/1/all',
    '/api/rule/list/library'
  ]))
  expect(pageErrors).toEqual([])
  expect(consoleErrors).toEqual([])
  assertClean()
})
