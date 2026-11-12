const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDocsApiData } = require('./support/docsFixtures.cjs')
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
    await expect(table.locator('.el-table__body > tbody > tr')).toHaveCount(20)
    await expect(pager.locator('.el-select')).toHaveText('20条/页')
    await expectFixedList(page)
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

for (const mode of ['FIXED', 'AUTO']) test(`变量每页 500 条在 ${mode} 模式滚动分批请求，末行操作和下一页仍使用正确 ID`, async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const data = createDocsApiData()
  const calls = []
  const records = Array.from({ length: 507 }, (_, index) => ({
    id: index + 1, varCode: 'field_' + (index + 1), varLabel: '字段' + (index + 1), varType: 'STRING', varSource: 'INPUT', scope: 'GLOBAL', status: 1,
  }))
  data.set('/api/rule/variable/list', ({ url }) => {
    const size = Number(url.searchParams.get('pageSize')), num = Number(url.searchParams.get('pageNum'))
    calls.push([num, size])
    return { records: records.slice((num - 1) * size, num * size), total: records.length }
  })
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: data })
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto(origin + '/#/variable')
  const pane = page.locator('#pane-list'), table = pane.locator('.management-table')
  const pager = pane.locator('.el-pagination')
  if (mode === 'AUTO') await chooseMode(page, 'AUTO')
  await pager.locator('.el-select').click()
  await page.getByRole('option', { name: '500条/页', exact: true }).click()
  await expect(pager).toContainText('500条/页')
  await expect(table.getByText('field_1', { exact: true }).first()).toBeVisible()
  expect(calls).toEqual([[1, 20], [1, 50]])
  expect(harness.requests.some(r => new URL(r.url).pathname === '/api/rule/dataobject/tree')).toBe(false)
  const wrap = mode === 'FIXED' ? table.locator('.el-scrollbar__wrap').first() : page.locator('.layout-main')
  for (let chunk = 2; chunk <= 10; chunk++) {
    await wrap.evaluate(el => { el.scrollTop = el.scrollHeight })
    await expect.poll(() => calls.at(-1)).toEqual([chunk, 50])
    await expect(table).toHaveAttribute('aria-busy', 'false')
    expect(await table.locator('.el-table__row').count()).toBeLessThanOrEqual(50)
  }
  await wrap.evaluate(el => { el.scrollTop = el.scrollHeight })
  const last = table.getByRole('row').filter({ hasText: 'field_500' })
  await expect(last).toBeVisible()
  await last.getByRole('button', { name: '版本', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '版本管理' })).toBeVisible()
  expect(harness.requests.some(r => new URL(r.url).pathname.endsWith('/resource-versions/VARIABLE/500'))).toBe(true)
  await page.keyboard.press('Escape')
  await pager.locator('.btn-next').click()
  await expect(table).toContainText('field_507')
  await expect(table.locator('.el-table__row')).toHaveCount(7)
  expect(calls.at(-1)).toEqual([11, 50])
  await expect(pager).toContainText('共 507 条')
  harness.assertClean()
})

test('大型对象的子字段在展开后按可见区渲染，滚动可以抵达最后一项', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const data = createDocsApiData()
  const node = data.get('/api/rule/dataobject/tree').tree[0]
  node.variables = [{ id: 100, varCode: 'root', varLabel: '根字段', varType: 'OBJECT',
    children: Array.from({ length: 5000 }, (_, i) => ({ id: i + 101, varCode: 'child_' + i, varLabel: '子字段' + i, varType: 'STRING' })),
  }]
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: data })
  await page.goto(origin + '/#/variable')
  await page.getByRole('tab', { name: '数据对象', exact: true }).click()
  await page.getByTestId('data-object-table').getByText(node.object.objectCode, { exact: true }).first().click()
  const fields = page.locator('.object-field-expansion .el-table')
  await expect(fields.locator('.el-table__row')).toHaveCount(1)
  await page.getByRole('button', { name: '展开全部' }).click()
  await expect(fields.getByText('child_0', { exact: true })).toBeVisible()
  expect(await fields.locator('.el-table__row').count()).toBeLessThan(50)
  await fields.locator('.el-scrollbar__wrap').evaluate(el => { el.scrollTop = el.scrollHeight })
  await expect(fields.getByText('child_4999', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '收起全部' }).click()
  await expect(fields.locator('.el-table__row')).toHaveCount(1)
  harness.assertClean()
})

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
