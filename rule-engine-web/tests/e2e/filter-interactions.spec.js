const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')

const cases = [
  { path: '/dashboard', label: '规则编码', field: 'ruleCode', api: '/dashboard/operations', candidate: 'age_rule' },
  { path: '/sftp', label: '项目编码', field: 'projectCode', api: '/sftp/list', candidate: 'e2e_project' },
  { path: '/sftp/logs', label: '项目编码', field: 'projectCode', api: '/runtime-log/list', candidate: 'e2e_project' },
  { path: '/project', label: '项目编码', field: 'projectCode', api: '/project/list', candidate: 'e2e_project' },
  { path: '/rule', label: '规则编码', field: 'ruleCode', api: '/definition/list', candidate: 'age_rule' },
  { path: '/variable', placeholder: '变量编码', field: 'varCode', api: '/variable/list', candidate: 'age' },
  { path: '/variable', tab: '常量列表', placeholder: '常量编码', field: 'varCode', api: '/variable/list', candidate: 'MAX_AGE' },
  { path: '/variable', tab: '字段校验', placeholder: '编码或名称', field: 'keyword', api: '/field-validation/list', candidate: 'mobile_required' },
  { path: '/model', label: '模型编码', field: 'modelCode', api: '/model/list', candidate: 'credit_score' },
  { path: '/function', label: '函数编码', field: 'funcCode', api: '/function/list', candidate: 'calcRisk' },
  { path: '/database', label: '数据源编码', field: 'datasourceCode', api: '/database/list', candidate: 'risk_mysql' },
  { path: '/datasource', label: '数据源编码', field: 'datasourceCode', api: '/datasource/list', candidate: 'credit_vendor' },
  { path: '/datasource', tab: '调用日志', label: '接口编码', field: 'targetCode', api: '/runtime-log/list', candidate: 'credit_query', area: '.log-filter:visible' },
  { path: '/list', label: '关键字', field: 'keyword', api: '/list/library', candidate: 'mobile_black' },
  { path: '/experiment', label: '关键字', field: 'keyword', api: '/experiment/list', candidate: 'risk_ab' },
  { path: '/billing', label: '计费编码', field: 'billingCode', api: '/billing/config/list', candidate: 'engine_call' },
  { path: '/billing', tab: '计费明细', label: '计费编码', field: 'billingCode', api: '/billing/record/list', candidate: 'engine_call' },
  { path: '/billing', tab: '计费汇总', label: '计费编码', field: 'billingCode', api: '/billing/summary/list', candidate: 'engine_call' },
  { path: '/log', label: '规则', field: 'ruleCode', api: '/log/list', candidate: '年龄判断规则' },
  { path: '/log', tab: '规则集命中统计', label: '规则集', field: 'ruleCode', api: '/log/rule-set-stats', candidate: '风控规则集' },
  { path: '/project/1', tab: '项目规则', label: '规则编码', field: 'ruleCode', api: '/definition/project-list/1', candidate: 'age_rule' },
  { path: '/list/9', label: '关键字', field: 'keyword', api: '/list/9/record', candidate: '13800138000' },
  { path: '/approval', placeholder: '审批单号或变更说明', field: 'keyword', api: '/governance/requests', candidate: 'GOV-FILTER-1', area: '.filter-bar:visible' },
]

async function expectAttached(page, input) {
  const panel = page.locator('.el-popper.remote-filter-suggestions:visible')
  const anchor = input.locator('xpath=ancestor::*[contains(@class,"el-input__wrapper")][1]')
  await expect.poll(async () => {
    const [box, trigger, placement] = await Promise.all([panel.boundingBox(), anchor.boundingBox(), panel.getAttribute('data-popper-placement')])
    if (!box || !trigger || !placement) return false
    const gap = placement.startsWith('top') ? trigger.y - box.y - box.height : box.y - trigger.y - trigger.height
    return Math.abs(box.x - trigger.x) <= 2 && Math.abs(box.width - trigger.width) <= 2 && gap >= 0 && gap <= 12
  }).toBe(true)
}

