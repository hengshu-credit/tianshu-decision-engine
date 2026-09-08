const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createManagementApiData } = require('./support/managementFixtures.cjs')

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
    colorProbe.remove()
    return {
      backgroundColor: buttonStyle.backgroundColor,
      maskBackgroundColor: maskStyle.backgroundColor,
      opacity: Number(buttonStyle.opacity),
      resolvedThemeBackground,
      spinnerVisible: Boolean(button.querySelector('.el-icon.is-loading')),
      text: button.textContent.trim(),
    }
  })

  expect(visual.backgroundColor).toBe(visual.resolvedThemeBackground)
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
  const readAppearance = () => table.evaluate(element => {
    const header = Array.from(element.querySelectorAll('th.el-table__cell'))
      .find(cell => cell.textContent.includes('作用范围'))
    const scope = element.querySelector('.el-tag--scope-project')
    const status = element.querySelector('.el-tag--success')
    const edit = Array.from(element.querySelectorAll('button'))
      .find(button => button.textContent.trim() === '编辑')
    const remove = Array.from(element.querySelectorAll('button'))
      .find(button => button.textContent.trim() === '删除')
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
      statusUsesAccent: current.statusColor === current.accent,
      editUsesActionColor: current.editColor === current.editAction,
      deleteUsesActionColor: current.deleteColor === current.deleteAction,
    }
  }).toEqual({
    headerUpdated: true,
    scopeUpdated: true,
    scopeUsesAccent: true,
    statusUsesAccent: true,
    editUsesActionColor: true,
    deleteUsesActionColor: true,
  })
  const after = await readAppearance()

  expect(after.headerBackground).not.toBe(before.headerBackground)
  expect(after.scopeBorder).not.toBe(before.scopeBorder)
  expect(after.scopeColor).toBe(after.accent)
  expect(after.statusColor).toBe(after.accent)
  expect(after.editColor).toBe(after.editAction)
  expect(after.deleteColor).toBe(after.deleteAction)
  expect(after.deleteColor).not.toBe(after.editColor)

  await page.getByRole('button', { name: '夜间模式', exact: true }).click()
  await expect.poll(async () => {
    const current = await readAppearance()
    return {
      headerUpdated: current.headerBackground !== after.headerBackground,
      scopeUsesAccent: current.scopeColor === current.accent,
      statusUsesAccent: current.statusColor === current.accent,
      editUsesActionColor: current.editColor === current.editAction,
      deleteUsesActionColor: current.deleteColor === current.deleteAction,
      dangerRemainsDistinct: current.deleteColor !== current.editColor,
    }
  }).toEqual({
    headerUpdated: true,
    scopeUsesAccent: true,
    statusUsesAccent: true,
    editUsesActionColor: true,
    deleteUsesActionColor: true,
    dangerRemainsDistinct: true,
  })
  assertClean()
})

test('项目列表同一行操作使用互不重复且语义稳定的主题色', async ({ page }) => {
  const { assertClean } = await installDistRoutes(page, {
    apiData: createManagementApiData()
  })
  await page.goto('http://tianshu.local/index.html#/project')
  const row = page.getByRole('row').filter({ hasText: 'E2E 项目' })
  const actions = row.locator('button[data-action]')
  await expect(actions).toHaveCount(5)

  const appearance = await actions.evaluateAll(buttons => buttons.map(button => ({
    action: button.dataset.action,
    color: getComputedStyle(button).color,
  })))
  expect(appearance.map(item => item.action)).toEqual([
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
