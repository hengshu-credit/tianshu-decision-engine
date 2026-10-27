const { test, expect } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createManagementApiData } = require('./support/managementFixtures.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')
const { createOperationsApiData } = require('./support/operationsFixtures.cjs')

const cases = [
  ['数据库列表', '/database', null, 'database-list'],
  ['模型列表', '/model', null, 'model-list'],
  ['函数列表', '/function', null, 'function-list'],
  ['名单列表', '/list', null, 'list-library'],
  ['外数列表', '/datasource', null, 'datasource-list'],
  ['API 列表', '/datasource', 'API 接口', 'api-list'],
  ['外数监控', '/datasource', '监控看板', 'external-api-monitor'],
  ['规则执行日志', '/log', null, 'execution-log'],
  ['规则集统计', '/log', '规则集命中统计', 'execution-log-rule-set'],
  ['计费配置', '/billing', null, 'billing-list'],
  ['计费明细', '/billing', '计费明细', 'billing-records'],
  ['计费汇总', '/billing', '计费汇总', 'billing-summary'],
  ['审批列表', '/approval', null, 'approval-list'],
  ['实验列表', '/experiment', null, 'experiment-list'],
  ['账户列表', '/account', null, 'account-management-accounts'],
  ['角色列表', '/account', '角色', 'account-management-roles'],
  ['模型调用日志', '/model', '模型执行日志', 'module-log-model'],
  ['数据库调用日志', '/database', '调用日志', 'module-log-database'],
  ['外数调用日志', '/datasource', '调用日志', 'module-log-datasource'],
  ['名单调用日志', '/list', '名单日志', 'module-log-list'],
]

for (const [title, path, tab, key] of cases) test(`${title}：字段目录、排序、隐藏与重载一致`, async ({ page }) => {
  const apiData = new Map([...createDetailApiData(), ...createManagementApiData(), ...createOperationsApiData()])
  apiData.set('/api/rule/governance/requests/summary', { pendingCount: 0, myDraftCount: 0, myRequestCount: 0, completedCount: 0 })
  apiData.set('/api/rule/governance/requests', { records: [], total: 0 })
  apiData.set('/api/rule/console/accounts', [{ id: 1, username: 'column_test', displayName: '字段验收', email: '', roleCodes: [], effectivePermissions: [], status: 1 }])
  apiData.set('/api/rule/console/roles', [{ id: 1, roleCode: 'REVIEW', roleName: '审查角色', permissionCodes: [], memberCount: 1, status: 1 }])
  apiData.set('/api/rule/console/permissions', [])
  apiData.set('/api/rule/runtime-log/external-api-stats', { overview: {}, providers: [{ targetCode: 'quality_api', targetName: '质量验收接口', queryCount: 10, requestSuccessRate: 0.9, failureRate: 0.1, foundRate: 0.8, cacheHitRate: 0.3, p95CostTimeMs: 40, p99CostTimeMs: 60 }] })
  const harness = await installDistRoutes(page, { apiData })
  await page.goto(`http://tianshu.local/#${path}`)
  const enterTab = async () => {
    if (tab) await page.getByRole('tab', { name: tab, exact: true }).click()
  }
  await enterTab()
  const settings = page.locator(`[data-table-column-settings="tianshu:table-columns:${key}"]`)
  const table = page.locator('.el-table:visible').first()
  const headers = async () => (await table.locator('.el-table__header-wrapper th .cell').allTextContents())
    .map(value => value.trim()).filter(value => value && value !== '操作')
  await expect(settings).toBeVisible()
  await settings.getByRole('button').press('Enter')
  const panel = page.locator('.table-column-settings__panel:visible')
  await expect(panel).toBeVisible()
  await expect.poll(() => panel.locator('.table-column-settings__visibility').count()).toBeGreaterThan(2)
  await expect.poll(headers).not.toEqual([])
  const names = await panel.locator('.table-column-settings__visibility').evaluateAll(nodes =>
    nodes.map(node => node.getAttribute('aria-label').slice(2)))
  expect([...names].sort()).toEqual((await headers()).sort())
  const hidden = names[1], moved = names[2]
  await panel.getByRole('button', { name: `隐藏${hidden}`, exact: true }).press('Space')
  for (let step = 0; step < 2; step++) {
    await panel.locator('.table-column-settings__item').filter({ has: page.getByRole('button', { name: `隐藏${moved}`, exact: true }) })
      .getByRole('button', { name: '上移字段', exact: true }).click()
  }
  await panel.getByRole('button', { name: '应用', exact: true }).press('Enter')
  await expect.poll(headers).not.toContain(hidden)
  await expect.poll(async () => (await headers())[0]).toBe(moved)
  const query = page.getByRole('button', { name: '查询', exact: true }).filter({ visible: true }).first()
  if (await query.count()) await query.click()
  await expect.poll(headers).not.toContain(hidden)
  await page.reload()
  await enterTab()
  await expect(settings).toBeVisible()
  await expect.poll(headers).not.toContain(hidden)
  await expect.poll(async () => (await headers())[0]).toBe(moved)
  await page.setViewportSize({ width: 560, height: 900 })
  await expect(settings.getByRole('button')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  harness.assertClean()
})
