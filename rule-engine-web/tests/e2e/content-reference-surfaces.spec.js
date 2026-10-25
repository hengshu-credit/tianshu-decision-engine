const { test, expect } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')
const { createOperationsApiData } = require('./support/operationsFixtures.cjs')

async function checkBlocks(root) {
  await expect(root).toBeVisible()
  const parts = await root.evaluate(element => [...element.children].filter(child => child.dataset.contentPart).map(child => ({ key: child.dataset.contentPart, x: child.getBoundingClientRect().x, right: child.getBoundingClientRect().right, border: getComputedStyle(child).borderRadius, margin: getComputedStyle(child).margin })))
  expect(parts.slice(0, 2).map(part => part.key)).toEqual(['source', 'type'])
  for (let i = 1; i < parts.length; i++) expect(Math.abs(parts[i].x - parts[i - 1].right)).toBeLessThanOrEqual(0.5)
  expect(parts.every(part => part.border === '0px' && part.margin === '0px')).toBe(true)
}
async function pick(page, input, code) {
  await input.click()
  const panel = page.locator('.fp-panel:visible').last()
  await panel.getByRole('textbox', { name: '搜索内容名称或编码' }).fill(code)
  await panel.getByRole('gridcell', { name: code, exact: true }).dblclick()
}
const variable = { id: 1, varCode: 'clientCode', varLabel: '客户编码', varType: 'STRING', varSource: 'INPUT', version: 3 }

test('冻结 Monaco 脚本反解内置方法和固定版本引用，不借当前资源名称', async ({ page }, testInfo) => {
  const data = createDesignerApiData()
  const script = 'return resourceVersionValue("VARIABLE", "548", "BUSINESS_VERSION:948", "");'
  const revision = { id: 2109, definitionId: 109, revisionNo: 3, state: 'PUBLISHED', artifactId: 10, lockVersion: 0, modelJson: JSON.stringify({ script, scriptVarRefs: [] }) }
  data.set('/api/rule/definition/109/revisions', [revision])
  data.set('/api/rule/definition/109/revisions/2109', revision)
  data.set('/api/rule/definition/109/reference-catalog', ({ url }) => ({ status: 'AVAILABLE', referenceNamespace: 'ARTIFACT_SOURCE', localEditingAllowed: true,
    executionIdentity: { definitionId: '109', sourceType: url.searchParams.get('sourceType'), sourceId: url.searchParams.get('sourceId') },
    variables: [{ id: '548', varCode: 'current_age', varLabel: '其他版本年龄', varType: 'INTEGER' }], objectTree: [], functions: [], models: [], rules: [], apis: [], lists: [],
    versionReferences: [{ refType: 'VARIABLE', refId: '548', versionId: '948', versionSource: 'BUSINESS_VERSION', version: 7, displayVersion: 3, code: 'age_old', label: '历史年龄', valueType: 'INTEGER' }],
  }))
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/#/designer/script/109')
  const lines = page.locator('.view-lines')
  await expect(lines.locator('.content-ref__source')).toHaveText(['方法', '变量'])
  // Monaco 在不同字体度量下会把注入的留白拆成独立 span；只核对实际显示的名称。
  await expect(lines.locator('.content-ref__name').filter({ hasText: /\S/ })).toHaveText(['固定版本取值', '历史年龄'])
  await expect(lines.locator('.content-ref__code')).toHaveText(['resourceVersionValue', 'age_old'])
  await expect(lines.locator('.content-ref__version')).toHaveText(['内置', 'v3'])
  await expect(lines).not.toContainText('其他版本年龄')
  const overflow = await lines.evaluate(element => {
    const viewport = element.closest('.monaco-scrollable-element').getBoundingClientRect()
    return [...element.querySelectorAll('.content-ref__segment')].some(part => part.getBoundingClientRect().right > viewport.right + 1)
  })
  expect(overflow).toBe(false)
  expect(await page.evaluate(() => window.monaco.editor.getModels().some(model => model.getValue() === 'return resourceVersionValue("VARIABLE", "548", "BUSINESS_VERSION:948", "");'))).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('monaco-fixed-method-reference.png'), animations: 'disabled' })
  assertClean()
})

for (const module of ['API', '数据源鉴权']) test(`${module} 选择后使用通用连续色块`, async ({ page }) => {
  const data = createDetailApiData()
  data.set('/api/rule/variable/project/1', [variable])
  if (module === 'API') Object.assign(data.get('/api/rule/datasource/api-config/22'), { authMode: 'BASIC', authApiConfig: JSON.stringify({ username: null, password: null }) })
  else Object.assign(data.get('/api/rule/datasource/21'), { authType: 'BASIC', authConfig: JSON.stringify({ username: null, password: null }) })
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto(`http://tianshu.local/#/datasource/${module === 'API' ? 'api/22' : 'source/21'}`)
  const username = page.locator('.api-auth-editor .el-form-item').filter({ hasText: /^用户名/ })
  await pick(page, username.locator('input'), 'clientCode')
  await checkBlocks(username.locator('.content-ref'))
  await expect(username.locator('.content-ref__code')).toHaveText('clientCode')
  assertClean()
})

