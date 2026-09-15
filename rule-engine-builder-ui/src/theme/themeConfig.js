const DEFAULT_GRADIENT_COLORS = Object.freeze(['#2639E9', '#873FF2'])

export const DEFAULT_THEME_CONFIG = Object.freeze({
  schemaVersion: 3,
  colorScheme: 'LIGHT',
  accentMode: 'PRESET',
  accentPreset: 'THEME_BLUE',
  customSolidColor: '#2639E9',
  customGradientColors: DEFAULT_GRADIENT_COLORS,
  customGradientType: 'LINEAR',
  customGradientAngle: 135,
  navigationLayout: 'LEFT',
  sidebarTheme: 'DARK',
  contentWidth: 'FLUID',
  tableScrollMode: 'AUTO',
  fixedSidebar: true,
  colorWeak: false,
})

export const ACCENT_PRESETS = Object.freeze([
  solid('THEME_BLUE', '主题蓝', '#2639E9', '#F76E6C', '#FFFFFF'),
  solid('LIQUID_PURPLE', '凝液紫', '#873FF2', '#2CC7B8', '#FFFFFF'),
  solid('PEACH_PINK', '桃粉红', '#FF6B9D', '#5B6CFF', '#19103B'),
  solid('NEON_PINK', '霓虹粉', '#E14ECA', '#3A9BFF', '#19103B'),
  solid('MIDNIGHT_PURPLE', '深夜紫', '#19103B', '#F2B35D', '#FFFFFF'),
  solid('INDIGO_PURPLE', '靛青紫', '#5B3DF5', '#2BBEC4', '#FFFFFF'),
  solid('NEBULA_BLUE', '星云蓝', '#536DFE', '#D96BB3', '#FFFFFF'),
  solid('CRYSTAL_BLUE', '晶石蓝', '#2F80ED', '#22B8A7', '#FFFFFF'),
  gradient(
    'THEME_BLUE_GRADIENT',
    '主题蓝渐变',
    '#2639E9',
    '#E66AB1',
    'linear-gradient(135deg, #2639E9 0%, #536DFE 100%)'
  ),
  gradient(
    'LIQUID_PURPLE_GRADIENT',
    '凝液紫渐变',
    '#873FF2',
    '#2CC7B8',
    'linear-gradient(135deg, #19103B 0%, #873FF2 100%)'
  ),
  gradient(
    'PEACH_PINK_GRADIENT',
    '桃粉渐变',
    '#873FF2',
    '#E14ECA',
    'linear-gradient(135deg, #873FF2 0%, #E14ECA 100%)'
  ),
  gradient(
    'NEBULA_GRADIENT',
    '星云渐变',
    '#536DFE',
    '#E14ECA',
    'linear-gradient(135deg, #536DFE 0%, #E14ECA 100%)'
  ),
])

const PRESET_BY_ID = new Map(ACCENT_PRESETS.map(item => [item.id, item]))
const CONFIG_KEYS = Object.keys(DEFAULT_THEME_CONFIG)
const V2_CONFIG_KEYS = CONFIG_KEYS.filter(key => key !== 'tableScrollMode')
const LEGACY_CONFIG_KEYS = [
  'schemaVersion',
  'colorScheme',
  'accentPreset',
  'sidebarTheme',
  'contentWidth',
  'fixedSidebar',
  'colorWeak',
]
const HEX_COLOR_PATTERN = /^#[0-9A-F]{6}$/

function solid(id, name, primary, secondary, foreground) {
  return Object.freeze({
    id,
    name,
    kind: 'solid',
    primary,
    secondary,
    foreground,
    background: primary,
  })
}

function gradient(id, name, primary, secondary, background) {
  return Object.freeze({
    id,
    name,
    kind: 'gradient',
    primary,
    secondary,
    foreground: '#FFFFFF',
    background,
  })
}

function defaultConfig() {
  return {
    ...DEFAULT_THEME_CONFIG,
    customGradientColors: [...DEFAULT_THEME_CONFIG.customGradientColors],
  }
}

