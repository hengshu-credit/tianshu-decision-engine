const { expect } = require('@playwright/test')
const base = process.env.E2E_BASE_URL
async function login(page, route) {
  await page.goto(`${base}/#${route}`)
  await page.locator('input[autocomplete="username"]').fill(process.env.E2E_USERNAME)
  await page.locator('input[autocomplete="current-password"]').fill(process.env.E2E_PASSWORD)
  await page.locator('button[type="submit"]').click()
  await expect(page.locator('.layout-sidebar')).toBeVisible()
}
async function approve(page, name) {
  await page.getByRole('navigation', { name: '主导航' }).getByRole('button', { name: '审批管理', exact: true }).click()
  await page.getByRole('row').filter({ hasText: name }).first().getByRole('button', { name: '查看', exact: true }).click()
  await expect(page).toHaveURL(/#\/approval\/\d+/)
  await page.getByRole('button', { name: '通过并生效', exact: true }).click()
  await page.getByRole('dialog', { name: '通过并立即生效', exact: true }).getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('.header-meta')).toContainText('已通过', { timeout: 30000 })
}
async function submitRow(page, name) {
  await page.getByRole('row').filter({ hasText: name }).getByRole('button', { name: '发布', exact: true }).click()
  const submitted = page.waitForResponse(item => /\/governance\/requests\/\d+\/submit$/.test(item.url()) && item.request().method() === 'POST')
  await page.getByRole('dialog', { name: '提交发布审批', exact: true }).getByRole('button', { name: '确定', exact: true }).click()
  const submission = await submitted
  const result = await submission.json()
  if (result.code !== '000000') {
    await page.goto(`${base}/#/approval/${submission.url().match(/requests\/(\d+)/)[1]}`)
    const checked = page.waitForResponse(item => item.url().endsWith('/preflight') && item.request().method() === 'POST')
    await page.getByRole('button', { name: '依赖预检', exact: true }).click()
    result.data = (await (await checked).json()).data
  }
  expect(result.code, JSON.stringify({ message: result.message, data: result.data })).toBe('000000')
  await expect(page.getByText('已提交发布审批，生产内容在审批通过前保持原状', { exact: true })).toBeVisible()
}
async function createRule(page, projectId, model) {
  await page.goto(`${base}/#/project/${projectId}`)
  await page.getByRole('tab', { name: '项目规则', exact: true }).click()
  await page.getByRole('button', { name: '新建规则', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '新建规则', exact: true })
  await dialog.getByRole('textbox', { name: /规则编码$/ }).fill(`Journey_${Date.now()}`)
  await dialog.getByRole('textbox', { name: /规则名称$/ }).fill(`${model}业务旅程`)
  await dialog.locator('.el-form-item').filter({ hasText: '模型类型' }).locator('.el-select').click()
  await page.getByRole('option', { name: model, exact: true }).click()
  await dialog.getByRole('button', { name: '创建并继续配置', exact: true }).click()
  await expect(page).toHaveURL(/#\/designer\//)
  await expect(page.locator('.rule-designer-actions [data-action="save"]')).toBeEnabled()
}
async function field(page, trigger, code) {
  await trigger.click()
  const panel = page.locator('.fp-panel:visible')
  await panel.getByRole('textbox', { name: '搜索内容名称或编码' }).fill(code)
  await panel.locator('.fp-row').filter({ hasText: code }).dblclick()
  await expect(panel).toHaveCount(0)
}
async function literal(page, trigger, value) {
  await trigger.click()
  const panel = page.locator('.fp-panel:visible')
  await panel.getByRole('button', { name: '输入阈值', exact: true }).click()
  if (typeof value === 'number') {
    await panel.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '阈值数据类型' }) }).click()
    await page.getByRole('option', { name: '数值 (NUMBER)', exact: true }).click()
  }
  const content = panel.getByRole('textbox', { name: /^阈值内容/ })
  await content.click(); await page.keyboard.press('Control+A'); await page.keyboard.insertText(String(value))
  await panel.getByRole('button', { name: '应用阈值', exact: true }).click()
  await expect(panel).toHaveCount(0)
}
async function save(page, overwrite = false) {
  const response = page.waitForResponse(item => item.url().includes('/designer/drafts') && item.request().method() === 'POST')
  await page.locator('.rule-designer-actions [data-action="save"]').click()
  if (overwrite) await page.getByRole('dialog', { name: '保存草稿', exact: true }).getByRole('button', { name: '保存草稿', exact: true }).click()
  const body = await (await response).json()
  expect(body.code, body.message).toBe('000000')
  expect(body.data.compileSuccess, JSON.stringify(body.data.issues)).toBe(true)
  await expect(page).toHaveURL(/sourceId=/)
  return body.data
}
async function execute(page) {
  await page.locator('.rule-designer-actions [data-action="test"]').click()
  const dialog = page.getByRole('dialog', { name: '测试执行', exact: true })
  const response = page.waitForResponse(item => item.url().endsWith('/definition/execute') && item.request().method() === 'POST')
  await dialog.getByRole('button', { name: '执行测试', exact: true }).click()
  const body = await (await response).json()
  expect(body.code, body.message).toBe('000000')
  expect(body.data.success, body.data.errorMessage).toBe(true)
  await expect(dialog.getByText('执行成功', { exact: true })).toBeVisible()
  await dialog.getByRole('button', { name: '关闭', exact: true }).click()
  return body.data
}
module.exports = { login, approve, submitRow, createRule, field, literal, save, execute }
