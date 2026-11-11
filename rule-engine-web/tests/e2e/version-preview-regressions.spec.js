const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDocsApiData } = require('./support/docsFixtures.cjs')
const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
test.setTimeout(30000)

async function capture(page, name, observations) {
  await page.screenshot({ path: test.info().outputPath(`${name}.png`), fullPage: true })
  await test.info().attach(name, { body: JSON.stringify(observations, null, 2), contentType: 'application/json' })
}
function approvalData(overrides = {}) {
  return { request: { id: '900', requestNo: 'AUDIT-900', resourceType: 'VARIABLE', resourceId: '9001',
    action: 'CREATE', status: 'EDITING', applicant: 'e2e', baseVersionId: null, baseVersionNo: null,
    draftSnapshotJson: JSON.stringify({ varCode: 'audit_value', varLabel: '审查样例', varType: 'STRING', varSource: 'INPUT' }),
    validationReportJson: null, ...overrides },
    diff: { fields: [{ key: '$.varCode', changed: true, changeType: 'ADDED', leftValue: null, rightValue: 'audit_value' }] },
    dependencies: [], versions: [], events: [] }
}
async function openApproval(page, detail) {
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL),
    apiData: new Map([['/api/rule/governance/requests/900', detail], ['/api/rule/lineage/graph', { nodes: [], edges: [] }]]) })
  await page.goto(`${origin}/#/approval/900`)
  await expect(page.locator('.request-number')).toHaveText('AUDIT-900')
  return harness
}

test('审查预期：名单落库目标和内容改变必须显示业务差异', async ({ page }) => {
  const data = createDocsApiData()
  const rule = data.get('/api/rule/definition/list').records[0]
  const snapshot = (listId, value) => JSON.stringify({ ruleName: '名单落库审查', modelType: 'RULE_SET', content: {
    modelJson: JSON.stringify({ executionMode: 'SERIAL', rules: [{ ruleCode: 'R1', ruleName: '名单写入', priority: 1, enabled: true,
      conditionRoot: { type: 'group', op: 'AND', children: [] }, actionData: [{ type: 'list-write', listIds: [listId], operation: 'MERGE',
        expiryMode: 'DURATION', duration: 30, durationUnit: 'DAYS', entries: [{ itemType: 'MOBILE', valueOperand: { kind: 'LITERAL', value, valueType: 'STRING' } }] }] }] }) } })
  data.set(`/api/rule/resource-versions/RULE/${rule.id}`, { latestVersionId: '22', drafts: [],
    versions: [{ versionId: '22', version: 2, revision: 1, latest: true }, { versionId: '21', version: 1, revision: 1 }],
    history: [{ id: '22', versionNo: 2, revisionNo: 1, snapshotJson: snapshot('202', '13900000002') }, { id: '21', versionNo: 1, revisionNo: 1, snapshotJson: snapshot('101', '13800000001') }] })
  data.set(`/api/rule/resource-versions/RULE/${rule.id}/events`, [])
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: data })
  await page.goto(`${origin}/#/rule`)
  await page.getByRole('button', { name: '版本', exact: true }).first().click()
  const drawer = page.getByRole('dialog', { name: '版本管理', exact: true })
  await expect(drawer.locator('.version-preview-summary')).toContainText('名单落库审查')
  await drawer.locator('.version-comparison-toolbar .el-select').click()
  await page.getByRole('option', { name: 'v1 · 修订 1', exact: true }).click()
  const preview = drawer.locator('.version-preview-summary')
  await preview.locator('.preview-title').click()
  await capture(page, '01-list-write', { text: await preview.innerText() })
  await preview.locator('[data-section="rule:rules"]').scrollIntoViewIfNeeded()
  await page.screenshot({ path: test.info().outputPath('list-write-details.png') })
  harness.assertClean()
  await expect(preview).not.toContainText('预览内容一致', { timeout: 1000 })
  await expect(preview).toContainText('名单 #101')
  await expect(preview).toContainText('名单 #202')
  await expect(preview).toContainText('13800000001')
  await expect(preview).toContainText('13900000002')
  await expect(preview).toContainText('加入并合并有效期')
  await expect(preview).not.toContainText('赋值目标')
  await expect(preview.locator('.snapshot-branch')).toHaveCount(0)
})

test('审查预期：新建资源不得将自身比较后标为内容一致', async ({ page }) => {
  const harness = await openApproval(page, approvalData())
  await capture(page, '02-create-comparison', { comparison: await page.locator('.diff-summary').innerText(), selectors: await page.locator('.version-selector-row').innerText() })
  harness.assertClean()
  await expect(page.locator('.diff-summary')).not.toHaveText('内容一致', { timeout: 1000 })
  await expect(page.locator('.version-selector-left')).toContainText('空配置（新资源）')
  await expect(page.locator('.diff-row')).toHaveCount(0)
  await page.getByRole('button', { name: '字段变更明细（1 项）' }).click()
  await expect(page.locator('.diff-row')).toContainText('audit_value')
})

