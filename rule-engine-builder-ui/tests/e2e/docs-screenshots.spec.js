const path = require('node:path')
const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDocsApiData } = require('./support/docsFixtures.cjs')

const screenshotRoot = path.resolve(__dirname, '../../../docs/project-usage')

async function openPage(page, route, apiData = createDocsApiData()) {
  await page.setViewportSize({ width: 1600, height: 1000 })
  const routing = await installDistRoutes(page, { apiData })
  await page.goto(`http://tianshu.local/index.html#${route}`)
  await expect(page.getByRole('main')).toBeVisible()
  await page.waitForTimeout(250)
  return routing
}

async function screenshot(page, fileName) {
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  await page.evaluate(() => {
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
    document.querySelectorAll('.el-message, .el-notification').forEach((element) => {
      element.remove()
    })
  })
  await page.screenshot({
    path: path.join(screenshotRoot, fileName),
    animations: 'disabled'
  })
}

test('生成登录页和全部一级业务页面截图', async ({ page }) => {
  test.setTimeout(180000)

  const loginRoutes = createDocsApiData()
  loginRoutes.set('/api/auth/console/config', { loginEnabled: true })
  await openPage(page, '/login', loginRoutes)
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
  await screenshot(page, 'project-usage-01-login.png')

  const pages = [
    ['/project', 'project-usage-02-project-list.png'],
    ['/rule', 'project-usage-02-rule-list.png'],
    ['/variable', 'project-usage-03-variable.png'],
    ['/list', 'project-usage-09-list.png'],
    ['/datasource', 'project-usage-10-datasource.png'],
    ['/database', 'project-usage-11-database.png'],
    ['/model', 'project-usage-13-model.png'],
    ['/function', 'project-usage-04-function.png'],
    ['/experiment', 'project-usage-15-experiment.png'],
    ['/log', 'project-usage-06-execution-log.png'],
    ['/billing', 'project-usage-16-billing.png']
  ]

  for (const [route, fileName] of pages) {
    await openPage(page, route)
    await screenshot(page, fileName)
  }
})

test('生成详情、新建与配置页面截图', async ({ page }) => {
  test.setTimeout(180000)
  const pages = [
    ['/project/1', 'project-usage-07-project-detail.png'],
    ['/rule/101', 'project-usage-08-rule-detail.png'],
    ['/list/9', 'project-usage-09-list-detail.png'],
    ['/datasource/source/21', 'project-usage-10-datasource-detail.png'],
    ['/datasource/api/22', 'project-usage-10-api-detail.png'],
    ['/database/31', 'project-usage-11-database-detail.png'],
    ['/model/41', 'project-usage-13-model-detail.png'],
    ['/experiment/detail/61', 'project-usage-15-experiment-detail.png'],
    ['/datasource/source/new', 'project-usage-10-datasource-create.png'],
    ['/datasource/api/new', 'project-usage-10-api-create.png'],
    ['/database/new', 'project-usage-11-database-create.png'],
    ['/experiment/new', 'project-usage-15-experiment-create.png']
  ]

  for (const [route, fileName] of pages) {
    await openPage(page, route)
    await screenshot(page, fileName)
  }
})

