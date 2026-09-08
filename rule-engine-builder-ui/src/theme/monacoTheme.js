const LIGHT_DEFAULTS = {
  background: '#F8FAFC',
  muted: '#F1F5F9',
  foreground: '#172033',
  secondary: '#475569',
  tertiary: '#5B6678',
  border: '#E2E8F0',
  primary: '#2639E9',
  activePrimary: '#1E2EBA',
  secondaryAccent: '#F76E6C',
}

const DARK_DEFAULTS = {
  background: '#182238',
  muted: '#202B45',
  foreground: '#EDF2FF',
  secondary: '#C2CCE0',
  tertiary: '#93A2BD',
  border: '#28344D',
  primary: '#2639E9',
  activePrimary: '#6775F0',
  secondaryAccent: '#F76E6C',
}

export function getMonacoScrollbarOptions(root = document.documentElement) {
  const size = parseInt(getComputedStyle(root).getPropertyValue('--tianshu-scrollbar-size'), 10) || 4
  return {
    vertical: 'auto',
    horizontal: 'auto',
    verticalScrollbarSize: size,
    horizontalScrollbarSize: size,
  }
}

export function syncMonacoTheme(
  monaco,
  { root = document.documentElement, colorScheme } = {}
) {
  if (!monaco || !monaco.editor || !root) return ''
  const dark = colorScheme
    ? String(colorScheme).toUpperCase() === 'DARK'
    : root.dataset.theme === 'dark'
  const defaults = dark ? DARK_DEFAULTS : LIGHT_DEFAULTS
  const styles = getComputedStyle(root)
  const colors = {
    background: readColor(styles, '--tianshu-bg-soft', defaults.background),
    muted: readColor(styles, '--tianshu-bg-muted', defaults.muted),
    foreground: readColor(
      styles,
      '--tianshu-text-primary',
      defaults.foreground
    ),
    secondary: readColor(
      styles,
      '--tianshu-text-secondary',
      defaults.secondary
    ),
    tertiary: readColor(
      styles,
      '--tianshu-text-tertiary',
      defaults.tertiary
    ),
    border: readColor(
      styles,
      '--tianshu-border-subtle',
      defaults.border
    ),
    primary: readColor(styles, '--el-color-primary', defaults.primary),
    activePrimary: readColor(
      styles,
      dark ? '--el-color-primary-light-3' : '--el-color-primary-dark-2',
      defaults.activePrimary
    ),
    secondaryAccent: readColor(
      styles,
      '--tianshu-color-secondary',
      defaults.secondaryAccent
    ),
  }
  const name = dark ? 'tianshu-dark' : 'tianshu-light'

  monaco.editor.defineTheme(name, {
    base: dark ? 'vs-dark' : 'vs',
    inherit: true,
    rules: [
      { token: 'keyword', foreground: stripHash(colors.activePrimary), fontStyle: 'bold' },
      { token: 'function', foreground: stripHash(colors.secondaryAccent) },
      { token: 'predefined', foreground: stripHash(colors.secondaryAccent) },
      { token: 'identifier', foreground: stripHash(colors.foreground) },
      { token: 'number', foreground: stripHash(colors.secondaryAccent) },
      { token: 'string', foreground: stripHash(colors.primary) },
      { token: 'string.escape', foreground: stripHash(colors.activePrimary) },
      { token: 'comment', foreground: stripHash(colors.tertiary), fontStyle: 'italic' },
      { token: 'operator', foreground: stripHash(colors.activePrimary) },
      { token: 'delimiter', foreground: stripHash(colors.secondary) },
    ],
    colors: {
      'editor.background': colors.background,
      'editor.foreground': colors.foreground,
      'editorLineNumber.foreground': colors.tertiary,
      'editorLineNumber.activeForeground': colors.secondary,
      'editorCursor.foreground': colors.activePrimary,
      'editor.selectionBackground': withAlpha(colors.primary, '40'),
      'editor.inactiveSelectionBackground': withAlpha(colors.primary, '26'),
      'editor.lineHighlightBackground': colors.muted,
      'editorIndentGuide.background': colors.border,
      'editorIndentGuide.activeBackground': colors.tertiary,
      'editorWhitespace.foreground': colors.border,
      'editor.findMatchBackground': withAlpha(colors.primary, '55'),
      'editor.findMatchHighlightBackground': withAlpha(colors.primary, '2E'),
      'editor.hoverHighlightBackground': withAlpha(colors.primary, '1F'),
      'editorWidget.background': colors.background,
      'editorWidget.border': colors.border,
      'editorSuggestWidget.background': colors.background,
      'editorSuggestWidget.border': colors.border,
      'editorSuggestWidget.foreground': colors.foreground,
      'editorSuggestWidget.selectedBackground': colors.muted,
    },
  })
  monaco.editor.setTheme(name)
  return name
}

function readColor(styles, property, fallback) {
  return normalizeColor(styles.getPropertyValue(property)) || fallback
}

function normalizeColor(value) {
  const color = String(value || '').trim()
  const shortHex = color.match(/^#([0-9a-f]{3})$/i)
  if (shortHex) {
    return `#${shortHex[1]
      .split('')
      .map((part) => part + part)
      .join('')}`.toUpperCase()
  }
  if (/^#[0-9a-f]{6}$/i.test(color)) return color.toUpperCase()
  const rgb = color.match(
    /^rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*([\d.]+))?\s*\)$/i
  )
  if (!rgb) return ''
  const hex = rgb
    .slice(1, 4)
    .map((channel) => Number(channel).toString(16).padStart(2, '0'))
    .join('')
  const alpha = rgb[4]
    ? Math.round(Number(rgb[4]) * 255)
        .toString(16)
        .padStart(2, '0')
    : ''
  return `#${hex}${alpha}`.toUpperCase()
}

function withAlpha(color, alpha) {
  return `${color.slice(0, 7)}${alpha}`
}

function stripHash(color) {
  return color.replace('#', '').slice(0, 6)
}
