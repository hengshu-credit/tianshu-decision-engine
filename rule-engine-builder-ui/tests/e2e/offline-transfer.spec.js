const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const baseUrl = process.env.TRANSFER_DEV_URL || 'http://tianshu.local/index.html'

async function installTransferRoutes(page) {
  const { assertClean } = await installDistRoutes(page)
  const previews = []
  const imports = []
  const exports = []
  const searches = []
  let delayNextPreview = null
  let failNextSearch = false
  const parseOptions = request => {
    const match = /name="options"\r\n\r\n([\s\S]*?)\r\n--/.exec(request.postData())
    if (!match) throw new Error('导入请求缺少 options multipart 字段')
    return JSON.parse(match[1])
  }
  // 开发服务器验证也使用相同的 API 契约夹具；页面和交互运行真实 Vue/Element Plus。
  await page.route('**/api/**', async route => {
    const request = route.request()
    const pathname = new URL(request.url()).pathname
    let data
    if (pathname === '/api/auth/console/config') data = { loginEnabled: false }
    else if (pathname === '/api/auth/console/me') data = { username: 'e2e' }
    else if (pathname === '/api/rule/project/list') data = {
      records: [{ id: 7, projectName: '授信项目', projectCode: 'credit' }], total: 1,
    }
    else if (pathname === '/api/rule/transfer/resources') {
      const params = Object.fromEntries(new URL(request.url()).searchParams)
      searches.push(params)
      if (failNextSearch) {
        failNextSearch = false
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 500, message: '候选服务暂时不可用' }) })
        return
      }
      data = { records: params.nodeType === 'MODEL'
        ? [{ id: 101, displayName: '授信评分 (score)', scope: 'GLOBAL' }]
        : [{ id: 101, displayName: '授信判断 (risk_rule)', scope: 'PROJECT', projectId: 7, projectName: '授信项目', projectCode: 'credit' }], total: 1 }
    }
    else if (pathname === '/api/rule/transfer/export') {
      exports.push(request.postDataJSON())
      await route.fulfill({ contentType: 'application/zip', body: Buffer.from('transfer-test-package') })
      return
    } else if (pathname === '/api/rule/transfer/preview/options') {
      previews.push(parseOptions(request))
      const wait = delayNextPreview
      delayNextPreview = null
      if (wait) await wait
      data = { packageDigest: 'fixture-digest', conflictCount: 0, roots: ['RULE:101'], resources: [], warnings: [] }
    } else if (pathname === '/api/rule/transfer/import') {
      imports.push(parseOptions(request))
      data = { status: 'APPLIED', resources: [{ resourceType: 'RULE', action: 'CREATED', targetId: 201 }] }
    } else {
      await route.fallback()
      return
    }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'success', data }) })
  })
  return { assertClean, previews, imports, exports, searches, delayPreview(wait) { delayNextPreview = wait }, failSearch() { failNextSearch = true } }
}

async function exportAndUpload(page, testInfo) {
  await page.getByRole('combobox', { name: '第 1 项导出资源', exact: true }).fill('授信')
  await page.getByRole('option', { name: '授信判断 (risk_rule) · 授信项目 / credit', exact: true }).click()
  const downloaded = page.waitForEvent('download')
  await page.getByRole('button', { name: '生成并下载配置包' }).click()
  const file = testInfo.outputPath('transfer.zip')
  await (await downloaded).saveAs(file)
  await page.locator('input[type="file"]').setInputFiles(file)
}

async function chooseScope(page, label) {
  await page.getByRole('combobox', { name: '目标范围', exact: true }).press('ArrowDown')
  await page.getByRole('option', { name: label, exact: true }).click()
}

test('离线迁移页面展示导出、目标范围和冲突策略流程', async ({ page }) => {
  const { assertClean } = await installTransferRoutes(page)
  await page.goto(`${baseUrl}#/transfer`)

  await expect(page.getByRole('heading', { name: '离线配置迁移' })).toBeVisible()
  await expect(page.getByText('配置包即时生成')).toBeVisible()
  await expect(page.getByRole('button', { name: '生成并下载配置包' })).toBeVisible()
  await expect(page.getByText('名单记录、日志和账单不会进入配置包。')).toBeVisible()

  await expect(page.getByRole('combobox', { name: '第 1 项导出资源', exact: true })).toBeVisible()
  await page.getByText('导入时新建项目', { exact: true }).click()
  await expect(page.getByPlaceholder('新项目编码（可覆盖源编码）')).toBeVisible()
  await expect(page.getByPlaceholder('新项目名称（可覆盖源名称）')).toBeVisible()
  await expect(page.getByText('导入并发布规则（重建固定版本）', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '预览冲突' })).toBeDisabled()
  await expect(page.getByRole('button', { name: '确认导入' })).toBeDisabled()
  await expect(page.locator('.menu-label', { hasText: '离线迁移' })).toBeVisible()
  await assertClean()
})

