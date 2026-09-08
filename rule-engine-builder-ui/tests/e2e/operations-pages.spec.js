const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createOperationsApiData } = require('./support/operationsFixtures.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')

async function expectNoRootOverflow(page) {
  const metrics = await page.evaluate(() => ({
    viewport: window.innerWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth
  }))
  expect(metrics.document).toBeLessThanOrEqual(metrics.viewport + 1)
  expect(metrics.body).toBeLessThanOrEqual(metrics.viewport + 1)
}

async function expectTextSelectable(locator, expectedText) {
  const selection = await locator.evaluate(element => {
    const range = document.createRange()
    range.selectNodeContents(element)
    const current = window.getSelection()
    current.removeAllRanges()
    current.addRange(range)
    return {
      text: current.toString(),
      userSelect: getComputedStyle(element).userSelect,
      inert: Boolean(element.closest('[inert]'))
    }
  })
  expect(selection).toEqual({
    text: expectedText,
    userSelect: selection.userSelect,
    inert: false
  })
  expect(selection.userSelect).not.toBe('none')
}

async function expectDialogInsideViewport(page, dialog) {
  const viewport = page.viewportSize()
  await expect(dialog).toBeVisible()
  await expect.poll(async () => {
    const box = await dialog.boundingBox()
    return box &&
      box.x >= 0 &&
      box.y >= 0 &&
      box.x + box.width <= viewport.width + 1 &&
      box.y + box.height <= viewport.height + 1
  }).toBe(true)
}

async function expectStyleUsesToken(locator, property, token) {
  await expect(locator).toBeVisible()
  const colors = await locator.evaluate((element, options) => {
    const probe = document.createElement('span')
    probe.style[options.property] = `var(${options.token})`
    document.body.appendChild(probe)
    const actual = getComputedStyle(element)[options.property]
    const expected = getComputedStyle(probe)[options.property]
    probe.remove()
    return { actual, expected }
  }, { property, token })
  expect(colors.actual).toBe(colors.expected)
}

async function activateTab(page, name) {
  const tab = page.getByRole('tab', { name, exact: true })
  await tab.click()
  await expect(tab).toHaveAttribute('aria-selected', 'true')
  const pane = page.locator(`#${await tab.getAttribute('aria-controls')}`)
  await expect(pane).toBeVisible()
  await expect(pane).toHaveAttribute('aria-hidden', 'false')
  await expect(pane).not.toHaveAttribute('inert', '')
  return pane
}

test('函数管理的筛选、函数测试和新建弹窗可用', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1440, height: 900 })
  const { requests, assertClean } = await installDistRoutes(page, {
    apiData: createOperationsApiData()
  })
  await page.goto('http://tianshu.local/index.html#/function')

  const code = page.getByText('calcRisk', { exact: true }).first()
  await expect(code).toBeVisible()
  await expectTextSelectable(code, 'calcRisk')

  const codeInput = page.locator('.el-form-item')
    .filter({ hasText: '函数编码' })
    .locator('input:visible')
    .first()
  await codeInput.fill('calcRisk')
  const before = requests.filter(request =>
    new URL(request.url).pathname === '/api/rule/function/list'
  ).length
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => requests.filter(request =>
    new URL(request.url).pathname === '/api/rule/function/list'
  ).length).toBeGreaterThan(before)
  expect(new URL(requests.filter(request =>
    new URL(request.url).pathname === '/api/rule/function/list'
  ).at(-1).url).searchParams.get('funcCode')).toBe('calcRisk')

  const row = page.getByRole('row').filter({ hasText: 'calcRisk' })
  await row.getByRole('button', { name: '测试', exact: true }).click()
  const testDialog = page.getByRole('dialog', { name: '函数测试' })
  await expectDialogInsideViewport(page, testDialog)
  await testDialog.getByRole('button', { name: '执行测试' }).click()
  await expect(testDialog.getByText('20', { exact: true })).toBeVisible()
  await testDialog.getByRole('button', { name: '关闭', exact: true }).click()

  await page.getByRole('button', { name: '新建函数' }).click()
  const createDialog = page.getByRole('dialog', { name: '新建函数' })
  await expectDialogInsideViewport(page, createDialog)
  await page.keyboard.press('Escape')
  await expect(createDialog).toBeHidden()
  await expectNoRootOverflow(page)
  expect(pageErrors).toEqual([])
  assertClean()
})

