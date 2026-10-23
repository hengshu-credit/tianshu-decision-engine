const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')
const { platformResponse } = require('./support/platformResponse.cjs')

for (const accentPreset of ['THEME_BLUE', 'MIDNIGHT_PURPLE', 'PEACH_PINK']) {
  test(`深色 ${accentPreset} 决策表添加动作在普通、悬停、键盘聚焦时均清晰可读`, async ({ page }) => {
    await page.addInitScript(preset => {
      localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({
        schemaVersion: 1, colorScheme: 'DARK', accentPreset: preset,
        sidebarTheme: 'DARK', contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false,
      }))
    }, accentPreset)
    const { assertClean } = await installDistRoutes(page, { apiData: createDesignerApiData() })
    await page.goto('http://tianshu.local/index.html#/designer/table/101')
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
    await page.getByRole('button', { name: '添加行', exact: true }).click()
    const addAction = page.getByRole('button', { name: '添加动作', exact: true }).first()
    await expect(addAction).toBeVisible()
    for (const state of ['normal', 'hover', 'focus']) {
      if (state === 'hover') await addAction.hover()
      if (state === 'focus') {
        await page.mouse.move(0, 0)
        await addAction.focus()
      }
      await expect.poll(async () => addAction.evaluate(element => {
        const style = getComputedStyle(element)
        const canvas = document.createElement('canvas')
        canvas.width = canvas.height = 1
        const context = canvas.getContext('2d')
        const luminance = color => {
          context.fillStyle = color
          context.fillRect(0, 0, 1, 1)
          const channels = Array.from(context.getImageData(0, 0, 1, 1).data).slice(0, 3).map(channel => {
            const value = channel / 255
            return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
          })
          return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
        }
        const text = luminance(style.color)
        const background = luminance(style.backgroundColor)
        return (Math.max(text, background) + 0.05) / (Math.min(text, background) + 0.05)
      }), { message: `${accentPreset} / ${state} 文字对比度` }).toBeGreaterThanOrEqual(4.5)
    }
    const actions = page.locator('.dt-rule-card').first().locator('.dt-act-field')
    const count = await actions.count()
    await addAction.click()
    await expect(actions).toHaveCount(count + 1)
    assertClean()
  })
}

for (const [name, path] of [['决策流', '/designer/flow/103'], ['决策树', '/designer/tree/102']]) {
  test(`${name}画布随主题预览和取消即时刷新网格`, async ({ page }) => {
    const { assertClean } = await installDistRoutes(page, { apiData: createDesignerApiData() })
    await page.goto(`http://tianshu.local/index.html#${path}`)
    const grid = page.locator('.lf-grid:not(.lf-mini-map .lf-grid) pattern g').first()
    await expect(grid).toHaveAttribute('fill', '#94a3b8')
    await page.getByRole('button', { name: '本地用户的账户菜单' }).click()
    await page.getByRole('menuitem', { name: '主题设置' }).click()
    await page.locator('[data-theme-scheme="DARK"]').click()
    await expect(grid).toHaveAttribute('fill', '#53617d')
    await page.getByRole('dialog', { name: '主题设置' }).getByRole('button', { name: '取消', exact: true }).click()
    await expect(grid).toHaveAttribute('fill', '#94a3b8')
    assertClean()
  })
}

const designers = [
  {
    name: '决策表',
    path: '/designer/table/101',
    title: '决策表配置',
    action: '添加行',
    itemSelector: '.dt-rule-card',
    loadsVariables: true
  },
  {
    name: '决策树',
    path: '/designer/tree/102',
    title: '决策树设计器',
    action: '执行动作',
    itemSelector: '.tree-canvas .lf-node:not(.lf-mini-map .lf-node)',
    loadsVariables: true
  },
  {
    name: '决策流',
    path: '/designer/flow/103',
    title: '决策流设计器',
    action: '执行动作',
    itemSelector: '.flow-canvas .lf-node:not(.lf-mini-map .lf-node)',
    loadsVariables: true
  },
  {
    name: '规则集',
    path: '/designer/ruleset/104',
    title: '规则集配置',
    action: '添加规则',
    itemSelector: '.rs-rule-card',
    loadsVariables: true
  },
  {
    name: '交叉表',
    path: '/designer/cross/105',
    title: '交叉表设计器',
    action: '添加行',
    itemSelector: '.ct-matrix tbody tr'
  },
  {
    name: '评分卡',
    path: '/designer/score/106',
    title: '评分卡设计器',
    action: '添加评分项',
    itemSelector: '.score-item-card'
  },
  {
    name: '复杂交叉表',
    path: '/designer/cross-adv/107',
    title: '复杂交叉表设计器',
    action: '添加行维度',
    itemSelector: '.dim-config-card',
    loadsVariables: true
  },
  {
    name: '复杂评分卡',
    path: '/designer/score-adv/108',
    title: '复杂评分卡设计器',
    action: '添加维度组',
    itemSelector: '.asc-group',
    loadsVariables: true
  }
]