for (const entry of cases) {
  test(`${entry.path} ${entry.tab || ''} ${entry.field} 支持候选、直接查询、保留及重置`, async ({ page }) => {
    const apiData = createDetailApiData()
    if (entry.path === '/sftp') apiData.set('/api/rule/sftp/list', { records: [], total: 0 })
    if (entry.tab === '规则集命中统计') {
      apiData.set('/api/rule/definition/list', { records: [{ id: 201, ruleCode: 'risk_set', ruleName: '风控规则集', modelType: 'RULE_SET' }], total: 1 })
    }
    apiData.set('/api/rule/governance/requests/summary', { pendingCount: 1 })
    apiData.set('/api/rule/governance/requests', { records: [{
      id: 1, requestNo: 'GOV-FILTER-1', resourceType: 'VARIABLE', action: 'UPDATE', status: 'PENDING', changeSummary: '修改字段', applicant: 'e2e',
    }], total: 1 })
    const { assertClean } = await installDistRoutes(page, { apiData })
    await page.goto(`http://tianshu.local/index.html#${entry.path}`)
    if (entry.tab) await page.getByRole('tab', { name: entry.tab, exact: true }).click()
    const area = page.locator(entry.area || '.uiue-search-container:visible, .tab-filter-row:visible').first()
    const input = entry.placeholder
      ? area.getByPlaceholder(entry.placeholder, { exact: true })
      : area.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: new RegExp(`^${entry.label}$`) }) }).locator('.remote-filter-input input')
    await input.click()
    await expect(page.locator('.el-autocomplete-suggestion:visible li').filter({ hasText: entry.candidate }).first()).toBeVisible()

    await expectAttached(page, input)

    async function query(value, byEnter) {
      await input.fill(value)
      const requested = page.waitForRequest(request => {
        const url = new URL(request.url())
        return url.pathname === `/api/rule${entry.api}` && url.searchParams.get(entry.field) === value
      })
      if (byEnter) await input.press('Enter')
      else await area.getByRole('button', { name: '查询', exact: true }).click()
      await requested
      // 等过候选查询的 200ms 防抖窗口，确认回车后不会被排队任务重新打开。
      await page.waitForTimeout(350)
      await expect(input).toHaveValue(value)
      await expect(page.locator('.el-autocomplete-suggestion:visible')).toHaveCount(0)
    }

    await query('任意_% 混合字符', false)
    await query('任意_% 修改', true)
    await area.getByRole('button', { name: '重置', exact: true }).click()
    await expect(input).toHaveValue('')
    await query('重置后立即输入', false)
    assertClean()
  })
}

test('项目多条件匹配结果正确，修改其中一项保留其余条件', async ({ page }) => {
  const projects = [
    { id: 1, projectCode: 'risk_main', projectName: '风控主项目', status: 1 },
    { id: 2, projectCode: 'risk_second', projectName: '风控备用项目', status: 1 },
    { id: 3, projectCode: 'other', projectName: '其他项目', status: 1 },
  ]
  const { assertClean } = await installDistRoutes(page, { apiData: new Map([
    ['/api/rule/project/list', ({ url }) => {
      const records = projects.filter(row => ['projectCode', 'projectName'].every(field => row[field].includes(url.searchParams.get(field) || '')))
      return { records, total: records.length }
    }],
  ]) })
  await page.goto('http://tianshu.local/index.html#/project')
  const code = page.getByRole('textbox', { name: '项目编码', exact: true })
  const name = page.getByRole('textbox', { name: '项目名称', exact: true })
  await code.fill('risk')
  await expect(page.locator('.el-autocomplete-suggestion:visible li')).toHaveText(['risk_main', 'risk_second'])
  await code.press('Enter')
  await expect(page.getByText('共 2 条', { exact: true })).toBeVisible()
  await name.fill('主')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText('共 1 条', { exact: true })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'risk_main', exact: true })).toBeVisible()
  await expect(code).toHaveValue('risk')
  await expect(name).toHaveValue('主')
  await name.fill('备用')
  await name.press('Enter')
  await expect(page.getByRole('cell', { name: 'risk_second', exact: true })).toBeVisible()
  await expect(code).toHaveValue('risk')
  await expect(name).toHaveValue('备用')
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(code).toHaveValue('')
  await expect(name).toHaveValue('')
  await expect(page.getByText('共 3 条', { exact: true })).toBeVisible()
  await code.click()
  await page.getByRole('option', { name: 'risk_main', exact: true }).click()
  await expect(code).toHaveValue('risk_main')
  await code.press('Enter')
  await expect(page.getByText('共 1 条', { exact: true })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'risk_main', exact: true })).toBeVisible()
  await expect(page.locator('.el-autocomplete-suggestion:visible')).toHaveCount(0)
  assertClean()
})

