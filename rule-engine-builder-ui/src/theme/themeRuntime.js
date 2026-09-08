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
  const hintPalette = createHintPalette(
    preset.primary,
    preset.secondary,
    normalized.colorScheme
  )
  const actionPalette = createActionPalette(
    preset.primary,
    normalized.colorScheme
  )

  root.dataset.theme = normalized.colorScheme.toLowerCase()
  root.dataset.sidebarTheme = normalized.sidebarTheme.toLowerCase()
  root.dataset.navigationLayout = normalized.navigationLayout.toLowerCase()
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
  applyColorScale(root, '--el-color-primary', scale)
  applyColorScale(root, '--tianshu-color-secondary', secondaryScale)
  applyColorScale(root, '--el-color-info', hintPalette.info.scale)
  applyColorScale(root, '--el-color-success', hintPalette.success.scale)
  applyColorScale(root, '--el-color-warning', hintPalette.warning.scale)
  applyColorScale(root, '--el-color-danger', hintPalette.danger.scale)
  for (const [type, tone] of Object.entries(hintPalette)) {
    setProperty(root, `--tianshu-${type}-bg`, tone.bg)
    setProperty(root, `--tianshu-${type}-border`, tone.border)
    setProperty(root, `--tianshu-${type}-text`, tone.text)
    setProperty(root, `--tianshu-${type}-foreground`, tone.foreground)
  }
  for (const [role, tone] of Object.entries(actionPalette)) {
    setProperty(root, `--tianshu-action-${role}`, tone.color)
    setProperty(root, `--tianshu-action-${role}-rgb`, tone.rgb)
  }
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

function createHintPalette(primary, secondary, colorScheme) {
  const info = createPrimaryScale(primary)
  const success = createPrimaryScale(secondary)
  const warning = createPrimaryScale(mixThemeColors(primary, secondary, 0.5))
  const danger = createPrimaryScale(mixThemeColors(secondary, '#000000', 0.1))
  const dark = colorScheme === 'DARK'

  return {
    info: createHintTone(info, dark, 'soft'),
    success: createHintTone(success, dark, 'soft'),
    warning: createHintTone(warning, dark, 'medium'),
    danger: createHintTone(danger, dark, 'strong'),
  }
}

function createHintTone(scale, dark, strength) {
  const alpha = {
    soft: { bg: 0.18, border: 0.42 },
    medium: { bg: 0.22, border: 0.48 },
    strong: { bg: 0.28, border: 0.6 },
  }[strength]

  if (dark) {
    return {
      scale,
      bg: `rgba(${scale.rgb}, ${alpha.bg})`,
      border: `rgba(${scale.rgb}, ${alpha.border})`,
      text: mixThemeColors(scale.primary, '#FFFFFF', 0.7),
      foreground: readableForeground(scale.primary),
    }
  }
  return {
    scale,
    bg: strength === 'strong' ? scale.light8 : scale.light9,
    border: strength === 'soft' ? scale.light7 : scale.light6,
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

function ensureActionContrast(color, dark) {
  if (hasReadableActionContrast(color, dark)) return color

  let lowerWeight = 0
  let upperWeight = 1
  let readableColor = adjustActionLightness(color, dark, upperWeight)
  for (let iteration = 0; iteration < 16; iteration += 1) {
    const weight = (lowerWeight + upperWeight) / 2
    const candidate = adjustActionLightness(color, dark, weight)
    if (hasReadableActionContrast(candidate, dark)) {
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
  ['edit', 'delete'],
  ['execute', 'detail', 'delete'],
  ['execute', 'edit', 'version', 'delete'],
  ['inspect', 'compare', 'rollback'],
  ['detail', 'edit', 'publish', 'global', 'delete'],
  ['detail', 'edit', 'disable', 'global', 'delete'],
  ['edit', 'execute', 'inspect', 'delete'],
  ['edit', 'execute', 'add', 'delete'],
  ['edit', 'execute', 'delete'],
  ['detail', 'edit', 'delete'],
  ['edit', 'detail', 'execute', 'configure', 'global', 'delete'],
  ['edit', 'configure', 'delete'],
  ['edit', 'global', 'delete'],
  ['detail', 'inspect', 'delete'],
  ['edit', 'detail', 'configure', 'docs', 'delete'],
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

function hasReadableActionContrast(color, dark) {
  const surface = dark ? '#151D31' : '#FFFFFF'
  const rowHoverSurface = dark ? '#26324D' : '#EEF2F7'
  const interactionSurfaces = [
    surface,
    rowHoverSurface,
    mixThemeColors(rowHoverSurface, color, 0.08),
    mixThemeColors(rowHoverSurface, color, 0.12),
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
