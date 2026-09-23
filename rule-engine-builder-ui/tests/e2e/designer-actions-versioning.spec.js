const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')

const designers = [
  ['table', 101, 'TABLE', '添加行'], ['tree', 102, 'TREE', '执行动作'],
  ['flow', 103, 'FLOW', '执行动作'], ['ruleset', 104, 'RULE_SET', '添加规则'],
  ['cross', 105, 'CROSS', '添加行'], ['score', 106, 'SCORE', '添加评分项'],
  ['cross-adv', 107, 'CROSS_ADV', '添加行维度'], ['score-adv', 108, 'SCORE_ADV', '添加维度组'],
  ['script', 109, 'SCRIPT', null],
]

function fixtures(id) {
  const apiData = createDesignerApiData()
  const base = `/api/rule/definition/${id}`
  const saved = [], compiled = [], executed = [], published = []
  const drafts = new Map()
  const versions = [{ id: '81', version: 1, bindingId: '8001', generation: 3 }]
  apiData.set(`${base}/published-versions`, versions)
  apiData.set(`${base}/versions/81`, { ...versions[0], definitionId: id, modelJson: '{}' })
  apiData.set(`${base}/revisions`, () => [...drafts.values()])
  apiData.set(`POST ${base}/designer/compile`, ({ request }) => {
    compiled.push(request.postDataJSON())
    return { compileSuccess: true, compiledScript: 'result = 1;', preflightReport: { valid: true, errors: [], warnings: [] } }
  })
  apiData.set(`POST ${base}/designer/drafts`, ({ request }) => {
    const body = request.postDataJSON()
    saved.push(body)
    const revisionId = body.saveMode === 'NEW' ? String(9001 + drafts.size) : body.revisionId
    const draft = { id: revisionId, definitionId: id, state: 'DRAFT', revisionNo: drafts.get(revisionId)?.revisionNo || drafts.size + 1, lockVersion: (drafts.get(revisionId)?.lockVersion || 0) + 1, modelJson: body.modelJson, sourceType: body.sourceType, sourceId: body.sourceId }
    drafts.set(revisionId, draft)
    return { revision: draft, compileSuccess: true, issues: [], designVersion: 999 }
  })
  for (const revisionId of ['9001', '9002']) apiData.set(`${base}/revisions/${revisionId}`, () => drafts.get(revisionId))
  apiData.set('POST /api/rule/test-schema', { inputs: [], runtimeNodes: [], sampleParams: {}, diagnostics: [] })
  apiData.set('POST /api/rule/definition/execute', ({ request }) => {
    executed.push(request.postDataJSON())
    return { success: true, result: { result: 1 }, executeTimeMs: 1 }
  })
  apiData.set(`POST ${base}/designer/publish`, ({ request }) => {
    const body = request.postDataJSON()
    published.push(body)
    return { revision: { ...drafts.get(body.revisionId), state: 'REVIEW' }, approvalRequestId: '91' }
  })
  return { apiData, saved, compiled, executed, published, drafts }
}

async function editScript(page, text) {
  const editor = page.locator('.se-main').getByRole('textbox', { name: /Editor content/ })
  const target = await editor.count() ? editor : page.getByRole('textbox', { name: /Editor content/ }).first()
  await target.click()
  await target.press('ControlOrMeta+A')
  await target.fill(text)
}

