const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createOperationsApiData } = require('./support/operationsFixtures.cjs')
const { DEFAULT_THEME_CONFIG } = require('../../src/theme/themeConfig.js')

const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
const liveStatic = Boolean(process.env.E2E_DEV_BASE_URL)
function deferred() {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}
async function expectLoader(locator) {
  await expect(locator).toBeVisible()
  await expect.poll(() => locator.evaluate(element => {
    let opacity = 1
    for (let node = element; node; node = node.parentElement) opacity *= Number(getComputedStyle(node).opacity)
    return opacity
  })).toBe(1)
  await expect(locator).toHaveCSS('background-image', `url("${origin}/images/hscredit-loading.svg")`)
  await expect(locator).toHaveCSS('animation-name', 'none')
  const size = await locator.evaluate(element => getComputedStyle(element).fontSize)
  await expect(locator).toHaveCSS('background-size', `${size} ${size}`)
  await expect(locator.locator('svg:visible')).toHaveCount(0)
}

async function expectSquareButtonSpinner(spinner) {
  const geometry = await spinner.evaluate(element => {
    const arc = getComputedStyle(element, '::before'), dot = getComputedStyle(element, '::after')
    return { width: parseFloat(arc.width), height: parseFloat(arc.height), diameter: element.offsetWidth,
      dotWidth: dot.width, dotHeight: dot.height, gap: arc.borderLeftColor, stroke: arc.borderBottomColor, color: arc.color }
  })
  expect(geometry.width).toBe(geometry.height)
  expect(geometry.width).toBe(geometry.diameter)
  expect(geometry.width).toBeGreaterThanOrEqual(16)
  expect(geometry.dotWidth).toBe(geometry.dotHeight)
  expect(geometry.gap).toBe('rgba(0, 0, 0, 0)')
  expect(geometry.stroke).toBe(geometry.color)
}
async function centeredPanel(panel, loader) {
  const box = await panel.boundingBox()
  const icon = await loader.boundingBox()
  expect(Math.abs(box.x + box.width / 2 - icon.x - icon.width / 2)).toBeLessThan(2)
  expect(Math.abs(box.y + box.height / 2 - icon.y - icon.height / 2)).toBeLessThan(2)
  return box
}
function expectStablePanel(before, after) {
  for (const key of ['x', 'y', 'width', 'height']) {
    expect(Math.abs(after[key] - before[key]), key).toBeLessThan(2)
  }
}

test('脚本下载前首屏就加载 HSCredit 动图，脚本完成后正常进入页面', async ({ page }) => {
  const script = deferred()
  const { assertClean } = await installDistRoutes(page, { origin, liveStatic })
  await page.route(/\/(?:src\/main\.js|assets\/index-[^/]+\.js)(?:\?.*)?$/, async route => {
    await script.promise
    await route.fallback()
  })
  try {
    const image = page.waitForResponse(`${origin}/images/hscredit-loading.svg`)
    await page.goto(`${origin}/#/project`, { waitUntil: 'commit' })
    const response = await image
    expect(response.ok()).toBe(true)
    expect(response.headers()['content-type']).toContain('image/svg+xml')
    await expectLoader(page.locator('.app-bootstrap .tianshu-loading'))
    await expect(page.locator('.app-bootstrap .login-bg')).toHaveCSS('background-image', /radial-gradient/)
    script.resolve()
    await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeVisible()
    await expect(page.locator('.app-bootstrap')).toHaveCount(0)
    assertClean()
  } finally { script.resolve() }
})

