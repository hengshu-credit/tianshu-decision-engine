import { reactive } from 'vue'
import axios from 'axios'
import { PLATFORM_SUCCESS_CODE } from '@/utils/platformResponse'
import { DEFAULT_WATERMARK_SETTINGS, resolveWatermarkSettings } from '@/system/watermark'
import { getCurrentUser } from '@/security/permissionState'

const base = import.meta.env.BASE_URL || './'
export const DEFAULT_BRAND = Object.freeze({ systemName: '天枢决策引擎', subtitle: '天工开物，枢衡定策', logoUrl: `${base}images/hengshucredit_animated.svg`, faviconUrl: `${base}images/hengshucredit_animated.svg`, ...DEFAULT_WATERMARK_SETTINGS })
export const systemBrand = reactive({ ...DEFAULT_BRAND })
let pending = null
let fetchedAt = 0
let routeTitle = ''
let appliedRevision = 0

export function isBrandImageUrl(value) {
  if (typeof value !== 'string' || !value || /[\s\\]/.test(value)) return false
  try {
    const url = new URL(value, 'https://brand.invalid')
    return !url.username && !url.password && (/^https?:\/\//.test(value) || (value.startsWith('/') && !value.startsWith('//')))
  } catch { return false }
}

export function resolveBrandImages(value = {}) {
  const image = field => value[`${field}Id`] ? `/api/auth/console/brand/assets/${value[`${field}Id`]}` : isBrandImageUrl(value[`${field}Url`]) ? value[`${field}Url`] : ''
  const logoUrl = image('logo') || DEFAULT_BRAND.logoUrl
  return { logoUrl, faviconUrl: image('favicon') || logoUrl }
}

export function applySystemBrand(value) {
  if (!value || typeof value.systemName !== 'string' || !value.systemName.trim()) return
  appliedRevision += 1
  systemBrand.systemName = value.systemName
  systemBrand.subtitle = typeof value.subtitle === 'string' ? value.subtitle : DEFAULT_BRAND.subtitle
  Object.assign(systemBrand, resolveBrandImages(value), resolveWatermarkSettings(value), { watermarkEnabled: value.watermarkEnabled === true })
  updateBrandDocument()
}

export function updateBrandDocument(title = routeTitle) {
  routeTitle = title || ''
  if (typeof document === 'undefined') return
  document.title = routeTitle ? `${routeTitle} · ${systemBrand.systemName}` : systemBrand.systemName
  const icons = [...document.querySelectorAll('link[rel="icon"]')]
  let icon = icons[0]
  if (!icon) { icon = document.createElement('link'); icon.rel = 'icon'; document.head.appendChild(icon) }
  icons.slice(1).forEach(item => item.remove())
  icon.removeAttribute('type')
  icon.removeAttribute('sizes')
  icon.href = systemBrand.faviconUrl
}

export function loadSystemBrand(force = false) {
  if (pending) return pending
  if (!force && Date.now() - fetchedAt < 30000) return Promise.resolve(systemBrand)
  const requestRevision = appliedRevision
  pending = axios.get('/api/auth/console/brand', { timeout: 5000 }).then(response => {
    // 保存成功后的新配置不能被较早发起的刷新请求覆盖。
    if (response.data?.code === PLATFORM_SUCCESS_CODE && requestRevision === appliedRevision) { applySystemBrand(response.data.data); fetchedAt = Date.now() }
    return systemBrand
  }).catch(() => systemBrand).finally(() => { pending = null })
  return pending
}

export function initializeSystemBrand(router) {
  loadSystemBrand()
  const removeRouteHook = router.afterEach(to => { updateBrandDocument(to.meta?.title); loadSystemBrand() })
  const refreshVisible = () => { if (document.visibilityState !== 'hidden') loadSystemBrand(true) }
  const timer = window.setInterval(() => { if (getCurrentUser()) refreshVisible() }, 5000)
  window.addEventListener('focus', refreshVisible)
  document.addEventListener('visibilitychange', refreshVisible)
  return () => {
    window.clearInterval(timer)
    window.removeEventListener('focus', refreshVisible)
    document.removeEventListener('visibilitychange', refreshVisible)
    removeRouteHook()
  }
}
