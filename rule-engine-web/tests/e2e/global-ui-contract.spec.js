const { expect, test } = require('@playwright/test')
const fs = require('node:fs/promises')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createManagementApiData } = require('./support/managementFixtures.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')

for (const accentPreset of ['THEME_BLUE', 'LIQUID_PURPLE_GRADIENT']) {
  test(`深色 ${accentPreset} 复杂评分卡的维度权重公式可读`, async ({ page }) => {
    await page.addInitScript(preset => {
      localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({ schemaVersion: 1, colorScheme: 'DARK',
        accentPreset: preset, sidebarTheme: 'DARK', contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false }))
    }, accentPreset)
    const { assertClean } = await installDistRoutes(page, { apiData: createDesignerApiData() })
    await page.goto('http://tianshu.local/index.html#/designer/score-adv/108')
    await page.getByRole('button', { name: '添加维度组', exact: true }).click()
    await page.getByRole('button', { name: '添加维度', exact: true }).click()
    const term = page.locator('.formula-term').first()
    await expect(term).toBeVisible()
    expect((await controlContrast(term)).ratio).toBeGreaterThanOrEqual(4.5)
    await page.locator('.asc-formula').screenshot({ path: test.info().outputPath('scorecard-formula-' + accentPreset + '.png') })
    assertClean()
  })
}

for (const colorScheme of ['LIGHT', 'DARK']) {
  test(`${colorScheme} 禁用主按钮可辨且悬停不增亮，plain/link/text保留各自外观`, async ({ page }) => {
    await page.addInitScript(scheme => {
      localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({ schemaVersion: 1, colorScheme: scheme,
        accentPreset: 'LIQUID_PURPLE_GRADIENT', sidebarTheme: 'DARK', contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false }))
    }, colorScheme)
    const fixtures = createDetailApiData()
    const waiting = { trialId: '11111111-1111-4111-8111-111111111111', apiName: '可继续主题样例', status: 'WAITING', canResume: true, canCancel: true, createdAt: Date.now(), expiresAt: Date.now() + 60000 }
    const cancelled = { ...waiting, trialId: '22222222-2222-4222-8222-222222222222', apiName: '已取消主题样例', status: 'CANCELLED', canResume: false, canCancel: false }
    fixtures.set('/api/rule/datasource/api-config/trials', [waiting, cancelled])
    for (const trial of [waiting, cancelled]) fixtures.set('/api/rule/datasource/api-config/trials/' + trial.trialId, trial)
    const { assertClean } = await installDistRoutes(page, { apiData: fixtures })
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.goto('http://tianshu.local/index.html#/datasource/api/new')
    await page.getByRole('tab', { name: '接口测试', exact: true }).click()
    const select = async name => {
      await page.getByTestId('api-trial-select').click()
      await page.getByRole('option').filter({ hasText: name }).click()
    }
    const appearance = locator => locator.evaluate(element => {
      const css = getComputedStyle(element)
      return { color: css.color, backgroundColor: css.backgroundColor, backgroundImage: css.backgroundImage,
        borderColor: css.borderTopColor, filter: css.filter, opacity: css.opacity }
    })
    const settle = locator => locator.evaluate(async element => {
      await new Promise(requestAnimationFrame)
      await Promise.all(element.getAnimations().filter(animation => animation.effect?.getComputedTiming().iterations !== Infinity).map(animation => animation.finished.catch(() => {})))
    })
    await select(waiting.apiName)
    const resume = page.getByTestId('api-trial-resume')
    await expect(resume).toBeEnabled(); await page.mouse.move(0, 0); await settle(resume)
    const enabled = await appearance(resume)
    expect(enabled.backgroundImage).toContain('gradient')
    await select(cancelled.apiName)
    await expect(resume).toBeDisabled(); await page.mouse.move(0, 0); await settle(resume)
    const disabled = await appearance(resume)
    expect.soft(disabled.backgroundImage).toBe('none')
    expect.soft(disabled.backgroundColor).not.toBe(enabled.backgroundColor)
    expect.soft(disabled.color).not.toBe(enabled.color)
    const readability = await controlContrast(resume)
    const contrastValues = [{ variant: 'solid', ...readability }]
    expect.soft(readability.ratio).toBeGreaterThanOrEqual(4.5)
    expect.soft(readability.opacity).toBe(1)
    await resume.hover(); await settle(resume)
    expect.soft(await appearance(resume)).toEqual(disabled)
    expect.soft((await appearance(resume)).filter).toBe('none')
    await page.mouse.move(0, 0)
    await page.screenshot({ path: test.info().outputPath('cancelled-trial-' + colorScheme + '.png'), fullPage: true })

    const variants = ['plain', 'link', 'text', 'table-link', 'table-text', 'table-edit-link', 'table-delete-text', 'edit-link', 'dialog-solid', 'message-solid']
    // 复用页面已加载的真实Element/主题CSS，同时覆盖表格和语义操作的级联规则。
    // 探针仅用于浏览器样式测试，没有业务事件或配置请求。
    await resume.evaluate((button, variants) => {
      const holder = document.createElement('div'); holder.dataset.testid = 'primary-variant-probes'
      for (const variant of variants) for (const disabled of [false, true]) {
        const probe = button.cloneNode(true), style = variant.split('-').at(-1)
        probe.className = 'el-button el-button--primary is-' + style + (disabled ? ' is-disabled' : '')
        probe.disabled = disabled; probe.removeAttribute('aria-disabled'); probe.dataset.testid = `${variant}-${disabled ? 'disabled' : 'enabled'}`
        if (variant.includes('edit')) probe.dataset.action = 'edit'
        if (variant.includes('delete')) probe.dataset.action = 'delete'
        probe.textContent = `${variant} ${disabled ? '禁用' : '启用'}`
        const row = document.createElement('div')
        if (variant.startsWith('table-')) row.className = 'el-table'
        if (variant.startsWith('dialog-')) row.className = 'el-dialog__footer'
        if (variant.startsWith('message-')) row.className = 'el-message-box__btns'
        row.appendChild(probe); holder.appendChild(row)
      }
      button.parentElement.appendChild(holder)
    }, variants)
    for (const variant of variants) {
      const active = page.getByTestId(variant + '-enabled'), inactive = page.getByTestId(variant + '-disabled')
      await page.mouse.move(0, 0); await settle(inactive)
      const activeStyle = await appearance(active), inactiveStyle = await appearance(inactive)
      expect.soft(inactiveStyle.color, variant).not.toBe(activeStyle.color)
      expect.soft(inactiveStyle.color, variant).toBe(disabled.color)
      expect.soft(inactiveStyle.backgroundImage, variant).toBe('none')
      const readability = await controlContrast(inactive)
      contrastValues.push({ variant, ...readability })
      expect.soft(readability.ratio, variant).toBeGreaterThanOrEqual(4.5)
      expect.soft(readability.opacity, variant).toBe(1)
      if (variant === 'plain' || variant.endsWith('-solid')) {
        expect.soft(inactiveStyle.backgroundColor).not.toBe(activeStyle.backgroundColor)
        expect.soft(inactiveStyle.backgroundColor, variant).toBe(disabled.backgroundColor)
      }
      else {
        expect.soft(activeStyle.backgroundColor, variant).toBe('rgba(0, 0, 0, 0)')
        expect.soft(inactiveStyle.backgroundColor, variant).toBe('rgba(0, 0, 0, 0)')
      }
      await inactive.hover(); await settle(inactive)
      expect.soft(await appearance(inactive), variant).toEqual(inactiveStyle)
      await inactive.evaluate(element => element.classList.add('is-loading'))
      await settle(inactive)
      expect.soft(await appearance(inactive), `${variant} 业务禁用同时加载`).toEqual(inactiveStyle)
      await inactive.evaluate(element => element.classList.remove('is-loading', 'is-disabled'))
      await settle(inactive)
      expect.soft(await appearance(inactive), `${variant} 原生禁用`).toEqual(inactiveStyle)
      await inactive.evaluate(element => element.classList.add('is-disabled'))
    }
    await page.mouse.move(0, 0)
    const screenshotPath = test.info().outputPath('disabled-primary-' + colorScheme + '.png')
    await page.screenshot({ path: screenshotPath, fullPage: true })
    await test.info().attach('disabled-primary-' + colorScheme, { path: screenshotPath, contentType: 'image/png' })
    await test.info().attach('primary-button-states', { body: JSON.stringify({ colorScheme, enabled, disabled }, null, 2), contentType: 'application/json' })
    await fs.writeFile(test.info().outputPath('contrast-values.json'), JSON.stringify({ colorScheme, contrastValues }, null, 2))
    assertClean()
  })
}

