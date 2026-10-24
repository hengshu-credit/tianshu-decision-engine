const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDocsApiData } = require('./support/docsFixtures.cjs')

for (const colorScheme of ['LIGHT', 'DARK']) {
  test(`${colorScheme} 版本管理加载无灰色蒙版且刷新保留预览`, async ({ page }, testInfo) => {
    const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
    await page.addInitScript(scheme => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({
      schemaVersion: 1, colorScheme: scheme, accentPreset: 'LIQUID_PURPLE', sidebarTheme: 'DARK',
    })), colorScheme)
    const data = createDocsApiData()
    const project = data.get('/api/rule/project/list').records[0]
    let release
    let pending = new Promise(resolve => { release = resolve })
    const workspace = {
      latestVersionId: '21', drafts: [], actor: 'e2e',
      versions: [{ versionId: '21', version: 1, revision: 1, latest: true }],
      history: [{ id: '21', versionNo: 1, revisionNo: 1, snapshotJson: JSON.stringify({ projectName: '版本加载测试' }) }],
    }
    data.set(`/api/rule/resource-versions/PROJECT/${project.id}`, async () => { await pending; return workspace })
    data.set(`/api/rule/resource-versions/PROJECT/${project.id}/events`, [])
    const { assertClean } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: data })
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.goto(`${origin}/#/project`)
    await page.getByRole('button', { name: '版本', exact: true }).first().click()
    const drawer = page.getByRole('dialog', { name: '版本管理', exact: true })
    const mask = drawer.locator('.el-loading-mask')
    try {
      await expect(mask).toBeVisible()
      await expect(mask).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)')
      await expect(mask).toHaveCSS('opacity', '1')
      await expect(drawer.getByText('正在加载版本…', { exact: true })).toBeVisible()
      await expect(drawer.locator('.el-empty')).toHaveCount(0)
      await page.screenshot({ path: testInfo.outputPath(`version-loading-${colorScheme}.png`) })
      release()
      await expect(drawer.locator('.version-preview-summary')).toContainText('版本加载测试')
      await expect(mask).toBeHidden()
      pending = new Promise(resolve => { release = resolve })
      workspace.history[0].snapshotJson = JSON.stringify({ projectName: '刷新后的版本' })
      await drawer.getByRole('button', { name: '刷新', exact: true }).click()
      await expect(mask).toBeVisible()
      await expect(mask).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)')
      await expect(drawer.locator('.version-preview-summary')).toContainText('版本加载测试')
      release()
      await expect(drawer.locator('.version-preview-summary')).toContainText('刷新后的版本')
      await expect(mask).toBeHidden()
      assertClean()
    } finally { release() }
  })
}

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
