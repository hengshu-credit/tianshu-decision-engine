const PROJECT_CONTEXT_KEY = 'tianshu:project-context'

const CONTEXT_PATHS = new Set([
  '/rule',
  '/variable',
  '/datasource',
  '/database',
  '/model',
  '/test',
  '/experiment',
  '/experiment/new',
  '/log',
  '/approval',
])

export function normalizeProjectId(value) {
  const id = Number(value)
  return Number.isInteger(id) && id > 0 ? id : null
}

export function projectPageStateKey(pageKey, projectId) {
  // 全局和各项目独立缓存，避免自动项目筛选在退出范围后被恢复。
  return `${pageKey}:project:${normalizeProjectId(projectId) || 'all'}`
}

export function projectContextStorage() {
  try {
    return typeof window !== 'undefined' ? window.sessionStorage : null
  } catch (e) {
    return null
  }
}

export function readProjectContext(storage = projectContextStorage()) {
  if (!storage) return null
  try {
    const raw = storage.getItem(PROJECT_CONTEXT_KEY)
    if (!raw) return null
    const value = JSON.parse(raw)
    const id = normalizeProjectId(value && value.id)
    if (!id) return null
    return {
      id,
      projectCode: String(value.projectCode || ''),
      projectName: String(value.projectName || ''),
      status: value.status == null ? null : Number(value.status),
    }
  } catch (e) {
    return null
  }
}

export function writeProjectContext(
  storage = projectContextStorage(),
  project
) {
  if (!storage) return
  try {
    const id = normalizeProjectId(project && project.id)
    if (!id) {
      storage.removeItem(PROJECT_CONTEXT_KEY)
      return
    }
    storage.setItem(
      PROJECT_CONTEXT_KEY,
      JSON.stringify({
        id,
        projectCode: String(project.projectCode || ''),
        projectName: String(project.projectName || ''),
        status: project.status == null ? null : Number(project.status),
      })
    )
  } catch (e) {
    // 浏览器禁用会话存储时，仍允许当前页面继续使用项目上下文。
  }
}

export function routeSupportsProjectContext(path) {
  const normalized = String(path || '').split('?')[0]
  if (
    !normalized ||
    normalized === '/project' ||
    normalized.startsWith('/project/')
  ) {
    return false
  }
  return CONTEXT_PATHS.has(normalized)
}

export function withProjectContext(fullPath, projectId) {
  const id = normalizeProjectId(projectId)
  const source = String(fullPath || '')
  const [path, queryText = ''] = source.split('?')
  if (!id || !routeSupportsProjectContext(path)) return fullPath
  const query = Object.fromEntries(new URLSearchParams(queryText).entries())
  query.projectId = id
  return { path, query }
}

export function routeProjectId(route, currentProject) {
  const explicit = normalizeProjectId(
    route && route.query && route.query.projectId
  )
  if (explicit) return explicit
  return normalizeProjectId(currentProject && currentProject.id)
}
