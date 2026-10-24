import { mount } from '@test-utils'
import FieldReferenceDisplay from '@/components/common/FieldReferenceDisplay.vue'

describe('FieldReferenceDisplay', () => {
  test('按规则设计器样式分层展示类型、字段编码和业务名称', () => {
    const wrapper = mount(FieldReferenceDisplay, {
      props: {
        field: {
          fieldType: 'INTEGER',
          fieldCode: 'age',
          fieldName: '年龄',
        },
      },
    })

    const type = wrapper.find('.content-ref__type')
    expect(type.text()).toBe('123')
    expect(type.attributes('title')).toContain('INTEGER')
    expect(type.classes()).toContain('content-ref__type--number')
    expect(wrapper.find('.content-ref__code').text()).toBe('age')
    expect(wrapper.find('.content-ref__name').text()).toBe('年龄')
  })

  test('未知类型使用问号标识且缺少名称时回退到字段编码', () => {
    const wrapper = mount(FieldReferenceDisplay, {
      props: {
        field: {
          fieldType: 'CUSTOM',
          fieldCode: 'custom_field',
        },
        compact: true,
      },
    })

    expect(wrapper.find('.content-ref--wrap').exists()).toBe(false)
    expect(wrapper.find('.content-ref__type').text()).toBe('?')
    expect(wrapper.find('.content-ref__name').text()).toBe(
      'custom_field'
    )
  })
})