test('执行日志规则候选在工具栏换行、滚动和视口变化后紧贴输入框', async ({ page }, testInfo) => {
  const { assertClean } = await installDistRoutes(page, { apiData: createDetailApiData() })
  await page.setViewportSize({ width: 3000, height: 900 })
  await page.goto('http://tianshu.local/index.html#/log')
  const input = page.getByPlaceholder('全部规则', { exact: true })
  await input.click()
  await expect(page.getByRole('option', { name: '年龄判断规则', exact: true })).toBeVisible()
  await expectAttached(page, input)
  const panel = page.locator('.el-popper.remote-filter-suggestions:visible')
  await expect(panel).toHaveAttribute('data-popper-placement', 'bottom-start')
  const queryButton = page.locator('.uiue-filter-toolbar').getByRole('button', { name: '查询', exact: true })
  expect((await queryButton.boundingBox()).y).toBeGreaterThan((await input.boundingBox()).y + 30)
  await page.screenshot({ path: testInfo.outputPath('execution-log-dropdown.png') })
  await page.setViewportSize({ width: 3000, height: 310 })
  await expect(panel).toHaveAttribute('data-popper-placement', 'top-start')
  await expectAttached(page, input)
  // 换行后查询按钮落到候选层正下方时，改在输入框上方显示。
  await page.setViewportSize({ width: 2560, height: 900 })
  await expect(panel).toHaveAttribute('data-popper-placement', 'top-start')
  await expectAttached(page, input)
  await page.setViewportSize({ width: 390, height: 760 })
  await input.scrollIntoViewIfNeeded()
  await expectAttached(page, input)
  await page.getByRole('option', { name: '年龄判断规则', exact: true }).click()
  await expect(input).toHaveValue('age_rule')
  assertClean()
})

test('审批筛选在宽屏内同排，窄屏换行且查询和重置均可用', async ({ page }, testInfo) => {
  const calls = []
  const apiData = createDetailApiData()
  apiData.set('/api/rule/governance/requests/summary', { pendingCount: 0, completedCount: 1, myRequestCount: 1, myDraftCount: 0 })
  apiData.set('/api/rule/governance/requests', ({ url }) => {
    calls.push(Object.fromEntries(url.searchParams))
    const records = url.searchParams.get('taskScope') === 'COMPLETED' ? [{
      id: 7, requestNo: 'GOV-0007', resourceType: 'RULE', action: 'UPDATE', status: 'APPROVED',
      applicant: '审核员', submittedSnapshotJson: '{"ruleName":"额度复核"}', submitTime: '2026-11-20 10:30:00',
    }] : []
    return { records, total: records.length }
  })
  const { assertClean } = await installDistRoutes(page, { apiData })
  await page.setViewportSize({ width: 3000, height: 900 })
  await page.goto('http://tianshu.local/index.html#/approval')
  const form = page.locator('.approval-filters')
  const status = form.getByRole('combobox', { name: '申请状态' })
  await expect(status).toBeHidden()
  await page.getByRole('tab', { name: '已结束', exact: true }).click()
  const input = form.getByRole('textbox', { name: '关键词' })
  const search = form.getByRole('button', { name: '查询' })
  for (const width of [3000, 1440]) {
    await page.setViewportSize({ width, height: 900 })
    const boxes = await Promise.all([input, ...await form.locator('.el-select__wrapper').all(), search,
      page.getByRole('button', { name: '设置表格显示字段' })].map(control => control.boundingBox()))
    expect(boxes[0].width).toBeLessThanOrEqual(260)
    for (const box of boxes) expect(Math.abs((box.y + box.height / 2) - (boxes[0].y + boxes[0].height / 2))).toBeLessThan(3)
  }
  await page.screenshot({ path: testInfo.outputPath('approval-filters.png') })
  await page.setViewportSize({ width: 390, height: 844 })
  const fields = form.locator('.el-autocomplete, .el-select__wrapper')
  const starts = await fields.evaluateAll(elements => elements.map(el => el.getBoundingClientRect().left))
  expect(Math.max(...starts) - Math.min(...starts)).toBeLessThanOrEqual(2)
  await input.fill('复核')
  await form.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '申请状态' }) }).click()
  await page.getByRole('option', { name: '已通过', exact: true }).click()
  await search.click()
  await expect.poll(() => calls.at(-1)).toMatchObject({ taskScope: 'COMPLETED', status: 'APPROVED', keyword: '复核' })
  await form.getByRole('button', { name: '重置' }).click()
  await expect(input).toHaveValue('')
  await expect.poll(() => calls.at(-1).status).toBeUndefined()
  const box = await form.boundingBox()
  expect(box.x + box.width).toBeLessThanOrEqual(390)
  assertClean()
})