export function normalizeThemeConfig(value) {
  if (!isObject(value)) return defaultConfig()
  if (value.schemaVersion === 1) return migrateLegacyConfig(value)
  if (value.schemaVersion === 2 && hasExactKeys(value, V2_CONFIG_KEYS)) {
    return normalizeThemeConfig({ ...value, schemaVersion: 3, tableScrollMode: 'AUTO' })
  }
  if (value.schemaVersion !== 3 || !hasExactKeys(value, CONFIG_KEYS)) {
    return defaultConfig()
  }

  const customSolidColor = normalizeColor(value.customSolidColor)
  const customGradientColors = normalizeGradientColors(
    value.customGradientColors
  )
  const angle = normalizeGradientAngle(value.customGradientAngle)
  if (
    !isCommonConfigValid(value) ||
    !['PRESET', 'CUSTOM_SOLID', 'CUSTOM_GRADIENT'].includes(value.accentMode) ||
    !customSolidColor ||
    !customGradientColors ||
    !['LINEAR', 'RADIAL'].includes(value.customGradientType) ||
    angle === null ||
    !['LEFT', 'TOP'].includes(value.navigationLayout) ||
    !['AUTO', 'FIXED'].includes(value.tableScrollMode)
  ) {
    return defaultConfig()
  }

  return {
    schemaVersion: 3,
    colorScheme: value.colorScheme,
    accentMode: value.accentMode,
    accentPreset: value.accentPreset,
    customSolidColor,
    customGradientColors,
    customGradientType: value.customGradientType,
    customGradientAngle: angle,
    navigationLayout: value.navigationLayout,
    sidebarTheme: value.sidebarTheme,
    contentWidth: value.contentWidth,
    tableScrollMode: value.tableScrollMode,
    fixedSidebar: value.fixedSidebar,
    colorWeak: value.colorWeak,
  }
}

export function resolveAccentPreset(id) {
  return PRESET_BY_ID.get(id) || ACCENT_PRESETS[0]
}

export function resolveThemeAccent(config) {
  const normalized = normalizeThemeConfig(config)
  if (normalized.accentMode === 'CUSTOM_SOLID') {
    const primary = normalized.customSolidColor
    return {
      id: 'CUSTOM_SOLID',
      name: '自定义纯色',
      kind: 'solid',
      primary,
      secondary: deriveSecondaryColor(primary),
      foreground: readableForeground([primary]),
      background: primary,
    }
  }
  if (normalized.accentMode === 'CUSTOM_GRADIENT') {
    const colors = normalized.customGradientColors
    return {
      id: 'CUSTOM_GRADIENT',
      name: '自定义渐变',
      kind: 'gradient',
      primary: colors[0],
      secondary: colors[colors.length - 1],
      foreground: readableForeground(colors),
      background: buildGradientBackground(
        colors,
        normalized.customGradientType,
        normalized.customGradientAngle
      ),
    }
  }
  return resolveAccentPreset(normalized.accentPreset)
}

export function createPrimaryScale(primary) {
  const rgb = hexToRgb(primary)
  const scale = {
    rgb: `${rgb.r}, ${rgb.g}, ${rgb.b}`,
    primary: rgbToHex(rgb),
  }
  for (let index = 1; index <= 9; index += 1) {
    scale[`light${index}`] = mixRgb(
      rgb,
      { r: 255, g: 255, b: 255 },
      index / 10
    )
  }
  scale.dark1 = mixRgb(rgb, { r: 0, g: 0, b: 0 }, 0.1)
  scale.dark2 = mixRgb(rgb, { r: 0, g: 0, b: 0 }, 0.2)
  return scale
}

export function mixThemeColors(source, target, weight) {
  return mixRgb(hexToRgb(source), hexToRgb(target), weight)
}

function migrateLegacyConfig(value) {
  if (!hasExactKeys(value, LEGACY_CONFIG_KEYS) || !isCommonConfigValid(value)) {
    return defaultConfig()
  }
  return {
    ...defaultConfig(),
    colorScheme: value.colorScheme,
    accentPreset: value.accentPreset,
    sidebarTheme: value.sidebarTheme,
    contentWidth: value.contentWidth,
    fixedSidebar: value.fixedSidebar,
    colorWeak: value.colorWeak,
  }
}

function isCommonConfigValid(value) {
  return (
    ['LIGHT', 'DARK'].includes(value.colorScheme) &&
    PRESET_BY_ID.has(value.accentPreset) &&
    ['LIGHT', 'DARK'].includes(value.sidebarTheme) &&
    ['FLUID', 'FIXED'].includes(value.contentWidth) &&
    typeof value.fixedSidebar === 'boolean' &&
    typeof value.colorWeak === 'boolean'
  )
}

