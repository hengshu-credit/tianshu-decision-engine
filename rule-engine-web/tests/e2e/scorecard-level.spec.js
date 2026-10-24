const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')

for (const [route, id, prefix] of [['score', 106, 'sc'], ['score-adv', 108, 'asc']]) {
  test(`${route} 等级区间的编辑、结果转换、保存重载和删除保持一致`, async ({ page }, testInfo) => {
    const apiData = createDesignerApiData()
    const base = `/api/rule/definition/${id}`
    let draft = { id: 9002, definitionId: id, revisionNo: 1, state: 'DRAFT', lockVersion: 0, modelJson: JSON.stringify({
      resultVar: { varCode: 'score' }, thresholds: [{ min: 0, max: 100, result: route === 'score' ? '基础档' : '"基础档"', resultOperand: { kind: 'LITERAL', value: '基础档', valueType: 'STRING' } }],
    }) }
    const saved = []
    apiData.set(`${base}/revisions`, () => [draft])
    apiData.set(`${base}/revisions/9002`, () => draft)
    apiData.set(`POST ${base}/designer/drafts`, ({ request }) => {
      const body = request.postDataJSON()
      saved.push(JSON.parse(body.modelJson))
      draft = { ...draft, lockVersion: draft.lockVersion + 1, modelJson: body.modelJson }
      return { revision: draft, compileSuccess: true, issues: [] }
    })
    const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
    const { assertClean } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData })
    await page.setViewportSize({ width: 1600, height: 1000 })
    await page.goto(`${origin}/#/designer/${route}/${id}?sourceType=REVISION&sourceId=9002`)
    const rows = page.locator('.threshold-item')
    await expect(rows).toHaveCount(1)
    expect(await rows.first().evaluate(element => ({ padding: getComputedStyle(element).padding, gap: getComputedStyle(element).gap })))
      .toEqual(route === 'score' ? { padding: '8px 10px', gap: '10px' } : { padding: '10px 12px', gap: '12px' })
    await page.getByRole('spinbutton', { name: '等级 1 最大分数', exact: true }).fill('50')
    await page.getByRole('button', { name: '添加等级', exact: true }).click()
    await expect(rows).toHaveCount(2)
    await expect(page.getByRole('spinbutton', { name: '等级 2 最小分数', exact: true })).toHaveValue('50')
    await rows.nth(1).getByRole('textbox', { name: '打开内容选择器', exact: true }).click()
    const picker = page.getByRole('dialog', { name: '内容选择器', exact: true })
    await picker.getByRole('button', { name: '输入阈值', exact: true }).click()
    await picker.getByRole('textbox', { name: /^阈值内容/ }).fill('第二档')
    await picker.getByRole('button', { name: '应用阈值', exact: true }).click()
    const save = async () => {
      await page.getByRole('button', { name: '保存', exact: true }).click()
      await page.getByRole('dialog', { name: '保存草稿', exact: true }).locator('[data-action="confirm-save"]').click()
    }
    await save()
    await expect.poll(() => saved.length).toBe(1)
    expect(saved[0].thresholds).toHaveLength(2)
    expect(saved[0].thresholds[0]).toMatchObject({ min: 0, max: 50 })
    expect(saved[0].thresholds[1]).toMatchObject({ min: 50, max: 100, result: route === 'score' ? '第二档' : '"第二档"', resultOperand: { kind: 'LITERAL', value: '第二档', valueType: 'STRING' } })
    await page.reload()
    await expect(rows).toHaveCount(2)
    await expect(rows.nth(1).getByRole('textbox', { name: '打开内容选择器', exact: true })).toHaveValue(/第二档/)
    await page.screenshot({ path: testInfo.outputPath('shared-thresholds.png') })
    await page.getByRole('button', { name: '删除等级 2', exact: true }).click()
    await save()
    await expect.poll(() => saved.length).toBe(2)
    expect(saved[1].thresholds).toEqual([saved[0].thresholds[0]])
    assertClean()
  })

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
    const picker = page.getByRole('dialog', { name: '内容选择器', exact: true })
    await picker.getByRole('textbox', { name: '搜索内容名称或编码' }).fill('Grade_Code')
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
