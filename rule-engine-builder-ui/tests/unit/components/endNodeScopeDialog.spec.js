import { shallowMount } from '@vue/test-utils'
import EndNodeScopeDialog from '@/components/flow/EndNodeScopeDialog.vue'
import { END_SCOPE_CURRENT_RULE, END_SCOPE_ALL_RULES } from '@/utils/endNodeScope'

describe('EndNodeScopeDialog', () => {
  test('默认选择跳出当前规则，并在确认时返回用户选择', async () => {
    const wrapper = shallowMount(EndNodeScopeDialog, {
      propsData: { visible: true },
      stubs: {
        'el-dialog': true,
        'el-alert': true,
        'el-radio-group': true,
        'el-radio': true,
        'el-button': true
      }
    })

    expect(wrapper.vm.scope).toBe(END_SCOPE_CURRENT_RULE)
    wrapper.vm.scope = END_SCOPE_ALL_RULES
    wrapper.vm.confirm()

    expect(wrapper.emitted('confirm')[0]).toEqual([END_SCOPE_ALL_RULES])
    expect(wrapper.emitted('update:visible')[0]).toEqual([false])
  })

  test('每次打开都恢复为风险较低的当前规则范围', async () => {
    const wrapper = shallowMount(EndNodeScopeDialog, {
      propsData: { visible: false },
      stubs: {
        'el-dialog': true,
        'el-alert': true,
        'el-radio-group': true,
        'el-radio': true,
        'el-button': true
      }
    })
    wrapper.vm.scope = END_SCOPE_ALL_RULES

    await wrapper.setProps({ visible: true })

    expect(wrapper.vm.scope).toBe(END_SCOPE_CURRENT_RULE)
  })
})
