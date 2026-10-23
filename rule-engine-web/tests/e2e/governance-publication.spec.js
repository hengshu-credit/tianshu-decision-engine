const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDocsApiData } = require('./support/docsFixtures.cjs')
const { createManagementApiData } = require('./support/managementFixtures.cjs')

const modules = [['/project', 'PROJECT'], ['/rule', 'RULE'], ['/variable', 'FIELD'], ['/list', 'LIST_LIBRARY'], ['/datasource', 'EXTERNAL_DATASOURCE'], ['/datasource?tab=api', 'EXTERNAL_API'], ['/database', 'DATABASE'], ['/model', 'MODEL'], ['/function', 'FUNCTION'], ['/experiment', 'EXPERIMENT'], ['/billing', 'BILLING_CONFIG']]

test('发布只在保存了可提交草稿后出现，审批中及没有变更时隐藏', async ({ page }) => {
  const data = createManagementApiData()
  let phase = 'APPROVED', draft, preflightValid = false
  data.set('/api/rule/governance/workspaces/states', ({ url }) => url.searchParams.get('ids').split(',').map(resourceId => ({ resourceId, effectiveStatus: 'ACTIVE', requestStatus: phase, action: 'UPDATE', owned: true })))
  data.set('/api/rule/governance/workspaces/PROJECT/1', () => phase === 'EDITING' ? draft : null)
  data.set('POST /api/rule/governance/drafts', ({ request }) => {
    const payload = request.postDataJSON()
    expect(payload.resourceType).toBe('PROJECT')
    expect(String(payload.resourceId)).toBe('1')
    phase = 'EDITING'
    draft = { ...payload, id: 991, status: phase, draftSnapshotJson: payload.snapshotJson, snapshotDigest: 'saved-project' }
    return draft
  })
  data.set('POST /api/rule/governance/requests/991/submit', ({ request }) => {
    expect(request.postDataJSON().expectedSnapshotDigest).toBe('saved-project')
    phase = 'PENDING'
    return { ...draft, status: phase }
  })
  data.set('POST /api/rule/governance/requests/991/preflight', () => ({ valid: preflightValid, errors: preflightValid ? [] : [{ message: '依赖资源已停用，请先恢复来源', referencePath: '$.sourceId' }], warnings: [] }))
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/index.html#/project')
  const row = page.locator('.el-table__body tr').filter({ hasText: 'E2E 项目' }).first()
  await expect(row.locator('.governance-resource-status')).toHaveText('已发布')
  await expect(row.getByRole('button', { name: '发布', exact: true })).toHaveCount(0)
  await row.getByRole('button', { name: '编辑', exact: true }).click()
  const edit = page.getByRole('dialog', { name: '编辑项目', exact: true })
  await edit.locator('.el-form-item').filter({ hasText: '项目名称' }).locator('input').fill('待审批的新名称')
  await edit.getByRole('button', { name: '确定', exact: true }).click()
  await expect(edit).toBeHidden()
  await expect(row.locator('.governance-resource-status')).toHaveText('已发布·待更新')
  await row.getByRole('button', { name: '发布', exact: true }).click()
  const preflight = page.getByRole('dialog', { name: '发布前检查未通过', exact: true })
  await expect(preflight).toContainText('依赖资源已停用，请先恢复来源')
  await preflight.getByRole('button', { name: '关闭', exact: true }).click()
  preflightValid = true
  await row.getByRole('button', { name: '发布', exact: true }).click()
  await page.getByRole('dialog', { name: '提交发布审批', exact: true }).getByRole('button', { name: '确定', exact: true }).click()
  await expect(row.locator('.governance-resource-status')).toHaveText('已发布·审批中')
  await expect(row.getByRole('button', { name: '发布', exact: true })).toHaveCount(0)
  assertClean()
})

for (const colorScheme of ['LIGHT', 'DARK']) {
  test(`${colorScheme} 十个模块在工具栏右侧查看草稿审批，行内发布与生产变更状态一致`, async ({ page }, testInfo) => {
    test.setTimeout(90000)
    await page.addInitScript(colorScheme => {
      localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({ schemaVersion: 1, colorScheme, accentPreset: 'THEME_BLUE', sidebarTheme: 'DARK' }))
      sessionStorage.setItem('tianshu:layout:sidebar', JSON.stringify({ width: 64, lastExpandedWidth: 220 }))
    }, colorScheme)
    const data = createDocsApiData()
    data.set('/api/rule/governance/workspaces/states', ({ url }) => url.searchParams.get('ids').split(',').map(resourceId => ({ resourceId, effectiveStatus: 'ACTIVE', hasEffectiveVersion: true, requestStatus: 'PENDING', action: 'UPDATE', owned: true })))
    data.set('/api/rule/governance/workspaces', ({ url }) => {
      const resourceType = url.searchParams.get('resourceType') || 'VARIABLE'
      const statuses = url.searchParams.get('status') === 'ALL' ? ['EDITING', 'PENDING', 'APPROVED', 'REJECTED'] : [url.searchParams.get('status')]
      return { records: statuses.map((status, index) => ({ id: 991 + index, requestNo: '申请-' + status, resourceType, resourceId: 7, status, action: 'UPDATE', applicant: 'e2e', reviewComment: status === 'REJECTED' ? '请补充配置' : '' })), total: statuses.length }
    })
    const { requests, assertClean } = await installDistRoutes(page, { apiData: data })
    await page.setViewportSize({ width: 1280, height: 900 })
    for (const [route, resourceType] of modules) {
      await page.goto('http://tianshu.local/index.html#' + route)
      await expect(page.locator(`.workspace-tab[data-tab="${route}"]`)).toHaveClass(/is-active/)
      const toolbar = page.locator('.uiue-filter-toolbar:visible, .tab-filter-row:visible').filter({ has: page.getByRole('button', { name: '草稿与审批', exact: true }) }).first()
      await expect(toolbar).toBeVisible()
      await expect(page.locator('.workspace-release-bar')).toHaveCount(0)
      const row = page.locator('.el-table:visible .el-table__body tr').filter({ has: page.locator('.governance-resource-status') }).first()
      await expect(row).toContainText('审批中')
      await expect(row.getByRole('button', { name: '发布', exact: true })).toHaveCount(0)
      await toolbar.getByRole('button', { name: '草稿与审批', exact: true }).click()
      const drawer = page.getByRole('dialog', { name: '草稿与审批', exact: true })
      await expect(drawer).toBeVisible()
      for (const name of ['审批中', '已通过', '已驳回', '全部状态']) {
        await drawer.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '审批状态' }) }).click()
        await page.getByRole('option', { name, exact: true }).click()
        if (name === '全部状态') await expect(drawer.locator('.el-table__body tr')).toHaveCount(4)
        else await expect(drawer.locator('.el-table__body')).toContainText(name)
      }
      const query = new URL(requests.filter(item => new URL(item.url).pathname === '/api/rule/governance/workspaces').at(-1).url).searchParams
      expect(resourceType === 'FIELD' ? query.get('tab') : query.get('resourceType')).toBe(resourceType)
      await drawer.locator('.el-drawer__close-btn').click()
      await expect(drawer).toBeHidden()
    }
    await page.setViewportSize({ width: 560, height: 800 })
    await page.getByRole('button', { name: '草稿与审批', exact: true }).click()
    await expect(page.getByRole('dialog', { name: '草稿与审批', exact: true })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1)
    await page.screenshot({ path: testInfo.outputPath('publication-' + colorScheme + '-narrow.png'), fullPage: true })
    assertClean()
  })
}
