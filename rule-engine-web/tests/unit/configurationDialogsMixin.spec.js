import { mount } from '@vue/test-utils'
import configurationDialogsMixin from '@/mixins/configurationDialogsMixin'
import { clearDesignerLeaveGuards, confirmDesignerPathsCanClose } from '@/utils/designerLeaveGuard'

afterEach(clearDesignerLeaveGuards)
test('关闭第二个弹窗不会把第一个弹窗的未保存输入标为已保存', async () => {
  const confirm = vi.fn().mockResolvedValue(true)
  const view = mount({ template: '<div/>', mixins: [configurationDialogsMixin([
    { visible: 'first', draft: vm => vm.a, busy: () => false }, { visible: 'second', draft: vm => vm.b, busy: () => false },
  ])], data: () => ({ first: false, second: false, a: { name: '' }, b: { name: '' } }) }, { global: { mocks: { $route: { path: '/nested' }, $confirm: confirm } } })
  view.vm.first = true; view.vm.a.name = '第一份修改'
  view.vm.second = true
  await view.vm.closeConfigurationDialog('second')
  expect(confirm).not.toHaveBeenCalled()
  expect(view.vm.configurationHasUnsavedChanges).toBe(true)
  await view.vm.closeConfigurationDialog('first')
  expect(confirm).toHaveBeenCalledTimes(1)
  view.unmount()
})
test('弹窗打开建立基线，取消离开保留输入，保存中阻止工作区关闭', async () => {
  const confirm = vi.fn().mockRejectedValue('cancel')
  const view = mount({ template: '<div/>', mixins: [configurationDialogsMixin([{ visible: 'open', draft: vm => vm.form, busy: vm => vm.saving }])], data: () => ({ open: false, form: {}, saving: false }) }, { global: { mocks: { $route: { path: '/example' }, $confirm: confirm, $message: { info: vi.fn() } } } })
  view.vm.form = { name: '加载值' }; view.vm.open = true; await view.vm.$nextTick()
  expect(view.vm.configurationHasUnsavedChanges).toBe(false)
  view.vm.form.name = '新输入'
  expect(await confirmDesignerPathsCanClose(['/example'])).toBe(false)
  expect(view.vm.form.name).toBe('新输入')
  view.vm.saving = true
  expect(await view.vm.confirmConfigurationLeave()).toBe(false)
  expect(confirm).toHaveBeenCalledTimes(1)
  view.vm.saving = false; confirm.mockResolvedValue(true)
  await view.vm.closeConfigurationDialog('open')
  expect(view.vm.open).toBe(false)
  expect(view.vm.configurationHasUnsavedChanges).toBe(false)
  view.unmount()
})
