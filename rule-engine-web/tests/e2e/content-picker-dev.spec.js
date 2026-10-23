const { test, expect } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')

test('Vite 开发页面可打开当前请求内容选择器，无页面或控制台错误', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL
  test.skip(!origin, '仅在显式启动开发服务器后运行')
  const { assertClean } = await installDistRoutes(page, { origin, liveStatic: true, apiData: createDetailApiData() })
  await page.goto(`${origin}/#/datasource/api/22?projectId=1`)
  await page.getByRole('tab', { name: '请求字段', exact: true }).click()
  await expect(page.locator('.api-execution-editor:visible')).toBeVisible()
  await page.getByRole('tab', { name: '响应结构', exact: true }).click()
  await expect(page.locator('.api-execution-editor:visible')).toBeVisible()
  assertClean()
})
