const fs = require('node:fs')
const { expect, test } = require('@playwright/test')
const { login } = require('./support/optimizationLive.cjs')
const groups = require('../../src/constants/faker-methods.json')

test('逐条点击全局 Faker 规则并核对默认结果和带参结果', async ({ page }, testInfo) => {
  test.skip(!process.env.E2E_BASE_URL, '需要已初始化且启用了默认 Mock 规则的联调环境')
  test.setTimeout(900000)
  await page.setViewportSize({ width: 1600, height: 1050 })
  await login(page, '/test')
  const listed = page.waitForResponse(r => r.url().endsWith('/test-data/rules') && r.request().method() === 'GET')
  await page.getByRole('tab', { name: 'Mock 规则', exact: true }).click()
  const rows = (await (await listed).json()).data.filter(r => r.builtin && r.generator === 'FAKER' && r.projectId == null)
  const defaultRules = new Map(rows.map(row => {
    const cfg = JSON.parse(row.configJson)
    return [typeof cfg === 'string' ? cfg : cfg.method, row]
  }))
  expect(rows.length).toBe(262)
  expect(defaultRules.size).toBe(262)
  const pane = page.locator('.test-management:visible')
  const dialog = page.getByRole('dialog', { name: '测试 Mock 规则', exact: true })
  const evidence = []
  const keyword = pane.getByPlaceholder('规则名称、方法或匹配字段')
  async function sample(button, key, type, id, args) {
    const [response] = await Promise.all([
      page.waitForResponse(r => r.url().endsWith('/test-data/rules/preview') && r.request().method() === 'POST'),
      button.click(),
    ])
    const sent = response.request().postDataJSON()
    expect(String(sent.id), key).toBe(String(id))
    const config = JSON.parse(sent.configJson)
    expect(typeof config === 'string' ? config : config.method).toBe(key)
    if (args) expect(config.args).toEqual(args)
    const body = await response.json()
    expect(body.code, key + ' ' + body.message).toBe('000000')
    const raw = body.data.valueJson
    await expect(dialog.getByLabel('生成预览')).toHaveText(raw)
    const value = JSON.parse(raw)
    const expected = { STRING: 'string', NUMBER: 'number', DATETIME: 'string', BOOLEAN: 'boolean', OBJECT: 'object', LIST: 'object' }
    expect(typeof value, key).toBe(expected[type])
    expect(Array.isArray(value), key).toBe(type === 'LIST')
    expect(value, key).not.toBeNull()
    if (type === 'DATETIME') expect(Number.isNaN(Date.parse(value))).toBe(false)
    return raw
  }
  for (const group of groups) {
    await keyword.fill('')
    await pane.getByRole('combobox', { name: '规则类型' }).click()
    await page.getByRole('option', { name: group.label + ' · ' + group.module, exact: true }).click()
    await pane.getByRole('button', { name: '查询', exact: true }).click()
    for (const method of group.methods) {
      const key = group.module + '.' + method.name
      const rule = defaultRules.get(key)
      expect(rule, key).toMatchObject({ name: method.label, ruleType: group.label, ruleTypeCode: group.module })
      // 一个类别可能超过每页 20 条；通过真实搜索框定位当前方法。
      if (group.methods.length > 20) {
        await keyword.fill(key)
        await keyword.press('Enter')
      }
      const row = pane.getByRole('row').filter({ has: page.locator(`button[data-rule-id="${rule.id}"]`) })
      await expect(row.getByRole('cell', { name: key, exact: true })).toBeVisible()
      await expect(row.getByRole('cell', { name: method.label, exact: true }).first()).toBeVisible()
      const result = { ruleType: rule.ruleType, ruleTypeCode: rule.ruleTypeCode, ruleName: rule.name, method: key, ruleId: rule.id, type: method.type }
      result.defaultJson = await sample(row.getByRole('button', { name: '测试', exact: true }), key, method.type, rule.id)
      if (method.parameters) {
        await dialog.getByRole('button', { name: '填入示例参数' }).click()
        result.args = method.exampleArgs
        result.parameterizedJson = await sample(dialog.getByRole('button', { name: '试生成' }), key, method.type, rule.id, result.args)
      }
      await dialog.getByRole('button', { name: '关闭', exact: true }).click()
      evidence.push(result)
    }
    fs.writeFileSync(testInfo.outputPath('faker-ui-results.json'), JSON.stringify(evidence, null, 2))
  }
  expect(evidence).toHaveLength(262)
  expect(evidence.filter(row => row.args)).toHaveLength(126)
})
