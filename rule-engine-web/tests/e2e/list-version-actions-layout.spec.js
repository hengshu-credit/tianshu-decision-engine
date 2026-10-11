const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDocsApiData } = require('./support/docsFixtures.cjs')

for (const colorScheme of ['LIGHT', 'DARK']) {
  test(`${colorScheme} 版本入口与发布同时出现时操作列单行且完整可见`, async ({ page }) => {
    test.setTimeout(60000)
    await page.addInitScript(scheme => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({
      schemaVersion: 1, colorScheme: scheme, accentPreset: 'LIQUID_PURPLE', sidebarTheme: 'DARK', contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false,
    })), colorScheme)
    const data = createDocsApiData()
    data.set('/api/rule/governance/workspaces/states', ({ url }) => url.searchParams.get('ids').split(',').map(resourceId => ({ resourceId, effectiveStatus: 'ACTIVE', requestStatus: 'EDITING', action: 'UPDATE', owned: true })))
    const { assertClean } = await installDistRoutes(page, { apiData: data })
    const failures = []
    await page.setViewportSize({ width: 1280, height: 900 })
    for (const [route, tab] of [
      ['/project'], ['/rule'], ['/variable', '变量列表'], ['/variable', '数据对象'], ['/variable', '常量列表'],
      ['/list'], ['/datasource'], ['/datasource?tab=api'], ['/database'], ['/model'], ['/function'], ['/experiment'], ['/billing'],
    ]) {
      await page.goto('http://tianshu.local/index.html#' + route)
      if (tab) await page.getByRole('tab', { name: tab, exact: true }).click()
      const table = page.locator('.el-table:visible').first()
      await expect(table.getByRole('button', { name: '发布', exact: true }).first()).toBeVisible()
      const problems = await table.evaluate(element => {
        const issues = []
        for (const cell of element.querySelectorAll('td.table-operation-column')) {
          const content = cell.querySelector('.cell')
          if (content.scrollWidth > content.clientWidth + 1) issues.push('操作内容裁剪')
          const buttons = [...cell.querySelectorAll('button')].filter(button => button.getBoundingClientRect().width)
          const bounds = cell.getBoundingClientRect()
          const top = buttons[0]?.getBoundingClientRect().top
          for (const button of buttons) {
            const rect = button.getBoundingClientRect()
            if (Math.abs(rect.top - top) > 1 || rect.left < bounds.left || rect.right > bounds.right) issues.push(button.textContent.trim() + '换行或被裁剪')
          }
        }
        return issues
      })
      const actionTexts = (await table.locator('.el-table__body tr').first().locator('.table-operation-column button:visible').allTextContents()).map(text => text.trim())
      const versionIndex = actionTexts.indexOf('版本')
      if (versionIndex >= 0) {
        for (const before of ['进入', '详情', '查看', '编辑', '查询', '测试', '验证执行']) {
          const index = actionTexts.indexOf(before)
          if (index >= 0) expect(index, `${route} 的版本按钮应排在${before}之后`).toBeLessThan(versionIndex)
        }
        for (const after of ['发布', '加接口', '添加字段', '转为全局', '枚举', '鉴权', 'API', '删除']) {
          const index = actionTexts.indexOf(after)
          if (index >= 0) expect(versionIndex, `${route} 的版本按钮应排在${after}之前`).toBeLessThan(index)
        }
      }
      if (problems.length) failures.push({ route, tab, problems })
    }
    expect(failures).toEqual([])
    assertClean()
  })
}
