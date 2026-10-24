import {
  ACCENT_PRESETS,
  DEFAULT_THEME_CONFIG,
} from '@/theme/themeConfig'
import {
  LOCAL_THEME_STORAGE_KEY,
  applyTheme,
  bootstrapLocalTheme,
  readLocalTheme,
  writeLocalTheme,
} from '@/theme/themeRuntime'

describe('themeRuntime — 全局主题应用和本地降级', () => {
  test.each(['LIGHT', 'DARK'])('%s 的实心按钮在预设及极端自定义色的各交互状态均可读', colorScheme => {
    const cases = [
      ...ACCENT_PRESETS.map(preset => ({ accentPreset: preset.id })),
      ...['#000000', '#FFFFFF', '#FFFF00', '#00FFFF', '#FF0000', '#808080'].map(customSolidColor => ({ accentMode: 'CUSTOM_SOLID', customSolidColor })),
      ...[['#000000', '#FFFFFF'], ['#FFFF00', '#0000FF'], ['#FF0000', '#00FF00', '#0000FF']].flatMap(customGradientColors =>
        ['LINEAR', 'RADIAL'].map(customGradientType => ({ accentMode: 'CUSTOM_GRADIENT', customGradientColors, customGradientType }))
      ),
    ]
    for (const config of cases) {
      applyTheme({ ...DEFAULT_THEME_CONFIG, colorScheme, ...config })
      const style = document.documentElement.style
      for (const type of ['primary', 'success', 'warning', 'danger']) {
        for (const state of ['', 'hover-', 'active-']) {
          const text = style.getPropertyValue(`--tianshu-button-${type}-${state}text`)
          const colors = style.getPropertyValue(`--tianshu-button-${type}-${state}bg`).match(/#[\da-f]{6}/gi)
          expect(colors, JSON.stringify(config)).not.toBeNull()
          const samples = colors.flatMap((color, index) => index === 0 ? [color]
            : Array.from({ length: 100 }, (_, step) => compositeColor(colors[index - 1], color, (step + 1) / 100)))
          for (const background of samples) {
            expect(contrastRatio(text, background), `${JSON.stringify(config)} / ${type} / ${state} / ${background}`).toBeGreaterThanOrEqual(4.5)
          }
        }
      }
    }
  })

  test.each(['LIGHT', 'DARK'])('%s 下按钮根据实际底色选择深浅文字，悬停和按下有独立反馈', colorScheme => {
    for (const [color, foreground] of [['#FFFFFF', '#19103B'], ['#000000', '#FFFFFF'], ['#FFFF00', '#19103B']]) {
      applyTheme({ ...DEFAULT_THEME_CONFIG, colorScheme, accentMode: 'CUSTOM_SOLID', customSolidColor: color })
      const style = document.documentElement.style
      expect(style.getPropertyValue('--tianshu-button-primary-text')).toBe(foreground)
      expect(style.getPropertyValue('--tianshu-button-primary-bg')).toBe(color)
      expect(style.getPropertyValue('--tianshu-button-primary-hover-bg')).not.toBe(color)
      expect(style.getPropertyValue('--tianshu-button-primary-active-bg')).not.toBe(style.getPropertyValue('--tianshu-button-primary-hover-bg'))
      expect(style.getPropertyValue('--el-color-primary')).toBe(color)
    }
  })

  test.each(['LIGHT', 'DARK'])('%s 的中等亮度粉色优先白字，浅粉和亮绿仍使用深色字', colorScheme => {
    for (const [color, foreground] of [
      ['#FF6B9D', '#FFFFFF'], ['#FF5B96', '#FFFFFF'], ['#E14ECA', '#FFFFFF'],
      ['#808080', '#FFFFFF'], ['#FFB6C1', '#19103B'], ['#FDE7EF', '#19103B'],
      ['#AAAAAA', '#19103B'], ['#00FF00', '#19103B'],
    ]) {
      applyTheme({ ...DEFAULT_THEME_CONFIG, colorScheme, accentMode: 'CUSTOM_SOLID', customSolidColor: color })
      const style = document.documentElement.style
      expect(style.getPropertyValue('--el-color-primary'), color).toBe(color)
      expect(style.getPropertyValue('--tianshu-brand-background'), color).toBe(color)
      for (const state of ['', 'hover-', 'active-']) {
        expect(style.getPropertyValue(`--tianshu-button-primary-${state}text`), color).toBe(foreground)
        expect(contrastRatio(foreground, style.getPropertyValue(`--tianshu-button-primary-${state}bg`)), color).toBeGreaterThanOrEqual(4.5)
      }
    }
  })

  beforeEach(() => {
    window.localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
    document.documentElement.removeAttribute('data-sidebar-theme')
    document.documentElement.removeAttribute('data-navigation-layout')
    document.documentElement.removeAttribute('data-table-scroll-mode')
    document.documentElement.className = ''
    document.documentElement.removeAttribute('style')
  })

  test('表格滚动模式能应用、持久化并恢复默认', () => {
    const auto = { ...DEFAULT_THEME_CONFIG, tableScrollMode: 'AUTO' }
    writeLocalTheme(window.localStorage, auto)
    bootstrapLocalTheme(window.localStorage)
    expect(document.documentElement.dataset.tableScrollMode).toBe('auto')
    expect(readLocalTheme(window.localStorage).tableScrollMode).toBe('AUTO')
    window.localStorage.clear()
    bootstrapLocalTheme(window.localStorage)
    expect(document.documentElement.dataset.tableScrollMode).toBe('fixed')
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(document.documentElement.dataset.navigationLayout).toBe('left')
    expect(document.documentElement.dataset.sidebarTheme).toBe('dark')
    expect(document.documentElement.classList.contains('theme-content--fixed')).toBe(false)
    expect(document.documentElement.style.getPropertyValue('--tianshu-brand-background'))
      .toBe('linear-gradient(135deg, #873FF2 0%, #E14ECA 100%)')
  })

  test('夜间渐变主题一次更新根属性、布局状态和 Element Plus 主色', () => {
    const theme = applyTheme({
      ...DEFAULT_THEME_CONFIG,
      colorScheme: 'DARK',
      accentPreset: 'PEACH_PINK_GRADIENT',
      sidebarTheme: 'LIGHT',
      contentWidth: 'FIXED',
      fixedSidebar: false,
      colorWeak: true,
    })

    expect(theme.colorScheme).toBe('DARK')
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(document.documentElement.dataset.sidebarTheme).toBe('light')
    expect(document.documentElement.classList.contains('theme-content--fixed')).toBe(true)
    expect(document.documentElement.classList.contains('theme-sidebar--static')).toBe(false)
    expect(document.documentElement.classList.contains('theme-color-weak')).toBe(true)
    expect(document.documentElement.style.getPropertyValue('--tianshu-brand-gradient'))
      .toContain('#E14ECA')
    expect(document.documentElement.style.getPropertyValue('--el-color-primary'))
      .toBe('#873FF2')
  })

  test('自定义三色渐变同步驱动品牌、主辅色和科技感滚动条令牌', () => {
    applyTheme({
      ...DEFAULT_THEME_CONFIG,
      accentMode: 'CUSTOM_GRADIENT',
      customGradientColors: ['#125DFF', '#6C3BFF', '#E94FC7'],
      customGradientType: 'LINEAR',
      customGradientAngle: 120,
      navigationLayout: 'TOP',
    })

    const style = document.documentElement.style
    expect(document.documentElement.dataset.navigationLayout).toBe('top')
    expect(style.getPropertyValue('--tianshu-brand-background')).toBe(
      'linear-gradient(120deg, #125DFF 0%, #6C3BFF 50%, #E94FC7 100%)'
    )
    expect(style.getPropertyValue('--el-color-primary')).toBe('#125DFF')
    expect(style.getPropertyValue('--tianshu-color-secondary')).toBe('#E94FC7')
    expect(style.getPropertyValue('--tianshu-scrollbar-thumb')).toContain(
      'linear-gradient(90deg, #125DFF 0%, #E94FC7 100%)'
    )
    expect(style.getPropertyValue('--tianshu-scrollbar-glow')).toBe(
      'rgba(18, 93, 255, 0.42)'
    )
  })

  test.each([
    [{ accentPreset: 'LIQUID_PURPLE' }, '#873FF2'],
    [{ accentMode: 'CUSTOM_SOLID', customSolidColor: '#127855' }, '#127855'],
  ])('从渐变切换到纯色 %j 时两个方向的滚动条都移除渐变', (config, color) => {
    applyTheme({ ...DEFAULT_THEME_CONFIG, accentPreset: 'THEME_BLUE_GRADIENT' })
    const style = document.documentElement.style
    expect(style.getPropertyValue('--tianshu-scrollbar-thumb')).toContain('90deg')
    expect(style.getPropertyValue('--tianshu-scrollbar-thumb-vertical')).toContain('180deg')
    applyTheme({ ...DEFAULT_THEME_CONFIG, ...config })
    expect(style.getPropertyValue('--tianshu-scrollbar-thumb')).toBe(color)
    expect(style.getPropertyValue('--tianshu-scrollbar-thumb-vertical')).toBe(color)
    expect(style.getPropertyValue('--tianshu-scrollbar-thumb-solid')).toBe(color)
  })

  test('切换主题色时更新辅助色和信息提示，业务状态保持稳定', () => {
    applyTheme({ ...DEFAULT_THEME_CONFIG, accentPreset: 'THEME_BLUE' })
    const bluePalette = readAppliedPalette()

    applyTheme({
      ...DEFAULT_THEME_CONFIG,
      accentPreset: 'LIQUID_PURPLE',
    })
    const purplePalette = readAppliedPalette()

    expect(bluePalette.secondary).toBe('#F76E6C')
    expect(purplePalette.secondary).toBe('#2CC7B8')
    expect(purplePalette.infoBg).toBe('#F3ECFE')
    expect(purplePalette.successBg).toBe('#ECFDF3')
    for (const field of ['successBg', 'successBorder', 'successText', 'warningBg', 'warningBorder', 'warningText', 'dangerBg', 'dangerBorder', 'dangerText']) {
      expect(purplePalette[field], field).toBe(bluePalette[field])
    }
    expect(purplePalette).not.toEqual(bluePalette)
    expect(new Set(Object.values(purplePalette).filter(Boolean)).size)
      .toBeGreaterThanOrEqual(8)
  })

  test('夜间模式使用当前主题色生成更深的提示背景', () => {
    applyTheme({
      ...DEFAULT_THEME_CONFIG,
      colorScheme: 'DARK',
      accentPreset: 'CRYSTAL_BLUE',
    })

    const palette = readAppliedPalette()
    expect(palette.secondary).toBe('#22B8A7')
    expect(palette.infoBg).toBe('rgba(47, 128, 237, 0.18)')
    expect(palette.successBg).toBe('rgba(52, 211, 153, 0.18)')
    expect(palette.dangerBorder).toBe('rgba(248, 113, 113, 0.5)')
  })

  test('设计器工具栏边框根据品牌前景明暗选择同色阶对比色', () => {
    applyTheme({ ...DEFAULT_THEME_CONFIG, accentPreset: 'LIQUID_PURPLE' })
    const lightForegroundAccent = document.documentElement.style
      .getPropertyValue('--tianshu-designer-toolbar-accent')
    expect(document.documentElement.style.getPropertyValue('--tianshu-brand-foreground'))
      .toBe('#FFFFFF')
    expect(lightForegroundAccent).not.toBe('#873FF2')

    applyTheme({ ...DEFAULT_THEME_CONFIG, accentPreset: 'PEACH_PINK' })
    expect(document.documentElement.style.getPropertyValue('--tianshu-brand-foreground'))
      .toBe('#19103B')
    expect(document.documentElement.style.getPropertyValue('--tianshu-designer-toolbar-accent'))
      .toBe('#CC567E')
  })

  test('管理列表操作色随主题切换且编辑使用当前主题色', () => {
    applyTheme({ ...DEFAULT_THEME_CONFIG, accentPreset: 'THEME_BLUE' })
    const blueActions = readAppliedActions()

    applyTheme({
      ...DEFAULT_THEME_CONFIG,
      accentPreset: 'LIQUID_PURPLE',
    })
    const purpleActions = readAppliedActions()

    expect(blueActions.edit).not.toBe(purpleActions.edit)
    expect(purpleActions).not.toEqual(blueActions)
    expect(isRedTone(blueActions.delete)).toBe(true)
    expect(isRedTone(purpleActions.delete)).toBe(true)
  })

  test.each(ACCENT_PRESETS.flatMap(preset => [
    ['LIGHT', preset.id, preset.primary],
    ['DARK', preset.id, preset.primary],
  ]))('%s / %s 的编辑色保持当前配置主题的色相', (colorScheme, accentPreset, primary) => {
    applyTheme({
      ...DEFAULT_THEME_CONFIG,
      colorScheme,
      accentPreset,
    })

    const editHue = hsl(readAppliedActions().edit).hue
    const primaryHue = hsl(primary).hue
    const hueDistance = Math.min(
      Math.abs(editHue - primaryHue),
      360 - Math.abs(editHue - primaryHue)
    )
    expect(hueDistance).toBeLessThanOrEqual(4)
  })

  test.each(customActionThemeCases)(
    '%s / %s / %s 的自定义主题普通操作统一跟随主题且保持可读',
    (colorScheme, accentMode, primary) => {
      applyTheme({
        ...DEFAULT_THEME_CONFIG,
        colorScheme,
        accentMode,
        customSolidColor: primary,
        customGradientColors: [primary, '#873FF2'],
      })

      const actions = readAppliedActions()
      expect(isRedTone(actions.delete)).toBe(true)
      const editHue = hsl(actions.edit).hue
      const primaryHue = hsl(primary).hue
      expect(Math.min(
        Math.abs(editHue - primaryHue),
        360 - Math.abs(editHue - primaryHue)
      )).toBeLessThanOrEqual(4)

      for (const role of actionRoles.filter(role => role !== 'delete')) {
        expect(actions[role], role).toBe(actions.edit)
      }

      for (const interactionSurface of actionInteractionSurfaces(
        actions.edit,
        colorScheme
      )) {
        expect(contrastRatio(actions.edit, interactionSurface))
          .toBeGreaterThanOrEqual(4.5)
      }
    }
  )

  test.each(ACCENT_PRESETS.flatMap(preset => ['LIGHT', 'DARK'].map(colorScheme => [colorScheme, preset.id])))('%s / %s 普通操作使用主题色，删除保留风险语义', (colorScheme, accentPreset) => {
    applyTheme({ ...DEFAULT_THEME_CONFIG, colorScheme, accentPreset })
    const actions = readAppliedActions()
    const text = document.documentElement.style.getPropertyValue('--tianshu-primary-text')
    for (const role of actionRoles.filter(role => role !== 'delete')) expect(actions[role], role).toBe(text)
    expect(isRedTone(actions.delete)).toBe(true)
  })

  test.each(ACCENT_PRESETS.flatMap(preset => [
    ['LIGHT', preset.id, '#FFFFFF', '#EEF2F7'],
    ['DARK', preset.id, '#151D31', '#26324D'],
  ]))('%s / %s 的操作色在表格各交互背景上保持可读', (
    colorScheme,
    accentPreset,
    surface,
    rowHoverSurface
  ) => {
    applyTheme({
      ...DEFAULT_THEME_CONFIG,
      colorScheme,
      accentPreset,
    })

    const actions = readAppliedActions()
    for (const [role, color] of Object.entries(actions)) {
      const surfaces = [
        surface,
        rowHoverSurface,
        compositeColor(rowHoverSurface, color, 0.08),
        compositeColor(rowHoverSurface, color, 0.12),
      ]
      for (const interactionSurface of surfaces) {
        expect(
          contrastRatio(color, interactionSurface),
          `${role} / ${interactionSurface}`
        ).toBeGreaterThanOrEqual(4.5)
      }
    }
  })

  test('每次应用主题都发送完整的 Monaco 可消费事件', () => {
    const listener = vi.fn()
    window.addEventListener('tianshu-theme-change', listener)

    applyTheme({ ...DEFAULT_THEME_CONFIG, colorScheme: 'DARK' })

    expect(listener).toHaveBeenCalledTimes(1)
    expect(listener.mock.calls[0][0].detail).toEqual({
      ...DEFAULT_THEME_CONFIG,
      colorScheme: 'DARK',
    })
    window.removeEventListener('tianshu-theme-change', listener)
  })

  test.each(['LIGHT', 'DARK'])('%s 的选中项文字在所有预置及极端自定义主色下均可读', colorScheme => {
    const cases = [
      ...ACCENT_PRESETS.map(preset => ({ accentPreset: preset.id, primary: preset.primary })),
      ...['#000000', '#FFFFFF', '#FFFF00'].map(primary => ({
        accentMode: 'CUSTOM_SOLID', customSolidColor: primary, primary,
      })),
    ]
    for (const { primary, ...config } of cases) {
      applyTheme({ ...DEFAULT_THEME_CONFIG, colorScheme, ...config })
      const foreground = document.documentElement.style.getPropertyValue('--tianshu-primary-text')
      expect(foreground, primary).toMatch(/^#[0-9A-F]{6}$/)
      const surface = colorScheme === 'DARK' ? '#151D31' : '#FFFFFF'
      const background = compositeColor(surface, primary, colorScheme === 'DARK' ? 0.2 : 0.1)
      expect(contrastRatio(foreground, background), primary).toBeGreaterThanOrEqual(4.5)
    }
  })

  test('本地配置可往返，损坏配置回退默认且存储异常不阻断', () => {
    const dark = { ...DEFAULT_THEME_CONFIG, colorScheme: 'DARK' }
    writeLocalTheme(window.localStorage, dark)
    expect(readLocalTheme(window.localStorage)).toEqual(dark)

    window.localStorage.setItem(LOCAL_THEME_STORAGE_KEY, 'not-json')
    expect(readLocalTheme(window.localStorage)).toEqual(DEFAULT_THEME_CONFIG)

    const deniedStorage = {
      getItem() { throw new Error('denied') },
      setItem() { throw new Error('denied') },
    }
    expect(readLocalTheme(deniedStorage)).toEqual(DEFAULT_THEME_CONFIG)
    expect(() => writeLocalTheme(deniedStorage, dark)).not.toThrow()
  })

  test('启动阶段读取本地配置并立即应用', () => {
    window.localStorage.setItem(LOCAL_THEME_STORAGE_KEY, JSON.stringify({
      ...DEFAULT_THEME_CONFIG,
      colorScheme: 'DARK',
    }))

    bootstrapLocalTheme(window.localStorage)

    expect(document.documentElement.dataset.theme).toBe('dark')
  })
})

function readAppliedPalette() {
  const style = document.documentElement.style
  return {
    secondary: style.getPropertyValue('--tianshu-color-secondary'),
    infoBg: style.getPropertyValue('--tianshu-info-bg'),
    infoBorder: style.getPropertyValue('--tianshu-info-border'),
    infoText: style.getPropertyValue('--tianshu-info-text'),
    successBg: style.getPropertyValue('--tianshu-success-bg'),
    successBorder: style.getPropertyValue('--tianshu-success-border'),
    successText: style.getPropertyValue('--tianshu-success-text'),
    warningBg: style.getPropertyValue('--tianshu-warning-bg'),
    warningBorder: style.getPropertyValue('--tianshu-warning-border'),
    warningText: style.getPropertyValue('--tianshu-warning-text'),
    dangerBg: style.getPropertyValue('--tianshu-danger-bg'),
    dangerBorder: style.getPropertyValue('--tianshu-danger-border'),
    dangerText: style.getPropertyValue('--tianshu-danger-text'),
  }
}

const actionRoles = [
  'edit',
  'detail',
  'execute',
  'inspect',
  'configure',
  'version',
  'compare',
  'rollback',
  'global',
  'publish',
  'reset',
  'disable',
  'add',
  'docs',
  'delete',
]

const customActionThemeCases = [
  '#FF0000',
  '#FF0018',
  '#FF0024',
  '#FF002F',
  '#FF0040',
  '#FF0055',
  '#FF006E',
  '#FF6500',
  '#FFD400',
  '#00A896',
  '#00B8D9',
  '#2639E9',
  '#873FF2',
  '#E14ECA',
].flatMap(primary => [
  ['LIGHT', 'CUSTOM_SOLID', primary],
  ['DARK', 'CUSTOM_SOLID', primary],
]).concat([
  ['LIGHT', 'CUSTOM_GRADIENT', '#FF0040'],
  ['DARK', 'CUSTOM_GRADIENT', '#FF0040'],
])

function readAppliedActions() {
  const style = document.documentElement.style
  return Object.fromEntries(actionRoles.map(role => [
    role,
    style.getPropertyValue(`--tianshu-action-${role}`).trim(),
  ]))
}

function isRedTone(color) {
  const { hue, saturation } = hsl(color)
  return (hue < 10 || hue > 350) && saturation > 0.55
}

function contrastRatio(foreground, background) {
  const foregroundLuminance = relativeLuminance(rgb(foreground))
  const backgroundLuminance = relativeLuminance(rgb(background))
  const lighter = Math.max(foregroundLuminance, backgroundLuminance)
  const darker = Math.min(foregroundLuminance, backgroundLuminance)
  return (lighter + 0.05) / (darker + 0.05)
}

function relativeLuminance({ r, g, b }) {
  return [r, g, b]
    .map(value => {
      const channel = value / 255
      return channel <= 0.03928
        ? channel / 12.92
        : ((channel + 0.055) / 1.055) ** 2.4
    })
    .reduce((sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index], 0)
}

function rgb(color) {
  const normalized = color.replace('#', '')
  return {
    r: Number.parseInt(normalized.slice(0, 2), 16),
    g: Number.parseInt(normalized.slice(2, 4), 16),
    b: Number.parseInt(normalized.slice(4, 6), 16),
  }
}

function compositeColor(background, foreground, alpha) {
  const backgroundRgb = rgb(background)
  const foregroundRgb = rgb(foreground)
  return `#${['r', 'g', 'b'].map(channel => Math.round(
    backgroundRgb[channel] * (1 - alpha) + foregroundRgb[channel] * alpha
  ).toString(16).padStart(2, '0')).join('')}`.toUpperCase()
}

function actionInteractionSurfaces(color, colorScheme) {
  const dark = colorScheme === 'DARK'
  const surface = dark ? '#151D31' : '#FFFFFF'
  const rowHoverSurface = dark ? '#26324D' : '#EEF2F7'
  return [
    surface,
    rowHoverSurface,
    compositeColor(rowHoverSurface, color, 0.08),
    compositeColor(rowHoverSurface, color, 0.12),
  ]
}

function hsl(color) {
  const channels = Object.values(rgb(color)).map(value => value / 255)
  const maximum = Math.max(...channels)
  const minimum = Math.min(...channels)
  const delta = maximum - minimum
  const lightness = (maximum + minimum) / 2
  let hue = 0

  if (delta > 0) {
    if (maximum === channels[0]) {
      hue = 60 * (((channels[1] - channels[2]) / delta) % 6)
    } else if (maximum === channels[1]) {
      hue = 60 * ((channels[2] - channels[0]) / delta + 2)
    } else {
      hue = 60 * ((channels[0] - channels[1]) / delta + 4)
    }
  }

  return {
    hue: hue < 0 ? hue + 360 : hue,
    saturation: delta === 0
      ? 0
      : delta / (1 - Math.abs(2 * lightness - 1)),
    lightness,
  }
}
