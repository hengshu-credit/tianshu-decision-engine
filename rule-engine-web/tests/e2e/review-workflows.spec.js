const { test, expect } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createManagementApiData } = require('./support/managementFixtures.cjs')

const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
async function prepare(page, data) {
  return installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: data })
}

test('规则创建搜索第 1001 个项目，取消关闭保留输入，提交保留精确 ID 和编码', async ({ page }, testInfo) => {
  const data = createManagementApiData()
  let submitted
  const queries = []
  data.set('/api/rule/project/options', ({ url }) => {
    queries.push(url.searchParams.get('keyword'))
    return { records: url.searchParams.get('keyword') === '项目1001' ? [{ id: '1001', projectName: '项目1001', projectCode: 'Mixed_Project' }] : [], total: 1001 }
  })
  data.set('POST /api/rule/definition', ({ request }) => { submitted = request.postDataJSON(); return { id: '2001' } })
  await page.addInitScript(() => sessionStorage.setItem('projectList', JSON.stringify([{ id: 9, projectName: '旧账户项目' }])))
  const harness = await prepare(page, data)
  await page.goto(`${origin}/#/rule`)
  await page.getByRole('button', { name: '新建规则', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '新建规则', exact: true })
  await dialog.locator('.el-form-item').filter({ hasText: '作用范围' }).locator('.el-select').click()
  await page.getByRole('option', { name: /项目级/ }).click()
  const project = dialog.locator('.el-form-item').filter({ hasText: '项目名称' }).locator('.el-select__input')
  await project.fill('项目1001')
  await page.getByRole('option', { name: '项目1001 · Mixed_Project', exact: true }).click()
  await dialog.getByLabel('规则编码').fill('Keep_Mixed_Code')
  await dialog.getByLabel('规则名称').fill('分页项目规则')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('dialog', { name: '未保存提醒' }).getByRole('button', { name: '继续编辑' }).click()
  await expect(dialog.getByLabel('规则编码')).toHaveValue('Keep_Mixed_Code')
  await expect(dialog).not.toContainText('旧账户项目')
  await page.screenshot({ path: testInfo.outputPath('rule-project-selection.png') })
  await dialog.getByRole('button', { name: '仅创建', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(submitted).toMatchObject({ projectId: '1001', ruleCode: 'Keep_Mixed_Code', ruleName: '分页项目规则', scope: 'PROJECT' })
  expect(queries).toContain('项目1001')
  harness.assertClean()
})

test('文章正文在工作区刷新和关闭取消后保留，保存草稿后可继续编辑', async ({ page }, testInfo) => {
  const data = new Map([['/api/rule/ledger/events', []]])
  let saved
  data.set('POST /api/rule/ledger/articles', ({ request }) => { saved = request.postDataJSON().content; return { id: '71', version: 1, publishedVersion: 0 } })
  data.set('/api/rule/ledger/articles/71/edit', () => ({ id: '71', version: 1, publishedVersion: 0, content: saved }))
  const harness = await prepare(page, data)
  await page.goto(`${origin}/#/ledger/articles/new`)
  await page.getByRole('textbox', { name: '文章标题', exact: true }).fill('刷新保护验收')
  const editor = page.locator('.vditor-wysiwyg [contenteditable=true]')
  await editor.fill('需要保留的正文')
  await page.keyboard.press('Control+r')
  const reminder = page.getByRole('dialog', { name: '未保存提醒' })
  await expect(reminder).toBeVisible()
  await reminder.getByRole('button', { name: '继续编辑' }).click()
  await expect(editor).toContainText('需要保留的正文')
  await page.getByRole('button', { name: '返回文章管理' }).click()
  await reminder.getByRole('button', { name: '继续编辑' }).click()
  await page.screenshot({ path: testInfo.outputPath('ledger-unsaved.png') })
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect(page).toHaveURL(/articles\/71\/edit/)
  await expect(page.getByRole('textbox', { name: '文章标题', exact: true })).toHaveValue('刷新保护验收')
  expect(saved.body).toContain('需要保留的正文')
  harness.assertClean()
})

test('只读台账用户直接打开编辑链接时返回可访问页面，不发出编辑请求', async ({ page }) => {
  const data = new Map([
    ['/api/auth/console/config', { loginEnabled: true }],
    ['/api/auth/console/me', { userId: '91', username: 'viewer', permissionVersion: 1, permissions: ['ledger:view'] }],
    ['/api/auth/console/preferences/theme', null],
    ['POST /api/rule/console/activity', true],
  ])
  const harness = await prepare(page, data)
  await page.goto(`${origin}/#/ledger/articles/new`)
  await expect(page).toHaveURL(/#\/dashboard$/)
  await expect(page.getByRole('button', { name: 'viewer的账户菜单' })).toBeVisible()
  // 等待布局初始化请求到达，避免只检查 URL 时漏掉延迟发出的越权请求。
  await expect.poll(() => ['/api/auth/console/preferences/theme', '/api/rule/console/activity']
    .every(path => harness.requests.some(item => new URL(item.url).pathname === path))).toBe(true)
  expect(harness.requests.some(item => item.url.includes('/api/rule/ledger/'))).toBe(false)
  harness.assertClean()
})
