const { test, expect } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')
const { createManagementApiData } = require('./support/managementFixtures.cjs')
const { createOperationsApiData } = require('./support/operationsFixtures.cjs')

function fixtures() { return new Map([...createDetailApiData(), ...createManagementApiData(), ['/api/rule/sftp/list', { records: [], total: 0 }]]) }
const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
async function prepare(page, data = fixtures()) {
  return installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: data })
}

test('回归报告保留历史结论，并按当前修订显示证据过期', async ({ page }, testInfo) => {
  const data = createOperationsApiData()
  const row = { id: '91', scenarioName: '边界样例', revisionId: '41', eligible: 1, state: 'COMPLETED', validationStatus: 'PASSED',
    executedBy: 'e2e', createTime: '2026-10-23 12:00:00', executionIdentityJson: '{"revisionNo":1,"revisionState":"DRAFT"}' }
  data.set('/api/rule/definition/101/api-scenarios/reports', { records: [row], total: 1 })
  data.set('/api/rule/definition/101/api-scenarios/reports/91', { ...row, message: '校验通过', validationJson: '{"status":"PASSED","checks":[{"label":"输出值","message":"符合预期"}],"diffs":[]}' })
  data.set('POST /api/rule/definition/101/api-scenarios/reports/check', ({ request }) => {
    expect(String(request.postDataJSON().revisionId)).toBe('41')
    return { allowed: false, requiredCount: 1, passedCount: 0, staleCount: 1, message: '当前依赖已变化，请重新回归', cases: [{ scenarioId: '7', reportId: '91', status: 'STALE' }] }
  })
  const harness = await prepare(page, data)
  await page.goto(`${origin}/#/test`)
  await page.locator('.test-left .el-form-item').filter({ hasText: '规则' }).locator('.el-select').click()
  await page.getByRole('option', { name: /age_rule/ }).click()
  await page.getByTestId('rule-test-source').click()
  await page.getByRole('option', { name: /草稿\s*1/ }).click()
  await expect(page.getByText('测试输入已就绪', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '回归报告', exact: true }).click()
  const drawer = page.getByRole('dialog', { name: '回归报告', exact: true })
  await expect(drawer.getByText('历史报告，待复核', { exact: true })).toBeVisible()
  await drawer.getByRole('button', { name: '复核当前修订', exact: true }).click()
  await expect(drawer.getByText('已过期', { exact: true })).toBeVisible()
  await drawer.getByRole('button', { name: '查看', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '回归报告详情', exact: true })).toContainText('输出值：符合预期')
  await page.screenshot({ path: testInfo.outputPath('regression-report.png'), fullPage: true })
  harness.assertClean()
})

test('数据库明确清除凭据参与试连与草稿保存，旧成功提示立即失效', async ({ page }) => {
  const data = createOperationsApiData()
  data.set('/api/rule/database/51', { id: 51, scope: 'GLOBAL', projectId: 0, datasourceCode: 'clear_db', datasourceName: '清除验收库',
    dbType: 'MYSQL', jdbcUrl: 'jdbc:mysql://fixture/risk', username: 'reader', configuredSecrets: { password: true } })
  const trials = [], drafts = []
  data.set('POST /api/rule/database/test', ({ request }) => { trials.push(request.postDataJSON()); return '连接成功' })
  data.set('POST /api/rule/governance/drafts', ({ request }) => { drafts.push(JSON.parse(request.postDataJSON().snapshotJson)); return { id: 91, resourceId: 51, resourceType: 'DATABASE', status: 'EDITING' } })
  const harness = await prepare(page, data)
  await page.goto(`${origin}/#/database/51`)
  await page.getByRole('button', { name: '测试连接', exact: true }).click()
  await expect(page.getByText('连接测试成功', { exact: true })).toBeVisible()
  await page.getByText('清除已存密码', { exact: true }).click()
  await expect(page.getByRole('textbox', { name: '密码', exact: true })).toBeDisabled()
  await expect(page.getByText('连接配置已变更，请重新测试', { exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: '测试连接', exact: true }).click()
  await expect.poll(() => trials.length).toBe(2)
  expect(trials[1]).toMatchObject({ password: '', clearCredentials: ['password'] })
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => drafts.length).toBe(1)
  expect(drafts[0]).toMatchObject({ password: '', clearCredentials: ['password'] })
  harness.assertClean()
})

