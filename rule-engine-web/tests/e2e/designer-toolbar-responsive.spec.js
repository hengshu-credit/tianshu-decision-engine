const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { DEFAULT_THEME_CONFIG } = require('../../src/theme/themeConfig.js')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')

for (const designer of [
  { name: '决策树', path: '/designer/tree/102', toolbar: '.tree-toolbar' },
  { name: '决策流', path: '/designer/flow/103', toolbar: '.flow-toolbar' },
]) {
  test(`${designer.name}工具栏空间不足时按钮分组自动换行`, async ({ page }) => {
    await page.addInitScript(config => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify(config)), {
      ...DEFAULT_THEME_CONFIG, accentMode: 'CUSTOM_SOLID', customSolidColor: '#563BFA',
    })
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
    await page.setViewportSize({ width: 1920, height: 1080 })
    const row = toolbar.locator('.toolbar-row-actions')
    await expect(toolbar.getByText('添加节点：', { exact: true })).toHaveCount(0)
    const publish = row.getByRole('button', { name: '发布', exact: true })
    const foreground = await toolbar.evaluate(el => getComputedStyle(el).color)
    await expect(publish).toHaveCSS('border-top-width', '2px')
    await expect(publish).toHaveCSS('border-top-color', foreground)
    await publish.hover()
    await expect(publish).toHaveCSS('border-top-color', foreground)
    const controls = row.locator('button, .zoom-input')
    const centers = await controls.evaluateAll(elements => elements.map(el => {
      const box = el.getBoundingClientRect()
      return box.top + box.height / 2
    }))
    expect(Math.max(...centers) - Math.min(...centers)).toBeLessThan(2)
    const bounds = await row.boundingBox()
    const edit = await row.locator('.toolbar-edit-actions button').first().boundingBox()
    const nodes = await row.locator('.toolbar-node-actions').boundingBox()
    const actions = await row.locator('.toolbar-right').boundingBox()
    expect(Math.abs(edit.x - bounds.x)).toBeLessThan(2)
    expect(Math.abs(nodes.x + nodes.width / 2 - (bounds.x + bounds.width / 2))).toBeLessThan(2)
    expect(nodes.x + nodes.width).toBeLessThan(actions.x)
    expect(Math.abs(actions.x + actions.width - (bounds.x + bounds.width))).toBeLessThan(2)
    assertClean()
  })
}
