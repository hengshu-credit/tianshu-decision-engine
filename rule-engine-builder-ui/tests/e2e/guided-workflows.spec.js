const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')
const { createManagementApiData } = require('./support/managementFixtures.cjs')

test('项目工作台加载失败后可重试，且未取得结果时不显示已就绪', async ({ page }, testInfo) => {
  const { pageErrors } = await installDistRoutes(page, { apiData: workflowFixtures() })
  let calls = 0
  let releaseFirst
  await page.route('**/api/rule/project/1/workbench', async route => {
    calls++
    if (calls === 1) {
      await new Promise(resolve => { releaseFirst = resolve })
      await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: '检查服务暂不可用' }) })
      return
    }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({
      code: 200, data: { metrics: {}, checks: [{ code: 'RULE', title: '设计决策规则', status: 'READY', reason: '已配置规则' }], warnings: [] },
    }) })
  })
  await page.goto('http://tianshu.local/index.html#/project/1')
  await expect(page.getByText('正在检查', { exact: true })).toBeVisible()
  await expect(page.getByTestId('refresh-workbench')).toBeDisabled()
  releaseFirst()
  await expect(page.getByText('状态读取失败', { exact: true })).toBeVisible()
  await expect(page.getByText('当前检查项已就绪', { exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '重新加载', exact: true }).click()
  await expect(page.getByText('当前检查项已就绪', { exact: true })).toBeVisible()
  expect(calls).toBe(2)
  expect(pageErrors).toEqual([])
  await page.screenshot({ path: testInfo.outputPath('workbench-recovered.png') })
})

