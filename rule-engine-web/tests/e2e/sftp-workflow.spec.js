const { test, expect } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')

test('规则与实验共用机构凭据，支持同类多账号、SFTP 和脱敏编辑', async ({ page }) => {
  test.setTimeout(90000)
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const base = '/api/rule/institution-auth'
  const permissions = ['rule:view', 'experiment:view', 'institution-auth:view', 'institution-auth:manage']
  const configurations = [
    { institution: '甲机构', authType: 'BASIC', identifier: 'partner_1' },
    { institution: '甲机构', authType: 'BASIC', identifier: 'partner_2', projectId: 1, existing: true },
    { institution: '乙机构', authType: 'HMAC_SHA256', identifier: 'partner_AK', projectId: 1 },
    { institution: '乙机构', authType: 'API_KEY', identifier: '', existing: true },
  ]
  const auths = []
  let reveals = 0
  const apiData = new Map([
    ['/api/auth/console/config', { loginEnabled: true }],
    ['/api/auth/console/me', { userId: 1, username: 'operator', permissions, defaultPermissions: permissions, globalEditable: true }],
    ['/api/auth/console/preferences/theme', {}],
    ['POST /api/rule/console/activity', null],
    ['/api/rule/experiment/list', { records: [], total: 0 }],
    [base, () => auths],
    ['POST ' + base, ({ request }) => {
      const { identifier, secret, ...data } = request.postDataJSON()
      const expected = configurations[auths.length]
      expect(identifier).toBe(expected.identifier)
      expect(secret).toBe('test-only-password')
      expect(data.authType).toBe(expected.authType)
      expect(data.managementProjectId ?? null).toBe(expected.projectId ?? null)
      expect(data).not.toHaveProperty('projectId')
      expect(data).not.toHaveProperty('sftpPublicKey')
      expect(data.sftpEnabled).toBe(true)
      const existing = auths.find(row => row.institutionId === data.institutionId)
      if (expected.existing) expect(existing?.institutionName).toBe(expected.institution)
      const row = { ...data, id: auths.length + 1, institutionId: existing?.institutionId || auths.length + 10,
        institutionName: existing?.institutionName || data.institutionName, identifierMasked: 'p****', secretMasked: '****' }
      auths.push(row)
      return row
    }],
    ['PUT ' + base + '/2', ({ request }) => {
      expect(request.postDataJSON()).toMatchObject({ id: 2, institutionId: 10, identifier: '', secret: '', authName: '备用账号' })
      expect(request.postDataJSON()).not.toHaveProperty('managementProjectId')
      auths[1].authName = request.postDataJSON().authName
      return auths[1]
    }],
    ['PUT ' + base + '/2/status', ({ url }) => {
      expect(url.searchParams.get('status')).toBe('0')
      auths[1].status = 0
      return auths[1]
    }],
    [base + '/2/full', () => {
      reveals++
      return { ...auths[1], sftpLogin: 'partner_2', identifier: 'partner_2', secret: 'test-only-password' }
    }],
  ])
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData })
  await page.goto(origin + '/#/rule')
  await page.getByRole('button', { name: '机构鉴权', exact: true }).click()
  const list = page.getByRole('dialog', { name: '机构调用鉴权', exact: true })
  for (const [index, { institution, authType, identifier, existing, projectId }] of configurations.entries()) {
    await list.getByRole('button', { name: '新增鉴权', exact: true }).click()
    const form = page.getByRole('dialog', { name: '新增鉴权', exact: true })
    if (existing) {
      await form.getByRole('combobox', { name: '所属机构' }).click()
      await page.getByRole('option', { name: institution, exact: true }).click()
    } else {
      await form.getByRole('textbox', { name: /新机构名称/ }).fill(institution)
    }
    if (projectId) {
      await form.getByRole('combobox', { name: '管理归属项目' }).click()
      await page.getByRole('option', { name: /E2E 项目/ }).click()
    }
    await form.getByRole('textbox', { name: /鉴权编码/ }).fill('PARTNER_' + (index + 1))
    await form.getByRole('textbox', { name: /显示名称/ }).fill('账号 ' + (index + 1))
    if (authType !== 'BASIC') {
      await form.locator('.el-form-item').filter({ hasText: '鉴权方式' }).locator('.el-select').click()
      await page.getByRole('option', { name: authType === 'API_KEY' ? 'API Key' : 'HMAC-SHA256', exact: true }).click()
    }
    if (identifier) await form.getByLabel(authType === 'BASIC' ? /账号$/ : 'Access Key', { exact: true }).fill(identifier)
    const secretLabel = { BASIC: /密码$/, HMAC_SHA256: 'Secret', API_KEY: 'API Key' }[authType]
    await form.getByLabel(secretLabel, { exact: true }).fill('test-only-password')
    await form.locator('.el-form-item').filter({ hasText: '开通 SFTP' }).locator('.el-switch').click()
    await expect(form.getByText('SSH 公钥', { exact: true })).toHaveCount(0)
    await form.getByRole('button', { name: '保存', exact: true }).click()
    await expect(form).toBeHidden()
  }
  await list.getByRole('button', { name: '关闭', exact: true }).click()
  await page.goto(origin + '/#/experiment')
  await page.getByRole('button', { name: '机构鉴权', exact: true }).click()
  await expect(list.getByRole('row').filter({ hasText: '甲机构' })).toHaveCount(2)
  await expect(list.getByRole('row').filter({ hasText: '乙机构' })).toHaveCount(2)
  const row = list.getByRole('row').filter({ hasText: 'PARTNER_2' })
  await page.setViewportSize({ width: 550, height: 830 })
  expect(await list.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  await row.getByRole('button', { name: '编辑', exact: true }).click()
  const edit = page.getByRole('dialog', { name: '编辑鉴权', exact: true })
  expect(await edit.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  await expect(edit.getByRole('combobox', { name: '管理归属项目' })).toBeDisabled()
  await expect(edit.getByLabel(/密码$/)).toHaveValue('')
  await edit.getByRole('textbox', { name: /显示名称/ }).fill('备用账号')
  await edit.getByRole('button', { name: '保存', exact: true }).click()
  await expect(row).toContainText('备用账号')
  expect(reveals).toBe(0)
  await row.getByRole('button', { name: '完整值', exact: true }).click()
  const full = page.getByRole('dialog', { name: '完整鉴权值', exact: true })
  await expect(full.getByText('SFTP 登录名', { exact: true })).toBeVisible()
  await expect(full.getByText('test-only-password', { exact: true })).toBeVisible()
  await full.getByRole('button', { name: '关闭', exact: true }).click()
  await expect(page.getByText('test-only-password', { exact: true })).toHaveCount(0)
  await row.getByRole('button', { name: '停用', exact: true }).click()
  await expect(row.getByRole('button', { name: '启用', exact: true })).toBeVisible()
  harness.assertClean()
})

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
    await expect(page.locator('.dismissible-guidance .guidance-heading')).toHaveText(title)
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

