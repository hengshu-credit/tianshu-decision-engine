const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')

test('生产构建可加载并完成核心管理页面导航', async ({ page }) => {
  const pageErrors = []
  const consoleErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })

  await installDistRoutes(page)
  await page.goto('http://tianshu.local/index.html#/project')

  await expect(page.locator('.sidebar-brand')).toContainText('天枢决策引擎')
  await expect(page.locator('[data-menu-path="/project"]')).toHaveClass(/is-active/)
  await expect(page.getByText('E2E 项目', { exact: true })).toBeVisible()

  await page.locator('[data-menu-path="/rule"]').click()
  await expect(page).toHaveURL(/#\/rule$/)
  await expect(page.locator('[data-menu-path="/rule"]')).toHaveClass(/is-active/)

  await page.locator('[data-menu-path="/model"]').click()
  await expect(page).toHaveURL(/#\/model$/)
  await expect(page.locator('[data-menu-path="/model"]')).toHaveClass(/is-active/)
  await expect(page.getByText('模型管理', { exact: true }).last()).toBeVisible()

  expect(pageErrors).toEqual([])
  expect(consoleErrors).toEqual([])
})
