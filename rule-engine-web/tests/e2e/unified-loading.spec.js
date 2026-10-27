const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
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
  const style = await locator.evaluate(element => {
    const root = getComputedStyle(element)
    const before = getComputedStyle(element, '::before')
    const after = getComputedStyle(element, '::after')
    return { animation: root.animationName, dot: root.backgroundImage,
      before: before.animationName, after: after.animationName, duration: before.animationDuration,
      width: parseFloat(before.width), height: parseFloat(after.height), fontSize: parseFloat(root.fontSize) }
  })
  expect(style).toMatchObject({ animation: 'none', before: 'tianshu-loader2', after: 'tianshu-loader2', duration: '1.35s' })
  expect(style.dot).toContain('radial-gradient')
  expect(style.width / style.fontSize).toBeCloseTo(24 / 52, 1)
  expect(style.height / style.fontSize).toBeCloseTo(24 / 52, 1)
  await expect(locator.locator('svg:visible')).toHaveCount(0)
}

test('脚本下载前首屏就显示瑶光动画，脚本完成后正常进入页面', async ({ page }) => {
  const script = deferred()
  const { assertClean } = await installDistRoutes(page, { origin, liveStatic })
  await page.route(/\/(?:src\/main\.js|assets\/index-[^/]+\.js)(?:\?.*)?$/, async route => {
    await script.promise
    await route.fallback()
  })
  try {
    await page.goto(`${origin}/#/project`, { waitUntil: 'commit' })
    await expectLoader(page.locator('.app-bootstrap .tianshu-loading'))
    script.resolve()
    await expect(page.getByRole('cell', { name: 'e2e_project', exact: true })).toBeVisible()
    await expect(page.locator('.app-bootstrap')).toHaveCount(0)
    assertClean()
  } finally { script.resolve() }
})

for (const colorScheme of ['LIGHT', 'DARK']) {
  test(`${colorScheme} 表格与抽屉直接展示内容，候选下拉保留请求动画`, async ({ page }, testInfo) => {
    const data = deferred(), options = deferred(), versions = deferred()
    const project = { id: 1, projectCode: 'e2e_project', projectName: 'E2E 项目', status: 1 }
    const { assertClean } = await installDistRoutes(page, { origin, liveStatic, apiData: new Map([
      ['/api/auth/console/config', { loginEnabled: true }],
      ['/api/auth/console/me', { username: 'admin', permissions: ['project:view', 'project:edit'] }],
      ['/api/auth/console/preferences/theme', { ...DEFAULT_THEME_CONFIG, colorScheme }],
      ['POST /api/rule/console/activity', true],
      ['/api/rule/project/list', async () => { await data.promise; return { records: [project], total: 1 } }],
      ['/api/rule/project/options', async () => { await options.promise; return { records: [project], total: 1 } }],
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
      const suggestion = page.locator('.el-autocomplete-suggestion.is-loading .el-icon.is-loading')
      await expectLoader(suggestion)
      await page.emulateMedia({ reducedMotion: 'reduce' })
      expect(await suggestion.evaluate(element => getComputedStyle(element, '::before').animationName)).toBe('none')
      options.resolve()
      await expect(page.getByRole('option', { name: 'e2e_project', exact: true })).toBeVisible()
      await expect(suggestion).toBeHidden()
      await input.press('Escape')
      await page.getByRole('button', { name: '版本', exact: true }).click()
      const drawer = page.getByRole('dialog', { name: '版本管理' })
      await expect(drawer.locator('.resource-version-manager')).toHaveAttribute('aria-busy', 'true')
      await expect(drawer.getByRole('tab', { name: '预览与对比' })).toBeVisible()
      await expect(drawer.locator('.el-loading-mask')).toHaveCount(0)
      versions.resolve()
      await expect(drawer.locator('.resource-version-manager')).toHaveAttribute('aria-busy', 'false')
      assertClean()
    } finally { data.resolve(); options.resolve(); versions.resolve() }
  })
}

test('登录按钮使用等比缩放的扇叶且保持禁用状态和文字对比色', async ({ page }, testInfo) => {
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
    await page.getByRole('button', { name: '登录', exact: true }).click()
    const button = page.locator('.login-btn')
    const spinner = button.locator('.el-icon.is-loading')
    await expectLoader(spinner)
    await expect(button).toBeDisabled()
    expect(await spinner.evaluate(element => getComputedStyle(element, '::before').borderTopColor)).toBe(await button.evaluate(element => getComputedStyle(element).color))
    await page.screenshot({ path: testInfo.outputPath('button-loading.png') })
    login.resolve()
    await expect(page).not.toHaveURL(/#\/login$/)
    assertClean()
  } finally { login.resolve() }
})

test('独立状态图标也使用统一扇叶，完成邮箱验证后消失', async ({ page }) => {
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