// 初始数据只有正式版本；草稿只能由 UI 暂存请求生成。
function manualDraftFixtures(definitionId, modelJson = '{}') {
  const apiData = createDesignerApiData()
  if (definitionId === 108 && modelJson === '{}') {
    modelJson = JSON.stringify({ initialScore: 0, resultVar: { varCode: 'score' }, dimensionGroups: [], thresholds: [] })
  }
  const saves = []
  let draft = null
  const base = `/api/rule/definition/${definitionId}`
  apiData.set(`${base}/revisions`, () => draft ? [draft] : [])
  apiData.set(`${base}/published-versions`, [
    { id: 82, definitionId, version: 2 },
    { id: 81, definitionId, version: 1 }
  ])
  for (const id of [81, 82]) {
    apiData.set(`${base}/versions/${id}`, { id, definitionId, version: id - 80, modelJson })
  }
  apiData.set(`POST ${base}/designer/drafts`, ({ request }) => {
    const payload = request.postDataJSON()
    saves.push(payload)
    draft = { id: 901, definitionId, revisionNo: 3, state: 'DRAFT', lockVersion: 1, modelJson: payload.modelJson }
    return { revision: draft, compileSuccess: true, issues: [] }
  })
  apiData.set(`${base}/revisions/901`, () => draft)
  return { apiData, saves }
}

for (const designer of [...designers, { name: 'QL 脚本', path: '/designer/script/109' }]) {
  test(`${designer.name}慢加载使用页内 info，不遮挡页面且保留编辑保护`, async ({ page }) => {
    const definitionId = Number(designer.path.split('/').pop())
    const { apiData } = manualDraftFixtures(definitionId)
    let release
    const pendingVersion = new Promise(resolve => { release = resolve })
    apiData.set(`/api/rule/definition/${definitionId}/versions/82`, async () => {
      await pendingVersion
      return { id: 82, definitionId, version: 2, modelJson: '{}' }
    })
    const { requests, assertClean } = await installDistRoutes(page, { apiData })
    await page.setViewportSize({ width: 2560, height: 900 })
    await page.goto(`http://tianshu.local/index.html#${designer.path}`)
    const notice = page.getByRole('status', { name: '规则版本信息' })
    try {
      await expect(notice).toContainText('正在加载规则版本')
      await expect(page.getByRole('dialog')).toHaveCount(0)
      const layout = await notice.evaluate(element => {
        const box = element.getBoundingClientRect()
        const toolbar = element.closest('.uiue-compact-designer').firstElementChild
        const message = element.querySelector('.rule-draft-read-only__notice').getBoundingClientRect()
        const version = element.querySelector('.rule-designer-version-select').getBoundingClientRect()
        return { position: getComputedStyle(element).position, inToolbar: toolbar.contains(element), height: box.height, alignment: Math.abs(message.y + message.height / 2 - version.y - version.height / 2) }
      })
      expect(layout.position).toBe('static')
      expect(layout.height).toBeLessThan(120)
      expect(layout.inToolbar).toBe(true)
      expect(layout.alignment).toBeLessThanOrEqual(1)
      await expect(page.locator('main [data-action="save"]')).toBeDisabled()
      await expect(page.locator('[data-designer-back]')).toBeEnabled()
      expect(await page.locator('[data-designer-back]').evaluate(element => !!element.closest('[inert]'))).toBe(false)
      await expect(notice.getByRole('button', { name: '返回', exact: true })).toHaveCount(0)
      expect(await notice.evaluate(element => !!element.closest('[inert]'))).toBe(false)
      expect(await page.locator('main [data-action="save"]').evaluate(element => !!element.closest('[inert]'))).toBe(true)
      expect(requests.filter(request => request.method !== 'GET')).toEqual([])
    } finally {
      release()
    }
    await expect(notice).toHaveCount(0)
    await expect(page.getByRole('button', { name: '保存', exact: true })).toBeEnabled()
    expect(await page.locator('main [data-action="save"]').evaluate(element => !!element.closest('[inert]'))).toBe(false)
    assertClean()
  })

  test(`${designer.name}仅在明确暂存时创建服务端草稿，刷新后可恢复`, async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    const definitionId = Number(designer.path.split('/').pop())
    const { apiData, saves } = manualDraftFixtures(definitionId)
    const { requests, assertClean } = await installDistRoutes(page, { apiData })
    const writes = () => requests.filter(request => request.method !== 'GET')
    await page.goto(`http://tianshu.local/index.html#${designer.path}`)
    await expect(page.getByTestId('designer-version-select')).toContainText('生产 · 2')
    expect(writes()).toEqual([])
    await page.getByTestId('designer-version-select').getByText('生产 · 2', { exact: true }).click()
    await page.getByRole('option', { name: '生产 · 1', exact: true }).click()
    await expect(page).toHaveURL(/sourceType=VERSION&sourceId=81/)
    await expect(page.getByRole('status', { name: '规则版本信息' })).toHaveCount(0)
    await expect(page.getByTestId('designer-info').getByRole('status')).toContainText('已加载，尚未修改')
    expect(writes()).toEqual([])

    let count
    if (designer.action) {
      count = await page.locator(designer.itemSelector).count()
      await page.getByRole('button', { name: designer.action, exact: true }).first().click()
      await expect(page.locator(designer.itemSelector)).toHaveCount(count + 1)
    } else {
      await page.locator('.se-var-item').filter({ hasText: 'age' }).first().dblclick()
      await expect(page.getByRole('textbox', { name: /Editor content/ })).toHaveValue('age')
    }
    await expect(page.getByTestId('designer-info').getByRole('status')).toContainText('有未保存修改')
    expect(writes()).toEqual([])
    expect(await page.evaluate(() => [...Object.keys(sessionStorage), ...Object.keys(localStorage)]
      .filter(key => key.startsWith('tianshu:rule-designer-recovery')))).toEqual([])
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.getByTestId('designer-info').getByRole('status')).toContainText('草稿已保存')
    await expect(page).toHaveURL(/sourceType=REVISION&sourceId=901/)
    expect(saves).toHaveLength(1)
    expect(saves[0]).toMatchObject({ sourceType: 'VERSION', sourceId: '81' })
    expect(JSON.parse(saves[0].modelJson)).not.toEqual({})
    expect(writes()).toHaveLength(1)
    expect(writes().map(write => new URL(write.url).pathname)).toEqual([
      `/api/rule/definition/${definitionId}/designer/drafts`
    ])

    await page.reload()
    await expect(page.getByTestId('designer-version-select')).toContainText('草稿 · 3')
    if (designer.action) await expect(page.locator(designer.itemSelector)).toHaveCount(count + 1)
    else await expect(page.getByRole('textbox', { name: /Editor content/ })).toHaveValue('age')
    expect(writes()).toHaveLength(1)
    assertClean()
  })
}

