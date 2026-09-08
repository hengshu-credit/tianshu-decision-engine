const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createOperationsApiData } = require('./support/operationsFixtures.cjs')

test('原生和 Element 滚动条共用主题，切换配色及夜间模式立即生效', async ({ page }) => {
  const { assertClean } = await installDistRoutes(page)
  await page.setViewportSize({ width: 1280, height: 720 })
  await page.goto('http://tianshu.local/index.html#/project')
  await expect(page.getByText('E2E 项目', { exact: true })).toBeVisible()
  const appearance = () => page.evaluate(() => {
    const native = document.querySelector('.sidebar-menu')
    const bar = document.querySelector('.el-table .el-scrollbar__bar.is-horizontal')
    const wrapper = document.querySelector('.el-table .el-scrollbar__wrap')
    const thumb = bar.querySelector('.el-scrollbar__thumb')
    return {
      width: getComputedStyle(native, '::-webkit-scrollbar').width,
      height: getComputedStyle(bar).height,
      nativeThumb: getComputedStyle(native, '::-webkit-scrollbar-thumb').backgroundImage,
      elementThumb: getComputedStyle(thumb).backgroundImage,
      nativeColor: getComputedStyle(native, '::-webkit-scrollbar-thumb').backgroundColor,
      elementColor: getComputedStyle(thumb).backgroundColor,
      nativeTrack: getComputedStyle(native, '::-webkit-scrollbar-track').backgroundColor,
      elementTrack: getComputedStyle(bar).backgroundColor,
      radius: getComputedStyle(thumb).borderRadius,
      hiddenNative: getComputedStyle(wrapper).scrollbarWidth,
      hiddenWebkit: getComputedStyle(wrapper, '::-webkit-scrollbar').display,
    }
  })
  const check = async (gradient = false) => {
    const style = await appearance()
    expect(style.width).toBe('4px')
    expect(style.height).toBe('4px')
    if (gradient) {
      // 浏览器会省略默认的 180deg（从上到下），横向必须保留 90deg。
      expect(style.nativeThumb).toMatch(/^linear-gradient\(rgb/)
      expect(style.elementThumb).toBe(style.nativeThumb.replace('linear-gradient(', 'linear-gradient(90deg, '))
    } else {
      expect(style.nativeThumb).toBe('none')
      expect(style.elementThumb).toBe('none')
      expect(style.nativeColor).not.toBe('rgba(0, 0, 0, 0)')
      expect(style.elementColor).toBe(style.nativeColor)
    }
    expect(style.elementTrack).toBe(style.nativeTrack)
    expect(style.radius).toBe('0px')
    expect(style.hiddenNative).toBe('none')
    expect(style.hiddenWebkit).toBe('none')
    return style
  }
  const before = await check()
  await page.getByRole('navigation', { name: '主导航' }).hover()
  await page.mouse.wheel(0, 500)
  await expect.poll(() => page.locator('.sidebar-menu').evaluate(el => el.scrollTop)).toBeGreaterThan(0)
  await page.getByRole('button', { name: '本地用户的账户菜单' }).click()
  await page.getByRole('menuitem', { name: '主题设置' }).click()
  await page.getByRole('button', { name: '凝液紫', exact: true }).click()
  await expect.poll(async () => (await appearance()).nativeColor).not.toBe(before.nativeColor)
  const purple = await check()
  await page.getByRole('button', { name: '夜间模式', exact: true }).click()
  await expect.poll(async () => (await appearance()).nativeTrack).not.toBe(purple.nativeTrack)
  await check()
  await page.getByRole('button', { name: '主题蓝渐变', exact: true }).click()
  await expect.poll(async () => (await appearance()).nativeThumb).not.toBe(purple.nativeThumb)
  await check(true)
  assertClean()
})

test('弹窗、下拉框和 Monaco 使用同一滚动条，编辑器仍可拖动滚动', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({
      schemaVersion: 1,
      colorScheme: 'LIGHT',
      accentPreset: 'THEME_BLUE_GRADIENT',
      sidebarTheme: 'DARK',
      contentWidth: 'FLUID',
      fixedSidebar: true,
      colorWeak: false,
    }))
  })
  const { assertClean } = await installDistRoutes(page, { apiData: createOperationsApiData() })
  await page.goto('http://tianshu.local/index.html#/function')
  await page.getByRole('button', { name: '新建函数' }).click()
  const dialog = page.getByRole('dialog', { name: '新建函数' })
  const editor = dialog.locator('.monaco-editor')
  const input = editor.locator('textarea')
  await input.focus()
  await page.keyboard.press('Control+A')
  await page.keyboard.insertText(Array.from({ length: 80 }, (_, i) => `value${i} = ${i};`).join('\n'))
  await page.keyboard.press('Control+Home')
  const slider = editor.locator('.editor-scrollable > .scrollbar.vertical > .slider')
  await expect(slider).toBeVisible()
  const style = await slider.evaluate(el => {
    const native = document.querySelector('.el-dialog__body')
    return {
      thumb: getComputedStyle(el).backgroundImage,
      nativeThumb: getComputedStyle(native, '::-webkit-scrollbar-thumb').backgroundImage,
      track: getComputedStyle(el.parentElement).backgroundColor,
      nativeTrack: getComputedStyle(native, '::-webkit-scrollbar-track').backgroundColor,
      width: getComputedStyle(el.parentElement).width,
      radius: getComputedStyle(el).borderRadius,
    }
  })
  expect(style.width).toBe('4px')
  expect(style.thumb).toBe(style.nativeThumb)
  expect(style.thumb).not.toBe('none')
  expect(style.track).toBe(style.nativeTrack)
  expect(style.radius).toBe('0px')
  const box = await slider.boundingBox()
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await expect(slider).not.toHaveCSS('box-shadow', 'none')
  await page.mouse.down()
  await page.mouse.move(box.x + box.width / 2, box.y + 80, { steps: 5 })
  await page.mouse.up()
  await expect.poll(() => slider.evaluate(el => parseFloat(el.style.top))).toBeGreaterThan(0)
  await expect(editor.locator('.view-lines')).not.toContainText('value0 = 0;')
  await dialog.locator('.el-form-item').filter({ hasText: '返回类型' }).locator('.el-select').click()
  const dropdownBar = page.locator('.el-select__popper:visible .el-scrollbar__bar.is-vertical')
  await expect(dropdownBar).toHaveCSS('width', '4px')
  await expect(dropdownBar.locator('.el-scrollbar__thumb')).toHaveCSS('background-image', style.thumb)
  await page.keyboard.press('Escape')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).toBeHidden()
  assertClean()
})