for (const [route, id, modelType, add] of designers) {
  test(`${modelType} 四按钮使用当前配置，编译测试零保存，保存产生独立草稿`, async ({ page }) => {
    const data = fixtures(id)
    const harness = await installDistRoutes(page, { apiData: data.apiData })
    await page.setViewportSize({ width: 1600, height: 1000 })
    await page.goto(`http://tianshu.local/index.html#/designer/${route}/${id}?projectId=1`)
    const bar = page.locator('main .rule-designer-actions')
    await expect(bar.locator('[data-action="test"]')).toBeEnabled()
    expect(await bar.locator('[data-action]').evaluateAll(elements => elements.map(element => element.dataset.action))).toEqual(['compile', 'save', 'publish', 'test'])
    if (add) await page.getByRole('button', { name: add, exact: true }).first().click()
    else await editScript(page, 'result = age;')
    await expect(page.getByTestId('designer-info')).toContainText('有未保存修改')
    await bar.locator('[data-action="test"]').click()
    await page.getByRole('dialog', { name: '测试执行' }).getByRole('button', { name: '执行测试', exact: true }).click()
    await expect(page.getByRole('dialog', { name: '测试执行' })).toContainText('执行成功')
    expect(data.executed[0]).toMatchObject({ definitionId: String(id), projectId: 1, modelType, modelJson: expect.any(String) })
    expect(data.saved).toHaveLength(0)
    await page.getByRole('dialog', { name: '测试执行' }).getByRole('button', { name: '关闭', exact: true }).click()
    await expect(page.getByRole('dialog', { name: '测试执行' })).toBeHidden()
    const beforeCompile = await bar.boundingBox()
    await bar.locator('[data-action="compile"]').click()
    await expect(page.getByTestId('designer-info')).toContainText('编译与发布前检查通过')
    const afterCompile = await bar.boundingBox()
    expect(afterCompile.height).toBe(beforeCompile.height)
    expect(afterCompile.y).toBe(beforeCompile.y)
    await expect(page.getByRole('dialog', { name: '编译检查结果' })).toHaveCount(0)
    expect(data.compiled[0]).toMatchObject({ sourceType: 'VERSION', sourceId: '81', modelJson: data.executed[0].modelJson })
    expect(data.saved).toHaveLength(0)
    await bar.locator('[data-action="save"]').click()
    await expect(page).toHaveURL(/sourceType=REVISION&sourceId=9001/)
    expect(data.saved).toHaveLength(1)
    expect(data.saved[0]).toMatchObject({ saveMode: 'NEW', sourceType: 'VERSION', sourceId: '81', requestId: expect.any(String) })
    expect(data.saved[0]).not.toHaveProperty('revisionId')
    await expect(page.getByTestId('designer-version-select')).toContainText('草稿')
    await expect(page.getByTestId('designer-version-select')).not.toContainText('999')
    await page.getByTestId('designer-version-select').click()
    const draftOption = page.getByRole('option', { name: /草稿 · 1/ })
    await expect(draftOption.getByRole('button', { name: /删除草稿/ })).toBeVisible()
    // 下拉层入场有缩放动画，父子矩形必须在同一帧读取。
    await expect(async () => {
      const { verticalOffset, rightOverflow } = await draftOption.evaluate(option => {
        const optionBox = option.getBoundingClientRect()
        const deleteBox = option.querySelector('.delete-draft').getBoundingClientRect()
        return {
          verticalOffset: Math.abs(deleteBox.y + deleteBox.height / 2 - optionBox.y - optionBox.height / 2),
          rightOverflow: deleteBox.right - optionBox.right
        }
      })
      expect(verticalOffset).toBeLessThanOrEqual(1)
      expect(rightOverflow).toBeLessThanOrEqual(0)
    }).toPass()
    await page.getByRole('combobox', { name: '选择规则版本' }).press('Escape')
    await page.reload()
    await expect(page.getByTestId('designer-version-select')).toContainText('草稿')
    expect(data.saved).toHaveLength(1)
    harness.assertClean()
  })
}

