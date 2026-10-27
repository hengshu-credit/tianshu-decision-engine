import {
  DEFAULT_THEME_CONFIG,
  createPrimaryScale,
  mixThemeColors,
  normalizeThemeConfig,
  resolveThemeAccent,
} from './themeConfig'

export const LOCAL_THEME_STORAGE_KEY = 'tianshu-ui-theme-v1'

export function applyTheme(config, root = document.documentElement) {
  const normalized = normalizeThemeConfig(config)
  const preset = resolveThemeAccent(normalized)
  const scale = createPrimaryScale(preset.primary)
  const secondaryScale = createPrimaryScale(preset.secondary)
  const infoTone = createHintTone(scale, normalized.colorScheme === 'DARK')
  const actionPalette = createActionPalette(
    preset.primary,
    normalized.colorScheme
  )

  root.dataset.theme = normalized.colorScheme.toLowerCase()
  root.dataset.sidebarTheme = normalized.sidebarTheme.toLowerCase()
  root.dataset.navigationLayout = normalized.navigationLayout.toLowerCase()
  root.dataset.tableScrollMode = normalized.tableScrollMode.toLowerCase()
  root.classList.toggle(
    'theme-content--fixed',
    normalized.contentWidth === 'FIXED'
  )
  root.classList.remove('theme-sidebar--static')
  root.classList.toggle('theme-color-weak', normalized.colorWeak)

  setProperty(root, '--tianshu-brand-background', preset.background)
  applyButtonPalette(root, 'primary', preset.background)
  setProperty(
    root,
    '--tianshu-brand-gradient',
    preset.kind === 'gradient' ? preset.background : 'none'
  )
  setProperty(root, '--tianshu-brand-foreground', preset.foreground)
  const sidebarText = normalized.sidebarTheme === 'LIGHT'
    ? ensureActionContrast(preset.primary, false, [scale.light9])
    : '#FFFFFF'
  setProperty(root, '--tianshu-sidebar-text-active', sidebarText)
  setProperty(root, '--tianshu-sidebar-active-foreground', normalized.sidebarTheme === 'LIGHT'
    ? sidebarText
    : preset.foreground)
  // 工具栏铺在主题背景上，边框需要使用与当前主题明暗相反的同色阶，
  // 否则浅色主题的主题色会融入浅色背景，深色主题又会显得过暗。
  setProperty(
    root,
    '--tianshu-designer-toolbar-accent',
    preset.foreground === '#FFFFFF' ? scale.light3 : scale.dark2
  )
  applyColorScale(root, '--el-color-primary', scale)
  applyColorScale(root, '--tianshu-color-secondary', secondaryScale)
  applyColorScale(root, '--el-color-info', scale)
  for (const [role, colors] of Object.entries(createContentReferencePalette(preset, normalized.colorScheme))) {
    setProperty(root, `--tianshu-content-${role}-bg`, colors.background)
    setProperty(root, `--tianshu-content-${role}-text`, colors.foreground)
  }
  for (const [name, value] of Object.entries(infoTone)) {
    setProperty(root, `--tianshu-info-${name}`, value)
  }
  for (const [role, tone] of Object.entries(actionPalette)) {
    setProperty(root, `--tianshu-action-${role}`, tone.color)
    setProperty(root, `--tianshu-action-${role}-rgb`, tone.rgb)
  }
  const dark = normalized.colorScheme === 'DARK'
  const actionSurfaces = [0.1, 0.14, 0.18, 0.22].map(weight =>
    mixThemeColors(dark ? '#151D31' : '#FFFFFF', preset.primary, weight)
  )
  setProperty(root, '--tianshu-primary-text', ensureActionContrast(preset.primary, dark, actionSurfaces))
  applySemanticStatusPalette(root, normalized.colorScheme)
  setProperty(root, '--tianshu-focus-ring', `rgba(${scale.rgb}, 0.18)`)
  setProperty(root, '--tianshu-primary-shadow', `rgba(${scale.rgb}, 0.16)`)
  setProperty(
    root,
    '--tianshu-scrollbar-thumb',
    preset.kind === 'gradient'
      ? `linear-gradient(90deg, ${preset.primary} 0%, ${preset.secondary} 100%)`
      : preset.primary
  )
  setProperty(root, '--tianshu-scrollbar-thumb-solid', preset.primary)
  setProperty(
    root,
    '--tianshu-scrollbar-thumb-vertical',
    preset.kind === 'gradient'
      ? `linear-gradient(180deg, ${preset.primary} 0%, ${preset.secondary} 100%)`
      : preset.primary
  )
  setProperty(
    root,
    '--tianshu-scrollbar-track',
    `rgba(${scale.rgb}, ${normalized.colorScheme === 'DARK' ? 0.12 : 0.08})`
  )
  setProperty(root, '--tianshu-scrollbar-glow', `rgba(${scale.rgb}, 0.42)`)

  if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
    window.dispatchEvent(new CustomEvent('tianshu-theme-change', {
      detail: { ...normalized },
    }))
  }
  return normalized
}

