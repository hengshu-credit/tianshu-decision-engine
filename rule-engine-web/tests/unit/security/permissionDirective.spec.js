import {
  applyPermissionVisibility
} from '@/directives/permission'
import {
  clearCurrentUser,
  setPermissionEnforcement,
  setCurrentUser
} from '@/security/permissionState'

describe('操作按钮权限指令', () => {
  afterEach(() => {
    clearCurrentUser()
    setPermissionEnforcement(false)
  })

  test('鉴权启用但账户尚未加载时隐藏受控按钮', () => {
    setPermissionEnforcement(true)
    const button = document.createElement('button')

    applyPermissionVisibility(button, 'rule:edit')

    expect(button.hidden).toBe(true)
  })

  test('没有最终权限时隐藏操作按钮', () => {
    setCurrentUser({ permissions: ['approval:view'] })
    const button = document.createElement('button')

    applyPermissionVisibility(button, 'approval:approve')

    expect(button.hidden).toBe(true)
    expect(button.getAttribute('aria-hidden')).toBe('true')
  })

  test('拥有最终权限时展示操作按钮', () => {
    setCurrentUser({ permissions: ['approval:approve'] })
    const button = document.createElement('button')
    button.hidden = true

    applyPermissionVisibility(button, 'approval:approve')

    expect(button.hidden).toBe(false)
    expect(button.hasAttribute('aria-hidden')).toBe(false)
  })
})
