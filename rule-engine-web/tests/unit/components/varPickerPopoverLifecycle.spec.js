import { nextTick } from 'vue'
import { shallowMount } from '@test-utils'

vi.unmock('@/components/common/VarPicker.vue')

const VarPicker = (await vi.importActual('@/components/common/VarPicker.vue')).default

const PopoverVisibilityStub = {
  props: ['visible'],
  emits: ['update:visible'],
  template: `
    <div>
      <div
        v-show="visible"
        class="popover-content"
        @mouseenter="$emit('update:visible', false)"
      ><slot /></div>
      <slot name="reference" />
    </div>
  `
}

describe('VarPicker 字段面板生命周期', () => {
  test('默认不创建隐藏字段面板，首次打开读取最新目录，关闭再打开保留同一面板和引用显示', async () => {
    const field = { varCode: 'age', varLabel: '年龄', varType: 'NUMBER', _varId: 1, _refType: 'VARIABLE', _ref: { category: 'standalone' } }
    const wrapper = shallowMount(VarPicker, {
      props: { value: 'age', vars: [field] },
      stubs: { 'el-popover': PopoverVisibilityStub },
    })
    expect(wrapper.findComponent({ name: 'FieldPickerPanel' }).exists()).toBe(false)
    await wrapper.setProps({ vars: [{ ...field, varLabel: '最新年龄' }] })
    expect(wrapper.vm.referenceInputValue).toContain('最新年龄')
    expect(wrapper.findComponent({ name: 'FieldPickerPanel' }).exists()).toBe(false)
    wrapper.vm.openPopover()
    await nextTick()
    const panel = wrapper.findComponent({ name: 'FieldPickerPanel' })
    expect(panel.exists()).toBe(true)
    expect(panel.props('items')[0].varLabel).toBe('最新年龄')
    wrapper.vm.closePopover()
    await nextTick()
    expect(wrapper.findComponent({ name: 'FieldPickerPanel' }).vm).toBe(panel.vm)
    expect(panel.props('visible')).toBe(false)
    expect(wrapper.vm.referenceInputValue).toContain('最新年龄')
    wrapper.vm.openPopover()
    await nextTick()
    expect(wrapper.findComponent({ name: 'FieldPickerPanel' }).vm).toBe(panel.vm)
    expect(wrapper.emitted('input')).toBeUndefined()
    wrapper.unmount()
  })

  test('鼠标移入候选面板时不接受弹层内部的误关闭回写', async () => {
    const wrapper = shallowMount(VarPicker, {
      props: {
        operandMode: true,
        vars: [{
          varCode: 'hit_ruleset',
          varLabel: '命中规则集',
          varType: 'LIST',
          _varId: 1,
          _refType: 'VARIABLE',
          _ref: { category: 'standalone' }
        }]
      },
      stubs: {
        'el-popover': PopoverVisibilityStub
      }
    })

    wrapper.vm.openPopover()
    await nextTick()
    expect(wrapper.vm.popoverVisible).toBe(true)

    await wrapper.get('.popover-content').trigger('mouseenter')

    expect(wrapper.vm.popoverVisible).toBe(true)
    wrapper.unmount()
  })
})
