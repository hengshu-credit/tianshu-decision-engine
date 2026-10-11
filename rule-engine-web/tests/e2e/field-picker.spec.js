const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')

for (const scheme of ['LIGHT', 'DARK']) test(`${scheme} 函数选入后逐个配置参数，LIST 参数仅提供兼容字段`, async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.addInitScript(colorScheme => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({ schemaVersion: 1, colorScheme, accentPreset: 'THEME_BLUE', sidebarTheme: 'DARK', contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false })), scheme)
  const data = createDesignerApiData()
  data.get('/api/rule/variable/project/1').push({ id: 5, varCode: 'contacts', varLabel: '联系人列表', varType: 'LIST', varSource: 'INPUT' })
  data.set('/api/rule/function/project/1/all', [{ id: 99, funcCode: 'sum_values', funcName: '列表求和', returnType: 'NUMBER', version: 3, paramsJson: '[{"name":"values","type":"LIST"}]' }])
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/index.html#/designer/table/101')
  await expect(page.locator('html')).toHaveAttribute('data-theme', scheme.toLowerCase())
  await page.getByRole('button', { name: '添加行', exact: true }).click()
  await page.getByPlaceholder('选择左操作数...').click()
  const panel = page.locator('.fp-panel:visible')
  await panel.getByRole('button', { name: /^函数 \/ 方法/ }).click()
  await expect(panel).toContainText('最新·v3')
  await panel.locator('.fp-row').filter({ hasText: '列表求和' }).dblclick()
  await expect(page).toHaveURL(/\/designer\/table\/101$/)
  await page.getByRole('button', { name: '配置参数 values', exact: true }).click()
  await expect(panel.locator('.fp-row')).toHaveCount(1)
  await expect(panel.locator('.fp-row')).toContainText('联系人列表')
  await expect(panel.getByRole('button', { name: /^变量/ })).toContainText('1')
  await panel.locator('.fp-row').dblclick()
  await expect(panel).toHaveCount(0)
  await expect(page.getByRole('button', { name: '配置参数 values', exact: true })).toContainText('联系人列表')
  await page.getByPlaceholder('选择左操作数...').click()
  await expect(panel).toHaveCount(1)
  await panel.getByRole('button', { name: /^最近使用/ }).click()
  await expect(page.locator('.var-picker-popover:visible')).toHaveCSS('opacity', '1')
  const contrast = await panel.locator('.fp-categories button.active').evaluate(element => {
    const style = getComputedStyle(element)
    const parse = color => color.match(/[\d.]+/g).map(Number)
    const luminance = color => color.slice(0, 3).map(value => value / 255).map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4).reduce((total, value, index) => total + value * [.2126, .7152, .0722][index], 0)
    const surface = parse(getComputedStyle(element.closest('.fp-panel')).backgroundColor)
    const tint = parse(style.backgroundColor), alpha = tint[3] ?? 1
    const background = luminance(tint.slice(0, 3).map((value, index) => value * alpha + surface[index] * (1 - alpha)))
    const foreground = luminance(parse(style.color))
    return (Math.max(foreground, background) + .05) / (Math.min(foreground, background) + .05)
  })
  expect(contrast).toBeGreaterThanOrEqual(4.5)
  await page.screenshot({ path: testInfo.outputPath(`field-picker-${scheme.toLowerCase()}.png`) })
  const overflow = await panel.locator('.fp-categories').evaluate(element => element.scrollWidth > element.clientWidth)
  expect(overflow).toBe(false)
  assertClean()
})