function isObject(value) {
  return value && typeof value === 'object' && !Array.isArray(value)
}

function hasExactKeys(value, expected) {
  const keys = Object.keys(value)
  return keys.length === expected.length && keys.every(key => expected.includes(key))
}

function normalizeColor(value) {
  const normalized = String(value || '').toUpperCase()
  return HEX_COLOR_PATTERN.test(normalized) ? normalized : null
}

function normalizeGradientColors(value) {
  if (!Array.isArray(value) || ![2, 3].includes(value.length)) return null
  const colors = value.map(normalizeColor)
  return colors.every(Boolean) ? colors : null
}

function normalizeGradientAngle(value) {
  if (!Number.isInteger(value) || value < 0 || value > 360) return null
  return value
}

function buildGradientBackground(colors, type, angle) {
  const lastIndex = colors.length - 1
  const stops = colors
    .map((color, index) => `${color} ${Math.round((index / lastIndex) * 100)}%`)
    .join(', ')
  return type === 'RADIAL'
    ? `radial-gradient(circle at center, ${stops})`
    : `linear-gradient(${angle}deg, ${stops})`
}

function deriveSecondaryColor(primary) {
  const hsl = rgbToHsl(hexToRgb(primary))
  return rgbToHex(hslToRgb({
    h: (hsl.h + 137) % 360,
    s: Math.max(0.58, Math.min(0.82, hsl.s)),
    l: Math.max(0.44, Math.min(0.6, hsl.l)),
  }))
}

function readableForeground(colors) {
  const candidates = ['#FFFFFF', '#19103B']
  return candidates
    .map(color => ({
      color,
      score: Math.min(...colors.map(background => contrastRatio(color, background))),
    }))
    .sort((first, second) => second.score - first.score)[0].color
}

function contrastRatio(first, second) {
  const firstLuminance = relativeLuminance(first)
  const secondLuminance = relativeLuminance(second)
  const lighter = Math.max(firstLuminance, secondLuminance)
  const darker = Math.min(firstLuminance, secondLuminance)
  return (lighter + 0.05) / (darker + 0.05)
}

function relativeLuminance(color) {
  const { r, g, b } = hexToRgb(color)
  return [r, g, b]
    .map(value => value / 255)
    .map(channel => channel <= 0.03928
      ? channel / 12.92
      : ((channel + 0.055) / 1.055) ** 2.4)
    .reduce(
      (sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index],
      0
    )
}

function hexToRgb(value) {
  const normalized = String(value || '').replace('#', '')
  if (!/^[0-9a-f]{6}$/i.test(normalized)) {
    throw new Error('主题配置包含无效色值')
  }
  return {
    r: Number.parseInt(normalized.slice(0, 2), 16),
    g: Number.parseInt(normalized.slice(2, 4), 16),
    b: Number.parseInt(normalized.slice(4, 6), 16),
  }
}

function rgbToHsl({ r, g, b }) {
  const channels = [r, g, b].map(value => value / 255)
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
    h: hue < 0 ? hue + 360 : hue,
    s: delta === 0 ? 0 : delta / (1 - Math.abs(2 * lightness - 1)),
    l: lightness,
  }
}

function hslToRgb({ h, s, l }) {
  const chroma = (1 - Math.abs(2 * l - 1)) * s
  const segment = h / 60
  const intermediate = chroma * (1 - Math.abs((segment % 2) - 1))
  const [red, green, blue] = [
    [chroma, intermediate, 0],
    [intermediate, chroma, 0],
    [0, chroma, intermediate],
    [0, intermediate, chroma],
    [intermediate, 0, chroma],
    [chroma, 0, intermediate],
  ][Math.floor(segment) % 6]
  const match = l - chroma / 2
  return {
    r: Math.round((red + match) * 255),
    g: Math.round((green + match) * 255),
    b: Math.round((blue + match) * 255),
  }
}

function mixRgb(source, target, weight) {
  return rgbToHex({
    r: Math.round(source.r + (target.r - source.r) * weight),
    g: Math.round(source.g + (target.g - source.g) * weight),
    b: Math.round(source.b + (target.b - source.b) * weight),
  })
}

function rgbToHex({ r, g, b }) {
  const channel = value => Math.max(0, Math.min(255, value))
    .toString(16)
    .padStart(2, '0')
    .toUpperCase()
  return `#${channel(r)}${channel(g)}${channel(b)}`
}
