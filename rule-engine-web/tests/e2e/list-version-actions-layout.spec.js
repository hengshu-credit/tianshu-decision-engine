const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDocsApiData } = require('./support/docsFixtures.cjs')

async function setVersionTheme(page, colorScheme) {
  await page.addInitScript(value => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify(value)), {
    schemaVersion: 3, colorScheme, accentMode: 'PRESET', accentPreset: 'LIQUID_PURPLE',
    customSolidColor: '#2639E9', customGradientColors: ['#2639E9', '#873FF2'],
    customGradientType: 'LINEAR', customGradientAngle: 135, navigationLayout: 'LEFT',
    sidebarTheme: 'DARK', contentWidth: 'FLUID', tableScrollMode: 'FIXED', fixedSidebar: true, colorWeak: false,
  })
}

for (const [type, route, listUrl, snapshot, expected] of [
  ['VARIABLE', '/variable', '/api/rule/variable/list', { varCode: 'risk_score', varLabel: '风险分', varType: 'NUMBER', varSource: 'DB', sourceConfig: '{"dbDatasourceId":1,"sql":"select score from risk","maxRows":1}', options: [{ value: 0, label: '低风险' }] }, ['数据库查询', '查询 SQL', '低风险']],
  ['MODEL', '/model', '/api/rule/model/list', { modelCode: 'credit_model', modelName: '授信评分模型', modelType: 'PMML', inputFields: [{ fieldName: '年龄', fieldType: 'NUMBER', required: true }], outputFields: [{ fieldName: '违约概率', fieldType: 'NUMBER' }] }, ['输入字段', '年龄', '违约概率']],
]) {
  test(`${type} 版本入口展示业务字段和嵌套配置`, async ({ page }) => {
    const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
    const data = createDocsApiData()
    data.set(listUrl, { records: [{ ...snapshot, id: '9001', status: 1, scope: 'GLOBAL' }], total: 1 })
    data.set(`/api/rule/resource-versions/${type}/9001`, { latestVersionId: '51', versions: [{ versionId: '51', version: 1, revision: 1, latest: true }], history: [{ id: '51', versionNo: 1, revisionNo: 1, snapshotJson: JSON.stringify(snapshot) }], drafts: [] })
    data.set(`/api/rule/resource-versions/${type}/9001/events`, [])
    const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: data })
    await page.goto(`${origin}/#${route}`)
    await page.getByRole('button', { name: '版本', exact: true }).first().click()
    const drawer = page.getByRole('dialog', { name: '版本管理', exact: true })
    for (const text of expected) await expect(drawer.locator('.version-preview-summary')).toContainText(text)
    await expect(drawer.locator('pre.version-preview')).toHaveCount(0)
    harness.assertClean()
  })
}

test('文章版本左右呈现排版后的正文，底部可展开原始 JSON', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const data = new Map()
  const history = [1, 2].map(versionNo => ({ id: String(versionNo), versionNo, revisionNo: 1, snapshotJson: JSON.stringify({ title: '授信策略调整', body: `## 调整说明\n\n最低年龄 **${versionNo === 1 ? 18 : 21} 岁**。`, scope: 'GLOBAL' }) }))
  data.set('/api/rule/ledger/articles/71/versions', { latestVersionId: '2', versions: history.map(item => ({ versionId: item.id, version: item.versionNo, revision: 1, latest: item.id === '2' })), history, drafts: [], canEdit: false })
  data.set('/api/rule/ledger/articles/71/version-events', [])
  data.set('/api/rule/ledger/revisions/2', { articleId: '71', title: '授信策略调整', body: '最低年龄 21 岁。', scope: 'GLOBAL' })
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: data })
  await page.goto(`${origin}/#/ledger/articles/71`)
  await page.getByRole('button', { name: '版本', exact: true }).click()
  const drawer = page.getByRole('dialog', { name: '版本管理', exact: true })
  await expect(drawer.locator('.ledger-version-preview strong')).toHaveText('21 岁')
  await drawer.locator('.version-comparison-toolbar .el-select').click()
  await page.getByRole('option', { name: 'v1 · 修订 1', exact: true }).click()
  await expect(drawer.locator('.ledger-version-columns section').first().getByRole('heading', { name: '调整说明' })).toBeVisible()
  await expect(drawer.locator('.ledger-version-columns section').first().locator('strong')).toHaveText('18 岁')
  await expect(drawer.locator('.ledger-version-columns section').last().locator('strong')).toHaveText('21 岁')
  await drawer.getByRole('button', { name: '查看原始 JSON（完整配置）' }).click()
  await expect(drawer.locator('.json-version-diff')).toBeVisible()
  harness.assertClean()
})

