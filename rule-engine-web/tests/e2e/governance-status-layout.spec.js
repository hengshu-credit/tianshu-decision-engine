const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDocsApiData } = require('./support/docsFixtures.cjs')

const pages = [
  ['/project'], ['/rule'], ['/variable'], ['/variable', '数据对象'],
  ['/variable', '常量列表'], ['/variable', '字段校验'], ['/list'],
  ['/datasource'], ['/datasource?tab=api'], ['/database'], ['/model'],
  ['/function'], ['/experiment'], ['/billing'], ['/project/1', '项目规则'],
]

for (const scheme of ['LIGHT', 'DARK']) {
  test(`${scheme} 各模块只显示一个合并状态且单行不裁剪`, async ({ page }, testInfo) => {
    test.setTimeout(90000)
    await page.addInitScript(colorScheme => {
      localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({ schemaVersion: 1, colorScheme, accentPreset: 'THEME_BLUE', sidebarTheme: 'DARK' }))
      sessionStorage.setItem('tianshu:layout:sidebar', JSON.stringify({ width: 64, lastExpandedWidth: 220 }))
    }, scheme)
    const data = createDocsApiData()
    data.set('/api/rule/governance/workspaces/states', ({ url }) => url.searchParams.get('ids').split(',').map(resourceId => ({ resourceId, effectiveStatus: 'ACTIVE', requestStatus: 'PENDING', action: 'UPDATE', owned: true })))
    const { assertClean } = await installDistRoutes(page, { apiData: data })
    await page.setViewportSize({ width: 1280, height: 900 })
    for (const [route, tab] of pages) {
      await page.goto('http://tianshu.local/index.html#' + route)
      await expect(page.locator(`.workspace-tab[data-tab="${route}"]`)).toHaveClass(/is-active/)
      if (tab) await page.getByRole('tab', { name: tab, exact: true }).click()
      const status = page.locator('.governance-resource-status:visible').first()
      await expect(status).toContainText('审批中')
      await expect(status.locator('.el-tag')).toHaveCount(1)
      for (const width of [1280, 560]) {
        await page.setViewportSize({ width, height: 900 })
        const layout = await status.evaluate(element => {
          const tags = [...element.querySelectorAll('.el-tag')].map(tag => tag.getBoundingClientRect())
          const cell = element.closest('.cell')
          const rect = cell.getBoundingClientRect()
          return {
            tagCount: tags.length,
            tagHeight: tags[0].height,
            clipped: cell.scrollWidth > cell.clientWidth + 1 || tags.some(tag => tag.left < rect.left || tag.right > rect.right + 1),
            whiteSpace: getComputedStyle(element).whiteSpace,
          }
        })
        expect(layout, `${route} ${tab || ''} ${width}`).toEqual({ tagCount: 1, tagHeight: 20, clipped: false, whiteSpace: 'nowrap' })
      }
      await page.setViewportSize({ width: 1280, height: 900 })
    }
    await page.screenshot({ path: testInfo.outputPath(`status-single-line-${scheme}.png`), fullPage: true })
    assertClean()
  })
}