for (const withSnapshot of [false, true]) {
  test(`历史日志只展示执行时快照（已保存=${withSnapshot}）`, async ({ page }, testInfo) => {
    const data = createOperationsApiData()
    const row = data.get('/api/rule/log/list').records[0]
    row.rootRuleId = 101
    row.ruleVersion = 1
    row.traceInfo = withSnapshot ? JSON.stringify([{
      schemaVersion: 2, traceKind: 'RULE', ruleId: 101, ruleCode: 'age_rule',
      ruleName: '执行时规则 V1', modelType: 'TABLE', status: 'SUCCESS',
      modelJson: JSON.stringify({ rows: [], hitPolicy: 'FIRST' }),
      expressionTrace: [], events: [], children: [], durationMs: 6
    }]) : JSON.stringify([{
      type: 'OPERATOR', token: '=', evaluated: true, value: 42,
      children: [{ type: 'VARIABLE', token: 'audit_old_score', evaluated: true, value: 42 },
        { type: 'VALUE', evaluated: true, value: 42 }]
    }])
    if (!withSnapshot) row.modelType = 'SCRIPT'
    data.set('/api/rule/definition/content/101', { modelJson: '{"rows":[],"hitPolicy":"ALL"}' })
    const harness = await prepare(page, data)
    await page.goto(`${origin}/#/log`)
    await page.getByRole('row').filter({ hasText: 'trace_rule_001' }).getByRole('button', { name: '详情', exact: true }).click()
    const detail = page.getByRole('dialog', { name: '日志详情', exact: true })
    await detail.getByRole('tab', { name: '表达式追踪树', exact: true }).click()
    if (withSnapshot) {
      await expect(detail.getByText('执行时规则 V1', { exact: true })).toBeVisible()
      await expect(detail.getByText(/未保存执行时的设计快照/)).toHaveCount(0)
    } else {
      await expect(detail.getByText(/未保存执行时的设计快照/)).toBeVisible()
      await expect(detail.locator('.trace-wrap').first()).toContainText('audit_old_score')
    }
    expect(harness.requests.some(request => new URL(request.url).pathname === '/api/rule/definition/content/101')).toBe(false)
    await page.screenshot({ path: testInfo.outputPath(`historical-snapshot-${withSnapshot}.png`), fullPage: true })
    harness.assertClean()
  })
}

