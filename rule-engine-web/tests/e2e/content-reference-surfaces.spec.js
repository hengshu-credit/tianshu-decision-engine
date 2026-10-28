const { test, expect } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')
const { createOperationsApiData } = require('./support/operationsFixtures.cjs')
const { insertScriptReference } = require('./support/scriptPicker.cjs')

async function checkBlocks(root) {
  await expect(root).toBeVisible()
  const parts = await root.evaluate(element => [...element.children].filter(child => child.dataset.contentPart).map(child => ({ key: child.dataset.contentPart, x: child.getBoundingClientRect().x, right: child.getBoundingClientRect().right, border: getComputedStyle(child).borderRadius, margin: getComputedStyle(child).margin })))
  expect(parts.slice(0, 2).map(part => part.key)).toEqual(['source', 'type'])
  for (let i = 1; i < parts.length; i++) expect(Math.abs(parts[i].x - parts[i - 1].right)).toBeLessThanOrEqual(0.5)
  expect(parts.every(part => part.border === '0px' && part.margin === '0px')).toBe(true)
}
async function pick(page, input, code) {
  await input.click()
  const panel = page.locator('.fp-panel:visible').last()
  await panel.getByRole('textbox', { name: '搜索内容名称或编码' }).fill(code)
  await panel.getByRole('gridcell', { name: code, exact: true }).dblclick()
}
const variable = { id: 1, varCode: 'clientCode', varLabel: '客户编码', varType: 'STRING', varSource: 'INPUT', version: 3 }

async function openReferenceScript(page, script = 'age + 1', configure = () => {}) {
  const data = createDesignerApiData()
  configure(data)
  const revision = { id: 2109, definitionId: 109, revisionNo: 1, state: 'DRAFT', lockVersion: 0,
    modelJson: JSON.stringify({ script, scriptVarRefs: [{ refCode: 'age', varId: 1, refType: 'VARIABLE' }] }) }
  data.set('/api/rule/definition/109/revisions', [revision])
  data.set('/api/rule/definition/109/revisions/2109', revision)
  const harness = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/#/designer/script/109')
  await expect(page.locator('.view-lines .content-ref__code').first()).toBeVisible()
  await expect(page.locator('.view-lines .content-ref__version').filter({ hasText: /v1/ }).first()).toBeVisible()
  return harness
}

for (const embedded of [true, false]) test(`${embedded ? '常驻' : '弹出'}分类栏可拖拽，分类只计一级内容`, async ({ page }) => {
  const configure = data => data.get('/api/rule/dataobject/tree/1')[0].variables.push({ id: 51, varCode: 'extra', varLabel: '额外子字段', varType: 'NUMBER' })
  let harness
  if (embedded) harness = await openReferenceScript(page, 'age + 1', configure)
  else {
    const data = createDesignerApiData(); configure(data)
    harness = await installDistRoutes(page, { apiData: data })
    await page.goto('http://tianshu.local/#/designer/table/101')
    await page.getByRole('button', { name: '添加行', exact: true }).click()
    await page.getByPlaceholder('选择左操作数...').click()
  }
  const picker = page.locator('.fp-panel:visible'), nav = picker.getByRole('navigation', { name: '内容分类' })
  const category = nav.locator('.fp-category-group > button').filter({ hasText: '数据对象' })
  await expect(category.locator('small')).toHaveText('1')
  await category.click()
  await expect(nav.locator('.fp-subcategory')).toHaveText(['税务请求'])
  await expect(nav.locator('.fp-subcategory small')).toHaveCount(0)
  await expect(nav).not.toContainText('额外子字段')
  const before = (await nav.boundingBox()).width
  const handle = await picker.getByRole('separator', { name: '调整分类栏宽度', exact: true }).boundingBox()
  await page.mouse.move(handle.x + handle.width / 2, handle.y + 50)
  await page.mouse.down(); await page.mouse.move(handle.x + handle.width / 2 + 60, handle.y + 50, { steps: 8 }); await page.mouse.up()
  await expect.poll(async () => (await nav.boundingBox()).width).toBeGreaterThanOrEqual(before + 50)
  const resized = (await nav.boundingBox()).width
  if (embedded) await page.reload()
  else {
    await picker.getByRole('button', { name: '关闭内容选择器' }).click()
    await page.getByPlaceholder('选择左操作数...').click()
  }
  await expect.poll(async () => (await page.locator('.fp-panel:visible').getByRole('navigation', { name: '内容分类' }).boundingBox()).width).toBeCloseTo(resized, 0)
  harness.assertClean()
})

test('引用标签不消耗光标步数，选中修改仅作用于真实编码', async ({ page }) => {
  const harness = await openReferenceScript(page)
  const editor = page.getByRole('textbox', { name: /Editor content/ })
  await editor.press('Control+Home')
  await editor.press('ArrowRight')
  await editor.press('Shift+ArrowRight')
  await page.keyboard.insertText('X')
  await expect(editor).toHaveValue('aXe + 1')
  await editor.press('Control+z')
  await expect(editor).toHaveValue('age + 1')
  harness.assertClean()
})

