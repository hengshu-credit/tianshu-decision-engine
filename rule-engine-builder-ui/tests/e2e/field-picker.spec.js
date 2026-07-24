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
  const popoverId = await targetField.getAttribute('aria-describedby')
  expect(popoverId).toBeTruthy()
  const popover = page.locator(`[id="${popoverId}"]`)
  await expect(popover).toBeVisible()
  await popover.locator('.vp-cat-item').filter({ hasText: '普通变量' }).click()
  await popover.locator('.vp-row').filter({ hasText: 'age' }).click()
  await expect(targetField).toHaveValue(/age/)
  await expect(popover).toBeHidden()
  const typeToValueGap = await targetReference.evaluate(reference => {
    const type = reference.querySelector('.vp-operand-kind')
    const input = reference.querySelector('input')
    const wrapper = reference.querySelector('.el-input__wrapper')
    const typeRect = type.getBoundingClientRect()
    const inputRect = input.getBoundingClientRect()
    const inputStyle = getComputedStyle(input)
    const wrapperStyle = getComputedStyle(wrapper)
    return {
      gap: inputRect.left + parseFloat(inputStyle.paddingLeft || '0') - typeRect.right,
      paddingLeft: parseFloat(wrapperStyle.paddingLeft || '0'),
      paddingRight: parseFloat(wrapperStyle.paddingRight || '0'),
      suffixIcons: reference.querySelectorAll('.el-input__suffix .el-icon').length
    }
  })
  expect(typeToValueGap.gap).toBeGreaterThanOrEqual(0)
  expect(typeToValueGap.gap).toBeLessThanOrEqual(8)
  expect(typeToValueGap).toMatchObject({
    paddingLeft: 4,
    paddingRight: 4,
    suffixIcons: 1
  })

  const operatorWrapper = page.locator('.cg-field--op .el-select__wrapper').first()
  await expect(operatorWrapper).toBeVisible()
  const operatorSpacing = await operatorWrapper.evaluate(element => {
    const style = getComputedStyle(element)
    return {
      gap: parseFloat(style.gap || '0'),
      paddingLeft: parseFloat(style.paddingLeft || '0'),
      paddingRight: parseFloat(style.paddingRight || '0')
    }
  })
  expect(operatorSpacing).toEqual({
    gap: 4,
    paddingLeft: 8,
    paddingRight: 8
  })

  const leftField = page.getByPlaceholder('选择左操作数...')
  await leftField.click()
  const leftPopoverId = await leftField.getAttribute('aria-describedby')
  expect(leftPopoverId).toBeTruthy()
  const leftPopover = page.locator(`[id="${leftPopoverId}"]`)
  await leftPopover.locator('.vp-cat-item').filter({ hasText: '普通变量' }).click()
  await leftPopover.locator('.vp-row').filter({ hasText: 'income' }).click()
  await expect(leftField).toHaveValue(/income/)
  const compactFieldMetrics = await leftField.evaluate(input => ({
    clientWidth: input.clientWidth,
    scrollWidth: input.scrollWidth
  }))
  expect(compactFieldMetrics.scrollWidth).toBeLessThanOrEqual(
    compactFieldMetrics.clientWidth
  )

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