export function readLocalTheme(storage) {
  if (!storage || typeof storage.getItem !== 'function') {
    return { ...DEFAULT_THEME_CONFIG }
  }
  try {
    const raw = storage.getItem(LOCAL_THEME_STORAGE_KEY)
    return raw ? normalizeThemeConfig(JSON.parse(raw)) : { ...DEFAULT_THEME_CONFIG }
  } catch (error) {
    return { ...DEFAULT_THEME_CONFIG }
  }
}

export function writeLocalTheme(storage, config) {
  if (!storage || typeof storage.setItem !== 'function') return
  try {
    storage.setItem(
      LOCAL_THEME_STORAGE_KEY,
      JSON.stringify(normalizeThemeConfig(config))
    )
  } catch (error) {
    // 本地存储不可用时仍保持当前页面主题。
  }
}

export function bootstrapLocalTheme(
  storage,
  root = document.documentElement
) {
  return applyTheme(readLocalTheme(storage), root)
}

function setProperty(root, name, value) {
  root.style.setProperty(name, value)
}

function applyColorScale(root, prefix, scale) {
  setProperty(root, `${prefix}-rgb`, scale.rgb)
  setProperty(root, prefix, scale.primary)
  for (let index = 1; index <= 9; index += 1) {
    setProperty(root, `${prefix}-light-${index}`, scale[`light${index}`])
  }
  setProperty(root, `${prefix}-dark-1`, scale.dark1)
  setProperty(root, `${prefix}-dark-2`, scale.dark2)
}

function applySemanticStatusPalette(root, colorScheme) {
  const dark = colorScheme === 'DARK'
  const palette = dark
    ? {
      success: ['rgba(52, 211, 153, 0.18)', 'rgba(52, 211, 153, 0.42)', '#A7F3D0', '#34D399', '#052E16'],
      warning: ['rgba(245, 158, 11, 0.18)', 'rgba(245, 158, 11, 0.42)', '#FED7AA', '#F59E0B', '#451A03'],
      danger: ['rgba(248, 113, 113, 0.2)', 'rgba(248, 113, 113, 0.5)', '#FECCCA', '#F87171', '#450A0A'],
    }
    : {
      success: ['#ECFDF3', '#A7F3D0', '#166534', '#15803D', '#FFFFFF'],
      warning: ['#FFF7ED', '#FED7AA', '#9A3412', '#D97706', '#451A03'],
      danger: ['#FEF3F2', '#FECDCA', '#B42318', '#DC2626', '#FFFFFF'],
    }

  for (const [type, values] of Object.entries(palette)) {
    const [background, border, text, solid, foreground] = values
    setProperty(root, `--tianshu-status-${type}-bg`, background)
    setProperty(root, `--tianshu-status-${type}-border`, border)
    setProperty(root, `--tianshu-status-${type}-text`, text)
    setProperty(root, `--tianshu-status-${type}-solid`, solid)
    setProperty(root, `--tianshu-status-${type}-solid-foreground`, foreground)
    // Element Plus 的消息、通知、表单校验，以及页面内提示共用同一套业务语义。
    setProperty(root, `--tianshu-${type}-bg`, background)
    setProperty(root, `--tianshu-${type}-border`, border)
    setProperty(root, `--tianshu-${type}-text`, text)
    setProperty(root, `--tianshu-${type}-foreground`, foreground)
    const scale = createPrimaryScale(solid)
    if (dark) {
      for (let index = 1; index <= 9; index += 1) {
        scale[`light${index}`] = mixThemeColors(solid, '#151D31', index / 10)
      }
    }
    applyColorScale(root, `--el-color-${type}`, scale)
    if (type === 'danger') applyColorScale(root, '--el-color-error', scale)
    applyButtonPalette(root, type, solid)
  }
}