for (const route of ['table', 'flow', 'tree', 'script']) {
  test(`${route} 编译错误在弹窗展示，关闭报告后操作栏保持高度`, async ({ page }) => {
    const [, id] = designers.find(item => item[0] === route)
    const data = fixtures(id)
    const issue = { code: 'COMPILE_FAILED', message: '表达式缺少右括号', path: '$.script' }
    data.apiData.set(`POST /api/rule/definition/${id}/designer/compile`, {
      compileSuccess: false, compileMessage: issue.message,
      preflightReport: { valid: false, errors: [issue], warnings: [] }
    })
    const harness = await installDistRoutes(page, { apiData: data.apiData })
    await page.setViewportSize({ width: 1600, height: 1000 })
    await page.goto(`http://tianshu.local/index.html#/designer/${route}/${id}`)
    const bar = page.locator('main .rule-designer-actions')
    await expect(bar.locator('[data-action="compile"]')).toBeEnabled()
    const before = await bar.boundingBox()
    await bar.locator('[data-action="compile"]').click()
    const report = page.getByRole('dialog', { name: '编译检查结果' })
    await expect(report).toContainText(issue.message)
    await expect(bar.locator('.validation-report')).toHaveCount(0)
    await report.getByRole('button', { name: '关闭', exact: true }).click()
    await expect(report).toBeHidden()
    const after = await bar.boundingBox()
    expect(after.height).toBe(before.height)
    expect(after.y).toBe(before.y)
    expect(data.saved).toHaveLength(0)
    harness.assertClean()
  })
}

for (const route of ['flow', 'tree']) for (const direction of ['right', 'bottom']) {
  test(`${route} ${direction} 美化后分支在实际网格画布中等距居中`, async ({ page }) => {
    const [, id] = designers.find(item => item[0] === route)
    const data = fixtures(id)
    const nodes = [
      { id: 'root', type: 'exclusive-gateway', x: 300, y: 300, properties: { nodeName: '分支中心' } },
      { id: 'a', type: 'script-task', x: 520, y: 300, properties: { nodeName: '分支 A' } },
      { id: 'b', type: 'script-task', x: 520, y: 460, properties: { nodeName: '分支 B' } },
    ]
    const edges = ['a', 'b'].map(target => ({
      id: `root-${target}`, type: 'polyline', sourceNodeId: 'root', targetNodeId: target,
      sourceAnchorId: `root_${direction === 'right' ? 1 : 2}`,
      targetAnchorId: `${target}_${direction === 'right' ? 3 : 0}`, properties: {}
    }))
    data.apiData.set(`/api/rule/definition/${id}/versions/81`, { id: 81, version: 1, modelJson: JSON.stringify({ logicflow: { nodes, edges } }) })
    const harness = await installDistRoutes(page, { apiData: data.apiData })
    await page.goto(`http://tianshu.local/index.html#/designer/${route}/${id}`)
    const canvasNodes = page.locator(`.${route}-canvas .lf-node:not(.lf-mini-map .lf-node)`)
    await expect(canvasNodes).toHaveCount(3)
    await page.getByRole('button', { name: '一键美化', exact: true }).click()
    const readPositions = () => canvasNodes.evaluateAll(elements => Object.fromEntries(elements.map(element => {
      const box = element.querySelector('circle, rect, polygon').getBBox()
      return [element.textContent.trim(), { x: box.x + box.width / 2, y: box.y + box.height / 2 }]
    })))
    const axis = direction === 'right' ? 'y' : 'x'
    const positions = await readPositions()
    expect((positions['分支 A'][axis] + positions['分支 B'][axis]) / 2).toBe(positions['分支中心'][axis])
    expect(positions['分支 A'][axis]).not.toBe(positions['分支 B'][axis])
    await page.getByRole('button', { name: '一键美化', exact: true }).click()
    expect(await readPositions()).toEqual(positions)
    expect(data.saved).toHaveLength(0)
    harness.assertClean()
  })
}