test('九千字段分页虚拟化、搜索保留、已选内容和最近使用', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  const data = createDesignerApiData()
  data.set('/api/rule/dataobject/tree/1', [{ object: { id: 10, objectCode: 'CreditReport', objectLabel: '征信报告' }, variables: [
    { id: 50000, varCode: 'items', varLabel: '明细', varType: 'LIST' },
    ...Array.from({ length: 9000 }, (_, i) => ({ id: 50001 + i, parentFieldId: 50000, varCode: `items.score${i}`, varLabel: `评分字段${i}`, varType: 'NUMBER' })),
  ] }])
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/index.html#/designer/table/101')
  await page.getByRole('button', { name: '添加行', exact: true }).click()
  const field = page.getByPlaceholder('选择左操作数...')
  await field.click()
  const panel = page.locator('.fp-panel:visible')
  await panel.getByRole('textbox', { name: '搜索字段名称或编码' }).fill('score8999')
  await panel.getByRole('button', { name: /^变量/ }).click()
  await expect(panel.getByRole('textbox', { name: '搜索字段名称或编码' })).toHaveValue('score8999')
  await panel.getByRole('button', { name: /^数据对象/ }).click()
  const selected = panel.locator('.fp-row').filter({ hasText: '评分字段8999' })
  await selected.click()
  await expect(field).toHaveValue('')
  await selected.dblclick()
  await expect(field).toHaveValue(/CreditReport.items.score8999/)
  await field.click()
  await expect(panel.getByRole('textbox', { name: '搜索字段名称或编码' })).toHaveValue('score8999')
  await panel.getByRole('textbox', { name: '搜索字段名称或编码' }).fill('')
  await panel.getByRole('button', { name: /^最近使用/ }).click()
  await expect(panel.locator('.fp-row')).toHaveCount(1)
  await expect(panel.locator('.fp-row')).toContainText('score8999')
  await panel.getByRole('button', { name: /^已选内容/ }).click()
  await expect(panel.locator('.fp-row')).toContainText('score8999')
  await panel.getByRole('button', { name: /^数据对象/ }).click()
  await panel.getByRole('combobox', { name: '每页字段数' }).selectOption('500')
  await expect(panel.locator('.fp-row').count()).resolves.toBeLessThan(35)
  const header = await panel.locator('.fp-header').boundingBox()
  const title = await panel.locator('.fp-header strong').boundingBox()
  await page.mouse.move(title.x + title.width / 2, title.y + title.height / 2)
  await page.mouse.down(); await page.mouse.move(title.x - 80, title.y + 60); await page.mouse.up()
  const moved = await panel.boundingBox()
  expect(moved.x).toBeLessThan(header.x)
  const rowHeight = await panel.locator('.fp-row').first().evaluate(element => element.getBoundingClientRect().height)
  expect(rowHeight).toBe(36)
  await expect(panel.locator('.fp-type small')).toHaveCount(0)
  await panel.getByRole('button', { name: '关闭字段选择器', exact: true }).click()
  await field.click()
  await expect(panel.locator('.fp-row--selected')).toContainText('score8999')
  await expect(panel.getByRole('spinbutton', { name: '页码' })).toHaveValue('19')
  await expect(panel.locator('.fp-sticky')).toContainText('征信报告')
  await expect(panel.locator('.fp-sticky')).toContainText('明细')
  await panel.getByRole('button', { name: '关闭字段选择器', exact: true }).click()
  await expect(panel).toHaveCount(0)
  await page.getByPlaceholder('选择目标字段').click()
  await expect(panel.getByRole('combobox', { name: '每页字段数' })).toHaveValue('500')
  await page.reload()
  await page.getByRole('button', { name: '添加行', exact: true }).click()
  await page.getByPlaceholder('选择左操作数...').click()
  await expect(panel.getByRole('combobox', { name: '每页字段数' })).toHaveValue('500')
  assertClean()
})