for (const colorScheme of ['LIGHT', 'DARK']) {
  test(`${colorScheme} 业务版本预览、左右对比和原始 JSON 可通过界面完整操作`, async ({ page }, testInfo) => {
    const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
    await setVersionTheme(page, colorScheme)
    const data = createDocsApiData()
    const rule = data.get('/api/rule/definition/list').records[0]
    const snapshot = threshold => JSON.stringify({ ruleCode: 'credit_entry', ruleName: '个人信贷准入', modelType: 'TABLE', description: '根据申请年龄判断是否进入授信流程', content: { modelJson: JSON.stringify({ hitPolicy: 'FIRST', rules: [{ ruleName: '成年客户准入', conditionRoot: { type: 'group', op: 'AND', children: [{ type: 'leaf', leftOperand: { kind: 'REFERENCE', refId: '101', refType: 'VARIABLE', label: '申请年龄', code: 'age', valueType: 'NUMBER', version: 1 }, operator: '>=', rightOperand: { kind: 'LITERAL', value: threshold, valueType: 'NUMBER' } }] }, actions: [{ targetOperand: { kind: 'REFERENCE', refId: '102', refType: 'VARIABLE', label: '准入结果', code: 'decision', valueType: 'STRING', version: 1 }, valueOperand: { kind: 'LITERAL', value: '允许申请', valueType: 'STRING' } }] }] }) }, inputFieldsJson: [{ varId: '101', fieldName: '申请年龄', required: true }], outputFieldsJson: [{ varId: '102', fieldName: '准入结果' }] })
    data.set(`/api/rule/resource-versions/RULE/${rule.id}`, {
      latestVersionId: '22', drafts: [], versions: [{ versionId: '22', version: 2, revision: 1, latest: true }, { versionId: '21', version: 1, revision: 1 }],
      history: [{ id: '22', versionNo: 2, revisionNo: 1, snapshotJson: snapshot(21) }, { id: '21', versionNo: 1, revisionNo: 1, snapshotJson: snapshot(18) }],
    })
    data.set(`/api/rule/resource-versions/RULE/${rule.id}/events`, [])
    const { assertClean } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: data })
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.goto(`${origin}/#/rule`)
    await expect(page.locator('html')).toHaveAttribute('data-theme', colorScheme.toLowerCase())
    await page.getByRole('button', { name: '版本', exact: true }).first().click()
    const drawer = page.getByRole('dialog', { name: '版本管理', exact: true })
    const preview = drawer.locator('.version-preview-summary')
    await expect(preview).toContainText('个人信贷准入')
    await expect(preview).toContainText('申请年龄')
    await expect(preview).toContainText('允许申请')
    await expect(drawer.locator('.version-preview')).toHaveCount(0)
    await drawer.locator('.version-comparison-toolbar .el-select').click()
    await page.getByRole('option', { name: 'v1 · 修订 1', exact: true }).click()
    await expect(preview.locator('.business-preview-head')).toContainText('v1 · 修订 1')
    const rules = preview.locator('[data-section="rule:rules"]')
    const branch = rules.locator('.business-preview-card').first()
    await branch.getByRole('button', { name: /成年客户准入/ }).click()
    await expect(branch.locator('.content-ref__name')).toHaveCount(0)
    await expect(rules.locator('.business-preview-card').last().locator('.content-ref__name').first()).toBeVisible()
    await branch.getByRole('button', { name: /成年客户准入/ }).click()
    await preview.getByText('只看差异', { exact: true }).click()
    await expect(preview.getByRole('heading', { name: '基本信息' })).toHaveCount(0)
    await expect(rules).toBeVisible()
    await drawer.screenshot({ path: testInfo.outputPath(`tree-comparison-${colorScheme}.png`), animations: 'disabled' })
    await preview.getByText('只看差异', { exact: true }).click()
    await expect(rules.locator('.business-preview-card').first()).toContainText('18')
    await expect(rules.locator('.business-preview-card').last()).toContainText('21')
    await expect(rules).toHaveClass(/is-changed/)
    await drawer.getByRole('button', { name: '交换左右' }).click()
    await expect(rules.locator('.business-preview-card').first()).toContainText('21')
    await expect(rules.locator('.business-preview-card').last()).toContainText('18')
    for (const width of [1440, 768, 390]) {
      await page.setViewportSize({ width, height: 1000 })
      await expect(preview).toBeVisible()
      expect(await preview.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
      expect(await drawer.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
      await rules.scrollIntoViewIfNeeded()
      await page.screenshot({ path: testInfo.outputPath(`business-preview-${colorScheme}-${width}.png`) })
    }
    await page.setViewportSize({ width: 1440, height: 1000 })
    await drawer.getByRole('button', { name: '查看原始 JSON（完整配置）' }).click()
    await expect(drawer.locator('.json-version-diff')).toBeVisible()
    await drawer.getByRole('button', { name: '退出对比' }).click()
    await expect(drawer.locator('.json-version-diff')).toHaveCount(0)
    await drawer.getByRole('button', { name: '查看原始 JSON（完整配置）' }).click()
    await expect(drawer.locator('pre.version-preview')).toContainText('modelJson')
    await drawer.getByRole('tab', { name: '版本与修订', exact: true }).click()
    await drawer.getByRole('button', { name: '预览', exact: true }).first().click()
    await expect(preview).toContainText('v2 · 修订 1')
    await expect(drawer.locator('pre.version-preview')).toHaveCount(0)
    assertClean()
  })
}

