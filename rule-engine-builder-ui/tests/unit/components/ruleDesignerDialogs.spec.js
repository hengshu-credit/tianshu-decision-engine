import { mount } from '@test-utils'
import RuleDesignerDialogs from '@/components/rule/RuleDesignerDialogs.vue'

test('覆盖已有草稿为默认选择，正式版本仍只能新建草稿', async () => {
  const wrapper = mount(RuleDesignerDialogs, { props: { choice: { kind: 'save', canOverwrite: true } } })
  expect(wrapper.vm.saveMode).toBe('OVERWRITE')
  await wrapper.setProps({ choice: { kind: 'save', canOverwrite: false } })
  expect(wrapper.vm.saveMode).toBe('NEW')
  wrapper.unmount()
})

test('发布提交成功不冒充已生效，支持留在设计器或查看审批', async () => {
  const wrapper = mount(RuleDesignerDialogs, {
    props: { choice: { kind: 'submitted', approvalRequestId: '19', canViewApproval: true } },
    stubs: { 'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' } },
  })
  expect(wrapper.text()).toContain('审批通过后才会生效')
  expect(wrapper.find('[data-action="confirm-save"]').exists()).toBe(false)
  await wrapper.get('[data-action="view-approval"]').trigger('click')
  expect(wrapper.emitted('resolve')).toEqual([[{ action: 'approval' }]])
  await wrapper.get('[data-action="stay-designer"]').trigger('click')
  expect(wrapper.emitted('resolve')[1]).toEqual([{ action: 'cancel' }])
  wrapper.unmount()
})
