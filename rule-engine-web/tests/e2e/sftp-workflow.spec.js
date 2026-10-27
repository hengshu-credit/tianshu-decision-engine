const { test, expect } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')

async function expectCompactSftpLayout(page) {
  await expect.poll(async () => {
    const tabs = await page.locator('.sftp-tabs').boundingBox()
    const filter = await page.locator('.uiue-filter-toolbar').boundingBox()
    return filter.y - tabs.y
  }).toBeLessThan(100)
  const filter = await page.locator('.uiue-filter-toolbar').boundingBox()
  const table = await page.locator('.management-table').boundingBox()
  expect(table.y - filter.y - filter.height).toBeGreaterThanOrEqual(0)
  expect(table.y - filter.y - filter.height).toBeLessThanOrEqual(20)
}

test('SFTP 未保存配置取消关闭后完整保留，确认放弃后清空且不产生草稿', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const harness = await installDistRoutes(page, {
    origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL),
    apiData: new Map([['/api/rule/sftp/list', { records: [], total: 0 }]]),
  })
  await page.goto(`${origin}/#/sftp`)
  await page.getByRole('button', { name: '新建 SFTP 数据源', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '新建 SFTP 数据源', exact: true })
  await dialog.getByRole('textbox', { name: /数据源名称/ }).fill('保留我的配置')
  await dialog.getByRole('textbox', { name: '密码', exact: true }).fill('test-only-unsaved')
  await dialog.getByRole('button', { name: '关闭此对话框' }).click()
  const reminder = page.getByRole('dialog', { name: '未保存提醒', exact: true })
  await expect(reminder).toBeVisible()
  await reminder.getByRole('button', { name: '继续编辑', exact: true }).click()
  await expect(dialog.getByRole('textbox', { name: /数据源名称/ })).toHaveValue('保留我的配置')
  await expect(dialog.getByRole('textbox', { name: '密码', exact: true })).toHaveValue('test-only-unsaved')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await reminder.getByRole('button', { name: '继续离开', exact: true }).click()
  await expect(dialog).toBeHidden()
  await page.getByRole('button', { name: '新建 SFTP 数据源', exact: true }).click()
  await expect(dialog.getByRole('textbox', { name: /数据源名称/ })).toHaveValue('')
  await expect(dialog.getByRole('textbox', { name: '密码', exact: true })).toHaveValue('')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).toBeHidden()
  expect(harness.requests.filter(item => item.method === 'POST' && item.url.includes('/governance/drafts'))).toEqual([])
  harness.assertClean()
})

test('SFTP 配置与日志字段设置位于操作区首位，可隐藏字段并保持操作可用', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const harness = await installDistRoutes(page, {
    origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL),
    apiData: new Map([['/api/rule/sftp/list', { records: [], total: 0 }], ['/api/rule/runtime-log/list', { records: [], total: 0 }]]),
  })
  await page.goto(`${origin}/#/sftp`)
  for (const field of ['认证用户', '文件链接地址']) {
    const actions = page.locator('.uiue-filter-toolbar .btn-right')
    await expect(actions.getByRole('button').first()).toHaveAccessibleName('设置表格显示字段')
    await actions.getByRole('button', { name: '设置表格显示字段' }).click()
    await page.getByRole('button', { name: `隐藏${field}`, exact: true }).click()
    if (field === '认证用户') {
      await page.locator('.table-column-settings__item').filter({ hasText: 'SFTP 地址' }).getByRole('button', { name: '上移字段', exact: true }).click()
    }
    await page.locator('.table-column-settings__panel').getByRole('button', { name: '应用', exact: true }).click()
    await expect(page.getByRole('columnheader', { name: field, exact: true })).toBeHidden()
    if (field === '认证用户') {
      const headers = await page.getByRole('columnheader').allTextContents()
      expect(headers.indexOf('SFTP 地址')).toBeLessThan(headers.indexOf('数据源名称'))
    }
    await page.reload()
    await expect(page.getByRole('button', { name: '设置表格显示字段' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: field, exact: true })).toBeHidden()
    await expect(actions.getByRole('button', { name: '刷新', exact: true })).toBeVisible()
    if (field === '认证用户') await page.getByRole('tab', { name: '查询日志', exact: true }).click()
  }
  harness.assertClean()
})

