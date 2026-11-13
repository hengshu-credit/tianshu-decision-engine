const fs = require('node:fs')
const assert = require('node:assert/strict')
const path = require('node:path')
const { createRequire } = require('node:module')
const requireUi = createRequire(path.resolve(__dirname, '../../rule-engine-web/package.json'))
const { chromium, expect } = requireUi('@playwright/test')
const { installDistRoutes } = require('../../rule-engine-web/tests/e2e/support/distRoutes.cjs')
const { withTestExecutionIdentity } = require('../../rule-engine-web/tests/e2e/support/testExecutionFixtures.cjs')
const { platformResponse } = require('../../rule-engine-web/tests/e2e/support/platformResponse.cjs')
const { createDocsApiData } = require('../../rule-engine-web/tests/e2e/support/docsFixtures.cjs')
const { screenshotRoot, screenshotViewport, captureFileName, referencedScreenshots } = require('./screenshot-files.cjs')
const root = path.resolve(__dirname, '../..')
const publishedScreenshots = referencedScreenshots({ includeManual: false })
const examples = JSON.parse(fs.readFileSync(path.join(root, 'rule-engine-core/target/docs/executed-examples.json'), 'utf8'))
for (const example of examples) assert.deepEqual(example.execution.result, example.expectedOutput, example.ruleCode)
const output = fs.mkdtempSync(path.join(root, 'rule-engine-core/target/docs/gallery-'))
const captures = []

function configureExternalApiExample(data) {
  const endpoint = '/api/rule/datasource/api-config/22'
  const api = data.get(endpoint)
  const log = data.get('/api/rule/log/list').records[0]
  const call = JSON.parse(log.traceInfo)[0].events.find(event => event.moduleType === 'EXTERNAL_API' &&
    event.traceSteps?.some(step => step.refType === 'API' && step.refId === api.id))
  assert.equal(call.resourceCode, api.apiCode)
  const requestBody = call.traceSteps.find(step => step.type === 'REQUEST_PREPARE').input
  const mapped = call.traceSteps.find(step => step.type === 'RESPONSE_MAP').output
  const params = { faceImageUrl: requestBody.image_url, idCardNumber: requestBody.id_number }
  const variables = data.get('/api/rule/variable/project/1')
  const requestFields = [{ field: 'image_url', refId: 1 }, { field: 'id_number', refId: 2 }].map(({ field, refId }) => {
    const variable = variables.find(item => item.id === refId)
    return { id: field, location: 'JSON', path: field, required: true, overridable: true,
      value: { kind: 'REFERENCE', refType: 'VARIABLE', refId: variable.id, code: variable.varCode,
        label: variable.varLabel, valueType: variable.varType } }
  })
  const outputFields = Object.entries(JSON.parse(api.responseMapping)).map(([field, source]) => ({
    id: field, path: field, value: { kind: 'PATH', value: `response.${source}`, protocolPath: true }
  }))
  const sample = { id: 'paired-call', name: '供应商返回成功', params }
  api.executionConfig = JSON.stringify({
    version: 2, requestFields, requestSample: requestBody,
    responseBranches: [{ id: 'scores', name: '核验结果', mode: 'FIELDS', outputFields,
      condition: { type: 'group', operator: 'AND', children: [] },
      sample: { data: { liveness_score: mapped.livenessScore, similarity: mapped.faceSimilarity } } }],
    samples: [sample]
  })
  data.set(`POST ${endpoint}/invoke-preview`, ({ request }) => {
    const body = request.postDataJSON()
    assert.equal(body.config.apiCode, call.resourceCode)
    assert.equal(body.config.endpointUrl, api.endpointUrl)
    const fields = JSON.parse(body.config.executionConfig).requestFields
    assert.deepEqual(fields.map(field => [field.path, String(field.value.refId)]),
      requestFields.map(field => [field.path, String(field.value.refId)]))
    assert.deepEqual(body.params, params, 'API test must use the sample associated with this trace')
    return { success: true, callId: call.callId, traceId: call.traceId,
      costTimeMs: call.durationMs, body: mapped, traceSteps: call.traceSteps }
  })
}

