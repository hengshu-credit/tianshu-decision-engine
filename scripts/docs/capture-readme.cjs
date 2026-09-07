const fs = require('node:fs')
const assert = require('node:assert/strict')
const path = require('node:path')
const { createRequire } = require('node:module')
const requireUi = createRequire(path.resolve(__dirname, '../../rule-engine-builder-ui/package.json'))
const { chromium, expect } = requireUi('@playwright/test')
const { installDistRoutes } = require('../../rule-engine-builder-ui/tests/e2e/support/distRoutes.cjs')
const { createDocsApiData } = require('../../rule-engine-builder-ui/tests/e2e/support/docsFixtures.cjs')
const root = path.resolve(__dirname, '../..')
const output = path.join(root, 'docs/readme')
const examples = JSON.parse(fs.readFileSync(path.join(root, 'rule-engine-core/target/docs/executed-examples.json'), 'utf8'))
for (const example of examples) assert.deepEqual(example.execution.result, example.expectedOutput, example.ruleCode)
fs.mkdirSync(output, { recursive: true })
const captures = []

function fixtures(example) {
  const data = createDocsApiData()
  const scoreVariable = { id: 15, projectId: 1, varCode: 'totalScore', scriptName: 'totalScore', varLabel: '核验总分', varType: 'NUMBER', varSource: 'INPUT', status: 1 }
  const vars = data.get('/api/rule/variable/project/1')
  if (Array.isArray(vars)) vars.push(scoreVariable)
  for (const item of examples) {
    data.set(`/api/rule/definition/${item.id}`, item)
    data.set(`/api/rule/definition/content/${item.id}`, { definitionId: item.id, modelJson: item.modelJson, scriptMode: item.slug === 'script' ? 'script' : 'visual' })
    const revision = { id: 2000 + item.id, definitionId: item.id, revisionNo: 1, state: 'DRAFT', lockVersion: 0, modelJson: item.modelJson }
    data.set(`/api/rule/definition/${item.id}/revisions`, [revision])
    data.set(`/api/rule/definition/${item.id}/revisions/${revision.id}`, revision)
    data.set(`/api/rule/definition/versions/${item.id}`, [])
    data.set(`/api/rule/definition/${item.id}/api-scenarios`, [])
  }
  if (example) {
    const fields = Object.entries(example.params).map(([code, value]) => ({ fieldName: code, scriptName: code, fieldType: typeof value === 'number' ? 'NUMBER' : 'STRING', defaultValue: String(value) }))
    const selected = { ...example, status: 1, currentVersion: 1, inputFields: fields, outputFields: [] }
    data.set('/api/rule/definition/list', { records: [selected], total: 1 })
    data.set('/api/rule/definition/project-list/1', { records: [selected], total: 1 })
    data.set(`/api/rule/definition/detail/${example.id}`, selected)
    data.set(`/api/rule/definition/inputFields/${example.id}`, fields)
    data.set(`/api/rule/definition/outputFields/${example.id}`, [])
    data.set('POST /api/rule/test-schema', { inputs: fields.map(field => ({ ...field, code: field.fieldName, valueType: field.fieldType })), outputs: [], sampleParams: example.params, diagnostics: [] })
    data.set('POST /api/rule/definition/execute', ({ request }) => {
      const body = request.postDataJSON()
      if (Number(body.definitionId) !== example.id) throw new Error('Unexpected rule execution')
      assert.deepEqual(body.params, example.params, 'UI inputs must match the executed example')
      return example.execution
    })
  }
  data.set('/api/rule/console/accounts', [
    { id: 1, username: 'strategy_editor', displayName: '策略配置员', roleCodes: ['STRATEGY_EDITOR'], effectivePermissions: ['rule:view', 'rule:edit', 'field:view'], status: 1 },
    { id: 2, username: 'risk_reviewer', displayName: '风险审核员', roleCodes: ['RISK_REVIEWER'], effectivePermissions: ['rule:view', 'approval:view', 'approval:approve'], status: 1 }
  ])
  data.set('/api/rule/console/roles', [
    { id: 1, roleCode: 'STRATEGY_EDITOR', roleName: '策略配置', permissions: ['rule:view', 'rule:edit', 'field:view'], status: 1 },
    { id: 2, roleCode: 'RISK_REVIEWER', roleName: '风险审核', permissions: ['rule:view', 'approval:view', 'approval:approve'], status: 1 }
  ])
  data.set('/api/rule/console/permissions', [])
  data.set('/api/rule/governance/requests', { records: [{ id: 1, resourceId: 101, requestNo: 'DEMO-20260907-001', resourceType: 'RULE', action: 'UPDATE', status: 'PENDING', applicant: 'strategy_editor', submitTime: '2026-09-07 10:00:00', submittedSnapshotJson: JSON.stringify({ ruleName: '人脸阈值决策表', ruleCode: 'face_threshold_table' }) }], total: 1 })
  data.set('/api/rule/governance/requests/summary', { pendingCount: 1, myDraftCount: 0, myRequestCount: 1, completedCount: 0 })
  return data
}

