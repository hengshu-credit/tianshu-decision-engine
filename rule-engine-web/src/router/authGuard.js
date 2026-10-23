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

const PUBLIC_AUTH_PATHS = new Set([
  '/login',
  '/register',
  '/forgot-password',
  '/password-reset',
  '/admin-recovery'
])

export function createConsoleAuthGuard(loadConfig, loadCurrentUser) {
  return async function consoleAuthGuard(to, from, next) {
    var body = null
    try {
      body = await loadConfig()
    } catch (e) { /* fail closed below */ }
    var configLoaded = Boolean(body && body.code === PLATFORM_SUCCESS_CODE && body.data)
    var loginEnabled = configLoaded && body.data.loginEnabled === true
    if (loginEnabled && body.data.initializationRequired === true && to.path !== '/register') {
      setPermissionEnforcement(true)
      clearCurrentUser()
      return next({ path: '/register', replace: true })
    }
    if (PUBLIC_AUTH_PATHS.has(to.path)) {
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
      if (me && me.code === PLATFORM_SUCCESS_CODE && me.data && me.data.username) {
        setCurrentUser({
          ...me.data,
          permissions: Array.isArray(me.data.permissions)
            ? me.data.permissions
            : []
        })
        if (getCurrentUser().permissions.length === 0
            && to.path !== '/no-permission'
            && !(to.meta && to.meta.authOnly)) {
          return next({ path: '/no-permission', replace: true })
        }
        var requiredPermission = resolveRoutePermission(to)
        if (requiredPermission && !hasPermission(requiredPermission)) {
          return next({ path: firstAccessibleRoute(), replace: true })
        }
        return next()
      }
    } catch (e) { /* ignore */ }
    clearCurrentUser()
    return next({ path: '/login', query: { redirect: to.fullPath }, replace: true })
  }
}