test('切换历史修订按各自配置还原矩阵、评分区间、流程画布和 QL 源码', async ({ page }) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const data = createDocsApiData()
  const rule = data.get('/api/rule/definition/list').records[0]
  const models = [
    ['CROSS', { rowHeaders: ['成年', '未成年'], colHeaders: ['低风险', '高风险'], cells: [[10, 0], [0, -5]] }],
    ['SCORE', { scoreItems: [{ condition: '按期还款', score: 8, weight: 2 }], thresholds: [{ min: 60, max: 90, result: '人工审核' }] }],
    ['FLOW', { nodes: [{ id: 'a', type: 'start', name: '申请开始' }, { id: 'review', type: 'task', name: '核定额度' }, { id: 'b', type: 'end', name: '完成审批' }], edges: [{ source: 'a', target: 'review' }, { source: 'review', target: 'b' }] }],
    ['SCRIPT', { script: 'return 17;' }],
    ['CROSS_ADV', { rowDimensions: [{ varCode: 'age', segments: [{ label: '青年' }, { label: '中年' }] }], colDimensions: [{ varCode: 'risk', segments: [{ label: '正常' }] }], cells: [[{ kind: 'LITERAL', value: 3 }], [{ kind: 'LITERAL', value: 6 }]] }],
  ]
  const history = models.map(([modelType, model], index) => ({
    id: String(index + 1), versionNo: index + 1, revisionNo: 1,
    snapshotJson: JSON.stringify({ ruleName: '版本样例', modelType, content: { modelJson: JSON.stringify(model) } }),
  }))
  data.set(`/api/rule/resource-versions/RULE/${rule.id}`, {
    latestVersionId: '1', drafts: [], history,
    versions: history.map(row => ({ versionId: row.id, version: row.versionNo, revision: 1, latest: row.id === '1' })),
  })
  data.set(`/api/rule/resource-versions/RULE/${rule.id}/events`, [])
  const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: data })
  await page.goto(`${origin}/#/rule`)
  await page.getByRole('button', { name: '版本', exact: true }).first().click()
  const drawer = page.getByRole('dialog', { name: '版本管理', exact: true })
  const select = async version => {
    await drawer.locator('.uiue-filter-toolbar .el-select').first().click()
    await page.getByRole('option', { name: 'v' + version, exact: true }).click()
  }
  const matrix = drawer.locator('.result-matrix')
  await expect(matrix.getByRole('columnheader', { name: '高风险' })).toBeVisible()
  await expect(matrix.getByRole('row', { name: /未成年/ }).getByRole('cell').last()).toContainText('-5')
  await select(2)
  await expect(drawer.locator('[data-section="rule:scoreItems"] table').first()).toContainText('分值')
  await expect(drawer.locator('.threshold-item')).toContainText('60 ≤ 分数 < 90')
  await expect(drawer.locator('.threshold-item')).toContainText('人工审核')
  await expect(drawer.locator('.threshold-item button, .threshold-item input')).toHaveCount(0)
  await select(3)
  await expect(drawer.locator('.snapshot-graph__canvas .lf-node')).toHaveCount(3)
  await expect(drawer.locator('.snapshot-graph__canvas .lf-edge')).toHaveCount(2)
  await expect(drawer.locator('.snapshot-graph__canvas')).toContainText('核定额度')
  await drawer.getByText('节点配置（3）', { exact: true }).click()
  await expect(drawer.locator('.graph-details')).toContainText('完成审批')
  await select(4)
  await expect(drawer.locator('[data-section="rule:script"] .view-lines')).toContainText('return')
  await select(5)
  await expect(matrix.getByRole('row', { name: /中年/ }).getByRole('cell')).toContainText('6')
  harness.assertClean()
})