function createHintTone(scale, dark) {
  if (dark) {
    return {
      bg: `rgba(${scale.rgb}, 0.18)`,
      border: `rgba(${scale.rgb}, 0.42)`,
      text: mixThemeColors(scale.primary, '#FFFFFF', 0.7),
      foreground: readableForeground(scale.primary),
    }
  }
  return {
    bg: scale.light9,
    border: scale.light7,
    text: mixThemeColors(scale.primary, '#111827', 0.55),
    foreground: readableForeground(scale.primary),
  }
}

// 色块由当前主题派生，交错明度避免相邻角色混成一片；不为任何主色写死另一套色相。
export function createContentReferencePalette(accent, colorScheme = 'LIGHT') {
  const roles = ['source', 'type', 'name', 'code', 'version']
  const levels = [0.26, 0.5, 0.72, 0.38, 0.62]
  const hueOffsets = [0, -18, 18, -9, 9]
  const saturations = [1, 0.9, 0.75, 0.95, 0.85]
  const gradient = accent.kind === 'gradient'
  const stops = gradient ? accent.background.match(/#[\da-f]{6}/gi) : [accent.primary]
  const sample = position => {
    const index = position * (stops.length - 1)
    const left = Math.min(stops.length - 1, Math.floor(index))
    return mixThemeColors(stops[left], stops[Math.min(left + 1, stops.length - 1)], index - left)
  }
  return Object.fromEntries(roles.map((role, index) => {
    const start = index / roles.length, end = (index + 1) / roles.length
    const positions = gradient ? [start, ...stops.map((_, i) => i / (stops.length - 1)).filter(position => position > start && position < end), end] : [0]
    const colors = positions.map(position => {
      const base = hslColor(sample(position))
      return hslToHex((base.hue + (gradient ? 0 : hueOffsets[index]) + 360) % 360,
        base.saturation * saturations[index],
        Math.max(0.18, Math.min(0.82, levels[index] + (colorScheme === 'DARK' ? 0.04 : 0) + (base.lightness - 0.5) * 0.06)))
    })
    const foreground = minimumContrast('#FFFFFF', colors) >= minimumContrast('#19103B', colors) ? '#FFFFFF' : '#19103B'
    const readable = readableButtonColors(colors, foreground, foreground === '#FFFFFF' ? '#000000' : '#FFFFFF')
    const background = gradient
      ? accent.background.slice(0, accent.background.indexOf('#')) + readable.map((color, i) => `${color} ${Math.round((positions[i] - start) / (end - start) * 100)}%`).join(', ') + ')'
      : readable[0]
    return [role, { background, foreground, colors: readable }]
  }))
}

// 实心按钮按实际底色选择文字；渐变同时检查端点和中间色，交互状态不使用整块亮度滤镜。
function applyButtonPalette(root, type, background) {
  const colors = background.match(/#[\da-f]{6}/gi)
  // 相对亮度 0.4 以下的中等/偏暗底色优先白字，避免饱和粉、红、紫仅因
  // 深色字的对比度更高而失去实心按钮的视觉层级；再微调底色保证可读性。
  // 含浅亮色的渐变仍按最弱对比度选字色，不能只看第一个色标。
  const prefersWhiteText = colors.every(color => relativeLuminance(color) <= 0.4)
  const foreground = prefersWhiteText ? '#FFFFFF' : ['#FFFFFF', '#19103B'].reduce((best, candidate) =>
    minimumContrast(candidate, colors) > minimumContrast(best, colors) ? candidate : best
  )
  const contrastTarget = foreground === '#FFFFFF' ? '#000000' : '#FFFFFF'
  const normal = readableButtonColors(colors, foreground, contrastTarget)
  for (const [state, weight] of [['', 0], ['hover-', 0.08], ['active-', 0.16]]) {
    const shifted = normal.map(color => mixThemeColors(color, foreground, weight))
    const readable = readableButtonColors(shifted, foreground, contrastTarget)
    let index = 0
    setProperty(root, `--tianshu-button-${type}-${state}bg`, background.replace(/#[\da-f]{6}/gi, () => readable[index++]))
    setProperty(root, `--tianshu-button-${type}-${state}text`, foreground)
    setProperty(root, `--tianshu-button-${type}-${state}border`, readable[0])
  }
}

function readableButtonColors(colors, foreground, target) {
  for (let step = 0; step <= 100; step++) {
    const adjusted = colors.map(color => mixThemeColors(color, target, step / 100))
    if (minimumContrast(foreground, adjusted) >= 4.6) return adjusted
  }
  return colors.map(() => target)
}

function minimumContrast(foreground, colors) {
  const samples = colors.flatMap((color, index) => index === 0 ? [color]
    : Array.from({ length: 20 }, (_, step) => mixThemeColors(colors[index - 1], color, (step + 1) / 20)))
  return Math.min(...samples.map(color => contrastRatio(foreground, color)))
}

function createActionPalette(primary, colorScheme) {
  const dark = colorScheme === 'DARK'
  const surfaces = [0.1, 0.14, 0.18, 0.22].map(weight =>
    mixThemeColors(dark ? '#151D31' : '#FFFFFF', primary, weight)
  )
  const themeColor = ensureActionContrast(primary, dark, surfaces)
  const dangerColor = ensureActionContrast(dark ? '#F87171' : '#DC2626', dark)

  return Object.fromEntries(actionRoles.map(role => {
    const color = role === 'delete' ? dangerColor : themeColor
    return [role, {
      color,
      rgb: createPrimaryScale(color).rgb,
    }]
  }))
}

function ensureActionContrast(color, dark, extraSurfaces = []) {
  if (hasReadableActionContrast(color, dark, extraSurfaces)) return color

  let lowerWeight = 0
  let upperWeight = 1
  let readableColor = adjustActionLightness(color, dark, upperWeight)
  for (let iteration = 0; iteration < 16; iteration += 1) {
    const weight = (lowerWeight + upperWeight) / 2
    const candidate = adjustActionLightness(color, dark, weight)
    if (hasReadableActionContrast(candidate, dark, extraSurfaces)) {
      readableColor = candidate
      upperWeight = weight
    } else {
      lowerWeight = weight
    }
  }
  return readableColor
}

function adjustActionLightness(color, dark, weight) {
  const { hue, saturation, lightness } = hslColor(color)
  const targetLightness = dark ? 0.96 : 0.08
  return hslToHex(
    hue,
    saturation,
    lightness + (targetLightness - lightness) * weight
  )
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

function hasReadableActionContrast(color, dark, extraSurfaces) {
  const surface = dark ? '#151D31' : '#FFFFFF'
  const rowHoverSurface = dark ? '#26324D' : '#EEF2F7'
  const interactionSurfaces = [
    surface,
    rowHoverSurface,
    mixThemeColors(rowHoverSurface, color, 0.08),
    mixThemeColors(rowHoverSurface, color, 0.12),
    ...extraSurfaces,
  ]
  return interactionSurfaces.every(background =>
    contrastRatio(color, background) >= 4.5
  )
}

function contrastRatio(first, second) {
  const firstLuminance = relativeLuminance(first)
  const secondLuminance = relativeLuminance(second)
  const lighter = Math.max(firstLuminance, secondLuminance)
  const darker = Math.min(firstLuminance, secondLuminance)
  return (lighter + 0.05) / (darker + 0.05)
}

function relativeLuminance(color) {
  return hexChannels(color)
    .map(value => value / 255)
    .map(channel => channel <= 0.03928
      ? channel / 12.92
      : ((channel + 0.055) / 1.055) ** 2.4)
    .reduce((sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index], 0)
}

function hexChannels(color) {
  return String(color)
    .replace('#', '')
    .match(/.{2}/g)
    .map(value => Number.parseInt(value, 16))
}

function hslColor(color) {
  const channels = hexChannels(color).map(value => value / 255)
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

function hslToHex(hue, saturation, lightness) {
  const chroma = (1 - Math.abs(2 * lightness - 1)) * saturation
  const segment = hue / 60
  const intermediate = chroma * (1 - Math.abs((segment % 2) - 1))
  const [red, green, blue] = [
    [chroma, intermediate, 0],
    [intermediate, chroma, 0],
    [0, chroma, intermediate],
    [0, intermediate, chroma],
    [intermediate, 0, chroma],
    [chroma, 0, intermediate],
  ][Math.floor(segment) % 6]
  const offset = lightness - chroma / 2
  return `#${[red, green, blue]
    .map(channel => Math.round((channel + offset) * 255)
      .toString(16)
      .padStart(2, '0'))
    .join('')}`.toUpperCase()
}

function readableForeground(background) {
  const luminance = relativeLuminance(background)
  const whiteContrast = 1.05 / (luminance + 0.05)
  const darkContrast = (luminance + 0.05) / 0.058
  return whiteContrast >= darkContrast ? '#FFFFFF' : '#19103B'
}
