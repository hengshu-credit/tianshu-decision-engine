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

async function resizeDrawer(page, drawer, delta) {
  const handle = drawer.locator('.el-drawer__dragger')
  await handle.hover()
  const rect = await handle.boundingBox()
  const before = await drawer.boundingBox()
  await page.mouse.move(rect.x + rect.width / 2, rect.y + rect.height / 2)
  await page.mouse.down()
  await page.mouse.move(rect.x + rect.width / 2 - delta, rect.y + rect.height / 2, { steps: 5 })
  await page.mouse.up()
  await expect.poll(async () => Math.round((await drawer.boundingBox()).width - before.width)).toBe(delta)
}

async function checkDrawerScroll(drawer, table, mode) {
  const body = drawer.locator('.el-drawer__body')
  const wrap = table.locator('.el-scrollbar__wrap').first()
  const scrollRange = el => el.scrollHeight - el.clientHeight
  await expect.poll(() => (mode === 'FIXED' ? wrap : body).evaluate(scrollRange)).toBeGreaterThan(50)
  await expect.poll(() => (mode === 'FIXED' ? body : wrap).evaluate(scrollRange)).toBeLessThanOrEqual(1)
  const scroll = mode === 'FIXED' ? wrap : body
  await scroll.evaluate(el => { el.scrollTop = el.scrollHeight })
  await expect.poll(() => scroll.evaluate(el => el.scrollTop)).toBeGreaterThan(50)
}

for (const mode of ['FIXED', 'AUTO']) test(`版本与审批抽屉支持左侧调宽、长参数预览和 ${mode} 滚动`, async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const data = createDocsApiData()
  const fn = data.get('/api/rule/function/list').records[0]
  const vector = Array.from({ length: 512 }, (_, i) => i)
  const parameters = [{ name: 'embeddings', type: 'LIST', example: [vector, vector] },
    ...Array.from({ length: 30 }, (_, i) => ({ name: 'field_' + i, type: 'NUMBER', example: i }))]
  const snapshot = { ...fn, paramsJson: JSON.stringify(parameters) }
  const history = Array.from({ length: 40 }, (_, i) => ({
    id: i + 1, versionNo: i + 1, revisionNo: 1, snapshotJson: JSON.stringify(i === 39 ? snapshot : fn),
  }))
  const api = `/api/rule/resource-versions/FUNCTION/${fn.id}`
  data.set(api, { latestVersionId: 40, history, versions: history.map(row => ({ versionId: row.id, version: row.versionNo, revision: 1 })) })
  data.set(api + '/events', history.map(row => ({ id: row.id, action: 'SAVE_DRAFT' })))
  data.set(`/api/rule/function/versions/${fn.id}`, history.map(row => ({ version: row.versionNo, functionJson: row.snapshotJson })))
  data.set('/api/rule/governance/workspaces', { total: 20, records: history.slice(0, 20).map(row => ({
    id: row.id, status: 'EDITING', resourceType: 'FUNCTION', action: 'UPDATE', requestNo: '申请' + row.id,
  })) })
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: data })
  await page.setViewportSize({ width: 1440, height: 800 })
  await page.goto(origin + '/#/function')
  await chooseMode(page, mode)
  await page.getByRole('button', { name: '版本', exact: true }).first().click()
  const versions = page.getByRole('dialog', { name: '版本管理', exact: true })
  const params = versions.locator('[data-section="paramsJson"]')
  await expect(params).toContainText('embeddings')
  await resizeDrawer(page, versions, 128)
  const first = params.getByRole('button', { name: /第 1 项$/, expanded: false }).first()
  await first.click()
  const lastValue = params.getByText('第 512 项', { exact: true })
  await lastValue.scrollIntoViewIfNeeded()
  await expect(lastValue).toBeInViewport()
  const scrollOwners = await lastValue.evaluate(el => {
    const owners = []
    for (let p = el.parentElement; p; p = p.parentElement) {
      if (/auto|scroll/.test(getComputedStyle(p).overflowY) && p.scrollHeight > p.clientHeight + 1) owners.push(p.className)
    }
    return owners
  })
  expect(scrollOwners).toEqual(['preview-region-body', 'el-drawer__body'])
  await params.locator('.section-toggle').click()
  await expect(lastValue).toBeHidden()
  await params.locator('.section-toggle').click()
  await params.getByRole('button', { name: '显示完整高度' }).click()
  await lastValue.scrollIntoViewIfNeeded()
  await expect(lastValue).toBeInViewport()
  expect(await params.locator('.preview-region-body').evaluate(el => el.scrollHeight - el.clientHeight)).toBeLessThanOrEqual(1)
  await params.getByText('field_29', { exact: true }).scrollIntoViewIfNeeded()
  await expect(params.getByText('field_29', { exact: true })).toBeInViewport()
  await versions.locator('.version-comparison-toolbar .el-select').click()
  await page.getByRole('option', { name: 'v1 · 修订 1', exact: true }).click()
  await resizeDrawer(page, versions, -688)
  const panels = params.locator('.business-preview-card')
  await expect(panels).toHaveCount(2)
  expect((await panels.last().boundingBox()).y).toBeGreaterThan((await panels.first().boundingBox()).y)
  await resizeDrawer(page, versions, 688)
  await versions.getByRole('tab', { name: '版本与修订' }).click()
  await checkDrawerScroll(versions, versions.locator('.workspace-table:visible').first(), mode)
  await versions.getByRole('tab', { name: '变更记录' }).click()
  await checkDrawerScroll(versions, versions.locator('.workspace-table:visible'), mode)
  await versions.getByRole('button', { name: '早期历史快照' }).click()
  const historical = page.getByRole('dialog', { name: '函数版本管理' })
  await checkDrawerScroll(historical, historical.locator('.workspace-history-table'), mode)
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '草稿与审批', exact: true }).click()
  const drafts = page.getByRole('dialog', { name: '草稿与审批', exact: true })
  await expect(drafts.locator('.workspace-table')).toContainText('申请20')
  await resizeDrawer(page, drafts, -128)
  await checkDrawerScroll(drafts, drafts.locator('.workspace-table'), mode)
  await expect(drafts.locator('.el-pagination')).toBeInViewport()
  await page.setViewportSize({ width: 390, height: 700 })
  await expect.poll(() => drafts.locator('.el-drawer__body').evaluate(el => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(1)
  harness.assertClean()
})

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

