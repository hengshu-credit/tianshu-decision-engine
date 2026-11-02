const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')
const { createOperationsApiData } = require('./support/operationsFixtures.cjs')
const { withTestExecutionIdentity } = require('./support/testExecutionFixtures.cjs')

const graphAsset = /\/(?:src\/components\/flow\/graphCanvas\.js|assets\/graphCanvas-[^/]+\.js)(?:\?.*)?$/

test('规则测试执行后才加载决策流画布，等待期间仍可查看节点详情', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1920, height: 1080 })
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const apiData = createOperationsApiData()
  for (const endpoint of ['/api/rule/definition/list', '/api/rule/definition/project-list/1']) {
    apiData.get(endpoint).records[0].modelType = 'FLOW'
  }
  const revision = apiData.get('/api/rule/definition/101/revisions/41')
  revision.modelJson = JSON.stringify({
    nodes: [
      { id: 'start', type: 'start', name: '开始', x: 100, y: 100 },
      { id: 'pass', type: 'task', name: '通过处理', x: 320, y: 100, actionData: [{ target: 'decision' }] },
      { id: 'end', type: 'end', name: '结束', x: 540, y: 100 },
    ],
    edges: [{ id: 'a', source: 'start', target: 'pass' }, { id: 'b', source: 'pass', target: 'end' }],
  })
  apiData.set('POST /api/rule/definition/execute', withTestExecutionIdentity({
    success: true, result: { decision: 'PASS' }, executeTimeMs: 1,
    traces: [[{ type: 'OPERATOR', token: '=', evaluated: true, value: 'PASS', children: [
      { type: 'VARIABLE', token: 'decision', evaluated: true, value: 'PASS' },
      { type: 'VALUE', evaluated: true, value: 'PASS' },
    ] }, { type: 'OPERATOR', token: '=', evaluated: true, value: { decision: 'PASS' }, children: [
      { type: 'VARIABLE', token: '_result', evaluated: true, value: { decision: 'PASS' } },
      { type: 'VALUE', evaluated: true, value: { decision: 'PASS' } },
    ] }]],
  }))
  const { assertClean } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData })
  let release, graphRequests = 0
  const held = new Promise(resolve => { release = resolve })
  await page.route(graphAsset, async route => { graphRequests++; await held; await route.fallback() })
  try {
    await page.goto(`${origin}/#/test`)
    await page.locator('.test-left .el-form-item').filter({ hasText: '规则' }).locator('.el-select').click()
    await page.getByRole('option', { name: /age_rule/ }).click()
    await expect(page.getByRole('button', { name: '执行测试', exact: true })).toBeEnabled()
    expect(graphRequests).toBe(0)
    await page.getByRole('button', { name: '执行测试', exact: true }).click()
    await expect(page.getByText('正在加载执行图…', { exact: true })).toBeVisible()
    await page.locator('.flow-trace-fallback__node').filter({ hasText: '通过处理' }).click()
    await expect(page.getByTestId('flow-trace-detail')).toContainText('PASS')
    release()
    await expect(page.getByTestId('flow-trace-canvas').locator('.lf-graph')).toBeVisible()
    await expect(page.getByTestId('flow-trace-canvas').locator('.lf-node')).toHaveCount(3)
    await expect(page.getByTestId('flow-trace-fallback')).toHaveCount(0)
    expect(graphRequests).toBe(1)
    await page.screenshot({ path: testInfo.outputPath('rule-test-flow-ready.png') })
    assertClean()
  } finally { release() }
})

test('首次打开规则测试和血缘筛选不等待图形库，生成图谱才加载且再次生成复用', async ({ page }, testInfo) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const { assertClean } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: createOperationsApiData() })
  let release, graphRequests = 0
  const held = new Promise(resolve => { release = resolve })
  await page.route(graphAsset, async route => { graphRequests++; await held; await route.fallback() })
  try {
    await page.goto(`${origin}/#/project`)
    await page.locator('[data-menu-path="/test"]').click()
    await expect(page.locator('.test-left')).toBeVisible()
    await expect(page.getByRole('button', { name: '执行测试', exact: true })).toBeVisible()
    await page.locator('[data-menu-path="/lineage"]').click()
    await expect(page.getByRole('combobox', { name: '起点', exact: true })).toBeVisible()
    await expect(page.getByText('请选择起点后生成血缘图')).toBeVisible()
    expect(graphRequests).toBe(0)
    await page.getByRole('combobox', { name: '起点', exact: true }).click()
    await page.getByRole('option', { name: '年龄 (age)', exact: true }).click()
    await page.getByRole('button', { name: '生成血缘图', exact: true }).click()
    await expect.poll(() => graphRequests).toBe(1)
    await expect(page.getByText('正在加载血缘图…', { exact: true })).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('lineage-canvas-loading.png') })
    release()
    await expect(page.locator('.current-node')).toBeVisible()
    await expect(page.getByText('正在加载血缘图…', { exact: true })).toHaveCount(0)
    await page.getByRole('button', { name: '生成血缘图', exact: true }).click()
    await expect(page.locator('.current-node')).toBeVisible()
    expect(graphRequests).toBe(1)
    await page.screenshot({ path: testInfo.outputPath('lineage-canvas-ready.png') })
    assertClean()
  } finally { release() }
})