for (const colorScheme of ['LIGHT', 'DARK']) {
  test(`${colorScheme} 表格与抽屉直接展示内容，候选下拉保留请求动画`, async ({ page }, testInfo) => {
    const data = deferred(), options = deferred(), search = deferred(), versions = deferred()
    const queries = []
    const project = { id: 1, projectCode: 'e2e_project', projectName: 'E2E 项目', status: 1 }
    const { assertClean } = await installDistRoutes(page, { origin, liveStatic, apiData: new Map([
      ['/api/auth/console/config', { loginEnabled: true }],
      ['/api/auth/console/me', { username: 'admin', permissions: ['project:view', 'project:edit'] }],
      ['/api/auth/console/preferences/theme', { ...DEFAULT_THEME_CONFIG, colorScheme }],
      ['POST /api/rule/console/activity', true],
      ['/api/rule/project/list', async () => { await data.promise; return { records: [project], total: 1 } }],
      ['/api/rule/project/options', async ({ url }) => {
        const query = url.searchParams.get('projectCode') || ''
        queries.push(query)
        await (query ? search : options).promise
        return { records: [query ? { ...project, projectCode: 'typed_result' } : project], total: 1 }
      }],
      ['/api/rule/resource-versions/PROJECT/1', async () => { await versions.promise; return { versions: [], history: [], drafts: [], actor: 'admin' } }],
    ]) })
    try {
      await page.goto(`${origin}/#/project`)
      await expect(page.locator('html')).toHaveAttribute('data-theme', colorScheme.toLowerCase())
      const table = page.locator('.el-table').first()
      await expect(table).toHaveAttribute('aria-busy', 'true')
      await expect(table.locator('thead')).toBeVisible()
      await expect(page.locator('.el-loading-mask')).toHaveCount(0)
      await page.screenshot({ path: testInfo.outputPath(`table-loading-${colorScheme}.png`) })
      data.resolve()
      await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeVisible()
      await expect(table).toHaveAttribute('aria-busy', 'false')
      const input = page.getByRole('textbox', { name: '项目编码', exact: true })
      await input.click()
      const panel = page.locator('.remote-filter-suggestions:visible')
      const suggestion = panel.locator('.tianshu-loading')
      await expectLoader(suggestion)
      const before = await centeredPanel(panel, suggestion)
      options.resolve()
      await expect(page.getByRole('option', { name: 'e2e_project', exact: true })).toBeVisible()
      await expect(suggestion).toBeHidden()
      expectStablePanel(before, await panel.boundingBox())
      await input.fill('t')
      await input.fill('typed')
      await expectLoader(suggestion)
      expectStablePanel(before, await centeredPanel(panel, suggestion))
      await expect.poll(() => queries).toEqual(['', 'typed'])
      search.resolve()
      await expect(page.getByRole('option', { name: 'typed_result', exact: true })).toBeVisible()
      expectStablePanel(before, await panel.boundingBox())
      await input.press('Escape')
      await page.getByRole('button', { name: '版本', exact: true }).click()
      const drawer = page.getByRole('dialog', { name: '版本管理' })
      await expect(drawer.locator('.resource-version-manager')).toHaveAttribute('aria-busy', 'true')
      await expect(drawer.getByRole('tab', { name: '预览与对比' })).toBeVisible()
      await expect(drawer.locator('.el-loading-mask')).toHaveCount(0)
      versions.resolve()
      await expect(drawer.locator('.resource-version-manager')).toHaveAttribute('aria-busy', 'false')
      assertClean()
    } finally { data.resolve(); options.resolve(); search.resolve(); versions.resolve() }
  })
}

