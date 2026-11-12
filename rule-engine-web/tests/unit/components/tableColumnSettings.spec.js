import { mount } from '@vue/test-utils'
import TableColumnSettings from '@/components/common/TableColumnSettings.vue'

const columns = [
  { key: 'code', label: '编码', required: true },
  { key: 'name', label: '名称' },
  { key: 'status', label: '状态' },
]

function mountSettings(modelValue = columns.map(column => column.key)) {
  return mount(TableColumnSettings, {
    props: {
      columns,
      modelValue,
      storageKey: 'test:table-columns',
    },
    global: {
      stubs: {
        'el-popover': {
          name: 'ElPopover',
          props: ['visible'],
          emits: ['update:visible', 'show'],
          template: '<div><slot name="reference" /><slot /></div>',
        },
        'el-button': {
          template: '<button><slot /></button>',
        },
      },
    },
  })
}

describe('TableColumnSettings', () => {
  beforeEach(() => window.localStorage.clear())

  test('打开时即按已应用字段勾选，显示动画完成保留新选择，取消后重开还原', async () => {
    const wrapper = mountSettings(['code', 'name'])
    const popover = wrapper.getComponent({ name: 'ElPopover' })
    popover.vm.$emit('update:visible', true)
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.draftVisibleKeys).toEqual(['code', 'name'])
    await wrapper.get('button[aria-label="隐藏名称"]').trigger('click')
    popover.vm.$emit('show')
    expect(wrapper.vm.draftVisibleKeys).toEqual(['code'])
    await wrapper.get('.table-column-settings__footer button').trigger('click')
    popover.vm.$emit('update:visible', true)
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.draftVisibleKeys).toEqual(['code', 'name'])
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrapper.unmount()
  })

  test('支持隐藏字段并按新顺序应用', async () => {
    const wrapper = mountSettings()
    wrapper.vm.beginEdit()
    wrapper.vm.toggleColumn('name')
    wrapper.vm.moveColumn(2, -1)
    wrapper.vm.apply()
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual([
      ['code', 'status'],
    ])
    expect(JSON.parse(window.localStorage.getItem('test:table-columns')))
      .toEqual({ version: 2, visibleKeys: ['code', 'status'] })
  })

  test('必选字段不能隐藏，恢复默认会重新显示全部字段', () => {
    const wrapper = mountSettings(['code', 'status'])
    wrapper.vm.beginEdit()
    wrapper.vm.toggleColumn('code')
    expect(wrapper.vm.draftVisibleKeys).toEqual(['code', 'status'])
    wrapper.vm.resetDraft()
    expect(wrapper.vm.draftVisibleKeys).toEqual(['code', 'name', 'status'])
  })

  test('从本地存储恢复字段配置并保留已隐藏字段', async () => {
    window.localStorage.setItem(
      'test:table-columns',
      JSON.stringify(['status', 'code'])
    )
    const wrapper = mountSettings()
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('update:modelValue')[0]).toEqual([['status', 'code']])
  })

  test.each([[[]], [['code']], [['retired']]])('旧偏好只剩必选列或失效字段时采用页面默认值 %j', legacy => {
    window.localStorage.setItem('test:table-columns', JSON.stringify(legacy))
    const view = mountSettings()
    expect(view.emitted('update:modelValue')).toBeUndefined()
    view.unmount()
  })

  test('显式隐藏全部非必选字段后，空偏好仍能恢复并继续编辑', async () => {
    const wrapper = mountSettings([])
    await wrapper.setProps({ columns: columns.map(column => ({ ...column, required: false })) })
    wrapper.vm.beginEdit()
    expect(wrapper.vm.draftVisibleKeys).toEqual([])
    wrapper.vm.apply()
    wrapper.unmount()
    const restored = mount(TableColumnSettings, { props: { columns: [{ key: 'name', label: '名称' }], storageKey: 'test:table-columns' }, shallow: true })
    expect(restored.emitted('update:modelValue')[0]).toEqual([[]])
    restored.unmount()
  })

  test('切换表格范围时使用新范围的偏好，不沿用上一个表格', async () => {
    window.localStorage.setItem('test:other-columns', '["status"]')
    const wrapper = mountSettings(['code', 'name'])
    await wrapper.setProps({ storageKey: 'test:other-columns' })
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual([['code', 'status']])
    await wrapper.setProps({ storageKey: 'test:new-columns' })
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual([['code', 'name', 'status']])
    wrapper.unmount()
  })
})