test('规则配置与编译脚本共用来源、类型、名称、编码和版本', async ({ page }) => {
  const data = createDesignerApiData()
  data.set('POST /api/rule/definition/101/designer/compile', { compileSuccess: true, compiledScript: 'return age;', preflightReport: { valid: true, errors: [], warnings: [] } })
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/#/designer/table/101')
  await page.getByRole('button', { name: '添加行', exact: true }).click()
  const input = page.getByPlaceholder('选择左操作数...').first()
  await pick(page, input, 'age')
  await checkBlocks(input.locator('xpath=ancestor::div[contains(@class,"vp-reference")]').locator('.content-ref'))
  await page.getByRole('button', { name: '编译', exact: true }).click()
  await page.getByRole('button', { name: '展开脚本面板', exact: true }).click()
  await expect(page.locator('.script-panel .content-ref__source')).toHaveText('变量')
  await expect(page.locator('.script-panel .content-ref__code')).toHaveText('age')
  assertClean()
})

test('规则开放接口与模型入出参均复用通用色块', async ({ page }) => {
  const { assertClean } = await installDistRoutes(page, { apiData: createDetailApiData() })
  await page.goto('http://tianshu.local/#/rule/101')
  await page.getByRole('tab', { name: /开放接口/ }).click()
  await checkBlocks(page.locator('.open-api-section .content-ref').first())
  await page.goto('http://tianshu.local/#/model/41')
  await checkBlocks(page.locator('.operand-value-display').first())
  await page.getByRole('tab', { name: /输出字段/ }).click()
  await checkBlocks(page.locator('.el-tab-pane:visible .operand-value-display').first())
  assertClean()
})

test('分流实验请求键及执行规则展示通用内容', async ({ page }) => {
  const data = createDetailApiData()
  data.set('/api/rule/variable/project/1', [variable])
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/#/experiment/detail/61')
  await pick(page, page.getByPlaceholder('选择字段或配置表达式', { exact: true }), 'clientCode')
  await checkBlocks(page.locator('.base-form .content-ref').first())
  await checkBlocks(page.locator('.group-action-form .content-ref').first())
  assertClean()
})

test('账单指定计费对象使用相同色块', async ({ page }) => {
  const data = createDetailApiData()
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/#/billing')
  await page.getByRole('row').filter({ hasText: 'engine_call' }).getByRole('button', { name: '编辑', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: /计费配置/ })
  await checkBlocks(dialog.locator('.content-ref').first())
  await expect(dialog.locator('.content-ref__source')).toHaveText('规则')
  assertClean()
})

test('血缘图节点和信息面板使用通用色块', async ({ page }, testInfo) => {
  const data = createOperationsApiData()
  const graph = data.get('/api/rule/lineage/graph')
  graph.startNode.valueType = 'INTEGER'; graph.startNode.version = 3
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/#/lineage')
  await page.locator('.query-panel .el-form-item').filter({ hasText: '起点' }).locator('.el-select').click()
  await page.getByRole('option', { name: '年龄 (age)' }).click()
  await page.getByRole('button', { name: '生成血缘图' }).click()
  const root = page.locator('.current-node .content-ref')
  await checkBlocks(root)
  await expect(root.locator('.content-ref__type')).toHaveText('123')
  await root.click()
  await checkBlocks(page.getByTestId('lineage-node-info-panel').locator('.content-ref').first())
  await page.screenshot({ path: testInfo.outputPath('lineage-content-reference.png'), animations: 'disabled' })
  assertClean()
})

test('执行日志的表达式追踪保留独立变量与阈值色块', async ({ page }) => {
  const data = createOperationsApiData()
  const record = data.get('/api/rule/log/list').records[0]
  record.modelType = 'SCRIPT'
  record.traceInfo = JSON.stringify([{ type: 'OPERATOR', token: '>', evaluated: true, value: true, children: [{ type: 'VARIABLE', token: 'age', label: '年龄', refType: 'VARIABLE', refId: 1, valueType: 'INTEGER', version: 3, value: 28, evaluated: true }, { type: 'VALUE', token: '18', value: 18, valueType: 'NUMBER', evaluated: true }] }])
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/#/log')
  await page.getByRole('row').filter({ hasText: 'trace_rule_001' }).getByRole('button', { name: '详情', exact: true }).click()
  await page.getByRole('tab', { name: /表达式追踪树/ }).click()
  await checkBlocks(page.locator('.trace-wrap .content-ref').first())
  await expect(page.locator('.trace-wrap .content-ref__code').filter({ hasText: /^age$/ }).first()).toBeVisible()
  assertClean()
})
