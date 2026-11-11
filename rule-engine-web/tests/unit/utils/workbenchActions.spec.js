import { canUseWorkbenchAction, workbenchActionRoute } from '@/utils/workbenchActions'
import { setCurrentUser, clearCurrentUser, setPermissionEnforcement } from '@/security/permissionState'

afterEach(() => { clearCurrentUser(); setPermissionEnforcement(false) })

test('工作台按钮按所在项目的允许和拒绝检查', () => {
  setPermissionEnforcement(true)
  setCurrentUser({ permissions: ['project:view', 'log:view'], defaultPermissions: ['project:view'],
    projectPermissionOverrides: { 7: { 'log:view': 'ALLOW' }, 8: { 'log:view': 'DENY' } } })
  expect(canUseWorkbenchAction('VIEW_LOGS', 7)).toBe(true)
  expect(canUseWorkbenchAction('VIEW_LOGS', 8)).toBe(false)
})

test('工作台复用模块查看权限，刷新无需其他模块权限', () => {
  setPermissionEnforcement(true)
  setCurrentUser({ permissions: ['project:view', 'rule:view'] })
  expect(canUseWorkbenchAction('CONFIGURE_RULES')).toBe(true)
  expect(canUseWorkbenchAction('CONFIGURE_FIELDS')).toBe(false)
  expect(canUseWorkbenchAction('REVIEW_APPROVALS')).toBe(false)
  expect(canUseWorkbenchAction('REFRESH_WORKBENCH')).toBe(true)
  expect(workbenchActionRoute('CONFIGURE_DATABASE_SOURCES')).toBe('/database')
  expect(canUseWorkbenchAction('UNKNOWN')).toBe(false)
})
