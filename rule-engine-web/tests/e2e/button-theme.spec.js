const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createManagementApiData } = require('./support/managementFixtures.cjs')

const themes = [
  ['深紫', { accentPreset: 'MIDNIGHT_PURPLE' }],
  ['桃粉渐变', { accentPreset: 'PEACH_PINK_GRADIENT' }],
  ['桃粉红', { accentPreset: 'PEACH_PINK' }],
  ['亮黄', { accentMode: 'CUSTOM_SOLID', customSolidColor: '#FFFF00' }],
  ['纯白', { accentMode: 'CUSTOM_SOLID', customSolidColor: '#FFFFFF' }],
  ['跨明暗渐变', { accentMode: 'CUSTOM_GRADIENT', customGradientColors: ['#FFFFFF', '#000000', '#FFFF00'] }],
]

async function appearance(button) {
  return button.evaluate(element => {
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = 1
    const context = canvas.getContext('2d')
    const rgba = color => {
      context.clearRect(0, 0, 1, 1)
      context.fillStyle = color
      context.fillRect(0, 0, 1, 1)
      return [...context.getImageData(0, 0, 1, 1).data].map((value, index) => index === 3 ? value / 255 : value)
    }
    const composite = (front, back) => {
      const alpha = front[3] + back[3] * (1 - front[3])
      return alpha ? [...front.slice(0, 3).map((value, index) => (value * front[3] + back[index] * back[3] * (1 - front[3])) / alpha), alpha] : [0, 0, 0, 0]
    }
    let surface = [0, 0, 0, 0]
    for (let parent = element.parentElement; parent; parent = parent.parentElement) surface = composite(surface, rgba(getComputedStyle(parent).backgroundColor))
    surface = composite(surface, [255, 255, 255, 1])
    const style = getComputedStyle(element)
    const stops = style.backgroundImage.match(/rgba?\([^)]+\)/g)?.map(rgba) || [rgba(style.backgroundColor)]
    const samples = stops.flatMap((color, index) => index === 0 ? [color] : Array.from({ length: 41 }, (_, step) => color.map((value, channel) => stops[index - 1][channel] + (value - stops[index - 1][channel]) * step / 40)))
    const luminance = color => color.slice(0, 3).map(value => value / 255).map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4).reduce((sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index], 0)
    const ratios = samples.map(color => {
      const background = composite(color, surface)
      const foreground = composite(rgba(style.color), background)
      const a = luminance(background), b = luminance(foreground)
      return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05)
    })
    return { ratio: Math.min(...ratios), color: style.color, background: style.backgroundColor, image: style.backgroundImage, filter: style.filter, opacity: style.opacity }
  })
}