for (const colorScheme of ['LIGHT', 'DARK']) {
  test(`${colorScheme} 版本管理加载无灰色蒙版且刷新保留预览`, async ({ page }, testInfo) => {
    const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
    await setVersionTheme(page, colorScheme)
    const data = createDocsApiData()
    const project = data.get('/api/rule/project/list').records[0]
    let release
    let pending = new Promise(resolve => { release = resolve })
    const workspace = {
      latestVersionId: '21', drafts: [], actor: 'e2e',
      versions: [{ versionId: '21', version: 1, revision: 1, latest: true }],
      history: [{ id: '21', versionNo: 1, revisionNo: 1, snapshotJson: JSON.stringify({ projectName: '版本加载测试' }) }],
    }
    data.set(`/api/rule/resource-versions/PROJECT/${project.id}`, async () => { await pending; return workspace })
    data.set(`/api/rule/resource-versions/PROJECT/${project.id}/events`, [])
    const { assertClean } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: data })
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.goto(`${origin}/#/project`)
    await expect(page.locator('html')).toHaveAttribute('data-theme', colorScheme.toLowerCase())
    await page.getByRole('button', { name: '版本', exact: true }).first().click()
    const drawer = page.getByRole('dialog', { name: '版本管理', exact: true })
    const content = drawer.locator('.resource-version-manager')
    try {
      await expect(content).toHaveAttribute('aria-busy', 'true')
      await expect(drawer.locator('.el-loading-mask')).toHaveCount(0)
      await expect(drawer.getByRole('tab', { name: '预览与对比' })).toBeVisible()
      await expect(drawer.locator('.el-empty')).toHaveCount(0)
      await page.screenshot({ path: testInfo.outputPath(`version-loading-${colorScheme}.png`) })
      release()
      await expect(drawer.locator('.version-preview-summary')).toContainText('版本加载测试')
      await expect(content).toHaveAttribute('aria-busy', 'false')
      pending = new Promise(resolve => { release = resolve })
      workspace.history[0].snapshotJson = JSON.stringify({ projectName: '刷新后的版本' })
      await drawer.getByRole('button', { name: '刷新', exact: true }).click()
      await expect(content).toHaveAttribute('aria-busy', 'true')
      await expect(drawer.locator('.el-loading-mask')).toHaveCount(0)
      await expect(drawer.locator('.version-preview-summary')).toContainText('版本加载测试')
      release()
      await expect(drawer.locator('.version-preview-summary')).toContainText('刷新后的版本')
      await expect(content).toHaveAttribute('aria-busy', 'false')
      assertClean()
    } finally { release() }
  })
}