for (const embedded of [true, false]) test(`${embedded ? '常驻' : '弹出'}明细为无色块四列表格，表头联动调宽且始终铺满`, async ({ page }) => {
  let harness
  if (embedded) harness = await openReferenceScript(page)
  else {
    harness = await installDistRoutes(page, { apiData: createDesignerApiData() })
    await page.goto('http://tianshu.local/#/designer/table/101')
    await page.getByRole('button', { name: '添加行', exact: true }).click()
    await page.getByPlaceholder('选择左操作数...').click()
  }
  const picker = page.locator('.fp-panel:visible')
  await expect(picker.getByRole('columnheader')).toHaveText(['数据类型', '名称', '编码', '版本'])
  const backgrounds = await picker.locator('.fp-cell-label').evaluateAll(elements => elements.map(element => ({ color: getComputedStyle(element).backgroundColor, image: getComputedStyle(element).backgroundImage })))
  expect(backgrounds.length).toBeGreaterThan(0)
  expect(backgrounds.every(style => style.color === 'rgba(0, 0, 0, 0)' && style.image === 'none')).toBe(true)
  const measure = () => picker.evaluate(element => ({
    viewport: element.querySelector('.fp-viewport').clientWidth,
    header: element.querySelector('.fp-columns').getBoundingClientRect().width,
    row: element.querySelector('.fp-row').getBoundingClientRect().width,
    columns: [...element.querySelectorAll('[data-column-key]')].map(cell => ({ key: cell.dataset.columnKey, width: cell.getBoundingClientRect().width })),
  }))
  await expect.poll(async () => Math.abs((await measure()).header - (await measure()).viewport)).toBeLessThan(1)
  const initial = await measure()
  const header = picker.locator('[data-column-key="code"]'), before = (await header.boundingBox()).width
  const grip = await picker.getByRole('separator', { name: '调整编码列宽', exact: true }).boundingBox()
  await page.mouse.move(grip.x + grip.width / 2, grip.y + grip.height / 2)
  await page.mouse.down(); await page.mouse.move(grip.x + grip.width / 2 + 16, grip.y + grip.height / 2, { steps: 8 }); await page.mouse.up()
  await expect.poll(async () => (await header.boundingBox()).width).toBeCloseTo(before + 16, 0)
  const resized = await measure()
  expect(resized.header).toBeCloseTo(initial.header, 0)
  expect(resized.row).toBeCloseTo(resized.header, 0)
  expect(resized.columns.find(column => column.key === 'name').width).toBeLessThan(initial.columns.find(column => column.key === 'name').width)
  if (embedded) await page.reload()
  else { await picker.getByRole('button', { name: '关闭内容选择器' }).click(); await page.getByPlaceholder('选择左操作数...').click() }
  await expect.poll(async () => (await page.locator('.fp-panel:visible [data-column-key="code"]').boundingBox()).width).toBeCloseTo(before + 16, 0)
  const sidebarGrip = picker.getByRole('separator', { name: '调整分类栏宽度', exact: true })
  await sidebarGrip.press('ArrowLeft')
  await expect.poll(async () => (await measure()).header).toBeGreaterThan(initial.header + 14)
  const expanded = await measure()
  expect(expanded.header).toBeCloseTo(expanded.viewport, 0)
  expect(expanded.row).toBeCloseTo(expanded.viewport, 0)
  await sidebarGrip.press('ArrowRight')
  await expect.poll(async () => (await measure()).header).toBeCloseTo(initial.header, 0)
  await picker.getByRole('separator', { name: '调整编码列宽', exact: true }).dblclick()
  await expect.poll(async () => (await header.boundingBox()).width).toBeCloseTo(before, 0)
  harness.assertClean()
})

test('两万引用目录下连续输入不丢字，引用展示切换保持源码与撤销', async ({ page }, testInfo) => {
  test.setTimeout(90000)
  const script = 'result = ' + Array(32).fill('age').join(' + ') + ';'
  const harness = await openReferenceScript(page, script, data => {
    data.get('/api/rule/variable/project/1').push(...Array.from({ length: 20000 }, (_, index) => ({
      id: index + 1000, varCode: 'field' + index, varLabel: '性能字段' + index, varType: 'NUMBER', varSource: 'INPUT',
    })))
  })
  const editor = page.getByRole('textbox', { name: /Editor content/ })
  await editor.press('Control+End')
  // Monaco 的无障碍 textarea 仅包含光标附近文本；校验完整脚本用只读模型值。
  const readScript = () => page.evaluate(() => window.monaco.editor.getModels().find(model => model.getLanguageId() === 'ql').getValue())
  await page.evaluate(() => {
    window.typingFrames = []
    document.querySelector('.monaco-editor textarea').addEventListener('keydown', event => {
      if (event.key.length !== 1 || event.ctrlKey) return
      const start = performance.now()
      requestAnimationFrame(() => window.typingFrames.push(performance.now() - start))
    }, true)
  })
  const typed = ' // typing_latency_0123456789'
  const started = Date.now()
  await editor.pressSequentially(typed, { delay: 20 })
  await expect.poll(readScript).toBe(script + typed)
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
  const frames = await page.evaluate(() => window.typingFrames)
  expect(frames.length).toBe(typed.length)
  const sorted = [...frames].sort((a, b) => a - b)
  await testInfo.attach('typing-performance', { contentType: 'application/json', body: JSON.stringify({
    catalogSize: 20000, keystrokes: typed.length, elapsedMs: Date.now() - started,
    frameMeanMs: frames.reduce((sum, value) => sum + value, 0) / frames.length, frameP95Ms: sorted[Math.ceil(sorted.length * 0.95) - 1], frameMaxMs: sorted.at(-1),
  }) })
  await editor.press('Control+z')
  await expect.poll(readScript).not.toBe(script + typed)
  expect((await readScript()).startsWith(script)).toBe(true)
  await editor.press('Control+y')
  await expect.poll(readScript).toBe(script + typed)
  const toggle = page.locator('.el-switch').filter({ has: page.getByRole('switch', { name: '显示引用名称与版本' }) })
  await toggle.click()
  await expect(page.locator('.view-lines .content-ref__code')).toHaveCount(0)
  await toggle.click()
  await expect(page.locator('.view-lines .content-ref__code').first()).toBeVisible()
  await expect.poll(readScript).toBe(script + typed)
  harness.assertClean()
})

