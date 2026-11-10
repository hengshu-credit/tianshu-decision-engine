const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createManagementApiData } = require('./support/managementFixtures.cjs')
const { createOperationsApiData } = require('./support/operationsFixtures.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')
const { createDocsApiData } = require('./support/docsFixtures.cjs')
const { DEFAULT_THEME_CONFIG } = require('../../src/theme/themeConfig.js')

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

async function expectReadableRadioGroup(group, labels) {
  const buttons = group.locator('.el-radio-button__inner')
  await expect(buttons).toHaveText(labels)
  for (const button of await buttons.all()) {
    const geometry = await button.evaluate(element => {
      const style = getComputedStyle(element)
      const box = element.getBoundingClientRect()
      const range = document.createRange()
      range.selectNodeContents(element)
      const text = range.getBoundingClientRect()
      return {
        fontSize: Number.parseFloat(style.fontSize), height: box.height,
        textWidth: text.width, textHeight: text.height,
        textInside: text.left >= box.left && text.right <= box.right && text.top >= box.top && text.bottom <= box.bottom,
        controlHeight: Number.parseFloat(style.getPropertyValue('--el-component-size-small')),
      }
    })
    expect(geometry.fontSize).toBe(14)
    expect(geometry.textWidth).toBeGreaterThan(0)
    expect(geometry.textHeight).toBeGreaterThan(0)
    expect(geometry.textInside).toBe(true)
    expect(geometry.height).toBe(geometry.controlHeight)
    expect((await appearance(button)).ratio).toBeGreaterThanOrEqual(4.5)
  }
}

async function expectThemePalette(control, selected, state = '') {
  await expect(async () => {
    const colors = await control.evaluate((element, { selected, state }) => {
      const probe = document.createElement('span')
      const prefix = `--tianshu-button-primary-${state ? `${state}-` : ''}`
      probe.style.color = `var(${state === 'disabled' ? '--tianshu-text-secondary' : selected ? `${prefix}text` : '--tianshu-primary-text'})`
      probe.style.background = `var(${state === 'disabled' ? '--tianshu-bg-muted' : selected ? `${prefix}bg` : `--tianshu-primary-soft-${state ? `${state}-` : ''}bg`})`
      probe.style.borderColor = `var(${state === 'disabled' ? '--tianshu-border-subtle' : selected ? `${prefix}border` : '--el-color-primary'})`
      document.body.appendChild(probe)
      const read = node => {
        const style = getComputedStyle(node)
        return { color: style.color, background: style.backgroundColor, image: style.backgroundImage, border: style.borderColor }
      }
      const result = { actual: read(element), expected: read(probe) }
      probe.remove()
      return result
    }, { selected, state })
    expect(colors.actual).toEqual(colors.expected)
    expect((await appearance(control)).ratio).toBeGreaterThanOrEqual(4.5)
  }).toPass({ timeout: 2000 })
}

