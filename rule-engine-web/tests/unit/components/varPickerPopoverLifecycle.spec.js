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
  test('三层虚拟锚点选择器保留祖先，点击最深层与其下拉菜单不关闭父面板', async () => {
    const panels = Array.from({ length: 3 }, () => document.body.appendChild(document.createElement('div')))
    const anchors = panels.map(panel => panel.appendChild(document.createElement('button')))
    const wrappers = panels.map((panel, index) => {
      const wrapper = shallowMount(VarPicker, { props: { operandMode: true, virtualRef: index ? anchors[index - 1] : null },
        stubs: { 'el-popover': PopoverVisibilityStub } })
      wrapper.vm.getPopoverElement = () => panel
      return wrapper
    })
    try {
      for (const wrapper of wrappers) { wrapper.vm.openPopover(); await nextTick() }
      expect(wrappers.map(wrapper => wrapper.vm.popoverVisible)).toEqual([true, true, true])
      const dropdown = document.body.appendChild(document.createElement('div'))
      dropdown.id = 'nested-picker-dropdown'
      anchors[2].setAttribute('aria-controls', dropdown.id)
      for (const wrapper of wrappers) wrapper.vm.onDocumentPointerDown({ target: dropdown })
      expect(wrappers.map(wrapper => wrapper.vm.popoverVisible)).toEqual([true, true, true])
      wrappers[2].vm.closePopover()
      await nextTick()
      expect(wrappers.map(wrapper => wrapper.vm.popoverVisible)).toEqual([true, true, false])
      wrappers[0].vm.onDocumentPointerDown({ target: document.body })
      await nextTick()
      expect(wrappers.map(wrapper => wrapper.vm.popoverVisible)).toEqual([false, false, false])
      dropdown.remove()
    } finally { wrappers.forEach(wrapper => wrapper.unmount()); panels.forEach(panel => panel.remove()) }
  })
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
