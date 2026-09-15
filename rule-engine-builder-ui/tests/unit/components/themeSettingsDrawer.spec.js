import { mount } from '@test-utils'
import ThemeSettingsDrawer from '@/components/theme/ThemeSettingsDrawer.vue'
import { DEFAULT_THEME_CONFIG } from '@/theme/themeConfig'

const drawerStub = {
  name: 'ElDrawer',
  props: ['modelValue', 'beforeClose', 'lockScroll', 'modal'],
  template: `
    <section v-if="modelValue" class="drawer-stub">
      <slot />
      <slot name="footer" />
    </section>
  `,
}

function mountDrawer(overrides = {}) {
  return mount(ThemeSettingsDrawer, {
    props: {
      modelValue: true,
      config: { ...DEFAULT_THEME_CONFIG },
      saving: false,
      ...overrides,
    },
    stubs: {
      'el-drawer': drawerStub,
      'el-icon': { template: '<i><slot /></i>' },
      'el-switch': {
        props: ['modelValue', 'disabled'],
        template: '<button type="button" class="switch-stub" :disabled="disabled" />',
      },
      'el-color-picker': {
        props: ['modelValue'],
        emits: ['update:modelValue', 'change'],
        template: '<button type="button" class="color-picker-stub" />',
      },
      'el-input-number': {
        props: ['modelValue'],
        emits: ['update:modelValue', 'change'],
        template: '<input class="input-number-stub" />',
      },
    },
  })
}

describe('ThemeSettingsDrawer', () => {
  test('打开主题抽屉时不显示页面遮罩，便于直接观察实时主题变化', () => {
    const wrapper = mountDrawer()

    expect(wrapper.getComponent(drawerStub).props('modal')).toBe(false)
  })

  test('打开主题抽屉时不锁定页面滚动，避免工作区宽度变化', () => {
    const wrapper = mountDrawer()

    expect(wrapper.getComponent(drawerStub).props('lockScroll')).toBe(false)
  })

  test('管理端固定使用内容区滚动，不再展示会开启页面滚动的侧栏选项', () => {
    const wrapper = mountDrawer()

    expect(wrapper.find('[aria-label="固定侧栏"]').exists()).toBe(false)
  })

  test('列表滚动方式支持预览、取消、保存和恢复默认', async () => {
    const wrapper = mountDrawer()
    await wrapper.get('[data-table-scroll-mode="FIXED"]').trigger('click')
    expect(wrapper.emitted('preview').at(-1)[0].tableScrollMode).toBe('FIXED')
    expect(wrapper.get('[data-table-scroll-mode="FIXED"]').attributes('aria-pressed')).toBe('true')
    await wrapper.get('[data-action="cancel"]').trigger('click')
    expect(wrapper.emitted('cancel').at(-1)[0].tableScrollMode).toBe('AUTO')
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    expect(wrapper.get('[data-table-scroll-mode="AUTO"]').attributes('aria-pressed')).toBe('true')
    await wrapper.get('[data-table-scroll-mode="FIXED"]').trigger('click')
    await wrapper.get('[data-action="save"]').trigger('click')
    expect(wrapper.emitted('save').at(-1)[0].tableScrollMode).toBe('FIXED')
    await wrapper.get('[data-action="restore-default"]').trigger('click')
    expect(wrapper.emitted('preview').at(-1)[0].tableScrollMode).toBe('AUTO')
    wrapper.unmount()
  })

  test('展示 8 个纯色和 4 个固定渐变色卡', () => {
    const wrapper = mountDrawer()

    expect(wrapper.findAll('[data-accent-kind="solid"]')).toHaveLength(8)
    expect(wrapper.findAll('[data-accent-kind="gradient"]')).toHaveLength(4)
    expect(wrapper.find('[data-accent="THEME_BLUE"]').attributes('aria-label'))
      .toBe('主题蓝')
    expect(wrapper.find('[data-accent="PEACH_PINK_GRADIENT"]').attributes('aria-label'))
      .toBe('桃粉渐变')
  })

  test('支持自定义纯色和二色或三色渐变，并实时预览渐变方式', async () => {
    const wrapper = mountDrawer()

    await wrapper.find('[data-accent-mode="CUSTOM_GRADIENT"]').trigger('click')
    await wrapper.find('[data-gradient-count="3"]').trigger('click')
    await wrapper.find('[data-gradient-type="RADIAL"]').trigger('click')
    wrapper.vm.updateGradientColor(1, '#6C3BFF')
    await wrapper.vm.$nextTick()

    const preview = wrapper.emitted('preview').at(-1)[0]
    expect(preview.accentMode).toBe('CUSTOM_GRADIENT')
    expect(preview.customGradientColors).toHaveLength(3)
    expect(preview.customGradientColors[1]).toBe('#6C3BFF')
    expect(preview.customGradientType).toBe('RADIAL')
    expect(wrapper.findAll('[data-custom-gradient-color]')).toHaveLength(3)
  })

  test('菜单位置可在左侧和顶部间切换并参与草稿预览', async () => {
    const wrapper = mountDrawer()

    await wrapper.find('[data-navigation-layout="TOP"]').trigger('click')

    expect(wrapper.emitted('preview').at(-1)[0].navigationLayout).toBe('TOP')
    expect(wrapper.find('[data-navigation-layout="TOP"]')
      .attributes('aria-pressed')).toBe('true')
  })

  test('白天夜间和色卡选择只更新草稿并发送实时预览', async () => {
    const wrapper = mountDrawer()

    await wrapper.find('[data-theme-scheme="DARK"]').trigger('click')
    await wrapper.find('[data-accent="PEACH_PINK_GRADIENT"]').trigger('click')

    const preview = wrapper.emitted('preview').at(-1)[0]
    expect(preview.colorScheme).toBe('DARK')
    expect(preview.accentPreset).toBe('PEACH_PINK_GRADIENT')
    expect(wrapper.emitted('save')).toBeUndefined()
  })

  test('取消和非保存关闭发送打开前快照并关闭抽屉', async () => {
    const opened = { ...DEFAULT_THEME_CONFIG, accentPreset: 'LIQUID_PURPLE' }
    const wrapper = mountDrawer({ config: opened })
    await wrapper.find('[data-theme-scheme="DARK"]').trigger('click')

    wrapper.vm.requestClose()

    expect(wrapper.emitted('cancel')[0]).toEqual([opened])
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual([false])
  })

  test('恢复默认只预览默认草稿，仍需显式保存', async () => {
    const wrapper = mountDrawer({
      config: { ...DEFAULT_THEME_CONFIG, colorScheme: 'DARK' },
    })

    await wrapper.find('[data-action="restore-default"]').trigger('click')

    expect(wrapper.emitted('preview').at(-1)[0]).toEqual(DEFAULT_THEME_CONFIG)
    expect(wrapper.emitted('save')).toBeUndefined()
  })

  test('保存发送完整草稿且保存中禁止重复提交', async () => {
    const wrapper = mountDrawer()
    await wrapper.find('[data-theme-scheme="DARK"]').trigger('click')
    await wrapper.find('[data-action="save"]').trigger('click')

    expect(wrapper.emitted('save').at(-1)[0]).toEqual({
      ...DEFAULT_THEME_CONFIG,
      colorScheme: 'DARK',
    })

    await wrapper.setProps({ saving: true })
    expect(wrapper.find('[data-action="save"]').attributes('disabled')).toBeDefined()
  })
})