for (const colorScheme of ['LIGHT', 'DARK']) {
  test(`${colorScheme} 规则、API、分流和账户配置共用主题按钮且保留配置交互`, async ({ page }, testInfo) => {
    test.setTimeout(90000)
    page.setDefaultTimeout(7000)
    const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
    await page.addInitScript(theme => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify(theme)), {
      ...DEFAULT_THEME_CONFIG, colorScheme, accentPreset: 'PEACH_PINK_GRADIENT',
    })
    const apiData = new Map([...createDocsApiData(), ...createDetailApiData(), ...createOperationsApiData()])
    apiData.set('/api/rule/console/permissions', [{ permissionCode: 'field:view', permissionName: '查看字段管理', permissionGroup: '字段管理' }])
    const apiConfig = apiData.get('/api/rule/datasource/api-config/22')
    let releaseConfig
    const configReady = new Promise(resolve => { releaseConfig = resolve })
    apiData.set('/api/rule/datasource/api-config/22', async () => { await configReady; return apiConfig })
    const { assertClean } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData })
    await page.goto(`${origin}/#/test`)
    const inputMode = page.getByRole('radiogroup', { name: '输入方式' })
    await expectReadableRadioGroup(inputMode, ['表单填写', 'JSON 编辑'])
    await inputMode.getByText('JSON 编辑', { exact: true }).click()
    await expect(inputMode.getByRole('radio', { name: 'JSON 编辑' })).toBeChecked()
    await inputMode.getByText('表单填写', { exact: true }).click()
    await page.screenshot({ path: testInfo.outputPath(`rule-controls-${colorScheme}.png`) })

    await page.goto(`${origin}/#/datasource/api/22`)
    const requestMode = page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: /^调用模式$/ }) }).locator('.el-radio-group')
    try {
      await expect(page.locator('.detail-form')).toHaveAttribute('aria-busy', 'true')
      await expect(requestMode.getByRole('radio', { name: '同步' })).toBeDisabled()
      await expectReadableRadioGroup(requestMode, ['同步', '异步'])
      for (const control of await requestMode.locator('.el-radio-button__inner').all()) await expectThemePalette(control, false, 'disabled')
    } finally { releaseConfig() }
    await expect(page.locator('.detail-form')).toHaveAttribute('aria-busy', 'false')
    await expectReadableRadioGroup(requestMode, ['同步', '异步'])
    await requestMode.getByText('异步', { exact: true }).click()
    await expect(requestMode.getByRole('radio', { name: '异步' })).toBeChecked()
    await requestMode.getByText('同步', { exact: true }).click()
    await page.getByRole('button', { name: /业务配置/ }).click()
    await page.getByRole('tab', { name: '接口鉴权', exact: true }).click()
    const refresh = page.locator('.el-form-item').filter({ hasText: '鉴权失败刷新' }).getByRole('switch')
    if (!(await refresh.isChecked())) await refresh.check()
    const failure = page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: /^Token 失效条件$/ }) }).locator('.el-radio-group')
    await expectReadableRadioGroup(failure, ['HTTP 401 / 403', '自定义条件'])
    await failure.getByText('自定义条件', { exact: true }).click()
    await expect(failure.getByRole('radio', { name: '自定义条件' })).toBeChecked()
    await failure.getByText('HTTP 401 / 403', { exact: true }).click()
    await page.getByRole('tab', { name: '报文留存', exact: true }).click()
    for (const [index, labels] of [[0, ['实际发送内容', '处理中间副本']], [1, ['原始内容', '处理中间副本']]]) {
      const card = page.locator('.payload-capture-card').nth(index)
      await expectReadableRadioGroup(card.locator('.el-radio-group'), labels)
      await card.getByText('处理中间副本', { exact: true }).click()
      await expect(card.getByRole('radio', { name: '处理中间副本' })).toBeChecked()
      await card.getByRole('button', { name: '添加路径', exact: true }).click()
      await expect(card.locator('.payload-path-row')).toHaveCount(1)
      await card.getByRole('button', { name: '添加路径', exact: true }).hover()
      await expectThemePalette(card.getByRole('button', { name: '添加路径', exact: true }), false, 'hover')
      await card.getByRole('button', { name: '删除', exact: true }).click()
      await expect(card.locator('.payload-path-row')).toHaveCount(0)
      await card.getByRole('button', { name: '添加超长字段', exact: true }).click()
      await expect(card.locator('.oversized-field-row')).toHaveCount(1)
      await card.getByRole('button', { name: '删除', exact: true }).click()
    }
    await page.screenshot({ path: testInfo.outputPath(`payload-controls-${colorScheme}.png`) })

    await page.goto(`${origin}/#/experiment/detail/61`)
    await page.getByRole('tab', { name: '空跑测试', exact: true }).click()
    const mode = page.locator('.test-mode')
    await expectReadableRadioGroup(mode.locator('.el-radio-group'), ['随机分流', '条件分流'])
    await mode.getByText('条件分流', { exact: true }).click()
    const mutual = mode.getByRole('switch')
    await mutual.check()
    await page.mouse.move(0, 0)
    await mutual.blur()
    await expectThemePalette(mode.locator('.el-switch__core'), true)
    await mode.locator('.el-switch__core').click()
    await expect(mutual).not.toBeChecked()
    await mutual.press('Enter')
    await expect(mutual).toBeChecked()
    await expectThemePalette(mode.locator('.el-switch__core'), true, 'hover')
    const checkModeLayout = async () => {
      expect(await mode.locator('.el-radio-button__inner').evaluateAll(elements => new Set(elements.map(element => Math.round(element.getBoundingClientRect().top))).size)).toBe(1)
      for (const label of await mode.locator('.el-switch__label').all()) {
        await expect(label).toHaveCSS('white-space', 'nowrap')
        expect(await label.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)
      }
    }
    await checkModeLayout()
    await page.screenshot({ path: testInfo.outputPath(`experiment-controls-${colorScheme}.png`) })
    await page.setViewportSize({ width: 760, height: 900 })
    await checkModeLayout()
    await page.setViewportSize({ width: 1280, height: 720 })

    await page.goto(`${origin}/#/account`)
    await page.getByRole('button', { name: '权限调整', exact: true }).first().click()
    const dialog = page.getByRole('dialog', { name: '账户权限调整', exact: true })
    await expect(dialog).toBeVisible()
    const projectScope = dialog.locator('.el-form .el-radio-group').first()
    await expectReadableRadioGroup(projectScope, ['不限制项目', '仅指定项目'])
    await projectScope.getByText('仅指定项目', { exact: true }).click()
    await expect(projectScope.getByRole('radio', { name: '仅指定项目' })).toBeChecked()
    await expect(dialog.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: /^可访问项目$/ }) }).locator('.el-select')).toBeVisible()
    const override = dialog.locator('.el-table .el-radio-group').first()
    await expectReadableRadioGroup(override, ['继承', '允许', '拒绝'])
    await override.getByText('拒绝', { exact: true }).click()
    await expect(override.getByRole('radio', { name: '拒绝' })).toBeChecked()
    await expect(dialog.locator('.el-table__body tr').first().locator('.el-tag').last()).toHaveText('拒绝')
    await override.getByText('允许', { exact: true }).click()
    await expect(dialog.locator('.el-table__body tr').first().locator('.el-tag').last()).toHaveText('允许')
    await page.screenshot({ path: testInfo.outputPath(`account-controls-${colorScheme}.png`) })
    assertClean()
  })

  for (const [name, overrides] of themes) {
    test(`${colorScheme} ${name} 分段配置按钮使用完整主题交互状态`, async ({ page }) => {
      const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
      await page.addInitScript(theme => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify(theme)), { ...DEFAULT_THEME_CONFIG, colorScheme, ...overrides })
      const harness = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData: new Map([...createDetailApiData(), ...createOperationsApiData()]) })
      await page.goto(`${origin}/#/test`)
      const group = page.locator('.test-left .el-radio-group').first()
      await expectReadableRadioGroup(group, ['全部', '项目级', '全局'])
      const selected = group.locator('.is-active .el-radio-button__inner')
      const inactive = group.getByText('全局', { exact: true })
      await expectThemePalette(selected, true)
      await expectThemePalette(inactive, false)
      for (const [control, active] of [[inactive, false], [selected, true]]) {
        await control.hover()
        await expectThemePalette(control, active, 'hover')
        await page.mouse.down()
        try { await expectThemePalette(control, active, 'active') }
        finally { await page.mouse.up() }
        await group.getByText('全部', { exact: true }).click()
        await page.mouse.move(0, 0)
      }
      await group.getByRole('radio', { name: '全部', exact: true }).press('ArrowRight')
      await expect(group.getByRole('radio', { name: '项目级', exact: true })).toBeChecked()
      await expectThemePalette(selected, true, 'hover')
      await expect(selected).toHaveCSS('outline-style', 'solid')
      await expect(selected).toHaveCSS('outline-width', '2px')
      await page.goto(`${origin}/#/experiment/detail/61`)
      await page.getByRole('tab', { name: '空跑测试', exact: true }).click()
      const mode = page.locator('.test-mode')
      await mode.getByText('条件分流', { exact: true }).click()
      const mutual = mode.getByRole('switch')
      await mutual.check()
      await mutual.blur()
      await page.mouse.move(0, 0)
      const core = mode.locator('.el-switch__core')
      await expectThemePalette(core, true)
      await core.hover()
      await expectThemePalette(core, true, 'hover')
      await page.mouse.down()
      try { await expectThemePalette(core, true, 'active') }
      finally { await page.mouse.up() }
      harness.assertClean()
    })
  }
}