async function controlContrast(locator) {
  return locator.evaluate(element => {
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = 1
    const context = canvas.getContext('2d')
    const rgba = color => {
      context.clearRect(0, 0, 1, 1)
      context.fillStyle = color
      context.fillRect(0, 0, 1, 1)
      return Array.from(context.getImageData(0, 0, 1, 1).data)
    }
    const composite = (front, back) => {
      const frontAlpha = front[3] / 255, backAlpha = back[3] / 255
      const alpha = frontAlpha + backAlpha * (1 - frontAlpha)
      if (alpha === 0) return [0, 0, 0, 0]
      return [...front.slice(0, 3).map((value, index) =>
        (value * frontAlpha + back[index] * backAlpha * (1 - frontAlpha)) / alpha), alpha * 255]
    }
    let foreground = rgba(getComputedStyle(element).color)
    let background = [0, 0, 0, 0]
    let opacity = 1
    for (let current = element; current; current = current.parentElement) {
      const style = getComputedStyle(current)
      const layer = rgba(style.backgroundColor), layerOpacity = Number(style.opacity)
      foreground = composite(foreground, layer)
      background = composite(background, layer)
      foreground[3] *= layerOpacity
      background[3] *= layerOpacity
      opacity *= layerOpacity
    }
    foreground = composite(foreground, [255, 255, 255, 255])
    background = composite(background, [255, 255, 255, 255])
    const luminance = color => color.slice(0, 3).map(value => value / 255)
      .map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4)
      .reduce((sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index], 0)
    const foregroundLuminance = luminance(foreground)
    const backgroundLuminance = luminance(background)
    return {
      ratio: (Math.max(foregroundLuminance, backgroundLuminance) + 0.05) / (Math.min(foregroundLuminance, backgroundLuminance) + 0.05),
      backgroundLuminance,
      opacity,
    }
  })
}

