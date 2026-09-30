const { expect, test } = require('@playwright/test')

test.afterEach(async ({ page }) => {
  for (const input of await page.locator('input[type="password"]').all()) await input.fill('')
})

test('真实函数复用：相同配置复用，变更后拒绝复用并可选择后缀新建', async ({ page }, testInfo) => {
  const base = process.env.E2E_TRANSFER_BASE_URL
  test.skip(!base, '设置 E2E_TRANSFER_BASE_URL 后运行；会创建独立项目和函数副本')
  test.setTimeout(180000)
  page.setDefaultTimeout(15000)
  const sourceCode = process.env.E2E_TRANSFER_SOURCE_FUNCTION_CODE || 'roundTax'
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.goto(`${base}/#/function`)
  await expect(page.locator('.layout-sidebar, input[autocomplete="username"]').first()).toBeVisible()
  if (page.url().includes('#/login')) {
    await page.locator('input[autocomplete="username"]').fill(process.env.E2E_USERNAME)
    await page.locator('input[autocomplete="current-password"]').fill(process.env.E2E_PASSWORD)
    const response = page.waitForResponse('**/api/auth/console/login')
    await page.locator('button[type="submit"]').click()
    expect((await (await response).json()).code).toBe(200)
  }
  await expect(page.locator('.layout-sidebar')).toBeVisible()
  await page.getByRole('textbox', { name: '函数编码', exact: true }).fill(sourceCode)
  const listResponse = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.pathname === '/api/rule/function/list' && url.searchParams.get('funcCode') === sourceCode
  })
  await page.getByRole('button', { name: '查询', exact: true }).click()
  // ID 来自当前页面查询返回的记录；没有通过接口创建或修改测试数据。
  const rows = (await (await listResponse).json()).data.records
  const source = rows.find(row => row.funcCode === sourceCode && row.status === 1)
  expect(source).toBeTruthy()
  await expect(page.getByRole('cell', { name: sourceCode, exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: '离线迁移', exact: true }).first().click()
  await page.locator('.root-type').click()
  await page.getByRole('option', { name: '函数', exact: true }).click()
  await page.getByRole('combobox', { name: '第 1 项导出资源', exact: true }).fill(sourceCode)
  const sourceScope = source.scope === 'GLOBAL' ? '全局' : source.projectName || `项目 ${source.projectId}`
  await page.getByRole('option').filter({ hasText: `${source.funcName || source.funcCode} (${source.funcCode}) · ${sourceScope}` }).first().click()
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: '生成并下载配置包' }).click()
  const filename = testInfo.outputPath('real-function-transfer.zip')
  await (await download).saveAs(filename)

  const choosePolicy = async label => {
    await page.getByRole('combobox', { name: '资源冲突策略', exact: true }).press('ArrowDown')
    await page.getByRole('option', { name: label, exact: true }).click()
  }
  const preview = async () => {
    const response = page.waitForResponse('**/api/rule/transfer/preview/options')
    await page.getByRole('button', { name: '预览冲突', exact: true }).click()
    const result = await (await response).json()
    expect(result.code, result.message).toBe(200)
    return result.data
  }
  const apply = async success => {
    await page.getByRole('button', { name: '确认导入', exact: true }).click()
    const response = page.waitForResponse('**/api/rule/transfer/import')
    const dialog = page.getByRole('dialog', { name: '确认导入' })
    await dialog.getByRole('button', { name: '确定', exact: true }).click()
    const result = await (await response).json()
    expect(result.code, result.message).toBe(success ? 200 : 422)
    if (success) await page.getByRole('dialog', { name: '导入结果' }).getByRole('button', { name: '关闭此对话框' }).click()
    else await expect(page.getByRole('dialog', { name: '导入结果' })).toBeHidden()
    return result
  }
  const newCode = `reuse_verify_${Date.now()}`
  await page.locator('input[type="file"]').setInputFiles(filename)
  await page.getByText('导入时新建项目', { exact: true }).click()
  await page.getByPlaceholder('新项目编码（可覆盖源编码）').fill(newCode)
  await page.getByPlaceholder('新项目名称（可覆盖源名称）').fill('复用策略独立验收项目')
  await choosePolicy('复用相同配置')
  await preview()
  const created = (await apply(true)).data
  const copiedFunctionId = created.resourceIdMapping[`FUNCTION:${source.id}`]
  expect(copiedFunctionId).not.toBe(source.id)

  await page.getByText('导入时新建项目', { exact: true }).click()
  await page.getByRole('combobox', { name: '目标项目', exact: true }).fill(newCode)
  await page.getByRole('option', { name: new RegExp(` / ${newCode}$`) }).click()
  const samePreview = await preview()
  expect(samePreview.conflicts.find(item => item.resourceType === 'FUNCTION').conflictType).toBe('IDENTICAL')
  const reused = (await apply(true)).data
  expect(reused.resources.find(item => item.sourceKey === `FUNCTION:${source.id}`).status).toBe('REUSED')
  expect(reused.resourceIdMapping[`FUNCTION:${source.id}`]).toBe(copiedFunctionId)

  await page.goto(`${base}/#/function`)
  await page.getByRole('textbox', { name: '项目编码', exact: true }).fill(newCode)
  await page.getByRole('textbox', { name: '函数编码', exact: true }).fill(sourceCode)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  const functionRow = page.getByRole('row').filter({ hasText: source.projectName || newCode }).filter({ has: page.getByRole('cell', { name: sourceCode, exact: true }) }).first()
  await expect(functionRow).toBeVisible()
  await functionRow.getByRole('button', { name: '编辑', exact: true }).click()
  await page.getByPlaceholder('函数功能说明').fill(`配置差异验收 ${newCode}`)
  await page.getByRole('dialog').getByRole('button', { name: '保存', exact: true }).click()
  await expect(page).toHaveURL(/#\/approval\/\d+/)
  for (const label of ['提交审批', '通过并生效']) {
    await page.getByRole('button', { name: label, exact: true }).click()
    await page.getByRole('textbox', { name: '操作说明', exact: true }).fill('只调整独立验收函数副本的说明')
    await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
    await expect(page.getByRole('dialog')).toBeHidden()
  }
  await expect(page.getByRole('button', { name: '通过并生效', exact: true })).toBeHidden()
  await page.goto(`${base}/#/transfer`)
  await page.locator('input[type="file"]').setInputFiles(filename)
  await page.getByRole('combobox', { name: '目标项目', exact: true }).fill(newCode)
  await page.getByRole('option', { name: new RegExp(` / ${newCode}$`) }).click()
  await choosePolicy('复用相同配置')
  const changed = await preview()
  expect(changed.conflicts.find(item => item.resourceType === 'FUNCTION').conflictType).toBe('CONFIG_CONFLICT')
  expect((await apply(false)).message).toContain('不能复用')
  await choosePolicy('新建并追加后缀')
  await page.getByPlaceholder('新建后缀，如 _dev').fill('_reuse_check')
  await preview()
  const suffixed = (await apply(true)).data
  expect(suffixed.resourceIdMapping[`FUNCTION:${source.id}`]).not.toBe(copiedFunctionId)
  expect(suffixed.resources.find(item => item.sourceKey === `FUNCTION:${source.id}`).status).toBe('CREATED')
  expect(pageErrors).toEqual([])
  console.log(JSON.stringify({ targetProjectId: created.targetProjectId, projectCode: newCode, copiedFunctionId,
    suffixedFunctionId: suffixed.resourceIdMapping[`FUNCTION:${source.id}`] }))
})