test('规则测试可选择规则、加载字段并展示执行结果', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1440, height: 900 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createOperationsApiData()
  })
  await page.goto('http://tianshu.local/index.html#/test')

  const ruleSelect = page.locator('.test-left .el-form-item')
    .filter({ hasText: '规则' })
    .locator('.el-select')
  await ruleSelect.click()
  await page.getByRole('option', { name: /age_rule/ }).click()

  await expect(page.getByText('测试输入已就绪', { exact: true })).toBeVisible()
  await expect(page.getByText(
    '已从统一测试结构加载 1 个参数；重新加载会保留已填写值。',
    { exact: true }
  )).toBeVisible()
  await expect(page.locator('.param-key input')).toHaveValue('age')
  await expect(page.getByText('(年龄)', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '执行测试' }).click()
  await expect(page.getByText('执行成功')).toBeVisible()
  const result = page.locator('.result-pre').filter({ hasText: 'approved' }).first()
  await expect(result).toBeVisible()
  await expectTextSelectable(result, await result.innerText())
  await expectNoRootOverflow(page)
  expect(pageErrors).toEqual([])
  assertClean()
})

test('血缘分析可生成关系图、拖动节点、缩放并恢复最佳分布', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1440, height: 900 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createOperationsApiData()
  })
  await page.goto('http://tianshu.local/index.html#/lineage')

  const startSelect = page.locator('.query-panel .el-form-item')
    .filter({ hasText: '起点' })
    .locator('.el-select')
  await startSelect.click()
  await page.getByRole('option', { name: '年龄 (age)' }).click()
  await page.getByRole('button', { name: '生成血缘图' }).click()

  const current = page.locator('.current-node .node-code')
  const downstream = page.locator('.branch-node .node-code')
  await expect(current).toHaveText('age')
  await expect(downstream).toHaveText('age_rule')
  await expectTextSelectable(downstream, 'age_rule')

  await expect(page.getByRole('button', { name: '放大血缘图' })).toBeVisible()
  const zoomPercent = page.locator('.zoom-percent')
  const initialZoom = await zoomPercent.innerText()
  await page.getByRole('button', { name: '放大血缘图' }).click()
  await expect(zoomPercent).not.toHaveText(initialZoom)

  const branchNode = page.locator('.branch-node').first()
  const initialLeft = await branchNode.evaluate(element => element.style.left)
  const box = await branchNode.boundingBox()
  await page.mouse.move(box.x + 6, box.y + 6)
  await page.mouse.down()
  await page.mouse.move(box.x + 86, box.y + 36)
  await page.mouse.up()
  await expect(branchNode).not.toHaveCSS('left', initialLeft)

  await page.getByRole('button', { name: '一键回到最佳分布' }).click()
  await expect(branchNode).toHaveCSS('left', initialLeft)
  await page.setViewportSize({ width: 1920, height: 1200 })
  const graphWrap = page.locator('.graph-wrap')
  await expect.poll(async () => {
    const graphBox = await graphWrap.boundingBox()
    return 1200 - graphBox.y - graphBox.height
  }).toBeLessThanOrEqual(40)

  // 将节点拖到 SVG 原始边界之外，但仍留在可视画布中。
  const currentNode = page.locator('.current-node')
  const currentBox = await currentNode.boundingBox()
  const graphBox = await graphWrap.boundingBox()
  const edgeLayer = page.locator('.edge-layer')
  const edgeBox = await edgeLayer.boundingBox()
  await page.mouse.move(currentBox.x + 6, currentBox.y + 6)
  await page.mouse.down()
  await page.mouse.move(currentBox.x + 6, graphBox.y + 40)
  await page.mouse.up()
  expect((await currentNode.boundingBox()).y + currentBox.height).toBeLessThan(edgeBox.y)
  expect(await edgeLayer.locator('.edge-path').first().evaluate(path => path.getBBox().y)).toBeLessThan(0)
  await expect(edgeLayer).toHaveCSS('overflow', 'visible')
  await expect(graphWrap).toHaveCSS('overflow', 'hidden')
  await page.getByRole('button', { name: '一键回到最佳分布' }).click()
  await expectNoRootOverflow(page)
  expect(pageErrors).toEqual([])
  assertClean()
})