test('SFTP 往返切换、工作区页签和浏览器历史保持选中项与页面内容一致', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const harness = await installDistRoutes(page, {
    origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL),
    apiData: new Map([['/api/rule/sftp/list', { records: [], total: 0 }]]),
  })
  const expectPage = async (path, title, selected) => {
    await expect(page).toHaveURL(`${origin}/#${path}`)
    await expect(page.locator('.module-hint .hint-title')).toHaveText(title)
    await expect(page.getByRole('tab', { name: selected, exact: true })).toHaveAttribute('aria-selected', 'true')
  }
  await page.goto(`${origin}/#/sftp`)
  await expectPage('/sftp', 'SFTP 数据源配置', '数据源配置')
  await page.getByPlaceholder('编码', { exact: true }).fill('keep-datasource-filter')
  await page.getByRole('tab', { name: '查询日志', exact: true }).click()
  await expectPage('/sftp/logs', 'SFTP 查询日志', '查询日志')
  await page.getByPlaceholder('规则或模块 trace_id').fill('keep-log-filter')
  await page.getByRole('tab', { name: '数据源配置', exact: true }).click()
  await expectPage('/sftp', 'SFTP 数据源配置', '数据源配置')
  await expect(page.getByPlaceholder('编码', { exact: true })).toHaveValue('keep-datasource-filter')
  const workspace = page.getByRole('navigation', { name: '工作区页签' })
  await workspace.getByRole('button', { name: 'SFTP查询日志', exact: true }).click()
  await expectPage('/sftp/logs', 'SFTP 查询日志', '查询日志')
  await expect(page.getByPlaceholder('规则或模块 trace_id')).toHaveValue('keep-log-filter')
  await workspace.getByRole('button', { name: 'SFTP数据源配置', exact: true }).click()
  await expectPage('/sftp', 'SFTP 数据源配置', '数据源配置')
  await page.goBack()
  await expectPage('/sftp/logs', 'SFTP 查询日志', '查询日志')
  await page.goForward()
  await expectPage('/sftp', 'SFTP 数据源配置', '数据源配置')
  await page.reload()
  await expectPage('/sftp', 'SFTP 数据源配置', '数据源配置')
  harness.assertClean()
})

test('SFTP 标签切换保留显式项目范围，键盘切换也能跳转', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const harness = await installDistRoutes(page, {
    origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL),
    apiData: new Map([['/api/rule/sftp/list', { records: [], total: 0 }]]),
  })
  await page.goto(`${origin}/#/sftp?projectId=1`)
  await page.getByRole('tab', { name: '查询日志', exact: true }).click()
  await expect(page).toHaveURL(`${origin}/#/sftp/logs?projectId=1`)
  await expect(page.getByRole('tab', { name: '查询日志', exact: true })).toHaveAttribute('aria-selected', 'true')
  await page.getByRole('tab', { name: '查询日志', exact: true }).focus()
  await page.keyboard.press('ArrowLeft')
  await expect(page).toHaveURL(`${origin}/#/sftp?projectId=1`)
  await expect(page.getByRole('tab', { name: '数据源配置', exact: true })).toHaveAttribute('aria-selected', 'true')
  await page.getByRole('tab', { name: '数据源配置', exact: true }).focus()
  await page.keyboard.press('ArrowRight')
  await expect(page).toHaveURL(`${origin}/#/sftp/logs?projectId=1`)
  harness.assertClean()
})

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
  await expectCompactSftpLayout(page)
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
  await expectCompactSftpLayout(page)
  await page.locator('.el-pagination .btn-next').click()
  await expect.poll(() => logQueries.at(-1)?.pageNum).toBe('2')
  await page.getByPlaceholder('规则或模块 trace_id').fill('audit-trace')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => logQueries.at(-1)).toMatchObject({ pageNum: '1', traceId: 'audit-trace', moduleType: 'SFTP' })
  expect(warnings).toEqual([])
  harness.assertClean()
})
