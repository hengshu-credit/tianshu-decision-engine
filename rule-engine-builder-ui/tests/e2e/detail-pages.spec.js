const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')
const { createDocsApiData } = require('./support/docsFixtures.cjs')

async function openDetailPage(page, path, apiData = createDetailApiData()) {
  const pageErrors = []
  const consoleErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  await page.setViewportSize({ width: 1440, height: 900 })
  const { assertClean } = await installDistRoutes(page, {
    apiData
  })
  await page.goto(`http://tianshu.local/index.html#${path}`)
  await expect(page.getByRole('main')).toBeVisible()
  return { pageErrors, consoleErrors, assertClean }
}

async function expectNoRootOverflow(page) {
  const metrics = await page.evaluate(() => ({
    viewport: window.innerWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth
  }))
  expect(metrics.document).toBeLessThanOrEqual(metrics.viewport + 1)
  expect(metrics.body).toBeLessThanOrEqual(metrics.viewport + 1)
}

async function expectTextSelectable(locator, expectedText) {
  const selection = await locator.evaluate(element => {
    const range = document.createRange()
    range.selectNodeContents(element)
    const current = window.getSelection()
    current.removeAllRanges()
    current.addRange(range)
    return {
      text: current.toString(),
      userSelect: getComputedStyle(element).userSelect,
      inert: Boolean(element.closest('[inert]'))
    }
  })
  expect(selection.text).toBe(expectedText)
  expect(selection.userSelect).not.toBe('none')
  expect(selection.inert).toBe(false)
}

