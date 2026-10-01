export const DASHBOARD_FILTERS_KEY = 'tianshu:dashboard:filters'
export const DASHBOARD_MAP_VIEW_KEY = 'tianshu:dashboard:map-view'

const MAX_RANGE_MS = 90 * 24 * 60 * 60 * 1000

function pad(value) {
  return String(value).padStart(2, '0')
}

export function formatDashboardTime(value) {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function parseDashboardTime(value) {
  if (typeof value !== 'string') return null
  const match = value.match(
    /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})$/
  )
  if (!match) return null
  const date = new Date(
    Number(match[1]), Number(match[2]) - 1, Number(match[3]),
    Number(match[4]), Number(match[5]), Number(match[6])
  )
  return formatDashboardTime(date) === value.replace('T', ' ') ? date : null
}

export function defaultDashboardFilters(now = new Date()) {
  const end = new Date(now)
  const start = new Date(
    end.getFullYear(), end.getMonth(), end.getDate() - 6, 0, 0, 0
  )
  return {
    projectId: null,
    ruleCode: '',
    ruleName: '',
    startTime: formatDashboardTime(start),
    endTime: formatDashboardTime(end)
  }
}

export function dashboardQuickRange(days, now = new Date()) {
  const end = new Date(now)
  const start = new Date(
    end.getFullYear(), end.getMonth(), end.getDate() - days + 1, 0, 0, 0
  )
  return [start, end]
}

export function validateDashboardRange(startTime, endTime) {
  const start = parseDashboardTime(startTime)
  const end = parseDashboardTime(endTime)
  if (!start || !end) return { valid: false, message: '请选择完整的开始和结束时间' }
  if (start.getTime() > end.getTime()) {
    return { valid: false, message: '开始时间不能晚于结束时间' }
  }
  if (end.getTime() - start.getTime() > MAX_RANGE_MS) {
    return { valid: false, message: '统计时间跨度不能超过90天' }
  }
  return { valid: true, message: '' }
}

export function readDashboardFilters(storage, now = new Date()) {
  const fallback = defaultDashboardFilters(now)
  if (!storage || typeof storage.getItem !== 'function') return fallback
  try {
    const raw = storage.getItem(DASHBOARD_FILTERS_KEY)
    if (!raw) return fallback
    const value = JSON.parse(raw)
    if (!value || !validateDashboardRange(
      value.startTime, value.endTime
    ).valid) return fallback
    const projectId = value.projectId === null || value.projectId === ''
      ? null
      : Number(value.projectId)
    if (projectId !== null && (!Number.isInteger(projectId) || projectId <= 0)) {
      return fallback
    }
    return {
      projectId,
      ruleCode: typeof value.ruleCode === 'string' ? value.ruleCode.trim() : '',
      ruleName: typeof value.ruleName === 'string' ? value.ruleName.trim() : '',
      startTime: value.startTime,
      endTime: value.endTime
    }
  } catch (error) {
    return fallback
  }
}

export function writeDashboardFilters(storage, filters) {
  if (!storage || typeof storage.setItem !== 'function') return
  try {
    storage.setItem(DASHBOARD_FILTERS_KEY, JSON.stringify(filters))
  } catch (error) {
    // 会话存储不可用时不阻断看板查询。
  }
}

export function defaultDashboardMapView() {
  return { longitude: 104, latitude: 35, zoom: 1.5 }
}

export function defaultDashboardGlobalMapView() {
  return { longitude: 0, latitude: 0, zoom: 1 }
}

function normalizeDashboardMapView(view) {
  const value = view || {}
  const number = raw => raw === null || raw === undefined ||
    (typeof raw === 'string' && !raw.trim()) ? Number.NaN : Number(raw)
  return {
    longitude: number(value.longitude),
    latitude: number(value.latitude),
    zoom: number(value.zoom)
  }
}

export function validateDashboardMapView(view) {
  const value = normalizeDashboardMapView(view)
  if (!Number.isFinite(value.longitude) || value.longitude < -180 || value.longitude > 180) {
    return { valid: false, message: '中心经度须在 -180 至 180 之间' }
  }
  if (!Number.isFinite(value.latitude) || value.latitude < -90 || value.latitude > 90) {
    return { valid: false, message: '中心纬度须在 -90 至 90 之间' }
  }
  if (!Number.isFinite(value.zoom) || value.zoom < 0.5 || value.zoom > 10) {
    return { valid: false, message: '缩放比例须在 0.5 至 10 之间' }
  }
  return { valid: true, message: '', value }
}

export function readDashboardMapView(storage) {
  const fallback = defaultDashboardMapView()
  if (!storage || typeof storage.getItem !== 'function') return fallback
  try {
    const raw = storage.getItem(DASHBOARD_MAP_VIEW_KEY)
    if (!raw) return fallback
    const result = validateDashboardMapView(JSON.parse(raw))
    return result.valid ? result.value : fallback
  } catch (error) {
    return fallback
  }
}

export function writeDashboardMapView(storage, view) {
  const result = validateDashboardMapView(view)
  if (!result.valid) return result
  if (!storage || typeof storage.setItem !== 'function') {
    return { valid: false, message: '当前浏览器无法保存地图视角' }
  }
  try {
    storage.setItem(DASHBOARD_MAP_VIEW_KEY, JSON.stringify(result.value))
    return result
  } catch (error) {
    return { valid: false, message: '当前浏览器无法保存地图视角' }
  }
}