for (const mode of ['FIXED', 'AUTO']) test(`大型对象在 ${mode} 模式按可见区渲染子字段，滚动可以抵达最后一项`, async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const data = createDocsApiData()
  const node = data.get('/api/rule/dataobject/tree').tree[0]
  node.variables = [{ id: 100, varCode: 'root', varLabel: '根字段', varType: 'OBJECT',
    children: Array.from({ length: 5000 }, (_, i) => ({ id: i + 101, varCode: 'child_' + i, varLabel: '子字段' + i, varType: 'STRING' })),
  }]
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: data })
  await page.goto(origin + '/#/variable')
  await page.getByRole('tab', { name: '数据对象', exact: true }).click()
  if (mode === 'AUTO') await chooseMode(page, 'AUTO')
  const table = page.getByTestId('data-object-table')
  const rootCount = await table.locator('.el-table__row').count()
  await table.getByRole('button', { name: '展开或收起 ' + node.object.objectCode, exact: true }).click()
  await expect(table.locator('.el-table__row')).toHaveCount(rootCount + 1)
  await expect(table.getByRole('button', { name: /展开全部|收起全部/ })).toHaveCount(0)
  const root = table.getByRole('row').filter({ has: page.getByText('根字段', { exact: true }) })
  const toggle = root.getByRole('button', { name: '展开或收起 root', exact: true })
  await toggle.click()
  const child = table.getByRole('row').filter({ has: page.getByText('child_0', { exact: true }) })
  const headers = await table.getByRole('columnheader').allTextContents()
  const cell = (row, label) => row.getByRole('cell').nth(headers.indexOf(label))
  await expect(cell(child, '对象编码')).toHaveText('child_0')
  await expect(cell(child, '对象名称')).toHaveText('子字段0')
  await expect(cell(child, '类型')).toHaveText('字符串')
  const childGuides = await cell(child, '对象编码').locator('.object-tree-guides').boundingBox()
  const rootGuides = await cell(root, '对象编码').locator('.object-tree-guides').boundingBox()
  expect(childGuides.x).toBe(rootGuides.x)
  expect(childGuides.width).toBe(rootGuides.width * 2)
  const object = table.getByRole('row').filter({ has: page.getByRole('button', { name: '展开或收起 ' + node.object.objectCode }) })
  const background = await cell(object, '对象编码').evaluate(el => getComputedStyle(el).backgroundColor)
  for (const row of [root, child]) {
    await cell(row, '对象编码').hover()
    await expect(cell(row, '对象编码')).toHaveCSS('background-color', background)
    await expect(cell(row, '操作')).toHaveCSS('background-color', background)
    await expect(cell(object, '对象编码')).toHaveCSS('background-color', background)
  }
  expect(await table.locator('.el-table__row').count()).toBeLessThan(50)
  const wrap = mode === 'FIXED' ? table.locator('.el-scrollbar__wrap').first() : page.locator('.layout-main')
  await wrap.evaluate(el => { el.scrollTop = el.scrollHeight })
  await expect(table.getByText('child_4999', { exact: true })).toBeVisible()
  await wrap.evaluate(el => { el.scrollTop = 0 })
  await toggle.click()
  await expect(table.locator('.el-table__row')).toHaveCount(rootCount + 1)
  await table.getByRole('button', { name: '展开或收起 ' + node.object.objectCode, exact: true }).click()
  await expect(table.locator('.el-table__row')).toHaveCount(rootCount)
  harness.assertClean()
})