test('复杂交叉表从草稿切到加载失败的生产版时，版本与提示始终位于同一顶部工具栏', async ({ page }, testInfo) => {
  const { apiData, saves } = manualDraftFixtures(107)
  const { assertClean } = await installDistRoutes(page, { apiData })
  await page.setViewportSize({ width: 1920, height: 900 })
  await page.goto('http://tianshu.local/index.html#/designer/cross-adv/107')
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page).toHaveURL(/sourceType=REVISION&sourceId=901/)
  const header = page.locator('.act-header')
  const version = page.getByTestId('designer-version-control')
  await expect(header.getByTestId('designer-version-control')).toHaveCount(1)
  const draftBox = await header.boundingBox()
  await page.screenshot({ path: testInfo.outputPath('draft-toolbar.png') })

  await page.route('**/api/rule/definition/107/versions/82', route => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ code: 400, message: '指定修订不存在、已删除或不属于当前规则' }),
  }))
  await version.getByTestId('designer-version-select').click()
  await page.getByRole('option', { name: '生产 · 2', exact: true }).click()
  await expect(header.getByTestId('draft-read-only')).toContainText('当前版本加载失败')
  await expect(header.getByTestId('designer-version-control')).toHaveCount(1)
  const geometry = await header.evaluate(element => {
    const title = element.querySelector('.act-title').getBoundingClientRect()
    const message = element.querySelector('.rule-draft-read-only__notice').getBoundingClientRect()
    const version = element.querySelector('.rule-designer-version-select').getBoundingClientRect()
    return { titleY: title.y + title.height / 2, messageY: message.y + message.height / 2, versionY: version.y + version.height / 2 }
  })
  expect(Math.abs(geometry.messageY - geometry.versionY)).toBeLessThanOrEqual(1)
  expect(Math.abs(geometry.titleY - geometry.versionY)).toBeLessThanOrEqual(1)
  expect((await header.boundingBox()).y).toBe(draftBox.y)
  await expect(page.getByTestId('designer-source-retry')).toBeEnabled()
  expect(await page.locator('.act-dim-row').evaluate(element => element.hasAttribute('inert'))).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('production-error-toolbar.png') })
  await version.getByRole('combobox').press('Escape')
  await page.setViewportSize({ width: 1280, height: 720 })
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(1280)
  await version.getByTestId('designer-version-select').click()
  await page.getByRole('option', { name: /^草稿 · 3/ }).click()
  await expect(header.getByTestId('draft-read-only')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeEnabled()
  expect(await page.locator('.act-dim-row').evaluate(element => element.hasAttribute('inert'))).toBe(false)
  expect(saves).toHaveLength(1)
  assertClean()
})

test('版本读取失败在页内重试恢复，不弹确认也不创建草稿', async ({ page }) => {
  const { apiData } = manualDraftFixtures(109, JSON.stringify({ script: 'result = 2' }))
  const { requests, assertClean } = await installDistRoutes(page, { apiData })
  let attempts = 0
  await page.route('**/api/rule/definition/109/versions/82', async route => {
    attempts++
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(attempts === 1
        ? platformResponse(null, 'REQUEST_REJECTED', '模拟版本读取失败')
        : platformResponse({ id: 82, definitionId: 109, version: 2, modelJson: '{"script":"result = 2"}' }))
    })
  })
  await page.goto('http://tianshu.local/index.html#/designer/script/109?sourceType=VERSION&sourceId=82')
  const notice = page.getByRole('status', { name: '规则版本信息' })
  await expect(notice).toContainText('当前版本加载失败')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.locator('main [data-action="save"]')).toBeDisabled()
  await notice.getByRole('button', { name: '重试', exact: true }).click()
  await expect(notice).toHaveCount(0)
  await expect(page.getByRole('textbox', { name: /Editor content/ })).toHaveValue('result = 2')
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeEnabled()
  expect(attempts).toBe(2)
  expect(requests.filter(request => request.method !== 'GET')).toEqual([])
  assertClean()
})