for (const accentPreset of ['MIDNIGHT_PURPLE', 'PEACH_PINK_GRADIENT']) {
  test(`深色 ${accentPreset} 的来源标签、通用操作及输入附加按钮可读`, async ({ page }) => {
    await page.addInitScript(preset => {
      localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({
        schemaVersion: 1, colorScheme: 'DARK', accentPreset: preset,
        sidebarTheme: 'DARK', contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false,
      }))
    }, accentPreset)
    const { assertClean } = await installDistRoutes(page, { apiData: createDetailApiData() })
    await page.goto('http://tianshu.local/index.html#/log')
    const sourceTag = page.locator('.el-table .el-tag').filter({ hasText: '服务端' }).first()
    const detail = page.locator('.el-table').getByRole('button', { name: '详情', exact: true }).first()
    for (const control of [sourceTag, detail]) {
      await expect(control).toBeVisible()
      await expect.poll(async () => (await controlContrast(control)).ratio).toBeGreaterThanOrEqual(4.5)
    }
    await detail.hover()
    await expect.poll(async () => (await controlContrast(detail)).ratio).toBeGreaterThanOrEqual(4.5)
    await page.goto('http://tianshu.local/index.html#/database/31')
    const generate = page.getByRole('button', { name: '生成', exact: true })
    await expect(generate).toBeVisible()
    await expect.poll(async () => (await controlContrast(generate)).ratio).toBeGreaterThanOrEqual(4.5)
    const testConnection = page.getByRole('button', { name: '测试连接', exact: true })
    await testConnection.hover()
    await expect.poll(async () => (await controlContrast(testConnection)).ratio).toBeGreaterThanOrEqual(4.5)
    await expect.poll(async () => (await controlContrast(testConnection)).backgroundLuminance).toBeLessThan(0.2)
    assertClean()
  })
}

for (const sidebarTheme of ['LIGHT', 'DARK']) {
  for (const navigationLayout of ['LEFT', 'TOP']) {
    test(`夜间桃粉主题的 ${sidebarTheme}/${navigationLayout} 活动导航保持清晰`, async ({ page }) => {
      await page.addInitScript(appearance => {
        localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({
          schemaVersion: 2, colorScheme: 'DARK', accentMode: 'PRESET', accentPreset: 'PEACH_PINK',
          customSolidColor: '#2639E9', customGradientColors: ['#2639E9', '#873FF2'],
          customGradientType: 'LINEAR', customGradientAngle: 135,
          contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false, ...appearance,
        }))
      }, { sidebarTheme, navigationLayout })
      const { assertClean } = await installDistRoutes(page, { apiData: createManagementApiData() })
      await page.goto('http://tianshu.local/index.html#/database')
      const menu = page.locator('.sidebar-menu__item.is-active, .top-navigation__item.is-active')
      await expect(menu).toBeVisible()
      const ratio = await menu.evaluate(element => {
        const style = getComputedStyle(element)
        const luminance = color => {
          const channels = color.match(/[\d.]+/g).slice(0, 3).map(Number).map(channel => {
            const value = channel / 255
            return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
          })
          return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
        }
        const foreground = luminance(style.color)
        const background = luminance(style.backgroundColor)
        return (Math.max(foreground, background) + 0.05) / (Math.min(foreground, background) + 0.05)
      })
      expect(ratio).toBeGreaterThanOrEqual(4.5)
      await expect(page.getByRole('tab', { name: '数据源配置', exact: true })).toBeVisible()
      assertClean()
    })
  }
}

test('看板初始加载不拉伸说明区，查询保留图表和页面布局', async ({ page }) => {
  await page.setViewportSize({ width: 1600, height: 1400 })
  const { assertClean } = await installDistRoutes(page)
  let release
  let pending = new Promise(resolve => { release = resolve })
  await page.route('**/api/rule/dashboard/**', async route => {
    await pending
    await route.fallback()
  })
  try {
    await page.goto('http://tianshu.local/index.html#/dashboard')
    await expect(page.locator('.dashboard-section__state')).toHaveCount(3)
    expect((await page.locator('.dashboard-heading').boundingBox()).height).toBeLessThan(70)
    release()
    await expect(page.locator('.dashboard-section__state')).toHaveCount(0)
    const chart = page.locator('.dashboard-chart canvas').first()
    await expect(chart).toBeVisible()
    const canvas = await chart.elementHandle()
    const heading = await page.locator('.dashboard-heading').boundingBox()
    const metrics = await page.locator('.dashboard-metrics').boundingBox()
    pending = new Promise(resolve => { release = resolve })
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await expect(page.locator('.dashboard-section[aria-busy="true"]')).toHaveCount(3)
    await expect(page.locator('.dashboard-metrics')).toBeVisible()
    expect(await canvas.evaluate(element => element.isConnected)).toBe(true)
    expect(await page.locator('.dashboard-heading').boundingBox()).toEqual(heading)
    expect(await page.locator('.dashboard-metrics').boundingBox()).toEqual(metrics)
    release()
    await expect(page.locator('.dashboard-section[aria-busy="true"]')).toHaveCount(0)
    expect(await canvas.evaluate(element => element.isConnected)).toBe(true)
    assertClean()
  } finally {
    release()
  }
})

