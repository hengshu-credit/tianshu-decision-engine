import { shallowRef } from 'vue'

var currentUser = shallowRef(null)
var permissionEnforcementEnabled = shallowRef(false)

export function setPermissionEnforcement(enabled) {
  permissionEnforcementEnabled.value = Boolean(enabled)
}

export function setCurrentUser(user) {
  if (!user) {
    currentUser.value = null
    return
  }
  currentUser.value = {
    ...user,
    roleCodes: Array.isArray(user.roleCodes) ? user.roleCodes.slice() : [],
    permissions: Array.isArray(user.permissions)
      ? Array.from(new Set(user.permissions))
      : []
  }
}

export function clearCurrentUser() {
  currentUser.value = null
}

export function getCurrentUser() {
  if (!currentUser.value) return null
  return {
    ...currentUser.value,
    roleCodes: currentUser.value.roleCodes.slice(),
    permissions: currentUser.value.permissions.slice()
  }
}

export function hasPermission(permissionCode) {
  if (!permissionCode) return true
  if (Array.isArray(permissionCode)) return permissionCode.some(hasPermission)
  if (!currentUser.value) return !permissionEnforcementEnabled.value
  return currentUser.value.permissions.includes(permissionCode)
}

export function filterMenus(menus, options = {}) {
  const source = Array.isArray(menus) ? menus : []
  return source.filter(menu =>
    (!menu.permission
      && !(options.hidePublicWhenEmpty
        && currentUser.value
        && currentUser.value.permissions.length === 0))
      || hasPermission(menu.permission)
  )
}