test('离线迁移从下载到绑定项目导入始终使用当前预检选项', async ({ page }, testInfo) => {
  const fixture = await installTransferRoutes(page)
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto(`${baseUrl}#/transfer`)
  await exportAndUpload(page, testInfo)
  await page.getByRole('combobox', { name: '目标项目', exact: true }).fill('授信')
  await page.getByRole('option', { name: '授信项目 / credit', exact: true }).click()
  const apply = page.getByRole('button', { name: '确认导入', exact: true })
  await expect(apply).toBeDisabled()
  await page.getByRole('button', { name: '预览冲突' }).click()
  await expect(apply).toBeEnabled()

  await page.getByRole('combobox', { name: '资源冲突策略' }).press('ArrowDown')
  await page.getByRole('option', { name: '覆盖已有配置', exact: true }).click()
  await expect(apply).toBeDisabled()
  await expect(page.getByRole('heading', { name: '导入预检' })).toHaveCount(0)
  await page.getByRole('button', { name: '预览冲突' }).click()
  await expect(apply).toBeEnabled()
  await apply.click()
  const confirm = page.getByRole('dialog', { name: '确认导入' })
  await expect(confirm).toContainText('所选项目')
  await confirm.getByRole('button', { name: '确定', exact: true }).click()
  await expect(confirm).toBeHidden()
  await expect(page.getByRole('dialog', { name: '导入结果' })).toBeVisible()
  expect(fixture.exports).toEqual([[{ resourceType: 'RULE', resourceId: 101 }]])
  expect(fixture.previews).toHaveLength(2)
  expect(fixture.imports).toEqual([fixture.previews[1]])
  expect(fixture.imports[0]).toMatchObject({ targetProjectId: 7, targetScope: 'PROJECT', resourcePolicy: 'OVERWRITE' })
  await page.screenshot({ path: testInfo.outputPath('project-import-result.png'), fullPage: true })
  await page.getByRole('dialog', { name: '导入结果' }).getByRole('button', { name: '关闭此对话框' }).click()
  await expect(apply).toBeDisabled()
  fixture.assertClean()
})

test('新建项目切换全局后迟到预检不能恢复导入按钮', async ({ page }, testInfo) => {
  const fixture = await installTransferRoutes(page)
  await page.goto(`${baseUrl}#/transfer`)
  await exportAndUpload(page, testInfo)
  await page.getByText('导入时新建项目', { exact: true }).click()
  await page.getByPlaceholder('新项目编码（可覆盖源编码）').fill('new_credit')
  await page.getByPlaceholder('新项目名称（可覆盖源名称）').fill('新授信项目')
  let release
  fixture.delayPreview(new Promise(resolve => { release = resolve }))
  await page.getByRole('button', { name: '预览冲突' }).click()
  await expect.poll(() => fixture.previews.length).toBe(1)
  await chooseScope(page, '全局')
  const oldResponse = page.waitForResponse('**/api/rule/transfer/preview/options')
  release()
  await oldResponse
  const apply = page.getByRole('button', { name: '确认导入', exact: true })
  await expect(apply).toBeDisabled()
  await expect(page.getByPlaceholder('新项目编码（可覆盖源编码）')).toHaveCount(0)
  await page.getByRole('button', { name: '预览冲突' }).click()
  await expect(apply).toBeEnabled()
  await apply.click()
  const confirm = page.getByRole('dialog', { name: '确认导入' })
  await expect(confirm).toContainText('全局范围')
  await confirm.getByRole('button', { name: '取消', exact: true }).click()
  expect(fixture.imports).toHaveLength(0)
  await expect(apply).toBeEnabled()
  await apply.click()
  await confirm.getByRole('button', { name: '确定', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '导入结果' })).toBeVisible()
  expect(fixture.imports).toEqual([fixture.previews[1]])
  expect(fixture.imports[0]).toMatchObject({ targetScope: 'GLOBAL', createProject: false, targetProjectId: null, projectCode: null, projectName: null })
  fixture.assertClean()
})

