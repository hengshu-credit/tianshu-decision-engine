const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')
const { createManagementApiData } = require('./support/managementFixtures.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')

function fixtures() { return new Map([...createManagementApiData(), ...createDetailApiData()]) }

test('A08 工作台只读角色可查看规则，但不可进入无权限字段模块', async ({ page }) => {
  const data = fixtures()
  data.set('/api/auth/console/config', { loginEnabled: true })
  data.set('/api/auth/console/me', { username: 'reviewer', permissions: ['project:view', 'rule:view'] })
  data.set('/api/auth/console/preferences/theme', null)
  data.set('POST /api/rule/console/activity', true)
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/index.html#/project/1')
  await expect(page.locator('.quick-actions').getByRole('button', { name: '业务字段', exact: true })).toBeDisabled()
  await expect(page.getByText(/灰色入口表示当前账号没有对应模块/)).toBeVisible()
  await page.locator('.quick-actions').getByRole('button', { name: '规则设计', exact: true }).click()
  await expect(page).toHaveURL(/#\/rule\?projectId=1$/)
  assertClean()
})

test('A13 变量表通过字段设置控制可见列，保留关键列和行内操作', async ({ page }, testInfo) => {
  const { assertClean } = await installDistRoutes(page, { apiData: fixtures() })
  await page.setViewportSize({ width: 1280, height: 720 })
  await page.goto('http://tianshu.local/index.html#/variable?projectId=1')
  const table = page.locator('.var-list-section .el-table')
  const settings = page.locator('[data-table-column-settings="tianshu:table-columns:variable-list"]')
  for (const label of ['脚本名称', '默认值', '取值范围']) {
    await expect(table.locator('th').filter({ hasText: label })).toHaveCount(1)
  }
  await settings.getByRole('button', { name: '设置表格显示字段' }).click()
  await expect(page.getByRole('button', { name: '隐藏作用范围', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '隐藏变量编码', exact: true })).toBeDisabled()
  for (const label of ['脚本名称', '默认值', '取值范围']) {
    await page.getByRole('button', { name: `隐藏${label}`, exact: true }).click()
  }
  await page.getByRole('button', { name: '应用', exact: true }).click()
  for (const label of ['脚本名称', '默认值', '取值范围']) {
    await expect(table.locator('th').filter({ hasText: label })).toHaveCount(0)
  }
  await page.reload()
  await expect(table.locator('.el-table__body tr').first()).toBeVisible()
  for (const label of ['脚本名称', '默认值', '取值范围']) {
    await expect(table.locator('th').filter({ hasText: label })).toHaveCount(0)
  }
  await settings.getByRole('button', { name: '设置表格显示字段' }).click()
  await page.getByRole('button', { name: '显示脚本名称', exact: true }).click()
  await page.getByRole('button', { name: '应用', exact: true }).click()
  await expect(table.locator('th').filter({ hasText: '脚本名称' })).toHaveCount(1)
  await expect(table.locator('th').filter({ hasText: '默认值' })).toHaveCount(0)
  await expect(table.locator('th').filter({ hasText: '取值范围' })).toHaveCount(0)
  await expect(table.locator('th').filter({ hasText: '变量编码' })).toHaveCount(1)
  await expect(table.locator('th').filter({ hasText: '作用范围' })).toHaveCount(1)
  await expect(table.locator('.el-dropdown')).toHaveCount(0)
  const row = table.locator('.el-table__body tr').first()
  await expect(row.getByRole('button', { name: '转全局', exact: true })).toHaveCount(0)
  await row.getByRole('button', { name: '删除', exact: true }).click()
  const confirmation = page.getByRole('dialog', { name: '确认删除', exact: true })
  await expect(confirmation).toBeVisible()
  await confirmation.getByRole('button', { name: '取消', exact: true }).click()
  await page.screenshot({ path: testInfo.outputPath('variable-columns.png') })
  assertClean()
})

for (const entry of ['project/1', 'rule?projectId=1']) {
  test(`A14 ${entry} 创建后直接继续配置`, async ({ page }) => {
    const data = new Map([...fixtures(), ...createDesignerApiData()])
    data.set('/api/rule/definition/101', { id: 101, projectId: 1, scope: 'PROJECT', modelType: 'SCRIPT', ruleCode: 'Mixed_Case_New' })
    data.set('/api/rule/definition/101/revisions', [])
    data.set('/api/rule/definition/versions/101', [])
    data.set('/api/rule/definition/101/published-versions', [])
    data.set('/api/rule/definition/content/101', { definitionId: 101, modelJson: '{"script":""}' })
    let submitted
    let savedDraft
    data.set('POST /api/rule/definition/101/designer/drafts', ({ request }) => {
      savedDraft = request.postDataJSON()
      return { revision: { id: 901, definitionId: 101, state: 'DRAFT', lockVersion: 1, revisionNo: 1, modelJson: savedDraft.modelJson }, compileSuccess: true, issues: [] }
    })
    data.set('POST /api/rule/definition', ({ request }) => { submitted = request.postDataJSON(); return { id: 101 } })
    const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
    const { requests, assertClean } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: data })
    await page.goto(origin + '/index.html#/' + entry)
    if (entry.startsWith('project')) await page.getByRole('tab', { name: '项目规则', exact: true }).click()
    await page.getByRole('button', { name: '新建规则', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '新建规则' })
    await dialog.getByRole('textbox', { name: '规则编码' }).fill('Mixed_Case_New')
    await dialog.getByRole('textbox', { name: '规则名称' }).fill('继续配置验证')
    await dialog.locator('.el-form-item').filter({ hasText: '模型类型' }).locator('.el-select').click()
    await page.getByRole('option', { name: 'QL脚本', exact: true }).click()
    await dialog.getByRole('button', { name: '创建并继续配置', exact: true }).click()
    await expect(page).toHaveURL(/#\/designer\/script\/101\?projectId=1$/)
    expect(submitted.ruleCode).toBe('Mixed_Case_New')
    await expect(page.getByRole('button', { name: '保存', exact: true })).toBeVisible()
    await page.locator('.se-var-item').filter({ hasText: 'age' }).first().dblclick()
    await expect(page.getByRole('textbox', { name: /Editor content/ })).toHaveValue('age')
    expect(requests.filter(request => request.method === 'POST')).toHaveLength(1)
    expect(savedDraft).toBeUndefined()
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.getByTestId('designer-info').getByRole('status')).toContainText('草稿已保存')
    expect(JSON.parse(savedDraft.modelJson).script).toBe('age')
    expect(savedDraft.sourceType).toBeUndefined()
    expect(savedDraft.sourceId).toBeUndefined()
    await expect(page).toHaveURL(/projectId=1.*sourceType=REVISION.*sourceId=901/)
    assertClean()
  })
}