for (const colorScheme of ['LIGHT', 'DARK']) {
  test(`${colorScheme} 只读用户通过 info 区切版查看，无模态遮挡且文字清晰`, async ({ page }) => {
    await page.addInitScript(scheme => {
      localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({
        schemaVersion: 1, colorScheme: scheme, accentPreset: 'LIQUID_PURPLE',
        sidebarTheme: 'DARK', contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false
      }))
    }, colorScheme)
    const { apiData } = manualDraftFixtures(109, JSON.stringify({ script: 'result = 2' }))
    apiData.set('/api/auth/console/config', { loginEnabled: true })
    apiData.set('/api/auth/console/me', { username: 'reader', permissions: ['rule:view'] })
    apiData.set('POST /api/rule/console/activity', true)
    apiData.set('/api/auth/console/preferences/theme', {
      schemaVersion: 1, colorScheme, accentPreset: 'LIQUID_PURPLE',
      sidebarTheme: 'DARK', contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false
    })
    apiData.set('/api/rule/definition/109/versions/81', { id: 81, definitionId: 109, version: 1, modelJson: '{"script":"result = 1"}' })
    const { requests, assertClean } = await installDistRoutes(page, { apiData })
    await page.goto('http://tianshu.local/index.html#/designer/script/109')
    const notice = page.getByRole('status', { name: '规则版本信息' })
    await expect(notice).toContainText('当前账号没有规则编辑权限')
    await expect(page.locator('html')).toHaveAttribute('data-theme', colorScheme.toLowerCase())
    const contrast = await notice.evaluate(element => {
      const style = getComputedStyle(element)
      const rgba = color => {
        const values = color.match(/[\d.]+/g).map(Number)
        return [...values.slice(0, 3), values.length > 3 ? values[3] : 1]
      }
      const blend = (color, base) => color.slice(0, 3).map((value, index) => value * color[3] + base[index] * (1 - color[3]))
      // 信息区背景透明；按实际祖先背景合成，不能把 transparent 当成黑色。
      const ancestors = []
      for (let node = element; node; node = node.parentElement) ancestors.unshift(node)
      let backgroundChannels = [255, 255, 255]
      for (const node of ancestors) backgroundChannels = blend(rgba(getComputedStyle(node).backgroundColor), backgroundChannels)
      const luminance = color => {
        const channels = color.map(channel => {
          const value = channel / 255
          return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
        })
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
      }
      const text = luminance(blend(rgba(style.color), backgroundChannels))
      const background = luminance(backgroundChannels)
      return (Math.max(text, background) + 0.05) / (Math.min(text, background) + 0.05)
    })
    expect(contrast).toBeGreaterThanOrEqual(4.5)
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await notice.getByText('生产 · 2', { exact: true }).click()
    await page.getByRole('option', { name: '生产 · 1', exact: true }).click()
    await expect(page).toHaveURL(/sourceType=VERSION&sourceId=81/)
    await expect(notice).toContainText('版本 1')
    await expect(page.locator('.view-lines')).toContainText('result')
    expect(await page.locator('.se-body').evaluate(element => element.hasAttribute('inert'))).toBe(true)
    // 页面访问会记录审计事件，查看与切版不能写入规则或草稿。
    expect(requests.filter(request => request.method !== 'GET' && new URL(request.url).pathname !== '/api/rule/console/activity')).toEqual([])
    assertClean()
  })
}

test('重新进入默认最新版本，已有草稿需主动选择；切版取消保留未暂存内容', async ({ page }) => {
  const { apiData, saves } = manualDraftFixtures(109, JSON.stringify({ script: 'result = 2' }))
  apiData.set('/api/rule/definition/109/versions/81', {
    id: 81, definitionId: 109, version: 1, modelJson: JSON.stringify({ script: 'result = 1' })
  })
  const { assertClean } = await installDistRoutes(page, { apiData })
  await page.goto('http://tianshu.local/index.html#/designer/script/109')
  const editor = page.getByRole('textbox', { name: /Editor content/ })
  await expect(editor).toHaveValue('result = 2')
  await editor.press('Control+End')
  await editor.press('Space')
  await expect(page.getByTestId('designer-info').getByRole('status')).toContainText('有未保存修改')
  await page.getByTestId('designer-version-select').getByText('生产 · 2', { exact: true }).click()
  await page.getByRole('option', { name: '生产 · 1', exact: true }).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(editor).toHaveValue('result = 2 ')
  expect(saves).toHaveLength(0)
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByTestId('designer-info').getByRole('status')).toContainText('草稿已保存')
  await page.goto('http://tianshu.local/index.html#/designer/script/109')
  await expect(editor).toHaveValue('result = 2')
  await page.getByTestId('designer-version-select').getByText('生产 · 2', { exact: true }).click()
  await page.getByRole('option', { name: /^草稿 · 3/ }).click()
  await expect(editor).toHaveValue('result = 2 ')
  expect(saves).toHaveLength(1)
  assertClean()
})

async function expectDesignerShell(page, title, saveButtonName) {
  const main = page.getByRole('main')
  await expect(main.getByText(title, { exact: true })).toBeVisible()
  const save = main.getByRole('button', { name: saveButtonName })
  await expect(save).toBeVisible()
  await expect(save).toBeInViewport({ ratio: 0.6 })
  const metrics = await page.evaluate(() => ({
    viewport: window.innerWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth
  }))
  expect(metrics.document).toBeLessThanOrEqual(metrics.viewport + 1)
  expect(metrics.body).toBeLessThanOrEqual(metrics.viewport + 1)
}

