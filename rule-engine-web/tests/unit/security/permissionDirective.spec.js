import {
  applyPermissionVisibility
} from '@/directives/permission'
import permissionDirective from '@/directives/permission'
import { nextTick } from 'vue'
import {
  clearCurrentUser,
  setPermissionEnforcement,
  setCurrentUser
} from '@/security/permissionState'

describe('操作按钮权限指令', () => {
  test('账户权限调整按钮接受独立授权，复合业务权限仍要求全部满足', () => {
    setCurrentUser({ permissions: ['account:role-assign', 'field:edit'] })
    const button = document.createElement('button')
    applyPermissionVisibility(button, { anyOf: ['account:manage', 'account:role-assign'] })
    expect(button.hidden).toBe(false)
    applyPermissionVisibility(button, ['field:edit', 'approval:submit'])
    expect(button.hidden).toBe(true)
  })
  test('其他渲染指令改写样式后仍保持无权限按钮隐藏', () => {
    setCurrentUser({ permissions: [] })
    const button = document.createElement('button')
    permissionDirective.mounted(button, { value: 'rule:edit' })
    button.style.display = 'inline-flex'
    permissionDirective.updated(button, { value: 'rule:edit' })
    expect(button.style.display).toBe('none')
    permissionDirective.unmounted(button)
  })
  test('会话权限撤回后立即隐藏已挂载按钮，样式优先级不会覆盖隐藏', async() => {
    setCurrentUser({ permissions: ['rule:edit'] })
    const button = document.createElement('button')
    button.style.display = 'inline-flex'
    permissionDirective.mounted(button, { value: 'rule:edit' })
    setCurrentUser({ permissions: [] })
    await nextTick()
    expect(button.style.display).toBe('none')
    expect(button.style.getPropertyPriority('display')).toBe('important')
    setCurrentUser({ permissions: ['rule:edit'] })
    await nextTick()
    expect(button.style.display).toBe('inline-flex')
    permissionDirective.unmounted(button)
  })
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