test('血缘画布下载期间可离开，下载完成后不重建已离开的页面', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const { assertClean } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: createOperationsApiData() })
  let release
  const held = new Promise(resolve => { release = resolve })
  await page.route(graphAsset, async route => { await held; await route.fallback() })
  try {
    await page.goto(`${origin}/#/lineage`)
    await page.getByRole('combobox', { name: '起点', exact: true }).click()
    await page.getByRole('option', { name: '年龄 (age)', exact: true }).click()
    await page.getByRole('button', { name: '生成血缘图', exact: true }).click()
    await expect(page.getByText('正在加载血缘图…', { exact: true })).toBeVisible()
    await page.locator('[data-menu-path="/test"]').click()
    await expect(page.locator('.test-left')).toBeVisible()
    release()
    await page.locator('[data-menu-path="/lineage"]').click()
    await expect(page.getByText('请选择起点后生成血缘图')).toBeVisible()
    await page.getByRole('combobox', { name: '起点', exact: true }).click()
    await page.getByRole('option', { name: '年龄 (age)', exact: true }).click()
    await page.getByRole('button', { name: '生成血缘图', exact: true }).click()
    await expect(page.locator('.current-node')).toBeVisible()
    assertClean()
  } finally { release() }
})

test('血缘图资源下载失败后明确刷新页面，保留起点并恢复图谱', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: createOperationsApiData() })
  let attempts = 0, unavailable = true
  await page.route(graphAsset, async route => {
    attempts++
    if (unavailable) await route.fulfill({ status: 503, body: 'Temporarily unavailable' })
    else await route.fallback()
  })
  await page.goto(`${origin}/#/lineage`)
  await page.getByRole('combobox', { name: '起点', exact: true }).click()
  await page.getByRole('option', { name: '年龄 (age)', exact: true }).click()
  await page.getByRole('button', { name: '生成血缘图', exact: true }).click()
  await expect(page.getByText('血缘图资源下载失败，请刷新页面重新加载', { exact: true })).toBeVisible()
  const failedAttempts = attempts
  unavailable = false
  await page.getByRole('button', { name: '重新加载', exact: true }).click()
  await expect(page.locator('.current-node')).toBeVisible()
  await expect(page).toHaveURL(/#\/lineage\?nodeType=VARIABLE&nodeId=1&direction=ALL$/)
  await expect(page.locator('.query-panel .el-form-item').filter({ hasText: '起点' })).toContainText('年龄 (age)')
  expect(attempts).toBeGreaterThan(failedAttempts)
  expect(harness.pageErrors).toEqual([])
  expect(harness.unmatchedRequests).toEqual([])
  expect(harness.consoleErrors.filter(message => !message.includes('503 (Service Unavailable)'))).toEqual([])
})

test('普通页面不下载编辑器，进入 QL 编辑器后按需下载且仅加载一次', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const assets = []
  page.on('request', request => assets.push(new URL(request.url()).pathname))
  const apiData = createDesignerApiData()
  const definition = apiData.get('/api/rule/definition/109')
  apiData.set('/api/rule/definition/list', { records: [definition], total: 1 })
  const { assertClean } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData })
  await page.goto(`${origin}/#/project`)
  await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeVisible()
  await page.locator('[data-menu-path="/rule"]').click()
  await expect(page.getByRole('cell', { name: definition.ruleCode, exact: true })).toBeVisible()
  expect(assets.filter(path => path.startsWith('/vs/'))).toEqual([])
  await expect.poll(() => assets.includes('/fonts/font.woff2')).toBe(true)
  expect(assets).not.toContain('/fonts/font.ttf')
  await page.getByTestId('view-rule').click()
  await expect(page).toHaveURL(/#\/designer\/script\/109$/)
  await expect(page.locator('.script-editor .monaco-editor, .monaco-editor-container .monaco-editor').first()).toBeVisible()
  expect(assets.filter(path => path === '/vs/loader.js')).toHaveLength(1)
  expect(assets.filter(path => path === '/vs/editor/editor.main.js')).toHaveLength(1)
  await page.locator('.workspace-tab__main[data-path="/rule"]').click()
  await expect(page).toHaveURL(/#\/rule$/)
  await page.locator('.workspace-tab__main[data-path="/designer/script/109"]').click()
  await expect(page.locator('.monaco-editor-container .monaco-editor').first()).toBeVisible()
  expect(assets.filter(path => path === '/vs/loader.js')).toHaveLength(1)
  assertClean()
})