test('工作台显示执行失败建议并携带项目范围进入日志', async ({ page }, testInfo) => {
  const fixtures = workflowFixtures()
  fixtures.set('/api/rule/project/1/workbench', {
    metrics: { recentExecutionCount: 12, recentSuccessRate: 83.3 },
    checks: [{ code: 'RUN', title: '检查执行结果', status: 'ATTENTION', reason: '最近 24 小时共有 12 次执行，其中 2 次失败，请查看日志。', actionCode: 'VIEW_LOGS', actionLabel: '排查执行失败' }],
    recentExecution: { success: 0, ruleCode: 'age_rule', executeTimeMs: 23, source: 'SERVER' },
    warnings: [],
  })
  const { assertClean } = await installDistRoutes(page, { apiData: fixtures })
  await page.setViewportSize({ width: 1280, height: 720 })
  await page.goto('http://tianshu.local/index.html#/project/1')
  await expect(page.getByText('还有 1 项待处理', { exact: true })).toBeVisible()
  await expect(page.locator('.next-action-card')).toContainText('2 次失败')
  await page.screenshot({ path: testInfo.outputPath('workbench-failed-run.png') })
  await page.locator('.next-action-card').getByRole('button', { name: '排查执行失败' }).click()
  await expect(page).toHaveURL(/#\/log\?projectId=1$/)
  assertClean()
})

for (const entry of ['project', 'rule']) {
  test(`${entry} 新建规则按业务说明选型并保留输入内容和类型`, async ({ page }, testInfo) => {
    const fixtures = workflowFixtures()
    let submitted
    fixtures.set('POST /api/rule/definition', ({ request }) => {
      submitted = request.postDataJSON()
      return { id: 202, ...submitted }
    })
    const { assertClean } = await installDistRoutes(page, { apiData: fixtures })
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(`http://tianshu.local/index.html#/${entry === 'project' ? 'project/1' : 'rule?projectId=1'}`)
    if (entry === 'project') await page.getByRole('tab', { name: '项目规则', exact: true }).click()
    await page.getByRole('button', { name: '新建规则', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '新建规则' })
    await dialog.getByRole('textbox', { name: '规则编码' }).fill('Audit_Rule_MixedCase')
    await dialog.getByRole('textbox', { name: '规则名称' }).fill('审批前评分验证')
    for (const [type, help] of [['决策流', '编排多个判断'], ['QL脚本', '直接编写 QL 脚本'], ['评分卡', '按权重累加']]) {
      await dialog.locator('.el-form-item').filter({ hasText: '模型类型' }).locator('.el-select').click()
      await page.getByRole('option', { name: type, exact: true }).click()
      await expect(dialog.locator('.model-type-help')).toContainText(help)
    }
    await expect(dialog.locator('.model-type-help')).toContainText('通过校验和审批后再发布')
    const box = await dialog.boundingBox()
    expect(box.y).toBeGreaterThanOrEqual(0)
    expect(box.y + box.height).toBeLessThanOrEqual(720)
    await page.screenshot({ path: testInfo.outputPath(`${entry}-rule-guide.png`) })
    await dialog.getByRole('button', { name: '仅创建', exact: true }).click()
    await expect(dialog).not.toBeVisible()
    expect(submitted).toEqual(expect.objectContaining({ ruleCode: 'Audit_Rule_MixedCase', ruleName: '审批前评分验证', modelType: 'SCORE' }))
    expect(String(submitted.projectId)).toBe('1')
    assertClean()
  })
}

function workflowFixtures() {
  const routes = new Map([
    ...createDetailApiData(),
    ...createManagementApiData()
  ])
  routes.set('/api/rule/governance/requests/summary', {
    pendingCount: 3,
    myDraftCount: 1,
    myRequestCount: 6,
    completedCount: 9
  })
  routes.set('/api/rule/governance/requests', {
    records: [{
      id: 91,
      requestNo: 'GOV-E2E-91',
      resourceType: 'LIST_RECORD_BATCH',
      resourceId: 0,
      action: 'CREATE',
      status: 'PENDING',
      applicant: 'e2e-applicant',
      submitTime: '2026-08-03T08:30:00',
      submittedSnapshotJson: JSON.stringify({
        batchId: 101,
        listName: '手机号黑名单',
        addCount: 2,
        updateCount: 1,
        deleteCount: 1
      })
    }],
    total: 1
  })
  routes.set('/api/rule/variable/source-options', {
    apiOptions: [{
      id: 22,
      code: 'credit_query',
      name: '征信查询',
      scope: 'PROJECT',
      projectId: 1,
      parentId: 21,
      parentName: '征信供应商'
    }],
    databaseOptions: [{
      id: 31,
      code: 'risk_mysql',
      name: '风控只读库',
      scope: 'PROJECT',
      projectId: 1
    }],
    listOptions: [{
      id: 9,
      code: 'mobile_black',
      name: '手机号黑名单',
      scope: 'PROJECT',
      projectId: 1
    }]
  })
  routes.set('POST /api/rule/variable/preview', {
    varCode: 'riskScore',
    varSource: 'API',
    resolvedValue: 88,
    resolvedParams: { riskScore: 88 }
  })
  return routes
}

test('审批任务中心默认跨模块展示待处理事项并覆盖全部资源筛选', async ({
  page
}) => {
  const { assertClean } = await installDistRoutes(page, {
    apiData: workflowFixtures()
  })

  await page.goto('http://tianshu.local/index.html#/approval')

  await expect(page.getByRole('heading', { name: '审批任务中心' })).toBeVisible()
  await expect(page.getByRole('tab', { name: '待处理', exact: true }))
    .toHaveAttribute('aria-selected', 'true')
  await expect(page.getByText('手机号黑名单 · 4 条内容变更', { exact: true }))
    .toBeVisible()
  await page.locator('.filter-bar .el-select').first().click()
  await expect(page.getByText('名单内容批次', { exact: true }).last()).toBeVisible()
  await expect(page.getByText('计费配置', { exact: true }).last()).toBeVisible()
  await page.keyboard.press('Escape')
  assertClean()
})

test('审批详情配置差异只显示一组编辑器版本标题', async ({ page }) => {
  const fixtures = workflowFixtures()
  fixtures.set('/api/rule/governance/requests/91', {
    request: {
      id: 91,
      requestNo: 'GOV-E2E-91',
      resourceType: 'UNKNOWN',
      resourceId: 7,
      action: 'UPDATE',
      status: 'PENDING',
      applicant: 'e2e-applicant',
      baseVersionNo: 1,
      submittedSnapshotJson: '{"timeout":60}'
    },
    diff: {
      summary: '共 1 项变更',
      fields: [{
        key: '$.timeout',
        leftValue: 30,
        rightValue: 60,
        changeType: 'MODIFIED',
        changed: true
      }]
    },
    events: [],
    dependencies: [],
    versions: [{
      id: 100,
      versionNo: 1,
      snapshotJson: '{"timeout":30}'
    }]
  })
  const { assertClean } = await installDistRoutes(page, { apiData: fixtures })

  await page.goto('http://tianshu.local/index.html#/approval/91')
  await expect(page.locator('h2').filter({ hasText: '配置差异' }).first()).toBeVisible()
  await expect(page.locator('h2 .diff-summary')).toHaveText('内容有差异')
  const leftSelector = page.locator('.version-selector-left')
  const rightSelector = page.locator('.version-selector-right')
  const leftBox = await leftSelector.boundingBox()
  const rightBox = await rightSelector.boundingBox()
  expect(leftBox.width).toBeGreaterThan(0)
  expect(rightBox.width).toBeGreaterThan(0)
  expect(rightBox.x).toBeGreaterThan(leftBox.x + leftBox.width)
  await expect(page.locator('.diff-columns-head')).toHaveCount(0)
  await expect(page.locator('.json-version-diff__head')).toHaveCount(0)
  assertClean()
})

test('审批详情血缘复用统一血缘图并展示解析后的引用内容', async ({ page }) => {
  const fixtures = workflowFixtures()
  fixtures.set('/api/rule/governance/requests/91', {
    request: {
      id: 91,
      requestNo: 'GOV-E2E-91',
      resourceType: 'DATA_OBJECT',
      resourceId: 3,
      action: 'UPDATE',
      status: 'PENDING',
      applicant: 'e2e-applicant',
      baseVersionNo: 1,
      submittedSnapshotJson: JSON.stringify({
        fields: [{ projectId: 3, varLabel: '项目归属' }]
      })
    },
    diff: { summary: '共 1 项变更', fields: [] },
    events: [],
    dependencies: [{
      targetResourceType: 'PROJECT',
      targetResourceId: 3,
      targetVersionNo: 1,
      referencePath: '$.fields[0].projectId'
    }],
    versions: [{ id: 100, versionNo: 1, snapshotJson: '{"fields":[]}' }]
  })
  fixtures.set('/api/rule/lineage/graph', {
    startNode: {
      id: 'DATA_OBJECT:3',
      refId: 3,
      type: 'DATA_OBJECT',
      code: 'application',
      label: '申请信息',
      hasUpstream: true,
      hasDownstream: false
    },
    nodes: [{
      id: 'DATA_OBJECT:3',
      refId: 3,
      type: 'DATA_OBJECT',
      code: 'application',
      label: '申请信息',
      hasUpstream: true,
      hasDownstream: false
    }],
    edges: []
  })
  const { assertClean } = await installDistRoutes(page, { apiData: fixtures })

  await page.goto('http://tianshu.local/index.html#/approval/91')
  await expect(page.locator('.lineage-page.is-embedded')).toBeVisible()
  await expect(page.locator('.dependency-reference')).toContainText(
    '字段 1（项目归属） · 项目 ID = 3'
  )
  await expect(page.locator('.dependency-reference')).not.toContainText('$.fields')
  await expect(page.locator('.lineage-page.is-embedded')).toContainText('申请信息')
  assertClean()
})

test('外数 API 按业务、稳定性和高级能力分层且保留完整配置入口', async ({
  page
}) => {
  const { assertClean } = await installDistRoutes(page, {
    apiData: workflowFixtures()
  })

  await page.goto('http://tianshu.local/index.html#/datasource/api/new?projectId=1')

  await expect(page.getByText('配置检查', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /业务配置/ })).toBeVisible()
  await expect(page.getByText('接口鉴权', { exact: true }).last()).toBeVisible()
  await page.getByRole('button', { name: /稳定性策略/ }).click()
  await expect(page.getByText('连接&流控', { exact: true })).toBeVisible()
  await expect(page.getByText('异常&重试', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: /高级能力/ }).click()
  await expect(page.getByText('脚本处理', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '生成审批草稿', exact: true }))
    .toBeVisible()
  assertClean()
})

