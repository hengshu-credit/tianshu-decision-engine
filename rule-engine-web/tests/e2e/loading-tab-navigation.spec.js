const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')

const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
const designers = ['table', 'tree', 'flow', 'ruleset', 'cross', 'score', 'cross-adv', 'score-adv', 'script']

for (const entry of [...designers.map((kind, index) => ({ name: kind, id: 101 + index, path: `/designer/${kind}/${101 + index}`, button: '查看' })),
  { name: '规则详情', id: 101, path: '/rule/101', button: '详情' }]) {
  test(`${entry.name} 数据未返回时可以点击顶部页签切回规则列表`, async ({ page }, testInfo) => {
    const apiData = entry.button === '详情' ? createDetailApiData() : new Map([...createDetailApiData(), ...createDesignerApiData()])
    const definition = apiData.get(`/api/rule/definition/${entry.id}`)
    apiData.set('/api/rule/definition/list', { records: [definition], total: 1 })
    const endpoint = entry.button === '查看' ? `/api/rule/definition/${entry.id}/revisions` : `/api/rule/definition/detail/${entry.id}`
    const response = apiData.get(endpoint)
    let release, requested = false, returned = false
    const pending = new Promise(resolve => { release = resolve })
    apiData.set(endpoint, async () => { requested = true; await pending; returned = true; return response })
    const { assertClean } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData })
    try {
      await page.goto(`${origin}/#/rule`)
      await page.getByRole('row').filter({ has: page.getByRole('cell', { name: definition.ruleCode, exact: true }) }).getByRole('button', { name: entry.button, exact: true }).click()
      await expect(page).toHaveURL(`${origin}/#${entry.path}`)
      await expect.poll(() => requested).toBe(true)
      await expect(page.locator('.layout-main .el-loading-mask, .layout-main .graph-loading-mask')).toHaveCount(0)
      const tab = page.locator('.workspace-tab__main[data-path="/rule"]')
      expect(await tab.evaluate(element => Boolean(element.closest('[inert]')))).toBe(false)
      await tab.click({ timeout: 1500 })
      await expect(page).toHaveURL(`${origin}/#/rule`, { timeout: 1500 })
      await expect(page.locator('.workspace-tab.is-active')).toContainText('规则管理')
      expect(returned).toBe(false)
      if (entry.name === 'table' || entry.name === '规则详情') {
        await page.screenshot({ path: testInfo.outputPath('switched-before-data-ready.png') })
      }
      release()
      await expect.poll(() => returned).toBe(true)
      await expect(page).toHaveURL(`${origin}/#/rule`)
      assertClean()
    } finally { release() }
  })
}

test('决策表加载引用目录时不创建未打开的字段面板，打开选择器后仍可选择字段', async ({ page }) => {
  const apiData = createDesignerApiData()
  const { assertClean } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData })
  await page.goto(`${origin}/#/designer/table/101`)
  await expect(page.getByRole('button', { name: '添加行', exact: true })).toBeEnabled()
  await expect(page.locator('.fp-panel')).toHaveCount(0)
  await page.getByRole('button', { name: '添加行', exact: true }).click()
  const input = page.getByRole('textbox', { name: '打开内容选择器' }).first()
  await input.click()
  await expect(page.getByRole('dialog', { name: '内容选择器', exact: true })).toBeVisible()
  await expect(page.locator('.fp-panel')).toHaveCount(1)
  await page.keyboard.press('Escape')
  await expect(page.locator('.fp-panel')).toBeHidden()
  assertClean()
})