test('血缘共享依赖在部分规则收起及对象展开后保持层级和连线', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1920, height: 1080 })
  const apiData = createOperationsApiData()
  const startNode = { id: 'VARIABLE:311', refId: 311, type: 'VARIABLE', code: 'credit_apply_count_1m', label: '近1个月授信申请次数' }
  const object = { id: 'DATA_OBJECT:12', refId: 12, type: 'DATA_OBJECT', code: 'request', label: '申请数据' }
  const ruleIds = [30, 31, 32, 33, 34, 35]
  apiData.set('/api/rule/lineage/options', [{ ...startNode, id: 311, displayName: '近1个月授信申请次数 (credit_apply_count_1m)' }])
  apiData.set('/api/rule/lineage/graph', {
    startNode,
    nodes: [startNode,
      { id: 'PROJECT:4', refId: 4, type: 'PROJECT', code: 'project', label: '共享项目' },
      ...ruleIds.map(id => ({ id: `RULE:${id}`, refId: id, type: 'RULE', code: `rule${id}`, hasUpstream: true })),
      { id: 'DATA_FIELD:125', refId: 125, type: 'DATA_FIELD', code: 'gaid', dataObject: object }
    ],
    edges: [
      { from: 'PROJECT:4', to: startNode.id, label: '项目包含' },
      ...ruleIds.flatMap(id => [
        { from: 'PROJECT:4', to: `RULE:${id}`, label: '项目包含' },
        { from: 'DATA_FIELD:125', to: `RULE:${id}`, label: '规则输入' },
        { from: `RULE:${id}`, to: startNode.id, label: '规则输出' }
      ])
    ]
  })
  const { assertClean } = await installDistRoutes(page, { apiData })
  await page.goto('http://tianshu.local/index.html#/lineage')
  await page.getByRole('combobox', { name: '起点', exact: true }).click()
  await page.getByRole('option', { name: '近1个月授信申请次数 (credit_apply_count_1m)', exact: true }).click()
  await page.getByRole('button', { name: '生成血缘图', exact: true }).click()
  await expect(page.locator('.current-node .node-code')).toHaveText('credit_apply_count_1m')
  const node = id => page.locator(`[data-node-id="${id}"]`)
  const assertLayers = async expanded => {
    const currentX = (await page.locator('.current-node').boundingBox()).x
    const projectX = (await node('PROJECT:4').boundingBox()).x
    const objectX = (await node('DATA_OBJECT:12').boundingBox()).x
    const ruleXs = []
    for (const id of ruleIds) ruleXs.push((await node(`RULE:${id}`).boundingBox()).x)
    expect(Math.max(...ruleXs) - Math.min(...ruleXs)).toBeLessThan(1)
    expect(projectX).toBeLessThan(ruleXs[0])
    expect(objectX).toBeLessThan(ruleXs[0])
    expect(ruleXs[0]).toBeLessThan(currentX)
    if (expanded) {
      const fieldX = (await node('DATA_FIELD:125').boundingBox()).x
      expect(objectX).toBeLessThan(fieldX)
      expect(fieldX).toBeLessThan(ruleXs[0])
    }
  }
  await assertLayers(false)
  await expect(page.locator('.edge-path')).toHaveCount(19)
  for (const id of ruleIds.slice(0, -1)) {
    await node(`RULE:${id}`).getByRole('button', { name: '收起节点' }).click()
    await expect(page.locator('.edge-path')).toHaveCount(19)
    await assertLayers(false)
  }
  await node('DATA_OBJECT:12').getByRole('button', { name: '展开字段' }).click()
  await expect(node('DATA_FIELD:125')).toHaveCount(1)
  await expect(page.locator('.edge-path')).toHaveCount(20)
  await assertLayers(true)
  await node('RULE:35').getByRole('button', { name: '收起节点' }).click()
  await expect(node('DATA_OBJECT:12')).toHaveCount(0)
  await expect(node('DATA_FIELD:125')).toHaveCount(0)
  await expect(page.locator('.edge-path')).toHaveCount(13)
  await node('RULE:30').getByRole('button', { name: '展开节点' }).click()
  await expect(page.locator('.edge-path')).toHaveCount(20)
  await assertLayers(true)
  await node('DATA_OBJECT:12').getByRole('button', { name: '收起字段' }).click()
  await expect(page.locator('.edge-path')).toHaveCount(19)
  await assertLayers(false)
  expect(pageErrors).toEqual([])
  assertClean()
})

