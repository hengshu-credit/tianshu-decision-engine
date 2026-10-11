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

  test('无问题的编译报告不打断操作，非阻断提醒直接展示', async () => {
    const wrapper = mountWithReport()
    await wrapper.setProps({ report: { valid: true, errors: [], warnings: [] } })
    expect(wrapper.find('.validation-report').exists()).toBe(false)
    const warning = { valid: true, warnings: [{ message: '依赖版本已更新' }] }
    await wrapper.setProps({ report: warning })
    expect(wrapper.get('[role="dialog"]').text()).toContain('依赖版本已更新')
    await wrapper.get('[data-action="close-report"]').trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    await wrapper.setProps({ report: null })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    wrapper.unmount()
  })

  test('未保存配置仍可独立编译与测试', async () => {
    const wrapper = mount(RuleDesignerActionBar, { props: { canEdit: true, state: 'DIRTY', canTest: true } })
    expect(wrapper.findAll('.rule-designer-actions__buttons [data-action]').map(button => button.attributes('data-action'))).toEqual(['check', 'compile', 'save', 'publish', 'test'])
    expect(wrapper.get('[data-action="test"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-action="compile"]').trigger('click')
    expect(wrapper.emitted('compile')).toHaveLength(1)
    expect(wrapper.emitted('save')).toBeUndefined()
  })

  test('保存返回诊断时直接展示报告，避免移除入口后遗漏问题', async () => {
    const wrapper = mountWithReport()
    await wrapper.setProps({ report: { valid: false, autoOpen: false, errors: [{ message: '缺少结束节点' }], warnings: [] } })
    expect(wrapper.get('[role="dialog"]').text()).toContain('缺少结束节点')
    wrapper.unmount()
  })

  test('检查始终触发新校验，收到报告后直接展示并支持清除', async () => {
    const wrapper = mountWithReport()
    const entry = wrapper.get('[data-action="check"]')
    expect(entry.text()).toBe('检查')
    expect(wrapper.find('[data-action="show-report"]').exists()).toBe(false)
    expect(entry.attributes('disabled')).toBeUndefined()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    await entry.trigger('click')
    expect(wrapper.emitted('check')).toHaveLength(1)
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    await wrapper.setProps({ report: { valid: true, errors: [], warnings: [], autoOpen: true } })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    await wrapper.setProps({ report: null })
    expect(entry.attributes('disabled')).toBeUndefined()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    wrapper.unmount()
  })

  test('移除报告按钮后，保存和编译的错误或提醒仍直接展示', async () => {
    const wrapper = mountWithReport()
    expect(wrapper.find('[data-action="show-report"]').exists()).toBe(false)
    for (const report of [
      { valid: false, autoOpen: false, errors: [{ message: '缺少字段' }] },
      { valid: true, warnings: [{ message: '依赖版本已更新' }] },
    ]) {
      await wrapper.setProps({ report })
      expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
      await wrapper.get('[data-action="close-report"]').trigger('click')
    }
    await wrapper.setProps({ report: { valid: true, errors: [], warnings: [] } })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    wrapper.unmount()
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
