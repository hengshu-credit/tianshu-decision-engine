import { ACCENT_PRESETS, DEFAULT_THEME_CONFIG, mixThemeColors, resolveThemeAccent } from '@/theme/themeConfig'
import { applyTheme, createContentReferencePalette } from '@/theme/themeRuntime'

const rgb = value => value.slice(1).match(/../g).map(channel => parseInt(channel, 16))
const luminance = color => rgb(color).map(channel => channel / 255).map(channel => channel <= .04045 ? channel / 12.92 : ((channel + .055) / 1.055) ** 2.4)
  .reduce((total, channel, index) => total + channel * [.2126, .7152, .0722][index], 0)
const contrast = (first, second) => (Math.max(luminance(first), luminance(second)) + .05) / (Math.min(luminance(first), luminance(second)) + .05)
const distance = (first, second) => Math.hypot(...rgb(first).map((channel, index) => channel - rgb(second)[index]))

test.each(['LIGHT', 'DARK'])('%s 的纯色和渐变五段均可读且彼此有区分', colorScheme => {
  const cases = [
    ...ACCENT_PRESETS.map(preset => ({ accentPreset: preset.id })),
    ...['#000000', '#FFFFFF', '#777777', '#FFFF00', '#00FFFF', '#127855'].map(customSolidColor => ({ accentMode: 'CUSTOM_SOLID', customSolidColor })),
    ...[['#000000', '#FFFFFF'], ['#125DFF', '#17C37B', '#E94FC7'], ['#888888', '#888888']].flatMap(customGradientColors =>
      ['LINEAR', 'RADIAL'].map(customGradientType => ({ accentMode: 'CUSTOM_GRADIENT', customGradientColors, customGradientType }))),
  ]
  for (const overrides of cases) {
    const accent = resolveThemeAccent({ ...DEFAULT_THEME_CONFIG, colorScheme, ...overrides })
    const palette = createContentReferencePalette(accent, colorScheme)
    expect(Object.keys(palette)).toEqual(['source', 'type', 'name', 'code', 'version'])
    const tones = Object.values(palette)
    expect(new Set(tones.map(tone => tone.background)).size).toBe(5)
    for (const tone of tones) {
      const samples = tone.colors.flatMap((color, index) => index === 0 ? [color]
        : Array.from({ length: 100 }, (_, step) => mixThemeColors(tone.colors[index - 1], color, (step + 1) / 100)))
      for (const color of samples) expect(contrast(tone.foreground, color), `${JSON.stringify(overrides)} ${color}`).toBeGreaterThanOrEqual(4.5)
    }
    const centers = tones.map(tone => mixThemeColors(tone.colors[0], tone.colors.at(-1), .5))
    for (let i = 1; i < centers.length; i++) expect(distance(centers[i - 1], centers[i]), JSON.stringify(overrides)).toBeGreaterThan(40)
  }
})

test('切换主色和渐变中间色会更新内容色块令牌，保留渐变方向和类型', () => {
  const root = document.createElement('div')
  applyTheme({ ...DEFAULT_THEME_CONFIG, accentMode: 'CUSTOM_SOLID', customSolidColor: '#127855' }, root)
  const green = root.style.getPropertyValue('--tianshu-content-source-bg')
  expect(rgb(green)[1]).toBeGreaterThan(rgb(green)[0])
  applyTheme({ ...DEFAULT_THEME_CONFIG, accentMode: 'CUSTOM_SOLID', customSolidColor: '#2639E9' }, root)
  expect(root.style.getPropertyValue('--tianshu-content-source-bg')).not.toBe(green)
  const config = { ...DEFAULT_THEME_CONFIG, accentMode: 'CUSTOM_GRADIENT', customGradientColors: ['#125DFF', '#17C37B', '#E94FC7'], customGradientAngle: 120 }
  applyTheme(config, root)
  const middle = root.style.getPropertyValue('--tianshu-content-name-bg')
  expect(middle).toContain('linear-gradient(120deg,')
  applyTheme({ ...config, customGradientColors: ['#125DFF', '#EA7823', '#E94FC7'] }, root)
  expect(root.style.getPropertyValue('--tianshu-content-name-bg')).not.toBe(middle)
  applyTheme({ ...config, customGradientType: 'RADIAL' }, root)
  expect(root.style.getPropertyValue('--tianshu-content-name-bg')).toContain('radial-gradient(circle at center,')
})
