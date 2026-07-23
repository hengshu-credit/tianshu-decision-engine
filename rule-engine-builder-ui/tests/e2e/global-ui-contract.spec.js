const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')

test('全局字体、业务文本选择和关键按钮语义可用', async ({ page }) => {
  const fontResponses = []
  page.on('response', response => {
    if (new URL(response.url()).pathname === '/fonts/font.ttf') {
      fontResponses.push(response.status())
    }
  })

  const { assertClean } = await installDistRoutes(page)
  await page.goto('http://tianshu.local/index.html#/project')

  await expect(page.getByText('E2E 项目', { exact: true })).toBeVisible()
  await page.evaluate(() => document.fonts.load('16px "AlimamaFangYuanTiVF"'))
  expect(fontResponses).toContain(200)
  expect(
    await page.evaluate(() =>
      document.fonts.check('16px "AlimamaFangYuanTiVF"')
    )
  ).toBe(true)

  const projectCode = page.getByText('e2e_project', { exact: true })
  const selection = await projectCode.evaluate(element => {
    const range = document.createRange()
    range.selectNodeContents(element)
    const currentSelection = window.getSelection()
    currentSelection.removeAllRanges()
    currentSelection.addRange(range)
    return {
      text: currentSelection.toString(),
      userSelect: window.getComputedStyle(element).userSelect,
    }
  })
  expect(selection.text).toBe('e2e_project')
  expect(selection.userSelect).not.toBe('none')

  await expect(page.getByRole('button', { name: '查询' })).toBeVisible()
  await expect(page.getByRole('button', { name: '重置' })).toBeVisible()
  await expect(page.getByRole('button', { name: '新建项目' })).toBeVisible()
  assertClean()
})
