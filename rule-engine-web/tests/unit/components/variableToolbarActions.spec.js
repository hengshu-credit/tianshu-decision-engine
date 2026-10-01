import { mount } from '@test-utils'
import permissionDirective from '@/directives/permission'
import {
  clearCurrentUser,
  setCurrentUser,
  setPermissionEnforcement,
} from '@/security/permissionState'
import VariableToolbarActions from '@/views/variable/components/VariableToolbarActions.vue'

describe('变量管理工具栏权限', () => {
  afterEach(() => {
    clearCurrentUser()
    setPermissionEnforcement(false)
  })

  test('仅有查看权限时隐藏批量导入、新建和验证操作', () => {
    setPermissionEnforcement(true)
    setCurrentUser({ permissions: ['field:view'] })
    const wrapper = mount(VariableToolbarActions, {
      props: { primaryCreateLabel: '新建字段' },
      global: { directives: { permission: permissionDirective } },
    })

    expect(wrapper.find('el-dropdown-stub').element.hidden).toBe(true)
    const buttons = wrapper.findAll('el-button-stub')
    expect(buttons.find(button => button.text().includes('新建字段')).element.hidden).toBe(true)
    expect(buttons.find(button => button.text().includes('验证规则')).element.hidden).toBe(true)
  })

  test('批量导入使用审批提交权限，与字段编辑权限分离', () => {
    setPermissionEnforcement(true)
    setCurrentUser({ permissions: ['field:view', 'field:edit'] })
    const editWrapper = mount(VariableToolbarActions, {
      props: { primaryCreateLabel: '新建字段' },
      global: { directives: { permission: permissionDirective } },
    })

    expect(editWrapper.find('el-dropdown-stub').element.hidden).toBe(true)
    expect(editWrapper.findAll('el-button-stub')
      .find(button => button.text().includes('新建字段')).element.hidden).toBe(false)
    editWrapper.unmount()

    setCurrentUser({ permissions: ['field:view', 'approval:submit'] })
    const approvalWrapper = mount(VariableToolbarActions, {
      props: { primaryCreateLabel: '新建字段' },
      global: { directives: { permission: permissionDirective } },
    })

    expect(approvalWrapper.find('el-dropdown-stub').element.hidden).toBe(false)
    expect(approvalWrapper.findAll('el-button-stub')
      .find(button => button.text().includes('新建字段')).element.hidden).toBe(true)
  })
})