for (const colorScheme of ['LIGHT', 'DARK']) {
  test(`${colorScheme} 版本入口与发布同时出现时操作列单行且完整可见`, async ({ page }) => {
    test.setTimeout(60000)
    await page.addInitScript(scheme => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({
      schemaVersion: 1, colorScheme: scheme, accentPreset: 'LIQUID_PURPLE', sidebarTheme: 'DARK', contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false,
    })), colorScheme)
    const data = createDocsApiData()
    data.set('/api/rule/governance/workspaces/states', ({ url }) => url.searchParams.get('ids').split(',').map(resourceId => ({ resourceId, effectiveStatus: 'ACTIVE', requestStatus: 'EDITING', action: 'UPDATE', owned: true })))
    const { assertClean } = await installDistRoutes(page, { apiData: data })
    const failures = []
    await page.setViewportSize({ width: 1280, height: 900 })
    for (const [route, tab] of [
      ['/project'], ['/rule'], ['/variable', '变量列表'], ['/variable', '数据对象'], ['/variable', '常量列表'],
      ['/list'], ['/datasource'], ['/datasource?tab=api'], ['/database'], ['/model'], ['/function'], ['/experiment'], ['/billing'],
    ]) {
      await page.goto('http://tianshu.local/index.html#' + route)
      if (tab) await page.getByRole('tab', { name: tab, exact: true }).click()
      const table = page.locator('.el-table:visible').first()
      await expect(table.getByRole('button', { name: '发布', exact: true }).first()).toBeVisible()
      const problems = await table.evaluate(element => {
        const issues = []
        for (const cell of element.querySelectorAll('td.table-operation-column')) {
          const content = cell.querySelector('.cell')
          if (content.scrollWidth > content.clientWidth + 1) issues.push('操作内容裁剪')
          const buttons = [...cell.querySelectorAll('button')].filter(button => button.getBoundingClientRect().width)
          const bounds = cell.getBoundingClientRect()
          const top = buttons[0]?.getBoundingClientRect().top
          for (const button of buttons) {
            const rect = button.getBoundingClientRect()
            if (Math.abs(rect.top - top) > 1 || rect.left < bounds.left || rect.right > bounds.right) issues.push(button.textContent.trim() + '换行或被裁剪')
          }
        }
        return issues
      })
      const actionTexts = (await table.locator('.el-table__body tr').first().locator('.table-operation-column button:visible').allTextContents()).map(text => text.trim())
      const versionIndex = actionTexts.indexOf('版本')
      if (versionIndex >= 0) {
        for (const before of ['进入', '详情', '查看', '编辑', '查询', '测试', '验证执行']) {
          const index = actionTexts.indexOf(before)
          if (index >= 0) expect(index, `${route} 的版本按钮应排在${before}之后`).toBeLessThan(versionIndex)
        }
        for (const after of ['发布', '加接口', '添加字段', '转为全局', '枚举', '鉴权', 'API', '删除']) {
          const index = actionTexts.indexOf(after)
          if (index >= 0) expect(versionIndex, `${route} 的版本按钮应排在${after}之前`).toBeLessThan(index)
        }
      }
      if (problems.length) failures.push({ route, tab, problems })
    }
    expect(failures).toEqual([])
    assertClean()
  })
}