test('看板地图可切换到市级和县级行政层级', async ({ page }) => {
  const { assertClean } = await installDistRoutes(page)
  await page.goto('http://tianshu.local/index.html#/dashboard')

  const level = page.locator('.dashboard-map-toolbar .el-select').nth(1)
  await expect(level).toBeVisible()
  await level.click()
  await page.getByRole('option', { name: '地市级行政区', exact: true }).click()
  await expect(level).toContainText('地市级行政区')

  await level.click()
  await page.getByRole('option', { name: '区县级行政区', exact: true }).click()
  await expect(level).toContainText('区县级行政区')
  assertClean()
})

test('期数和金额占比都无数据时隐藏占比整行但保留地图', async ({ page }) => {
  const { assertClean } = await installDistRoutes(page, {
    apiData: new Map([
      ['/api/rule/dashboard/applications', {
        visible: true,
        summary: { applicationCount: 0 },
        periods: { items: [], validCount: 0, excludedCount: 0 },
        amounts: { items: [], validCount: 0, excludedCount: 0 },
        geo: { points: [], validCount: 0, excludedCount: 0 },
        mappingIssues: []
      }]
    ])
  })
  await page.goto('http://tianshu.local/index.html#/dashboard')

  await expect(page.locator('.dashboard-distribution-grid')).toHaveCount(0)
  await expect(page.locator('.dashboard-chart-card--wide')).toBeVisible()
  assertClean()
})

test('全局字体、业务文本选择和关键按钮语义可用', async ({ page }) => {
  const fontResponses = []
  page.on('response', response => {
    if (new URL(response.url()).pathname === '/fonts/font.ttf') {
      fontResponses.push(response.status())
    }
  })

  const { assertClean } = await installDistRoutes(page)
  await page.goto('http://tianshu.local/index.html#/project')

  await expect(page.getByText('E2E 项目', { exact: true })).toBeVisible()
  await page.evaluate(() => document.fonts.load('16px "AlimamaFangYuanTiVF"'))
  expect(fontResponses).toContain(200)
  expect(
    await page.evaluate(() =>
      document.fonts.check('16px "AlimamaFangYuanTiVF"')
    )
  ).toBe(true)

  const projectCode = page.getByText('e2e_project', { exact: true })
  const selection = await projectCode.evaluate(element => {
    const range = document.createRange()
    range.selectNodeContents(element)
    const currentSelection = window.getSelection()
    currentSelection.removeAllRanges()
    currentSelection.addRange(range)
    return {
      text: currentSelection.toString(),
      userSelect: window.getComputedStyle(element).userSelect,
    }
  })
  expect(selection.text).toBe('e2e_project')
  expect(selection.userSelect).not.toBe('none')

  await expect(page.getByRole('button', { name: '查询' })).toBeVisible()
  await expect(page.getByRole('button', { name: '重置' })).toBeVisible()
  await expect(page.getByRole('button', { name: '新建项目' })).toBeVisible()
  assertClean()
})

test('打开主题设置抽屉不显示遮罩且不改变页面可视宽度', async ({ page }) => {
  const { assertClean } = await installDistRoutes(page)
  await page.goto('http://tianshu.local/index.html#/project')
  const before = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    bodyWidth: document.body.style.width,
    bodyOverflow: document.body.style.overflow
  }))

  await page.getByRole('button', { name: '本地用户的账户菜单' }).click()
  await expect(page.getByRole('menuitem', { name: '退出账号' })).toBeVisible()
  await page.getByRole('menuitem', { name: '主题设置' }).click()
  await expect(page.getByRole('heading', { name: '主题色' })).toBeVisible()
  await expect(page.locator('.el-overlay:visible')).toHaveCount(0)
  const after = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    bodyWidth: document.body.style.width,
    bodyOverflow: document.body.style.overflow
  }))

  expect(after).toEqual(before)
  assertClean()
})

test('本地用户可从右上角账户菜单退出', async ({ page }) => {
  const { assertClean, requests } = await installDistRoutes(page, {
    apiData: new Map([
      ['POST /api/auth/console/logout', true],
    ])
  })
  await page.goto('http://tianshu.local/index.html#/project')

  await page.getByRole('button', { name: '本地用户的账户菜单' }).click()
  await page.getByRole('menuitem', { name: '退出账号' }).click()

  await expect.poll(() => requests.filter(request =>
    request.method === 'POST' &&
    new URL(request.url).pathname === '/api/auth/console/logout'
  ).length).toBe(1)
  assertClean()
})

