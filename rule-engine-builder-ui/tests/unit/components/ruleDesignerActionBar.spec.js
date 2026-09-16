import { mount } from '@test-utils'
import RuleDesignerActionBar from '@/components/rule/RuleDesignerActionBar.vue'

describe('RuleDesignerActionBar', () => {
  function mountWithReport() {
    return mount(RuleDesignerActionBar, {
      props: { canEdit: true, canTest: true },
      stubs: {
        'el-dialog': {
          props: ['modelValue'],
          template: '<div v-if="modelValue" role="dialog"><slot /><slot name="footer" /></div>'
        }
      }
    })
  }

  test('编译阻断项通过独立弹窗展示，定位后关闭且不占用操作栏', async () => {
    const wrapper = mountWithReport()
    const issue = { code: 'COMPILE_FAILED', message: '表达式缺少右括号', path: '$.script' }
    await wrapper.setProps({ report: { valid: false, errors: [issue], warnings: [] } })
    expect(wrapper.find('.rule-designer-actions .validation-report').exists()).toBe(false)
    expect(wrapper.get('[role="dialog"]').text()).toContain('表达式缺少右括号')
    await wrapper.get('[data-action="locate-issue"]').trigger('click')
    expect(wrapper.emitted('locate')).toEqual([[issue]])
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    wrapper.unmount()
  })

  test('成功不展开报告，提醒可关闭，再次编译的报告仍可弹出', async () => {
    const wrapper = mountWithReport()
    await wrapper.setProps({ report: { valid: true, errors: [], warnings: [] } })
    expect(wrapper.find('.validation-report').exists()).toBe(false)
    const warning = { valid: true, warnings: [{ message: '依赖版本已更新' }] }
    await wrapper.setProps({ report: warning })
    expect(wrapper.get('[role="dialog"]').text()).toContain('依赖版本已更新')
    await wrapper.get('[data-action="close-report"]').trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    await wrapper.setProps({ report: { ...warning } })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    wrapper.unmount()
  })

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
