export const SIDEBAR_MIN_WIDTH = 64
export const SIDEBAR_MAX_WIDTH = 320
export const SIDEBAR_DEFAULT_WIDTH = 220
export const SIDEBAR_COMPACT_THRESHOLD = 168
export const SIDEBAR_STORAGE_KEY = 'tianshu:layout:sidebar'

export const SIDEBAR_MENUS = [
  { index: '/project', label: '项目管理', icon: 'el-icon-folder-opened' },
  { index: '/rule', label: '规则管理', icon: 'el-icon-s-operation' },
  { index: '/variable', label: '变量管理', icon: 'el-icon-collection' },
  { index: '/list', label: '名单管理', icon: 'el-icon-notebook-2' },
  { index: '/datasource', label: '外数管理', icon: 'el-icon-connection' },
  { index: '/database', label: '数据库管理', icon: 'el-icon-coin' },
  { index: '/model', label: '模型管理', icon: 'el-icon-cpu' },
  { index: '/function', label: '函数管理', icon: 'el-icon-c-scale-to-original' },
  { index: '/test', label: '规则测试', icon: 'el-icon-video-play' },
  { index: '/lineage', label: '血缘分析', icon: 'el-icon-share' },
  { index: '/experiment', label: '分流实验', icon: 'el-icon-s-flag' },
  { index: '/log', label: '执行日志', icon: 'el-icon-document-checked' },
  { index: '/billing', label: '账单管理', icon: 'el-icon-wallet' }
]

const MENU_PREFIXES = [
  ['/designer/', '/rule'],
  ['/project/', '/project'],
  ['/rule/', '/rule'],
  ['/list/', '/list'],
  ['/model/', '/model'],
  ['/function/', '/function'],
  ['/datasource/', '/datasource'],
  ['/database/', '/database'],
  ['/experiment/', '/experiment']
]

const PINYIN_BOUNDARIES = '阿八嚓哒妸发旮哈讥咔垃妈拏噢妑七呥仨他屲夕丫帀'
const PINYIN_INITIALS = 'ABCDEFGHJKLMNOPQRSTWXYZ'

function defaultSidebarState() {
  return {
    width: SIDEBAR_DEFAULT_WIDTH,
    lastExpandedWidth: SIDEBAR_DEFAULT_WIDTH
  }
}

export function clampSidebarWidth(width) {
  const value = Number(width)
  if (!Number.isFinite(value)) return SIDEBAR_DEFAULT_WIDTH
  return Math.min(SIDEBAR_MAX_WIDTH, Math.max(SIDEBAR_MIN_WIDTH, Math.round(value)))
}

function clampExpandedWidth(width) {
  return Math.max(SIDEBAR_COMPACT_THRESHOLD, clampSidebarWidth(width))
}

export function readSidebarState(storage) {
  if (!storage || typeof storage.getItem !== 'function') return defaultSidebarState()
  try {
    const raw = storage.getItem(SIDEBAR_STORAGE_KEY)
    if (!raw) return defaultSidebarState()
    const saved = JSON.parse(raw)
    return {
      width: clampSidebarWidth(saved && saved.width),
      lastExpandedWidth: clampExpandedWidth(saved && saved.lastExpandedWidth)
    }
  } catch (e) {
    return defaultSidebarState()
  }
}

export function writeSidebarState(storage, state) {
  if (!storage || typeof storage.setItem !== 'function') return
  try {
    storage.setItem(SIDEBAR_STORAGE_KEY, JSON.stringify({
      width: clampSidebarWidth(state && state.width),
      lastExpandedWidth: clampExpandedWidth(state && state.lastExpandedWidth)
    }))
  } catch (e) {
    // 浏览器禁用存储时仍保持当前页面可用。
  }
}

export function getActiveMenuIndex(path) {
  const currentPath = path || ''
  const matched = MENU_PREFIXES.find(item => currentPath.startsWith(item[0]))
  return matched ? matched[1] : currentPath
}