test('规则管理和项目规则的查看入口都按规则定义 ID 打开对应设计器', async ({ page }) => {
  const { assertClean } = await installDistRoutes(page, {
    apiData: createDocsApiData()
  })

  await page.goto('http://tianshu.local/index.html#/rule')
  const ruleRow = page.getByRole('row', { name: /face_identity_rule/ })
  await ruleRow.getByTestId('view-rule').click()
  await expect(page).toHaveURL(/#\/designer\/flow\/101$/)

  await page.goto('http://tianshu.local/index.html#/project/1')
  await page.getByRole('tab', { name: '项目规则' }).click()
  const projectRuleRow = page.getByRole('row', { name: /face_identity_rule/ })
  await projectRuleRow.getByTestId('view-rule').click()
  await expect(page).toHaveURL(/#\/designer\/flow\/101$/)

  assertClean()
})

async function expectPrimaryActions(page, names) {
  for (const name of names) {
    const button = page.getByRole('main').getByRole('button', { name, exact: true }).first()
    await expect(button).toBeVisible()
    await button.scrollIntoViewIfNeeded()
    await expect(button).toBeInViewport()
  }
}

test('项目详情加载项目规则并保持内容可复制', async ({ page }) => {
  const errors = await openDetailPage(page, '/project/1')
  const title = page.getByText('E2E 项目', { exact: true }).first()
  const rulesTab = page.getByRole('tab', { name: '项目规则', exact: true })
  const ruleCode = page.getByText('age_rule', { exact: true }).first()
  await expect(title).toBeVisible()
  await rulesTab.click()
  await expect(rulesTab).toHaveAttribute('aria-selected', 'true')
  await expect(ruleCode).toBeVisible()
  await expectTextSelectable(ruleCode, 'age_rule')
  await expectPrimaryActions(page, ['返回', '添加规则', '新建规则'])
  await expectNoRootOverflow(page)
  expect(errors.pageErrors).toEqual([])
  expect(errors.consoleErrors).toEqual([])
  errors.assertClean()
})

test('规则详情加载输入输出字段且页签可操作', async ({ page }) => {
  const errors = await openDetailPage(page, '/rule/101')
  await expect(page.getByText('age_rule', { exact: true }).first()).toBeVisible()
  const inputTab = page.getByRole('tab', { name: /输入字段/ })
  await inputTab.click()
  await expect(inputTab).toHaveAttribute('aria-selected', 'true')
  const inputCode = page.getByText('age', { exact: true }).first()
  await expect(inputCode).toBeVisible()
  await expectTextSelectable(inputCode, 'age')
  await expectPrimaryActions(page, ['返回'])

  const outputTab = page.getByRole('tab', { name: /输出字段/ })
  await outputTab.click()
  await expect(outputTab).toHaveAttribute('aria-selected', 'true')
  const outputCode = page.getByText('approved', { exact: true }).first()
  await expect(outputCode).toBeVisible()
  await expectTextSelectable(outputCode, 'approved')
  await expectNoRootOverflow(page)
  expect(errors.pageErrors).toEqual([])
  expect(errors.consoleErrors).toEqual([])
  errors.assertClean()
})

test('规则输入和输出的数据对象字段按对象父层和字段子层显示', async ({ page }) => {
  const apiData = createDetailApiData()
  const detail = apiData.get('/api/rule/definition/detail/101')
  const object = {
    id: 20,
    objectCode: 'score_f1_fields',
    objectLabel: '评分字段',
    scriptName: 'score_f1_fields'
  }
  const inputField = {
    id: 2001,
    varId: 301,
    refType: 'DATA_OBJECT',
    fieldName: 'HYBASE_X115',
    fieldLabel: 'HYBASE_X115',
    scriptName: 'score_f1_fields.HYBASE_X115',
    fieldType: 'DOUBLE',
    validationRuleIds: '[]'
  }
  const outputField = {
    id: 2002,
    varId: 302,
    refType: 'DATA_OBJECT',
    fieldName: 'risk_result',
    fieldLabel: '风险结果',
    scriptName: 'score_f1_fields.risk_result',
    fieldType: 'STRING'
  }
  detail.inputFieldsJson.push(inputField)
  detail.outputFieldsJson.push(outputField)
  apiData.set('/api/rule/dataobject/tree/1', [{
    object,
    variables: [{
      id: 301,
      varCode: 'HYBASE_X115',
      varLabel: 'HYBASE_X115',
      scriptName: 'HYBASE_X115',
      varType: 'DOUBLE'
    }, {
      id: 302,
      varCode: 'risk_result',
      varLabel: '风险结果',
      scriptName: 'risk_result',
      varType: 'STRING'
    }]
  }])

  const errors = await openDetailPage(page, '/rule/101', apiData)
  await page.getByRole('tab', { name: /输入字段/ }).click()
  const inputHierarchy = page.getByTestId('input-field-hierarchy-2001')
  await expect(inputHierarchy.locator('.rule-field-hierarchy__object-name')).toHaveText('评分字段')
  await expect(inputHierarchy.locator('.rule-field-hierarchy__object-code')).toHaveText('score_f1_fields')
  await expect(inputHierarchy.locator('.rule-field-hierarchy__field-name')).toHaveText('HYBASE_X115')
  await expect(inputHierarchy.locator('.rule-field-hierarchy__field-path')).toHaveText('score_f1_fields.HYBASE_X115')

  await page.getByRole('tab', { name: /输出字段/ }).click()
  const outputHierarchy = page.getByTestId('output-field-hierarchy-2002')
  await expect(outputHierarchy.locator('.rule-field-hierarchy__object-name')).toHaveText('评分字段')
  await expect(outputHierarchy.locator('.rule-field-hierarchy__field-name')).toHaveText('风险结果')
  await expect(outputHierarchy.locator('.rule-field-hierarchy__field-path')).toHaveText('score_f1_fields.risk_result')
  await expectNoRootOverflow(page)
  expect(errors.pageErrors).toEqual([])
  expect(errors.consoleErrors).toEqual([])
  errors.assertClean()
})

test('开放接口字段选择器按规则设计样式展示类型、编码和名称', async ({ page }) => {
  const errors = await openDetailPage(page, '/rule/101')
  const openApiTab = page.getByRole('tab', { name: /开放接口/ })
  await openApiTab.click()
  await expect(openApiTab).toHaveAttribute('aria-selected', 'true')

  const requestSection = page.locator('.open-api-section').filter({
    has: page.getByText('请求映射', { exact: true })
  })
  const selectedField = requestSection.locator(
    '.field-reference-display--compact'
  )
  await expect(selectedField).toHaveCount(1)
  await expect(selectedField.locator('.field-reference-display__type')).toHaveText('i')
  await expect(selectedField.locator('.field-reference-display__code')).toHaveText('age')
  await expect(selectedField.locator('.field-reference-display__name')).toHaveText('年龄')

  await requestSection.locator('.el-select').first().click()
  const fieldOption = page.locator(
    '.el-select-dropdown:visible .field-reference-display'
  )
  await expect(fieldOption).toHaveCount(1)
  await expect(fieldOption.locator('.field-reference-display__type')).toHaveText('i')
  await expect(fieldOption.locator('.field-reference-display__code')).toHaveText('age')
  await expect(fieldOption.locator('.field-reference-display__name')).toHaveText('年龄')

  const responseSection = page.locator('.open-api-section').filter({
    has: page.getByText('响应字段映射', { exact: true })
  })
  const selectedOutput = responseSection.locator(
    '.field-reference-display--compact'
  )
  await expect(selectedOutput).toHaveCount(1)
  await expect(selectedOutput.locator('.field-reference-display__type')).toHaveText('b')
  await expect(selectedOutput.locator('.field-reference-display__code')).toHaveText('approved')
  await expect(selectedOutput.locator('.field-reference-display__name')).toHaveText('是否通过')

  await expectNoRootOverflow(page)
  expect(errors.pageErrors).toEqual([])
  expect(errors.consoleErrors).toEqual([])
  errors.assertClean()
})

test('名单详情加载记录和变更日志且内容可复制', async ({ page }) => {
  const errors = await openDetailPage(page, '/list/9')
  await expect(page.locator('.detail-meta')).toContainText('mobile_black')
  const mobile = page.getByText('13800138000', { exact: true }).first()
  await expect(mobile).toBeVisible()
  await expectTextSelectable(mobile, '13800138000')

  const logsTab = page.getByRole('tab', { name: '变更日志', exact: true })
  await logsTab.click()
  await expect(logsTab).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByText('INSERT', { exact: true }).first()).toBeVisible()
  await expectPrimaryActions(page, ['返回', '导入并审批', '导出'])
  await expectNoRootOverflow(page)
  expect(errors.pageErrors).toEqual([])
  expect(errors.consoleErrors).toEqual([])
  errors.assertClean()
})

const editableDetails = [
  {
    name: '外数数据源详情',
    path: '/datasource/source/21',
    label: '数据源编码',
    value: 'credit_vendor'
  },
  {
    name: '外数接口详情',
    path: '/datasource/api/22',
    label: '接口编码',
    value: 'credit_query',
    primaryAction: '生成审批草稿'
  },
  {
    name: '数据库详情',
    path: '/database/31',
    label: '数据源编码',
    value: 'risk_mysql'
  },
  {
    name: '分流实验详情',
    path: '/experiment/detail/61',
    label: '实验编码',
    value: 'risk_ab'
  }
]

for (const detail of editableDetails) {
  test(`${detail.name}加载表单且主操作可用`, async ({ page }) => {
    const errors = await openDetailPage(page, detail.path)
    const input = page.locator('.el-form-item:visible')
      .filter({ hasText: detail.label })
      .locator('input:visible')
      .first()
    await expect(input).toBeVisible()
    await expect(input).toHaveValue(detail.value)
    await expectPrimaryActions(page, [
      '返回',
      detail.primaryAction || '保存'
    ])
    await expectNoRootOverflow(page)
    expect(errors.pageErrors).toEqual([])
    expect(errors.consoleErrors).toEqual([])
    errors.assertClean()
  })
}

test('模型详情加载输入输出字段且字段内容可复制', async ({ page }) => {
  const errors = await openDetailPage(page, '/model/41')
  await expect(page.getByText('credit_score', { exact: true }).first()).toBeVisible()
  const inputCode = page.getByText('age', { exact: true }).first()
  await expect(inputCode).toBeVisible()
  await expectTextSelectable(inputCode, 'age')

  const outputTab = page.getByRole('tab', { name: /输出字段/ })
  await outputTab.click()
  await expect(outputTab).toHaveAttribute('aria-selected', 'true')
  const outputCode = page.getByText('riskScore', { exact: true }).first()
  await expect(outputCode).toBeVisible()
  await expectTextSelectable(outputCode, 'riskScore')
  await expectPrimaryActions(page, ['返回'])
  await expectNoRootOverflow(page)
  expect(errors.pageErrors).toEqual([])
  expect(errors.consoleErrors).toEqual([])
  errors.assertClean()
})

test('新建外数接口页面字段与主操作正常显示', async ({ page }) => {
  const errors = await openDetailPage(page, '/datasource/api/new')
  await expect(page.getByText('新建外数 API 接口', { exact: true }).first()).toBeVisible()
  await expectPrimaryActions(page, ['返回', '生成审批草稿'])
  await expect(page.locator('.el-form-item:visible').first()).toBeVisible()
  await expectNoRootOverflow(page)
  expect(errors.pageErrors).toEqual([])
  expect(errors.consoleErrors).toEqual([])
  errors.assertClean()
})

test('规则生命周期和版本历史按稳定 ID 打开对应脚本内容', async ({ page }) => {
  const errors = await openDetailPage(page, '/rule/101')

  await page.getByTestId('view-design').click()
  await page.waitForURL(/sourceType=REVISION.*sourceId=41/)
  await expect(page.getByRole('textbox', { name: /Editor content/ })).toHaveValue("result = 'revision-41'")

  await page.goto('http://tianshu.local/index.html#/rule/101')
  await expect(page.getByRole('main')).toBeVisible()
  await page.getByRole('button', { name: '版本历史', exact: true }).click()
  const versionDialog = page.locator('.el-dialog').filter({ hasText: '版本历史' })
  await expect(versionDialog).toBeVisible()
  await versionDialog.getByRole('button', { name: '查看设计', exact: true }).click()
  await page.waitForURL(/sourceType=VERSION.*sourceId=81/)
  await expect(page.getByRole('textbox', { name: /Editor content/ })).toHaveValue("result = 'version-81'")
  await expect(
    page.getByRole('combobox', { name: '选择规则版本' })
  ).toBeVisible()
  await expect(
    page.getByRole('button', { name: 'QL脚本编辑器', exact: true })
  ).toHaveCount(1)

  expect(errors.pageErrors).toEqual([])
  expect(errors.consoleErrors).toEqual([])
  errors.assertClean()
})
