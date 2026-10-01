var currentUser = null
var permissionEnforcementEnabled = false

export function setPermissionEnforcement(enabled) {
  permissionEnforcementEnabled = Boolean(enabled)
}

export function setCurrentUser(user) {
  if (!user) {
    currentUser = null
    return
  }
  currentUser = {
    ...user,
    roleCodes: Array.isArray(user.roleCodes) ? user.roleCodes.slice() : [],
    permissions: Array.isArray(user.permissions)
      ? Array.from(new Set(user.permissions))
      : []
  }
}

export function clearCurrentUser() {
  currentUser = null
}

export function getCurrentUser() {
  if (!currentUser) return null
  return {
    ...currentUser,
    roleCodes: currentUser.roleCodes.slice(),
    permissions: currentUser.permissions.slice()
  }
}

export function hasPermission(permissionCode) {
  if (!permissionCode) return true
  if (!currentUser) return !permissionEnforcementEnabled
  return currentUser.permissions.includes(permissionCode)
}

export function filterMenus(menus) {
  const source = Array.isArray(menus) ? menus : []
  return source.filter(menu =>
    !menu.permission || hasPermission(menu.permission)
  )
}
