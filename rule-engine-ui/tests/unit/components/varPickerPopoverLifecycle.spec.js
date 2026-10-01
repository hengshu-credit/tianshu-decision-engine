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
