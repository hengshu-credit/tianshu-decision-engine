import { mount } from '@test-utils'
import { defineComponent, h, ref } from 'vue'
import { ElForm } from 'element-plus/es/components/form/index.mjs'
import { ElInputNumber } from 'element-plus/es/components/input-number/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'

test('数字输入框在加载、编辑、回退及只读切换后同步实际值与无障碍状态', async () => {
  const disabled = ref(true)
  const value = ref(88)
  const wrapper = mount(defineComponent({
    setup: () => () => h(ElForm, { disabled: disabled.value }, () => h(ElInputNumber, {
      modelValue: value.value, min: 0, max: 100,
      'onUpdate:modelValue': next => { value.value = next },
    })),
  }), { stubs: { 'el-form': ElForm, 'el-input-number': ElInputNumber, 'el-input': ElInput } })
  try {
    const input = wrapper.get('input[role="spinbutton"]')
    expect(input.element.disabled).toBe(true)
    expect(input.attributes('aria-disabled')).toBe('true')
    disabled.value = false
    await wrapper.vm.$nextTick()
    expect(input.element.disabled).toBe(false)
    expect(input.attributes('aria-disabled')).toBe('false')
    await input.setValue('55')
    await input.trigger('blur')
    expect(value.value).toBe(55)
    expect(input.element.value).toBe('55')
    expect(input.attributes('aria-valuenow')).toBe('55')
    value.value = 0
    await wrapper.vm.$nextTick()
    expect(input.element.value).toBe('0')
    expect(input.attributes('aria-valuenow')).toBe('0')
    value.value = 88
    disabled.value = true
    await wrapper.vm.$nextTick()
    expect(input.element.value).toBe('88')
    expect(input.attributes('aria-valuenow')).toBe('88')
    expect(input.element.disabled).toBe(true)
    expect(input.attributes('aria-disabled')).toBe('true')
  } finally { wrapper.unmount() }
})