test('登录鉴权开启后隐藏无编辑权限的名单操作', async ({ page }) => {
  const fixtures = createOperationsApiData()
  fixtures.set('/api/auth/console/config', { loginEnabled: true })
  fixtures.set('/api/auth/console/me', {
    username: 'field-viewer',
    permissions: ['field:view']
  })
  fixtures.set('/api/auth/console/preferences/theme', null)
  const { assertClean } = await installDistRoutes(page, { apiData: fixtures })

  await page.goto('http://tianshu.local/index.html#/list')

  await expect(page.getByText('名单管理', { exact: true }).last()).toBeVisible()
  await expect(page.getByRole('button', { name: '新建名单库' })).toHaveCount(0)

  await page.goto('http://tianshu.local/index.html#/designer/table/99')
  await expect(page).toHaveURL(/#\/dashboard$/)
  await page.locator('[data-menu-path="/variable"]').click()
  await expect(page).toHaveURL(/#\/variable$/)
  await expect(page.getByText('变量管理', { exact: true }).last()).toBeVisible()
  await expect(page.getByRole('button', { name: '批量导入' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '新建字段' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '验证规则' })).toHaveCount(0)
  assertClean()
})

test('分流实验列表、筛选与新建详情页可用', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1440, height: 900 })
  const { requests, assertClean } = await installDistRoutes(page, {
    apiData: createOperationsApiData()
  })
  await page.goto('http://tianshu.local/index.html#/experiment')

  const code = page.getByText('risk_ab', { exact: true })
  await expect(code).toBeVisible()
  await expectTextSelectable(code, 'risk_ab')
  const keyword = page.locator('.el-form-item')
    .filter({ hasText: '关键字' })
    .locator('input')
  await keyword.fill('risk')
  await page.getByRole('button', { name: '查询' }).click()
  expect(new URL(requests.filter(request =>
    new URL(request.url).pathname === '/api/rule/experiment/list'
  ).at(-1).url).searchParams.get('keyword')).toBe('risk')

  await page.getByRole('button', { name: '新建实验' }).click()
  await expect(page).toHaveURL(/#\/experiment\/new$/)
  const main = page.getByRole('main')
  await expect(main.getByText('新建分流实验', { exact: true }).first()).toBeVisible()
  await expect(main.getByRole('button', { name: '保存' })).toBeVisible()
  await expect(main.getByRole('button', { name: '返回' })).toBeVisible()
  await expectNoRootOverflow(page)
  await main.getByRole('button', { name: '返回' }).click()
  await expect(code).toBeVisible()
  expect(pageErrors).toEqual([])
  assertClean()
})

test('分流实验验证执行加载 Schema 表单并提交嵌套参数', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1440, height: 900 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createOperationsApiData()
  })
  await page.goto('http://tianshu.local/index.html#/experiment')

  const experimentRow = page.getByRole('row').filter({ hasText: 'risk_ab' })
  const schemaRequestPromise = page.waitForRequest(request =>
    request.method() === 'POST' &&
    new URL(request.url()).pathname === '/api/rule/test-schema'
  )
  await experimentRow.getByRole('button', { name: '验证执行', exact: true }).click()
  const schemaRequest = await schemaRequestPromise
  expect(schemaRequest.postDataJSON()).toEqual({
    targetType: 'EXPERIMENT',
    targetId: 61
  })

  const dialog = page.getByRole('dialog', { name: '验证执行分流实验' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByText(/参数来源：Schema 样例/)).toBeVisible()
  await expect(dialog.locator('.execution-field-grid')).toBeVisible()
  await expect(dialog.locator('.execution-field-cell').filter({ hasText: '申请年龄' }))
    .toBeVisible()
  await expect(dialog.locator('.execution-field-cell').filter({ hasText: '是否会员' }))
    .toBeVisible()

  await dialog.locator('.execution-field-cell').filter({ hasText: '申请年龄' })
    .locator('input').fill('35')
  await dialog.locator('.execution-context-form .el-form-item')
    .filter({ hasText: '请求唯一键' })
    .locator('input:visible').fill('REQ-E2E-20260804')
  await dialog.locator('.execution-context-form .el-form-item')
    .filter({ hasText: '进件时间' })
    .locator('input:visible').fill('2026-08-04T10:30:00')
  await dialog.locator('.execution-identity').click()

  const executeRequestPromise = page.waitForRequest(request =>
    request.method() === 'POST' &&
    new URL(request.url()).pathname === '/api/rule/experiment/execute/risk_ab'
  )
  await dialog.getByRole('button', { name: '验证执行', exact: true }).click()
  const executeRequest = await executeRequestPromise
  expect(executeRequest.postDataJSON()).toEqual({
    params: {
      profile: {
        age: 35,
        isVip: false,
        customerType: 'NEW'
      }
    },
    requestKey: 'REQ-E2E-20260804',
    requestTime: '2026-08-04T10:30:00'
  })

  await expect(dialog.getByText('冠军组', { exact: true })).toBeVisible()
  await expect(dialog.getByText('challenger', { exact: true })).toBeVisible()
  await expect(dialog.locator('.execution-trace')).toContainText('exp_trace_e2e_001')
  await expect(dialog.locator('.execution-trace')).toContainText('耗时：17 ms')
  await expectNoRootOverflow(page)
  expect(pageErrors).toEqual([])
  assertClean()
})

