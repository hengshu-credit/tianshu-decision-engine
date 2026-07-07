const DOLLAR_PATH_PREFIX = '$.'
const TEMPLATE_PATTERN = /\$\{([^}]+)\}/g
const BARE_PATH_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*$/
const RESERVED_WORDS = new Set(['true', 'false', 'null', 'undefined', 'nan', 'infinity'])

function normalizePath(path) {
  if (typeof path !== 'string') return ''
  const text = path.trim()
  if (!text) return ''
  return text.indexOf(DOLLAR_PATH_PREFIX) === 0 ? text.substring(2).trim() : text
}

function looksLikeBarePath(text) {
  if (!BARE_PATH_PATTERN.test(text)) return false
  const lower = text.toLowerCase()
  if (RESERVED_WORDS.has(lower)) return false
  if (text.indexOf('.') >= 0) return true
  return /^[a-z][A-Za-z0-9_]*$/.test(text)
}

function addPath(paths, seen, path) {
  const normalized = normalizePath(path)
  if (!normalized || seen.has(normalized)) return
  seen.add(normalized)
  paths.push(normalized)
}

export function collectReferencePathsFromText(value, options = {}) {
  if (typeof value !== 'string') return []
  const text = value.trim()
  if (!text) return []
  const paths = []
  const seen = new Set()

  if (text.indexOf(DOLLAR_PATH_PREFIX) === 0) {
    addPath(paths, seen, text)
  }

  let match
  TEMPLATE_PATTERN.lastIndex = 0
  while ((match = TEMPLATE_PATTERN.exec(text)) !== null) {
    addPath(paths, seen, match[1])
  }

  if (options.allowBarePath && paths.length === 0 && looksLikeBarePath(text)) {
    addPath(paths, seen, text)
  }

  return paths
}

export function collectReferencePaths(value, options = {}) {
  const paths = []
  const seen = new Set()
  const allowBarePath = options.allowBarePath === true

  const visit = node => {
    if (node == null) return
    if (typeof node === 'string') {
      collectReferencePathsFromText(node, { allowBarePath }).forEach(path => addPath(paths, seen, path))
      return
    }
    if (Array.isArray(node)) {
      node.forEach(visit)
      return
    }
    if (typeof node === 'object') {
      Object.keys(node).forEach(key => visit(node[key]))
    }
  }

  visit(value)
  return paths
}

export function setPathValue(target, path, value) {
  if (!target || !path) return
  const parts = normalizePath(path).split('.').map(item => item.trim()).filter(Boolean)
  if (parts.length === 0) return
  let current = target
  parts.forEach((part, index) => {
    if (index === parts.length - 1) {
      current[part] = value
      return
    }
    if (!current[part] || typeof current[part] !== 'object' || Array.isArray(current[part])) {
      current[part] = {}
    }
    current = current[part]
  })
}

export function buildObjectFromPaths(paths, value = '') {
  const result = {}
  const list = paths || []
  list.forEach(path => setPathValue(result, path, value))
  return result
}

export function sampleValueForVarType(type) {
  const normalized = String(type || '').toUpperCase()
  if (['NUMBER', 'INTEGER', 'INT', 'LONG', 'DECIMAL', 'DOUBLE', 'FLOAT'].indexOf(normalized) >= 0) return 0
  if (['BOOLEAN', 'BOOL'].indexOf(normalized) >= 0) return false
  if (['LIST', 'ARRAY'].indexOf(normalized) >= 0) return []
  if (['MAP', 'OBJECT'].indexOf(normalized) >= 0) return {}
  return ''
}
