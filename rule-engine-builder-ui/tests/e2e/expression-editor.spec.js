const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')

test('表达式编辑器从规则设计器打开后可配置、测试、暂存和编译', async ({ page }) => {
  const pageErrors = []
  const consoleErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  await page.setViewportSize({ width: 1440, height: 900 })
  const { requests, assertClean } = await installDistRoutes(page, {
    apiData: createDesignerApiData()
  })

  await page.goto('http://tianshu.local/index.html#/designer/table/101')
  await page.getByRole('button', { name: '添加行', exact: true }).click()
  await page.getByRole('button', { name: '配置组合表达式' }).first().click()

  await expect(page).toHaveURL(/#\/designer\/expression\/101\/expression-101-operand-picker-\d+$/)
  const main = page.getByRole('main')
  await expect(main.getByText('决策表 · 左操作数', { exact: true }).first()).toBeVisible()
  await expect(main.getByText('age', { exact: true })).toBeVisible()

  const metrics = await page.evaluate(() => ({
    viewport: window.innerWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
    userSelect: getComputedStyle(document.querySelector('main')).userSelect
  }))
  expect(metrics.document).toBeLessThanOrEqual(metrics.viewport + 1)
  expect(metrics.body).toBeLessThanOrEqual(metrics.viewport + 1)
  expect(metrics.userSelect).not.toBe('none')

  await main.getByText('age', { exact: true }).click()
  await expect(main.getByText('年龄 age', { exact: false }).first()).toBeVisible()

  await main.getByRole('button', { name: '测试', exact: true }).click()
  const testDialog = page.getByRole('dialog', { name: '测试当前表达式' })
  await expect(testDialog).toBeVisible()
  await expect(testDialog.getByText('年龄')).toBeVisible()
  await expect.poll(async () => {
    const dialogBox = await testDialog.boundingBox()
    return dialogBox &&
      dialogBox.x >= 0 &&
      dialogBox.y >= 0 &&
      dialogBox.x + dialogBox.width <= 1440 &&
      dialogBox.y + dialogBox.height <= 900
  }).toBe(true)
  await testDialog.getByRole('button', { name: '开始测试' }).click()
  await expect(testDialog.getByText('测试通过')).toBeVisible()
  await testDialog.getByRole('button', { name: '关闭', exact: true }).click()

  await main.getByRole('button', { name: '临时保存' }).click()
  await expect(main.getByText('草稿已临时保存')).toBeVisible()
  await main.getByRole('button', { name: '保存并编译' }).click()
  await expect(page).toHaveURL(/#\/designer\/table\/101$/)
  await expect(page.getByText('共 1 条规则', { exact: true })).toBeVisible()

  expect(requests.some(request => new URL(request.url).pathname === '/api/rule/expression/schema')).toBe(true)
  expect(requests.some(request => new URL(request.url).pathname === '/api/rule/expression/test')).toBe(true)
  expect(requests.some(request => new URL(request.url).pathname === '/api/rule/expression/compile')).toBe(true)
  expect(pageErrors).toEqual([])
  expect(consoleErrors).toEqual([])
  assertClean()
})

test('多个规则表达式会话返回各自设计器并通过草稿安全切换', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  const designerApiData = createDesignerApiData()
  // 此用例验证已有草稿中的表达式会话；正式版本的手动暂存另有覆盖。
  designerApiData.set('/api/rule/definition/versions/101', [])
  designerApiData.set('/api/rule/definition/101/published-versions', [])
  designerApiData.set('POST /api/rule/definition/101/designer/drafts', async ({ request }) => {
    const payload = request.postDataJSON()
    return {
      revision: {
        id: 2101,
        definitionId: 101,
        revisionNo: 1,
        state: 'DRAFT',
        lockVersion: Number(payload.lockVersion || 0) + 1,
        modelJson: payload.modelJson
      },
      compileSuccess: true,
      issues: []
    }
  })
  const { assertClean } = await installDistRoutes(page, {
    apiData: designerApiData
  })

  await page.goto('http://tianshu.local/index.html#/designer/table/101')
  await page.getByRole('button', { name: '添加行', exact: true }).click()
  await expect(page.getByText('共 1 条规则', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '配置组合表达式' }).first().click()
  await expect(page).toHaveURL(/#\/designer\/expression\/101\//)

  await page.goto('http://tianshu.local/index.html#/designer/ruleset/104')
  await page.getByRole('button', { name: '添加规则', exact: true }).click()
  await expect(page.getByText('共 1 条规则', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '配置组合表达式' }).first().click()
  await expect(page).toHaveURL(/#\/designer\/expression\/104\//)

  await page.getByRole('button', { name: '决策表 · 左操作数', exact: true }).click()
  await page.getByRole('main').getByRole('button', { name: '返回', exact: true }).click()
  await expect(page).toHaveURL(/#\/designer\/table\/101$/)
  await expect(page.getByText('共 1 条规则', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  const saveDialog = page.getByRole('dialog', { name: '保存草稿', exact: true })
  await saveDialog.getByText('覆盖当前草稿', { exact: true }).click()
  await saveDialog.locator('[data-action="confirm-save"]').click()
  await expect(page.getByRole('status')).toContainText('草稿已保存')

  await page.getByRole('button', { name: '规则集 · 左操作数', exact: true }).click()
  await page.getByRole('main').getByRole('button', { name: '返回', exact: true }).click()
  await expect(page).toHaveURL(/#\/designer\/ruleset\/104$/)
  await expect(page.getByText('共 1 条规则', { exact: true })).toBeVisible()
  assertClean()
})