test('登录按钮显示旋转圆圈，提交期间保持禁用状态和文字颜色', async ({ page }, testInfo) => {
  const login = deferred()
  const { assertClean } = await installDistRoutes(page, { origin, liveStatic, apiData: new Map([
    ['/api/auth/console/config', { loginEnabled: true }],
    ['POST /api/auth/console/login', async () => { await login.promise; return { username: 'e2e' } }],
    ['/api/auth/console/preferences/theme', null],
    ['POST /api/rule/console/activity', true],
  ]) })
  try {
    await page.goto(`${origin}/#/login`)
    await page.locator('input[autocomplete="username"]').fill('visual-test-user')
    await page.locator('input[autocomplete="current-password"]').fill('visual-test-password')
    const button = page.locator('.login-btn')
    const textColor = await button.evaluate(element => getComputedStyle(element).color)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    const spinner = button.locator('.login-spinner')
    await expect(spinner).toBeVisible()
    await expect(spinner).toHaveCSS('color', textColor)
    await expect(spinner).toHaveCSS('background-image', 'none')
    const arc = await spinner.evaluate(el => {
      const style = getComputedStyle(el, '::before')
      return { animation: style.animationName, count: style.animationIterationCount, stroke: style.borderTopColor }
    })
    expect(arc.animation).not.toBe('none')
    expect(arc.count).toBe('infinite')
    expect(arc.stroke).toBe(textColor)
    await expectSquareButtonSpinner(spinner)
    await expect(button).toHaveAccessibleName('登录中…')
    await expect(button).toBeDisabled()
    await expect(button).toHaveCSS('color', textColor)
    await page.screenshot({ path: testInfo.outputPath('button-loading.png') })
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await expect.poll(() => spinner.evaluate(el => getComputedStyle(el, '::before').animationName)).toBe('none')
    login.resolve()
    await expect(page).not.toHaveURL(/#\/login$/)
    assertClean()
  } finally { login.resolve() }
})

test('规则选项加载与列表使用相同面板，执行期间按钮显示双弧、结果区显示品牌动画', async ({ page }) => {
  const options = deferred(), execution = deferred()
  const fixtures = createOperationsApiData()
  const optionsPath = '/api/rule/definition/list'
  const executePath = 'POST /api/rule/definition/101/api-scenarios/execute-validated'
  const execute = fixtures.get(executePath)
  fixtures.set(executePath, async context => { await execution.promise; return execute(context) })
  const { assertClean } = await installDistRoutes(page, { origin, liveStatic, apiData: fixtures })
  try {
    await page.goto(`${origin}/#/test`)
    const selector = page.locator('.test-left .el-form-item').filter({ hasText: '规则' }).locator('.el-select')
    await page.route(`**${optionsPath}*`, async route => { await options.promise; await route.fallback() })
    await selector.click()
    const panel = page.locator('.el-popper.remote-filter-options:visible')
    const icon = panel.locator('.tianshu-loading')
    await expectLoader(icon)
    const before = await centeredPanel(panel, icon)
    options.resolve()
    const rule = page.getByRole('option', { name: /age_rule/ })
    await expect(rule).toBeVisible()
    expectStablePanel(before, await panel.boundingBox())
    await rule.click()
    const button = page.getByRole('button', { name: '执行测试', exact: true })
    await button.click()
    const spinner = button.locator('.el-icon.is-loading')
    await expect(spinner).toBeVisible()
    await expect(spinner).toHaveCSS('background-image', 'none')
    await expect(spinner.locator('svg')).toBeHidden()
    expect(await spinner.evaluate(el => getComputedStyle(el, '::before').animationName)).toBe('button-spin')
    expect(await spinner.evaluate(el => getComputedStyle(el, '::after').backgroundColor)).toBe(await spinner.evaluate(el => getComputedStyle(el).color))
    await expectSquareButtonSpinner(spinner)
    const result = page.locator('.result-loading')
    await expectLoader(result.locator('.tianshu-loading'))
    await centeredPanel(result, result.locator('.tianshu-loading'))
    execution.resolve()
    await expect(page.getByText('执行成功', { exact: true })).toBeVisible()
    assertClean()
  } finally { options.resolve(); execution.resolve() }
})

test('独立状态图标显示 HSCredit 动图，完成邮箱验证后消失', async ({ page }) => {
  const verification = deferred()
  const { assertClean } = await installDistRoutes(page, { origin, liveStatic, apiData: new Map([
    ['/api/auth/console/config', { loginEnabled: true, initializationRequired: false }],
    ['POST /api/auth/console/register/verify', async () => { await verification.promise; return {} }],
  ]) })
  try {
    await page.goto(`${origin}/#/register?token=loading-fixture`)
    const icon = page.locator('.auth-state .tianshu-loading')
    await expectLoader(icon)
    await expect(icon).toHaveCSS('font-size', '28px')
    await expect(page.locator('.auth-state .el-icon.is-loading')).toHaveCount(0)
    verification.resolve()
    await expect(page.getByText('邮箱验证成功', { exact: true })).toBeVisible()
    await expect(icon).toHaveCount(0)
    assertClean()
  } finally { verification.resolve() }
})

test('减少动态效果时同一个 SVG 显示完整静态标志', async ({ page }) => {
  await installDistRoutes(page, { origin, liveStatic })
  await page.goto(`${origin}/images/hscredit-loading.svg`)
  await expect(page.locator('#motion')).toBeVisible()
  await expect(page.locator('#still')).toBeHidden()
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await expect(page.locator('#motion')).toBeHidden()
  await expect(page.locator('#still')).toBeVisible()
})
