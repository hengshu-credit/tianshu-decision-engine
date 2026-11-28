const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createManagementApiData } = require('./support/managementFixtures.cjs')

test('从实验管理创建全局实验、选择已发布全局规则并重新编辑同一草稿', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const permissions = ['experiment:view', 'experiment:edit', 'rule:view']
  let snapshot
  const rule = { id: 81, ruleCode: 'GLOBAL_APPROVED', ruleName: '已发布全局规则', projectId: 0,
    scope: 'GLOBAL', status: 1, publishedVersion: 1, modelType: 'SCRIPT' }
  const apiData = new Map([
    ['/api/auth/console/config', { loginEnabled: true }],
    ['/api/auth/console/me', { userId: 1, username: 'operator', permissions, defaultPermissions: permissions }],
    ['/api/auth/console/preferences/theme', {}], ['POST /api/rule/console/activity', null],
    ['/api/rule/experiment/list', { records: [], total: 0 }],
    ['/api/rule/definition/project-list/0', ({ url }) => {
      expect(url.searchParams.get('scope')).toBe('GLOBAL')
      expect(url.searchParams.get('status')).toBe('1')
      return { records: [rule, { ...rule, id: 82, scope: 'PROJECT', projectId: 1, ruleName: '项目私有规则' },
        { ...rule, id: 83, ruleName: '未发布规则', publishedVersion: 0 }], total: 3 }
    }],
    ['/api/rule/definition/81/published-versions', [{ version: 1, bindingId: 94 }]],
    ['/api/rule/field-validation/available', []],
    ['/api/rule/experiment/logs', { records: [], total: 0 }],
    ['/api/rule/experiment/21', () => ({ ...snapshot, id: 21 })],
    ['POST /api/rule/governance/drafts', ({ request }) => {
      const draft = request.postDataJSON()
      expect(draft).toMatchObject({ resourceType: 'EXPERIMENT', action: snapshot ? 'UPDATE' : 'CREATE', projectId: null })
      snapshot = JSON.parse(draft.snapshotJson)
      expect(snapshot).toMatchObject({ projectId: null, groups: [{ ruleId: 81, ruleCode: 'GLOBAL_APPROVED' }] })
      return { id: 52, resourceId: 21, status: 'EDITING' }
    }],
  ])
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData })
  await page.goto(origin + '/#/experiment')
  await page.getByRole('button', { name: '新建实验', exact: true }).click()
  await page.locator('.base-form .el-form-item').filter({ hasText: '作用范围' }).locator('.el-select').click()
  await page.getByRole('option', { name: '全局', exact: true }).click()
  await page.getByRole('textbox', { name: /实验编码$/ }).fill('EXP_GLOBAL')
  await page.getByRole('textbox', { name: /实验名称$/ }).fill('全局分流实验')
  await page.getByPlaceholder('选择要执行的规则').first().click()
  const picker = page.locator('.fp-panel:visible')
  await expect(picker.locator('.fp-row').filter({ hasText: '已发布全局规则' })).toBeVisible()
  await expect(picker.locator('.fp-row').filter({ hasText: /项目私有规则|未发布规则/ })).toHaveCount(0)
  await picker.locator('.fp-row').filter({ hasText: '已发布全局规则' }).dblclick()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page).toHaveURL(/#\/experiment\/detail\/21/)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('button', { name: '分流实验', exact: true }).click()
  await page.getByRole('button', { name: '新建实验', exact: true }).click()
  await expect(page.getByRole('textbox', { name: /实验名称$/ })).toHaveValue('')
  await expect(page.getByRole('combobox', { name: '实验作用范围' })).toBeEnabled()
  await page.goto(origin + '/#/experiment/detail/21')
  await expect(page.getByRole('combobox', { name: '实验作用范围' })).toBeDisabled()
  await expect(page.getByRole('textbox', { name: /实验名称$/ })).toHaveValue('全局分流实验')
  await page.getByRole('textbox', { name: /实验名称$/ }).fill('更新全局实验')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => snapshot?.experimentName).toBe('更新全局实验')
  harness.assertClean()
})

