import { mount } from '@test-utils'
import RuleDesignerActionBar from '@/components/rule/RuleDesignerActionBar.vue'

describe('RuleDesignerActionBar', () => {
  test('未保存配置仍可独立编译与测试', async () => {
    const wrapper = mount(RuleDesignerActionBar, { props: { canEdit: true, state: 'DIRTY', canTest: true } })
    expect(wrapper.findAll('[data-action]').map(button => button.attributes('data-action'))).toEqual(['compile', 'save', 'publish', 'test'])
    expect(wrapper.get('[data-action="test"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-action="compile"]').trigger('click')
    expect(wrapper.emitted('compile')).toHaveLength(1)
    expect(wrapper.emitted('save')).toBeUndefined()
  })

  test('进行中禁止重复操作，无执行权限时测试禁用', async () => {
    const wrapper = mount(RuleDesignerActionBar, { props: { canEdit: true, busy: true, canTest: false } })
    expect(wrapper.findAll('button').every(button => button.attributes('disabled') !== undefined)).toBe(true)
    await wrapper.setProps({ busy: false })
    expect(wrapper.get('[data-action="test"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-action="publish"]').trigger('click')
    expect(wrapper.emitted('publish')).toHaveLength(1)
  })
})