export function getAvatarInitial(username) {
  const value = String(username || '').trim()
  if (!value) return 'U'
  const first = Array.from(value)[0]
  if (/^[a-z0-9]$/i.test(first)) return first.toUpperCase()
  if (!/^[\u3400-\u9fff]$/.test(first)) return 'U'

  try {
    const collator = new Intl.Collator('zh-CN-u-co-pinyin')
    let initial = 'U'
    for (let index = 0; index < PINYIN_BOUNDARIES.length; index += 1) {
      if (collator.compare(first, PINYIN_BOUNDARIES[index]) < 0) break
      initial = PINYIN_INITIALS[index]
    }
    return initial || 'U'
  } catch (e) {
    return 'U'
  }
}

export function routeToTab(route) {
  const meta = (route && route.meta) || {}
  const path = (route && route.path) || '/project'
  return {
    fullPath: (route && route.fullPath) || path,
    path,
    name: route && route.name,
    title: meta.title || (route && route.name) || path
  }
}

export function isWorkspaceRoute(route) {
  return !!(route && route.path !== '/login' && Array.isArray(route.matched) && route.matched.length > 1)
}

export function isEditableShortcutTarget(target) {
  const element = target && target.nodeType === 3 ? target.parentElement : target
  if (!element || typeof element.closest !== 'function') return false
  return !!element.closest('input, textarea, select, [contenteditable="true"], [contenteditable=""], .monaco-editor')
}

export function resolveTabSwitchPath(tabs, activePath, offset, loop) {
  const source = Array.isArray(tabs) ? tabs : []
  const index = source.findIndex(tab => tab.fullPath === activePath)
  if (index < 0 || source.length < 2) return ''

  let nextIndex = index + offset
  if (loop) nextIndex = (nextIndex + source.length) % source.length
  return source[nextIndex] ? source[nextIndex].fullPath : ''
}

export function resolveWorkspaceShortcut(event, tabs, activePath) {
  if (!event || !event.ctrlKey || event.altKey || event.metaKey || !activePath) return null
  const key = String(event.key || '').toLowerCase()

  if (!event.shiftKey && (key === 'w' || key === 'r')) {
    return {
      type: 'operate',
      operation: key === 'w' ? 'current' : 'refresh',
      targetPath: activePath
    }
  }

  if (key === 'tab') {
    const targetPath = resolveTabSwitchPath(tabs, activePath, event.shiftKey ? -1 : 1, true)
    return targetPath ? { type: 'activate', targetPath } : null
  }

  if (!event.shiftKey && (key === 'arrowleft' || key === 'arrowright')) {
    if (isEditableShortcutTarget(event.target)) return null
    const targetPath = resolveTabSwitchPath(tabs, activePath, key === 'arrowleft' ? -1 : 1, false)
    return targetPath ? { type: 'activate', targetPath } : null
  }

  return null
}

function includesPath(tabs, path) {
  return tabs.some(tab => tab.fullPath === path)
}

export function resolveCloseOperation(tabs, activePath, targetPath, operation) {
  const source = Array.isArray(tabs) ? tabs.slice() : []
  const targetIndex = source.findIndex(tab => tab.fullPath === targetPath)
  if (targetIndex < 0) return { tabs: source, nextPath: activePath || '/project' }

  let remaining
  if (operation === 'current') {
    remaining = source.filter(tab => tab.fullPath !== targetPath)
  } else if (operation === 'left') {
    remaining = source.slice(targetIndex)
  } else if (operation === 'right') {
    remaining = source.slice(0, targetIndex + 1)
  } else if (operation === 'others') {
    remaining = [source[targetIndex]]
  } else if (operation === 'all') {
    remaining = []
  } else {
    return { tabs: source, nextPath: activePath || '/project' }
  }

  if (operation === 'all' || remaining.length === 0) {
    return { tabs: remaining, nextPath: '/project' }
  }
  if (includesPath(remaining, activePath)) {
    return { tabs: remaining, nextPath: activePath }
  }
  if (operation === 'current' && activePath === targetPath) {
    const neighbor = remaining[targetIndex] || remaining[targetIndex - 1]
    return { tabs: remaining, nextPath: neighbor ? neighbor.fullPath : '/project' }
  }
  if (includesPath(remaining, targetPath)) {
    return { tabs: remaining, nextPath: targetPath }
  }
  return { tabs: remaining, nextPath: remaining[0].fullPath }
}