function fixtures(example) {
  const data = createDocsApiData()
  const scoreVariable = { id: 15, projectId: 1, varCode: 'totalScore', scriptName: 'totalScore', varLabel: '核验总分', varType: 'NUMBER', varSource: 'INPUT', status: 1 }
  const vars = data.get('/api/rule/variable/project/1')
  if (Array.isArray(vars)) vars.push(scoreVariable)
  for (const variable of vars || []) data.set(`/api/rule/variable/picker-versions/${variable.id}`, [])
  if (example) {
    data.set(`/api/rule/definition/${example.id}`, example)
    data.set(`/api/rule/definition/content/${example.id}`, { definitionId: example.id, modelJson: example.modelJson, scriptMode: example.slug === 'script' ? 'script' : 'visual' })
    const revision = { id: 2000 + example.id, definitionId: example.id, revisionNo: 1, state: 'DRAFT', lockVersion: example.lockVersion || 0, modelJson: example.modelJson }
    data.set(`/api/rule/definition/${example.id}/revisions`, [revision])
    data.set(`/api/rule/definition/${example.id}/revisions/${revision.id}`, revision)
    data.set(`/api/rule/definition/versions/${example.id}`, [])
    data.set(`/api/rule/definition/${example.id}/api-scenarios`, [])
    if (['tree', 'flow'].includes(example.slug)) {
      data.set(`POST /api/rule/definition/${example.id}/designer/drafts`, ({ request }) => {
        const { modelJson } = request.postDataJSON()
        const saved = JSON.parse(modelJson)
        const original = JSON.parse(example.modelJson)
        // The traces use the compiled example; only carry the saved canvas positions across.
        const positions = new Map(saved.nodes.map(node => [node.id, [node.x, node.y]]))
        assert.deepEqual([...positions.keys()].sort(), original.nodes.map(node => node.id).sort())
        for (const node of original.nodes) [node.x, node.y] = positions.get(node.id)
        example.modelJson = JSON.stringify(original)
        example.lockVersion = revision.lockVersion + 1
        Object.assign(revision, { lockVersion: example.lockVersion, modelJson })
        return { revision, issues: [] }
      })
    }
    const fields = Object.entries(example.params).map(([code, value]) => ({ fieldName: code, scriptName: code, fieldType: typeof value === 'number' ? 'NUMBER' : 'STRING', defaultValue: String(value) }))
    const selected = { ...example, status: 1, currentVersion: 1, inputFields: fields, outputFields: [] }
    data.set('/api/rule/definition/list', { records: [selected], total: 1 })
    data.set('/api/rule/definition/project-list/1', { records: [selected], total: 1 })
    data.set(`/api/rule/definition/detail/${example.id}`, selected)
    data.set(`/api/rule/definition/inputFields/${example.id}`, fields)
    data.set(`/api/rule/definition/outputFields/${example.id}`, [])
    data.set('POST /api/rule/test-schema', withTestExecutionIdentity({
      inputs: fields.map(field => ({ ...field, code: field.fieldName, valueType: field.fieldType })),
      outputs: [], sampleParams: example.params, modelJson: example.modelJson, diagnostics: []
    }))
    data.set(`POST /api/rule/definition/${example.id}/api-scenarios/execute-validated`, async context => {
      const body = context.request.postDataJSON()
      assert.deepEqual(JSON.parse(body.requestJson).params, example.params, 'UI inputs must match the executed example')
      assert.equal(body.executionSource.sourceId, String(2000 + example.id), 'UI must execute the selected revision')
      const execution = await withTestExecutionIdentity(example.execution)(context)
      return { execution, responseJson: JSON.stringify(platformResponse(execution)), validation: { status: 'NOT_CONFIGURED' } }
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
  data.set('/api/rule/governance/requests', { records: [{ id: 1, resourceId: 101, requestNo: 'DEMO-20260930-001', resourceType: 'RULE', action: 'UPDATE', status: 'PENDING', applicant: 'strategy_editor', submitTime: '2026-09-30 10:00:00', submittedSnapshotJson: JSON.stringify({ ruleName: '人脸阈值决策表', ruleCode: 'face_threshold_table' }) }], total: 1 })
  data.set('/api/rule/governance/requests/summary', { pendingCount: 1, myDraftCount: 0, myRequestCount: 1, completedCount: 0 })
  return data
}

async function main() {
  const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL })
  try {
    const capture = async (name, route, action, example, customize) => {
      const fileName = captureFileName(name)
      if (!publishedScreenshots.has(fileName)) return
      const page = await browser.newPage({ viewport: screenshotViewport, deviceScaleFactor: 1 })
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
        await page.screenshot({ path: path.join(output, fileName), animations: 'disabled' })
        captures.push(fileName)
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
      ['rules', '/rule'], ['rule-detail', '/rule/101'], ['variable', '/variable'], ['lists', '/list'], ['datasource', '/datasource'],
      ['database', '/database'], ['models', '/model'], ['functions', '/function'],
      ['15-experiment-config', '/experiment/detail/61'], ['logs', '/log'], ['billing', '/billing'],
      ['transfer', '/transfer'],
      ['account', '/account'], ['approval', '/approval']
    ]
    for (const [name, route] of basic) await capture(name, route)
    await capture('login', '/login', page => expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible(), null, data => data.set('/api/auth/console/config', { loginEnabled: true }))
    await capture('data-object', '/variable', async page => {
      await page.getByRole('tab', { name: '数据对象', exact: true }).click()
      await page.getByRole('row').filter({ hasText: 'FaceVerifyRequest' }).locator('.el-table__expand-icon').click()
      await expect(page.getByText('deviceId', { exact: true }).first()).toBeVisible()
    })
    await capture('10-api-config', '/datasource/api/22?projectId=1', async page => {
      await expect(page.getByPlaceholder('如 查询征信报告')).toHaveValue('人脸活体与一致性检测')
      await page.getByRole('tab', { name: '请求字段', exact: true }).click()
      const editor = page.locator('.api-execution-editor:visible')
      await expect(editor.locator('.api-parameter-table:visible tbody tr')).toHaveCount(2)
      await expect(editor.getByPlaceholder('如 mobile 或 customer.mobile').first()).toHaveValue('image_url')
      await editor.scrollIntoViewIfNeeded()
      return editor
    }, null, configureExternalApiExample)
    await capture('10-api-execution-trace', '/datasource/api/22?projectId=1', async page => {
      await page.getByRole('tab', { name: '接口测试', exact: true }).click()
      await page.locator('.el-form-item').filter({ hasText: '选择测试样例' }).locator('.el-select').click()
      await page.getByRole('option', { name: '供应商返回成功', exact: true }).click()
      await page.getByRole('button', { name: '执行测试', exact: true }).click()
      const trace = page.getByRole('region', { name: '完整外数调用链' })
      await expect(trace.getByText('映射供应商响应', { exact: true })).toBeVisible()
      await expect(trace.getByText('供应商鉴权', { exact: true })).toBeVisible()
      await trace.evaluate(el => el.scrollIntoView({ block: 'start' }))
      return trace
    }, null, configureExternalApiExample)
    await capture('experiment-trace', '/experiment/detail/61', async page => {
      await page.getByRole('tab', { name: '分流日志', exact: true }).click()
      await expect(page.getByText('REQ-FACE-20260724093115-001', { exact: true })).toBeVisible()
      await page.getByRole('button', { name: '详情', exact: true }).last().click()
      const drawer = page.getByRole('dialog', { name: '分流日志详情' })
      const trace = drawer.locator('.experiment-trace-session')
      await expect(trace.locator('.experiment-routing-tree')).toBeVisible()
      await expect(trace.locator('.experiment-rule-execution')).toContainText('实际规则执行')
      await trace.evaluate(el => el.scrollIntoView({ block: 'start' }))
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
    const runTest = async (page, example) => {
      await page.locator('.test-left .el-form-item').filter({ hasText: /^规则/ }).locator('.el-select').click()
      await page.getByRole('option', { name: new RegExp(example.ruleCode) }).click()
      await page.getByRole('button', { name: '执行测试', exact: true }).click()
      await expect(page.getByText('执行成功', { exact: true }).first()).toBeVisible()
    }
    const showTrace = async (page, example) => {
      await runTest(page, example)
      await page.getByRole('tab', { name: '表达式追踪树', exact: true }).click()
      const projections = { table: '.rt-table', tree: '.dt-body .dtt', flow: '.fc-steps', ruleset: '.rs-table',
        cross: '.trace-cross-matrix', score: '.sc-table', 'cross-adv': '.trace-cross-matrix',
        'score-adv': '.sc-table', script: '.fc-steps, .trace-step-list' }
      const trace = page.locator('.trace-tabs')
      await expect(trace.getByRole('tab', { name: '表达式追踪树' })).toHaveAttribute('aria-selected', 'true')
      await expect(trace.locator(projections[example.slug]).first()).toBeVisible()
      if (example.slug === 'score') await expect(trace.getByText('基础分', { exact: true })).toBeVisible()
      await trace.evaluate(el => el.scrollIntoView({ block: 'nearest' }))
      return trace
    }
    for (const example of examples) {
      await capture(`rule-${example.slug}-config`, `/designer/${example.slug}/${example.id}?sourceType=REVISION&sourceId=${2000 + example.id}`, async page => {
        await expect(page.locator('.rule-designer-actions__compile')).toBeVisible()
        if (['tree', 'flow'].includes(example.slug)) {
          await page.getByRole('button', { name: '一键美化', exact: true }).click()
          await expect(page.getByText('画布已根据连线锚点自动排布，请点击保存后生效', { exact: true })).toBeVisible()
          await page.getByRole('button', { name: '保存', exact: true }).click()
          const dialog = page.getByRole('dialog', { name: '保存草稿' })
          await dialog.getByText('覆盖当前草稿', { exact: true }).click()
          await dialog.locator('[data-action="confirm-save"]').click()
          await expect(page.getByText('草稿已保存', { exact: true })).toBeVisible()
        }
        if (example.slug === 'table') await expect(page.locator('.dt-rule-card').first()).toBeVisible()
      }, example)
      await capture(`rule-${example.slug}-trace`, '/test', page => showTrace(page, example), example)
    }
    const images = captures.sort()
    const required = [...publishedScreenshots].filter(name => name.startsWith('project-usage-')).sort()
    assert.deepEqual(images, required, 'Every README screenshot must be captured in this run')
    for (const name of images) fs.copyFileSync(path.join(output, name), path.join(screenshotRoot, name))
    fs.writeFileSync(path.join(screenshotRoot, 'capture-manifest.json'), JSON.stringify({ source: 'Current Vue production build; documentation API fixtures; nine rule traces from the local QLExpress engine', capturedAt: new Date().toISOString(), viewport: screenshotViewport, images }, null, 2) + '\n')
    console.log(`PASS ${captures.length} screenshots, no unmatched API requests or browser errors`)
  } finally { await browser.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 })

