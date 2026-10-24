const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')

const designers = [
  ['决策树', 'tree', 102], ['决策流', 'flow', 103], ['决策表', 'table', 101],
  ['规则集', 'ruleset', 104], ['交叉表', 'cross', 105], ['评分卡', 'score', 106],
  ['复杂交叉表', 'cross-adv', 107], ['复杂评分卡', 'score-adv', 108], ['QL 脚本', 'script', 109],
]

async function appearance(button) {
  return button.evaluate(element => {
    const style = getComputedStyle(element)
    const icon = element.querySelector('svg')
    const foreground = getComputedStyle(icon || element).color
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = 1
    const context = canvas.getContext('2d')
    const rgba = color => {
      context.clearRect(0, 0, 1, 1)
      context.fillStyle = color
      context.fillRect(0, 0, 1, 1)
      return Array.from(context.getImageData(0, 0, 1, 1).data)
    }
    const luminance = channels => channels.slice(0, 3).map(value => value / 255)
      .map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4)
      .reduce((sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index], 0)
    const background = rgba(style.backgroundColor)
    const a = luminance(background), b = luminance(rgba(foreground))
    return {
      width: element.getBoundingClientRect().width, height: element.getBoundingClientRect().height,
      radius: style.borderRadius, borderWidth: style.borderTopWidth,
      color: foreground, background: style.backgroundColor, backgroundAlpha: background[3],
      contrast: (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05),
    }
  })
}

for (const colorScheme of ['LIGHT', 'DARK']) {
  for (const [themeName, theme] of [
    ['深紫', { accentPreset: 'MIDNIGHT_PURPLE' }],
    ['桃粉渐变', { accentPreset: 'PEACH_PINK_GRADIENT' }],
    ['自定义亮黄', { accentMode: 'CUSTOM_SOLID', customSolidColor: '#FFFF00' }],
  ]) {
    test(`${colorScheme} ${themeName} 九类设计器返回按钮与详情页一致且各状态清晰可见`, async ({ page }, testInfo) => {
      test.setTimeout(90000)
      await page.addInitScript(value => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify(value)), {
        schemaVersion: 3, colorScheme, accentMode: 'PRESET', accentPreset: 'THEME_BLUE',
        customSolidColor: '#2639E9', customGradientColors: ['#2639E9', '#873FF2'],
        customGradientType: 'LINEAR', customGradientAngle: 135,
        navigationLayout: 'LEFT', sidebarTheme: 'DARK', contentWidth: 'FLUID',
        tableScrollMode: 'FIXED', fixedSidebar: true, colorWeak: false, ...theme,
      })
      const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
      const { assertClean } = await installDistRoutes(page, {
        origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: createDesignerApiData(),
      })
      for (const [name, route, id] of designers) {
        await page.goto(`${origin}/#/project/1`)
        const standard = page.getByRole('button', { name: '返回', exact: true })
        await expect(standard).toBeVisible()
        await expect(page.locator('html')).toHaveAttribute('data-theme', colorScheme.toLowerCase())
        await page.mouse.move(0, 0)
        await standard.blur()
        const reference = await appearance(standard)
        await page.goto(`${origin}/#/designer/${route}/${id}`)
        const back = page.getByRole('button', { name: '返回', exact: true })
        await expect(back).toBeVisible()
        await expect(page.locator('html')).toHaveAttribute('data-theme', colorScheme.toLowerCase())
        await expect.poll(() => appearance(back), { message: `${name} 应复用详情页的尺寸、边框和配色` }).toEqual(reference)
        for (const state of ['normal', 'hover', 'focus', 'active']) {
          if (state === 'hover') await back.hover()
          if (state === 'focus') { await page.mouse.move(0, 0); await back.focus() }
          if (state === 'active') { await back.hover(); await page.mouse.down() }
          try {
            const rendered = await appearance(back)
            expect(rendered.backgroundAlpha, `${name} ${state} 必须有独立底色`).toBe(255)
            expect(rendered.contrast, `${name} ${state} 图标对比度`).toBeGreaterThanOrEqual(4.5)
          } finally {
            if (state === 'active') await page.mouse.move(0, 0)
            if (state === 'active') await page.mouse.up()
          }
        }
        if (route === 'tree' && themeName === '深紫') {
          await back.blur()
          await page.screenshot({ path: testInfo.outputPath(`back-${colorScheme.toLowerCase()}.png`) })
        }
        await back.click()
        await expect(page).toHaveURL(/#\/project\/1$/)
      }
      assertClean()
    })
  }
}