test('尺寸通知延迟时旧列宽偏好仍由CSS铺满，最后一列不留下空白', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('tianshu-field-picker-column-widths', JSON.stringify({ type: 64, name: 104, code: 148, version: 88 }))
    const Observer = window.ResizeObserver
    window.ResizeObserver = class extends Observer {
      constructor(callback) { super((entries, observer) => {
        const immediate = entries.filter(entry => !entry.target.matches('.fp-panel, .fp-viewport'))
        if (immediate.length) callback(immediate, observer)
      }) }
    }
  })
  await page.setViewportSize({ width: 1920, height: 1000 })
  const harness = await openReferenceScript(page)
  const picker = page.locator('.fp-panel:visible')
  const gap = () => picker.evaluate(panel => {
    const viewport = panel.querySelector('.fp-viewport'), header = panel.querySelector('.fp-columns')
    const lastCell = header.lastElementChild.getBoundingClientRect()
    return viewport.getBoundingClientRect().left + viewport.clientWidth - lastCell.right - parseFloat(getComputedStyle(header).paddingRight)
  })
  const divider = await page.getByRole('separator', { name: '调整字段列表宽度', exact: true }).boundingBox()
  await page.mouse.move(divider.x + 4, divider.y + 50)
  await page.mouse.down(); await page.mouse.move(divider.x + 124, divider.y + 50, { steps: 8 }); await page.mouse.up()
  await expect.poll(gap).toBeLessThan(1)
  expect(await gap()).toBeGreaterThan(-1)
  await picker.getByRole('separator', { name: '调整编码列宽', exact: true }).press('ArrowRight')
  await expect.poll(gap).toBeLessThan(1)
  const category = picker.getByRole('separator', { name: '调整分类栏宽度', exact: true })
  await category.press('ArrowLeft')
  await expect.poll(gap).toBeLessThan(1)
  expect(await gap()).toBeGreaterThan(-1)
  harness.assertClean()
})

for (const localEditingAllowed of [true, false]) test(`全局历史版本空目录${localEditingAllowed ? '提供当前字段入口且清除空的已选过滤' : '不允许越过本地绑定限制'}`, async ({ page }) => {
  const data = createDesignerApiData()
  const script = 'return resourceVersionValue("VARIABLE", "548", "BUSINESS_VERSION:948", "");'
  const revision = { id: 2109, definitionId: 109, revisionNo: 1, state: 'PUBLISHED', artifactId: 10, lockVersion: 0, modelJson: JSON.stringify({ script, scriptVarRefs: [] }) }
  data.set('/api/rule/definition/109', { ...data.get('/api/rule/definition/109'), scope: 'GLOBAL', projectId: null })
  data.set('/api/rule/definition/109/revisions', [revision])
  data.set('/api/rule/definition/109/revisions/2109', revision)
  data.set('/api/rule/definition/109/reference-catalog', ({ url }) => ({ status: 'AVAILABLE', referenceNamespace: 'ARTIFACT_SOURCE', localEditingAllowed,
    executionIdentity: { definitionId: '109', sourceType: url.searchParams.get('sourceType'), sourceId: url.searchParams.get('sourceId') },
    variables: [], objectTree: [], functions: [], models: [], rules: [], apis: [], lists: [], versionReferences: [],
  }))
  data.set('/api/rule/variable/project/0', [{ id: '7', varCode: 'Global_Age', varLabel: '全局年龄', varType: 'NUMBER', varSource: 'INPUT' }])
  data.set('/api/rule/variable/picker-versions/7', [])
  data.set('/api/rule/dataobject/tree/0', [])
  data.set('/api/rule/model/project/0/all', [])
  data.set('/api/rule/function/project/0/all', [])
  data.set('/api/rule/datasource/api-config/available', [])
  data.set('/api/rule/list/library', { records: [], total: 0 })
  const harness = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/#/designer/script/109')
  const picker = page.locator('.fp-panel:visible')
  await expect(picker.getByText('此历史版本未保存可选字段', { exact: true })).toBeVisible()
  expect(harness.requests.some(request => new URL(request.url).pathname === '/api/rule/variable/project/0')).toBe(false)
  const load = picker.getByRole('button', { name: '加载当前可用字段', exact: true })
  if (localEditingAllowed) {
    await picker.getByRole('button', { name: /^已选内容/ }).click()
    await picker.getByRole('textbox', { name: '搜索内容名称或编码' }).fill('旧字段')
    await load.click()
    await expect(picker.getByRole('gridcell', { name: 'Global_Age', exact: true })).toBeVisible()
    await expect(page.getByTestId('designer-info')).toContainText('当前环境资源')
    await expect(picker.getByRole('textbox', { name: '搜索内容名称或编码' })).toHaveValue('')
    const editor = page.getByRole('textbox', { name: /Editor content/ })
    await expect(editor).toHaveValue(script)
    expect(harness.requests.filter(request => request.method === 'POST' && /designer\/(drafts|publish)/.test(request.url))).toEqual([])
    await editor.press('Control+End'); await editor.press('Enter')
    await picker.getByRole('gridcell', { name: 'Global_Age', exact: true }).dblclick()
    await expect(editor).toHaveValue(script + '\nGlobal_Age')
  } else await expect(load).toHaveCount(0)
  harness.assertClean()
})