test('新建项目与发布策略显示和发送一致', async ({ page }, testInfo) => {
  const fixture = await installTransferRoutes(page)
  await page.goto(`${baseUrl}#/transfer`)
  await exportAndUpload(page, testInfo)
  await page.getByText('导入时新建项目', { exact: true }).click()
  await page.getByPlaceholder('新项目编码（可覆盖源编码）').fill('new_credit')
  await page.getByPlaceholder('新项目名称（可覆盖源名称）').fill('新授信项目')
  await page.getByText('导入并发布规则（重建固定版本）', { exact: true }).click()
  await page.getByRole('button', { name: '预览冲突' }).click()
  await page.getByRole('button', { name: '确认导入', exact: true }).click()
  const confirm = page.getByRole('dialog', { name: '确认导入' })
  await expect(confirm).toContainText('新项目「新授信项目」')
  await expect(confirm).toContainText('规则将按选项发布')
  await confirm.getByRole('button', { name: '确定', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '导入结果' })).toContainText('规则已按所选策略发布')
  expect(fixture.imports).toEqual([fixture.previews[0]])
  expect(fixture.imports[0]).toMatchObject({ createProject: true, projectCode: 'new_credit', publishRules: true })
  fixture.assertClean()
})

test('导出按名称选择，重复提示，切换类型后重新选择并提交各自 ID', async ({ page }, testInfo) => {
  const fixture = await installTransferRoutes(page)
  await page.goto(`${baseUrl}#/transfer`)
  const first = page.getByRole('combobox', { name: '第 1 项导出资源', exact: true })
  await first.fill('risk_rule')
  await page.getByRole('option', { name: '授信判断 (risk_rule) · 授信项目 / credit', exact: true }).click()
  await page.getByRole('button', { name: '添加根资源', exact: true }).click()
  const second = page.getByRole('combobox', { name: '第 2 项导出资源', exact: true })
  await second.fill('授信')
  const secondList = page.locator(`[id="${await second.getAttribute('aria-controls')}"]`)
  await secondList.getByRole('option', { name: '授信判断 (risk_rule) · 授信项目 / credit', exact: true }).click()
  await expect(page.getByText('该资源已添加，请选择其他资源', { exact: true })).toBeVisible()
  await page.getByRole('combobox', { name: '第 2 项资源类型', exact: true }).press('ArrowDown')
  await page.getByRole('option', { name: '模型', exact: true }).click()
  await page.getByRole('button', { name: '生成并下载配置包', exact: true }).click()
  await expect(page.getByText('请为每一行选择要导出的资源', { exact: true })).toBeVisible()
  expect(fixture.exports).toEqual([])
  await second.fill('score')
  await page.getByRole('option', { name: '授信评分 (score) · 全局', exact: true }).click()
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: '生成并下载配置包', exact: true }).click()
  await (await download).saveAs(testInfo.outputPath('multiple-resources.zip'))
  expect(fixture.exports).toEqual([[{ resourceType: 'RULE', resourceId: 101 }, { resourceType: 'MODEL', resourceId: 101 }]])
  expect(fixture.searches).toContainEqual(expect.objectContaining({ nodeType: 'RULE', keyword: 'risk_rule', pageNum: '1', pageSize: '20' }))
  expect(fixture.searches).toContainEqual(expect.objectContaining({ nodeType: 'MODEL', keyword: 'score' }))
  await page.getByRole('button', { name: '移除第 1 项资源', exact: true }).click()
  await expect(page.locator('.root-row')).toHaveCount(1)
  await expect(page.locator('.root-row')).toContainText('授信评分 (score) · 全局')
  fixture.assertClean()
})

test('资源候选加载失败后显示行内说明，再次搜索可恢复', async ({ page }) => {
  const fixture = await installTransferRoutes(page)
  await page.goto(`${baseUrl}#/transfer`)
  fixture.failSearch()
  const select = page.getByRole('combobox', { name: '第 1 项导出资源', exact: true })
  await select.click()
  await expect(page.getByText('候选资源加载失败，请重新展开选择器或搜索重试', { exact: true })).toBeVisible()
  expect(fixture.searches).toHaveLength(1)
  await select.fill('risk_rule')
  await page.getByRole('option', { name: '授信判断 (risk_rule) · 授信项目 / credit', exact: true }).click()
  await expect(page.locator('.root-error')).toHaveCount(0)
  expect(fixture.searches).toHaveLength(2)
  fixture.assertClean()
})
