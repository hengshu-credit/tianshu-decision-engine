const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')

test('离线迁移页面展示导出、目标范围和冲突策略流程', async ({ page }) => {
  const { assertClean } = await installDistRoutes(page)
  await page.goto('http://tianshu.local/index.html#/transfer')

  await expect(page.getByRole('heading', { name: '离线配置迁移' })).toBeVisible()
  await expect(page.getByText('配置包即时生成')).toBeVisible()
  await expect(page.getByRole('button', { name: '生成并下载配置包' })).toBeVisible()
  await expect(page.getByText('名单记录、日志和账单不会进入配置包。')).toBeVisible()

  await page.getByPlaceholder('资源 ID').fill('101')
  await page.getByText('导入时新建项目', { exact: true }).click()
  await expect(page.getByPlaceholder('新项目编码（可覆盖源编码）')).toBeVisible()
  await expect(page.getByPlaceholder('新项目名称（可覆盖源名称）')).toBeVisible()
  await expect(page.getByRole('button', { name: '预览冲突' })).toBeDisabled()
  await expect(page.getByRole('button', { name: '确认导入' })).toBeDisabled()
  await expect(page.locator('.menu-label', { hasText: '离线迁移' })).toBeVisible()
  await assertClean()
})