test('点击展示模块定位到编码首尾，编码仍可双击修改和撤销', async ({ page }) => {
  const harness = await openReferenceScript(page)
  const editor = page.getByRole('textbox', { name: /Editor content/ })
  await editor.press('Control+End')
  for (const part of ['source', 'type', 'name', 'version']) {
    await page.locator(`.view-lines .content-ref__${part}`).filter({ hasText: /\S/ }).first().dblclick()
    const expected = part === 'version' ? 3 : 0
    expect(await editor.evaluate(element => ({ start: element.selectionStart, end: element.selectionEnd }))).toEqual({ start: expected, end: expected })
    await expect.poll(async () => page.locator('.monaco-editor-container > .monaco-editor').evaluate((element, end) => {
      const code = [...element.querySelectorAll('.content-ref__code')].find(part => part.textContent === 'age').getBoundingClientRect()
      const cursor = element.querySelector('.cursors-layer .cursor').getBoundingClientRect()
      return Math.abs(cursor.left - (end ? code.right : code.left))
    }, part === 'version')).toBeLessThanOrEqual(1.5)
  }
  const code = page.locator('.view-lines .content-ref__code').filter({ hasText: /^age$/ })
  await code.dblclick()
  expect(await editor.evaluate(element => element.value.slice(element.selectionStart, element.selectionEnd))).toBe('age')
  await page.keyboard.insertText('editedCode')
  await expect(editor).toHaveValue('editedCode + 1')
  await editor.press('Control+z')
  await expect(editor).toHaveValue('age + 1')
  harness.assertClean()
})

for (const sample of [{ script: 'return age;', start: 7, end: 10, edited: 'return ageX;' }, { script: 'age', start: 0, end: 3, edited: 'ageX' }]) {
test(`点击引用整体前后空白定位到可见编码边缘：${sample.script}`, async ({ page }) => {
  const harness = await openReferenceScript(page, sample.script)
  const editor = page.getByRole('textbox', { name: /Editor content/ })
  for (const end of [false, true]) {
    const edge = page.locator(`.view-lines .content-ref__${end ? 'version' : 'source'}`)
    let bounds
    await expect.poll(async () => {
      bounds = await edge.evaluateAll(elements => {
        const rects = elements.map(element => element.getBoundingClientRect()).filter(rect => rect.width > 0)
        if (!rects.length) return null
        const x = Math.min(...rects.map(rect => rect.left)), right = Math.max(...rects.map(rect => rect.right))
        return { x, y: rects[0].top, width: right - x, height: rects[0].height }
      })
      return Boolean(bounds)
    }).toBe(true)
    await page.mouse.click(end ? bounds.x + bounds.width + 2 : bounds.x - 2, bounds.y + bounds.height / 2)
    await expect.poll(async () => page.locator('.monaco-editor-container > .monaco-editor').evaluate((element, end) => {
      const code = [...element.querySelectorAll('.content-ref__code')].find(part => part.textContent === 'age').getBoundingClientRect()
      const cursor = element.querySelector('.cursors-layer .cursor').getBoundingClientRect()
      return Math.abs(cursor.left - (end ? code.right : code.left))
    }, end)).toBeLessThanOrEqual(1.5)
    expect(await editor.evaluate(element => element.selectionStart)).toBe(end ? sample.end : sample.start)
  }
  await page.keyboard.insertText('X')
  await expect(editor).toHaveValue(sample.edited)
  await editor.press('Control+z')
  const code = await page.locator('.view-lines .content-ref__code').filter({ hasText: /^age$/ }).boundingBox()
  await page.mouse.move(code.x + .5, code.y + code.height / 2)
  await page.mouse.down()
  await page.mouse.move(code.x + code.width - .5, code.y + code.height / 2, { steps: 5 })
  await page.mouse.up()
  expect(await editor.evaluate(element => element.value.slice(element.selectionStart, element.selectionEnd))).toBe('age')
  harness.assertClean()
})
}

