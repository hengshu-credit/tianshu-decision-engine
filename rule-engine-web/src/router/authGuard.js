import { PLATFORM_SUCCESS_CODE } from '@/utils/platformResponse'
import {
  clearCurrentUser,
  hasPermission,
  setPermissionEnforcement,
  setCurrentUser
} from '@/security/permissionState'
import {
  getActiveMenuIndex,
  SIDEBAR_MENUS
} from '@/layout/layoutState'
import { getCurrentUser } from '@/security/permissionState'

function resolveRoutePermission(route) {
  var meta = route && route.meta ? route.meta : {}
  if (meta.authOnly) return ''
  if (meta.permission) return meta.permission
  var menuIndex = meta.menu || getActiveMenuIndex(route && route.path)
  var menu = SIDEBAR_MENUS.find(item => item.index === menuIndex)
  return menu ? menu.permission : ''
}

function firstAccessibleRoute() {
  var user = getCurrentUser()
  if (user && Array.isArray(user.permissions) && user.permissions.length === 0) {
    return '/no-permission'
  }
  var menu = SIDEBAR_MENUS.find(item => hasPermission(item.permission))
  return menu ? menu.index : '/no-permission'
}

export const PUBLIC_AUTH_PATHS = new Set([
  '/login',
  '/register',
  '/forgot-password',
  '/password-reset',
  '/admin-recovery'
])

function sessionIdentity(user) {
  return user ? JSON.stringify([user.userId, user.username, user.permissionVersion]) : null
}

function accessRedirect(to) {
  if (getCurrentUser()?.permissions.length === 0 && to.path !== '/no-permission' && !to.meta?.authOnly) {
    return { path: '/no-permission', replace: true }
  }
  const permission = resolveRoutePermission(to)
  if (permission && !hasPermission(permission)) return { path: firstAccessibleRoute(), replace: true }
}

export function createConsoleAuthGuard(loadConfig, loadCurrentUser, isCurrentNavigation = () => true, { onRedirect } = {}) {
  let generation = 0
  let verifiedIdentity = null
  async function validate(to, from, next, background = false) {
    const requestGeneration = ++generation
    const isCurrent = () => requestGeneration === generation && isCurrentNavigation(to)
    var body = null
    try {
      body = await loadConfig()
    } catch (e) { /* fail closed below */ }
    if (!isCurrent()) return next(false)
    var configLoaded = Boolean(body && body.code === PLATFORM_SUCCESS_CODE && body.data)
    var loginEnabled = configLoaded && body.data.loginEnabled === true
    if (loginEnabled && body.data.initializationRequired === true && to.path !== '/register') {
      setPermissionEnforcement(true)
      clearCurrentUser()
      return next({ path: '/register', replace: true })
    }
    if (PUBLIC_AUTH_PATHS.has(to.path)) {
      verifiedIdentity = null
      setPermissionEnforcement(!configLoaded || loginEnabled)
      if (configLoaded && !loginEnabled) {
        // 登录已禁用，直接跳转（使用 Vue Router 而非 location 避免页面刷新）
        return next({ path: to.query.redirect || '/dashboard', replace: true })
      }
      return next()
    }
    if (configLoaded && !loginEnabled) {
      setPermissionEnforcement(false)
      clearCurrentUser()
      return next()
    }
    setPermissionEnforcement(true)
    if (!configLoaded) {
      clearCurrentUser()
      return next({
        path: '/login',
        query: { redirect: to.fullPath },
        replace: true
      })
    }
    try {
      var me = await loadCurrentUser()
      if (!isCurrent()) return next(false)
      if (me && me.code === PLATFORM_SUCCESS_CODE && me.data && me.data.username) {
        // 身份或权限版本变更必须重新登录，不能让已缓存页面跨身份复用。
        if (background && sessionIdentity(me.data) !== verifiedIdentity) {
          clearCurrentUser()
          verifiedIdentity = null
          return next({ path: '/login', query: { redirect: to.fullPath }, replace: true })
        }
        setCurrentUser({
          ...me.data,
          permissions: Array.isArray(me.data.permissions)
            ? me.data.permissions
            : []
        })
        verifiedIdentity = sessionIdentity(getCurrentUser())
        const denied = accessRedirect(to)
        return denied ? next(denied) : next()
      }
    } catch (e) { /* ignore */ }
    if (!isCurrent()) return next(false)
    clearCurrentUser()
    return next({ path: '/login', query: { redirect: to.fullPath }, replace: true })
  }

  return function consoleAuthGuard(to, from, next) {
    const currentIdentity = sessionIdentity(getCurrentUser())
    if (onRedirect && currentIdentity && currentIdentity === verifiedIdentity &&
        !PUBLIC_AUTH_PATHS.has(to.path) && !PUBLIC_AUTH_PATHS.has(from.path)) {
      const denied = accessRedirect(to)
      if (denied) return next(denied)
      // 页面结构立即显示；会话继续复核，业务请求仍由服务端逐次鉴权。
      const validation = validate(to, from, target => {
        if (target && target !== false) onRedirect(target)
      }, true)
      next()
      return validation
    }
    return validate(to, from, next)
  }
}
