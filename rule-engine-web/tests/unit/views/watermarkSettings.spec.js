import { shallowMount } from '@test-utils'
import WatermarkSettings from '@/views/system/WatermarkSettings.vue'
import { DEFAULT_WATERMARK_SETTINGS } from '@/system/watermark'

describe('水印设置', () => {
  test('追加字段保留原内容和元数据，编辑只产生表单变更而不修改生效对象', async () => {
    const original = { ...DEFAULT_WATERMARK_SETTINGS, id: 1, configVersion: 3, systemName: '引擎', watermarkContent: '内部资料\n' }
    const wrapper = shallowMount(WatermarkSettings, { propsData: { modelValue: original } })
    expect(wrapper.vm.tokenContent('用户名')).toBe('内部资料\n{用户名}')
    await wrapper.findAll('el-button-stub').find(button => button.text() === '用户名').trigger('click')
    const changed = wrapper.emitted('update:modelValue')[0][0]
    expect(changed).toEqual({ ...original, watermarkContent: '内部资料\n{用户名}' })
    expect(original.watermarkContent).toBe('内部资料\n')
    await wrapper.setProps({ modelValue: changed })
    expect(wrapper.vm.form.watermarkContent).toBe('内部资料\n{用户名}')
    expect(wrapper.find('.watermark-preview').exists()).toBe(false)
    wrapper.unmount()
  })
  test('只读账户不能通过开关、内容或样式编辑修改配置', () => {
    const wrapper = shallowMount(WatermarkSettings, { propsData: { modelValue: DEFAULT_WATERMARK_SETTINGS, disabled: true } })
    for (const [field, value] of Object.entries({ watermarkEnabled: true, watermarkContent: '替换', watermarkTransparency: 30, watermarkLayout: 'HORIZONTAL', watermarkDensity: 'DENSE' })) wrapper.vm.update(field, value)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.find('el-form-stub').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
})
