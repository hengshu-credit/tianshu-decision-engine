import { canUseWorkbenchAction, workbenchActionRoute } from '@/utils/workbenchActions'
import { setCurrentUser, clearCurrentUser, setPermissionEnforcement } from '@/security/permissionState'

afterEach(() => { clearCurrentUser(); setPermissionEnforcement(false) })

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