test('生成鉴权、开放接口、外数、分流和账单关键业务状态截图', async ({
  page
}) => {
  test.setTimeout(180000)

  let routing = await openPage(page, '/project')
  const projectRow = page.getByRole('row').filter({ hasText: 'face_risk_center' })
  await projectRow.getByRole('button', { name: '鉴权', exact: true }).click()
  const authDialog = page.getByRole('dialog', { name: '项目调用鉴权' })
  await expect(authDialog).toBeVisible()
  await screenshot(page, 'project-usage-02-project-auth.png')
  const authRow = authDialog.getByRole('row').filter({ hasText: 'FACE_API_MAIN' })
  await authRow.getByRole('button', { name: 'Token', exact: true }).click()
  await expect(authDialog.getByText('TOKEN_FACE_20260724', { exact: true })).toBeVisible()
  await screenshot(page, 'project-usage-02-project-auth-token.png')
  await authDialog.getByRole('tab', { name: '访问审计', exact: true }).click()
  await expect(authDialog.getByText('/api/open/rule/face_identity_rule/execute', { exact: true })).toBeVisible()
  await screenshot(page, 'project-usage-02-project-auth-audit.png')
  routing.assertClean()

  routing = await openPage(page, '/datasource/source/21')
  const authSection = page.getByText('数据源鉴权配置', { exact: true })
  await authSection.scrollIntoViewIfNeeded()
  await screenshot(page, 'project-usage-10-datasource-auth.png')
  routing.assertClean()

  routing = await openPage(page, '/datasource/api/22')
  await page.getByRole('tab', { name: '接口鉴权', exact: true }).click()
  await screenshot(page, 'project-usage-10-api-auth.png')
  await page.getByRole('tab', { name: '请求体', exact: true }).click()
  await screenshot(page, 'project-usage-10-api-request.png')
  await page.getByRole('tab', { name: '响应体', exact: true }).click()
  await screenshot(page, 'project-usage-10-api-response.png')
  routing.assertClean()

  routing = await openPage(page, '/rule/101')
  await page.getByRole('tab', { name: /开放接口/ }).click()
  await expect(page.getByText('对外规则契约', { exact: true })).toBeVisible()
  await page.locator('.open-api-panel').scrollIntoViewIfNeeded()
  await screenshot(page, 'project-usage-08-rule-open-api.png')
  await page.getByRole('tab', { name: /API 测试用例/ }).click()
  await expect(page.getByText('活体通过且人证一致', { exact: true })).toBeVisible()
  await page.locator('.api-scenario-panel').scrollIntoViewIfNeeded()
  await screenshot(page, 'project-usage-08-rule-api-scenarios.png')
  routing.assertClean()

  routing = await openPage(page, '/experiment/detail/61')
  await expect(
    page.locator('.el-form-item').filter({ hasText: '实验编码' }).locator('input')
  ).toHaveValue('face_model_upgrade_ab')
  await screenshot(page, 'project-usage-15-experiment-config.png')
  routing.assertClean()

  routing = await openPage(page, '/billing')
  await screenshot(page, 'project-usage-16-billing-config.png')
  await page.getByRole('tab', { name: '计费明细', exact: true }).click()
  await screenshot(page, 'project-usage-16-billing-record.png')
  await page.getByRole('tab', { name: '计费汇总', exact: true }).click()
  await screenshot(page, 'project-usage-16-billing-summary.png')
  routing.assertClean()
})

test('生成字段管理、规则字段、模型字段和调用详情截图', async ({ page }) => {
  test.setTimeout(180000)

  let routing = await openPage(page, '/variable')
  await page.getByRole('tab', { name: '数据对象', exact: true }).click()
  await expect(page.getByText('FaceVerifyRequest', { exact: true })).toBeVisible()
  await page.locator('.var-group-header').filter({ hasText: 'FaceVerifyRequest' }).click()
  await expect(page.getByText('deviceId', { exact: true })).toBeVisible()
  await screenshot(page, 'project-usage-03-data-object.png')
  await page.getByRole('tab', { name: '字段校验', exact: true }).click()
  await expect(page.getByText('face_url_required', { exact: true })).toBeVisible()
  await screenshot(page, 'project-usage-03-field-validation.png')
  routing.assertClean()

  routing = await openPage(page, '/rule/101')
  const inputTab = page.getByRole('tab', { name: /输入字段/ })
  await inputTab.click()
  await page.getByText('faceImageUrl', { exact: true }).first().scrollIntoViewIfNeeded()
  await screenshot(page, 'project-usage-08-rule-input-fields.png')
  const outputTab = page.getByRole('tab', { name: /输出字段/ })
  await outputTab.click()
  await page.getByText('verified', { exact: true }).first().scrollIntoViewIfNeeded()
  await screenshot(page, 'project-usage-08-rule-output-fields.png')
  routing.assertClean()

  routing = await openPage(page, '/model/41')
  await page.getByRole('tab', { name: /输出字段/ }).click()
  await screenshot(page, 'project-usage-13-model-output-fields.png')
  await page.getByRole('button', { name: '模型测试', exact: true }).click()
  const modelTestDialog = page.getByRole('dialog', { name: '模型测试' })
  await expect(modelTestDialog).toBeVisible()
  await modelTestDialog.getByRole('button', { name: '执行测试', exact: true }).click()
  await expect(modelTestDialog.getByText('执行成功', { exact: true })).toBeVisible()
  await screenshot(page, 'project-usage-13-model-test.png')
  routing.assertClean()

  routing = await openPage(page, '/datasource')
  await page.getByRole('tab', { name: '调用日志', exact: true }).click()
  await expect(page.getByText('外数调用日志', { exact: true })).toBeVisible()
  await screenshot(page, 'project-usage-10-datasource-log.png')
  routing.assertClean()

  routing = await openPage(page, '/database')
  await page.getByRole('tab', { name: '调用日志', exact: true }).click()
  await expect(page.getByText('数据库调用日志', { exact: true })).toBeVisible()
  await screenshot(page, 'project-usage-12-database-log.png')
  routing.assertClean()

  routing = await openPage(page, '/datasource/api/22')
  await page.getByRole('tab', { name: '接口测试', exact: true }).click()
  await page.getByRole('button', { name: '生成请求预览', exact: true }).click()
  await expect(page.getByText('请求预览已生成，未访问外部地址', { exact: true })).toBeVisible()
  await screenshot(page, 'project-usage-10-api-test.png')
  routing.assertClean()

  routing = await openPage(page, '/log')
  const logRow = page.getByRole('row').filter({ hasText: 'FACE202607240930000000000000000001' })
  await logRow.getByRole('button', { name: '详情', exact: true }).click()
  const logDrawer = page.getByRole('dialog', { name: '日志详情' })
  await expect(logDrawer).toBeVisible()
  await screenshot(page, 'project-usage-06-execution-log-detail.png')
  await logDrawer.getByRole('tab', { name: /表达式追踪树/ }).click()
  await expect(logDrawer.getByText('人脸识别核验', { exact: true }).first()).toBeVisible()
  await screenshot(page, 'project-usage-06-execution-log-trace.png')
  routing.assertClean()
})