test('执行日志详情和规则集命中统计可用', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1440, height: 900 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createOperationsApiData()
  })
  await page.goto('http://tianshu.local/index.html#/log')

  const trace = page.getByText('trace_rule_001', { exact: true })
  await expect(trace).toBeVisible()
  await expectTextSelectable(trace, 'trace_rule_001')
  const row = page.getByRole('row').filter({ hasText: 'trace_rule_001' })
  await row.getByRole('button', { name: '详情' }).click()
  const drawer = page.getByRole('dialog', { name: '日志详情' })
  await expect(drawer).toBeVisible()
  await expect(drawer.getByText('trace_rule_001', { exact: true })).toBeVisible()
  await drawer.getByRole('button', { name: '关闭' }).click()
  await expect(drawer).toBeHidden()

  const statsTab = page.getByRole('tab', { name: '规则集命中统计' })
  await statsTab.click()
  await expect(statsTab).toHaveAttribute('aria-selected', 'true')
  const statsPane = page.locator('.rule-set-stats')
  await expect(statsPane.getByText('风险规则集', { exact: true })).toBeVisible()
  await expect(statsPane.getByText('80.00%', { exact: true }).first()).toBeVisible()
  await expectNoRootOverflow(page)
  expect(pageErrors).toEqual([])
  assertClean()
})

test('账单配置、明细、汇总及新建弹窗可用', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1280, height: 720 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createOperationsApiData()
  })
  await page.goto('http://tianshu.local/index.html#/billing')

  const configCode = page.getByText('engine_call', { exact: true }).first()
  await expect(configCode).toBeVisible()
  await expectTextSelectable(configCode, 'engine_call')
  await page.getByRole('button', { name: '新建计费项' }).click()
  const dialog = page.getByRole('dialog', { name: '新建计费配置审批' })
  await expectDialogInsideViewport(page, dialog)
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()

  const recordPane = await activateTab(page, '计费明细')
  await expect(recordPane.getByText('age_rule', { exact: true })).toBeVisible()
  await expectTextSelectable(recordPane.getByText('TOKEN_E2E', { exact: true }), 'TOKEN_E2E')

  const summaryPane = await activateTab(page, '计费汇总')
  await expect(summaryPane.getByText('2026-07-23', { exact: true })).toBeVisible()
  await expect(summaryPane.getByText('CNY 0.1', { exact: true })).toBeVisible()
  await expectNoRootOverflow(page)
  expect(pageErrors).toEqual([])
  assertClean()
})