test('对象字段分页、脚本名称显示和编辑使用当前行', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const data = createDocsApiData()
  const node = data.get('/api/rule/dataobject/tree').tree[0]
  data.get('/api/rule/dataobject/tree').tree.push({
    object: { ...node.object, id: 999, objectCode: 'Other', scriptName: 'OtherAlias' }, variables: [],
  })
  node.object.scriptName = null
  node.variables = Array.from({ length: 21 }, (_, i) => ({
    id: i + 1, objectId: node.object.id, varCode: 'code_' + i, varLabel: '字段 ' + i,
    varType: 'STRING', originalScriptName: 'alias_' + i, scriptName: 'path.alias_' + i,
  }))
  node.variables[0].originalScriptName = null
  data.set('/api/rule/governance/workspaces/DATA_OBJECT/' + node.object.id, null)
  data.set('/api/rule/dataobject/' + node.object.id, node)
  data.set('/api/rule/dataobject/project/' + node.object.projectId, [node.object])
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: data })
  await page.goto(origin + '/#/variable')
  await page.getByRole('tab', { name: '数据对象', exact: true }).click()
  const table = page.getByTestId('data-object-table')
  await table.getByRole('button', { name: '展开或收起 ' + node.object.objectCode }).click()
  const headers = await table.getByRole('columnheader').allTextContents()
  const scriptIndex = headers.indexOf('脚本名称')
  expect(scriptIndex).toBeGreaterThanOrEqual(0)
  const objectRow = table.getByRole('row').filter({ has: page.getByRole('button', { name: '展开或收起 ' + node.object.objectCode }) })
  await expect(objectRow.getByRole('cell').nth(scriptIndex)).toHaveText('')
  const other = table.getByRole('row').filter({ has: page.getByText('Other', { exact: true }) })
  await expect(other.getByRole('cell').nth(scriptIndex)).toHaveText('OtherAlias')
  const first = table.getByRole('row').filter({ has: page.getByText('code_0', { exact: true }) })
  await expect(first.getByRole('cell').nth(scriptIndex)).toHaveText('')
  const guide = first.locator('.object-tree-guides')
  const guidePaint = () => guide.evaluate(el => getComputedStyle(el).backgroundImage)
  const initialPaint = await guidePaint()
  await openTheme(page)
  const drawer = page.getByRole('dialog', { name: '主题设置' })
  await drawer.locator('[data-accent="THEME_BLUE"]').click()
  await drawer.locator('[data-action="save"]').click()
  await expect.poll(guidePaint).toContain('rgb(38, 57, 233)')
  expect(await guidePaint()).not.toBe(initialPaint)
  await table.getByRole('button', { name: '下一页' }).click()
  const last = table.getByRole('row').filter({ has: page.getByText('code_20', { exact: true }) })
  await expect(last.getByRole('cell').nth(scriptIndex)).toHaveText('alias_20')
  await expect(table.getByText('code_0', { exact: true })).toHaveCount(0)
  await last.getByRole('button', { name: '编辑', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '编辑对象字段', exact: true })
  await expect(dialog.getByRole('textbox', { name: /字段编码/ })).toHaveValue('code_20')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await table.getByRole('button', { name: '上一页' }).click()
  await expect(table.getByText('code_0', { exact: true })).toBeVisible()
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