test('A09 A11 数据库字段先保存草稿，显式发布才提交审批', async ({ page }, testInfo) => {
  const data = fixtures()
  const age = { id: 1, projectId: 1, scope: 'PROJECT', status: 1, varCode: 'age', scriptName: 'age', varLabel: '年龄', varType: 'INTEGER', varSource: 'INPUT' }
  data.set('/api/rule/variable/project/1', [age])
  data.set('/api/rule/dataobject/tree/1', [])
  data.set('/api/rule/function/project/1/all', [])
  data.set('/api/rule/model/project/1/all', [])
  data.set('/api/rule/variable/source-options', { apiOptions: [], databaseOptions: [{ id: 31, code: 'risk_mysql', name: '风控只读库', scope: 'PROJECT', projectId: 1 }], listOptions: [] })
  let preview
  let submitted
  let draft
  data.set('POST /api/rule/variable/preview', ({ request }) => {
    preview = request.postDataJSON()
    return { resolvedValue: 88, databaseRows: [{ score: 88, level: 'A' }] }
  })
  data.set('POST /api/rule/governance/drafts', ({ request }) => {
    submitted = request.postDataJSON()
    draft = { ...submitted, id: 991, resourceId: 301, status: 'EDITING', draftSnapshotJson: submitted.snapshotJson, snapshotDigest: 'shown-draft' }
    return draft
  })
  data.set('/api/rule/governance/workspaces', () => ({ records: draft ? [draft] : [], total: draft ? 1 : 0 }))
  data.set('POST /api/rule/governance/requests/991/submit', ({ request }) => {
    expect(request.postDataJSON().expectedSnapshotDigest).toBe('shown-draft')
    draft = { ...draft, status: 'PENDING', submittedSnapshotJson: draft.draftSnapshotJson }
    return draft
  })
  data.set('/api/rule/governance/requests/991', () => ({ request: draft, versions: [], dependencies: [], events: [], diff: { fields: [] } }))
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.setViewportSize({ width: 1280, height: 720 })
  await page.goto('http://tianshu.local/index.html#/variable?projectId=1')
  await page.getByRole('button', { name: '新建字段', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '新建字段' })
  await dialog.getByRole('button', { name: /数据库查询/ }).click()
  await dialog.locator('.el-form-item').filter({ hasText: '字段编码' }).locator('input').fill('Query_Score')
  await dialog.locator('.el-form-item').filter({ hasText: '字段名称' }).locator('input').fill('数据库评分')
  await dialog.getByPlaceholder('选择数据库', { exact: true }).click()
  await page.getByRole('dialog', { name: '内容选择器', exact: true }).getByRole('row').filter({ hasText: '风控只读库' }).dblclick()
  await dialog.locator('.el-form-item').filter({ hasText: '查询 SQL' }).getByRole('textbox', { name: /Editor content/ }).click()
  const sql = "/* query ; ? */ SELECT ? AS age, ? AS code;"
  await page.keyboard.insertText(sql)
  await dialog.getByRole('button', { name: '补齐参数', exact: true }).click()
  const rows = dialog.locator('.sql-param-row')
  await rows.nth(0).locator('.el-select').first().click()
  await page.getByRole('option', { name: '业务字段', exact: true }).click()
  const parameterPicker = rows.nth(0).locator('.operand-picker')
  await parameterPicker.locator('.vp-reference').click()
  const parameterPopover = page.locator('.fp-panel:visible')
  await parameterPopover.locator('.fp-row').filter({ has: page.locator('.content-ref__code', { hasText: /^age$/ }) }).locator('.content-ref__name').dblclick()
  await rows.nth(1).getByRole('textbox', { name: '参数 2 值' }).fill('001')
  await dialog.getByRole('textbox', { name: '样例 年龄' }).fill('28')
  await dialog.getByRole('button', { name: '预览取值', exact: true }).click()
  await expect(dialog.getByText('查询返回 1 行', { exact: true })).toBeVisible()
  await dialog.locator('.db-preview-rows .el-select').click()
  await page.getByRole('option', { name: '第 1 行 · score', exact: true }).click()
  await expect(dialog.locator('.el-form-item').filter({ hasText: '结果路径' }).locator('input')).toHaveValue('0.score')
  await dialog.getByRole('button', { name: '预览取值', exact: true }).click()
  await expect(dialog.getByText('查询返回 1 行', { exact: true })).toBeVisible()
  expect(preview.params).toEqual({ age: 28 })
  await page.screenshot({ path: testInfo.outputPath('database-variable-form.png') })
  await dialog.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect(dialog).toBeHidden()
  await expect(page).toHaveURL(/#\/variable\?projectId=1$/)
  expect(draft.status).toBe('EDITING')
  await page.getByRole('button', { name: '草稿与审批', exact: true }).click()
  await page.locator('[data-request-id="991"]').click()
  await page.getByRole('dialog', { name: '提交发布审批', exact: true }).getByRole('button', { name: '确定', exact: true }).click()
  const workspace = page.getByRole('dialog', { name: '草稿与审批', exact: true })
  await workspace.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '审批状态' }) }).click()
  await page.getByRole('option', { name: '审批中', exact: true }).click()
  await workspace.locator('[data-review-id="991"]').click()
  await expect(page).toHaveURL(/#\/approval\/991$/)
  await expect(page.locator('.header-meta')).toContainText('审批中')
  const variable = JSON.parse(submitted.snapshotJson)
  const config = JSON.parse(variable.sourceConfig)
  expect(variable.varCode).toBe('Query_Score')
  expect(config.sql).toBe(sql)
  expect(config.resultPath).toBe('0.score')
  expect(config.params[0]).toEqual(expect.objectContaining({ kind: 'REFERENCE', refId: 1, refType: 'VARIABLE' }))
  expect(config.params[1]).toEqual({ kind: 'LITERAL', valueType: 'STRING', value: '001' })
  assertClean()
})