test('登录输入框使用 Element 内置交互且图标与当前主题一致', async ({
  page,
}) => {
  await page.addInitScript(() => {
    localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({
      schemaVersion: 1,
      colorScheme: 'DARK',
      accentPreset: 'LIQUID_PURPLE',
      sidebarTheme: 'DARK',
      contentWidth: 'FLUID',
      fixedSidebar: true,
      colorWeak: false,
    }))
  })
  const { assertClean } = await installDistRoutes(page, {
    apiData: new Map([
      ['/api/auth/console/config', { loginEnabled: true }],
    ])
  })
  await page.goto('http://tianshu.local/index.html#/login')

  const username = page.locator('input[autocomplete="username"]')
  const password = page.locator('input[autocomplete="current-password"]')
  const usernameRoot = username.locator(
    'xpath=ancestor::*[contains(concat(" ", normalize-space(@class), " "), " el-input ")][1]'
  )
  const passwordRoot = password.locator(
    'xpath=ancestor::*[contains(concat(" ", normalize-space(@class), " "), " el-input ")][1]'
  )

  await username.fill('theme-user')
  await username.hover()
  const clearButton = usernameRoot.locator('.el-input__clear')
  await expect(clearButton).toBeVisible()
  await clearButton.click()
  await expect(username).toHaveValue('')

  await password.fill('theme-password')
  const passwordToggle = passwordRoot.locator('.el-input__password')
  await expect(passwordToggle).toBeVisible()
  await expect(password).toHaveAttribute('type', 'password')
  await passwordToggle.click()
  await expect(password).toHaveAttribute('type', 'text')
  await passwordToggle.click()
  await expect(password).toHaveAttribute('type', 'password')

  await password.focus()
  const themedLayout = await passwordRoot.evaluate(root => {
    const wrapper = root.querySelector('.el-input__wrapper')
    const input = root.querySelector('input')
    const suffix = root.querySelector('.el-input__suffix')
    const wrapperRect = wrapper.getBoundingClientRect()
    const inputRect = input.getBoundingClientRect()
    const suffixRect = suffix.getBoundingClientRect()
    return {
      theme: document.documentElement.dataset.theme,
      primary: getComputedStyle(document.documentElement)
        .getPropertyValue('--el-color-primary').trim(),
      focusShadow: getComputedStyle(wrapper).boxShadow,
      suffixCenterDelta: Math.abs(
        suffixRect.top + suffixRect.height / 2 -
        (inputRect.top + inputRect.height / 2)
      ),
      suffixRightInset: wrapperRect.right - suffixRect.right,
    }
  })

  expect(themedLayout.theme).toBe('dark')
  expect(themedLayout.primary).toBe('#873FF2')
  expect(themedLayout.focusShadow).toContain('rgba(135, 63, 242')
  expect(themedLayout.suffixCenterDelta).toBeLessThanOrEqual(1)
  expect(themedLayout.suffixRightInset).toBeGreaterThanOrEqual(8)
  expect(themedLayout.suffixRightInset).toBeLessThanOrEqual(16)
  assertClean()
})

test('登录按钮加载时保留主题背景并仅轻微淡化', async ({ page }) => {
  const { assertClean } = await installDistRoutes(page, {
    apiData: new Map([
      ['/api/auth/console/config', { loginEnabled: true }],
      ['POST /api/auth/console/login', async () => {
        await new Promise(resolve => setTimeout(resolve, 800))
        return { username: 'e2e' }
      }],
      ['/api/auth/console/preferences/theme', null],
      ['/api/rule/project/options', []],
      ['POST /api/rule/console/activity', true],
    ])
  })
  await page.goto('http://tianshu.local/index.html#/login')

  await page.locator('input[autocomplete="username"]').fill('visual-test-user')
  await page.locator('input[autocomplete="current-password"]')
    .fill('visual-test-password')
  const loginButton = page.locator('button.login-btn')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(loginButton).toHaveClass(/is-loading/)

  const visual = await loginButton.evaluate(button => {
    const buttonStyle = getComputedStyle(button)
    const maskStyle = getComputedStyle(button, '::before')
    const themeBackground = getComputedStyle(document.documentElement)
      .getPropertyValue('--tianshu-brand-background').trim()
    const colorProbe = document.createElement('span')
    colorProbe.style.color = themeBackground
    document.body.appendChild(colorProbe)
    const resolvedThemeBackground = getComputedStyle(colorProbe).color
    colorProbe.style.color = 'var(--tianshu-brand-foreground)'
    const resolvedThemeForeground = getComputedStyle(colorProbe).color
    colorProbe.remove()
    return {
      color: buttonStyle.color,
      backgroundColor: buttonStyle.backgroundColor,
      maskBackgroundColor: maskStyle.backgroundColor,
      opacity: Number(buttonStyle.opacity),
      resolvedThemeBackground,
      resolvedThemeForeground,
      spinnerVisible: Boolean(button.querySelector('.el-icon.is-loading')),
      text: button.textContent.trim(),
    }
  })

  expect(visual.backgroundColor).toBe(visual.resolvedThemeBackground)
  expect(visual.color).toBe(visual.resolvedThemeForeground)
  const loadingReadability = await controlContrast(loginButton)
  expect(loadingReadability.ratio).toBeGreaterThanOrEqual(4.5)
  await fs.mkdir(test.info().outputDir, { recursive: true })
  await fs.writeFile(test.info().outputPath('contrast-values.json'), JSON.stringify({ loadingReadability }, null, 2))
  expect(visual.maskBackgroundColor).toBe('rgba(0, 0, 0, 0)')
  expect(visual.opacity).toBeGreaterThanOrEqual(0.85)
  expect(visual.opacity).toBeLessThan(1)
  expect(visual.spinnerVisible).toBe(true)
  expect(visual.text).toBe('登录中…')
  await expect(page).not.toHaveURL(/#\/login$/)
  assertClean()
})

test('浏览器图标可访问且非溢出页签不注册滚动阻塞监听', async ({ page }) => {
  await page.addInitScript(() => {
    window.__tianshuBlockingTabListeners = []
    const originalAddEventListener = EventTarget.prototype.addEventListener
    EventTarget.prototype.addEventListener = function (type, listener, options) {
      const passive = typeof options === 'object' && options?.passive === true
      if (
        ['wheel', 'touchstart', 'touchmove'].includes(type) &&
        !passive &&
        this instanceof Element &&
        (this.matches('[role="tablist"]') || this.classList.contains('el-tabs__nav'))
      ) {
        window.__tianshuBlockingTabListeners.push(type)
      }
      return originalAddEventListener.call(this, type, listener, options)
    }
  })
  const warnings = []
  const faviconStatuses = []
  page.on('response', response => {
    if (new URL(response.url()).pathname === '/favicon.ico') {
      faviconStatuses.push(response.status())
    }
  })
  page.on('console', message => {
    if (message.type() === 'warning') warnings.push(message.text())
  })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createManagementApiData()
  })

  await page.goto('http://tianshu.local/index.html#/variable')
  await expect(page.getByRole('tab', { name: '变量列表' })).toBeVisible()

  expect(await page.evaluate(() => window.__tianshuBlockingTabListeners))
    .toEqual([])

  await page.evaluate(() => console.warn(
    '[Violation] Added non-passive event listener regression probe'
  ))
  expect(warnings).toContain(
    '[Violation] Added non-passive event listener regression probe'
  )
  const faviconSize = await page.evaluate(() => new Promise(resolve => {
    const image = new Image()
    image.onload = () => resolve({ width: image.naturalWidth, height: image.naturalHeight })
    image.onerror = () => resolve(null)
    image.src = `/favicon.ico?contract=${Date.now()}`
  }))
  expect(faviconStatuses).toContain(200)
  expect(faviconSize).toEqual({ width: 64, height: 64 })
  assertClean()
})

