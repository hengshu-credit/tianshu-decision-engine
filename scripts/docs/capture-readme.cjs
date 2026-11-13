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
const workDir = path.join(root, 'rule-engine-core/target/docs')
const publishedScreenshots = referencedScreenshots()
const readmeScreenshots = referencedScreenshots({ includeManual: false })
const examples = JSON.parse(fs.readFileSync(path.join(root, 'rule-engine-core/target/docs/executed-examples.json'), 'utf8'))
for (const example of examples) assert.deepEqual(example.execution.result, example.expectedOutput, example.ruleCode)

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

// Encode a small copy of the actual pixels. A focused preview keeps a trace or dialog legible.
async function writePreview(page, png, panel, file) {
  const bounds = panel ? await panel.boundingBox() : null
  if (panel) assert.ok(bounds && bounds.width && bounds.height, 'Preview panel must be visible')
  const preview = await page.evaluate(async ({ base64, bounds }) => {
    const bytes = Uint8Array.from(atob(base64), char => char.charCodeAt(0))
    const image = await createImageBitmap(new Blob([bytes], { type: 'image/png' }))
    const x = bounds ? Math.max(0, Math.floor(bounds.x) - 12) : 0
    const y = bounds ? Math.max(0, Math.floor(bounds.y) - 12) : 0
    const right = bounds ? Math.min(image.width, Math.ceil(bounds.x + bounds.width) + 12) : image.width
    const bottom = bounds ? Math.min(image.height, Math.ceil(bounds.y + bounds.height) + 12) : image.height
    const width = right - x, height = bottom - y
    if (width <= 0 || height <= 0) throw new Error('Preview panel is outside the captured viewport')
    const scale = Math.min(1, 900 / width, 560 / height)
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(width * scale))
    canvas.height = Math.max(1, Math.round(height * scale))
    const ctx = canvas.getContext('2d')
    ctx.imageSmoothingQuality = 'high'
    ctx.drawImage(image, x, y, width, height, 0, 0, canvas.width, canvas.height)
    image.close()
    return canvas.toDataURL('image/webp', 0.86).split(',')[1]
  }, { base64: png.toString('base64'), bounds })
  fs.writeFileSync(file, Buffer.from(preview, 'base64'))
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
  if (!example) {
    const revisionList = '/api/rule/definition/101/revisions'
    const revisions = data.get(revisionList)
    data.set(revisionList, () => revisions().map(row => ({ ...row, lockVersion: 0 })))
    const endpoint = 'POST /api/rule/definition/101/revisions/2003/preflight'
    data.set(endpoint, { ...data.get(endpoint), revisionId: 2003, lockVersion: 0 })
  }
  const event = { id: 31, title: '策略版本调整', scope: 'GLOBAL', status: 'PUBLISHED' }
  const article = { id: 81, title: '业务准入策略调整记录', scope: 'GLOBAL', category: 'STRATEGY', publishedVersion: 1,
    publishedRevisionId: 91, hasDraft: false, updateTime: '2026-11-12T09:00:00' }
  data.set('/api/rule/ledger/events', [event])
  data.set('/api/rule/ledger/timeline', { records: [{ ...article, id: 91, articleId: article.id, versionNo: 1,
    summary: '已完成准入条件调整与固定用例复测，发布后持续观察规则命中和业务分布。',
    activityTime: article.updateTime, actorName: '策略运营', events: [event] }], total: 1 })
  data.set('/api/rule/ledger/articles', { records: [article], total: 1 })
  data.set('/api/rule/sftp/list', { records: [{ id: 71, projectId: 1, projectCode: 'face_risk_center',
    datasourceCode: 'partner_documents', datasourceName: '合作方业务文件', host: 'files.example.test',
    port: 22, basePath: '/business/documents', connectionMode: 'DIRECT', status: 1 }], total: 1 })
  data.set('/api/rule/sftp/71', data.get('/api/rule/sftp/list').records[0])
  data.set('/api/rule/system/settings', { brand: { systemName: '天枢决策引擎' }, mail: {}, access: {},
    codes: { mappings: {} }, codeCatalog: [], states: [], deploymentManaged: {} })
  return data
}