test('新建字段按业务取值方式引导并可在送审前预览外数结果', async ({
  page
}) => {
  const { requests, assertClean } = await installDistRoutes(page, {
    apiData: workflowFixtures()
  })

  await page.goto('http://tianshu.local/index.html#/variable?projectId=1')
  await page.getByRole('button', { name: '新建字段', exact: true }).click()

  const dialog = page.locator('.el-dialog:visible').last()
  await expect(dialog.getByText('按业务取值方式完成字段配置', { exact: true }))
    .toBeVisible()
  await expect(dialog.getByText('3 个可用', { exact: true })).toHaveCount(0)
  await expect(dialog.getByText('1 个可用', { exact: true })).toHaveCount(3)
  await dialog.getByRole('button', { name: /外数接口/ }).click()
  await expect(dialog.getByText('保存前验证取值', { exact: true })).toBeVisible()

  await dialog.locator('.el-form-item').filter({ hasText: '字段编码' })
    .locator('input').fill('riskScore')
  await dialog.locator('.el-form-item').filter({ hasText: '字段名称' })
    .locator('input').fill('风险分')
  await dialog.locator('.el-form-item').filter({ hasText: '接口配置' })
    .locator('.el-select').click()
  await page.getByText('征信查询 / credit_query', { exact: true }).last().click()

  await expect(dialog.getByText('3 / 4 已就绪', { exact: true })).toBeVisible()
  await dialog.getByRole('button', { name: '预览取值', exact: true }).click()
  await expect(dialog.getByText('4 / 4 已就绪', { exact: true })).toBeVisible()
  await expect(dialog.locator('.draft-preview-result')).toContainText('88')
  await expect(dialog.getByRole('button', {
    name: '生成审批草稿', exact: true
  })).toBeVisible()
  expect(requests.some(request =>
    request.method === 'POST' &&
    new URL(request.url).pathname === '/api/rule/variable/preview'
  )).toBe(true)
  assertClean()
})

