import { shallowRef } from 'vue'

var currentUser = shallowRef(null)
var permissionEnforcementEnabled = shallowRef(false)
const IDENTITY_KEY = 'tianshu:security:identity'

function storedIdentity() {
  try { return window.sessionStorage.getItem(IDENTITY_KEY) } catch { return null }
}

function changeIdentity(identity) {
  try {
    const storage = window.sessionStorage
    const prefixes = ['tianshu:layout:workspace-tabs', 'tianshu:rule-designer-recovery:', 'tianshu:project-context', 'qlexpress.pageState.', 'tianshu:dashboard:']
    const keys = Array.from({ length: storage.length }, (_, index) => storage.key(index))
    keys.filter(key => key && prefixes.some(prefix => key.startsWith(prefix))).forEach(key => storage.removeItem(key))
    if (identity) storage.setItem(IDENTITY_KEY, identity)
    else storage.removeItem(IDENTITY_KEY)
  } catch { /* 禁用存储时仍清理当前页面中的业务状态。 */ }
  if (typeof window !== 'undefined') window.dispatchEvent(new Event('tianshu:identity-change'))
}

export function setPermissionEnforcement(enabled) {
  permissionEnforcementEnabled.value = Boolean(enabled)
}

export function setCurrentUser(user) {
  if (!user) {
    clearCurrentUser()
    return
  }
  const identity = `${user.userId ?? user.username ?? ''}:${user.permissionVersion ?? 0}`
  if (storedIdentity() !== identity) changeIdentity(identity)
  currentUser.value = {
    ...user,
    operatorNames: Array.isArray(user.operatorNames) ? Array.from(new Set(user.operatorNames)) : [user.operatorName || user.username].filter(Boolean),
    roleCodes: Array.isArray(user.roleCodes) ? user.roleCodes.slice() : [],
    permissions: Array.isArray(user.permissions)
      ? Array.from(new Set(user.permissions))
      : [],
    defaultPermissions: Array.isArray(user.defaultPermissions) ? user.defaultPermissions.slice() : (Array.isArray(user.permissions) ? user.permissions.slice() : []),
    projectIds: Array.isArray(user.projectIds) ? user.projectIds.slice() : [],
    projectAccessMode: user.projectAccessMode === 'SELECTED' ? 'SELECTED' : 'ALL',
    globalEditable: user.globalEditable !== false,
    projectPermissionOverrides: Object.fromEntries(Object.entries(user.projectPermissionOverrides || {}).map(([id, values]) => [id, { ...values }]))
  }
}

export function clearCurrentUser() {
  if (currentUser.value || storedIdentity()) changeIdentity(null)
  currentUser.value = null
}

export function getCurrentUser() {
  if (!currentUser.value) return null
  return {
    ...currentUser.value,
    operatorNames: currentUser.value.operatorNames.slice(),
    roleCodes: currentUser.value.roleCodes.slice(),
    permissions: currentUser.value.permissions.slice(),
    defaultPermissions: currentUser.value.defaultPermissions.slice(),
    projectIds: currentUser.value.projectIds.slice(),
    projectPermissionOverrides: Object.fromEntries(Object.entries(currentUser.value.projectPermissionOverrides).map(([id, values]) => [id, { ...values }]))
  }
}

export function hasPermission(permissionCode, resource) {
  if (permissionCode && typeof permissionCode === 'object' && !Array.isArray(permissionCode)) {
    if (Array.isArray(permissionCode.anyOf)) return permissionCode.anyOf.some(code => hasPermission(code, permissionCode))
    if (Array.isArray(permissionCode.allOf)) return permissionCode.allOf.every(code => hasPermission(code, permissionCode))
    return Boolean(permissionCode.code) && hasPermission(permissionCode.code, permissionCode)
  }
  if (!permissionCode) return true
  if (Array.isArray(permissionCode)) return permissionCode.some(code => hasPermission(code, resource))
  if (!currentUser.value) return !permissionEnforcementEnabled.value
  if (resource && typeof resource === 'object' && !permissionCode.startsWith('account:') && !permissionCode.startsWith('role:') && permissionCode !== 'function:native') {
    const user = currentUser.value
    const projectId = resource.scope === 'GLOBAL' ? null : resource.projectId
    if (!projectId || Number(projectId) <= 0) {
      const write = permissionCode.endsWith(':edit') || permissionCode.endsWith(':submit') || ['approval:approve', 'transfer:import'].includes(permissionCode)
      return user.defaultPermissions.includes(permissionCode) && (!write || user.globalEditable)
    }
    if (user.projectAccessMode === 'SELECTED' && !user.projectIds.some(id => String(id) === String(projectId))) return false
    const effect = user.projectPermissionOverrides[String(projectId)]?.[permissionCode]
    return effect === 'ALLOW' || (effect !== 'DENY' && user.defaultPermissions.includes(permissionCode))
  }
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