for (const route of ['flow', 'tree']) for (const direction of ['right', 'bottom']) {
  test(`${route} ${direction} 美化统一折线主干并将中间节点居中，保存重载保留路径`, async ({ page }) => {
    const [, id] = designers.find(item => item[0] === route)
    const data = fixtures(id)
    const point = (main, cross) => direction === 'right' ? { x: main, y: cross } : { x: cross, y: main }
    const nodes = [
      { id: 'root', type: 'exclusive-gateway', ...point(300, 300), properties: { nodeName: '是否有违' } },
      { id: 'd', type: 'script-task', ...point(540, 180), properties: { nodeName: '信用等级D' } },
      { id: 'middle', type: 'exclusive-gateway', ...point(540, 420), properties: { nodeName: '合规评分' } },
      ...['a', 'b', 'c'].map((id, index) => ({ id, type: 'script-task', ...point(780, 260 + index * 160), properties: { nodeName: `信用等级${id.toUpperCase()}` } }))
    ]
    const edges = [['root', 'd'], ['root', 'middle'], ['middle', 'a'], ['middle', 'b'], ['middle', 'c']]
      .map(([source, target]) => ({
        id: `${source}-${target}`, type: 'polyline', sourceNodeId: source, targetNodeId: target,
        sourceAnchorId: `${source}_${direction === 'right' ? 1 : 2}`,
        targetAnchorId: `${target}_${direction === 'right' ? 3 : 0}`, properties: {}
      }))
    data.apiData.set(`/api/rule/definition/${id}/versions/81`, { id: 81, version: 1, modelJson: JSON.stringify({ logicflow: { nodes, edges } }) })
    const harness = await installDistRoutes(page, { apiData: data.apiData })
    await page.setViewportSize({ width: 1600, height: 1000 })
    await page.goto(`http://tianshu.local/index.html#/designer/${route}/${id}`)
    const canvas = page.locator(`.${route}-canvas`)
    await expect(canvas.locator('.lf-node:not(.lf-mini-map .lf-node)')).toHaveCount(6)
    const geometry = () => canvas.evaluate(element => {
      const paths = Object.fromEntries([...element.querySelectorAll('.lf-edge polyline[marker-end]')]
        .filter(edge => !edge.closest('.lf-mini-map'))
        .map(edge => [edge.getAttribute('marker-end').match(/#marker-end-(.+)\)/)[1], edge.getAttribute('points').trim().split(/\s+/).map(point => point.split(',').map(Number))]))
      const middle = [...element.querySelectorAll('.lf-node')].find(node => !node.closest('.lf-mini-map') && node.textContent === '合规评分')
      const bounds = middle.querySelector('polygon').getBBox()
      return { paths, center: [bounds.x + bounds.width / 2, bounds.y + bounds.height / 2] }
    })
    const axis = direction === 'right' ? 0 : 1
    const cross = 1 - axis
    const lane = points => points.slice(1).map((p, i) => ({ p, previous: points[i] }))
      .filter(({ p, previous }) => p[axis] === previous[axis])
      .sort((a, b) => Math.abs(b.p[cross] - b.previous[cross]) - Math.abs(a.p[cross] - a.previous[cross]))[0]?.p[axis]
    await page.getByRole('button', { name: '一键美化', exact: true }).click()
    const result = await geometry()
    const before = lane(result.paths['root-middle'])
    const after = lane(result.paths['middle-a'])
    expect(lane(result.paths['root-d'])).toBe(before)
    expect(lane(result.paths['middle-c'])).toBe(after)
    expect(result.center[axis]).toBe((before + after) / 2)
    await page.getByRole('button', { name: '一键美化', exact: true }).click()
    expect(await geometry()).toEqual(result)
    await page.locator('main .rule-designer-actions [data-action="save"]').click()
    await expect(page).toHaveURL(/sourceId=9001/)
    await page.reload()
    await expect(canvas.locator('.lf-node:not(.lf-mini-map .lf-node)')).toHaveCount(6)
    expect(await geometry()).toEqual(result)
    harness.assertClean()
  })
}

test('草稿新增与覆盖、取消切版、保存并切换、发布绑定请求', async ({ page }) => {
  const data = fixtures(109)
  const harness = await installDistRoutes(page, { apiData: data.apiData })
  await page.goto('http://tianshu.local/index.html#/designer/script/109?projectId=1')
  const bar = page.locator('main .rule-designer-actions')
  await expect(bar.locator('[data-action="save"]')).toBeEnabled()
  await editScript(page, 'result = 1;')
  await bar.locator('[data-action="save"]').click()
  await expect(page).toHaveURL(/sourceId=9001/)
  await editScript(page, 'result = 2;')
  await bar.locator('[data-action="save"]').click()
  const save = page.getByRole('dialog', { name: '保存草稿', exact: true })
  await save.getByText('覆盖当前草稿', { exact: true }).click()
  await save.locator('[data-action="confirm-save"]').click()
  await expect(page.getByTestId('designer-info')).toContainText('草稿已保存')
  expect(data.saved[1]).toMatchObject({ saveMode: 'OVERWRITE', revisionId: '9001', lockVersion: 1, sourceType: 'REVISION', sourceId: '9001' })
  await editScript(page, 'result = 3;')
  await page.getByTestId('designer-version-select').click()
  await page.getByRole('option', { name: '发布版本 v1', exact: true }).click()
  const switching = page.getByRole('dialog', { name: '切换版本', exact: true })
  await switching.locator('[data-action="cancel-choice"]').click()
  await expect(page).toHaveURL(/sourceId=9001/)
  expect(data.saved).toHaveLength(2)
  await page.getByTestId('designer-version-select').click()
  await page.getByRole('option', { name: '发布版本 v1', exact: true }).click()
  await switching.getByText('另存为新草稿', { exact: true }).click()
  await expect(switching.getByRole('radio', { name: '另存为新草稿', exact: true })).toBeChecked()
  await switching.locator('[data-action="confirm-save"]').click()
  await expect(page).toHaveURL(/sourceType=VERSION&sourceId=81/)
  expect(data.saved[2]).toMatchObject({ saveMode: 'NEW', sourceId: '9001' })
  await page.getByTestId('designer-version-select').click()
  await page.getByRole('option', { name: /草稿 · 2/ }).click()
  await expect(page).toHaveURL(/sourceId=9002/)
  await bar.locator('[data-action="publish"]').click()
  const publish = page.getByRole('dialog', { name: '发布规则', exact: true })
  await publish.getByText('覆盖已有版本', { exact: true }).click()
  await publish.getByText('选择覆盖版本', { exact: true }).click()
  await page.getByRole('option', { name: '版本 v1', exact: true }).click()
  await publish.getByRole('textbox', { name: '变更说明' }).fill('调整额度')
  await publish.locator('[data-action="confirm-publish"]').click()
  await expect(page.getByRole('dialog', { name: '已提交发布审批', exact: true }))
    .toContainText('当前配置已提交发布审批，审批通过后才会生效。已有生效版本保持不变。')
  expect(data.published[0]).toEqual({ revisionId: '9002', lockVersion: 1, publishMode: 'OVERWRITE', targetVersionId: '8001', targetGeneration: 3, comment: '调整额度' })
  expect(data.saved).toHaveLength(3)
  harness.assertClean()
})

for (const scheme of ['LIGHT', 'DARK']) test(`${scheme} 字段在左状态在右，按钮文字对比度达标`, async ({ page }) => {
  await page.addInitScript(colorScheme => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({ schemaVersion: 1, colorScheme, accentPreset: 'LIQUID_PURPLE', sidebarTheme: 'DARK', contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false })), scheme)
  const data = fixtures(109)
  const harness = await installDistRoutes(page, { apiData: data.apiData })
  await page.goto('http://tianshu.local/index.html#/designer/script/109')
  await expect(page.locator('html')).toHaveAttribute('data-theme', scheme.toLowerCase())
  await expect(page.locator('main .rule-designer-actions [data-action="test"]')).toBeEnabled()
  const layout = await page.getByTestId('designer-info').evaluate(element => {
    const fields = element.firstElementChild.getBoundingClientRect(), status = element.lastElementChild.getBoundingClientRect()
    return { fieldsLeft: fields.left, statusLeft: status.left, fieldsTop: fields.top, statusTop: status.top }
  })
  expect(layout.fieldsLeft).toBeLessThan(layout.statusLeft)
  expect(layout.fieldsTop).toBe(layout.statusTop)
  const contrasts = await page.locator('main .rule-designer-actions button').evaluateAll(buttons => buttons.map(button => {
    const style = getComputedStyle(button)
    const luminance = color => color.match(/[\d.]+/g).slice(0, 3).map(Number).map(n => n / 255).map(n => n <= .04045 ? n / 12.92 : ((n + .055) / 1.055) ** 2.4).reduce((sum, n, index) => sum + n * [.2126, .7152, .0722][index], 0)
    const a = luminance(style.color), b = luminance(style.backgroundColor)
    return (Math.max(a, b) + .05) / (Math.min(a, b) + .05)
  }))
  contrasts.forEach(contrast => expect(contrast).toBeGreaterThanOrEqual(4.5))
  harness.assertClean()
})

test('改动草稿发布时连续完成保存选择和发布选择，取消发布保留已保存草稿', async ({ page }) => {
  const data = fixtures(109)
  const harness = await installDistRoutes(page, { apiData: data.apiData })
  await page.goto('http://tianshu.local/index.html#/designer/script/109')
  const bar = page.locator('main .rule-designer-actions')
  await expect(bar.locator('[data-action="save"]')).toBeEnabled()
  await editScript(page, 'return 1;')
  await bar.locator('[data-action="save"]').click()
  await expect(page).toHaveURL(/sourceId=9001/)
  await editScript(page, 'return 2;')
  await bar.locator('[data-action="publish"]').click()
  const save = page.getByRole('dialog', { name: '保存草稿', exact: true })
  await save.getByText('覆盖当前草稿', { exact: true }).click()
  await save.locator('[data-action="confirm-save"]').click()
  const publish = page.getByRole('dialog', { name: '发布规则', exact: true })
  await expect(publish).toBeVisible()
  await publish.locator('[data-action="cancel-choice"]').click()
  expect(data.saved).toHaveLength(2)
  expect(JSON.parse(data.saved[1].modelJson).script).toBe('return 2;')
  expect(data.published).toHaveLength(0)
  harness.assertClean()
})

for (const colorScheme of ['LIGHT', 'DARK']) for (const accentMode of ['CUSTOM_SOLID', 'CUSTOM_GRADIENT']) {
  test(`${colorScheme} ${accentMode} 操作按钮背景、文字、hover 和 focus 跟随主题`, async ({ page }, testInfo) => {
    const theme = { schemaVersion: 2, colorScheme, accentMode, accentPreset: 'THEME_BLUE', customSolidColor: '#FFE066', customGradientColors: ['#2639E9', '#873FF2'], customGradientType: 'LINEAR', customGradientAngle: 135, navigationLayout: 'LEFT', sidebarTheme: 'DARK', contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false }
    await page.addInitScript(theme => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify(theme)), theme)
    const data = fixtures(109)
    data.apiData.set('/api/auth/console/preferences/theme', theme)
    const harness = await installDistRoutes(page, { apiData: data.apiData })
    await page.goto('http://tianshu.local/index.html#/designer/script/109')
    const bar = page.locator('main .rule-designer-actions')
    await expect(bar.locator('[data-action="test"]')).toBeEnabled()
    for (const button of await bar.locator('[data-action]').all()) {
      for (const state of ['normal', 'hover', 'focus']) {
        if (state === 'hover') await button.hover()
        if (state === 'focus') await button.focus()
        const colors = await button.evaluate(element => {
          const style = getComputedStyle(element)
          const luminance = color => color.match(/[\d.]+/g).slice(0, 3).map(Number).map(n => n / 255).map(n => n <= .04045 ? n / 12.92 : ((n + .055) / 1.055) ** 2.4).reduce((sum, n, i) => sum + n * [.2126, .7152, .0722][i], 0)
          const a = luminance(style.color), b = luminance(style.backgroundColor)
          return { contrast: (Math.max(a, b) + .05) / (Math.min(a, b) + .05), image: style.backgroundImage, border: style.borderTopStyle }
        })
        expect(colors.contrast).toBeGreaterThanOrEqual(4.5)
        expect(colors.border).toBe('solid')
        if (await button.getAttribute('data-action') === 'publish' && accentMode === 'CUSTOM_GRADIENT') expect(colors.image).toContain('linear-gradient')
      }
    }
    await page.screenshot({ path: testInfo.outputPath('designer-theme.png') })
    harness.assertClean()
  })
}