test('引擎 SFTP 服务配置只修改选中的账户、策略版本与封禁记录', async ({ page }) => {
  const permissions = ['database:view', 'account:manage', 'account:password-reset', 'system:edit']
  let account = { id: 24, username: 'supplier', displayName: '上传方', active: true, mode: 'READ_WRITE', revision: 7 }
  let policy = { ipAttemptsPerMinute: 120, fileRequestsPerSecond: 256, failureThreshold: 8, failureWindowSeconds: 300, banSeconds: 900, revision: 2 }
  const ban = { subject: 'USER:24', reason: 'FAILURE', failures: 8, blockedUntil: Date.now() + 600000 }
  let blocks = [ban]
  const apiData = new Map([
    ['/api/auth/console/config', { loginEnabled: true }],
    ['/api/auth/console/me', { userId: 1, username: 'operator', permissions, defaultPermissions: permissions, globalEditable: true }],
    ['/api/rule/sftp/list', { records: [], total: 0 }],
    ['/api/rule/sftp/engine/accounts', () => ({ records: [account], total: 1 })],
    ['PUT /api/rule/sftp/engine/accounts/24', ({ request }) => {
      expect(request.postDataJSON()).toEqual({ mode: 'READ_ONLY', publicKey: '', revoke: false, revision: 7 })
      account = { ...account, mode: 'READ_ONLY', revision: 8 }
      return null
    }],
    ['/api/rule/sftp/engine/policy', () => policy],
    ['PUT /api/rule/sftp/engine/policy', ({ request }) => {
      expect(request.postDataJSON()).toEqual({ ...policy, failureThreshold: 4 })
      policy = { ...request.postDataJSON(), revision: 3 }
      return policy
    }],
    ['/api/rule/sftp/engine/bans', () => ({ records: blocks, total: blocks.length })],
    ['POST /api/rule/sftp/engine/unblock', ({ request }) => {
      expect(request.postDataJSON()).toEqual({ subject: ban.subject, blockedUntil: ban.blockedUntil })
      blocks = []
      return null
    }],
  ])
  await installDistRoutes(page, { apiData })
  await page.setViewportSize({ width: 560, height: 850 })
  await page.goto('http://tianshu.local/index.html#/sftp')
  await page.getByRole('button', { name: '引擎 SFTP 服务配置' }).click()
  const settings = page.getByRole('dialog', { name: '引擎 SFTP 服务配置' })
  const row = settings.getByRole('row').filter({ hasText: 'supplier' })
  await row.getByRole('button', { name: '管理' }).click()
  const edit = page.getByRole('dialog', { name: '管理 SFTP 上传账户' })
  await edit.locator('.el-form-item').filter({ hasText: '账户权限' }).locator('.el-select').click()
  await page.getByRole('option', { name: '只读', exact: true }).click()
  await edit.getByRole('button', { name: '保存', exact: true }).click()
  await expect(row).toContainText('只读')
  await settings.getByRole('tab', { name: '限流与熔断' }).click()
  const limit = settings.getByRole('spinbutton', { name: '封禁前允许尝试的失败次数' })
  await limit.fill('4')
  await limit.press('Tab')
  await settings.getByRole('button', { name: '保存策略' }).click()
  await expect(settings.getByRole('button', { name: '保存策略' })).toBeDisabled()
  await settings.getByRole('tab', { name: '封禁记录' }).click()
  await settings.getByRole('button', { name: '解封', exact: true }).click()
  await page.locator('.el-message-box:visible').getByRole('button', { name: '确定' }).click()
  await expect(settings.getByText('当前没有生效的封禁')).toBeVisible()
  expect(await settings.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
})