for (const colorScheme of ['LIGHT', 'DARK']) for (const accentPreset of ['THEME_BLUE', 'PEACH_PINK_GRADIENT']) {
test(`${colorScheme} ${accentPreset} 引用为紧凑五色块与主题外框，装饰不写入源码`, async ({ page }) => {
  await page.addInitScript(theme => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({ schemaVersion: 1, ...theme, sidebarTheme: 'DARK', contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false })), { colorScheme, accentPreset })
  const harness = await openReferenceScript(page)
  const pieces = await page.locator('.view-lines .content-ref__segment').evaluateAll(elements => elements.filter(element => element.isConnected && element.getBoundingClientRect().width > 0).map(element => {
    const style = getComputedStyle(element), rect = element.getBoundingClientRect()
    const canvas = document.createElement('canvas'), context = canvas.getContext('2d')
    const rgb = color => { context.fillStyle = color; context.fillRect(0, 0, 1, 1); return [...context.getImageData(0, 0, 1, 1).data].slice(0, 3).map(channel => channel / 255) }
    const luminance = channels => channels.map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4).reduce((sum, value, index) => sum + value * [.2126, .7152, .0722][index], 0)
    const foreground = luminance(rgb(style.color))
    const stops = style.backgroundImage === 'none' ? [style.backgroundColor] : style.backgroundImage.match(/rgba?\([^)]+\)/g)
    if (!stops?.length) throw new Error(`无法解析实际色块背景：${style.backgroundImage}`)
    const colors = stops.map(rgb)
    const samples = colors.flatMap((color, index) => index === 0 ? [color] : Array.from({ length: 20 }, (_, step) => color.map((channel, c) => colors[index - 1][c] + (channel - colors[index - 1][c]) * (step + 1) / 20)))
    const contrast = Math.min(...samples.map(channels => (Math.max(foreground, luminance(channels)) + .05) / (Math.min(foreground, luminance(channels)) + .05)))
    const role = ['source', 'type', 'name', 'code', 'version'].find(role => element.classList.contains(`content-ref__${role}`))
    return { role, text: element.textContent, left: rect.left, right: rect.right, width: rect.width, color: style.backgroundImage === 'none' ? style.backgroundColor : style.backgroundImage, contrast, shadow: style.boxShadow, className: element.className }
  }).filter(part => part.width > 0))
  expect(pieces.filter(part => /\S/.test(part.text))).toHaveLength(5)
  expect(new Set(pieces.map(part => part.color)).size).toBe(5)
  for (const piece of pieces) {
    expect(piece.contrast).toBeGreaterThanOrEqual(4.5)
    expect(piece.color.includes('gradient')).toBe(accentPreset.endsWith('GRADIENT'))
  }
  expect(pieces.every(part => part.shadow !== 'none')).toBe(true)
  expect(pieces[0].className).toContain('content-ref__first')
  expect(pieces.at(-1).className).toContain('content-ref__last')
  for (let i = 1; i < pieces.length; i++) expect(Math.abs(pieces[i].left - pieces[i - 1].right)).toBeLessThanOrEqual(1)
  for (const role of ['source', 'type', 'name', 'version']) {
    const label = pieces.filter(part => part.role === role).map(part => part.text).join('')
    expect(label).toMatch(/^\s.*\s$/)
  }
  await expect(page.getByRole('textbox', { name: /Editor content/ })).toHaveValue('age + 1')
  const shadow = pieces[0].shadow
  await page.getByRole('button', { name: '本地用户的账户菜单' }).click()
  await page.getByRole('menuitem', { name: '主题设置' }).click()
  await page.locator('[data-accent="MIDNIGHT_PURPLE"]').click()
  await expect.poll(() => page.locator('.view-lines .content-ref__first').evaluate(element => getComputedStyle(element).boxShadow)).not.toBe(shadow)
  await page.getByRole('dialog', { name: '主题设置', exact: true }).getByRole('button', { name: '取消', exact: true }).click()
  harness.assertClean()
})
}

test('首行引用悬浮提示不被编辑器边界裁切', async ({ page }) => {
  const harness = await openReferenceScript(page)
  await page.locator('.view-lines .content-ref__code').filter({ hasText: /^age$/ }).hover()
  const hover = page.locator('.monaco-hover:visible').last()
  await expect(hover).toBeVisible()
  await expect(hover).not.toContainText('版本未提供')
  expect((await hover.innerText()).match(/尚未绑定/g)?.length || 0).toBeLessThanOrEqual(1)
  const visible = await hover.evaluate(element => {
    const rect = element.getBoundingClientRect()
    const points = [[rect.left + 12, rect.top + 12], [rect.right - 12, rect.bottom - 12]]
    return points.every(([x, y]) => element.contains(document.elementFromPoint(x, y)))
  })
  expect(visible, '提示上下边缘应可见，不能只留下被裁切的中间部分').toBe(true)
  harness.assertClean()
})