function createConnectedGraphModel() {
  const logicflow = {
    nodes: [
      {
        id: 'start',
        type: 'start-event',
        x: 260,
        y: 300,
        properties: { nodeName: '开始', nodeCode: 'START', nodeDesc: '流程开始节点' }
      },
      {
        id: 'task',
        type: 'script-task',
        x: 560,
        y: 300,
        properties: { nodeName: '执行动作', nodeCode: 'TASK', actionData: [] }
      }
    ],
    edges: [
      {
        id: 'edge-1',
        type: 'polyline',
        sourceNodeId: 'start',
        targetNodeId: 'task',
        sourceAnchorId: 'start_1',
        targetAnchorId: 'task_3',
        properties: { conditionName: '已有分支', priority: 10 }
      }
    ]
  }
  return {
    nodes: [
      { id: 'start', type: 'start', name: '开始', x: 260, y: 300, actionData: [] },
      { id: 'task', type: 'task', name: '执行动作', x: 560, y: 300, actionData: [] }
    ],
    edges: [
      {
        id: 'edge-1',
        source: 'start',
        target: 'task',
        name: '已有分支',
        conditionExpression: '',
        priority: 10
      }
    ],
    defaultEdgeLineType: 'polyline',
    logicflow
  }
}

for (const designer of [
  { name: '决策树', definitionId: 102, path: '/designer/tree/102', canvasClass: '.tree-canvas' },
  { name: '决策流', definitionId: 103, path: '/designer/flow/103', canvasClass: '.flow-canvas' }
]) {
  test(`${designer.name}点击条件编辑器空白区域时清除字段输入焦点`, async ({ page }) => {
    const modelJson = JSON.stringify({
      defaultEdgeLineType: 'polyline',
      logicflow: {
        nodes: [
          {
            id: 'start',
            type: 'start-event',
            x: 260,
            y: 300,
            properties: { nodeName: '开始', nodeCode: 'START' }
          },
          {
            id: 'task',
            type: 'script-task',
            x: 560,
            y: 300,
            properties: { nodeName: '执行动作', nodeCode: 'TASK', actionData: [] }
          }
        ],
        edges: [{
          id: 'edge-1',
          type: 'polyline',
          sourceNodeId: 'start',
          targetNodeId: 'task',
          sourceAnchorId: 'start_1',
          targetAnchorId: 'task_3',
          properties: {
            conditionConfig: {
              type: 'group',
              op: 'AND',
              children: [{ type: 'leaf', leftOperand: null, operator: '==', rightOperand: null }]
            }
          }
        }]
      }
    })
    const apiData = createDesignerApiData()
    apiData.set(`/api/rule/definition/content/${designer.definitionId}`, {
      definitionId: designer.definitionId,
      modelJson,
      scriptMode: 'visual'
    })
    apiData.set(`/api/rule/definition/${designer.definitionId}/revisions`, [{
      id: 2000 + designer.definitionId,
      definitionId: designer.definitionId,
      revisionNo: 1,
      state: 'DRAFT',
      lockVersion: 0,
      modelJson,
      createTime: '2026-07-23 12:00:00'
    }])
    const { assertClean } = await installDistRoutes(page, { apiData })
    await page.goto(`http://tianshu.local/index.html#${designer.path}`)

    const edge = page.locator(`${designer.canvasClass} .lf-edge:not(.lf-mini-map .lf-edge)`).first()
    await expect(edge).toBeVisible()
    await edge.locator('.lf-edge-append').click({ force: true })

    const leftField = page.getByPlaceholder('选择左操作数...')
    await expect(leftField).toBeVisible()
    await leftField.click()
    const picker = page.getByRole('dialog', { name: '字段选择器', exact: true })
    await picker.getByRole('row', { name: /年龄.*age/ }).dblclick()
    await expect(leftField).toHaveValue(/age/)

    const blankHint = page.getByText('清空整个条件组时为默认分支；未完成的条件不会视为默认分支。', { exact: true })
    await blankHint.click()
    await expect(leftField).not.toBeFocused()

    const operator = page.locator('.cg-field--op .el-select__wrapper').first()
    await operator.click()
    await expect.poll(() => operator.evaluate(element => element.classList.contains('is-focused'))).toBe(true)
    await blankHint.click()
    await expect.poll(() => operator.evaluate(element => element.classList.contains('is-focused'))).toBe(false)
    assertClean()
  })
}

for (const designer of designers) {
  test(`${designer.name}设计器可加载变量、显示工具栏并新增配置项`, async ({ page }) => {
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
    await page.goto(`http://tianshu.local/index.html#${designer.path}`)

    await expectDesignerShell(page, designer.title, '编译')
    await expect(page.getByRole('button', { name: '保存' })).toBeVisible()
    await expect(page.getByRole('button', { name: '测试', exact: true })).toBeEnabled()
    if (designer.loadsVariables) {
      await expect.poll(() => requests.some(request =>
        new URL(request.url).pathname === `/api/rule/definition/${designer.path.split('/').pop()}/reference-catalog`
      )).toBe(true)
      await expect(page.getByTestId('designer-info')).toContainText('已加载 7 个可选引用') // 包含对象和模型整体
    }
    const items = page.locator(designer.itemSelector)
    const before = await items.count()
    await page.getByRole('button', { name: designer.action, exact: true }).first().click()
    await expect(items).toHaveCount(before + 1)
    expect(pageErrors).toEqual([])
    expect(consoleErrors).toEqual([])
    assertClean()
  })
}

