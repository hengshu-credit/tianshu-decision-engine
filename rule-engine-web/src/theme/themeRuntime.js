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
  for (const [name, value] of Object.entries(infoTone)) {
    setProperty(root, `--tianshu-info-${name}`, value)
  }
  for (const [role, tone] of Object.entries(actionPalette)) {
    setProperty(root, `--tianshu-action-${role}`, tone.color)
    setProperty(root, `--tianshu-action-${role}-rgb`, tone.rgb)
  }
  const dark = normalized.colorScheme === 'DARK'
  const activeSurface = mixThemeColors(dark ? '#151D31' : '#FFFFFF', preset.primary, dark ? 0.2 : 0.1)
  setProperty(root, '--tianshu-primary-text', ensureActionContrast(preset.primary, dark, [activeSurface]))
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

function createActionPalette(primary, colorScheme) {
  const dark = colorScheme === 'DARK'
  const colors = {
    edit: ensureActionContrast(primary, dark),
  }
  colors.delete = selectActionColor(
    'delete',
    [
      '#F00024',
      '#B00000',
      '#A60038',
      '#FF3F00',
      '#C02000',
    ],
    colors,
    dark
  )

  for (const role of actionAssignmentOrder) {
    colors[role] = selectActionColor(
      role,
      preferredActionCandidates(role),
      colors,
      dark
    )
  }

  return Object.fromEntries(actionRoles.map(role => {
    const color = colors[role]
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

const actionAssignmentOrder = [
  'configure',
  'detail',
  'execute',
  'global',
  'inspect',
  'reset',
  'publish',
  'disable',
  'version',
  'docs',
  'add',
  'compare',
  'rollback',
]

const actionCandidateBases = [
  '#0052FF',
  '#2546F5',
  '#5A1BFF',
  '#8418FF',
  '#B100FF',
  '#D500E6',
  '#F000B8',
  '#F00083',
  '#F0004F',
  '#F00024',
  '#FF3600',
  '#FF6500',
  // 发布加入多个操作组合后，补充明度不同的蓝紫候选，保留既有对比度和色差门槛。
  '#9BC9FF',
  '#D8B4FF',
  // 版本入口出现在更多列表组合中，补充浅粉候选以保持同列操作的色差。
  '#FFC9DD',
]

const preferredActionCandidateIndex = {
  detail: 0,
  execute: 11,
  inspect: 6,
  configure: 3,
  version: 1,
  compare: 10,
  rollback: 8,
  global: 7,
  publish: 11,
  reset: 0,
  disable: 5,
  add: 4,
  docs: 6,
}

const coexistingActionGroups = [
  ['edit', 'configure', 'reset', 'publish'],
  ['edit', 'configure', 'reset', 'disable'],
  ['configure', 'publish'],
  ['configure', 'disable'],
  ['version', 'edit', 'publish', 'delete'],
  ['version', 'execute', 'detail', 'publish', 'delete'],
  ['execute', 'edit', 'version', 'publish', 'delete'],
  ['inspect', 'compare', 'rollback'],
  ['version', 'detail', 'edit', 'publish', 'global', 'delete'],
  ['detail', 'edit', 'disable', 'global', 'delete'],
  ['version', 'edit', 'execute', 'inspect', 'publish', 'delete'],
  ['version', 'edit', 'execute', 'add', 'publish', 'delete'],
  ['version', 'edit', 'execute', 'publish', 'delete'],
  ['version', 'detail', 'edit', 'publish', 'delete'],
  ['version', 'edit', 'detail', 'execute', 'configure', 'global', 'publish', 'delete'],
  ['version', 'edit', 'configure', 'publish', 'delete'],
  ['version', 'edit', 'global', 'publish', 'delete'],
  ['version', 'detail', 'inspect', 'publish', 'delete'],
  ['version', 'edit', 'detail', 'configure', 'docs', 'publish', 'delete'],
]

function preferredActionCandidates(role) {
  const start = preferredActionCandidateIndex[role]
  return [
    ...actionCandidateBases.slice(start),
    ...actionCandidateBases.slice(0, start),
  ]
}

function selectActionColor(role, candidates, selectedColors, dark) {
  const neighborColors = coexistingActionGroups
    .filter(group => group.includes(role))
    .flatMap(group => group)
    .filter(neighbor => neighbor !== role && selectedColors[neighbor])
    .map(neighbor => selectedColors[neighbor])
  const readableCandidates = [...new Set(
    candidates.map(candidate => ensureActionContrast(candidate, dark))
  )]
  if (neighborColors.length === 0) return readableCandidates[0]

  const distinctCandidate = readableCandidates.find(candidate =>
    neighborColors.every(color => perceptualColorDistance(candidate, color) >= 18)
  )
  if (distinctCandidate) return distinctCandidate

  return readableCandidates.reduce((best, candidate) => {
    const distance = Math.min(...neighborColors.map(color =>
      perceptualColorDistance(candidate, color)
    ))
    return distance > best.distance ? { color: candidate, distance } : best
  }, { color: readableCandidates[0], distance: -1 }).color
}

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

function perceptualColorDistance(first, second) {
  const firstLab = labColor(first)
  const secondLab = labColor(second)
  return Math.sqrt(
    (firstLab.lightness - secondLab.lightness) ** 2 +
    (firstLab.a - secondLab.a) ** 2 +
    (firstLab.b - secondLab.b) ** 2
  )
}

function labColor(color) {
  const [red, green, blue] = hexChannels(color).map(value => {
    const channel = value / 255
    return channel <= 0.04045
      ? channel / 12.92
      : ((channel + 0.055) / 1.055) ** 2.4
  })
  const [x, y, z] = [
    (red * 0.4124 + green * 0.3576 + blue * 0.1805) / 0.95047,
    red * 0.2126 + green * 0.7152 + blue * 0.0722,
    (red * 0.0193 + green * 0.1192 + blue * 0.9505) / 1.08883,
  ].map(value => value > 0.008856
    ? Math.cbrt(value)
    : 7.787 * value + 16 / 116)

  return {
    lightness: 116 * y - 16,
    a: 500 * (x - y),
    b: 200 * (y - z),
  }
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
