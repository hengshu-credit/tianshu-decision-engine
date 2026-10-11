import { shallowMount } from '@test-utils'
import CanvasBackgroundSettings from '@/components/flow/CanvasBackgroundSettings.vue'

test('默认背景可实时调整透明度，包含完全透明的零值', async () => {
  const wrapper = shallowMount(CanvasBackgroundSettings, {
    global: {
      stubs: {
        'el-popover': { template: '<div><slot /><slot name="reference" /></div>' },
        'el-slider': {
          name: 'ElSlider', props: ['disabled', 'modelValue'],
          emits: ['update:modelValue'], template: '<div />',
        },
      },
    },
  })
  try {
    const slider = wrapper.findComponent({ name: 'ElSlider' })
    expect(slider.props('disabled')).toBeFalsy()
    slider.vm.$emit('update:modelValue', 0.35)
    expect(wrapper.emitted('change').at(-1)).toEqual([{ backgroundOpacity: 0.35 }])
    slider.vm.$emit('update:modelValue', 0)
    expect(wrapper.emitted('change').at(-1)).toEqual([{ backgroundOpacity: 0 }])
  } finally { wrapper.unmount() }
})
