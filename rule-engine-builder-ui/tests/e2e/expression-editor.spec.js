const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')

test('表达式画布支持删除空项、修改运算符及连续键盘缩进反缩进', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1440, height: 900 })
  const { assertClean } = await installDistRoutes(page, { apiData: createDesignerApiData() })
  await page.goto('http://tianshu.local/index.html#/designer/table/101')
  await page.getByRole('button', { name: '添加行', exact: true }).click()
  await page.getByRole('button', { name: '配置组合表达式' }).first().click()
  const editor = page.locator('.expression-editor')
  const main = page.getByRole('main')
  await main.getByText('age', { exact: true }).click()
  await editor.getByRole('button', { name: '展开脚本面板', exact: true }).click()
  const script = editor.locator('.sp-editor')
  const selected = editor.locator('.canvas-node--selected')
  await selected.click()
  await selected.press('+')
  await expect(editor.locator('.canvas-node--empty')).toHaveCount(1)
  await expect(selected).toBeFocused()
  await selected.press('|')
  await selected.press('|')
  await expect(editor.locator('.canvas-edge-operator')).toHaveCount(1)
  await expect(editor.locator('.canvas-edge-operator')).toHaveText('||')
  await selected.press('-')
  await main.getByText('income', { exact: true }).click()
  await expect(script).toHaveValue('(age - income)')
  await selected.click()
  await selected.press('*')
  await selected.press('Tab')
  await expect(editor.locator('.canvas-edge-operator')).toHaveCount(2)
  await expect(editor.locator('.canvas-children .canvas-children')).toHaveCount(1)
  await expect(selected).toBeFocused()
  await selected.press('Shift+Tab')
  await expect(editor.locator('.canvas-children .canvas-children')).toHaveCount(0)
  await selected.press('Backspace')
  await expect(editor.locator('.canvas-node--empty')).toHaveCount(0)
  await expect(script).toHaveValue('(age - income)')
  await editor.getByRole('button', { name: '修改运算符 -', exact: true }).click()
  await page.getByRole('menuitem', { name: '+', exact: true }).click()
  await expect(script).toHaveValue('(age + income)')
  await editor.getByRole('button', { name: '修改运算符 +', exact: true }).click()
  await page.getByRole('menuitem', { name: '删除运算符及后一项', exact: true }).click()
  await expect(script).toHaveValue('age')
  await main.getByRole('button', { name: '撤销', exact: true }).click()
  await expect(script).toHaveValue('(age + income)')
  await main.getByRole('button', { name: '重做', exact: true }).click()
  await expect(script).toHaveValue('age')
  await selected.click()
  await selected.press('+')
  await editor.getByRole('button', { name: '删除节点', exact: true }).click()
  await expect(script).toHaveValue('age')

  // Text input keeps its ordinary operator/delete/Tab behavior; Esc returns to structural shortcuts.
  await main.getByRole('button', { name: /手动输入/ }).click()
  await main.getByRole('button', { name: /输入阈值/ }).click()
  const literalInput = editor.getByPlaceholder('请输入阈值')
  await literalInput.fill('a+b|c&d')
  await literalInput.press('End')
  await literalInput.press('Backspace')
  await expect(literalInput).toHaveValue('a+b|c&')
  await expect(editor.locator('.canvas-edge-operator')).toHaveCount(0)
  await literalInput.press('Escape')
  await expect(selected).toBeFocused()
  await selected.press('/')
  await expect(editor.locator('.canvas-edge-operator')).toHaveText('/')
  expect(pageErrors).toEqual([])
  assertClean()
})

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

  const preview = main.locator('.expression-editor__preview')
  const script = preview.locator('.sp-editor')
  await expect(main.locator('.expression-workspace .expression-formula-preview')).toHaveCount(0)
  await expect(preview.locator('.expression-formula-preview__business')).toContainText('年龄 age')
  await preview.getByRole('button', { name: '展开脚本面板', exact: true }).click()
  await expect(script).toBeVisible()
  await expect(script).toHaveValue('age')
  await expect(script).toHaveAttribute('readonly', '')
  await expect(preview).toContainText('根据当前表达式实时生成')
  await expect(preview.locator('.sp-slide-enter-active')).toHaveCount(0)
  for (const size of [{ width: 1440, height: 900 }, { width: 1280, height: 720 }]) {
    await page.setViewportSize(size)
    const bodyBox = await main.locator('.expression-editor__body').boundingBox()
    const previewBox = await preview.boundingBox()
    const formulaBox = await preview.locator('.expression-formula-preview__business').boundingBox()
    const scriptBox = await preview.locator('.script-panel').boundingBox()
    const footerBox = await preview.locator('.sp-footer').boundingBox()
    expect(bodyBox.height).toBeGreaterThan(100)
    expect(bodyBox.y + bodyBox.height).toBeLessThanOrEqual(previewBox.y + 1)
    expect(formulaBox.y + formulaBox.height).toBeLessThanOrEqual(scriptBox.y)
    expect(previewBox.y + previewBox.height).toBeLessThanOrEqual(size.height)
    expect(footerBox.y + footerBox.height).toBeLessThanOrEqual(previewBox.y + previewBox.height)
  }
  await page.setViewportSize({ width: 1440, height: 900 })
  await main.getByText('income', { exact: true }).click()
  await expect(preview.locator('.expression-formula-preview__business')).toContainText('收入 income')
  await expect(script).toHaveValue('income')
  await main.getByRole('button', { name: '撤销', exact: true }).click()
  await expect(script).toHaveValue('age')
  await preview.getByRole('button', { name: '编辑脚本', exact: true }).click()
  const scriptEditor = preview.getByRole('textbox', { name: /Editor content/ })
  await scriptEditor.click()
  await scriptEditor.press('ControlOrMeta+A')
  await scriptEditor.fill('age + 1')
  await preview.getByRole('button', { name: '确认脚本', exact: true }).click()
  await expect(script).toBeVisible()
  await expect(script).toHaveValue('(age + 1)')
  await expect(preview.locator('.expression-formula-preview__business')).toContainText('年龄 age')
  await expect(preview.locator('.expression-formula-preview__business')).toContainText('1')
  await main.getByRole('button', { name: '撤销', exact: true }).click()
  await expect(script).toHaveValue('age')

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

  await page.getByRole('button', { name: '表达式 · 决策表 · 左操作数', exact: true }).click()
  await page.getByRole('main').getByRole('button', { name: '返回', exact: true }).click()
  await expect(page).toHaveURL(/#\/designer\/table\/101$/)
  await expect(page.getByText('共 1 条规则', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  const saveDialog = page.getByRole('dialog', { name: '保存草稿', exact: true })
  await saveDialog.getByText('覆盖当前草稿', { exact: true }).click()
  await saveDialog.locator('[data-action="confirm-save"]').click()
  await expect(page.getByRole('status')).toContainText('草稿已保存')

  await page.getByRole('button', { name: '表达式 · 规则集 · 左操作数', exact: true }).click()
  await page.getByRole('main').getByRole('button', { name: '返回', exact: true }).click()
  await expect(page).toHaveURL(/#\/designer\/ruleset\/104$/)
  await expect(page.getByText('共 1 条规则', { exact: true })).toBeVisible()
  assertClean()
})