for (const designer of designers.filter(item => ['决策树', '决策流'].includes(item.name))) {
  test(`${designer.name}节点拖动后吸附到 20px 网格`, async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    const { assertClean } = await installDistRoutes(page, {
      apiData: createDesignerApiData()
    })
    await page.goto(`http://tianshu.local/index.html#${designer.path}`)

    await expect(page.getByRole('button', { name: '开始', exact: true })).toBeDisabled()
    await page.getByRole('button', { name: '执行动作', exact: true }).click()
    const canvasClass = designer.name === '决策树' ? '.tree-canvas' : '.flow-canvas'
    const node = page.locator(
      `${canvasClass} .lf-node:not(.lf-mini-map .lf-node)`
    ).last()
    await expect(node).toBeVisible()
    const before = await node.boundingBox()
    expect(before).toBeTruthy()

    await page.mouse.move(before.x + before.width / 2, before.y + before.height / 2)
    await page.mouse.down()
    await page.mouse.move(
      before.x + before.width / 2 + 47,
      before.y + before.height / 2 + 43,
      { steps: 10 }
    )
    await page.mouse.up()

    const after = await node.boundingBox()
    expect(after).toBeTruthy()
    const deltaX = Math.round(after.x - before.x)
    const deltaY = Math.round(after.y - before.y)
    expect(Math.abs(deltaX) + Math.abs(deltaY)).toBeGreaterThan(0)
    expect(deltaX % 20).toBe(0)
    expect(deltaY % 20).toBe(0)
    assertClean()
  })
}

for (const designer of designers.filter(item => ['决策树', '决策流'].includes(item.name))) {
  test(`${designer.name}可调整已有连线的目标锚点并持久化`, async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    const definitionId = designer.name === '决策树' ? 102 : 103
    const canvasClass = designer.name === '决策树' ? '.tree-canvas' : '.flow-canvas'
    let modelJson = JSON.stringify(createConnectedGraphModel())
    let savedPayload = null
    const apiData = createDesignerApiData()
    apiData.set(`/api/rule/definition/content/${definitionId}`, () => ({
      definitionId,
      modelJson,
      scriptMode: 'visual'
    }))
    apiData.set(`/api/rule/definition/${definitionId}/revisions`, () => [
      {
        id: 2000 + definitionId,
        definitionId,
        revisionNo: 1,
        state: 'DRAFT',
        lockVersion: 0,
        modelJson,
        createTime: '2026-07-23 12:00:00'
      }
    ])
    apiData.set(`/api/rule/definition/${definitionId}/revisions/${2000 + definitionId}`, () => ({
      id: 2000 + definitionId,
      definitionId,
      revisionNo: 1,
      state: 'DRAFT',
      lockVersion: savedPayload ? 1 : 0,
      modelJson,
    }))
    apiData.set(`POST /api/rule/definition/${definitionId}/designer/drafts`, async ({ request }) => {
      savedPayload = JSON.parse(request.postData())
      modelJson = savedPayload.modelJson
      return {
        revision: {
          id: 2000 + definitionId,
          definitionId,
          revisionNo: 1,
          state: 'DRAFT',
          lockVersion: 1,
          modelJson: savedPayload.modelJson
        },
        issues: []
      }
    })
    const { assertClean } = await installDistRoutes(page, { apiData })
    await page.goto(`http://tianshu.local/index.html#${designer.path}`)

    const edge = page.locator(`${canvasClass} .lf-edge:not(.lf-mini-map .lf-edge)`).first()
    await expect(edge).toBeVisible()
    await edge.locator('.lf-edge-append').click({ force: true })

    const adjustPoints = page.locator(`${canvasClass} .lf-edge-adjust-point`)
    await expect(adjustPoints).toHaveCount(2)
    await expect(page.getByText('拖动连线两端的圆点，可切换节点的连接锚点')).toBeVisible()
    const targetNode = page.locator(`${canvasClass} .lf-node:not(.lf-mini-map .lf-node)`).filter({ hasText: '执行动作' })
    const targetBox = await targetNode.boundingBox()
    const targetHandleBox = await adjustPoints.nth(1).boundingBox()
    expect(targetBox).toBeTruthy()
    expect(targetHandleBox).toBeTruthy()

    await page.mouse.move(
      targetHandleBox.x + targetHandleBox.width / 2,
      targetHandleBox.y + targetHandleBox.height / 2
    )
    await page.mouse.down()
    await page.mouse.move(targetBox.x + targetBox.width / 2, targetBox.y, { steps: 10 })
    await page.mouse.up()

    await page.getByRole('button', { name: '保存' }).click()
    const saveDialog = page.getByRole('dialog', { name: '保存草稿', exact: true })
    await saveDialog.getByText('覆盖当前草稿', { exact: true }).click()
    await saveDialog.locator('[data-action="confirm-save"]').click()
    await expect.poll(() => savedPayload).not.toBeNull()
    const savedModel = JSON.parse(savedPayload.modelJson)
    expect(savedModel.logicflow.edges[0]).toMatchObject({
      id: 'edge-1',
      sourceAnchorId: 'start_1',
      targetAnchorId: 'task_0',
      properties: { conditionName: '已有分支', priority: 10 }
    })
    await expect(saveDialog).toBeHidden()
    await expect(page).toHaveURL(new RegExp(`sourceType=REVISION.*sourceId=${2000 + definitionId}`))
    await page.reload()
    const reloadedEdge = page.locator(`${canvasClass} .lf-edge:not(.lf-mini-map .lf-edge)`).first()
    await expect(reloadedEdge).toBeVisible()
    await reloadedEdge.locator('.lf-edge-append path').first().click({ force: true })
    const reloadedTargetHandle = page.locator(`${canvasClass} .lf-edge-adjust-point`).nth(1)
    await expect(reloadedTargetHandle).toHaveAttribute('cx', '560')
    await expect(reloadedTargetHandle).toHaveAttribute('cy', '279')
    assertClean()
  })
}