test('决策表字段选择器可加载并选择普通变量和对象字段', async ({ page }) => {
  const pageErrors = []
  const consoleErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })

  const designerData = createDesignerApiData()
  designerData.set('/api/rule/datasource/api-config/list', {
    records: [{ id: 31, apiCode: 'credit_api', apiName: '征信接口', status: 1 }],
    total: 1,
  })
  const { requests, assertClean } = await installDistRoutes(page, {
    apiData: designerData
  })
  await page.goto('http://tianshu.local/index.html#/designer/table/101')

  await expect(page.locator('.rule-designer-info__fields')).toContainText(/已加载 \d+ 个可选引用/)
  await page.getByRole('button', { name: '添加行' }).click()

  const targetField = page.getByPlaceholder('选择目标字段')
  await expect(targetField).toBeVisible()
  await targetField.click()

  const targetReference = targetField.locator('xpath=ancestor::div[contains(@class, "vp-reference")]')
  const popoverId = await targetField.getAttribute('aria-describedby')
  expect(popoverId).toBeTruthy()
  const popover = page.locator(`[id="${popoverId}"]`)
  await expect(popover).toBeVisible()
  await popover.locator('.fp-categories button').filter({ hasText: '变量' }).click()
  await popover.locator('.fp-row').filter({ hasText: 'age' }).dblclick()
  await expect(targetField).toHaveValue(/age/)
  await expect(popover).toBeHidden()
  const typeToValueGap = await targetReference.evaluate(reference => {
    const type = reference.querySelector('.vp-operand-kind')
    const input = reference.querySelector('input')
    const wrapper = reference.querySelector('.el-input__wrapper')
    const typeRect = type.getBoundingClientRect()
    const inputRect = input.getBoundingClientRect()
    const inputStyle = getComputedStyle(input)
    const wrapperStyle = getComputedStyle(wrapper)
    return {
      gap: inputRect.left + parseFloat(inputStyle.paddingLeft || '0') - typeRect.right,
      paddingLeft: parseFloat(wrapperStyle.paddingLeft || '0'),
      paddingRight: parseFloat(wrapperStyle.paddingRight || '0'),
      suffixIcons: reference.querySelectorAll('.el-input__suffix .el-icon').length
    }
  })
  expect(typeToValueGap.gap).toBeGreaterThanOrEqual(0)
  expect(typeToValueGap.gap).toBeLessThanOrEqual(8)
  expect(typeToValueGap).toMatchObject({
    paddingLeft: 4,
    paddingRight: 4,
    suffixIcons: 1
  })

  const operatorWrapper = page.locator('.cg-field--op .el-select__wrapper').first()
  await expect(operatorWrapper).toBeVisible()
  const operatorSpacing = await operatorWrapper.evaluate(element => {
    const style = getComputedStyle(element)
    return {
      gap: parseFloat(style.gap || '0'),
      paddingLeft: parseFloat(style.paddingLeft || '0'),
      paddingRight: parseFloat(style.paddingRight || '0')
    }
  })
  expect(operatorSpacing).toEqual({
    gap: 4,
    paddingLeft: 8,
    paddingRight: 8
  })

  const leftField = page.getByPlaceholder('选择左操作数...')
  await leftField.click()
  const leftPopoverId = await leftField.getAttribute('aria-describedby')
  expect(leftPopoverId).toBeTruthy()
  const leftPopover = page.locator(`[id="${leftPopoverId}"]`)
  await expect(leftPopover).toBeVisible()
  await expect(leftPopover.getByRole('button', { name: '输入阈值', exact: true })).toBeVisible()
  const standaloneCategory = leftPopover
    .locator('.fp-categories button')
    .filter({ hasText: '变量' })
  await standaloneCategory.click()
  await expect(standaloneCategory).toHaveClass(/active/)
  const incomeOption = leftPopover.locator('.fp-row').filter({
    hasText: 'income',
  })
  await expect(incomeOption).toBeVisible()
  await incomeOption.dblclick()
  await expect(leftField).toHaveValue(/income/)
  const compactFieldMetrics = await leftField.evaluate(input => ({
    clientWidth: input.clientWidth,
    scrollWidth: input.scrollWidth
  }))
  expect(compactFieldMetrics.scrollWidth).toBeLessThanOrEqual(
    compactFieldMetrics.clientWidth
  )

  await leftField.click()
  await expect(leftPopover).toBeVisible()
  const externalCategory = leftPopover.locator('.fp-categories button').filter({ hasText: '外数 API' })
  await externalCategory.click()
  const externalGroup = leftPopover.locator('.fp-row').filter({ hasText: 'credit_api' })
  await expect(externalGroup).toBeVisible()
  await externalGroup.click()
  await leftPopover.locator('.fp-row').filter({ hasText: '完整调用结果' }).click()
  await leftPopover.locator('.fp-row').filter({ hasText: 'credit_api.response' }).click()
  await leftPopover.locator('.fp-row').filter({ hasText: 'HTTP 状态码' }).dblclick()
  await expect(leftField).toHaveValue(/response\.httpStatus/)

  await targetField.click()
  await expect(popover).toBeVisible()
  const objectCategory = popover
    .locator('.fp-categories button')
    .filter({ hasText: '数据对象' })
  await objectCategory.click()
  await expect(objectCategory).toHaveClass(/active/)
  const objectOption = popover.locator('.fp-row').filter({
    hasText: 'TaxRequest',
  })
  await expect(objectOption).toBeVisible()
  await objectOption.click()
  const amountOption = popover.locator('.fp-row').filter({
    hasText: 'amount',
  })
  await expect(amountOption).toBeVisible()
  await amountOption.dblclick()
  await expect(targetField).toHaveValue(/TaxRequest\.amount/)
  await expect(popover).toBeHidden()

  await targetField.click()
  await expect(popover).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(popover).toBeHidden()

  const requestedPaths = requests.map(request => new URL(request.url).pathname)
  expect(requestedPaths).toEqual(expect.arrayContaining([
    '/api/rule/definition/101',
    '/api/rule/definition/101/revisions',
    '/api/rule/variable/project/1',
    '/api/rule/dataobject/tree/1',
    '/api/rule/function/project/1/all',
    '/api/rule/model/project/1/all',
    '/api/rule/list/library',
    '/api/rule/datasource/api-config/list'
  ]))
  expect(pageErrors).toEqual([])
  expect(consoleErrors).toEqual([])
  assertClean()
})