test('管理端外层不滚动且业务内容区独立滚动', async ({ page }) => {
  const { assertClean } = await installDistRoutes(page)
  await page.goto('http://tianshu.local/index.html#/project')
  await expect(page.getByText('E2E 项目', { exact: true })).toBeVisible()

  const result = await page.locator('.layout-main').evaluate(main => {
    document.documentElement.classList.add('theme-sidebar--static')
    const spacer = document.createElement('div')
    spacer.style.height = '1600px'
    spacer.setAttribute('aria-hidden', 'true')
    main.appendChild(spacer)
    window.scrollTo(0, 240)
    main.scrollTop = 240
    return {
      documentScrollTop: document.scrollingElement.scrollTop,
      mainScrollTop: main.scrollTop,
      mainOverflowY: getComputedStyle(main).overflowY,
      shellHeight: document.querySelector('.layout-shell').getBoundingClientRect().height,
      viewportHeight: window.innerHeight,
    }
  })

  expect(result.documentScrollTop).toBe(0)
  expect(result.mainScrollTop).toBe(240)
  expect(result.mainOverflowY).toBe('auto')
  expect(result.shellHeight).toBe(result.viewportHeight)
  assertClean()
})

for (const appearance of [
  { colorScheme: 'LIGHT', navigationLayout: 'LEFT', contentWidth: 'FLUID', background: 'rgb(255, 255, 255)' },
  { colorScheme: 'DARK', navigationLayout: 'TOP', contentWidth: 'FIXED', background: 'rgb(21, 29, 49)' },
]) {
  test(`短页面底色铺满内容区且长页面仍可滚动：${appearance.colorScheme}`, async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1120 })
    const { assertClean } = await installDistRoutes(page, {
      apiData: createManagementApiData(),
    })
    await page.goto('http://tianshu.local/index.html#/database')
    const dataTab = page.getByRole('tabpanel', { name: '数据源配置' })
    await expect(dataTab.getByText('风控只读库', { exact: true })).toBeVisible()
    await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
    await page.getByRole('button', { name: '本地用户的账户菜单' }).click()
    await page.getByRole('menuitem', { name: '主题设置' }).click()
    await page.locator(`[data-theme-scheme="${appearance.colorScheme}"]`).click()
    await page.locator(`[data-navigation-layout="${appearance.navigationLayout}"]`).click()
    await page.locator(`[data-content-width="${appearance.contentWidth}"]`).click()
    await page.getByRole('button', { name: '保存设置', exact: true }).click()
    await expect(page.getByRole('dialog', { name: '主题设置' })).not.toBeVisible()

    const surface = page.locator('.layout-main > .uiue-list-page')
    await expect(surface).toHaveCSS('background-color', appearance.background)
    const layout = await surface.evaluate(element => {
      const main = element.parentElement
      const mainStyle = getComputedStyle(main)
      const rect = element.getBoundingClientRect()
      const availableBottom = main.getBoundingClientRect().bottom - parseFloat(mainStyle.paddingBottom)
      return {
        bottomGap: availableBottom - rect.bottom,
        width: rect.width,
        mainScrollRange: main.scrollHeight - main.clientHeight,
      }
    })
    expect(Math.abs(layout.bottomGap)).toBeLessThanOrEqual(1)
    expect(layout.mainScrollRange).toBe(0)
    if (appearance.contentWidth === 'FIXED') expect(layout.width).toBe(1440)

    await page.setViewportSize({ width: 1280, height: 420 })
    await dataTab.locator('.el-pagination').scrollIntoViewIfNeeded()
    await expect(dataTab.locator('.el-pagination')).toBeVisible()
    expect(await page.locator('.layout-main').evaluate(main => main.scrollTop)).toBeGreaterThan(0)
    expect(await page.evaluate(() => document.scrollingElement.scrollTop)).toBe(0)
    assertClean()
  })
}

