const { test, expect } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')

for (const [route, id] of [['tree', 102], ['flow', 103]]) {
  test(`开发模式 ${route} 的 Dagre 按需加载和一键美化可用`, async ({ page }) => {
    const origin = process.env.E2E_DEV_BASE_URL
    test.skip(!origin, '需要显式启动开发服务器')
    const harness = await installDistRoutes(page, { origin, liveStatic: true, apiData: createDesignerApiData() })
    await page.goto(`${origin}/#/designer/${route}/${id}`)
    const nodes = page.locator(`.${route}-canvas .lf-node:not(.lf-mini-map .lf-node)`)
    await expect(nodes).toHaveCount(1)
    await page.getByRole('button', { name: '执行动作', exact: true }).click()
    await page.getByRole('button', { name: '执行动作', exact: true }).click()
    await expect(nodes).toHaveCount(3)
    const count = await nodes.count()
    await page.getByRole('button', { name: '一键美化', exact: true }).click()
    await expect(nodes).toHaveCount(count)
    await expect(page.getByText(/一键美化失败/)).toHaveCount(0)
    harness.assertClean()
  })
}