test('夜间模式下运行页面的提示区、代码块和血缘画布使用主题色', async ({
  page
}) => {
  await page.addInitScript(() => {
    localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({
      schemaVersion: 1,
      colorScheme: 'DARK',
      accentPreset: 'LIQUID_PURPLE',
      sidebarTheme: 'DARK',
      contentWidth: 'FLUID',
      fixedSidebar: true,
      colorWeak: false
    }))
  })
  const fixtures = createDetailApiData()
  fixtures.set('POST /api/rule/definition/execute', {
    success: false,
    errorMessage: '规则执行失败：测试异常',
    executeTimeMs: 6,
    traces: []
  })
  const logList = fixtures.get('/api/rule/log/list')
  fixtures.set('/api/rule/log/list', {
    ...logList,
    records: logList.records.map(row => ({
      ...row,
      success: 0,
      inputParams: { age: 18 },
      outputResult: null,
      errorMessage: '日志执行失败：测试异常'
    }))
  })
  const { assertClean } = await installDistRoutes(page, { apiData: fixtures })

  await page.goto('http://tianshu.local/index.html#/list')
  await expectStyleUsesToken(
    page.locator('.approval-guide'),
    'backgroundColor',
    '--tianshu-info-bg'
  )
  await expectStyleUsesToken(
    page.locator('.approval-guide-title'),
    'color',
    '--tianshu-info-text'
  )

  await page.goto('http://tianshu.local/index.html#/test')
  const ruleSelect = page.locator('.test-left .el-form-item')
    .filter({ hasText: '规则' })
    .locator('.el-select')
  await ruleSelect.click()
  await page.getByRole('option', { name: /age_rule/ }).click()
  const inputReady = page.locator('.input-load-state.is-ready')
  await expectStyleUsesToken(
    inputReady,
    'backgroundColor',
    '--tianshu-success-bg'
  )
  await expectStyleUsesToken(
    inputReady,
    'borderTopColor',
    '--tianshu-success-border'
  )
  await page.getByRole('button', { name: '执行测试' }).click()
  const ruleError = page.locator('.result-pre').filter({ hasText: '规则执行失败' })
  await expectStyleUsesToken(ruleError, 'backgroundColor', '--tianshu-danger-bg')
  await expectStyleUsesToken(ruleError, 'color', '--tianshu-danger-text')

  await page.goto('http://tianshu.local/index.html#/lineage')
  await expectStyleUsesToken(
    page.locator('.query-panel'),
    'borderTopColor',
    '--tianshu-border-subtle'
  )
  const startSelect = page.locator('.query-panel .el-form-item')
    .filter({ hasText: '起点' })
    .locator('.el-select')
  await startSelect.click()
  await page.getByRole('option', { name: '年龄 (age)' }).click()
  await page.getByRole('button', { name: '生成血缘图' }).click()
  await expect(page.locator('.current-node')).toBeVisible()
  const gridLayers = await page.evaluate(() => ({
    wrap: getComputedStyle(document.querySelector('.graph-wrap')).backgroundImage,
    canvas: getComputedStyle(document.querySelector('.graph-canvas')).backgroundImage
  }))
  expect(gridLayers.wrap).not.toBe('none')
  expect(gridLayers.canvas).toBe('none')
  await expectStyleUsesToken(
    page.locator('.current-node .node-label'),
    'color',
    '--tianshu-text-primary'
  )

  await page.goto('http://tianshu.local/index.html#/experiment')
  await expectStyleUsesToken(
    page.locator('.experiment-page h2'),
    'color',
    '--tianshu-text-primary'
  )
  const experimentRow = page.getByRole('row').filter({ hasText: 'risk_ab' })
  await experimentRow.getByRole('button', { name: '验证执行', exact: true }).click()
  const executionDialog = page.getByRole('dialog', { name: '验证执行分流实验' })
  await expectStyleUsesToken(
    executionDialog.locator('.execution-identity'),
    'backgroundColor',
    '--tianshu-info-bg'
  )
  await expectStyleUsesToken(
    executionDialog.locator('.execution-readiness'),
    'backgroundColor',
    '--tianshu-success-bg'
  )

  await page.goto('http://tianshu.local/index.html#/experiment/detail/61')
  await expectStyleUsesToken(
    page.locator('.detail-title'),
    'color',
    '--tianshu-text-primary'
  )
  await expectStyleUsesToken(
    page.locator('.base-form'),
    'borderTopColor',
    '--tianshu-border-subtle'
  )
  await expectStyleUsesToken(
    page.locator('.ratio-config-panel').first(),
    'backgroundColor',
    '--tianshu-info-bg'
  )
  await expectStyleUsesToken(
    page.locator('.ratio-config-title').first(),
    'color',
    '--tianshu-text-primary'
  )

  await page.goto('http://tianshu.local/index.html#/log')
  const logRow = page.getByRole('row').filter({ hasText: 'trace_rule_001' })
  await logRow.getByRole('button', { name: '详情' }).click()
  const logDrawer = page.getByRole('dialog', { name: '日志详情' })
  const logBlocks = logDrawer.locator('.log-pre')
  await expectStyleUsesToken(
    logBlocks.nth(0),
    'backgroundColor',
    '--tianshu-bg-muted'
  )
  await expectStyleUsesToken(
    logBlocks.nth(1),
    'color',
    '--tianshu-text-primary'
  )
  await expectStyleUsesToken(
    logBlocks.filter({ hasText: '日志执行失败' }),
    'backgroundColor',
    '--tianshu-danger-bg'
  )

  await page.goto('http://tianshu.local/index.html#/database')
  const databaseRow = page.getByRole('row').filter({ hasText: 'risk_mysql' })
  await databaseRow.getByRole('button', { name: '查询', exact: true }).click()
  const queryDialog = page.locator('.el-dialog:visible').last()
  const editorBackground = queryDialog.locator(
    '.monaco-editor .monaco-editor-background'
  ).first()
  await expectStyleUsesToken(
    editorBackground,
    'backgroundColor',
    '--tianshu-bg-soft'
  )
  assertClean()
})