test('生成人脸识别追踪树、血缘和表达式配置截图', async ({ page }) => {
  test.setTimeout(180000)

  let routing = await openPage(page, '/test')
  const ruleSelect = page.locator('.test-left .el-form-item')
    .filter({ hasText: '规则' })
    .locator('.el-select')
  await ruleSelect.click()
  await page.getByRole('option', { name: /face_identity_rule/ }).click()
  await page.getByRole('button', { name: '执行测试', exact: true }).click()
  await expect(page.getByText('执行成功', { exact: true })).toBeVisible()
  await screenshot(page, 'project-usage-05-rule-test.png')
  await page.getByRole('tab', { name: '表达式追踪树', exact: true }).click()
  await expect(page.getByText('人脸识别核验', { exact: true }).first()).toBeVisible()
  await screenshot(page, 'project-usage-05-rule-trace-tree.png')
  routing.assertClean()

  routing = await openPage(page, '/lineage')
  const startSelect = page.locator('.query-panel .el-form-item')
    .filter({ hasText: '起点' })
    .locator('.el-select')
  await startSelect.click()
  await page.getByRole('option', { name: '人脸图片地址 (faceImageUrl)' }).click()
  await page.getByRole('button', { name: '生成血缘图', exact: true }).click()
  await expect(page.getByText('face_identity_rule', { exact: true })).toBeVisible()
  await screenshot(page, 'project-usage-14-lineage.png')
  routing.assertClean()

  routing = await openPage(page, '/designer/table/101')
  await page.getByRole('button', { name: '添加行', exact: true }).click()
  await page.getByRole('button', { name: '配置组合表达式' }).first().click()
  await expect(page).toHaveURL(/#\/designer\/expression\/101\//)
  const faceField = page.getByText('livenessScore', { exact: true }).first()
  await expect(faceField).toBeVisible()
  await faceField.click()
  await page.getByText('运算符', { exact: true }).click()
  await page.getByRole('button', { name: '>=', exact: true }).click()
  await page.locator('.expression-palette')
    .locator('.palette-category').filter({ hasText: '手动输入' }).click()
  await page.locator('.expression-palette')
    .locator('.palette-manual-kind').filter({ hasText: '输入阈值' }).click()
  await page.locator('.canvas-inline-editor .el-select').click()
  await page.getByRole('option', { name: '数字', exact: true }).click()
  const thresholdInput = page.locator('input[placeholder="请输入阈值"]')
  await expect(thresholdInput).toBeVisible()
  await thresholdInput.fill('0.95')
  await expect(page.getByText(/livenessScore >= 0\.95/).first()).toBeVisible()
  await page.setViewportSize({ width: 1280, height: 720 })
  const editorMetrics = await page.locator('.expression-editor__body').evaluate((element) => ({
    clientWidth: element.clientWidth,
    scrollWidth: element.scrollWidth
  }))
  expect(editorMetrics.scrollWidth).toBeLessThanOrEqual(editorMetrics.clientWidth + 1)
  await page.setViewportSize({ width: 1440, height: 900 })
  await screenshot(page, 'project-usage-17-expression-editor.png')
  await page.getByRole('button', { name: '测试', exact: true }).click()
  const expressionDialog = page.getByRole('dialog', { name: '测试当前表达式' })
  await expect(expressionDialog).toBeVisible()
  await expressionDialog.getByText('朔源到最底层', { exact: true }).click()
  await page.getByRole('button', { name: '确认并继续', exact: true }).click()
  await expect(expressionDialog.getByText(/朔源测试可能产生费用/)).toBeVisible()
  await screenshot(page, 'project-usage-17-expression-test.png')
  routing.assertClean()
})

test('生成人脸识别修改、校验、评审、固化和发布流程截图', async ({ page }) => {
  test.setTimeout(180000)
  const routing = await openPage(page, '/rule/101')

  await page.getByTestId('preflight').click()
  await expect(page.getByText('发布前校验', { exact: true }).last()).toBeVisible()
  await expect(page.getByText('可继续', { exact: true })).toBeVisible()
  await screenshot(page, 'project-usage-08-rule-release-preflight.png')

  await page.getByTestId('submit').click()
  await expect(page.getByText('评审中', { exact: true }).first()).toBeVisible()
  await screenshot(page, 'project-usage-08-rule-release-review.png')

  await page.getByTestId('approve').click()
  await expect(page.getByText('已批准', { exact: true }).first()).toBeVisible()
  await screenshot(page, 'project-usage-08-rule-release-approved.png')

  await page.getByTestId('publish').click()
  await expect(page.getByText('已发布', { exact: true }).first()).toBeVisible()
  await screenshot(page, 'project-usage-08-rule-release-published.png')
  routing.assertClean()
})

test('生成九类规则设计器最终截图', async ({ browser }) => {
  test.setTimeout(180000)
  const designers = [
    ['/designer/table/101', null, 'project-usage-designer-table.png'],
    ['/designer/tree/102', null, 'project-usage-designer-tree.png'],
    ['/designer/flow/103', null, 'project-usage-designer-flow.png'],
    ['/designer/ruleset/104', null, 'project-usage-designer-ruleset.png'],
    ['/designer/cross/105', null, 'project-usage-designer-cross.png'],
    ['/designer/score/106', null, 'project-usage-designer-score.png'],
    ['/designer/cross-adv/107', null, 'project-usage-designer-cross-adv.png'],
    ['/designer/score-adv/108', null, 'project-usage-designer-score-adv.png']
  ]

  for (const [route, action, fileName] of designers) {
    const context = await browser.newContext()
    const page = await context.newPage()
    const routing = await openPage(page, route)
    if (action) {
      await page.getByRole('button', { name: action, exact: true }).first().click()
    }
    if (route === '/designer/score/106') {
      const numberInputs = page.locator('.el-input-number input')
      await expect(numberInputs).toHaveCount(3)
      expect(
        await numberInputs.evaluateAll((inputs) => inputs.map((input) => input.value))
      ).toEqual(['0', '60', '1.00'])
      for (const input of await numberInputs.all()) {
        expect(await input.evaluate((element) => getComputedStyle(element).color))
          .not.toBe('rgba(0, 0, 0, 0)')
      }
    }
    await screenshot(page, fileName)
    routing.assertClean()
    await context.close()
  }

  const context = await browser.newContext()
  const page = await context.newPage()
  const routing = await openPage(page, '/designer/script/109')
  await expect(page.getByText('livenessScore', { exact: true }).first()).toBeVisible()
  await screenshot(page, 'project-usage-designer-script.png')
  routing.assertClean()
  await context.close()
})