test('普通脚本方法不重复显示缺失版本提示，真实资源版本仍保留', async ({ page }) => {
  const harness = await openReferenceScript(page, 'return customMethod(age) + otherMethod(age);')
  await expect(page.locator('.view-lines')).not.toContainText('版本未提供')
  await expect(page.locator('.view-lines .content-ref__version')).toHaveText(['v1', 'v1'])
  harness.assertClean()
})

test('内容选择器短名称和编码按内容紧凑展示，不拉伸成大片空白', async ({ page }) => {
  const harness = await installDistRoutes(page, { apiData: createDesignerApiData() })
  await page.goto('http://tianshu.local/#/designer/table/101')
  await page.getByRole('button', { name: '添加行', exact: true }).click()
  await page.getByPlaceholder('选择左操作数...').first().click()
  const panel = page.locator('.fp-panel:visible')
  await panel.getByRole('textbox', { name: '搜索内容名称或编码' }).fill('age')
  const row = panel.locator('.fp-row').filter({ has: page.locator('.content-ref__code', { hasText: /^age$/ }) })
  const spaces = await row.locator('.content-ref__name, .content-ref__code').evaluateAll(elements => elements.map(element => {
    const text = document.createRange(); text.selectNodeContents(element)
    return element.getBoundingClientRect().width - text.getBoundingClientRect().width
  }))
  expect(spaces.length).toBe(2)
  expect(spaces.every(space => space <= 12)).toBe(true)
  await expect(panel.getByRole('columnheader', { name: '编码', exact: true })).toBeVisible()
  const columns = await panel.getByRole('columnheader').evaluateAll(elements => elements.map(element => element.getBoundingClientRect().left))
  const cells = await row.getByRole('gridcell').evaluateAll(elements => elements.map(element => element.getBoundingClientRect().left))
  cells.forEach((left, index) => expect(Math.abs(left - columns[index])).toBeLessThanOrEqual(1))
  harness.assertClean()
})

for (const width of [1440, 560]) test(`${width}px 常驻内容选择器保留二级分类、表格、插入和收起状态`, async ({ page }, testInfo) => {
  await page.setViewportSize({ width, height: 900 })
  const harness = await openReferenceScript(page)
  const picker = page.getByRole('complementary', { name: '天枢决策引擎内容选择器', exact: true })
  await expect(picker).toBeVisible()
  await expect(picker.getByRole('columnheader', { name: '值 / 路径', exact: true })).toHaveCount(0)
  if (width > 1100) {
    const pickerBox = await picker.boundingBox(), editorBox = await page.locator('.se-editor-area').boundingBox()
    expect(pickerBox.x + pickerBox.width).toBeLessThanOrEqual(editorBox.x)
    const divider = await page.getByRole('separator', { name: '调整字段列表宽度', exact: true }).boundingBox()
    await page.mouse.move(divider.x + divider.width / 2, divider.y + 40)
    await page.mouse.down(); await page.mouse.move(divider.x + divider.width / 2 + 48, divider.y + 40, { steps: 8 }); await page.mouse.up()
    await expect.poll(async () => (await picker.boundingBox()).width).toBeGreaterThanOrEqual(pickerBox.width + 40)
  }
  const navigation = picker.getByRole('navigation', { name: '内容分类', exact: true })
  await navigation.getByRole('button', { name: /^数据对象/ }).click()
  await navigation.getByRole('button', { name: '数据对象：税务请求', exact: true }).click()
  await expect(picker.locator('.fp-row')).toContainText(['税务请求', '金额'])
  const navBox = await navigation.boundingBox(), tableBox = await picker.getByRole('treegrid').boundingBox()
  expect(navBox.x + navBox.width).toBeLessThanOrEqual(tableBox.x + 1)
  const editor = page.getByRole('textbox', { name: /Editor content/ })
  await editor.press('Control+End'); await page.keyboard.insertText('; ')
  await insertScriptReference(page, 'income')
  await expect(editor).toHaveValue('age + 1; income')
  await expect(picker).toBeVisible()
  await picker.getByRole('button', { name: '收起内容选择器', exact: true }).click()
  await expect(picker).toBeHidden()
  await page.getByRole('button', { name: '展开内容选择器', exact: true }).click()
  await expect(picker).toBeVisible()
  await expect(editor).toHaveValue('age + 1; income')
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width)
  await page.screenshot({ path: testInfo.outputPath(`persistent-picker-${width}.png`) })
  harness.assertClean()
})