test('数据库未保存配置在导航取消后完整保留，确认后才离开', async ({ page }) => {
  const harness = await prepare(page)
  await page.goto(`${origin}/#/database/new`)
  await page.getByRole('textbox', { name: /数据源名称$/ }).fill('未保存连接')
  await page.getByRole('button', { name: '项目管理', exact: true }).click()
  const reminder = page.getByRole('dialog', { name: '未保存提醒', exact: true })
  await expect(reminder).toBeVisible()
  await reminder.getByRole('button', { name: '继续编辑', exact: true }).click()
  await expect(page).toHaveURL(/#\/database\/new$/)
  await expect(page.getByRole('textbox', { name: /数据源名称$/ })).toHaveValue('未保存连接')
  await page.getByRole('button', { name: '项目管理', exact: true }).click()
  await reminder.getByRole('button', { name: '继续离开', exact: true }).click()
  await expect(page).toHaveURL(/#\/project$/)
  harness.assertClean()
})

test('项目远程搜索可选择第一页以外的项目并保留 ID', async ({ page }) => {
  const data = fixtures()
  const seen = []
  data.set('/api/rule/project/list', ({ url }) => {
    seen.push(Object.fromEntries(url.searchParams))
    return url.searchParams.get('keyword') === '末尾项目'
      ? { records: [{ id: 1001, projectName: '末尾项目', projectCode: 'Keep_Code', status: 1 }], total: 1 }
      : { records: [{ id: 1, projectName: '第一页', projectCode: 'first', status: 1 }], total: 1001 }
  })
  let submitted
  data.set('POST /api/rule/governance/drafts', ({ request }) => { submitted = request.postDataJSON(); return { id: 90 } })
  const harness = await prepare(page, data)
  await page.goto(`${origin}/#/sftp`)
  await page.getByRole('button', { name: '新建 SFTP 数据源', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '新建 SFTP 数据源', exact: true })
  const project = dialog.locator('.scope-project-fields .el-select').last()
  await project.click()
  await project.locator('input').fill('末尾项目')
  await page.getByRole('option', { name: '末尾项目 · Keep_Code', exact: true }).click()
  for (const [label, value] of [['数据源编码', 'Keep_Sftp'], ['数据源名称', '分页选择测试'], ['主机', 'fixture.invalid'], ['用户名', 'fixture']]) {
    await dialog.getByRole('textbox', { name: new RegExp(label + '$') }).fill(value)
  }
  await dialog.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect(dialog).toBeHidden()
  expect(JSON.parse(submitted.snapshotJson).projectId).toBe(1001)
  expect(seen).toContainEqual(expect.objectContaining({ keyword: '末尾项目', pageSize: '20' }))
  harness.assertClean()
})

test('SFTP 项目和池参数有误时保留表单并阻止提交', async ({ page }) => {
  const data = fixtures()
  data.set('/api/rule/sftp/list', { records: [], total: 0 })
  const harness = await prepare(page, data)
  await page.goto(`${origin}/#/sftp`)
  await page.getByRole('button', { name: '新建 SFTP 数据源', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '新建 SFTP 数据源', exact: true })
  for (const [label, value] of [['数据源编码', 'Keep_Sftp'], ['数据源名称', '校验测试'], ['主机', 'fixture.invalid'], ['用户名', 'fixture']]) {
    await dialog.getByRole('textbox', { name: new RegExp(label + '$') }).fill(value)
  }
  await dialog.getByRole('spinbutton', { name: '最小空闲', exact: true }).fill('9')
  await dialog.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect(dialog.getByText('请选择所属项目', { exact: true })).toBeVisible()
  await expect(dialog.getByText('最小空闲连接数不能超过最大连接数', { exact: true })).toBeVisible()
  expect(harness.requests.filter(item => item.method === 'POST' && item.url.endsWith('/governance/drafts'))).toEqual([])
  harness.assertClean()
})

test('批量测试可停止后续请求并保留当前结果及进度', async ({ page }) => {
  const data = createOperationsApiData()
  data.set('/api/rule/definition/101/api-scenarios', [
    { id: 1, scenarioName: '停止测试一', requestJson: '{"params":{"age":18}}', responseJson: '{}' },
    { id: 2, scenarioName: '停止测试二', requestJson: '{"params":{"age":19}}', responseJson: '{}' },
  ])
  const key = 'POST /api/rule/definition/101/api-scenarios/execute-validated'
  const execute = data.get(key)
  let release, calls = 0
  data.set(key, async context => { calls++; await new Promise(resolve => { release = resolve }); return execute(context) })
  const harness = await prepare(page, data)
  await page.goto(`${origin}/#/test`)
  await page.locator('.test-left .el-form-item').filter({ hasText: '规则' }).locator('.el-select').click()
  await page.getByRole('option', { name: /age_rule/ }).click()
  await expect(page.getByText('测试输入已就绪', { exact: true })).toBeVisible()
  await page.getByText('停止测试一', { exact: true }).click()
  await page.getByText('停止测试二', { exact: true }).click()
  await page.getByRole('button', { name: '批量执行', exact: true }).click()
  await expect.poll(() => calls).toBe(1)
  await page.getByRole('button', { name: '停止后续', exact: true }).click()
  await expect(page.getByRole('button', { name: '停止后续', exact: true })).toBeDisabled()
  release()
  await expect(page.getByRole('button', { name: '停止后续', exact: true })).toHaveCount(0)
  await expect(page.getByText(/已完成 1 \/ 2 个用例/)).toBeVisible()
  await expect(page.locator('.batch-result-table').getByRole('row').filter({ hasText: '停止测试一' })).toBeVisible()
  expect(calls).toBe(1)
  harness.assertClean()
})
