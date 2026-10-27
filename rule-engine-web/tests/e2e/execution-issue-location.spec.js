const { test, expect } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')
const { withTestExecutionIdentity } = require('./support/testExecutionFixtures.cjs')

for (const width of [1280, 560]) {
test(`${width}px 唯一命中冲突显示业务说明与规则行，可定位到未保存但已测试的画布`, async ({ page }) => {
  await page.setViewportSize({ width, height: 720 })
  const data = createDesignerApiData()
  const rule = { conditionRoot: { type: 'group', op: 'AND', children: [] }, actions: [{ targetOperand: { kind: 'PATH', value: 'decision', valueType: 'STRING' }, valueOperand: { kind: 'LITERAL', value: 'PASS', valueType: 'STRING' } }] }
  const model = { hitPolicy: 'FIRST', rules: [rule, rule] }
  data.set('/api/rule/definition/101/revisions', [{ id: 2101, definitionId: 101, revisionNo: 1, state: 'DRAFT', lockVersion: 0, modelJson: JSON.stringify(model) }])
  data.set('/api/rule/definition/101/published-versions', [])
  data.set('POST /api/rule/test-schema', withTestExecutionIdentity({ inputs: [], runtimeNodes: [], sampleParams: {}, diagnostics: [] }))
  data.set('POST /api/rule/definition/execute', withTestExecutionIdentity({
    success: false, errorMessage: '唯一命中（UNIQUE）冲突：第 1、2 行规则同时满足条件，本次未执行动作。',
    executionErrorDetail: 'INVOKE_FUNCTION_INNER_ERROR at line 8',
    executionIssues: [0, 1].map(index => ({ code: 'UNIQUE_CONFLICT', path: `$.rules[${index}]`, title: `第 ${index + 1} 行同时命中`, message: '此行与其他规则行同时命中，本次未执行动作', nextAction: '调整重叠条件后重新测试' })),
  }))
  const harness = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/#/designer/table/101')
  await expect(page.locator('.dt-rule-card')).toHaveCount(2)
  await page.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '命中策略', exact: true }) }).click()
  await page.getByRole('option', { name: '唯一命中', exact: true }).click()
  await page.getByRole('button', { name: '测试', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '测试执行', exact: true })
  await dialog.getByRole('button', { name: '执行测试', exact: true }).click()
  await expect(dialog).toContainText('本次未执行动作')
  const report = dialog.getByRole('region', { name: '执行问题定位', exact: true })
  await expect(report.getByRole('button', { name: '定位到规则行', exact: true })).toHaveCount(2)
  await expect(report.getByRole('button', { name: '定位到规则行', exact: true }).nth(1)).toBeInViewport()
  await expect(dialog.locator('details')).not.toHaveAttribute('open', '')
  await report.getByRole('button', { name: '定位到规则行', exact: true }).nth(1).click()
  await expect(dialog).toBeHidden()
  await expect(page.locator('[data-validation-path="$.rules[1]"]')).toBeFocused()
  expect(harness.requests.filter(request => request.method === 'POST' && /\/designer\/drafts$/.test(new URL(request.url).pathname))).toEqual([])
  harness.assertClean()
})
}

test('短窗口执行成功后自动显示输出，输入仍可从结果页签核对', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 720 })
  const data = createDesignerApiData()
  data.set('POST /api/rule/test-schema', withTestExecutionIdentity({ inputs: [], runtimeNodes: [], sampleParams: { age: 25 }, diagnostics: [] }))
  data.set('POST /api/rule/definition/execute', withTestExecutionIdentity({ success: true, result: { decision: 'PASS' } }))
  const harness = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/#/designer/table/101')
  await page.getByRole('button', { name: '测试', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '测试执行', exact: true })
  await dialog.getByRole('button', { name: '执行测试', exact: true }).click()
  await expect(dialog.getByRole('tabpanel', { name: '执行输出', exact: true })).toBeInViewport()
  await expect(dialog.getByRole('tabpanel', { name: '执行输出', exact: true })).toContainText('PASS')
  await dialog.getByRole('tab', { name: '本次输入', exact: true }).click()
  await expect(dialog.getByRole('tabpanel', { name: '本次输入', exact: true })).toContainText('25')
  harness.assertClean()
})
