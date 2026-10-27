import {
  clearCurrentUser,
  filterMenus,
  getCurrentUser,
  hasPermission,
  hasUnrestrictedPermissions,
  setPermissionEnforcement,
  setCurrentUser
} from '@/security/permissionState'

describe('控制台最终权限状态', () => {
  test('节点预热要求默认全局及所有项目权限，项目拒绝和全局只读不能绕过', () => {
    const user = { permissions: ['model:view', 'model:edit'], projectAccessMode: 'ALL', globalEditable: true }
    setCurrentUser(user)
    expect(hasUnrestrictedPermissions('model:view', 'model:edit')).toBe(true)
    for (const change of [{ projectAccessMode: 'SELECTED', projectIds: [1] }, { globalEditable: false }, { projectPermissionOverrides: { 2: { 'model:view': 'DENY' } } }]) {
      setCurrentUser({ ...user, ...change })
      expect(hasUnrestrictedPermissions('model:view', 'model:edit')).toBe(false)
    }
  })
  test('同一身份保留恢复内容，切换账号或权限版本时清除旧业务缓存', () => {
    setCurrentUser({ userId: 1, permissionVersion: 1, permissions: ['rule:view'] })
    window.sessionStorage.setItem('tianshu:rule-designer-recovery:9:1', 'private draft')
    window.sessionStorage.setItem('tianshu:layout:workspace-tabs', 'private title')
    setCurrentUser({ userId: 1, permissionVersion: 1, permissions: ['rule:view'] })
    expect(window.sessionStorage.getItem('tianshu:rule-designer-recovery:9:1')).toBe('private draft')
    setCurrentUser({ userId: 2, permissionVersion: 1, permissions: [] })
    expect(window.sessionStorage.getItem('tianshu:rule-designer-recovery:9:1')).toBeNull()
    expect(window.sessionStorage.getItem('tianshu:layout:workspace-tabs')).toBeNull()
    window.sessionStorage.setItem('tianshu:project-context', 'old scope')
    setCurrentUser({ userId: 2, permissionVersion: 2, permissions: [] })
    expect(window.sessionStorage.getItem('tianshu:project-context')).toBeNull()
  })
  test('默认模块权限可被单个项目覆盖，不改变全局权限', () => {
    setCurrentUser({ permissions: ['field:view', 'field:edit'], defaultPermissions: ['field:view', 'field:edit'],
      projectPermissionOverrides: { 2: { 'field:edit': 'DENY' } } })
    expect(hasPermission('field:edit', { projectId: 1, scope: 'PROJECT' })).toBe(true)
    expect(hasPermission('field:edit', { projectId: 2, scope: 'PROJECT' })).toBe(false)
    expect(hasPermission('field:edit', { scope: 'GLOBAL' })).toBe(true)
  })

  test('全局只读和指定项目限制分别生效，项目允许不授予全局权限', () => {
    setCurrentUser({ permissions: ['field:view', 'field:edit'], defaultPermissions: [], projectAccessMode: 'SELECTED', projectIds: [1], globalEditable: false,
      projectPermissionOverrides: { 1: { 'field:view': 'ALLOW', 'field:edit': 'ALLOW' }, 2: { 'field:view': 'ALLOW' } } })
    expect(hasPermission({ code: 'field:edit', projectId: 1, scope: 'PROJECT' })).toBe(true)
    expect(hasPermission('field:view', { projectId: 2 })).toBe(false)
    expect(hasPermission('field:view', { scope: 'GLOBAL' })).toBe(false)
    expect(hasPermission('field:edit', { scope: 'GLOBAL' })).toBe(false)
  })
  afterEach(() => {
    clearCurrentUser()
    setPermissionEnforcement(false)
  })

  test('仅保留当前用户拥有访问权限的菜单', () => {
    setCurrentUser({
      userId: 9,
      username: 'reviewer',
      permissions: ['approval:view', 'account:view']
    })
    const menus = [
      { index: '/approval', permission: 'approval:view' },
      { index: '/account', permission: 'account:view' },
      { index: '/rule', permission: 'rule:view' }
    ]

    expect(filterMenus(menus).map(item => item.index))
      .toEqual(['/approval', '/account'])
    expect(hasPermission('approval:view')).toBe(true)
    expect(hasPermission('approval:approve')).toBe(false)
  })

  test('未加载账户时保持兼容，不隐藏原有菜单', () => {
    const menus = [
      { index: '/project', permission: 'project:view' },
      { index: '/rule', permission: 'rule:view' }
    ]

    expect(filterMenus(menus)).toEqual(menus)
  })

  test('登录鉴权启用后未加载账户时默认拒绝权限与菜单', () => {
    setPermissionEnforcement(true)
    const menus = [
      { index: '/project', permission: 'project:view' },
      { index: '/login' }
    ]

    expect(hasPermission('project:view')).toBe(false)
    expect(filterMenus(menus)).toEqual([{ index: '/login' }])
  })

  test('状态保存不可变副本并可清空', () => {
    const source = { username: 'admin', permissions: ['account:manage'] }
    setCurrentUser(source)
    source.permissions.push('role:manage')

    expect(getCurrentUser().permissions).toEqual(['account:manage'])
    clearCurrentUser()
    expect(getCurrentUser()).toBeNull()
  })
})
