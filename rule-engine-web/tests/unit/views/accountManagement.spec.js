vi.mock('@/api/consoleAccount', () => ({
  listConsoleAccounts: vi.fn().mockResolvedValue({
    data: [{
      id: 9,
      username: 'reviewer',
      displayName: '审批员',
      status: 1,
      roleIds: [2],
      roleCodes: ['REVIEWER'],
      inheritedPermissions: ['approval:view', 'approval:approve'],
      permissionOverrides: { 'approval:approve': 'DENY' },
      effectivePermissions: ['approval:view']
    }]
  }),
  listConsoleRoles: vi.fn().mockResolvedValue({
    data: [{
      id: 2,
      roleCode: 'REVIEWER',
      roleName: '审批员',
      status: 1,
      permissionCodes: ['approval:view', 'approval:approve'],
      memberCount: 1
    }]
  }),
  listConsolePermissions: vi.fn().mockResolvedValue({
    data: [
      {
        id: 1,
        permissionCode: 'approval:view',
        permissionName: '查看审批',
        permissionGroup: '审批管理',
        permissionType: 'MENU'
      },
      {
        id: 2,
        permissionCode: 'approval:approve',
        permissionName: '审批处理',
        permissionGroup: '审批管理',
        permissionType: 'ACTION'
      }
    ]
  }),
  saveConsoleAccount: vi.fn(),
  saveConsoleAccountPermissions: vi.fn(),
  changeConsoleAccountStatus: vi.fn(),
  resetConsoleAccountPassword: vi.fn(),
  saveConsoleRole: vi.fn(),
  changeConsoleRoleStatus: vi.fn()
}))

import { flushPromises, shallowMount } from '@test-utils'
import * as accountApi from '@/api/consoleAccount'
import AccountManagement from '@/views/account/AccountManagement.vue'
import { setCurrentUser, clearCurrentUser } from '@/security/permissionState'

function mountPage() {
  return shallowMount(AccountManagement, {
    global: {
      directives: {
        permission: {}
      }
    }
  })
}

describe('账户与角色管理页面', () => {
  afterEach(() => clearCurrentUser())

  test('只有账户查看权限时不请求无权查看的角色和权限目录', async() => {
    vi.clearAllMocks()
    setCurrentUser({ permissions: ['account:view'] })
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.vm.accounts[0].username).toBe('reviewer')
    expect(accountApi.listConsoleRoles).not.toHaveBeenCalled()
    expect(accountApi.listConsolePermissions).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('仅日志审计权限可进入日志标签，不读取账户和角色目录', async() => {
    vi.clearAllMocks()
    setCurrentUser({ permissions: ['account:audit'] })
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.vm.activeTab).toBe('logs')
    expect(wrapper.vm.canViewAccounts).toBe(false)
    expect(accountApi.listConsoleAccounts).not.toHaveBeenCalled()
    expect(accountApi.listConsoleRoles).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  test('进入页面同时加载账户、角色和权限目录', async() => {
    const wrapper = mountPage()
    await flushPromises()

    expect(accountApi.listConsoleAccounts).toHaveBeenCalled()
    expect(accountApi.listConsoleRoles).toHaveBeenCalled()
    expect(accountApi.listConsolePermissions).toHaveBeenCalled()
    expect(wrapper.vm.accounts[0].displayName).toBe('审批员')
    expect(wrapper.vm.roles[0].memberCount).toBe(1)
  })

  test('用户明确拒绝覆盖角色继承授权', async() => {
    const wrapper = mountPage()
    await flushPromises()
    wrapper.vm.openPermissionDialog(wrapper.vm.accounts[0])

    const approve = wrapper.vm.permissionRows.find(
      item => item.permissionCode === 'approval:approve'
    )
    const view = wrapper.vm.permissionRows.find(
      item => item.permissionCode === 'approval:view'
    )
    expect(approve.inherited).toBe(true)
    expect(approve.override).toBe('DENY')
    expect(approve.effective).toBe(false)
    expect(view.override).toBe('INHERIT')
    expect(view.effective).toBe(true)
  })

  test('页面提供账户和角色两个业务标签页', () => {
    const wrapper = mountPage()
    expect(wrapper.text()).toContain('账户')
    expect(wrapper.text()).toContain('角色')
  })

  test('编辑账户时用户名保持为稳定身份不可修改', async() => {
    const wrapper = mountPage()
    await flushPromises()

    wrapper.vm.openAccountDialog(wrapper.vm.accounts[0])
    await wrapper.vm.$nextTick()

    const input = wrapper.find('[data-testid="account-username-input"]')
    expect(input.exists()).toBe(true)
    expect(input.attributes('disabled')).toBeDefined()
  })
})