test('数据加载只显示内容中央的透明 SVG 转圈', async ({ page }) => {
  let releaseProjects
  const projectsReady = new Promise(resolve => { releaseProjects = resolve })
  const apiData = createManagementApiData()
  apiData.set('/api/rule/project/list', async () => {
    await projectsReady
    return { records: [], total: 0 }
  })
  const { assertClean } = await installDistRoutes(page, { apiData })

  try {
    await page.goto('http://tianshu.local/index.html#/project')
    const table = page.locator('.el-table').first()
    const mask = table.locator('.el-loading-mask')
    const spinner = mask.locator('svg.circular')
    await expect(mask).toBeVisible()
    await expect(spinner).toBeVisible()

    const appearance = await Promise.all([
      table.boundingBox(),
      spinner.boundingBox(),
      mask.evaluate(element => getComputedStyle(element).backgroundColor),
    ])
    const [tableBox, spinnerBox, backgroundColor] = appearance
    expect(backgroundColor).toBe('rgba(0, 0, 0, 0)')
    expect(Math.abs(
      spinnerBox.x + spinnerBox.width / 2 - (tableBox.x + tableBox.width / 2)
    )).toBeLessThanOrEqual(2)
    expect(Math.abs(
      spinnerBox.y + spinnerBox.height / 2 - (tableBox.y + tableBox.height / 2)
    )).toBeLessThanOrEqual(2)
  } finally {
    releaseProjects()
  }

  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  assertClean()
})

test('右上角账户头像和名字视觉边界对齐且侧栏不再显示账号区', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('tianshu:layout:sidebar', JSON.stringify({
      width: 64,
      lastExpandedWidth: 220,
    }))
  })
  const { assertClean } = await installDistRoutes(page, {
    apiData: new Map([
      ['/api/auth/console/config', { loginEnabled: true }],
      ['/api/auth/console/me', {
        username: 'admin',
        permissions: ['project:view'],
      }],
      ['POST /api/rule/console/activity', true],
      ['/api/auth/console/preferences/theme', {
        schemaVersion: 1,
        colorScheme: 'LIGHT',
        accentPreset: 'THEME_BLUE',
        sidebarTheme: 'DARK',
        contentWidth: 'FLUID',
        fixedSidebar: true,
        colorWeak: false,
      }],
    ])
  })
  await page.goto('http://tianshu.local/index.html#/project')

  const trigger = page.getByRole('button', { name: 'admin的账户菜单' })
  const avatar = trigger.locator('.layout-account-avatar')
  const name = trigger.locator('.layout-account-name')
  await expect(trigger).toBeVisible()
  await trigger.focus()
  const appearance = await trigger.evaluate(element => {
    const avatarElement = element.querySelector('.layout-account-avatar')
    const nameElement = element.querySelector('.layout-account-name')
    const avatarRect = avatarElement.getBoundingClientRect()
    const nameRect = nameElement.getBoundingClientRect()
    const style = getComputedStyle(element)
    return {
      avatarHeight: avatarRect.height,
      nameHeight: nameRect.height,
      topDelta: Math.abs(avatarRect.top - nameRect.top),
      bottomDelta: Math.abs(avatarRect.bottom - nameRect.bottom),
      borderTopWidth: style.borderTopWidth,
      outlineStyle: style.outlineStyle,
      outlineWidth: style.outlineWidth,
    }
  })

  expect(appearance).toMatchObject({
    avatarHeight: 24,
    nameHeight: 24,
    borderTopWidth: '0px',
    outlineStyle: 'solid',
    outlineWidth: '2px',
  })
  expect(appearance.topDelta).toBeLessThanOrEqual(1)
  expect(appearance.bottomDelta).toBeLessThanOrEqual(1)
  await expect(avatar).toHaveText('A')
  await expect(name).toHaveText('admin')
  await expect(page.locator('.sidebar-account')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '页签操作' })).toHaveCount(0)

  await trigger.click()
  await expect(page.getByRole('menuitem', { name: '用户设置' })).toBeVisible()
  await expect(page.getByRole('menuitem', { name: '主题设置' })).toBeVisible()
  await expect(page.getByRole('menuitem', { name: '退出账号' })).toBeVisible()
  assertClean()
})