test('冻结 Monaco 脚本反解内置方法和固定版本引用，不借当前资源名称', async ({ page }, testInfo) => {
  const data = createDesignerApiData()
  const script = 'return resourceVersionValue("VARIABLE", "548", "BUSINESS_VERSION:948", "");'
  const revision = { id: 2109, definitionId: 109, revisionNo: 3, state: 'PUBLISHED', artifactId: 10, lockVersion: 0, modelJson: JSON.stringify({ script, scriptVarRefs: [] }) }
  data.set('/api/rule/definition/109/revisions', [revision])
  data.set('/api/rule/definition/109/revisions/2109', revision)
  data.set('/api/rule/definition/109/reference-catalog', ({ url }) => ({ status: 'AVAILABLE', referenceNamespace: 'ARTIFACT_SOURCE', localEditingAllowed: true,
    executionIdentity: { definitionId: '109', sourceType: url.searchParams.get('sourceType'), sourceId: url.searchParams.get('sourceId') },
    variables: [{ id: '548', varCode: 'current_age', varLabel: '其他版本年龄', varType: 'INTEGER' }], objectTree: [], functions: [], models: [], rules: [], apis: [], lists: [],
    versionReferences: [{ refType: 'VARIABLE', refId: '548', versionId: '948', versionSource: 'BUSINESS_VERSION', version: 7, displayVersion: 3, code: 'age_old', label: '历史年龄', valueType: 'INTEGER' }],
  }))
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/#/designer/script/109')
  const lines = page.locator('.view-lines')
  await expect(lines.locator('.content-ref__source').filter({ hasText: /\S/ })).toHaveText(['方法', '变量'])
  // Monaco 在不同字体度量下会把注入的留白拆成独立 span；只核对实际显示的名称。
  await expect(lines.locator('.content-ref__name').filter({ hasText: /\S/ })).toHaveText(['固定版本取值', '历史年龄'])
  await expect(lines.locator('.content-ref__code').filter({ hasText: /\S/ })).toHaveText(['resourceVersionValue', 'age_old'])
  await expect(lines.locator('.content-ref__version').filter({ hasText: /\S/ })).toHaveText(['v3'])
  await expect(lines).not.toContainText('其他版本年龄')
  const overflow = await lines.evaluate(element => {
    const viewport = element.closest('.monaco-scrollable-element').getBoundingClientRect()
    return [...element.querySelectorAll('.content-ref__segment')].some(part => part.getBoundingClientRect().right > viewport.right + 1)
  })
  expect(overflow).toBe(false)
  expect(await page.evaluate(() => window.monaco.editor.getModels().some(model => model.getValue() === 'return resourceVersionValue("VARIABLE", "548", "BUSINESS_VERSION:948", "");'))).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('monaco-fixed-method-reference.png'), animations: 'disabled' })
  assertClean()
})

for (const module of ['API', '数据源鉴权']) test(`${module} 选择后使用通用连续色块`, async ({ page }) => {
  const data = createDetailApiData()
  data.set('/api/rule/variable/project/1', [variable])
  if (module === 'API') Object.assign(data.get('/api/rule/datasource/api-config/22'), { authMode: 'BASIC', authApiConfig: JSON.stringify({ username: null, password: null }) })
  else Object.assign(data.get('/api/rule/datasource/21'), { authType: 'BASIC', authConfig: JSON.stringify({ username: null, password: null }) })
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto(`http://tianshu.local/#/datasource/${module === 'API' ? 'api/22' : 'source/21'}`)
  const username = page.locator('.api-auth-editor .el-form-item').filter({ hasText: /^用户名/ })
  await pick(page, username.locator('input'), 'clientCode')
  await checkBlocks(username.locator('.content-ref'))
  await expect(username.locator('.content-ref__code')).toHaveText('clientCode')
  assertClean()
})

test('规则配置与编译脚本共用来源、类型、名称、编码和版本', async ({ page }) => {
  const data = createDesignerApiData()
  data.set('POST /api/rule/definition/101/designer/compile', { compileSuccess: true, compiledScript: 'return age;', preflightReport: { valid: true, errors: [], warnings: [] } })
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/#/designer/table/101')
  await page.getByRole('button', { name: '添加行', exact: true }).click()
  const input = page.getByPlaceholder('选择左操作数...').first()
  await pick(page, input, 'age')
  await checkBlocks(input.locator('xpath=ancestor::div[contains(@class,"vp-reference")]').locator('.content-ref'))
  await page.getByRole('button', { name: '编译', exact: true }).click()
  await page.getByRole('button', { name: '展开脚本面板', exact: true }).click()
  await expect(page.locator('.script-panel .content-ref__source')).toHaveText('变量')
  await expect(page.locator('.script-panel .content-ref__code')).toHaveText('age')
  assertClean()
})

test('规则开放接口与模型入出参均复用通用色块', async ({ page }) => {
  const { assertClean } = await installDistRoutes(page, { apiData: createDetailApiData() })
  await page.goto('http://tianshu.local/#/rule/101')
  await page.getByRole('tab', { name: /开放接口/ }).click()
  await checkBlocks(page.locator('.open-api-section .content-ref').first())
  await page.goto('http://tianshu.local/#/model/41')
  await checkBlocks(page.locator('.operand-value-display').first())
  await page.getByRole('tab', { name: /输出字段/ }).click()
  await checkBlocks(page.locator('.el-tab-pane:visible .operand-value-display').first())
  assertClean()
})

test('分流实验请求键及执行规则展示通用内容', async ({ page }) => {
  const data = createDetailApiData()
  data.set('/api/rule/variable/project/1', [variable])
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/#/experiment/detail/61')
  await pick(page, page.getByPlaceholder('选择字段或配置表达式', { exact: true }), 'clientCode')
  await checkBlocks(page.locator('.base-form .content-ref').first())
  await checkBlocks(page.locator('.group-action-form .content-ref').first())
  assertClean()
})

