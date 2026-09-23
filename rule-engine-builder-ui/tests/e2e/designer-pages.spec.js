const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')

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
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(`http://tianshu.local/index.html#${designer.path}`)
    const notice = page.getByRole('status', { name: '规则版本信息' })
    try {
      await expect(notice).toContainText('正在加载规则版本')
      await expect(page.getByRole('dialog')).toHaveCount(0)
      const layout = await notice.evaluate(element => {
        const box = element.getBoundingClientRect()
        const toolbar = element.parentElement.nextElementSibling.getBoundingClientRect()
        return { position: getComputedStyle(element).position, bottom: box.bottom, toolbarTop: toolbar.top, height: box.height }
      })
      expect(layout.position).toBe('static')
      expect(layout.height).toBeLessThan(120)
      expect(layout.bottom).toBeLessThanOrEqual(layout.toolbarTop)
      await expect(page.locator('main [data-action="save"]')).toBeDisabled()
      await expect(notice.getByRole('button', { name: '返回', exact: true })).toBeEnabled()
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
    await expect(page.getByTestId('designer-version-select')).toContainText('发布版本 v2')
    expect(writes()).toEqual([])
    await page.getByTestId('designer-version-select').getByText('发布版本 v2', { exact: true }).click()
    await page.getByRole('option', { name: '发布版本 v1', exact: true }).click()
    await expect(page).toHaveURL(/sourceType=VERSION&sourceId=81/)
    await expect(page.getByRole('status', { name: '规则版本信息' })).toHaveCount(0)
    await expect(page.getByRole('status')).toContainText('已加载，尚未修改')
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
    await expect(page.getByRole('status')).toContainText('有未保存修改')
    expect(writes()).toEqual([])
    expect(await page.evaluate(() => [...Object.keys(sessionStorage), ...Object.keys(localStorage)]
      .filter(key => key.startsWith('tianshu:rule-designer-recovery')))).toEqual([])
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.getByRole('status')).toContainText('草稿已保存')
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
        ? { code: 400, message: '模拟版本读取失败' }
        : { code: 200, data: { id: 82, definitionId: 109, version: 2, modelJson: '{"script":"result = 2"}' } })
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
      const luminance = color => {
        const channels = color.match(/[\d.]+/g).slice(0, 3).map(Number).map(channel => {
          const value = channel / 255
          return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
        })
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
      }
      const text = luminance(style.color)
      const background = luminance(style.backgroundColor)
      return (Math.max(text, background) + 0.05) / (Math.min(text, background) + 0.05)
    })
    expect(contrast).toBeGreaterThanOrEqual(4.5)
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await notice.getByText('发布版本 v2', { exact: true }).click()
    await page.getByRole('option', { name: '发布版本 v1', exact: true }).click()
    await expect(page).toHaveURL(/sourceType=VERSION&sourceId=81/)
    await expect(notice).toContainText('版本 1')
    await expect(page.locator('.view-lines')).toContainText('result')
    expect(await page.locator('.se-body').evaluate(element => element.hasAttribute('inert'))).toBe(true)
    expect(requests.filter(request => request.method !== 'GET')).toEqual([])
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
  await expect(page.getByRole('status')).toContainText('有未保存修改')
  await page.getByTestId('designer-version-select').getByText('发布版本 v2', { exact: true }).click()
  await page.getByRole('option', { name: '发布版本 v1', exact: true }).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(editor).toHaveValue('result = 2 ')
  expect(saves).toHaveLength(0)
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('草稿已保存')
  await page.goto('http://tianshu.local/index.html#/designer/script/109')
  await expect(editor).toHaveValue('result = 2')
  await page.getByTestId('designer-version-select').getByText('发布版本 v2', { exact: true }).click()
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
    const popoverId = await leftField.getAttribute('aria-describedby')
    const popover = page.locator(`[id="${popoverId}"]`)
    await popover.locator('.vp-cat-item').filter({ hasText: '普通变量' }).click()
    await popover.locator('.vp-row').filter({ hasText: 'age' }).click()
    await expect(leftField).toHaveValue(/age/)
    await expect(leftField).toBeFocused()

    const blankHint = page.getByText('条件为空表示默认分支（else）', { exact: true })
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
        new URL(request.url).pathname === '/api/rule/variable/project/1'
      )).toBe(true)
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