test('结束节点范围弹窗完整容纳两项说明且不发生内容重叠', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createDesignerApiData()
  })
  await page.goto('http://tianshu.local/index.html#/designer/tree/102')

  await page.getByRole('button', { name: '结束', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '添加结束节点' })
  await expect(dialog).toBeVisible()
  const metrics = await dialog.locator('.scope-option').evaluateAll(options =>
    options.map(option => {
      const optionRect = option.getBoundingClientRect()
      const descriptionRect = option.querySelector('.scope-description').getBoundingClientRect()
      return {
        optionHeight: optionRect.height,
        contentFits: descriptionRect.bottom <= optionRect.bottom + 1
      }
    })
  )

  expect(metrics).toHaveLength(2)
  metrics.forEach(metric => {
    expect(metric.optionHeight).toBeGreaterThanOrEqual(72)
    expect(metric.contentFits).toBe(true)
  })
  const bodyOverflow = await dialog.locator('.el-dialog__body').evaluate(body => ({
    clientHeight: body.clientHeight,
    scrollHeight: body.scrollHeight
  }))
  expect(bodyOverflow.scrollHeight).toBeLessThanOrEqual(bodyOverflow.clientHeight + 1)
  assertClean()
})

test('判断节点只在菱形内部显示一次名称', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createDesignerApiData()
  })
  await page.goto('http://tianshu.local/index.html#/designer/tree/102')

  await page.getByRole('button', { name: '条件判断', exact: true }).click()
  const gateway = page.locator('.lf-node').filter({ hasText: '条件判断' })
  await expect(gateway).toBeVisible()
  const labels = (await gateway.locator('text').allTextContents())
    .map(text => text.trim())
    .filter(Boolean)

  expect(labels).toEqual(['条件判断'])
  assertClean()
})

test('QL 脚本编辑器可加载变量、插入字段并保持工具栏可操作', async ({ page }) => {
  const pageErrors = []
  const consoleErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  await page.setViewportSize({ width: 1440, height: 900 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createDesignerApiData()
  })
  await page.goto('http://tianshu.local/index.html#/designer/script/109')

  await expectDesignerShell(page, 'QL脚本编辑器', '编译')
  await expect(page.getByRole('button', { name: '保存' })).toBeVisible()
  await expect(page.getByRole('button', { name: '测试', exact: true })).toBeEnabled()
  const variable = page.locator('.se-var-item').filter({ hasText: 'age' }).first()
  await expect(variable).toBeVisible()
  await variable.dblclick()
  await expect(page.locator('.monaco-editor-container')).toBeVisible()
  await expect.poll(async () => page.locator('.view-lines').textContent()).toContain('age')
  expect(pageErrors).toEqual([])
  expect(consoleErrors).toEqual([])
  assertClean()
})

