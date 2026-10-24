import { canViewPickerItem, recordPickerActivity } from '@/security/pickerAccess'
import { recordConsoleActivity } from '@/security/operationActivity'
import { clearCurrentUser, setCurrentUser } from '@/security/permissionState'
vi.mock('@/security/operationActivity', () => ({ recordConsoleActivity: vi.fn() }))

describe('内容选择器资源权限', () => {
  test('字段访问只呈报身份和选定版本，不传入字段配置', () => {
    setCurrentUser({ permissions: ['model:view'] })
    recordPickerActivity('FIELD_VIEW', { _refType: 'MODEL', _varId: '3', version: 2, varLabel: '模型字段', sourceConfig: 'PRIVATE_CONFIG' }, '/rule/1')
    expect(recordConsoleActivity).toHaveBeenLastCalledWith({ action: 'FIELD_VIEW', pagePath: '/rule/1', resourceType: 'MODEL', resourceId: '3', name: '模型字段', version: 'v2' })
  })
  afterEach(clearCurrentUser)
  test('模型与名单目录不能借助变量查看权限显示', () => {
    setCurrentUser({ permissions: ['field:view'] })
    expect(canViewPickerItem({ _refType: 'VARIABLE', _varId: '1' })).toBe(true)
    expect(canViewPickerItem({ _refType: 'MODEL_OUTPUT', _varId: '2' })).toBe(false)
    expect(canViewPickerItem({ _refType: 'LIST_LIBRARY', _varId: '3' })).toBe(false)
  })
  test('内置方法与局部协议字段可用，持久化函数仍需函数查看权限', () => {
    setCurrentUser({ permissions: ['rule:view'] })
    expect(canViewPickerItem({ _refType: 'API_CONTEXT' })).toBe(true)
    expect(canViewPickerItem({ _function: true, function: { funcName: 'sum' } })).toBe(true)
    expect(canViewPickerItem({ _function: true, function: { id: '3' } })).toBe(false)
  })
})
