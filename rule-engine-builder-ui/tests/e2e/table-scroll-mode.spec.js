const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createOperationsApiData } = require('./support/operationsFixtures.cjs')

const listCases = [
  ['project', '/api/rule/project/list'],
  ['rule', '/api/rule/definition/list'],
  ['variable', '/api/rule/variable/list'],
  ['list', '/api/rule/list/library'],
  ['datasource', '/api/rule/datasource/list'],
  ['datasource?tab=api', '/api/rule/datasource/api-config/list'],
  ['database', '/api/rule/database/list'],
  ['model', '/api/rule/model/list'],
  ['function', '/api/rule/function/list'],
  ['experiment', '/api/rule/experiment/list'],
  ['log', '/api/rule/log/list'],
  ['billing', '/api/rule/billing/config/list'],
]

async function openTheme(page) {
  await page.locator('.layout-account-trigger').click()
  await page.locator('[data-account-command="theme"]').click()
  await expect(page.getByRole('dialog', { name: '主题设置' })).toBeVisible()
}

async function chooseMode(page, mode, { save = true, top = false } = {}) {
  await openTheme(page)
  const drawer = page.getByRole('dialog', { name: '主题设置' })
  if (top) await drawer.locator('[data-navigation-layout="TOP"]').click()
  await drawer.locator(`[data-table-scroll-mode="${mode}"]`).click()
  if (save) await drawer.locator('[data-action="save"]').click()
  await expect(page.locator('html')).toHaveAttribute('data-table-scroll-mode', mode.toLowerCase())
}

async function expectFixedList(page) {
  const table = page.locator('.management-table:visible').first()
  const pager = page.locator('.management-list-page .el-pagination:visible').last()
  await expect(table).toBeVisible()
  await expect(pager).toBeInViewport()
  await expect.poll(() => page.locator('.layout-main').evaluate(el => el.scrollHeight - el.clientHeight)).toBeLessThanOrEqual(1)
  const pageBox = await page.locator('.management-list-page').boundingBox()
  const pagerBox = await pager.boundingBox()
  expect(pageBox.y + pageBox.height - pagerBox.y - pagerBox.height).toBeLessThanOrEqual(42)
  return { table, pager }
}

for (const [index, [route, api]] of listCases.entries()) {
  test(`${route} 50 行可在表格内滚动，表头分页固定并可恢复自动高度`, async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 1000 })
    const apiData = createOperationsApiData()
    apiData.set('/api/rule/variable/list', {
      records: [{ id: 1, varCode: 'age', varLabel: '年龄', varType: 'INTEGER', varSource: 'INPUT', scope: 'PROJECT', status: 1 }], total: 1,
    })
    const source = apiData.get(api).records[0]
    const rows = Array.from({ length: 65 }, (_, i) => ({ ...source, id: i + 1 }))
    apiData.set(api, ({ url }) => {
      const size = Number(url.searchParams.get('pageSize') || 10)
      const pageNum = Number(url.searchParams.get('pageNum') || 1)
      return { records: rows.slice((pageNum - 1) * size, pageNum * size), total: rows.length }
    })
    const { assertClean } = await installDistRoutes(page, { apiData })
    await page.goto(`http://tianshu.local/index.html#/${route}`)
    const table = page.locator('.management-table:visible').first()
    await expect(table.locator('.el-table__body > tbody > tr').first()).toBeVisible()
    const pager = page.locator('.management-list-page .el-pagination:visible').last()
    await pager.locator('.el-select').click()
    await page.getByRole('option', { name: '50条/页', exact: true }).click()
    await expect(table.locator('.el-table__body > tbody > tr')).toHaveCount(50)
    await chooseMode(page, 'FIXED', { top: index % 2 === 1 })
    await expectFixedList(page)

    const header = table.locator('.el-table__header-wrapper')
    const before = { header: await header.boundingBox(), pager: await pager.boundingBox() }
    const wrap = table.locator('.el-scrollbar__wrap').first()
    await wrap.hover()
    await page.mouse.wheel(0, 1800)
    await expect.poll(() => wrap.evaluate(el => el.scrollTop)).toBeGreaterThan(0)
    expect(Math.abs((await header.boundingBox()).y - before.header.y)).toBeLessThan(1)
    expect(Math.abs((await pager.boundingBox()).y - before.pager.y)).toBeLessThan(1)
    await page.setViewportSize({ width: 1280, height: 900 })
    await expectFixedList(page)
    await pager.getByRole('button', { name: '下一页' }).click()
    await expect(table.locator('.el-table__body > tbody > tr')).toHaveCount(15)
    await expectFixedList(page)

    await chooseMode(page, 'AUTO', { save: false })
    await page.getByRole('dialog', { name: '主题设置' }).locator('[data-action="cancel"]').click()
    await expect(page.locator('html')).toHaveAttribute('data-table-scroll-mode', 'fixed')
    await page.reload()
    await expect(page.locator('html')).toHaveAttribute('data-table-scroll-mode', 'fixed')
    await chooseMode(page, 'AUTO')
    await pager.locator('.el-select').click()
    await page.getByRole('option', { name: '50条/页', exact: true }).click()
    await pager.getByRole('listitem', { name: '第 1 页', exact: true }).click()
    await expect(table.locator('.el-table__body > tbody > tr')).toHaveCount(50)
    await expect.poll(() => page.locator('.layout-main').evaluate(el => el.scrollHeight - el.clientHeight)).toBeGreaterThan(100)
    expect(await wrap.evaluate(el => el.scrollHeight - el.clientHeight)).toBeLessThanOrEqual(1)
    assertClean()
  })
}