async function main() {
  const { DEFAULT_THEME_CONFIG: theme, ACCENT_PRESETS } = await import('../../rule-engine-web/src/theme/themeConfig.js')
  const accent = ACCENT_PRESETS.find(preset => preset.id === theme.accentPreset)
  const captures = []
  const output = fs.mkdtempSync(path.join(workDir, 'gallery-'))
  fs.mkdirSync(path.join(output, 'thumbs'))
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
        const panel = action ? await action(page) : null
        await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
        await expect(page.locator('.el-message:visible')).toHaveCount(0)
        await page.waitForTimeout(900)
        const visibleTheme = await page.evaluate(() => {
          const root = document.documentElement
          return { mode: root.dataset.theme, sidebar: root.dataset.sidebarTheme,
            navigation: root.dataset.navigationLayout, table: root.dataset.tableScrollMode,
            background: root.style.getPropertyValue('--tianshu-brand-background').trim() }
        })
        assert.deepEqual(visibleTheme, { mode: theme.colorScheme.toLowerCase(), sidebar: theme.sidebarTheme.toLowerCase(),
          navigation: theme.navigationLayout.toLowerCase(), table: theme.tableScrollMode.toLowerCase(), background: accent.background }, name)
        checks.assertClean()
        const png = await page.screenshot({ path: path.join(output, fileName), animations: 'disabled' })
        if (readmeScreenshots.has(fileName)) {
          await writePreview(page, png, panel, path.join(output, 'thumbs', fileName.replace(/\.png$/, '.webp')))
        }
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
      ['experiment', '/experiment'], ['logs', '/log'], ['billing', '/billing'],
      ['transfer', '/transfer'], ['account', '/account'], ['approval', '/approval'],
      ['13-model-detail', '/model/41'], ['15-experiment-config', '/experiment/detail/61'], ['12-sftp', '/sftp']
    ]
    for (const [name, route] of basic) await capture(name, route)
    await capture('login', '/login', page => expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible(), null, data => data.set('/api/auth/console/config', { loginEnabled: true }))
    await capture('02-project-auth', '/project', async page => {
      await page.getByRole('row').filter({ hasText: 'face_risk_center' }).getByRole('button', { name: '鉴权', exact: true }).click()
      await expect(page.getByRole('dialog', { name: '项目调用鉴权' })).toBeVisible()
    })
    await capture('03-constants', '/variable', page => page.getByRole('tab', { name: '常量列表' }).click())
    await capture('03-field-validation', '/variable', async page => {
      await page.getByRole('tab', { name: '字段校验' }).click()
      await expect(page.getByText('face_url_required', { exact: true }).first()).toBeVisible()
    })
    await capture('16-billing-record', '/billing', page => page.getByRole('tab', { name: '计费明细' }).click())
    await capture('24-ledger', '/ledger', async page => {
      await page.getByRole('button', { name: /打开文档：业务准入策略调整记录/ }).hover()
      await expect(page.locator('.ledger-card-popover:visible')).toBeVisible()
    })
    await capture('24-ledger-articles', '/ledger', async page => {
      await page.getByRole('tab', { name: '文章管理' }).click()
      await expect(page.getByText('业务准入策略调整记录', { exact: true }).first()).toBeVisible()
    })
    await capture('25-system-settings', '/system/settings', page => expect(page.getByRole('tab', { name: '基本信息' })).toBeVisible(), null, data => {
      data.set('/api/auth/console/config', { loginEnabled: true })
      data.set('/api/auth/console/me', { userId: 1, username: 'docs_admin', displayName: '系统管理员',
        permissions: ['system:view', 'system:edit'], defaultPermissions: ['system:view', 'system:edit'], permissionVersion: 1 })
      data.set('/api/auth/console/preferences/theme', null)
      data.set('POST /api/rule/console/activity', null)
    })
    await capture('data-object', '/variable', async page => {
      await page.getByRole('tab', { name: '数据对象', exact: true }).click()
      await page.getByRole('row').filter({ hasText: 'FaceVerifyRequest' }).locator('.el-table__expand-icon').click()
      await expect(page.getByText('deviceId', { exact: true }).first()).toBeVisible()
    })
    await capture('api-list', '/datasource', async page => {
      await page.getByRole('tab', { name: 'API 接口', exact: true }).click()
      await expect(page.getByText('face_liveness_check', { exact: true })).toBeVisible()
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
    await capture('11-database-detail', '/database/31', async page => {
      await expect(page.getByTestId('test-database-connection')).toBeVisible()
    })
    await capture('12-sftp-config', '/sftp', async page => {
      const row = page.getByRole('row').filter({ hasText: 'partner_documents' })
      await row.getByRole('button', { name: '编辑', exact: true }).click()
      const dialog = page.getByRole('dialog', { name: '编辑 SFTP 数据源' })
      await expect(dialog.getByPlaceholder('sftp.example.com')).toHaveValue('files.example.test')
      return dialog
    })
    await capture('09-list-detail', '/list/9', async page => {
      await expect(page.getByRole('tab', { name: '名单内容' })).toHaveAttribute('aria-selected', 'true')
    })
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
    await capture('external-call-trace', '/log', async page => {
      const row = page.getByRole('row').filter({ hasText: 'FACE202607240930000000000000000001' })
      await row.getByRole('button', { name: '详情', exact: true }).click()
      const drawer = page.getByRole('dialog', { name: '日志详情' })
      await expect(drawer).toBeVisible()
      await drawer.getByRole('tab', { name: /表达式追踪树/ }).click()
      await expect(drawer.getByText('拼装供应商请求', { exact: true })).toBeVisible()
    })
    await capture('experiment-trace', '/experiment/detail/61', async page => {
      await page.getByRole('tab', { name: '分流日志', exact: true }).click()
      await expect(page.getByText('REQ-FACE-20260724093115-001', { exact: true })).toBeVisible()
      await page.getByRole('button', { name: '详情', exact: true }).last().click()
      const drawer = page.getByRole('dialog', { name: '分流日志详情' })
      const trace = drawer.locator('.experiment-trace-session')
      await expect(trace.locator('.experiment-routing-tree')).toBeVisible()
      await expect(trace.locator('.experiment-rule-execution')).toContainText('实际规则执行')
      await trace.evaluate(el => el.scrollIntoView({ block: 'start' }))
      return trace
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
    await capture('05-rule-test', '/test', page => runTest(page, examples[0]), examples[0])
    const treeExample = examples.find(example => example.slug === 'tree')
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
    await capture('05-rule-trace-tree', '/test', page => showTrace(page, treeExample), treeExample)
    for (const state of ['preflight', 'published']) {
      await capture(`08-rule-release-${state}`, '/rule/101', async page => {
        await page.getByTestId('preflight').click()
        await expect(page.getByText('可继续', { exact: true })).toBeVisible()
        if (state === 'published') {
          for (const action of ['submit', 'approve', 'publish']) await page.getByTestId(action).click()
          await expect(page.getByTestId('offline')).toBeVisible()
        }
      })
    }
    const images = captures.sort()
    const required = [...publishedScreenshots].filter(name => name.startsWith('project-usage-')).sort()
    assert.deepEqual(images, required, 'Every published screenshot must come from this run')
    fs.mkdirSync(path.join(screenshotRoot, 'thumbs'), { recursive: true })
    for (const name of images) {
      const thumb = path.join('thumbs', name.replace(/\.png$/, '.webp'))
      fs.copyFileSync(path.join(output, name), path.join(screenshotRoot, name))
      if (readmeScreenshots.has(name)) fs.copyFileSync(path.join(output, thumb), path.join(screenshotRoot, thumb))
    }
    const manifest = { source: 'Current Vue production build; documentation API fixtures; nine rule traces from the local QLExpress engine',
      capturedAt: new Date().toISOString(), viewport: screenshotViewport, theme: theme.accentPreset, images }
    fs.writeFileSync(path.join(screenshotRoot, 'capture-manifest.json'), JSON.stringify(manifest, null, 2) + '\n')
    console.log(`PASS ${captures.length} screenshots, no unmatched API requests or browser errors`)
  } finally { await browser.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 })

