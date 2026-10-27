import { SIDEBAR_MENUS } from '@/layout/layoutState'
import { hasPermission, hasUnrestrictedPermissions } from '@/security/permissionState'

const ACTION_ROUTES = {
  MANAGE_PROJECT: '/project',
  CONFIGURE_FIELDS: '/variable',
  CONFIGURE_SOURCES: '/datasource',
  CONFIGURE_EXTERNAL_SOURCES: '/datasource',
  CONFIGURE_DATABASE_SOURCES: '/database',
  CONFIGURE_SFTP_SOURCES: '/sftp',
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
  if (actionCode === 'RETRY_WARMUP') return hasUnrestrictedPermissions('rule:view', 'rule:submit')
  const menu = SIDEBAR_MENUS.find(item => item.index === workbenchActionRoute(actionCode))
  return Boolean(menu && hasPermission(menu.permission))
}

export function workbenchActionPermissionHint(actionCode) {
  return actionCode === 'RETRY_WARMUP'
    ? '重新验证影响当前节点所有项目，需要全局及全部项目的规则查看、提交权限'
    : '当前账号没有此模块的访问权限'
}