for (const colorScheme of ['LIGHT', 'DARK']) {
  for (const [name, overrides] of themes) {
    test(`${colorScheme} ${name} 的按钮变体、交互和禁用状态按实际底色保持可读`, async ({ page }) => {
      test.setTimeout(90000)
      const theme = {
        schemaVersion: 3, colorScheme, accentMode: 'PRESET', accentPreset: 'THEME_BLUE',
        customSolidColor: '#2639E9', customGradientColors: ['#2639E9', '#873FF2'],
        customGradientType: 'LINEAR', customGradientAngle: 135,
        navigationLayout: 'LEFT', sidebarTheme: 'DARK', contentWidth: 'FLUID',
        tableScrollMode: 'FIXED', fixedSidebar: true, colorWeak: false, ...overrides,
      }
      await page.addInitScript(value => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify(value)), theme)
      const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
      const { assertClean } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: createManagementApiData() })
      await page.goto(`${origin}/#/project`)
      await expect(page.locator('html')).toHaveAttribute('data-theme', colorScheme.toLowerCase())
      await expect(page.getByRole('button', { name: '新建项目', exact: true })).toBeVisible()
      // 使用已加载的真实组件 CSS 检查全部变体；探针没有业务事件，不触发数据修改。
      await page.evaluate(() => {
        const holder = document.createElement('section')
        holder.id = 'button-theme-probes'
        holder.style.cssText = 'position:fixed;inset:0;z-index:10000;background:var(--tianshu-bg-surface);padding:24px;display:flex;flex-wrap:wrap;align-content:flex-start;gap:8px;overflow:auto'
        for (const type of ['default', 'primary', 'info', 'success', 'warning', 'danger']) {
          for (const variant of ['solid', 'plain', 'link', 'text']) {
            for (const disabled of [false, true]) {
              const button = document.createElement('button')
              button.className = `el-button el-button--small ${type === 'default' ? '' : `el-button--${type}`} ${variant === 'solid' ? '' : `is-${variant}`} ${disabled ? 'is-disabled' : ''}`
              button.disabled = disabled
              button.dataset.probe = `${type}-${variant}-${disabled}`
              button.textContent = `${type} ${variant} ${disabled ? '禁用' : '操作'}`
              holder.appendChild(button)
            }
          }
        }
        document.body.appendChild(holder)
      })
      await page.addStyleTag({ content: '#button-theme-probes button { transition: none !important; }' })
      for (const button of await page.locator('#button-theme-probes button').all()) {
        const label = await button.getAttribute('data-probe')
        const baseline = await appearance(button)
        expect(baseline.ratio, `${label} 普通状态`).toBeGreaterThanOrEqual(4.5)
        await button.hover()
        const hovered = await appearance(button)
        expect(hovered.ratio, `${label} hover`).toBeGreaterThanOrEqual(4.5)
        expect(hovered.filter, label).toBe('none')
        if (await button.isDisabled()) {
          expect(hovered, `${label} 禁用状态不能响应 hover`).toEqual(baseline)
        } else {
          await button.focus()
          expect((await appearance(button)).ratio, `${label} focus`).toBeGreaterThanOrEqual(4.5)
          await page.mouse.down()
          try { expect((await appearance(button)).ratio, `${label} active`).toBeGreaterThanOrEqual(4.5) }
          finally { await page.mouse.up() }
        }
        await page.mouse.move(0, 0)
        await button.blur()
      }
      await page.locator('#button-theme-probes').evaluate(element => element.remove())
      await page.getByRole('button', { name: '新建项目', exact: true }).click()
      const dialog = page.getByRole('dialog', { name: '新建项目', exact: true })
      await expect(dialog).toBeVisible()
      await dialog.getByRole('button', { name: '取消', exact: true }).click()
      assertClean()
    })
  }
}

test('通过主题设置预览、保存和取消时，页面与原生按钮同步适配主题', async ({ page }, testInfo) => {
  const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
  const { assertClean } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: createManagementApiData() })
  await page.goto(`${origin}/#/project`)
  const create = page.getByRole('button', { name: '新建项目', exact: true })
  await expect(create).toBeVisible()
  const before = await appearance(create)
  await page.getByRole('button', { name: '本地用户的账户菜单' }).click()
  await page.getByRole('menuitem', { name: '主题设置' }).click()
  const drawer = page.getByRole('dialog', { name: '主题设置', exact: true })
  await drawer.getByRole('button', { name: '桃粉红', exact: true }).click()
  const save = drawer.getByRole('button', { name: '保存设置', exact: true })
  const preview = await appearance(create)
  expect([preview.background, preview.image]).not.toEqual([before.background, before.image])
  expect(preview.color).toBe('rgb(255, 255, 255)')
  expect((await appearance(save)).color).toBe('rgb(255, 255, 255)')
  for (const control of [create, save]) expect((await appearance(control)).ratio).toBeGreaterThanOrEqual(4.5)
  await save.hover()
  expect((await appearance(save)).ratio).toBeGreaterThanOrEqual(4.5)
  await save.click()
  await expect(drawer).toBeHidden()
  await page.getByRole('button', { name: '设置表格显示字段', exact: true }).click()
  const fields = page.locator('.table-column-settings__panel')
  expect((await appearance(fields.getByRole('button', { name: '应用', exact: true }))).ratio).toBeGreaterThanOrEqual(4.5)
  await fields.getByRole('button', { name: '取消', exact: true }).click()
  await expect(fields).toBeHidden()
  await create.screenshot({ path: testInfo.outputPath('pink-button.png') })
  await page.screenshot({ path: testInfo.outputPath('theme-buttons-pink.png') })
  await page.getByRole('button', { name: '本地用户的账户菜单' }).click()
  await page.getByRole('menuitem', { name: '主题设置' }).click()
  await drawer.locator('[data-theme-scheme="DARK"]').click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  expect((await appearance(create)).ratio).toBeGreaterThanOrEqual(4.5)
  await drawer.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await page.reload()
  await expect(create).toBeVisible()
  expect((await appearance(create)).ratio).toBeGreaterThanOrEqual(4.5)
  assertClean()
})