test('账单指定计费对象使用相同色块', async ({ page }) => {
  const data = createDetailApiData()
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/#/billing')
  await page.getByRole('row').filter({ hasText: 'engine_call' }).getByRole('button', { name: '编辑', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: /计费配置/ })
  await checkBlocks(dialog.locator('.content-ref').first())
  await expect(dialog.locator('.content-ref__source')).toHaveText('规则')
  assertClean()
})

test('血缘图分行展示名称编码和元信息，白底工具栏保留选中反馈', async ({ page }, testInfo) => {
  const data = createOperationsApiData()
  const graph = data.get('/api/rule/lineage/graph')
  const label = '授信状态及当前审核阶段说明'
  const code = 'credit_status_and_current_review_stage'
  for (const node of [graph.startNode, graph.nodes[0]]) Object.assign(node, { valueType: 'INTEGER', version: 3, label, code })
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/#/lineage')
  await page.locator('.query-panel .el-form-item').filter({ hasText: '起点' }).locator('.el-select').click()
  await page.getByRole('option', { name: '年龄 (age)' }).click()
  await page.getByRole('button', { name: '生成血缘图' }).click()
  const root = page.locator('.current-node .content-ref')
  await expect(root.locator('.content-ref__name')).toHaveText(label)
  await expect(root.locator('.content-ref__code')).toHaveText(code)
  await expect(root.locator('.content-ref__type')).toHaveText('123')
  await expect(root.locator('.content-ref__version')).toHaveText('v3')
  for (const part of ['name', 'code', 'type', 'version']) {
    await expect(root.locator(`.content-ref__${part}`)).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)')
  }
  const nameBox = await root.locator('.content-ref__name').boundingBox()
  const codeBox = await root.locator('.content-ref__code').boundingBox()
  const versionBox = await root.locator('.content-ref__version').boundingBox()
  const cardBox = await page.locator('.current-node').boundingBox()
  expect(codeBox.y).toBeGreaterThanOrEqual(nameBox.y + nameBox.height)
  expect(versionBox.y).toBeGreaterThanOrEqual(codeBox.y + codeBox.height)
  expect(versionBox.y + versionBox.height).toBeLessThanOrEqual(cardBox.y + cardBox.height)
  expect(nameBox.width).toBeGreaterThan(cardBox.width * 0.8)
  await expect(root.locator('.content-ref__code')).toHaveAttribute('title', code)
  await root.locator('.content-ref__name').click()
  const panel = page.getByTestId('lineage-node-info-panel')
  await expect(panel.locator('.content-ref__name')).toHaveText(label)
  await expect(panel.locator('.content-ref__code')).toHaveText(code)
  await expect(panel.locator('.content-ref__code')).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)')
  expect(await panel.locator('.content-ref__code').evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  const toolbar = page.locator('.graph-toolbar')
  await expect(toolbar).toHaveCSS('background-color', 'rgb(255, 255, 255)')
  const miniMap = toolbar.getByRole('button', { name: '小地图', exact: true })
  await expect(miniMap).toHaveAttribute('aria-pressed', 'false')
  await miniMap.click()
  await expect(miniMap).toHaveAttribute('aria-pressed', 'true')
  await expect(miniMap).not.toHaveCSS('background-color', 'rgb(255, 255, 255)')
  await miniMap.click()
  await panel.getByRole('button', { name: '关闭节点信息' }).click()
  await page.setViewportSize({ width: 768, height: 900 })
  const toolbarBox = await toolbar.boundingBox()
  expect(toolbarBox.x).toBeGreaterThanOrEqual(0)
  expect(toolbarBox.x + toolbarBox.width).toBeLessThanOrEqual(769)
  await expect(toolbar.getByRole('button', { name: '重置血缘图' })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('lineage-content-reference.png'), animations: 'disabled' })
  assertClean()
})

test('执行日志的表达式追踪保留独立变量与阈值色块', async ({ page }) => {
  const data = createOperationsApiData()
  const record = data.get('/api/rule/log/list').records[0]
  record.modelType = 'SCRIPT'
  record.traceInfo = JSON.stringify([{ type: 'OPERATOR', token: '>', evaluated: true, value: true, children: [{ type: 'VARIABLE', token: 'age', label: '年龄', refType: 'VARIABLE', refId: 1, valueType: 'INTEGER', version: 3, value: 28, evaluated: true }, { type: 'VALUE', token: '18', value: 18, valueType: 'NUMBER', evaluated: true }] }])
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/#/log')
  await page.getByRole('row').filter({ hasText: 'trace_rule_001' }).getByRole('button', { name: '详情', exact: true }).click()
  await page.getByRole('tab', { name: /表达式追踪树/ }).click()
  await checkBlocks(page.locator('.trace-wrap .content-ref').first())
  await expect(page.locator('.trace-wrap .content-ref__code').filter({ hasText: /^age$/ }).first()).toBeVisible()
  assertClean()
})
