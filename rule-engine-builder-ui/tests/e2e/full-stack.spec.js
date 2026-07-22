const { expect, test } = require('@playwright/test')

const baseUrl = process.env.E2E_BASE_URL

test('真实部署核心页面可访问', async ({ page }) => {
  test.skip(!baseUrl, '设置 E2E_BASE_URL 后执行真实前后端联调')

  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))

  await page.goto(`${baseUrl.replace(/\/$/, '')}/#/project`)
  if (page.url().includes('#/login')) {
    const username = process.env.E2E_USERNAME
    const password = process.env.E2E_PASSWORD
    if (!username || !password) {
      throw new Error('控制台启用登录时必须设置 E2E_USERNAME 和 E2E_PASSWORD')
    }
    await page.locator('input[autocomplete="username"]').fill(username)
    await page.locator('input[autocomplete="current-password"]').fill(password)
    await page.locator('button[type="submit"]').click()
  }

  await expect(page.locator('.layout-sidebar')).toBeVisible()
  await expect(page.locator('[data-menu-path="/project"]')).toHaveClass(/is-active/)
  expect(pageErrors).toEqual([])
})
