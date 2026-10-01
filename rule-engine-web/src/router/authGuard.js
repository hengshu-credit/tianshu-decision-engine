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

function resolveRoutePermission(route) {
  var meta = route && route.meta ? route.meta : {}
  if (meta.permission) return meta.permission
  var menuIndex = meta.menu || getActiveMenuIndex(route && route.path)
  var menu = SIDEBAR_MENUS.find(item => item.index === menuIndex)
  return menu ? menu.permission : ''
}

function firstAccessibleRoute() {
  var menu = SIDEBAR_MENUS.find(item => hasPermission(item.permission))
  return menu ? menu.index : '/login'
}

export function createConsoleAuthGuard(loadConfig, loadCurrentUser) {
  return async function consoleAuthGuard(to, from, next) {
    var body = null
    try {
      body = await loadConfig()
    } catch (e) { /* fail closed below */ }
    var configLoaded = Boolean(body && body.code === 200 && body.data)
    var loginEnabled = configLoaded && body.data.loginEnabled === true
    if (to.path === '/login') {
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
      if (me && me.code === 200 && me.data && me.data.username) {
        setCurrentUser({
          ...me.data,
          permissions: Array.isArray(me.data.permissions)
            ? me.data.permissions
            : []
        })
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
