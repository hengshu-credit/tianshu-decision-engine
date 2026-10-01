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
          props: ['visible'],
          emits: ['update:visible'],
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
      .toEqual(['code', 'status'])
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
})