test('A07 从校验报告进入确切修订并定位决策表规则行', async ({ page }, testInfo) => {
  const data = new Map([...fixtures(), ...createDesignerApiData()])
  const model = { rules: [{ conditionOperator: 'AND', conditions: [], actions: [] }] }
  const revision = { id: 2101, definitionId: 101, revisionNo: 1, state: 'DRAFT', lockVersion: 0, modelJson: JSON.stringify(model) }
  data.set('/api/rule/definition/detail/101', { ...fixtures().get('/api/rule/definition/detail/101'), modelType: 'TABLE' })
  data.set('/api/rule/definition/101/revisions', [revision])
  data.set('/api/rule/definition/101/revisions/2101', revision)
  data.set('/api/rule/definition/content/101', { definitionId: 101, modelJson: revision.modelJson })
  data.set('POST /api/rule/definition/101/revisions/2101/preflight', { revisionId: 2101, lockVersion: 0, valid: false, errors: [{ code: 'MISSING_REFERENCE_ID', message: '请重新选择条件字段', path: '$.rules[0].conditions[0]', revisionId: 2101 }] })
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/index.html#/rule/101')
  await page.getByRole('button', { name: '发布前校验', exact: true }).click()
  await page.getByRole('button', { name: '定位到规则设计', exact: true }).click()
  await expect(page).toHaveURL(/#\/designer\/table\/101\?.*sourceId=2101/)
  await expect(page.getByText(/校验定位 · 第 1 个规则行/)).toBeVisible()
  await page.getByRole('button', { name: '定位问题', exact: true }).click()
  await expect(page.locator('[data-validation-path="$.rules[0]"]')).toBeFocused()
  await page.screenshot({ path: testInfo.outputPath('validation-location.png') })
  assertClean()
})