test('夜间模式下审批与版本历史区域使用主题表面色', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({
      schemaVersion: 1,
      colorScheme: 'DARK',
      accentPreset: 'THEME_BLUE',
      sidebarTheme: 'DARK',
      contentWidth: 'FLUID',
      fixedSidebar: true,
      colorWeak: false
    }))
  })
  const fixtures = createDetailApiData()
  fixtures.set('/api/rule/governance/requests/summary', {
    pendingCount: 2,
    myDraftCount: 2,
    myRequestCount: 3,
    completedCount: 23
  })
  fixtures.set('/api/rule/governance/requests', {
    records: [{
      id: 91,
      requestNo: 'GOV-E2E-91',
      resourceType: 'RULE',
      resourceId: 101,
      resourceName: '年龄判断规则',
      action: 'PUBLISH',
      status: 'PENDING',
      applicant: 'admin',
      submitTime: '2026-08-04T08:30:00'
    }],
    total: 1
  })
  fixtures.set('/api/rule/definition/versions/101', [
    {
      id: 80,
      definitionId: 101,
      version: 1,
      publishBy: 'admin',
      publishTime: '2026-07-27 06:10:33',
      changeLog: '无变更说明'
    },
    {
      id: 81,
      definitionId: 101,
      version: 2,
      publishBy: 'admin',
      publishTime: '2026-08-04 01:49:04',
      changeLog: '统一审批已确认 Schema 与依赖影响'
    }
  ])
  fixtures.set('/api/rule/definition/versionCompare/101', {
    left: {
      version: 1,
      publishBy: 'admin',
      publishTime: '2026-07-27 06:10:33',
      changeLog: '无变更说明',
      modelJson: JSON.stringify({ script: 'result = age;' })
    },
    right: {
      version: 2,
      publishBy: 'admin',
      publishTime: '2026-08-04 01:49:04',
      changeLog: '统一审批已确认 Schema 与依赖影响',
      modelJson: JSON.stringify({ script: 'result = age + 1;' })
    }
  })
  const { assertClean } = await installDistRoutes(page, { apiData: fixtures })

  await page.goto('http://tianshu.local/index.html#/approval')
  await expectStyleUsesToken(
    page.locator('.summary-card.is-active'),
    'backgroundColor',
    '--tianshu-bg-active'
  )
  await expectStyleUsesToken(
    page.locator('.filter-bar'),
    'backgroundColor',
    '--tianshu-bg-soft'
  )
  await expectStyleUsesToken(
    page.locator('.request-icon').first(),
    'backgroundColor',
    '--tianshu-info-bg'
  )

  await page.goto('http://tianshu.local/index.html#/rule/101')
  await page.getByRole('button', { name: '版本历史', exact: true }).click()
  const versionDialog = page.locator('.el-dialog').filter({ hasText: '版本历史' })
  await expectStyleUsesToken(
    versionDialog.locator('.version-compare-toolbar'),
    'backgroundColor',
    '--tianshu-bg-soft'
  )
  await expectStyleUsesToken(
    versionDialog.locator('.rule-version-side').first(),
    'backgroundColor',
    '--tianshu-bg-soft'
  )
  assertClean()
})