test('审查预期：尚无预检报告的草稿不得显示检查通过', async ({ page }) => {
  const harness = await openApproval(page, approvalData())
  await capture(page, '03-unchecked', { overview: await page.locator('.overview-grid').innerText(), explanation: await page.locator('.release-explanation').innerText() })
  harness.assertClean()
  await expect(page.locator('.overview-grid')).not.toContainText('检查通过', { timeout: 1000 })
  await expect(page.locator('.overview-grid')).toContainText('尚未预检')
})

test('审查预期：同一版本不同修订在审批对比选择器中可区分', async ({ page }) => {
  const detail = approvalData({ action: 'UPDATE', status: 'PENDING', baseVersionId: '21', baseVersionNo: 1, submittedSnapshotJson: '{"varLabel":"新内容"}' })
  detail.versions = [{ id: '22', versionNo: 1, revisionNo: 2, snapshotJson: '{"varLabel":"第二修订"}' }, { id: '21', versionNo: 1, revisionNo: 1, snapshotJson: '{"varLabel":"第一修订"}' }]
  const harness = await openApproval(page, detail)
  await page.locator('.version-selector-left').click()
  const labels = await page.getByRole('option').allTextContents()
  await capture(page, '04-revision-labels', { labels })
  harness.assertClean()
  expect(new Set(labels).size).toBe(labels.length)
  expect(labels).toEqual(expect.arrayContaining(['历史生效 V1 · 修订 1', '历史生效 V1 · 修订 2']))
})

test('审查预期：配置内的业务 id 参数不能当成元数据过滤', async ({ page }) => {
  const data = createDocsApiData()
  const snapshot = { varCode: 'query_result', varLabel: '查询参数审查', varType: 'STRING', varSource: 'API', sourceConfig: { apiConfigId: 1, requestOverrides: { customer: { kind: 'LITERAL', valueType: 'OBJECT', value: { id: 'customer-42', createTime: '2026-01-01' } } } } }
  data.set('/api/rule/variable/list', { records: [{ ...snapshot, id: '9001', status: 1, scope: 'GLOBAL' }], total: 1 })
  data.set('/api/rule/resource-versions/VARIABLE/9001', { latestVersionId: '51', versions: [{ versionId: '51', version: 1, revision: 1, latest: true }], history: [{ id: '51', versionNo: 1, revisionNo: 1, snapshotJson: JSON.stringify(snapshot) }], drafts: [] })
  data.set('/api/rule/resource-versions/VARIABLE/9001/events', [])
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: data })
  await page.goto(`${origin}/#/variable`)
  await page.getByRole('button', { name: '版本', exact: true }).first().click()
  const preview = page.getByRole('dialog', { name: '版本管理', exact: true }).locator('.version-preview-summary')
  await expect(preview).toContainText('查询参数审查')
  await capture(page, '05-business-id', { text: await preview.innerText() })
  harness.assertClean()
  await expect(preview).toContainText('customer-42', { timeout: 1000 })
})

test('审查预期：快速切换审批页签应保留最后一次查询结果', async ({ page }) => {
  let releaseMine
  const mineGate = new Promise(resolve => { releaseMine = resolve })
  let mineRequested = false
  const data = new Map([
    ['/api/rule/governance/requests/summary', { pendingCount: 1, myDraftCount: 1, myRequestCount: 1, completedCount: 1 }],
    ['/api/rule/governance/requests', async ({ url }) => {
      const scope = url.searchParams.get('taskScope')
      if (scope === 'MINE') { mineRequested = true; await mineGate }
      return { records: [{ id: scope === 'MINE' ? '901' : '902', requestNo: `AUDIT-${scope}`, resourceType: 'VARIABLE', action: 'UPDATE',
        status: scope === 'COMPLETED' ? 'APPROVED' : 'EDITING', applicant: 'e2e', draftSnapshotJson: JSON.stringify({ varLabel: `${scope}查询结果` }) }], total: 1 }
    }],
  ])
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: data })
  await page.goto(`${origin}/#/approval`)
  await expect(page.locator('.approval-table')).toContainText('PENDING查询结果')
  await page.getByRole('tab', { name: '我的申请', exact: true }).click()
  await expect.poll(() => mineRequested).toBe(true)
  await page.getByRole('tab', { name: '已结束', exact: true }).click()
  await expect(page.locator('.approval-table')).toContainText('COMPLETED查询结果')
  const oldResponse = page.waitForResponse(response => response.url().includes('/governance/requests?') && response.url().includes('taskScope=MINE'))
  releaseMine()
  await (await oldResponse).finished()
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
  await capture(page, '06-response-race', { activeTab: await page.getByRole('tab', { selected: true }).innerText(), table: await page.locator('.approval-table').innerText() })
  harness.assertClean()
  await expect(page.locator('.approval-table')).toContainText('COMPLETED查询结果', { timeout: 1000 })
})