test('表头、状态、作用范围和普通操作随主题色切换', async ({ page }) => {
  const { assertClean } = await installDistRoutes(page, {
    apiData: createManagementApiData()
  })
  await page.goto('http://tianshu.local/index.html#/variable')
  await expect(page.getByText('年龄', { exact: true })).toBeVisible()

  const table = page.locator('.var-list-section .el-table').first()
  await expect(table.getByRole('button', { name: '删除', exact: true }).first()).toBeVisible()
  const readAppearance = () => table.evaluate(element => {
    const header = Array.from(element.querySelectorAll('th.el-table__cell'))
      .find(cell => cell.textContent.includes('作用范围'))
    const scope = element.querySelector('.el-tag--scope-project')
    const status = element.querySelector('.el-tag--success')
    const edit = Array.from(element.querySelectorAll('button'))
      .find(button => button.textContent.trim() === '编辑')
    const remove = element.querySelector('[data-action="delete"]')
    const normalizeColor = value => {
      const probe = document.createElement('span')
      probe.style.color = value
      document.body.appendChild(probe)
      const color = getComputedStyle(probe).color
      probe.remove()
      return color
    }
    return {
      accent: normalizeColor(
        getComputedStyle(document.documentElement)
          .getPropertyValue('--tianshu-table-accent')
      ),
      editAction: normalizeColor(
        getComputedStyle(document.documentElement)
          .getPropertyValue('--tianshu-action-edit')
      ),
      successStatus: normalizeColor(
        getComputedStyle(document.documentElement)
          .getPropertyValue('--tianshu-status-success-text')
      ),
      deleteAction: normalizeColor(
        getComputedStyle(document.documentElement)
          .getPropertyValue('--tianshu-action-delete')
      ),
      headerBackground: getComputedStyle(header).backgroundColor,
      scopeColor: getComputedStyle(scope).color,
      scopeBorder: getComputedStyle(scope).borderColor,
      statusColor: getComputedStyle(status).color,
      editColor: getComputedStyle(edit).color,
      deleteColor: getComputedStyle(remove).color,
    }
  })

  const before = await readAppearance()
  await page.getByRole('button', { name: '本地用户的账户菜单' }).click()
  await page.getByRole('menuitem', { name: '主题设置' }).click()
  await page.getByRole('button', { name: '凝液紫', exact: true }).click()
  await expect.poll(async () => {
    const current = await readAppearance()
    return {
      headerUpdated: current.headerBackground !== before.headerBackground,
      scopeUpdated: current.scopeBorder !== before.scopeBorder,
      scopeUsesAccent: current.scopeColor === current.accent,
      statusUsesSemanticColor: current.statusColor === current.successStatus,
      editUsesActionColor: current.editColor === current.editAction,
      deleteUsesActionColor: current.deleteColor === current.deleteAction,
    }
  }).toEqual({
    headerUpdated: true,
    scopeUpdated: true,
    scopeUsesAccent: true,
    statusUsesSemanticColor: true,
    editUsesActionColor: true,
    deleteUsesActionColor: true,
  })
  const after = await readAppearance()

  expect(after.headerBackground).not.toBe(before.headerBackground)
  expect(after.scopeBorder).not.toBe(before.scopeBorder)
  expect(after.scopeColor).toBe(after.accent)
  expect(after.statusColor).toBe(after.successStatus)
  expect(after.editColor).toBe(after.editAction)
  expect(after.deleteColor).toBe(after.deleteAction)
  expect(after.deleteColor).not.toBe(after.editColor)

  await page.getByRole('button', { name: '夜间模式', exact: true }).click()
  await expect.poll(async () => {
    const current = await readAppearance()
    return {
      headerUpdated: current.headerBackground !== after.headerBackground,
      scopeUsesAccent: current.scopeColor === current.accent,
      statusUsesSemanticColor: current.statusColor === current.successStatus,
      editUsesActionColor: current.editColor === current.editAction,
      deleteUsesActionColor: current.deleteColor === current.deleteAction,
      dangerRemainsDistinct: current.deleteColor !== current.editColor,
    }
  }).toEqual({
    headerUpdated: true,
    scopeUsesAccent: true,
    statusUsesSemanticColor: true,
    editUsesActionColor: true,
    deleteUsesActionColor: true,
    dangerRemainsDistinct: true,
  })
  assertClean()
})

test('项目列表同一行操作使用互不重复且语义稳定的主题色', async ({ page }) => {
  const apiData = createManagementApiData()
  apiData.set('/api/rule/governance/workspaces/states', ({ url }) => url.searchParams.get('ids').split(',').map(resourceId => ({ resourceId, effectiveStatus: 'ACTIVE', requestStatus: 'EDITING', action: 'UPDATE', owned: true })))
  const { assertClean } = await installDistRoutes(page, {
    apiData
  })
  await page.goto('http://tianshu.local/index.html#/project')
  const row = page.getByRole('row').filter({ hasText: 'E2E 项目' })
  const actions = row.locator('button[data-action]')
  await expect(actions).toHaveCount(7)
  await expect(row.getByRole('button', { name: '发布', exact: true })).toBeEnabled()
  await actions.evaluateAll(async buttons => {
    await new Promise(requestAnimationFrame)
    await Promise.all(buttons.flatMap(button => button.getAnimations().filter(animation => animation.effect?.getComputedTiming().iterations !== Infinity).map(animation => animation.finished.catch(() => {}))))
  })

  const appearance = await actions.evaluateAll(buttons => buttons.map(button => ({
    action: button.dataset.action,
    color: getComputedStyle(button).color,
  })))
  expect(appearance.map(item => item.action)).toEqual([
    'version',
    'publish',
    'edit',
    'detail',
    'configure',
    'docs',
    'delete',
  ])
  expect(new Set(appearance.map(item => item.color)).size).toBe(appearance.length)

  const deleteColor = appearance.find(item => item.action === 'delete').color
  const [red, green, blue] = deleteColor.match(/\d+/g).map(Number)
  expect(red).toBeGreaterThan(green * 1.8)
  expect(red).toBeGreaterThan(blue * 1.4)
  assertClean()
})