test('规则集阈值在独立面板内编辑，双栏页面保留完整摘要', async ({ page }) => {
  await page.setViewportSize({ width: 1850, height: 1000 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createDesignerApiData()
  })
  await page.goto('http://tianshu.local/index.html#/designer/ruleset/104')

  await page.getByRole('button', { name: '添加规则' }).click()
  const leftField = page.getByPlaceholder('选择左操作数...')
  await leftField.click()
  const leftPopoverId = await leftField.getAttribute('aria-describedby')
  const leftPopover = page.locator(`[id="${leftPopoverId}"]`)
  await leftPopover.locator('.fp-categories button').filter({ hasText: '数据对象' }).click()
  await leftPopover.locator('.fp-row').filter({ hasText: 'TaxRequest' }).click()
  await leftPopover.locator('.fp-row').filter({ hasText: 'amount' }).dblclick()

  const rightField = page.getByPlaceholder('选择右操作数...')
  await rightField.click()
  const rightPopoverId = await rightField.getAttribute('aria-describedby')
  const rightPopover = page.locator(`[id="${rightPopoverId}"]`)
  await rightPopover.getByRole('button', { name: '输入阈值', exact: true }).click()

  const thresholdInput = rightPopover.getByRole('textbox', { name: /^阈值内容/ })
  await thresholdInput.fill('1234567890')
  const metrics = await rightPopover.locator('.monaco-editor-container').evaluate(input => {
    return {
      width: input.getBoundingClientRect().width,
      clientWidth: input.clientWidth,
      scrollWidth: input.scrollWidth,
    }
  })

  expect(metrics.width).toBeGreaterThanOrEqual(400)
  expect(metrics.scrollWidth).toBeLessThanOrEqual(metrics.clientWidth)
  await rightPopover.getByRole('button', { name: '应用阈值', exact: true }).click()
  await expect(rightField).toHaveValue('1234567890')
  assertClean()
})