// 显式指定独立验证环境后运行；所有创建与导入操作均通过页面完成。
test('真实离线迁移：全局、已有项目覆盖策略、新建项目均保持明确归属', async ({ page }, testInfo) => {
  const base = process.env.E2E_TRANSFER_BASE_URL
  test.skip(!base, '设置 E2E_TRANSFER_BASE_URL 后执行真实服务验收；会创建一个独立验证项目')
  test.setTimeout(120000)
  page.setDefaultTimeout(15000)
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto(`${base}/#/project`)
  await expect(page.locator('.layout-sidebar, input[autocomplete="username"]').first()).toBeVisible()
  if (page.url().includes('#/login')) {
    if (!process.env.E2E_USERNAME || !process.env.E2E_PASSWORD) throw new Error('需要提供本地验证账号')
    await page.locator('input[autocomplete="username"]').fill(process.env.E2E_USERNAME)
    await page.locator('input[autocomplete="current-password"]').fill(process.env.E2E_PASSWORD)
    const loginResponse = page.waitForResponse('**/api/auth/console/login')
    await page.locator('button[type="submit"]').click()
    const login = await (await loginResponse).json()
    if (await page.locator('input[autocomplete="current-password"]').count()) {
      await page.locator('input[autocomplete="current-password"]').fill('').catch(() => {})
    }
    expect(login.code, login.message).toBe(200)
  }
  await expect(page.locator('.layout-sidebar')).toBeVisible()
  const row = page.locator('.el-table__body-wrapper tbody tr').first()
  await expect(row).toBeVisible()
  const projectCode = (await row.locator('td').nth(0).innerText()).trim()
  await row.getByRole('button', { name: '进入', exact: true }).click()
  await expect(page).toHaveURL(/#\/project\/\d+/)
  const sourceId = Number(/#\/project\/(\d+)/.exec(page.url())[1])
  await page.getByRole('button', { name: '离线迁移', exact: true }).first().click()
  await expect(page.getByRole('heading', { name: '离线配置迁移' })).toBeVisible()
  await page.locator('.root-type').click()
  await page.getByRole('option', { name: '项目', exact: true }).click()
  await page.getByRole('combobox', { name: '第 1 项导出资源', exact: true }).fill(projectCode)
  await page.getByRole('option', { name: new RegExp(`\\(${projectCode}\\)$`) }).click()
  const downloaded = page.waitForEvent('download')
  await page.getByRole('button', { name: '生成并下载配置包' }).click()
  const filename = testInfo.outputPath('real-project-transfer.zip')
  await (await downloaded).saveAs(filename)
  await page.locator('input[type="file"]').setInputFiles(filename)

  const choose = async (label, value) => {
    await page.getByRole('combobox', { name: label, exact: true }).press('ArrowDown')
    await page.getByRole('option', { name: value, exact: true }).click()
  }
  const importFromUi = async () => {
    const previewResponse = page.waitForResponse('**/api/rule/transfer/preview/options')
    await page.getByRole('button', { name: '预览冲突', exact: true }).click()
    const preview = await (await previewResponse).json()
    expect(preview.code, preview.message).toBe(200)
    expect(preview.data.conflictCount).toBe(0)
    await page.getByRole('button', { name: '确认导入', exact: true }).click()
    const appliedResponse = page.waitForResponse('**/api/rule/transfer/import')
    await page.getByRole('dialog', { name: '确认导入' }).getByRole('button', { name: '确定', exact: true }).click()
    const applied = await (await appliedResponse).json()
    expect(applied.code, applied.message).toBe(200)
    await expect(page.getByRole('dialog', { name: '导入结果' })).toBeVisible()
    await page.getByRole('dialog', { name: '导入结果' }).getByRole('button', { name: '关闭此对话框' }).click()
    return applied.data
  }

  await choose('目标范围', '全局')
  const global = await importFromUi()
  expect(global.projectCreated).toBe(false)
  expect(global.resourceIdMapping[`PROJECT:${sourceId}`]).toBe(0)
  expect(global.resources[0].status).toBe('SKIPPED')

  await choose('目标范围', '项目级')
  await page.getByRole('combobox', { name: '目标项目', exact: true }).fill(projectCode)
  await page.getByRole('option', { name: new RegExp(` / ${projectCode}$`) }).click()
  await choose('资源冲突策略', '覆盖已有配置')
  const bound = await importFromUi()
  expect(bound.projectCreated).toBe(false)
  expect(bound.resourceIdMapping[`PROJECT:${sourceId}`]).toBe(sourceId)
  expect(bound.resources[0].status).toBe('BOUND')

  await page.getByText('导入时新建项目', { exact: true }).click()
  const newCode = `transfer_verify_${Date.now()}`
  await page.getByPlaceholder('新项目编码（可覆盖源编码）').fill(newCode)
  await page.getByPlaceholder('新项目名称（可覆盖源名称）').fill('离线迁移独立验收项目')
  await choose('资源冲突策略', '复用相同配置')
  const created = await importFromUi()
  expect(created.projectCreated).toBe(true)
  expect(created.targetProjectId).toBeGreaterThan(0)
  expect(created.targetProjectId).not.toBe(sourceId)
  expect(created.resourceIdMapping[`PROJECT:${sourceId}`]).toBe(created.targetProjectId)

  await page.getByRole('button', { name: '项目管理', exact: true }).first().click()
  await page.getByRole('textbox', { name: '项目编码', exact: true }).fill(newCode)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('cell', { name: newCode, exact: true })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('real-new-project.png'), fullPage: true })
  expect(pageErrors).toEqual([])
  console.log(JSON.stringify({ sourceId, createdProjectId: created.targetProjectId, createdProjectCode: newCode }))
})