test('数据库只读查询按 SQL 占位符引导填写参数并区分结果状态', async ({
  page
}) => {
  let queryPayload
  const fixtures = workflowFixtures()
  fixtures.set('POST /api/rule/database/31/query', async ({ request }) => {
    queryPayload = request.postDataJSON()
    return [{ score: queryPayload.params[0] }]
  })
  const { assertClean } = await installDistRoutes(page, { apiData: fixtures })

  await page.goto('http://tianshu.local/index.html#/database')
  const datasourceRow = page.getByRole('row').filter({ hasText: 'risk_mysql' })
  await datasourceRow.getByRole('button', { name: '查询', exact: true }).click()

  const dialog = page.locator('.el-dialog:visible').last()
  await expect(dialog.getByText('只读查询', { exact: true })).toBeVisible()
  const editor = dialog.locator('.monaco-editor-container')
  await editor.click()
  await page.keyboard.insertText('SELECT ? AS score')
  await expect(dialog.getByText('已识别 1 个有效 ? 占位符', { exact: true }))
    .toBeVisible()

  await dialog.locator('.query-param-type').click()
  await page.getByRole('option', { name: '数值', exact: true }).last().click()
  await dialog.getByLabel('参数 1 值').fill('88')
  await dialog.getByRole('button', { name: '执行查询', exact: true }).click()

  await expect(dialog.getByText('查询成功', { exact: true })).toBeVisible()
  await expect(dialog.getByRole('cell', { name: '88', exact: true })).toBeVisible()
  expect(queryPayload).toEqual({
    sql: 'SELECT ? AS score',
    params: [88],
    maxRows: 100,
    queryTimeoutSeconds: 5
  })
  assertClean()
})

test('模型测试明确参数来源、降级原因并只发送当前表单参数', async ({
  page
}) => {
  let executePayload
  const fixtures = workflowFixtures()
  fixtures.set('POST /api/rule/test-schema', {
    inputs: [{
      refId: 1,
      refType: 'VARIABLE',
      scriptName: 'age',
      label: '年龄',
      valueType: 'INTEGER'
    }],
    sampleParams: { age: 28 }
  })
  fixtures.set('/api/rule/model/41', {
    ...fixtures.get('/api/rule/model/41'),
    modelConfig: '{invalid json'
  })
  fixtures.set('POST /api/rule/model/execute/41', async ({ request }) => {
    executePayload = request.postDataJSON()
    return {
      success: true,
      outputs: { riskScore: executePayload.age * 2 },
      executeTimeMs: 12
    }
  })
  const { assertClean } = await installDistRoutes(page, { apiData: fixtures })

  await page.setViewportSize({ width: 1280, height: 720 })
  await page.goto('http://tianshu.local/index.html#/model/41')
  await page.getByRole('button', { name: '模型测试', exact: true }).click()

  const dialog = page.getByRole('dialog', { name: '模型测试' })
  await expect(dialog.getByText('测试配置已降级加载', { exact: true }))
    .toBeVisible()
  await expect(dialog.getByText(/参数来源：测试 Schema 样例/)).toBeVisible()
  await expect(dialog.getByText(/模型配置样例解析失败/)).toBeVisible()

  const ageField = dialog.locator('.test-field-cell').filter({ hasText: '年龄' })
  await ageField.locator('input').fill('35')
  await dialog.getByRole('button', { name: '执行测试', exact: true }).click()

  await expect(dialog.getByText('执行成功', { exact: true })).toBeVisible()
  await expect(dialog.locator('pre')).toContainText('70')
  expect(executePayload).toEqual({ age: 35 })
  const dialogBox = await dialog.boundingBox()
  const viewport = page.viewportSize()
  expect(dialogBox.x).toBeGreaterThanOrEqual(0)
  expect(dialogBox.y).toBeGreaterThanOrEqual(0)
  expect(dialogBox.x + dialogBox.width).toBeLessThanOrEqual(viewport.width)
  expect(dialogBox.y + dialogBox.height).toBeLessThanOrEqual(viewport.height)
  assertClean()
})
