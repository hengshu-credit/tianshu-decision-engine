import { SIDEBAR_MENUS } from '@/layout/layoutState'
import { hasPermission } from '@/security/permissionState'

const ACTION_ROUTES = {
  MANAGE_PROJECT: '/project',
  CONFIGURE_FIELDS: '/variable',
  CONFIGURE_SOURCES: '/datasource',
  CONFIGURE_EXTERNAL_SOURCES: '/datasource',
  CONFIGURE_DATABASE_SOURCES: '/database',
  CONFIGURE_MODELS: '/model',
  CONFIGURE_RULES: '/rule',
  TEST_RULES: '/test',
  REVIEW_APPROVALS: '/approval',
  VIEW_LOGS: '/log',
}

export function workbenchActionRoute(actionCode) {
  return ACTION_ROUTES[actionCode] || null
}

export function canUseWorkbenchAction(actionCode) {
  if (actionCode === 'REFRESH_WORKBENCH') return hasPermission('project:view')
  const menu = SIDEBAR_MENUS.find(item => item.index === workbenchActionRoute(actionCode))
  return Boolean(menu && hasPermission(menu.permission))
}