for (const colorScheme of ['LIGHT', 'DARK']) {
  test(`${colorScheme} 真实单选按钮文字可见、尺寸一致，方向切换和跨页复用正常`, async ({ page }, testInfo) => {
    const origin = process.env.E2E_DEV_BASE_URL || 'http://tianshu.local'
    await page.addInitScript(theme => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify(theme)), { ...DEFAULT_THEME_CONFIG, colorScheme })
    const apiData = new Map([...createDetailApiData(), ...createOperationsApiData()])
    const { assertClean } = await installDistRoutes(page, { origin, liveStatic: Boolean(process.env.E2E_DEV_BASE_URL), apiData })
    await page.goto(`${origin}/#/lineage`)
    const direction = page.locator('.query-panel .el-radio-group')
    await expectReadableRadioGroup(direction, ['全部', '上游', '下游'])
    await expect(direction.getByRole('radio', { name: '全部', exact: true })).toBeChecked()
    await page.getByRole('combobox', { name: '起点', exact: true }).click()
    await page.getByRole('option', { name: '年龄 (age)', exact: true }).click()
    await page.getByRole('button', { name: '生成血缘图', exact: true }).click()
    await expect(page.locator('.graph-canvas .lf-node')).toHaveCount(2)
    await direction.getByText('上游', { exact: true }).click()
    await expect(direction.getByRole('radio', { name: '上游', exact: true })).toBeChecked()
    await expect(page.locator('.graph-canvas .lf-node')).toHaveCount(1)
    await expectReadableRadioGroup(direction, ['全部', '上游', '下游'])
    await direction.getByRole('radio', { name: '上游', exact: true }).press('ArrowRight')
    await expect(direction.getByRole('radio', { name: '下游', exact: true })).toBeChecked()
    await expect(page.locator('.graph-canvas .lf-node')).toHaveCount(2)
    await direction.getByText('全部', { exact: true }).click()
    await expect(direction.getByRole('radio', { name: '全部', exact: true })).toBeChecked()
    await direction.screenshot({ path: testInfo.outputPath(`direction-control-${colorScheme}.png`) })
    await page.screenshot({ path: testInfo.outputPath(`lineage-direction-${colorScheme}.png`) })
    await page.setViewportSize({ width: 560, height: 900 })
    await expectReadableRadioGroup(direction, ['全部', '上游', '下游'])
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1)
    await page.screenshot({ path: testInfo.outputPath(`lineage-direction-narrow-${colorScheme}.png`) })
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.locator('[data-menu-path="/test"]').click()
    await expectReadableRadioGroup(page.locator('.test-left .el-radio-group').first(), ['全部', '项目级', '全局'])
    await page.goto(`${origin}/#/database/new`)
    const connection = page.locator('.detail-form .el-radio-group').first()
    await expectReadableRadioGroup(connection, ['直连', 'SSH隧道'])
    await connection.getByText('SSH隧道', { exact: true }).click()
    await expect(connection.getByRole('radio', { name: 'SSH隧道', exact: true })).toBeChecked()
    assertClean()
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