test('固定表格模式支持空结果、弹窗和较小窗口', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  const apiData = createOperationsApiData()
  apiData.set('/api/rule/project/list', { records: [], total: 0 })
  const { assertClean } = await installDistRoutes(page, { apiData })
  await page.goto('http://tianshu.local/index.html#/project')
  await chooseMode(page, 'FIXED')
  const { table } = await expectFixedList(page)
  await expect(table.locator('.el-table__empty-block')).toBeVisible()
  await page.getByRole('button', { name: '新建项目', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '新建项目', exact: true })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await page.setViewportSize({ width: 800, height: 500 })
  const pager = page.locator('.management-list-page .el-pagination:visible')
  await pager.locator('.el-select').click()
  await expect(page.getByRole('option', { name: '50条/页', exact: true })).toBeVisible()
  await page.keyboard.press('Escape')
  expect((await table.boundingBox()).height).toBeGreaterThanOrEqual(160)
  assertClean()
})

test('账单和数据库标签切换后主表格继续填满剩余高度', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 })
  const apiData = createOperationsApiData()
  apiData.set('/api/rule/runtime-log/list', { records: [], total: 0 })
  const { assertClean } = await installDistRoutes(page, { apiData })
  await page.goto('http://tianshu.local/index.html#/billing')
  await chooseMode(page, 'FIXED')
  for (const name of ['计费明细', '计费汇总', '计费配置']) {
    await page.getByRole('tab', { name, exact: true }).click()
    await expectFixedList(page)
  }
  await page.getByRole('button', { name: '数据库管理', exact: true }).click()
  await page.getByRole('tab', { name: '调用日志', exact: true }).click()
  await expectFixedList(page)
  await page.getByRole('tab', { name: '数据源配置', exact: true }).click()
  await expectFixedList(page)
  assertClean()
})

test('账户与审批列表也支持表格内部滚动', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 })
  const apiData = createOperationsApiData()
  apiData.set('/api/rule/console/accounts', Array.from({ length: 50 }, (_, i) => ({ id: i + 1, username: `reviewer${i}`, displayName: `审批账户${i}`, roleCodes: [], status: 1 })))
  apiData.set('/api/rule/console/roles', [])
  apiData.set('/api/rule/console/permissions', [])
  apiData.set('/api/rule/governance/requests/summary', { pendingCount: 50 })
  apiData.set('/api/rule/governance/requests', { records: Array.from({ length: 50 }, (_, i) => ({ id: i + 1, requestNo: `APPROVAL_${i}`, resourceType: 'RULE', action: 'UPDATE', status: 'PENDING', applicant: 'reviewer' })), total: 50 })
  const { assertClean } = await installDistRoutes(page, { apiData })
  await page.goto('http://tianshu.local/index.html#/account')
  await chooseMode(page, 'FIXED')
  const table = page.locator('.management-table:visible').first()
  await table.locator('.el-scrollbar__wrap').first().hover()
  await page.mouse.wheel(0, 1200)
  await expect.poll(() => table.locator('.el-scrollbar__wrap').first().evaluate(el => el.scrollTop)).toBeGreaterThan(0)
  await expect.poll(() => page.locator('.layout-main').evaluate(el => el.scrollHeight - el.clientHeight)).toBeLessThanOrEqual(1)
  await page.getByRole('button', { name: '审批管理', exact: true }).click()
  await expectFixedList(page)
  assertClean()
})
