const { test, expect } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')

for (const width of [1440, 560]) test(`复杂交叉表 ${width}px 删除中间分段不会错配结果，新增分段为空`, async ({ page }, testInfo) => {
  const data = createDesignerApiData()
  const model = {
    rowDimensions: [{ varCode: 'age', varLabel: '年龄', varType: 'NUMBER', _varId: 1, _refType: 'VARIABLE', segments: [10, 20, 30].map(value => ({ label: String(value), operator: '==', value: String(value) })) }],
    colDimensions: [{ varCode: 'income', varLabel: '收入', varType: 'NUMBER', _varId: 2, _refType: 'VARIABLE', segments: [1, 2].map(value => ({ label: String(value), operator: '==', value: String(value) })) }],
    resultVar: { varCode: 'result', varType: 'NUMBER', operand: { kind: 'PATH', value: 'result', valueType: 'NUMBER' } },
    cells: [['101', '102'], ['201', '202'], ['301', '302']],
  }
  data.set('/api/rule/definition/107/revisions', [{ id: 2107, definitionId: 107, revisionNo: 1, state: 'DRAFT', lockVersion: 0, modelJson: JSON.stringify(model) }])
  const harness = await installDistRoutes(page, { apiData: data })
  await page.setViewportSize({ width, height: 1000 })
  await page.goto('http://tianshu.local/#/designer/cross-adv/107')
  const values = () => page.locator('.act-matrix .data-cell input').evaluateAll(inputs => inputs.map(input => input.value))
  await expect.poll(values).toEqual(['101', '102', '201', '202', '301', '302'])
  await page.getByRole('button', { name: '删除行维度 1 分段 2', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '确认矩阵变更', exact: true })
  await expect(dialog).toContainText('2 个已配置结果')
  await dialog.getByRole('button', { name: '保留配置', exact: true }).click()
  await expect.poll(values).toEqual(['101', '102', '201', '202', '301', '302'])
  await page.getByRole('button', { name: '删除行维度 1 分段 2', exact: true }).click()
  await dialog.getByRole('button', { name: '继续变更', exact: true }).click()
  await expect.poll(values).toEqual(['101', '102', '301', '302'])
  await page.getByRole('button', { name: '添加分段', exact: true }).first().click()
  await expect.poll(values).toEqual(['101', '102', '301', '302', '', ''])
  await page.getByRole('button', { name: '添加行维度', exact: true }).click()
  await expect(dialog).toContainText('4 个已配置结果')
  await dialog.getByRole('button', { name: '保留配置', exact: true }).click()
  await expect.poll(values).toEqual(['101', '102', '301', '302', '', ''])
  await page.screenshot({ path: testInfo.outputPath(`matrix-${width}.png`), fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(harness.requests.filter(request => request.method !== 'GET')).toEqual([])
  harness.assertClean()
})