async function main() {
  const browser = await chromium.launch()
  try {
    const capture = async (name, route, action, example, customize) => {
      const page = await browser.newPage({ viewport: { width: 1720, height: 1120 } })
      const data = fixtures(example)
      if (customize) customize(data)
      const checks = await installDistRoutes(page, { apiData: data })
      try {
        await page.goto(`http://tianshu.local/index.html#${route}`)
        await expect(page.locator('#app').first()).not.toBeEmpty()
        if (action) await action(page)
        await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
        await expect(page.locator('.el-message:visible')).toHaveCount(0)
        await page.waitForTimeout(900)
        checks.assertClean()
        await page.screenshot({ path: path.join(output, `${name}.png`), animations: 'disabled' })
        captures.push(name)
        console.log(`CAPTURE ${name}`)
      } catch (error) {
        await page.screenshot({ path: path.join(root, 'rule-engine-core/target/docs/failed.png') })
        fs.writeFileSync(path.join(root, 'rule-engine-core/target/docs/failed.txt'), await page.locator('body').innerText())
        console.error(checks.unmatchedRequests, checks.pageErrors)
        throw error
      } finally { await page.close() }
    }
    const basic = [
      ['dashboard', '/dashboard'], ['project', '/project'], ['project-detail', '/project/1'],
      ['rules', '/rule'], ['variable', '/variable'], ['lists', '/list'], ['datasource', '/datasource'],
      ['database', '/database'], ['models', '/model'], ['functions', '/function'],
      ['experiment', '/experiment'], ['logs', '/log'], ['billing', '/billing'],
      ['account', '/account'], ['approval', '/approval']
    ]
    for (const [name, route] of basic) await capture(name, route)
    await capture('login', '/login', page => expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible(), null, data => data.set('/api/auth/console/config', { loginEnabled: true }))
    await capture('data-object', '/variable', async page => {
      await page.getByRole('tab', { name: '数据对象', exact: true }).click()
      await page.getByRole('row').filter({ hasText: 'FaceVerifyRequest' }).locator('.el-table__expand-icon').click()
      await expect(page.getByText('deviceId', { exact: true }).first()).toBeVisible()
    })
    await capture('lineage', '/lineage', async page => {
      await page.locator('.query-panel .el-form-item').filter({ hasText: '起点' }).locator('.el-select').click()
      await page.getByRole('option', { name: '人脸图片地址 (faceImageUrl)' }).click()
      await page.getByRole('button', { name: '生成血缘图', exact: true }).click()
      await expect(page.getByText('face_identity_rule', { exact: true })).toBeVisible()
    })
    const openTheme = async page => {
      await page.locator('.layout-account-trigger').click()
      await page.locator('[data-account-command="theme"]').click()
      await expect(page.getByRole('dialog', { name: '主题设置' })).toBeVisible()
    }
    await capture('theme-light', '/dashboard', openTheme)
    await capture('theme-dark', '/dashboard', async page => {
      await openTheme(page)
      await page.locator('[data-theme-scheme="DARK"]').click()
      await page.locator('[data-accent="LIQUID_PURPLE_GRADIENT"]').click()
    })
    await capture('theme-custom', '/dashboard', async page => {
      await openTheme(page)
      await page.locator('[data-accent-mode="CUSTOM_GRADIENT"]').click()
      await page.locator('[data-gradient-count="3"]').click()
      await page.locator('[data-gradient-type="RADIAL"]').click()
    })
    await capture('theme-top', '/dashboard', async page => {
      await openTheme(page)
      await page.locator('[data-navigation-layout="TOP"]').click()
      await page.locator('[data-action="save"]').click()
      await page.reload()
      await expect(page.locator('.top-navigation__menu')).toBeVisible()
    })
    for (const example of examples) {
      await capture(`rule-${example.slug}-config`, `/designer/${example.slug}/${example.id}`, async page => {
        await expect(page.getByRole('button', { name: '保存并检查', exact: true })).toBeVisible()
      }, example)
      await capture(`rule-${example.slug}-trace`, '/test', async page => {
        await page.locator('.test-left .el-form-item').filter({ hasText: /^规则/ }).locator('.el-select').click()
        await page.getByRole('option', { name: new RegExp(example.ruleCode) }).click()
        await page.getByRole('button', { name: '执行测试', exact: true }).click()
        await expect(page.getByText('执行成功', { exact: true })).toBeVisible()
        await page.getByRole('tab', { name: '表达式追踪树', exact: true }).click()
        await expect(page.locator('.trace-tree-wrap')).toBeVisible()
        if (example.slug === 'score') await expect(page.locator('.trace-tree-wrap').getByText('基础分', { exact: true })).toBeVisible()
      }, example)
    }
    fs.writeFileSync(path.join(output, 'capture-manifest.json'), JSON.stringify({ source: 'Current Vue production build; documentation API fixtures; nine rule traces from the local QLExpress engine', capturedAt: new Date().toISOString(), viewport: { width: 1720, height: 1120 }, images: captures.map(name => `${name}.png`) }, null, 2) + '\n')
    console.log(`PASS ${captures.length} screenshots, no unmatched API requests or browser errors`)
  } finally { await browser.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