for (const colorScheme of ['LIGHT', 'DARK']) {
  test(`QL 脚本行内引用 ${colorScheme} 支持编辑、最长路径、撤销与原始脚本保存`, async ({ page }, testInfo) => {
    await page.addInitScript(scheme => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({
      schemaVersion: 1, colorScheme: scheme, accentPreset: 'THEME_BLUE', sidebarTheme: 'DARK', contentWidth: 'FLUID',
      fixedSidebar: true, colorWeak: false,
    })), colorScheme)
    const apiData = createDesignerApiData()
    apiData.get('/api/rule/dataobject/tree/1')[0].variables.push(
      { id: 5, varCode: 'orders', varLabel: '订单', varType: 'LIST' },
      { id: 6, varCode: 'orders.amount', varLabel: '订单金额', varType: 'NUMBER', parentFieldId: 5 },
    )
    apiData.set('/api/rule/function/project/1/all', [{ id: 77, funcCode: 'risk', funcName: '风险计算', returnType: 'NUMBER' }])
    apiData.set('/api/rule/function/picker-versions/77', [{ versionId: 777, version: 4 }])
    apiData.set('/api/rule/dataobject/picker-versions/10', [{ versionId: 501, version: 3 }])
    let modelJson = '{}'
    const revision = () => ({ id: 2109, definitionId: 109, revisionNo: 1, state: 'DRAFT', lockVersion: 0, modelJson })
    apiData.set('/api/rule/definition/109/revisions', () => [revision()])
    apiData.set('/api/rule/definition/109/revisions/2109', revision)
    const saved = []
    apiData.set('POST /api/rule/definition/109/designer/drafts', ({ request }) => {
      modelJson = request.postDataJSON().modelJson
      saved.push(JSON.parse(modelJson))
      return { revision: revision(), compileSuccess: true, issues: [] }
    })
    const { assertClean } = await installDistRoutes(page, { apiData })
    await page.setViewportSize({ width: 1600, height: 1000 })
    await page.goto('http://tianshu.local/index.html#/designer/script/109')
    await expect(page.locator('html')).toHaveAttribute('data-theme', colorScheme.toLowerCase())
    const editor = page.getByRole('textbox', { name: /Editor content/ })
    await expect(editor).toBeVisible()
    // 所有脚本编辑都通过键盘和字段列表完成，API 只保存 UI 实际提交的脚本。
    await page.locator('.se-var-item').filter({ has: page.locator('.var-code', { hasText: /^TaxRequest.orders$/ }) }).dblclick()
    await editor.press('End')
    await page.keyboard.insertText('[0].amount + ')
    await page.locator('.se-var-item').filter({ has: page.locator('.var-code', { hasText: /^age$/ }) }).dblclick()
    await editor.press('Home')
    await page.keyboard.insertText('result = ')
    await editor.press('End')
    await page.keyboard.insertText(';\ncalculated = ')
    await page.locator('.se-var-item').filter({ hasText: 'risk()' }).dblclick()
    await editor.press('ArrowLeft')
    await page.keyboard.insertText('result')
    await editor.press('End')
    await page.keyboard.insertText(';\n// age 与 TaxRequest.orders 不应渲染\n"age";\n_result = {"age": age, "TaxRequest.orders.amount": risk(age), "other": {"age": age}};\n_result')
    const script = 'result = TaxRequest.orders[0].amount + age;\ncalculated = risk(result);\n// age 与 TaxRequest.orders 不应渲染\n"age";\n_result = {"age": age, "TaxRequest.orders.amount": risk(age), "other": {"age": age}};\n_result'
    const labels = page.locator('.view-lines .se-reference-name')
    const versions = page.locator('.view-lines .se-reference-version')
    await expect(labels).toHaveCount(5)
    await expect(labels.nth(0)).toContainText('订单金额')
    await expect(labels.nth(1)).toContainText('年龄')
    await expect(labels.nth(2)).toContainText('风险计算')
    await expect(versions).toHaveText(['v3', 'v1', 'v4', 'v1', 'v3'])
    await expect(page.locator('.view-lines .se-reference-code').filter({ hasText: /^"age"$/ })).toHaveCount(1)
    await expect(page.locator('.view-lines .se-reference-code').filter({ hasText: /^"TaxRequest.orders.amount"$/ })).toHaveCount(1)
    const ageCode = page.locator('.view-lines .se-reference-code').filter({ hasText: /^age$/ }).first()
    const ageBox = await ageCode.boundingBox()
    await page.mouse.click(ageBox.x + ageBox.width - 1, ageBox.y + ageBox.height / 2)
    await page.keyboard.insertText('X')
    await expect(editor).toHaveValue(/\+ ageX;/)
    await editor.press('Control+z')
    const readReferenceStyles = () => page.locator('.view-lines').evaluate(element =>
      ['code', 'name', 'version'].map(part => {
        const style = getComputedStyle(element.querySelector('.se-reference-' + part))
        const canvas = document.createElement('canvas')
        canvas.width = canvas.height = 1
        const context = canvas.getContext('2d')
        context.fillStyle = style.backgroundColor
        context.fillRect(0, 0, 1, 1)
        return { color: style.color, background: style.backgroundColor, alpha: context.getImageData(0, 0, 1, 1).data[3], border: style.borderWidth, radius: style.borderRadius }
      }))
    const referenceStyles = await readReferenceStyles()
    expect(new Set(referenceStyles.map(style => style.color)).size).toBe(3)
    expect(new Set(referenceStyles.map(style => style.background)).size).toBe(3)
    for (const style of referenceStyles) {
      expect(style.alpha).toBe(255)
      expect(style.border).toBe('0px')
      expect(style.radius).toBe('0px')
    }
    await page.locator('.se-statusbar .el-switch').click()
    await expect(labels).toHaveCount(0)
    await page.locator('.se-statusbar .el-switch').click()
    await expect(labels).toHaveCount(5)
    await editor.press('Control+Home')
    await editor.press('Control+Shift+End')
    await editor.press('Backspace')
    await expect(labels).toHaveCount(0)
    await editor.press('Control+z')
    await editor.press('ArrowRight')
    await expect(labels).toHaveCount(5)
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await page.getByRole('dialog', { name: '保存草稿', exact: true }).getByRole('button', { name: '保存草稿', exact: true }).click()
    await expect(page.getByTestId('designer-info')).toContainText('草稿已保存')
    expect(saved).toHaveLength(1)
    expect(saved[0].script.replace(/\r\n/g, '\n')).toBe(script)
    expect(saved[0].scriptVarRefs).toEqual(expect.arrayContaining([
      { refCode: 'TaxRequest.orders', varId: 5, refType: 'DATA_OBJECT' },
      { refCode: 'age', varId: 1, refType: 'VARIABLE' },
      { refCode: 'risk', varId: 77, refType: 'FUNCTION' },
    ]))
    await page.reload()
    await expect(labels).toHaveCount(5)
    await expect(labels.nth(0)).toContainText('订单金额')
    await expect(versions).toHaveText(['v3', 'v1', 'v4', 'v1', 'v3'])
    await page.screenshot({ path: testInfo.outputPath(`ql-inline-${colorScheme}.png`) })
    await page.getByRole('button', { name: '本地用户的账户菜单' }).click()
    await page.getByRole('menuitem', { name: '主题设置' }).click()
    await page.locator('[data-accent="MIDNIGHT_PURPLE"]').click()
    await expect.poll(async () => (await readReferenceStyles())[0].color).not.toBe(referenceStyles[0].color)
    expect(new Set((await readReferenceStyles()).map(style => style.color)).size).toBe(3)
    expect(new Set((await readReferenceStyles()).map(style => style.background)).size).toBe(3)
    await page.getByRole('dialog', { name: '主题设置' }).getByRole('button', { name: '取消', exact: true }).click()
    await page.setViewportSize({ width: 1024, height: 768 })
    await expect(page.locator('.se-statusbar .el-switch')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(1024)
    assertClean()
  })
}
