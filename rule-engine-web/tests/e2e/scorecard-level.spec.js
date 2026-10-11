const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')

for (const [route, id, prefix] of [['score', 106, 'sc'], ['score-adv', 108, 'asc']]) {
  test(`${route} 从基础配置选择等级字段，保存重载后保留ID，清空后仍可保存分数配置`, async ({ page }, testInfo) => {
    const apiData = createDesignerApiData()
    apiData.get('/api/rule/variable/project/1').push({ id: 27, varCode: 'Grade_Code', varLabel: '风险等级', varType: 'STRING', varSource: 'OUTPUT' })
    const base = `/api/rule/definition/${id}`
    let draft = { id: 9001, definitionId: id, revisionNo: 1, state: 'DRAFT', lockVersion: 0, modelJson: '{"resultVar":{"varCode":"score"}}' }
    const saved = []
    apiData.set(`${base}/revisions`, () => [draft])
    apiData.set(`${base}/revisions/9001`, () => draft)
    apiData.set(`POST ${base}/designer/drafts`, ({ request }) => {
      const body = request.postDataJSON()
      saved.push(JSON.parse(body.modelJson))
      draft = { ...draft, lockVersion: draft.lockVersion + 1, modelJson: body.modelJson }
      return { revision: draft, compileSuccess: true, issues: [] }
    })
    const { assertClean } = await installDistRoutes(page, { apiData })
    await page.setViewportSize({ width: 1600, height: 1000 })
    await page.goto(`http://tianshu.local/index.html#/designer/${route}/${id}?sourceType=REVISION&sourceId=9001`)
    const baseConfig = page.locator(`.${prefix}-base-config`)
    await expect(baseConfig).toContainText('等级赋值字段（可选）')
    const target = baseConfig.locator('.operand-picker').filter({ has: page.getByPlaceholder('不选则仅输出分数') })
    await target.getByRole('textbox').click()
    const picker = page.getByRole('dialog', { name: '字段选择器', exact: true })
    await picker.getByRole('textbox', { name: '搜索字段名称或编码' }).fill('Grade_Code')
    await picker.getByRole('row', { name: /风险等级.*Grade_Code/ }).dblclick()
    await expect(target.getByRole('textbox')).toHaveValue(/Grade_Code/)
    await page.getByRole('button', { name: '添加等级', exact: true }).click()
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.getByRole('dialog', { name: '保存草稿', exact: true })).toBeVisible()
    await page.getByRole('dialog', { name: '保存草稿', exact: true }).locator('[data-action="confirm-save"]').click()
    await expect.poll(() => saved.length).toBe(1)
    expect(saved[0].levelVar).toMatchObject({ _varId: 27, _refType: 'VARIABLE', operand: { refId: 27, refType: 'VARIABLE', code: 'Grade_Code' } })
    await page.reload()
    await expect(target.getByRole('textbox')).toHaveValue(/Grade_Code/)
    await page.screenshot({ path: testInfo.outputPath('level-target.png') })
    await target.locator('.vp-clear-btn').click()
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await page.getByRole('dialog', { name: '保存草稿', exact: true }).locator('[data-action="confirm-save"]').click()
    await expect.poll(() => saved.length).toBe(2)
    expect(saved[1].levelVar).toMatchObject({ operand: null, _varId: null, varCode: '' })
    expect(saved[1].thresholds).toHaveLength(1)
    assertClean()
  })
}
