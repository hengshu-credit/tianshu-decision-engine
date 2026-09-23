import {
  ACCENT_PRESETS,
  DEFAULT_THEME_CONFIG,
  mixThemeColors,
  normalizeThemeConfig,
  resolveAccentPreset,
  resolveThemeAccent,
} from '@/theme/themeConfig'

describe('themeConfig — 配置白名单和固定色卡', () => {
  test('损坏、缺字段或未来版本配置整体回退默认值', () => {
    expect(normalizeThemeConfig(null)).toEqual(DEFAULT_THEME_CONFIG)
    expect(normalizeThemeConfig({
      schemaVersion: 4,
      colorScheme: 'DARK',
      accentPreset: 'url(evil)',
    })).toEqual(DEFAULT_THEME_CONFIG)
    expect(normalizeThemeConfig({
      ...DEFAULT_THEME_CONFIG,
      unknown: true,
    })).toEqual(DEFAULT_THEME_CONFIG)
    expect(normalizeThemeConfig(null)).not.toBe(DEFAULT_THEME_CONFIG)
  })

  test('有效配置只返回白名单字段且保持布尔值', () => {
    const value = {
      ...DEFAULT_THEME_CONFIG,
      colorScheme: 'DARK',
      accentPreset: 'PEACH_PINK_GRADIENT',
      sidebarTheme: 'LIGHT',
      contentWidth: 'FIXED',
      fixedSidebar: false,
      colorWeak: true,
    }

    expect(normalizeThemeConfig(value)).toEqual(value)
  })

  test('包含 8 个纯色和 4 个固定渐变预设且主题蓝排首位', () => {
    expect(ACCENT_PRESETS.filter(item => item.kind === 'solid')).toHaveLength(8)
    expect(ACCENT_PRESETS.filter(item => item.kind === 'gradient')).toHaveLength(4)
    expect(ACCENT_PRESETS[0]).toMatchObject({
      id: 'THEME_BLUE',
      name: '主题蓝',
      primary: '#2639E9',
      secondary: '#F76E6C',
      background: '#2639E9',
    })
    expect(resolveAccentPreset('LIQUID_PURPLE')).toMatchObject({
      primary: '#873FF2',
      secondary: '#2CC7B8',
    })
  })

  test('v1 配置自动迁移到当前版本并保留原有外观选择', () => {
    const legacy = {
      schemaVersion: 1,
      colorScheme: 'DARK',
      accentPreset: 'PEACH_PINK_GRADIENT',
      sidebarTheme: 'LIGHT',
      contentWidth: 'FIXED',
      fixedSidebar: false,
      colorWeak: true,
    }

    expect(normalizeThemeConfig(legacy)).toEqual({
      ...DEFAULT_THEME_CONFIG,
      colorScheme: 'DARK',
      accentPreset: 'PEACH_PINK_GRADIENT',
      sidebarTheme: 'LIGHT',
      contentWidth: 'FIXED',
      fixedSidebar: false,
      colorWeak: true,
    })
  })

  test('v2 配置保留自定义外观并默认随内容增高', () => {
    const legacy = { ...DEFAULT_THEME_CONFIG, schemaVersion: 2, navigationLayout: 'TOP', colorScheme: 'DARK' }
    delete legacy.tableScrollMode

    expect(normalizeThemeConfig(legacy)).toEqual({
      ...legacy, schemaVersion: 3, tableScrollMode: 'AUTO',
    })
    expect(normalizeThemeConfig({ ...legacy, unknown: true })).toEqual(DEFAULT_THEME_CONFIG)
  })

  test.each(['AUTO', 'FIXED'])('表格滚动方式 %s 参与主题保存', tableScrollMode => {
    const config = { ...DEFAULT_THEME_CONFIG, tableScrollMode, colorScheme: 'DARK' }
    expect(normalizeThemeConfig(config)).toEqual(config)
  })

  test('表格滚动配置缺失或非法时回退默认值', () => {
    const config = { ...DEFAULT_THEME_CONFIG, colorScheme: 'DARK' }
    delete config.tableScrollMode
    expect(normalizeThemeConfig(config)).toEqual(DEFAULT_THEME_CONFIG)
    for (const value of ['SCROLL', null, true]) {
      expect(normalizeThemeConfig({ ...config, tableScrollMode: value })).toEqual(DEFAULT_THEME_CONFIG)
    }
  })

  test('自定义纯色生成与主色协调但不同的辅助色', () => {
    const config = normalizeThemeConfig({
      ...DEFAULT_THEME_CONFIG,
      accentMode: 'CUSTOM_SOLID',
      customSolidColor: '#125DFF',
    })
    const accent = resolveThemeAccent(config)

    expect(accent).toMatchObject({
      kind: 'solid',
      primary: '#125DFF',
      background: '#125DFF',
    })
    expect(accent.secondary).toMatch(/^#[0-9A-F]{6}$/)
    expect(accent.secondary).not.toBe(accent.primary)
  })

  test('自定义渐变支持二色、三色、线性和径向方式', () => {
    const linear = resolveThemeAccent({
      ...DEFAULT_THEME_CONFIG,
      accentMode: 'CUSTOM_GRADIENT',
      customGradientColors: ['#125DFF', '#6C3BFF', '#E94FC7'],
      customGradientType: 'LINEAR',
      customGradientAngle: 120,
    })
    const radial = resolveThemeAccent({
      ...DEFAULT_THEME_CONFIG,
      accentMode: 'CUSTOM_GRADIENT',
      customGradientColors: ['#125DFF', '#E94FC7'],
      customGradientType: 'RADIAL',
    })

    expect(linear.background).toBe(
      'linear-gradient(120deg, #125DFF 0%, #6C3BFF 50%, #E94FC7 100%)'
    )
    expect(radial.background).toBe(
      'radial-gradient(circle at center, #125DFF 0%, #E94FC7 100%)'
    )
  })

  test('自定义配置拒绝非法颜色、色数、渐变方式和菜单布局', () => {
    const invalidValues = [
      { customSolidColor: 'url(evil)' },
      { customGradientColors: ['#125DFF'] },
      { customGradientColors: ['#125DFF', '#GGGGGG'] },
      { customGradientType: 'CONIC' },
      { customGradientAngle: 361 },
      { navigationLayout: 'FLOATING' },
    ]

    for (const patch of invalidValues) {
      expect(normalizeThemeConfig({
        ...DEFAULT_THEME_CONFIG,
        ...patch,
      })).toEqual(DEFAULT_THEME_CONFIG)
    }
  })

  test('渐变预设同时提供实色主色和固定渐变背景', () => {
    expect(resolveAccentPreset('LIQUID_PURPLE_GRADIENT')).toEqual(
      expect.objectContaining({
        primary: '#873FF2',
        secondary: '#B16CF2',
        foreground: '#FFFFFF',
        background: 'linear-gradient(135deg, #19103B 0%, #873FF2 100%)',
      })
    )
  })

  test('图表使用 Element Plus 三位十六进制表面色时可正常混色', () => {
    expect(mixThemeColors('#fff', '#2639E9', 0.1)).toMatch(/^#[0-9A-F]{6}$/)
  })
})
