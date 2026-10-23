const { test, expect } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')

for (const width of [1440, 560]) test(`SFTP ${width}px 查询、连接测试与保存草稿`, async ({ page }, testInfo) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const queries = []
  const logQueries = []
  const warnings = []
  page.on('console', message => { if (message.type() === 'warning' && message.text().includes('Failed to resolve component')) warnings.push(message.text()) })
  let saved, tested
  const row = { id: 51, scope: 'GLOBAL', datasourceCode: 'Existing_Sftp', datasourceName: '已有文件源', host: 'sftp.example.com', port: 22, minIdle: 0, maxPoolSize: 8, status: 1 }
  const apiData = new Map([
    ['/api/rule/sftp/list', ({ url }) => { queries.push(Object.fromEntries(url.searchParams)); return { records: [row], total: 40 } }],
    ['POST /api/rule/sftp/test', ({ request }) => { tested = request.postDataJSON(); return 'SFTP连接成功' }],
    ['POST /api/rule/governance/drafts', ({ request }) => { saved = request.postDataJSON(); return { id: 81, status: 'DRAFT' } }],
    ['/api/rule/runtime-log/list', ({ url }) => { logQueries.push(Object.fromEntries(url.searchParams)); return { records: [], total: 40 } }],
  ])
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData })
  await page.setViewportSize({ width, height: 1000 })
  await page.goto(`${origin}/#/sftp`)
  await expect(page.getByRole('row').filter({ hasText: 'Existing_Sftp' })).toContainText('0 / 8')
  await page.locator('.el-pagination .btn-next').click()
  await expect.poll(() => queries.at(-1)?.pageNum).toBe('2')
  await page.getByPlaceholder('编码', { exact: true }).fill('Existing_Sftp')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => queries.at(-1)).toMatchObject({ pageNum: '1', datasourceCode: 'Existing_Sftp' })
  await page.getByRole('button', { name: '新建 SFTP 数据源', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '新建 SFTP 数据源', exact: true })
  await dialog.locator('.scope-project-fields .el-select').first().click()
  await page.getByRole('option', { name: '🌐 全局（所有项目可用）', exact: true }).click()
  for (const [label, value] of [['数据源编码', 'Audit_Sftp_MixedCase'], ['数据源名称', '审查文件源'], ['主机', 'sftp.example.com'], ['用户名', 'audit'], ['密码', 'fixture-only']]) {
    await dialog.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: new RegExp(`^${label}$`) }) }).locator('input').fill(value)
  }
  await dialog.getByRole('button', { name: '测试连接', exact: true }).click()
  await expect(page.getByText('SFTP 连接成功', { exact: true })).toBeVisible()
  expect(tested.datasourceCode).toBe('Audit_Sftp_MixedCase')
  await page.screenshot({ path: testInfo.outputPath(`sftp-form-${width}.png`), fullPage: true })
  for (const input of await dialog.getByRole('spinbutton').all()) expect((await input.boundingBox()).width).toBeGreaterThan(60)
  await dialog.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByText('草稿已保存，可继续编辑；点击行内“发布”或“草稿与审批”提交审批')).toBeVisible()
  expect(saved).toMatchObject({ resourceType: 'SFTP', action: 'CREATE', projectId: 0 })
  expect(JSON.parse(saved.snapshotJson)).toMatchObject({ datasourceCode: 'Audit_Sftp_MixedCase', datasourceName: '审查文件源' })
  expect(harness.requests.filter(item => /\/submit$/.test(new URL(item.url).pathname))).toEqual([])
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.getByRole('tab', { name: '查询日志', exact: true }).click()
  await expect(page).toHaveURL(/#\/sftp\/logs$/)
  await expect(page.getByRole('textbox', { name: '项目编码', exact: true })).toBeVisible()
  await page.locator('.el-pagination .btn-next').click()
  await expect.poll(() => logQueries.at(-1)?.pageNum).toBe('2')
  await page.getByPlaceholder('规则或模块 trace_id').fill('audit-trace')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => logQueries.at(-1)).toMatchObject({ pageNum: '1', traceId: 'audit-trace', moduleType: 'SFTP' })
  expect(warnings).toEqual([])
  harness.assertClean()
})