test('模型字段数按实际数量显示数字进出，旧列表缺少汇总时读取对应详情', async ({ page }) => {
  const apiData = createManagementApiData()
  const list = apiData.get('/api/rule/model/list')
  const current = list.records[0]
  const legacy = { ...current, id: 42, modelCode: 'legacy_model', inputFieldCount: null, outputFieldCount: null }
  const empty = { ...legacy, id: 43, modelCode: 'empty_model', inputFields: [], outputFields: [] }
  list.records.push(legacy, empty)
  list.total = list.records.length
  apiData.set('/api/rule/model/42', { ...legacy, inputFields: [{ fieldName: 'image' }], outputFields: [{ fieldName: 'boxes' }] })
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const { assertClean, requests } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData })
  await page.goto(`${origin}/#/model`)
  for (const [code, count] of [['credit_score', '5进 / 2出'], ['legacy_model', '1进 / 1出'], ['empty_model', '0进 / 0出']]) {
    await expect(page.getByRole('row').filter({ hasText: code }).getByRole('cell', { name: count, exact: true })).toBeVisible()
  }
  expect(requests.filter(r => /\/model\/\d+$/.test(new URL(r.url).pathname)).map(r => new URL(r.url).pathname)).toEqual(['/api/rule/model/42'])
  assertClean()
})

const pages = [
  {
    name: '项目管理',
    path: '/project',
    rowText: 'e2e_project',
    createButton: '新建项目',
    dialogTitle: '新建项目',
    listApi: '/api/rule/project/list'
  },
  {
    name: '规则管理',
    path: '/rule',
    rowText: 'age_rule',
    createButton: '新建规则',
    dialogTitle: '新建规则',
    listApi: '/api/rule/definition/list'
  },
  {
    name: '变量管理',
    path: '/variable',
    rowText: 'age',
    createButton: '新建字段',
    dialogTitle: '新建字段',
    listApi: '/api/rule/variable/list',
    projectSelectIndex: 1
  },
  {
    name: '名单管理',
    path: '/list',
    rowText: 'mobile_black',
    createButton: '新建名单库',
    dialogTitle: '新建名单库',
    listApi: '/api/rule/list/library'
  },
  {
    name: '外数管理',
    path: '/datasource',
    rowText: 'credit_vendor',
    createButton: '新建数据源',
    dialogTitle: '新建外数数据源',
    createRoute: '/datasource/source/new',
    listApi: '/api/rule/datasource/list'
  },
  {
    name: '数据库管理',
    path: '/database',
    rowText: 'risk_mysql',
    createButton: '新建数据库',
    dialogTitle: '新建数据库数据源',
    createRoute: '/database/new',
    listApi: '/api/rule/database/list'
  },
  {
    name: '模型管理',
    path: '/model',
    rowText: 'credit_score',
    createButton: '上传模型',
    dialogTitle: '上传模型',
    listApi: '/api/rule/model/list'
  }
]

function visibleFilterArea(page) {
  return page.locator('.uiue-search-container:visible, .tab-filter-row:visible').first()
}

async function projectCodeInput(page, pageCase) {
  if (pageCase.projectSelectIndex != null) {
    return visibleFilterArea(page)
      .locator('.remote-filter-input input:visible')
      .first()
  }
  return page
    .locator('.el-form-item')
    .filter({ hasText: '项目编码' })
    .locator('input:visible')
    .first()
}

async function expectTextSelectable(locator, expectedText) {
  const selection = await locator.evaluate(element => {
    const range = document.createRange()
    range.selectNodeContents(element)
    const rangeText = range.toString()
    const currentSelection = window.getSelection()
    currentSelection.removeAllRanges()
    currentSelection.addRange(range)
    return {
      text: currentSelection.toString(),
      rangeText,
      userSelect: window.getComputedStyle(element).userSelect,
      inertAncestor: element.closest('[inert]')?.outerHTML.slice(0, 300)
    }
  })
  expect(selection.text, JSON.stringify(selection)).toBe(expectedText)
  expect(selection.userSelect).not.toBe('none')
}

