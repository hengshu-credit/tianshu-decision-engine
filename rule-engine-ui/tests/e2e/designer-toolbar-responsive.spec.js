const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')

for (const designer of [
  { name: '决策树', path: '/designer/tree/102', toolbar: '.tree-toolbar' },
  { name: '决策流', path: '/designer/flow/103', toolbar: '.flow-toolbar' },
]) {
  test(`${designer.name}工具栏空间不足时按钮分组自动换行`, async ({ page }) => {
    const { assertClean } = await installDistRoutes(page, {
      apiData: createDesignerApiData(),
    })
    await page.setViewportSize({ width: 900, height: 720 })
    await page.goto(`http://tianshu.local/index.html#${designer.path}`)

    const toolbar = page.locator(designer.toolbar)
    await expect(toolbar).toBeVisible()
    const layout = await toolbar.locator('.toolbar-row-actions').evaluate(row => {
      const childTops = [...row.children].map(child => Math.round(child.getBoundingClientRect().top))
      return {
        rowHeight: row.getBoundingClientRect().height,
        lineCount: new Set(childTops).size,
        clientWidth: row.clientWidth,
        scrollWidth: row.scrollWidth,
        documentWidth: document.documentElement.scrollWidth,
        viewportWidth: window.innerWidth,
      }
    })

    expect(layout.lineCount).toBeGreaterThan(1)
    expect(layout.scrollWidth).toBeLessThanOrEqual(layout.clientWidth + 1)
    expect(layout.documentWidth).toBeLessThanOrEqual(layout.viewportWidth + 1)
    assertClean()
  })
}