for (const pageCase of pages) {
  test(`${pageCase.name}的数据、筛选、复制和弹框均可用`, async ({ page }) => {
    const pageErrors = []
    const consoleErrors = []
    page.on('pageerror', error => pageErrors.push(error.message))
    page.on('console', message => {
      if (message.type() === 'error') consoleErrors.push(message.text())
    })

    await page.setViewportSize({ width: 1440, height: 900 })
    const { requests, assertClean } = await installDistRoutes(page, {
      apiData: createManagementApiData()
    })
    await page.goto(`http://tianshu.local/index.html#${pageCase.path}`)

    const rowText = page.getByText(pageCase.rowText, { exact: true }).first()
    await expect(rowText).toBeVisible()
    await expect(visibleFilterArea(page)).toBeVisible()
    await expect(page.getByRole('button', { name: pageCase.createButton })).toBeVisible()

    const viewportMetrics = await page.evaluate(() => ({
      viewportWidth: window.innerWidth,
      documentWidth: document.documentElement.scrollWidth,
      bodyWidth: document.body.scrollWidth
    }))
    expect(viewportMetrics.documentWidth).toBeLessThanOrEqual(viewportMetrics.viewportWidth + 1)
    expect(viewportMetrics.bodyWidth).toBeLessThanOrEqual(viewportMetrics.viewportWidth + 1)

    await expectTextSelectable(rowText, pageCase.rowText)

    const projectInput = await projectCodeInput(page, pageCase)
    await expect(projectInput).toBeVisible()
    await projectInput.fill('e2e_project')
    const requestCount = requests.filter(request =>
      new URL(request.url).pathname === pageCase.listApi
    ).length
    await visibleFilterArea(page).getByRole('button', { name: '查询' }).first().click()
    await expect.poll(() => requests.filter(request =>
      new URL(request.url).pathname === pageCase.listApi
    ).length).toBeGreaterThan(requestCount)
    const lastListRequest = requests
      .filter(request => new URL(request.url).pathname === pageCase.listApi)
      .at(-1)
    expect(new URL(lastListRequest.url).searchParams.get('projectCode')).toBe('e2e_project')

    await visibleFilterArea(page).getByRole('button', { name: '重置' }).first().click()
    await expect(projectInput).toHaveValue('')

    await page.getByRole('button', { name: pageCase.createButton }).click()
    if (pageCase.createRoute) {
      await expect(page).toHaveURL(new RegExp(`#${pageCase.createRoute}$`))
      await expect(
        page.getByText(pageCase.dialogTitle, { exact: true }).first()
      ).toBeVisible()
      await expect(page.getByRole('button', { name: '保存', exact: true })).toBeVisible()
      await expect(page.getByRole('button', { name: '返回' })).toBeVisible()
      const createPageMetrics = await page.evaluate(() => ({
        viewportWidth: window.innerWidth,
        documentWidth: document.documentElement.scrollWidth,
        bodyWidth: document.body.scrollWidth
      }))
      expect(createPageMetrics.documentWidth).toBeLessThanOrEqual(
        createPageMetrics.viewportWidth + 1
      )
      expect(createPageMetrics.bodyWidth).toBeLessThanOrEqual(
        createPageMetrics.viewportWidth + 1
      )
      await page.getByRole('button', { name: '返回' }).click()
      await expect(rowText).toBeVisible()
      expect(pageErrors).toEqual([])
      expect(consoleErrors).toEqual([])
      assertClean()
      return
    }
    const dialog = page.locator('.el-dialog:visible').last()
    await expect(dialog).toBeVisible()
    await expect(dialog.getByText(pageCase.dialogTitle, { exact: true }).first()).toBeVisible()
    const viewport = page.viewportSize()
    // 弹窗可见时入场位移动画可能尚未结束，等待最终布局但保持原视口边界断言。
    await expect(async () => {
      const box = await dialog.boundingBox()
      expect(box).not.toBeNull()
      expect(box.x).toBeGreaterThanOrEqual(0)
      expect(box.y).toBeGreaterThanOrEqual(0)
      expect(box.x + box.width).toBeLessThanOrEqual(viewport.width + 1)
      expect(box.y + box.height).toBeLessThanOrEqual(viewport.height + 1)
    }).toPass()

    const handle = dialog.getByRole('separator', { name: '拖拽调整弹窗大小' })
    await expect(handle).toHaveCount(1)
    const body = dialog.locator(':scope > .el-dialog__body')
    expect(await body.evaluate(el => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(1)
    expect(await body.evaluate(el => parseFloat(getComputedStyle(el).paddingTop))).toBeGreaterThanOrEqual(8)
    const before = await dialog.boundingBox()
    await handle.focus()
    await page.keyboard.press('ArrowRight')
    await page.keyboard.press('ArrowUp')
    await expect.poll(async () => (await dialog.boundingBox()).width).toBeGreaterThan(before.width)
    await expect.poll(async () => (await dialog.boundingBox()).height).toBeLessThan(before.height)

    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
    expect(pageErrors).toEqual([])
    expect(consoleErrors).toEqual([])
    assertClean()
  })
}
